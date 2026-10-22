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
 * Tailscale/Headscale networks (NETWORKS.md §8.1, phase 2b): the kinds the
 * server joins, the form's body (ephemeral, the key kept, replaced or
 * removed, the default hostname), the core's rules, the states, the
 * devices → hosts (imported ones marked) and the error texts, also the
 * ones host tests and connections only carry as netd's English message.
 */
@RunWith(RobolectricTestRunner::class) // org.json is the Android one.
@Config(sdk = [34], application = Application::class)
class TailscaleNetworksTest {
    @Before
    fun engineErrors() = FakeApiErrors.install()

    private val tailnetJson = """{"id":"0b9f3c2e-1111-4a5b-8c7d-12345678abcd","name":"Tailnet","kind":"tailscale","vault_id":"v1",
        "description":"","has_secret":true,"secret_hidden":false,"created_at":5,
        "tailscale":{"control_url":"https://hs.example.com","hostname":null,"tags":["tag:termoak"],"ephemeral":null,"future":"kept"}}"""

    @Test
    fun tailscaleOnlyWhenTheServerListsIt() {
        assertEquals(listOf("tailscale", "wireguard"), Networks.kinds("""{"features":{"networks":true,"network_kinds":["tailscale","wireguard"]}}"""))
        // A server without the field (0.7.0-next.2): WireGuard only.
        assertEquals(listOf("wireguard"), Networks.kinds("""{"features":{"networks":true}}"""))
        assertEquals(emptyList<String>(), Networks.kinds("""{"features":{"networks":false,"network_kinds":["tailscale"]}}"""))
        assertEquals(emptyList<String>(), Networks.kinds("not json"))
        NetworkBook.reset()
        NetworkBook.setKinds("a1", listOf("wireguard"))
        assertTrue(NetworkBook.supported("a1"))
        assertFalse(NetworkBook.kindSupported("a1", Networks.TAILSCALE))
        NetworkBook.setKinds("a1", listOf("tailscale", "wireguard"))
        assertTrue(NetworkBook.kindSupported("a1", Networks.TAILSCALE))
        assertFalse(NetworkBook.kindSupported(null, Networks.TAILSCALE))
        NetworkBook.setKinds("a1", emptyList())
        assertFalse(NetworkBook.supported("a1"))
        NetworkBook.reset()
    }

    @Test
    fun readsATailnet() {
        val n = Networks.network(JSONObject(tailnetJson))
        assertTrue(n.isTailscale)
        assertEquals(TsConfig("https://hs.example.com", null, listOf("tag:termoak"), null), n.tailscale)
        assertEquals("hs.example.com", Networks.controlServerLabel(n.tailscale?.controlUrl))
        assertEquals("hs.example.com:8080", Networks.controlServerLabel("http://hs.example.com:8080/x"))
        assertEquals("Tailscale", Networks.controlServerLabel(null))
    }

    @Test
    fun theFormsBodySendsTheWholeNetworkBack() {
        val n = Networks.network(JSONObject(tailnetJson))
        // Untouched ephemeral stays null; the key kept: no secret at all.
        val keep = JSONObject(Networks.tailscaleBody(n.json, null, "Tailnet", "", TsConfig("https://hs.example.com/", null, listOf("tag:termoak"), null), SecretChange.Keep))
        assertFalse(keep.has("id"))
        assertFalse(keep.has("secret"))
        val ts = keep.getJSONObject("tailscale")
        assertTrue(ts.has("ephemeral") && ts.isNull("ephemeral"))
        assertEquals("https://hs.example.com", ts.getString("control_url"))
        assertTrue(ts.isNull("hostname"))
        // What the server sent and this version doesn't know goes back too.
        assertEquals("kept", ts.getString("future"))
        assertEquals(5, keep.getInt("created_at"))
        assertEquals("tailscale", keep.getString("kind"))
        // Remove the key: secret null (`{}` would store an empty secret and has_secret would stay true).
        val clear = JSONObject(Networks.tailscaleBody(n.json, null, "Tailnet", "", TsConfig(), SecretChange.Clear))
        assertTrue(clear.has("secret") && clear.isNull("secret"))
        assertTrue(clear.getJSONObject("tailscale").isNull("control_url"))
        // A new key, ephemeral changed by the user.
        val set = JSONObject(Networks.tailscaleBody(null, "v1", "New", "d", TsConfig(null, " office-1 ", listOf("tag:a"), false), SecretChange.Set(" tskey-auth-k ")))
        assertEquals("tskey-auth-k", set.getJSONObject("secret").getString("auth_key"))
        assertEquals("v1", set.getString("vault_id"))
        assertEquals("office-1", set.getJSONObject("tailscale").getString("hostname"))
        assertFalse(set.getJSONObject("tailscale").getBoolean("ephemeral"))
        // A new network without a key: no secret.
        assertFalse(JSONObject(Networks.tailscaleBody(null, "v1", "New", "", TsConfig(), SecretChange.Keep)).has("secret"))
    }

