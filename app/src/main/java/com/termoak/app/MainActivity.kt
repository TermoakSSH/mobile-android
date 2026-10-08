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
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
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
        // The app lock: the recent apps screen doesn't show the app's content while it is on.
        if (Build.VERSION.SDK_INT >= 33) {
            lifecycleScope.launch { app.appLock.enabled.collect { setRecentsScreenshotEnabled(!it) } }
        }
        setContent {
            TermoakTheme(app.prefs) {
                Box(Modifier.fillMaxSize()) {
                    AppRoot(app)
                    AppLockScreen(app)
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

    /** Invitation links (`termoak://join`, `https://…/join/…`, `termoak://invite`, `https://…/invite/…`), our notifications and the shortcuts of the app's icon. */
    private fun handleIntent(intent: Intent?) {
        val app = application as TermoakApp
        if (app.shareNotices.handleIntent(intent)) return
        // A shortcut of the app's icon (touch and hold it).
        QuickAction.parse(intent?.action, intent?.getStringExtra(QuickAction.EXTRA_HOST), intent?.getStringExtra(QuickAction.EXTRA_ACCOUNT))?.let {
            app.pendingQuickAction.value = it
            app.shortcuts.used(it)
            return
        }
        if (intent?.action == Intent.ACTION_VIEW) {
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
