package com.termoak.app.data

import android.app.Application
import com.termoak.ffi.E2eeKeyInfo
import com.termoak.ffi.E2eePairingRequest
import com.termoak.ffi.E2eeStatus
import com.termoak.ffi.SyncReport
import com.termoak.ffi.TermoakException
import com.termoak.ffi.VaultE2eeInfo
import com.termoak.ffi.VaultInfo
import com.termoak.ffi.VaultKind
import com.termoak.ffi.VaultRole
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The E2EE state of the accounts against a stand-in server (0.7 phase 5c). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class E2eeBookTest {
    private val now = 1_800_000_000_000L

    private class Fake : E2eeApi {
        var info = """{"features":{"e2ee":true,"sso":true,"audit_v2":{"export":["csv"],"retention":true,"sinks":["webhook"]}}}"""
        var status = E2eeStatus(true, true, E2eeKeyInfo("k1", "bd73 81ea b08d 2d62 675c 27c7 9e99 8215", 1), true, true, 0u, 0uL)
        var pairings = emptyList<E2eePairingRequest>()
        val rotated = mutableListOf<String>()
        var pairingsAsked = 0
        var rotateFails = false
        override suspend fun info() = info
        override suspend fun status() = status
        override suspend fun pairings(): List<E2eePairingRequest> {
            pairingsAsked++
            return pairings
        }
        override suspend fun rotateVault(vaultId: String): UInt {
            rotated += vaultId
            if (rotateFails) throw TermoakException.Server("the server responded 409 (e2ee_stale): stale")
            return 3u
        }
    }

    private val fake = Fake()

    @Before
    fun setUp() {
        E2eeBook.reset()
        E2eeBook.now = { now }
        E2eeBook.apiOf = { if (it == "a1") fake else null }
    }

    @After
    fun tearDown() {
        E2eeBook.reset()
        E2eeBook.apiOf = { null }
        E2eeBook.now = { System.currentTimeMillis() }
    }

    private fun vault(id: String, e: VaultE2eeInfo?) = VaultInfo(id, "a1", "Ops-$id", "", VaultKind.TEAM, VaultRole.MANAGER, "t", "T", "T", null, null, 1L, 1L, true, null, null, e)

    @Test
    fun featuresAndState() = runBlocking {
        E2eeBook.start("a1")
        assertTrue(E2eeBook.supported("a1"))
        assertTrue(E2eeBook.ssoSupported("a1"))
        assertEquals(listOf("csv"), E2eeBook.audit("a1")!!.exports)
        assertFalse(E2eeBook.supported("other"))
        assertFalse(E2eeBook.supported(null))
        assertTrue(E2eeBook.unlocked("a1"))
        assertFalse(E2eeBook.locked("a1"))
        // An older server: nothing.
        val old = E2eeBook.parseFeatures("""{"features":{"vaults":true}}""")
        assertFalse(old.e2ee || old.sso)
        assertEquals(null, old.audit)
        E2eeBook.forget("a1")
        assertFalse(E2eeBook.supported("a1"))
    }

    @Test
    fun pairingsOnlyOnAnUnlockedDeviceAndOnce() = runBlocking {
        E2eeBook.start("a1")
        val p = E2eePairingRequest("p1", "Pixel 9", "123456", now - 1_000, now + 600_000)
        fake.pairings = listOf(p, p.copy(id = "old", expiresAt = now - 1))
        fake.status = fake.status.copy(pendingPairings = 2u)
        val first = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { withTimeout(2_000) { E2eeBook.newPairings.first() } }
        assertEquals(listOf(p), E2eeBook.refresh("a1"))
        assertEquals("a1" to p, first.await())
        assertEquals(listOf(p), E2eeBook.pairings("a1"))
        assertEquals(1, E2eeBook.pairingCount())
        // The same request again is not new.
        assertTrue(E2eeBook.refresh("a1").isEmpty())
        E2eeBook.decided("a1", "p1")
        assertEquals(0, E2eeBook.pairingCount())
        // A locked device never lists them (it can't approve).
        fake.status = fake.status.copy(unlocked = false)
        val asked = fake.pairingsAsked
        E2eeBook.refresh("a1")
        assertEquals(asked, fake.pairingsAsked)
        assertTrue(E2eeBook.locked("a1"))
        assertTrue(E2eeBook.isPairingEvent(org.json.JSONObject("""{"type":"e2ee","event":"pairing","pairing":{"id":"p1"}}""")))
        assertFalse(E2eeBook.isPairingEvent(org.json.JSONObject("""{"type":"vault"}""")))
    }

    @Test
    fun syncNoticesAndAutomaticRotationOncePerVersion() = runBlocking {
        E2eeBook.start("a1")
        val report = SyncReport(0uL, 0uL, 1L, "a1", e2eeWaiting = 2uL)
        val notice = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { withTimeout(2_000) { E2eeBook.notices.first() } }
        val flagged = vault("v1", VaultE2eeInfo(2u, true, true, true, 0u, 0L))
        val notHolder = vault("v2", VaultE2eeInfo(2u, true, false, true, 0u, 0L))
        assertTrue(E2eeBook.synced("a1", report, listOf(flagged, notHolder, vault("v3", null))))
        assertEquals(E2eeBook.Notice.Waiting("a1", 2), notice.await())
        assertEquals(listOf("v1"), fake.rotated)
        // The next sync still sees the flag (the server had not caught up): no second rotation of version 2.
        assertFalse(E2eeBook.synced("a1", report, listOf(flagged)))
        assertEquals(listOf("v1"), fake.rotated)
        // A new version flagged again: rotate again; a race (e2ee_stale) is quiet.
        fake.rotateFails = true
        assertFalse(E2eeBook.synced("a1", report, listOf(vault("v1", VaultE2eeInfo(3u, true, true, true, 0u, 0L)))))
        assertEquals(listOf("v1", "v1"), fake.rotated)
        // Locked: never rotates.
        fake.status = fake.status.copy(unlocked = false)
        E2eeBook.refresh("a1")
        assertFalse(E2eeBook.synced("a1", report, listOf(vault("v1", VaultE2eeInfo(4u, true, true, true, 0u, 0L)))))
        assertEquals(2, fake.rotated.size)
    }
}
