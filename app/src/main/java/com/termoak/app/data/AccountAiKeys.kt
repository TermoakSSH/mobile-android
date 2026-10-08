package com.termoak.app.data

import com.termoak.ffi.AccountHandle
import com.termoak.ffi.AiKeyInfo
import com.termoak.ffi.AiKeyTestResult
import com.termoak.ffi.TermoakException
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Your own AI API keys on **one account** (the one picked in the AI section),
 * not only the current one: `TermoakCore.listAiKeys` and friends work on the
 * current account, so these go through the account's handle and the same
 * endpoints (`/api/v1/me/ai/keys…`) the engine uses.
 */
class AccountAiKeys(private val handle: AccountHandle) {
    suspend fun list(): List<AiKeyInfo> = parsed { parseKeys(handle.apiGet(PATH)) }

    /** Saves the key (`null`: only the model changes) and the model (empty: the provider's default). */
    suspend fun set(provider: String, key: String?, model: String?): AiKeyInfo =
        parsed { parseKey(JSONObject(handle.apiPut(path(provider), setBody(key, model)))) }

    /** `false` if there was no key. */
    suspend fun delete(provider: String): Boolean {
        val reply = handle.apiDelete(path(provider))
        return runCatching { JSONObject(reply).optBoolean("deleted", true) }.getOrDefault(true)
    }

    /** Checks [key] (or the saved one when `null`) with its provider. */
    suspend fun test(provider: String, key: String?): AiKeyTestResult =
        parsed { parseTest(handle.apiPost("${path(provider)}/test", testBody(key))) }

    /** A reply that isn't the JSON expected is a server error, like in the engine. */
    private inline fun <T> parsed(block: () -> T): T = try {
        block()
    } catch (e: JSONException) {
        throw TermoakException.Server(e.message ?: "invalid response")
    }

    companion object {
        const val PATH = "/api/v1/me/ai/keys"

        /** The provider as a path segment (the engine's rule): letters, digits, `-`, `_` and `.`. */
        fun path(provider: String): String {
            val p = provider.trim()
            if (p.isEmpty() || p.length > 64 || !p.all { it.isLetterOrDigit() && it.code < 128 || it in "-_." }) {
                throw TermoakException.Invalid("invalid AI provider \"${p.take(64)}\"")
            }
            return "$PATH/$p"
        }

        fun setBody(key: String?, model: String?): String = JSONObject().apply {
            put("model", model?.trim()?.takeIf { it.isNotEmpty() } ?: JSONObject.NULL)
            key?.let { put("key", it.trim()) }
        }.toString()

        fun testBody(key: String?): String =
            JSONObject().put("key", key?.trim()?.takeIf { it.isNotEmpty() } ?: JSONObject.NULL).toString()

        fun parseKeys(json: String): List<AiKeyInfo> {
            val trimmed = json.trim()
            // An array; tolerate `{"keys": [...]}` too.
            val array = if (trimmed.startsWith("[")) JSONArray(trimmed) else JSONObject(trimmed).optJSONArray("keys") ?: JSONArray()
            return (0 until array.length()).mapNotNull { array.optJSONObject(it)?.let(::parseKey) }
        }

        fun parseKey(o: JSONObject): AiKeyInfo = AiKeyInfo(
            provider = o.optString("provider"),
            label = o.optString("label"),
            model = o.optString("model").takeIf { !o.isNull("model") && it.isNotEmpty() },
            hint = o.optString("hint"),
            createdAt = o.optLong("created_at"),
            updatedAt = o.optLong("updated_at"),
        )

        fun parseTest(json: String): AiKeyTestResult {
            val o = JSONObject(json)
            return AiKeyTestResult(
                ok = o.optBoolean("ok"),
                error = o.optString("error").takeIf { !o.isNull("error") && it.isNotEmpty() },
                status = if (o.has("status") && !o.isNull("status")) o.optInt("status").toUShort() else null,
            )
        }
    }
}
