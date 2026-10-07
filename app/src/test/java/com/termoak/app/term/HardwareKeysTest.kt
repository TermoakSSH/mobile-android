package com.termoak.app.term

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Test

/** Fake keyboard layouts with Android's KeyCharacterMap semantics (dead keys flagged with COMBINING_ACCENT). */
private open class FakeLayout : KeyLayout {
    /** keyCode → (base, shift, altGr): 0 for none. */
    protected val keys = mutableMapOf<Int, Triple<Int, Int, Int>>()

    init {
        for (k in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z) {
            val c = 'a'.code + k - KeyEvent.KEYCODE_A
            keys[k] = Triple(c, c - 32, 0)
        }
        keys[KeyEvent.KEYCODE_SPACE] = Triple(' '.code, ' '.code, 0)
    }

    fun put(code: Int, base: Char, shift: Char, altGr: Char? = null) {
        keys[code] = Triple(base.code, shift.code, altGr?.code ?: 0)
    }

    override fun char(keyCode: Int, shift: Boolean, altGr: Boolean, capsLock: Boolean, numLock: Boolean): Int {
        val (base, shifted, third) = keys[keyCode] ?: return 0
        if (altGr) return third
        val letter = base in 'a'.code..'z'.code
        return if (shift xor (capsLock && letter)) shifted else base
    }
}

private class UsLayout : FakeLayout() {
    init {
        "0123456789".forEachIndexed { i, c -> put(KeyEvent.KEYCODE_0 + i, c, ")!@#$%^&*("[i]) }
        put(KeyEvent.KEYCODE_LEFT_BRACKET, '[', '{')
        put(KeyEvent.KEYCODE_RIGHT_BRACKET, ']', '}')
        put(KeyEvent.KEYCODE_BACKSLASH, '\\', '|')
        put(KeyEvent.KEYCODE_SLASH, '/', '?')
        put(KeyEvent.KEYCODE_MINUS, '-', '_')
        put(KeyEvent.KEYCODE_EQUALS, '=', '+')
        put(KeyEvent.KEYCODE_GRAVE, '`', '~')
        put(KeyEvent.KEYCODE_SEMICOLON, ';', ':')
        put(KeyEvent.KEYCODE_APOSTROPHE, '\'', '"')
        put(KeyEvent.KEYCODE_COMMA, ',', '<')
        put(KeyEvent.KEYCODE_PERIOD, '.', '>')
    }
}

/** Android's keyboard_layout_spanish.kcm (the keys that matter here). */
private class SpanishLayout : FakeLayout() {
    init {
        val shifted = "=!\"·$%&/()"
        val third = mapOf(1 to '|', 2 to '@', 3 to '#', 4 to '~', 5 to '€', 6 to '¬')
        for (i in 0..9) put(KeyEvent.KEYCODE_0 + i, '0' + i, shifted[i], third[i])
        put(KeyEvent.KEYCODE_E, 'e', 'E', '€')
        put(KeyEvent.KEYCODE_GRAVE, 'º', 'ª', '\\')
        put(KeyEvent.KEYCODE_MINUS, '\'', '?')
        put(KeyEvent.KEYCODE_EQUALS, '¡', '¿')
        put(KeyEvent.KEYCODE_RIGHT_BRACKET, '+', '*', ']')
        put(KeyEvent.KEYCODE_SEMICOLON, 'ñ', 'Ñ')
        put(KeyEvent.KEYCODE_BACKSLASH, 'ç', 'Ç', '}')
        put(KeyEvent.KEYCODE_COMMA, ',', ';')
        put(KeyEvent.KEYCODE_PERIOD, '.', ':')
        put(KeyEvent.KEYCODE_SLASH, '-', '_')
        // Dead keys: ` and ^ on the key right of P, ´ and ¨ right of Ñ.
        keys[KeyEvent.KEYCODE_LEFT_BRACKET] = Triple(dead(0x02CB), dead(0x02C6), '['.code)
        keys[KeyEvent.KEYCODE_APOSTROPHE] = Triple(dead(0x00B4), dead(0x00A8), '{'.code)
    }

