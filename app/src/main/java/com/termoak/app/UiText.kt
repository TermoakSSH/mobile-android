package com.termoak.app

import android.content.res.Resources
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
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
        is Res -> resources.getString(id, *args.toTypedArray())
        is Plural -> resources.getQuantityString(id, count, *args.toTypedArray())
    }
}

fun uiText(@StringRes id: Int, vararg args: Any): UiText = UiText.Res(id, args.toList())

/**
 * The error's text: a translated one for the errors of vaults and accounts
 * the app knows, otherwise its message (from the core or the server) or,
 * without one, [fallback].
 */
fun Throwable.toUiText(@StringRes fallback: Int): UiText =
    knownError(this)?.let { UiText.Res(it) }
        ?: message?.takeIf { it.isNotBlank() }?.let { UiText.Raw(it) }
        ?: UiText.Res(fallback)

/** [toUiText], resolved: for snackbars. */
fun Throwable.userMessage(resources: Resources, @StringRes fallback: Int): String = toUiText(fallback).resolve(resources)

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
    is UiText.Res -> stringResource(id, *args.toTypedArray())
    is UiText.Plural -> pluralStringResource(id, count, *args.toTypedArray())
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
