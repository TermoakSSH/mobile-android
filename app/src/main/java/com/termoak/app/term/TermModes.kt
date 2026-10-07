package com.termoak.app.term

/** Mouse reporting the remote program asked for (`CSI ? 9/1000/1002/1003 h`). */
enum class MouseTracking {
    OFF,
    /** 9: presses only. */
    X10,
    /** 1000: presses, releases and the wheel. */
    NORMAL,
    /** 1002: also movement while a button is held. */
    BUTTON,
    /** 1003: all movement. */
    ANY,
}

/** How mouse reports are encoded (`CSI ? 1005/1006/1015 h`). */
enum class MouseEncoding { DEFAULT, UTF8, SGR, URXVT }

/**
 * Follows the terminal output for the modes the emulator doesn't expose:
 * mouse reporting and the application keypad (DECKPAM, `ESC =` / `ESC >`).
 * A small parser that only looks at escape sequences (split across chunks
 * too) and skips OSC/DCS strings. Fed from the terminal's thread and read
 * from the UI one.
 */
class ModeTracker {
    @Volatile var mouse = MouseTracking.OFF
        private set
    @Volatile var encoding = MouseEncoding.DEFAULT
        private set
    @Volatile var appKeypad = false
        private set

    private enum class State { GROUND, ESC, CSI, STRING, STRING_ESC }

    private var state = State.GROUND
    private val params = StringBuilder()
    private var private = false
    private var intermediate = 0

    /** Back to the initial modes (`ESC c`, reconnection). */
    @Synchronized
    fun reset() {
        mouse = MouseTracking.OFF
        encoding = MouseEncoding.DEFAULT
        appKeypad = false
        state = State.GROUND
    }

    @Synchronized
    fun feed(data: ByteArray) {
        for (b in data) step(b.toInt() and 0xFF)
    }

    private fun step(b: Int) {
        when (state) {
            State.GROUND -> if (b == 0x1b) state = State.ESC
            State.ESC -> escape(b)
            State.CSI -> when (b) {
                0x1b -> state = State.ESC
                in 0x30..0x3f -> {
                    if (b == '?'.code && params.isEmpty()) private = true else if (params.length < 64) params.append(b.toChar())
                }
                in 0x20..0x2f -> intermediate = b
                in 0x40..0x7e -> {
                    csi(b.toChar())
                    state = State.GROUND
                }
                // Control characters inside a sequence are executed; the sequence goes on.
                else -> Unit
            }
            State.STRING -> when (b) {
                0x07 -> state = State.GROUND
                0x1b -> state = State.STRING_ESC
            }
            State.STRING_ESC -> if (b == '\\'.code) state = State.GROUND else escape(b)
        }
    }

    private fun escape(b: Int) {
        state = State.GROUND
        when (b.toChar()) {
            '[' -> {
                params.setLength(0)
                private = false
                intermediate = 0
                state = State.CSI
            }
            // OSC, DCS, APC, PM, SOS: strings until BEL or ST.
            ']', 'P', '_', '^', 'X' -> state = State.STRING
            'c' -> reset()
            '=' -> appKeypad = true
            '>' -> appKeypad = false
            else -> if (b == 0x1b) state = State.ESC
        }
    }

    private fun csi(final: Char) {
        if (!private) {
            // DECSTR (soft reset): CSI ! p.
            if (intermediate == '!'.code && final == 'p') appKeypad = false
            return
        }
        if (final != 'h' && final != 'l') return
        val on = final == 'h'
        for (p in params.split(';')) {
            when (p.toIntOrNull()) {
                9 -> setMouse(MouseTracking.X10, on)
                1000 -> setMouse(MouseTracking.NORMAL, on)
                1002 -> setMouse(MouseTracking.BUTTON, on)
                1003 -> setMouse(MouseTracking.ANY, on)
                1005 -> setEncoding(MouseEncoding.UTF8, on)
                1006 -> setEncoding(MouseEncoding.SGR, on)
                1015 -> setEncoding(MouseEncoding.URXVT, on)
                66 -> appKeypad = on
            }
        }
    }

    private fun setMouse(mode: MouseTracking, on: Boolean) {
        if (on) mouse = mode else if (mouse == mode || mode != MouseTracking.X10) mouse = MouseTracking.OFF
    }

    private fun setEncoding(e: MouseEncoding, on: Boolean) {
        if (on) encoding = e else if (encoding == e) encoding = MouseEncoding.DEFAULT
    }
}

/** Mouse reports for the remote program (xterm's protocol). */
object MouseEncoder {
    enum class Button(val code: Int) { LEFT(0), MIDDLE(1), RIGHT(2), NONE(3), WHEEL_UP(64), WHEEL_DOWN(65) }
    enum class Kind { PRESS, RELEASE, MOTION }

    /** The program wants this event with [tracking] ([held]: a button is down). */
    fun wants(tracking: MouseTracking, kind: Kind, held: Boolean): Boolean = when (tracking) {
        MouseTracking.OFF -> false
        MouseTracking.X10 -> kind == Kind.PRESS
        MouseTracking.NORMAL -> kind != Kind.MOTION
        MouseTracking.BUTTON -> kind != Kind.MOTION || held
        MouseTracking.ANY -> true
    }

    /**
     * Bytes of a mouse event at the cell [col], [row] (from 0); `null` when
     * the position can't be encoded (beyond column/row 223 without SGR or
     * URXVT encoding). X10 mode has no modifiers.
     */
    fun encode(
        button: Button,
        kind: Kind,
        col: Int,
        row: Int,
        encoding: MouseEncoding,
        tracking: MouseTracking = MouseTracking.NORMAL,
        shift: Boolean = false,
        alt: Boolean = false,
        ctrl: Boolean = false,
    ): ByteArray? {
        val x = col.coerceAtLeast(0) + 1
        val y = row.coerceAtLeast(0) + 1
        var b = button.code
        if (kind == Kind.MOTION) b += 32
        if (tracking != MouseTracking.X10) {
            if (shift) b += 4
            if (alt) b += 8
            if (ctrl) b += 16
        }
        val text = when (encoding) {
            MouseEncoding.SGR -> "\u001b[<$b;$x;${y}${if (kind == Kind.RELEASE) 'm' else 'M'}"
            MouseEncoding.URXVT -> {
                val code = if (kind == Kind.RELEASE) 3 + (b and 0b11100) else b
                "\u001b[${code + 32};$x;${y}M"
            }
            MouseEncoding.DEFAULT, MouseEncoding.UTF8 -> {
                val code = if (kind == Kind.RELEASE) 3 + (b and 0b11100) else b
                val limit = if (encoding == MouseEncoding.UTF8) 2015 else 223
                if (x > limit || y > limit) return null
                return if (encoding == MouseEncoding.UTF8) {
                    ("\u001b[M" + String(Character.toChars(32 + code)) + String(Character.toChars(32 + x)) + String(Character.toChars(32 + y)))
                        .toByteArray(Charsets.UTF_8)
                } else {
                    byteArrayOf(0x1b, '['.code.toByte(), 'M'.code.toByte(), (32 + code).toByte(), (32 + x).toByte(), (32 + y).toByte())
                }
            }
        }
        return text.toByteArray(Charsets.US_ASCII)
    }
}
