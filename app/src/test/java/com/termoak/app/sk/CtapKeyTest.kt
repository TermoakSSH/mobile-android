package com.termoak.app.sk

import com.termoak.ffi.SecurityKeyAlgorithm
import com.termoak.ffi.SecurityKeyException
import com.yubico.yubikit.fido.Cbor
import com.yubico.yubikit.fido.ctap.Ctap2Session
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/**
 * The app's YubiKit layer ([CtapKey]) against a pretend key that speaks
 * CTAPHID and CTAP 2.1 ([SoftFido]): what goes to the key (rp id `ssh:`,
 * the client data hash, `up` only off for no-touch keys, never `uv`, the
 * PIN's token and auth param) and what comes back (authenticator data,
 * signatures that verify), resident keys through credential management
 * (2.1 and 2.1-PRE), errors, and Cancel sending CTAPHID_CANCEL.
 */
class CtapKeyTest {
    private fun key(d: SoftFido): CtapKey {
        val c = d.connection()
        return CtapKey("Soft", nfc = false, connection = c) { Ctap2Session(c) }
    }

    private fun flags(authData: ByteArray) = authData[32].toInt() and 0xFF

    @Test
    fun createsAndSignsEd25519AndEcdsa() {
        for (alg in listOf(SecurityKeyAlgorithm.ED25519, SecurityKeyAlgorithm.ECDSA_P256)) {
            val d = SoftFido()
            val req = SkFixtures.createRequest(alg)
            val cred = key(d).use { it.makeCredential(req, null, SkCancel()) }
            val authData = cred.authenticatorData
            assertArrayEquals(SoftFido.sha256("ssh:".toByteArray()), authData.copyOfRange(0, 32))
            assertEquals("user present and attested data", 0x41, flags(authData))
            val made = d.credentials.single()
            assertEquals("ssh:", made.rpId)
            assertFalse(made.resident)
            // The COSE key in the authenticator data is the device's.
            assertTrue(Cbor.encode(made.cose).let { cose -> authData.toList().windowed(cose.size).any { it == cose.toList() } })

            val sign = SkFixtures.signRequest(made.id, algorithm = alg)
            val a = key(d).use { it.sign(sign, null, SkCancel()) }
            val data = a.authenticatorData!!
            assertEquals("touched, no PIN", 0x01, flags(data))
            assertTrue("signature over authenticatorData || clientDataHash", SoftFido.verify(made.keys.public, data + sign.clientDataHash, a.signature))
        }
    }

    @Test
    fun noTouchKeysAskWithUpOff() {
        val d = SoftFido()
        key(d).use { it.makeCredential(SkFixtures.createRequest(), null, SkCancel()) }
        d.autoTouch = false // a touch would hang: up=false must not wait for one
        val a = key(d).use { it.sign(SkFixtures.signRequest(d.credentials.single().id, presence = false), null, SkCancel()) }
        assertEquals(0x00, flags(a.authenticatorData!!))
    }

    @Test
    fun pinGivesTokenAndUserVerification() {
        val d = SoftFido(pin = "1234")
        // A resident key on a key with a PIN needs it.
        try {
            key(d).use { it.makeCredential(SkFixtures.createRequest(resident = true), null, SkCancel()) }
            fail("no PIN")
        } catch (e: SecurityKeyException.PinRequired) {
        }
        val cred = key(d).use { it.makeCredential(SkFixtures.createRequest(resident = true, verify = true), "1234", SkCancel()) }
        assertEquals("touched, verified, attested", 0x45, flags(cred.authenticatorData))
        val sign = SkFixtures.signRequest(d.credentials.single().id, verify = true)
        val a = key(d).use { it.sign(sign, "1234", SkCancel()) }
        assertEquals(0x05, flags(a.authenticatorData!!))
        assertTrue(SoftFido.verify(d.credentials.single().keys.public, a.authenticatorData!! + sign.clientDataHash, a.signature))
    }

    @Test
    fun wrongPinCountsDownAndBlocks() {
        val d = SoftFido(pin = "1234")
        key(d).use { it.makeCredential(SkFixtures.createRequest(), null, SkCancel()) }
        val id = d.credentials.single().id
        val k = key(d)
        try {
            k.sign(SkFixtures.signRequest(id, verify = true), "0000", SkCancel())
            fail("wrong PIN")
        } catch (e: SecurityKeyException.PinInvalid) {
            assertFalse(SecurityKeyFlow.pinBlocked(e))
        }
        assertEquals(7, k.pinRetries())
        // Too short for a FIDO PIN: YubiKit refuses it before sending, as a wrong PIN.
        try {
            k.sign(SkFixtures.signRequest(id, verify = true), "12", SkCancel())
            fail("short PIN")
        } catch (e: SecurityKeyException.PinInvalid) {
        }
        k.close()
        d.pinRetries = 1
        try {
            key(d).use { it.sign(SkFixtures.signRequest(id, verify = true), "0000", SkCancel()) }
            fail("blocked")
        } catch (e: SecurityKeyException.PinInvalid) {
            assertTrue(SkErrors.messageOf(e), SecurityKeyFlow.pinBlocked(e))
        }
    }

