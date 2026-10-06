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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
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

    private val _split = MutableStateFlow(SplitState())
    /** Terminals side by side on wide windows (tablets, unfolded foldables). */
    val split: StateFlow<SplitState> = _split

    private val _notices = MutableSharedFlow<ShareNotice>(extraBufferCapacity = 16)
    /** Join and keyboard requests from the open terminals (owner), for the whole app. */
    val notices: SharedFlow<ShareNotice> = _notices

    fun get(id: String): TermSession? = _list.value.firstOrNull { it.id == id }

    /** The open tab of server session [sessionId] (attached, or a shared local terminal). */
    fun bySessionId(sessionId: String): TermSession? = _list.value.firstOrNull {
        (it is ServerTerminal && it.sessionId == sessionId) || (it is LocalTerminal && it.sharedId.value == sessionId)
    }

    /** SSH from the phone ([activate]: it becomes the terminal on screen). */
    fun openLocal(host: SshHost, activate: Boolean = true): TermSession =
        add(LocalTerminal(core, host.label, host.id, host.address), activate)

    /** An open terminal of [hostId] from the phone that is connected or connecting (to reuse it). */
    fun liveLocal(hostId: String): TermSession? = _list.value.firstOrNull {
        it is LocalTerminal && it.hostId == hostId && it.state.value !is TermState.Closed
    }

    /** New (persistent) session on the server. */
    fun openOnServer(host: SshHost): TermSession =
        add(ServerTerminal(core, host.label, host.id, null))

    /** Attaches to a session that already lives on the server ([owner]: yours, not shared with you). */
    fun attach(sessionId: String, label: String, hostId: String?, owner: Boolean = true): TermSession {
        bySessionId(sessionId)?.let {
            select(it.id)
            return it
        }
        return add(ServerTerminal(core, label, hostId, sessionId, owner))
    }

    /** Joins a shared session with an invitation link ([sessionId] from the link's details). */
    fun joinLink(link: LinkJoin, label: String, sessionId: String?): TermSession {
        sessionId?.let { id ->
            bySessionId(id)?.let { open ->
                // Already in (or a closed tab of it): use that tab.
                if (open.state.value is TermState.Closed) open.reconnect()
                select(open.id)
                return open
            }
        }
        return add(ServerTerminal(core, label, null, null, owner = false, link = link))
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
                .apply {
                    onShareNotice = { _notices.tryEmit(it) }
                    sleep()
                }
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

    private fun add(session: TermSession, activate: Boolean = true): TermSession {
        session.onShareNotice = { _notices.tryEmit(it) }
        _list.value = _list.value + session
        if (activate || _active.value == null) setActive(session.id)
        session.start()
        updateService()
        return session
    }

    fun select(id: String) {
        setActive(id)
        wake(id)
    }

    /**
     * The active terminal is the focused pane of the split view: picking one
     * that isn't in the split puts it in the place of the focused pane.
     */
    private fun setActive(id: String?) {
        val before = _active.value
        _active.value = id
        val sp = _split.value
        if (id == null || !sp.on || id in sp.panes) return
        val at = sp.panes.indexOf(before).takeIf { it >= 0 } ?: sp.panes.lastIndex
        _split.value = sp.copy(
            panes = sp.panes.toMutableList().also { it[at] = id },
            maximized = sp.maximized?.let { if (it == before) id else it },
        )
    }

    // ----- Split view -----

    /** Shows [ids] side by side (fewer than two: back to a single terminal). */
    fun setSplit(ids: List<String>) {
        val panes = ids.distinct().filter { get(it) != null }.take(SplitState.MAX_PANES)
        _split.value = if (panes.size >= 2) _split.value.copy(panes = panes, maximized = null) else SplitState()
        val active = _active.value
        if (panes.size >= 2 && (active == null || active !in panes)) _active.value = panes.first()
        panes.forEach { wake(it) }
    }

    /** Takes a pane out of the split (the terminal stays open). */
    fun removePane(id: String) {
        val sp = _split.value
        val panes = sp.panes - id
        _split.value = if (panes.size >= 2) sp.copy(panes = panes, maximized = sp.maximized.takeIf { it != id }) else SplitState()
        if (_active.value == id) _active.value = panes.firstOrNull() ?: id
    }

    /** Shows [id] alone for a while (`null`: back to the grid). */
    fun maximize(id: String?) {
        val sp = _split.value
        if (!sp.on) return
        _split.value = sp.copy(maximized = id?.takeIf { it in sp.panes })
        if (id != null) _active.value = id
    }

    fun setBroadcast(on: Boolean) {
        _split.value = _split.value.copy(broadcast = on && _split.value.on)
    }

    /** Closes the tab (a server session stays alive there). */
    fun close(id: String) {
        val session = get(id) ?: return
        session.close()
        val rest = _list.value.filterNot { it.id == id }
        _list.value = rest
        if (id in _split.value.panes) removePane(id)
        if (_active.value == id) _active.value = rest.lastOrNull()?.id
        updateService()
    }

    fun closeAll() {
        _list.value.forEach { it.close() }
        _list.value = emptyList()
        _active.value = null
        _split.value = SplitState()
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
