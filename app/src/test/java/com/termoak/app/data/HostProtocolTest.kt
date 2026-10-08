package com.termoak.app.data

import com.termoak.ffi.SshHost
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    private fun t(user: String?, host: String, port: UInt?) = QuickTarget(ssh, user, host, port)
    private fun tn(user: String?, host: String, port: UInt?) = QuickTarget(telnet, user, host, port)

    @Test
    fun telnetUrls() {
        assertEquals(tn(null, "10.0.0.1", null), QuickTarget.parse("telnet://10.0.0.1"))
        assertEquals(tn("admin", "switch1", 2323u), QuickTarget.parse(" TELNET://admin@switch1:2323/ "))
        // A bare name is fine after the scheme.
        assertEquals(tn(null, "router", null), QuickTarget.parse("telnet://router"))
        assertEquals(tn(null, "2001:db8::1", 23u), QuickTarget.parse("telnet://[2001:db8::1]:23"))
        assertEquals(tn(null, "towel.blinkenlights.nl", 23u), QuickTarget.parse("telnet towel.blinkenlights.nl 23"))
        assertEquals(tn(null, "bbs", null), QuickTarget.parse("telnet bbs"))
        assertEquals(t(null, "web", null), QuickTarget.parse("ssh://web"))
        assertNull(QuickTarget.parse("telnet://"))
        assertNull(QuickTarget.parse("telnet://h:0"))
        assertNull(QuickTarget.parse("telnet://h:70000"))
        assertNull(QuickTarget.parse("telnet://@h"))
        assertNull(QuickTarget.parse("telnet h 23 extra"))
        assertEquals("telnet://a@h:2323", tn("a", "h", 2323u).display())
        assertEquals("telnet://h", tn(null, "h", null).display())
        assertEquals(23u, tn(null, "h", null).effectivePort)
        assertEquals(2323u, tn(null, "h", 2323u).effectivePort)
    }

    @Test
    fun sshAddresses() {
        assertEquals(t("root", "10.0.0.5", null), QuickTarget.parse("root@10.0.0.5"))
        assertEquals(t("ana", "web.example.com", 2222u), QuickTarget.parse(" ana@web.example.com:2222 "))
        assertEquals(t(null, "db.local", 22u), QuickTarget.parse("db.local:22"))
        assertEquals(t("pi", "raspberrypi.lan", 2200u), QuickTarget.parse("ssh pi@raspberrypi.lan -p 2200"))
        assertEquals(t(null, "2001:db8::1", 2222u), QuickTarget.parse("[2001:db8::1]:2222"))
        assertEquals(t("me", "2001:db8::1", null), QuickTarget.parse("me@2001:db8::1"))
        assertEquals(22u, t(null, "h", null).effectivePort)
        assertEquals("ana@h.io:22", t("ana", "h.io", 22u).display())
        assertEquals("[::1]:2222", t(null, "::1", 2222u).display())
    }

    @Test
    fun aSearchWordIsNotAnAddress() {
        assertNull(QuickTarget.parse("web"))
        assertNull(QuickTarget.parse(""))
        assertNull(QuickTarget.parse("telnet"))
        assertNull(QuickTarget.parse("@host.com"))
        assertNull(QuickTarget.parse("host.com:0"))
        assertNull(QuickTarget.parse("host.com:99999"))
        assertNull(QuickTarget.parse("two words.com"))
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
