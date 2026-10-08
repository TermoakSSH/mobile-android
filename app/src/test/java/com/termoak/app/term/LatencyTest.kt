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
