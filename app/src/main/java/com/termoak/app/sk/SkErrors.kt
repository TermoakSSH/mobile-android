package com.termoak.app.sk

import com.termoak.app.R
import com.termoak.app.UiText
import com.termoak.app.data.ApiErrors
import com.termoak.app.uiText
import com.termoak.ffi.SecurityKeyException
import com.termoak.ffi.SecurityKeySignRequest
import com.termoak.ffi.TermoakException

/**
 * Security key errors in words (the desktop's and the web's texts): by
 * their code (`apiError.code` `security_key_*` of the engine and the
 * server), and from the
 * English text of the server's session reasons, host tests and `/exec`
 * results, which carry no code.
 */
object SkErrors {
    /** The `security_key_*` codes (the engine's `apiError.code` and the server's). */
    enum class Code(val api: String) {
        REQUIRED("security_key_required"),
        NO_DEVICE("security_key_no_device"),
        TIMEOUT("security_key_timeout"),
        CANCELLED("security_key_cancelled"),
        NOT_FOUND("security_key_not_found"),
        PIN_REQUIRED("security_key_pin_required"),
        PIN_INVALID("security_key_pin_invalid"),
        WRONG_KEY("security_key_wrong_key"),
        UNSUPPORTED("security_key_unsupported"),
        INVALID("security_key_invalid"),
        FAILED("security_key_failed"),
        ;

        companion object {
            /** `security_key_pin_invalid` → [PIN_INVALID]; `null`: not a security key code. */
            fun parse(code: String?): Code? {
                val c = code?.trim()?.lowercase() ?: return null
                return entries.firstOrNull { it.api == c }
            }
        }
    }

    /** The CTAP status of a device error (CTAP2_ERR_*) as a code, like the desktop's ctap.rs. */
    fun ofCtap(status: Int): Code = when (status and 0xFF) {
        // CTAP2_ERR_NO_CREDENTIALS, CTAP2_ERR_INVALID_CREDENTIAL
        0x2E, 0x22 -> Code.NOT_FOUND
        // CTAP2_ERR_PIN_REQUIRED / PUAT_REQUIRED, PIN not set
        0x36, 0x35 -> Code.PIN_REQUIRED
        // PIN invalid, PIN auth invalid; blocked (0x32 reset, 0x34 power cycle)
        0x31, 0x33, 0x32, 0x34 -> Code.PIN_INVALID
        // CTAP2_ERR_USER_ACTION_TIMEOUT, CTAP2_ERR_ACTION_TIMEOUT, CTAP1 timeout
        0x2F, 0x3A, 0x05 -> Code.TIMEOUT
        // CTAP2_ERR_KEEPALIVE_CANCEL, CTAP2_ERR_OPERATION_DENIED
        0x2D, 0x27 -> Code.CANCELLED
        // Unsupported algorithm, option or command; key store full.
        0x26, 0x2B, 0x2C, 0x01, 0x28 -> Code.UNSUPPORTED
        else -> Code.FAILED
    }

    /** The handler's exception for a CTAP status. */
    fun exceptionOfCtap(status: Int, name: String): SecurityKeyException {
        val s = status and 0xFF
        if (s == 0x32 || s == 0x34) {
            return SecurityKeyException.PinInvalid("the PIN is blocked: unplug the key and plug it in again (or reset it)")
        }
        return exception(ofCtap(s), String.format("CTAP error 0x%02X %s", s, name).trim())
    }

    /** The handler's exception of [code]. */
    fun exception(code: Code, message: String): SecurityKeyException = when (code) {
        Code.NO_DEVICE, Code.REQUIRED -> SecurityKeyException.NoDevice(message)
        Code.TIMEOUT -> SecurityKeyException.Timeout(message)
        Code.CANCELLED -> SecurityKeyException.Cancelled(message)
        Code.NOT_FOUND -> SecurityKeyException.NotFound(message)
        Code.PIN_REQUIRED -> SecurityKeyException.PinRequired(message)
        Code.PIN_INVALID -> SecurityKeyException.PinInvalid(message)
        Code.UNSUPPORTED -> SecurityKeyException.Unsupported(message)
        Code.WRONG_KEY, Code.INVALID, Code.FAILED -> SecurityKeyException.Failed(message)
    }

    /** The text a handler exception carries (its `reason`; UniFFI's `message` is "reason=…"). */
    fun messageOf(e: SecurityKeyException): String = when (e) {
        is SecurityKeyException.NoDevice -> e.reason
        is SecurityKeyException.Timeout -> e.reason
        is SecurityKeyException.Cancelled -> e.reason
        is SecurityKeyException.NotFound -> e.reason
        is SecurityKeyException.PinRequired -> e.reason
        is SecurityKeyException.PinInvalid -> e.reason
        is SecurityKeyException.Unsupported -> e.reason
        is SecurityKeyException.Failed -> e.reason
    }

