package com.termoak.app.term

import com.termoak.app.R
import com.termoak.app.UiText
import com.termoak.app.toUiText
import com.termoak.app.uiText
import com.termoak.ffi.ServerSessionState
import com.termoak.ffi.ServerTerminalEvent
import com.termoak.ffi.ServerTerminalHandle
import com.termoak.ffi.ServerTerminalListener
import com.termoak.ffi.TermoakCore
import com.termoak.ffi.TermoakException
import kotlinx.coroutines.launch

/**
 * Session that lives on the server: it stays open even if the phone sleeps or
 * loses coverage. When it comes back, the server sends the whole history.
 *
 * With [sessionId] it attaches to an existing one; without it, it opens a new
 * one on host [hostId].
 */
class ServerTerminal(
    private val core: TermoakCore,
    label: String,
    hostId: String?,
    sessionId: String?,
) : TermSession(label, hostId), ServerTerminalListener {
    @Volatile var sessionId: String? = sessionId
        private set
    @Volatile private var handle: ServerTerminalHandle? = null
    override val persistent = true

    override fun start() {
        if (handle != null) return
        _state.value = TermState.Connecting(uiText(R.string.term_connecting_server))
        scope.launch {
            try {
                val id = sessionId ?: core.openServerSession(hostId!!, screen.cols(), screen.rows(), label, null).id
                    .also { sessionId = it }
                handle = core.attachServerSession(id, this@ServerTerminal)
                handle?.resize(screen.cols(), screen.rows())
            } catch (e: TermoakException) {
                _state.value = TermState.Closed(e.toUiText(R.string.term_open_session_failed))
            }
        }
    }

    override fun send(bytes: ByteArray) {
        handle?.write(bytes)
    }

    override fun resizeRemote(cols: UInt, rows: UInt) {
        handle?.resize(cols, rows)
    }

    /** Detaches: the session stays on the server. */
    override fun release() {
        handle?.let {
            runCatching { it.detach() }
            it.close()
        }
        handle = null
    }

    /** Ends the session on the server (for everyone). */
    fun terminate() {
        handle?.let { runCatching { it.closeSession() } }
            ?: sessionId?.let { id -> scope.launch { runCatching { core.closeServerSession(id) } } }
    }

    override fun onEvent(event: ServerTerminalEvent) {
        when (event) {
            is ServerTerminalEvent.Hello -> {
                _title.value = event.session.title.ifBlank { null }
                applyState(event.session.state)
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
            is ServerTerminalEvent.Error -> _state.value = TermState.Closed(UiText.Raw(event.message))
            is ServerTerminalEvent.Closed -> if (_state.value !is TermState.Closed) {
                _state.value = TermState.Closed(uiText(R.string.term_server_session_closed))
            }
            else -> Unit
        }
    }

    private fun applyState(state: ServerSessionState) {
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
}
