package com.termoak.app.data

import android.app.Application
import com.termoak.app.R
import com.termoak.app.UiText
import com.termoak.app.networkError
import com.termoak.ffi.HttpMethod
import com.termoak.ffi.TermoakException
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Private networks (NETWORKS.md §8.1): the WireGuard .conf, the state in the
 * server, peers → host addresses, the network of a host and the editors'
 * picker, the error texts and the REST calls through a stand-in API.
 */
@RunWith(RobolectricTestRunner::class) // org.json is the Android one.
@Config(sdk = [34], application = Application::class)
class NetworksTest {
    private val priv = "yAnz5TF+lXXJte14tji3zlMNq+hd2rYUIgJBgB3fBmk="
    private val pub = "xTIBA5rboUvnH4htodjb6e697QjLERt1NAB4mZqp8Dg="
    private val psk = "FpCyhws9cxwWoV4xELtfJvjJN+zQVRPISllRWgeopVE="

    @Before
    fun engineErrors() = FakeApiErrors.install()

    private fun ok(text: String): WgConf = (Networks.parse(text) as ConfRead.Ok).conf
    private fun bad(text: String): ConfError = (Networks.parse(text) as ConfRead.Bad).error

    @Test
    fun readsAWgQuickConf() {
        val c = ok(
            """
            # made by the router
            [Interface]
            PrivateKey = $priv
            Address = 10.9.0.2/32, fd00::2/128
            address = 10.9.0.3/32
            DNS = 10.9.0.1, lan
            MTU = 1380
            ListenPort = 51820
            PostUp = iptables -A FORWARD -i %i -j ACCEPT; iptables -t nat -A POSTROUTING

            [Peer]
            PublicKey = $pub
            PresharedKey = $psk
            Endpoint = vpn.example.com:51820
            AllowedIPs = 10.9.0.0/24
            AllowedIPs = 192.168.1.0/24 ; second line
            PersistentKeepalive = 25
            """.trimIndent(),
        )
        assertEquals(listOf("10.9.0.2/32", "fd00::2/128", "10.9.0.3/32"), c.addresses)
        assertEquals(listOf("10.9.0.1", "lan"), c.dns)
        assertEquals(1380, c.mtu)
        assertEquals(priv, c.privateKey)
        assertEquals(psk, c.presharedKey)
        assertEquals(1, c.peers.size)
        val p = c.peers[0]
        assertEquals(pub, p.publicKey)
        assertEquals("vpn.example.com:51820", p.endpoint)
        assertEquals(listOf("10.9.0.0/24", "192.168.1.0/24"), p.allowedIps)
        assertEquals(25, p.keepalive)
        // Keys Termoak doesn't use, named as WireGuard writes them.
        assertEquals(listOf("ListenPort", "PostUp"), c.ignored)
        assertNull(Networks.problem(c))
    }

    @Test
    fun withoutPrivateKeyTheServerGeneratesIt() {
        val c = ok("[Interface]\nAddress = 10.0.0.2/32\n\n[Peer]\nPublicKey = $pub\nEndpoint = [2001:db8::1]:51820\nAllowedIPs = 10.0.0.0/24\nPersistentKeepalive = off\n")
        assertNull(c.privateKey)
        assertNull(c.peers[0].keepalive)
        assertNull(Networks.problem(c))
    }