    private fun dead(accent: Int) = accent or KeyLayout.COMBINING_ACCENT
}

class HardwareKeysTest {
    private val us = UsLayout()
    private val es = SpanishLayout()

    /** What [presses] send, one after the other, as text (escape sequences readable). */
    private fun typed(layout: KeyLayout, vararg presses: KeyPress, modes: KeyModes = KeyModes()): String {
        val keys = HardwareKeys()
        val out = StringBuilder()
        for (p in presses) {
            when (val r = keys.press(p, layout)) {
                is KeyResult.Send -> r.strokes.forEach { out.append(String(KeyEncoder.encode(it, modes), Charsets.UTF_8)) }
                is KeyResult.Action -> out.append("<${r.shortcut}>")
                KeyResult.Consumed -> Unit
                KeyResult.Unhandled -> out.append("<unhandled>")
            }
        }
        return out.toString()
    }

    private fun us(vararg presses: KeyPress, modes: KeyModes = KeyModes()) = typed(us, *presses, modes = modes)
    private fun es(vararg presses: KeyPress) = typed(es, *presses)
    private fun k(code: Int) = KeyPress(code)
    private fun shift(code: Int) = KeyPress(code, shift = true)
    private fun ctrl(code: Int, shift: Boolean = false) = KeyPress(code, ctrl = true, shift = shift)
    private fun alt(code: Int, shift: Boolean = false) = KeyPress(code, alt = true, shift = shift)
    private fun altGr(code: Int) = KeyPress(code, altGr = true)

    @Test
    fun letters() {
        assertEquals("aB", us(k(KeyEvent.KEYCODE_A), shift(KeyEvent.KEYCODE_B)))
        // Caps Lock: capitals, and Shift turns them back.
        assertEquals("Ab", us(KeyPress(KeyEvent.KEYCODE_A, capsLock = true), KeyPress(KeyEvent.KEYCODE_B, capsLock = true, shift = true)))
        // Key repeat: each repeat types again.
        assertEquals("xx", us(k(KeyEvent.KEYCODE_X), KeyPress(KeyEvent.KEYCODE_X, repeat = 1)))
    }

    @Test
    fun controlKeys() {
        assertEquals("\u0003", us(ctrl(KeyEvent.KEYCODE_C)))
        assertEquals("\u0004", us(ctrl(KeyEvent.KEYCODE_D)))
        assertEquals("\u001a", us(ctrl(KeyEvent.KEYCODE_Z)))
        assertEquals("\u000c", us(ctrl(KeyEvent.KEYCODE_L)))
        assertEquals("\u0012", us(ctrl(KeyEvent.KEYCODE_R)))
        assertEquals("\u001b", us(ctrl(KeyEvent.KEYCODE_LEFT_BRACKET)))
        assertEquals("\u001c", us(ctrl(KeyEvent.KEYCODE_BACKSLASH)))
        assertEquals("\u001d", us(ctrl(KeyEvent.KEYCODE_RIGHT_BRACKET)))
        assertEquals("\u0000", us(ctrl(KeyEvent.KEYCODE_SPACE)))
        assertEquals("\u0000", us(ctrl(KeyEvent.KEYCODE_2, shift = true)))
        assertEquals("\u001e", us(ctrl(KeyEvent.KEYCODE_6)))
        assertEquals("\u001f", us(ctrl(KeyEvent.KEYCODE_MINUS)))
        // Ctrl+Shift+letter that isn't a shortcut: still the control code.
        assertEquals("\u0001", us(ctrl(KeyEvent.KEYCODE_A, shift = true)))
        // Caps Lock doesn't change Ctrl+letter.
        assertEquals("\u0003", us(KeyPress(KeyEvent.KEYCODE_C, ctrl = true, capsLock = true)))
    }

    @Test
    fun altAndMetaAreEscPrefixes() {
        assertEquals("\u001bx", us(alt(KeyEvent.KEYCODE_X)))
        assertEquals("\u001bF", us(alt(KeyEvent.KEYCODE_F, shift = true)))
        assertEquals("\u001bb", us(KeyPress(KeyEvent.KEYCODE_B, meta = true)))
        assertEquals("\u001b\u0003", us(KeyPress(KeyEvent.KEYCODE_C, ctrl = true, alt = true)))
        assertEquals("\u001b.", us(alt(KeyEvent.KEYCODE_PERIOD)))
    }

