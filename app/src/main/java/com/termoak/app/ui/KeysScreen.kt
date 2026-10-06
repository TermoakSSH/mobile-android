package com.termoak.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.termoak.app.R
import com.termoak.app.TermoakApp
import com.termoak.ffi.KeyType
import com.termoak.ffi.KnownHost
import com.termoak.ffi.SecretChange
import com.termoak.ffi.Snippet
import com.termoak.ffi.SshIdentity
import com.termoak.ffi.SshKey
import com.termoak.ffi.SyncMode
import com.termoak.ffi.TermoakException
import kotlinx.coroutines.launch

/** What the keychain opens with, from the Vault's "+" ([Routes.keys]). */
object KeysAction {
    const val GENERATE = "generate"
    const val IMPORT = "import"
}

@Composable
fun KeysScreen(app: TermoakApp, nav: NavHostController, action: String? = null) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    var keys by remember { mutableStateOf(runCatching { app.core.listKeys() }.getOrDefault(emptyList())) }
    var creating by rememberSaveable { mutableStateOf(action == KeysAction.GENERATE) }
    var importing by rememberSaveable { mutableStateOf(action == KeysAction.IMPORT) }
    var deleting by remember { mutableStateOf<SshKey?>(null) }
    var fab by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf(0) }
    var identities by remember { mutableStateOf(runCatching { app.core.listIdentities() }.getOrDefault(emptyList())) }
    val reload = { keys = runCatching { app.core.listKeys() }.getOrDefault(emptyList()).sortedBy { it.label.lowercase() } }

    fun copyPublic(k: SshKey) {
        context.getSystemService(ClipboardManager::class.java)
            ?.setPrimaryClip(ClipData.newPlainText(resources.getString(R.string.keys_public_key_clip_label), k.publicKey))
        scope.launch { snackbar.showSnackbar(resources.getString(R.string.keys_public_key_copied)) }
    }

    VaultScaffold(
        VaultSection.KEYCHAIN, nav,
        floatingActionButton = {
            if (tab == 0) Box {
                ExtendedFloatingActionButton(
                    onClick = { fab = true }, icon = { Icon(Icons.Outlined.Add, null) },
                    text = { Text(stringResource(R.string.keys_add)) },
                )
                DropdownMenu(fab, { fab = false }) {
                    DropdownMenuItem({ Text(stringResource(R.string.keys_generate_new)) }, { fab = false; creating = true },
                        leadingIcon = { Icon(Icons.Outlined.Key, null) })
                    DropdownMenuItem({ Text(stringResource(R.string.keys_import_paste)) }, { fab = false; importing = true },
                        leadingIcon = { Icon(Icons.Outlined.FileDownload, null) })
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
        PrimaryTabRow(selectedTabIndex = tab) {
            Tab(tab == 0, { tab = 0 }, text = { Text(stringResource(R.string.keys_tab_keys, keys.size)) })
            Tab(tab == 1, { tab = 1 }, text = { Text(stringResource(R.string.keys_tab_identities, identities.size)) })
        }
        if (tab == 1) {
            IdentitiesList(app, identities, keys, onChanged = { identities = runCatching { app.core.listIdentities() }.getOrDefault(emptyList()) })
        } else if (keys.isEmpty()) {
            EmptyState(
                Icons.Outlined.Key, stringResource(R.string.keys_empty_title),
                stringResource(R.string.keys_empty_text),
                action = stringResource(R.string.keys_generate_title), onAction = { creating = true },
            )
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp, top = 8.dp)) {
                items(keys, key = { it.id }) { k ->
                    CardBox {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Key, null, tint = MaterialTheme.colorScheme.primary)
                                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                    Text(k.label, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        if (k.hasPassphrase) stringResource(R.string.keys_with_passphrase, k.algorithm) else k.algorithm,
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (k.syncMode == SyncMode.DEVICE_ONLY) {
                                    Icon(
                                        Icons.Outlined.PhoneAndroid, stringResource(R.string.common_device_only),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Text(k.fingerprint, Modifier.padding(top = 8.dp), fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(onClick = { copyPublic(k) }) {
                                    Icon(Icons.Outlined.ContentCopy, null, Modifier.padding(end = 6.dp))
                                    Text(stringResource(R.string.keys_copy_public))
                                }
                                TextButton(onClick = { deleting = k }) {
                                    Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
        }
    }

    if (creating) {
        var label by remember { mutableStateOf("") }
        var type by remember { mutableStateOf(KeyType.ED25519) }
        var passphrase by remember { mutableStateOf("") }
        var deviceOnly by remember { mutableStateOf(true) }
        var busy by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { if (!busy) creating = false },
            title = { Text(stringResource(R.string.keys_generate_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(label, { label = it }, label = { Text(stringResource(R.string.common_name)) }, singleLine = true,
                        placeholder = { Text(stringResource(R.string.keys_name_placeholder)) })
                    val types = listOf(KeyType.ED25519 to "Ed25519", KeyType.ECDSA_P256 to "ECDSA", KeyType.RSA4096 to "RSA 4096")
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        types.forEachIndexed { i, (t, n) ->
                            SegmentedButton(type == t, { type = t }, SegmentedButtonDefaults.itemShape(i, types.size)) { Text(n, fontSize = 12.sp) }
                        }
                    }
                    OutlinedTextField(passphrase, { passphrase = it }, label = { Text(stringResource(R.string.keys_passphrase_optional)) },
                        singleLine = true, visualTransformation = PasswordVisualTransformation())
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Checkbox(deviceOnly, { deviceOnly = it })
                        Text(stringResource(R.string.keys_device_only_check), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        try {
                            val name = label.trim().ifEmpty { android.os.Build.MODEL }
                            app.core.generateKey(name, type, "$name (Termoak)", passphrase.ifEmpty { null },
                                passphrase.isNotEmpty(), if (deviceOnly) SyncMode.DEVICE_ONLY else SyncMode.SYNCED)
                            reload()
                            app.account.sync()
                            creating = false
                        } catch (e: TermoakException) {
                            snackbar.showSnackbar(e.message ?: resources.getString(R.string.keys_generate_failed))
                        } finally {
                            busy = false
                        }
                    }
                }) { Text(stringResource(if (busy) R.string.keys_generating else R.string.keys_generate)) }
            },
            dismissButton = {
                TextButton(onClick = { creating = false }, enabled = !busy) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }

    if (importing) {
        var label by remember { mutableStateOf("") }
        var pem by remember { mutableStateOf("") }
        var passphrase by remember { mutableStateOf("") }
        val importedLabel = stringResource(R.string.keys_imported_label)
        AlertDialog(
            onDismissRequest = { importing = false },
            title = { Text(stringResource(R.string.keys_import_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(label, { label = it }, label = { Text(stringResource(R.string.common_name)) }, singleLine = true)
                    OutlinedTextField(pem, { pem = it }, label = { Text(stringResource(R.string.keys_private_key)) },
                        placeholder = { Text("-----BEGIN OPENSSH PRIVATE KEY-----") }, minLines = 4, maxLines = 8,
                        textStyle = Mono.copy(fontSize = 11.sp))
                    OutlinedTextField(passphrase, { passphrase = it }, label = { Text(stringResource(R.string.keys_passphrase_if_any)) },
                        singleLine = true, visualTransformation = PasswordVisualTransformation())
                }
            },
            confirmButton = {
                TextButton(enabled = pem.isNotBlank(), onClick = {
                    scope.launch {
                        try {
                            app.core.importKey(label.trim().ifEmpty { importedLabel }, pem.trim(), passphrase.ifEmpty { null },
                                passphrase.isNotEmpty(), null)
                            reload()
                            app.account.sync()
                            importing = false
                        } catch (e: TermoakException) {
                            snackbar.showSnackbar(e.message ?: resources.getString(R.string.keys_import_failed))
                        }
                    }
                }) { Text(stringResource(R.string.keys_import)) }
            },
            dismissButton = { TextButton(onClick = { importing = false }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }

    deleting?.let { k ->
        ConfirmDialog(
            stringResource(R.string.common_delete_named, k.label), stringResource(R.string.keys_delete_text),
            stringResource(R.string.common_delete), true,
            onDismiss = { deleting = null },
        ) {
            runCatching { app.core.deleteKey(k.id) }
            reload()
            app.account.sync()
        }
    }
}

@Composable
fun SnippetsScreen(app: TermoakApp, nav: NavHostController) {
    var snippets by remember { mutableStateOf(runCatching { app.core.listSnippets() }.getOrDefault(emptyList())) }
    var editing by remember { mutableStateOf<Snippet?>(null) }
    var deleting by remember { mutableStateOf<Snippet?>(null) }
    // "Run on several servers": hosts or groups, or the open terminals.
    var running by remember { mutableStateOf<Snippet?>(null) }
    val reload = { snippets = runCatching { app.core.listSnippets() }.getOrDefault(emptyList()).sortedBy { it.name.lowercase() } }
    val resources = LocalResources.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()

    VaultScaffold(
        VaultSection.SNIPPETS, nav,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editing = Snippet(id = "", name = "", script = "", description = "", tags = emptyList(), syncMode = null, updatedAt = 0L) },
                icon = { Icon(Icons.Outlined.Add, null) }, text = { Text(stringResource(R.string.snippets_new)) },
            )
        },
    ) { padding ->
        if (snippets.isEmpty()) {
            EmptyState(
                Icons.Outlined.Code, stringResource(R.string.snippets_empty_title),
                stringResource(R.string.snippets_empty_text),
                Modifier.padding(padding),
            )
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 96.dp, top = 8.dp)) {
                items(snippets, key = { it.id }) { sn ->
                    CardBox {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(sn.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                                IconButton(onClick = { running = sn }) {
                                    Icon(Icons.Outlined.PlayArrow, stringResource(R.string.multi_run_on), tint = MaterialTheme.colorScheme.primary)
                                }
                                IconButton(onClick = { editing = sn }) { Icon(Icons.Outlined.Edit, stringResource(R.string.common_edit)) }
                                IconButton(onClick = { deleting = sn }) { Icon(Icons.Outlined.Delete, stringResource(R.string.common_delete)) }
                            }
                            if (sn.description.isNotBlank()) {
                                Text(sn.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(sn.script, Modifier.padding(top = 6.dp), fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                                maxLines = 4, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }

    editing?.let { sn ->
        var name by remember(sn) { mutableStateOf(sn.name) }
        var script by remember(sn) { mutableStateOf(sn.script) }
        var description by remember(sn) { mutableStateOf(sn.description) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(stringResource(if (sn.id.isEmpty()) R.string.snippets_new else R.string.snippets_edit)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.common_name)) }, singleLine = true)
                    OutlinedTextField(script, { script = it }, label = { Text(stringResource(R.string.snippets_command)) }, minLines = 3,
                        textStyle = Mono, supportingText = { Text(stringResource(R.string.snippets_variables_hint)) })
                    OutlinedTextField(description, { description = it },
                        label = { Text(stringResource(R.string.snippets_description_optional)) })
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank() && script.isNotBlank(), onClick = {
                    try {
                        app.core.saveSnippet(sn.copy(name = name.trim(), script = script, description = description.trim()))
                        reload()
                        app.account.sync()
                        editing = null
                    } catch (e: TermoakException) {
                        scope.launch { snackbar.showSnackbar(e.message ?: resources.getString(R.string.error_save_failed)) }
                    }
                }) { Text(stringResource(R.string.common_save)) }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
    running?.let { sn ->
        RunSnippetSheet(app, sn, onDismiss = { running = null }) { running = null }
    }
    deleting?.let { sn ->
        ConfirmDialog(
            stringResource(R.string.common_delete_named, sn.name), stringResource(R.string.snippets_delete_text),
            stringResource(R.string.common_delete), true,
            onDismiss = { deleting = null },
        ) {
            runCatching { app.core.deleteSnippet(sn.id) }
            reload()
            app.account.sync()
        }
    }
}

/** Identities: a username with its password and/or key, to reuse on several hosts. */
@Composable
private fun IdentitiesList(app: TermoakApp, identities: List<SshIdentity>, keys: List<SshKey>, onChanged: () -> Unit) {
    var editing by remember { mutableStateOf<SshIdentity?>(null) }
    var deleting by remember { mutableStateOf<SshIdentity?>(null) }
    val resources = LocalResources.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    Box(Modifier.fillMaxSize()) {
        if (identities.isEmpty()) {
            EmptyState(
                Icons.Outlined.Person, stringResource(R.string.identities_empty_title),
                stringResource(R.string.identities_empty_text),
                action = stringResource(R.string.identities_new),
                onAction = { editing = SshIdentity(id = "", label = "", username = "", keyId = null, syncMode = null, hasPassword = false, updatedAt = 0L) },
            )
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 96.dp, top = 8.dp)) {
                items(identities, key = { it.id }) { idn ->
                    ListItem(
                        modifier = Modifier.clickable { editing = idn },
                        headlineContent = { Text(idn.label) },
                        supportingContent = {
                            Text(
                                listOfNotNull(
                                    idn.username,
                                    if (idn.hasPassword) stringResource(R.string.identities_has_password) else null,
                                    keys.firstOrNull { it.id == idn.keyId }?.label?.let { stringResource(R.string.identities_key, it) },
                                ).joinToString(" · "),
                            )
                        },
                        leadingContent = { HostTile(idn.label, null, size = 36.dp) },
                        trailingContent = {
                            IconButton(onClick = { deleting = idn }) { Icon(Icons.Outlined.Delete, stringResource(R.string.common_delete)) }
                        },
                    )
                }
            }
            ExtendedFloatingActionButton(
                onClick = { editing = SshIdentity(id = "", label = "", username = "", keyId = null, syncMode = null, hasPassword = false, updatedAt = 0L) },
                icon = { Icon(Icons.Outlined.Add, null) }, text = { Text(stringResource(R.string.identities_new)) },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            )
        }
    }
    editing?.let { idn ->
        var label by remember(idn) { mutableStateOf(idn.label) }
        var user by remember(idn) { mutableStateOf(idn.username) }
        var password by remember(idn) { mutableStateOf("") }
        var keyId by remember(idn) { mutableStateOf(idn.keyId) }
        val noKey = stringResource(R.string.common_no_key)
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(stringResource(if (idn.id.isEmpty()) R.string.identities_new else R.string.identities_edit)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(label, { label = it }, label = { Text(stringResource(R.string.common_name)) }, singleLine = true)
                    OutlinedTextField(user, { user = it.trim() }, label = { Text(stringResource(R.string.common_username)) }, singleLine = true)
                    OutlinedTextField(
                        password, { password = it },
                        label = {
                            Text(
                                stringResource(
                                    if (idn.hasPassword) R.string.identities_password_keep else R.string.identities_password_optional,
                                ),
                            )
                        },
                        singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    )
                    if (keys.isNotEmpty()) {
                        Picker(stringResource(R.string.common_key), keys.firstOrNull { it.id == keyId }?.label ?: noKey, null,
                            listOf<Pair<String?, String>>(null to noKey) + keys.map { it.id to it.label }) { keyId = it }
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = user.isNotBlank(), onClick = {
                    runCatching {
                        app.core.saveIdentity(
                            idn.copy(label = label.trim().ifEmpty { user }, username = user, keyId = keyId),
                            if (password.isEmpty()) SecretChange.Keep else SecretChange.Set(password),
                        )
                    }.onFailure { scope.launch { snackbar.showSnackbar(it.message ?: resources.getString(R.string.error_save_failed)) } }
                    editing = null
                    onChanged()
                    app.account.sync()
                }) { Text(stringResource(R.string.common_save)) }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
    deleting?.let { idn ->
        ConfirmDialog(
            stringResource(R.string.common_delete_named, idn.label), stringResource(R.string.identities_delete_text),
            stringResource(R.string.common_delete), true, onDismiss = { deleting = null },
        ) {
            runCatching { app.core.deleteIdentity(idn.id) }
            onChanged()
            app.account.sync()
        }
    }
}

/** Known hosts: the fingerprints of the servers you trust. */
@Composable
fun KnownHostsScreen(app: TermoakApp, nav: NavHostController) {
    var list by remember { mutableStateOf(runCatching { app.core.listKnownHosts() }.getOrDefault(emptyList())) }
    var deleting by remember { mutableStateOf<KnownHost?>(null) }
    VaultScaffold(VaultSection.KNOWN_HOSTS, nav) { padding ->
        if (list.isEmpty()) {
            EmptyState(
                Icons.Outlined.VerifiedUser, stringResource(R.string.known_hosts_empty_title),
                stringResource(R.string.known_hosts_empty_text),
                Modifier.padding(padding),
            )
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp, top = 8.dp)) {
                items(list.sortedBy { it.host }, key = { it.id }) { k ->
                    ListItem(
                        headlineContent = { Text(if (k.port == 22u) k.host else "${k.host}:${k.port}") },
                        supportingContent = {
                            Column {
                                Text(k.keyType, style = MaterialTheme.typography.labelMedium)
                                Text(k.fingerprint, fontFamily = FontFamily.Monospace, fontSize = 11.sp, maxLines = 1,
                                    overflow = TextOverflow.Ellipsis)
                            }
                        },
                        leadingContent = { Icon(Icons.Outlined.VerifiedUser, null, tint = Brand.Green) },
                        trailingContent = {
                            IconButton(onClick = { deleting = k }) {
                                Icon(Icons.Outlined.Delete, stringResource(R.string.known_hosts_forget))
                            }
                        },
                    )
                }
            }
        }
    }
    deleting?.let { k ->
        ConfirmDialog(
            stringResource(R.string.known_hosts_forget_title, k.host), stringResource(R.string.known_hosts_forget_text),
            stringResource(R.string.known_hosts_forget), true, onDismiss = { deleting = null },
        ) {
            runCatching { app.core.deleteKnownHost(k.id) }
            list = runCatching { app.core.listKnownHosts() }.getOrDefault(emptyList())
            app.account.sync()
        }
    }
}
