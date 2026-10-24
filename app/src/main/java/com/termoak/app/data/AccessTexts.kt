package com.termoak.app.data

import android.content.res.Resources
import androidx.annotation.StringRes
import com.termoak.app.R
import com.termoak.app.UiText
import com.termoak.ffi.TermoakException

/**
 * The texts of teams security: every error code of TEAMS-SECURITY.md §7
 * (always the app's text, never the server's English message), the session
 * close reasons, request states and durations.
 */
object AccessTexts {
    /** Error code → its text. */
    val ERRORS: Map<String, Int> = mapOf(
        "connect_only" to R.string.error_connect_only,
        "secret_reveal_forbidden" to R.string.error_secret_reveal_forbidden,
        "access_approval_required" to R.string.error_access_approval_required,
        "access_needs_server" to R.string.error_access_needs_server,
        "access_request_exists" to R.string.error_access_request_exists,
        "access_request_not_needed" to R.string.error_access_request_not_needed,
        "access_request_closed" to R.string.error_access_request_closed,
        "access_duration_too_long" to R.string.error_access_duration_too_long,
        "self_approval_forbidden" to R.string.error_self_approval_forbidden,
        "not_an_approver" to R.string.error_not_an_approver,
        "ca_not_configured" to R.string.error_ca_not_configured,
        "ca_exists" to R.string.error_ca_exists,
        "certificate_key_not_owned" to R.string.error_certificate_key_not_owned,
        "certificate_key_revoked" to R.string.error_certificate_key_revoked,
        "certificate_proof_invalid" to R.string.error_certificate_proof_invalid,
        "certificate_no_principals" to R.string.error_certificate_no_principals,
        "team_2fa_required" to R.string.error_team_2fa_required,
        "policy_requires_2fa" to R.string.error_policy_requires_2fa,
        "ai_forbidden_by_policy" to R.string.error_ai_forbidden_by_policy,
        "networks_forbidden_by_policy" to R.string.error_networks_forbidden_by_policy,
        "recording_required" to R.string.error_recording_required,
        "invalid_reason" to R.string.error_invalid_reason,
    )

    /** Close reason → its text. */
    val CLOSE_REASONS: Map<String, Int> = mapOf(
        "access_expired" to R.string.access_closed_access_expired,
        "access_revoked" to R.string.access_closed_access_revoked,
        "vault_access_revoked" to R.string.access_closed_vault_access_revoked,
    )

    /**
     * The server's English messages of these codes, for places that only
     * carry the text (`/exec` results, a server session's close reason,
     * host tests), matched by a part of them.
     */
    private val MESSAGES: List<Pair<String, String>> = listOf(
        "connect only members connect" to "connect_only",
        "needs an approved access window" to "access_approval_required",
        "there is no certificate authority" to "ca_not_configured",
        "the team does not allow private networks" to "networks_forbidden_by_policy",
        "requires two-step verification" to "team_2fa_required",
        "must be recorded" to "recording_required",
    )

    /** The text of a teams security code (`null`: not one). */
    fun error(code: String?): UiText? {
        val id = code?.let { ERRORS[it] } ?: return null
        return UiText.Res(id)
    }

    /** The error's text when it is a teams security one (by code; also the bare reasons and the English texts). */
    fun of(e: Throwable): UiText? {
        val info = ApiErrors.of(e)
        info?.code?.let { c -> error(c)?.let { return it } }
        val msg = (info?.message ?: e.message).orEmpty()
        return serverText(msg)
    }

    /** A close reason or an English message of the server in words (`null`: not one of them). */
    fun serverText(text: String?): UiText? {
        val t = text?.trim().orEmpty()
        if (t.isEmpty()) return null
        CLOSE_REASONS[t]?.let { return UiText.Res(it) }
        ERRORS[t]?.let { return UiText.Res(it) }
        val low = t.lowercase()
        MESSAGES.firstOrNull { low.contains(it.first) }?.let { return UiText.Res(ERRORS.getValue(it.second)) }
        return null
    }

