package com.termoak.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Invitation links (read by the engine's parseLink, which has its own tests): the same server. */
class JoinLinkRefTest {
    @Test
    fun sameServer() {
        assertTrue(JoinLinkRef.sameServer("https://Termoak.com/", "https://termoak.com"))
        assertTrue(JoinLinkRef.sameServer("https://x.com/sub/", "https://x.com/sub"))
        assertFalse(JoinLinkRef.sameServer("https://x.com/sub", "https://x.com"))
        assertFalse(JoinLinkRef.sameServer(null, "https://x.com"))
    }
}
