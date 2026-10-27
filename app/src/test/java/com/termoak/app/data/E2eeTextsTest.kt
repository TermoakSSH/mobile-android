package com.termoak.app.data

import android.app.Application
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import com.termoak.app.R
import com.termoak.app.UiText
import com.termoak.app.toUiText
import com.termoak.ffi.TermoakException
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
 * E2EE, SSO and audit texts (0.7 phase 5c): every new error code of
 * E2EE-SSO-AUDIT.md §6 has the app's own text in English and Spanish (never
 * the server's English), the recovery phrase's refusal reasons, `sso_required`
 * naming the team, every audit action and preset in both languages.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class E2eeTextsTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val res get() = app.resources

    private fun resIn(lang: String) = app.createConfigurationContext(Configuration(res.configuration).apply { setLocale(Locale.forLanguageTag(lang)) }).resources

    @Before
    fun setUp() = FakeApiErrors.install()

    /** §6 of the design doc. */
    private val codes = listOf(
        "e2ee_locked", "e2ee_unlock_required", "e2ee_vault", "e2ee_plaintext_rejected", "e2ee_key_outdated", "e2ee_not_enabled",
        "e2ee_already_enabled", "e2ee_incomplete", "e2ee_stale", "e2ee_wrap_invalid", "e2ee_not_set_up", "e2ee_key_exists",
        "e2ee_key_mismatch", "e2ee_client_required", "e2ee_pairing_closed", "e2ee_passphrase_invalid", "e2ee_passphrase_too_short",
        "e2ee_recovery_key_invalid", "e2ee_decrypt_failed", "sso_required", "sso_not_configured", "sso_issuer_invalid",
        "sso_redirect_invalid", "sso_pkce_invalid", "sso_state_invalid", "sso_code_invalid", "sso_failed", "sso_email_missing",
        "sso_email_unverified", "sso_account_not_found", "sso_account_not_linked", "sso_identity_taken", "sso_domain_taken",
        "sso_domain_unverified", "sso_only_account", "audit_sink_invalid", "invalid_format", "invalid_retention",
    )

    @Test
    fun everyCodeHasEnglishAndSpanish() {
        val en = resIn("en")
        val es = resIn("es")
        assertEquals(codes.toSet(), E2eeTexts.ERRORS.keys)
        for (c in codes) {
            val id = E2eeTexts.ERRORS.getValue(c)
            assertTrue(c, en.getString(id).isNotBlank())
            assertNotEquals(c, en.getString(id), es.getString(id))
            // By code, from the engine's error: always the app's text.
            val e = TermoakException.Server(FakeApiErrors.with(409, c, "some English text"))
            assertEquals(c, UiText.Res(id), e.toUiText(R.string.login_failed))
        }
        for (r in E2eeTexts.RECOVERY_REASONS) {
            val t = E2eeTexts.recoveryReason(r, 3, 12)
            assertNotEquals(r, t.resolve(en), t.resolve(es))
            assertFalse(t.resolve(en).contains("%"))
        }
        assertTrue(E2eeTexts.recoveryReason("word_count", 0, 12).resolve(en).contains("12"))
        assertTrue(E2eeTexts.recoveryReason("unknown_word", 5, 24).resolve(es).contains("5"))
    }

    @Test
    fun detailsAddWhatTheyKnow() {
        val en = resIn("en")
        val refused = TermoakException.E2eeLocked(FakeApiErrors.with(400, "e2ee_recovery_key_invalid", "checksum", """{"reason":"unknown_word","word":7,"words":24}"""))
        assertTrue(refused.toUiText(R.string.login_failed).resolve(en).contains("7"))
        val sso = TermoakException.SsoRequired(FakeApiErrors.with(403, "sso_required", "Corp signs in…", """{"team_id":"t1","team_name":"Corp"}"""))
        assertEquals("Corp signs in through single sign-on: use “Continue with Corp”", sso.toUiText(R.string.login_failed).resolve(en))
        assertEquals("t1" to "Corp", E2eeTexts.ssoRequired(sso))
        assertNull(E2eeTexts.ssoRequired(TermoakException.Server(FakeApiErrors.with(401, "invalid_credentials", "no"))))
        // Without details: the plain texts.
        assertEquals(UiText.Res(R.string.error_sso_required), E2eeTexts.error("sso_required", JSONObject()))
        assertEquals(UiText.Res(R.string.error_e2ee_locked), E2eeTexts.of(TermoakException.E2eeLocked("x")))
        assertEquals(UiText.Res(R.string.error_sso_required), E2eeTexts.of(TermoakException.SsoRequired("x")))
    }

    @Test
    fun lockedAndUnlockRequired() {
        val unlockRequired = TermoakException.E2eeLocked(FakeApiErrors.with(409, "e2ee_unlock_required", "send the credentials"))
        assertTrue(E2eeTexts.needsUnlock(unlockRequired))
        assertTrue(E2eeTexts.isLocked(unlockRequired))
        val locked = TermoakException.E2eeLocked(FakeApiErrors.device("this host's credentials are end-to-end encrypted: unlock E2EE on this device", "e2ee_locked"))
        assertFalse(E2eeTexts.needsUnlock(locked))
        assertTrue(E2eeTexts.isLocked(locked))
        assertFalse(E2eeTexts.isLocked(TermoakException.Server(FakeApiErrors.with(409, "access_approval_required", "x"))))
        assertEquals(UiText.Res(R.string.error_e2ee_unlock_required), E2eeTexts.serverText("e2ee_unlock_required"))
        assertNull(E2eeTexts.serverText("connection refused"))
    }

    @Test
    fun auditActionsAndPresetsInBothLanguages() {
        val en = resIn("en")
        val es = resIn("es")
        for ((action, id) in AuditRules.ACTIONS) {
            assertTrue(action, en.getString(id).isNotBlank())
            assertTrue(action, es.getString(id).isNotBlank())
        }
        // Every action of §4.6 that the phone can meet is in words.
        listOf(
            "e2ee.setup", "e2ee.seal", "e2ee.passphrase_removed", "e2ee.key_rotate", "e2ee.reset", "e2ee.pairing_request",
            "e2ee.pairing_approve", "e2ee.pairing_deny", "e2ee.enable", "e2ee.rotate", "e2ee.disable", "e2ee.wraps_add", "e2ee.unlock",
            "sso.config", "sso.domain_add", "sso.domain_verify", "sso.domain_check_failed", "sso.domain_remove", "sso.login",
            "sso.login_failed", "sso.link", "sso.unlink", "sso.user_created", "auth.login_refused", "audit.export", "audit.settings",
            "audit.sink_create", "audit.sink_update", "audit.sink_delete", "auth.login", "secret.reveal", "session.open",
        ).forEach { assertTrue(it, AuditRules.actionText(it) != null) }
        assertNull(AuditRules.actionText("something.new"))
        for (p in AuditRules.Preset.entries) assertTrue(p.name, es.getString(p.label).isNotBlank())
        assertNotEquals(en.getString(AuditRules.Preset.ALL.label), es.getString(AuditRules.Preset.ALL.label))
        for (r in AuditRules.Range.entries) assertNotEquals(r.name, en.getString(r.label), es.getString(r.label))
    }
}
