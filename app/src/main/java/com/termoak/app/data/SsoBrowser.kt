package com.termoak.app.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import com.termoak.ffi.SsoFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * A single sign-on in the browser (RFC 8252): the identity provider opens in
 * a Custom Tab (the system browser, with its cookies and password manager,
 * never a WebView) and comes back to `termoak://sso` (the manifest's intent
 * filter; MainActivity hands the URL here). The engine's PKCE makes a code
 * caught on its way useless without the verifier, which stays in memory.
 */
object SsoBrowser {
    /** What waits for the browser. */
    sealed interface Pending {
        /** A sign-in (`sso_start`): finished with `sso_complete`. */
        data class SignIn(val flow: SsoFlow) : Pending
        /** Linking an identity to a signed-in account (`POST /me/sso/link`): the server links it when it comes back. */
        data class Link(val accountId: String, val teamId: String, val state: String) : Pending
    }

    private val _pending = MutableStateFlow<Pending?>(null)
    val pending: StateFlow<Pending?> = _pending

    private val _callbacks = MutableSharedFlow<String>(replay = 1, extraBufferCapacity = 2)
    /** Callback URLs as they come back (the last one is replayed to a screen that starts after it). */
    val callbacks: SharedFlow<String> = _callbacks

    fun begin(p: Pending) {
        _callbacks.resetReplayCache()
        _pending.value = p
    }

    fun clear() {
        _pending.value = null
        _callbacks.resetReplayCache()
    }

    /** MainActivity got `termoak://sso…`. Returns whether it was one. */
    fun deliver(url: String?): Boolean {
        if (!SsoRules.isCallback(url)) return false
        _callbacks.tryEmit(url!!)
        return true
    }

    /** Opens [url] in a Custom Tab (or any browser). Returns `false` without a browser. */
    fun open(context: Context, url: String): Boolean = try {
        CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, url.toUri())
        true
    } catch (_: ActivityNotFoundException) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }
}
