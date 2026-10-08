package com.termoak.app.term

import android.view.KeyEvent
import java.text.Normalizer

/*
 * Hardware keyboards (Bluetooth, USB, tablet covers, Chromebooks, DeX):
 * from a key press to what reaches the remote program, as xterm does it.
 * Pure Kotlin: only the KEYCODE_* constants of Android (compile-time
 * constants), so it runs in the JVM unit tests (HardwareKeysTest) with fake
 * keyboard layouts.
 */

/** A key press, without Android's KeyEvent (the view builds it; the tests too). */
data class KeyPress(
    /** `KeyEvent.KEYCODE_*`. */
    val keyCode: Int,
    val shift: Boolean = false,
    val ctrl: Boolean = false,
    /** Left Alt (Alt as an ESC prefix). */
    val alt: Boolean = false,
    /** Right Alt: AltGr on the layouts that have a third level (Spanish @ # € [ ] { } \ | ~...). */
    val altGr: Boolean = false,
    /** Meta (Windows, Command, Search): like Alt, an ESC prefix. */
    val meta: Boolean = false,
    val capsLock: Boolean = false,
    val numLock: Boolean = false,
    /** Auto-repeat count (0: the first press). */
    val repeat: Int = 0,
)

/**
 * The keyboard layout: the character a key gives, as Android's
 * `KeyCharacterMap.get` returns it: 0 for none, and for a dead key the
 * accent with [COMBINING_ACCENT] set (e.g. `´` on the Spanish layout).
 */
fun interface KeyLayout {
    fun char(keyCode: Int, shift: Boolean, altGr: Boolean, capsLock: Boolean, numLock: Boolean): Int

    companion object {
        /** `KeyCharacterMap.COMBINING_ACCENT`. */
        const val COMBINING_ACCENT = 0x80000000.toInt()
        /** `KeyCharacterMap.COMBINING_ACCENT_MASK`. */
        const val COMBINING_ACCENT_MASK = 0x7FFFFFFF
    }
}

/** Keys that aren't characters. */
enum class SpecialKey {
    UP, DOWN, LEFT, RIGHT, HOME, END, PAGE_UP, PAGE_DOWN, INSERT, DELETE,
    ENTER, TAB, BACKSPACE, ESCAPE,
    F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12,
    /** Keypad keys that change with the application keypad mode (DECKPAM). */
    KP_ENTER, KP_PLUS, KP_MINUS, KP_MULTIPLY, KP_DIVIDE,
}

/** What a key press types, already resolved (layout, AltGr, dead keys), still without the terminal's modes. */
sealed class KeyStroke {
    /** Characters; with [ctrl] they become control codes (Ctrl+C → 0x03), with [alt] they get an ESC before. */
    data class Text(val text: String, val ctrl: Boolean = false, val alt: Boolean = false) : KeyStroke()
    data class Special(val key: SpecialKey, val shift: Boolean = false, val alt: Boolean = false, val ctrl: Boolean = false) : KeyStroke()
}

/** App actions on the keyboard (Ctrl+Shift+…, so that plain Ctrl stays for the shell). */
enum class Shortcut {
    NEW_TAB, CLOSE_TAB, NEXT_TAB, PREV_TAB, COPY, PASTE, ZOOM_IN, ZOOM_OUT, ZOOM_RESET,
    NEXT_PANE, SEARCH_HOSTS, SHORTCUTS, SCROLL_PAGE_UP, SCROLL_PAGE_DOWN,

    // The desktop's (Windows and Linux) shortcuts.
    /** Ctrl+Alt+arrows: the split view's pane in that direction. */
    PANE_LEFT, PANE_RIGHT, PANE_UP, PANE_DOWN,
    /** Ctrl+Shift+D: another terminal into the split view. */
    ADD_PANE,
    /** Ctrl+Shift+M: the focused pane big, the others small. */
    FOCUS_MODE,
    /** Ctrl+Alt+B: broadcast input to the panes. */
    BROADCAST,
    /** Ctrl+Shift+PgUp / PgDn: move the tab. */
    MOVE_TAB_LEFT, MOVE_TAB_RIGHT,
    /** Ctrl+Shift+H: Home (the Vault). */
    HOME,
    /** Ctrl+,: Settings. */
    SETTINGS,
    /** Ctrl+Shift+I: the copilot. */
    COPILOT,
    /** Ctrl+Shift+N: a new host. */
    NEW_HOST,
    /** Ctrl+Shift+S: snippets. */
    SNIPPETS,
    /** Ctrl+Shift+R: reconnect. */
    RECONNECT,
    ;

