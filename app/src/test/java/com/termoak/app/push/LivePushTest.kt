package com.termoak.app.push

import android.app.Application
import android.util.Base64
import com.termoak.ffi.PushCategory
import com.termoak.ffi.ServerChoice
import com.termoak.ffi.TermoakCore
import com.termoak.ffi.generateVaultKey
import com.termoak.ffi.parsePushPayload
import com.termoak.ffi.webpushDecrypt
import com.termoak.ffi.webpushGenerateKeys
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.net.HttpURLConnection
import java.net.URI

/**
 * Opt-in end-to-end check of push without Google against a real server and
 * an ntfy server, through the real engine (built for this computer) and the
 * app's own mapping: this "phone" registers a UnifiedPush-style endpoint (an
 * ntfy `up…` topic) with Web Push keys, asks for a test, reads the message
 * from ntfy as a distributor would, decrypts it with its keys and turns it
 * into the notification the app shows. Preferences go to the server and
 * back; at the end the registration is removed and the device signed out.
 *
 * Skipped unless TERMOAK_LIVE_PUSH_SERVER is set, with TERMOAK_LIVE_FFI_DIR
 * (the folder of libtermoak_ffi.so), TERMOAK_LIVE_EMAIL,
 * TERMOAK_LIVE_PASSWORD and TERMOAK_LIVE_NTFY (an ntfy the server may call,
 * e.g. http://127.0.0.1:2586 on staging's machine).
 */
@RunWith(RobolectricTestRunner::class) // org.json and Base64 are Android's.
@Config(sdk = [33], application = Application::class)
class LivePushTest {
    private fun env(k: String): String = System.getenv(k) ?: error("$k is not set")

    private fun get(url: String): String {
        val c = URI(url).toURL().openConnection() as HttpURLConnection
        c.connectTimeout = 5_000
        c.readTimeout = 10_000
        return c.inputStream.bufferedReader().use { it.readText() }
    }

    @Test
    fun webPushThroughNtfy() = runBlocking {
        val server = System.getenv("TERMOAK_LIVE_PUSH_SERVER")
        assumeTrue("TERMOAK_LIVE_PUSH_SERVER not set: skipped", server != null)
        System.setProperty("jna.library.path", env("TERMOAK_LIVE_FFI_DIR"))
        val ntfy = env("TERMOAK_LIVE_NTFY").trimEnd('/')
        val dir = File(System.getProperty("java.io.tmpdir"), "termoak-live-push-${System.nanoTime()}").apply { mkdirs() }
        val core = TermoakCore(dir.path, generateVaultKey())
        val info = core.signIn(ServerChoice.Custom(server!!), env("TERMOAK_LIVE_EMAIL"), env("TERMOAK_LIVE_PASSWORD"))
        val handle = core.account(info.id)
        val log = mutableListOf<String>()
        try {
            val before = handle.pushStatus()
            assertTrue("the server sends Web Push", before.serverWebpush)
            assertNotNull("a VAPID key the connector accepts", PushSetup.vapid(before.vapidPublicKey))
            assertTrue(before.kinds.contains("security_key_request"))

            // The "distributor": an ntfy UnifiedPush topic; keys as the connector makes them.
            val keys = webpushGenerateKeys()
            val topic = "upAndroidLive${System.nanoTime()}"
            val endpoint = "$ntfy/$topic?up=1"
            assertTrue(handle.registerWebPush(endpoint, keys.p256dh, keys.auth))
            val st = handle.pushStatus()
            assertTrue(st.registered)
            assertEquals("webpush", st.platform)
            assertEquals(endpoint, st.endpoint)
            log += "registered on $server (VAPID ${before.vapidPublicKey?.take(12)}…)"

            // Preferences round trip.
            val saved = handle.setPushPreferences(st.preferences.copy(kinds = st.preferences.kinds + ("team_added" to false)))
            assertFalse(saved.kinds["team_added"]!!)
            assertTrue(saved.kinds["ai_approval"]!!)
            handle.setPushPreferences(st.preferences)

            // A test notification, read from ntfy like the distributor, decrypted with this phone's keys.
            handle.sendTestPush()
            var payload: String? = null
            for (i in 0 until 50) {
                val lines = get("$ntfy/$topic/json?poll=1").lines().filter { it.isNotBlank() }
                val msg = lines.map { JSONObject(it) }.firstOrNull { it.optString("event") == "message" }
                if (msg != null) {
                    assertEquals("base64", msg.optString("encoding"))
                    val body = Base64.decode(msg.getString("message"), Base64.DEFAULT)
                    payload = webpushDecrypt(keys, body).decodeToString()
                    break
                }
                Thread.sleep(200)
            }
            assertNotNull("the test arrived at ntfy", payload)
            val event = parsePushPayload(payload!!)
            assertEquals("test", event.kind)
            assertEquals(PushCategory.INFO, event.category)
            val plan = (PushRoutes.decide(event, info.id, info.userId) as PushDecision.Show).plan
            assertEquals(PushOpen.Home(info.id, "test"), plan.open)
            assertEquals("general", plan.channel)
            assertTrue(plan.body.isNotBlank())
            log += "test push: \"${plan.title}\" / \"${plan.body}\" (tag ${plan.tag}, id ${plan.id})"
        } finally {
            runCatching { handle.unregisterPushToken() }
            assertFalse(handle.pushStatus().registered)
            runCatching { core.signOutAccount(info.id, true) }
            dir.deleteRecursively()
            println("LivePushTest:\n  " + log.joinToString("\n  "))
        }
    }
}
