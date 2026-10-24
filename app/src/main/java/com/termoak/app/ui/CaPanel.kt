package com.termoak.app.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.termoak.app.R
import com.termoak.app.TermoakApp
import com.termoak.app.data.AccessBook
import com.termoak.app.data.AccessRules
import com.termoak.app.data.AccessTexts
import com.termoak.app.data.shownEmail
import com.termoak.app.userMessage
import com.termoak.ffi.AccountHandle
import com.termoak.ffi.CaOverview
import com.termoak.ffi.IssuedCertificateInfo
import com.termoak.ffi.RevokeTarget
import com.termoak.ffi.TermoakException
import kotlinx.coroutines.launch

/** Hands text to another app (mail, chat, notes…). */
internal fun shareText(context: Context, title: String, text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text).putExtra(Intent.EXTRA_SUBJECT, title)
    runCatching { context.startActivity(Intent.createChooser(send, title)) }
}

/**
 * The SSH certificate authority of a team (or, with [teamId] `null`, your
 * personal one), the same panel in Teams → Security and Settings → SSH
 * certificates: create it (Ed25519 or ECDSA P-256), its fingerprint, public
 * key and `sshd_config` lines with copy, the install command (the script in
 * a heredoc: `install.sh` needs a manager's token, so a host can't fetch it
 * with `curl … | sh`) with copy and share, rotate with an overlap, disable
 * with the typed name, issued certificates and revocation. Members who
 * can't manage it see the public parts.
 */
