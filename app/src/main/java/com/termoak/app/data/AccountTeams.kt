package com.termoak.app.data

import com.termoak.ffi.AccountHandle
import com.termoak.ffi.TermoakException
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** A team member: their id on the server, email, name and role (`owner`, `admin`, `member`). */
data class TeamPerson(val userId: String, val email: String, val name: String, val role: String)

/**
 * Managing the teams of **one account** (any signed-in one), with the same
 * endpoints as the engine's team calls, which only work on the current
 * account: create, rename, delete, members (add by email, role, remove) and
 * leave. Owners and admins manage; only owners appoint owners and delete.
 */
class AccountTeams(private val handle: AccountHandle) {
    suspend fun create(name: String) {
        handle.apiPost("/api/v1/teams", JSONObject().put("name", name.trim()).toString())
    }

    suspend fun rename(teamId: String, name: String) {
        handle.apiPatch(teamPath(teamId), JSONObject().put("name", name.trim()).toString())
    }

    suspend fun delete(teamId: String) {
        handle.apiDelete(teamPath(teamId))
    }

    suspend fun members(teamId: String): List<TeamPerson> = parsed { parseMembers(handle.apiGet("${teamPath(teamId)}/members")) }

    suspend fun add(teamId: String, email: String, role: String): List<TeamPerson> = parsed {
        parseMembers(handle.apiPost("${teamPath(teamId)}/members", JSONObject().put("email", email.trim()).put("role", role).toString()))
    }

    suspend fun setRole(teamId: String, userId: String, role: String): List<TeamPerson> = parsed {
        parseMembers(handle.apiPatch("${teamPath(teamId)}/members/${segment(userId)}", JSONObject().put("role", role).toString()))
    }

    suspend fun remove(teamId: String, userId: String) {
        handle.apiDelete("${teamPath(teamId)}/members/${segment(userId)}")
    }

    /** Your own id on that server (to mark you in the list and to leave). */
    suspend fun myId(): String? = parsed { JSONObject(handle.apiGet("/api/v1/me")).optJSONObject("user")?.optString("id")?.ifEmpty { null } }

    suspend fun leave(teamId: String) {
        val me = myId() ?: throw TermoakException.Server("unknown user")
        remove(teamId, me)
    }

    private inline fun <T> parsed(block: () -> T): T = try {
        block()
    } catch (e: JSONException) {
        throw TermoakException.Server(e.message ?: "invalid response")
    }

    companion object {
        const val OWNER = "owner"
        const val ADMIN = "admin"
        const val MEMBER = "member"
        val Roles = listOf(MEMBER, ADMIN, OWNER)

        private val Id = Regex("^[A-Za-z0-9-]{1,64}$")

        private fun segment(id: String): String {
            if (!Id.matches(id)) throw TermoakException.Invalid("invalid id")
            return id
        }

        fun teamPath(teamId: String) = "/api/v1/teams/${segment(teamId)}"

        fun parseMembers(json: String): List<TeamPerson> {
            val a = JSONArray(json)
            return (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map { m ->
                TeamPerson(m.optString("user_id"), m.optString("email"), m.optString("name"), m.optString("role"))
            }
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
