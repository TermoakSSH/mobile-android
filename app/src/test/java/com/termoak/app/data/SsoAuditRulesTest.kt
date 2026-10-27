package com.termoak.app.data

import android.app.Application
import com.termoak.app.R
import com.termoak.ffi.AuditEvent
import com.termoak.ffi.AuditSinkInfo
import com.termoak.ffi.SsoSettings
import com.termoak.ffi.TeamDomainInfo
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Single sign-on and audit rules of the app (0.7 phase 5c): callbacks, PKCE, the team form, domains, filters, exports, sinks. */
@RunWith(RobolectricTestRunner::class) // org.json is Android's.
@Config(sdk = [34], application = Application::class)
class SsoAuditRulesTest {
    @Test
    fun callbacks() {
        assertTrue(SsoRules.isCallback("termoak://sso?code=abc&state=xyz"))
        assertTrue(SsoRules.isCallback("termoak://sso"))
        assertFalse(SsoRules.isCallback("termoak://join/abc"))
        assertFalse(SsoRules.isCallback("https://evil.example/termoak://sso?code=1"))
        assertFalse(SsoRules.isCallback(null))
        val q = SsoRules.query("termoak://sso?code=a%2Bb&state=s1&error_description=went%20wrong")
        assertEquals("a+b", q["code"])
        assertEquals("went wrong", q["error_description"])
        assertEquals(SsoRules.LinkResult.Linked, SsoRules.linkResult("termoak://sso?code=c&state=s1", "s1"))
        assertEquals(SsoRules.LinkResult.Failed("sso_state_invalid"), SsoRules.linkResult("termoak://sso?code=c&state=other", "s1"))
        assertEquals(SsoRules.LinkResult.Failed("sso_identity_taken"), SsoRules.linkResult("termoak://sso?error=sso_identity_taken&state=s1", "s1"))
        assertEquals(SsoRules.LinkResult.Failed("sso_failed"), SsoRules.linkResult("termoak://sso?state=s1", "s1"))
    }

    @Test
    fun pkceOfALink() {
        // RFC 7636 appendix B.
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", SsoRules.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
        val a = SsoRules.token()
        assertEquals(43, a.length)
        assertTrue(a != SsoRules.token())
        val body = JSONObject(SsoRules.linkBody("t1", "s", "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk", "Pixel 9"))
        assertEquals("termoak://sso", body.getString("redirect_uri"))
        assertEquals("S256", body.getString("code_challenge_method"))
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", body.getString("code_challenge"))
        assertFalse(body.toString().contains("dBjftJeZ"))
        assertEquals("android", body.getString("platform"))
    }

    @Test
    fun teamForm() {
        val f = SsoRules.Form.newFor("microsoft")
        assertFalse(f.requireEmailVerified)
        assertEquals(SsoRules.Checked.Missing(R.string.sso_form_client_id_required), SsoRules.check(f, hasSecret = false, canEnforce = false))
        assertEquals(SsoRules.Checked.Missing(R.string.sso_form_tenant_required), SsoRules.check(f.copy(clientId = "c"), false, false))
        val ok = SsoRules.check(f.copy(clientId = " c ", tenant = "tid", clientSecret = "s3", enforce = true, scopes = "offline_access, email"), false, canEnforce = false)
        ok as SsoRules.Checked.Ok
        assertEquals("c", ok.settings.clientId)
        assertEquals("tid", ok.settings.tenant)
        assertEquals("", ok.settings.issuer)
        assertEquals(listOf("openid", "email", "profile", "offline_access"), ok.settings.scopes)
        // Enforcement needs a verified domain.
        assertFalse(ok.settings.enforce)
        assertEquals("s3", ok.secret)
        assertEquals(SsoRules.Checked.Missing(R.string.sso_form_issuer_required), SsoRules.check(SsoRules.Form.newFor("keycloak").copy(clientId = "c"), false, true))
        assertEquals(SsoRules.Checked.Missing(R.string.sso_form_secret_required_google), SsoRules.check(SsoRules.Form.newFor("google").copy(clientId = "c"), false, true))
        assertTrue(SsoRules.check(SsoRules.Form.newFor("google").copy(clientId = "c"), hasSecret = true, canEnforce = true) is SsoRules.Checked.Ok)
        // Back from the server: the extra scopes only.
        val s = SsoSettings(true, "keycloak", "https://kc/realms/x", null, "cid", listOf("openid", "email", "profile", "groups"), null, true, true, true, true, true, false, true, "https://t/api/v1/auth/sso/callback")
        val back = SsoRules.Form.of(s)
        assertEquals("groups", back.scopes)
        assertEquals("", back.clientSecret)
        assertEquals(SsoRules.TeamState.ENFORCED, SsoRules.teamState(s))
        assertEquals(SsoRules.TeamState.NOT_SET_UP, SsoRules.teamState(null))
    }