@Composable
fun CaPanel(app: TermoakApp, accountId: String, teamId: String?, teamName: String?) {
    val res = LocalResources.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val handle = remember(accountId) { app.accounts.handle(accountId) }
    var overview by remember { mutableStateOf<CaOverview?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var rotating by remember { mutableStateOf(false) }
    var disabling by remember { mutableStateOf(false) }
    var certsOpen by remember { mutableStateOf(false) }
    var revokingKey by remember { mutableStateOf(false) }
    suspend fun load() {
        val h = handle ?: return
        try {
            overview = h.caInfo(teamId)
            error = null
        } catch (e: TermoakException) {
            error = e.userMessage(res, R.string.team_security_load_failed)
        }
    }
    LaunchedEffect(accountId, teamId) { load() }
    fun act(done: Int?, block: suspend (AccountHandle) -> Unit) {
        val h = handle ?: return
        scope.launch {
            busy = true
            try {
                block(h)
                load()
                done?.let { snackbar.showSnackbar(res.getString(it)) }
            } catch (e: TermoakException) {
                snackbar.showSnackbar(e.userMessage(res, R.string.team_security_load_failed))
            } finally {
                busy = false
            }
        }
    }
    val o = overview
    CaPanelContent(
        overview = o,
        error = error,
        personal = teamId == null,
        busy = busy,
        onCreate = { algorithm -> act(R.string.ca_created) { it.createCa(teamId, algorithm) } },
        onRotate = { rotating = true },
        onDisable = { disabling = true },
        onCertificates = { certsOpen = true },
        onRevokeKey = { revokingKey = true },
        installScript = { krl, principals, refresh ->
            AccessRules.installCommand(handle?.caInstallScript(teamId, krl, principals, refresh) ?: throw TermoakException.NotLoggedIn(""))
        },
    )
    if (rotating) {
        var hours by remember { mutableStateOf("24") }
        val valid = AccessRules.overlapHours(hours)
        AlertDialog(
            onDismissRequest = { rotating = false },
            title = { Text(stringResource(R.string.ca_rotate_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.ca_rotate_message))
                    OutlinedTextField(
                        hours, { hours = it.filter(Char::isDigit).take(3) }, Modifier.fillMaxWidth(), singleLine = true,
                        label = { Text(stringResource(R.string.ca_rotate_overlap)) },
                        isError = valid == null,
                        supportingText = { Text(stringResource(if (valid == null) R.string.ca_rotate_overlap_error else R.string.ca_rotate_overlap_hint)) },
                    )
                }
            },
            confirmButton = {
                TextButton(enabled = valid != null, onClick = {
                    rotating = false
                    act(R.string.ca_rotated) { it.rotateCa(teamId, valid!!.toUInt()) }
                }) { Text(stringResource(R.string.ca_rotate_ok)) }
            },
            dismissButton = { TextButton(onClick = { rotating = false }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
    if (disabling) {
        val word = if (teamId == null) "personal" else teamName.orEmpty()
        var typed by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { disabling = false },
            title = { Text(stringResource(R.string.ca_disable_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.ca_disable_message))
                    Text(stringResource(R.string.ca_disable_type, word), style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(typed, { typed = it }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text(word) })
                }
            },
            confirmButton = {
                TextButton(enabled = typed.trim() == word, onClick = {
                    disabling = false
                    act(R.string.ca_disabled) { it.disableCa(teamId, typed.trim()) }
                }) { Text(stringResource(R.string.ca_disable_ok), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { disabling = false }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
    if (certsOpen && handle != null) IssuedCertificatesDialog(handle, teamId) { certsOpen = false }
    if (revokingKey) {
        var key by remember { mutableStateOf("") }
        var reason by remember { mutableStateOf("") }
        val ok = AccessRules.looksLikePublicKey(key)
        AlertDialog(
            onDismissRequest = { revokingKey = false },
            title = { Text(stringResource(R.string.ca_revoke_key_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.ca_revoke_key_message))
                    OutlinedTextField(
                        key, { key = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.ca_revoke_key_key)) },
                        minLines = 2, isError = key.isNotBlank() && !ok,
                        supportingText = if (key.isNotBlank() && !ok) ({ Text(stringResource(R.string.ca_revoke_key_invalid)) }) else null,
                    )
                    OutlinedTextField(reason, { reason = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.ca_revoke_reason)) })
                }
            },
            confirmButton = {
                TextButton(enabled = ok, onClick = {
                    revokingKey = false
                    act(null) {
                        val n = it.revokeCertificates(teamId, RevokeTarget.PublicKey(key.trim()), reason.trim().ifEmpty { null }).size
                        snackbar.showSnackbar(res.getQuantityString(R.plurals.ca_revoked, n, n))
                    }
                }) { Text(stringResource(R.string.ca_revoke_key_ok), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { revokingKey = false }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
}

/** The CA panel's content (the screenshot tests draw it with data). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CaPanelContent(
    overview: CaOverview?,
    error: String?,
    personal: Boolean,
    busy: Boolean,
    onCreate: (String) -> Unit,
    onRotate: () -> Unit,
    onDisable: () -> Unit,
    onCertificates: () -> Unit,
    onRevokeKey: () -> Unit,
    installScript: suspend (Boolean, Boolean, Boolean) -> String,
) {
    val context = LocalContext.current
    val snackbar = LocalSnackbar.current
    val res = LocalResources.current
    val scope = rememberCoroutineScope()
    FormSection(stringResource(R.string.ca_title)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(if (personal) R.string.ca_hint_personal else R.string.ca_hint_team),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when {
                error != null -> Text(error, color = MaterialTheme.colorScheme.error)
                overview == null -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                overview.ca == null -> {
                    Text(stringResource(if (personal) R.string.ca_none_personal else R.string.ca_none_team))
                    if (overview.canManage) {
                        var algorithm by remember { mutableStateOf("ed25519") }
                        Picker(
                            stringResource(R.string.ca_algorithm_label),
                            stringResource(if (algorithm == "ed25519") R.string.ca_algorithm_ed25519 else R.string.ca_algorithm_ecdsa), null,
                            listOf("ed25519" to stringResource(R.string.ca_algorithm_ed25519), "ecdsa-p256" to stringResource(R.string.ca_algorithm_ecdsa)),
                        ) { algorithm = it }
                        Text(stringResource(R.string.ca_algorithm_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = { onCreate(algorithm) }, enabled = !busy, modifier = Modifier.testTag("ca-create")) {
                            Text(stringResource(R.string.ca_create))
                        }
                    }
                }
                else -> {
                    val ca = overview.ca!!
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Pill(stringResource(R.string.ca_status_active), Brand.Green)
                        Text(
                            ca.algorithm + " · " + stringResource(R.string.ca_created_at, AccessTexts.date(ca.createdAt)),
                            Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    CopyBlock(stringResource(R.string.ca_fingerprint), ca.fingerprint, stringResource(R.string.ca_copy_fingerprint)) {
                        copyText(context, "fingerprint", ca.fingerprint)
                        scope.launch { snackbar.showSnackbar(res.getString(R.string.ca_copied_fingerprint)) }
                    }
                    CopyBlock(stringResource(R.string.ca_public_key), ca.publicKey, stringResource(R.string.ca_copy_key)) {
                        copyText(context, "ca", ca.publicKey)
                        scope.launch { snackbar.showSnackbar(res.getString(R.string.ca_copied_public_key)) }
                    }
                    overview.previous.forEach { p ->
                        Text(
                            stringResource(R.string.ca_retiring, p.fingerprint, p.retireAt?.let { AccessTexts.dateTime(it) }.orEmpty()),
                            style = MaterialTheme.typography.bodySmall, color = Brand.Amber,
                        )
                    }
                    HorizontalDivider()
                    // ----- Install on a host -----
                    Text(stringResource(R.string.ca_install_title), style = MaterialTheme.typography.titleSmall)
                    var krl by remember { mutableStateOf(true) }
                    var principals by remember { mutableStateOf(false) }
                    var refresh by remember { mutableStateOf(true) }
                    val lines = AccessRules.sshdLines(overview, krl, principals)
                    CopyBlock(stringResource(R.string.ca_sshd_config), lines.joinToString("\n"), stringResource(R.string.ca_copy_lines)) {
                        copyText(context, "sshd_config", lines.joinToString("\n"))
                        scope.launch { snackbar.showSnackbar(res.getString(R.string.ca_copied_sshd)) }
                    }
                    Text(stringResource(R.string.ca_sshd_config_hint, overview.keysUrl), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (overview.canManage) {
                        OptionSwitch(stringResource(R.string.ca_install_krl), krl) { krl = it }
                        OptionSwitch(stringResource(R.string.ca_install_principals), principals) { principals = it }
                        OptionSwitch(stringResource(R.string.ca_install_refresh), refresh) { refresh = it }
                        Text(
                            "sudo sh -s <<'TERMOAK_CA' … TERMOAK_CA", style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(stringResource(R.string.ca_install_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        var working by remember { mutableStateOf(false) }
                        fun withScript(use: (String) -> Unit) {
                            scope.launch {
                                working = true
                                try {
                                    use(installScript(krl, principals, refresh))
                                } catch (e: TermoakException) {
                                    snackbar.showSnackbar(e.userMessage(res, R.string.team_security_load_failed))
                                } finally {
                                    working = false
                                }
                            }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                withScript { cmd ->
                                    copyText(context, "install", cmd)
                                    scope.launch { snackbar.showSnackbar(res.getString(R.string.ca_copied_install)) }
                                }
                            }, enabled = !working, modifier = Modifier.testTag("ca-copy-install")) {
                                Icon(Icons.Outlined.ContentCopy, null, Modifier.size(16.dp))
                                Text(stringResource(R.string.ca_install_copy), Modifier.padding(start = 6.dp))
                            }
                            OutlinedButton(onClick = { withScript { cmd -> shareText(context, res.getString(R.string.ca_install_title), cmd) } }, enabled = !working) {
                                Icon(Icons.Outlined.Share, null, Modifier.size(16.dp))
                                Text(stringResource(R.string.ca_install_share), Modifier.padding(start = 6.dp))
                            }
                        }
                        HorizontalDivider()
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = onCertificates) { Text(stringResource(R.string.ca_certs_open)) }
                            TextButton(onClick = onRevokeKey) { Text(stringResource(R.string.ca_revoke_key_button)) }
                            TextButton(onClick = onRotate, enabled = !busy) { Text(stringResource(R.string.ca_rotate_button)) }
                            TextButton(onClick = onDisable, enabled = !busy) { Text(stringResource(R.string.ca_disable_button), color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionSwitch(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked, onChange)
    }
}

/** A label, a value in monospace (selectable) and a copy button. */
@Composable
fun CopyBlock(label: String, value: String, copyLabel: String, onCopy: () -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onCopy) {
                Icon(Icons.Outlined.ContentCopy, null, Modifier.size(14.dp))
                Text(copyLabel, Modifier.padding(start = 4.dp), style = MaterialTheme.typography.labelMedium)
            }
        }
        SelectionContainer {
            Text(value, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, maxLines = 6, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Certificates the CA issued: valid only or all, revoke one or all of a member's. */
@Composable
fun IssuedCertificatesDialog(handle: AccountHandle, teamId: String?, onDismiss: () -> Unit) {
    val res = LocalResources.current
    val scope = rememberCoroutineScope()
    var activeOnly by remember { mutableStateOf(true) }
    var list by remember { mutableStateOf<List<IssuedCertificateInfo>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var revoking by remember { mutableStateOf<Pair<IssuedCertificateInfo, Boolean>?>(null) }
    suspend fun load() {
        try {
            list = handle.caCertificates(teamId, null, activeOnly).sortedByDescending { it.createdAt }
            error = null
        } catch (e: TermoakException) {
            error = e.userMessage(res, R.string.team_security_load_failed)
        }
    }
    LaunchedEffect(activeOnly) { load() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ca_certs_title)) },
        text = {
            Column {
                OptionSwitch(stringResource(R.string.ca_certs_active_only), activeOnly) { activeOnly = it }
                val l = list
                when {
                    error != null -> Text(error ?: "", color = MaterialTheme.colorScheme.error)
                    l == null -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    l.isEmpty() -> Text(stringResource(R.string.ca_certs_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else -> LazyColumn(Modifier.heightIn(max = 420.dp)) {
                        items(l, key = { it.serial }) { c -> IssuedCertificateRow(c, onRevoke = { revoking = c to false }, onRevokeUser = { revoking = c to true }) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) } },
    )
    revoking?.let { (c, all) ->
        ConfirmDialog(
            if (all) stringResource(R.string.ca_cert_revoke_user_confirm, c.userEmail?.let(::shownEmail) ?: c.userId)
            else stringResource(R.string.ca_cert_revoke_confirm, c.serial.toString()),
            stringResource(R.string.ca_revoke_key_message), stringResource(R.string.ca_revoke_key_ok), destructive = true,
            onDismiss = { revoking = null },
        ) {
            scope.launch {
                runCatching {
                    handle.revokeCertificates(teamId, if (all) RevokeTarget.User(c.userId) else RevokeTarget.Serial(c.serial), null)
                }
                load()
            }
        }
    }
}

@Composable
fun IssuedCertificateRow(c: IssuedCertificateInfo, onRevoke: (() -> Unit)?, onRevokeUser: (() -> Unit)?) {
    val now = AccessBook.now()
    val expired = c.validBefore <= now
    ListItem(
        headlineContent = {
            Text("#${c.serial} · " + (c.userEmail?.let(::shownEmail) ?: c.userId), maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Column {
                Text(stringResource(R.string.ca_cert_detail, c.principals.joinToString(", "), certPurpose(c.purpose)), style = MaterialTheme.typography.bodySmall)
                Text(
                    when {
                        c.revokedAt != null -> stringResource(R.string.ca_cert_revoked)
                        expired -> stringResource(R.string.ca_cert_expired)
                        else -> stringResource(R.string.ca_cert_until, AccessTexts.dateTime(c.validBefore))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (c.revokedAt != null || expired) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (c.revokedAt == null && !expired && (onRevoke != null || onRevokeUser != null)) {
                    Row {
                        onRevoke?.let { TextButton(onClick = it) { Text(stringResource(R.string.ca_revoke_serial), color = MaterialTheme.colorScheme.error) } }
                        onRevokeUser?.let { TextButton(onClick = it) { Text(stringResource(R.string.ca_revoke_user), color = MaterialTheme.colorScheme.error) } }
                    }
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}
