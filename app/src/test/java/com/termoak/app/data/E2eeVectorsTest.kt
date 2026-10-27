package com.termoak.app.data

import android.app.Application
import com.termoak.ffi.e2eeFingerprint
import com.termoak.ffi.e2eeRecoveryKeyCheck
import com.termoak.ffi.e2eeRecoveryKeyValid
import com.termoak.ffi.e2eeRecoveryWordlist
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.security.MessageDigest
import java.util.Base64

/**
 * The shared E2EE vectors (core `crates/termoak-core/tests/e2ee_vectors.json`,
 * docs-next/E2EE-SSO-AUDIT.md §2.2) through the engine the app ships
 * (built for this computer): the user key's fingerprint, the recovery
 * phrase and Trezor's 256-bit BIP39 vectors, the typed-phrase cases with
 * their refusal reasons (and the app's live feedback on top of the core's
 * check), the word list's SHA-256; and the pairing code and fingerprint
 * computed again in Kotlin from their definitions.
 *
 * The engine part needs its library: TERMOAK_FFI_DIR (or
 * TERMOAK_LIVE_FFI_DIR) with `libtermoak_ffi.so` built for this computer;
 * without it only the Kotlin part runs.
 */
@RunWith(RobolectricTestRunner::class) // org.json is Android's.
@Config(sdk = [33], application = Application::class)
class E2eeVectorsTest {
    private val vectors: JSONObject by lazy {
        val f = listOf("../core/crates/termoak-core/tests/e2ee_vectors.json", "core/crates/termoak-core/tests/e2ee_vectors.json").map(::File).first { it.exists() }
        JSONObject(f.readText())
    }

    private fun engine(): Boolean {
        val dir = System.getenv("TERMOAK_FFI_DIR") ?: System.getenv("TERMOAK_LIVE_FFI_DIR") ?: return false
        System.setProperty("jna.library.path", dir)
        return true
    }

    private fun sha256(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b)
    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }

    @Test
    fun fingerprintAndPairingCodeFromTheirDefinitions() {
        val uk = vectors.getJSONObject("user_key")
        val pub = Base64.getDecoder().decode(uk.getString("public_b64"))
        // The first 16 bytes of the SHA-256, as eight groups of four hex digits.
        val fp = hex(sha256(pub).copyOf(16)).chunked(4).joinToString(" ")
        assertEquals(uk.getString("fingerprint"), fp)
        // SHA-256("termoak:e2ee:pairing-code:v1:{pairing}:" ‖ device public key), first 4 bytes big-endian mod 10⁶.
        val p = vectors.getJSONObject("pairing")
        val h = sha256("termoak:e2ee:pairing-code:v1:${p.getString("pairing_id")}:".toByteArray() + Base64.getDecoder().decode(p.getString("device_public_b64")))
        val n = ((h[0].toLong() and 0xff) shl 24) or ((h[1].toLong() and 0xff) shl 16) or ((h[2].toLong() and 0xff) shl 8) or (h[3].toLong() and 0xff)
        assertEquals(p.getString("code"), "%06d".format(n % 1_000_000))
    }

    @Test
    fun throughTheEngine() {
        assumeTrue("TERMOAK_FFI_DIR not set: the engine part is skipped", engine())
        val uk = vectors.getJSONObject("user_key")
        assertEquals(uk.getString("fingerprint"), e2eeFingerprint(uk.getString("public_b64")))

        val bip = vectors.getJSONObject("bip39")
        val words = e2eeRecoveryWordlist()
        assertEquals(2048, words.size)
        assertEquals(words.sorted(), words)
        assertEquals(bip.getString("wordlist_sha256"), hex(sha256((words.joinToString("\n") + "\n").toByteArray())))

        val display = vectors.getJSONObject("recovery_key").getString("display")
        val c = e2eeRecoveryKeyCheck(display)
        assertTrue(c.valid)
        assertEquals(24u, c.words)
        assertEquals(E2eeRules.Feedback.Valid, E2eeRules.feedback(display, ::e2eeRecoveryKeyCheck, words))

        val v = bip.getJSONArray("vectors")
        for (i in 0 until v.length()) {
            val phrase = v.getJSONObject(i).getString("phrase")
            assertTrue(phrase, e2eeRecoveryKeyValid(phrase))
            // Four-letter prefixes and capitals are accepted too (the unlock field's promise).
            assertTrue(e2eeRecoveryKeyValid(phrase.split(' ').joinToString("  ") { it.take(4).uppercase() }))
        }

        val parse = bip.getJSONArray("parse")
        for (i in 0 until parse.length()) {
            val case = parse.getJSONObject(i)
            val input = case.getString("input")
            val r = e2eeRecoveryKeyCheck(input)
            if (case.has("raw")) {
                assertTrue(input, r.valid)
                assertEquals(E2eeRules.Feedback.Valid, E2eeRules.feedback(input, ::e2eeRecoveryKeyCheck, words))
                continue
            }
            assertFalse(input, r.valid)
            val reason = case.getString("reason")
            assertEquals(input, reason, r.reason)
            if (case.has("word")) assertEquals(case.getInt("word").toUInt(), r.word)
            if (case.has("words")) assertEquals(case.getInt("words").toUInt(), r.words)
            // What the unlock field says while typing: an unknown word at once, a short phrase as "typing".
            val fb = E2eeRules.feedback(input, ::e2eeRecoveryKeyCheck, words)
            when (reason) {
                "word_count" -> assertEquals(E2eeRules.Feedback.Typing(case.getInt("words")), fb)
                "unknown_word" -> assertEquals(E2eeRules.Feedback.Problem("unknown_word", case.getInt("word"), 24), fb)
                else -> assertEquals(reason, (fb as E2eeRules.Feedback.Problem).reason)
            }
        }
    }
}
