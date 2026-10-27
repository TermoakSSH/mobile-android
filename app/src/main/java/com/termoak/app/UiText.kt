package com.termoak.app

import android.content.res.Resources
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.termoak.app.data.ApiErrors
import com.termoak.app.data.Networks
import com.termoak.ffi.TermoakException

/**
 * Text for the user produced outside the UI (terminal states, errors): a
 * string resource, resolved in the app language when it is shown, or a text
 * that is already final, such as a message from the core or the server.
 */
sealed interface UiText {
    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText
    data class Plural(@PluralsRes val id: Int, val count: Int, val args: List<Any> = emptyList()) : UiText
    data class Raw(val text: String) : UiText

    fun resolve(resources: Resources): String = when (this) {
        is Raw -> text
        is Res -> resources.getString(id, *args.map { if (it is UiText) it.resolve(resources) else it }.toTypedArray())
        is Plural -> resources.getQuantityString(id, count, *args.map { if (it is UiText) it.resolve(resources) else it }.toTypedArray())
    }
}

fun uiText(@StringRes id: Int, vararg args: Any): UiText = UiText.Res(id, args.toList())

/**
 * The error's text: a translated one for the errors of vaults and accounts
 * the app knows, otherwise its message (from the core or the server) or,
 * without one, [fallback].
 */
fun Throwable.toUiText(@StringRes fallback: Int): UiText =
    com.termoak.app.sk.SkErrors.textOf(this)
        // Teams security codes (0.7 phase 4d): always the app's text.
        ?: com.termoak.app.data.AccessTexts.of(this)
        // E2EE, SSO and audit codes (0.7 phase 5c): always the app's text.
        ?: com.termoak.app.data.E2eeTexts.of(this)
        ?: networkError(this)
        ?: knownError(this)?.let { UiText.Res(it) }
        ?: message?.takeIf { it.isNotBlank() }?.let { UiText.Raw(it) }
        ?: UiText.Res(fallback)

/** [toUiText], resolved: for snackbars. */
fun Throwable.userMessage(resources: Resources, @StringRes fallback: Int): String = toUiText(fallback).resolve(resources)

/**
 * The friendly text of a private network error (0.7), from its code and
 * details (`apiError`): the server's (`network_dial_failed` with its
 * `netd_code`, `plan_limit` of `max_networks`, `key_exists`...) and the
 * engine's own (a network host on This device or signed out). `null`: not
 * one of them.
 */
fun networkError(e: Throwable): UiText? {
    val info = ApiErrors.of(e) ?: return if (e is TermoakException.NetworkUnavailable) UiText.Res(R.string.error_network_unavailable) else null
    return Networks.errorText(info.code, info.detailsJson, info.message, device = info.status.toInt() == 0)
}

/** Translated message of the errors that have a fixed meaning for the user (`null`: use the message). */
@StringRes
fun knownError(e: Throwable): Int? = when (e) {
    is TermoakException.VaultReadOnly -> R.string.error_vault_read_only
    is TermoakException.SecretHidden -> R.string.error_secret_hidden
    is TermoakException.UseOnlyStrict -> R.string.error_use_only_strict
    is TermoakException.UseOnlyNeedsServer -> R.string.error_use_only_needs_server
    is TermoakException.SessionExpired -> R.string.error_session_expired
    is TermoakException.EmailNotVerified -> R.string.error_email_not_verified
    is TermoakException.AiKeyRequired -> R.string.error_ai_key_required
    is TermoakException.AiBudgetExceeded -> R.string.error_ai_budget_exceeded
    is TermoakException.NotSupportedForTelnet -> R.string.error_not_supported_for_telnet
    // A transfer stopped with its TransferHandle (Cancel).
    is TermoakException.Cancelled -> R.string.error_cancelled
    else -> null
}

@Composable
fun UiText.asString(): String = when (this) {
    is UiText.Raw -> text
    is UiText.Res -> stringResource(id, *args.map { if (it is UiText) it.asString() else it }.toTypedArray())
    is UiText.Plural -> pluralStringResource(id, count, *args.map { if (it is UiText) it.asString() else it }.toTypedArray())
}

/**
 * The translated message of an error that is fixed in Settings → AI (no API
 * key the AI can use, or this month's AI credit spent), or `null` for any
 * other error.
 */
@StringRes
fun aiSetupError(e: Throwable): Int? = when (e) {
    is TermoakException.AiKeyRequired -> R.string.error_ai_key_required
    is TermoakException.AiBudgetExceeded -> R.string.error_ai_budget_exceeded
    else -> null
}
