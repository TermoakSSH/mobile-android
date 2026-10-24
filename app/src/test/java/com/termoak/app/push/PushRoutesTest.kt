package com.termoak.app.push

import com.termoak.ffi.PushCategory
import com.termoak.ffi.PushEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What each push kind becomes on the phone (docs-next/PUSH.md). */
class PushRoutesTest {
    private val now = 1_800_000_000_000L

    private fun ev(
        kind: String,
        category: PushCategory,
        data: Map<String, String> = emptyMap(),
        channel: String = "general",
        tag: String = "$kind-1",
        user: String? = "u1",
        expiresAt: Long? = null,
        expired: Boolean = false,
    ) = PushEvent(
        kind = kind, category = category, title = "T", body = "B", data = data, server = "srv", user = user,
        sentAt = now - 1_000, expiresAt = expiresAt, expired = expired, tag = tag, notificationId = 42, channel = channel,
    )

    private fun show(e: PushEvent): PushPlan = (PushRoutes.decide(e, "acc", "u1", now) as PushDecision.Show).plan

    @Test
    fun approvalsOfferButtonsThatOnlyOpenTheConfirmation() {
        val p = show(ev("ai_approval", PushCategory.APPROVAL, mapOf("task_id" to "t1", "approval_id" to "a1"), "approvals", "approval-a1"))
        assertEquals(listOf(PushAction.APPROVE, PushAction.DENY), p.actions)
        assertEquals(PushOpen.AiTask("acc", "t1", "a1"), p.open)
        assertEquals("approvals", p.channel)
        assertEquals("approval-a1", p.tag)
        assertEquals(42, p.id)
        assertTrue(p.urgent)
        assertNull(p.timeoutMs)
        // Every button only carries where to go: the app asks again (behind its lock).
        for (a in p.actions) {
            val (open, action) = PushRoutes.fromExtras(PushRoutes.extras(p.open, a)::get)!!
            assertEquals(p.open, open)
            assertEquals(a, action)
        }
        for (kind in listOf("join_request", "control_request")) {
            val s = show(ev(kind, PushCategory.APPROVAL, mapOf("session_id" to "s1", "participant" to "p1"), "sessions"))
            assertEquals(PushOpen.Session("acc", "s1", kind, "p1"), s.open)
            assertEquals(2, s.actions.size)
        }
        // Phase 4b: no screen yet, the app opens.
        val access = show(ev("access_request", PushCategory.APPROVAL, mapOf("request_id" to "r"), "approvals"))
        assertEquals(PushOpen.Home("acc", "access_request"), access.open)
    }

    @Test
    fun securityKeysOpenTheAppAndExpire() {
        val p = show(ev("security_key_request", PushCategory.SECURITY_KEY, mapOf("request_id" to "r1"), "security_keys", "sk-r1", expiresAt = now + 45_000))
        assertEquals(PushOpen.SecurityKey("acc", "r1"), p.open)
        assertTrue(p.actions.isEmpty())
        assertEquals(45_000L, p.timeoutMs)
        assertTrue(p.urgent)
        assertEquals(PushDecision.Ignore, PushRoutes.decide(ev("security_key_request", PushCategory.SECURITY_KEY, expiresAt = now - 1), "acc", "u1", now))
        assertEquals(PushDecision.Ignore, PushRoutes.decide(ev("security_key_request", PushCategory.SECURITY_KEY, expired = true), "acc", "u1", now))
    }

    @Test
    fun infoKindsAndWithdrawals() {
        assertEquals(PushOpen.AiTask("acc", "t", null), show(ev("ai_finished", PushCategory.INFO, mapOf("task_id" to "t"), "ai")).open)
        assertEquals(PushOpen.Session("acc", "s", "session_shared", null), show(ev("session_shared", PushCategory.INFO, mapOf("session_id" to "s"), "sessions")).open)
        val prompt = show(ev("session_prompt", PushCategory.INFO, mapOf("session_id" to "s", "prompt_id" to "p"), "sessions"))
        assertEquals(PushOpen.Session("acc", "s", "session_prompt", null), prompt.open)
        assertTrue(prompt.urgent)
        assertEquals(PushOpen.Teams("acc"), show(ev("team_added", PushCategory.INFO, mapOf("team_id" to "x"))).open)
        assertEquals(PushOpen.Home("acc", "test"), show(ev("test", PushCategory.INFO)).open)
        // A kind without its ids, or a new one: just the app; an unknown channel goes to "general".
        assertEquals(PushOpen.Home("acc", "ai_finished"), show(ev("ai_finished", PushCategory.INFO)).open)
        assertEquals("general", show(ev("future_kind", PushCategory.INFO, channel = "future")).channel)
        // Withdrawn: the same id and tag as what it replaces.
        assertEquals(PushDecision.Cancel(42, "approval-a1"), PushRoutes.decide(ev("resolved", PushCategory.RESOLVED, tag = "approval-a1"), "acc", "u1", now))
    }

    @Test
    fun anotherAccountsPushIsIgnored() {
        assertEquals(PushDecision.Ignore, PushRoutes.decide(ev("test", PushCategory.INFO, user = "someone-else"), "acc", "u1", now))
        // Unknown on either side: shown.
        assertTrue(PushRoutes.decide(ev("test", PushCategory.INFO, user = null), "acc", "u1", now) is PushDecision.Show)
        assertTrue(PushRoutes.decide(ev("test", PushCategory.INFO), "acc", null, now) is PushDecision.Show)
    }

    @Test
    fun extrasRoundTripAndForeignIntents() {
        val opens = listOf(
            PushOpen.AiTask("a", "t", null), PushOpen.AiTask("a", "t", "ap"), PushOpen.Session("a", "s", "join_request", "p"),
            PushOpen.Session("a", "s", "session_prompt", null), PushOpen.Teams("a"), PushOpen.SecurityKey("a", "r"),
            PushOpen.SecurityKey("a", null), PushOpen.Home("a", "test"),
        )
        for (o in opens) assertEquals(o to null, PushRoutes.fromExtras(PushRoutes.extras(o, null)::get))
        assertNull(PushRoutes.fromExtras { null })
        assertNull(PushRoutes.fromExtras(mapOf("push_account" to "a", "push_open" to "bogus")::get))
        assertNull(PushRoutes.fromExtras(mapOf("push_account" to "a", "push_open" to "ai")::get))
    }

    @Test
    fun distributorChoice() {
        val ntfy = "io.heckel.ntfy"
        val next = "org.unifiedpush.distributor.nextpush"
        assertEquals(ntfy, PushSetup.distributor(listOf(ntfy), null, turnedOff = false))
        assertNull(PushSetup.distributor(listOf(ntfy), null, turnedOff = true))
        assertNull(PushSetup.distributor(listOf(ntfy, next), null, turnedOff = false))
        assertEquals(next, PushSetup.distributor(listOf(ntfy, next), next, turnedOff = false))
        // The picked one was uninstalled.
        assertNull(PushSetup.distributor(listOf(ntfy, "x"), next, turnedOff = false))
        assertNull(PushSetup.distributor(emptyList(), null, turnedOff = false))
        assertEquals(listOf(ntfy), PushSetup.external(listOf("com.termoak", ntfy, ntfy), "com.termoak"))
        assertEquals("Termoak · ana@x.com · next.termoak.com", PushSetup.label("ana@x.com", "https://next.termoak.com/"))
        val key = "B" + "A".repeat(86)
        assertEquals(key, PushSetup.vapid("$key="))
        assertNull(PushSetup.vapid("short"))
        assertNull(PushSetup.vapid(null))
    }
}
