package com.termoak.app.data

import com.termoak.app.R
import com.termoak.app.UiText
import com.termoak.app.toUiText
import com.termoak.app.uiText
import org.json.JSONArray
import org.json.JSONObject
import java.net.Inet6Address
import java.net.InetAddress

/*
 * Private networks (0.7): what the screens need besides the views, like the
 * desktop's src/networks.rs and the web's networks.js (NETWORKS.md §8.1).
 *
 * The server joins the networks (termoak-netd); the app manages them through
 * the REST API and reaches their hosts through the server relay (the engine
 * does that when it connects). Pure logic (JVM tests): the WireGuard .conf,
 * the state in the server, the network of a host (its own or its groups'),
 * the picker of the editors and the texts of the network errors.
 */

/** A WireGuard peer of a `.conf` or of a network (`wireguard.peers`). */
data class WgPeer(
    val publicKey: String,
    val endpoint: String? = null,
    val allowedIps: List<String> = emptyList(),
    val keepalive: Int? = null,
)

/** What a WireGuard `.conf` holds, split as Termoak stores it: the configuration and the keys (the secret). */
data class WgConf(
    val addresses: List<String> = emptyList(),
    val dns: List<String> = emptyList(),
    val mtu: Int? = null,
    val peers: List<WgPeer> = emptyList(),
    val privateKey: String? = null,
    val presharedKey: String? = null,
    /** Keys of the file Termoak doesn't use (`ListenPort`, `PostUp`...), as WireGuard writes them. */
    val ignored: List<String> = emptyList(),
)

/** Why a `.conf` can't be used (or saved). */
sealed interface ConfError {
    /** Nothing in it, or text before any `[Interface]`/`[Peer]`. */
    data object NotAConf : ConfError
    /** A line that is not `Key = Value` (its number). */
    data class BadLine(val line: Int) : ConfError
    data object PeerWithoutKey : ConfError
    data object NoPeer : ConfError
    /** A key that is not 32 bytes of base64 (`PrivateKey`, `PublicKey`, `PresharedKey`). */
    data class BadKey(val field: String) : ConfError
    /** `MTU` or `PersistentKeepalive` that is not a number. */
    data class BadNumber(val field: String) : ConfError
    /** Peers with different preshared keys: Termoak keeps one per network. */
    data object MixedPresharedKeys : ConfError
    /** No `Address` in `[Interface]`. */
    data object NoAddress : ConfError
    /** Peer [n] (from 1) has no `AllowedIPs`. */
    data class NoAllowedIps(val n: Int) : ConfError
    /** Peer [n]'s `Endpoint` is not `host:port`. */
    data class BadEndpoint(val n: Int) : ConfError
    /** No peer has an endpoint: the server is the side that connects. */
    data object NoEndpoint : ConfError
}

/** A `.conf` read, or why not. */
sealed interface ConfRead {
    data class Ok(val conf: WgConf) : ConfRead
    data class Bad(val error: ConfError) : ConfRead
}

/** State of a network as the screens show it. */
enum class NetState {
    /** Not running: it starts when a host in it is used. */
    DOWN,
    STARTING,
    /** Running, but the WireGuard peer never answered (no handshake yet). */
    WAITING,
    /** Running, and the other side answered (Tailscale: the node is in the tailnet). */
    UP,
    /** The server's node waits to be authorized (ZeroTier, or a Tailscale state this version doesn't know). */
    NEEDS_AUTH,
    /** Tailscale: the server's node must be logged in (an Editor opens the login URL). */
    NEEDS_LOGIN,
    /** Tailscale: the node is logged in and waits for approval in the admin console. */
    NEEDS_APPROVAL,
    ERROR,
    /** Not asked yet, or the server didn't answer (netd down): not an error page. */
    UNKNOWN,
}

/** `GET /networks/{id}/status` (and the answer of `up`). */
data class NetStatus(
    val state: String,
    val message: String? = null,
    val lastHandshakeAt: Long? = null,
    val rxBytes: Long? = null,
    val txBytes: Long? = null,
    val streams: Long? = null,
    val nodeAddresses: List<String> = emptyList(),
    val since: Long? = null,
    /** Tailscale, with `needs_auth`: `login` or `approval`. */
    val auth: String? = null,
    /** Tailscale: the control server (`null`: Tailscale's). */
    val controlUrl: String? = null,
    val tailnet: String? = null,
    val magicDnsSuffix: String? = null,
    /** Tailscale: devices the server's node sees. */
    val peerCount: Int? = null,
    val ephemeral: Boolean? = null,
    /** Tailscale: the last error as the control server said it. */
    val lastError: String? = null,
    /** The server's node: its name, MagicDNS name and id. */
    val nodeName: String? = null,
    val nodeDnsName: String? = null,
    val nodeId: String? = null,
    /** Tailscale: the login page (only Editors get it). */
    val authUrl: String? = null,
)

/** Tailscale/Headscale settings of a network (`tailscale`); `ephemeral` `null`: yes with an auth key, no without one. */
data class TsConfig(
    /** `null`: Tailscale's control server; otherwise a Headscale URL. */
    val controlUrl: String? = null,
    val hostname: String? = null,
    val tags: List<String> = emptyList(),
    val ephemeral: Boolean? = null,
)

/** What an update does with the stored secret (Tailscale's auth key). */
sealed interface SecretChange {
    /** No `secret` in the body: the stored one stays. */
    data object Keep : SecretChange
    /** `secret: {auth_key}`. */
    data class Set(val authKey: String) : SecretChange
    /** `secret: null` removes it (`{}` would store an empty secret and `has_secret` would stay true). */
    data object Clear : SecretChange
}

/** Why Tailscale settings can't be saved (the core's rules, checked again by the server). */
enum class TsProblem { CONTROL_URL_MISSING, CONTROL_URL, HOSTNAME, TAGS, TAGS_MAX, AUTH_KEY, KEY_MISSING }

/** A host of a vault as the devices list sees it (to mark devices already added). */
data class HostRef(val id: String, val label: String, val address: String, val networkId: String?)

/** A network as the REST API gives it (`GET /networks`): the whole object is kept to send it back on PUT. */
data class NetworkItem(
    val id: String,
    val name: String,
    val kind: String,
    val description: String,
    val vaultId: String?,
    val hasSecret: Boolean,
    val secretHidden: Boolean,
    val publicKey: String?,
    val addresses: List<String>,
    val dns: List<String>,
    val mtu: Int?,
    val peers: List<WgPeer>,
    /** The JSON as read (PUT sends it back whole, with the changes). */
    val json: String,
    /** Tailscale settings (kind `tailscale`). */
    val tailscale: TsConfig? = null,
) {
    val isWireguard: Boolean get() = kind == Networks.WIREGUARD
    val isTailscale: Boolean get() = kind == Networks.TAILSCALE
}

