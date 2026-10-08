package com.termoak.app.data

import com.termoak.ffi.AccountHandle
import com.termoak.ffi.Team
import com.termoak.ffi.TeamMember
import com.termoak.ffi.TeamRole

/** A team member: their id on the server, email, name and role (`owner`, `admin`, `member`). */
data class TeamPerson(val userId: String, val email: String, val name: String, val role: String) {
    companion object {
        fun of(m: TeamMember) = TeamPerson(m.userId, m.email, m.name, AccountTeams.roleKey(m.role))
    }
}

/**
 * What adding someone by email did: added (they have an account) or invited
 * to sign up (and emailed); [link] is the invitation to hand over when it
 * wasn't emailed.
 */
data class TeamAddResult(val members: List<TeamPerson>, val added: Boolean, val emailed: Boolean, val link: String? = null)

/**
 * Managing the teams of **one account** (any signed-in one), with the
 * engine's per-account team calls: list, create, rename, delete, members
 * (add or invite by email, role, remove) and leave. Owners and admins
 * manage; only owners appoint owners and delete.
 */
class AccountTeams(private val handle: AccountHandle) {
    suspend fun list(): List<Team> = handle.listTeams()

    suspend fun create(name: String) {
        handle.createTeam(name.trim())
    }

    suspend fun rename(teamId: String, name: String) {
        handle.renameTeam(teamId, name.trim())
    }

    suspend fun delete(teamId: String) {
        handle.deleteTeam(teamId)
    }

    suspend fun members(teamId: String): List<TeamPerson> = handle.listTeamMembers(teamId).map(TeamPerson::of)

    /** Adds someone with an account at once; otherwise the server creates an invitation to sign up (emailed when it can). */
    suspend fun add(teamId: String, email: String, role: String): TeamAddResult {
        val r = handle.inviteToTeam(teamId, email.trim(), roleOf(role))
        val link = r.invite?.let { inviteLink(it.server, it.token) }
        return TeamAddResult(r.members.map(TeamPerson::of), r.added, r.emailed, link)
    }

    /** The invitations of the team waiting to be used (not used, revoked or expired). */
    suspend fun invites(teamId: String): List<com.termoak.ffi.AccountInvite> {
        val now = System.currentTimeMillis() / 1000
        return handle.listTeamInvites(teamId).filter { it.usedAt == null && !it.revoked && (it.expiresAt ?: Long.MAX_VALUE) > now }
    }

    suspend fun revokeInvite(teamId: String, inviteId: String) {
        handle.revokeTeamInvite(teamId, inviteId)
    }

    suspend fun setRole(teamId: String, userId: String, role: String): List<TeamPerson> =
        handle.setTeamMemberRole(teamId, userId, roleOf(role)).map(TeamPerson::of)

    suspend fun remove(teamId: String, userId: String) {
        handle.removeTeamMember(teamId, userId)
    }

    /** Your own id on that server (to mark you in the list). */
    suspend fun myId(): String? = handle.currentUser().id.ifEmpty { null }

    suspend fun leave(teamId: String) {
        handle.leaveTeam(teamId)
    }

    companion object {
        const val OWNER = "owner"
        const val ADMIN = "admin"
        const val MEMBER = "member"
        val Roles = listOf(MEMBER, ADMIN, OWNER)

        fun roleKey(role: TeamRole): String = when (role) {
            TeamRole.OWNER -> OWNER
            TeamRole.ADMIN -> ADMIN
            TeamRole.MEMBER -> MEMBER
        }

        /** The web link of an invitation to sign up (it opens the app too, an App Link). */
        fun inviteLink(server: String, token: String): String = server.trimEnd('/') + "/invite/" + token

        fun roleOf(key: String): TeamRole = when (key) {
            OWNER -> TeamRole.OWNER
            ADMIN -> TeamRole.ADMIN
            else -> TeamRole.MEMBER
        }

        /** Who can do what: admins manage members (not owners), owners everything. */
        fun canManage(myRole: String?): Boolean = myRole == OWNER || myRole == ADMIN

        /** The roles [myRole] can give (only owners appoint owners). */
        fun assignable(myRole: String?): List<String> = when (myRole) {
            OWNER -> Roles
            ADMIN -> listOf(MEMBER, ADMIN)
            else -> emptyList()
        }
    }
}
