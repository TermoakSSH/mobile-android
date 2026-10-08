package com.termoak.app.data

import com.termoak.ffi.SshHost
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The host editor's protocol switch and quick connect's addresses (the desktop's rules). */
class HostProtocolTest {
    private val ssh = HostProtocol.SSH
    private val telnet = HostProtocol.TELNET

    @Test
    fun defaultPorts() {
        assertEquals(22u, HostProtocol.defaultPort(ssh))
        assertEquals(23u, HostProtocol.defaultPort(telnet))
        assertEquals(23u, HostProtocol.defaultPort("TELNET"))
        assertEquals(22u, HostProtocol.defaultPort(null))
        // A later app's protocol is not Telnet.
        assertEquals(22u, HostProtocol.defaultPort("mosh"))
        assertTrue(SshHost(label = "r", address = "r", protocol = "telnet").isTelnet)
        assertFalse(SshHost(label = "s", address = "s").isTelnet)
    }

    @Test
    fun theDefaultPortFollowsTheProtocol() {
        // Empty or the old default: the new protocol's (written out for Telnet, empty for SSH).
        assertEquals("23", HostProtocol.portAfterSwitch(ssh, telnet, ""))
        assertEquals("23", HostProtocol.portAfterSwitch(ssh, telnet, "22"))
        assertEquals("23", HostProtocol.portAfterSwitch(ssh, telnet, " 22 "))
        assertEquals("", HostProtocol.portAfterSwitch(telnet, ssh, "23"))
        assertEquals("", HostProtocol.portAfterSwitch(telnet, ssh, ""))
    }

    @Test
    fun anotherPortStays() {
        assertEquals("2222", HostProtocol.portAfterSwitch(ssh, telnet, "2222"))
        assertEquals("2323", HostProtocol.portAfterSwitch(telnet, ssh, "2323"))
        // Port 22 on a Telnet host was set on purpose: it stays when switching to SSH.
        assertEquals("22", HostProtocol.portAfterSwitch(telnet, ssh, "22"))
        // Not a port: left for the form to complain about.
        assertEquals("99999", HostProtocol.portAfterSwitch(ssh, telnet, "99999"))
        // The same protocol: nothing changes.
        assertEquals("22", HostProtocol.portAfterSwitch(ssh, ssh, "22"))
    }
}

/** The host editor's address field (iOS `splitAddress`). */
class AddressSplitTest {
    @Test
    fun userHostAndPort() {
        assertEquals(AddressSplit("web.example.com", "root", "2222", null), AddressSplit.of(" root@web.example.com:2222 ", "", ""))
        // A user or port already written stays; the address keeps what wasn't taken.
        assertEquals(AddressSplit("root@web:2222", null, null, null), AddressSplit.of("root@web:2222", "admin", "22"))
        assertEquals(AddressSplit("web:2222", "root", null, null), AddressSplit.of("root@web:2222", "", "22"))
        // IPv6 has several colons: no port taken.
        assertEquals(AddressSplit("fe80::1", null, null, null), AddressSplit.of("fe80::1", "", ""))
        assertEquals(AddressSplit("web:99999", null, null, null), AddressSplit.of("web:99999", "", ""))
    }

    @Test
    fun schemes() {
        assertEquals(AddressSplit("router", "admin", "2323", HostProtocol.TELNET), AddressSplit.of("telnet://admin@router:2323/", "", ""))
        assertEquals(AddressSplit("box", null, null, HostProtocol.SSH), AddressSplit.of("SSH://box", "", ""))
    }
}