    @Test
    fun badConfsSayWhyWithoutEchoingKeys() {
        assertEquals(ConfError.NotAConf, bad(""))
        assertEquals(ConfError.NotAConf, bad("hello world"))
        assertEquals(ConfError.BadLine(2), bad("[Interface]\nnot a key value\n"))
        assertEquals(ConfError.NoPeer, bad("[Interface]\nAddress = 10.0.0.2/32\n"))
        assertEquals(ConfError.PeerWithoutKey, bad("[Interface]\nAddress = 10.0.0.2/32\n[Peer]\nAllowedIPs = 10.0.0.0/24\n"))
        assertEquals(ConfError.BadKey("PrivateKey"), bad("[Interface]\nPrivateKey = secret-not-a-key\n[Peer]\nPublicKey = $pub\n"))
        assertEquals(ConfError.BadKey("PublicKey"), bad("[Interface]\n[Peer]\nPublicKey = abc=\n"))
        assertEquals(ConfError.BadNumber("MTU"), bad("[Interface]\nMTU = big\n[Peer]\nPublicKey = $pub\n"))
        assertEquals(ConfError.BadNumber("PersistentKeepalive"), bad("[Interface]\n[Peer]\nPublicKey = $pub\nPersistentKeepalive = soon\n"))
        val other = "aGVsbG9oZWxsb2hlbGxvaGVsbG9oZWxsb2hlbGxvaGk="
        assertTrue(Networks.validKey(other))
        assertEquals(
            ConfError.MixedPresharedKeys,
            bad("[Interface]\n[Peer]\nPublicKey = $pub\nPresharedKey = $psk\n[Peer]\nPublicKey = $other\nPresharedKey = $other\n"),
        )
        // A peer with one and another without is mixed too (Termoak keeps one for the network).
        assertEquals(ConfError.MixedPresharedKeys, bad("[Interface]\n[Peer]\nPublicKey = $pub\nPresharedKey = $psk\n[Peer]\nPublicKey = $other\n"))
        // The error texts never carry the key.
        val text = Networks.confErrorText(ConfError.BadKey("PrivateKey")) as UiText.Res
        assertEquals(listOf<Any>("PrivateKey"), text.args)
    }

    @Test
    fun whatStopsSaving() {
        fun conf(addresses: List<String> = listOf("10.0.0.2/32"), peers: List<WgPeer>) = WgConf(addresses = addresses, peers = peers)
        val peer = WgPeer(pub, "vpn.example.com:51820", listOf("10.0.0.0/24"))
        assertEquals(ConfError.NoAddress, Networks.problem(conf(emptyList(), listOf(peer))))
        assertEquals(ConfError.NoAllowedIps(1), Networks.problem(conf(peers = listOf(peer.copy(allowedIps = emptyList())))))
        assertEquals(ConfError.BadEndpoint(2), Networks.problem(conf(peers = listOf(peer, peer.copy(endpoint = "no-port")))))
        assertEquals(ConfError.NoEndpoint, Networks.problem(conf(peers = listOf(peer.copy(endpoint = null)))))
        assertTrue(Networks.validEndpoint("[fd00::1]:51820"))
        assertFalse(Networks.validEndpoint("host:70000"))
        assertTrue(Networks.check("[Interface]\n[Peer]\nPublicKey = $pub\nAllowedIPs = 10.0.0.0/24\nEndpoint = a:1\n") is ConfRead.Bad)
    }

    @Test
    fun networksOfTheApiAndTheirConfText() {
        val json = """[{"id":"n2","name":"lab","kind":"wireguard","vault_id":"v","has_secret":false,"wireguard":{"addresses":["10.1.0.2/32"],"peers":[]}},
            {"id":"n1","name":"Office","kind":"wireguard","description":"HQ","vault_id":"v","has_secret":true,"secret_hidden":false,"future":1,
             "wireguard":{"addresses":["10.9.0.2/32"],"dns":["10.9.0.1"],"mtu":null,"public_key":"$pub",
             "peers":[{"public_key":"$pub","endpoint":"vpn:51820","allowed_ips":["10.9.0.0/24"],"persistent_keepalive":25}]}}]"""
        val list = Networks.networks(json)
        assertEquals(listOf("lab", "Office"), list.map { it.name })
        val n = list[1]
        assertTrue(n.hasSecret)
        assertEquals(pub, n.publicKey)
        assertEquals(listOf(WgPeer(pub, "vpn:51820", listOf("10.9.0.0/24"), 25)), n.peers)
        // Its .conf (no keys but the peers'), read back the same.
        val text = Networks.confText(n, "the key is kept")
        assertTrue(text.contains("# the key is kept"))
        assertFalse(text.contains("PrivateKey"))
        val again = ok(text)
        assertEquals(n.addresses, again.addresses)
        assertEquals(n.peers, again.peers)
        // The [Peer] block for the other side.
        assertEquals("[Peer]\n# Termoak: Office\nPublicKey = $pub\nAllowedIPs = 10.9.0.2/32, 10.0.0.3/32", Networks.peerSnippet("Office", pub, listOf("10.9.0.2/32", "10.0.0.3")))
    }

