package com.termoak.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Invitation links, with the iOS app's rules (servers under a path included). */
class JoinLinkRefTest {
    @Test
    fun webLinks() {
        assertEquals(JoinLinkRef("https://termoak.com", "abc_123-X"), JoinLinkRef.parse("https://termoak.com/join/abc_123-X"))
        assertEquals(JoinLinkRef("https://termoak.com", "tok"), JoinLinkRef.parse("  https://termoak.com/join/tok/  "))
        assertEquals(JoinLinkRef("http://10.0.0.2:7733", "tok"), JoinLinkRef.parse("http://10.0.0.2:7733/join/tok"))
        // The API path and the website's language are not part of the server.
        assertEquals(JoinLinkRef("https://termoak.com", "tok"), JoinLinkRef.parse("https://termoak.com/api/v1/join/tok"))
        assertEquals(JoinLinkRef("https://termoak.com", "tok"), JoinLinkRef.parse("https://termoak.com/es/join/tok"))
    }

    @Test
    fun serversUnderAPathKeepIt() {
        assertEquals(JoinLinkRef("https://example.com/termoak", "tok"), JoinLinkRef.parse("https://example.com/termoak/join/tok"))
        assertEquals(JoinLinkRef("https://example.com/a/b", "tok"), JoinLinkRef.parse("https://example.com/a/b/api/v1/join/tok"))
        assertEquals(JoinLinkRef("https://example.com:8443/ssh", "tok"), JoinLinkRef.parse("https://example.com:8443/ssh/join/tok?x=1"))
    }

    @Test
    fun appLinks() {
        assertEquals(
            JoinLinkRef("https://example.com/termoak", "tok"),
            JoinLinkRef.parse("termoak://join?server=https://example.com/termoak/&token=tok"),
        )
        assertEquals(
            JoinLinkRef("https://termoak.com", "tok"),
            JoinLinkRef.parse("termoak://join?server=https%3A%2F%2Ftermoak.com&token=tok"),
        )
        // The token can also come as the path.
        assertEquals(JoinLinkRef("https://termoak.com", "tok"), JoinLinkRef.parse("termoak://join/tok?server=https://termoak.com"))
    }

    @Test
    fun invalidLinks() {
        listOf(
            null, "", "hello", "https://termoak.com/", "https://termoak.com/join/", "https://termoak.com/join/bad token",
            "https://termoak.com/join/a.b", "ftp://termoak.com/join/tok", "termoak://invite?server=https://x&token=t",
            "termoak://join?token=tok", "termoak://join?server=ftp://x&token=tok", "termoak://join?server=https://x",
        ).forEach { assertNull(it, JoinLinkRef.parse(it)) }
    }

    @Test
    fun sameServer() {
        assertTrue(JoinLinkRef.sameServer("https://Termoak.com/", "https://termoak.com"))
        assertTrue(JoinLinkRef.sameServer("https://x.com/sub/", "https://x.com/sub"))
        assertFalse(JoinLinkRef.sameServer("https://x.com/sub", "https://x.com"))
        assertFalse(JoinLinkRef.sameServer(null, "https://x.com"))
    }
}
