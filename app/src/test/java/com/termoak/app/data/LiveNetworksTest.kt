package com.termoak.app.data

import android.app.Application
import com.termoak.app.R
import com.termoak.app.UiText
import com.termoak.app.toUiText
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
 * Opt-in end-to-end check against a real server with private networks and a
 * real WireGuard site, through the real engine (built for this computer):
 * the networks screens' REST calls, the engine's error details, the sync of
 * the network and the host, and SSH from "the phone" through the server
 * relay. Skipped unless TERMOAK_LIVE_SERVER is set, with:
 * TERMOAK_LIVE_FFI_DIR (the folder of libtermoak_ffi.so), TERMOAK_LIVE_EMAIL,
 * TERMOAK_LIVE_PASSWORD, TERMOAK_LIVE_SITE_KEY / TERMOAK_LIVE_SITE_ENDPOINT
 * (the site's WireGuard public key and host:port as the server sees it),
 * TERMOAK_LIVE_SITE_FIFO (termoak-wgpeer's stdin: `peer <key> <ip>`),
 * TERMOAK_LIVE_ADDRESS (this network's address in the tunnel),
 * TERMOAK_LIVE_HOST (the SSH host inside), TERMOAK_LIVE_SSH_USER and
 * TERMOAK_LIVE_SSH_KEY (a private key file).
 */
@RunWith(RobolectricTestRunner::class) // org.json is the Android one.
// API 33: the engine's objects use JNA's cleaner (API 34's SystemCleaner isn't reachable on the JVM).
@Config(sdk = [33], application = Application::class)
class LiveNetworksTest {
    private fun env(k: String): String = System.getenv(k) ?: error("$k is not set")

    private object TrustAll : AuthHandler {
        override fun onHostKey(host: String, port: UInt, keyType: String, fingerprint: String) = true
        override fun onPrompt(request: AuthRequest): List<String>? = null
    }

    @Test
    fun liveRelayThroughAWireguardNetwork() = runBlocking {
        val server = System.getenv("TERMOAK_LIVE_SERVER")
        assumeTrue("TERMOAK_LIVE_SERVER not set: skipped", server != null)
        System.setProperty("jna.library.path", env("TERMOAK_LIVE_FFI_DIR"))
        // The real engine's details (other tests give a stand-in).
        ApiErrors.lookup = { it.apiError }
        val dir = File(System.getProperty("java.io.tmpdir"), "termoak-live-${System.nanoTime()}").apply { mkdirs() }
        val core = TermoakCore(dir.path, generateVaultKey())
        val info = core.signIn(ServerChoice.Custom(server!!), env("TERMOAK_LIVE_EMAIL"), env("TERMOAK_LIVE_PASSWORD"))
        val handle = core.account(info.id)
        handle.syncNow()
        val api = NetworksApi(handle)
        assertTrue("the server has no networks", api.supported())
        val vault = info.userId!!

        // The network as the form sends it: a .conf without PrivateKey (the server generates it).
        val address = env("TERMOAK_LIVE_ADDRESS")
        val conf = (Networks.check(
            "[Interface]\nAddress = $address\n\n[Peer]\nPublicKey = ${env("TERMOAK_LIVE_SITE_KEY")}\n" +
                "Endpoint = ${env("TERMOAK_LIVE_SITE_ENDPOINT")}\nAllowedIPs = 10.9.0.0/24\nPersistentKeepalive = 25\n",
        ) as ConfRead.Ok).conf
        assertNull(conf.privateKey)
        val name = "android-live-${System.nanoTime() % 100000}"
        val net = api.create(Networks.body(null, vault, name, "android live test", conf, null))
        var hostId: String? = null
        var keyId: String? = null
        try {
            val publicKey = api.generateKey(net.id, replace = false)
            assertTrue(publicKey, Networks.validKey(publicKey))
            // A second time needs replace: the engine's details give key_exists and its text.
            val again = runCatching { api.generateKey(net.id, replace = false) }.exceptionOrNull()!!
            assertEquals("key_exists", ApiErrors.of(again)?.code)
            assertEquals(UiText.Res(R.string.error_key_exists), again.toUiText(R.string.networks_action_failed))
            File(env("TERMOAK_LIVE_SITE_FIFO")).appendText("peer $publicKey $address\n")

            // Test = up + waiting for the handshake.
            val seen = mutableListOf<String>()
            val st = api.test(net.id, Networks.WIREGUARD) { seen += it.state }
            assertEquals("states $seen, ${st.message}", NetState.UP, Networks.state(st, Networks.WIREGUARD))
            assertNotNull(st.lastHandshakeAt)

            // A host in the network with the site's SSH key (as "Add host at" does, plus the key).
            val key = JSONObject(
                handle.apiRequest(
                    HttpMethod.POST, "/api/v1/keys/import",
                    JSONObject().put("label", "android-live").put("private_key", File(env("TERMOAK_LIVE_SSH_KEY")).readText()).toString(),
                ),
            )
            keyId = key.getString("id")
            val hostBody = JSONObject(Networks.hostBody("android-live-host", env("TERMOAK_LIVE_HOST"), 22, env("TERMOAK_LIVE_SSH_USER"), net))
            hostBody.getJSONObject("settings").put("key_id", keyId)
            hostId = JSONObject(handle.apiRequest(HttpMethod.POST, "/api/v1/hosts", hostBody.toString())).getString("id")

            // The sync brings both, with the server's public key and the host's network.
            handle.syncNow()
            val local = core.listNetworks(ItemFilter(accountIds = listOf(info.id), vaultIds = null, includeDevice = false)).first { it.id == net.id }
            assertEquals(publicKey, local.wireguard?.publicKey)
            val host = core.getHost(hostId, info.id)
            assertEquals(net.id, host.settings.networkId)
            NetworkBook.index(core, listOf(host), emptyList())
            assertEquals(net.id, NetworkBook.networkOf(host))
            assertEquals(net.id to name, NetworkBook.resolve(core, hostId, info.id))

            // SSH from the device through the server relay (connect_item picks it).
            val session = core.connect(hostId, TrustAll, info.id)
            val out = session.exec("echo relay-ok", 20u)
            session.disconnect()
            assertTrue(out.stdout, out.stdout.contains("relay-ok"))

            // The server's own test through the network ("via … · N ms"), trusting the site's key.
            val test = NetworkBook.testHost(api, host, trust = true)
            assertTrue("${test.errorCode} ${test.error}", test.ok)
            assertEquals(net.id, test.networkId)
            assertEquals(name, test.networkName)
            // Its devices: the site at an address to add hosts at.
            val peers = api.peers(net.id)
            assertTrue(peers.flatMap { Networks.hostAddresses(it) }.any { it.address.startsWith("10.9.0.") })
            println(
                "live: state ${Networks.state(st, Networks.WIREGUARD)} after $seen; relay echo ok; " +
                    "server test via ${test.networkName} · ${test.latencyMs} ms; usage ${api.usage()}; peers ${peers.map { it.title }}",
            )
        } finally {
            hostId?.let { runCatching { handle.apiRequest(HttpMethod.DELETE, "/api/v1/hosts/$it", null) } }
            keyId?.let { runCatching { handle.apiRequest(HttpMethod.DELETE, "/api/v1/keys/$it", null) } }
            runCatching { api.delete(net.id) }
        }
        // Deleted: the server forgets it (its state can't be read: "unknown").
        assertNull(api.status(net.id))
        runCatching { core.signOutAccount(info.id, true) }
        dir.deleteRecursively()
        Unit
    }
}
