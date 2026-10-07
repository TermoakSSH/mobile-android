package com.termoak.app

import android.Manifest
import android.content.Intent
import android.content.res.Configuration
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.KeyboardShortcutGroup
import android.view.Menu
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.termoak.app.data.JoinLinkRef
import com.termoak.app.term.HardwareKeys
import com.termoak.app.term.layout
import com.termoak.app.term.toKeyPress
import com.termoak.app.ui.AppRoot
import com.termoak.app.ui.KeyShortcuts
import com.termoak.app.ui.keyboardShortcutGroups
import com.termoak.app.ui.TermoakTheme

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
        setContent {
            TermoakTheme(app.prefs) { AppRoot(app) }
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
        (application as TermoakApp).shareNotices.inForeground = true
    }

    override fun onStop() {
        (application as TermoakApp).shareNotices.inForeground = false
        super.onStop()
    }

    /** Invitation links (`termoak://join`, `https://…/join/…`) and our notifications. */
    private fun handleIntent(intent: Intent?) {
        val app = application as TermoakApp
        if (app.shareNotices.handleIntent(intent)) return
        if (intent?.action == Intent.ACTION_VIEW) {
            JoinLinkRef.parse(intent.dataString)?.let { app.pendingLink.value = it }
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
