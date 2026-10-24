package com.termoak.app.data

import com.termoak.ffi.AccessRequestInfo
import com.termoak.ffi.AccessRequestStatus
import com.termoak.ffi.AccessTargetKind
import com.termoak.ffi.CaOverview
import com.termoak.ffi.HostAccessInfo
import com.termoak.ffi.ItemAccess
import com.termoak.ffi.VaultPolicyInfo
import com.termoak.ffi.VaultRole
import org.json.JSONObject

/**
 * Teams security in the app (0.7 phase 4d, docs-next/TEAMS-SECURITY.md
 * §13.2): the rules the screens share, in plain Kotlin so the JVM tests
 * check them.
 *
 * - Roles a vault Admin can give (Connect only, Use, Edit, Admin) and who
 *   may delete a vault (only implicit managers, never an explicit Admin).
 * - What a vault's team policy hides ([Gates]): reveal/copy/export, the AI,
 *   the private network picker; whether sessions are recorded.
 * - The effective authentication of a host or group ("Team certificate",
 *   its own or its nearest group's) and where a connection goes (from the
 *   phone, or through the server for Connect only / Strict hosts that can't
 *   use a team certificate).
 * - Access windows (countdown and its ticks), durations offered when asking
 *   and approving, what an `access_approval_required` error says (two
 *   shapes: the server's and the engine's own check), the request form's
 *   rules, `access_request` events and the session close reasons.
 * - The CA install command: the script in a heredoc (`install.sh` needs a
 *   manager's token, so `curl … | sh` can't work from a host).
 */
object AccessRules {
    /** `HostSettings.auth` of hosts and groups that use the vault's team certificate. */
    const val TEAM_CERTIFICATE = "team_certificate"

    // ----- Roles -----

    /** Rank of a role (never compare their names): unknown < connect only < use < edit < admin. */
    fun rank(role: VaultRole?): Int = when (role) {
        VaultRole.CONNECT_ONLY -> 1
        VaultRole.USE_ONLY -> 2
        VaultRole.EDITOR -> 3
        VaultRole.MANAGER -> 4
        VaultRole.UNKNOWN, null -> 0
    }

    /** The roles the picker offers: the four with a server that has teams security, else the two every server knows. */
    fun grantableRoles(teamsSecurity: Boolean): List<VaultRole> =
        if (teamsSecurity) listOf(VaultRole.CONNECT_ONLY, VaultRole.USE_ONLY, VaultRole.EDITOR, VaultRole.MANAGER)
        else listOf(VaultRole.USE_ONLY, VaultRole.EDITOR)

    /**
     * "Delete vault" is offered: only to implicit managers (the owner of a
     * shared vault, the team's owners and admins). An explicit Admin (a
     * `manager` grant) manages the vault but can't delete it.
     */
    fun canDeleteVault(role: VaultRole, implicitManager: Boolean): Boolean = role == VaultRole.MANAGER && implicitManager

    /** No secrets on this device for these items (Use only and Connect only). */
    fun noSecrets(access: ItemAccess?): Boolean = access == ItemAccess.USE_ONLY || access == ItemAccess.CONNECT_ONLY

    // ----- Policy gates -----

    /** What a vault's team policy lets this member do (and what to hide). */
    data class Gates(
        /** Reveal or copy secrets, export private keys, copy items with their secrets out of the vault. */
        val reveal: Boolean,
        /** AI actions on its hosts (and their place in the AI's host lists). */
        val ai: Boolean,
        /** The private network picker. */
        val networks: Boolean,
        /** Every session on its hosts is recorded ("Recorded" badge). */
        val recorded: Boolean,
        /** Some hosts have access policies (ask the server about them). */
        val accessRules: Boolean,
        /** The vault's owner has an SSH CA ("Team certificate" works). */
        val certificates: Boolean,
    ) {
        companion object {
            /** This device's items, and vaults without a policy where the role allows everything. */
            val ALL = Gates(reveal = true, ai = true, networks = true, recorded = false, accessRules = false, certificates = false)
        }
    }

    /** [Gates] of a member with [role] in a vault with [policy] (`null`: no team policy, as the role allows). */
    fun gates(role: VaultRole, policy: VaultPolicyInfo?): Gates {
        val canRead = rank(role) >= rank(VaultRole.EDITOR)
        return Gates(
            reveal = canRead && (role == VaultRole.MANAGER || policy?.revealAllowed != false),
            ai = policy?.aiAllowed != false,
            networks = policy?.networksAllowed != false,
            recorded = policy?.recordSessions == true,
            accessRules = policy?.accessPolicies == true,
            certificates = policy?.certificates == true,
        )
    }

    // ----- Authentication (team certificates, inherited from groups) -----

