package com.termoak.app.term

import com.termoak.app.R
import com.termoak.app.UiText
import com.termoak.app.data.uid
import com.termoak.app.toUiText
import com.termoak.app.uiText
import com.termoak.ffi.ActiveForward
import com.termoak.ffi.AuthHandler
import com.termoak.ffi.AuthPromptKind
import com.termoak.ffi.AuthRequest
import com.termoak.ffi.ForwardKind
import com.termoak.ffi.ForwardStats
import com.termoak.ffi.ItemFilter
import com.termoak.ffi.PortForward
import com.termoak.ffi.SshSession
import com.termoak.ffi.TermoakCore
import com.termoak.ffi.TermoakException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Running tunnels (port forwarding), as on iOS. They go over an SSH
 * connection from the phone: the one of a terminal open to that host if
 * there is one, or an own one that is closed when its last tunnel stops.
 * The ones marked "Start on connect" start when a terminal of their host
 * connects. While any runs, the terminals' foreground service keeps the app
 * (and so the tunnels) alive in the background.
 */
class Tunnels(private val core: TermoakCore, private val sessions: Sessions) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _running = MutableStateFlow<Map<String, ActiveForward>>(emptyMap())
    /** Running tunnels by [PortForward.uid]. */
    val running: StateFlow<Map<String, ActiveForward>> = _running
    private val _stats = MutableStateFlow<Map<String, ForwardStats>>(emptyMap())
    /** Their connections and bytes, every second while any runs. */
    val stats: StateFlow<Map<String, ForwardStats>> = _stats
    private val _starting = MutableStateFlow<Set<String>>(emptySet())
    /** Tunnels being started (connecting first). */
    val starting: StateFlow<Set<String>> = _starting
    private val _pending = MutableStateFlow<Pending?>(null)
    /** A host key or a password asked by an own connection. */
    val pending: StateFlow<Pending?> = _pending
    private val _errors = MutableSharedFlow<UiText>(extraBufferCapacity = 8)
    val errors: SharedFlow<UiText> = _errors

    private class Connection(val session: SshSession, val own: Boolean)

    /** Connections by host ("account/host"). */
    private val connections = mutableMapOf<String, Connection>()
    /** The host of each running tunnel. */
    private val hostOf = mutableMapOf<String, String>()
    private var ticker: Job? = null

    private fun hostKey(accountId: String?, hostId: String) = "${accountId.orEmpty()}/$hostId"

    fun isRunning(f: PortForward): Boolean = f.uid in _running.value

    /** Starts a saved tunnel (connecting first if needed). */
    fun start(f: PortForward) {
        val uid = f.uid
        if (uid in _running.value || uid in _starting.value) return
        _starting.value += uid
        scope.launch {
            try {
                val s = connection(f.hostId, f.accountId)
                val active = s.startForward(f.id)
                _running.value += uid to active
                hostOf[uid] = hostKey(f.accountId, f.hostId)
                changed()
            } catch (e: TermoakException) {
                _errors.tryEmit(e.toUiText(R.string.tunnels_start_failed))
                releaseIfUnused(hostKey(f.accountId, f.hostId))
            } finally {
                _starting.value -= uid
            }
        }
    }

    fun stop(f: PortForward) {
        val uid = f.uid
        val active = _running.value[uid] ?: return
        _running.value -= uid
        _stats.value -= uid
        val host = hostOf.remove(uid)
        changed()
        scope.launch {
            runCatching { active.stop() }
            active.close()
            host?.let { releaseIfUnused(it) }
        }
    }

    /** Stops every tunnel ("Close all" of the notification). */
    fun stopAll() {
        val all = _running.value.values.toList()
        _running.value = emptyMap()
        _stats.value = emptyMap()
        hostOf.clear()
        changed()
        scope.launch {
            all.forEach { a ->
                runCatching { a.stop() }
                a.close()
            }
            connections.keys.toList().forEach { releaseIfUnused(it) }
        }
    }

    /** A terminal of [hostId] connected from the phone: its tunnels marked "Start on connect" start over its connection. */
    fun onTerminalConnected(terminal: LocalTerminal) {
        val hostId = terminal.hostId ?: return
        if (terminal.telnet) return
        scope.launch {
            val everywhere = ItemFilter(accountIds = null, vaultIds = null, includeDevice = true)
            val automatic = runCatching { core.listForwards(hostId, everywhere) }.getOrDefault(emptyList())
                .filter { it.autoStart && it.uid !in _running.value && it.uid !in _starting.value }
            if (automatic.isEmpty()) return@launch
            val key = hostKey(terminal.accountId, hostId)
            val conn = connections[key]?.takeIf { !it.session.isClosed() }
                ?: terminal.connection()?.let { Connection(it, own = false).also { c -> connections[key] = c } }
                ?: return@launch
            for (f in automatic) {
                try {
                    _running.value += f.uid to conn.session.startForward(f.id)
                    hostOf[f.uid] = key
                } catch (e: TermoakException) {
                    _errors.tryEmit(e.toUiText(R.string.tunnels_start_failed))
                }
            }
            changed()
        }
    }

    private suspend fun connection(hostId: String, accountId: String?): SshSession {
        val key = hostKey(accountId, hostId)
        connections[key]?.let { c ->
            if (!c.session.isClosed()) return c.session
            connections.remove(key)
        }
        // A terminal open to that host: its connection, without connecting again.
        val terminal = sessions.list.value.filterIsInstance<LocalTerminal>()
            .firstOrNull { it.hostId == hostId && it.accountId == accountId && !it.telnet }
        terminal?.connection()?.let { s ->
            connections[key] = Connection(s, own = false)
            return s
        }
        val s = core.connect(hostId, Auth(), accountId)
        connections[key] = Connection(s, own = true)
        return s
    }

    /** Closes the own connection of a host that has no tunnels left. */
    private fun releaseIfUnused(key: String) {
        if (key in hostOf.values) return
        val c = connections.remove(key) ?: return
        CoroutineScope(Dispatchers.IO).launch {
            if (c.own) runCatching { c.session.disconnect() }
            c.session.close()
        }
    }

    private fun changed() {
        sessions.updateService()
        if (_running.value.isEmpty()) {
            ticker?.cancel()
            ticker = null
            return
        }
        if (ticker?.isActive == true) return
        ticker = scope.launch {
            while (isActive && _running.value.isNotEmpty()) {
                measure()
                delay(1000)
            }
        }
    }

    /** Stats of the running tunnels; the ones that went down (their terminal closed...) are removed. */
    private fun measure() {
        val stats = mutableMapOf<String, ForwardStats>()
        val gone = mutableListOf<String>()
        for ((uid, a) in _running.value) {
            if (runCatching { a.isRunning() }.getOrDefault(false)) {
                runCatching { a.stats() }.getOrNull()?.let { stats[uid] = it }
            } else {
                gone += uid
            }
        }
        if (gone.isNotEmpty()) {
            _running.value -= gone.toSet()
            gone.forEach { uid -> hostOf.remove(uid)?.let { releaseIfUnused(it) } }
            sessions.updateService()
        }
        _stats.value = stats
    }

    /** Questions of an own connection (each on its own thread; they may block). */
    private inner class Auth : AuthHandler {
        override fun onHostKey(host: String, port: UInt, keyType: String, fingerprint: String): Boolean {
            val answer = CompletableDeferred<Boolean>()
            _pending.value = Pending.HostKey(if (port == 22u) host else "$host:$port", keyType, fingerprint) { answer.complete(it) }
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
}

/** Texts and links of a tunnel (JVM tests). */
object TunnelText {
    /** `L 127.0.0.1:8080 → db:5432` (with the real port if a free one was asked), `D SOCKS 127.0.0.1:1080`. */
    fun summary(f: PortForward, boundPort: UInt?, remoteWord: String): String {
        val p = boundPort ?: f.bindPort
        val listen = "${f.bindAddress}:${if (p == 0u) "auto" else p.toString()}"
        val destination = "${f.destHost ?: "?"}:${f.destPort?.toString() ?: "?"}"
        return when (f.kind) {
            ForwardKind.LOCAL -> "L $listen → $destination"
            ForwardKind.REMOTE -> "R $remoteWord $listen → $destination"
            ForwardKind.DYNAMIC -> "D SOCKS $listen"
        }
    }

    /** "Open in the browser": a local tunnel running on [boundPort] (`null` for the others). */
    fun browserUrl(f: PortForward, boundPort: UInt?): String? =
        if (f.kind == ForwardKind.LOCAL && boundPort != null && boundPort > 0u) "http://127.0.0.1:$boundPort" else null
}
