package com.termoak.app.sk

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.termoak.app.R
import com.termoak.app.data.FakeApiErrors
import com.termoak.app.data.HostTest
import com.termoak.app.data.Networks
import com.termoak.app.toUiText
import com.termoak.ffi.TermoakException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Security key errors in words: by code (the engine's and the server's
 * `security_key_*`), from the server's English texts (session reasons,
 * host tests, `/exec` results, which carry no code) and the server
 * session's "Touch your security key (X) on one of your devices…", in
 * English and Spanish.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SkTextsTest {
    private val res get() = ApplicationProvider.getApplicationContext<Application>().resources
    private fun s(id: Int) = res.getString(id)

    @Before
    fun setUp() = FakeApiErrors.install()

    @Test
    fun theServersEnglishBecomesTheAppsText() {
        // The holder's reason when no device is online, and the server's /exec and test texts.
        val cases = mapOf(
            "none of your devices that can use security keys is online: open Termoak on your phone or computer (signed in) and try again" to R.string.sk_error_no_device,
            "nobody confirmed the security key in time" to R.string.sk_error_timeout,
            "the security key request was cancelled" to R.string.sk_error_cancelled,
            "the key is a security key and none can be used here" to R.string.sk_error_required,
            "wrong PIN for the security key" to R.string.sk_error_pin_invalid,
            "the security key's signature does not match this key" to R.string.sk_error_wrong_key,
        )
        cases.forEach { (text, id) -> assertEquals(text, s(id), SkErrors.serverText(text)!!.resolve(res)) }
        assertNull(SkErrors.serverText("connection refused"))
        // A host test with the code, and one with only the text.
        assertEquals(s(R.string.sk_error_no_device), Networks.hostTestError(HostTest(false, errorCode = "security_key_no_device", error = "none of your devices…")).resolve(res))
        assertEquals(s(R.string.sk_error_timeout), Networks.hostTestError(HostTest(false, error = "nobody confirmed the security key in time")).resolve(res))
        // A plain timeout of a host test stays one.
        assertEquals(s(R.string.networks_test_timeout), Networks.hostTestError(HostTest(false, errorCode = "timeout")).resolve(res))
    }

    @Test
    fun touchMessageOfAServerSession() {
        assertEquals(res.getString(R.string.sk_session_touch_key, "YubiKey 5C"),
            SkErrors.sessionMessage("Touch your security key (YubiKey 5C) on one of your devices…")!!.resolve(res))
        assertEquals(s(R.string.sk_session_touch), SkErrors.sessionMessage("Touch your security key on one of your devices…")!!.resolve(res))
        assertNull(SkErrors.sessionMessage("Connecting to web1…"))
    }

    @Test
    @Config(qualifiers = "es")
    fun inSpanish() {
        assertEquals("Toca tu llave de seguridad (YubiKey 5C) en uno de tus dispositivos…",
            SkErrors.sessionMessage("Touch your security key (YubiKey 5C) on one of your devices…")!!.resolve(res))
        assertEquals("No se tocó la llave de seguridad a tiempo.", SkErrors.serverText("nobody confirmed the security key in time")!!.resolve(res))
    }

    @Test
    fun engineErrorsByCode() {
        // The engine's SecurityKey error: its apiError code (status 0).
        val text = FakeApiErrors.device("security key: no security key was connected to this phone in time", "security_key_no_device")
        assertEquals(s(R.string.sk_error_no_device), TermoakException.SecurityKey(text).toUiText(R.string.term_connect_failed).resolve(res))
        // A failure adds the device's own words.
        val failed = FakeApiErrors.device("security key: CTAP error 0x7F OTHER", "security_key_failed")
        assertEquals(res.getString(R.string.sk_error_with_detail, s(R.string.sk_error_failed), "CTAP error 0x7F OTHER"),
            TermoakException.SecurityKey(failed).toUiText(R.string.term_connect_failed).resolve(res))
        // The server's (an HTTP status and the code).
        val server = FakeApiErrors.with(504, "security_key_timeout", "nobody confirmed the security key in time")
        assertEquals(s(R.string.sk_error_timeout), TermoakException.Server(server).toUiText(R.string.term_connect_failed).resolve(res))
        // A blocked PIN says so.
        val blocked = FakeApiErrors.device("security key: the PIN is blocked: unplug the key", "security_key_pin_invalid")
        assertEquals(res.getString(R.string.sk_error_with_detail, s(R.string.sk_error_pin_invalid), "the PIN is blocked: unplug the key"),
            TermoakException.SecurityKey(blocked).toUiText(R.string.term_connect_failed).resolve(res))
    }

    @Test
    fun whatTheSheetShows() {
        val r = SkFixtures.signRequest(ByteArray(4), purpose = "server_session")
        assertEquals("deploy@web1 (10.0.0.5)", SkErrors.targetText(r))
        assertEquals("YubiKey · YubiKey 5C NFC", SkErrors.keyText(r))
        assertEquals(s(R.string.sk_purpose_server_session), SkErrors.purposeText(SkPurpose.Sign(r)).resolve(res))
        assertEquals(s(R.string.sk_purpose_connect), SkErrors.purposeText(SkPurpose.Sign(r.copy(purpose = "connect"))).resolve(res))
        assertEquals(s(R.string.sk_purpose_load), SkErrors.purposeText(SkPurpose.LoadResident).resolve(res))
    }
}
