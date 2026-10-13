package com.termoak.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Pin
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.termoak.app.R
import com.termoak.app.TermoakApp
import com.termoak.app.UiText
import com.termoak.app.asString
import com.termoak.app.data.AccountDeletion
import com.termoak.app.data.DeleteOutcome
import com.termoak.app.data.displayEmail
import com.termoak.app.data.serverHost
import com.termoak.app.toUiText
import com.termoak.app.uiText
import com.termoak.ffi.AccountInfo
import com.termoak.ffi.AccountStatus
import kotlinx.coroutines.launch

/**
 * The form to delete an account on its server, as on the web's Account
 * page: password, the two-step code when the account has it, and the
 * confirmation word. A `shared_vaults` refusal asks [askVaults] and, if
 * confirmed, sends the same again with `delete_shared_vaults` (the server
 * checks the code after that refusal, so the same code works).
 */
@Stable
class DeleteAccountForm(private val deletion: AccountDeletion, val word: String) {
    var password by mutableStateOf("")
    var code by mutableStateOf("")
    var typed by mutableStateOf("")
    var needsCode by mutableStateOf(false)
    var busy by mutableStateOf(false)
        private set
    /** Error of the whole form (password, last owner, server...). */
    var error by mutableStateOf<UiText?>(null)
        private set
    /** Error of the two-step code. */
    var codeError by mutableStateOf<UiText?>(null)
        private set
    /** Showing the second confirmation for the shared vaults. */
    var askVaults by mutableStateOf(false)
        private set
    private var deleteShared = false

    val canSubmit: Boolean get() = !busy && AccountDeletion.canSubmit(password, typed, word, needsCode, code)

    /** Shows the code field when the account has two-step verification (`GET /api/v1/me`). */
    suspend fun loadTotp() {
        if (deletion.totpEnabled() == true) needsCode = true
    }

    /**
     * Sends the form; `true` when the account was deleted, after [deleted]
     * (removing it from this device) ran, still busy.
     */
    suspend fun submit(deleted: suspend () -> Unit = {}): Boolean = canSubmit && send(deleted)

    /** "Delete them with my account": sends the request again with `delete_shared_vaults`. */
    suspend fun confirmVaults(deleted: suspend () -> Unit = {}): Boolean {
        askVaults = false
        deleteShared = true
        return send(deleted)
    }

    fun cancelVaults() {
        askVaults = false
    }

    private suspend fun send(deleted: suspend () -> Unit): Boolean {
        busy = true
        error = null
        codeError = null
        try {
            val code = code.trim().takeIf { needsCode && it.isNotEmpty() }
            when (val r = deletion.delete(password, code, deleteShared)) {
                DeleteOutcome.Deleted -> {
                    deleted()
                    return true
                }
                DeleteOutcome.InvalidPassword -> error = uiText(R.string.account_delete_wrong_password)
                DeleteOutcome.TotpRequired -> {
                    needsCode = true
                    codeError = uiText(R.string.account_delete_code_required)
                }
                DeleteOutcome.TotpInvalid -> codeError = uiText(R.string.login_code_invalid)
                DeleteOutcome.LastAdmin -> error = uiText(R.string.account_delete_last_admin)
                is DeleteOutcome.LastTeamOwner -> error = when {
                    r.teams.isNotEmpty() -> uiText(R.string.account_delete_last_owner, r.teams.joinToString(", ") { "“$it”" })
                    r.message != null -> UiText.Raw(r.message)
                    else -> uiText(R.string.account_delete_failed)
                }
                DeleteOutcome.SharedVaults -> askVaults = true
                DeleteOutcome.Unsupported -> error = uiText(R.string.account_delete_unsupported)
                is DeleteOutcome.Failed -> error = r.error.toUiText(R.string.account_delete_failed)
            }
            return false
        } finally {
            busy = false
        }
    }
}

/**
 * Settings → Accounts, at the bottom: "Danger zone" with "Delete my
 * account…" for each signed-in account (not for This device's items).
 */
