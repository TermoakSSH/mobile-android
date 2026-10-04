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
 * An account signed in on a server that requires a verified email and has
 * not verified it yet: the app shows the "check your email" screen to enter
 * the six-digit code. [sentAt] is when an email was just sent (sign-in or
 * resend), to hold back the "resend" button for a minute; `null` if unknown.
 */
data class PendingVerification(val server: String, val email: String, val sentAt: Long? = null)

/** How long the server makes you wait between verification emails. */
const val RESEND_INTERVAL_MS = 60_000L

/**
 * Account state on the server: sign-in, sync, pending AI approvals and live
 * events (WebSocket).
 */
class Account(private val core: TermoakCore) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _loggedIn = MutableStateFlow<Boolean?>(null)
    /**
     * `null` while still unknown. `false` too while the email is waiting for
     * verification ([verification]): the server only lets that account
     * manage itself.
     */
    val loggedIn: StateFlow<Boolean?> = _loggedIn
    private val _verification = MutableStateFlow<PendingVerification?>(null)
    /** Signed in, but the email must be verified with the code first. */
    val verification: StateFlow<PendingVerification?> = _verification
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
        scope.launch { refreshNow() }
    }

    private suspend fun refreshNow() {
        val logged = runCatching { core.isLoggedIn() }.getOrDefault(false)
        if (!logged) _verification.value = null
        _loggedIn.value = logged && _verification.value == null
        _serverUrl.value = runCatching { core.serverUrl() }.getOrNull()
        _user.value = runCatching { core.serverUser() }.getOrNull()
        if (logged && _verification.value == null) {
            // The account may still have to verify its email (signed up
            // elsewhere, or the app closed on the code screen). Offline,
            // assume it doesn't: the server will say so later.
            if (runCatching { core.verificationRequired() }.getOrDefault(false)) {
                markUnverified()
                return
            }
            refreshApprovals()
            startEvents()
        } else {
            stopEvents()
        }
    }

    /**
     * Signs in. Returns `false` if the account must verify its email first:
     * [verification] is then set and the app shows the code screen.
     */
    suspend fun login(url: String, email: String, password: String, code: String?): Boolean {
        core.login(url, email, password, code)
        if (runCatching { core.verificationRequired() }.getOrDefault(false)) {
            // Signing in emails a new code if the last one can't be used.
            _verification.value = PendingVerification(url, core.serverUser() ?: email, System.currentTimeMillis())
            refreshNow()
            return false
        }
        signedIn()
        return true
    }

    /**
     * Verifies the email with the six-digit code (spaces and dashes are
     * ignored) and signs in. Fails with `Invalid` for a wrong or expired
     * code and with `TotpRequired`/`TotpInvalid` if the account has 2FA.
     */
    suspend fun verifyCode(code: String, totpCode: String?) {
        val pending = _verification.value ?: return
        core.verifyCode(pending.server, pending.email, code.filter { it.isDigit() }, totpCode?.trim()?.ifEmpty { null })
        _verification.value = null
        signedIn()
    }

    /** Emails a new code (once a minute at most: fails with `Server` if asked too often). */
    suspend fun resendCode() {
        val pending = _verification.value ?: return
        core.resendCode(pending.server, pending.email)
        _verification.value = pending.copy(sentAt = System.currentTimeMillis())
    }

    /** Leaves the code screen: signs out to sign in with another account. */
    fun useDifferentEmail() {
        _verification.value = null
        logout()
    }

    /** The server answered `email_not_verified`: show the code screen. */
    private suspend fun markUnverified() {
        if (_verification.value == null) {
            val url = runCatching { core.serverUrl() }.getOrNull()
            val email = runCatching { core.serverUser() }.getOrNull()
            if (url != null && email != null) _verification.value = PendingVerification(url, email)
        }
        _loggedIn.value = false
        stopEvents()
        _pendingApprovals.value = 0
    }

    private suspend fun signedIn() {
        refreshNow()
        sync()
        // A language chosen in the app wins over the one in the account.
        AppLanguage.chosen()?.let { saveLocale(it) }
    }

    fun logout() {
        scope.launch {
            stopEvents()
            runCatching { core.logout() }
            _verification.value = null
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
        // Until the email is verified, the server refuses to sync.
        if (_syncing.value || _verification.value != null) return
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
            } catch (e: TermoakException.EmailNotVerified) {
                _syncError.value = uiText(R.string.error_email_not_verified)
                markUnverified()
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
                } catch (e: TermoakException.EmailNotVerified) {
                    markUnverified()
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
