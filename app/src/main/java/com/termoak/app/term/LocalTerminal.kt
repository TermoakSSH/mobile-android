package com.termoak.app.term

import com.termoak.app.R
import com.termoak.app.UiText
import com.termoak.app.toUiText
import com.termoak.app.uiText
import com.termoak.ffi.AuthHandler
import com.termoak.ffi.AuthPromptKind
import com.termoak.ffi.AuthRequest
import com.termoak.ffi.SharedTerminal
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/** SSH terminal that goes out from the phone itself. */
class LocalTerminal(
    private val core: TermoakCore,
    label: String,
    hostId: String,
    private val address: String,
) : TermSession(label, hostId), TerminalListener, AuthHandler {
    @Volatile private var handle: TerminalHandle? = null
    override val persistent = false

    /** Shared with the server (only for you) so the AI can type in it. */
    private var shared: SharedTerminal? = null
    /** Shared for the copilot (not by hand): stopped when the panel closes. */
    private var sharedForCopilot = false
    private val _sharedId = MutableStateFlow<String?>(null)
    /** Id of the relay session while shared. */
    val sharedId: StateFlow<String?> = _sharedId

    /**
     * Shares the terminal with the server as [title] (or reuses what is
     * already shared) and returns the id of the relay session; `null` if it
     * couldn't. Sharing stops when the terminal closes or reconnects; with
     * [forCopilot], also with [stopCopilotShare].
     */
    suspend fun share(title: String, forCopilot: Boolean = false): String? {
        shared?.let { return it.sessionId() }
        val h = handle ?: return null
        if (_state.value != TermState.Running) return null
        val s = try {
            core.shareTerminal(h, title)
        } catch (_: TermoakException) {
            return null
        }
        // The terminal closed (or reconnected) in the meantime.
        if (handle !== h || shared != null) {
            runCatching { s.stop() }
            s.close()
            return shared?.sessionId()
        }
        shared = s
        sharedForCopilot = forCopilot
        _sharedId.value = s.sessionId()
        return _sharedId.value
    }

    /** Stops sharing if it was shared for the copilot (not if it was by hand). */
    fun stopCopilotShare() {
        if (sharedForCopilot) stopSharing()
    }

    private fun stopSharing() {
        val s = shared ?: return
        shared = null
        sharedForCopilot = false
        _sharedId.value = null
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
                handle = core.connectTerminal(hostId!!, screen.cols(), screen.rows(), this@LocalTerminal, this@LocalTerminal)
                _state.value = TermState.Running
            } catch (e: TermoakException) {
                _state.value = TermState.Closed(e.toUiText(R.string.term_connect_failed))
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
}
