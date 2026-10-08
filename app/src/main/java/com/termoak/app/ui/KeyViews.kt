package com.termoak.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.termoak.app.R
import com.termoak.app.TermoakApp
import com.termoak.app.data.AuthorizedKeys
import com.termoak.app.data.isTelnet
import com.termoak.app.data.uid
import com.termoak.app.data.useOnly
import com.termoak.app.files.RemotePaths
import com.termoak.app.term.PromptAuth
import com.termoak.app.userMessage
import com.termoak.ffi.SecretChange
import com.termoak.ffi.SshHost
import com.termoak.ffi.SshKey
import com.termoak.ffi.SshSession
import com.termoak.ffi.TermoakException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Copies a secret to the clipboard marked sensitive (not shown in the
 * clipboard preview, Android 13+) and clears it after two minutes if it is
 * still there (the iOS app's local-only copy with an expiry).
 */
internal fun copySecret(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    val clip = ClipData.newPlainText(label, text).apply {
        // ClipDescription.EXTRA_IS_SENSITIVE (Android 13+; ignored before).
        description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
    }
    clipboard.setPrimaryClip(clip)
    Handler(Looper.getMainLooper()).postDelayed({
        val now = clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
        if (now == text) {
            if (android.os.Build.VERSION.SDK_INT >= 28) runCatching { clipboard.clearPrimaryClip() }
            else clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
        }
    }, 120_000)
}

/**
 * A key, as on iOS: its name and comment (editable), type, fingerprint and
 * public key (copy, share, QR code), install it on a host, and export the
 * private key after unlocking the phone.
 */
@Composable
fun KeyDetailScreen(app: TermoakApp, nav: NavHostController, keyId: String, accountId: String?) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val auth = rememberDeviceAuth()
    val original = remember { runCatching { app.core.getKey(keyId, accountId) }.getOrNull() }
    if (original == null) {
        LaunchedEffectPop(nav)
        return
    }
    val editable = !original.access.useOnly()
    var label by remember { mutableStateOf(original.label) }
    var comment by remember { mutableStateOf(original.comment) }
    var showingQr by remember { mutableStateOf(false) }
    var installing by remember { mutableStateOf(false) }
    var exported by remember { mutableStateOf<String?>(null) }
    val changed = label != original.label || comment != original.comment

    fun save() {
        try {
            app.core.saveKey(original.copy(label = label.trim(), comment = comment.trim()), SecretChange.Keep)
            app.accounts.sync()
            nav.popBackStack()
        } catch (e: TermoakException) {
            scope.launch { snackbar.showSnackbar(e.userMessage(resources, R.string.error_save_failed)) }
        }
    }

    ScreenScaffold(
        title = original.label,
        subtitle = stringResource(R.string.keys_detail_title),
        navigationIcon = {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back)) }
        },
        actions = {
            if (editable) {
                TextButton(onClick = { save() }, enabled = changed && label.isNotBlank()) { Text(stringResource(R.string.common_save)) }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            FormSection(stringResource(R.string.common_name)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(label, { label = it }, Modifier.fillMaxWidth(), enabled = editable, singleLine = true,
                        label = { Text(stringResource(R.string.common_name)) })
                    OutlinedTextField(
                        comment, { comment = it }, Modifier.fillMaxWidth(), enabled = editable, singleLine = true,
                        label = { Text(stringResource(R.string.keys_detail_comment)) },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                    )
                }
            }
            FormHint(stringResource(R.string.keys_detail_comment_hint))
            FormSection(stringResource(R.string.keys_detail_public_key)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DetailLine(stringResource(R.string.keys_detail_type), original.algorithm)
                    Text(stringResource(R.string.keys_detail_fingerprint), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SelectionContainer { Text(original.fingerprint, fontFamily = FontFamily.Monospace, fontSize = 12.sp) }
                    SelectionContainer {
                        Text(original.publicKey, fontFamily = FontFamily.Monospace, fontSize = 11.sp, maxLines = 6, overflow = TextOverflow.Ellipsis)
                    }
                    if (original.certificate != null) DetailLine(stringResource(R.string.keys_detail_certificate), stringResource(R.string.keys_detail_yes))
                    if (original.hasPassphrase) DetailLine(stringResource(R.string.keys_detail_passphrase), stringResource(R.string.keys_detail_encrypted))
                }
            }
            FormSection(stringResource(R.string.keys_detail_title)) {
                ActionRow(Icons.Outlined.ContentCopy, stringResource(R.string.keys_copy_public)) {
                    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(original.label, original.publicKey))
                    scope.launch { snackbar.showSnackbar(resources.getString(R.string.keys_public_copied)) }
                }
                ActionRow(Icons.Outlined.Share, stringResource(R.string.keys_detail_share_public)) {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, original.publicKey)
                    runCatching { context.startActivity(Intent.createChooser(send, original.label)) }
                }
                ActionRow(Icons.Outlined.QrCode2, stringResource(R.string.keys_detail_qr)) { showingQr = true }
                ActionRow(Icons.Outlined.Dns, stringResource(R.string.keys_install_title)) { installing = true }
                if (editable && original.hasPrivateKey) {
                    ActionRow(Icons.Outlined.Key, stringResource(R.string.keys_export_action)) {
                        auth.unlock(resources.getString(R.string.keys_export_reason)) { ok ->
                            if (!ok) return@unlock
                            try {
                                exported = app.core.exportPrivateKey(original.id, original.accountId)
                                if (exported == null) scope.launch { snackbar.showSnackbar(resources.getString(R.string.keys_export_none)) }
                            } catch (e: TermoakException) {
                                scope.launch { snackbar.showSnackbar(e.userMessage(resources, R.string.keys_export_none)) }
                            }
                        }
                    }
                }
            }
            if (editable && original.hasPrivateKey) FormHint(stringResource(R.string.keys_export_footer))
        }
    }
    if (showingQr) QrCodeDialog(original.label, original.publicKey) { showingQr = false }
    if (installing) InstallKeyDialog(app, original) { installing = false }
    exported?.let { pem -> ExportedKeyDialog(original.label, pem) { exported = null } }
}

