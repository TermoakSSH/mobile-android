package com.termoak.app.data

import com.termoak.app.R
import com.termoak.ffi.AuditEvent
import com.termoak.ffi.AuditQuery
import com.termoak.ffi.AuditSinkInfo
import org.json.JSONObject

/**
 * The extended audit in the app (0.7 phase 5c, docs-next/E2EE-SSO-AUDIT.md
 * §4, as the desktop's `audit.rs`): filter presets and time ranges to an
 * `AuditQuery`, the actions in words, a short summary of an entry's
 * details, export names and formats, team retention and the sinks' states.
 */
object AuditRules {
    /** Entries per page (`limit`; `before` the last one for the next page). */
    const val PAGE = 50u

    /** The action presets of the filter (the desktop's). */
    enum class Preset(val label: Int, val actions: String?) {
        ALL(R.string.audit_preset_all, null),
        ACCESS(R.string.audit_preset_access, "access.*,access_policy.*"),
        CERTIFICATES(R.string.audit_preset_certificates, "cert.*,ca.*"),
        POLICY(R.string.audit_preset_policy, "team.policy,access_policy.*,team.role_changed"),
        E2EE(R.string.audit_preset_e2ee, "e2ee.*"),
        SSO(R.string.audit_preset_sso, "sso.*,auth.login_refused"),
        SIGN_INS(R.string.audit_preset_sign_ins, "auth.*,sso.login,sso.login_failed"),
    }

    /** Time ranges ending now. */
    enum class Range(val label: Int, val days: Int?) {
        ANY(R.string.audit_range_any, null),
        DAY(R.string.audit_range_day, 1),
        WEEK(R.string.audit_range_week, 7),
        MONTH(R.string.audit_range_month, 30),
        QUARTER(R.string.audit_range_quarter, 90),
        ;

        fun since(now: Long): Long? = days?.let { now - it * 24L * 3600 * 1000 }
    }

    /** What the filter bar holds. */
    data class Filters(
        val preset: Preset = Preset.ALL,
        /** More actions typed by hand (comma list, `*` prefixes). */
        val actions: String = "",
        val actor: String = "",
        val vaultId: String? = null,
        val range: Range = Range.ANY,
        val text: String = "",
    ) {
        /** The server's query (newest first; `before`: the next page). */
        fun query(now: Long, before: Long? = null): AuditQuery {
            val list = (preset.actions?.split(',').orEmpty() + actions.split(Regex("[,\\s]+"))).filter { it.isNotEmpty() }
            return AuditQuery(
                action = list.joinToString(",").ifEmpty { null },
                actor = actor.trim().ifEmpty { null },
                vaultId = vaultId,
                target = null,
                since = range.since(now),
                until = null,
                text = text.trim().ifEmpty { null },
                before = before,
                limit = PAGE,
            )
        }

        /** For an export: every entry matching, oldest first (no page). */
        fun exportQuery(now: Long): AuditQuery = query(now).copy(before = null, limit = null)

        val active: Boolean get() = preset != Preset.ALL || actions.isNotBlank() || actor.isNotBlank() || range != Range.ANY || text.isNotBlank()
    }

    /** The next page: older than the last entry shown (`null` when the last page was not full). */
    fun nextBefore(entries: List<AuditEvent>, lastPage: Int): Long? = if (lastPage.toUInt() < PAGE) null else entries.lastOrNull()?.id

    /** Export formats (`format` of the server). */
    val FORMATS = listOf("csv", "json", "ndjson")

    fun mime(format: String): String = when (format) {
        "csv" -> "text/csv"
        "json" -> "application/json"
        else -> "application/x-ndjson"
    }

    /** The file name of an export ("termoak-audit-ops-2026-10-27.csv"). */
    fun exportName(scope: String, format: String, date: String): String {
        val s = scope.lowercase().map { if (it.isLetterOrDigit() || it == '-') it else '-' }.joinToString("").trim('-')
        return "termoak-audit-${s.ifEmpty { "audit" }}-$date.$format"
    }

