package com.termoak.app.term

import com.termoak.ffi.TerminalThemeInfo
import com.termoak.ffi.terminalThemeForHost
import com.termoak.ffi.terminalThemes

/**
 * The terminal's colour themes: the engine's list (`terminalThemes()`), the
 * same 15 with the same ids as the desktop and iOS. The ids are stored in
 * the settings and in hosts (`HostSettings.theme`).
 */
object TerminalThemes {
    /** The default (dark) one. */
    const val DEFAULT = "termoak"

    val all: List<TerminalThemeInfo> by lazy { runCatching { terminalThemes() }.getOrDefault(emptyList()) }

    fun byId(id: String?): TerminalThemeInfo? = all.firstOrNull { it.id == id } ?: all.firstOrNull()

    /**
     * Theme of a host's terminal by the engine's rule (the same in every
     * app): `dark` and `light` (what the desktop's host editor saves) keep
     * the app's theme when it is of that kind and otherwise use Termoak or
     * Termoak Light; a theme's id uses it; anything else (or nothing)
     * follows the app ([app]).
     */
    fun forHost(value: String?, app: String): String {
        if (value.isNullOrBlank()) return app
        return runCatching { terminalThemeForHost(value, app) }.getOrDefault(app)
    }
}

/**
 * The colours of the terminal's bars and keys, from its theme (the iOS
 * app's rule: the bars a little lighter, or darker on a light theme, than
 * the background). ARGB.
 */
data class TermChrome(
    val screen: Int,
    val bar: Int,
    val key: Int,
    val text: Int,
    /** The theme's green: keys that are on, accents. */
    val accent: Int,
    val light: Boolean,
) {
    companion object {
        private const val OPAQUE = 0xFF000000.toInt()

        /** The Termoak theme's (before any theme is known). */
        val DEFAULT = of(0x12151D, 0xD6DBE4, 0x3FB27F, light = false)

        fun of(theme: TerminalThemeInfo?): TermChrome {
            val c = theme?.colors ?: return DEFAULT
            return of(c.background.toInt(), c.foreground.toInt(), c.ansi.getOrNull(2)?.toInt() ?: 0x3FB27F, theme.isLight)
        }

        /** From a theme's background, text and green (RGB or ARGB). */
        fun of(background: Int, foreground: Int, green: Int, light: Boolean): TermChrome {
            val bg = background or OPAQUE
            val toward = if (light) OPAQUE else -1
            return TermChrome(
                screen = bg,
                bar = blend(bg, toward, 0.06f),
                key = blend(bg, toward, 0.12f),
                text = foreground or OPAQUE,
                accent = green or OPAQUE,
                light = light,
            )
        }

        /** [a] blended with [b] in the proportion [t] (0 = [a]), opaque. */
        fun blend(a: Int, b: Int, t: Float): Int {
            fun ch(x: Int, shift: Int) = (x shr shift) and 0xFF
            fun mix(shift: Int) = Math.round(ch(a, shift) + (ch(b, shift) - ch(a, shift)) * t).coerceIn(0, 255)
            return OPAQUE or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
        }
    }
}