/** A device of a network (`GET /networks/{id}/peers`); WireGuard: its configured peers. */
data class NetPeer(
    val name: String? = null,
    val dnsName: String? = null,
    val addresses: List<String> = emptyList(),
    val online: Boolean? = null,
    val lastSeen: Long? = null,
    val publicKey: String? = null,
    val endpoint: String? = null,
    val os: String? = null,
    /** Tailscale: ACL tags. */
    val tags: List<String> = emptyList(),
    /** Tailscale: subnets the device routes (exit nodes: `/0`). */
    val routes: List<String> = emptyList(),
) {
    /** Its MagicDNS name without the trailing dot (`nas.tail1234.ts.net`). */
    val bareDnsName: String? get() = dnsName?.trim()?.trimEnd('.')?.takeIf { it.isNotEmpty() }

    /** Tailscale: where a host on this device connects: its MagicDNS name, or its first IP. */
    val deviceAddress: String?
        get() = bareDnsName ?: addresses.firstOrNull()?.substringBefore('/')?.trim()?.takeIf { it.isNotEmpty() }

    /** Tailscale: the subnets of [routes] a host can be in (never an exit node's `/0`). */
    val hostRoutes: List<String> get() = routes.map { it.trim() }.filter { it.isNotEmpty() && !it.endsWith("/0") }

    /** Its name, DNS name, endpoint or the start of its key. */
    val title: String
        get() = name?.takeIf { it.isNotBlank() } ?: dnsName?.takeIf { it.isNotBlank() } ?: endpoint?.takeIf { it.isNotBlank() }
            ?: publicKey?.let { it.take(10) + "…" } ?: "—"
}

/** An address a host can be added at: a single address ([exact]), or a subnet's start to complete. */
data class HostAddress(val address: String, val exact: Boolean, val source: String)

/** `POST /hosts/{id}/test`: "via Office · 23 ms", or why it failed. */
data class HostTest(
    val ok: Boolean,
    val latencyMs: Long? = null,
    val fingerprint: String? = null,
    val error: String? = null,
    val errorCode: String? = null,
    val networkId: String? = null,
    val networkName: String? = null,
    val at: Long = 0L,
    val networkKind: String? = null,
)

/** A group as the network inheritance sees it. */
data class GroupLink(val id: String, val name: String, val parentId: String?, val networkId: String?)

/** The network a host or group gets from its groups: the group that sets it and the network. */
data class InheritedNetwork(val group: GroupLink, val networkId: String)

/** A choice of the "Network" picker of the host and group editors. */
sealed interface NetworkChoice {
    val id: String?

    /** `null`: the group's network ([inherited]), or direct when no group gives one. */
    data class Inherit(val inherited: InheritedNetwork?, val inheritedName: String?) : NetworkChoice {
        override val id: String? get() = null
    }

    /** A network of the item's vault. */
    data class Network(override val id: String, val name: String, val kind: String) : NetworkChoice

    /** The item's network is not in its vault (deleted, or not synced yet): kept, and flagged. */
    data class Missing(override val id: String) : NetworkChoice
}

object Networks {
    /** Feature of `/api/v1/info` that says the server can reach private networks. */
    const val FEATURE = "networks"
    const val WIREGUARD = "wireguard"
    const val TAILSCALE = "tailscale"
    const val ZEROTIER = "zerotier"

    /** Most levels of nested groups followed (as the engine). */
    private const val MAX_DEPTH = 16

    /** A `.conf` bigger than this is not a WireGuard configuration. */
    const val MAX_CONF_BYTES = 64 * 1024

    /** Polls of the state after Test, about once a second, until the handshake (wireguard-go only shakes hands with traffic). */
    const val TEST_POLLS = 6

    /** Polls of a Tailscale network's state after Test while it is starting (`up` already waits 10 s): about 30 s. */
    const val TS_TEST_POLLS = 30

    /** A login or an approval is followed every 2 s for 5 minutes at most. */
    const val LOGIN_POLL_MS = 2_000L
    const val LOGIN_WAIT_MS = 5 * 60_000L

    /** Most tags a node may ask for (the core's `TailscaleConfig::MAX_TAGS`). */
    const val MAX_TAGS = 20

    private val TS_HOSTNAME = Regex("^[A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?$")
    private val TS_TAG = Regex("^tag:[A-Za-z0-9][A-Za-z0-9_-]{0,62}$")

    private val WG_KEY = Regex("^[A-Za-z0-9+/]{42}[AEIMQUYcgkosw480]=$")
    private val ENDPOINT = Regex("""^(\[[0-9a-fA-F:.]+]|[^\s:\[\]]+):(\d{1,5})$""")

    /** Template of a new network's `.conf`. */
    const val CONF_TEMPLATE = "[Interface]\nAddress = 10.0.0.2/32\n# DNS = 10.0.0.1\n\n[Peer]\nPublicKey = \n" +
        "Endpoint = vpn.example.com:51820\nAllowedIPs = 10.0.0.0/24\nPersistentKeepalive = 25\n"

    /** Whether a server's `/api/v1/info` says it has private networks. */
    fun supported(infoJson: String): Boolean = runCatching {
        JSONObject(infoJson).optJSONObject("features")?.optBoolean(FEATURE, false) ?: false
    }.getOrDefault(false)

    /**
     * Kinds of network the server can join (`features.network_kinds`): a
     * server without the field (0.7.0-next.2) joins WireGuard only; none
     * without `features.networks`.
     */
    fun kinds(infoJson: String): List<String> = runCatching {
        val f = JSONObject(infoJson).optJSONObject("features") ?: return emptyList()
        if (!f.optBoolean(FEATURE, false)) return emptyList()
        val a = f.optJSONArray("network_kinds") ?: return listOf(WIREGUARD)
        (0 until a.length()).mapNotNull { a.optString(it, "").ifEmpty { null } }
    }.getOrDefault(emptyList())

    /** A WireGuard key: 32 bytes in standard base64. */
    fun validKey(text: String): Boolean = WG_KEY.matches(text.trim())

    private fun list(value: String): List<String> = value.split(',').map { it.trim() }.filter { it.isNotEmpty() }

