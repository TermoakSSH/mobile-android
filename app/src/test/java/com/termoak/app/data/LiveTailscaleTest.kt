package com.termoak.app.data

import android.app.Application
import com.termoak.app.R
import com.termoak.app.UiText
import com.termoak.ffi.AuthHandler
import com.termoak.ffi.AuthRequest
import com.termoak.ffi.HttpMethod
import com.termoak.ffi.ItemFilter
import com.termoak.ffi.ServerChoice
import com.termoak.ffi.TermoakCore
import com.termoak.ffi.generateVaultKey
import com.termoak.kit.apiError
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Opt-in end-to-end check of a Tailscale/Headscale network against a real
 * server whose netd joins a real Headscale, through the real engine (built
 * for this computer): the form's body with a pre-auth key, Test until it is
 * connected, the devices → a host by MagicDNS name (then marked as added),
 * SSH from "the phone" through the relay, the server's host test, a dial
 * outside the tailnet, removing the key (`secret: null`), a rejected key
 * and a network without a key (login) that goes down while it is followed.
 * Skipped unless TERMOAK_LIVE_TS_SERVER is set, with TERMOAK_LIVE_FFI_DIR,
 * TERMOAK_LIVE_EMAIL, TERMOAK_LIVE_PASSWORD, TERMOAK_LIVE_TS_CONTROL (the
 * Headscale URL as the server's netd reaches it), TERMOAK_LIVE_TS_KEY (a
 * reusable pre-auth key tagged so the ACLs let it see the site),
 * TERMOAK_LIVE_TS_SITE (the site's hostname in the tailnet, its port 22 an
 * sshd), TERMOAK_LIVE_SSH_USER and TERMOAK_LIVE_SSH_KEY.
 */
@RunWith(RobolectricTestRunner::class) // org.json is the Android one.
// API 33: the engine's objects use JNA's cleaner (API 34's SystemCleaner isn't reachable on the JVM).
@Config(sdk = [33], application = Application::class)
class LiveTailscaleTest {
    private fun env(k: String): String = System.getenv(k) ?: error("$k is not set")

    private object TrustAll : AuthHandler {
        override fun onHostKey(host: String, port: UInt, keyType: String, fingerprint: String) = true
        override fun onPrompt(request: AuthRequest): List<String>? = null
    }