    @Test
    fun listsResidentKeysWithCtap21AndPreview() {
        for (preview in listOf(false, true)) {
            val d = SoftFido(pin = "1234", preview = preview)
            key(d).use { k ->
                k.makeCredential(SkFixtures.createRequest(resident = true, user = "alice"), "1234", SkCancel())
            }
            key(d).use { it.makeCredential(SkFixtures.createRequest(SecurityKeyAlgorithm.ECDSA_P256, resident = true, user = "bob", application = "ssh:work"), "1234", SkCancel()) }
            key(d).use { it.makeCredential(SkFixtures.createRequest(), null, SkCancel()) } // not resident
            key(d).use { it.makeCredential(SkFixtures.createRequest(resident = true, application = "webauthn.io"), "1234", SkCancel()) }
            val list = key(d).use { it.listResident("ssh:", "1234", SkCancel()) }
            assertEquals("preview=$preview", listOf("alice", "bob"), list.mapNotNull { it.userName }.sorted())
            val bob = list.single { it.userName == "bob" }
            val made = d.credentials.single { it.user["name"] == "bob" }
            assertEquals("ssh:work", bob.application)
            assertArrayEquals(made.id, bob.credentialId)
            assertArrayEquals(Cbor.encode(made.cose), bob.publicKeyCose)
            assertArrayEquals(SkFixtures.userId("bob"), bob.userId)
        }
        // Nothing stored: an empty list.
        assertTrue(key(SoftFido(pin = "1234")).use { it.listResident("ssh:", "1234", SkCancel()) }.isEmpty())
    }

    @Test
    fun unknownKeyIsNotFound() {
        val d = SoftFido()
        try {
            key(d).use { it.sign(SkFixtures.signRequest(SkFixtures.random(48)), null, SkCancel()) }
            fail("not found")
        } catch (e: SecurityKeyException.NotFound) {
        }
    }

    @Test
    fun cancelStopsTheKeyWaitingForATouch() {
        val d = SoftFido()
        key(d).use { it.makeCredential(SkFixtures.createRequest(), null, SkCancel()) }
        d.autoTouch = false
        val cancel = SkCancel()
        val result = AtomicReference<Throwable?>()
        val t = thread {
            try {
                key(d).use { it.sign(SkFixtures.signRequest(d.credentials.single().id), null, cancel) }
            } catch (e: Throwable) {
                result.set(e)
            }
        }
        val deadline = System.currentTimeMillis() + 3000
        while (!d.waitingForTouch && System.currentTimeMillis() < deadline) Thread.sleep(10)
        assertTrue("the key waits for a touch", d.waitingForTouch)
        cancel.stop(SkStop.CANCELLED)
        t.join(3000)
        assertTrue(result.get().toString(), result.get() is SecurityKeyException.Cancelled)
        assertTrue("CTAPHID_CANCEL reached the key", d.cancelsReceived >= 1)
        assertFalse(d.waitingForTouch)
    }

    @Test
    fun ctapStatusesBecomeCodes() {
        assertEquals(SkErrors.Code.NOT_FOUND, SkErrors.ofCtap(0x2E))
        assertEquals(SkErrors.Code.PIN_REQUIRED, SkErrors.ofCtap(0x36))
        assertEquals(SkErrors.Code.PIN_INVALID, SkErrors.ofCtap(0x31))
        assertEquals(SkErrors.Code.TIMEOUT, SkErrors.ofCtap(0x2F))
        assertEquals(SkErrors.Code.CANCELLED, SkErrors.ofCtap(0x2D))
        assertEquals(SkErrors.Code.UNSUPPORTED, SkErrors.ofCtap(0x26))
        assertEquals(SkErrors.Code.FAILED, SkErrors.ofCtap(0x7F))
        assertTrue(SkErrors.exceptionOfCtap(0x34, "PIN_AUTH_BLOCKED") is SecurityKeyException.PinInvalid)
        assertTrue(SecurityKeyFlow.pinBlocked(SkErrors.exceptionOfCtap(0x32, "PIN_BLOCKED")))
    }
}
