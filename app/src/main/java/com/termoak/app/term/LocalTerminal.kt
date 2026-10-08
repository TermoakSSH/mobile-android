package com.termoak.app.term

import com.termoak.app.R
import com.termoak.app.UiText
import com.termoak.app.toUiText
import com.termoak.app.uiText
import com.termoak.ffi.AuthHandler
import com.termoak.ffi.AuthPromptKind
import com.termoak.ffi.AuthRequest
import com.termoak.ffi.HostKeyChange
import com.termoak.ffi.HostKeyChangeHandler
import com.termoak.ffi.SharedTerminal
import com.termoak.ffi.SharedTerminalEvent
import com.termoak.ffi.SharedTerminalListener
import com.termoak.ffi.SshSession
import com.termoak.ffi.TerminalHandle
import com.termoak.ffi.TerminalListener
import com.termoak.ffi.TerminalStatus
import com.termoak.ffi.TermoakCore
import com.termoak.ffi.TermoakException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/** SSH (or Telnet) terminal that goes out from the phone itself. */
class LocalTerminal(
    private val core: TermoakCore,
    label: String,
    hostId: String,
    private val address: String,
    accountId: String? = null,
    /** A Telnet host: unencrypted, without SFTP or another SSH connection. */
    val telnet: Boolean = false,
    /** Recorded (asciicast) even if its host doesn't record every session; also after reconnecting. */
    val record: Boolean = false,
    /** Settings → Terminal: the host's username and password answer its first login prompts. */
    private val telnetAutoLogin: () -> Boolean = { true },
) : TermSession(label, hostId, accountId), TerminalListener, AuthHandler, HostKeyChangeHandler {
    @Volatile private var handle: TerminalHandle? = null
    override val persistent = false
    /** Connected (each time: also after reconnecting). */
    @Volatile var onConnected: () -> Unit = {}

    // ----- Reconnecting by itself (the iOS app's rule, AutoReconnect) -----

    /** It was connected when the app (or the network) went away. */
    @Volatile private var connectedWhenLeaving = false
    /** It closed with an exit code (`exit`): that doesn't come back by itself. */
    @Volatile private var endedByProgram = false
    /** Until when a drop reconnects by itself (just after coming back). */
    @Volatile private var reconnectsUntil = 0L

    /** The app goes to the background (or the network drops). */
    fun leaving() {
        connectedWhenLeaving = _state.value == TermState.Running
    }

    /**
     * Back after [awayMs]: cut meanwhile, it reconnects by itself; still
     * looking connected after a long while, a keep-alive checks it first; and
     * if it drops in the next seconds it reconnects too. Not the ones closed by hand or by `exit`.
     */
    fun returned(awayMs: Long) {
        val action = AutoReconnect.onReturn(
            connectedWhenLeaving, asleep, _state.value is TermState.Closed, _state.value == TermState.Running, endedByProgram, awayMs,
        )
        connectedWhenLeaving = false
        when (action) {
            AutoReconnect.Action.NONE -> Unit
            AutoReconnect.Action.RECONNECT -> autoReconnect()
            AutoReconnect.Action.WATCH -> reconnectsUntil = System.currentTimeMillis() + AutoReconnect.WINDOW_MS
            AutoReconnect.Action.CHECK -> {
                reconnectsUntil = System.currentTimeMillis() + AutoReconnect.WINDOW_MS
                val h = handle
                scope.launch {
                    val alive = latencyMs() != null
                    if (!alive && handle === h && _state.value == TermState.Running) autoReconnect()
                }
            }
        }
    }

    private fun autoReconnect() {
        reconnectsUntil = 0L
        toast(uiText(R.string.term_reconnecting))
        reconnect()
    }

    /**
     * Round trip to the host in milliseconds (an SSH keep-alive or a Telnet
     * TIMING-MARK), or `null` when unknown: not connected, no answer in
     * time, or a host that doesn't speak Telnet.
     */
    override suspend fun latencyMs(): Double? {
        val h = handle?.takeIf { _state.value == TermState.Running } ?: return null
        return try {
            h.latencyMs(LATENCY_TIMEOUT_MS)
        } catch (_: TermoakException) {
            null
        }
    }

    /** Shared through the server: for the AI (only for you) and/or with other people. */
    private var shared: SharedTerminal? = null
    /** The copilot uses the share: it stays while its panel is open. */
    private var sharedForCopilot = false
    /** Shared by hand (share sheet): it stays until "Stop sharing". */
    private var sharedByHand = false
    private val _sharedId = MutableStateFlow<String?>(null)
    /** Id of the relay session while shared. */
    val sharedId: StateFlow<String?> = _sharedId

    /**
     * Shares the terminal with the server as [title] (or reuses what is
     * already shared) and returns the id of the relay session; `null` if it
     * couldn't. Sharing stops when the terminal closes or reconnects; with
     * [forCopilot], also with [stopCopilotShare].
     */
    suspend fun share(title: String, forCopilot: Boolean = false): String? =
        try {
            shareOrThrow(title, forCopilot)
        } catch (_: TermoakException) {
            null
        }

    /** Like [share], but says why it couldn't (not signed in...). By hand unless [forCopilot]. */
    suspend fun shareOrThrow(title: String, forCopilot: Boolean = false): String? {
        shared?.let {
            if (forCopilot) sharedForCopilot = true else sharedByHand = true
            return it.sessionId()
        }
        val h = handle ?: return null
        if (_state.value != TermState.Running) return null
        val s = core.shareTerminal(h, title)
        // The terminal closed (or reconnected) in the meantime.
        if (handle !== h || shared != null) {
            runCatching { s.stop() }
            s.close()
            return shared?.sessionId()
        }
        shared = s
        sharedForCopilot = forCopilot
        sharedByHand = !forCopilot
        _sharedId.value = s.sessionId()
        runCatching { s.setListener(ShareListener(s)) }
        return _sharedId.value
    }

    /**
     * A new reference to this terminal's SSH connection (to browse its files
     * without connecting again), while connected. Whoever takes it closes it.
     */
    /** The recording of this terminal (an asciicast `.cast` file the engine writes), when its host records sessions. */
    fun recordingPath(): String? = handle?.let { runCatching { it.recordingPath() }.getOrNull() }

    fun connection(): SshSession? = handle?.takeIf { _state.value == TermState.Running && !telnet }?.session()

    /** The relay share, once [shareOrThrow] made it. */
    val sharedTerminal: SharedTerminal? get() = shared

    /** Stops sharing if it was shared only for the copilot (not if it was by hand). */
    fun stopCopilotShare() {
        sharedForCopilot = false
        if (!sharedByHand) stopSharing()
    }

    /** "Stop sharing" from the share sheet: everyone leaves; the AI keeps it while its panel is open. */
    suspend fun stopSharingByHand() {
        val s = shared ?: return
        sharedByHand = false
        runCatching { s.revokeAllInvites() }
        _live.value = LiveShare()
        if (!sharedForCopilot) stopSharing()
    }

    private fun stopSharing() {
        val s = shared ?: return
        shared = null
        sharedForCopilot = false
        sharedByHand = false
        _sharedId.value = null
        _live.value = LiveShare()
        // Outside [scope]: it is cancelled when the tab closes.
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { s.stop() }
            s.close()
        }
    }

    override fun start() {
        if (handle != null) return
        _state.value = TermState.Connecting(uiText(R.string.term_connecting_to, address))
        scope.launch {
            try {
                handle = core.connectTerminal(
                    hostId!!, screen.cols(), screen.rows(), this@LocalTerminal, this@LocalTerminal, accountId,
                    telnetAutoLogin = telnetAutoLogin(),
                    record = record,
                    keyChanged = this@LocalTerminal,
                )
                _state.value = TermState.Running
                onConnected()
            } catch (e: TermoakException) {
                _state.value = TermState.Closed(
                    // A Strict vault: its hosts open through the server, which doesn't open Telnet sessions.
                    if (telnet && e is TermoakException.UseOnlyStrict) uiText(R.string.telnet_strict_vault)
                    else e.toUiText(R.string.term_connect_failed),
                )
            }
        }
    }

    override fun send(bytes: ByteArray) {
        handle?.write(bytes)
    }

    override fun resizeRemote(cols: UInt, rows: UInt) {
        handle?.resize(cols, rows)
        shared?.let { s -> scope.launch { runCatching { s.resize(cols, rows) } } }
    }

    // ----- Owner actions (this phone is the host) -----

    private fun owner(action: suspend (SharedTerminal) -> Unit) {
        val s = shared ?: return
        scope.launch {
            runCatching { action(s) }.onFailure { toast(it.toUiText(R.string.share_action_failed)) }
        }
    }

    override fun allowJoin(participantId: String) {
        _live.update { it.dropRequest(participantId) }
        owner { it.allowJoin(participantId) }
    }

    override fun denyJoin(participantId: String) {
        _live.update { it.dropRequest(participantId) }
        owner { it.denyJoin(participantId) }
    }

    override fun grantControl(participantId: String, minutes: UInt?) {
        _live.update { it.dropRequest(participantId) }
        owner { it.grantControl(participantId, minutes) }
    }

    override fun denyControl(participantId: String) {
        _live.update { it.dropRequest(participantId) }
        owner { it.denyControl(participantId) }
    }

    override fun takeControl() = owner { it.takeControl() }

    override fun kick(participantId: String, block: Boolean) {
        _live.update { it.dropRequest(participantId) }
        owner { it.kick(participantId, block) }
    }

    /** What the server says about the shared terminal (its own thread, in order). */
    private inner class ShareListener(private val share: SharedTerminal) : SharedTerminalListener {
        override fun onEvent(event: SharedTerminalEvent) {
            if (shared !== share) return
            when (event) {
                is SharedTerminalEvent.Participants -> _live.update { it.withParticipants(event.participants, event.driver) }
                is SharedTerminalEvent.Control -> _live.update {
                    it.copy(driver = event.driver, driverName = event.driverName, driverUntil = event.until)
                }
                is SharedTerminalEvent.ControlExpired -> toast(
                    _live.value.nameOf(event.participantId)?.let { uiText(R.string.share_control_expired_owner, it) }
                        ?: uiText(R.string.share_control_expired_owner_anon),
                )
                is SharedTerminalEvent.JoinRequest -> {
                    _live.update { it.addJoinRequest(event.participant) }
                    notice(ShareNotice.Kind.JOIN_REQUEST, event.participant.name, event.participant.id)
                }
                is SharedTerminalEvent.ControlRequest -> {
                    _live.update { it.addControlRequest(event.participant) }
                    notice(ShareNotice.Kind.CONTROL_REQUEST, event.participant.name, event.participant.id)
                }
                // The terminal is here: guests follow the size of this one.
                is SharedTerminalEvent.ResizeRequest -> Unit
                is SharedTerminalEvent.Reconnecting -> toast(uiText(R.string.share_relay_reconnecting))
                is SharedTerminalEvent.Reconnected -> Unit
                is SharedTerminalEvent.Ended -> {
                    if (sharedByHand) toast(uiText(R.string.share_relay_ended))
                    scope.launch { if (shared === share) stopSharing() }
                }
            }
        }

        private fun notice(kind: ShareNotice.Kind, name: String, participantId: String) {
            onShareNotice(ShareNotice(kind, id, share.sessionId(), _title.value ?: label, name, participantId))
        }
    }

    override fun release() {
        stopSharing()
        handle?.let {
            runCatching { it.closeTerminal() }
            it.close()
        }
        handle = null
    }

    // ----- TerminalListener (terminal thread, in order) -----

    override fun onOutput(data: ByteArray) = output(data)

    override fun onStatus(status: TerminalStatus) {
        if (status is TerminalStatus.Closed) {
            endedByProgram = status.exitCode != null
            // Cut just after coming back from the background (not `exit`): again by itself.
            if (status.exitCode == null && System.currentTimeMillis() < reconnectsUntil && _state.value == TermState.Running) {
                scope.launch { autoReconnect() }
                return
            }
            val why = status.reason?.let { UiText.Raw(it) }
                ?: status.exitCode?.let { uiText(R.string.term_session_ended_code, it.toString()) }
            _state.value = TermState.Closed(why ?: uiText(R.string.term_connection_closed))
            scope.launch { stopSharing() }
        }
    }

    // ----- AuthHandler (each question on its own thread; it may block) -----

    override fun onHostKey(host: String, port: UInt, keyType: String, fingerprint: String): Boolean {
        val answer = CompletableDeferred<Boolean>()
        _pending.value = Pending.HostKey(if (port == 22u) host else "$host:$port", keyType, fingerprint) {
            answer.complete(it)
        }
        // The SSH handshake times out after ~30 s.
        return runBlocking { withTimeoutOrNull(30_000) { answer.await() } ?: false }
            .also { _pending.value = null }
    }

    /** A known host (or a jump host) whose key changed: asked here, with both fingerprints. */
    override fun onHostKeyChanged(change: HostKeyChange): Boolean = askChangedKey(_pending, change)

    override fun onPrompt(request: AuthRequest): List<String>? {
        val answer = CompletableDeferred<List<String>?>()
        val title = request.title.takeIf { it.isNotBlank() }?.let { UiText.Raw(it) } ?: when (request.kind) {
            AuthPromptKind.PASSWORD -> uiText(R.string.term_password_for, request.host)
            AuthPromptKind.PASSPHRASE -> uiText(R.string.term_key_passphrase)
            AuthPromptKind.KEYBOARD_INTERACTIVE -> UiText.Raw(request.host)
        }
        _pending.value = Pending.Credentials(title, request.instructions, request.fields) { answer.complete(it) }
        return runBlocking { answer.await() }.also { _pending.value = null }
    }

    private companion object {
        /** Longest wait for a latency answer (then it is unknown). */
        const val LATENCY_TIMEOUT_MS = 5000u
    }
}
