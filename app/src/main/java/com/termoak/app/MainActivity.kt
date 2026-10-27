package com.termoak.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.KeyboardShortcutGroup
import android.view.Menu
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.termoak.app.data.IncomingFiles
import com.termoak.app.data.InviteLinkRef
import com.termoak.app.data.JoinLinkRef
import com.termoak.app.data.QuickAction
import com.termoak.app.term.HardwareKeys
import com.termoak.app.term.layout
import com.termoak.app.term.toKeyPress
import com.termoak.app.ui.AppLockScreen
import com.termoak.app.ui.AppRoot
import com.termoak.app.ui.KeyShortcuts
import com.termoak.app.ui.TermoakTheme
import com.termoak.app.ui.keyboardShortcutGroups
import kotlinx.coroutines.launch

/** The only activity. AppCompat applies the per-app language on Android 12 and older (AppLanguage). */
class MainActivity : AppCompatActivity() {
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as TermoakApp
        app.accounts.refresh()
        app.shareNotices.start()
        if (savedInstanceState == null) handleIntent(intent)
        app.shortcuts.update()
        // The names of the accounts and "Hide email addresses", for every screen.
        lifecycleScope.launch {
            kotlinx.coroutines.flow.combine(app.prefs.accountAliases, app.prefs.hideEmails) { aliases, hide ->
                com.termoak.ffi.AccountNames(aliases, hide)
            }.collect { com.termoak.app.data.AccountNamesState.names = it }
        }
        // The app lock: the recent apps screen doesn't show the app's content while it is on.
        if (Build.VERSION.SDK_INT >= 33) {
            lifecycleScope.launch { app.appLock.enabled.collect { setRecentsScreenshotEnabled(!it) } }
        }
        setContent {
            TermoakTheme(app.prefs) {
                Box(Modifier.fillMaxSize()) {
                    AppRoot(app)
                    AppLockScreen(app)
                    // "Tap or plug in your security key", its PIN and "Touch your security key", above everything.
                    val locked by app.appLock.locked.collectAsState()
                    if (!locked) com.termoak.app.ui.SecurityKeySheet(app.securityKeys)
                }
            }
        }
    }

    /** Keys whose press was an app shortcut: their release is taken too. */
    private val shortcutKeys = mutableSetOf<Int>()

    /**
     * The app's keyboard shortcuts (Ctrl+Shift+…, Ctrl+/...) go to the
     * screen on top before the focused view gets the key, so they work
     * wherever the focus is; the rest of the keys go on as usual.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                val shortcut = HardwareKeys.shortcutOf(event.toKeyPress(), event.layout())
                if (shortcut != null) {
                    // Held down: again only for the ones that repeat (zoom, tabs, scrolling).
                    val taken = if (event.repeatCount > 0 && shortcut in KeyShortcuts.once) event.keyCode in shortcutKeys
                    else KeyShortcuts.dispatch(shortcut)
                    if (taken) {
                        shortcutKeys += event.keyCode
                        return true
                    }
                }
            }
            KeyEvent.ACTION_UP -> if (shortcutKeys.remove(event.keyCode)) return true
        }
        if (super.dispatchKeyEvent(event)) return true
        // Nothing took it: the screen's own keys (Ctrl+F, F5...).
        return event.action == KeyEvent.ACTION_DOWN && KeyShortcuts.dispatchUnhandled(event)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        (application as TermoakApp).keyboard.refresh(newConfig)
    }

    /** Android's list of shortcuts (Meta+/): the app's. */
    override fun onProvideKeyboardShortcuts(data: MutableList<KeyboardShortcutGroup>, menu: Menu?, deviceId: Int) {
        super.onProvideKeyboardShortcuts(data, menu, deviceId)
        data += keyboardShortcutGroups(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        // Security keys: NFC reading (only while a request is open) needs the activity in front.
        (application as TermoakApp).securityKeyHardware.activity = this
    }

    override fun onPause() {
        (application as TermoakApp).securityKeyHardware.activity = null
        super.onPause()
    }

    override fun onStart() {
        super.onStart()
        (application as TermoakApp).appLock.started()
        (application as TermoakApp).sessions.appReturned()
        (application as TermoakApp).shareNotices.inForeground = true
    }

    override fun onStop() {
        // Rotating or folding isn't leaving the app.
        if (!isChangingConfigurations) {
            (application as TermoakApp).appLock.stopped()
            (application as TermoakApp).sessions.appLeaving()
            // The hosts opened meanwhile, for the shortcuts of the app's icon.
            (application as TermoakApp).shortcuts.update()
        }
        (application as TermoakApp).shareNotices.inForeground = false
        super.onStop()
    }

    /** Invitation links (`termoak://join`, `https://…/join/…`, `termoak://invite`, `https://…/invite/…`), our notifications, the shortcuts of the app's icon and files shared into the app. */
    private fun handleIntent(intent: Intent?) {
        val app = application as TermoakApp
        if (app.shareNotices.handleIntent(intent)) return
        // A push notification or one of its buttons (Approve / Deny open the app's own confirmation).
        if (intent != null) {
            com.termoak.app.push.PushRoutes.fromExtras { intent.getStringExtra(it) }?.let { opened ->
                val id = intent.getIntExtra(com.termoak.app.push.PushRoutes.EXTRA_NOTIFICATION, 0)
                app.pushNotifier.cancel(id, intent.getStringExtra(com.termoak.app.push.PushNotifier.EXTRA_TAG).orEmpty())
                intent.removeExtra(com.termoak.app.push.PushRoutes.EXTRA_KIND)
                // The events sockets start (or reconnect) now: a waiting security key request arrives.
                app.accounts.refresh()
                app.pendingPush.value = opened
                return
            }
        }
        // A shortcut of the app's icon (touch and hold it).
        QuickAction.parse(intent?.action, intent?.getStringExtra(QuickAction.EXTRA_HOST), intent?.getStringExtra(QuickAction.EXTRA_ACCOUNT))?.let {
            app.pendingQuickAction.value = it
            app.shortcuts.used(it)
            return
        }
        // Files shared from another app: to a host's files.
        if (intent?.action == Intent.ACTION_SEND || intent?.action == Intent.ACTION_SEND_MULTIPLE) {
            val uris = IncomingFiles.urisOf(intent)
            if (uris.isNotEmpty()) app.incomingFiles.offer(uris)
            return
        }
        if (intent?.action == Intent.ACTION_VIEW) {
            // Single sign-on: the browser came back (termoak://sso, RFC 8252 §7.1).
            if (com.termoak.app.data.SsoBrowser.deliver(intent.dataString)) return
            JoinLinkRef.parse(intent.dataString)?.let { app.pendingLink.value = it }
                ?: InviteLinkRef.parse(intent.dataString)?.let { app.pendingInvite.value = it }
        }
    }

    /** For the "open terminals" notification (Android 13+ asks for it). */
    fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
