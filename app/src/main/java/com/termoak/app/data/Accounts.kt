package com.termoak.app.data

import android.content.Context
import android.util.Log
import com.termoak.app.AppLanguage
import com.termoak.app.R
import com.termoak.app.UiText
import com.termoak.app.localized
import com.termoak.app.toUiText
import com.termoak.app.uiText
import com.termoak.ffi.AccountHandle
import com.termoak.ffi.AccountInfo
import com.termoak.ffi.AccountStatus
import com.termoak.ffi.EventSubscription
import com.termoak.ffi.ItemFilter
import com.termoak.ffi.ServerChoice
import com.termoak.ffi.ServerEventListener
import com.termoak.ffi.SignOutReport
import com.termoak.ffi.SyncReport
import com.termoak.ffi.TermoakCore
import com.termoak.ffi.TermoakException
import com.termoak.ffi.VaultInfo
import com.termoak.ffi.VaultKind
import com.termoak.ffi.VaultRole
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
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File

/**
 * An account that signed in (or up) on a server that requires a verified
 * email and has not verified it yet: the app shows the "check your email"
 * screen to enter the six-digit code. [sentAt] is when an email was just
 * sent (sign-in, sign-up or resend), to hold back the "resend" button for a
 * minute; `null` if unknown.
 */
data class PendingVerification(val accountId: String, val server: String, val email: String, val sentAt: Long? = null)

/** How long the server makes you wait between verification emails. */
const val RESEND_INTERVAL_MS = 60_000L

/** Vault filter value for the items of This device. */
const val DEVICE_VAULT = "device"

/** What the Vault shows (the account switcher). */
sealed interface AccountView {
    /** Every account together, plus This device. */
    data object All : AccountView
    /** Only the items of This device. */
    data object Device : AccountView
    /** One account (plus This device). */
    data class One(val id: String) : AccountView
}

/**
 * The accounts signed in on this device (one per server and user, core 0.4):
 * sign-in and sign-up, the view of the Vault, each account's sync and live
 * events (WebSocket), pending AI approvals and the notices of the syncs.
 */
