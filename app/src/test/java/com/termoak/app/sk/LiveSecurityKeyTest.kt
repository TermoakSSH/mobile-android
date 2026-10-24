package com.termoak.app.sk

import android.app.Application
import com.termoak.app.R
import com.termoak.app.UiText
import com.termoak.app.data.ApiErrors
import com.termoak.app.data.Networks
import com.termoak.ffi.AuthHandler
import com.termoak.ffi.AuthRequest
import com.termoak.ffi.HttpMethod
import com.termoak.ffi.ItemFilter
import com.termoak.ffi.SecurityKeyAlgorithm
import com.termoak.ffi.SecurityKeyOptions
import com.termoak.ffi.ServerChoice
import com.termoak.ffi.ServerEventListener
import com.termoak.ffi.SyncMode
import com.termoak.ffi.TermoakCore
import com.termoak.ffi.generateVaultKey
import com.termoak.ffi.securityKeyAuthorizedKeysLine
import com.termoak.kit.apiError
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Opt-in end-to-end check of security keys against a real server, through
 * the real engine (built for this computer) with the app's flow and its
 * YubiKit layer talking to a pretend key ([SoftFido]) as "the phone": keys
 * made on the device (Ed25519; ECDSA P-256 resident and verify-required
 * with a PIN), a connection from the phone to an OpenSSH sshd, and the
 * server's requests answered by the phone's events socket (host test,
 * server session, `/exec` of a snippet with variables); without the phone
 * (and with the app in the background) `security_key_no_device` and its
 * text. Skipped unless TERMOAK_LIVE_SK_SERVER is set, with
 * TERMOAK_LIVE_FFI_DIR, TERMOAK_LIVE_EMAIL, TERMOAK_LIVE_PASSWORD,
 * TERMOAK_LIVE_SSHD (`host:port` of an sshd both this computer and the
 * server reach, user `qa`) and TERMOAK_LIVE_AUTHORIZED (its
 * authorized_keys file, rewritten by the test).
 */
@RunWith(RobolectricTestRunner::class) // org.json is the Android one.
// API 33: the engine's objects use JNA's cleaner (API 34's SystemCleaner isn't reachable on the JVM).
@Config(sdk = [33], application = Application::class)
class LiveSecurityKeyTest {
    private fun env(k: String): String = System.getenv(k) ?: error("$k is not set")

    private object TrustAll : AuthHandler {
        override fun onHostKey(host: String, port: UInt, keyType: String, fingerprint: String) = true
        override fun onPrompt(request: AuthRequest): List<String>? = null
    }

