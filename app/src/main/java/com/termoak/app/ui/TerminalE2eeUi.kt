package com.termoak.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EnhancedEncryption
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.termoak.app.R
import com.termoak.app.TermoakApp
import com.termoak.app.term.ServerTerminal
import com.termoak.app.term.TermSession
import com.termoak.ffi.SshHost

/**
 * The card of a terminal to an end-to-end encrypted host (stateless: the
 * screenshots draw it). UNLOCK: this device can't open the host's
 * credentials yet. SEND: a server session; the server can only use them if
 * this device sends them for this session (audited `e2ee.unlock`).
 */
@Composable
fun BoxScope.TerminalE2eeCardContent(need: TermSession.E2eeNeed, onUnlock: () -> Unit, onSend: () -> Unit, onClose: () -> Unit) {
    Surface(
        Modifier.align(Alignment.Center).padding(20.dp).widthIn(max = 480.dp).testTag("terminal-e2ee"),
        color = TermBarBg, contentColor = TermKeyFg, shape = RoundedCornerShape(20.dp),
    ) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.EnhancedEncryption, null, Modifier.size(44.dp), tint = Brand.Amber)
            Text(
                stringResource(if (need == TermSession.E2eeNeed.SEND) R.string.vault_e2ee_session_title else R.string.vault_e2ee_locked_card_title),
                Modifier.padding(top = 14.dp), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center,
            )
            Text(
                stringResource(if (need == TermSession.E2eeNeed.SEND) R.string.vault_e2ee_session_text else R.string.error_e2ee_locked),
                Modifier.padding(top = 8.dp), color = TermKeyFg.copy(alpha = 0.8f), style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            if (need == TermSession.E2eeNeed.SEND) {
                Text(
                    stringResource(R.string.vault_e2ee_session_hint), Modifier.padding(top = 6.dp), color = TermKeyFg.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
                )
            }
            Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onClose) { Text(stringResource(R.string.common_close), color = TermKeyFg) }
                if (need == TermSession.E2eeNeed.SEND) {
                    Button(onClick = onSend, modifier = Modifier.testTag("e2ee-send")) { Text(stringResource(R.string.vault_e2ee_session_send)) }
                } else {
                    Button(onClick = onUnlock, modifier = Modifier.testTag("e2ee-unlock-terminal")) { Text(stringResource(R.string.e2ee_unlock_button)) }
                }
            }
        }
    }
}

/** The card with its actions: Unlock opens the unlock dialog of the host's account, then connects again. */
@Composable
fun BoxScope.TerminalE2eeCard(app: TermoakApp, session: TermSession, host: SshHost?) {
    val need by session.e2eeNeed.collectAsState()
    val n = need ?: return
    val accountId = session.accountId ?: host?.accountId
    var unlocking by remember { mutableStateOf(false) }
    TerminalE2eeCardContent(
        n,
        onUnlock = { if (accountId != null) unlocking = true },
        onSend = { (session as? ServerTerminal)?.sendCredentialsAndConnect() },
        onClose = { app.sessions.close(session.id) },
    )
    if (unlocking && accountId != null) {
        UnlockDialog(app, accountId) { ok ->
            unlocking = false
            if (ok) session.e2eeUnlocked()
        }
    }
}

/** "Send the credentials for this test?" before a server host test of an end-to-end encrypted host. */
@Composable
fun SendForTestDialog(host: String, onDismiss: () -> Unit, onSend: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.EnhancedEncryption, null) },
        title = { Text(stringResource(R.string.vault_e2ee_test_title)) },
        text = { Text(stringResource(R.string.vault_e2ee_test_message, host) + "\n\n" + stringResource(R.string.vault_e2ee_session_hint)) },
        confirmButton = { TextButton(onClick = onSend, modifier = Modifier.testTag("e2ee-send-test")) { Text(stringResource(R.string.vault_e2ee_test_send)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}
