package com.termoak.app.term

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.termoak.app.R
import com.termoak.app.localized
import com.termoak.ffi.ServerSession
import com.termoak.ffi.ServerSessionState
import com.termoak.ffi.SessionAccess
import com.termoak.ffi.SshHost
import com.termoak.ffi.TermoakCore
import com.termoak.ffi.TermoakException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Terminals open in the app. They survive navigation; while there is any,
 * [TerminalService] keeps the app alive in the background so Android doesn't
 * cut the connections.
 */
class Sessions(private val context: Context, private val core: TermoakCore) {
    private val _list = MutableStateFlow<List<TermSession>>(emptyList())
    val list: StateFlow<List<TermSession>> = _list
    private val _active = MutableStateFlow<String?>(null)
    val active: StateFlow<String?> = _active

    private val _onServer = MutableStateFlow<List<ServerSession>>(emptyList())
    /** Your sessions open on the server (without closed ones, relays or those shared with you). */
    val onServer: StateFlow<List<ServerSession>> = _onServer
    /** The sleeping tabs were already added since startup or sign-in. */
    private var restored = false

    fun get(id: String): TermSession? = _list.value.firstOrNull { it.id == id }

    /** SSH from the phone. */
    fun openLocal(host: SshHost): TermSession =
        add(LocalTerminal(core, host.label, host.id, host.address))

    /** New (persistent) session on the server. */
    fun openOnServer(host: SshHost): TermSession =
        add(ServerTerminal(core, host.label, host.id, null))

    /** Attaches to a session that already lives on the server. */
    fun attach(sessionId: String, label: String, hostId: String?): TermSession {
        _list.value.firstOrNull { it is ServerTerminal && it.sessionId == sessionId }?.let {
            select(it.id)
            return it
        }
        return add(ServerTerminal(core, label, hostId, sessionId))
    }

    /**
     * Fetches the server sessions. The first time after startup or sign-in,
     * adds a sleeping tab for each of yours that isn't open yet, without
     * changing the active tab: it attaches when opened.
     */
    suspend fun loadServerSessions() {
        val active = try {
            core.listServerSessions().active
        } catch (_: TermoakException) {
            return
        }
        // Only the ones running on the server: relays (phone or other
        // computers' terminals shared, the copilot's too) are not attached.
        val mine = active.filter {
            it.kind == "server" && it.state !is ServerSessionState.Closed && it.access == SessionAccess.OWNER
        }
        _onServer.value = mine
        if (restored) return
        restored = true
        val open = _list.value.mapNotNull { (it as? ServerTerminal)?.sessionId }.toSet()
        val missing = mine.filter { it.id !in open }
        if (missing.isEmpty()) return
        val labels = runCatching { core.listHosts() }.getOrDefault(emptyList()).associate { it.id to it.label }
        val untitled = context.localized().getString(R.string.common_session)
        _list.value = _list.value + missing.map { s ->
            ServerTerminal(core, s.title.ifBlank { s.hostId?.let { labels[it] } ?: untitled }, s.hostId, s.id)
                .apply { sleep() }
        }
    }

    /** Signed out of the server: they are added again on the next sign-in. */
    fun forgetServerSessions() {
        restored = false
        _onServer.value = emptyList()
    }

    /** Attaches a sleeping tab (if it is one). */
    fun wake(id: String) {
        val session = get(id) ?: return
        if (!session.asleep) return
        session.start()
        updateService()
    }

    private fun add(session: TermSession): TermSession {
        _list.value = _list.value + session
        _active.value = session.id
        session.start()
        updateService()
        return session
    }

    fun select(id: String) {
        _active.value = id
        wake(id)
    }

    /** Closes the tab (a server session stays alive there). */
    fun close(id: String) {
        val session = get(id) ?: return
        session.close()
        val rest = _list.value.filterNot { it.id == id }
        _list.value = rest
        if (_active.value == id) _active.value = rest.lastOrNull()?.id
        updateService()
    }

    fun closeAll() {
        _list.value.forEach { it.close() }
        _list.value = emptyList()
        _active.value = null
        updateService()
    }

    private fun updateService() {
        val intent = Intent(context, TerminalService::class.java)
        // Sleeping tabs have no connection to keep alive.
        val connected = _list.value.count { !it.asleep }
        if (connected == 0) {
            context.stopService(intent)
        } else {
            intent.putExtra(TerminalService.EXTRA_COUNT, connected)
            runCatching { ContextCompat.startForegroundService(context, intent) }
        }
    }
}
