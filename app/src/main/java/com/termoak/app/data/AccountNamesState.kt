package com.termoak.app.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.termoak.ffi.AccountInfo
import com.termoak.ffi.AccountNames
import com.termoak.ffi.accountDisplayEmail
import com.termoak.ffi.accountDisplayName
import com.termoak.ffi.accountInitial
import com.termoak.ffi.maskEmail

/**
 * The names you give your accounts on this device ("Work", "Personal") and
 * "Hide email addresses" (Settings → Privacy), as the engine's rules take
 * them, like the desktop's and iOS's. One shared copy, kept up to date from
 * [Prefs] by the app (Compose state: what shows a name is redrawn when it
 * changes).
 */
object AccountNamesState {
    var names by mutableStateOf(AccountNames())
}

/** Its alias, or its email (masked when emails are hidden). */
val AccountInfo.displayName: String
    get() = runCatching { accountDisplayName(AccountNamesState.names, id, email) }.getOrDefault(email)

/** Its email as shown (masked when emails are hidden). */
val AccountInfo.displayEmail: String
    get() = runCatching { accountDisplayEmail(AccountNamesState.names, email) }.getOrDefault(email)

/** The alias given to it on this device. */
val AccountInfo.alias: String? get() = AccountNamesState.names.aliases[id]

/** Letter of its avatar (from the alias, the name or the email). */
val AccountInfo.initial: String
    get() = runCatching { accountInitial(AccountNamesState.names, id, name, email) }.getOrNull()?.ifEmpty { null } ?: "?"

/** Someone else's email (vault and team members, sharing): masked too when emails are hidden. */
fun shownEmail(email: String): String =
    if (AccountNamesState.names.hideEmails) runCatching { maskEmail(email) }.getOrDefault(email) else email
