package com.termoak.app.push

import com.termoak.ffi.PushCategory
import com.termoak.ffi.PushEvent

/**
 * What a push notification of the server leads to (docs-next/PUSH.md, core
 * docs/MOBILE.md): the decrypted payload, read by the engine
 * (`parsePushPayload` → [PushEvent]), becomes a notification to show, one to
 * withdraw or nothing. Plain Kotlin: the JVM tests check every kind.
 *
 * Rules: an approval's Approve and Deny only **open** the app's own
 * confirmation (behind the app lock); a security key request only opens the
 * app, which then starts its events socket and gets the waiting request
 * (never connected from the background when the push arrives).
 */
sealed interface PushOpen {
    val accountId: String

    /** An AI task (its approval, or its result). */
    data class AiTask(override val accountId: String, val taskId: String, val approvalId: String?) : PushOpen

    /** A server session: `kind` is the push kind (join_request, control_request, session_prompt, session_shared). */
    data class Session(override val accountId: String, val sessionId: String, val kind: String, val participant: String?) : PushOpen

    /** The teams (you were added to one). */
    data class Teams(override val accountId: String) : PushOpen

    /** A connection made by the server waits for a security key: open the app (events socket, then the sheet). */
    data class SecurityKey(override val accountId: String, val requestId: String?) : PushOpen

    /**
     * Temporary access (teams security, 0.7 phase 4d): an `access_request` to
     * decide opens the Access screen on that request (Approve/Deny only open
     * its confirmation there, behind the app lock); `access_decided` opens
     * "My requests".
     */
    data class Access(override val accountId: String, val requestId: String?, val mine: Boolean) : PushOpen

    /**
     * Another device of yours asks for your E2EE key (0.7 phase 5c). The
     * server sends no push for it: the events socket brings it and, with the
     * app in the background, the app posts this notification itself; it opens
     * Settings → Security on that request (compare the code, then decide).
     */
    data class E2eePairing(override val accountId: String, val pairingId: String) : PushOpen

    /** Just the app (a test, an unknown kind...). */
    data class Home(override val accountId: String, val kind: String) : PushOpen
}

/** The buttons of a notification. Each one opens the app's confirmation; none decides by itself. */
enum class PushAction { APPROVE, DENY }

/** A notification to show. Empty texts: the notifier uses the app's own for that channel. */
data class PushPlan(
    val channel: String,
    val title: String,
    val body: String,
    val tag: String,
    val id: Int,
    val open: PushOpen,
    val actions: List<PushAction>,
    /** Milliseconds after which the system removes it (a request that expires), or `null`. */
    val timeoutMs: Long?,
    /** Heads-up (approvals, requests and security keys). */
    val urgent: Boolean,
)

sealed interface PushDecision {
    /** Nothing to show (expired, another account's, unreadable). */
    data object Ignore : PushDecision

    /** Withdraw the notification with this id (answered elsewhere or expired). */
    data class Cancel(val id: Int, val tag: String) : PushDecision

    data class Show(val plan: PushPlan) : PushDecision
}

object PushRoutes {
    /** The notification channels, in the order Settings shows them. */
    val CHANNELS = listOf("approvals", "sessions", "security_keys", "ai", "general")

    /**
     * What to do with [ev], received for the account [accountId] (the
     * UnifiedPush instance). [userId]: that account's user id, to check the
     * payload is for it; `null` when unknown.
     */
    fun decide(ev: PushEvent, accountId: String, userId: String?, now: Long = System.currentTimeMillis()): PushDecision {
        if (ev.user != null && userId != null && ev.user != userId) return PushDecision.Ignore
        if (ev.category == PushCategory.RESOLVED) return PushDecision.Cancel(ev.notificationId, ev.tag)
        val expires = ev.expiresAt
        if (ev.expired || (expires != null && expires <= now)) return PushDecision.Ignore
        val open = openOf(ev, accountId)
        val actions = if (ev.category == PushCategory.APPROVAL) listOf(PushAction.APPROVE, PushAction.DENY) else emptyList()
        return PushDecision.Show(
            PushPlan(
                channel = ev.channel.takeIf { it in CHANNELS } ?: "general",
                title = ev.title,
                body = ev.body,
                tag = ev.tag,
                id = ev.notificationId,
                open = open,
                actions = actions,
                timeoutMs = expires?.let { (it - now).coerceAtLeast(1_000) },
                urgent = ev.category == PushCategory.APPROVAL || ev.category == PushCategory.SECURITY_KEY ||
                    ev.kind == "session_prompt",
            ),
        )
    }