    @Test
    fun bodiesKeepWhatTheServerSent() {
        val n = Networks.networks("""[{"id":"n1","name":"Office","kind":"wireguard","future":{"x":1},"vault_id":"v","wireguard":{"addresses":["10.9.0.2/32"],"public_key":"$pub","peers":[]}}]""")[0]
        val conf = WgConf(addresses = listOf("10.9.0.5/32"), peers = listOf(WgPeer(pub, "vpn:1", listOf("10.9.0.0/24"), 25)))
        // Edit, keeping the key: no secret, the unknown fields stay, no id, no public key (the server derives it).
        val keep = JSONObject(Networks.body(n.json, null, "Office 2", "", conf, null))
        assertFalse(keep.has("secret"))
        assertFalse(keep.has("id"))
        assertEquals(1, keep.getJSONObject("future").getInt("x"))
        assertEquals("Office 2", keep.getString("name"))
        assertFalse(keep.getJSONObject("wireguard").has("public_key"))
        assertEquals("10.9.0.5/32", keep.getJSONObject("wireguard").getJSONArray("addresses").getString(0))
        // New, with the keys of the .conf.
        val created = JSONObject(Networks.body(null, "vault-1", "Lab", "x", conf, priv to psk))
        assertEquals("vault-1", created.getString("vault_id"))
        assertEquals("wireguard", created.getString("kind"))
        assertEquals(priv, created.getJSONObject("secret").getString("private_key"))
        assertEquals(psk, created.getJSONObject("secret").getString("preshared_key"))
        // A preshared key alone (the server generates the private one next).
        assertTrue(JSONObject(Networks.body(null, "v", "Lab", "", conf, null to psk)).getJSONObject("secret").isNull("private_key"))
        // A host in the network.
        val host = JSONObject(Networks.hostBody("nas", "10.9.0.7", 2222, null, n))
        assertEquals("v", host.getString("vault_id"))
        assertEquals("n1", host.getJSONObject("settings").getString("network_id"))
        assertEquals(2222, host.getJSONObject("settings").getInt("port"))
    }

    @Test
    fun statesFromTheServer() {
        val up = Networks.status("""{"state":"up","last_handshake_at":1760000000000,"rx_bytes":1500,"tx_bytes":20,"node":{"addresses":["10.9.0.2"]}}""")!!
        assertEquals(NetState.UP, Networks.state(up, "wireguard"))
        assertEquals(listOf("10.9.0.2"), up.nodeAddresses)
        // Up without a handshake: waiting for the peer (WireGuard only).
        val waiting = Networks.status("""{"state":"up"}""")
        assertEquals(NetState.WAITING, Networks.state(waiting, "wireguard"))
        assertEquals(NetState.UP, Networks.state(waiting, "tailscale"))
        assertEquals(NetState.DOWN, Networks.state(Networks.status("""{"state":"down"}"""), "wireguard"))
        assertEquals(NetState.ERROR, Networks.state(Networks.status("""{"state":"error","message":"bad key"}"""), "wireguard"))
        // A tailnet's node needs a login (or an approval, TailscaleNetworksTest); other kinds are "authorize".
        assertEquals(NetState.NEEDS_LOGIN, Networks.state(Networks.status("""{"state":"needs_auth"}"""), "tailscale"))
        assertEquals(NetState.NEEDS_AUTH, Networks.state(Networks.status("""{"state":"needs_auth"}"""), "zerotier"))
        // Not asked, or the server didn't answer: unknown (not an error page).
        assertEquals(NetState.UNKNOWN, Networks.state(null, "wireguard"))
        assertNull(Networks.status("not json"))
        assertEquals("1.5 kB", Networks.bytes(1500))
        assertEquals(Pair(3, 10), Networks.usage("""{"usage":{"networks":3},"plan":{"limits":{"max_networks":10}}}"""))
        assertEquals(Pair(0, null), Networks.usage("""{"usage":{},"plan":{"limits":{"max_networks":null}}}"""))
        assertTrue(Networks.supported("""{"features":{"networks":true}}"""))
        assertFalse(Networks.supported("""{"features":{"networks":false}}"""))
        assertFalse(Networks.supported("""{"features":{}}"""))
    }

