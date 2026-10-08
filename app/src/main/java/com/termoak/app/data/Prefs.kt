package com.termoak.app.data

import android.content.Context
import androidx.core.content.edit
import com.termoak.app.files.FileSort
import com.termoak.app.term.GestureMode
import com.termoak.app.term.KeyboardLayout
import com.termoak.app.term.SuggestionMode
import com.termoak.app.term.TerminalFont
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class ThemeMode { SYSTEM, DARK, LIGHT }

/**
 * Layout of wide windows (Settings → Appearance): [AUTO] gives the desktop
 * layout only to tablets, unfolded foldables and Chromebooks (an Expanded
 * width and a height that isn't Compact); [PHONE] never; [DESKTOP] whenever
 * the window is ≥ 840 dp wide (phones in landscape too).
 */
enum class WideLayout {
    AUTO, PHONE, DESKTOP;

    /** Does a window with an Expanded width ([expandedWidth], ≥ 840 dp) and [compactHeight] (< 480 dp) get the desktop layout? */
    fun desktop(expandedWidth: Boolean, compactHeight: Boolean): Boolean = when (this) {
        AUTO -> expandedWidth && !compactHeight
        PHONE -> false
        DESKTOP -> expandedWidth
    }
}

/** App preferences (not synced: they belong to this device). */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _fontSize = MutableStateFlow(sp.getFloat("font_size", DEFAULT_FONT))
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

    private val _checkUpdates = MutableStateFlow(sp.getBoolean("check_updates", true))
    /** Look for a new version of the app once a day (Updates). */
    val checkUpdates: StateFlow<Boolean> = _checkUpdates
    fun setCheckUpdates(on: Boolean) {
        _checkUpdates.value = on
        sp.edit { putBoolean("check_updates", on) }
    }

    private val _confirmPaste = MutableStateFlow(sp.getBoolean("confirm_multiline_paste", true))
    /** Ask before pasting several lines into a terminal (unless it uses bracketed paste). */
    val confirmMultilinePaste: StateFlow<Boolean> = _confirmPaste
    fun setConfirmMultilinePaste(on: Boolean) {
        _confirmPaste.value = on
        sp.edit { putBoolean("confirm_multiline_paste", on) }
    }

    /** The file browser shows hidden files (`.name`). */
    var filesShowHidden: Boolean
        get() = sp.getBoolean("files_hidden", false)
        set(v) = sp.edit { putBoolean("files_hidden", v) }

    /** How the file browser orders folders. */
    var filesSort: FileSort
        get() = runCatching { FileSort.valueOf(sp.getString("files_sort", null) ?: "") }
            .getOrDefault(FileSort.NAME)
        set(v) = sp.edit { putString("files_sort", v.name) }

    var filesSortDescending: Boolean
        get() = sp.getBoolean("files_sort_desc", false)
        set(v) = sp.edit { putBoolean("files_sort_desc", v) }

    private val _keyBar = MutableStateFlow(sp.getBoolean("key_bar_with_keyboard", false))
    /** Keep the terminal's key bar (Esc, Ctrl, arrows...) with a hardware keyboard attached. */
    val keyBarWithKeyboard: StateFlow<Boolean> = _keyBar
    fun setKeyBarWithKeyboard(on: Boolean) {
        _keyBar.value = on
        sp.edit { putBoolean("key_bar_with_keyboard", on) }
    }

    private val _cursorGestures = MutableStateFlow(GestureMode.of(sp.getString("cursor_gestures", null)))
    /** How a finger moves the cursor in the terminal (Settings → Terminal; the iOS app's setting). */
    val cursorGestures: StateFlow<GestureMode> = _cursorGestures
    fun setCursorGestures(mode: GestureMode) {
        _cursorGestures.value = mode
        sp.edit { putString("cursor_gestures", mode.key) }
    }

    private val _terminalFont = MutableStateFlow(TerminalFont.of(sp.getString("terminal_font", null)))
    /** The terminal's font (Settings → Terminal → Font). */
    val terminalFont: StateFlow<TerminalFont> = _terminalFont
    fun setTerminalFont(font: TerminalFont) {
        _terminalFont.value = font
        sp.edit { putString("terminal_font", font.id) }
    }

    private val _terminalTheme = MutableStateFlow(sp.getString("terminal_theme", null) ?: com.termoak.app.term.TerminalThemes.DEFAULT)
    /** The terminals' colour theme (Settings → Terminal → Theme; an id of the engine's list, as on the desktop and iOS). */
    val terminalTheme: StateFlow<String> = _terminalTheme
    fun setTerminalTheme(id: String) {
        _terminalTheme.value = id
        sp.edit { putString("terminal_theme", id) }
    }

    private val _aiFixChip = MutableStateFlow(sp.getBoolean("ai_fix_chip", true))
    /** "Command failed · Explain · Fix" under a command that failed (the AI is only asked when tapped; nothing runs by itself). */
    val aiFixChip: StateFlow<Boolean> = _aiFixChip
    fun setAiFixChip(on: Boolean) {
        _aiFixChip.value = on
        sp.edit { putBoolean("ai_fix_chip", on) }
    }

    private val _suggestions = MutableStateFlow(SuggestionMode.of(sp.getString("command_suggestions", null)))
    /** Where command suggestions show while typing (Settings → Terminal; the iOS app's setting). */
    val commandSuggestions: StateFlow<SuggestionMode> = _suggestions
    fun setCommandSuggestions(mode: SuggestionMode) {
        _suggestions.value = mode
        sp.edit { putString("command_suggestions", mode.key) }
    }

    private val _keyboardLayout = MutableStateFlow(KeyboardLayout.fromJson(sp.getString("keyboard_layout", null)))
    /** The key bar above the keyboard and the groups of the quick panel (Settings → Terminal → Keys above the keyboard). */
    val keyboardLayout: StateFlow<KeyboardLayout> = _keyboardLayout
    fun setKeyboardLayout(layout: KeyboardLayout) {
        _keyboardLayout.value = layout
        sp.edit { putString("keyboard_layout", layout.toJson()) }
    }

    /** The quick panel's tab opened last. */
    var quickPanelTab: String
        get() = sp.getString("quick_panel_tab", null) ?: "teclas"
        set(v) = sp.edit { putString("quick_panel_tab", v) }

    private val _telnetAutoLogin = MutableStateFlow(sp.getBoolean("telnet_auto_login", true))
    /** The host's username and password answer the first login prompts of a Telnet host (the desktop's setting). */
    val telnetAutoLogin: StateFlow<Boolean> = _telnetAutoLogin
    fun setTelnetAutoLogin(on: Boolean) {
        _telnetAutoLogin.value = on
        sp.edit { putBoolean("telnet_auto_login", on) }
    }

    private val _wideLayout = MutableStateFlow(
        runCatching { WideLayout.valueOf(sp.getString("wide_layout", null) ?: "") }.getOrDefault(WideLayout.AUTO),
    )
    /** Which layout wide windows get (Settings → Appearance). */
    val wideLayout: StateFlow<WideLayout> = _wideLayout
    fun setWideLayout(mode: WideLayout) {
        _wideLayout.value = mode
        sp.edit { putString("wide_layout", mode.name) }
    }

    private val _sidebarCollapsed = MutableStateFlow(sp.getBoolean("sidebar_collapsed", false))
    /** The sidebar of wide windows (tablets, Chromebooks, DeX) is shrunk to its icons. */
    val sidebarCollapsed: StateFlow<Boolean> = _sidebarCollapsed
    fun setSidebarCollapsed(on: Boolean) {
        _sidebarCollapsed.value = on
        sp.edit { putBoolean("sidebar_collapsed", on) }
    }

    /** The Vault shows only This-device items (the account switcher's "This device only"). */
    var deviceOnlyView: Boolean
        get() = sp.getBoolean("view_device_only", false)
        set(v) = sp.edit { putBoolean("view_device_only", v) }

    /** Vault chosen in the Vault's filter (`null`: all of them; [DEVICE_VAULT]: This device). */
    var vaultFilter: String?
        get() = sp.getString("vault_filter", null)
        set(v) = sp.edit { putString("vault_filter", v) }

    /** Where the last new item went: `<account id>/<vault id>`, or [DEVICE_VAULT]. */
    var lastTarget: String?
        get() = sp.getString("last_target", null)
        set(v) = sp.edit { putString("last_target", v) }

    /** The notice about the move of the data to one store per account was shown. */
    var layoutNoticeShown: Boolean
        get() = sp.getBoolean("layout_notice_shown", false)
        set(v) = sp.edit { putBoolean("layout_notice_shown", v) }

    var lastEmail: String?
        get() = sp.getString("last_email", null)
        set(v) = sp.edit().putString("last_email", v).apply()

    /** Name used last time to join a shared session with a link (without an account). */
    var guestName: String?
        get() = sp.getString("guest_name", null)
        set(v) = sp.edit { putString("guest_name", v) }

    /** How this phone appears in the accounts (Settings → About; empty: the system's name). */
    var deviceName: String?
        get() = sp.getString("device_name", null)?.trim()?.takeIf { it.isNotEmpty() }
        set(v) = sp.edit { putString("device_name", v?.trim()?.take(64)) }

    /** The hosts opened last, for the app's shortcuts (on this device only). */
    var recentHosts: RecentHosts
        get() = RecentHosts(sp.getString("recent_hosts", null)?.split('\n')?.filter { it.isNotEmpty() }.orEmpty())
        set(v) = sp.edit { putString("recent_hosts", v.keys.joinToString("\n")) }

    /** The device's push token (Firebase), when the build has push. */
    var pushToken: String?
        get() = sp.getString("push_token", null)
        set(v) = sp.edit { putString("push_token", v) }

    /** Already used without a server: don't show the welcome screen again. */
    var skippedLogin: Boolean
        get() = sp.getBoolean("skipped_login", false)
        set(v) = sp.edit().putBoolean("skipped_login", v).apply()

    init {
        // Replaced by the accounts (core 0.4): every account remembers its server.
        if (sp.contains("last_server")) sp.edit { remove("last_server") }
    }

    companion object {
        const val DEFAULT_FONT = 13f
        const val MIN_FONT = 8f
        const val MAX_FONT = 24f
    }
}