@Composable
fun DangerZoneSection(accounts: List<AccountInfo>, onDelete: (AccountInfo) -> Unit) {
    val signedIn = accounts.filter { it.status == AccountStatus.ACTIVE }
    if (signedIn.isEmpty()) return
    SectionLabel(stringResource(R.string.account_delete_section))
    CardBox {
        Column {
            signedIn.forEach { a ->
                ListItem(
                    modifier = Modifier.clickable { onDelete(a) }.testTag("delete-account-${a.id}"),
                    headlineContent = { Text(stringResource(R.string.account_delete_button), color = MaterialTheme.colorScheme.error) },
                    supportingContent = {
                        Text(
                            listOfNotNull(
                                stringResource(R.string.account_delete_subtitle),
                                // Several accounts: which one.
                                a.displayEmail.takeIf { accounts.size > 1 },
                            ).joinToString(" · "),
                        )
                    },
                    leadingContent = { Icon(Icons.Outlined.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }
    }
}

/**
 * Deleting [a] on its server: the form, the shared-vaults confirmation and,
 * once deleted, the account leaves this device as when signing out
 * (discarding what wasn't uploaded) and [onDeleted] tells it.
 */
@Composable
fun DeleteAccountFlow(app: TermoakApp, a: AccountInfo, onDone: () -> Unit, onDeleted: () -> Unit) {
    val scope = rememberCoroutineScope()
    val word = stringResource(R.string.account_delete_confirm_word)
    val form = remember(a.id) {
        val handle = app.accounts.handle(a.id)
        handle?.let { DeleteAccountForm(AccountDeletion(it), word) }
    }
    if (form == null) {
        LaunchedEffect(Unit) { onDone() }
        return
    }
    LaunchedEffect(form) { form.loadTotp() }

    // Deleted: the account leaves this device like signing out (the server's logout fails quietly).
    val remove: suspend () -> Unit = {
        runCatching { app.accounts.removeDeleted(a.id) }
        app.sessions.closeAccount(a.id)
    }

    fun finish(deleted: Boolean) {
        if (!deleted) return
        onDone()
        onDeleted()
    }

    DeleteAccountDialog(
        form, serverHost(a.serverUrl),
        onSubmit = { scope.launch { finish(form.submit(remove)) } },
        onDismiss = { if (!form.busy) onDone() },
    )
    if (form.askVaults) {
        SharedVaultsDialog(
            onConfirm = { scope.launch { finish(form.confirmVaults(remove)) } },
            onDismiss = { form.cancelVaults() },
        )
    }
}

/** "Delete your account": what is deleted, and the password, code and word. */
@Composable
internal fun DeleteAccountDialog(form: DeleteAccountForm, server: String, onSubmit: () -> Unit, onDismiss: () -> Unit) {
    var showPassword by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
        title = { Text(stringResource(R.string.account_delete_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.account_delete_text, server))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(
                        R.string.account_delete_item_data, R.string.account_delete_item_vaults,
                        R.string.account_delete_item_sessions, R.string.account_delete_item_teams,
                    ).forEach { Bullet(stringResource(it)) }
                }
                PasswordField(
                    form.password, showPassword, { form.password = it }, { showPassword = !showPassword }, ImeAction.Next,
                ) {}
                if (form.needsCode) {
                    OutlinedTextField(
                        form.code, { v -> form.code = v.filter { it.isLetterOrDigit() || it == '-' } },
                        Modifier.fillMaxWidth().testTag("delete-account-code"),
                        label = { Text(stringResource(R.string.login_code)) }, singleLine = true,
                        leadingIcon = { Icon(Icons.Outlined.Pin, null) },
                        isError = form.codeError != null,
                        supportingText = { Text(form.codeError?.asString() ?: stringResource(R.string.account_delete_code_hint)) },
                        keyboardOptions = TwoFactorKeyboard.copy(imeAction = ImeAction.Next),
                    )
                }
                OutlinedTextField(
                    form.typed, { form.typed = it }, Modifier.fillMaxWidth().testTag("delete-account-confirm"),
                    label = { Text(stringResource(R.string.account_delete_confirm_label, form.word)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false, imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { if (form.canSubmit) onSubmit() }),
                )
                form.error?.let { MessageBox(it.asString(), isError = true) }
            }
        },
        confirmButton = {
            TextButton(onClick = onSubmit, enabled = form.canSubmit, modifier = Modifier.testTag("delete-account-submit")) {
                if (form.busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text(
                    stringResource(R.string.account_delete_submit),
                    color = if (form.canSubmit) MaterialTheme.colorScheme.error else Color.Unspecified,
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !form.busy) { Text(stringResource(R.string.common_cancel)) } },
    )
}

/** Second confirmation: your shared vaults with members go with the account. */
@Composable
internal fun SharedVaultsDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.WarningAmber, null, tint = MaterialTheme.colorScheme.error) },
        title = { Text(stringResource(R.string.account_delete_vaults_title)) },
        text = { Text(stringResource(R.string.account_delete_vaults_text)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.account_delete_vaults_confirm), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

@Composable
private fun Bullet(text: String) {
    Row {
        Text("•", Modifier.padding(end = 8.dp), color = MaterialTheme.colorScheme.error)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