    @Test
    fun theDefaultHostnameAndTags() {
        assertEquals("termoak-5678abcd", Networks.defaultHostname("0b9f3c2e-1111-4a5b-8c7d-12345678ABCD"))
        assertEquals("termoak-xxxxxxxx", Networks.defaultHostname(null))
        assertEquals(listOf("tag:a", "tag:b", "tag:c"), Networks.parseTags("tag:a, tag:b\ntag:c ,"))
    }

    @Test
    fun theCoresRulesBeforeSaving() {
        assertNull(Networks.tailscaleProblem(null, "", emptyList(), null))
        assertNull(Networks.tailscaleProblem("https://hs.example.com:8443/path", "termoak-office", listOf("tag:termoak", "tag:a_b-1"), "hskey-auth-abc"))
        assertEquals(TsProblem.CONTROL_URL_MISSING, Networks.tailscaleProblem("  ", null, emptyList(), null))
        assertEquals(TsProblem.CONTROL_URL, Networks.tailscaleProblem("hs.example.com", null, emptyList(), null))
        assertEquals(TsProblem.CONTROL_URL, Networks.tailscaleProblem("https://user@hs.example.com", null, emptyList(), null))
        assertEquals(TsProblem.CONTROL_URL, Networks.tailscaleProblem("https://hs.example.com/?x=1", null, emptyList(), null))
        assertEquals(TsProblem.CONTROL_URL, Networks.tailscaleProblem("https:///x", null, emptyList(), null))
        assertEquals(TsProblem.HOSTNAME, Networks.tailscaleProblem(null, "-office", emptyList(), null))
        assertEquals(TsProblem.HOSTNAME, Networks.tailscaleProblem(null, "a".repeat(64), emptyList(), null))
        assertEquals(TsProblem.TAGS, Networks.tailscaleProblem(null, null, listOf("termoak"), null))
        assertEquals(TsProblem.TAGS, Networks.tailscaleProblem(null, null, listOf("tag:-x"), null))
        assertEquals(TsProblem.TAGS_MAX, Networks.tailscaleProblem(null, null, (1..21).map { "tag:t$it" }, null))
        assertEquals(TsProblem.AUTH_KEY, Networks.tailscaleProblem(null, null, emptyList(), "has space"))
        assertEquals(TsProblem.AUTH_KEY, Networks.tailscaleProblem(null, null, emptyList(), "ñ"))
        // Its text never echoes the key.
        assertEquals(UiText.Res(R.string.networks_ts_error_auth_key), Networks.tsProblemText(TsProblem.AUTH_KEY))
        assertEquals(UiText.Res(R.string.networks_ts_error_tags_max, listOf(20)), Networks.tsProblemText(TsProblem.TAGS_MAX))
    }

