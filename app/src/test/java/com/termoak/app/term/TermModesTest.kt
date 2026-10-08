package com.termoak.app.term

import com.termoak.ffi.MouseEncoding
import com.termoak.ffi.MouseMode
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Mouse reports (the modes themselves come from the engine's TerminalScreen.modes()). */
class TermModesTest {
    private fun s(b: ByteArray?) = b?.let { String(it, Charsets.UTF_8) }

    @Test
    fun mouseReports() {
        val m = MouseEncoder
        assertEquals("\u001b[<0;3;2M", s(m.encode(MouseEncoder.Button.LEFT, MouseEncoder.Kind.PRESS, 2, 1, MouseEncoding.SGR)))
        assertEquals("\u001b[<0;3;2m", s(m.encode(MouseEncoder.Button.LEFT, MouseEncoder.Kind.RELEASE, 2, 1, MouseEncoding.SGR)))
        assertEquals("\u001b[<2;1;1M", s(m.encode(MouseEncoder.Button.RIGHT, MouseEncoder.Kind.PRESS, 0, 0, MouseEncoding.SGR)))
        assertEquals("\u001b[<64;10;5M", s(m.encode(MouseEncoder.Button.WHEEL_UP, MouseEncoder.Kind.PRESS, 9, 4, MouseEncoding.SGR)))
        assertEquals("\u001b[<32;4;4M", s(m.encode(MouseEncoder.Button.LEFT, MouseEncoder.Kind.MOTION, 3, 3, MouseEncoding.SGR)))
        // Ctrl+click: +16.
        assertEquals("\u001b[<16;1;1M", s(m.encode(MouseEncoder.Button.LEFT, MouseEncoder.Kind.PRESS, 0, 0, MouseEncoding.SGR, ctrl = true)))
        // Classic encoding: 32 + values, releases as button 3.
        assertArrayEquals(
            byteArrayOf(0x1b, '['.code.toByte(), 'M'.code.toByte(), 32, 35, 34),
            m.encode(MouseEncoder.Button.LEFT, MouseEncoder.Kind.PRESS, 2, 1, MouseEncoding.DEFAULT),
        )
        assertArrayEquals(
            byteArrayOf(0x1b, '['.code.toByte(), 'M'.code.toByte(), 35, 33, 33),
            m.encode(MouseEncoder.Button.LEFT, MouseEncoder.Kind.RELEASE, 0, 0, MouseEncoding.DEFAULT),
        )
        assertNull(m.encode(MouseEncoder.Button.LEFT, MouseEncoder.Kind.PRESS, 300, 0, MouseEncoding.DEFAULT))
        // SGR has no limit.
        assertEquals("\u001b[<0;301;1M", s(m.encode(MouseEncoder.Button.LEFT, MouseEncoder.Kind.PRESS, 300, 0, MouseEncoding.SGR)))
    }

    @Test
    fun whichEventsAreReported() {
        val m = MouseEncoder
        assertTrue(m.wants(MouseMode.CLICK, MouseEncoder.Kind.PRESS, held = false))
        assertTrue(m.wants(MouseMode.CLICK, MouseEncoder.Kind.RELEASE, held = true))
        assertFalse(m.wants(MouseMode.CLICK, MouseEncoder.Kind.MOTION, held = true))
        assertTrue(m.wants(MouseMode.DRAG, MouseEncoder.Kind.MOTION, held = true))
        assertFalse(m.wants(MouseMode.DRAG, MouseEncoder.Kind.MOTION, held = false))
        assertTrue(m.wants(MouseMode.MOTION, MouseEncoder.Kind.MOTION, held = false))
        assertFalse(m.wants(MouseMode.OFF, MouseEncoder.Kind.PRESS, held = false))
    }
}