    @Test
    fun editingKeys() {
        assertEquals("\u001b", us(k(KeyEvent.KEYCODE_ESCAPE)))
        assertEquals("\u001b\u001b", us(alt(KeyEvent.KEYCODE_ESCAPE)))
        assertEquals("\t", us(k(KeyEvent.KEYCODE_TAB)))
        assertEquals("\u001b[Z", us(shift(KeyEvent.KEYCODE_TAB)))
        assertEquals("\r", us(k(KeyEvent.KEYCODE_ENTER)))
        assertEquals("\u001b\r", us(alt(KeyEvent.KEYCODE_ENTER)))
        assertEquals("\u007f", us(k(KeyEvent.KEYCODE_DEL)))
        assertEquals("\b", us(ctrl(KeyEvent.KEYCODE_DEL)))
        assertEquals("\u001b\u007f", us(alt(KeyEvent.KEYCODE_DEL)))
        assertEquals("\u001b[3~", us(k(KeyEvent.KEYCODE_FORWARD_DEL)))
        assertEquals("\u001b[3;2~", us(shift(KeyEvent.KEYCODE_FORWARD_DEL)))
        assertEquals("\u001b[2~", us(k(KeyEvent.KEYCODE_INSERT)))
    }

    @Test
    fun cursorKeysWithModifiersAndModes() {
        assertEquals("\u001b[A", us(k(KeyEvent.KEYCODE_DPAD_UP)))
        assertEquals("\u001bOA", us(k(KeyEvent.KEYCODE_DPAD_UP), modes = KeyModes(appCursor = true)))
        assertEquals("\u001b[1;2A", us(shift(KeyEvent.KEYCODE_DPAD_UP)))
        assertEquals("\u001b[1;5C", us(ctrl(KeyEvent.KEYCODE_DPAD_RIGHT)))
        // Modifiers win over the application mode.
        assertEquals("\u001b[1;5C", us(ctrl(KeyEvent.KEYCODE_DPAD_RIGHT), modes = KeyModes(appCursor = true)))
        assertEquals("\u001b[1;3D", us(alt(KeyEvent.KEYCODE_DPAD_LEFT)))
        assertEquals("\u001b[1;6A", us(ctrl(KeyEvent.KEYCODE_DPAD_UP, shift = true)))
        assertEquals("\u001b[H\u001b[F", us(k(KeyEvent.KEYCODE_MOVE_HOME), k(KeyEvent.KEYCODE_MOVE_END)))
        assertEquals("\u001bOH", us(k(KeyEvent.KEYCODE_MOVE_HOME), modes = KeyModes(appCursor = true)))
        assertEquals("\u001b[5~\u001b[6~", us(k(KeyEvent.KEYCODE_PAGE_UP), k(KeyEvent.KEYCODE_PAGE_DOWN)))
        assertEquals("\u001b[5;5~", us(ctrl(KeyEvent.KEYCODE_PAGE_UP)))
    }

    @Test
    fun functionKeys() {
        val all = (KeyEvent.KEYCODE_F1..KeyEvent.KEYCODE_F12).map { k(it) }.toTypedArray()
        assertEquals(
            "\u001bOP\u001bOQ\u001bOR\u001bOS\u001b[15~\u001b[17~\u001b[18~\u001b[19~\u001b[20~\u001b[21~\u001b[23~\u001b[24~",
            us(*all),
        )
        assertEquals("\u001b[15;2~", us(shift(KeyEvent.KEYCODE_F5)))
        assertEquals("\u001b[1;5P", us(ctrl(KeyEvent.KEYCODE_F1)))
    }