    /** What the inheritance needs of a group. */
    data class GroupAuth(val id: String, val name: String, val parentId: String?, val auth: String?)

    /** The authentication that applies (`null`: password/key) and the group it comes from (`null`: its own, or none). */
    data class EffectiveAuth(val auth: String?, val fromGroup: String?) {
        val teamCertificate get() = auth == TEAM_CERTIFICATE
    }

    /** A host's or group's own setting, else its nearest group's ([groups]: those of its vault). */
    fun effectiveAuth(own: String?, groupId: String?, groups: List<GroupAuth>): EffectiveAuth {
        if (own != null) return EffectiveAuth(own, null)
        var next = groupId
        var hops = 0
        while (next != null && hops++ < 16) {
            val g = groups.firstOrNull { it.id == next } ?: break
            if (g.auth != null) return EffectiveAuth(g.auth, g.name)
            next = g.parentId
        }
        return EffectiveAuth(null, null)
    }

    // ----- Where a connection goes -----

    enum class Route { PHONE, SERVER }

    /**
     * Where a terminal to a host opens. Connect only members, and Use only
     * members of Strict vaults, never get secrets: they connect from the
     * phone only with a team certificate (the engine certifies an ephemeral
     * key) on a server that has teams security; otherwise through the
     * server. Telnet stays on the phone (the server doesn't open it; its
     * tab says why).
     */
    fun route(access: ItemAccess?, strict: Boolean, telnet: Boolean, teamCertificateUsable: Boolean): Route {
        val noLocalSecrets = access == ItemAccess.CONNECT_ONLY || (access == ItemAccess.USE_ONLY && strict)
        return when {
            !noLocalSecrets || telnet -> Route.PHONE
            teamCertificateUsable -> Route.PHONE
            else -> Route.SERVER
        }
    }

    // ----- Access windows -----

    /** Seconds left until [endsAt] (ms), never negative. */
    fun remainingSecs(endsAt: Long, now: Long): Long = ((endsAt - now).coerceAtLeast(0) + 999) / 1000

    /** How often a countdown repaints: every second in the last two minutes, else every 15 seconds. */
    fun tickMs(secsLeft: Long): Long = if (secsLeft <= 120) 1_000L else 15_000L

    /** The window is about to end (the countdown turns to a warning colour). */
    fun endingSoon(secsLeft: Long): Boolean = secsLeft <= 300

    /** A short "time left": `(hours, minutes, seconds)`, with seconds only under a minute and minutes rounded up. */
    data class Left(val hours: Long, val minutes: Long, val seconds: Long?)

    fun left(secs: Long): Left {
        val s = secs.coerceAtLeast(0)
        if (s < 60) return Left(0, 0, s)
        val minutes = (s + 59) / 60
        return Left(minutes / 60, minutes % 60, null)
    }

    /** Durations offered when asking (minutes), up to [max]; [max] itself always. */
    fun durationChoices(max: Int): List<Int> {
        val v = listOf(15, 30, 60, 120, 240, 480, 1440, 4320, 10080).filter { it < max }.toMutableList()
        if (max > 0) v += max
        return v
    }

    /** Windows an approver may give: the one asked for, or shorter. */
    fun approveChoices(requested: Int): List<Int> = durationChoices(requested)

    // ----- "Request access" -----

    /** What an `access_approval_required` error says: the host, the policy's target and the longest window. */
    data class AccessNeed(
        val hostId: String?,
        val vaultId: String?,
        val targetKind: AccessTargetKind,
        val targetId: String?,
        val maxMinutes: Int,
        /** A request of yours already waits (its id). */
        val pending: String?,
        val canRequest: Boolean,
    )

    /**
     * From the error's details: the server's (`host_id, policy_id,
     * target_kind, target_id, max_minutes`) or the engine's own check (also
     * `vault_id, pending, can_request`).
     */
    fun needOf(d: JSONObject, hostId: String? = null): AccessNeed {
        fun s(k: String): String? = d.optString(k).takeIf { d.has(k) && !d.isNull(k) && it.isNotBlank() }
        val pending = d.opt("pending").let { p ->
            when (p) {
                is JSONObject -> p.optString("id").ifBlank { null }
                is String -> p.ifBlank { null }
                else -> null
            }
        }
        return AccessNeed(
            hostId = s("host_id") ?: hostId,
            vaultId = s("vault_id"),
            targetKind = kindOf(s("target_kind")),
            targetId = s("target_id"),
            maxMinutes = d.optInt("max_minutes", 0).takeIf { it > 0 } ?: 60,
            pending = pending,
            canRequest = if (d.has("can_request")) d.optBoolean("can_request", true) else true,
        )
    }

