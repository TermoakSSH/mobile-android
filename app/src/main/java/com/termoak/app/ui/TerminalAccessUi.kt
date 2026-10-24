package com.termoak.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.LockClock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.termoak.app.R
import com.termoak.app.TermoakApp
import com.termoak.app.data.AccessBook
import com.termoak.app.data.AccessRules
import com.termoak.app.data.AccessTexts
import com.termoak.app.term.TermSession
import com.termoak.app.userMessage
import com.termoak.ffi.IssuedCertificateInfo
import com.termoak.ffi.SshHost
import com.termoak.ffi.TermoakException
import kotlinx.coroutines.launch

/**
 * Teams security in the terminal (0.7 phase 4d): the access window and
 * "Recorded" in its bar, the "Approval needed" / "Waiting for approval"
 * card instead of an error (the terminal connects by itself when the
 * window opens), and the connection's team certificates.
 */

/** "Access 42 min" (amber in the last 5 minutes, every second in the last 2) and "Recorded". */
@Composable
fun TerminalAccessPills(app: TermoakApp, host: SshHost?, modifier: Modifier = Modifier) {
    host ?: return
    val window = AccessBook.windowOf(host.accountId, host.id)
    val res = LocalResources.current
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (window is AccessBook.HostWindow.Open) {
            val now = rememberNow(window.endsAt)
            val left = AccessRules.remainingSecs(window.endsAt, now)
            Pill(
                if (left > 0) stringResource(R.string.term_access_window, AccessTexts.left(res, left)) else stringResource(R.string.access_bar_ended),
                if (AccessRules.endingSoon(left)) Brand.Amber else Brand.Green,
            )
        }
        val recorded = AccessUi.gates(app, host).recorded || AccessBook.host(host.accountId, host.id)?.recordRequired == true
        if (recorded) RecordedChip()
    }
}

/** "Access until 15:42 · 42 min left" for the bar's status line (`null`: no open window). */
@Composable
fun accessWindowText(host: SshHost?): String? {
    host ?: return null
    val window = AccessBook.windowOf(host.accountId, host.id) as? AccessBook.HostWindow.Open ?: return null
    val now = rememberNow(window.endsAt)
    return stringResource(R.string.access_window_until, AccessTexts.clock(window.endsAt), AccessTexts.left(LocalResources.current, AccessRules.remainingSecs(window.endsAt, now)))
}

/**
 * The card a terminal shows when connecting needs an approved window:
 * Request access (duration, reason, what for), then "Waiting for approval"
 * with Cancel request; it connects by itself when the window opens.
 */
@Composable
fun BoxScope.TerminalAccessCard(app: TermoakApp, session: TermSession, host: SshHost?) {
    val need by session.accessNeed.collectAsState()
    val n = need ?: return
    val res = LocalResources.current
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbar.current
    val accountId = session.accountId ?: host?.accountId
    val hostId = session.hostId
    // The server's answer (pending request, window), asked once when the card appears.
    LaunchedEffect(n) { if (accountId != null && hostId != null) AccessBook.ask(accountId, hostId, force = true) }
    val access = AccessBook.host(accountId, hostId)
    val pending = access?.pending ?: n.pending?.let { id -> AccessBook.mine(accountId.orEmpty()).firstOrNull { it.id == id } }
    var asking by remember { mutableStateOf(false) }
    TerminalAccessCardContent(
        pending = pending != null || n.pending != null,
        maxText = AccessTexts.duration(res, (access?.maxMinutes?.toInt() ?: n.maxMinutes).coerceAtLeast(1)),
        canRequest = (access?.canRequest ?: n.canRequest) && host != null && accountId != null,
        onRequest = { asking = true },
        onCancel = {
            val id = pending?.id ?: n.pending ?: return@TerminalAccessCardContent
            scope.launch {
                try {
                    val r = app.accounts.handle(accountId ?: return@launch)?.cancelAccessRequest(id) ?: return@launch
                    AccessBook.remember(accountId, r, mineToo = true)
                    hostId?.let { AccessBook.ask(accountId, it, force = true) }
                    snackbar.showSnackbar(res.getString(R.string.access_cancelled))
                } catch (e: TermoakException) {
                    snackbar.showSnackbar(e.userMessage(res, R.string.access_check_failed))
                }
            }
        },
        onClose = { app.sessions.close(session.id) },
    )
    if (asking && host != null) {
        val fresh = access?.let { AccessRules.needOf(it) } ?: n
        RequestAccessFor(app, host, fresh, onDismiss = { asking = false }) { r ->
            asking = false
            scope.launch { snackbar.showSnackbar(res.getString(R.string.access_request_sent, host.label)) }
            // Maybe approved at once (self-approval): look now.
            scope.launch { app.sessions.checkWaiting(accountId) }
        }
    }
}

