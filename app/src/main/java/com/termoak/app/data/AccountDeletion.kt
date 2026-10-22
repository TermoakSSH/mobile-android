package com.termoak.app.data

import com.termoak.ffi.AccountHandle
import com.termoak.ffi.ApiErrorInfo
import com.termoak.ffi.HttpMethod
import com.termoak.ffi.TermoakException
import org.json.JSONObject

/** A shared vault that would go with the account: its name and how many people use it. */
data class SharedVault(val name: String, val members: Int?)

/** What the server answered to deleting the account (`DELETE /api/v1/me`). */
sealed interface DeleteOutcome {
    /** Deleted: remove the account from this device. */
    data object Deleted : DeleteOutcome

    data object InvalidPassword : DeleteOutcome

    /** The account has two-step verification: ask for the code. */
    data object TotpRequired : DeleteOutcome

    data object TotpInvalid : DeleteOutcome

    /** The only administrator of the server. */
    data object LastAdmin : DeleteOutcome

    /**
     * The only owner of teams with other people: [teams] are their names (the
     * error's `teams`), or empty, and then [message] is the server's own text.
     */
    data class LastTeamOwner(val teams: List<String>, val message: String?) : DeleteOutcome

    /**
     * The account has vaults shared with other people: confirm and send again
     * with `delete_shared_vaults`. [vaults] are the ones the server listed
     * (the error's `vaults`), to name them in the confirmation.
     */
    data class SharedVaults(val vaults: List<SharedVault>) : DeleteOutcome

    /** An older server without `DELETE /api/v1/me`: it's done on its web. */
    data object Unsupported : DeleteOutcome

    /** Anything else (network, server...): show the error. */
    data class Failed(val error: Throwable) : DeleteOutcome
}

/**
 * Deleting an account on its server, through the account's handle (the
 * engine's generic API): the same request and checks as the web's Account
 * page. [request] is the handle's `apiRequest` (tests give a stand-in).
 */
class AccountDeletion(private val request: suspend (HttpMethod, String, String?) -> String) {
    constructor(handle: AccountHandle) : this({ method, path, body -> handle.apiRequest(method, path, body) })

    /** Whether the account has two-step verification (`null`: unknown, the server didn't answer). */
    suspend fun totpEnabled(): Boolean? = try {
        totpOf(request(HttpMethod.GET, PATH, null))
    } catch (e: TermoakException) {
        null
    }

    /** Deletes the account. [totpCode] only when the account has two-step verification. */
    suspend fun delete(password: String, totpCode: String?, deleteSharedVaults: Boolean): DeleteOutcome = try {
        request(HttpMethod.DELETE, PATH, body(password, totpCode, deleteSharedVaults))
        DeleteOutcome.Deleted
    } catch (e: TermoakException) {
        outcomeOf(e)
    }

    companion object {
        const val PATH = "/api/v1/me"

        /** The word typed to confirm must be exactly [word] (spaces around it don't count). */
        fun confirmed(typed: String, word: String): Boolean = typed.trim() == word

        /** "Delete my account forever" can be pressed. */
        fun canSubmit(password: String, typed: String, word: String, needsCode: Boolean, code: String): Boolean =
            password.isNotEmpty() && confirmed(typed, word) && (!needsCode || code.isNotBlank())

        fun body(password: String, totpCode: String?, deleteSharedVaults: Boolean): String = JSONObject().apply {
            put("password", password)
            totpCode?.trim()?.takeIf { it.isNotEmpty() }?.let { put("totp_code", it) }
            if (deleteSharedVaults) put("delete_shared_vaults", true)
        }.toString()

        /** `totp_enabled` of `GET /api/v1/me` (the engine's `two_factor`). */
        fun totpOf(json: String): Boolean? = runCatching {
            val o = JSONObject(json)
            val user = o.optJSONObject("user") ?: o
            if (user.has("totp_enabled")) user.optBoolean("totp_enabled") else null
        }.getOrNull()

        /** The `teams` of a `last_team_owner` refusal. */
        fun teamsOf(info: ApiErrorInfo): List<String> {
            val a = ApiErrors.details(info).optJSONArray("teams") ?: return emptyList()
            return (0 until a.length()).mapNotNull { a.optString(it, "").ifBlank { null } }
        }

        /** The `vaults` of a `shared_vaults` refusal (`[{id, name, member_count}]`). */
        fun vaultsOf(info: ApiErrorInfo): List<SharedVault> {
            val a = ApiErrors.details(info).optJSONArray("vaults") ?: return emptyList()
            return (0 until a.length()).mapNotNull { i ->
                val v = a.optJSONObject(i) ?: return@mapNotNull null
                val name = v.optString("name", "").ifBlank { return@mapNotNull null }
                SharedVault(name, if (v.has("member_count") && !v.isNull("member_count")) v.optInt("member_count") else null)
            }
        }

        /** What a refusal means, from the error's status, code and details ([ApiErrors], the engine's `apiError`). */
        fun outcomeOf(e: Throwable, info: ApiErrorInfo? = ApiErrors.of(e)): DeleteOutcome {
            if (e is TermoakException.TotpRequired) return DeleteOutcome.TotpRequired
            if (e is TermoakException.TotpInvalid) return DeleteOutcome.TotpInvalid
            return when (info?.code) {
                "invalid_password" -> DeleteOutcome.InvalidPassword
                "totp_required" -> DeleteOutcome.TotpRequired
                "totp_invalid" -> DeleteOutcome.TotpInvalid
                "last_admin" -> DeleteOutcome.LastAdmin
                "last_team_owner" -> DeleteOutcome.LastTeamOwner(teamsOf(info), info.message.ifBlank { null })
                "shared_vaults" -> DeleteOutcome.SharedVaults(vaultsOf(info))
                else -> when {
                    // No such route (or method) on an older server.
                    info?.status?.toInt() == 404 || info?.status?.toInt() == 405 -> DeleteOutcome.Unsupported
                    e is TermoakException.NotFound -> DeleteOutcome.Unsupported
                    else -> DeleteOutcome.Failed(e)
                }
            }
        }
    }
}