    @Test
    fun statesOfATailnet() {
        val ts = Networks.TAILSCALE
        // No handshake in a tailnet: up is connected.
        assertEquals(NetState.UP, Networks.state(NetStatus("up"), ts))
        assertEquals(NetState.WAITING, Networks.state(NetStatus("up"), Networks.WIREGUARD))
        assertEquals(NetState.NEEDS_LOGIN, Networks.state(NetStatus("needs_auth", auth = "login"), ts))
        assertEquals(NetState.NEEDS_LOGIN, Networks.state(NetStatus("needs_auth"), ts))
        assertEquals(NetState.NEEDS_APPROVAL, Networks.state(NetStatus("needs_auth", auth = "approval"), ts))
        assertEquals(NetState.NEEDS_AUTH, Networks.state(NetStatus("needs_auth", auth = "approval"), Networks.ZEROTIER))
        assertEquals(NetState.STARTING, Networks.state(NetStatus("starting"), ts))
        val st = Networks.status(
            """{"state":"up","control_url":"https://hs.example.com","tailnet":"qa","magic_dns_suffix":"ts.qa.test","peer_count":4,
                "ephemeral":true,"last_error":null,"node":{"name":"termoak-5678abcd","dns_name":"termoak-5678abcd.ts.qa.test.",
                "addresses":["100.64.0.3","fd7a:115c:a1e0::3"],"node_id":"nX","auth_url":"https://hs.example.com/register/abc"},"rx_bytes":10}""",
        )!!
        assertEquals(4, st.peerCount)
        assertEquals(true, st.ephemeral)
        assertEquals("termoak-5678abcd", st.nodeName)
        assertEquals("nX", st.nodeId)
        assertEquals("https://hs.example.com/register/abc", st.authUrl)
        assertNull(st.lastError)
        assertEquals("100.64.0.3 · 4 devices", Networks.tailnetSummary(st) { "$it devices" })
        assertEquals("0 devices", Networks.tailnetSummary(NetStatus("up")) { "$it devices" })
    }

    @Test
    fun devicesBecomeHostsAndAddedOnesAreMarked() {
        val peers = Networks.peers(
            """[{"name":"nas","dns_name":"nas.ts.qa.test.","addresses":["100.64.0.5","fd7a::5"],"os":"linux","online":true,"tags":["tag:srv"],"routes":["192.168.10.0/24","0.0.0.0/0","::/0"]},
                {"name":"laptop","dns_name":"laptop.ts.qa.test.","addresses":["100.64.0.6"],"online":false,"last_seen":1000},
                {"name":"phone","addresses":["100.64.0.7/32"]},
                {"name":"ghost"}]""",
        )
        val nas = peers[0]
        assertEquals("nas.ts.qa.test", nas.deviceAddress)
        assertEquals(listOf("192.168.10.0/24"), nas.hostRoutes)
        assertEquals(listOf("tag:srv"), nas.tags)
        assertEquals("linux", nas.os)
        assertEquals("100.64.0.7", peers[2].deviceAddress)
        assertNull(peers[3].deviceAddress)
        // A host of the same network by MagicDNS name, short label, an IP or the device's name.
        val n = "n1"
        assertEquals("h1", Networks.importedHost(nas, n, listOf(HostRef("h1", "x", "NAS.ts.qa.test.", n)))?.id)
        assertEquals("h1", Networks.importedHost(nas, n, listOf(HostRef("h1", "x", "nas", n)))?.id)
        assertEquals("h1", Networks.importedHost(nas, n, listOf(HostRef("h1", "x", "fd7a::5", n)))?.id)
        assertEquals("h1", Networks.importedHost(peers[1], n, listOf(HostRef("h1", "laptop", "10.0.0.9", n)))?.id)
        // Another network, or direct: not this device's host.
        assertNull(Networks.importedHost(nas, n, listOf(HostRef("h1", "nas", "nas.ts.qa.test", "n2"))))
        assertNull(Networks.importedHost(nas, n, listOf(HostRef("h1", "nas", "nas.ts.qa.test", null))))
        // "Add N hosts": the ones not added yet and with an address.
        val hosts = listOf(HostRef("h1", "nas", "nas.ts.qa.test", n))
        assertEquals(listOf("laptop", "phone"), Networks.newDevices(peers, n, hosts).map { it.name })
        assertEquals(listOf("nas", "ghost", "laptop", "phone"), Networks.sortDevices(peers).map { it.name })
        // The host it makes: its name, its MagicDNS name, port 22, in the network's vault.
        val net = Networks.network(JSONObject(tailnetJson))
        val body = JSONObject(Networks.deviceHostBody(peers[1], net)!!)
        assertEquals("laptop", body.getString("label"))
        assertEquals("laptop.ts.qa.test", body.getString("address"))
        assertEquals(22, body.getJSONObject("settings").getInt("port"))
        assertEquals(net.id, body.getJSONObject("settings").getString("network_id"))
        assertEquals("v1", body.getString("vault_id"))
        assertEquals(
            listOf(HostRef("h1", "nas", "nas.ts.qa.test", "n1"), HostRef("h2", "web", "10.0.0.1", null)),
            Networks.hostRefs("""[{"id":"h1","label":"nas","address":"nas.ts.qa.test","settings":{"network_id":"n1"}},{"id":"h2","label":"web","address":"10.0.0.1","settings":{}}]"""),
        )
    }