@Composable
fun BoxScope.TerminalAccessCardContent(
    pending: Boolean,
    maxText: String,
    canRequest: Boolean,
    onRequest: () -> Unit,
    onCancel: () -> Unit,
    onClose: () -> Unit,
) {
    Surface(
        Modifier.align(Alignment.Center).padding(20.dp).widthIn(max = 480.dp),
        color = TermBarBg, contentColor = TermKeyFg, shape = RoundedCornerShape(20.dp),
    ) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(if (pending) Icons.Outlined.HourglassTop else Icons.Outlined.LockClock, null, Modifier.size(44.dp), tint = Brand.Amber)
            Text(
                stringResource(if (pending) R.string.access_card_pending_title else R.string.access_card_title),
                Modifier.padding(top = 14.dp), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center,
            )
            Text(
                if (pending) stringResource(R.string.access_card_pending_text) else stringResource(R.string.access_card_text, maxText),
                Modifier.padding(top = 8.dp), color = TermKeyFg.copy(alpha = 0.8f), style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                stringResource(R.string.access_card_auto), Modifier.padding(top = 6.dp), color = TermKeyFg.copy(alpha = 0.6f),
                style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
            )
            if (pending) CircularProgressIndicator(Modifier.padding(top = 14.dp).size(22.dp), strokeWidth = 2.dp, color = Brand.Amber)
            Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onClose) { Text(stringResource(R.string.common_close), color = TermKeyFg) }
                if (pending) {
                    Button(onClick = onCancel) { Text(stringResource(R.string.access_request_cancel_request)) }
                } else if (canRequest) {
                    Button(onClick = onRequest) { Text(stringResource(R.string.access_request_button)) }
                }
            }
        }
    }
}

/**
 * The team certificates issued to you for this host (newest first; the
 * engine asks for them by itself when connecting): serial, principals,
 * validity, key and request.
 */
@Composable
fun ConnectionCertificatesDialog(app: TermoakApp, host: SshHost, onDismiss: () -> Unit) {
    val res = LocalResources.current
    var certs by remember { mutableStateOf<List<IssuedCertificateInfo>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(host.id) {
        try {
            val h = app.accounts.handle(host.accountId ?: return@LaunchedEffect) ?: return@LaunchedEffect
            certs = h.myCertificates().filter { it.hostId == host.id }.sortedByDescending { it.createdAt }
        } catch (e: TermoakException) {
            error = e.userMessage(res, R.string.access_check_failed)
            certs = emptyList()
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.conn_details_title, host.label)) },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val auth = AccessUi.hostAuth(app, host)
                DetailLine(stringResource(R.string.conn_details_auth), if (auth.teamCertificate) stringResource(R.string.conn_details_auth_cert) else stringResource(R.string.auth_field_keys))
                val list = certs
                when {
                    list == null -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    error != null -> Text(error ?: "", color = MaterialTheme.colorScheme.error)
                    list.isEmpty() -> Text(stringResource(R.string.certs_none_for_host), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else -> {
                        Text(stringResource(R.string.certs_for_host), style = MaterialTheme.typography.titleSmall)
                        list.take(5).forEachIndexed { i, c ->
                            if (i > 0) HorizontalDivider()
                            CertificateLines(c)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) } },
    )
}

/** One issued certificate's details. */
@Composable
fun CertificateLines(c: IssuedCertificateInfo) {
    val res = LocalResources.current
    val now = AccessBook.now()
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(stringResource(R.string.certs_title, c.serial.toString()), style = MaterialTheme.typography.titleSmall)
        DetailLine(stringResource(R.string.conn_details_cert_principals), c.principals.joinToString(", "))
        DetailLine(stringResource(R.string.conn_details_cert_valid_after), AccessTexts.dateTime(c.validAfter))
        val left = AccessRules.remainingSecs(c.validBefore, now)
        DetailLine(
            stringResource(R.string.conn_details_cert_valid_before),
            AccessTexts.dateTime(c.validBefore) + " · " +
                if (left > 0) stringResource(R.string.conn_details_cert_left, AccessTexts.left(res, left)) else stringResource(R.string.conn_details_cert_expired),
        )
        DetailLine(stringResource(R.string.conn_details_cert_key_id), c.keyId, mono = true)
        DetailLine(stringResource(R.string.certs_key_fingerprint), c.keyFingerprint, mono = true)
        DetailLine(stringResource(R.string.certs_purpose), certPurpose(c.purpose))
        if (c.requestId != null) DetailLine(stringResource(R.string.certs_request), c.requestId ?: "", mono = true)
        c.revokedAt?.let { Text(stringResource(R.string.certs_revoked_reason, c.revokeReason ?: AccessTexts.dateTime(it)), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
fun certPurpose(p: String): String = when (p) {
    "device" -> stringResource(R.string.certs_purpose_device)
    "server" -> stringResource(R.string.certs_purpose_server)
    "credentials" -> stringResource(R.string.certs_purpose_credentials)
    else -> p
}

@Composable
fun DetailLine(label: String, value: String, mono: Boolean = false) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SelectionContainer {
            Text(value, style = MaterialTheme.typography.bodySmall, fontFamily = if (mono) FontFamily.Monospace else null)
        }
    }
}
