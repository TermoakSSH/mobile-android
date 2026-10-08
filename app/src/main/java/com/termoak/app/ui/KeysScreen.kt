package com.termoak.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
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
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.termoak.app.data.canWrite
import com.termoak.app.data.uid
import com.termoak.app.data.useOnly
import com.termoak.app.userMessage
import com.termoak.ffi.KeyDetails
import com.termoak.ffi.KeyType
import com.termoak.ffi.KnownHost
import com.termoak.ffi.SecretChange
import com.termoak.ffi.Snippet
import com.termoak.ffi.SshIdentity
import com.termoak.ffi.SshKey
import com.termoak.ffi.SyncMode
import com.termoak.ffi.TermoakException
import com.termoak.ffi.inspectPrivateKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Whether there is any account (This-device items then show a phone). */
private fun hasAccounts(app: TermoakApp) = app.accounts.list.value.isNotEmpty()

/** A new identity, in the place new items go. */
private fun newIdentity(app: TermoakApp): SshIdentity {
    val p = app.accounts.defaultPlace()
    return SshIdentity(
        id = "", label = "", username = "", keyId = null,
        syncMode = if (p.device && hasAccounts(app)) SyncMode.DEVICE_ONLY else null, hasPassword = false, updatedAt = 0L,
        accountId = p.account, vaultId = p.vault,
    )
}

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
    var keys by remember { mutableStateOf(runCatching { app.core.listKeys(app.accounts.filter()) }.getOrDefault(emptyList()).sortedBy { it.label.lowercase() }) }
    var creating by rememberSaveable { mutableStateOf(action == KeysAction.GENERATE) }
    var importing by rememberSaveable { mutableStateOf(action == KeysAction.IMPORT) }
    var deleting by remember { mutableStateOf<SshKey?>(null) }
    var fab by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf(0) }
    var identities by remember { mutableStateOf(runCatching { app.core.listIdentities(app.accounts.filter()) }.getOrDefault(emptyList())) }
    val reload = { keys = runCatching { app.core.listKeys(app.accounts.filter()) }.getOrDefault(emptyList()).sortedBy { it.label.lowercase() } }
    // Vault chips and account avatars, like the hosts': with several vaults or accounts in view.
    val accountList by app.accounts.list.collectAsState()
    val vaults by app.accounts.vaults.collectAsState()
    val view by app.accounts.view.collectAsState()
    val showVaults = app.accounts.vaultsInView().size > 1 ||
        (accountList.isNotEmpty() && keys.any { it.accountId == null } && keys.any { it.accountId != null })
    val showAccounts = accountList.size > 1 && view == com.termoak.app.data.AccountView.All
    LaunchedEffect(Unit) {
        app.accounts.itemsChanged.collect {
            reload()
            identities = runCatching { app.core.listIdentities(app.accounts.filter()) }.getOrDefault(emptyList())
        }
    }

    val hasAccounts = hasAccounts(app)

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
            IdentitiesList(app, identities, keys, onChanged = { identities = runCatching { app.core.listIdentities(app.accounts.filter()) }.getOrDefault(emptyList()) })
        } else if (keys.isEmpty()) {
            EmptyState(
                Icons.Outlined.Key, stringResource(R.string.keys_empty_title),
                stringResource(R.string.keys_empty_text),
                action = stringResource(R.string.keys_generate_title), onAction = { creating = true },
            )
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp, top = 8.dp)) {
                items(keys, key = { it.uid }) { k ->
                    CardBox {
                        // A tap opens its page: details, QR code, install on a host, export.
                        Column(Modifier.clickable { nav.navigate(Routes.key(k.id, k.accountId)) }.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Key, null, tint = MaterialTheme.colorScheme.primary)
                                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                    Text(k.label, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        if (k.hasPassphrase) stringResource(R.string.keys_with_passphrase, k.algorithm) else k.algorithm,
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (k.access.useOnly()) {
                                    Icon(Icons.Outlined.Lock, stringResource(R.string.vault_use_only), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                } else if (k.syncMode == SyncMode.DEVICE_ONLY || (k.accountId == null && hasAccounts)) {
                                    Icon(
                                        Icons.Outlined.PhoneAndroid, stringResource(R.string.common_device_only),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            // Its vault (with several), as on iOS; the fingerprint can be selected and copied.
                            if (showVaults) {
                                val v = vaults.firstOrNull { it.id == k.vaultId && it.accountId == k.accountId }
                                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    if (showAccounts) {
                                        accountList.firstOrNull { it.id == k.accountId }?.let { AccountAvatar(it, 16.dp) }
                                        Spacer(Modifier.width(6.dp))
                                    }
                                    VaultChip(
                                        if (k.accountId == null) stringResource(R.string.vault_this_device) else v?.let { vaultName(it) } ?: "",
                                        if (k.accountId == null) MaterialTheme.colorScheme.onSurfaceVariant else vaultColor(v),
                                        useOnly = k.access.useOnly(),
                                    )
                                }
                            }
                            SelectionContainer {
                                Text(k.fingerprint, Modifier.padding(top = 8.dp), fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(onClick = { copyPublic(k) }) {
                                    Icon(Icons.Outlined.ContentCopy, null, Modifier.padding(end = 6.dp))
                                    Text(stringResource(R.string.keys_copy_public))
                                }
                                if (k.access.canWrite()) {
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
    }

    if (creating) {
        var label by remember { mutableStateOf("") }
        var type by remember { mutableStateOf(KeyType.ED25519) }
        var comment by remember { mutableStateOf("") }
        var passphrase by remember { mutableStateOf("") }
        var storePassphrase by remember { mutableStateOf(true) }
        // Where it goes: a private key stays on this phone unless you choose a vault (as on iOS).
        var place by remember { mutableStateOf(com.termoak.app.data.Place.DEVICE) }
        var busy by remember { mutableStateOf(false) }
        val defaultName = android.os.Build.MODEL
        AlertDialog(
            onDismissRequest = { if (!busy) creating = false },
            title = { Text(stringResource(R.string.keys_generate_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(label, { label = it }, label = { Text(stringResource(R.string.common_name)) }, singleLine = true,
                        placeholder = { Text(stringResource(R.string.keys_name_placeholder)) })
                    // Every type the engine makes (as on iOS).
                    Picker(stringResource(R.string.keys_detail_type), KeyTypes.firstOrNull { it.first == type }?.second, null, KeyTypes) { type = it }
                    OutlinedTextField(
                        comment, { comment = it }, singleLine = true,
                        label = { Text(stringResource(R.string.keys_comment_placeholder, label.trim().ifEmpty { defaultName })) },
                    )
                    OutlinedTextField(passphrase, { passphrase = it }, label = { Text(stringResource(R.string.keys_passphrase_optional)) },
                        singleLine = true, visualTransformation = PasswordVisualTransformation())
                    if (passphrase.isNotEmpty()) StorePassphraseSwitch(storePassphrase) { storePassphrase = it }
                    PlacePicker(app, place) { place = it }
                }
            },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        try {
                            val name = label.trim().ifEmpty { defaultName }
                            app.core.generateKey(name, type, comment.trim().ifEmpty { "$name (Termoak)" }, passphrase.ifEmpty { null },
                                passphrase.isNotEmpty() && storePassphrase, if (place.device) SyncMode.DEVICE_ONLY else SyncMode.SYNCED,
                                place.account, place.vault)
                            app.accounts.rememberPlace(place)
                            reload()
                            app.accounts.sync()
                            creating = false
                        } catch (e: TermoakException) {
                            snackbar.showSnackbar(e.userMessage(resources, R.string.keys_generate_failed))
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
        var storePassphrase by remember { mutableStateOf(true) }
        var place by remember { mutableStateOf(app.accounts.defaultPlace()) }
        // What the key is (type, fingerprint...), checked without saving it.
        var details by remember { mutableStateOf<KeyDetails?>(null) }
        var checking by remember { mutableStateOf(false) }
        var problem by remember { mutableStateOf<String?>(null) }
        val importedLabel = stringResource(R.string.keys_imported_label)
        fun check() {
            checking = true
            problem = null
            scope.launch {
                try {
                    details = inspectPrivateKey(pem.trim(), passphrase.ifEmpty { null })
                    if (label.isBlank()) details?.comment?.takeIf { it.isNotBlank() }?.let { label = it }
                } catch (e: TermoakException) {
                    details = null
                    problem = e.userMessage(resources, R.string.keys_import_not_a_key)
                } finally {
                    checking = false
                }
            }
        }
        val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch {
                // A private key is small: anything big is not one.
                val text = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            val bytes = input.readBytes()
                            if (bytes.size > 64 * 1024 || bytes.contains(0)) null else String(bytes, Charsets.UTF_8)
                        }
                    }.getOrNull()
                }
                if (text == null) {
                    problem = resources.getString(R.string.keys_import_not_a_key)
                } else {
                    pem = text
                    if (label.isBlank()) {
                        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                            ?.use { c -> if (c.moveToFirst()) label = c.getString(0).substringBeforeLast('.') }
                    }
                    check()
                }
            }
        }
        AlertDialog(
            onDismissRequest = { importing = false },
            title = { Text(stringResource(R.string.keys_import_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(label, { label = it }, label = { Text(stringResource(R.string.common_name)) }, singleLine = true)
                    TextButton(onClick = { pickFile.launch(arrayOf("*/*")) }) {
                        Icon(Icons.Outlined.FolderOpen, null, Modifier.padding(end = 6.dp))
                        Text(stringResource(R.string.keys_import_choose_file))
                    }
                    OutlinedTextField(pem, { pem = it; details = null }, label = { Text(stringResource(R.string.keys_private_key)) },
                        placeholder = { Text("-----BEGIN OPENSSH PRIVATE KEY-----") }, minLines = 4, maxLines = 8,
                        textStyle = Mono.copy(fontSize = 11.sp))
                    OutlinedTextField(passphrase, { passphrase = it; details = null }, label = { Text(stringResource(R.string.keys_passphrase_if_any)) },
                        singleLine = true, visualTransformation = PasswordVisualTransformation())
                    if (passphrase.isNotEmpty()) StorePassphraseSwitch(storePassphrase) { storePassphrase = it }
                    TextButton(onClick = { check() }, enabled = pem.isNotBlank() && !checking) {
                        Icon(Icons.Outlined.VerifiedUser, null, Modifier.padding(end = 6.dp))
                        Text(stringResource(R.string.keys_import_check))
                    }
                    details?.let { d ->
                        Column {
                            Text(d.algorithm, style = MaterialTheme.typography.titleSmall)
                            Text(d.fingerprint, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                            if (d.comment.isNotBlank()) Text(d.comment, style = MaterialTheme.typography.bodySmall)
                            if (d.encrypted) Text(stringResource(R.string.keys_import_encrypted), style = MaterialTheme.typography.bodySmall,
                                color = Brand.Amber)
                        }
                    }
                    problem?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    PlacePicker(app, place) { place = it }
                }
            },
            confirmButton = {
                TextButton(enabled = pem.isNotBlank(), onClick = {
                    scope.launch {
                        try {
                            app.core.importKey(label.trim().ifEmpty { importedLabel }, pem.trim(), passphrase.ifEmpty { null },
                                passphrase.isNotEmpty() && storePassphrase, if (place.device) SyncMode.DEVICE_ONLY else SyncMode.SYNCED, place.account, place.vault)
                            app.accounts.rememberPlace(place)
                            reload()
                            app.accounts.sync()
                            importing = false
                        } catch (e: TermoakException) {
                            snackbar.showSnackbar(e.userMessage(resources, R.string.keys_import_failed))
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
            runCatching { app.core.deleteKey(k.id, k.accountId) }
                .onFailure { scope.launch { snackbar.showSnackbar(it.userMessage(resources, R.string.error_save_failed)) } }
            reload()
            app.accounts.sync()
        }
    }
}

@Composable
fun SnippetsScreen(app: TermoakApp, nav: NavHostController) {
    var snippets by remember { mutableStateOf(runCatching { app.core.listSnippets(app.accounts.filter()) }.getOrDefault(emptyList()).sortedBy { it.name.lowercase() }) }
    var editing by remember { mutableStateOf<Snippet?>(null) }
    var deleting by remember { mutableStateOf<Snippet?>(null) }
    // "Run on several servers": hosts or groups, or the open terminals.
    var running by remember { mutableStateOf<Snippet?>(null) }
    val reload = { snippets = runCatching { app.core.listSnippets(app.accounts.filter()) }.getOrDefault(emptyList()).sortedBy { it.name.lowercase() } }
    LaunchedEffect(Unit) { app.accounts.itemsChanged.collect { reload() } }
    val resources = LocalResources.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()

    VaultScaffold(
        VaultSection.SNIPPETS, nav,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    val place = app.accounts.defaultPlace()
                    editing = Snippet(
                        id = "", name = "", script = "", description = "", tags = emptyList(),
                        syncMode = if (place.device && hasAccounts(app)) SyncMode.DEVICE_ONLY else null, updatedAt = 0L,
                        accountId = place.account, vaultId = place.vault,
                    )
                },
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
                items(snippets, key = { it.uid }) { sn ->
                    CardBox {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(sn.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                                IconButton(onClick = { running = sn }) {
                                    Icon(Icons.Outlined.PlayArrow, stringResource(R.string.multi_run_on), tint = MaterialTheme.colorScheme.primary)
                                }
                                if (sn.access.canWrite()) {
                                    IconButton(onClick = { editing = sn }) { Icon(Icons.Outlined.Edit, stringResource(R.string.common_edit)) }
                                    IconButton(onClick = { deleting = sn }) { Icon(Icons.Outlined.Delete, stringResource(R.string.common_delete)) }
                                } else {
                                    Icon(Icons.Outlined.Lock, stringResource(R.string.vault_use_only), Modifier.padding(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
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
        var place by remember(sn) { mutableStateOf(com.termoak.app.data.Place(sn.accountId, sn.vaultId)) }
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
                    if (sn.id.isEmpty()) PlacePicker(app, place) { place = it }
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank() && script.isNotBlank(), onClick = {
                    try {
                        app.core.saveSnippet(
                            sn.copy(name = name.trim(), script = script, description = description.trim()).let {
                                if (it.id.isEmpty()) {
                                    app.accounts.rememberPlace(place)
                                    it.copy(accountId = place.account, vaultId = place.vault, syncMode = place.syncMode(hasAccounts(app)))
                                } else it
                            },
                        )
                        reload()
                        app.accounts.sync()
                        editing = null
                    } catch (e: TermoakException) {
                        scope.launch { snackbar.showSnackbar(e.userMessage(resources, R.string.error_save_failed)) }
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
            runCatching { app.core.deleteSnippet(sn.id, sn.accountId) }
                .onFailure { scope.launch { snackbar.showSnackbar(it.userMessage(resources, R.string.error_save_failed)) } }
            reload()
            app.accounts.sync()
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
                onAction = { editing = newIdentity(app) },
            )
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 96.dp, top = 8.dp)) {
                items(identities, key = { it.uid }) { idn ->
                    ListItem(
                        // A Use-only identity can't be changed (its secrets are hidden too): no editor.
                        modifier = if (idn.access.canWrite()) Modifier.clickable { editing = idn } else Modifier,
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
                            if (idn.access.canWrite()) {
                                IconButton(onClick = { deleting = idn }) { Icon(Icons.Outlined.Delete, stringResource(R.string.common_delete)) }
                            } else {
                                Icon(Icons.Outlined.Lock, stringResource(R.string.vault_use_only), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                    )
                }
            }
            ExtendedFloatingActionButton(
                onClick = { editing = newIdentity(app) },
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
        var place by remember(idn) { mutableStateOf(com.termoak.app.data.Place(idn.accountId, idn.vaultId)) }
        var forgetPassword by remember(idn) { mutableStateOf(false) }
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
                        singleLine = true, visualTransformation = PasswordVisualTransformation(), enabled = !forgetPassword,
                    )
                    // Forget the saved password (as on iOS).
                    if (idn.hasPassword) {
                        Row(
                            Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable { forgetPassword = !forgetPassword },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            androidx.compose.material3.Checkbox(forgetPassword, { forgetPassword = it })
                            Text(stringResource(R.string.identities_clear_password), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    if (idn.id.isEmpty()) PlacePicker(app, place) { place = it; keyId = null }
                    // Keys of the identity's own place (or This device).
                    val usable = keys.filter { place.reaches(it.accountId, it.vaultId) }
                    if (usable.isNotEmpty()) {
                        Picker(stringResource(R.string.common_key), usable.firstOrNull { it.id == keyId }?.label ?: noKey, null,
                            listOf<Pair<String?, String>>(null to noKey) + usable.map { it.id to it.label }) { keyId = it }
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = user.isNotBlank() && idn.access.canWrite(), onClick = {
                    runCatching {
                        app.core.saveIdentity(
                            idn.copy(label = label.trim().ifEmpty { user }, username = user, keyId = keyId).let {
                                if (it.id.isEmpty()) {
                                    app.accounts.rememberPlace(place)
                                    it.copy(accountId = place.account, vaultId = place.vault, syncMode = place.syncMode(hasAccounts(app)))
                                } else it
                            },
                            when {
                                forgetPassword -> SecretChange.Clear
                                password.isEmpty() -> SecretChange.Keep
                                else -> SecretChange.Set(password)
                            },
                        )
                    }.onFailure { scope.launch { snackbar.showSnackbar(it.userMessage(resources, R.string.error_save_failed)) } }
                    editing = null
                    onChanged()
                    app.accounts.sync()
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
            runCatching { app.core.deleteIdentity(idn.id, idn.accountId) }
                .onFailure { scope.launch { snackbar.showSnackbar(it.userMessage(resources, R.string.error_save_failed)) } }
            onChanged()
            app.accounts.sync()
        }
    }
}

/** Known hosts: the fingerprints of the servers you trust. */
@Composable
fun KnownHostsScreen(app: TermoakApp, nav: NavHostController) {
    var list by remember { mutableStateOf(runCatching { app.core.listKnownHosts(app.accounts.filter()) }.getOrDefault(emptyList())) }
    LaunchedEffect(Unit) { app.accounts.itemsChanged.collect { list = runCatching { app.core.listKnownHosts(app.accounts.filter()) }.getOrDefault(emptyList()) } }
    var deleting by remember { mutableStateOf<KnownHost?>(null) }
    var query by remember { mutableStateOf("") }
    val resources = LocalResources.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    VaultScaffold(VaultSection.KNOWN_HOSTS, nav) { padding ->
        if (list.isEmpty()) {
            EmptyState(
                Icons.Outlined.VerifiedUser, stringResource(R.string.known_hosts_empty_title),
                stringResource(R.string.known_hosts_empty_text),
                Modifier.padding(padding),
            )
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp, top = 8.dp)) {
                // Search by host, key type or fingerprint.
                item {
                    OutlinedTextField(
                        query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        placeholder = { Text(stringResource(R.string.known_hosts_search)) }, singleLine = true,
                        leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    )
                }
                val q = query.trim().lowercase()
                val shown = list.sortedBy { it.host }.filter { k ->
                    q.isEmpty() || listOf(k.host, k.keyType, k.fingerprint, k.port.toString()).any { it.lowercase().contains(q) }
                }
                items(shown, key = { it.uid }) { k ->
                    ListItem(
                        headlineContent = { Text(if (k.port == 22u) k.host else "${k.host}:${k.port}") },
                        supportingContent = {
                            Column {
                                Text(k.keyType, style = MaterialTheme.typography.labelMedium)
                                // The whole fingerprint, selectable (to compare it with the server's).
                                SelectionContainer {
                                    Text(k.fingerprint, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                                }
                            }
                        },
                        leadingContent = { Icon(Icons.Outlined.VerifiedUser, null, tint = Brand.Green) },
                        trailingContent = {
                            if (k.access.canWrite()) {
                                IconButton(onClick = { deleting = k }) {
                                    Icon(Icons.Outlined.Delete, stringResource(R.string.known_hosts_forget))
                                }
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
            runCatching { app.core.deleteKnownHost(k.id, k.accountId) }
                .onFailure { scope.launch { snackbar.showSnackbar(it.userMessage(resources, R.string.error_save_failed)) } }
            list = runCatching { app.core.listKnownHosts(app.accounts.filter()) }.getOrDefault(emptyList())
            app.accounts.sync()
        }
    }
}

/** Every key type the engine generates, as on iOS. */
private val KeyTypes = listOf(
    KeyType.ED25519 to "Ed25519", KeyType.ECDSA_P256 to "ECDSA P-256", KeyType.ECDSA_P384 to "ECDSA P-384",
    KeyType.ECDSA_P521 to "ECDSA P-521", KeyType.RSA4096 to "RSA 4096", KeyType.RSA3072 to "RSA 3072", KeyType.RSA2048 to "RSA 2048",
)

/** "Save the passphrase": off, it is asked every time the key is used. */
@Composable
private fun StorePassphraseSwitch(on: Boolean, onChange: (Boolean) -> Unit) {
    Column {
        Row(
            Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable { onChange(!on) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.keys_store_passphrase), Modifier.weight(1f))
            androidx.compose.material3.Switch(on, onChange)
        }
        if (!on) {
            Text(stringResource(R.string.keys_store_passphrase_off), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
