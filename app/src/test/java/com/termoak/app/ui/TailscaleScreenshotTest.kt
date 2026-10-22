package com.termoak.app.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import com.termoak.app.R
import com.termoak.app.data.HostRef
import com.termoak.app.data.NetStatus
import com.termoak.app.data.NetworkBook
import com.termoak.app.data.Networks
import com.termoak.ffi.VaultInfo
import com.termoak.ffi.VaultKind
import com.termoak.ffi.VaultRole
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
 * Tailscale/Headscale networks drawn on the JVM (Robolectric): the list's
 * states, a tailnet's page (connected, login, approval, error), its devices
 * with the added ones marked, and the form (key, Headscale, Advanced). The
 * PNGs are left in app/build/screenshots/networks-ts/.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// The plain Application: TermoakApp loads the engine (a native library for Android only).
@Config(application = Application::class, sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi")
class TailscaleScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun string(id: Int, vararg args: Any) = rule.activity.getString(id, *args)

    private fun save(name: String) {
        rule.waitForIdle()
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        File("build/screenshots/networks-ts/$name.png").apply { parentFile!!.mkdirs() }
            .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun tailnet(id: String, name: String, key: Boolean = true, control: String? = null, ephemeral: Boolean? = null) = Networks.network(
        JSONObject(
            """{"id":"$id","name":"$name","kind":"tailscale","vault_id":"v1","has_secret":$key,"description":"",
                "tailscale":{"control_url":${control?.let { "\"$it\"" } ?: "null"},"hostname":null,"tags":[],"ephemeral":${ephemeral ?: "null"}}}""",
        ),
    )

    private val up = NetStatus(
        "up", nodeAddresses = listOf("100.64.0.3", "fd7a:115c:a1e0::3"), controlUrl = "https://hs.example.com", tailnet = "qa",
        magicDnsSuffix = "ts.qa.test", peerCount = 4, ephemeral = true, nodeName = "termoak-5678abcd",
        nodeDnsName = "termoak-5678abcd.ts.qa.test.", nodeId = "nodekey:8f3a", rxBytes = 182_000, txBytes = 41_000, streams = 1,
    )

    private val vaults = listOf(VaultInfo("v1", "a1", "", "", VaultKind.PERSONAL, VaultRole.MANAGER, null, null, null, null, null, 1, 0, false, null))

    @After
    fun clean() = NetworkBook.reset()

    @Test
    fun list() {
        NetworkBook.setStatus("t1", up)
        NetworkBook.setStatus("t2", NetStatus("needs_auth", auth = "login"))
        NetworkBook.setStatus("t3", NetStatus("needs_auth", auth = "approval", nodeName = "termoak-11112222"))
        NetworkBook.setStatus("t4", NetStatus("error", message = "the control server refused the auth key: invalid key"))
        NetworkBook.setStatus("t5", NetStatus("down"))
        val nets = listOf(
            tailnet("t1", "Office tailnet", control = "https://hs.example.com"), tailnet("t2", "Home", key = false), tailnet("t3", "Lab"),
            tailnet("t4", "Old key"), tailnet("t5", "Datacenter"),
        )
        rule.setContent {
            MaterialTheme {
                Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                    UsageCard(5, 10)
                    nets.forEach { NetworkRow(it, "Personal", busy = false) {} }
                    // A server that can't join Tailscale networks.
                    NetworkRow(tailnet("t6", "Elsewhere"), "Personal", busy = false, available = false) {}
                }
            }
        }
        rule.onNodeWithText("100.64.0.3 · 4 devices", substring = true).assertExists()
        rule.onNodeWithText(string(R.string.networks_state_login_short)).assertExists()
        rule.onNodeWithText(string(R.string.networks_state_approval_short)).assertExists()
        rule.onNodeWithText(string(R.string.networks_state_cannot_join)).assertExists()
        rule.onNodeWithText(string(R.string.networks_ts_control_line, "hs.example.com"), substring = true).assertExists()
        save("list")
    }

    @Test
    fun connected() {
        NetworkBook.setStatus("t1", up)
        rule.setContent {
            MaterialTheme {
                Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                    NetworkDetail(
                        tailnet("t1", "Office tailnet", control = "https://hs.example.com"), "Personal", editor = true, busy = false, users = 3,
                        onTest = {}, onDown = {}, onEdit = {}, onDelete = {}, onKey = {}, onGenerate = {}, onPeers = {}, onCopied = {},
                    )
                }
            }
        }
        rule.onNodeWithTag("tailnet-summary").assertExists()
        rule.onNodeWithText(string(R.string.networks_ts_connected, "100.64.0.3 · 4 devices")).assertExists()
        rule.onNodeWithText("nodekey:8f3a").assertExists()
        rule.onNodeWithText(string(R.string.networks_ts_action_devices)).assertExists()
        // No WireGuard key or handshake for a tailnet.
        rule.onNodeWithTag("network-generate").assertDoesNotExist()
        save("detail-connected")
    }

    @Test
    fun loginForEditorsAndUseOnly() {
        NetworkBook.setStatus("t2", NetStatus("needs_auth", auth = "login", authUrl = "https://hs.example.com/register/abc"))
        var opened: String? = null
        var editor by androidx.compose.runtime.mutableStateOf(true)
        rule.setContent {
            MaterialTheme {
                Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                    NetworkDetail(
                        tailnet("t2", "Home", key = false), "Personal", editor = editor, busy = false, users = 0,
                        onTest = {}, onDown = {}, onEdit = {}, onDelete = {}, onKey = {}, onGenerate = {}, onPeers = {}, onCopied = {},
                        watching = true, onLogin = { opened = it },
                    )
                }
            }
        }
        rule.onNodeWithText(string(R.string.networks_ts_login_waiting)).assertExists()
        save("detail-login")
        rule.onNodeWithTag("tailnet-login-open").performClick()
        assertEquals("https://hs.example.com/register/abc", opened)
        // Use-only: no login page (the server only sends it to Editors).
        editor = false
        NetworkBook.setStatus("t2", NetStatus("needs_auth", auth = "login"))
        rule.waitForIdle()
        rule.onNodeWithTag("tailnet-login-open").assertDoesNotExist()
        rule.onNodeWithText(string(R.string.networks_ts_login_use_only)).assertExists()
        save("detail-login-use-only")
    }

    @Test
    fun approvalAndError() {
        NetworkBook.setStatus("t3", NetStatus("needs_auth", auth = "approval", nodeName = "termoak-11112222"))
        NetworkBook.setStatus("t4", NetStatus("error", message = "the control server refused the auth key: invalid key", lastError = "401 Unauthorized"))
        var other = 0
        var shown by androidx.compose.runtime.mutableStateOf(tailnet("t3", "Lab"))
        rule.setContent {
            MaterialTheme {
                Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                    NetworkDetail(
                        shown, "Personal", editor = true, busy = false, users = 0,
                        onTest = {}, onDown = {}, onEdit = {}, onDelete = {}, onKey = {}, onGenerate = {}, onPeers = {}, onCopied = {},
                        onOtherKey = { other++ },
                    )
                }
            }
        }
        rule.onNodeWithText(string(R.string.networks_ts_approval_text, "termoak-11112222")).assertExists()
        save("detail-approval")
        shown = tailnet("t4", "Old key")
        rule.waitForIdle()
        rule.onNodeWithText("The control server refused the auth key: invalid key").assertExists()
        rule.onNodeWithText(string(R.string.networks_ts_error_details, "401 Unauthorized")).assertExists()
        save("detail-error")
        rule.onNodeWithTag("tailnet-other-key").performScrollTo().performClick()
        assertEquals(1, other)
    }

    @Test
    fun devices() {
        val peers = Networks.peers(
            """[{"name":"nas","dns_name":"nas.ts.qa.test.","addresses":["100.64.0.5"],"os":"linux","online":true,"tags":["tag:srv"],"routes":["192.168.10.0/24","0.0.0.0/0"]},
                {"name":"ssh-site","dns_name":"ssh-site.ts.qa.test.","addresses":["100.64.0.6"],"os":"linux","online":true},
                {"name":"laptop","dns_name":"laptop.ts.qa.test.","addresses":["100.64.0.7"],"os":"macOS","online":false,"last_seen":1700000000000},
                {"name":"phone","dns_name":"phone.ts.qa.test.","addresses":["100.64.0.8"],"os":"iOS","online":true}]""",
        )
        val n = tailnet("t1", "Office tailnet")
        val hosts = listOf(HostRef("h1", "ssh-site", "ssh-site.ts.qa.test", "t1"))
        val added = mutableListOf<String>()
        var all: List<String>? = null
        rule.setContent {
            MaterialTheme {
                Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
                    TailnetDevices(peers, hosts, n, editor = true, adding = false, onAdd = { added += it.deviceAddress!! }, onAddIn = { added += it }, onAddAll = { all = it.map { p -> p.name!! } })
                }
            }
        }
        rule.onNodeWithTag("device-imported").assertExists()
        rule.onNodeWithText(string(R.string.networks_ts_peers_imported, "ssh-site"), substring = true).assertExists()
        // An exit node's /0 route is no subnet to add hosts in.
        rule.onNodeWithTag("device-add-in-0.0.0.0/0").assertDoesNotExist()
        save("devices")
        rule.onNodeWithTag("device-add-nas.ts.qa.test").performClick()
        rule.onNodeWithTag("device-add-in-192.168.10.0/24").performClick()
        assertEquals(listOf("nas.ts.qa.test", "192.168.10.0/24"), added)
        rule.onNodeWithTag("tailnet-add-all").performClick()
        assertEquals(listOf("nas", "laptop", "phone"), all)
    }

    @Test
    fun formForANewTailnet() {
        var plan: NetworkPlan? = null
        rule.setContent {
            MaterialTheme {
                NetworkFormPage(null, vaults, onDismiss = {}, onSave = { plan = it; null }, kinds = listOf("tailscale", "wireguard"))
            }
        }
        rule.onNodeWithTag("network-kind-tailscale").performClick()
        rule.onNodeWithTag("network-name").performTextInput("Office tailnet")
        rule.onNodeWithTag("ts-control-headscale").performClick()
        rule.onNodeWithTag("ts-control-url").performTextInput("https://hs.example.com/")
        rule.onNodeWithTag("ts-advanced").performScrollTo().performClick()
        // Without a key the node is not ephemeral by default; with one it is (the switch follows until touched).
        rule.onNodeWithText(string(R.string.networks_ts_form_ephemeral_default_off)).assertExists()
        rule.onNodeWithTag("ts-auth-key").performScrollTo().performTextInput("hskey-auth-abc123")
        rule.onNodeWithText(string(R.string.networks_ts_form_ephemeral_default_on)).assertExists()
        save("form-new")
        rule.onNodeWithTag("network-save").performClick()
        rule.waitForIdle()
        val p = plan!!
        assertTrue(p.test)
        val body = JSONObject(p.body)
        assertEquals("tailscale", body.getString("kind"))
        assertEquals("v1", body.getString("vault_id"))
        assertEquals("hskey-auth-abc123", body.getJSONObject("secret").getString("auth_key"))
        val ts = body.getJSONObject("tailscale")
        assertEquals("https://hs.example.com", ts.getString("control_url"))
        // Never touched: null (the server's default).
        assertTrue(ts.isNull("ephemeral"))
    }

    @Test
    fun formChecksTheRulesAndTheServerOffersTailscale() {
        var plan: NetworkPlan? = null
        var kinds by androidx.compose.runtime.mutableStateOf(listOf("wireguard"))
        rule.setContent { MaterialTheme { NetworkFormPage(null, vaults, onDismiss = {}, onSave = { plan = it; null }, kinds = kinds) } }
        // A server without Tailscale: coming soon.
        rule.onNodeWithText(string(R.string.networks_form_kind_soon, "Tailscale")).assertExists()
        kinds = listOf("tailscale", "wireguard")
        rule.waitForIdle()
        rule.onNodeWithText(string(R.string.networks_ts_kind_option)).performClick()
        rule.onNodeWithTag("network-name").performTextInput("Lab")
        rule.onNodeWithTag("ts-advanced").performScrollTo().performClick()
        rule.onNodeWithTag("ts-tags").performScrollTo().performTextInput("termoak")
        rule.onNodeWithTag("network-save").performClick()
        rule.onNodeWithText(string(R.string.networks_ts_error_tags)).assertExists()
        assertEquals(null, plan)
    }

    @Test
    fun editingKeepsReplacesOrRemovesTheKey() {
        val n = tailnet("0b9f3c2e-1111-4a5b-8c7d-12345678abcd", "Office tailnet", ephemeral = false)
        var plan: NetworkPlan? = null
        rule.setContent { MaterialTheme { NetworkFormPage(n, emptyList(), onDismiss = {}, onSave = { plan = it; null }, kinds = listOf("tailscale")) } }
        // Kept by default: no secret; the stored ephemeral goes back as it was.
        rule.onNodeWithTag("network-save").performClick()
        rule.waitForIdle()
        var body = JSONObject(plan!!.body)
        assertFalse(body.has("secret"))
        assertFalse(plan!!.test)
        assertFalse(body.getJSONObject("tailscale").getBoolean("ephemeral"))
        // The default hostname from the id.
        rule.onNodeWithText(string(R.string.networks_ts_form_hostname_hint, "termoak-5678abcd")).assertExists()
        save("form-edit")
        // Remove it: secret null.
        rule.onNodeWithTag("ts-key-remove").performClick()
        rule.onNodeWithTag("network-save").performClick()
        rule.waitForIdle()
        body = JSONObject(plan!!.body)
        assertTrue(body.has("secret") && body.isNull("secret"))
        // A new key: it has to be there, then it is tested once saved.
        rule.onNodeWithTag("ts-key-new").performClick()
        plan = null
        rule.onNodeWithTag("network-save").performClick()
        rule.onNodeWithText(string(R.string.networks_ts_error_key_missing)).assertExists()
        assertEquals(null, plan)
        rule.onNodeWithTag("ts-auth-key").performTextInput("tskey-auth-new")
        rule.onNodeWithTag("network-save").performClick()
        rule.waitForIdle()
        assertTrue(plan!!.test)
        assertEquals("tskey-auth-new", JSONObject(plan!!.body).getJSONObject("secret").getString("auth_key"))
    }

    @Test
    @Config(qualifiers = "es-w411dp-h891dp-port-xxhdpi")
    fun spanish() {
        NetworkBook.setStatus("t1", up)
        rule.setContent {
            MaterialTheme {
                Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                    NetworkDetail(
                        tailnet("t1", "Oficina", control = "https://hs.example.com"), "Personal", editor = true, busy = false, users = 1,
                        onTest = {}, onDown = {}, onEdit = {}, onDelete = {}, onKey = {}, onGenerate = {}, onPeers = {}, onCopied = {},
                    )
                }
            }
        }
        rule.onNodeWithText("Conectada · 100.64.0.3 · 4 dispositivos").assertExists()
        save("detail-connected-es")
    }
}