    /** With Ctrl+Alt: when nothing on screen takes it, the key goes to the terminal as it is. */
    val typesWhenFree: Boolean get() = this in setOf(PANE_LEFT, PANE_RIGHT, PANE_UP, PANE_DOWN, BROADCAST)
}

sealed class KeyResult {
    /** Type these (an accent left alone by a dead key may come first). */
    data class Send(val strokes: List<KeyStroke>) : KeyResult()
    data class Action(val shortcut: Shortcut) : KeyResult()
    /** Taken (a dead key waiting for the next one). */
    data object Consumed : KeyResult()
    /** Not for the terminal (volume, modifiers alone...): the system gets it. */
    data object Unhandled : KeyResult()
}

/** Terminal modes that change what keys send. */
data class KeyModes(
    /** DECCKM: arrows, Home and End as `ESC O x`. */
    val appCursor: Boolean = false,
    /** DECKPAM: the keypad's Enter and operators as `ESC O x`. */
    val appKeypad: Boolean = false,
)

/**
 * Resolves key presses. It keeps a dead key (´ ` ^ ¨ ~) until the next
 * one: ´ then a gives á, ´ then space gives ´, ´ then x gives ´x.
 */
class HardwareKeys {
    /** Pending dead key (its spacing accent), 0: none. */
    var pendingAccent: Int = 0
        private set

    fun reset() {
        pendingAccent = 0
    }

    fun press(p: KeyPress, layout: KeyLayout, shortcuts: Boolean = true): KeyResult {
        if (shortcuts) shortcutOf(p, layout)?.let {
            pendingAccent = 0
            return KeyResult.Action(it)
        }
        if (p.keyCode in ModifierKeys) return KeyResult.Unhandled
        // Meta works as Alt; AltGr without a third level on that key too.
        val esc = p.alt || p.meta
        special(p)?.let { key ->
            return send(KeyStroke.Special(key, shift = p.shift, alt = esc || p.altGr, ctrl = p.ctrl))
        }
        keypadText(p)?.let { return send(KeyStroke.Text(it, alt = esc)) }

        var c = 0
        var altGrUsed = false
        if (p.altGr) {
            val third = layout.char(p.keyCode, p.shift, true, p.capsLock, p.numLock)
            val plain = layout.char(p.keyCode, p.shift, false, p.capsLock, p.numLock)
            if (third != 0 && third != plain) {
                c = third
                altGrUsed = true
            }
        }
        if (c == 0) c = layout.char(p.keyCode, p.shift, false, p.capsLock, p.numLock)
        val alt = esc || (p.altGr && !altGrUsed)

        if (p.ctrl) {
            // Ctrl+letter, Ctrl+[ ... by the character; on layouts without it
            // (dead keys, other alphabets, AltGr symbols), by the key's place on a US keyboard.
            val typed = if (c and KeyLayout.COMBINING_ACCENT != 0) 0 else c
            val ch = typed.takeIf { controlCode(it) != null } ?: usChar(p.keyCode, p.shift).takeIf { controlCode(it) != null }
            if (ch != null) return send(KeyStroke.Text(String(Character.toChars(ch)), ctrl = true, alt = alt))
            if (typed == 0) return KeyResult.Unhandled
            return send(KeyStroke.Text(String(Character.toChars(typed)), alt = alt))
        }

        if (c and KeyLayout.COMBINING_ACCENT != 0) {
            val accent = c and KeyLayout.COMBINING_ACCENT_MASK
            if (p.repeat > 0) return KeyResult.Consumed
            val before = pendingAccent
            if (before != 0) {
                // Two dead keys: the same one twice gives the accent; another one, the first accent and waits again.
                pendingAccent = if (before == accent) 0 else accent
                return KeyResult.Send(listOf(KeyStroke.Text(DeadKeys.spacing(before), alt = alt)))
            }
            pendingAccent = accent
            return KeyResult.Consumed
        }
        if (c == 0) return KeyResult.Unhandled
        val accent = pendingAccent
        pendingAccent = 0
        val text = if (accent == 0) {
            String(Character.toChars(c))
        } else if (c == ' '.code) {
            DeadKeys.spacing(accent)
        } else {
            DeadKeys.compose(accent, c) ?: (DeadKeys.spacing(accent) + String(Character.toChars(c)))
        }
        return KeyResult.Send(listOf(KeyStroke.Text(text, alt = alt)))
    }

