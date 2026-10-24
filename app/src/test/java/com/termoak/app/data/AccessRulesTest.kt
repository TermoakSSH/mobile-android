package com.termoak.app.data

import android.app.Application
import com.termoak.ffi.AccessMode
import com.termoak.ffi.AccessPolicyInfo
import com.termoak.ffi.AccessPolicyRecord
import com.termoak.ffi.AccessRequestInfo
import com.termoak.ffi.AccessRequestStatus
import com.termoak.ffi.AccessTargetKind
import com.termoak.ffi.AiApprovalLevel
import com.termoak.ffi.CaOverview
import com.termoak.ffi.CertificatePolicy
import com.termoak.ffi.HostAccessInfo
import com.termoak.ffi.ItemAccess
import com.termoak.ffi.Team2faRequirement
import com.termoak.ffi.VaultPolicyInfo
import com.termoak.ffi.VaultRole
import com.termoak.app.ui.inheritedPolicy
import com.termoak.app.ui.requestTargets
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Teams security rules of the app (0.7 phase 4d, TEAMS-SECURITY.md §13.2):
 * roles, policy gates, inherited authentication and where a terminal opens,
 * countdowns and durations, the two shapes of `access_approval_required`,
 * the request form, events and notices, close reasons, the CA install
 * command, and the shared [AccessBook] against a stand-in server.
 */
@RunWith(RobolectricTestRunner::class) // org.json is Android's.
@Config(sdk = [34], application = Application::class)
class AccessRulesTest {
    private val now = 1_800_000_000_000L

    private fun policy(
        reveal: Boolean = true, ai: Boolean = true, networks: Boolean = true, record: Boolean = false,
        access: Boolean = false, certificates: Boolean = false,
    ) = VaultPolicyInfo("t", reveal, ai, AiApprovalLevel.DEFAULT, networks, record, false, access, certificates)

    private fun request(
        id: String = "r1", status: AccessRequestStatus = AccessRequestStatus.PENDING, requester: String = "u2",
        canDecide: Boolean = true, startsAt: Long? = null, endsAt: Long? = null, reason: String? = null,
    ) = AccessRequestInfo(
        id, "v1", AccessTargetKind.HOST, "h1", "web1", requester, "Ana", "ana@example.com", "deploy", 60u, status,
        now - 60_000, now + 3_600_000, null, "Bea", null, reason, startsAt, endsAt, false, false, canDecide,
    )

    @Test
    fun rolesAndDeleting() {
        assertTrue(AccessRules.rank(VaultRole.CONNECT_ONLY) < AccessRules.rank(VaultRole.USE_ONLY))
        assertTrue(AccessRules.rank(VaultRole.USE_ONLY) < AccessRules.rank(VaultRole.EDITOR))
        assertTrue(AccessRules.rank(VaultRole.EDITOR) < AccessRules.rank(VaultRole.MANAGER))
        assertEquals(0, AccessRules.rank(VaultRole.UNKNOWN))
        assertEquals(listOf(VaultRole.CONNECT_ONLY, VaultRole.USE_ONLY, VaultRole.EDITOR, VaultRole.MANAGER), AccessRules.grantableRoles(true))
        assertEquals(listOf(VaultRole.USE_ONLY, VaultRole.EDITOR), AccessRules.grantableRoles(false))
        // An explicit Admin (a grant) never deletes; the owner (implicit) does.
        assertFalse(AccessRules.canDeleteVault(VaultRole.MANAGER, implicitManager = false))
        assertTrue(AccessRules.canDeleteVault(VaultRole.MANAGER, implicitManager = true))
        assertFalse(AccessRules.canDeleteVault(VaultRole.EDITOR, implicitManager = true))
        assertTrue(AccessRules.noSecrets(ItemAccess.CONNECT_ONLY))
        assertTrue(AccessRules.noSecrets(ItemAccess.USE_ONLY))
        assertFalse(AccessRules.noSecrets(ItemAccess.EDITOR))
        assertTrue(ItemAccess.CONNECT_ONLY.useOnly())
        assertFalse(ItemAccess.CONNECT_ONLY.canWrite())
    }

