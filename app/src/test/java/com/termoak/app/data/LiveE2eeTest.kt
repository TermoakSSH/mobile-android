package com.termoak.app.data

import android.app.Application
import com.termoak.app.R
import com.termoak.app.UiText
import com.termoak.ffi.AccountHandle
import com.termoak.ffi.AuditScope
import com.termoak.ffi.AuthHandler
import com.termoak.ffi.AuthRequest
import com.termoak.ffi.HttpMethod
import com.termoak.ffi.ItemFilter
import com.termoak.ffi.NewVault
import com.termoak.ffi.ServerChoice
import com.termoak.ffi.SsoSettings
import com.termoak.ffi.TermoakCore
import com.termoak.ffi.TermoakException
import com.termoak.ffi.VaultMemberTarget
import com.termoak.ffi.VaultRole
import com.termoak.ffi.e2eeRecoveryKeyCheck
import com.termoak.ffi.e2eeRecoveryWordlist
import com.termoak.ffi.generateVaultKey
import com.termoak.kit.apiError
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.net.CookieManager
import java.net.HttpCookie
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder

/**
 * Opt-in end-to-end check of E2EE vaults, SSO and the audit from the phone
 * (0.7 phase 5c) against a real server, through the real engine (built for
 * this computer) and the app's own rules (E2eeBook, E2eeRules, E2eeTexts,
 * SsoRules, AuditRules, NetworksApi):
 *
 * - "this phone" (the member account) sets E2EE up (passphrase + the 24
 *   words, checked as the confirmation step does), the other person (the
 *   admin account) too; the phone makes a vault with a key and a host (an
 *   OpenSSH container), shares it as Editor, turns E2EE on giving the key
 *   only to itself, then confirms the other person by fingerprint;
 * - the server can no longer reveal the key; a host test and a server
 *   session are refused with `e2ee_unlock_required`, then work with the
 *   per-test/per-session unlock (the host key trusted with the test);
 *   local SSH from the phone with the sealed key;
 * - a second device of the phone's account is locked, asks for the key
 *   with a pairing whose six-digit code the phone sees and approves, then
 *   locks and unlocks again with the recovery phrase typed as 4-letter
 *   capitals;
 * - the other person is downgraded to Use: the phone rotates the vault key
 *   by itself after its sync (once);
 * - the vault's audit has the E2EE entries and is exported as CSV;
 * - SSO through a local Dex (optional, TERMOAK_LIVE_DEX): a team with the
 *   provider and a domain vouched for by the server's administrator; a new
 *   person signs in from the phone through `termoak://sso` (account
 *   created), the phone's account links its identity, enforcement refuses
 *   a password sign-in with `sso_required` naming the team;
 * - E2EE turned off; everything removed (vault, team, SSO account, links,
 *   E2EE keys reset, devices signed out, known hosts).
 *
 * Skipped unless TERMOAK_LIVE_E2EE_SERVER is set, with TERMOAK_LIVE_FFI_DIR,
 * TERMOAK_LIVE_ADMIN_EMAIL/PASSWORD, TERMOAK_LIVE_MEMBER_EMAIL/PASSWORD,
 * TERMOAK_LIVE_SSHD (`ip:port` both reach), TERMOAK_LIVE_SSH_KEY (the
 * private key the container trusts) and optionally TERMOAK_LIVE_DEX /
 * TERMOAK_LIVE_DEX_SECRET (issuer and client secret of a Dex whose static
 * users have the password `password`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class LiveE2eeTest {
    private fun env(k: String): String = System.getenv(k) ?: error("$k is not set")

    private object TrustAll : AuthHandler {
        override fun onHostKey(host: String, port: UInt, keyType: String, fingerprint: String) = true
        override fun onPrompt(request: AuthRequest): List<String>? = null
    }

    private val log = mutableListOf<String>()

    private suspend fun code(block: suspend () -> Unit): String? = try {
        block(); null
    } catch (e: TermoakException) {
        e.apiError?.code ?: e.javaClass.simpleName
    }

    /** The test's "browser": signs in at Dex as [user] and follows redirects until the app's callback. */
    private fun browse(start: String, user: String): String {
        val cookies = CookieManager()
        var url = start
        repeat(25) {
            if (url.startsWith("termoak://")) return url
            val c = URI(url).toURL().openConnection() as HttpURLConnection
            c.instanceFollowRedirects = false
            val host = URI(url)
            cookies.cookieStore.get(host).takeIf { it.isNotEmpty() }?.let { list -> c.setRequestProperty("Cookie", list.joinToString("; ") { "${it.name}=${it.value}" }) }
            val st = c.responseCode
            c.headerFields["Set-Cookie"]?.forEach { h -> HttpCookie.parse(h).forEach { cookies.cookieStore.add(host, it) } }
            if (st in 300..399) {
                url = host.resolve(c.getHeaderField("Location")).toString().let { if (c.getHeaderField("Location").startsWith("termoak://")) c.getHeaderField("Location") else it }
                return@repeat
            }
            val body = (if (st >= 400) c.errorStream else c.inputStream)?.readBytes()?.decodeToString().orEmpty()
            require(body.contains("name=\"login\"")) { "stuck at $st: ${body.take(200)}" }
            val action = Regex("<form[^>]*action=\"([^\"]*)\"").find(body)?.groupValues?.get(1)?.replace("&amp;", "&") ?: url
            val post = host.resolve(action)
            val p = post.toURL().openConnection() as HttpURLConnection
            p.instanceFollowRedirects = false
            p.requestMethod = "POST"
            p.doOutput = true
            cookies.cookieStore.get(post).takeIf { it.isNotEmpty() }?.let { list -> p.setRequestProperty("Cookie", list.joinToString("; ") { "${it.name}=${it.value}" }) }
            p.outputStream.use { it.write("login=${URLEncoder.encode(user, "UTF-8")}&password=password".toByteArray()) }
            require(p.responseCode in 300..399) { "login refused: ${p.responseCode}" }
            p.headerFields["Set-Cookie"]?.forEach { h -> HttpCookie.parse(h).forEach { cookies.cookieStore.add(post, it) } }
            url = post.resolve(p.getHeaderField("Location")).toString()
        }
        error("no callback")
    }

    private suspend fun AccountHandle.json(m: HttpMethod, path: String, body: String? = null) = apiRequest(m, path, body)

    /** What the steps share. */
    private inner class Run {
        val server = System.getenv("TERMOAK_LIVE_E2EE_SERVER")!!
        val sshIp: String
        val sshPort: Int
        val sshKey = File(env("TERMOAK_LIVE_SSH_KEY")).readText()
        val dex: String? = System.getenv("TERMOAK_LIVE_DEX")
        val tag = (System.nanoTime() % 100000).toString()
        val choice = ServerChoice.Custom(server)
        val dirs = listOf("phone", "phone2", "peer", "sso").map { File(System.getProperty("java.io.tmpdir"), "termoak-live-e2ee-$it-$tag").apply { mkdirs() } }
        val phoneCore = TermoakCore(dirs[0].path, generateVaultKey())
        val phone2Core = TermoakCore(dirs[1].path, generateVaultKey())
        val peerCore = TermoakCore(dirs[2].path, generateVaultKey())
        val ssoCore = TermoakCore(dirs[3].path, generateVaultKey())
        val memberEmail = env("TERMOAK_LIVE_MEMBER_EMAIL")
        val adminEmail = env("TERMOAK_LIVE_ADMIN_EMAIL")
        lateinit var phoneInfo: com.termoak.ffi.AccountInfo
        lateinit var peerInfo: com.termoak.ffi.AccountInfo
        lateinit var phone: AccountHandle
        lateinit var peer: AccountHandle
        var phone2Id: String? = null
        var vaultId: String? = null
        var vaultName = ""
        var teamId: String? = null
        var ssoAccount: String? = null
        val sessions = mutableListOf<String>()
        lateinit var words: List<String>
        lateinit var keyId: String
        lateinit var hostId: String
        lateinit var peerFp: String

        init {
            val (ip, port) = env("TERMOAK_LIVE_SSHD").split(':').let { it[0] to it[1].toInt() }
            sshIp = ip
            sshPort = port
        }
    }

    @Test
    fun e2eeVaultPairingSsoAndAudit() = runBlocking {
        val server = System.getenv("TERMOAK_LIVE_E2EE_SERVER")
        assumeTrue("TERMOAK_LIVE_E2EE_SERVER not set: skipped", server != null)
        System.setProperty("jna.library.path", env("TERMOAK_LIVE_FFI_DIR"))
        ApiErrors.lookup = { it.apiError }
        val r = Run()
        r.phoneInfo = r.phoneCore.signIn(r.choice, r.memberEmail, env("TERMOAK_LIVE_MEMBER_PASSWORD"))
        r.peerInfo = r.peerCore.signIn(r.choice, r.adminEmail, env("TERMOAK_LIVE_ADMIN_PASSWORD"))
        r.phone = r.phoneCore.account(r.phoneInfo.id)
        r.peer = r.peerCore.account(r.peerInfo.id)
        try {
            setUp(r)
            vault(r)
            serverSide(r)
            localSsh(r)
            pairing(r)
            rotation(r)
            audit(r)
            if (r.dex != null) sso(r)
            off(r)
        } finally {
            cleanup(r)
        }
    }

    private suspend fun setUp(r: Run) = with(r) {
        // ----- Features -----
        val f = E2eeBook.parseFeatures(phone.json(HttpMethod.GET, "/api/v1/info"))
        assertTrue("e2ee", f.e2ee)
        assertNotNull("audit_v2", f.audit)
        E2eeBook.reset()
        E2eeBook.apiOf = { if (it == phoneInfo.id) HandleE2eeApi(phone) else null }
        E2eeBook.start(phoneInfo.id)
        assertTrue(E2eeBook.supported(phoneInfo.id))
        assertFalse("the phone's account starts without an E2EE key", E2eeBook.setUp(phoneInfo.id))

        // ----- Setup: passphrase, the 24 words, three of them confirmed -----
        val setup = phone.e2eeSetup("staging only phrase for 5c $tag")
        words = setup.recoveryKey.split(' ')
        assertEquals(24, words.size)
        assertTrue(e2eeRecoveryKeyCheck(setup.recoveryKey).valid)
        val pos = E2eeRules.confirmPositions(tag.toLong())
        assertTrue(E2eeRules.confirmMatches(setup.recoveryKey, pos, pos.map { words[it].take(4).uppercase() }))
        E2eeBook.refresh(phoneInfo.id)
        assertTrue(E2eeBook.unlocked(phoneInfo.id))
        val phoneFp = E2eeBook.status(phoneInfo.id)!!.key!!.fingerprint
        peerFp = peer.e2eeSetup(null).key.fingerprint
        log += "E2EE set up on the phone ($phoneFp, passphrase + 24 words) and for the other person ($peerFp)"

    }

    private suspend fun vault(r: Run) = with(r) {
        // ----- A vault with a key and a host, shared as Editor; E2EE on with the key for the phone only -----
        vaultName = "5c-android-$tag"
        val vault = phone.createVault(NewVault(name = vaultName))
        vaultId = vault.id
        phone.addVaultMember(vault.id, VaultMemberTarget.User(env("TERMOAK_LIVE_ADMIN_EMAIL")), VaultRole.EDITOR)
        val key = phoneCore.importKey("5c-key", sshKey, null, false, null, phoneInfo.id, vault.id)
        keyId = key.id
        hostId = JSONObject(
            phone.json(
                HttpMethod.POST, "/api/v1/hosts",
                JSONObject().put("label", "5c-android-$tag").put("address", sshIp).put("vault_id", vault.id)
                    .put("settings", JSONObject().put("port", sshPort).put("username", "qa").put("key_id", key.id)).toString(),
            ),
        ).getString("id")
        phone.syncNow()
        val state = phone.e2eeVaultState(vault.id)
        assertFalse(state.e2ee)
        val peerMember = state.recipients.first { it.email.equals(env("TERMOAK_LIVE_ADMIN_EMAIL"), true) }
        assertEquals("the fingerprint shown for the other person is theirs", peerFp, peerMember.fingerprint)
        phone.e2eeEnableVault(vault.id, listOf(phoneInfo.userId!!))
        phone.syncNow()
        val enabled = phoneCore.vaults(ItemFilter(listOf(phoneInfo.id), listOf(vault.id), false)).first()
        assertEquals(E2eeRules.Badge.E2EE, E2eeRules.badge(enabled, unlocked = true))
        assertEquals(1u, enabled.e2ee!!.pendingMembers)
        val pending = phone.e2eePendingMembers(vault.id)
        assertEquals(listOf(peerFp), pending.map { it.fingerprint })
        assertFalse(pending.first().keyChanged)
        assertEquals(1u, phone.e2eeAddMembers(vault.id, pending.map { it.userId }))
        peer.syncNow()
        val peerVault = peerCore.vaults(ItemFilter(listOf(peerInfo.id), listOf(vault.id), false)).first()
        assertTrue("the other person got the key", peerVault.e2ee!!.hasWrap)
        assertFalse(peerCore.getKey(key.id, peerInfo.id).e2eeLocked)
        log += "vault $vaultName end-to-end encrypted (${state.items} secrets sealed on the phone); the other person confirmed by fingerprint"

    }

    private suspend fun serverSide(r: Run) = with(r) {
        // ----- What the server can no longer do -----
        val reveal = code { phone.json(HttpMethod.GET, "/api/v1/keys/${keyId}/secret") }
        assertEquals("e2ee_locked", reveal)
        var test: Throwable? = null
        try {
            phone.json(HttpMethod.POST, "/api/v1/hosts/$hostId/test?trust=true", "{}")
        } catch (e: TermoakException) {
            test = e
        }
        assertTrue("host test refused: ${test?.message}", test != null && E2eeTexts.needsUnlock(test))
        assertEquals(UiText.Res(R.string.error_e2ee_unlock_required), E2eeTexts.of(test!!))
        val t = NetworksApi(phone).testHostUnlocked(hostId, trust = true)
        assertTrue("host test with the unlock: ${t.error} ${t.errorCode}", t.ok)
        log += "server: reveal refused (e2ee_locked), host test refused (e2ee_unlock_required), then ok with the per-test unlock (${t.latencyMs} ms, host key trusted)"
        var refused: Throwable? = null
        try {
            phone.openServerSession(hostId, 80u, 24u, "5c", null)
        } catch (e: TermoakException) {
            refused = e
        }
        assertTrue("server session refused: ${refused?.message}", refused != null && E2eeTexts.needsUnlock(refused))
        val s = phone.openServerSessionUnlocked(hostId, 80u, 24u, "5c-unlocked", null)
        sessions += s.id
        Thread.sleep(3_000)
        val st = phone.getServerSession(s.id)
        log += "server session with the per-session unlock: ${st.state}"
        phone.closeServerSession(s.id)
        sessions.remove(s.id)

    }

    private suspend fun localSsh(r: Run) = with(r) {
        // ----- Local SSH from the phone with the sealed key -----
        val ssh = phoneCore.connect(hostId, TrustAll, phoneInfo.id)
        val out = ssh.exec("echo e2ee-ok", 20u)
        ssh.disconnect()
        assertEquals("e2ee-ok", out.stdout.trim())
        log += "local SSH from the phone with the end-to-end encrypted key: ${out.stdout.trim()}"

    }

    private suspend fun pairing(r: Run) = with(r) {
        // ----- A second device: locked, pairing with the same code, then the recovery phrase -----
        val phone2Info = phone2Core.signIn(choice, memberEmail, env("TERMOAK_LIVE_MEMBER_PASSWORD"))
        phone2Id = phone2Info.id
        val phone2 = phone2Core.account(phone2Info.id)
        phone2.syncNow()
        val st2 = phone2.e2eeStatus()
        assertTrue(st2.setUp && !st2.unlocked)
        assertTrue(phone2Core.getHost(hostId, phone2Info.id).e2eeLocked || phone2Core.getKey(keyId, phone2Info.id).e2eeLocked)
        val locked = runCatching { phone2Core.connect(hostId, TrustAll, phone2Info.id) }.exceptionOrNull()
        assertTrue("locked here: ${locked?.message}", locked != null && E2eeTexts.isLocked(locked) && !E2eeTexts.needsUnlock(locked))
        val start = phone2.e2eePairingStart()
        val fresh = E2eeBook.refresh(phoneInfo.id)
        val asked = fresh.firstOrNull { it.id == start.id } ?: E2eeBook.pairings(phoneInfo.id).first { it.id == start.id }
        assertEquals("the phone shows the same six-digit code", start.code, asked.code)
        phone.e2eePairingDecide(asked.id, true)
        var opened: ULong? = null
        repeat(10) { if (opened == null) { opened = phone2.e2eePairingFinish(start.id); if (opened == null) Thread.sleep(500) } }
        assertNotNull(opened)
        assertTrue(phone2.e2eeStatus().unlocked)
        log += "second device unlocked by pairing (code ${start.code} on both, $opened secrets opened)"
        phone2.e2eeLock()
        assertFalse(phone2.e2eeStatus().unlocked)
        val typed = words.mapIndexed { i, w -> "${i + 1}) ${w.take(4).uppercase()}" }.joinToString("\n")
        assertEquals(E2eeRules.Feedback.Valid, E2eeRules.feedback(typed, ::e2eeRecoveryKeyCheck, e2eeRecoveryWordlist()))
        val wrong = code { phone2.e2eeUnlockWithPassphrase("not the passphrase") }
        assertEquals("e2ee_passphrase_invalid", wrong)
        phone2.e2eeUnlockWithRecoveryKey(typed)
        assertTrue(phone2.e2eeStatus().unlocked)
        log += "locked and unlocked again with the recovery phrase typed as numbered 4-letter capitals (a wrong passphrase: $wrong)"

    }

    private suspend fun rotation(r: Run) = with(r) {
        // ----- Downgrade: the phone rotates by itself after its sync, once -----
        val grant = phone.vaultMembers(vaultId!!).first { it.email.equals(env("TERMOAK_LIVE_ADMIN_EMAIL"), true) }
        phone.setVaultMemberRole(vaultId!!, grant.id, VaultRole.USE_ONLY)
        val report = phone.syncNow()
        val before = phoneCore.vaults(ItemFilter(listOf(phoneInfo.id), listOf(vaultId!!), false)).first()
        assertTrue("rekey_needed after the downgrade", before.e2ee!!.rekeyNeeded)
        assertTrue(E2eeBook.synced(phoneInfo.id, report, listOf(before)))
        assertFalse("only once per version", E2eeBook.synced(phoneInfo.id, report, listOf(before)))
        phone.syncNow()
        val after = phoneCore.vaults(ItemFilter(listOf(phoneInfo.id), listOf(vaultId!!), false)).first()
        assertEquals(before.e2ee!!.keyVersion + 1u, after.e2ee!!.keyVersion)
        assertFalse(after.e2ee!!.rekeyNeeded)
        log += "the other person downgraded to Use: the phone rotated the vault key by itself (version ${before.e2ee!!.keyVersion} → ${after.e2ee!!.keyVersion})"

    }

    private suspend fun audit(r: Run) = with(r) {
        // ----- The vault's audit and its export -----
        val q = AuditRules.Filters(AuditRules.Preset.E2EE).query(System.currentTimeMillis())
        val entries = phone.auditSearch(AuditScope.VAULT, q, vaultId!!)
        val actions = entries.map { it.action }.toSet()
        assertTrue(actions.toString(), actions.containsAll(listOf("e2ee.enable", "e2ee.wraps_add", "e2ee.unlock", "e2ee.rotate")))
        assertTrue(entries.all { AuditRules.actionText(it.action) != null })
        val file = File(dirs[0], AuditRules.exportName(vaultName, "csv", "2026-10-27"))
        val bytes = phone.auditExport(AuditScope.VAULT, AuditRules.Filters(AuditRules.Preset.E2EE).exportQuery(System.currentTimeMillis()), "csv", file.path, vaultId!!)
        val csv = file.readText()
        assertTrue(csv.startsWith("id,time,action,actor"))
        assertTrue(csv.contains("e2ee.enable"))
        log += "vault audit (E2EE preset): ${actions.sorted()}; exported ${file.name} ($bytes bytes)"

    }

    private suspend fun sso(r: Run) = with(r) {
        // ----- SSO through a local Dex -----
        if (dex != null) {
            val team = peer.createTeam("5c-sso-$tag")
            teamId = team.id
            val domain = "sso5c.test"
            val settings = SsoSettings(
                enabled = true, preset = "generic", issuer = dex, tenant = null, clientId = "termoak", scopes = listOf("openid", "email", "profile"),
                emailClaim = null, requireEmailVerified = false, enforce = false, ownerPasswordAllowed = true, autoCreate = true, autoJoin = true,
                satisfies2fa = false,
            )
            peer.setTeamSso(team.id, settings, env("TERMOAK_LIVE_DEX_SECRET"))
            peer.addTeamDomain(team.id, domain)
            peer.json(HttpMethod.POST, "/api/v1/admin/teams/${team.id}/domains/$domain/verify", "{}")
            assertTrue(SsoRules.canEnforce(peer.teamDomains(team.id)))
            // A new person signs in from the phone: Custom Tab → termoak://sso.
            val nora = "nora@$domain"
            val hint = ssoCore.ssoDiscover(choice, nora)
            assertTrue(hint.sso)
            assertEquals("5c-sso-$tag", hint.teamName)
            val flow = ssoCore.ssoStart(choice, nora, null, null)
            assertEquals(SsoRules.REDIRECT, flow.redirectUri)
            val callback = browse(flow.authorizeUrl, nora)
            assertTrue(callback, SsoRules.isCallback(callback))
            assertTrue(SsoBrowser.deliver(callback))
            val noraInfo = ssoCore.ssoComplete(flow, callback, null)
            ssoAccount = noraInfo.id
            assertEquals(nora, noraInfo.email)
            log += "SSO sign-in from the phone through termoak://sso: $nora created and in team ${hint.teamName}"
            // The phone's account (a member of the team) links its identity (POST /me/sso/link, the app's own PKCE).
            peer.addTeamMember(team.id, memberEmail, com.termoak.ffi.TeamRole.MEMBER)
            val state = SsoRules.token()
            val verifier = SsoRules.token()
            val v = JSONObject(phone.json(HttpMethod.POST, "/api/v1/me/sso/link", SsoRules.linkBody(team.id, state, verifier, "5c test")))
            val back = browse(v.getString("authorize_url"), memberEmail)
            assertEquals(SsoRules.LinkResult.Linked, SsoRules.linkResult(back, state))
            val ids = LinkedIdentityParse.parse(phone.json(HttpMethod.GET, "/api/v1/me/sso"))
            assertEquals(listOf("5c-sso-$tag"), ids)
            phone.json(HttpMethod.DELETE, "/api/v1/me/sso/${team.id}")
            log += "identity linked from the phone and unlinked"
            // Enforcement: a password sign-in of the domain is refused naming the team.
            peer.setTeamSso(team.id, settings.copy(enforce = true), null)
            val enforced = runCatching { TermoakCore(File(dirs[3], "x").apply { mkdirs() }.path, generateVaultKey()).signIn(choice, nora, "whatever-password") }.exceptionOrNull()
            assertNotNull(enforced)
            assertEquals(null to "5c-sso-$tag", E2eeTexts.ssoRequired(enforced!!)?.let { null to it.second })
            assertTrue(E2eeTexts.of(enforced)!!.toString(), (E2eeTexts.of(enforced) as UiText.Res).id == R.string.error_sso_required_team)
            log += "enforced: password sign-in refused with sso_required (${E2eeTexts.ssoRequired(enforced)?.second})"
        }

    }

    private suspend fun off(r: Run) = with(r) {
        // ----- Off again -----
        phone.syncNow()
        phone.e2eeDisableVault(vaultId!!)
        phone.syncNow()
        assertNull(phoneCore.vaults(ItemFilter(listOf(phoneInfo.id), listOf(vaultId!!), false)).first().e2ee)
        log += "E2EE turned off: the server keeps the vault key again"
    }

    private suspend fun cleanup(r: Run) = with(r) {
        println("live 5c:\n" + log.joinToString("\n") { "  - $it" })
        sessions.forEach { runCatching { phone.closeServerSession(it) } }
        vaultId?.let { id -> runCatching { phone.deleteVault(id, vaultName) }.onFailure { println("live: vault not deleted: ${it.message}") } }
        runCatching {
            val known = JSONArray(phone.json(HttpMethod.GET, "/api/v1/known-hosts"))
            for (i in 0 until known.length()) {
                val k = known.getJSONObject(i)
                if ((k.optString("host").ifEmpty { k.optString("hostname") }) == sshIp) phone.json(HttpMethod.DELETE, "/api/v1/known-hosts/${k.getString("id")}")
            }
        }
        runCatching { phone.json(HttpMethod.DELETE, "/api/v1/me/sso/${teamId ?: "none"}") }
        teamId?.let { id -> runCatching { peer.deleteTeam(id) }.onFailure { println("live: team not deleted: ${it.message}") } }
        ssoAccount?.let {
            // The SSO-made account goes (after its team, so no enforcement): the administrator gives it a password,
            // it signs in with it and deletes itself.
            runCatching {
                val users = JSONObject("{\"u\":" + peer.json(HttpMethod.GET, "/api/v1/admin/users") + "}").get("u")
                val arr = if (users is JSONArray) users else (users as JSONObject).getJSONArray("users")
                val u = (0 until arr.length()).map { arr.getJSONObject(it) }.first { it.optString("email").endsWith("@sso5c.test") }
                val pw = "Tmp-" + SsoRules.token().take(20)
                peer.json(HttpMethod.POST, "/api/v1/admin/users/${u.getString("id")}/password", JSONObject().put("password", pw).toString())
                val c = TermoakCore(File(dirs[3], "del").apply { mkdirs() }.path, generateVaultKey())
                val info = c.signIn(choice, u.getString("email"), pw)
                c.account(info.id).json(HttpMethod.DELETE, "/api/v1/me", JSONObject().put("password", pw).toString())
            }.onFailure { println("live: SSO account not deleted: ${it.message}") }
        }
        runCatching { phone.json(HttpMethod.DELETE, "/api/v1/me/e2ee?confirm=reset") }.onFailure { println("live: phone e2ee reset: ${it.message}") }
        runCatching { peer.json(HttpMethod.DELETE, "/api/v1/me/e2ee?confirm=reset") }.onFailure { println("live: peer e2ee reset: ${it.message}") }
        phone2Id?.let { runCatching { phone2Core.signOutAccount(it, true) } }
        ssoAccount?.let { runCatching { ssoCore.signOutAccount(it, true) } }
        runCatching { phoneCore.signOutAccount(phoneInfo.id, true) }
        runCatching { peerCore.signOutAccount(peerInfo.id, true) }
        E2eeBook.reset()
        E2eeBook.apiOf = { null }
        dirs.forEach { it.deleteRecursively() }
    }

    /** The team names of `GET /me/sso`. */
    private object LinkedIdentityParse {
        fun parse(json: String): List<String> = JSONArray(json).let { a -> (0 until a.length()).map { a.getJSONObject(it).optString("team_name") } }
    }
}
