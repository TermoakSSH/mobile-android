package com.termoak.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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
        app.account.refresh()
        setContent {
            TermoakTheme(app.prefs) { AppRoot(app) }
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
