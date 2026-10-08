package com.termoak.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.termoak.app.R
import com.termoak.app.UiText
import com.termoak.app.aiSetupError
import com.termoak.app.localized
import com.termoak.app.term.LocalTerminal
import com.termoak.app.term.ServerTerminal
import com.termoak.app.term.Sessions
import com.termoak.app.term.TermSession
import com.termoak.app.toUiText
import com.termoak.ffi.AccountHandle
import com.termoak.ffi.AiPermissionMode
import com.termoak.ffi.AiTask
import com.termoak.ffi.AiTaskRequest
import com.termoak.ffi.AiTaskStatus
import com.termoak.ffi.TermoakCore
import com.termoak.ffi.TermoakException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * The copilot: one conversation with the server's AI per terminal tab. It
 * lives while the tab is open; when it closes it is forgotten (the task stays
 * in the AI section).
 */
class Copilot(private val context: Context, private val core: TermoakCore, private val accounts: Accounts, sessions: Sessions) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val chats = mutableMapOf<String, CopilotChat>()

    /**
     * The conversation of terminal [session]. It runs on one server: a
     * server session's own account; for an SSH terminal from the phone, the
     * current account (its terminal is shared there for the AI).
     */
    fun chat(session: TermSession): CopilotChat = chats.getOrPut(session.id) {
        val account = if (session is ServerTerminal) session.accountId ?: accounts.current.value?.id else accounts.current.value?.id
        CopilotChat(accounts, account, scope)
    }

    /**
     * Sends [text] to the conversation of terminal [session] ([where]: its
     * host). In a direct SSH terminal that isn't shared, it is first shared
     * with the server (only for you) so the AI can type in it; if that fails,
     * the screen is attached instead. If the session is no longer the one the
     * task knows (it was shared again or the SSH reconnected), the AI is told
     * the new one.
     */
    fun send(session: TermSession, where: String?, text: String) {
        val chat = chat(session)
        // The host is known to the AI when it is on that server.
        // Not a Telnet host: the AI's host tools (commands, files) are SSH; it uses the shared terminal.
        val telnet = session is LocalTerminal && session.telnet
        val hosts = listOfNotNull(session.hostId).takeIf { session.accountId != null && session.accountId == chat.accountId && !telnet }.orEmpty()
        chat.send(text, hosts) { first ->
            var sid = when (session) {
                is ServerTerminal -> session.sessionId
                is LocalTerminal -> session.sharedId.value
                else -> null
            }
            if (sid == null && session is LocalTerminal && !chat.shareFailed) {
                val title = context.localized().getString(R.string.copilot_share_title, where ?: session.label)
                sid = session.share(title, forCopilot = true)
                if (sid == null) chat.shareFailed = true
            }
            val prompt = when {
                // Without a session the AI can't read the terminal: attach the screen.
                sid == null -> withScreenContext(session, where ?: "local terminal", text)
                !first && sid != chat.sessionId ->
                    "<context>\nThe user's terminal is now session $sid: use it with read_terminal and send_to_terminal.\n</context>\n\n$text"
                else -> text
            }
            prompt to sid
        }
    }

    /**
     * When the panel is closed or Stop is tapped: the task is cancelled if it
     * is working, and the AI loses access to the terminal if it was shared for
     * it. Server sessions are left alone.
     */
    fun stop(session: TermSession) {
        chats[session.id]?.takeIf { it.active }?.cancel()
        (session as? LocalTerminal)?.stopCopilotShare()
    }

    init {
        scope.launch {
            accounts.aiEvents.collect { ev ->
                val taskId = ev.optString("task_id")
                val event = ev.optJSONObject("event") ?: return@collect
                chats.values.filter { it.taskId == taskId }.forEach { it.onEvent(event) }
            }
        }
        // Events were lost: reload whatever is open.
        scope.launch { accounts.changes.collect { if (it == "lagged") chats.values.forEach { c -> c.reload() } } }
        scope.launch {
            sessions.list.collect { list ->
                val open = list.map { it.id }.toSet()
                chats.keys.retainAll(open)
            }
        }
    }
}

/** Tool the AI is using right now (not yet stored in the conversation). */
data class LiveTool(val callId: String, val tool: String, val summary: String, val output: String? = null, val ok: Boolean = true)

/** Action waiting for your permission. */
data class CopilotApproval(
    val id: String,
    val tool: String,
    val summary: String,
    val command: String,
    /** What it shows (server 0.6: risk, diff...). */
    val preview: ApprovalPreview? = null,
)

