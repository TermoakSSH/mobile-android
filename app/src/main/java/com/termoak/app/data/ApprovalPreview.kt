package com.termoak.app.data

import com.termoak.ffi.AccountHandle
import com.termoak.ffi.AiApprovalPreview
import com.termoak.ffi.AiDecision
import com.termoak.ffi.AiRiskLevel
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

        /** What the engine says an approval is about (`AiApproval.preview`; `null` on servers before 0.6: its summary then). */
        fun of(p: AiApprovalPreview?): ApprovalPreview? = p?.let {
            ApprovalPreview(
                kind = it.kind,
                host = it.host,
                command = it.command,
                path = it.path,
                diff = it.diff,
                diffTruncated = it.truncated,
                added = it.added?.toInt(),
                removed = it.removed?.toInt(),
                newFile = it.newFile,
                diffError = it.diffError,
                risk = when (it.risk) {
                    AiRiskLevel.HIGH -> "high"
                    AiRiskLevel.MEDIUM -> "medium"
                    AiRiskLevel.LOW -> "low"
                },
                reasons = it.reasons.map { r -> RiskReason(r.code, r.text) },
                explanation = it.explanation,
                plan = it.plan,
                editable = it.editable,
            )
        }
    }
}

/** What was decided: approve or deny, for the rest of the task, an edited command or plan, and why. */
data class ApprovalDecision(val approve: Boolean, val always: Boolean = false, val edited: String? = null, val reason: String? = null)

/** The engine's decision: the edit only when approving, empty texts left out. */
fun ApprovalDecision.engine(): AiDecision = AiDecision(
    approve = approve,
    always = always,
    edited = edited?.trim()?.takeIf { approve && it.isNotEmpty() },
    reason = reason?.trim()?.takeIf { it.isNotEmpty() },
)

/** Decides an approval on [this] account, with an edited command or plan or a reason (the engine's typed call). */
suspend fun AccountHandle.decide(taskId: String, approvalId: String, d: ApprovalDecision) {
    decideApprovalWith(taskId, approvalId, d.engine())
}
