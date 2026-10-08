package com.termoak.app.term

import com.termoak.ffi.ForwardKind
import com.termoak.ffi.PortForward
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** How a tunnel reads, and its "Open in the browser" link (the iOS app's). */
class TunnelTextTest {
    private val local = PortForward(label = "db", hostId = "h", kind = ForwardKind.LOCAL, bindPort = 0u, destHost = "db", destPort = 5432u)

    @Test
    fun summaries() {
        assertEquals("L 127.0.0.1:auto → db:5432", TunnelText.summary(local, null, "server"))
        // The port actually taken when a free one was asked.
        assertEquals("L 127.0.0.1:40123 → db:5432", TunnelText.summary(local, 40123u, "server"))
        val remote = local.copy(kind = ForwardKind.REMOTE, bindAddress = "0.0.0.0", bindPort = 8080u, destHost = "localhost", destPort = 3000u)
        assertEquals("R servidor 0.0.0.0:8080 → localhost:3000", TunnelText.summary(remote, null, "servidor"))
        val socks = local.copy(kind = ForwardKind.DYNAMIC, bindPort = 1080u, destHost = null, destPort = null)
        assertEquals("D SOCKS 127.0.0.1:1080", TunnelText.summary(socks, null, "server"))
    }

    @Test
    fun browserLinks() {
        assertEquals("http://127.0.0.1:40123", TunnelText.browserUrl(local, 40123u))
        assertNull(TunnelText.browserUrl(local, null))
        assertNull(TunnelText.browserUrl(local, 0u))
        assertNull(TunnelText.browserUrl(local.copy(kind = ForwardKind.DYNAMIC), 1080u))
        assertNull(TunnelText.browserUrl(local.copy(kind = ForwardKind.REMOTE), 8080u))
    }
}
