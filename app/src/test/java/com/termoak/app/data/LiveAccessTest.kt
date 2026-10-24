package com.termoak.app.data

import android.app.Application
import android.util.Base64
import com.termoak.app.R
import com.termoak.app.UiText
import com.termoak.app.push.PushAction
import com.termoak.app.push.PushDecision
import com.termoak.app.push.PushOpen
import com.termoak.app.push.PushRoutes
import com.termoak.ffi.AccessMode
import com.termoak.ffi.AccessPolicyInfo
import com.termoak.ffi.AccessRequestScope
import com.termoak.ffi.AccessRequestStatus
import com.termoak.ffi.AccessTargetKind
import com.termoak.ffi.AccountHandle
import com.termoak.ffi.AuthHandler
import com.termoak.ffi.AuthRequest
import com.termoak.ffi.HttpMethod
import com.termoak.ffi.ItemAccess
import com.termoak.ffi.ItemFilter
import com.termoak.ffi.NewVault
import com.termoak.ffi.PushCategory
import com.termoak.ffi.ServerChoice
import com.termoak.ffi.TeamRole
import com.termoak.ffi.TermoakCore
import com.termoak.ffi.TermoakException
import com.termoak.ffi.VaultRole
import com.termoak.ffi.WebPushKeys
import com.termoak.ffi.generateVaultKey
import com.termoak.ffi.parsePushPayload
import com.termoak.ffi.webpushDecrypt
import com.termoak.ffi.webpushGenerateKeys
import com.termoak.kit.apiError
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
 * Opt-in end-to-end check of teams security from the phone (0.7 phase 4d)
 * against a real server, through the real engine (built for this computer)
 * and the app's own rules: an admin makes a team, a team vault where
 * members are Connect only, the team's SSH CA (its install script runs in
 * an OpenSSH container that then trusts it) and an approval policy; the
 * member ("this phone") is refused with `access_approval_required`, asks
 * for access, the admin's phone gets the `access_request` push through
 * ntfy (Approve/Deny only open the app), approves a one-minute window, the
 * member's phone gets `access_decided`; the member connects from the phone
 * with a team certificate for a key made just for it, and a server session
 * closes with `access_expired` when the window ends; a second window is
 * revoked (`access_revoked`) and the next connection is refused again.
 * Everything is removed afterwards (CA disabled, vault and team deleted,
 * push registrations and devices signed out).
 *
 * Skipped unless TERMOAK_LIVE_ACCESS_SERVER is set, with
 * TERMOAK_LIVE_FFI_DIR, TERMOAK_LIVE_ADMIN_EMAIL/PASSWORD,
 * TERMOAK_LIVE_MEMBER_EMAIL/PASSWORD, TERMOAK_LIVE_SSHD (`host:port` of a
 * fresh termoak-qa-sshd both reach), TERMOAK_LIVE_HANDSHAKE (a folder: the
 * test writes `install.sh` there and waits for `installed`, made by whoever
 * runs it in the container) and TERMOAK_LIVE_NTFY.
 */
@RunWith(RobolectricTestRunner::class) // org.json and Base64 are Android's.
// API 33: the engine's objects use JNA's cleaner (API 34's SystemCleaner isn't reachable on the JVM).
@Config(sdk = [33], application = Application::class)
class LiveAccessTest {
    private fun env(k: String): String = System.getenv(k) ?: error("$k is not set")

    private object TrustAll : AuthHandler {
        override fun onHostKey(host: String, port: UInt, keyType: String, fingerprint: String) = true
        override fun onPrompt(request: AuthRequest): List<String>? = null
    }

    private fun get(url: String): String {
        val c = URI(url).toURL().openConnection() as HttpURLConnection
        c.connectTimeout = 5_000
        c.readTimeout = 10_000
        return c.inputStream.bufferedReader().use { it.readText() }
    }