    /** The need from a host's access answer (before connecting, the host rows). */
    fun needOf(a: HostAccessInfo): AccessNeed = AccessNeed(
        hostId = a.hostId,
        vaultId = a.vaultId,
        targetKind = a.policy?.targetKind ?: AccessTargetKind.HOST,
        targetId = a.policy?.targetId,
        maxMinutes = a.maxMinutes.toInt().coerceAtLeast(1),
        pending = a.pending?.id,
        canRequest = a.canRequest,
    )

    fun kindOf(s: String?): AccessTargetKind = when (s) {
        "host" -> AccessTargetKind.HOST
        "group" -> AccessTargetKind.GROUP
        "vault" -> AccessTargetKind.VAULT
        null -> AccessTargetKind.HOST
        else -> AccessTargetKind.UNKNOWN
    }

    fun kindName(k: AccessTargetKind): String = when (k) {
        AccessTargetKind.HOST -> "host"
        AccessTargetKind.GROUP -> "group"
        AccessTargetKind.VAULT -> "vault"
        AccessTargetKind.UNKNOWN -> "unknown"
    }

    /** What is wrong with the request form (`null`: it can be sent). */
    enum class RequestProblem { REASON, REASON_LONG, DURATION }

    fun requestProblem(minutes: Int, max: Int, reason: String): RequestProblem? {
        val n = reason.trim().codePointCount(0, reason.trim().length)
        return when {
            n == 0 -> RequestProblem.REASON
            n > 500 -> RequestProblem.REASON_LONG
            minutes <= 0 || minutes > max -> RequestProblem.DURATION
            else -> null
        }
    }

    // ----- Requests -----

    /** Requests waiting for your decision. */
    fun waiting(approvals: List<AccessRequestInfo>): List<AccessRequestInfo> =
        approvals.filter { it.status == AccessRequestStatus.PENDING && it.canDecide }

    /** Approved windows you may end early (as an approver). */
    fun endable(approvals: List<AccessRequestInfo>, now: Long): List<AccessRequestInfo> =
        approvals.filter { isActive(it, now) && it.canDecide }

    /** Approved and inside its window at [now]. */
    fun isActive(r: AccessRequestInfo, now: Long): Boolean =
        r.status == AccessRequestStatus.APPROVED && (r.startsAt ?: Long.MAX_VALUE) <= now && (r.endsAt ?: 0) > now

    /** Status key of a request for its label. */
    enum class StatusKey { PENDING, ACTIVE, ENDED, DENIED, EXPIRED, CANCELLED, REVOKED, UNKNOWN }

    fun statusKey(r: AccessRequestInfo, now: Long): StatusKey = when (r.status) {
        AccessRequestStatus.PENDING -> StatusKey.PENDING
        AccessRequestStatus.APPROVED -> if (isActive(r, now)) StatusKey.ACTIVE else StatusKey.ENDED
        AccessRequestStatus.DENIED -> StatusKey.DENIED
        AccessRequestStatus.EXPIRED -> StatusKey.EXPIRED
        AccessRequestStatus.CANCELLED -> StatusKey.CANCELLED
        AccessRequestStatus.REVOKED -> StatusKey.REVOKED
        AccessRequestStatus.UNKNOWN -> StatusKey.UNKNOWN
    }

    fun statusOf(s: String?): AccessRequestStatus = when (s) {
        "pending" -> AccessRequestStatus.PENDING
        "approved" -> AccessRequestStatus.APPROVED
        "denied" -> AccessRequestStatus.DENIED
        "expired" -> AccessRequestStatus.EXPIRED
        "cancelled" -> AccessRequestStatus.CANCELLED
        "revoked" -> AccessRequestStatus.REVOKED
        else -> AccessRequestStatus.UNKNOWN
    }

    /**
     * A request as the server sends it (the events socket's `request`,
     * snake_case). Events carry the bare request without `can_decide`: the
     * lists are read again on any event.
     */
    fun requestOf(o: JSONObject, now: Long = System.currentTimeMillis()): AccessRequestInfo? {
        val id = o.optString("id").ifBlank { return null }
        fun s(k: String): String? = if (o.has(k) && !o.isNull(k)) o.optString(k) else null
        fun l(k: String): Long? = if (o.has(k) && !o.isNull(k)) o.optLong(k) else null
        val r = AccessRequestInfo(
            id = id,
            vaultId = s("vault_id").orEmpty(),
            targetKind = kindOf(s("target_kind")),
            targetId = s("target_id").orEmpty(),
            targetLabel = s("target_label").orEmpty(),
            requesterId = s("requester_id").orEmpty(),
            requesterName = s("requester_name").orEmpty(),
            requesterEmail = s("requester_email").orEmpty(),
            reason = s("reason").orEmpty(),
            minutes = o.optInt("minutes", 0).coerceAtLeast(0).toUInt(),
            status = statusOf(s("status")),
            createdAt = l("created_at") ?: 0,
            expiresAt = l("expires_at") ?: 0,
            decidedBy = s("decided_by"),
            decidedByName = s("decided_by_name"),
            decidedAt = l("decided_at"),
            decisionReason = s("decision_reason"),
            startsAt = l("starts_at"),
            endsAt = l("ends_at"),
            selfApproved = o.optBoolean("self_approved", false),
            active = false,
            canDecide = o.optBoolean("can_decide", false),
        )
        return r.copy(active = isActive(r, now))
    }

