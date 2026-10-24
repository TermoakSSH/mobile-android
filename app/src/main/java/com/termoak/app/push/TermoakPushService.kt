package com.termoak.app.push

import android.util.Log
import com.termoak.app.TermoakApp
import com.termoak.ffi.AccountStatus
import com.termoak.ffi.parsePushPayload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.unifiedpush.android.connector.FailedReason
import org.unifiedpush.android.connector.PushService
import org.unifiedpush.android.connector.data.PushEndpoint
import org.unifiedpush.android.connector.data.PushMessage

/**
 * What the UnifiedPush distributor hands the app: endpoints (registered on
 * the account's server), messages (already decrypted by the connector with
 * this phone's Web Push keys) and unregistrations. The instance is the
 * account id.
 *
 * A message only shows or withdraws a notification: nothing here connects
 * to the server (a security key request is answered once the user opens the
 * app and its events socket starts).
 */
class TermoakPushService : PushService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val app get() = application as TermoakApp

    override fun onNewEndpoint(endpoint: PushEndpoint, instance: String) {
        if (endpoint.temporary) Log.i("termoak", "push: temporary endpoint from the distributor's fallback")
        scope.launch {
            app.push.newEndpoint(instance, endpoint.url, endpoint.pubKeySet?.pubKey, endpoint.pubKeySet?.auth)
        }
    }

    override fun onMessage(message: PushMessage, instance: String) {
        if (!message.decrypted) {
            Log.w("termoak", "push: a message that could not be decrypted, ignored")
            return
        }
        val account = runCatching { app.core.accounts() }.getOrDefault(emptyList())
            .firstOrNull { it.id == instance && it.status == AccountStatus.ACTIVE }
            ?: return // Signed out meanwhile.
        val event = runCatching { parsePushPayload(message.content.decodeToString()) }.getOrNull() ?: return
        when (val d = PushRoutes.decide(event, account.id, account.userId)) {
            PushDecision.Ignore -> Unit
            is PushDecision.Cancel -> app.pushNotifier.cancel(d.id, d.tag)
            is PushDecision.Show -> app.pushNotifier.show(d.plan)
        }
    }

    override fun onRegistrationFailed(reason: FailedReason, instance: String) {
        Log.w("termoak", "push: registration failed: $reason")
        app.push.failed(instance, reason.name)
    }

    override fun onUnregistered(instance: String) {
        scope.launch { app.push.unregistered(instance) }
    }
}
