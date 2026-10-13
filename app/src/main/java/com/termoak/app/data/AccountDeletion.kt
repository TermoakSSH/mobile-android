package com.termoak.app.data

import com.termoak.ffi.AccountHandle
import com.termoak.ffi.HttpMethod
import com.termoak.ffi.TermoakException
import org.json.JSONObject

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
     * The only owner of teams with other people: [teams] are their names (read
     * from the server's message, the engine's error has no details), or
     * empty, and then [message] is the server's own text.
     */
    data class LastTeamOwner(val teams: List<String>, val message: String?) : DeleteOutcome

    /**
     * The account has vaults shared with other people: confirm and send again
     * with `delete_shared_vaults`. The server lists them, but the engine's
     * error keeps only its message, so the confirmation doesn't name them.
     */
    data object SharedVaults : DeleteOutcome

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

        private val API_ERROR = Regex("""responded (\d{3})[^(]*\(([A-Za-z0-9_.-]+)\):\s*(.*)""", RegexOption.DOT_MATCHES_ALL)

        /**
         * The HTTP status, the server's error code and its message, from the
         * engine's error text ("the server responded 409 (last_admin): …"):
         * the engine keeps only that of the server's error.
         */
        fun apiError(e: Throwable): Triple<Int, String, String>? =
            e.message?.let { API_ERROR.find(it) }?.let { m ->
                Triple(m.groupValues[1].toInt(), m.groupValues[2], m.groupValues[3].trim())
            }

        /** The team names, quoted in the server's `last_team_owner` message. */
        fun teamsOf(message: String): List<String> =
            Regex("\"([^\"]+)\"").findAll(message).map { it.groupValues[1] }.toList()

        fun outcomeOf(e: Throwable): DeleteOutcome {
            if (e is TermoakException.TotpRequired) return DeleteOutcome.TotpRequired
            if (e is TermoakException.TotpInvalid) return DeleteOutcome.TotpInvalid
            val api = apiError(e)
            return when (api?.second) {
                "invalid_password" -> DeleteOutcome.InvalidPassword
                "totp_required" -> DeleteOutcome.TotpRequired
                "totp_invalid" -> DeleteOutcome.TotpInvalid
                "last_admin" -> DeleteOutcome.LastAdmin
                "last_team_owner" -> DeleteOutcome.LastTeamOwner(teamsOf(api.third), api.third.ifBlank { null })
                "shared_vaults" -> DeleteOutcome.SharedVaults
                else -> when {
                    // No such route (or method) on an older server.
                    api?.first == 404 || api?.first == 405 -> DeleteOutcome.Unsupported
                    e is TermoakException.NotFound -> DeleteOutcome.Unsupported
                    else -> DeleteOutcome.Failed(e)
                }
            }
        }
    }
}
