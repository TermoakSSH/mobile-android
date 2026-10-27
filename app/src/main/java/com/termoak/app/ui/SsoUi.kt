package com.termoak.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.outlined.Pin
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.termoak.app.R
import com.termoak.ffi.SsoHint

object SsoUi {
    /** Worth asking the server about (something@domain.tld). */
    fun looksLikeEmail(e: String): Boolean {
        val at = e.indexOf('@')
        return at > 0 && e.indexOf('.', at) > at + 1 && !e.endsWith('.') && e.none { it.isWhitespace() }
    }
}

/**
 * "Corp can sign you in with single sign-on" with Continue with Corp; when
 * the team requires it, only that (the owner's break-glass password behind
 * a small link).
 */
@Composable
fun SsoCard(h: SsoHint, busy: Boolean, onContinue: () -> Unit, onOwnerPassword: (() -> Unit)?) {
    val team = h.teamName?.takeIf { it.isNotBlank() } ?: stringResource(R.string.add_account_sso_your_team)
    Surface(
        color = if (h.enforced) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.medium, modifier = Modifier.testTag("sso-card"),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                stringResource(if (h.enforced) R.string.add_account_sso_enforced else R.string.add_account_sso_available, team),
                style = MaterialTheme.typography.bodyMedium,
            )
            val content: @Composable () -> Unit = {
                if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else {
                    Icon(Icons.AutoMirrored.Outlined.Login, null, Modifier.size(18.dp))
                    Text(stringResource(R.string.add_account_sso_continue, team), Modifier.padding(start = 8.dp))
                }
            }
            if (h.enforced) {
                Button(onClick = onContinue, enabled = !busy, modifier = Modifier.fillMaxWidth().height(50.dp).testTag("sso-continue")) { content() }
            } else {
                OutlinedButton(onClick = onContinue, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("sso-continue")) { content() }
            }
            onOwnerPassword?.let {
                TextButton(onClick = it, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text(stringResource(R.string.add_account_sso_owner_password)) }
            }
        }
    }
}

/** Waiting for the browser to come back, or the two-step code of an SSO sign-in. */
@Composable
fun SsoWaitingCard(totp: Boolean, code: String, onCode: (String) -> Unit, busy: Boolean, onSubmit: () -> Unit, onCancel: () -> Unit, onAgain: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!totp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text(stringResource(R.string.add_account_sso_waiting), Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyMedium)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onAgain) { Text(stringResource(R.string.add_account_sso_open_again)) }
                    TextButton(onClick = onCancel) { Text(stringResource(R.string.common_cancel)) }
                }
            } else {
                Text(stringResource(R.string.login_code_prompt), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    code, onCode, Modifier.fillMaxWidth().testTag("sso-totp"), singleLine = true,
                    label = { Text(stringResource(R.string.login_code)) }, leadingIcon = { Icon(Icons.Outlined.Pin, null) },
                    supportingText = { Text(stringResource(R.string.login_code_hint)) },
                    keyboardOptions = TwoFactorKeyboard, keyboardActions = KeyboardActions(onGo = { onSubmit() }),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onSubmit, enabled = !busy && code.isNotBlank()) {
                        if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(stringResource(R.string.login_verify))
                    }
                    TextButton(onClick = onCancel) { Text(stringResource(R.string.common_cancel)) }
                }
            }
        }
    }
}