    @Test
    fun errorsOfATailnetByNetdCodeAndAuth() {
        val t = { code: String, d: String, m: String? -> Networks.tailscaleErrorText(code, d, m, "Tailnet") }
        assertEquals(UiText.Res(R.string.networks_ts_error_login, listOf("Tailnet")), t("network_dial_failed", """{"netd_code":"needs_auth","auth":"login"}""", null))
        assertEquals(UiText.Res(R.string.networks_ts_error_approval, listOf("Tailnet")), t("network_dial_failed", """{"netd_code":"needs_auth","auth":"approval"}""", null))
        assertEquals(
            UiText.Res(R.string.networks_ts_error_network_error, listOf("Tailnet", "the control server refused the auth key: invalid key")),
            t("network_dial_failed", """{"netd_code":"network_error"}""", "the network \"Tailnet\" could not start: the control server refused the auth key: invalid key"),
        )
        assertEquals(
            UiText.Res(R.string.networks_ts_error_dial_acl, listOf("8.8.8.8 is outside the tailnet (no device has that address or routes its subnet)")),
            t("network_dial_failed", """{"netd_code":"dial_failed"}""", "8.8.8.8 is outside the tailnet (no device has that address or routes its subnet)"),
        )
        assertEquals(UiText.Raw("Connection refused."), t("network_dial_failed", """{"netd_code":"dial_failed"}""", "connection refused"))
        assertEquals(UiText.Res(R.string.networks_ts_error_timeout), t("network_dial_failed", """{"netd_code":"timeout"}""", "x"))
        assertEquals(UiText.Res(R.string.networks_ts_error_limit), t("network_dial_failed", """{"netd_code":"limit"}""", "x"))
        assertEquals(UiText.Res(R.string.networks_ts_unsupported), t("network_unavailable", """{"netd_code":"unsupported_kind"}""", "x"))
        assertNull(t("plan_limit", """{"limit":"max_networks"}""", null))
        // The generic texts (any screen) with the details' auth.
        assertEquals(UiText.Res(R.string.networks_ts_netd_needs_login), Networks.errorText("network_dial_failed", """{"netd_code":"needs_auth","auth":"login"}"""))
        assertEquals(UiText.Res(R.string.networks_ts_netd_needs_approval), Networks.errorText("network_dial_failed", """{"netd_code":"needs_auth","auth":"approval"}"""))
        // The devices list: "log the server in first".
        assertTrue(Networks.needsLogin("network_dial_failed", """{"netd_code":"needs_auth","auth":"login"}"""))
        assertFalse(Networks.needsLogin("network_dial_failed", """{"netd_code":"timeout"}"""))
        // From the engine's error: the screens' text names the network.
        val net = Networks.network(JSONObject(tailnetJson))
        val e = TermoakException.Server(FakeApiErrors.with(502, "network_dial_failed", "waits", """{"netd_code":"needs_auth","auth":"login"}"""))
        assertEquals(UiText.Res(R.string.networks_ts_error_login, listOf("Tailnet")), Networks.errorFor(e, net, R.string.networks_test_failed))
    }

