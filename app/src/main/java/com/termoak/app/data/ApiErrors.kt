package com.termoak.app.data

import com.termoak.ffi.ApiErrorInfo
import com.termoak.ffi.TermoakException
import com.termoak.kit.apiError
import org.json.JSONObject

/**
 * The structured part of the engine's errors (core 0.7): HTTP status, the
 * server's stable code and its extra fields (`vaults` of `shared_vaults`,
 * `teams` of `last_team_owner`, `max` of `plan_limit`, `netd_code`...), no
 * longer read from the text "the server responded 409 (code): …".
 */
object ApiErrors {
    /**
     * The engine's lookup (`e.apiError`, com.termoak.kit). The JVM tests,
     * where the native library can't load, give a stand-in.
     */
    @Volatile var lookup: (TermoakException) -> ApiErrorInfo? = { it.apiError }

    /** The error's status, code and details, or `null` (not a server or device-rule error). */
    fun of(e: Throwable): ApiErrorInfo? = (e as? TermoakException)?.let { runCatching { lookup(it) }.getOrNull() }

    /** Its details as an object (`{}` when there are none). */
    fun details(info: ApiErrorInfo?): JSONObject =
        info?.detailsJson?.let { runCatching { JSONObject(it) }.getOrNull() } ?: JSONObject()
}
