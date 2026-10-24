package com.termoak.app.data

import android.app.Application
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import com.termoak.app.R
import com.termoak.app.toUiText
import com.termoak.ffi.AccessRequestStatus
import com.termoak.ffi.AccessTargetKind
import com.termoak.ffi.AccessRequestInfo
import com.termoak.ffi.TermoakException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

/**
 * Teams security texts: every error code of TEAMS-SECURITY.md §7 and every
 * close reason has the app's own text in English and Spanish (never the
 * server's English message), also when only the text arrives; durations and
 * notices.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AccessTextsTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val res get() = app.resources

    private fun resIn(lang: String) = app.createConfigurationContext(Configuration(res.configuration).apply { setLocale(Locale.forLanguageTag(lang)) }).resources

    @Before
    fun setUp() = FakeApiErrors.install()

    @Test
    fun everyCodeAndReasonHasEnglishAndSpanish() {
        val en = resIn("en")
        val es = resIn("es")
        val codes = listOf(
            "connect_only", "secret_reveal_forbidden", "access_approval_required", "access_needs_server", "access_request_exists",
            "access_request_not_needed", "access_request_closed", "access_duration_too_long", "self_approval_forbidden", "not_an_approver",
            "ca_not_configured", "ca_exists", "certificate_key_not_owned", "certificate_key_revoked", "certificate_proof_invalid",
            "certificate_no_principals", "team_2fa_required", "policy_requires_2fa", "ai_forbidden_by_policy", "networks_forbidden_by_policy",
            "recording_required",
        )
        for (c in codes) {
            val id = AccessTexts.ERRORS[c] ?: error("no text for $c")
            assertTrue(c, en.getString(id).isNotBlank())
            assertNotEquals(c, en.getString(id), es.getString(id))
        }
        for ((reason, id) in AccessTexts.CLOSE_REASONS) {
            assertNotEquals(reason, en.getString(id), es.getString(id))
            assertEquals(id, (AccessTexts.serverText(reason) as com.termoak.app.UiText.Res).id)
        }
        assertEquals(AccessRules.CLOSE_REASONS.toSet(), AccessTexts.CLOSE_REASONS.keys)
    }

    @Test
    fun errorsByCodeAndByText() {
        // By code (the server's, with details).
        val e = TermoakException.Server(FakeApiErrors.with(403, "access_approval_required", "\"web1\" needs an approved access window: request access", """{"host_id":"h1","max_minutes":90}"""))
        assertEquals(res.getString(R.string.error_access_approval_required), e.toUiText(R.string.term_connect_failed).resolve(res))
        val need = AccessTexts.needOf(e, null)!!
        assertEquals(90, need.maxMinutes)
        assertEquals("h1", need.hostId)
        // The engine's own rule (status 0).
        val c = TermoakException.Vault(FakeApiErrors.device("Connect only members connect through the server or with team certificates", "connect_only"))
        assertEquals(res.getString(R.string.error_connect_only), c.toUiText(R.string.term_connect_failed).resolve(res))
        assertNull(AccessTexts.needOf(c, "h1"))
        // Only the text (a server session's reason, /exec).
        assertEquals(R.string.error_access_approval_required, (AccessTexts.serverText("this host needs an approved access window: request access") as com.termoak.app.UiText.Res).id)
        assertEquals(R.string.error_ca_not_configured, (AccessTexts.serverText("there is no certificate authority for this vault") as com.termoak.app.UiText.Res).id)
        assertEquals(R.string.error_networks_forbidden_by_policy, (AccessTexts.serverText("the team does not allow private networks in this vault") as com.termoak.app.UiText.Res).id)
        assertNull(AccessTexts.serverText("connection refused"))
        assertNull(AccessTexts.serverText(null))
    }

    @Test
    fun durationsTimesAndNotices() {
        val en = resIn("en")
        assertEquals("15 min", AccessTexts.duration(en, 15))
        assertEquals("2 h", AccessTexts.duration(en, 120))
        assertEquals("1 h 30 min", AccessTexts.duration(en, 90))
        assertEquals("3 days", AccessTexts.duration(en, 4320))
        assertEquals("1 day", AccessTexts.duration(en, 1440))
        assertEquals("45 s", AccessTexts.left(en, 45))
        assertEquals("42 min", AccessTexts.left(en, 41 * 60 + 1))
        assertEquals("1 h 05 min", AccessTexts.left(en, 65 * 60))
        assertEquals("3 días", AccessTexts.duration(resIn("es"), 4320))
        val r = AccessRequestInfo(
            "r", "v", AccessTargetKind.HOST, "h", "web1", "u", "Ana", "ana@example.com", "deploy", 60u, AccessRequestStatus.DENIED,
            0, 0, "b", "Bea", 1, "not today", null, null, false, false, false,
        )
        val ev = AccessRules.AccessEvent("denied", r)
        assertEquals("Your request for web1 was denied: not today", AccessTexts.notice(en, AccessRules.Notice.DENIED_REASON, ev))
        assertEquals("Ana asks for access to web1 for 1 h", AccessTexts.notice(en, AccessRules.Notice.CREATED, ev))
        assertEquals("Bea ha terminado tu acceso a web1", AccessTexts.notice(resIn("es"), AccessRules.Notice.REVOKED, ev))
    }
}