    /** Actions → their text (`null`: unknown, the action is shown as it is). */
    val ACTIONS: Map<String, Int> = mapOf(
        "e2ee.setup" to R.string.audit_action_e2ee_setup,
        "e2ee.seal" to R.string.audit_action_e2ee_seal,
        "e2ee.passphrase_removed" to R.string.audit_action_e2ee_passphrase_removed,
        "e2ee.key_rotate" to R.string.audit_action_e2ee_key_rotate,
        "e2ee.reset" to R.string.audit_action_e2ee_reset,
        "e2ee.pairing_request" to R.string.audit_action_e2ee_pairing_request,
        "e2ee.pairing_approve" to R.string.audit_action_e2ee_pairing_approve,
        "e2ee.pairing_deny" to R.string.audit_action_e2ee_pairing_deny,
        "e2ee.enable" to R.string.audit_action_e2ee_enable,
        "e2ee.rotate" to R.string.audit_action_e2ee_rotate,
        "e2ee.disable" to R.string.audit_action_e2ee_disable,
        "e2ee.wraps_add" to R.string.audit_action_e2ee_wraps_add,
        "e2ee.unlock" to R.string.audit_action_e2ee_unlock,
        "sso.config" to R.string.audit_action_sso_config,
        "sso.domain_add" to R.string.audit_action_sso_domain_add,
        "sso.domain_verify" to R.string.audit_action_sso_domain_verify,
        "sso.domain_check_failed" to R.string.audit_action_sso_domain_check_failed,
        "sso.domain_remove" to R.string.audit_action_sso_domain_remove,
        "sso.login" to R.string.audit_action_sso_login,
        "sso.login_failed" to R.string.audit_action_sso_login_failed,
        "sso.link" to R.string.audit_action_sso_link,
        "sso.unlink" to R.string.audit_action_sso_unlink,
        "sso.user_created" to R.string.audit_action_sso_user_created,
        "auth.login_refused" to R.string.audit_action_auth_login_refused,
        "audit.export" to R.string.audit_action_audit_export,
        "audit.settings" to R.string.audit_action_audit_settings,
        "audit.sink_create" to R.string.audit_action_audit_sink_create,
        "audit.sink_update" to R.string.audit_action_audit_sink_update,
        "audit.sink_delete" to R.string.audit_action_audit_sink_delete,
        "audit.test" to R.string.audit_action_audit_test,
        "access.request" to R.string.audit_action_access_request,
        "access.approve" to R.string.audit_action_access_approve,
        "access.deny" to R.string.audit_action_access_deny,
        "access.revoke" to R.string.audit_action_access_revoke,
        "access.cancel" to R.string.audit_action_access_cancel,
        "access_policy.set" to R.string.audit_action_access_policy_set,
        "access_policy.delete" to R.string.audit_action_access_policy_delete,
        "cert.issue" to R.string.audit_action_cert_issue,
        "cert.revoke" to R.string.audit_action_cert_revoke,
        "ca.create" to R.string.audit_action_ca_create,
        "ca.rotate" to R.string.audit_action_ca_rotate,
        "ca.disable" to R.string.audit_action_ca_disable,
        "team.policy" to R.string.audit_action_team_policy,
        "vault.create" to R.string.audit_action_vault_create,
        "vault.update" to R.string.audit_action_vault_update,
        "vault.delete" to R.string.audit_action_vault_delete,
        "vault.member_add" to R.string.audit_action_vault_member_add,
        "vault.member_removed" to R.string.audit_action_vault_member_removed,
        "vault.role_changed" to R.string.audit_action_vault_role_changed,
        "vault.transfer" to R.string.audit_action_vault_transfer,
        "auth.devices_revoked" to R.string.audit_action_auth_devices_revoked,
        "auth.email_changed" to R.string.audit_action_auth_email_changed,
        "auth.email_verified" to R.string.audit_action_auth_email_verified,
        "auth.password_reset" to R.string.audit_action_auth_password_reset,
        "network.up" to R.string.audit_action_network_up,
        "network.down" to R.string.audit_action_network_down,
        "network.key_generated" to R.string.audit_action_network_key_generated,
        "team.principal_changed" to R.string.audit_action_team_principal_changed,
        "team.invite_created" to R.string.audit_action_team_invite_created,
        "team.invite_revoked" to R.string.audit_action_team_invite_revoked,
        "secret.reveal" to R.string.audit_action_secret_reveal,
        "secret.use" to R.string.audit_action_secret_use,
        "auth.login" to R.string.audit_action_auth_login,
        "auth.register" to R.string.audit_action_auth_register,
        "auth.password_changed" to R.string.audit_action_auth_password_changed,
        "auth.2fa_enabled" to R.string.audit_action_auth_2fa_enabled,
        "auth.2fa_disabled" to R.string.audit_action_auth_2fa_disabled,
        "auth.device_revoked" to R.string.audit_action_auth_device_revoked,
        "admin.user_created" to R.string.audit_action_admin_user_created,
        "admin.user_updated" to R.string.audit_action_admin_user_updated,
        "admin.password_reset" to R.string.audit_action_admin_password_reset,
        "admin.2fa_reset" to R.string.audit_action_admin_2fa_reset,
        "admin.device_revoked" to R.string.audit_action_admin_device_revoked,
        "admin.invite_created" to R.string.audit_action_admin_invite_created,
        "admin.invite_revoked" to R.string.audit_action_admin_invite_revoked,
        "team.created" to R.string.audit_action_team_created,
        "team.deleted" to R.string.audit_action_team_deleted,
        "team.member_added" to R.string.audit_action_team_member_added,
        "team.member_removed" to R.string.audit_action_team_member_removed,
        "team.role_changed" to R.string.audit_action_team_role_changed,
        "team.left" to R.string.audit_action_team_left,
        "session.open" to R.string.audit_action_session_open,
        "session.close" to R.string.audit_action_session_close,
        "session.attach" to R.string.audit_action_session_attach,
        "session.share" to R.string.audit_action_session_share,
        "session.share_revoked" to R.string.audit_action_session_share_revoked,
        "sftp.upload" to R.string.audit_action_sftp_upload,
        "sftp.download" to R.string.audit_action_sftp_download,
        "sftp.rename" to R.string.audit_action_sftp_rename,
        "sftp.delete" to R.string.audit_action_sftp_delete,
        "exec.batch" to R.string.audit_action_exec_batch,
        "key.create" to R.string.audit_action_key_create,
        "ai.task.mode" to R.string.audit_action_ai_task_mode,
        "ai.approval.approved" to R.string.audit_action_ai_approval_approved,
        "ai.approval.denied" to R.string.audit_action_ai_approval_denied,
    )

