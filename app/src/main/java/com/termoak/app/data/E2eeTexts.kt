package com.termoak.app.data

import com.termoak.app.R
import com.termoak.app.UiText
import com.termoak.ffi.TermoakException
import org.json.JSONObject

/**
 * The texts of end-to-end encrypted vaults, single sign-on and the extended
 * audit (0.7 phase 5c, docs-next/E2EE-SSO-AUDIT.md §6): every new error
 * code is always the app's text, never the server's English message; a
 * refused recovery phrase says why (`detail.reason`), `sso_required` names
 * the team.
 */
object E2eeTexts {
    /** Error code → its text. */
    val ERRORS: Map<String, Int> = mapOf(
        "e2ee_locked" to R.string.error_e2ee_locked,
        "e2ee_unlock_required" to R.string.error_e2ee_unlock_required,
        "e2ee_vault" to R.string.error_e2ee_vault,
        "e2ee_plaintext_rejected" to R.string.error_e2ee_plaintext_rejected,
        "e2ee_key_outdated" to R.string.error_e2ee_key_outdated,
        "e2ee_not_enabled" to R.string.error_e2ee_not_enabled,
        "e2ee_already_enabled" to R.string.error_e2ee_already_enabled,
        "e2ee_incomplete" to R.string.error_e2ee_incomplete,
        "e2ee_stale" to R.string.error_e2ee_stale,
        "e2ee_wrap_invalid" to R.string.error_e2ee_wrap_invalid,
        "e2ee_not_set_up" to R.string.error_e2ee_not_set_up,
        "e2ee_key_exists" to R.string.error_e2ee_key_exists,
        "e2ee_key_mismatch" to R.string.error_e2ee_key_mismatch,
        "e2ee_client_required" to R.string.error_e2ee_client_required,
        "e2ee_pairing_closed" to R.string.error_e2ee_pairing_closed,
        "e2ee_passphrase_invalid" to R.string.error_e2ee_passphrase_invalid,
        "e2ee_passphrase_too_short" to R.string.error_e2ee_passphrase_too_short,
        "e2ee_recovery_key_invalid" to R.string.error_e2ee_recovery_key_invalid,
        "e2ee_decrypt_failed" to R.string.error_e2ee_decrypt_failed,
        "sso_required" to R.string.error_sso_required,
        "sso_not_configured" to R.string.error_sso_not_configured,
        "sso_issuer_invalid" to R.string.error_sso_issuer_invalid,
        "sso_redirect_invalid" to R.string.error_sso_redirect_invalid,
        "sso_pkce_invalid" to R.string.error_sso_pkce_invalid,
        "sso_state_invalid" to R.string.error_sso_state_invalid,
        "sso_code_invalid" to R.string.error_sso_code_invalid,
        "sso_failed" to R.string.error_sso_failed,
        "sso_email_missing" to R.string.error_sso_email_missing,
        "sso_email_unverified" to R.string.error_sso_email_unverified,
        "sso_account_not_found" to R.string.error_sso_account_not_found,
        "sso_account_not_linked" to R.string.error_sso_account_not_linked,
        "sso_identity_taken" to R.string.error_sso_identity_taken,
        "sso_domain_taken" to R.string.error_sso_domain_taken,
        "sso_domain_unverified" to R.string.error_sso_domain_unverified,
        "sso_only_account" to R.string.error_sso_only_account,
        "audit_sink_invalid" to R.string.error_audit_sink_invalid,
        "invalid_format" to R.string.error_invalid_format,
        "invalid_retention" to R.string.error_invalid_retention,
    )

    /** The reasons a recovery phrase is refused (`detail.reason`, the core's check). */
    val RECOVERY_REASONS = listOf("word_count", "unknown_word", "checksum", "old_format")

    /** Words of a recovery phrase. */
    const val RECOVERY_WORDS = 24

    /** Why a recovery phrase is refused, in words ([word]: 1-based position; [words]: how many were typed). */
    fun recoveryReason(reason: String?, word: Int, words: Int): UiText = when (reason) {
        "word_count" -> UiText.Res(R.string.e2ee_recovery_reason_word_count, listOf(RECOVERY_WORDS.toString(), words.toString()))
        "unknown_word" -> UiText.Res(R.string.e2ee_recovery_reason_unknown_word, listOf(word.toString()))
        "checksum" -> UiText.Res(R.string.e2ee_recovery_reason_checksum)
        "old_format" -> UiText.Res(R.string.e2ee_recovery_reason_old_format)
        else -> UiText.Res(R.string.error_e2ee_recovery_key_invalid)
    }

    /** The text of a code with what its details add (`null`: not one of these codes). */
    fun error(code: String?, details: JSONObject = JSONObject()): UiText? {
        val c = code ?: return null
        if (c == "e2ee_recovery_key_invalid" && details.has("reason")) {
            return recoveryReason(details.optString("reason"), details.optInt("word"), details.optInt("words"))
        }
        if (c == "sso_required") {
            val team = details.optString("team_name").takeIf { it.isNotBlank() && !details.isNull("team_name") }
            if (team != null) return UiText.Res(R.string.error_sso_required_team, listOf(team))
        }
        return ERRORS[c]?.let { UiText.Res(it) }
    }

    /** The error's text when it is one of these codes (also the bare variants without details). */
    fun of(e: Throwable): UiText? {
        val info = ApiErrors.of(e)
        error(info?.code, ApiErrors.details(info))?.let { return it }
        return when (e) {
            is TermoakException.E2eeLocked -> UiText.Res(R.string.error_e2ee_locked)
            is TermoakException.SsoRequired -> UiText.Res(R.string.error_sso_required)
            else -> null
        }
    }

    /** The code of an error, `null` without one. */
    fun codeOf(e: Throwable): String? = ApiErrors.of(e)?.code

    /** A local connection or a server session needs this device's E2EE key, or the host's credentials sent for it. */
    fun isLocked(e: Throwable): Boolean =
        e is TermoakException.E2eeLocked || codeOf(e).let { it == "e2ee_locked" || it == "e2ee_unlock_required" }

    /** The server can only use the host's credentials if this device sends them (a server session, a host test). */
    fun needsUnlock(e: Throwable): Boolean = codeOf(e) == "e2ee_unlock_required"

    /** A password sign-in refused because the team requires single sign-on: the team (id, name) if the server said. */
    fun ssoRequired(e: Throwable): Pair<String?, String?>? {
        val info = ApiErrors.of(e)
        if (e !is TermoakException.SsoRequired && info?.code != "sso_required") return null
        val d = ApiErrors.details(info)
        fun s(k: String) = d.optString(k).takeIf { it.isNotBlank() && !d.isNull(k) }
        return s("team_id") to s("team_name")
    }

    /** A server's English text of these codes, where only the text travels (a test result, a session's close reason). */
    fun serverText(text: String?): UiText? {
        val t = text?.trim().orEmpty()
        if (t.isEmpty()) return null
        ERRORS[t]?.let { return UiText.Res(it) }
        val low = t.lowercase()
        return when {
            low.contains("end-to-end encrypted") && low.contains("unlock") -> UiText.Res(R.string.error_e2ee_unlock_required)
            low.contains("e2ee_unlock_required") -> UiText.Res(R.string.error_e2ee_unlock_required)
            low.contains("e2ee_locked") -> UiText.Res(R.string.error_e2ee_locked)
            else -> null
        }
    }
}
