package com.termoak.app.data

import com.termoak.app.R
import com.termoak.ffi.SsoSettings
import com.termoak.ffi.TeamDomainInfo
import java.net.URI
import java.net.URLDecoder
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Single sign-on in the app (0.7 phase 5c, docs-next/E2EE-SSO-AUDIT.md §3):
 * the callback `termoak://sso` (Custom Tab, RFC 8252 §7.1), the team's
 * provider presets and form, domains and their TXT records, and the PKCE of
 * linking an identity (`POST /me/sso/link`, which the engine has no call
 * for: the server links it when the browser comes back).
 */
object SsoRules {
    /** Where the browser comes back to (the manifest's intent filter). */
    const val REDIRECT = "termoak://sso"

    /** The callback of a sign-in or a link, from an intent's data. */
    fun isCallback(uri: String?): Boolean {
        val u = uri?.trim() ?: return false
        return u.startsWith("$REDIRECT?") || u == REDIRECT || u.startsWith("$REDIRECT/")
    }

    /** The query of a callback URL (`code`, `state`, `error`, `error_description`). */
    fun query(url: String): Map<String, String> {
        val q = runCatching { URI(url.trim()).rawQuery }.getOrNull() ?: return emptyMap()
        return q.split('&').filter { it.isNotEmpty() }.associate { part ->
            val i = part.indexOf('=')
            val k = if (i < 0) part else part.substring(0, i)
            val v = if (i < 0) "" else part.substring(i + 1)
            URLDecoder.decode(k, "UTF-8") to URLDecoder.decode(v, "UTF-8")
        }
    }

    /** What came back for a link: done, or the error code it names (the state must be ours). */
    sealed interface LinkResult {
        data object Linked : LinkResult
        data class Failed(val code: String) : LinkResult
    }

    fun linkResult(callback: String, state: String): LinkResult {
        val q = query(callback)
        if (q["state"] != state) return LinkResult.Failed("sso_state_invalid")
        q["error"]?.takeIf { it.isNotEmpty() }?.let { return LinkResult.Failed(it) }
        return if (q["code"].isNullOrEmpty()) LinkResult.Failed("sso_failed") else LinkResult.Linked
    }

    // ----- PKCE of a link -----

    private val random = SecureRandom()

    private fun b64url(bytes: ByteArray): String = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    /** A random token (32 bytes, base64url). */
    fun token(): String = ByteArray(32).also { random.nextBytes(it) }.let(::b64url)