    @Test
    fun gatesHideWhatThePolicyForbids() {
        // No policy: as the role allows.
        assertEquals(AccessRules.Gates(true, true, true, false, false, false), AccessRules.gates(VaultRole.EDITOR, null))
        assertFalse(AccessRules.gates(VaultRole.USE_ONLY, null).reveal)
        // Forbid reveal: Editors lose it, Admins keep it.
        assertFalse(AccessRules.gates(VaultRole.EDITOR, policy(reveal = false)).reveal)
        assertTrue(AccessRules.gates(VaultRole.MANAGER, policy(reveal = false)).reveal)
        val g = AccessRules.gates(VaultRole.CONNECT_ONLY, policy(ai = false, networks = false, record = true, access = true, certificates = true))
        assertEquals(AccessRules.Gates(false, false, false, true, true, true), g)
    }

    @Test
    fun authenticationIsInheritedFromTheNearestGroup() {
        val groups = listOf(
            AccessRules.GroupAuth("g1", "Prod", null, AccessRules.TEAM_CERTIFICATE),
            AccessRules.GroupAuth("g2", "DB", "g1", null),
        )
        val e = AccessRules.effectiveAuth(null, "g2", groups)
        assertTrue(e.teamCertificate)
        assertEquals("Prod", e.fromGroup)
        // Its own wins (a later version's value too), and none without groups.
        assertEquals(AccessRules.EffectiveAuth("future", null), AccessRules.effectiveAuth("future", "g2", groups))
        assertEquals(AccessRules.EffectiveAuth(null, null), AccessRules.effectiveAuth(null, null, groups))
        // A loop in the parents stops.
        val loop = listOf(AccessRules.GroupAuth("a", "A", "b", null), AccessRules.GroupAuth("b", "B", "a", null))
        assertNull(AccessRules.effectiveAuth(null, "a", loop).auth)
    }

    @Test
    fun whereATerminalOpens() {
        val phone = AccessRules.Route.PHONE
        val server = AccessRules.Route.SERVER
        assertEquals(phone, AccessRules.route(ItemAccess.EDITOR, strict = true, telnet = false, teamCertificateUsable = false))
        assertEquals(phone, AccessRules.route(ItemAccess.USE_ONLY, strict = false, telnet = false, teamCertificateUsable = false))
        // Connect only and Strict: the server, unless the host uses a team certificate.
        assertEquals(server, AccessRules.route(ItemAccess.CONNECT_ONLY, strict = false, telnet = false, teamCertificateUsable = false))
        assertEquals(phone, AccessRules.route(ItemAccess.CONNECT_ONLY, strict = false, telnet = false, teamCertificateUsable = true))
        assertEquals(server, AccessRules.route(ItemAccess.USE_ONLY, strict = true, telnet = false, teamCertificateUsable = false))
        assertEquals(phone, AccessRules.route(ItemAccess.USE_ONLY, strict = true, telnet = false, teamCertificateUsable = true))
        // Telnet stays here (the server doesn't open it; its tab says why).
        assertEquals(phone, AccessRules.route(ItemAccess.CONNECT_ONLY, strict = false, telnet = true, teamCertificateUsable = false))
    }

    @Test
    fun countdownsAndDurations() {
        assertEquals(60, AccessRules.remainingSecs(now + 59_001, now))
        assertEquals(0, AccessRules.remainingSecs(now - 5, now))
        assertEquals(1_000L, AccessRules.tickMs(120))
        assertEquals(15_000L, AccessRules.tickMs(121))
        assertTrue(AccessRules.endingSoon(300))
        assertFalse(AccessRules.endingSoon(301))
        assertEquals(AccessRules.Left(0, 0, 45), AccessRules.left(45))
        assertEquals(AccessRules.Left(0, 42, null), AccessRules.left(41 * 60 + 1))
        assertEquals(AccessRules.Left(1, 5, null), AccessRules.left(65 * 60))
        assertEquals(listOf(15, 30, 60), AccessRules.durationChoices(60))
        assertEquals(listOf(15, 30, 45), AccessRules.durationChoices(45))
        assertEquals(listOf(15, 30, 60, 120, 240, 480, 1440, 4320, 10080), AccessRules.durationChoices(10080))
        // An approver gives the asked window or a shorter one.
        assertEquals(listOf(15, 30, 60, 90), AccessRules.approveChoices(90))
    }