@Composable
private fun LaunchedEffectPop(nav: NavHostController) {
    androidx.compose.runtime.LaunchedEffect(Unit) { nav.popBackStack() }
}

@Composable
private fun DetailLine(title: String, value: String) {
    Row {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ActionRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) },
        headlineContent = { Text(text) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

/** The exported private key: copy (this device only, cleared after two minutes) or share. */
@Composable
private fun ExportedKeyDialog(label: String, text: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(label) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                SelectionContainer { Text(text, fontFamily = FontFamily.Monospace, fontSize = 10.sp) }
                Text(
                    stringResource(R.string.keys_export_warning), Modifier.padding(top = 12.dp),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { copySecret(context, label, text); copied = true }) {
                        Text(stringResource(if (copied) R.string.keys_export_copied else R.string.keys_export_copy))
                    }
                }
                TextButton(onClick = {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                    runCatching { context.startActivity(Intent.createChooser(send, label)) }
                }) { Text(stringResource(R.string.keys_export_share)) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) } },
    )
}

/**
 * "Install on a host" (like ssh-copy-id), as on iOS: connects to the host from
 * the phone and adds the public key to `~/.ssh/authorized_keys` (creating
 * `~/.ssh` with 700 and the file with 600; nothing if it is already there).
 */
@Composable
internal fun InstallKeyDialog(app: TermoakApp, key: SshKey, onDismiss: () -> Unit) {
    val resources = LocalResources.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val vaults by app.accounts.vaults.collectAsState()
    // SSH hosts the phone connects to directly (not Telnet, not Strict Use-only ones, which only go through the server).
    val hosts = remember {
        runCatching { app.core.listHosts(app.accounts.filter()) }.getOrDefault(emptyList()).filter { h ->
            !h.isTelnet && !(h.access.useOnly() && vaults.any { it.id == h.vaultId && it.accountId == h.accountId && it.strict })
        }.sortedBy { it.label.lowercase() }
    }
    var query by remember { mutableStateOf("") }
    var working by remember { mutableStateOf<String?>(null) }
    val auth = remember { PromptAuth() }
    val pending by auth.pending.collectAsState()
    fun install(h: SshHost) {
        working = h.uid
        scope.launch {
            val message = try {
                val session = app.core.connect(h.id, auth, h.accountId, keyChanged = auth)
                try {
                    val added = addKey(key.publicKey, session)
                    resources.getString(if (added) R.string.keys_install_done else R.string.keys_install_already, h.label)
                } finally {
                    withContext(Dispatchers.IO) { runCatching { session.disconnect() } }
                    session.close()
                }
            } catch (e: TermoakException) {
                resources.getString(R.string.keys_install_failed) + ": " + e.userMessage(resources, R.string.keys_install_failed)
            }
            working = null
            snackbar.showSnackbar(message)
        }
    }
    AlertDialog(
        onDismissRequest = { if (working == null) onDismiss() },
        title = { Text(stringResource(R.string.keys_install_title)) },
        text = {
            Column {
                Text(stringResource(R.string.keys_install_footer), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    query, { query = it }, Modifier.fillMaxWidth().padding(vertical = 8.dp), singleLine = true,
                    placeholder = { Text(stringResource(R.string.hosts_search)) }, leadingIcon = { Icon(Icons.Outlined.Search, null) },
                )
                val q = query.trim().lowercase()
                val shown = hosts.filter { q.isEmpty() || it.label.lowercase().contains(q) || it.address.lowercase().contains(q) }
                if (hosts.isEmpty()) Text(stringResource(R.string.keys_install_no_hosts))
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(shown, key = { it.uid }) { h ->
                        ListItem(
                            modifier = Modifier.clickable(enabled = working == null) { install(h) },
                            leadingContent = { HostTile(h, size = 32.dp) },
                            headlineContent = { Text(h.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = { Text(h.address, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            trailingContent = { if (working == h.uid) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss, enabled = working == null) { Text(stringResource(R.string.common_close)) } },
    )
    pending?.let { PendingDialog(it) }
}

/** Adds [publicKey] to `~/.ssh/authorized_keys` over [session]. `false`: it was already there. */
private suspend fun addKey(publicKey: String, session: SshSession): Boolean {
    val home = session.sftpHome()
    val folder = RemotePaths.child(home, AuthorizedKeys.FOLDER)
    val file = RemotePaths.child(home, AuthorizedKeys.FILE)
    if (runCatching { session.sftpStat(folder) }.isFailure) {
        session.sftpMkdir(folder, false)
        runCatching { session.sftpChmod(folder, "700".toUInt(8)) }
    }
    val existing = if (runCatching { session.sftpStat(file) }.isSuccess) {
        String(session.sftpRead(file, AuthorizedKeys.MAX_BYTES.toULong()), Charsets.UTF_8)
    } else ""
    if (AuthorizedKeys.contains(existing, publicKey)) return false
    session.sftpWrite(file, AuthorizedKeys.appending(existing, publicKey).toByteArray())
    runCatching { session.sftpChmod(file, "600".toUInt(8)) }
    return true
}