    /** S256 challenge of a verifier. */
    fun challenge(verifier: String): String = b64url(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))

    /** The body of `POST /me/sso/link`. */
    fun linkBody(teamId: String, state: String, verifier: String, deviceName: String): String =
        org.json.JSONObject()
            .put("team_id", teamId)
            .put("redirect_uri", REDIRECT)
            .put("state", state)
            .put("code_challenge", challenge(verifier))
            .put("code_challenge_method", "S256")
            .put("device_name", deviceName)
            .put("platform", "android")
            .toString()

    // ----- Team settings -----

    val PRESETS = listOf("google", "microsoft", "keycloak", "authentik", "generic")

    fun presetLabel(p: String): Int = when (p) {
        "google" -> R.string.sso_preset_google
        "microsoft" -> R.string.sso_preset_microsoft
        "keycloak" -> R.string.sso_preset_keycloak
        "authentik" -> R.string.sso_preset_authentik
        else -> R.string.sso_preset_generic
    }

    /** Which fields a preset asks for, its issuer example and its help. */
    data class PresetFields(val issuer: Boolean, val tenant: Boolean, val issuerExample: String, val requireEmailVerified: Boolean, val help: Int)

    fun fields(p: String): PresetFields = when (p) {
        "google" -> PresetFields(false, false, "https://accounts.google.com", true, R.string.sso_help_google)
        "microsoft" -> PresetFields(false, true, "https://login.microsoftonline.com/<tenant>/v2.0", false, R.string.sso_help_microsoft)
        "keycloak" -> PresetFields(true, false, "https://sso.example.com/realms/example", true, R.string.sso_help_keycloak)
        "authentik" -> PresetFields(true, false, "https://auth.example.com/application/o/termoak/", true, R.string.sso_help_authentik)
        else -> PresetFields(true, false, "https://idp.example.com", true, R.string.sso_help_generic)
    }

    /** The scopes always asked for. */
    private val BASE_SCOPES = listOf("openid", "email", "profile")

    /** What the form holds. */
    data class Form(
        val enabled: Boolean = true,
        val preset: String = "generic",
        val issuer: String = "",
        val tenant: String = "",
        val clientId: String = "",
        val clientSecret: String = "",
        val scopes: String = "",
        val emailClaim: String = "",
        val requireEmailVerified: Boolean = true,
        val enforce: Boolean = false,
        val ownerPasswordAllowed: Boolean = true,
        val autoCreate: Boolean = true,
        val autoJoin: Boolean = true,
        val satisfies2fa: Boolean = false,
    ) {
        companion object {
            fun of(s: SsoSettings) = Form(
                enabled = s.enabled, preset = s.preset.ifEmpty { "generic" }, issuer = s.issuer, tenant = s.tenant.orEmpty(),
                clientId = s.clientId, scopes = s.scopes.filter { it !in BASE_SCOPES }.joinToString(" "),
                emailClaim = s.emailClaim.orEmpty(), requireEmailVerified = s.requireEmailVerified, enforce = s.enforce,
                ownerPasswordAllowed = s.ownerPasswordAllowed, autoCreate = s.autoCreate, autoJoin = s.autoJoin,
                satisfies2fa = s.satisfies2fa,
            )

            /** A new form for a preset (its email-verified default). */
            fun newFor(preset: String) = Form(preset = preset, requireEmailVerified = fields(preset).requireEmailVerified)
        }
    }

    /** The settings to send, or the text of what is missing. */
    sealed interface Checked {
        data class Ok(val settings: SsoSettings, val secret: String?) : Checked
        data class Missing(val text: Int) : Checked
    }

    fun check(f: Form, hasSecret: Boolean, canEnforce: Boolean): Checked {
        val fl = fields(f.preset)
        if (f.clientId.isBlank()) return Checked.Missing(R.string.sso_form_client_id_required)
        if (fl.tenant && f.tenant.isBlank()) return Checked.Missing(R.string.sso_form_tenant_required)
        if (fl.issuer && f.issuer.isBlank()) return Checked.Missing(R.string.sso_form_issuer_required)
        if (f.preset == "google" && f.clientSecret.isBlank() && !hasSecret) return Checked.Missing(R.string.sso_form_secret_required_google)
        val scopes = BASE_SCOPES + f.scopes.split(Regex("[\\s,]+")).filter { it.isNotEmpty() && it !in BASE_SCOPES }
        val s = SsoSettings(
            enabled = f.enabled,
            preset = f.preset,
            issuer = if (fl.issuer) f.issuer.trim() else "",
            tenant = if (fl.tenant) f.tenant.trim().ifEmpty { null } else null,
            clientId = f.clientId.trim(),
            scopes = scopes.distinct(),
            emailClaim = f.emailClaim.trim().ifEmpty { null },
            requireEmailVerified = f.requireEmailVerified,
            enforce = f.enforce && canEnforce,
            ownerPasswordAllowed = f.ownerPasswordAllowed,
            autoCreate = f.autoCreate,
            autoJoin = f.autoJoin,
            satisfies2fa = f.satisfies2fa,
        )
        return Checked.Ok(s, f.clientSecret.trim().ifEmpty { null })
    }

    /** Enforcement needs a verified domain. */
    fun canEnforce(domains: List<TeamDomainInfo>): Boolean = domains.any { it.verified }

    /** The TXT record as a zone file line. */
    fun zoneLine(d: TeamDomainInfo): String = "${d.recordName}. 300 IN TXT \"${d.recordValue}\""

    /** A domain name a team can claim (letters, digits, hyphens, dots; a dot inside). */
    fun domainValid(d: String): Boolean {
        val t = d.trim().lowercase().trimEnd('.')
        return t.length in 3..253 && '.' in t && t.split('.').all { l ->
            l.isNotEmpty() && l.length <= 63 && l.all { it.isLetterOrDigit() || it == '-' } && !l.startsWith('-') && !l.endsWith('-')
        }
    }

    /** The state of a domain: verified, failed with its error, or waiting. */
    enum class DomainState { VERIFIED, FAILED, PENDING }

    fun domainState(d: TeamDomainInfo): DomainState = when {
        d.verified -> DomainState.VERIFIED
        !d.lastError.isNullOrBlank() -> DomainState.FAILED
        else -> DomainState.PENDING
    }

    /** The team's state for its list: not set up, off, on, required. */
    enum class TeamState { NOT_SET_UP, OFF, ON, ENFORCED }

    fun teamState(s: SsoSettings?): TeamState = when {
        s == null -> TeamState.NOT_SET_UP
        !s.enabled -> TeamState.OFF
        s.enforce -> TeamState.ENFORCED
        else -> TeamState.ON
    }
}
