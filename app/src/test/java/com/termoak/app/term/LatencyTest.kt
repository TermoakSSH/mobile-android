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