    @Test
    fun peersGiveHostAddresses() {
        val peers = Networks.peers(
            """[{"name":"vpn.example.com:51820","addresses":["10.9.0.1/32","10.9.0.0/24","0.0.0.0/0","fd00::1/128","nonsense"],"online":true,"last_seen":1760000000000,"public_key":"$pub"},
               {"dns_name":"nas.tail1234.ts.net.","addresses":["100.64.0.7"]}]""",
        )
        assertEquals(2, peers.size)
        assertEquals("vpn.example.com:51820", peers[0].title)
        val a = Networks.hostAddresses(peers[0])
        assertEquals(listOf(HostAddress("10.9.0.1", true, "10.9.0.1/32"), HostAddress("10.9.0.", false, "10.9.0.0/24"), HostAddress("fd00::1", true, "fd00::1/128")), a)
        val b = Networks.hostAddresses(peers[1])
        assertEquals(HostAddress("nas.tail1234.ts.net", true, "nas.tail1234.ts.net."), b[0])
        assertEquals("100.64.0.7", b[1].address)
        assertEquals("xTIBA5rboU…", NetPeer(publicKey = pub).title)
    }

    @Test
    fun hostsInheritTheNetworkOfTheirGroups() {
        val groups = listOf(
            GroupLink("root", "Servers", null, "net-office"),
            GroupLink("web", "Web", "root", null),
            GroupLink("lab", "Lab", "root", "net-lab"),
            GroupLink("loop1", "Loop 1", "loop2", null),
            GroupLink("loop2", "Loop 2", "loop1", null),
        )
        assertEquals("net-office", Networks.effective(null, "web", groups))
        assertEquals("net-lab", Networks.effective(null, "lab", groups))
        assertEquals("own", Networks.effective("own", "web", groups))
        assertNull(Networks.effective(null, null, groups))
        // A cycle of groups ends (no network).
        assertNull(Networks.effective(null, "loop1", groups))
        assertEquals("Servers", Networks.inherited("web", groups)!!.group.name)
    }

    @Test
    fun thePickerOffersTheSameVaultAndKeepsAMissingOne() {
        val groups = listOf(GroupLink("g", "Servers", null, "n1"))
        val nets = listOf(Triple("n2", "lab", "wireguard"), Triple("n1", "Office", "wireguard"))
        // A host in a group with a network: empty inherits it ("Same as group “Servers” (via Office)").
        val c = Networks.choices(null, "g", groups, nets)
        val inherit = c[0] as NetworkChoice.Inherit
        assertEquals("n1", inherit.inherited!!.networkId)
        assertEquals("Office", inherit.inheritedName)
        assertNull(inherit.id)
        assertEquals(listOf("lab", "Office"), c.drop(1).map { (it as NetworkChoice.Network).name })
        // Without a group: direct.
        assertNull((Networks.choices(null, null, groups, nets)[0] as NetworkChoice.Inherit).inherited)
        // A network that isn't in the vault (another vault's, deleted, not synced): kept, flagged.
        val missing = Networks.choices("gone", null, emptyList(), nets)
        assertEquals(NetworkChoice.Missing("gone"), missing.last())
        assertEquals(4, missing.size)
        // The group's network missing from the vault: inherited without a name.
        assertNull((Networks.choices(null, "g", groups, emptyList())[0] as NetworkChoice.Inherit).inheritedName)
    }