    private val KNOWN_KEYS = listOf("ListenPort", "Table", "PreUp", "PostUp", "PreDown", "PostDown", "SaveConfig", "FwMark", "Jc")

    /** `listenport` → `ListenPort` (for the "not used" note). */
    private fun keyName(lower: String, original: String): String = KNOWN_KEYS.firstOrNull { it.equals(lower, ignoreCase = true) } ?: original

    /**
     * Reads a WireGuard `.conf` (wg-quick): `[Interface]` `Address`
     * (repeatable, comma lists), `DNS`, `MTU`, `PrivateKey`; each `[Peer]`
     * `PublicKey`, `Endpoint`, `AllowedIPs`, `PersistentKeepalive` (`off` =
     * none), `PresharedKey`. Keys are case-insensitive, `#` and `;` start
     * comments, the value is everything after the first `=` (keys end in
     * `=`). Other keys are listed in [WgConf.ignored]; bad keys are never
     * echoed.
     */
    fun parse(text: String): ConfRead {
        var section: String? = null
        var seenSection = false
        val addresses = mutableListOf<String>()
        val dns = mutableListOf<String>()
        var mtu: Int? = null
        var privateKey: String? = null
        val peers = mutableListOf<WgPeer>()
        val psks = mutableListOf<String?>()
        var peer: WgPeer? = null
        var psk: String? = null
        val ignored = LinkedHashSet<String>()
        fun finishPeer(): ConfError? {
            val p = peer ?: return null
            if (p.publicKey.isEmpty()) return ConfError.PeerWithoutKey
            peers += p
            psks += psk
            peer = null
            psk = null
            return null
        }
        for ((i, raw) in text.lines().withIndex()) {
            val line = raw.split('#', ';').first().trim().trimStart('\uFEFF')
            if (line.isEmpty()) continue
            if (line.startsWith("[") && line.endsWith("]")) {
                finishPeer()?.let { return ConfRead.Bad(it) }
                seenSection = true
                section = line.substring(1, line.length - 1).trim().lowercase()
                if (section == "peer") peer = WgPeer("")
                continue
            }
            if (section == null) return ConfRead.Bad(ConfError.NotAConf)
            val eq = line.indexOf('=')
            if (eq < 1) return ConfRead.Bad(ConfError.BadLine(i + 1))
            val keyText = line.substring(0, eq).trim()
            val key = keyText.lowercase()
            val value = line.substring(eq + 1).trim()
            when {
                section == "interface" && key == "address" -> addresses += list(value)
                section == "interface" && key == "dns" -> dns += list(value)
                section == "interface" && key == "mtu" ->
                    mtu = value.toIntOrNull()?.takeIf { it in 576..65535 } ?: return ConfRead.Bad(ConfError.BadNumber("MTU"))
                section == "interface" && key == "privatekey" -> {
                    if (!validKey(value)) return ConfRead.Bad(ConfError.BadKey("PrivateKey"))
                    privateKey = value
                }
                section == "peer" -> {
                    val p = peer ?: WgPeer("")
                    peer = when (key) {
                        "publickey" -> {
                            if (!validKey(value)) return ConfRead.Bad(ConfError.BadKey("PublicKey"))
                            p.copy(publicKey = value)
                        }
                        "endpoint" -> p.copy(endpoint = value.ifEmpty { null })
                        "allowedips" -> p.copy(allowedIps = p.allowedIps + list(value))
                        "presharedkey" -> {
                            if (!validKey(value)) return ConfRead.Bad(ConfError.BadKey("PresharedKey"))
                            psk = value
                            p
                        }
                        "persistentkeepalive" -> {
                            val secs = if (value.equals("off", ignoreCase = true)) 0
                            else value.toIntOrNull()?.takeIf { it in 0..65535 } ?: return ConfRead.Bad(ConfError.BadNumber("PersistentKeepalive"))
                            p.copy(keepalive = secs.takeIf { it > 0 })
                        }
                        else -> {
                            ignored += keyName(key, keyText)
                            p
                        }
                    }
                }
                else -> ignored += keyName(key, keyText)
            }
        }
        finishPeer()?.let { return ConfRead.Bad(it) }
        if (!seenSection) return ConfRead.Bad(ConfError.NotAConf)
        if (peers.isEmpty()) return ConfRead.Bad(ConfError.NoPeer)
        // One preshared key per network (netd applies it to every peer).
        val withPsk = psks.filterNotNull()
        if (withPsk.isNotEmpty() && psks.toSet().size > 1) return ConfRead.Bad(ConfError.MixedPresharedKeys)
        return ConfRead.Ok(
            WgConf(addresses, dns, mtu, peers, privateKey, withPsk.firstOrNull(), ignored.toList()),
        )
    }

    /**
     * What stops saving a configuration (§8.1): addresses, each peer's allowed
     * IPs and `host:port` endpoint, and at least one endpoint (the server
     * is the side that connects). `null`: it can be saved.
     */
    fun problem(conf: WgConf): ConfError? {
        if (conf.addresses.isEmpty()) return ConfError.NoAddress
        if (conf.peers.isEmpty()) return ConfError.NoPeer
        conf.peers.forEachIndexed { i, p ->
            if (p.allowedIps.isEmpty()) return ConfError.NoAllowedIps(i + 1)
            val e = p.endpoint
            if (e != null && !validEndpoint(e)) return ConfError.BadEndpoint(i + 1)
        }
        if (conf.peers.none { it.endpoint != null }) return ConfError.NoEndpoint
        return null
    }

    /** `host:port` or `[v6]:port`. */
    fun validEndpoint(e: String): Boolean = ENDPOINT.find(e.trim())?.let { m ->
        m.groupValues[2].toIntOrNull()?.let { it in 1..65535 } == true
    } ?: false

    /** Reads and checks: what the form shows under the text. */
    fun check(text: String): ConfRead = when (val r = parse(text)) {
        is ConfRead.Ok -> problem(r.conf)?.let { ConfRead.Bad(it) } ?: r
        is ConfRead.Bad -> r
    }