    /** A UnifiedPush-style phone: an ntfy topic and Web Push keys. */
    private inner class Phone(val handle: AccountHandle, val accountId: String, val userId: String?) {
        val keys: WebPushKeys = webpushGenerateKeys()
        val topic = "upAccess${System.nanoTime()}"
        private val ntfy = env("TERMOAK_LIVE_NTFY").trimEnd('/')
        private val seen = mutableSetOf<String>()

        suspend fun register() = assertTrue(handle.registerWebPush("$ntfy/$topic?up=1", keys.p256dh, keys.auth))

        /** The next push whose kind is [kind] (decrypted and parsed as the app does), within 20 s. */
        fun next(kind: String, category: PushCategory? = null): com.termoak.ffi.PushEvent {
            repeat(100) {
                for (line in get("$ntfy/$topic/json?poll=1").lines().filter { it.isNotBlank() }) {
                    val m = JSONObject(line)
                    if (m.optString("event") != "message" || !seen.add(m.getString("id"))) continue
                    val e = parsePushPayload(webpushDecrypt(keys, Base64.decode(m.getString("message"), Base64.DEFAULT)).decodeToString())
                    if (e.kind == kind && (category == null || e.category == category)) return e
                }
                Thread.sleep(200)
            }
            error("no $kind push")
        }
    }