    /** Where tapping it goes. */
    fun openOf(ev: PushEvent, accountId: String): PushOpen {
        val d = ev.data
        return when (ev.kind) {
            "ai_approval", "ai_finished" -> d["task_id"]?.let { PushOpen.AiTask(accountId, it, d["approval_id"]) }
            "join_request", "control_request", "session_prompt", "session_shared" ->
                d["session_id"]?.let { PushOpen.Session(accountId, it, ev.kind, d["participant"]) }
            "team_added" -> PushOpen.Teams(accountId)
            "security_key_request" -> PushOpen.SecurityKey(accountId, d["request_id"])
            "access_request" -> PushOpen.Access(accountId, d["request_id"], mine = false)
            "access_decided" -> PushOpen.Access(accountId, d["request_id"], mine = true)
            else -> null
        } ?: PushOpen.Home(accountId, ev.kind)
    }

    /** The notification of a pairing request (the app's own, posted from the events socket in the background). */
    fun pairingPlan(accountId: String, p: com.termoak.ffi.E2eePairingRequest, res: android.content.res.Resources, now: Long): PushPlan =
        PushPlan(
            channel = "approvals",
            title = res.getString(com.termoak.app.R.string.e2ee_pairing_notice_title),
            body = res.getString(com.termoak.app.R.string.e2ee_pairing_notice_body, p.deviceName),
            tag = "e2ee-pairing",
            id = ("e2ee:" + p.id).hashCode(),
            open = PushOpen.E2eePairing(accountId, p.id),
            actions = emptyList(),
            timeoutMs = (p.expiresAt - now).coerceAtLeast(1_000),
            urgent = true,
        )

    // ----- Intent extras (the notification's PendingIntents) -----

    const val EXTRA_KIND = "push_open"
    private const val EXTRA_ACCOUNT = "push_account"
    private const val EXTRA_ID = "push_item"
    private const val EXTRA_ID2 = "push_item2"
    private const val EXTRA_TYPE = "push_type"
    private const val EXTRA_ACTION = "push_action"
    const val EXTRA_NOTIFICATION = "push_notification"

    /** The extras of a tap ([action] `null`) or of one of its buttons. */
    fun extras(open: PushOpen, action: PushAction?): Map<String, String> {
        val m = mutableMapOf(EXTRA_ACCOUNT to open.accountId)
        when (open) {
            is PushOpen.AiTask -> {
                m[EXTRA_KIND] = "ai"
                m[EXTRA_ID] = open.taskId
                open.approvalId?.let { m[EXTRA_ID2] = it }
            }
            is PushOpen.Session -> {
                m[EXTRA_KIND] = "session"
                m[EXTRA_ID] = open.sessionId
                m[EXTRA_TYPE] = open.kind
                open.participant?.let { m[EXTRA_ID2] = it }
            }
            is PushOpen.Teams -> m[EXTRA_KIND] = "teams"
            is PushOpen.SecurityKey -> {
                m[EXTRA_KIND] = "security_key"
                open.requestId?.let { m[EXTRA_ID] = it }
            }
            is PushOpen.Access -> {
                m[EXTRA_KIND] = "access"
                open.requestId?.let { m[EXTRA_ID] = it }
                m[EXTRA_TYPE] = if (open.mine) "mine" else "approvals"
            }
            is PushOpen.E2eePairing -> {
                m[EXTRA_KIND] = "e2ee_pairing"
                m[EXTRA_ID] = open.pairingId
            }
            is PushOpen.Home -> {
                m[EXTRA_KIND] = "home"
                m[EXTRA_TYPE] = open.kind
            }
        }
        action?.let { m[EXTRA_ACTION] = it.name }
        return m
    }

    /** Reads [extras] back (`null`: not one of ours). */
    fun fromExtras(get: (String) -> String?): Pair<PushOpen, PushAction?>? {
        val account = get(EXTRA_ACCOUNT) ?: return null
        val open: PushOpen = when (get(EXTRA_KIND)) {
            "ai" -> PushOpen.AiTask(account, get(EXTRA_ID) ?: return null, get(EXTRA_ID2))
            "session" -> PushOpen.Session(account, get(EXTRA_ID) ?: return null, get(EXTRA_TYPE).orEmpty(), get(EXTRA_ID2))
            "teams" -> PushOpen.Teams(account)
            "security_key" -> PushOpen.SecurityKey(account, get(EXTRA_ID))
            "access" -> PushOpen.Access(account, get(EXTRA_ID), get(EXTRA_TYPE) == "mine")
            "e2ee_pairing" -> PushOpen.E2eePairing(account, get(EXTRA_ID) ?: return null)
            "home" -> PushOpen.Home(account, get(EXTRA_TYPE).orEmpty())
            else -> return null
        }
        val action = get(EXTRA_ACTION)?.let { a -> PushAction.entries.firstOrNull { it.name == a } }
        return open to action
    }
}