    @Test
    fun hostTestsAndConnectionsOnlyCarryNetdsMessage() {
        fun test(error: String) = HostTest(false, error = error, errorCode = "network_dial_failed", networkName = "Tailnet", networkKind = "tailscale")
        assertEquals(
            UiText.Res(R.string.networks_ts_error_login, listOf("Tailnet")),
            Networks.hostTestError(test("the network \"Tailnet\" waits for authorization: the node must be logged in: open the login URL")),
        )
        // The approval message says "logged in" too: it is still an approval.
        assertEquals(
            UiText.Res(R.string.networks_ts_error_approval, listOf("Tailnet")),
            Networks.hostTestError(test("the network \"Tailnet\" waits for authorization: the node is logged in but must be approved: approve it in the tailnet's admin console (Machines) or with headscale")),
        )
        assertEquals(UiText.Res(R.string.networks_ts_error_timeout), Networks.hostTestError(test("the network \"Tailnet\" is still starting: joining")))
        assertEquals(
            UiText.Res(R.string.networks_ts_error_network_error, listOf("Tailnet", "the control server refused the auth key: bad")),
            Networks.hostTestError(test("the network \"Tailnet\" could not start: the control server refused the auth key: bad")),
        )
        assertEquals(
            UiText.Res(R.string.networks_ts_error_dial_acl, listOf("There is no device named nas in the tailnet (or it is not shared with the server's node)")),
            Networks.hostTestError(test("there is no device named nas in the tailnet (or it is not shared with the server's node)")),
        )
        assertEquals(UiText.Raw("Dial tcp: refused."), Networks.hostTestError(test("dial tcp: refused")))
        // A WireGuard network keeps the old texts.
        assertEquals(UiText.Res(R.string.error_network_dial_failed), Networks.hostTestError(HostTest(false, errorCode = "network_dial_failed", error = "x", networkKind = "wireguard")))
        assertEquals("tailscale", Networks.hostTest("""{"ok":false,"error_code":"network_dial_failed","network":{"id":"n1","name":"T","kind":"tailscale"}}""")!!.networkKind)
        // Connections through the relay: the engine's error keeps the server's message only.
        val relay = TermoakException.Connection(
            FakeApiErrors.device("the network \"Tailnet\" waits for authorization: the node must be logged in: open the login URL", "network_dial_failed"),
        )
        assertEquals(UiText.Res(R.string.networks_ts_error_login, listOf("Tailnet")), networkError(relay))
        assertEquals(
            UiText.Res(R.string.networks_ts_error_dial_acl, listOf("127.0.0.1 is outside the tailnet")),
            Networks.errorText("network_dial_failed", "{}", "127.0.0.1 is outside the tailnet", device = true),
        )
        // "Still starting" without the kind is any network's: the old text with netd's words.
        assertEquals(
            UiText.Res(R.string.error_network_dial_failed_detail, listOf("the network \"Office\" is still starting: x")),
            Networks.errorText("network_dial_failed", "{}", "the network \"Office\" is still starting: x", device = true),
        )
        assertEquals("the control server refused the auth key: bad", Networks.tailscaleReason("the network \"A\" could not start: the control server refused the auth key: bad"))
    }

