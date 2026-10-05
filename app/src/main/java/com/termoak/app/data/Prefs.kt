package com.termoak.app.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class ThemeMode { SYSTEM, DARK, LIGHT }

/** App preferences (not synced: they belong to this device). */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _fontSize = MutableStateFlow(sp.getFloat("font_size", 13f))
    val fontSize: StateFlow<Float> = _fontSize
    fun setFontSize(size: Float) {
        val v = size.coerceIn(MIN_FONT, MAX_FONT)
        _fontSize.value = v
        sp.edit().putFloat("font_size", v).apply()
    }

    private val _theme = MutableStateFlow(
        runCatching { ThemeMode.valueOf(sp.getString("theme", null) ?: "") }.getOrDefault(ThemeMode.DARK),
    )
    val theme: StateFlow<ThemeMode> = _theme
    fun setTheme(mode: ThemeMode) {
        _theme.value = mode
        sp.edit().putString("theme", mode.name).apply()
    }

    private val _keepScreenOn = MutableStateFlow(sp.getBoolean("keep_screen_on", true))
    val keepScreenOn: StateFlow<Boolean> = _keepScreenOn
    fun setKeepScreenOn(on: Boolean) {
        _keepScreenOn.value = on
        sp.edit().putBoolean("keep_screen_on", on).apply()
    }

    private val _vibrate = MutableStateFlow(sp.getBoolean("vibrate_bell", true))
    val vibrateOnBell: StateFlow<Boolean> = _vibrate
    fun setVibrateOnBell(on: Boolean) {
        _vibrate.value = on
        sp.edit().putBoolean("vibrate_bell", on).apply()
    }

    var lastServer: String?
        get() = sp.getString("last_server", null)
        set(v) = sp.edit().putString("last_server", v).apply()

    var lastEmail: String?
        get() = sp.getString("last_email", null)
        set(v) = sp.edit().putString("last_email", v).apply()

    /** Name used last time to join a shared session with a link (without an account). */
    var guestName: String?
        get() = sp.getString("guest_name", null)
        set(v) = sp.edit { putString("guest_name", v) }

    /** Already used without a server: don't show the welcome screen again. */
    var skippedLogin: Boolean
        get() = sp.getBoolean("skipped_login", false)
        set(v) = sp.edit().putBoolean("skipped_login", v).apply()

    companion object {
        const val MIN_FONT = 8f
        const val MAX_FONT = 24f
    }
}
