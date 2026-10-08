package com.termoak.app.term

import org.junit.Assert.assertEquals
import org.junit.Test

class LatencyTest {
    @Test
    fun levels() {
        assertEquals(Latency.Level.UNKNOWN, Latency.level(null))
        assertEquals(Latency.Level.UNKNOWN, Latency.level(Double.NaN))
        assertEquals(Latency.Level.GOOD, Latency.level(0.4))
        assertEquals(Latency.Level.GOOD, Latency.level(149.9))
        assertEquals(Latency.Level.FAIR, Latency.level(150.0))
        assertEquals(Latency.Level.FAIR, Latency.level(399.0))
        assertEquals(Latency.Level.POOR, Latency.level(400.0))
    }

    @Test
    fun text() {
        assertEquals("—", Latency.format(null))
        assertEquals("<1 ms", Latency.format(0.3))
        assertEquals("42 ms", Latency.format(42.9))
        assertEquals("1200 ms", Latency.format(1200.0))
    }
}

/** The Connections list's timer. */
class ElapsedTest {
    @org.junit.Test
    fun timer() {
        org.junit.Assert.assertEquals("0:00", Elapsed.format(0))
        org.junit.Assert.assertEquals("4:07", Elapsed.format(247_900))
        org.junit.Assert.assertEquals("1:02:09", Elapsed.format(3_729_000))
        org.junit.Assert.assertEquals("0:00", Elapsed.format(-5))
    }
}

/** When a terminal of the phone reconnects by itself (the iOS app's rule). */
class AutoReconnectTest {
    private fun on(left: Boolean = true, asleep: Boolean = false, closed: Boolean = false, connected: Boolean = false, byProgram: Boolean = false, away: Long = 0) =
        AutoReconnect.onReturn(left, asleep, closed, connected, byProgram, away)

    @org.junit.Test
    fun rule() {
        org.junit.Assert.assertEquals(AutoReconnect.Action.RECONNECT, on(closed = true))
        // `exit`, or closed before leaving, or a sleeping tab: nothing.
        org.junit.Assert.assertEquals(AutoReconnect.Action.NONE, on(closed = true, byProgram = true))
        org.junit.Assert.assertEquals(AutoReconnect.Action.NONE, on(left = false, closed = true))
        org.junit.Assert.assertEquals(AutoReconnect.Action.NONE, on(asleep = true, closed = true))
        // Still looks connected: watch a few seconds, or check it after a long while.
        org.junit.Assert.assertEquals(AutoReconnect.Action.WATCH, on(connected = true, away = 5_000))
        org.junit.Assert.assertEquals(AutoReconnect.Action.CHECK, on(connected = true, away = AutoReconnect.CHECK_AFTER_MS))
        org.junit.Assert.assertEquals(AutoReconnect.Action.NONE, on())
    }
}
