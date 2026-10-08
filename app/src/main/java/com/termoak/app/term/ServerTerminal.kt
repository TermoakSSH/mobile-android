package com.termoak.app.term

import com.termoak.app.R
import com.termoak.app.UiText
import com.termoak.app.toUiText
import com.termoak.app.uiText
import com.termoak.ffi.ServerSessionState
import com.termoak.ffi.ServerTerminalEvent
import com.termoak.ffi.ServerTerminalHandle
import com.termoak.ffi.ServerTerminalListener
import com.termoak.ffi.SessionAccess
import com.termoak.ffi.TermoakCore
import com.termoak.ffi.TermoakException
import com.termoak.ffi.joinSharedSessionAs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** How to get into a session with an invitation link. */
data class LinkJoin(
    val server: String,
    val token: String,
    /** Signed in to that server: join with the account (its name). */
    val asAccount: Boolean,
    /** Display name for guests without an account. */
    val name: String?,
)

/**
 * Session that lives on the server: it stays open even if the phone sleeps or
 * loses coverage. When it comes back, the server sends the whole history.
 *
 * With [sessionId] it attaches to an existing one (yours or shared with you);
 * with [link], it joins with an invitation link; otherwise it opens a new one
 * on host [hostId]. [owner] is what is known before the server says hello.
 */