    @Test
    fun temporaryAccessWithTeamCertificates() = runBlocking {
        val server = System.getenv("TERMOAK_LIVE_ACCESS_SERVER")
        assumeTrue("TERMOAK_LIVE_ACCESS_SERVER not set: skipped", server != null)
        System.setProperty("jna.library.path", env("TERMOAK_LIVE_FFI_DIR"))
        ApiErrors.lookup = { it.apiError }
        val (sshHost, sshPort) = env("TERMOAK_LIVE_SSHD").split(':').let { it[0] to it[1].toInt() }
        val handshake = File(env("TERMOAK_LIVE_HANDSHAKE"))
        val tag = (System.nanoTime() % 100000).toString()
        val log = mutableListOf<String>()
        val dirs = listOf("admin", "member").map { File(System.getProperty("java.io.tmpdir"), "termoak-live-access-$it-$tag").apply { mkdirs() } }
        val adminCore = TermoakCore(dirs[0].path, generateVaultKey())
        val memberCore = TermoakCore(dirs[1].path, generateVaultKey())
        val adminInfo = adminCore.signIn(ServerChoice.Custom(server!!), env("TERMOAK_LIVE_ADMIN_EMAIL"), env("TERMOAK_LIVE_ADMIN_PASSWORD"))
        val memberInfo = memberCore.signIn(ServerChoice.Custom(server), env("TERMOAK_LIVE_MEMBER_EMAIL"), env("TERMOAK_LIVE_MEMBER_PASSWORD"))
        val admin = adminCore.account(adminInfo.id)
        val member = memberCore.account(memberInfo.id)
        val adminPhone = Phone(admin, adminInfo.id, adminInfo.userId)
        val memberPhone = Phone(member, memberInfo.id, memberInfo.userId)
        var teamId: String? = null
        var teamName = ""
        var vaultId: String? = null
        var vaultName = ""
        var caMade = false
        val sessions = mutableListOf<String>()
        try {
            assertTrue("the server has teams security", AccessBook.hasFeature(admin.apiRequest(HttpMethod.GET, "/api/v1/info", null)))

            // ----- The admin: team, Connect only vault, CA trusted by the container, approval policy -----
            teamName = "4d-android-$tag"
            val team = admin.createTeam(teamName)
            teamId = team.id
            admin.addTeamMember(team.id, env("TERMOAK_LIVE_MEMBER_EMAIL"), TeamRole.MEMBER)
            vaultName = "4d-android-prod-$tag"
            val vault = admin.createVault(NewVault(name = vaultName, teamId = team.id, teamMemberRole = VaultRole.CONNECT_ONLY))
            vaultId = vault.id
            val ca = admin.createCa(team.id, "ed25519")
            caMade = true
            assertNotNull(ca.ca)
            val script = admin.caInstallScript(team.id, krl = true, principals = false, refresh = false)
            // What the app copies for a host: the script in a heredoc, never curl | sh.
            val command = AccessRules.installCommand(script)
            assertTrue(command.startsWith("sudo sh -s <<'TERMOAK_CA'\n#!/bin/sh"))
            File(handshake, "install.sh").writeText(script)
            var installed = false
            repeat(120) { if (!installed) { installed = File(handshake, "installed").exists(); if (!installed) Thread.sleep(500) } }
            assertTrue("the install script ran in the container", installed)
            log += "team $teamName, vault $vaultName (Connect only), CA ${ca.ca!!.fingerprint} trusted by the container"
            val hostId = JSONObject(
                admin.apiRequest(
                    HttpMethod.POST, "/api/v1/hosts",
                    JSONObject().put("label", "4d-android-$tag").put("address", sshHost).put("vault_id", vault.id)
                        .put("settings", JSONObject().put("port", sshPort).put("username", "qa").put("auth", AccessRules.TEAM_CERTIFICATE)).toString(),
                ),
            ).getString("id")
            admin.setAccessPolicy(vault.id, AccessTargetKind.VAULT, vault.id, AccessPolicyInfo(mode = AccessMode.APPROVAL, maxMinutes = 60u))
            adminPhone.register()
            memberPhone.register()

            // ----- The member's phone: Connect only, team certificate usable, approval needed -----
            member.syncNow()
            val mv = memberCore.vaults(ItemFilter(accountIds = listOf(memberInfo.id), vaultIds = null, includeDevice = false)).first { it.id == vault.id }
            assertEquals(VaultRole.CONNECT_ONLY, mv.role)
            assertTrue(mv.policy!!.accessPolicies && mv.policy!!.certificates)
            val host = memberCore.getHost(hostId, memberInfo.id)
            assertEquals(ItemAccess.CONNECT_ONLY, host.access)
            val gates = AccessRules.gates(mv.role, mv.policy)
            assertFalse(gates.reveal)
            val usable = gates.certificates && AccessRules.effectiveAuth(host.settings.auth, host.groupId, emptyList()).teamCertificate
            assertEquals(AccessRules.Route.PHONE, AccessRules.route(host.access, mv.strict, false, usable))
            val refused = runCatching { memberCore.connect(hostId, TrustAll, memberInfo.id) }.exceptionOrNull() as TermoakException
            val need = AccessTexts.needOf(refused, hostId)!!
            assertEquals(UiText.Res(R.string.error_access_approval_required), AccessTexts.of(refused))
            assertEquals(60, need.maxMinutes)
            log += "refused before a window: ${refused.apiError?.code} (max ${need.maxMinutes} min, target ${need.targetKind})"

            // ----- Request → the admin's phone → approve one minute → the member's phone -----
            val r = member.createAccessRequest(AccessTargetKind.HOST, hostId, 1u, "phase 4d live test")
            assertEquals(AccessRequestStatus.PENDING, r.status)
            val push = adminPhone.next("access_request", PushCategory.APPROVAL)
            val plan = (PushRoutes.decide(push, adminInfo.id, adminInfo.userId) as PushDecision.Show).plan
            assertEquals(PushOpen.Access(adminInfo.id, r.id, mine = false), plan.open)
            assertEquals(listOf(PushAction.APPROVE, PushAction.DENY), plan.actions)
            assertEquals("approvals", plan.channel)
            log += "admin's phone: access_request \"${plan.title}\" with Approve/Deny (they only open the app)"
            val inbox = admin.accessRequests(AccessRequestScope.Approvals, AccessRequestStatus.PENDING)
            assertTrue(AccessRules.waiting(inbox).any { it.id == r.id })
            val approved = admin.approveAccessRequest(r.id, null, "go ahead")
            assertEquals(AccessRequestStatus.APPROVED, approved.status)
            val decided = memberPhone.next("access_decided")
            assertEquals(PushOpen.Access(memberInfo.id, r.id, mine = true), (PushRoutes.decide(decided, memberInfo.id, memberInfo.userId) as PushDecision.Show).plan.open)
            assertTrue(PushRoutes.decide(adminPhone.next("resolved"), adminInfo.id, adminInfo.userId) is PushDecision.Cancel)
            log += "approved 1 min; member's phone: access_decided; admin's request notification withdrawn"

            // ----- A window: the phone connects with a team certificate for an ephemeral key -----
            member.forgetAccess()
            val access = member.hostAccess(hostId)
            assertTrue(access.allowed)
            assertEquals(AccessBook.HostWindow.Open(access.grant!!.endsAt!!), AccessBook.windowOf(access, System.currentTimeMillis()))
            val ssh = memberCore.connect(hostId, TrustAll, memberInfo.id)
            val out = ssh.exec("echo cert-$((40+2))", 20u)
            ssh.disconnect()
            assertTrue(out.stdout, out.stdout.contains("cert-42"))
            val cert = member.myCertificates().filter { it.hostId == hostId }.maxBy { it.createdAt }
            assertEquals(listOf("qa"), cert.principals)
            assertEquals(r.id, cert.requestId)
            assertTrue("capped to the window", cert.validBefore <= access.grant!!.endsAt!! + 1_000)
            log += "ssh from the phone with certificate #${cert.serial} (${cert.purpose}, principals ${cert.principals}, until ${cert.validBefore})"

            // The server trusts the container's host key (a host test inside the window, with a server-side certificate).
            val test = Networks.hostTest(member.apiRequest(HttpMethod.POST, "/api/v1/hosts/$hostId/test?trust=true", "{}"))!!
            assertTrue("${test.errorCode} ${test.error}", test.ok)
            log += "server host test inside the window: ok · ${test.latencyMs} ms"

            // ----- A server session closes with access_expired when the window ends -----
            val sid = JSONObject(member.apiRequest(HttpMethod.POST, "/api/v1/sessions", JSONObject().put("host_id", hostId).put("cols", 80).put("rows", 24).toString())).getString("id")
            sessions += sid
            val expired = waitClosed(member, sid, 150)
            assertEquals("access_expired", expired)
            assertEquals(UiText.Res(R.string.access_closed_access_expired), AccessTexts.serverText(AccessRules.keptReason(expired, "session_ended")))
            log += "server session closed: $expired"

            // ----- A second window revoked: access_revoked, then refused again -----
            val r2 = member.createAccessRequest(AccessTargetKind.HOST, hostId, 10u, "phase 4d live test, revoke")
            admin.approveAccessRequest(r2.id, null, null)
            member.forgetAccess()
            val sid2 = JSONObject(member.apiRequest(HttpMethod.POST, "/api/v1/sessions", JSONObject().put("host_id", hostId).put("cols", 80).put("rows", 24).toString())).getString("id")
            sessions += sid2
            waitRunning(member, sid2)
            admin.revokeAccessRequest(r2.id, "end of the test")
            assertEquals("access_revoked", waitClosed(member, sid2, 60))
            member.forgetAccess()
            val again = runCatching { memberCore.connect(hostId, TrustAll, memberInfo.id) }.exceptionOrNull() as TermoakException
            assertEquals("access_approval_required", again.apiError?.code)
            log += "revoked: access_revoked, then ${again.apiError?.code}"
        } finally {
            sessions.forEach { s -> runCatching { member.apiRequest(HttpMethod.DELETE, "/api/v1/sessions/$s", null) } }
            runCatching { adminPhone.handle.unregisterPushToken() }
            runCatching { memberPhone.handle.unregisterPushToken() }
            if (caMade) teamId?.let { runCatching { admin.disableCa(it, teamName) } }
            vaultId?.let { runCatching { admin.deleteVault(it, vaultName) } }
            teamId?.let { runCatching { admin.deleteTeam(it) } }
            runCatching { memberCore.listKnownHosts(ItemFilter()).filter { it.port.toInt() == sshPort }.forEach { memberCore.deleteKnownHost(it.id, it.accountId) } }
            runCatching { memberCore.signOutAccount(memberInfo.id, true) }
            runCatching { adminCore.signOutAccount(adminInfo.id, true) }
            dirs.forEach { it.deleteRecursively() }
            println("LiveAccessTest:\n  " + log.joinToString("\n  "))
        }
        Unit
    }

    private fun sessionState(h: AccountHandle, sid: String): JSONObject = runBlocking {
        JSONObject(h.apiRequest(HttpMethod.GET, "/api/v1/sessions/$sid", null)).optJSONObject("state") ?: JSONObject()
    }

    private fun waitRunning(h: AccountHandle, sid: String) {
        repeat(120) {
            val s = sessionState(h, sid)
            if (s.optString("state") == "running") return
            if (s.optString("state") == "closed") error("closed: ${s.optString("reason")}")
            Thread.sleep(250)
        }
        error("not running")
    }

    /** The reason the server closed the session with (within [seconds]). */
    private fun waitClosed(h: AccountHandle, sid: String, seconds: Int): String {
        repeat(seconds * 2) {
            val s = sessionState(h, sid)
            if (s.optString("state") == "closed") return s.optString("reason")
            Thread.sleep(500)
        }
        error("still open after $seconds s")
    }
}