    @Test
    fun domains() {
        val d = TeamDomainInfo("corp.example", "_termoak-challenge.corp.example", "termoak-verify=abc", false, null, "no TXT record")
        assertEquals("_termoak-challenge.corp.example. 300 IN TXT \"termoak-verify=abc\"", SsoRules.zoneLine(d))
        assertEquals(SsoRules.DomainState.FAILED, SsoRules.domainState(d))
        assertEquals(SsoRules.DomainState.PENDING, SsoRules.domainState(d.copy(lastError = null)))
        assertEquals(SsoRules.DomainState.VERIFIED, SsoRules.domainState(d.copy(verified = true, verifiedAt = 1)))
        assertFalse(SsoRules.canEnforce(listOf(d)))
        assertTrue(SsoRules.canEnforce(listOf(d, d.copy(verified = true))))
        assertTrue(SsoRules.domainValid("corp.example"))
        assertTrue(SsoRules.domainValid("sub.corp-1.example."))
        assertFalse(SsoRules.domainValid("localhost"))
        assertFalse(SsoRules.domainValid("-bad.example"))
        assertFalse(SsoRules.domainValid("a b.example"))
    }

    @Test
    fun auditFilters() {
        val now = 1_800_000_000_000L
        val q = AuditRules.Filters(AuditRules.Preset.SSO, actions = "vault.*, e2ee.unlock", actor = " ana@example.com ", range = AuditRules.Range.WEEK, text = " db ").query(now)
        assertEquals("sso.*,auth.login_refused,vault.*,e2ee.unlock", q.action)
        assertEquals("ana@example.com", q.actor)
        assertEquals(now - 7 * 86_400_000L, q.since)
        assertEquals("db", q.text)
        assertEquals(50u, q.limit)
        assertNull(q.before)
        val all = AuditRules.Filters().query(now, before = 99)
        assertNull(all.action)
        assertNull(all.since)
        assertEquals(99L, all.before)
        val export = AuditRules.Filters(AuditRules.Preset.E2EE).exportQuery(now)
        assertEquals("e2ee.*", export.action)
        assertNull(export.limit)
        assertFalse(AuditRules.Filters().active)
        assertTrue(AuditRules.Filters(range = AuditRules.Range.DAY).active)
        val page = (1..50).map { AuditEvent(it.toLong(), "o", "a", "x", null, "{}", 0) }
        assertEquals(50L, AuditRules.nextBefore(page, 50))
        assertNull(AuditRules.nextBefore(page.take(10), 10))
        assertEquals("termoak-audit-ops-team-2026-10-27.csv", AuditRules.exportName("Ops Team", "csv", "2026-10-27"))
        assertEquals("termoak-audit-audit-2026-10-27.json", AuditRules.exportName("***", "json", "2026-10-27"))
        assertEquals("text/csv", AuditRules.mime("csv"))
        assertEquals("device: Pixel · reason: lost", AuditRules.detailSummary("""{"device":"Pixel","none":null,"list":[],"reason":"lost"}"""))
        assertEquals(10, AuditRules.detailSummary("""{"k":"${"x".repeat(50)}"}""", 10).length - 1)
        assertEquals("You", AuditRules.actor("user:u1", mapOf("u1" to "You")))
        assertEquals("ai:task-1", AuditRules.actor("ai:task-1", mapOf("u1" to "You")))
    }

    @Test
    fun auditRetentionFeaturesAndSinks() {
        assertEquals(AuditRules.Retention.Ok(null), AuditRules.parseRetention(" ", 3650))
        assertEquals(AuditRules.Retention.Ok(90u), AuditRules.parseRetention("90", 3650))
        assertEquals(AuditRules.Retention.Invalid, AuditRules.parseRetention("0", 3650))
        assertEquals(AuditRules.Retention.Invalid, AuditRules.parseRetention("4000", 3650))
        val f = AuditRules.features("""{"features":{"audit_v2":{"filters":true,"export":["csv","json","ndjson"],"retention":true,"sinks":["webhook","syslog"]}}}""")!!
        assertEquals(listOf("csv", "json", "ndjson"), f.exports)
        assertEquals(listOf("webhook", "syslog"), f.sinks)
        assertTrue(f.retention)
        assertNull(AuditRules.features("""{"features":{}}"""))
        val r = AuditRules.retentionView("""{"retention_days":null,"default_retention_days":365,"max_retention_days":3650}""")!!
        assertNull(r.days)
        assertEquals(365, r.defaultDays)
        assertEquals(30, AuditRules.retentionView("""{"retention_days":30,"default_retention_days":365,"max_retention_days":3650}""")!!.days)
        val sink = AuditSinkInfo("s1", "syslog", "SIEM", null, "siem.example", null, "tls", emptyList(), true, 0, null, null)
        assertEquals("syslog+tls://siem.example:6514", AuditRules.sinkTarget(sink))
        assertEquals("syslog://siem.example:514", AuditRules.sinkTarget(sink.copy(transport = "tcp")))
        assertEquals("https://hook.example/x", AuditRules.sinkTarget(sink.copy(kind = "webhook", url = "https://hook.example/x")))
        assertEquals(AuditRules.SinkState.WAITING, AuditRules.sinkState(sink))
        assertEquals(AuditRules.SinkState.DELIVERED, AuditRules.sinkState(sink.copy(lastSuccessAt = 1)))
        assertEquals(AuditRules.SinkState.FAILING, AuditRules.sinkState(sink.copy(failures = 3, lastError = "refused")))
        assertEquals(AuditRules.SinkState.PAUSED, AuditRules.sinkState(sink.copy(enabled = false, failures = 3)))
    }
}
