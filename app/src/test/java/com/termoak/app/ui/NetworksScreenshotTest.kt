package com.termoak.app.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.termoak.app.R
import com.termoak.app.data.NetStatus
import com.termoak.app.data.NetworkBook
import com.termoak.app.data.Networks
import com.termoak.app.data.WgPeer
import com.termoak.app.data.NetworkItem
import com.termoak.ffi.VaultInfo
import com.termoak.ffi.VaultKind
import com.termoak.ffi.VaultRole
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * The private networks screens drawn on the JVM (Robolectric): the list with
 * the plan's usage and the states, a network's page, the server's key and
 * the form reading a .conf. The PNGs are left in app/build/screenshots/networks/.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// The plain Application: TermoakApp loads the engine (a native library for Android only).
@Config(application = Application::class, sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi")
class NetworksScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val pub = "xTIBA5rboUvnH4htodjb6e697QjLERt1NAB4mZqp8Dg="
    private val priv = "yAnz5TF+lXXJte14tji3zlMNq+hd2rYUIgJBgB3fBmk="

    private fun string(id: Int, vararg args: Any) = rule.activity.getString(id, *args)

    private fun save(name: String) {
        rule.waitForIdle()
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        File("build/screenshots/networks/$name.png").apply { parentFile!!.mkdirs() }
            .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun net(id: String, name: String, secret: Boolean = true, key: String? = pub) = Networks.network(
        JSONObject(
            """{"id":"$id","name":"$name","kind":"wireguard","vault_id":"v1","has_secret":$secret,
                "wireguard":{"addresses":["10.9.0.2/32"],"dns":["10.9.0.1"],"public_key":${key?.let { "\"$it\"" } ?: "null"},
                "peers":[{"public_key":"$pub","endpoint":"vpn.example.com:51820","allowed_ips":["10.9.0.0/24"],"persistent_keepalive":25}]}}""",
        ),
    )

    @After
    fun clean() = NetworkBook.reset()

    @Test
    fun list() {
        val now = System.currentTimeMillis()
        NetworkBook.setStatus("n1", NetStatus("up", lastHandshakeAt = now - 60_000, rxBytes = 1_234_567, txBytes = 45_000))
        NetworkBook.setStatus("n2", NetStatus("up"))
        NetworkBook.setStatus("n3", NetStatus("down"))
        NetworkBook.setStatus("n4", null)
        val nets = listOf(net("n1", "Office"), net("n2", "Home lab"), net("n3", "Datacenter"), net("n4", "Old site", secret = false, key = null))
        rule.setContent {
            MaterialTheme {
                Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                    UsageCard(4, 10)
                    nets.forEach { NetworkRow(it, "Personal", busy = false) {} }
                }
            }
        }
        rule.onNodeWithText(string(R.string.networks_usage, 4, 10)).assertExists()
        rule.onNodeWithText(string(R.string.networks_state_up)).assertExists()
        // Up without a handshake: waiting for the peer, not "connected".
        rule.onNodeWithText(string(R.string.networks_state_waiting_short)).assertExists()
        rule.onNodeWithText(string(R.string.networks_state_down)).assertExists()
        rule.onNodeWithText(string(R.string.networks_state_unknown)).assertExists()
        save("list")
    }

    @Test
    fun detailAndKey() {
        NetworkBook.setStatus("n1", NetStatus("up", lastHandshakeAt = System.currentTimeMillis() - 30_000, rxBytes = 52_000, txBytes = 9_000, nodeAddresses = listOf("10.9.0.2")))
        val n = net("n1", "Office")
        var tested = 0
        rule.setContent {
            MaterialTheme {
                Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                    NetworkDetail(n, "Personal", editor = true, busy = false, users = 2, onTest = { tested++ }, onDown = {}, onEdit = {}, onDelete = {}, onKey = {}, onGenerate = {}, onPeers = {}, onCopied = {})
                }
            }
        }
        rule.onNodeWithTag("network-public-key").assertExists()
        rule.onNodeWithText(rule.activity.resources.getQuantityString(R.plurals.networks_detail_hosts, 2, 2)).assertExists()
        save("detail")
        rule.onNodeWithTag("network-test").performClick()
        assertEquals(1, tested)
    }

    @Test
    fun useOnlySeesNoActions() {
        rule.setContent { MaterialTheme { NetworkDetail(net("n1", "Office"), "Team", editor = false, busy = false, users = 0, onTest = {}, onDown = {}, onEdit = {}, onDelete = {}, onKey = {}, onGenerate = {}, onPeers = {}, onCopied = {}) } }
        rule.onNodeWithText(string(R.string.networks_detail_read_only)).assertExists()
        rule.onNodeWithTag("network-test").assertDoesNotExist()
        rule.onNodeWithTag("network-generate").assertDoesNotExist()
    }

    @Test
    fun serverKey() {
        rule.setContent {
            MaterialTheme {
                Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                    ServerKeyContent(net("n1", "Office"), generated = true, canTest = true, onCopied = {}, onTest = {}, onDismiss = {})
                }
            }
        }
        rule.onNodeWithText(string(R.string.networks_key_generated_title)).assertExists()
        rule.onNodeWithText("[Peer]\n# Termoak: Office\nPublicKey = $pub\nAllowedIPs = 10.9.0.2/32").assertExists()
        save("server-key")
    }

    @Test
    fun formReadsAConf() {
        val vaults = listOf(VaultInfo("v1", "a1", "", "", VaultKind.PERSONAL, VaultRole.MANAGER, null, null, null, null, null, 1, 0, false, null, null))
        var plan: NetworkPlan? = null
        val conf = "[Interface]\nPrivateKey = $priv\nAddress = 10.9.0.2/32\nListenPort = 51820\n\n[Peer]\nPublicKey = $pub\n" +
            "Endpoint = vpn.example.com:51820\nAllowedIPs = 10.9.0.0/24\nPersistentKeepalive = 25\n"
        rule.setContent {
            MaterialTheme {
                NetworkFormPage(null, vaults, onDismiss = {}, onSave = { plan = it; null }, initialConf = conf)
            }
        }
        rule.onNodeWithTag("network-conf-ok").assertIsDisplayed()
        rule.onNodeWithText(string(R.string.networks_form_key_from_conf)).assertExists()
        rule.onNodeWithText(string(R.string.networks_form_ignored, "ListenPort")).assertExists()
        rule.onNodeWithText(string(R.string.networks_form_kind_soon, "Tailscale")).assertExists()
        save("form")
        // No name yet: it says so and sends nothing.
        rule.onNodeWithTag("network-save").performClick()
        rule.onNodeWithText(string(R.string.networks_form_error_name)).assertExists()
        assertEquals(null, plan)
    }

    @Test
    fun formSendsTheKeyOrAsksTheServer() = runBlocking {
        val vaults = listOf(VaultInfo("v1", "a1", "", "", VaultKind.PERSONAL, VaultRole.MANAGER, null, null, null, null, null, 1, 0, false, null, null))
        var plan: NetworkPlan? = null
        val noKey = "[Interface]\nAddress = 10.9.0.2/32\n[Peer]\nPublicKey = $pub\nEndpoint = vpn:51820\nAllowedIPs = 10.9.0.0/24\n"
        rule.setContent { MaterialTheme { NetworkFormPage(null, vaults, onDismiss = {}, onSave = { plan = it; null }, initialConf = noKey) } }
        rule.onNodeWithText(string(R.string.networks_form_key_generated)).assertExists()
        rule.onNodeWithTag("network-name").performTextInput("Office")
        rule.onNodeWithTag("network-save").performClick()
        rule.waitForIdle()
        val p = plan!!
        assertTrue(p.generate)
        assertFalse(p.replace)
        val body = JSONObject(p.body)
        assertEquals("Office", body.getString("name"))
        assertEquals("v1", body.getString("vault_id"))
        assertFalse(body.has("secret"))
    }
}