    /**
     * A network's WireGuard configuration as a `.conf` without keys but the
     * peers' public ones, to edit it as text; [keyNote] is a comment saying
     * the private key is kept.
     */
    fun confText(n: NetworkItem, keyNote: String): String = buildString {
        append("[Interface]\n")
        append("# ").append(keyNote).append('\n')
        if (n.addresses.isNotEmpty()) append("Address = ").append(n.addresses.joinToString(", ")).append('\n')
        if (n.dns.isNotEmpty()) append("DNS = ").append(n.dns.joinToString(", ")).append('\n')
        n.mtu?.let { append("MTU = ").append(it).append('\n') }
        for (p in n.peers) {
            append("\n[Peer]\n")
            append("PublicKey = ").append(p.publicKey).append('\n')
            p.endpoint?.let { append("Endpoint = ").append(it).append('\n') }
            if (p.allowedIps.isNotEmpty()) append("AllowedIPs = ").append(p.allowedIps.joinToString(", ")).append('\n')
            p.keepalive?.let { append("PersistentKeepalive = ").append(it).append('\n') }
        }
    }

    /** The `[Peer]` block to add on the other side of the tunnel. */
    fun peerSnippet(name: String, publicKey: String, addresses: List<String>): String = listOf(
        "[Peer]",
        "# Termoak: $name",
        "PublicKey = $publicKey",
        "AllowedIPs = " + (addresses.map { if (it.contains('/')) it else if (it.contains(':')) "$it/128" else "$it/32" }
            .joinToString(", ").ifEmpty { "10.0.0.2/32" }),
    ).joinToString("\n")

    // ----- JSON of the server -----