    @Test
    fun networkErrorsAreFriendly() {
        assertEquals(
            UiText.Plural(R.plurals.error_plan_limit_max_networks, 10, listOf(10)),
            Networks.errorText("plan_limit", """{"limit":"max_networks","max":10}"""),
        )
        assertEquals(UiText.Res(R.string.error_plan_limit_networks_unknown), Networks.errorText("plan_limit", """{"limit":"max_networks"}"""))
        // Other plan limits are not the networks' business.
        assertNull(Networks.errorText("plan_limit", """{"limit":"max_teams","max":3}"""))
        assertEquals(UiText.Res(R.string.networks_netd_timeout), Networks.errorText("network_dial_failed", """{"netd_code":"timeout"}"""))
        assertEquals(UiText.Res(R.string.networks_netd_needs_auth), Networks.errorText("network_dial_failed", """{"netd_code":"needs_auth"}"""))
        assertEquals(UiText.Res(R.string.error_network_dial_failed), Networks.errorText("network_dial_failed", "{}"))
        assertEquals(UiText.Res(R.string.error_unsupported_kind), Networks.errorText("network_unavailable", """{"netd_code":"unsupported_kind"}"""))
        assertEquals(UiText.Res(R.string.error_network_unavailable), Networks.errorText("network_unavailable", "{}"))
        assertEquals(UiText.Res(R.string.error_network_not_found), Networks.errorText("network_not_found", """{"network_id":"x"}"""))
        assertEquals(UiText.Res(R.string.error_key_exists), Networks.errorText("key_exists", null))
        assertEquals(UiText.Res(R.string.error_not_wireguard), Networks.errorText("not_wireguard", null))
        assertEquals(UiText.Res(R.string.error_relay_limit, listOf(16)), Networks.errorText("relay_limit", """{"max":16}"""))
        assertNull(Networks.errorText("invalid_password", "{}"))
        // The engine's own errors through the relay: the server's message only.
        assertEquals(
            UiText.Res(R.string.error_relay_limit_unknown),
            Networks.errorText("network_dial_failed", "{}", "server relay to nas:22: the server responded 429 (relay_limit): too many", device = true),
        )
        assertEquals(
            UiText.Res(R.string.error_network_dial_failed_detail, listOf("dial 10.9.0.9:22: connection refused")),
            Networks.errorText("network_dial_failed", "{}", "dial 10.9.0.9:22: connection refused", device = true),
        )
        assertEquals(UiText.Res(R.string.error_network_unavailable_device), Networks.errorText("network_unavailable", "{}", "nas: sign in", device = true))
    }

    @Test
    fun engineErrorsBecomeTheirTexts() {
        val plan = TermoakException.Forbidden(FakeApiErrors.with(403, "plan_limit", "plan limit", """{"limit":"max_networks","max":10}"""))
        assertEquals(UiText.Plural(R.plurals.error_plan_limit_max_networks, 10, listOf(10)), networkError(plan))
        val relay = TermoakException.NetworkUnavailable(FakeApiErrors.device("nas: the account's server does not support private networks", "network_unavailable"))
        assertEquals(UiText.Res(R.string.error_network_unavailable_device), networkError(relay))
        // Without details the variant still says it.
        assertEquals(UiText.Res(R.string.error_network_unavailable), networkError(TermoakException.NetworkUnavailable("x")))
        assertNull(networkError(TermoakException.Connection("refused")))
        assertNull(networkError(TermoakException.Conflict(FakeApiErrors.with(409, "last_admin", "only admin"))))
    }

