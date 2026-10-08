package com.termoak.app.data

import com.termoak.ffi.LinkTarget
import com.termoak.ffi.SshHost
import com.termoak.ffi.parseQuickConnect

/**
 * The protocol of a host (`SshHost.protocol`): `ssh` or `telnet`, the
 * desktop's `HostProtocol`. A later app's protocol comes as it is and is
 * kept when the host is saved again.
 */
object HostProtocol {
    const val SSH = "ssh"
    const val TELNET = "telnet"

    fun isTelnet(protocol: String?): Boolean = protocol?.trim().equals(TELNET, ignoreCase = true)

    /** Port used when neither the host nor its groups set one: 22 for SSH, 23 for Telnet. */
    fun defaultPort(protocol: String?): UInt = if (isTelnet(protocol)) 23u else 22u

    /**
     * Text of the port field after the protocol changes from [from] to [to]
     * (the desktop's `port_after_switch`): empty or the old protocol's
     * default becomes the new one's, written out for Telnet (so older apps
     * that only know SSH don't reach the SSH port of a Telnet host) and left
     * empty for SSH; another port, or text that is not a port, stays.
     */
    fun portAfterSwitch(from: String, to: String, text: String): String {
        val t = text.trim()
        if (from.equals(to, ignoreCase = true)) return t
        val port = if (t.isEmpty()) null else t.toUIntOrNull()?.takeIf { it in 1u..65535u } ?: return t
        return when {
            port != null && port != defaultPort(from) -> port.toString()
            !isTelnet(to) -> ""
            else -> defaultPort(to).toString()
        }
    }

    /** "SSH", "Telnet", or a later app's protocol as it is. */
    fun displayName(protocol: String?): String = when {
        isTelnet(protocol) -> "Telnet"
        protocol.isNullOrBlank() || protocol.equals(SSH, ignoreCase = true) -> "SSH"
        else -> protocol
    }
}

/**
 * The host editor's address field, as on iOS (`splitAddress`): `user@host`
 * and `host:port` typed there fill the user and the port when those are
 * empty, and `ssh://` or `telnet://` in front choose the protocol.
 */
data class AddressSplit(val address: String, val user: String?, val port: String?, val protocol: String?) {
    companion object {
        fun of(text: String, user: String, port: String): AddressSplit {
            var addr = text.trim()
            var protocol: String? = null
            for ((scheme, proto) in listOf("telnet://" to HostProtocol.TELNET, "ssh://" to HostProtocol.SSH)) {
                if (addr.lowercase().startsWith(scheme)) {
                    addr = addr.substring(scheme.length).trimEnd('/')
                    protocol = proto
                }
            }
            var newUser: String? = null
            val at = addr.lastIndexOf('@')
            if (at >= 0) {
                val u = addr.substring(0, at)
                val rest = addr.substring(at + 1)
                if (u.isNotEmpty() && rest.isNotEmpty() && user.isBlank()) {
                    newUser = u
                    addr = rest
                }
            }
            var newPort: String? = null
            // Only one colon: an IPv6 address has several.
            val parts = addr.split(':')
            val p = parts.getOrNull(1)?.trim()?.toUIntOrNull()?.takeIf { it in 1u..65535u }
            if (parts.size == 2 && p != null && parts[0].isNotEmpty() && port.isBlank()) {
                newPort = p.toString()
                addr = parts[0]
            }
            return AddressSplit(addr, newUser, newPort, protocol)
        }
    }
}

/** A Telnet host: no keys, jump hosts, SFTP, tunnels or server sessions, and unencrypted. */
val SshHost.isTelnet: Boolean get() = HostProtocol.isTelnet(protocol)

/**
 * An address typed in quick connect: `user@host:port`, `host:port`,
 * `[v6]:port`, `ssh user@host -p port`, and for Telnet
 * `telnet://[user@]host[:port]` or `telnet host [port]` (the desktop's
 * host_picker.rs `parse_quick_target`).
 */
data class QuickTarget(
    val protocol: String,
    val user: String?,
    val host: String,
    val port: UInt?,
) {
    val telnet: Boolean get() = HostProtocol.isTelnet(protocol)

    /** `user@host:port` as typed back (IPv6 in brackets with a port), with `telnet://` in front for Telnet. */
    fun display(): String {
        val h = if (port != null && host.contains(':')) "[$host]" else host
        return (if (telnet) "telnet://" else "") + (user?.let { "$it@" } ?: "") + h + (port?.let { ":$it" } ?: "")
    }

    /** The port it connects to. */
    val effectivePort: UInt get() = port ?: HostProtocol.defaultPort(protocol)

    companion object {
        /**
         * Reads a typed address with the engine's rules ([parseQuickConnect],
         * the same as the desktop's and iOS's), or `null`. Only text that
         * looks like an address (with `@`, `:` or `.`, or a scheme) counts,
         * so a plain search word is not taken as a host.
         */
        fun parse(input: String): QuickTarget? {
            val text = input.trim().takeIf { it.isNotEmpty() } ?: return null
            val t = runCatching { parseQuickConnect(text) }.getOrNull() as? LinkTarget.QuickConnect ?: return null
            val protocol = if (HostProtocol.isTelnet(t.protocol)) HostProtocol.TELNET else HostProtocol.SSH
            return QuickTarget(protocol, t.user?.takeIf { it.isNotEmpty() }, t.host, t.port)
        }
    }
}
