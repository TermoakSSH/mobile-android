package com.termoak.app.term

import com.termoak.ffi.MouseEncoding
import com.termoak.ffi.MouseMode

// The modes the remote program turned on (mouse reporting and its encoding,
// bracketed paste, application cursor and keypad, cursor blink...) come from
// the emulator itself: `TerminalScreen.modes()`.

/** Mouse reports for the remote program (xterm's protocol). */
object MouseEncoder {
    enum class Button(val code: Int) { LEFT(0), MIDDLE(1), RIGHT(2), NONE(3), WHEEL_UP(64), WHEEL_DOWN(65) }
    enum class Kind { PRESS, RELEASE, MOTION }

    /** The program wants this event with [mode] ([held]: a button is down). */
    fun wants(mode: MouseMode, kind: Kind, held: Boolean): Boolean = when (mode) {
        MouseMode.OFF -> false
        MouseMode.CLICK -> kind != Kind.MOTION
        MouseMode.DRAG -> kind != Kind.MOTION || held
        MouseMode.MOTION -> true
    }

    /**
     * Bytes of a mouse event at the cell [col], [row] (from 0); `null` when
     * the position can't be encoded (beyond column/row 223 without SGR
     * encoding).
     */
    fun encode(
        button: Button,
        kind: Kind,
        col: Int,
        row: Int,
        encoding: MouseEncoding,
        shift: Boolean = false,
        alt: Boolean = false,
        ctrl: Boolean = false,
    ): ByteArray? {
        val x = col.coerceAtLeast(0) + 1
        val y = row.coerceAtLeast(0) + 1
        var b = button.code
        if (kind == Kind.MOTION) b += 32
        if (shift) b += 4
        if (alt) b += 8
        if (ctrl) b += 16
        val text = when (encoding) {
            MouseEncoding.SGR -> "\u001b[<$b;$x;${y}${if (kind == Kind.RELEASE) 'm' else 'M'}"
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
