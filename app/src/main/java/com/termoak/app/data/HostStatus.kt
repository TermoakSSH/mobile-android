package com.termoak.app.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.termoak.ffi.HostProbe
import com.termoak.ffi.HostReach
import com.termoak.ffi.ItemRef
import com.termoak.ffi.SshHost
import com.termoak.ffi.TermoakCore

/** What the status dot of a host shows. */
sealed class HostDot {
    /** It accepted a connection, in [ms] milliseconds (green). */
    data class Up(val ms: Int) : HostDot()
    /** Refused, timed out or its name doesn't resolve (red). */
    data object Down : HostDot()
    /** Not checked (behind jump hosts, Strict vault, turned off) or not yet: no dot. */
    data object Unknown : HostDot()
}

/** The last check of a host: its dot, when, and the `address:port` checked. */
data class HostCheck(val dot: HostDot, val checkedAt: Long, val target: String)

/**
 * Rules of the status dots of the hosts lists (Settings → Check host
 * status), like the desktop's and iOS's: the hosts on screen are checked
 * from this device at most once a minute each, again at once when their
 * address or port changed. Pure logic (JVM tests).
 */
object HostStatusPlan {
    /** A host is checked again after a minute (the desktop's rhythm). */
    const val INTERVAL_MS = 60_000L
    /** How often a list on screen looks for hosts due. */
    const val TICK_MS = 15_000L

    /** `address:port` of a host (the default port of its protocol when it has none). */
    fun target(address: String, port: UInt?, telnet: Boolean): String = "${address.lowercase()}:${port ?: if (telnet) 23u else 22u}"

    /**
     * The hosts (keys) to check now, in the order given: never checked,
     * checked a minute ago or more, or whose target changed since; not those
     * being checked, nor a key twice.
     */
    fun due(hosts: List<Pair<String, String>>, checks: Map<String, HostCheck>, inFlight: Set<String>, now: Long, interval: Long = INTERVAL_MS): List<String> {
        val seen = mutableSetOf<String>()
        val out = mutableListOf<String>()
        for ((key, target) in hosts) {
            if (key in inFlight || !seen.add(key)) continue
            val c = checks[key]
            if (c == null || c.target != target || now - c.checkedAt >= interval) out += key
        }
        return out
    }

    /** "12 ms", "1.2 s". */
    fun latency(ms: Int): String {
        if (ms < 1000) return "$ms ms"
        val tenths = Math.round(ms / 100.0).toInt()
        return "${tenths / 10}.${tenths % 10} s"
    }

    /** The hosts turned off after turning one on or off (ids, as the engine takes them). */
    fun toggled(off: List<String>, id: String): List<String> = if (id in off) off - id else off + id
}

/**
 * The last check of each host (by its key), shared by the list, the grid
 * and every group screen (Compose state). The checks are the engine's
 * `probeHosts`: a TCP connection to the host's port through its proxy, no
 * SSH and no sign-in.
 */
object HostStatusStore {
    /** Settings → Check host status (kept up to date by the hosts screen). */
    var enabled by mutableStateOf(false)
    private val checks = mutableStateMapOf<String, HostCheck>()
    private val inFlight = mutableSetOf<String>()

    fun dot(host: SshHost): HostDot = checks[host.uid]?.dot ?: HostDot.Unknown

    fun target(h: SshHost): String = HostStatusPlan.target(h.address, h.settings.port, h.isTelnet)

    /** Checks the hosts given that are due; [off]: ids of the hosts whose check is turned off. */
    suspend fun refresh(hosts: List<SshHost>, core: TermoakCore, off: List<String>) {
        val due = HostStatusPlan.due(hosts.map { it.uid to target(it) }, checks, inFlight, System.currentTimeMillis()).toSet()
        if (due.isEmpty()) return
        val todo = hosts.filter { it.uid in due }.distinctBy { it.uid }
        inFlight += due
        try {
            val probes = runCatching { core.probeHosts(todo.map { ItemRef(it.accountId, it.id) }, off, 0u) }.getOrNull() ?: return
            val now = System.currentTimeMillis()
            for (p in probes) {
                val key = uidOf(p.accountId, p.hostId)
                val h = todo.firstOrNull { it.uid == key } ?: continue
                checks[key] = HostCheck(dotOf(p), now, target(h))
            }
        } finally {
            inFlight -= due
        }
    }

    /** Forgets a host's check (turned off or on: checked again next time). */
    fun forget(host: SshHost) {
        checks.remove(host.uid)
    }

    private fun dotOf(p: HostProbe): HostDot = when (p.status) {
        HostReach.UP -> HostDot.Up((p.ms ?: 0u).toInt())
        HostReach.DOWN -> HostDot.Down
        else -> HostDot.Unknown
    }
}