    /**
     * The user's text of a code, with the device's own message after it
     * when it adds something (failures, unsupported, a blocked PIN).
     */
    fun text(code: Code, message: String? = null): UiText {
        val base = uiText(
            when (code) {
                Code.REQUIRED -> R.string.sk_error_required
                Code.NO_DEVICE -> R.string.sk_error_no_device
                Code.TIMEOUT -> R.string.sk_error_timeout
                Code.CANCELLED -> R.string.sk_error_cancelled
                Code.NOT_FOUND -> R.string.sk_error_not_found
                Code.PIN_REQUIRED -> R.string.sk_error_pin_required
                Code.PIN_INVALID -> R.string.sk_error_pin_invalid
                Code.WRONG_KEY -> R.string.sk_error_wrong_key
                Code.UNSUPPORTED -> R.string.sk_error_unsupported
                Code.INVALID -> R.string.sk_error_invalid
                Code.FAILED -> R.string.sk_error_failed
            },
        )
        val detail = message?.trim()?.removePrefix("security key: ").orEmpty()
        val adds = code == Code.FAILED || code == Code.UNSUPPORTED || code == Code.INVALID ||
            (code == Code.PIN_INVALID && detail.contains("blocked"))
        return if (adds && detail.isNotEmpty() && codeOfMessage(detail) == null) {
            uiText(R.string.sk_error_with_detail, base, detail)
        } else base
    }

    /** The text of an engine error that is a security key one (by its code), or `null`. */
    fun textOf(e: Throwable): UiText? {
        val info = ApiErrors.of(e)
        Code.parse(info?.code)?.let { return text(it, info?.message) }
        if (e is TermoakException.SecurityKey) {
            return text(codeOfMessage(e.message.orEmpty()) ?: Code.FAILED, e.message)
        }
        return null
    }

    private val PATTERNS = listOf(
        "none of your devices that can use security keys" to Code.NO_DEVICE,
        "no device with a security key is available" to Code.NO_DEVICE,
        "no security key is plugged" to Code.NO_DEVICE,
        "no security key is connected" to Code.NO_DEVICE,
        "nobody confirmed the security key" to Code.TIMEOUT,
        "security key was not touched in time" to Code.TIMEOUT,
        "security key request was cancelled" to Code.CANCELLED,
        "is a security key and none can be used here" to Code.REQUIRED,
        "this key is a security key and none can be used" to Code.REQUIRED,
        "security key does not have this key" to Code.NOT_FOUND,
        "security key needs its pin" to Code.PIN_REQUIRED,
        "wrong pin for the security key" to Code.PIN_INVALID,
        "security key's signature does not match" to Code.WRONG_KEY,
    )

    /** The code of an English text of the server or the engine, if it is one of theirs. */
    fun codeOfMessage(message: String): Code? {
        val m = message.lowercase()
        return PATTERNS.firstOrNull { m.contains(it.first) }?.second
    }

    /** A text of the server (session reason, `/exec` result, host test): the app's words when it is a security key one. */
    fun serverText(message: String): UiText? = codeOfMessage(message)?.let { text(it) }

    /**
     * A server session's "connecting" message in the app's language when it
     * is the server's "Touch your security key (X) on one of your devices…";
     * `null` for any other message.
     */
    fun sessionMessage(message: String): UiText? {
        val rest = message.removePrefix("Touch your security key").takeIf { it != message } ?: return null
        val key = rest.trimStart().takeIf { it.startsWith("(") }?.substring(1)?.substringBefore(')', "")
        return if (!key.isNullOrBlank()) uiText(R.string.sk_session_touch_key, key) else uiText(R.string.sk_session_touch)
    }

    /** What a request is for. */
    fun purposeText(purpose: SkPurpose): UiText = uiText(
        when (purpose) {
            is SkPurpose.Sign -> when (purpose.request.purpose) {
                "server_session" -> R.string.sk_purpose_server_session
                "test" -> R.string.sk_purpose_test
                "server" -> R.string.sk_purpose_server
                else -> R.string.sk_purpose_connect
            }
            is SkPurpose.Create -> R.string.sk_purpose_create
            SkPurpose.LoadResident -> R.string.sk_purpose_load
        },
    )

    /** "deploy@web1 (10.0.0.5)" of a sign request. */
    fun targetText(r: SecurityKeySignRequest): String {
        val host = r.host.ifEmpty { r.address }
        var s = if (r.username.isEmpty()) host else "${r.username}@$host"
        if (r.address.isNotEmpty() && r.address != host) s += " (${r.address})"
        return s
    }

    /** The key of a sign request: its label and the device it lives on. */
    fun keyText(r: SecurityKeySignRequest): String {
        val d = r.device?.takeIf { it.isNotBlank() }
        return if (d != null && d != r.keyLabel) "${r.keyLabel} · $d" else r.keyLabel
    }
}