    /** A key that isn't a character: the pending accent (if any) goes first, alone. */
    private fun send(stroke: KeyStroke): KeyResult {
        val accent = pendingAccent
        pendingAccent = 0
        return KeyResult.Send(if (accent == 0) listOf(stroke) else listOf(KeyStroke.Text(DeadKeys.spacing(accent)), stroke))
    }

    companion object {
        private val ModifierKeys = setOf(
            KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT, KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_CTRL_RIGHT,
            KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT, KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_META_RIGHT,
            KeyEvent.KEYCODE_CAPS_LOCK, KeyEvent.KEYCODE_NUM_LOCK, KeyEvent.KEYCODE_SCROLL_LOCK, KeyEvent.KEYCODE_FUNCTION,
            KeyEvent.KEYCODE_SYM,
        )

        /**
         * The app shortcut of a press, if it is one: Ctrl+Shift+T/W/C/V/K/O,
         * Ctrl+Shift+= / - / 0 (zoom), Ctrl+Tab / Ctrl+Shift+Tab and
         * Ctrl+Shift+] / [ (tabs), Ctrl+/ (this list), Shift+PgUp/PgDn
         * (scrollback), Shift+Insert (paste) and Ctrl+Insert (copy); and the
         * desktop's: Ctrl+Alt+arrows, Ctrl+Shift+D / M and Ctrl+Alt+B (split
         * view), Ctrl+Shift+PgUp/PgDn (move the tab), Ctrl+Shift+H (Home),
         * Ctrl+, (Settings), Ctrl+Shift+I (copilot), Ctrl+Shift+N (new host),
         * Ctrl+Shift+S (snippets) and Ctrl+Shift+R (reconnect).
         * Symbols go by the character, so they work on other layouts too.
         */
        fun shortcutOf(p: KeyPress, layout: KeyLayout): Shortcut? {
            // Ctrl+Alt (the left Alt): the split view's.
            if (p.ctrl && p.alt && !p.shift && !p.meta && !p.altGr) {
                return when (p.keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> Shortcut.PANE_LEFT
                    KeyEvent.KEYCODE_DPAD_RIGHT -> Shortcut.PANE_RIGHT
                    KeyEvent.KEYCODE_DPAD_UP -> Shortcut.PANE_UP
                    KeyEvent.KEYCODE_DPAD_DOWN -> Shortcut.PANE_DOWN
                    KeyEvent.KEYCODE_B -> Shortcut.BROADCAST
                    else -> null
                }
            }
            if (p.alt || p.meta || p.altGr) return null
            fun plainChar(shift: Boolean): Int =
                layout.char(p.keyCode, shift, false, false, p.numLock).let { if (it and KeyLayout.COMBINING_ACCENT != 0) 0 else it }
            val base = plainChar(false)
            val typed = if (p.shift) plainChar(true) else base
            return when {
                p.ctrl && p.shift -> when (p.keyCode) {
                    KeyEvent.KEYCODE_T -> Shortcut.NEW_TAB
                    KeyEvent.KEYCODE_W -> Shortcut.CLOSE_TAB
                    KeyEvent.KEYCODE_C -> Shortcut.COPY
                    KeyEvent.KEYCODE_V -> Shortcut.PASTE
                    KeyEvent.KEYCODE_K -> Shortcut.SEARCH_HOSTS
                    KeyEvent.KEYCODE_O -> Shortcut.NEXT_PANE
                    KeyEvent.KEYCODE_D -> Shortcut.ADD_PANE
                    KeyEvent.KEYCODE_M -> Shortcut.FOCUS_MODE
                    KeyEvent.KEYCODE_H -> Shortcut.HOME
                    KeyEvent.KEYCODE_I -> Shortcut.COPILOT
                    KeyEvent.KEYCODE_N -> Shortcut.NEW_HOST
                    KeyEvent.KEYCODE_S -> Shortcut.SNIPPETS
                    KeyEvent.KEYCODE_R -> Shortcut.RECONNECT
                    KeyEvent.KEYCODE_PAGE_UP -> Shortcut.MOVE_TAB_LEFT
                    KeyEvent.KEYCODE_PAGE_DOWN -> Shortcut.MOVE_TAB_RIGHT
                    KeyEvent.KEYCODE_TAB -> Shortcut.PREV_TAB
                    KeyEvent.KEYCODE_NUMPAD_ADD -> Shortcut.ZOOM_IN
                    KeyEvent.KEYCODE_NUMPAD_SUBTRACT -> Shortcut.ZOOM_OUT
                    else -> when {
                        base == '='.code || base == '+'.code -> Shortcut.ZOOM_IN
                        base == '-'.code -> Shortcut.ZOOM_OUT
                        base == '0'.code -> Shortcut.ZOOM_RESET
                        base == '['.code -> Shortcut.PREV_TAB
                        base == ']'.code -> Shortcut.NEXT_TAB
                        base == '/'.code || typed == '/'.code -> Shortcut.SHORTCUTS
                        else -> null
                    }
                }
                p.ctrl -> when {
                    p.keyCode == KeyEvent.KEYCODE_TAB -> Shortcut.NEXT_TAB
                    p.keyCode == KeyEvent.KEYCODE_INSERT -> Shortcut.COPY
                    typed == '/'.code -> Shortcut.SHORTCUTS
                    typed == ','.code -> Shortcut.SETTINGS
                    else -> null
                }
                p.shift -> when (p.keyCode) {
                    KeyEvent.KEYCODE_PAGE_UP -> Shortcut.SCROLL_PAGE_UP
                    KeyEvent.KEYCODE_PAGE_DOWN -> Shortcut.SCROLL_PAGE_DOWN
                    KeyEvent.KEYCODE_INSERT -> Shortcut.PASTE
                    else -> null
                }
                else -> null
            }
        }

        /** Keys that aren't characters (the keypad's arrows without Num Lock too). */
        fun special(p: KeyPress): SpecialKey? = when (p.keyCode) {
            KeyEvent.KEYCODE_ENTER -> SpecialKey.ENTER
            KeyEvent.KEYCODE_NUMPAD_ENTER -> SpecialKey.KP_ENTER
            KeyEvent.KEYCODE_DEL -> SpecialKey.BACKSPACE
            KeyEvent.KEYCODE_FORWARD_DEL -> SpecialKey.DELETE
            KeyEvent.KEYCODE_TAB -> SpecialKey.TAB
            KeyEvent.KEYCODE_ESCAPE -> SpecialKey.ESCAPE
            KeyEvent.KEYCODE_DPAD_UP -> SpecialKey.UP
            KeyEvent.KEYCODE_DPAD_DOWN -> SpecialKey.DOWN
            KeyEvent.KEYCODE_DPAD_LEFT -> SpecialKey.LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT -> SpecialKey.RIGHT
            KeyEvent.KEYCODE_MOVE_HOME -> SpecialKey.HOME
            KeyEvent.KEYCODE_MOVE_END -> SpecialKey.END
            KeyEvent.KEYCODE_PAGE_UP -> SpecialKey.PAGE_UP
            KeyEvent.KEYCODE_PAGE_DOWN -> SpecialKey.PAGE_DOWN
            KeyEvent.KEYCODE_INSERT -> SpecialKey.INSERT
            in KeyEvent.KEYCODE_F1..KeyEvent.KEYCODE_F12 -> SpecialKey.entries[SpecialKey.F1.ordinal + p.keyCode - KeyEvent.KEYCODE_F1]
            KeyEvent.KEYCODE_NUMPAD_ADD -> SpecialKey.KP_PLUS
            KeyEvent.KEYCODE_NUMPAD_SUBTRACT -> SpecialKey.KP_MINUS
            KeyEvent.KEYCODE_NUMPAD_MULTIPLY -> SpecialKey.KP_MULTIPLY
            KeyEvent.KEYCODE_NUMPAD_DIVIDE -> SpecialKey.KP_DIVIDE
            else -> if (p.numLock) null else when (p.keyCode) {
                KeyEvent.KEYCODE_NUMPAD_8 -> SpecialKey.UP
                KeyEvent.KEYCODE_NUMPAD_2 -> SpecialKey.DOWN
                KeyEvent.KEYCODE_NUMPAD_4 -> SpecialKey.LEFT
                KeyEvent.KEYCODE_NUMPAD_6 -> SpecialKey.RIGHT
                KeyEvent.KEYCODE_NUMPAD_7 -> SpecialKey.HOME
                KeyEvent.KEYCODE_NUMPAD_1 -> SpecialKey.END
                KeyEvent.KEYCODE_NUMPAD_9 -> SpecialKey.PAGE_UP
                KeyEvent.KEYCODE_NUMPAD_3 -> SpecialKey.PAGE_DOWN
                KeyEvent.KEYCODE_NUMPAD_0 -> SpecialKey.INSERT
                KeyEvent.KEYCODE_NUMPAD_DOT -> SpecialKey.DELETE
                else -> null
            }
        }

        /** The keypad's characters (digits with Num Lock, `=`, `,`, parentheses), whatever the layout says. */
        private fun keypadText(p: KeyPress): String? = when (p.keyCode) {
            in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 ->
                if (p.numLock) ('0' + (p.keyCode - KeyEvent.KEYCODE_NUMPAD_0)).toString() else null
            KeyEvent.KEYCODE_NUMPAD_DOT -> if (p.numLock) "." else null
            KeyEvent.KEYCODE_NUMPAD_EQUALS -> "="
            KeyEvent.KEYCODE_NUMPAD_COMMA -> ","
            KeyEvent.KEYCODE_NUMPAD_LEFT_PAREN -> "("
            KeyEvent.KEYCODE_NUMPAD_RIGHT_PAREN -> ")"
            else -> null
        }

        /** The character of a key on a US keyboard (for Ctrl on other layouts); 0 if none. */
        fun usChar(keyCode: Int, shift: Boolean): Int = when (keyCode) {
            in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z -> 'a'.code + keyCode - KeyEvent.KEYCODE_A
            in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 ->
                if (shift) ")!@#$%^&*(".codePointAt(keyCode - KeyEvent.KEYCODE_0) else '0'.code + keyCode - KeyEvent.KEYCODE_0
            KeyEvent.KEYCODE_SPACE -> ' '.code
            KeyEvent.KEYCODE_LEFT_BRACKET -> if (shift) '{'.code else '['.code
            KeyEvent.KEYCODE_RIGHT_BRACKET -> if (shift) '}'.code else ']'.code
            KeyEvent.KEYCODE_BACKSLASH -> if (shift) '|'.code else '\\'.code
            KeyEvent.KEYCODE_SLASH -> if (shift) '?'.code else '/'.code
            KeyEvent.KEYCODE_MINUS -> if (shift) '_'.code else '-'.code
            KeyEvent.KEYCODE_EQUALS -> if (shift) '+'.code else '='.code
            KeyEvent.KEYCODE_GRAVE -> if (shift) '~'.code else '`'.code
            KeyEvent.KEYCODE_SEMICOLON -> if (shift) ':'.code else ';'.code
            KeyEvent.KEYCODE_APOSTROPHE -> if (shift) '"'.code else '\''.code
            KeyEvent.KEYCODE_COMMA -> if (shift) '<'.code else ','.code
            KeyEvent.KEYCODE_PERIOD -> if (shift) '>'.code else '.'.code
            else -> 0
        }

        /** The control code of Ctrl+[c] (as xterm: Ctrl+@ / Ctrl+2 / Ctrl+Space NUL, Ctrl+[ ESC...); `null` if it has none. */
        fun controlCode(c: Int): Int? = when (c) {
            in 'a'.code..'z'.code -> c - 'a'.code + 1
            in 'A'.code..'Z'.code -> c - 'A'.code + 1
            '@'.code, '2'.code, ' '.code -> 0x00
            '['.code, '3'.code -> 0x1b
            '\\'.code, '4'.code -> 0x1c
            ']'.code, '5'.code -> 0x1d
            '^'.code, '6'.code -> 0x1e
            '_'.code, '-'.code, '7'.code, '/'.code -> 0x1f
            '?'.code, '8'.code -> 0x7f
            else -> null
        }
    }
}

