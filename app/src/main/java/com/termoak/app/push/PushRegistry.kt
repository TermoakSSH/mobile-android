package com.termoak.app.push

import android.content.Context
import android.util.Log
import com.termoak.app.data.Accounts
import com.termoak.ffi.PushPreferences
import com.termoak.ffi.PushStatus
import com.termoak.ffi.TermoakException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.unifiedpush.android.connector.UnifiedPush

/**
 * Push without Google: UnifiedPush (docs-next/PUSH.md). The user's
 * distributor (the ntfy app, NextPush...) gives one endpoint per account
 * (the instance is the account id) with its Web Push keys; each account's
 * server gets its own (`registerWebPush`) and encrypts every notification for
 * this phone. Without a distributor nothing changes: the app notifies while
 * it runs (events sockets).
 */
class PushRegistry(private val context: Context, private val accounts: Accounts) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val sp = context.getSharedPreferences("push", Context.MODE_PRIVATE)

    private val _states = MutableStateFlow<Map<String, AccountPush>>(emptyMap())
    /** Each account's state (Settings → Notifications). */
    val states: StateFlow<Map<String, AccountPush>> = _states

    private fun set(id: String, s: AccountPush) {
        _states.value = _states.value + (id to s)
    }

    /** The user turned notifications off (no automatic distributor then). */
    var turnedOff: Boolean
        get() = sp.getBoolean("off", false)
        private set(v) = sp.edit().putBoolean("off", v).apply()

    /** Installed distributors (package names, without this app). */
    fun installed(): List<String> = runCatching {
        PushSetup.external(UnifiedPush.getDistributors(context), context.packageName)
    }.getOrDefault(emptyList())

    /** A distributor's name for people. */
    fun label(pkg: String): String = runCatching {
        val pm = context.packageManager
        @Suppress("DEPRECATION") pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    /** The distributor in use, if any. */
    fun current(): String? = PushSetup.distributor(installed(), runCatching { UnifiedPush.getSavedDistributor(context) }.getOrNull(), turnedOff)

    /** Uses [pkg] from now on (every account registers again with it). */
    fun choose(pkg: String) {
        turnedOff = false
        runCatching { UnifiedPush.saveDistributor(context, pkg) }
        registerAll()
    }

    /** Turns push off: the distributor forgets the registrations and the servers stop sending. */
    fun turnOff() {
        turnedOff = true
        val ids = accounts.active().map { it.id }
        runCatching { UnifiedPush.removeDistributor(context) }
        scope.launch {
            ids.forEach { id ->
                runCatching { accounts.handle(id)?.unregisterPushToken() }
                set(id, AccountPush.Off)
            }
        }
    }

    /** Registers every signed-in account (with the distributor in use). */
    fun registerAll() {
        accounts.active().forEach { register(it.id) }
    }

    /** Registers one account: only when its server can send Web Push. */
    fun register(accountId: String) {
        val distributor = current()
        if (distributor == null) {
            set(accountId, AccountPush.Off)
            return
        }
        scope.launch {
            val handle = accounts.handle(accountId) ?: return@launch
            val status: PushStatus = try {
                handle.pushStatus()
            } catch (_: TermoakException.NotFound) {
                set(accountId, AccountPush.ServerCannot)
                return@launch
            } catch (e: TermoakException) {
                set(accountId, AccountPush.Failed(e.message.orEmpty()))
                return@launch
            }
            if (!status.serverWebpush) {
                set(accountId, AccountPush.ServerCannot)
                return@launch
            }
            if (UnifiedPush.getSavedDistributor(context) != distributor) runCatching { UnifiedPush.saveDistributor(context, distributor) }
            val info = accounts.account(accountId)
            try {
                UnifiedPush.register(
                    context, accountId,
                    PushSetup.label(info?.email.orEmpty(), info?.serverUrl.orEmpty()),
                    PushSetup.vapid(status.vapidPublicKey),
                )
                if (_states.value[accountId] != AccountPush.Registered || !status.registered) set(accountId, AccountPush.Registering)
                if (status.registered && status.platform == "webpush") set(accountId, AccountPush.Registered)
            } catch (e: Exception) {
                Log.w("termoak", "push: registration not sent: ${e.message}")
                set(accountId, AccountPush.Failed(e.message.orEmpty()))
            }
        }
    }

    /** Signing out of [accountId]: its server stops sending and the distributor forgets it. */
    suspend fun unregister(accountId: String) {
        runCatching { accounts.handle(accountId)?.unregisterPushToken() }
        runCatching { UnifiedPush.unregister(context, accountId) }
        _states.value = _states.value - accountId
    }

    // ----- From the distributor (TermoakPushService) -----

    suspend fun newEndpoint(accountId: String, url: String, p256dh: String?, auth: String?) {
        if (p256dh == null || auth == null) {
            // A distributor without Web Push keys (UnifiedPush before 3): the server can't encrypt for it.
            set(accountId, AccountPush.Failed("the distributor gave no Web Push keys: update it"))
            return
        }
        val handle = accounts.handle(accountId) ?: return
        try {
            val ok = handle.registerWebPush(url, p256dh, auth)
            set(accountId, if (ok) AccountPush.Registered else AccountPush.ServerCannot)
        } catch (e: TermoakException) {
            Log.w("termoak", "push: endpoint not registered: ${e.message}")
            set(accountId, AccountPush.Failed(e.message.orEmpty()))
        }
    }

    suspend fun unregistered(accountId: String) {
        runCatching { accounts.handle(accountId)?.unregisterPushToken() }
        set(accountId, AccountPush.Off)
    }

    fun failed(accountId: String, reason: String) = set(accountId, AccountPush.Failed(reason))

    // ----- Settings -----

    suspend fun status(accountId: String): PushStatus? = runCatching { accounts.handle(accountId)?.pushStatus() }.getOrNull()

    suspend fun savePreferences(accountId: String, prefs: PushPreferences): PushPreferences? =
        accounts.handle(accountId)?.setPushPreferences(prefs)

    suspend fun sendTest(accountId: String) {
        accounts.handle(accountId)?.sendTestPush()
    }
}