class Accounts(private val context: Context, private val core: TermoakCore, private val prefs: Prefs) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _list = MutableStateFlow<List<AccountInfo>>(emptyList())
    /** Accounts on this device, in order. */
    val list: StateFlow<List<AccountInfo>> = _list
    private val _current = MutableStateFlow<AccountInfo?>(null)
    /**
     * The current account (the one of the view; in "All accounts", the first
     * active one): sessions, AI and the legacy calls of the core use it.
     */
    val current: StateFlow<AccountInfo?> = _current
    private val _view = MutableStateFlow<AccountView>(AccountView.All)
    val view: StateFlow<AccountView> = _view
    private val _vaultFilter = MutableStateFlow(prefs.vaultFilter)
    /** Vault shown in the Vault (`null`: all of them; [DEVICE_VAULT]: This device). */
    val vaultFilter: StateFlow<String?> = _vaultFilter
    private val _vaults = MutableStateFlow<List<VaultInfo>>(emptyList())
    /** Vaults of every account, as of their last sync. */
    val vaults: StateFlow<List<VaultInfo>> = _vaults

    private val _loggedIn = MutableStateFlow<Boolean?>(null)
    /** `null` while still unknown; `true` when some account is signed in (and verified). */
    val loggedIn: StateFlow<Boolean?> = _loggedIn
    private val _verification = MutableStateFlow<PendingVerification?>(null)
    /** An account waiting for the code from its email. */
    val verification: StateFlow<PendingVerification?> = _verification

    private val _syncingIds = MutableStateFlow<Set<String>>(emptySet())
    /** Accounts syncing right now. */
    val syncingIds: StateFlow<Set<String>> = _syncingIds
    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing
    private val _syncError = MutableStateFlow<UiText?>(null)
    val syncError: StateFlow<UiText?> = _syncError

    private val approvals = mutableMapOf<String, Int>()
    private val _pendingApprovals = MutableStateFlow(0)
    /** AI actions waiting for your permission, in every account. */
    val pendingApprovals: StateFlow<Int> = _pendingApprovals
    private val _onlineIds = MutableStateFlow<Set<String>>(emptySet())
    /** Accounts receiving live events. */
    val onlineIds: StateFlow<Set<String>> = _onlineIds
    private val _online = MutableStateFlow(false)
    /** Receiving live events from some server. */
    val online: StateFlow<Boolean> = _online

    /** Something changed on a server (AI tasks, sessions): reload. */
    private val _changes = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val changes: SharedFlow<String> = _changes
    /** AI events as they arrive, with `account_id` (the copilot draws the live text). */
    private val _aiEvents = MutableSharedFlow<JSONObject>(extraBufferCapacity = 512)
    val aiEvents: SharedFlow<JSONObject> = _aiEvents
    /** Session notices (the `notice` object of a `session` event, with `account_id` added). */
    private val _sessionNotices = MutableSharedFlow<JSONObject>(extraBufferCapacity = 16)
    val sessionNotices: SharedFlow<JSONObject> = _sessionNotices
    /** The items on this device changed (a sync, a sign-out, another view): reload. */
    private val _itemsChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    val itemsChanged: SharedFlow<Unit> = _itemsChanged
    /** Notices of the syncs (vaults shared with you or lost, changes discarded). */
    private val _notices = MutableSharedFlow<UiText>(extraBufferCapacity = 8)
    val notices: SharedFlow<UiText> = _notices

    private val _uploadOffer = MutableStateFlow<String?>(null)
    /**
     * The first account was just added and This device has items: offer
     * "Upload N items from this device to your Personal vault?" (its id).
     */
    val uploadOffer: StateFlow<String?> = _uploadOffer
    private val _layoutNotice = MutableStateFlow(false)
    /** The data was just moved to one store per account (core 0.4): say so once. */
    val layoutNotice: StateFlow<Boolean> = _layoutNotice

    private val handles = mutableMapOf<String, AccountHandle>()
    /** Unverified accounts whose code screen was left for later (not opened again by itself). */
    private val postponed = mutableSetOf<String>()
    private val eventJobs = mutableMapOf<String, Job>()
    private val subscriptions = mutableMapOf<String, EventSubscription>()

    // ----- State -----

    fun refresh() {
        scope.launch { refreshNow() }
    }

    /** Reads the accounts, the view and the vaults again, and starts or stops the events. */
    fun refreshNow() {
        val accounts = runCatching { core.accounts() }.getOrDefault(emptyList())
        _list.value = accounts
        _current.value = accounts.firstOrNull { it.isCurrent }
        val coreView = runCatching { core.accountView() }.getOrNull()
        _view.value = when {
            prefs.deviceOnlyView -> AccountView.Device
            coreView != null && accounts.any { it.id == coreView } -> AccountView.One(coreView)
            else -> AccountView.All
        }
        refreshVaults()
        val active = accounts.filter { it.status == AccountStatus.ACTIVE }
        _loggedIn.value = active.isNotEmpty()
        // The current account still has to verify its email (the app was
        // closed on the code screen, or signed up elsewhere).
        if (_verification.value == null) {
            accounts.firstOrNull { it.isCurrent && it.status == AccountStatus.UNVERIFIED && it.id !in postponed }?.let {
                _verification.value = PendingVerification(it.id, it.serverUrl, it.email)
            }
        } else if (accounts.none { it.id == _verification.value?.accountId && it.status == AccountStatus.UNVERIFIED }) {
            _verification.value = null
        }
        handles.keys.retainAll(accounts.map { it.id }.toSet())
        val activeIds = active.map { it.id }.toSet()
        (eventJobs.keys - activeIds).forEach { stopEvents(it) }
        approvals.keys.retainAll(activeIds)
        _pendingApprovals.value = approvals.values.sum()
        activeIds.forEach { startEvents(it) }
        checkLayoutNotice(accounts)
    }

    private fun refreshVaults() {
        _vaults.value = runCatching { core.vaults(ItemFilter(accountIds = null, vaultIds = null, includeDevice = false)) }
            .getOrDefault(emptyList())
        // A vault that is gone (or an account signed out) can't stay chosen.
        val f = _vaultFilter.value
        if (f != null && f != DEVICE_VAULT && _vaults.value.none { it.id == f && inView(it.accountId) }) setVaultFilter(null)
    }

    /** The data moved to one store per account in this start: show the notice once. */
    private fun checkLayoutNotice(accounts: List<AccountInfo>) {
        if (prefs.layoutNoticeShown) return
        val backup = File(File(context.filesDir, "termoak"), "termoak.db.pre-accounts")
        if (!backup.exists()) return
        // Without a server nothing moved: no notice (now or after a later sign-in).
        if (accounts.isEmpty()) prefs.layoutNoticeShown = true else _layoutNotice.value = true
    }

    fun dismissLayoutNotice() {
        prefs.layoutNoticeShown = true
        _layoutNotice.value = false
    }

    fun dismissUploadOffer() {
        _uploadOffer.value = null
    }

    fun account(id: String?): AccountInfo? = id?.let { i -> _list.value.firstOrNull { it.id == i } }

    /** The handle of one account (its server: sessions, AI, vaults...). */
    fun handle(id: String): AccountHandle? =
        handles[id] ?: runCatching { core.account(id) }.getOrNull()?.also { handles[id] = it }

    /** Handle of [id], or of the current account. */
    fun handleOrCurrent(id: String?): AccountHandle? = (id ?: _current.value?.id)?.let { handle(it) }

    /** Accounts that are signed in. */
    fun active(): List<AccountInfo> = _list.value.filter { it.status == AccountStatus.ACTIVE }

    /** Account chosen for the AI section (`null`: the current one). */
    var aiAccountId: String? = null

    /** The account the AI section works with: the one chosen there, the current one, or the first signed in. */
    fun aiAccount(): AccountInfo? =
        account(aiAccountId)?.takeIf { it.status == AccountStatus.ACTIVE }
            ?: _current.value?.takeIf { it.status == AccountStatus.ACTIVE }
            ?: active().firstOrNull()

    // ----- View -----

    /** The accounts the Vault shows (`null`: all of them). */
    fun viewAccounts(): List<String>? = when (val v = _view.value) {
        AccountView.All -> null
        AccountView.Device -> emptyList()
        is AccountView.One -> listOf(v.id)
    }

    private fun inView(accountId: String): Boolean = viewAccounts()?.contains(accountId) ?: true

    /** Vaults of the accounts in the view. */
    fun vaultsInView(): List<VaultInfo> = _vaults.value.filter { inView(it.accountId) }

    fun setView(view: AccountView) {
        when (view) {
            AccountView.All -> {
                prefs.deviceOnlyView = false
                runCatching { core.setAccountView(null) }
            }
            AccountView.Device -> prefs.deviceOnlyView = true
            is AccountView.One -> {
                prefs.deviceOnlyView = false
                runCatching { core.setAccountView(view.id) }
            }
        }
        setVaultFilter(null)
        refreshNow()
        _itemsChanged.tryEmit(Unit)
    }

    fun setVaultFilter(vault: String?) {
        prefs.vaultFilter = vault
        _vaultFilter.value = vault
        _itemsChanged.tryEmit(Unit)
    }

    /** Which items the lists of the Vault show: the view and the vault filter. */
    fun filter(): ItemFilter {
        val accounts = viewAccounts()
        val vault = _vaultFilter.value
        return when {
            accounts?.isEmpty() == true || vault == DEVICE_VAULT -> ItemFilter(accountIds = emptyList(), vaultIds = null, includeDevice = true)
            vault != null -> ItemFilter(accountIds = accounts, vaultIds = listOf(vault), includeDevice = false)
            else -> ItemFilter(accountIds = accounts, vaultIds = null, includeDevice = true)
        }
    }

    /** Items of one place: an account (all its vaults) or, with `null`, This device. */
    fun scopeFilter(accountId: String?): ItemFilter =
        if (accountId == null) ItemFilter(accountIds = emptyList(), vaultIds = null, includeDevice = true)
        else ItemFilter(accountIds = listOf(accountId), vaultIds = null, includeDevice = true)

    /** The personal vault of an account (if it has synced, and its server has vaults). */
    fun personalVault(accountId: String): VaultInfo? =
        _vaults.value.firstOrNull { it.accountId == accountId && it.kind == VaultKind.PERSONAL }

    /**
     * Where a new item goes by default: what the Vault shows (This device, the
     * vault chosen in the filter), otherwise the last place used, otherwise
     * the personal vault of the account shown (or the current one).
     */
    fun defaultPlace(): Place {
        val f = _vaultFilter.value
        if (_view.value == AccountView.Device || f == DEVICE_VAULT || _list.value.isEmpty()) return Place.DEVICE
        if (f != null) _vaults.value.firstOrNull { it.id == f && it.role.canEdit() }?.let { return Place(it.accountId, it.id) }
        Place.parse(prefs.lastTarget)?.let { p ->
            val ok = if (p.device) true else account(p.account) != null && inView(p.account!!) &&
                (p.vault == null || _vaults.value.any { it.id == p.vault && it.accountId == p.account && it.role.canEdit() })
            if (ok) return p
        }
        val id = viewAccounts()?.firstOrNull() ?: _current.value?.id ?: return Place.DEVICE
        return Place(id, personalVault(id)?.id)
    }

    /** Remembers where the last new item went. */
    fun rememberPlace(place: Place) {
        prefs.lastTarget = place.key()
    }

    // ----- Signing in -----

    /**
     * Signs in to [server] (a new account, or the same one again) and makes
     * it current. If the email must be verified first, [verification] is set
     * and the app shows the code screen. Fails with `TotpRequired` when the
     * account has two-step verification and no [totp] was given.
     */
    suspend fun signIn(server: ServerChoice, email: String, password: String, totp: String?): AccountInfo {
        val before = _list.value.map { it.id }.toSet()
        val info = core.signIn(server, email.trim(), password, totp?.trim()?.ifEmpty { null })
        prefs.lastEmail = email.trim()
        added(info, before)
        return info
    }

    /** Creates an account (official server, or your own with open registration or an invitation). */
    suspend fun signUp(server: ServerChoice, email: String, name: String, password: String, invite: String?): AccountInfo {
        val before = _list.value.map { it.id }.toSet()
        val info = core.signUp(server, email.trim(), name.trim(), password, invite?.trim()?.ifEmpty { null })
        prefs.lastEmail = email.trim()
        added(info, before)
        return info
    }

    private suspend fun added(info: AccountInfo, before: Set<String>) {
        // The new account is shown (the core made it current).
        prefs.deviceOnlyView = false
        setVaultFilter(null)
        refreshNow()
        if (info.status == AccountStatus.UNVERIFIED) {
            postponed.remove(info.id)
            _verification.value = PendingVerification(info.id, info.serverUrl, info.email, System.currentTimeMillis())
            return
        }
        signedIn(info.id, first = before.isEmpty())
    }

    /** A signed-in account: first sync, the app language, and the upload offer for the first one. */
    private suspend fun signedIn(id: String, first: Boolean) {
        AppLanguage.chosen()?.let { saveLocale(it, id) }
        syncOne(id)
        if (first && hasDeviceItems()) _uploadOffer.value = id
    }

    private fun hasDeviceItems(): Boolean = runCatching {
        val device = scopeFilter(null)
        core.listHosts(device).isNotEmpty() || core.listKeys(device).isNotEmpty() ||
            core.listIdentities(device).isNotEmpty() || core.listSnippets(device).isNotEmpty()
    }.getOrDefault(false)

    /**
     * Verifies the email of the pending account with the six-digit code
     * (spaces and dashes are ignored). Fails with `Invalid` for a wrong or
     * expired code and with `TotpRequired`/`TotpInvalid` if the account has 2FA.
     */
    suspend fun verifyCode(code: String, totpCode: String?) {
        val pending = _verification.value ?: return
        val first = _list.value.none { it.id != pending.accountId }
        val info = core.verifyAccount(pending.accountId, code.filter { it.isDigit() }, totpCode?.trim()?.ifEmpty { null })
        _verification.value = null
        refreshNow()
        signedIn(info.id, first)
    }

    /** Emails a new code (once a minute at most: fails with `Server` if asked too often). */
    suspend fun resendCode() {
        val pending = _verification.value ?: return
        core.resendAccountCode(pending.accountId)
        _verification.value = pending.copy(sentAt = System.currentTimeMillis())
    }

    /** Opens the code screen for an unverified account (Manage accounts). */
    fun startVerification(account: AccountInfo) {
        postponed.remove(account.id)
        _verification.value = PendingVerification(account.id, account.serverUrl, account.email)
    }

    /** Leaves the code screen: the unverified account is removed, to use another email. */
    fun useDifferentEmail() {
        val pending = _verification.value ?: return
        postponed += pending.accountId
        _verification.value = null
        scope.launch {
            runCatching { core.signOutAccount(pending.accountId, true) }
            refreshNow()
            _itemsChanged.tryEmit(Unit)
        }
    }

    /** Leaves the code screen for now (the account stays unverified in Manage accounts). */
    fun postponeVerification() {
        _verification.value?.let { postponed += it.accountId }
        _verification.value = null
    }

    /**
     * Signs out of one account and deletes its data on this device. With
     * changes not uploaded yet and `discard = false` nothing happens:
     * [SignOutReport.signedOut] is `false` and the app asks "Sync now /
     * Discard". The caller closes that account's terminals.
     */
    suspend fun signOut(id: String, discard: Boolean): SignOutReport {
        // TODO(push): unregister the push token on that server first (see registerPush).
        val report = core.signOutAccount(id, discard)
        if (report.signedOut) {
            stopEvents(id)
            handles.remove(id)?.close()
            approvals.remove(id)
            if (_verification.value?.accountId == id) _verification.value = null
            refreshNow()
            _itemsChanged.tryEmit(Unit)
        }
        return report
    }

    /** Changes not uploaded yet of an account. */
    fun unsynced(id: String): Long = runCatching { core.unsyncedChanges(id).total.toLong() }.getOrDefault(0L)

    /**
     * Saves the app language ([tag], BCP 47) in every account (or only in
     * [accountId]), so the servers write emails in it (`PATCH /api/v1/me`).
     */
    fun saveLocale(tag: String, accountId: String? = null) {
        val body = JSONObject().put("locale", tag).toString()
        val ids = accountId?.let { listOf(it) } ?: active().map { it.id }
        scope.launch {
            ids.forEach { id ->
                runCatching { handle(id)?.apiPatch("/api/v1/me", body) }
                    .onFailure { Log.w("termoak", "language not saved in the account: ${it.message}") }
            }
        }
    }

    // ----- Sync -----

    /** Syncs one account, or every signed-in one. */
    fun sync(accountId: String? = null) {
        val ids = accountId?.let { listOf(it) } ?: active().map { it.id }
        ids.forEach { id -> scope.launch { syncOne(id) } }
    }

    private suspend fun syncOne(id: String) {
        if (id in _syncingIds.value || _verification.value?.accountId == id) return
        val handle = handle(id) ?: return
        setSyncing(id, true)
        try {
            val report = handle.syncNow()
            _syncError.value = null
            notify(report)
        } catch (_: TermoakException.NotLoggedIn) {
            // Signed out meanwhile: nothing to sync.
        } catch (e: TermoakException.SessionExpired) {
            _syncError.value = uiText(R.string.accounts_session_expired, account(id)?.email ?: "")
        } catch (e: TermoakException.EmailNotVerified) {
            _syncError.value = uiText(R.string.error_email_not_verified)
            account(id)?.let { if (it.isCurrent) startVerification(it) }
        } catch (e: TermoakException) {
            _syncError.value = e.toUiText(R.string.error_sync_failed)
        } finally {
            setSyncing(id, false)
            refreshNow()
            _itemsChanged.tryEmit(Unit)
        }
    }

    /** "Sync now" of the sign-out dialog: one round, errors to the caller. */
    suspend fun syncNow(id: String): SyncReport {
        val handle = handle(id) ?: throw TermoakException.NotLoggedIn("")
        setSyncing(id, true)
        try {
            return handle.syncNow().also { notify(it) }
        } finally {
            setSyncing(id, false)
            refreshNow()
            _itemsChanged.tryEmit(Unit)
        }
    }

    private fun setSyncing(id: String, on: Boolean) {
        _syncingIds.value = if (on) _syncingIds.value + id else _syncingIds.value - id
        _syncing.value = _syncingIds.value.isNotEmpty()
    }

    /** Notices of a sync: vaults shared with you or lost, changes discarded. */
    private fun notify(r: SyncReport) {
        r.vaultsAdded.forEach { _notices.tryEmit(uiText(R.string.sync_vault_added, it.name)) }
        r.vaultsLost.forEach { lost ->
            val discarded = r.discarded.firstOrNull { it.vaultId == lost.id }?.count?.toInt() ?: 0
            _notices.tryEmit(
                if (discarded > 0) UiText.Plural(R.plurals.sync_vault_lost_discarded, discarded, listOf(lost.name, discarded))
                else uiText(R.string.sync_vault_lost, lost.name),
            )
        }
        r.discarded.filter { d -> r.vaultsLost.none { it.id == d.vaultId } }.forEach { d ->
            val n = d.count.toInt()
            _notices.tryEmit(UiText.Plural(R.plurals.sync_discarded, n, listOf(n, vaultName(d.vaultName))))
        }
    }

    private fun vaultName(name: String): String = name.ifBlank { context.localized().getString(R.string.vault_personal) }

    suspend fun refreshApprovals() {
        active().forEach { a ->
            approvals[a.id] = runCatching { handle(a.id)?.listPendingApprovals()?.size ?: 0 }.getOrDefault(approvals[a.id] ?: 0)
        }
        _pendingApprovals.value = approvals.values.sum()
    }

    // ----- Events -----

    /**
     * Push notifications (join and keyboard requests, sessions shared with
     * you... while the app is closed).
     *
     * TODO(push): Firebase Cloud Messaging is not set up in this app yet (no
     * Firebase dependency nor google-services.json: it needs the owner's
     * Firebase project). Once it is, register the token with every account
     * (`handle.apiPost("/api/v1/push/register", …)`, again on every new
     * token), unregister it on sign-out, and route a tap by the `user_id` and
     * `server` of the push data. Until then, the notices arrive through the
     * events WebSocket of each account while the app runs ([sessionNotices]).
     */
    private fun startEvents(id: String) {
        if (eventJobs[id]?.isActive == true) return
        eventJobs[id] = scope.launch {
            var backoff = 2_000L
            while (true) {
                val closed = CompletableDeferred<Unit>()
                val handle = handle(id) ?: break
                try {
                    subscriptions[id] = handle.subscribeEvents(object : ServerEventListener {
                        override fun onEvent(eventJson: String) {
                            scope.launch { handleEvent(id, eventJson) }
                        }

                        override fun onClosed(reason: String?) {
                            closed.complete(Unit)
                        }
                    })
                    setOnline(id, true)
                    backoff = 2_000L
                    refreshApprovals()
                    closed.await()
                } catch (_: TermoakException.NotLoggedIn) {
                    break
                } catch (_: TermoakException.SessionExpired) {
                    refreshNow()
                    break
                } catch (e: TermoakException.EmailNotVerified) {
                    account(id)?.let { if (it.isCurrent) startVerification(it) }
                    break
                } catch (e: TermoakException) {
                    Log.w("termoak", "events: ${e.message}")
                }
                setOnline(id, false)
                subscriptions.remove(id)?.let { runCatching { it.unsubscribe() }; it.close() }
                delay(backoff)
                backoff = (backoff * 2).coerceAtMost(60_000L)
            }
            setOnline(id, false)
        }
    }

    private fun stopEvents(id: String) {
        eventJobs.remove(id)?.cancel()
        subscriptions.remove(id)?.let { runCatching { it.unsubscribe() }; it.close() }
        setOnline(id, false)
    }

    private fun setOnline(id: String, on: Boolean) {
        _onlineIds.value = if (on) _onlineIds.value + id else _onlineIds.value - id
        _online.value = _onlineIds.value.isNotEmpty()
    }

    private suspend fun handleEvent(accountId: String, json: String) {
        // hello (on connect, with the pending approvals), ai, session, vault
        // or lagged (events were lost: reload everything).
        val event = runCatching { JSONObject(json) }.getOrNull() ?: return
        if (!event.has("account_id")) event.put("account_id", accountId)
        when (val type = event.optString("type")) {
            "hello" -> {
                approvals[accountId] = event.optInt("pending_approvals", approvals[accountId] ?: 0)
                _pendingApprovals.value = approvals.values.sum()
            }
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
                syncOne(accountId)
            }
            "session" -> {
                event.optJSONObject("notice")?.let {
                    it.put("account_id", accountId)
                    _sessionNotices.tryEmit(it)
                }
                _changes.tryEmit(type)
            }
            // A vault changed, was shared with you or lost: sync that account.
            "vault" -> syncOne(accountId)
        }
    }

    companion object {
        /** Name of a vault for people ("Personal" is translated). */
        fun vaultLabel(context: Context, v: VaultInfo): String =
            if (v.kind == VaultKind.PERSONAL) context.localized().getString(R.string.vault_personal) else v.name
    }
}

/** The role lets you change the vault's items. */
fun VaultRole.canEdit(): Boolean = this == VaultRole.EDITOR || this == VaultRole.MANAGER

/** AI events that are not stored (live text) and don't change the task state. */
private val LiveAiEvents = setOf("text", "reasoning", "reset", "usage")
