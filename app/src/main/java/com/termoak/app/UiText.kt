package com.termoak.app

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.termoak.ffi.TermoakException

/**
 * Text for the user produced outside the UI (terminal states, errors): a
 * string resource, resolved in the app language when it is shown, or a text
 * that is already final, such as a message from the core or the server.
 */
sealed interface UiText {
    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText
    data class Raw(val text: String) : UiText

    fun resolve(resources: Resources): String = when (this) {
        is Raw -> text
        is Res -> resources.getString(id, *args.toTypedArray())
    }
}

fun uiText(@StringRes id: Int, vararg args: Any): UiText = UiText.Res(id, args.toList())

/** The error's message (from the core or the server) or, without one, [fallback]. */
fun Throwable.toUiText(@StringRes fallback: Int): UiText =
    message?.takeIf { it.isNotBlank() }?.let { UiText.Raw(it) } ?: UiText.Res(fallback)

@Composable
fun UiText.asString(): String = when (this) {
    is UiText.Raw -> text
    is UiText.Res -> stringResource(id, *args.toTypedArray())
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