    private fun JSONObject.str(key: String): String? = if (isNull(key)) null else optString(key, "").ifEmpty { null }
    private fun JSONObject.long(key: String): Long? = if (has(key) && !isNull(key)) optLong(key) else null
    private fun JSONArray?.strings(): List<String> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optString(it, "").ifEmpty { null } }

    fun status(json: String): NetStatus? = runCatching { status(JSONObject(json)) }.getOrNull()

    private fun JSONObject.bool(key: String): Boolean? = if (has(key) && !isNull(key)) optBoolean(key) else null

    fun status(o: JSONObject): NetStatus {
        val node = o.optJSONObject("node")
        return NetStatus(
            state = o.optString("state", ""),
            message = o.str("message"),
            lastHandshakeAt = o.long("last_handshake_at")?.takeIf { it > 0 },
            rxBytes = o.long("rx_bytes"),
            txBytes = o.long("tx_bytes"),
            streams = o.long("streams"),
            nodeAddresses = node?.optJSONArray("addresses").strings(),
            since = o.long("since"),
            auth = o.str("auth"),
            controlUrl = o.str("control_url"),
            tailnet = o.str("tailnet"),
            magicDnsSuffix = o.str("magic_dns_suffix"),
            peerCount = o.long("peer_count")?.toInt(),
            ephemeral = o.bool("ephemeral"),
            lastError = o.str("last_error"),
            nodeName = node?.str("name"),
            nodeDnsName = node?.str("dns_name"),
            nodeId = node?.str("node_id"),
            authUrl = node?.str("auth_url"),
        )
    }

    /**
     * The state shown: `up` without a WireGuard handshake is "waiting"; a
     * Tailscale node that needs authorization needs a login or an approval.
     */
    fun state(status: NetStatus?, kind: String): NetState = when (status?.state) {
        null -> NetState.UNKNOWN
        "down" -> NetState.DOWN
        "starting" -> NetState.STARTING
        "needs_auth" -> when {
            kind != TAILSCALE -> NetState.NEEDS_AUTH
            status.auth == "approval" -> NetState.NEEDS_APPROVAL
            else -> NetState.NEEDS_LOGIN
        }
        "error" -> NetState.ERROR
        "up" -> if (kind == WIREGUARD && status.lastHandshakeAt == null) NetState.WAITING else NetState.UP
        else -> NetState.UNKNOWN
    }

    private fun peersOf(a: JSONArray?): List<WgPeer> = if (a == null) emptyList() else (0 until a.length()).mapNotNull { i ->
        val p = a.optJSONObject(i) ?: return@mapNotNull null
        WgPeer(
            publicKey = p.optString("public_key", ""),
            endpoint = p.str("endpoint"),
            allowedIps = p.optJSONArray("allowed_ips").strings(),
            keepalive = p.long("persistent_keepalive")?.toInt()?.takeIf { it > 0 },
        )
    }

    fun network(o: JSONObject): NetworkItem {
        val wg = o.optJSONObject("wireguard")
        return NetworkItem(
            id = o.optString("id", ""),
            name = o.optString("name", ""),
            kind = o.optString("kind", WIREGUARD),
            description = o.optString("description", ""),
            vaultId = o.str("vault_id"),
            hasSecret = o.optBoolean("has_secret", false),
            secretHidden = o.optBoolean("secret_hidden", false),
            publicKey = wg?.str("public_key"),
            addresses = wg?.optJSONArray("addresses").strings(),
            dns = wg?.optJSONArray("dns").strings(),
            mtu = wg?.long("mtu")?.toInt(),
            peers = peersOf(wg?.optJSONArray("peers")),
            json = o.toString(),
            tailscale = o.optJSONObject("tailscale")?.let { ts ->
                TsConfig(ts.str("control_url"), ts.str("hostname"), ts.optJSONArray("tags").strings(), ts.bool("ephemeral"))
            },
        )
    }

    fun networks(json: String): List<NetworkItem> = runCatching {
        val a = JSONArray(json)
        (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(::network) }
    }.getOrDefault(emptyList()).sortedBy { it.name.lowercase() }

    fun peers(json: String): List<NetPeer> = runCatching {
        val a = JSONArray(json)
        (0 until a.length()).mapNotNull { i ->
            val p = a.optJSONObject(i) ?: return@mapNotNull null
            NetPeer(
                name = p.str("name"), dnsName = p.str("dns_name"), addresses = p.optJSONArray("addresses").strings(),
                online = if (p.has("online") && !p.isNull("online")) p.optBoolean("online") else null,
                lastSeen = p.long("last_seen")?.takeIf { it > 0 }, publicKey = p.str("public_key"), endpoint = p.str("endpoint"),
                os = p.str("os"), tags = p.optJSONArray("tags").strings(), routes = p.optJSONArray("routes").strings(),
            )
        }
    }.getOrDefault(emptyList())

    fun hostTest(json: String, now: Long = System.currentTimeMillis()): HostTest? = runCatching {
        val o = JSONObject(json)
        val net = o.optJSONObject("network")
        HostTest(
            ok = o.optBoolean("ok", false),
            latencyMs = o.long("latency_ms"),
            fingerprint = o.str("fingerprint"),
            error = o.str("error"),
            errorCode = o.str("error_code"),
            networkId = net?.str("id"),
            networkName = net?.str("name"),
            at = now,
            networkKind = net?.str("kind"),
        )
    }.getOrNull()

    /** `(networks billed to you, max)` of `GET /me/plan` (`max` `null`: no limit). */
    fun usage(planJson: String): Pair<Int, Int?>? = runCatching {
        val o = JSONObject(planJson)
        val used = o.optJSONObject("usage")?.optInt("networks", 0) ?: 0
        val limits = o.optJSONObject("plan")?.optJSONObject("limits")
        val max = if (limits != null && limits.has("max_networks") && !limits.isNull("max_networks")) limits.optInt("max_networks") else null
        used to max
    }.getOrNull()

    // ----- Bodies of the REST calls -----

    /**
     * Body of a create (`vaultId` set) or an update (the network as read, in
     * [base]): name, description, kind `wireguard` and its configuration
     * (the server derives `public_key`); `secret` only when given (missing:
     * the stored keys are kept; an object replaces both).
     */
    fun body(base: String?, vaultId: String?, name: String, description: String, conf: WgConf, secret: Pair<String?, String?>?): String {
        val o = base?.let { JSONObject(it) } ?: JSONObject()
        o.remove("id")
        o.put("name", name)
        o.put("description", description)
        o.put("kind", WIREGUARD)
        val wg = o.optJSONObject("wireguard") ?: JSONObject()
        wg.put("addresses", JSONArray(conf.addresses))
        wg.put("dns", JSONArray(conf.dns))
        wg.put("mtu", conf.mtu ?: JSONObject.NULL)
        wg.put(
            "peers",
            JSONArray(
                conf.peers.map { p ->
                    JSONObject().put("public_key", p.publicKey).put("endpoint", p.endpoint ?: JSONObject.NULL)
                        .put("allowed_ips", JSONArray(p.allowedIps)).put("persistent_keepalive", p.keepalive ?: JSONObject.NULL)
                },
            ),
        )
        wg.remove("public_key")
        o.put("wireguard", wg)
        if (vaultId != null) o.put("vault_id", vaultId)
        if (secret != null) {
            o.put(
                "secret",
                JSONObject().put("private_key", secret.first ?: JSONObject.NULL).put("preshared_key", secret.second ?: JSONObject.NULL),
            )
        }
        return o.toString()
    }

    /** `POST /hosts` body for a host at [address] of a network (its vault, the network chosen). */
    fun hostBody(label: String, address: String, port: Int, username: String?, network: NetworkItem): String = JSONObject()
        .put("label", label)
        .put("address", address)
        .put("settings", JSONObject().put("port", port).put("username", username ?: JSONObject.NULL).put("network_id", network.id))
        .apply { network.vaultId?.let { put("vault_id", it) } }
        .toString()

    // ----- Tailscale / Headscale -----

    /** The node's name when none is set: `termoak-` and the last 8 hex digits of the id (as netd does). */
    fun defaultHostname(id: String?): String {
        val hex = id.orEmpty().replace("-", "").lowercase()
        return "termoak-" + if (hex.length >= 8) hex.takeLast(8) else "xxxxxxxx"
    }

    /** `tag:a, tag:b` → `[tag:a, tag:b]`. */
    fun parseTags(text: String): List<String> = text.split(',', ' ', '\n').map { it.trim() }.filter { it.isNotEmpty() }

    /** `https://hs.example.com/` → `https://hs.example.com`. */
    fun cleanControlUrl(url: String): String = url.trim().trimEnd('/')

    /**
     * What stops saving Tailscale settings and a new auth key (the core's
     * rules, NETWORKS.md §8.1; the server checks them again): [controlUrl]
     * `null` is Tailscale's control server, `""` a Headscale without its
     * URL. `null`: it can be saved. The key is never part of the text.
     */
    fun tailscaleProblem(controlUrl: String?, hostname: String?, tags: List<String>, authKey: String?): TsProblem? {
        if (controlUrl != null) {
            val url = controlUrl.trim()
            if (url.isEmpty()) return TsProblem.CONTROL_URL_MISSING
            val rest = url.removePrefix("https://").takeIf { it != url } ?: url.removePrefix("http://").takeIf { it != url }
                ?: return TsProblem.CONTROL_URL
            val authority = rest.substringBefore('/')
            if (authority.isEmpty() || authority.contains('@') || url.contains('?') || url.contains('#') ||
                url.any { it.isWhitespace() || it.isISOControl() }
            ) return TsProblem.CONTROL_URL
        }
        val h = hostname?.trim().orEmpty()
        if (h.isNotEmpty() && !TS_HOSTNAME.matches(h)) return TsProblem.HOSTNAME
        if (tags.size > MAX_TAGS) return TsProblem.TAGS_MAX
        if (tags.any { !TS_TAG.matches(it.trim()) }) return TsProblem.TAGS
        val k = authKey?.trim()
        if (!k.isNullOrEmpty() && (k.length > 1024 || k.any { it.code !in 0x21..0x7e })) return TsProblem.AUTH_KEY
        return null
    }

    /**
     * Body of a Tailscale network's create (`vaultId` set) or update (the
     * network as read, in [base], sent back whole with `ephemeral`): name,
     * description, kind and settings; the secret as [secret] says (`Keep`:
     * no `secret`, `Set`: `{auth_key}`, `Clear`: `null`).
     */
    fun tailscaleBody(base: String?, vaultId: String?, name: String, description: String, ts: TsConfig, secret: SecretChange): String {
        val o = base?.let { JSONObject(it) } ?: JSONObject()
        o.remove("id")
        o.put("name", name)
        o.put("description", description)
        o.put("kind", TAILSCALE)
        val t = o.optJSONObject("tailscale") ?: JSONObject()
        t.put("control_url", ts.controlUrl?.let { cleanControlUrl(it) }?.ifEmpty { null } ?: JSONObject.NULL)
        t.put("hostname", ts.hostname?.trim()?.ifEmpty { null } ?: JSONObject.NULL)
        t.put("tags", JSONArray(ts.tags.map { it.trim() }.filter { it.isNotEmpty() }))
        t.put("ephemeral", ts.ephemeral ?: JSONObject.NULL)
        o.put("tailscale", t)
        if (vaultId != null) o.put("vault_id", vaultId)
        when (secret) {
            SecretChange.Keep -> o.remove("secret")
            is SecretChange.Set -> o.put("secret", JSONObject().put("auth_key", secret.authKey.trim()))
            SecretChange.Clear -> o.put("secret", JSONObject.NULL)
        }
        return o.toString()
    }

    /** "Tailscale", or the Headscale server's host (`https://hs.example.com:8080/x` → `hs.example.com:8080`). */
    fun controlServerLabel(url: String?): String {
        val u = url?.trim()?.takeIf { it.isNotEmpty() } ?: return "Tailscale"
        return u.substringAfter("://").substringBefore('/').ifEmpty { u }
    }

    /** The node's address and the devices it sees ("100.64.0.3 · 4 devices"); [devices] makes the count's text. */
    fun tailnetSummary(status: NetStatus, devices: (Int) -> String): String =
        listOfNotNull(status.nodeAddresses.firstOrNull(), devices(status.peerCount ?: 0)).joinToString(" · ")

    /** `nas.tail1234.ts.net.` → `nas.tail1234.ts.net` (lowercase). */
    private fun bare(s: String?): String = s.orEmpty().trim().trimEnd('.').lowercase()

    /**
     * The host of [hosts] that already reaches [peer] through the network
     * [networkId]: same network, and its address is the device's name,
     * MagicDNS name (or its first label) or one of its IPs, or its label is
     * the device's name.
     */
    fun importedHost(peer: NetPeer, networkId: String, hosts: List<HostRef>): HostRef? {
        val names = mutableSetOf<String>()
        bare(peer.dnsName).takeIf { it.isNotEmpty() }?.let { names += it; names += it.substringBefore('.') }
        bare(peer.name).takeIf { it.isNotEmpty() }?.let { names += it }
        peer.addresses.forEach { a -> bare(a.substringBefore('/')).takeIf { it.isNotEmpty() }?.let { names += it } }
        val name = bare(peer.name)
        return hosts.firstOrNull { h ->
            h.networkId == networkId && (bare(h.address) in names || (name.isNotEmpty() && bare(h.label) == name))
        }
    }

    /** Devices of a tailnet that can be added at once: not added yet and with an address. */
    fun newDevices(peers: List<NetPeer>, networkId: String, hosts: List<HostRef>): List<NetPeer> =
        peers.filter { it.deviceAddress != null && importedHost(it, networkId, hosts) == null }

    /** Online devices first, then by name. */
    fun sortDevices(peers: List<NetPeer>): List<NetPeer> =
        peers.sortedWith(compareBy<NetPeer>({ it.online != true }, { it.title.lowercase() }))

    /** `GET /hosts?vault_id=…` as [HostRef]s. */
    fun hostRefs(json: String): List<HostRef> = runCatching {
        val a = JSONArray(json)
        (0 until a.length()).mapNotNull { i ->
            val h = a.optJSONObject(i) ?: return@mapNotNull null
            HostRef(h.optString("id", ""), h.optString("label", ""), h.optString("address", ""), h.optJSONObject("settings")?.str("network_id"))
        }
    }.getOrDefault(emptyList())

    /** `POST /hosts` body for a device of a tailnet: its name, its MagicDNS name (or first IP), port 22. */
    fun deviceHostBody(peer: NetPeer, network: NetworkItem): String? {
        val address = peer.deviceAddress ?: return null
        return hostBody(peer.name?.takeIf { it.isNotBlank() } ?: address, address, 22, null, network)
    }

    // ----- Peers → host addresses -----

    /**
     * Addresses a host can be added at, from a peer's addresses (WireGuard:
     * its allowed IPs): `10.9.0.5/32` is `10.9.0.5`; a subnet has no single
     * host, so it gives its start for the user to complete. Everything
     * (`0.0.0.0/0`) is none. Its DNS name first.
     */
    fun hostAddresses(peer: NetPeer): List<HostAddress> {
        val out = mutableListOf<HostAddress>()
        val seen = mutableSetOf<String>()
        for (raw in peer.addresses) {
            val a = raw.trim()
            val ip = a.substringBefore('/')
            val bits = if (a.contains('/')) a.substringAfter('/').toIntOrNull() else null
            if (bits == 0) continue
            val parsed = parseIp(ip) ?: continue
            val full = if (parsed is Inet6Address) 128 else 32
            val exact = bits == null || bits >= full
            val address = when {
                parsed is Inet6Address -> ip.lowercase()
                exact -> parsed.hostAddress ?: ip
                else -> {
                    // 10.9.0.0/24 → "10.9.0." to complete.
                    val keep = ((bits ?: 32) + 7).div(8).coerceIn(1, 3)
                    parsed.address.take(keep).joinToString(".") { (it.toInt() and 0xff).toString() } + "."
                }
            }
            if (seen.add(address)) out += HostAddress(address, exact, a)
        }
        val dns = peer.dnsName?.trim()?.trimEnd('.')
        if (!dns.isNullOrEmpty() && seen.add(dns)) out.add(0, HostAddress(dns, true, peer.dnsName))
        return out
    }

    /** An IP literal (never a name lookup). */
    private fun parseIp(s: String): InetAddress? {
        val v4 = Regex("""^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$""").matchEntire(s)
        if (v4 != null) {
            val b = v4.groupValues.drop(1).map { it.toInt() }
            if (b.any { it > 255 }) return null
            return InetAddress.getByAddress(b.map { it.toByte() }.toByteArray())
        }
        if (s.contains(':') && s.all { it.isLetterOrDigit() || it == ':' || it == '.' } && s.none { it in 'g'..'z' || it in 'G'..'Z' }) {
            return runCatching { InetAddress.getByName(s) }.getOrNull() as? Inet6Address
        }
        return null
    }

    // ----- The network of a host or group -----

    /** The group [groupId] (or its parents) that gives the hosts inside a network, and that network. */
    fun inherited(groupId: String?, groups: List<GroupLink>): InheritedNetwork? {
        val byId = groups.associateBy { it.id }
        val seen = mutableSetOf<String>()
        var g = groupId?.let { byId[it] }
        while (g != null && seen.add(g.id) && seen.size <= MAX_DEPTH) {
            g.networkId?.let { return InheritedNetwork(g, it) }
            g = g.parentId?.let { byId[it] }
        }
        return null
    }

    /** The network a host's (or group's) first connection goes through: its own, or the nearest group's. */
    fun effective(own: String?, groupId: String?, groups: List<GroupLink>): String? = own ?: inherited(groupId, groups)?.networkId

    /**
     * The choices of the "Network" picker: first `null` (the group's network,
     * or direct), then the networks of the item's own vault, then the item's
     * network when it is not among them (kept, flagged "missing").
     */
    fun choices(
        current: String?,
        groupId: String?,
        groups: List<GroupLink>,
        sameVault: List<Triple<String, String, String>>,
    ): List<NetworkChoice> {
        val inh = inherited(groupId, groups)
        val inhName = inh?.let { i -> sameVault.firstOrNull { it.first == i.networkId }?.second }
        val out = mutableListOf<NetworkChoice>(NetworkChoice.Inherit(inh, inhName))
        sameVault.sortedBy { it.second.lowercase() }.forEach { (id, name, kind) -> out += NetworkChoice.Network(id, name, kind) }
        if (current != null && sameVault.none { it.first == current }) out += NetworkChoice.Missing(current)
        return out
    }

    // ----- Errors -----

    /** Server codes with their own text (their English message is meant for logs). */
    private val CODES = mapOf(
        "network_unavailable" to R.string.error_network_unavailable,
        "network_not_found" to R.string.error_network_not_found,
        "network_dial_failed" to R.string.error_network_dial_failed,
        "unsupported_kind" to R.string.error_unsupported_kind,
        "key_exists" to R.string.error_key_exists,
        "not_wireguard" to R.string.error_not_wireguard,
        "not_in_network" to R.string.error_not_in_network,
    )

    private val NETD = mapOf(
        "dial_failed" to R.string.networks_netd_dial_failed,
        "timeout" to R.string.networks_netd_timeout,
        "needs_auth" to R.string.networks_netd_needs_auth,
        "network_error" to R.string.networks_netd_network_error,
        "limit" to R.string.networks_netd_limit,
        "invalid" to R.string.networks_netd_invalid,
        "unsupported_kind" to R.string.error_unsupported_kind,
    )

    /**
     * Friendly text of a network error of the API ([code] and its details
     * JSON): `plan_limit` of `max_networks` says the plan's number,
     * `network_dial_failed` what netd found (`netd_code`), `relay_limit`
     * its maximum. `null`: not a network error (show it as usual).
     */
    fun errorText(code: String, detailsJson: String?, message: String? = null, device: Boolean = false): UiText? {
        val d = runCatching { JSONObject(detailsJson ?: "{}") }.getOrElse { JSONObject() }
        val netd = d.optString("netd_code", "")
        val detail = message?.trim()?.takeIf { it.isNotEmpty() }
        return when (code) {
            "plan_limit" -> if (d.optString("limit") != "max_networks") null
            else if (d.has("max") && !d.isNull("max")) d.optInt("max").let { UiText.Plural(R.plurals.error_plan_limit_max_networks, it, listOf(it)) }
            else uiText(R.string.error_plan_limit_networks_unknown)
            "relay_limit" -> relayLimit(d)
            "network_dial_failed" -> when {
                netd == "needs_auth" && d.optString("auth") == "login" -> uiText(R.string.networks_ts_netd_needs_login)
                netd == "needs_auth" && d.optString("auth") == "approval" -> uiText(R.string.networks_ts_netd_needs_approval)
                NETD[netd] != null -> uiText(NETD.getValue(netd))
                // Through the relay the engine keeps netd's English message only: a tailnet's says what happened.
                detail != null && tailscaleMessageText(detail, null) != null -> tailscaleMessageText(detail, null)
                // Through the relay the engine keeps the server's message only: a full relay, or what netd said.
                detail != null && detail.contains("relay_limit") -> relayLimit(d)
                detail != null && device -> uiText(R.string.error_network_dial_failed_detail, detail)
                else -> uiText(R.string.error_network_dial_failed)
            }
            "network_unavailable" -> uiText(
                when {
                    netd == "unsupported_kind" -> R.string.error_unsupported_kind
                    // The engine's rule: a network host on This device, signed out, or a server without networks.
                    device -> R.string.error_network_unavailable_device
                    else -> R.string.error_network_unavailable
                },
            )
            else -> CODES[code]?.let { uiText(it) }
        }
    }

    private fun relayLimit(d: JSONObject): UiText =
        if (d.has("max") && !d.isNull("max")) uiText(R.string.error_relay_limit, d.optInt("max")) else uiText(R.string.error_relay_limit_unknown)

    /** netd's English reason without its `the network "X" could not start: ` prefix. */
    fun tailscaleReason(message: String): String {
        for (marker in listOf("\" could not start: ", "\" waits for authorization: ", "\" is still starting: ")) {
            val i = message.indexOf(marker)
            if (i >= 0) return message.substring(i + marker.length).trim()
        }
        return message.trim()
    }

    /** The network's name in netd's `the network "X" …` message. */
    private fun quotedName(message: String): String? =
        Regex("""the network "(.*?)" (could not start|waits for authorization|is still starting)""").find(message)?.groupValues?.get(1)

    /** A dial that failed inside a tailnet: netd's words and, for an address or name it can't see, why. */
    fun tailnetDialText(message: String): UiText {
        val text = message.trim().trimEnd('.').replaceFirstChar { it.uppercase() }
        return if (message.contains("outside the tailnet") || message.contains("no device named")) uiText(R.string.networks_ts_error_dial_acl, text)
        else UiText.Raw("$text.")
    }

    /**
     * What netd's English message says about a tailnet (host tests and
     * connections only carry it): a login or an approval, still starting,
     * could not join ([kindKnown]: the network is a tailnet), or a dial
     * outside the tailnet. `null`: none of those.
     */
    fun tailscaleMessageText(message: String, name: String?, kindKnown: Boolean = false): UiText? {
        val n = name ?: quotedName(message)
        val wait = message.indexOf("waits for authorization")
        return when {
            wait >= 0 -> {
                val rest = message.substring(wait)
                // "the node is logged in but must be approved": approval; "must be logged in": login.
                val approval = rest.contains("approv")
                when {
                    n == null -> uiText(if (approval) R.string.networks_ts_netd_needs_approval else R.string.networks_ts_netd_needs_login)
                    approval -> uiText(R.string.networks_ts_error_approval, n)
                    else -> uiText(R.string.networks_ts_error_login, n)
                }
            }
            // Any kind can be "still starting": "the tailnet" only when it is one.
            kindKnown && message.contains("is still starting") -> uiText(R.string.networks_ts_error_timeout)
            kindKnown && message.contains("could not start") && n != null -> uiText(R.string.networks_ts_error_network_error, n, tailscaleReason(message))
            message.contains("outside the tailnet") || message.contains("no device named") -> tailnetDialText(message)
            else -> null
        }
    }

    /**
     * Text of an error of a Tailscale network [name] on its screens (Test,
     * devices): what netd found (`netd_code` and `auth` of the error's
     * details), in words, with the network's name. Other errors as usual.
     */
    fun tailscaleErrorText(code: String, detailsJson: String?, message: String?, name: String): UiText? {
        val d = runCatching { JSONObject(detailsJson ?: "{}") }.getOrElse { JSONObject() }
        val netd = d.optString("netd_code", "")
        val msg = message.orEmpty()
        return when {
            code == "network_dial_failed" && netd == "needs_auth" ->
                uiText(if (d.optString("auth") == "approval") R.string.networks_ts_error_approval else R.string.networks_ts_error_login, name)
            code == "network_dial_failed" && netd == "network_error" -> uiText(R.string.networks_ts_error_network_error, name, tailscaleReason(msg))
            code == "network_dial_failed" && netd == "dial_failed" && msg.isNotBlank() -> tailnetDialText(msg)
            code == "network_dial_failed" && netd == "timeout" -> uiText(R.string.networks_ts_error_timeout)
            code == "network_dial_failed" && netd == "limit" -> uiText(R.string.networks_ts_error_limit)
            code == "network_unavailable" && netd == "unsupported_kind" -> uiText(R.string.networks_ts_unsupported)
            else -> null
        }
    }

    /** Text of an error on a network's screens (Test, devices): a tailnet's in words, otherwise as everywhere. */
    fun errorFor(e: Throwable, n: NetworkItem, @androidx.annotation.StringRes fallback: Int): UiText {
        if (n.isTailscale) {
            ApiErrors.of(e)?.let { info -> tailscaleErrorText(info.code, info.detailsJson, info.message, n.name)?.let { return it } }
        }
        return e.toUiText(fallback)
    }

    /** Text of an error saving a network: the server's own reason for a `400` (bad settings). */
    fun saveErrorText(e: Throwable): UiText {
        val info = ApiErrors.of(e)
        if (info != null && info.status.toInt() == 400 && info.message.isNotBlank()) {
            return uiText(R.string.networks_form_error_server, info.message.trim())
        }
        return e.toUiText(R.string.networks_save_failed)
    }

    /** Whether an error of the devices list means "log the server in first". */
    fun needsLogin(code: String?, detailsJson: String?): Boolean =
        code == "network_dial_failed" && runCatching { JSONObject(detailsJson ?: "{}").optString("netd_code") }.getOrNull() == "needs_auth"

    /** Text of a Tailscale settings problem. */
    fun tsProblemText(p: TsProblem): UiText = when (p) {
        TsProblem.CONTROL_URL_MISSING -> uiText(R.string.networks_ts_error_control_url_missing)
        TsProblem.CONTROL_URL -> uiText(R.string.networks_ts_error_control_url)
        TsProblem.HOSTNAME -> uiText(R.string.networks_ts_error_hostname)
        TsProblem.TAGS -> uiText(R.string.networks_ts_error_tags)
        TsProblem.TAGS_MAX -> uiText(R.string.networks_ts_error_tags_max, MAX_TAGS)
        TsProblem.AUTH_KEY -> uiText(R.string.networks_ts_error_auth_key)
        TsProblem.KEY_MISSING -> uiText(R.string.networks_ts_error_key_missing)
    }

    /** The text of a failed host test: its `error_code`, else the server's text. */
    fun hostTestError(t: HostTest): UiText {
        val code = t.errorCode.orEmpty()
        // A tailnet says more: netd's English reason (the test carries no netd_code), in words.
        if (code == "network_dial_failed" && t.networkKind == TAILSCALE && !t.error.isNullOrBlank()) {
            val e = t.error
            return tailscaleMessageText(e, t.networkName, kindKnown = true) ?: tailnetDialText(e)
        }
        val known = when (code) {
            "host_key_unknown" -> R.string.networks_test_host_key_unknown
            "host_key_changed" -> R.string.networks_test_host_key_changed
            "auth_failed" -> R.string.networks_test_auth_failed
            "timeout" -> R.string.networks_test_timeout
            "connect_failed" -> R.string.networks_test_connect_failed
            "telnet_not_supported" -> R.string.networks_test_telnet
            else -> null
        }
        return known?.let { uiText(it) } ?: errorText(code, null)
            ?: t.error?.takeIf { it.isNotBlank() }?.let { UiText.Raw(it.replaceFirstChar { c -> c.uppercase() }) }
            ?: uiText(R.string.networks_test_failed)
    }

    /** Text of a `.conf` that can't be used. */
    fun confErrorText(e: ConfError): UiText = when (e) {
        ConfError.NotAConf -> uiText(R.string.networks_conf_not_a_conf)
        is ConfError.BadLine -> uiText(R.string.networks_conf_bad_line, e.line)
        ConfError.PeerWithoutKey -> uiText(R.string.networks_conf_peer_without_key)
        ConfError.NoPeer -> uiText(R.string.networks_conf_no_peer)
        is ConfError.BadKey -> uiText(R.string.networks_conf_bad_key, e.field)
        is ConfError.BadNumber -> uiText(R.string.networks_conf_bad_number, e.field)
        ConfError.MixedPresharedKeys -> uiText(R.string.networks_conf_mixed_psk)
        ConfError.NoAddress -> uiText(R.string.networks_conf_no_address)
        is ConfError.NoAllowedIps -> uiText(R.string.networks_conf_no_allowed_ips, e.n)
        is ConfError.BadEndpoint -> uiText(R.string.networks_conf_bad_endpoint, e.n)
        ConfError.NoEndpoint -> uiText(R.string.networks_conf_no_endpoint)
    }

    /** Label of a network kind. */
    fun kindLabel(kind: String): String = when (kind) {
        WIREGUARD -> "WireGuard"
        TAILSCALE -> "Tailscale"
        ZEROTIER -> "ZeroTier"
        else -> kind
    }

    /** "1.2 MB" (decimal units, like the web). */
    fun bytes(n: Long): String = when {
        n < 1000 -> "$n B"
        n < 1_000_000 -> String.format(java.util.Locale.ROOT, "%.1f kB", n / 1000.0)
        n < 1_000_000_000 -> String.format(java.util.Locale.ROOT, "%.1f MB", n / 1_000_000.0)
        else -> String.format(java.util.Locale.ROOT, "%.2f GB", n / 1_000_000_000.0)
    }
}
