package com.termoak.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.termoak.app.R
import com.termoak.app.UiText
import com.termoak.app.aiSetupError
import com.termoak.app.localized
import com.termoak.app.term.AiCommandRisk
import com.termoak.app.term.TermSession
import com.termoak.app.term.TerminalAiRules
import com.termoak.app.toUiText
import com.termoak.app.uiText
import com.termoak.ffi.AccountStatus
import com.termoak.ffi.AiAssistContext
import com.termoak.ffi.AiCommandSuggestion
import com.termoak.ffi.LastCommandInfo
import com.termoak.ffi.nlRequest
import com.termoak.ffi.redactSecrets
import com.termoak.ffi.shortenText
import com.termoak.ffi.typeableCommand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** A command the AI proposes in a terminal, to be typed (never run). */
data class AiProposal(
    /** `null`: "Fix" on a failed command; otherwise the `# request` line it replaces. */
    val requestLine: String?,
    val state: State,
) {
    sealed class State {
        data object Asking : State()
        /** Waiting for "Type it" (a dangerous one asks first). */
        data class Ready(val suggestion: AiCommandSuggestion) : State()
        /** Already typed in place of the `# request` line. */
        data class Typed(val suggestion: AiCommandSuggestion) : State()
        data class Failed(val message: UiText) : State()
    }
}

/** The AI's explanation of a failed command (Markdown), in a sheet. */
data class AiExplanationItem(
    val title: String,
    val answer: String? = null,
    val provider: String? = null,
    val error: UiText? = null,
)

/** What the AI is doing over one terminal (Compose state). */
class TerminalAiState {
    var proposal by mutableStateOf<AiProposal?>(null)
    var explanation by mutableStateOf<AiExplanationItem?>(null)
    /** Answers of an older question are dropped. */
    internal var generation = 0
}

/**
 * The AI in the terminals, like the desktop's and iOS's: a failed command
 * offers "Explain" (why, in a sheet) and "Fix" (a corrected command), and a
 * `# request` line becomes a command (the key bar's AI key or Ctrl+Enter).
 * The AI is only asked when tapped and nothing runs by itself: a proposed
 * command is typed at the prompt for the user to review. Whatever is sent
 * goes through the engine's `redactSecrets` on the device first.
 */