class ServerTerminal(
    private val core: TermoakCore,
    label: String,
    hostId: String?,
    sessionId: String?,
    owner: Boolean = true,
    private val link: LinkJoin? = null,
    accountId: String? = null,
    /** A new session is recorded on the server (`null`: as the host says). */
    private val record: Boolean? = null,
) : TermSession(label, hostId, accountId), ServerTerminalListener {
    @Volatile var sessionId: String? = sessionId
        private set
    @Volatile private var handle: ServerTerminalHandle? = null
    override val persistent = true

    /** Size of the view here, and of the terminal on the server (guests follow it). */
    @Volatile private var viewCols = 0
    @Volatile private var viewRows = 0
    @Volatile private var remoteCols = 0
    @Volatile private var remoteRows = 0
    /** The server said hello (before it, what you can do is not known yet). */
    @Volatile private var helloSeen = false

    init {
        if (!owner) _live.value = LiveShare(isOwner = false, access = SessionAccess.VIEW, canWrite = false)
    }

    /** Joined with an invitation link without an account (a guest with a display name). */
    val asGuest: Boolean get() = link != null && !link.asAccount

    override fun start() {
        if (handle != null) return
        _state.value = TermState.Connecting(uiText(R.string.term_connecting_server))
        scope.launch {
            try {
                val h = when {
                    // On the account of the host (or of the session).
                    link == null && accountId != null -> {
                        val acc = core.account(accountId)
                        val id = sessionId
                            ?: acc.openServerSession(hostId!!, screen.cols(), screen.rows(), label, record).id
                        acc.attachServerSession(id, this@ServerTerminal)
                    }
                    link == null -> {
                        val id = sessionId
                            ?: core.openServerSession(hostId!!, screen.cols(), screen.rows(), label, record).id
                        core.attachServerSession(id, this@ServerTerminal)
                    }
                    link.asAccount -> core.joinLink(link.token, this@ServerTerminal)
                    else -> joinSharedSessionAs(link.server, link.token, link.name, this@ServerTerminal)
                }
                handle = h
                sessionId = h.sessionId()
                syncFromHandle()
            } catch (e: TermoakException) {
                _state.value = TermState.Closed(e.toUiText(R.string.term_open_session_failed))
            }
        }
    }

    /** What the library knows (the hello may have arrived before the handle). */
    private fun syncFromHandle() {
        val h = handle ?: return
        if (!helloSeen || h.isWaiting()) return
        val canWrite = h.canWrite()
        _live.update { it.copy(isOwner = it.isOwner || h.isOwner(), canWrite = canWrite) }
        if (canWrite) sendSize()
        applySize()
    }

    override fun send(bytes: ByteArray) {
        // Nothing goes out while someone else has the keyboard (not even the
        // emulator's answers): the server would drop it.
        val h = handle ?: return
        if (h.canWrite()) h.write(bytes)
    }

    override fun resizeRemote(cols: UInt, rows: UInt) {
        if (handle?.canWrite() == true) handle?.resize(cols, rows)
    }

    /**
     * The view changed size. Whoever can type sets the terminal's size; the
     * rest keep the server's and only remember theirs for when they get the keyboard.
     */
    override fun resize(cols: Int, rows: Int) {
        if (cols == viewCols && rows == viewRows) return
        viewCols = cols
        viewRows = rows
        if (_live.value.canWrite) super.resize(cols, rows) else applySize()
    }

    private fun sendSize() {
        if (viewCols > 0 && viewRows > 0 && handle?.canWrite() == true) {
            handle?.resize(viewCols.toUInt(), viewRows.toUInt())
        }
    }

    /** Puts the emulator at the size it should have: the view's, or the server's for read-only guests. */
    private fun applySize() {
        val follow = !_live.value.canWrite && remoteCols > 0 && remoteRows > 0
        val (c, r) = if (follow) remoteCols to remoteRows else viewCols to viewRows
        if (c <= 0 || r <= 0) return
        if (c.toUInt() == screen.cols() && r.toUInt() == screen.rows()) return
        screen.resize(c.toUInt(), r.toUInt())
        onScreenChanged()
    }

    /** Detaches: the session stays on the server (guests leave it). */
    override fun release() {
        handle?.let {
            runCatching { it.detach() }
            it.close()
        }
        handle = null
    }

    override fun reconnect() {
        helloSeen = false
        _live.update { it.copy(ended = null, waiting = null) }
        super.reconnect()
    }

    /** Ends the session on the server (for everyone). */
    fun terminate() {
        handle?.let { runCatching { it.closeSession() } }
            ?: sessionId?.let { id ->
                scope.launch {
                    runCatching { accountId?.let { core.account(it).closeServerSession(id) } ?: core.closeServerSession(id) }
                }
            }
    }

    // ----- Guests -----

    /** Asks the owner for the keyboard. */
    fun requestControl() {
        _live.update { it.copy(controlRequested = true) }
        io { it.requestControl() }
    }

    /** Gives the keyboard back (or withdraws the request). */
    fun releaseControl() {
        _live.update { it.copy(controlRequested = false) }
        io { it.releaseControl() }
    }

    /** Link guests: the name the others see. */
    fun setName(name: String) = io { it.setName(name) }

    // ----- Owner -----

    override fun allowJoin(participantId: String) {
        _live.update { it.dropRequest(participantId) }
        io { it.allowJoin(participantId) }
    }

    override fun denyJoin(participantId: String) {
        _live.update { it.dropRequest(participantId) }
        io { it.denyJoin(participantId) }
    }

    override fun grantControl(participantId: String, minutes: UInt?) {
        _live.update { it.dropRequest(participantId) }
        io { it.grantControl(participantId, minutes) }
    }

    override fun denyControl(participantId: String) {
        _live.update { it.dropRequest(participantId) }
        io { it.denyControl(participantId) }
    }

    override fun takeControl() = io { it.takeControl() }

    override fun kick(participantId: String, block: Boolean) {
        _live.update { it.dropRequest(participantId) }
        io { it.kick(participantId, block) }
    }

    /** Everyone else leaves and every invitation is revoked. */
    fun stopSharing() = io { it.stopSharing() }

    /** Calls the handle off the main thread (the library blocks briefly). */
    private fun io(action: (ServerTerminalHandle) -> Unit) {
        val h = handle ?: return
        scope.launch(Dispatchers.IO) {
            runCatching { action(h) }.onFailure { toast(it.toUiText(R.string.share_action_failed)) }
        }
    }

    // ----- Events (their own thread, in order) -----

    override fun onEvent(event: ServerTerminalEvent) {
        when (event) {
            is ServerTerminalEvent.Hello -> {
                helloSeen = true
                val s = event.session
                _title.value = s.title.ifBlank { null }
                remoteCols = s.cols.toInt()
                remoteRows = s.rows.toInt()
                val owner = s.access == SessionAccess.OWNER
                val canWrite = handle?.canWrite() ?: owner
                _live.update {
                    it.copy(isOwner = owner, access = s.access, canWrite = canWrite, waiting = null)
                        .withParticipants(s.participants, s.driver)
                        .copy(driverUntil = s.driverUntil)
                }
                if (canWrite) sendSize()
                applySize()
                applyState(s.state)
            }
            is ServerTerminalEvent.Output -> {
                if (_state.value !is TermState.Running) _state.value = TermState.Running
                output(event.data)
            }
            is ServerTerminalEvent.Resync -> {
                // The full history comes next.
                screen.reset()
                onScreenChanged()
            }
            is ServerTerminalEvent.Status -> applyState(event.state)
            is ServerTerminalEvent.Title -> _title.value = event.title.ifBlank { null }
            is ServerTerminalEvent.Resize -> {
                remoteCols = event.cols.toInt()
                remoteRows = event.rows.toInt()
                applySize()
            }
            is ServerTerminalEvent.Participants -> _live.update { it.withParticipants(event.participants, event.driver) }
            is ServerTerminalEvent.Control -> {
                val before = _live.value
                _live.update {
                    it.copy(
                        canWrite = event.canWrite,
                        driver = event.driver,
                        driverName = event.driverName,
                        driverUntil = event.until,
                        controlRequested = if (event.canWrite) false else it.controlRequested,
                    )
                }
                // A timed grant that ran out says so itself (`ControlExpired`).
                val timeUp = before.driverUntil?.let { System.currentTimeMillis() >= it - 2_000 } == true
                if (!before.isOwner && before.canWrite != event.canWrite && !(timeUp && !event.canWrite)) {
                    toast(uiText(if (event.canWrite) R.string.share_you_have_keyboard else R.string.share_keyboard_taken))
                }
                if (event.canWrite) sendSize()
                applySize()
            }
            is ServerTerminalEvent.ControlExpired -> {
                val live = _live.value
                toast(
                    when {
                        !live.isOwner -> uiText(R.string.share_control_expired_you)
                        else -> live.nameOf(event.participantId)?.let { uiText(R.string.share_control_expired_owner, it) }
                            ?: uiText(R.string.share_control_expired_owner_anon)
                    },
                )
            }
            is ServerTerminalEvent.Waiting -> {
                _live.update {
                    it.copy(
                        isOwner = false,
                        canWrite = false,
                        waiting = ShareWaiting(event.title, event.owner, event.participantId),
                    )
                }
                if (event.title.isNotBlank()) _title.value = event.title
            }
            is ServerTerminalEvent.JoinRequest -> {
                _live.update { it.addJoinRequest(event.participant) }
                notice(ShareNotice.Kind.JOIN_REQUEST, event.participant.name, event.participant.id)
            }
            is ServerTerminalEvent.ControlRequest -> {
                _live.update { it.addControlRequest(event.participant) }
                notice(ShareNotice.Kind.CONTROL_REQUEST, event.participant.name, event.participant.id)
            }
            is ServerTerminalEvent.ControlDenied -> {
                _live.update { it.copy(controlRequested = false) }
                toast(uiText(R.string.share_control_denied))
            }
            is ServerTerminalEvent.Prompt -> {
                val p = event.prompt
                val h = handle
                _pending.value = if (p.fingerprint != null) {
                    Pending.HostKey(p.host, p.keyType ?: "", p.fingerprint!!) { ok ->
                        h?.answerPrompt(p.promptId, ok, null)
                        _pending.value = null
                    }
                } else {
                    Pending.Credentials(UiText.Raw(p.host), p.message, p.fields) { answers ->
                        h?.answerPrompt(p.promptId, answers != null, answers)
                        _pending.value = null
                    }
                }
            }
            is ServerTerminalEvent.PromptDone -> _pending.value = null
            // An action that wasn't allowed: the connection stays open.
            is ServerTerminalEvent.Error -> toast(UiText.Raw(event.message))
            is ServerTerminalEvent.Ended -> {
                _live.update { it.copy(ended = ShareEnd(event.code, event.message), waiting = null, canWrite = false) }
                _state.value = TermState.Closed(endMessage(event.code)?.let { uiText(it) } ?: UiText.Raw(event.message))
            }
            is ServerTerminalEvent.Closed -> if (_state.value !is TermState.Closed) {
                _state.value = TermState.Closed(uiText(R.string.term_server_session_closed))
            }
            else -> Unit
        }
    }

    private fun notice(kind: ShareNotice.Kind, name: String, participantId: String) {
        onShareNotice(ShareNotice(kind, id, sessionId, _title.value ?: label, name, participantId))
    }

    private fun applyState(state: ServerSessionState) {
        // The end screen stays once the server has sent you away.
        if (_live.value.ended != null) return
        _state.value = when (state) {
            is ServerSessionState.Connecting -> TermState.Connecting(UiText.Raw(state.message))
            is ServerSessionState.Running -> TermState.Running
            is ServerSessionState.HostOffline -> TermState.Connecting(uiText(R.string.term_host_offline_waiting))
            is ServerSessionState.Closed -> TermState.Closed(
                state.reason?.let { UiText.Raw(it) }
                    ?: state.exitCode?.let { uiText(R.string.term_session_ended_code, it.toString()) }
                    ?: uiText(R.string.term_session_closed),
            )
        }
    }

    companion object {
        /** Title of the end screen for each close code. */
        fun endMessage(code: String): Int? = when (code) {
            "revoked" -> R.string.share_end_revoked
            "kicked" -> R.string.share_end_kicked
            "expired" -> R.string.share_end_expired
            "session_ended" -> R.string.share_end_session_ended
            "join_denied" -> R.string.share_end_join_denied
            "forbidden" -> R.string.share_end_forbidden
            else -> null
        }
    }
}
