package com.termoak.app.data

import com.termoak.ffi.ApiErrorInfo

/**
 * A stand-in for the engine's `apiError` (its native library can't load on
 * the JVM): the status, code and message of "the server responded 409
 * (code): message", and the details registered for that message with [with],
 * as the engine keeps them for its recent errors.
 */
object FakeApiErrors {
    private val details = mutableMapOf<String, String>()
    private val rules = mutableMapOf<String, ApiErrorInfo>()
    private val API = Regex("""^the server responded (\d{3}) \(([A-Za-z0-9_.-]+)\): (.*)$""", RegexOption.DOT_MATCHES_ALL)

    fun install() {
        details.clear()
        rules.clear()
        ApiErrors.lookup = { e -> info(e.message.orEmpty()) }
    }

    /** The engine's text of a server error, with its details. */
    fun with(status: Int, code: String, message: String, detailsJson: String = "{}"): String {
        val text = "the server responded $status ($code): $message"
        details[text] = detailsJson
        return text
    }

    /** A rule of the engine (status 0), e.g. a network host on This device. */
    fun device(text: String, code: String, detailsJson: String = "{}"): String {
        rules[text] = ApiErrorInfo(0u, code, text, detailsJson)
        return text
    }

    private fun info(text: String): ApiErrorInfo? {
        rules[text]?.let { return it }
        val m = API.matchEntire(text) ?: return null
        return ApiErrorInfo(m.groupValues[1].toUShort(), m.groupValues[2], m.groupValues[3], details[text] ?: "{}")
    }
}