/** Dead keys: the accents Android's layouts give and how they combine. */
object DeadKeys {
    /** Accent (as `KeyCharacterMap` gives it, current or legacy) → combining mark. */
    private val combining = mapOf(
        0x00B4 to 0x0301, // ´ acute
        0x02CB to 0x0300, '`'.code to 0x0300, // ` grave
        0x02C6 to 0x0302, '^'.code to 0x0302, // ^ circumflex
        0x02DC to 0x0303, '~'.code to 0x0303, // ~ tilde
        0x00A8 to 0x0308, // ¨ diaeresis
        0x00AF to 0x0304, // ¯ macron
        0x02D8 to 0x0306, // ˘ breve
        0x02D9 to 0x0307, // ˙ dot above
        0x02DA to 0x030A, // ˚ ring above
        0x02DD to 0x030B, // ˝ double acute
        0x02C7 to 0x030C, // ˇ caron
        0x00B8 to 0x0327, // ¸ cedilla
        0x02DB to 0x0328, // ˛ ogonek
    )

    /** What the accent alone types (dead key + space, or twice): the ASCII one when there is one (` ^ ~ for the shell). */
    fun spacing(accent: Int): String = String(
        Character.toChars(
            when (accent) {
                0x02CB -> '`'.code
                0x02C6 -> '^'.code
                0x02DC -> '~'.code
                else -> accent
            },
        ),
    )