    @Test
    fun aBadRequestSaysTheServersReason() {
        val bad = TermoakException.Invalid(FakeApiErrors.with(400, "bad_request", "invalid tag \"x\": tags look like tag:termoak"))
        assertEquals(UiText.Res(R.string.networks_form_error_server, listOf("invalid tag \"x\": tags look like tag:termoak")), Networks.saveErrorText(bad))
        val other = TermoakException.Forbidden(FakeApiErrors.with(403, "plan_limit", "limit", """{"limit":"max_networks","max":2}"""))
        assertEquals(UiText.Plural(R.plurals.error_plan_limit_max_networks, 2, listOf(2)), Networks.saveErrorText(other))
    }

    @Test
    fun testWaitsWhileTheNodeStartsAndTheLoginIsFollowed() = runBlocking {
        val states = ArrayDeque(listOf("starting", "starting", "needs_auth"))
        var paused = 0L
        val api = NetworksApi({ _, path, _ ->
            when {
                path.endsWith("/up") -> """{"state":"starting"}"""
                path.endsWith("/status") -> """{"state":"${states.removeFirst()}","auth":"login","node":{"auth_url":"https://x/register/1"}}"""
                else -> "null"
            }
        }, pause = { paused += it })
        val st = api.test("n1", Networks.TAILSCALE)
        assertEquals(NetState.NEEDS_LOGIN, Networks.state(st, Networks.TAILSCALE))
        assertEquals("https://x/register/1", st.authUrl)
        assertEquals(3000L, paused)
        // Starting for longer than 30 s: it stops asking.
        var polls = 0
        val slow = NetworksApi({ _, path, _ -> if (path.endsWith("/status")) polls++; """{"state":"starting"}""" }, pause = {})
        assertEquals("starting", slow.test("n1", Networks.TAILSCALE).state)
        assertEquals(Networks.TS_TEST_POLLS, polls)
        // A tailnet that is up stops at once (no handshake to wait for).
        polls = 0
        val up = NetworksApi({ _, path, _ -> if (path.endsWith("/status")) polls++; """{"state":"up"}""" }, pause = {})
        up.test("n1", Networks.TAILSCALE)
        assertEquals(0, polls)
    }

    @Test
    fun theLoginIsFollowedUntilUpOrIdle() = runBlocking {
        val sent = mutableListOf<String>()
        // "ERR": the state can't be read that time (netd down).
        fun api(vararg states: String): NetworksApi {
            val q = ArrayDeque(states.toList())
            return NetworksApi({ method, path, _ ->
                sent += "$method $path"
                val s = q.removeFirstOrNull() ?: "needs_auth"
                if (s == "ERR") throw TermoakException.Server("netd down") else """{"state":"$s"}"""
            }, pause = {})
        }
        var paused = 0L
        // Up after the login: it stops there.
        assertEquals("up", api("needs_auth", "ERR", "starting", "up").watchLogin("n1")?.state)
        // The network went idle meanwhile (down): it stops too, never bringing it up itself.
        sent.clear()
        assertEquals("down", api("needs_auth", "down").watchLogin("n1")?.state)
        assertTrue(sent.all { it == "GET /api/v1/networks/n1/status" })
        // Never more than 5 minutes, every 2 s.
        var polls = 0
        val waiting = NetworksApi({ _, _, _ -> polls++; """{"state":"needs_auth","auth":"login"}""" }, pause = { paused += it })
        assertEquals("needs_auth", waiting.watchLogin("n1")?.state)
        assertEquals((Networks.LOGIN_WAIT_MS / Networks.LOGIN_POLL_MS).toInt(), polls)
        assertEquals(Networks.LOGIN_WAIT_MS, paused)
        // The vault's hosts, to mark devices already added; empty when they can't be read.
        val hosts = NetworksApi({ _, path, _ ->
            assertEquals("/api/v1/hosts?vault_id=v1", path)
            """[{"id":"h1","label":"nas","address":"nas.ts.qa.test","settings":{"network_id":"n1"}}]"""
        })
        assertEquals("h1", hosts.hosts("v1").single().id)
        assertEquals(emptyList<HostRef>(), NetworksApi({ _, _, _ -> throw TermoakException.Server("x") }).hosts("v1"))
        Unit
    }
}