class TerminalAi(private val context: Context, private val accounts: Accounts) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val states = mutableMapOf<String, TerminalAiState>()

    fun state(session: TermSession): TerminalAiState = states.getOrPut(session.id) { TerminalAiState() }

    fun forget(sessionId: String) {
        states.remove(sessionId)
    }

    /**
     * Account whose AI a terminal uses: its host's (signed in), or else the
     * current one; `null`: there is no AI to ask.
     */
    fun accountFor(session: TermSession): String? {
        val list = accounts.list.value
        session.accountId?.let { id -> if (list.any { it.id == id && it.status == AccountStatus.ACTIVE }) return id }
        return accounts.current.value?.takeIf { it.status == AccountStatus.ACTIVE }?.id
    }

    private fun text(id: Int, vararg args: Any): String = context.localized().getString(id, *args)

    private fun errorOf(e: Throwable): UiText = aiSetupError(e)?.let { UiText.Res(it) } ?: e.toUiText(R.string.terminal_ai_failed)

    private fun context(session: TermSession, screen: String? = null): AiAssistContext =
        AiAssistContext(os = session.hostOs, screen = screen ?: session.screenTail(), cwd = null)

    private fun forAi(last: LastCommandInfo): String =
        redactSecrets(TerminalAiRules.forAi(last.command, last.output, last.exitCode))

    /** "Explain": why the command failed, in a sheet. */
    fun explainFailed(session: TermSession) {
        val last = session.failedCommand.value ?: session.lastCommand.value ?: return
        session.dismissFailed()
        val st = state(session)
        val short = runCatching { shortenText(last.command.orEmpty(), 48u) }.getOrDefault(last.command.orEmpty())
        val title = if (last.command == null) text(R.string.terminal_ai_error_title) else text(R.string.terminal_ai_failed_title, short)
        val account = accountFor(session)
        val handle = account?.let { accounts.handle(it) }
        if (handle == null) {
            st.explanation = AiExplanationItem(title, error = uiText(R.string.terminal_ai_not_signed_in))
            return
        }
        val question = last.exitCode?.let { text(R.string.terminal_ai_ask_failed_exit, it) } ?: text(R.string.terminal_ai_ask_failed)
        val item = AiExplanationItem(title)
        st.explanation = item
        val (body, ctx) = forAi(last) to context(session)
        scope.launch {
            val next = try {
                val r = handle.aiExplain(body, question, ctx, null)
                item.copy(answer = r.answer.ifBlank { text(R.string.terminal_ai_no_answer) }, provider = r.provider)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                item.copy(error = errorOf(e))
            }
            if (st.explanation === item) st.explanation = next
        }
    }

    /** "Fix": a corrected command, to type after reviewing it. */
    fun fixFailed(session: TermSession) {
        val last = session.failedCommand.value ?: session.lastCommand.value ?: return
        session.dismissFailed()
        val request = text(R.string.terminal_ai_ask_fix, redactSecrets(last.command.orEmpty()))
        // What the command printed is the screen that matters.
        propose(session, request, context(session, screen = forAi(last)), requestLine = null)
    }

    /**
     * The `# request` being typed becomes a command. Whether there was one
     * (otherwise a hint is due).
     */
    fun askForLine(session: TermSession): Boolean {
        if (!session.canType) return false
        val line = session.typedLine() ?: return false
        val request = runCatching { nlRequest(line) }.getOrNull() ?: return false
        propose(session, redactSecrets(request), context(session), requestLine = line)
        return true
    }

    private fun propose(session: TermSession, request: String, ctx: AiAssistContext, requestLine: String?) {
        val st = state(session)
        val n = ++st.generation
        val handle = accountFor(session)?.let { accounts.handle(it) }
        if (handle == null) {
            st.proposal = AiProposal(requestLine, AiProposal.State.Failed(uiText(R.string.terminal_ai_not_signed_in)))
            return
        }
        st.proposal = AiProposal(requestLine, AiProposal.State.Asking)
        scope.launch {
            val state = try {
                val r = handle.aiSuggest(request, ctx, null)
                if (runCatching { typeableCommand(r.command) }.getOrDefault(r.command).isBlank()) {
                    AiProposal.State.Failed(uiText(R.string.terminal_ai_no_command))
                } else {
                    AiProposal.State.Ready(r)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                AiProposal.State.Failed(errorOf(e))
            }
            if (st.generation != n || st.proposal == null) return@launch
            // A `# request` still at the prompt is replaced at once, unless the command is dangerous.
            if (state is AiProposal.State.Ready && requestLine != null && !AiCommandRisk.of(state.suggestion.risk).needsConfirmation &&
                session.typedLine() == requestLine
            ) {
                session.typeAiCommand(state.suggestion.command, requestLine)
                st.proposal = AiProposal(requestLine, AiProposal.State.Typed(state.suggestion))
            } else {
                st.proposal = st.proposal?.copy(state = state)
            }
        }
    }

    /** "Type it": the command at the prompt, without Enter. */
    fun typeProposal(session: TermSession) {
        val st = state(session)
        val p = st.proposal ?: return
        val s = (p.state as? AiProposal.State.Ready)?.suggestion ?: return
        session.typeAiCommand(s.command, p.requestLine)
        st.proposal = null
    }

    fun dismissProposal(session: TermSession) {
        val st = state(session)
        st.generation++
        st.proposal = null
    }

    /** Enter was pressed after a typed proposal: it's done. */
    fun commandSent(session: TermSession) {
        val st = states[session.id] ?: return
        if (st.proposal?.state is AiProposal.State.Typed) st.proposal = null
    }
}