    /** [accent] on [c] as a single character (´ + e → é), or `null` if there's none. */
    fun compose(accent: Int, c: Int): String? {
        val mark = combining[accent] ?: return null
        val composed = Normalizer.normalize(String(Character.toChars(c)) + String(Character.toChars(mark)), Normalizer.Form.NFC)
        return composed.takeIf { it.codePointCount(0, it.length) == 1 }
    }
}

/** Bytes of each [KeyStroke] for the terminal, like xterm. */
object KeyEncoder {
    private const val ESC = "\u001b"

    fun encode(stroke: KeyStroke, modes: KeyModes = KeyModes()): ByteArray = when (stroke) {
        is KeyStroke.Text -> text(stroke)
        is KeyStroke.Special -> special(stroke, modes).toByteArray(Charsets.UTF_8)
    }

    private fun text(t: KeyStroke.Text): ByteArray {
        if (!t.ctrl && !t.alt) return t.text.toByteArray(Charsets.UTF_8)
        val out = StringBuilder()
        t.text.codePoints().forEach { cp ->
            if (t.alt) out.append(ESC)
            val code = if (t.ctrl) HardwareKeys.controlCode(cp) else null
            if (code != null) out.append(code.toChar()) else out.appendCodePoint(cp)
        }
        return out.toString().toByteArray(Charsets.UTF_8)
    }

