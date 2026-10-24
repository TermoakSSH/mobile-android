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
            it.setDeviceName(prefs.deviceName ?: systemDeviceName)
            // Security keys (FIDO2) before any events socket opens: the sockets then
            // declare features=security_keys and answer the server's signature requests.
            if (securityKeys.available) it.setSecurityKeyHandler(securityKeys.flow)
        }
    }
    /** The phone's security keys: YubiKit over USB and NFC. */
    val securityKeyHardware: com.termoak.app.sk.YubiHardware by lazy {
        com.termoak.app.sk.YubiHardware(this).also { it.locked = { appLock.locked.value } }
    }
    /** Security keys: the engine's handler and the sheet ("Tap or plug in your security key", PIN, touch). */
    val securityKeys: com.termoak.app.sk.SecurityKeys by lazy {
        com.termoak.app.sk.SecurityKeys(securityKeyHardware, com.termoak.app.sk.SecurityKeys.supported(this))
    }
    val prefs: Prefs by lazy { Prefs(this) }
    /** The phone's name when none was chosen: its maker and model. */
    val systemDeviceName: String get() = "${Build.MANUFACTURER} ${Build.MODEL}"
    /** The accounts on this device (servers), the Vault's view and their sync. */
    val accounts: Accounts by lazy {
        Accounts(this, core, prefs).also { a ->
            a.securityKeyEvents = { securityKeys.onEvent(it) }
            a.pushHooks = object : Accounts.PushHooks {
                override fun register(accountId: String) = push.register(accountId)
                override suspend fun unregister(accountId: String) = push.unregister(accountId)
            }
        }
    }
    /** Push without Google (UnifiedPush): one registration per account with the user's distributor. */
    val push: com.termoak.app.push.PushRegistry by lazy { com.termoak.app.push.PushRegistry(this, accounts) }
    val pushNotifier: com.termoak.app.push.PushNotifier by lazy { com.termoak.app.push.PushNotifier(this) }
    /** A push notification (or one of its buttons) was tapped: where the app goes. */
    val pendingPush = kotlinx.coroutines.flow.MutableStateFlow<Pair<com.termoak.app.push.PushOpen, com.termoak.app.push.PushAction?>?>(null)
    val sessions: Sessions by lazy {
        Sessions(this, core, accounts).apply {
            telnetAutoLogin = { prefs.telnetAutoLogin.value }
            assist = CommandAssist(core) { prefs.commandSuggestions.value }
            tunnelCount = { tunnels.running.value.size }
            onLocalConnected = {
                tunnels.onTerminalConnected(it)
                detectOs(it)
            }
            onHostOpened = { shortcuts.record(it) }
            fixChipAllowed = { prefs.aiFixChip.value && terminalAi.accountFor(it) != null }
            onCommandEntered = { terminalAi.commandSent(it) }
        }
    }
    /** The app's shortcuts (touch and hold its icon). */
    val shortcuts: AppShortcuts by lazy { AppShortcuts(this, core, prefs) }
    /** A shortcut of the app's icon, waiting for the app to do it. */
    val pendingQuickAction = kotlinx.coroutines.flow.MutableStateFlow<com.termoak.app.data.QuickAction?>(null)
    /** The app lock (Settings → Lock). */
    val appLock: com.termoak.app.data.AppLock by lazy { com.termoak.app.data.AppLock(this) }
    /** Running tunnels (port forwarding). */
    val tunnels: Tunnels by lazy { Tunnels(core, sessions) }
    /** A snippet sent to several terminals at once, and how it went. */
    val snippetRuns: SnippetRuns by lazy { SnippetRuns(sessions) }
    val copilot: Copilot by lazy { Copilot(this, core, accounts, sessions) }
    /** The AI in the terminals: Explain/Fix of failed commands and `# request` lines. */
    val terminalAi: com.termoak.app.data.TerminalAi by lazy { com.termoak.app.data.TerminalAi(this, accounts) }
    val shareNotices: ShareNotices by lazy { ShareNotices(this, sessions, accounts) }
    /** New versions of the app (APK published on the server). */
    val updates: Updates by lazy { Updates(this, prefs) }
    /** A hardware keyboard is attached (the terminal then hides the key bar and the on-screen keyboard). */
    val keyboard: HardwareKeyboard by lazy { HardwareKeyboard(this) }
    /** An invitation link opened from outside (deep link), waiting for the app to show it. */
    val pendingLink = kotlinx.coroutines.flow.MutableStateFlow<com.termoak.app.data.JoinLinkRef?>(null)
    /** Files shared into the app from another one, on their way to a host's files. */
    val incomingFiles = com.termoak.app.data.IncomingFiles()
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
