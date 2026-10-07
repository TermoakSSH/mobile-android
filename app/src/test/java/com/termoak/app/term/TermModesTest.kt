package com.termoak.app.term

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TermModesTest {
    private fun ModeTracker.feed(s: String) = feed(s.toByteArray())

    @Test
    fun mouseModes() {
        val t = ModeTracker()
        assertEquals(MouseTracking.OFF, t.mouse)
        t.feed("hello\u001b[?1000h\u001b[?1006hworld")
        assertEquals(MouseTracking.NORMAL, t.mouse)
        assertEquals(MouseEncoding.SGR, t.encoding)
        // Several modes in one sequence (as tmux does).
        t.feed("\u001b[?1002;1006h")
        assertEquals(MouseTracking.BUTTON, t.mouse)
        t.feed("\u001b[?1002l\u001b[?1006l")
        assertEquals(MouseTracking.OFF, t.mouse)
        assertEquals(MouseEncoding.DEFAULT, t.encoding)
    }

    @Test
    fun sequencesSplitAcrossChunks() {
        val t = ModeTracker()
        t.feed("\u001b[?10")
        t.feed("03")
        t.feed("h")
        assertEquals(MouseTracking.ANY, t.mouse)
        t.feed("\u001b")
        t.feed("=")
        assertTrue(t.appKeypad)
        t.feed("\u001b>")
        assertFalse(t.appKeypad)
    }

    @Test
    fun stringsAreSkipped() {
        val t = ModeTracker()
        // Window titles (OSC) with the sequence's text, ended by BEL and by ST.
        t.feed("\u001b]0;[?1000h\u0007")
        t.feed("\u001b]2;x [?1000h\u001b\\")
        // A DCS string (tmux passthrough) too.
        t.feed("\u001bPtmux;[?1003h\u001b\\")
        assertEquals(MouseTracking.OFF, t.mouse)
        t.feed("\u001b[?1000h")
        assertEquals(MouseTracking.NORMAL, t.mouse)
    }

    @Test
    fun resets() {
        val t = ModeTracker()
        t.feed("\u001b[?1000h\u001b=")
        t.feed("\u001bc")
        assertEquals(MouseTracking.OFF, t.mouse)
        assertFalse(t.appKeypad)
        t.feed("\u001b[?66h")
        assertTrue(t.appKeypad)
        // DECSTR (soft reset).
        t.feed("\u001b[!p")
        assertFalse(t.appKeypad)
    }

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
        assertEquals("\u001b[32;301;1M", s(m.encode(MouseEncoder.Button.LEFT, MouseEncoder.Kind.PRESS, 300, 0, MouseEncoding.URXVT)))
    }

    @Test
    fun whichEventsAreReported() {
        val m = MouseEncoder
        assertTrue(m.wants(MouseTracking.X10, MouseEncoder.Kind.PRESS, held = false))
        assertFalse(m.wants(MouseTracking.X10, MouseEncoder.Kind.RELEASE, held = false))
        assertFalse(m.wants(MouseTracking.NORMAL, MouseEncoder.Kind.MOTION, held = true))
        assertTrue(m.wants(MouseTracking.BUTTON, MouseEncoder.Kind.MOTION, held = true))
        assertFalse(m.wants(MouseTracking.BUTTON, MouseEncoder.Kind.MOTION, held = false))
        assertTrue(m.wants(MouseTracking.ANY, MouseEncoder.Kind.MOTION, held = false))
        assertFalse(m.wants(MouseTracking.OFF, MouseEncoder.Kind.PRESS, held = false))
    }
}