    private fun special(s: KeyStroke.Special, modes: KeyModes): String {
        // xterm's modifier parameter: 1 + Shift + 2·Alt + 4·Ctrl.
        val m = 1 + (if (s.shift) 1 else 0) + (if (s.alt) 2 else 0) + (if (s.ctrl) 4 else 0)
        val alt = if (s.alt) ESC else ""
        fun cursor(letter: Char) = when {
            m > 1 -> "$ESC[1;$m$letter"
            modes.appCursor -> "${ESC}O$letter"
            else -> "$ESC[$letter"
        }
        fun tilde(code: Int) = if (m > 1) "$ESC[$code;$m~" else "$ESC[$code~"
        fun ss3(letter: Char) = if (m > 1) "$ESC[1;$m$letter" else "${ESC}O$letter"
        fun keypad(letter: Char, plain: String) = alt + if (modes.appKeypad) "${ESC}O$letter" else plain
        return when (s.key) {
            SpecialKey.UP -> cursor('A')
            SpecialKey.DOWN -> cursor('B')
            SpecialKey.RIGHT -> cursor('C')
            SpecialKey.LEFT -> cursor('D')
            SpecialKey.HOME -> cursor('H')
            SpecialKey.END -> cursor('F')
            SpecialKey.INSERT -> tilde(2)
            SpecialKey.DELETE -> tilde(3)
            SpecialKey.PAGE_UP -> tilde(5)
            SpecialKey.PAGE_DOWN -> tilde(6)
            SpecialKey.ENTER -> "$alt\r"
            SpecialKey.TAB -> if (s.shift) "$alt$ESC[Z" else "$alt\t"
            SpecialKey.ESCAPE -> "$alt$ESC"
            SpecialKey.BACKSPACE -> alt + if (s.ctrl) "\b" else "\u007f"
            SpecialKey.F1 -> ss3('P')
            SpecialKey.F2 -> ss3('Q')
            SpecialKey.F3 -> ss3('R')
            SpecialKey.F4 -> ss3('S')
            SpecialKey.F5 -> tilde(15)
            SpecialKey.F6 -> tilde(17)
            SpecialKey.F7 -> tilde(18)
            SpecialKey.F8 -> tilde(19)
            SpecialKey.F9 -> tilde(20)
            SpecialKey.F10 -> tilde(21)
            SpecialKey.F11 -> tilde(23)
            SpecialKey.F12 -> tilde(24)
            SpecialKey.KP_ENTER -> keypad('M', "\r")
            SpecialKey.KP_PLUS -> keypad('k', "+")
            SpecialKey.KP_MINUS -> keypad('m', "-")
            SpecialKey.KP_MULTIPLY -> keypad('j', "*")
            SpecialKey.KP_DIVIDE -> keypad('o', "/")
        }
    }
}