    @Test
    fun theTwoShapesOfApprovalRequired() {
        // The server's (a server session, a certificate).
        val server = AccessRules.needOf(JSONObject("""{"host_id":"h1","policy_id":"p1","target_kind":"group","target_id":"g1","max_minutes":120}"""))
        assertEquals(AccessRules.AccessNeed("h1", null, AccessTargetKind.GROUP, "g1", 120, null, true), server)
        // The engine's own check before a local connection.
        val engine = AccessRules.needOf(
            JSONObject("""{"host_id":"h1","vault_id":"v1","target_kind":"vault","target_id":"v1","max_minutes":30,"pending":{"id":"r9"},"can_request":false}"""),
        )
        assertEquals(AccessRules.AccessNeed("h1", "v1", AccessTargetKind.VAULT, "v1", 30, "r9", false), engine)
        // Nothing usable: this host, an hour.
        assertEquals(AccessRules.AccessNeed("h2", null, AccessTargetKind.HOST, null, 60, null, true), AccessRules.needOf(JSONObject("{}"), "h2"))
        // From a host's answer.
        val a = HostAccessInfo(
            "h1", "v1", AccessMode.APPROVAL,
            AccessPolicyRecord("p", "v1", AccessTargetKind.GROUP, "g1", AccessPolicyInfo(mode = AccessMode.APPROVAL, maxMinutes = 240u), "u", 0),
            false, false, null, request(), true, 240u, true, true, false, true, true,
        )
        assertEquals(AccessRules.AccessNeed("h1", "v1", AccessTargetKind.GROUP, "g1", 240, "r1", true), AccessRules.needOf(a))
        // What the dialog offers: the host, and what the policy covers.
        val targets = requestTargets(server, "web1", "Production", "Ops")
        assertEquals(listOf(AccessTargetKind.HOST, AccessTargetKind.GROUP), targets.map { it.kind })
        assertEquals("Production", targets[1].label)
    }

    @Test
    fun theRequestForm() {
        assertEquals(AccessRules.RequestProblem.REASON, AccessRules.requestProblem(60, 60, "  "))
        assertEquals(AccessRules.RequestProblem.REASON_LONG, AccessRules.requestProblem(60, 60, "x".repeat(501)))
        assertEquals(AccessRules.RequestProblem.DURATION, AccessRules.requestProblem(120, 60, "deploy"))
        assertNull(AccessRules.requestProblem(60, 60, "deploy"))
        assertNull(AccessRules.requestProblem(60, 60, "é".repeat(500)))
    }

    @Test
    fun requestsEventsAndNotices() {
        val json = JSONObject(
            """{"type":"access_request","event":"approved","request":{"id":"r1","vault_id":"v1","target_kind":"host","target_id":"h1",
               "target_label":"web1","requester_id":"me","requester_name":"","requester_email":"me@example.com","reason":"deploy","minutes":30,
               "status":"approved","created_at":1,"expires_at":2,"decided_by":"u9","decided_by_name":"Bea","starts_at":${now - 1000},"ends_at":${now + 1_800_000}}}""",
        )
        val ev = AccessRules.eventOf(json)!!
        assertEquals("approved", ev.event)
        assertEquals("me@example.com", AccessRules.requesterName(ev.request))
        assertFalse(ev.request.canDecide) // Events carry no can_decide: the lists are read again.
        assertTrue(AccessRules.isActive(ev.request, now))
        assertEquals(AccessRules.Notice.APPROVED, AccessRules.noticeOf("me", ev))
        assertNull(AccessRules.noticeOf("someone-else", ev))
        val created = AccessRules.AccessEvent("created", ev.request)
        assertEquals(AccessRules.Notice.CREATED, AccessRules.noticeOf("approver", created))
        assertNull(AccessRules.noticeOf("me", created))
        assertEquals(AccessRules.Notice.DENIED_REASON, AccessRules.noticeOf("me", AccessRules.AccessEvent("denied", ev.request.copy(decisionReason = "not now"))))
        assertEquals(AccessRules.Notice.DENIED, AccessRules.noticeOf("me", AccessRules.AccessEvent("denied", ev.request)))
        assertNull(AccessRules.eventOf(JSONObject("""{"type":"vault"}""")))
        // Lists: waiting (can decide), endable windows, states.
        val pending = request()
        val notMine = request("r2", canDecide = false)
        val open = request("r3", AccessRequestStatus.APPROVED, startsAt = now - 10, endsAt = now + 60_000)
        val ended = request("r4", AccessRequestStatus.APPROVED, startsAt = now - 100, endsAt = now - 10)
        assertEquals(listOf(pending), AccessRules.waiting(listOf(pending, notMine, open)))
        assertEquals(listOf(open), AccessRules.endable(listOf(pending, open, ended), now))
        assertEquals(AccessRules.StatusKey.ACTIVE, AccessRules.statusKey(open, now))
        assertEquals(AccessRules.StatusKey.ENDED, AccessRules.statusKey(ended, now))
        assertEquals(AccessRules.StatusKey.PENDING, AccessRules.statusKey(pending, now))
    }

