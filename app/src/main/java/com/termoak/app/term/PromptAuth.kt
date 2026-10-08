package com.termoak.app.term

import com.termoak.app.R
import com.termoak.app.UiText
import com.termoak.app.uiText
import com.termoak.ffi.AuthHandler
import com.termoak.ffi.AuthPromptKind
import com.termoak.ffi.AuthRequest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The questions of a connection made outside a terminal (tunnels,
 * installing a key): the host key the first time, and passwords or
 * passphrases. The screen shows [pending] with `PendingDialog`. Each
 * question comes on its own thread and waits for the answer.
 */
class PromptAuth : AuthHandler {
    private val _pending = MutableStateFlow<Pending?>(null)
    val pending: StateFlow<Pending?> = _pending

    override fun onHostKey(host: String, port: UInt, keyType: String, fingerprint: String): Boolean {
        val answer = CompletableDeferred<Boolean>()
        _pending.value = Pending.HostKey(if (port == 22u) host else "$host:$port", keyType, fingerprint) { answer.complete(it) }
        // The SSH handshake times out after ~30 s.
        return runBlocking { withTimeoutOrNull(30_000) { answer.await() } ?: false }.also { _pending.value = null }
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
