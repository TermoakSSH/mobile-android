package com.termoak.app

import android.app.Application
import android.os.Build
import android.util.Log
import com.termoak.app.data.Account
import com.termoak.app.data.Copilot
import com.termoak.app.data.Prefs
import com.termoak.app.term.Sessions
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
    val account: Account by lazy { Account(core) }
    val sessions: Sessions by lazy { Sessions(this, core) }
    val copilot: Copilot by lazy { Copilot(this, core, account, sessions) }

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