    fun actionText(action: String): Int? = ACTIONS[action] ?: ACTIONS[action.replace('-', '_')]

    /** A short summary of an entry's details ("device: Pixel · reason: …"), at most [max] characters. */
    fun detailSummary(detailJson: String, max: Int = 160): String {
        val o = runCatching { JSONObject(detailJson) }.getOrNull() ?: return ""
        val parts = o.keys().asSequence().mapNotNull { k ->
            when (val v = o.opt(k)) {
                null, JSONObject.NULL -> null
                is String -> "$k: $v"
                is org.json.JSONArray -> if (v.length() == 0) null else "$k: $v"
                else -> "$k: $v"
            }
        }.toList()
        val s = parts.joinToString(" · ")
        return if (s.length > max) s.take(max) + "…" else s
    }

    /** Retention in days: 1 to the server's maximum; empty is the server's default (`null`). */
    sealed interface Retention {
        data class Ok(val days: UInt?) : Retention
        data object Invalid : Retention
    }

    fun parseRetention(text: String, max: Int): Retention {
        val t = text.trim()
        if (t.isEmpty()) return Retention.Ok(null)
        val d = t.toIntOrNull() ?: return Retention.Invalid
        return if (d in 1..max) Retention.Ok(d.toUInt()) else Retention.Invalid
    }

    /** What `/info features.audit_v2` says: the export formats and sink kinds (`null`: no extended audit). */
    data class Features(val exports: List<String>, val retention: Boolean, val sinks: List<String>)

    fun features(infoJson: String): Features? = runCatching {
        val a = JSONObject(infoJson).optJSONObject("features")?.optJSONObject("audit_v2") ?: return null
        fun list(k: String, all: List<String>): List<String> = when (val v = a.opt(k)) {
            is org.json.JSONArray -> (0 until v.length()).map { v.optString(it) }.filter { it.isNotEmpty() }
            true -> all
            else -> emptyList()
        }
        Features(list("export", FORMATS), a.optBoolean("retention", false), list("sinks", listOf("webhook", "syslog")))
    }.getOrNull()

    /** `GET /teams/{id}/audit/settings`: the team's days (`null`: the server's default), the default and the maximum. */
    data class RetentionView(val days: Int?, val defaultDays: Int, val maxDays: Int)

    fun retentionView(json: String): RetentionView? = runCatching {
        val o = JSONObject(json)
        RetentionView(
            days = if (o.isNull("retention_days") || !o.has("retention_days")) null else o.getInt("retention_days"),
            defaultDays = o.optInt("default_retention_days", 365),
            maxDays = o.optInt("max_retention_days", 3650),
        )
    }.getOrNull()

    /** Where a sink delivers, in one line. */
    fun sinkTarget(s: AuditSinkInfo): String = when (s.kind) {
        "syslog" -> {
            val tls = s.transport != "tcp"
            val port = s.port?.toInt() ?: if (tls) 6514 else 514
            "syslog${if (tls) "+tls" else ""}://${s.host.orEmpty()}:$port"
        }
        else -> s.url.orEmpty()
    }

    /** The delivery state of a sink. */
    enum class SinkState { PAUSED, FAILING, DELIVERED, WAITING }

    fun sinkState(s: AuditSinkInfo): SinkState = when {
        !s.enabled -> SinkState.PAUSED
        s.failures > 0 -> SinkState.FAILING
        s.lastSuccessAt != null -> SinkState.DELIVERED
        else -> SinkState.WAITING
    }

    /** Who did it: the actor without `user:` when it is your own id, else as it is. */
    fun actor(actor: String, names: Map<String, String>): String {
        val id = actor.removePrefix("user:")
        return names[id] ?: actor
    }
}