    @Test
    fun closeReasonsSurviveTheSessionEnd() {
        assertEquals("access_expired", AccessRules.keptReason("access_expired", "session_ended"))
        assertEquals("access_revoked", AccessRules.keptReason(null, "access_revoked"))
        assertEquals("access_revoked", AccessRules.keptReason("access_expired", "access_revoked"))
        assertEquals("exit", AccessRules.keptReason(null, "exit"))
        assertTrue(AccessRules.isCloseReason("vault_access_revoked"))
    }

    @Test
    fun theCaInstallCommandIsAHeredoc() {
        val cmd = AccessRules.installCommand("#!/bin/sh\nset -eu\necho ok\n")
        assertTrue(cmd.startsWith("sudo sh -s <<'TERMOAK_CA'\n#!/bin/sh\n"))
        assertTrue(cmd.endsWith("echo ok\nTERMOAK_CA\n"))
        assertFalse(cmd.contains("curl"))
        val o = CaOverview(
            null, emptyList(), "", listOf("TrustedUserCAKeys /etc/ssh/termoak_user_ca.pub"),
            listOf("RevokedKeys /etc/ssh/termoak_revoked.krl", "AuthorizedPrincipalsFile /etc/ssh/termoak_principals/%u"),
            "k", "l", "i", CertificatePolicy(60u, 480u, listOf("{login}"), emptyList(), null), true,
        )
        assertEquals(2, AccessRules.sshdLines(o, krl = true, principals = false).size)
        assertEquals(3, AccessRules.sshdLines(o, krl = true, principals = true).size)
        assertEquals(1, AccessRules.sshdLines(o, krl = false, principals = false).size)
        assertTrue(AccessRules.principalValid("prod-admin"))
        assertFalse(AccessRules.principalValid("has space"))
        assertFalse(AccessRules.principalValid("x".repeat(65)))
        assertEquals("ana.maria_x", AccessRules.defaultPrincipal("Ana.Maria+x@example.com"))
        assertEquals(listOf("{login}", "deploy"), AccessRules.templates(" {login}, ,deploy "))
        assertEquals(24, AccessRules.overlapHours("24"))
        assertNull(AccessRules.overlapHours("721"))
        assertTrue(AccessRules.looksLikePublicKey("ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIOMqqnkVzrm0SdG6UOoqKLsabgH5C9okWi0dh2l9GKJl ana"))
        assertFalse(AccessRules.looksLikePublicKey("not a key"))
    }

    @Test
    fun inheritedPolicies() {
        fun rec(kind: AccessTargetKind, target: String, mode: AccessMode) =
            AccessPolicyRecord("p-$target", "v1", kind, target, AccessPolicyInfo(mode = mode), "u", 0)
        val list = listOf(rec(AccessTargetKind.GROUP, "g1", AccessMode.APPROVAL), rec(AccessTargetKind.VAULT, "v1", AccessMode.DIRECT))
        val parents = mapOf("g2" to "g1")
        assertEquals("p-g1", inheritedPolicy(AccessTargetKind.HOST, "h1", "v1", { "g2" }, { parents[it] }, list)?.id)
        assertEquals("p-v1", inheritedPolicy(AccessTargetKind.HOST, "h1", "v1", { null }, { parents[it] }, list)?.id)
        assertNull(inheritedPolicy(AccessTargetKind.VAULT, "v1", "v1", { null }, { null }, list))
    }