    @Test
    fun keypad() {
        val num = { code: Int -> KeyPress(code, numLock = true) }
        assertEquals("17.", us(num(KeyEvent.KEYCODE_NUMPAD_1), num(KeyEvent.KEYCODE_NUMPAD_7), num(KeyEvent.KEYCODE_NUMPAD_DOT)))
        // Without Num Lock: the arrows and the editing keys printed on them.
        assertEquals("\u001b[A\u001b[H\u001b[3~", us(k(KeyEvent.KEYCODE_NUMPAD_8), k(KeyEvent.KEYCODE_NUMPAD_7), k(KeyEvent.KEYCODE_NUMPAD_DOT)))
        assertEquals("\r+", us(num(KeyEvent.KEYCODE_NUMPAD_ENTER), num(KeyEvent.KEYCODE_NUMPAD_ADD)))
        val app = KeyModes(appKeypad = true)
        assertEquals("\u001bOM\u001bOk\u001bOm\u001bOj\u001bOo", us(
            num(KeyEvent.KEYCODE_NUMPAD_ENTER), num(KeyEvent.KEYCODE_NUMPAD_ADD), num(KeyEvent.KEYCODE_NUMPAD_SUBTRACT),
            num(KeyEvent.KEYCODE_NUMPAD_MULTIPLY), num(KeyEvent.KEYCODE_NUMPAD_DIVIDE), modes = app,
        ))
    }

    @Test
    fun spanishAltGr() {
        assertEquals("@#€|~\\", es(
            altGr(KeyEvent.KEYCODE_2), altGr(KeyEvent.KEYCODE_3), altGr(KeyEvent.KEYCODE_E), altGr(KeyEvent.KEYCODE_1),
            altGr(KeyEvent.KEYCODE_4), altGr(KeyEvent.KEYCODE_GRAVE),
        ))
        assertEquals("[]{}", es(
            altGr(KeyEvent.KEYCODE_LEFT_BRACKET), altGr(KeyEvent.KEYCODE_RIGHT_BRACKET),
            altGr(KeyEvent.KEYCODE_APOSTROPHE), altGr(KeyEvent.KEYCODE_BACKSLASH),
        ))
        // AltGr on a key without a third level: like Alt.
        assertEquals("\u001bx", es(altGr(KeyEvent.KEYCODE_X)))
        // Shifted symbols of the layout, ñ and ç.
        assertEquals("/=?ñÑç", es(
            shift(KeyEvent.KEYCODE_7), shift(KeyEvent.KEYCODE_0), shift(KeyEvent.KEYCODE_MINUS), k(KeyEvent.KEYCODE_SEMICOLON),
            shift(KeyEvent.KEYCODE_SEMICOLON), k(KeyEvent.KEYCODE_BACKSLASH),
        ))
    }

    @Test
    fun spanishDeadKeys() {
        val acute = k(KeyEvent.KEYCODE_APOSTROPHE)
        val diaeresis = shift(KeyEvent.KEYCODE_APOSTROPHE)
        val grave = k(KeyEvent.KEYCODE_LEFT_BRACKET)
        val circumflex = shift(KeyEvent.KEYCODE_LEFT_BRACKET)
        assertEquals("á", es(acute, k(KeyEvent.KEYCODE_A)))
        assertEquals("É", es(acute, shift(KeyEvent.KEYCODE_E)))
        assertEquals("à", es(grave, k(KeyEvent.KEYCODE_A)))
        assertEquals("ô", es(circumflex, k(KeyEvent.KEYCODE_O)))
        assertEquals("ü", es(diaeresis, k(KeyEvent.KEYCODE_U)))
        // With space: the accent alone; the ASCII one for ` and ^ (the shell needs them).
        assertEquals("´", es(acute, k(KeyEvent.KEYCODE_SPACE)))
        assertEquals("`", es(grave, k(KeyEvent.KEYCODE_SPACE)))
        assertEquals("^", es(circumflex, k(KeyEvent.KEYCODE_SPACE)))
        // Twice: the accent once.
        assertEquals("´", es(acute, acute))
        // A letter it doesn't go on: both.
        assertEquals("´x", es(acute, k(KeyEvent.KEYCODE_X)))
        // Another dead key: the first accent, and the second one waits.
        assertEquals("´è", es(acute, grave, k(KeyEvent.KEYCODE_E)))
        // A special key: the accent, then the key.
        assertEquals("`\r", es(grave, k(KeyEvent.KEYCODE_ENTER)))
        // A held dead key doesn't repeat.
        assertEquals("é", es(acute, KeyPress(KeyEvent.KEYCODE_APOSTROPHE, repeat = 1), k(KeyEvent.KEYCODE_E)))
        // Modifier keys alone keep it waiting (Shift for a capital).
        assertEquals("<unhandled>Á", es(acute, k(KeyEvent.KEYCODE_SHIFT_LEFT), shift(KeyEvent.KEYCODE_A)))
    }