    /** An `access_request` message of the events socket. */
    data class AccessEvent(val event: String, val request: AccessRequestInfo)

    fun eventOf(json: JSONObject): AccessEvent? {
        if (json.optString("type") != "access_request") return null
        val r = json.optJSONObject("request")?.let { requestOf(it) } ?: return null
        return AccessEvent(json.optString("event"), r)
    }

    /** What to tell the user about an event (`me`: the account's user id); `null`: nothing. */
    enum class Notice { CREATED, APPROVED, DENIED, DENIED_REASON, REVOKED, EXPIRED, ENDED }

    fun noticeOf(me: String?, ev: AccessEvent): Notice? {
        val mine = me != null && me == ev.request.requesterId
        return when (ev.event) {
            "created" -> if (!mine) Notice.CREATED else null
            "approved" -> if (mine) Notice.APPROVED else null
            "denied" -> if (!mine) null else if (ev.request.decisionReason.isNullOrBlank()) Notice.DENIED else Notice.DENIED_REASON
            "revoked" -> if (mine) Notice.REVOKED else null
            "expired" -> if (mine) Notice.EXPIRED else null
            "ended" -> if (mine) Notice.ENDED else null
            else -> null
        }
    }

    /** Who asked, for people: their name, else their email. */
    fun requesterName(r: AccessRequestInfo): String = r.requesterName.ifBlank { r.requesterEmail }

    // ----- Session close reasons -----

    /** Why the server closed a session that the app explains in words. */
    val CLOSE_REASONS = listOf("access_expired", "access_revoked", "vault_access_revoked")

    fun isCloseReason(reason: String?): Boolean = reason != null && reason in CLOSE_REASONS

    /**
     * The reason to keep when a session's close reasons arrive one after
     * another: the server closes it with `access_expired`/`access_revoked`
     * and then ends the shared session with `session_ended`; the first one
     * stays.
     */
    fun keptReason(previous: String?, next: String): String =
        if (isCloseReason(previous) && !isCloseReason(next)) previous!! else next

    // ----- The CA -----

    /** The command to paste on a host: the script in a heredoc, run as root (never `curl … | sh`). */
    fun installCommand(script: String): String = "sudo sh -s <<'TERMOAK_CA'\n${script.trimEnd('\n')}\nTERMOAK_CA\n"

    /** The `sshd_config` lines with the chosen options. */
    fun sshdLines(o: CaOverview, krl: Boolean, principals: Boolean): List<String> =
        o.sshdConfig + o.sshdConfigOptional.filter {
            (it.startsWith("RevokedKeys") && krl) || (it.startsWith("AuthorizedPrincipalsFile") && principals)
        }

    /** A member's principal (`{member}`): 1–64 of `[A-Za-z0-9._@+-]`. */
    fun principalValid(p: String): Boolean = p.length in 1..64 && p.all { it.isLetterOrDigit() && it.code < 128 || it in "._@+-" }

    /** The default principal: the email's local part, lowercased, other characters as `_`. */
    fun defaultPrincipal(email: String): String =
        email.substringBefore('@').lowercase().map { if (it in 'a'..'z' || it in '0'..'9' || it in "._-") it else '_' }.joinToString("")

    /** Principal templates from a comma-separated field (empty entries dropped). */
    fun templates(text: String): List<String> = text.split(',').map { it.trim() }.filter { it.isNotEmpty() }

    /** A rotation overlap (hours), 1–720; `null` when not valid. */
    fun overlapHours(text: String): Int? = text.trim().toIntOrNull()?.takeIf { it in 1..720 }

    /** Minutes in a policy field within [range]; `null` when not valid. */
    fun minutes(text: String, range: IntRange): Int? = text.trim().toIntOrNull()?.takeIf { it in range }

    /** An OpenSSH public key line (to revoke a key). */
    fun looksLikePublicKey(s: String): Boolean {
        val parts = s.trim().split(Regex("\\s+"))
        return parts.size >= 2 && (parts[0].startsWith("ssh-") || parts[0].startsWith("ecdsa-") || parts[0].startsWith("sk-")) &&
            parts[1].length > 40 && parts[1].all { it.isLetterOrDigit() || it in "+/=" }
    }
}