    @Test
    fun securityKeysFromThePhone() = runBlocking {
        val server = System.getenv("TERMOAK_LIVE_SK_SERVER")
        assumeTrue("TERMOAK_LIVE_SK_SERVER not set: skipped", server != null)
        System.setProperty("jna.library.path", env("TERMOAK_LIVE_FFI_DIR"))
        ApiErrors.lookup = { it.apiError }
        val (sshHost, sshPort) = env("TERMOAK_LIVE_SSHD").split(':').let { it[0] to it[1].toInt() }
        val dir = File(System.getProperty("java.io.tmpdir"), "termoak-live-sk-${System.nanoTime()}").apply { mkdirs() }
        val core = TermoakCore(dir.path, generateVaultKey())
        // The phone: a pretend key with a PIN, plugged in; the sheet scripted.
        val device = SoftFido(pin = "246810")
        val hw = FakeHardware(device)
        val ui = ScriptUi()
        val flow = SecurityKeyFlow(hw, ui)
        core.setSecurityKeyHandler(flow)
        val info = core.signIn(ServerChoice.Custom(server!!), env("TERMOAK_LIVE_EMAIL"), env("TERMOAK_LIVE_PASSWORD"))
        val handle = core.account(info.id)
        handle.syncNow()
        val vault = info.userId!!
        val tag = (System.nanoTime() % 100000).toString()
        val log = mutableListOf<String>()
        var hostId: String? = null
        var host2Id: String? = null
        val keyIds = mutableListOf<String>()
        var snippetId: String? = null
        fun purposes() = ui.shown.map { it.first.purpose }.filterIsInstance<SkPurpose.Sign>().map { it.request.purpose }
        try {
            // Keys made on the device, saved in the account.
            val ed = core.createSecurityKey(
                "android-live-sk-$tag", SecurityKeyOptions(SecurityKeyAlgorithm.ED25519, device = "Soft FIDO2 key"), "android-live",
                SyncMode.SYNCED, info.id, vault, flow,
            )
            ui.pins += "246810"
            val ec = core.createSecurityKey(
                "android-live-sk-ec-$tag",
                SecurityKeyOptions(SecurityKeyAlgorithm.ECDSA_P256, resident = true, verifyRequired = true, user = "android"),
                "android-live", SyncMode.SYNCED, info.id, vault, flow,
            )
            keyIds += listOf(ed.id, ec.id)
            assertEquals("sk-ssh-ed25519@openssh.com", ed.algorithm)
            assertTrue(ec.securityKey!!.resident && ec.securityKey!!.verifyRequired)
            File(env("TERMOAK_LIVE_AUTHORIZED")).writeText(
                listOf(ed, ec).joinToString("") { securityKeyAuthorizedKeysLine(it.publicKey, it.securityKey) + "\n" },
            )
            log += "made ${ed.fingerprint} and ${ec.fingerprint} (${securityKeyAuthorizedKeysLine(ec.publicKey, ec.securityKey).substringBefore(' ')})"
            handle.syncNow()
            fun hostBody(label: String, keyId: String) = JSONObject().put("label", label).put("address", sshHost)
                .put("settings", JSONObject().put("port", sshPort).put("username", "qa").put("key_id", keyId)).toString()
            hostId = JSONObject(handle.apiRequest(HttpMethod.POST, "/api/v1/hosts", hostBody("android-live-sk-$tag", ed.id))).getString("id")
            host2Id = JSONObject(handle.apiRequest(HttpMethod.POST, "/api/v1/hosts", hostBody("android-live-sk-ec-$tag", ec.id))).getString("id")
            handle.syncNow()

            // From the phone: Ed25519 with a touch; ECDSA with the PIN (verify-required).
            for (id in listOf(hostId, host2Id)) {
                if (id == host2Id) ui.pins += "246810"
                val session = core.connect(id!!, TrustAll, info.id)
                val out = session.exec("echo sk-ok", 20u)
                session.disconnect()
                assertTrue(out.stdout, out.stdout.contains("sk-ok"))
            }
            assertTrue(ui.pinAsks.isNotEmpty())
            log += "ssh from the phone: ok (both keys); purposes ${purposes().distinct()}"

            // The server without a device that can sign: at once, in words.
            val noDevice = Networks.hostTest(handle.apiRequest(HttpMethod.POST, "/api/v1/hosts/$hostId/test?trust=true", "{}"))!!
            assertEquals("security_key_no_device", noDevice.errorCode)
            assertEquals(UiText.Res(R.string.sk_error_no_device), Networks.hostTestError(noDevice))

            // The phone's events socket answers (the engine signs through the flow; the sheet sees the requests).
            val sub = handle.subscribeEvents(object : ServerEventListener {
                override fun onEvent(eventJson: String) {
                    val e = JSONObject(eventJson)
                    when (e.optString("type")) {
                        "security_key_request" -> flow.onServerRequest(e.optString("request_id"), e.optString("message"))
                        "security_key_done" -> flow.onServerDone(e.optString("request_id"))
                    }
                }

                override fun onClosed(reason: String?) {}
            })
            Thread.sleep(500)
            val test = Networks.hostTest(handle.apiRequest(HttpMethod.POST, "/api/v1/hosts/$hostId/test?trust=true", "{}"))!!
            assertTrue("${test.errorCode} ${test.error}", test.ok)
            assertEquals("test", purposes().last())
            log += "server test signed by the phone · ${test.latencyMs} ms"

            // A server session, held by the server, signed by the phone.
            val s = JSONObject(handle.apiRequest(HttpMethod.POST, "/api/v1/sessions", JSONObject().put("host_id", hostId).put("cols", 80).put("rows", 24).toString()))
            val sid = s.getString("id")
            var state = ""
            repeat(80) {
                if (state == "running" || state == "closed") return@repeat
                val v = JSONObject(handle.apiRequest(HttpMethod.GET, "/api/v1/sessions/$sid", null))
                state = v.optJSONObject("state")?.optString("state").orEmpty()
                Thread.sleep(250)
            }
            runCatching { handle.apiRequest(HttpMethod.DELETE, "/api/v1/sessions/$sid", null) }
            assertEquals("running", state)
            val sessionReq = ui.shown.map { it.first.purpose }.filterIsInstance<SkPurpose.Sign>().last().request
            assertEquals("server_session", sessionReq.purpose)
            assertEquals(sid, sessionReq.sessionId)
            log += "server session $sid running, signed by the phone"

            // "Run on" from the server: /exec of a snippet with variables, its pool connection signed by the phone.
            snippetId = JSONObject(
                handle.apiRequest(HttpMethod.POST, "/api/v1/snippets", JSONObject().put("name", "android-live-$tag").put("script", "echo {{label}}:{{prompt:Msg}}:{{select:Env|dev|prod}}").toString()),
            ).getString("id")
            val exec = JSONArray(
                handle.apiRequest(
                    HttpMethod.POST, "/api/v1/exec",
                    JSONObject().put("host_ids", JSONArray().put(hostId)).put("snippet_id", snippetId).put("variables", JSONObject().put("Msg", "hi")).toString(),
                ),
            )
            val stdout = exec.getJSONObject(0).toString()
            assertTrue(stdout, stdout.contains("android-live-sk-$tag:hi:dev"))
            assertEquals("server", purposes().last())
            log += "/exec rendered per host and signed by the phone: ${exec.getJSONObject(0).optString("stdout").trim()}"

            // In the background the phone answers no_device at once: the server says so.
            hw.foreground = false
            val shownBefore = ui.shown.size
            val bg = Networks.hostTest(handle.apiRequest(HttpMethod.POST, "/api/v1/hosts/$hostId/test?trust=true", "{}"))!!
            assertEquals("security_key_no_device", bg.errorCode)
            assertEquals(shownBefore, ui.shown.size)
            hw.foreground = true
            log += "in the background: ${bg.errorCode}"
            sub.unsubscribe()
        } finally {
            snippetId?.let { runCatching { handle.apiRequest(HttpMethod.DELETE, "/api/v1/snippets/$it", null) } }
            listOfNotNull(hostId, host2Id).forEach { runCatching { handle.apiRequest(HttpMethod.DELETE, "/api/v1/hosts/$it", null) } }
            keyIds.forEach { runCatching { handle.apiRequest(HttpMethod.DELETE, "/api/v1/keys/$it", null) } }
            runCatching {
                handle.syncNow()
                core.listKnownHosts(ItemFilter(accountIds = listOf(info.id), vaultIds = null, includeDevice = true))
                    .filter { it.host == sshHost && it.port.toInt() == sshPort }.forEach { core.deleteKnownHost(it.id, it.accountId) }
                handle.syncNow()
            }
            runCatching { File(env("TERMOAK_LIVE_AUTHORIZED")).writeText("") }
        }
        log.forEach { println("live-sk: $it") }
        runCatching { core.signOutAccount(info.id, true) }
        dir.deleteRecursively()
        Unit
    }
}
