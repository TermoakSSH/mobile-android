package com.termoak.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.termoak.app.data.JoinLinkRef
import com.termoak.app.ui.AppRoot
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