    @Test
    fun liveTailnetFromTheApp() = runBlocking {
        val server = System.getenv("TERMOAK_LIVE_TS_SERVER")
        assumeTrue("TERMOAK_LIVE_TS_SERVER not set: skipped", server != null)
        System.setProperty("jna.library.path", env("TERMOAK_LIVE_FFI_DIR"))
        ApiErrors.lookup = { it.apiError }
        val dir = File(System.getProperty("java.io.tmpdir"), "termoak-live-ts-${System.nanoTime()}").apply { mkdirs() }
        val core = TermoakCore(dir.path, generateVaultKey())
        val info = core.signIn(ServerChoice.Custom(server!!), env("TERMOAK_LIVE_EMAIL"), env("TERMOAK_LIVE_PASSWORD"))
        val handle = core.account(info.id)
        handle.syncNow()
        val api = NetworksApi(handle)
        assertTrue("the server joins no tailnets", Networks.TAILSCALE in api.kinds())
        val vault = info.userId!!
        val control = env("TERMOAK_LIVE_TS_CONTROL")
        val site = env("TERMOAK_LIVE_TS_SITE")
        val log = mutableListOf<String>()
        val created = mutableListOf<String>()
        var hostId: String? = null
        var outsideId: String? = null
        var keyId: String? = null
        var siteAddress: String? = null
        try {
            // The form: Headscale, a key, ephemeral untouched (null).
            val ts = TsConfig(controlUrl = "$control/", hostname = null, tags = emptyList(), ephemeral = null)
            assertNull(Networks.tailscaleProblem(Networks.cleanControlUrl(control), null, emptyList(), env("TERMOAK_LIVE_TS_KEY")))
            val name = "android-ts-${System.nanoTime() % 100000}"
            var net = api.create(Networks.tailscaleBody(null, vault, name, "android live", ts, SecretChange.Set(env("TERMOAK_LIVE_TS_KEY"))))
            created += net.id
            assertTrue(net.isTailscale)
            assertTrue(net.hasSecret)
            assertNull(net.tailscale?.ephemeral)
            assertEquals(control, net.tailscale?.controlUrl)

            // Test (as the screen does once the list shows it): up, then while it starts.
            val seen = mutableListOf<String>()
            val st = api.test(net.id, Networks.TAILSCALE) { seen += it.state }
            assertEquals("states $seen ${st.message}", NetState.UP, Networks.state(st, Networks.TAILSCALE))
            val summary = Networks.tailnetSummary(st) { "$it devices" }
            assertTrue(summary, st.nodeAddresses.first().startsWith("100."))
            assertEquals(Networks.defaultHostname(net.id), st.nodeName)
            assertEquals(true, st.ephemeral)
            log += "test: $seen → up · $summary · node ${st.nodeName} · tailnet ${st.tailnet} · MagicDNS ${st.magicDnsSuffix}"

            // Devices → the site, added as a host by its MagicDNS name (with the SSH key to log in).
            val peers = api.peers(net.id)
            val dev = peers.first { it.name == site }
            val address = dev.deviceAddress!!
            siteAddress = address
            assertFalse(address.endsWith("."))
            assertNull(Networks.importedHost(dev, net.id, api.hosts(vault)))
            keyId = JSONObject(
                handle.apiRequest(
                    HttpMethod.POST, "/api/v1/keys/import",
                    JSONObject().put("label", "android-ts-live").put("private_key", File(env("TERMOAK_LIVE_SSH_KEY")).readText()).toString(),
                ),
            ).getString("id")
            val body = JSONObject(Networks.deviceHostBody(dev, net)!!)
            body.getJSONObject("settings").put("key_id", keyId).put("username", env("TERMOAK_LIVE_SSH_USER"))
            hostId = JSONObject(handle.apiRequest(HttpMethod.POST, "/api/v1/hosts", body.toString())).getString("id")
            // Marked as added now.
            assertEquals(hostId, Networks.importedHost(dev, net.id, api.hosts(vault))?.id)
            log += "devices: ${peers.map { "${it.name} ${it.deviceAddress} online=${it.online}" }}; host ${body.getString("label")} at $address"

            // SSH from the device through the relay. The site's sshd is new each run: forget the key
            // an earlier run trusted for its name (known hosts sync with the account).
            handle.syncNow()
            val mine = ItemFilter(accountIds = listOf(info.id), vaultIds = null, includeDevice = false)
            core.listKnownHosts(mine).filter { it.host == address }.forEach { core.deleteKnownHost(it.id, info.id) }
            val host = core.getHost(hostId, info.id)
            assertEquals(net.id, host.settings.networkId)
            val session = core.connect(hostId, TrustAll, info.id)
            val out = session.exec("echo tailnet-relay-ok; hostname", 20u)
            session.disconnect()
            assertTrue(out.stdout, out.stdout.contains("tailnet-relay-ok"))
            log += "ssh through the relay: ${out.stdout.trim().replace('\n', ' ')}"

            // The server's own test through the tailnet (with the site's key the device just trusted, synced).
            handle.syncNow()
            NetworkBook.index(core, listOf(host), emptyList())
            val test = NetworkBook.testHost(api, host, trust = true)
            assertTrue("${test.errorCode} ${test.error}", test.ok)
            assertEquals("tailscale", test.networkKind)
            log += "server test via ${test.networkName} · ${test.latencyMs} ms"

            // A host outside the tailnet: netd refuses it, the app says why.
            val outside = JSONObject(Networks.hostBody("android-ts-outside", "127.0.0.1", 22, env("TERMOAK_LIVE_SSH_USER"), net))
            outsideId = JSONObject(handle.apiRequest(HttpMethod.POST, "/api/v1/hosts", outside.toString())).getString("id")
            handle.syncNow()
            val bad = api.testHost(outsideId, trust = true)
            assertFalse(bad.ok)
            val text = Networks.hostTestError(bad)
            assertTrue("$text", text is UiText.Res && text.id == R.string.networks_ts_error_dial_acl)
            log += "outside: ${bad.errorCode}: ${bad.error}"

            // Edit: remove the key (secret null) → has_secret false; the whole network goes back.
            net = api.update(net.id, Networks.tailscaleBody(net.json, null, net.name, net.description, net.tailscale!!, SecretChange.Clear))
            assertFalse(net.hasSecret)
            assertNull(net.tailscale?.ephemeral)
            log += "key removed: has_secret=${net.hasSecret}"

            // A rejected key: error with the control server's reason.
            val badKey = api.create(
                Networks.tailscaleBody(null, vault, "$name-badkey", "", TsConfig(controlUrl = control), SecretChange.Set("hskey-auth-nope-not-a-key")),
            )
            created += badKey.id
            val bst = api.test(badKey.id, Networks.TAILSCALE)
            assertEquals("${bst.state} ${bst.message}", NetState.ERROR, Networks.state(bst, Networks.TAILSCALE))
            log += "bad key: ${bst.message} / last_error ${bst.lastError}"

            // Without a key: a login for Editors (auth_url); followed until it goes down.
            val login = api.create(Networks.tailscaleBody(null, vault, "$name-login", "", TsConfig(controlUrl = control), SecretChange.Keep))
            created += login.id
            assertFalse(login.hasSecret)
            val lst = api.test(login.id, Networks.TAILSCALE)
            assertEquals("${lst.state} ${lst.auth}", NetState.NEEDS_LOGIN, Networks.state(lst, Networks.TAILSCALE))
            assertNotNull(lst.authUrl)
            // A connection or the devices now: "log the server in first".
            val peersErr = runCatching { api.peers(login.id) }.exceptionOrNull()!!
            val pe = ApiErrors.of(peersErr)
            assertTrue("${pe?.code} ${pe?.detailsJson}", Networks.needsLogin(pe?.code, pe?.detailsJson))
            assertEquals(UiText.Res(R.string.networks_ts_error_login, listOf(login.name)), Networks.errorFor(peersErr, login, R.string.networks_peers_failed))
            api.down(login.id)
            val watched = api.watchLogin(login.id)
            assertEquals("down", watched?.state)
            log += "no key: login at ${lst.authUrl?.substringBefore("/register")}/register/…; devices: ${pe?.code}/${JSONObject(pe!!.detailsJson).optString("netd_code")}; down while followed → ${watched?.state}"
        } finally {
            hostId?.let { runCatching { handle.apiRequest(HttpMethod.DELETE, "/api/v1/hosts/$it", null) } }
            outsideId?.let { runCatching { handle.apiRequest(HttpMethod.DELETE, "/api/v1/hosts/$it", null) } }
            keyId?.let { runCatching { handle.apiRequest(HttpMethod.DELETE, "/api/v1/keys/$it", null) } }
            created.forEach { id -> runCatching { api.delete(id) } }
            // The site's key this run trusted (known hosts sync with the account).
            siteAddress?.let { a ->
                runCatching {
                    core.listKnownHosts(ItemFilter(accountIds = listOf(info.id), vaultIds = null, includeDevice = false))
                        .filter { it.host == a }.forEach { core.deleteKnownHost(it.id, info.id) }
                    handle.syncNow()
                }
            }
        }
        created.forEach { assertNull(api.status(it)) }
        log.forEach { println("live-ts: $it") }
        runCatching { core.signOutAccount(info.id, true) }
        dir.deleteRecursively()
        Unit
    }
}
