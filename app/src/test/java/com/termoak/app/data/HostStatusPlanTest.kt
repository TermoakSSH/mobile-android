package com.termoak.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** When the hosts on screen are checked, and how the time reads (the desktop's and iOS's rules). */
class HostStatusPlanTest {
    @Test
    fun targets() {
        assertEquals("web.example.com:22", HostStatusPlan.target("Web.Example.com", null, telnet = false))
        assertEquals("router:23", HostStatusPlan.target("router", null, telnet = true))
        assertEquals("db:2222", HostStatusPlan.target("db", 2222u, telnet = false))
    }

    @Test
    fun whichHostsAreDue() {
        val now = 1_000_000L
        val checks = mapOf(
            "a" to HostCheck(HostDot.Down, now - 10_000, "a:22"),
            "b" to HostCheck(HostDot.Up(5), now - 61_000, "b:22"),
            "c" to HostCheck(HostDot.Up(5), now - 1_000, "c:22"),
        )
        val hosts = listOf("a" to "a:22", "b" to "b:22", "c" to "c:2222", "d" to "d:22", "d" to "d:22", "e" to "e:22")
        // a: checked 10 s ago; b: a minute ago; c: its port changed; d: never (once); e: being checked.
        assertEquals(listOf("b", "c", "d"), HostStatusPlan.due(hosts, checks, inFlight = setOf("e"), now = now))
    }

    @Test
    fun latencyAndToggles() {
        assertEquals("12 ms", HostStatusPlan.latency(12))
        assertEquals("1.2 s", HostStatusPlan.latency(1_249))
        assertEquals("2.0 s", HostStatusPlan.latency(1_999))
        assertEquals(listOf("x", "y"), HostStatusPlan.toggled(listOf("x"), "y"))
        assertEquals(listOf("x"), HostStatusPlan.toggled(listOf("x", "y"), "y"))
    }
}
