package com.termoak.app

import android.app.Application
import android.os.Build
import android.util.Log
import com.termoak.app.data.Accounts
import com.termoak.app.data.Copilot
import com.termoak.app.data.Prefs
import com.termoak.app.data.ShareNotices
import com.termoak.app.data.Updates
import com.termoak.app.term.CommandAssist
import com.termoak.app.term.HardwareKeyboard
import com.termoak.app.term.Sessions
import com.termoak.app.term.SnippetRuns
import com.termoak.app.term.Tunnels
import com.termoak.ffi.LogLevel
import com.termoak.ffi.LogListener
import com.termoak.ffi.TermoakCore
import com.termoak.ffi.initLogging
import java.io.File

/** A single core (vault, SSH and account) and its state for the whole app. */
class TermoakApp : Application() {
    val core: TermoakCore by lazy {
        TermoakCore(File(filesDir, "termoak").path, Vault.key(this)).also {
            it.setDeviceName("${Build.MANUFACTURER} ${Build.MODEL}")
        }
    }
    val prefs: Prefs by lazy { Prefs(this) }
    /** The accounts on this device (servers), the Vault's view and their sync. */
    val accounts: Accounts by lazy { Accounts(this, core, prefs) }
    val sessions: Sessions by lazy {
        Sessions(this, core, accounts).apply {
            telnetAutoLogin = { prefs.telnetAutoLogin.value }
            assist = CommandAssist(core) { prefs.commandSuggestions.value }
            tunnelCount = { tunnels.running.value.size }
            onLocalConnected = {
                tunnels.onTerminalConnected(it)
                detectOs(it)
            }
        }
    }
    /** The app lock (Settings → Lock). */
    val appLock: com.termoak.app.data.AppLock by lazy { com.termoak.app.data.AppLock(this) }
    /** Running tunnels (port forwarding). */
    val tunnels: Tunnels by lazy { Tunnels(core, sessions) }
    /** A snippet sent to several terminals at once, and how it went. */
    val snippetRuns: SnippetRuns by lazy { SnippetRuns(sessions) }
    val copilot: Copilot by lazy { Copilot(this, core, accounts, sessions) }
    val shareNotices: ShareNotices by lazy { ShareNotices(this, sessions, accounts) }
    /** New versions of the app (APK published on the server). */
    val updates: Updates by lazy { Updates(this, prefs) }
    /** A hardware keyboard is attached (the terminal then hides the key bar and the on-screen keyboard). */
    val keyboard: HardwareKeyboard by lazy { HardwareKeyboard(this) }
    /** An invitation link opened from outside (deep link), waiting for the app to show it. */
    val pendingLink = kotlinx.coroutines.flow.MutableStateFlow<com.termoak.app.data.JoinLinkRef?>(null)
    /** An invitation to create an account opened from outside, waiting for the sign-up form. */
    val pendingInvite = kotlinx.coroutines.flow.MutableStateFlow<com.termoak.app.data.InviteLinkRef?>(null)

    override fun onCreate() {
        super.onCreate()
        runCatching {
            initLogging(LogLevel.INFO, object : LogListener {
                override fun log(level: LogLevel, target: String, message: String) {
                    Log.println(
                        when (level) {
                            LogLevel.ERROR -> Log.ERROR
                            LogLevel.WARN -> Log.WARN
                            LogLevel.INFO -> Log.INFO
                            else -> Log.DEBUG
                        },
                        "termoak",
                        "$target: $message",
                    )
                }
            })
        }
    }
}