    /** The code of an error (`access_approval_required`…), `null` without one. */
    fun codeOf(e: Throwable): String? = ApiErrors.of(e)?.code

    /** The need for an approval in an error (`null`: it is not `access_approval_required`). */
    fun needOf(e: Throwable, hostId: String?): AccessRules.AccessNeed? {
        val info = ApiErrors.of(e) ?: return null
        if (info.code != "access_approval_required") return null
        return AccessRules.needOf(ApiErrors.details(info), hostId)
    }

    // ----- Durations, times, states -----

    /** "15 min", "2 h", "3 days" for a duration in minutes. */
    fun duration(res: Resources, minutes: Int): String = when {
        minutes < 60 -> res.getString(R.string.access_duration_minutes, minutes.toString())
        minutes % 1440 == 0 -> res.getQuantityString(R.plurals.access_duration_days, minutes / 1440, minutes / 1440)
        minutes % 60 == 0 -> res.getString(R.string.access_duration_hours, (minutes / 60).toString())
        else -> res.getString(R.string.access_duration_hours_minutes, (minutes / 60).toString(), (minutes % 60).toString())
    }

    /** "1 h 05 min", "42 min", "45 s": how long is left. */
    fun left(res: Resources, secs: Long): String {
        val l = AccessRules.left(secs)
        return when {
            l.seconds != null -> res.getString(R.string.access_time_seconds, l.seconds.toString())
            l.hours == 0L -> res.getString(R.string.access_time_minutes, l.minutes.toString())
            else -> res.getString(R.string.access_time_hours, l.hours.toString(), "%02d".format(l.minutes))
        }
    }

    /** Local "HH:MM" of a time (ms). */
    fun clock(ms: Long): String =
        java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(ms))

    /** A date and time for lists (ms). */
    fun dateTime(ms: Long): String =
        java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(java.util.Date(ms))

    /** A date (ms). */
    fun date(ms: Long): String = java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(ms))

    @StringRes
    fun status(k: AccessRules.StatusKey): Int = when (k) {
        AccessRules.StatusKey.PENDING -> R.string.access_status_pending
        AccessRules.StatusKey.ACTIVE -> R.string.access_status_active
        AccessRules.StatusKey.ENDED -> R.string.access_status_ended
        AccessRules.StatusKey.DENIED -> R.string.access_status_denied
        AccessRules.StatusKey.EXPIRED -> R.string.access_status_expired
        AccessRules.StatusKey.CANCELLED -> R.string.access_status_cancelled
        AccessRules.StatusKey.REVOKED -> R.string.access_status_revoked
        AccessRules.StatusKey.UNKNOWN -> R.string.access_status_unknown
    }

    /** What to say about an event (snackbar), `null`: nothing. */
    fun notice(res: Resources, n: AccessRules.Notice, ev: AccessRules.AccessEvent): String {
        val r = ev.request
        val target = r.targetLabel
        return when (n) {
            AccessRules.Notice.CREATED -> res.getString(
                R.string.access_notice_created, AccessRules.requesterName(r), target, duration(res, r.minutes.toInt()),
            )
            AccessRules.Notice.APPROVED -> res.getString(R.string.access_notice_approved, target, r.endsAt?.let(::clock).orEmpty())
            AccessRules.Notice.DENIED -> res.getString(R.string.access_notice_denied, target)
            AccessRules.Notice.DENIED_REASON -> res.getString(R.string.access_notice_denied_reason, target, r.decisionReason.orEmpty())
            AccessRules.Notice.REVOKED -> res.getString(R.string.access_notice_revoked, r.decidedByName.orEmpty(), target)
            AccessRules.Notice.EXPIRED -> res.getString(R.string.access_notice_expired, target)
            AccessRules.Notice.ENDED -> res.getString(R.string.access_notice_ended, target)
        }
    }

    /** A connection error that is only a teams security code of the engine (status 0), e.g. Connect only. */
    fun isConnectOnly(e: Throwable): Boolean = codeOf(e) == "connect_only" || (e is TermoakException && e.message?.startsWith("Connect only") == true)
}