/** A copilot conversation. Its state is Compose state: it is redrawn as it changes. */
class CopilotChat internal constructor(
    private val accounts: Accounts,
    /** Account whose AI it talks to (`null`: none signed in). */
    val accountId: String?,
    private val scope: CoroutineScope,
) {
    private fun api(): AccountHandle = accountId?.let { accounts.handle(it) }
        ?: throw TermoakException.NotLoggedIn("you are not signed in to any server")

    var taskId by mutableStateOf<String?>(null)
        private set
    /** Permissions: the task's or, without one, the ones it will be created with. */
    var mode by mutableStateOf(AiPermissionMode.ASK)
        private set
    /** What is being typed (kept when switching tabs). */
    var draft by mutableStateOf("")
    var status by mutableStateOf<AiTaskStatus?>(null)
        private set
    var turns by mutableStateOf<List<Turn>>(emptyList())
        private set
    var approvals by mutableStateOf<List<CopilotApproval>>(emptyList())
        private set
    var error by mutableStateOf<UiText?>(null)
        private set
    /** [error] is fixed in Settings → AI (an API key is missing or the credit is spent). */
    val needsAiSetup: Boolean
        get() = (error as? UiText.Res)?.id.let { it == R.string.error_ai_key_required || it == R.string.error_ai_budget_exceeded }
    /** Message sent that the server hasn't returned in the conversation yet. */
    var sending by mutableStateOf<String?>(null)
        private set

    // ----- Live (not stored yet) -----
    var liveText by mutableStateOf("")
        private set
    var liveReasoning by mutableStateOf("")
        private set
    var liveTools by mutableStateOf<List<LiveTool>>(emptyList())
        private set
    /** Output of tools whose call is already stored but whose result isn't yet. */
    var liveOutputs by mutableStateOf<Map<String, LiveTool>>(emptyMap())
        private set
    var notices by mutableStateOf<List<String>>(emptyList())
        private set

    val active: Boolean
        get() = status == AiTaskStatus.QUEUED || status == AiTaskStatus.RUNNING || status == AiTaskStatus.WAITING_APPROVAL

    val empty: Boolean get() = taskId == null && sending == null

    /** Changes with every "New conversation": whatever arrives from the previous one is dropped. */
    private var generation = 0

    /** Starts another conversation (the previous one stays in the AI section). */
    fun reset() {
        generation++
        taskId = null
        sessionId = null
        shareFailed = false
        status = null
        turns = emptyList()
        approvals = emptyList()
        error = null
        sending = null
        clearLive()
        notices = emptyList()
    }

    /** The last server session given to the AI (the one it works in). */
    var sessionId by mutableStateOf<String?>(null)
        private set
    /** The terminal couldn't be shared in this conversation: don't retry. */
    var shareFailed by mutableStateOf(false)

    /**
     * Sends [text]. First, [prepare] (with `true` for the first message)
     * returns the text that is actually sent (with context if needed) and, for
     * the first one, the session the AI will work in. The first message
     * creates the task with [hostIds]; the next ones continue the conversation.
     */
    fun send(text: String, hostIds: List<String>, prepare: suspend (first: Boolean) -> Pair<String, String?>) {
        if (sending != null) return
        sending = text
        error = null
        notices = emptyList()
        val gen = generation
        scope.launch {
            try {
                val id = taskId
                val (prompt, session) = prepare(id == null)
                if (gen != generation) return@launch
                val task = if (id == null) {
                    api().createAiTask(
                        AiTaskRequest(prompt = prompt, title = null, mode = mode, provider = null,
                            hostIds = hostIds, sessionId = session, effort = null),
                    )
                } else {
                    api().sendAiMessage(id, prompt)
                }
                if (gen != generation) return@launch
                if (session != null) sessionId = session
                taskId = task.id
                status = task.status
                load()
            } catch (e: TermoakException) {
                if (gen != generation) return@launch
                error = aiSetupError(e)?.let { UiText.Res(it) } ?: e.toUiText(R.string.error_send_failed)
                // Don't lose what was typed.
                if (draft.isEmpty()) draft = text
            } finally {
                if (gen == generation) sending = null
            }
        }
    }

    /** Changes the permissions (also mid-conversation: they apply from then on). */
    fun changeMode(m: AiPermissionMode) {
        mode = m
        val id = taskId ?: return
        scope.launch {
            runCatching { api().setAiTaskMode(id, m) }.onFailure { error = it.toUiText(R.string.copilot_change_mode_failed) }
            load()
        }
    }

    fun cancel() {
        val id = taskId ?: return
        scope.launch {
            runCatching { api().cancelAiTask(id) }.onFailure { error = it.message?.let { m -> UiText.Raw(m) } }
            load()
        }
    }

    fun decide(approvalId: String, decision: ApprovalDecision) {
        val id = taskId ?: return
        approvals = approvals.filterNot { it.id == approvalId }
        scope.launch {
            runCatching { api().decide(id, approvalId, decision) }
                .onFailure { error = it.toUiText(R.string.error_decide_failed) }
            load()
        }
    }

    fun reload() {
        if (taskId != null) scope.launch { load() }
    }

    private suspend fun load() {
        val id = taskId ?: return
        try {
            apply(api().getAiTask(id))
        } catch (e: TermoakException) {
            error = e.toUiText(R.string.copilot_load_failed)
        }
    }

    private fun apply(task: AiTask) {
        if (task.id != taskId) return
        val conversation = parseConversation(task.rawJson)
        turns = conversation
        status = task.status
        // After "Always approve" the task switches to Autonomous.
        mode = task.mode
        val previews = ApprovalPreview.byApproval(task.rawJson)
        approvals = task.pendingApprovals.map {
            CopilotApproval(it.id, it.tool, it.summary, toolSummary(it.inputJson), previews[it.id])
        }
        // What is already stored is no longer "live".
        val saved = conversation.filterIsInstance<Turn.Tool>()
        val savedIds = saved.map { it.id }.toSet()
        val pendingIds = saved.filter { it.output == null }.map { it.id }.toSet()
        liveOutputs = (liveOutputs + liveTools.filter { it.callId in savedIds }.associateBy { it.callId })
            .filterKeys { it in pendingIds }
        liveTools = liveTools.filterNot { it.callId in savedIds }
        if (!task.error.isNullOrBlank() && task.status == AiTaskStatus.FAILED) error = UiText.Raw(task.error!!)
    }

    private fun clearLive() {
        liveText = ""
        liveReasoning = ""
        liveTools = emptyList()
        liveOutputs = emptyMap()
    }

    /** An event of this task (`event` from the account WebSocket). */
    internal fun onEvent(ev: JSONObject) {
        when (ev.optString("type")) {
            "text" -> liveText += ev.optString("delta")
            "reasoning" -> liveReasoning += ev.optString("delta")
            "reset" -> { liveText = ""; liveReasoning = "" }
            "notice" -> ev.optString("message").takeIf { it.isNotBlank() }?.let { notices = notices + it }
            "tool_call" -> {
                val id = ev.optString("call_id")
                if (liveTools.none { it.callId == id }) {
                    val summary = ev.optString("summary").ifBlank { toolSummary(ev.opt("input")) }
                    liveTools = liveTools + LiveTool(id, ev.optString("tool"), summary)
                }
            }
            "tool_result" -> {
                val id = ev.optString("call_id")
                val out = ev.optString("output")
                val ok = ev.optBoolean("ok", true)
                if (liveTools.any { it.callId == id }) {
                    liveTools = liveTools.map { if (it.callId == id) it.copy(output = out, ok = ok) else it }
                } else {
                    liveOutputs = liveOutputs + (id to LiveTool(id, "", "", out, ok))
                }
            }
            "approval_requested" -> {
                val id = ev.optString("approval_id")
                if (approvals.none { it.id == id }) {
                    approvals = approvals +
                        CopilotApproval(
                            id, ev.optString("tool"), ev.optString("summary"), toolSummary(ev.opt("input")),
                            ApprovalPreview.parse(ev.optJSONObject("preview")),
                        )
                }
            }
            "approval_decided" -> approvals = approvals.filterNot { it.id == ev.optString("approval_id") }
            "status" -> status = statusOf(ev.optString("status")) ?: status
            "message", "finished" -> {
                if (ev.optString("type") == "finished") statusOf(ev.optString("status"))?.let { status = it }
                // What was live is stored now: it is removed on reload (not
                // before, so it doesn't flicker), keeping whatever arrives meanwhile.
                val text = liveText.length
                val reasoning = liveReasoning.length
                val id = taskId ?: return
                scope.launch {
                    load()
                    if (taskId == id) {
                        liveText = liveText.drop(text)
                        liveReasoning = liveReasoning.drop(reasoning)
                    }
                }
            }
        }
    }
}

private fun statusOf(s: String): AiTaskStatus? = when (s) {
    "queued" -> AiTaskStatus.QUEUED
    "running" -> AiTaskStatus.RUNNING
    "waiting_approval" -> AiTaskStatus.WAITING_APPROVAL
    "completed" -> AiTaskStatus.COMPLETED
    "failed" -> AiTaskStatus.FAILED
    "cancelled" -> AiTaskStatus.CANCELLED
    else -> null
}

/** Maximum screen text attached as context. */
private const val MaxScreenContext = 4000

/**
 * Prepends the last lines shown by the terminal to the message (when the AI
 * has no server session to read it by itself). This text is for the AI, not
 * for the user: it stays in English.
 */
private fun withScreenContext(session: TermSession, where: String, text: String): String {
    var screen = runCatching { session.screen.screenText() }.getOrDefault("")
        .lines().map { it.trimEnd() }.dropLastWhile { it.isEmpty() }.joinToString("\n").trim('\n')
    if (screen.length > MaxScreenContext) screen = screen.takeLast(MaxScreenContext).substringAfter('\n')
    if (screen.isBlank()) return text
    return "<context>\nWhat the user's terminal shows last ($where):\n```\n$screen\n```\n</context>\n\n$text"
}