    @Test
    fun hostTests() {
        val ok = Networks.hostTest("""{"ok":true,"latency_ms":23,"network":{"id":"n1","name":"Office","kind":"wireguard"}}""")!!
        assertTrue(ok.ok)
        assertEquals("Office", ok.networkName)
        val unknown = Networks.hostTest("""{"ok":false,"error":"unknown key","error_code":"host_key_unknown","fingerprint":"SHA256:abc"}""")!!
        assertEquals("SHA256:abc", unknown.fingerprint)
        assertEquals(UiText.Res(R.string.networks_test_host_key_unknown), Networks.hostTestError(unknown))
        assertEquals(UiText.Res(R.string.error_network_dial_failed), Networks.hostTestError(HostTest(false, errorCode = "network_dial_failed")))
        assertEquals(UiText.Raw("Something odd"), Networks.hostTestError(HostTest(false, error = "something odd", errorCode = "weird")))
        assertEquals(UiText.Res(R.string.networks_test_failed), Networks.hostTestError(HostTest(false)))
        // "via Office · 23 ms" after a test through that network; "via Office" otherwise.
        val via = { n: String -> "via $n" }
        val viaMs = { n: String, ms: String -> "via $n · $ms" }
        assertEquals("via Office · 23 ms", com.termoak.app.ui.viaText("Office", ok, "n1", false, viaMs, via))
        assertEquals("via Office", com.termoak.app.ui.viaText("Office", ok, "n2", false, viaMs, via))
        assertEquals("via Office · …", com.termoak.app.ui.viaText("Office", null, "n1", true, viaMs, via))
        assertEquals("via Office", com.termoak.app.ui.viaText("Office", unknown, "n1", false, viaMs, via))
    }

    @Test
    fun theRestCalls() = runBlocking {
        val sent = mutableListOf<Triple<HttpMethod, String, String?>>()
        val states = ArrayDeque(listOf("""{"state":"up"}""", """{"state":"up"}""", """{"state":"up","last_handshake_at":5}"""))
        var paused = 0
        val api = NetworksApi({ method, path, body ->
            sent += Triple(method, path, body)
            when {
                path.endsWith("/up") -> """{"state":"starting"}"""
                path.endsWith("/status") -> states.removeFirst()
                path.endsWith("/wireguard/key") -> """{"public_key":"$pub","network":{}}"""
                path == "/api/v1/info" -> """{"features":{"networks":true}}"""
                path.startsWith("/api/v1/hosts/h1/test") -> """{"ok":false,"error_code":"host_key_unknown"}"""
                else -> "null"
            }
        }, pause = { paused++ })
        assertTrue(api.supported())
        // Test: up, then the state about once a second until the handshake.
        val seen = mutableListOf<String>()
        val st = api.test("n1", "wireguard") { seen += it.state }
        assertEquals(5L, st.lastHandshakeAt)
        assertEquals(3, paused)
        assertEquals(listOf("starting", "up", "up", "up"), seen)
        assertEquals(HttpMethod.POST to "/api/v1/networks/n1/up", sent[1].first to sent[1].second)
        // The key: replace only when asked.
        assertEquals(pub, api.generateKey("n1", replace = true))
        assertTrue(JSONObject(sent.last().third!!).getBoolean("replace"))
        // The host test, trusting a new key on the second try.
        assertEquals("host_key_unknown", api.testHost("h1", trust = false).errorCode)
        api.testHost("h1", trust = true)
        assertEquals("/api/v1/hosts/h1/test?trust=true", sent.last().second)
        // A status that can't be read is "unknown", not an error.
        val failing = NetworksApi({ _, _, _ -> throw TermoakException.Server("netd down") })
        assertNull(failing.status("n1"))
    }

    @Test
    fun testGivesUpAfterAFewSeconds() = runBlocking {
        var polls = 0
        val api = NetworksApi({ _, path, _ -> if (path.endsWith("/status")) { polls++; """{"state":"up"}""" } else """{"state":"up"}""" }, pause = {})
        val st = api.test("n1", "wireguard")
        assertEquals(NetState.WAITING, Networks.state(st, "wireguard"))
        assertEquals(Networks.TEST_POLLS, polls)
    }
}
