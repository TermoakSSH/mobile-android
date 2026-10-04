package com.termoak.app.data

import android.util.Log
import com.termoak.app.AppLanguage
import com.termoak.app.R
import com.termoak.app.UiText
import com.termoak.app.toUiText
import com.termoak.app.uiText
import com.termoak.ffi.EventSubscription
import com.termoak.ffi.ServerEventListener
import com.termoak.ffi.TermoakCore
import com.termoak.ffi.TermoakException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Account state on the server: sign-in, sync, pending AI approvals and live
 * events (WebSocket).
 */
class Account(private val core: TermoakCore) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _loggedIn = MutableStateFlow<Boolean?>(null)
    /** `null` while still unknown. */
    val loggedIn: StateFlow<Boolean?> = _loggedIn
    private val _serverUrl = MutableStateFlow<String?>(null)
    val serverUrl: StateFlow<String?> = _serverUrl
    private val _user = MutableStateFlow<String?>(null)
    val user: StateFlow<String?> = _user

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing
    private val _lastSync = MutableStateFlow<Long?>(null)
    val lastSync: StateFlow<Long?> = _lastSync
    private val _syncError = MutableStateFlow<UiText?>(null)
    val syncError: StateFlow<UiText?> = _syncError

    private val _pendingApprovals = MutableStateFlow(0)
    val pendingApprovals: StateFlow<Int> = _pendingApprovals
    private val _online = MutableStateFlow(false)
    /** Receiving live events from the server. */
    val online: StateFlow<Boolean> = _online

    /** Something changed on the server (AI tasks, sessions): reload. */
    private val _changes = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val changes: SharedFlow<String> = _changes
    /**
     * AI events as they arrive (`{"type":"ai","task_id":…,"event":{…}}`),
     * live text included: the copilot draws them as they come.
     */
    private val _aiEvents = MutableSharedFlow<JSONObject>(extraBufferCapacity = 512)
    val aiEvents: SharedFlow<JSONObject> = _aiEvents
    /** The local vault changed after a sync. */
    private val _vaultChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    val vaultChanged: SharedFlow<Unit> = _vaultChanged

    private var events: EventSubscription? = null
    private var eventsJob: Job? = null

    fun refresh() {
        scope.launch {
            val logged = runCatching { core.isLoggedIn() }.getOrDefault(false)
            _loggedIn.value = logged
            _serverUrl.value = runCatching { core.serverUrl() }.getOrNull()
            _user.value = runCatching { core.serverUser() }.getOrNull()
            if (logged) {
                refreshApprovals()
                startEvents()
            } else {
                stopEvents()
            }
        }
    }

    suspend fun login(url: String, email: String, password: String, code: String?) {
        core.login(url, email, password, code)
        refresh()
        sync()
        // A language chosen in the app wins over the one in the account.
        AppLanguage.chosen()?.let { saveLocale(it) }
    }

    fun logout() {
        scope.launch {
            stopEvents()
            runCatching { core.logout() }
            _pendingApprovals.value = 0
            refresh()
        }
    }

    /**
     * Saves the app language ([tag], BCP 47) in the account, so the server
     * writes emails in it (`PATCH /api/v1/me` with `{"locale": …}`, see
     * core/docs/I18N.md). Called when the language is changed in Settings and after
     * signing in with a language chosen.
     */
    fun saveLocale(tag: String) {
        scope.launch {
            if (!runCatching { core.isLoggedIn() }.getOrDefault(false)) return@launch
            // The server keeps only the languages it has; `en-GB` becomes `en`.
            runCatching { core.setLocale(tag) }
                .onFailure { Log.w("termoak", "language not saved in the account: ${it.message}") }
        }
    }

    fun sync() {
        if (_syncing.value) return
        scope.launch {
            _syncing.value = true
            try {
                core.syncNow()
                _lastSync.value = System.currentTimeMillis()
                _syncError.value = null
                _vaultChanged.tryEmit(Unit)
            } catch (_: TermoakException.NotLoggedIn) {
                // No server: nothing to sync.
            } catch (e: TermoakException.SessionExpired) {
                _syncError.value = uiText(R.string.error_session_expired)
                refresh()
            } catch (e: TermoakException) {
                _syncError.value = e.toUiText(R.string.error_sync_failed)
            } finally {
                _syncing.value = false
            }
        }
    }

    suspend fun refreshApprovals() {
        _pendingApprovals.value = runCatching { core.listPendingApprovals().size }.getOrDefault(0)
    }

    private fun startEvents() {
        if (eventsJob?.isActive == true) return
        eventsJob = scope.launch {
            var backoff = 2_000L
            while (true) {
                val closed = kotlinx.coroutines.CompletableDeferred<Unit>()
                try {
                    events = core.subscribeEvents(object : ServerEventListener {
                        override fun onEvent(eventJson: String) {
                            scope.launch { handleEvent(eventJson) }
                        }

                        override fun onClosed(reason: String?) {
                            closed.complete(Unit)
                        }
                    })
                    _online.value = true
                    backoff = 2_000L
                    closed.await()
                } catch (e: TermoakException.NotLoggedIn) {
                    break
                } catch (e: TermoakException) {
                    Log.w("termoak", "events: ${e.message}")
                }
                _online.value = false
                events?.let { runCatching { it.unsubscribe() }; it.close() }
                events = null
                delay(backoff)
                backoff = (backoff * 2).coerceAtMost(60_000L)
            }
        }
    }

    private fun stopEvents() {
        eventsJob?.cancel()
        eventsJob = null
        events?.let { runCatching { it.unsubscribe() }; it.close() }
        events = null
        _online.value = false
    }

    private suspend fun handleEvent(json: String) {
        // hello (on connect, with the pending approvals), ai, session or
        // lagged (events were lost: reload everything).
        val event = runCatching { JSONObject(json) }.getOrNull() ?: return
        when (val type = event.optString("type")) {
            "hello" -> _pendingApprovals.value = event.optInt("pending_approvals", _pendingApprovals.value)
            "ai" -> {
                _aiEvents.tryEmit(event)
                // Live text arrives in chunks: no need to reload anything for each one.
                val kind = event.optJSONObject("event")?.optString("type")
                if (kind !in LiveAiEvents) {
                    refreshApprovals()
                    _changes.tryEmit(type)
                }
            }
            "lagged" -> {
                refreshApprovals()
                _changes.tryEmit(type)
            }
            "session" -> _changes.tryEmit(type)
        }
    }
}

/** AI events that are not stored (live text) and don't change the task state. */
private val LiveAiEvents = setOf("text", "reasoning", "reset", "usage")