    @Test
    fun ctrlOnOtherLayouts() {
        // Ctrl on a dead key or an AltGr symbol goes by the US place of the key.
        assertEquals("\u001b", es(ctrl(KeyEvent.KEYCODE_LEFT_BRACKET)))
        assertEquals("\u001d", es(ctrl(KeyEvent.KEYCODE_RIGHT_BRACKET)))
        assertEquals("\u0003", es(ctrl(KeyEvent.KEYCODE_C)))
        // Ctrl+/ is Ctrl+Shift+7 there.
        assertEquals("<SHORTCUTS>", es(ctrl(KeyEvent.KEYCODE_7, shift = true)))
    }

    @Test
    fun shortcuts() {
        fun cs(code: Int) = ctrl(code, shift = true)
        assertEquals("<NEW_TAB><CLOSE_TAB><COPY><PASTE><SEARCH_HOSTS><NEXT_PANE>", us(
            cs(KeyEvent.KEYCODE_T), cs(KeyEvent.KEYCODE_W), cs(KeyEvent.KEYCODE_C), cs(KeyEvent.KEYCODE_V),
            cs(KeyEvent.KEYCODE_K), cs(KeyEvent.KEYCODE_O),
        ))
        assertEquals("<NEXT_TAB><PREV_TAB><NEXT_TAB><PREV_TAB>", us(
            ctrl(KeyEvent.KEYCODE_TAB), cs(KeyEvent.KEYCODE_TAB), cs(KeyEvent.KEYCODE_RIGHT_BRACKET), cs(KeyEvent.KEYCODE_LEFT_BRACKET),
        ))
        assertEquals("<ZOOM_IN><ZOOM_OUT><ZOOM_RESET><ZOOM_IN>", us(
            cs(KeyEvent.KEYCODE_EQUALS), cs(KeyEvent.KEYCODE_MINUS), cs(KeyEvent.KEYCODE_0), cs(KeyEvent.KEYCODE_NUMPAD_ADD),
        ))
        assertEquals("<SHORTCUTS><SHORTCUTS>", us(ctrl(KeyEvent.KEYCODE_SLASH), cs(KeyEvent.KEYCODE_SLASH)))
        assertEquals("<SCROLL_PAGE_UP><SCROLL_PAGE_DOWN><PASTE><COPY>", us(
            shift(KeyEvent.KEYCODE_PAGE_UP), shift(KeyEvent.KEYCODE_PAGE_DOWN), shift(KeyEvent.KEYCODE_INSERT), ctrl(KeyEvent.KEYCODE_INSERT),
        ))
        // Spanish: the + key zooms in.
        assertEquals("<ZOOM_IN>", es(cs(KeyEvent.KEYCODE_RIGHT_BRACKET)))
        // With Alt they go to the terminal.
        assertEquals("\u001b\u0014", us(KeyPress(KeyEvent.KEYCODE_T, ctrl = true, shift = true, alt = true)))
    }

    @Test
    fun keysForTheSystem() {
        assertEquals("<unhandled>", us(k(KeyEvent.KEYCODE_VOLUME_UP)))
        assertEquals("<unhandled>", us(k(KeyEvent.KEYCODE_CTRL_LEFT)))
        assertEquals("<unhandled>", us(k(KeyEvent.KEYCODE_BACK)))
    }

    @Test
    fun deadKeyComposition() {
        assertEquals("ñ", DeadKeys.compose(0x02DC, 'n'.code))
        assertEquals("ç", DeadKeys.compose(0x00B8, 'c'.code))
        assertEquals(null, DeadKeys.compose(0x00B4, 'x'.code))
        assertEquals("~", DeadKeys.spacing('~'.code))
    }
}
