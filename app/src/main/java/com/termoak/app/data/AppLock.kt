package com.termoak.app.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** How long the app can be in the background before it locks again (seconds; the iOS app's choices). */
enum class LockDelay(val seconds: Int) {
    IMMEDIATELY(0), ONE_MINUTE(60), FIVE_MINUTES(300), FIFTEEN_MINUTES(900), HOUR(3600);

    companion object {
        fun of(seconds: Int): LockDelay = entries.firstOrNull { it.seconds == seconds } ?: IMMEDIATELY
    }
}

/** The app lock's rule, without Android (JVM tests). */
object LockPolicy {
    /** Coming back to the foreground: lock if it is on and the app was away at least [delay] (always with "immediately"). */
    fun shouldLock(enabled: Boolean, delay: LockDelay, backgroundedAt: Long?, now: Long): Boolean {
        if (!enabled || backgroundedAt == null) return false
        return now - backgroundedAt >= delay.seconds * 1000L
    }
}

/**
 * The app lock, like the desktop's and iOS's: the fingerprint, face or
 * screen lock to open Termoak, and again after it was in the background for
 * the chosen time. The terminals keep running underneath; while it is on,
 * the recent apps screen doesn't show the app's content (Android 13+).
 */
class AppLock(context: Context) {
    private val sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _enabled = MutableStateFlow(sp.getBoolean("app_lock", false))
    val enabled: StateFlow<Boolean> = _enabled
    private val _delay = MutableStateFlow(LockDelay.of(sp.getInt("app_lock_delay", 0)))
    val delay: StateFlow<LockDelay> = _delay
    private val _locked = MutableStateFlow(_enabled.value)
    /** Locked: the lock screen covers the app. */
    val locked: StateFlow<Boolean> = _locked
    private var backgroundedAt: Long? = null
    /** The app itself opened another screen (a file picker, the screen lock's): coming back isn't a return. */
    @Volatile private var ownTrip = false

    fun setEnabled(on: Boolean) {
        _enabled.value = on
        sp.edit { putBoolean("app_lock", on) }
        if (!on) _locked.value = false
    }

    fun setDelay(d: LockDelay) {
        _delay.value = d
        sp.edit { putInt("app_lock_delay", d.seconds) }
    }

    /** The app went to the background. */
    fun stopped(now: Long = System.currentTimeMillis()) {
        if (ownTrip) {
            ownTrip = false
            return
        }
        if (backgroundedAt == null) backgroundedAt = now
    }

    /** The app is about to open a screen of the system or another app and come back (a picker, the screen lock's). */
    fun expectReturn() {
        ownTrip = true
    }

    /** The app came back. */
    fun started(now: Long = System.currentTimeMillis()) {
        if (LockPolicy.shouldLock(_enabled.value, _delay.value, backgroundedAt, now)) _locked.value = true
        backgroundedAt = null
    }

    fun lockNow() {
        if (_enabled.value) _locked.value = true
    }

    fun unlocked() {
        _locked.value = false
    }
}
