package com.termoak.app.data

import com.termoak.ffi.AccountHandle
import org.json.JSONArray
import org.json.JSONObject

/** Why an action is risky: a stable code (translated) and the server's English text. */
data class RiskReason(val code: String, val text: String)

/**
 * What an AI approval shows (server 0.6, `pending_approvals[].preview`), as
 * the desktop and iOS show it: what runs and where, its risk and why, a
 * file's diff, or the plan to approve; and whether it can be edited.
 */
data class ApprovalPreview(
    /** `command`, `terminal`, `file`, `plan` or `other`. */
    val kind: String,
    val host: String? = null,
    val command: String? = null,
    val path: String? = null,
    val diff: String? = null,
    val diffTruncated: Boolean = false,
    val added: Int? = null,
    val removed: Int? = null,
    val newFile: Boolean = false,
    val diffError: String? = null,
    /** `low`, `medium` or `high`. */
    val risk: String = "low",
    val reasons: List<RiskReason> = emptyList(),
    val explanation: String? = null,
    val plan: String? = null,
    val editable: Boolean = false,
) {
    val isPlan: Boolean get() = kind == "plan"

    /** What "Edit and approve" starts from: the plan, or the command. */
    val editableText: String? get() = if (isPlan) plan else command

    companion object {
        private fun JSONObject.str(key: String): String? = if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }
        private fun JSONObject.int(key: String): Int? = if (has(key) && !isNull(key)) optInt(key) else null

        fun parse(o: JSONObject?): ApprovalPreview? {
            if (o == null) return null
            val reasons = o.optJSONArray("reasons")?.let { a ->
                (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map { RiskReason(it.optString("code"), it.optString("text")) }
            }.orEmpty()
            return ApprovalPreview(
                kind = o.optString("kind", "other"),
                host = o.str("host"),
                command = o.str("command"),
                path = o.str("path"),
                diff = o.str("diff"),
                diffTruncated = o.optBoolean("diff_truncated", false),
                added = o.int("added"),
                removed = o.int("removed"),
                newFile = o.optBoolean("new_file", false),
                diffError = o.str("diff_error"),
                risk = o.optString("risk", "low").ifEmpty { "low" },
                reasons = reasons,
                explanation = o.str("explanation"),
                plan = o.str("plan"),
                editable = o.optBoolean("editable", false),
            )
        }

        /** Previews by approval id, from `GET /ai/approvals` (an array) or a task (`pending_approvals`). */
        fun byApproval(json: String?): Map<String, ApprovalPreview> {
            val text = json?.trim().orEmpty()
            if (text.isEmpty()) return emptyMap()
            val list = runCatching {
                if (text.startsWith("[")) JSONArray(text) else JSONObject(text).optJSONArray("pending_approvals")
            }.getOrNull() ?: return emptyMap()
            return (0 until list.length()).mapNotNull { list.optJSONObject(it) }
                .mapNotNull { a -> parse(a.optJSONObject("preview"))?.let { a.optString("id") to it } }
                .toMap()
        }

        /** The decision (`POST /ai/tasks/{id}/approvals/{approval_id}`): approve, always, the edited text and the reason. */
        fun decisionBody(d: ApprovalDecision): String = JSONObject().apply {
            put("approve", d.approve)
            put("always", d.always)
            d.edited?.let { put("edited", it) }
            d.reason?.trim()?.takeIf { it.isNotEmpty() }?.let { put("reason", it) }
        }.toString()
    }
}

/** What was decided: approve or deny, for the rest of the task, an edited command or plan, and why. */
data class ApprovalDecision(val approve: Boolean, val always: Boolean = false, val edited: String? = null, val reason: String? = null)

/**
 * Decides an approval on [handle]: with an edited text or a reason, through
 * the server's decision endpoint (server 0.6); otherwise the engine's call.
 */
suspend fun AccountHandle.decide(taskId: String, approvalId: String, d: ApprovalDecision) {
    if (d.edited == null && d.reason.isNullOrBlank()) {
        decideApproval(taskId, approvalId, d.approve, d.always)
    } else {
        apiPost("/api/v1/ai/tasks/$taskId/approvals/$approvalId", ApprovalPreview.decisionBody(d))
    }
}