    // ----- The shared book -----

    private class FakeApi : AccessApi {
        var info = """{"features":{"teams_security":{"ca":true}}}"""
        var approvals = listOf<AccessRequestInfo>()
        var mine = listOf<AccessRequestInfo>()
        var hostAnswers = mutableMapOf<String, HostAccessInfo>()
        var asked = 0
        var forgot = 0
        var twoFa = listOf<Team2faRequirement>()
        override suspend fun info() = info
        override suspend fun approvals() = approvals
        override suspend fun mine() = mine
        override suspend fun hostAccess(hostId: String): HostAccessInfo {
            asked++
            return hostAnswers.getValue(hostId)
        }
        override suspend fun team2fa() = twoFa
        override fun forgetAccess() {
            forgot++
        }
    }

    private val api = FakeApi()
    private var clock = now

    @Before
    fun book() {
        AccessBook.clear()
        AccessBook.apiOf = { if (it == "acc") api else null }
        AccessBook.now = { clock }
    }

    @After
    fun reset() {
        AccessBook.clear()
        AccessBook.apiOf = { null }
        AccessBook.now = { System.currentTimeMillis() }
    }

    private fun answer(allowed: Boolean, grantEnds: Long? = null, pending: Boolean = false) = HostAccessInfo(
        "h1", "v1", AccessMode.APPROVAL, null, false, allowed,
        grantEnds?.let { request("g", AccessRequestStatus.APPROVED, startsAt = now - 1, endsAt = it) },
        if (pending) request() else null, true, 60u, true, true, true, false, true,
    )

    @Test
    fun theBookFollowsTheServer() = runBlocking {
        assertFalse(AccessBook.supported("acc"))
        api.approvals = listOf(request(), request("r2", canDecide = false))
        api.twoFa = listOf(Team2faRequirement("t", "Ops", now + 86_400_000, false))
        AccessBook.start("acc")
        assertTrue(AccessBook.supported("acc"))
        assertEquals(1, AccessBook.waitingCount())
        assertEquals("Ops", AccessBook.team2faAll().single().second.teamName)
        // An older server: nothing.
        api.info = """{"features":{}}"""
        AccessBook.start("acc")
        assertFalse(AccessBook.supported("acc"))
        api.info = """{"features":{"teams_security":{}}}"""
        AccessBook.start("acc")

        // Hosts: asked once in a while, the window from the answer.
        api.hostAnswers["h1"] = answer(allowed = false)
        assertEquals(AccessBook.HostWindow.Needed, AccessBook.run { ask("acc", "h1"); windowOf("acc", "h1") })
        AccessBook.ask("acc", "h1")
        assertEquals(1, api.asked) // Fresh: not asked again.
        clock += AccessBook.HOST_FRESH_MS + 1
        api.hostAnswers["h1"] = answer(allowed = false, pending = true)
        AccessBook.ask("acc", "h1")
        assertEquals(AccessBook.HostWindow.Pending, AccessBook.windowOf("acc", "h1"))

        // An event: the engine forgets, the lists and every host asked before are read again.
        val end = clock + 600_000
        api.hostAnswers["h1"] = answer(allowed = true, grantEnds = end)
        api.approvals = emptyList()
        val ev = AccessBook.onEvent("acc", JSONObject("""{"type":"access_request","event":"approved","request":{"id":"r1","requester_id":"me","status":"approved"}}"""))
        assertEquals("approved", ev!!.event)
        assertEquals(1, api.forgot)
        assertEquals(0, AccessBook.waitingCount())
        assertEquals(AccessBook.HostWindow.Open(end), AccessBook.windowOf("acc", "h1"))
        // Ended: needed again.
        assertEquals(AccessBook.HostWindow.Needed, AccessBook.windowOf("acc", "h1", end + 1))
        // Signed out: forgotten.
        AccessBook.forget("acc")
        assertNull(AccessBook.host("acc", "h1"))
        assertFalse(AccessBook.supported("acc"))
    }
}
