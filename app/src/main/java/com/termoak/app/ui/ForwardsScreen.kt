package com.termoak.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.termoak.app.R
import com.termoak.app.data.canWrite
import com.termoak.app.data.uid
import com.termoak.app.userMessage
import com.termoak.ffi.SyncMode
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.runtime.LaunchedEffect
import com.termoak.app.TermoakApp
import com.termoak.ffi.ForwardKind
import com.termoak.ffi.PortForward
import com.termoak.ffi.SshHost
import com.termoak.ffi.TermoakException
import kotlinx.coroutines.launch

/**
 * Tunnels (port forwarding) saved in the vault: local, remote and dynamic
 * (SOCKS5). They sync with the other devices; the desktop app starts them.
 */
@Composable
fun ForwardsScreen(app: TermoakApp, nav: NavHostController) {
    val resources = LocalResources.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    fun load() = runCatching { app.core.listForwards(null, app.accounts.filter()) }.getOrDefault(emptyList()).sortedBy { it.label.lowercase() }
    var forwards by remember { mutableStateOf(load()) }
    fun loadHosts() = runCatching { app.core.listHosts(app.accounts.filter()) }.getOrDefault(emptyList()).sortedBy { it.label.lowercase() }
    var hosts by remember { mutableStateOf(loadHosts()) }
    LaunchedEffect(Unit) { app.accounts.itemsChanged.collect { forwards = load(); hosts = loadHosts() } }
    // A tunnel lives with its host (same account and vault).
    val byId = remember(hosts) { hosts.associateBy { it.accountId to it.id } }
    // Hosts a tunnel can be added to (not those of Use-only vaults).
    val usable = hosts.filter { it.access.canWrite() }
    var editing by remember { mutableStateOf<PortForward?>(null) }
    var deleting by remember { mutableStateOf<PortForward?>(null) }

    fun newForward() {
        if (usable.isEmpty()) {
            scope.launch { snackbar.showSnackbar(resources.getString(R.string.forwards_no_hosts)) }
            return
        }
        val h = usable.first()
        editing = PortForward(
            id = "", label = "", hostId = h.id, kind = ForwardKind.LOCAL, destHost = "localhost",
            accountId = h.accountId, vaultId = h.vaultId,
        )
    }

    VaultScaffold(
        VaultSection.TUNNELS, nav,
        floatingActionButton = {
            if (forwards.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { newForward() }, icon = { Icon(Icons.Outlined.Add, null) },
                    text = { Text(stringResource(R.string.forwards_new)) },
                )
            }
        },
    ) { padding ->
        if (forwards.isEmpty()) {
            EmptyState(
                Icons.Outlined.SwapHoriz, stringResource(R.string.forwards_empty_title),
                stringResource(R.string.forwards_empty_text), Modifier.padding(padding),
                action = stringResource(R.string.forwards_new), onAction = { newForward() },
            )
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp)) {
                item {
                    Row(
                        Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Info, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            stringResource(R.string.forwards_note), Modifier.padding(start = 8.dp),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(forwards, key = { it.uid }) { f ->
                    ForwardRow(
                        f, byId[f.accountId to f.hostId] ?: byId[null to f.hostId],
                        onClick = { if (f.access.canWrite()) editing = f }, onDelete = if (f.access.canWrite()) ({ deleting = f }) else null,
                    )
                }
            }
        }
    }

    editing?.let { f ->
        // An existing tunnel stays in its place; a new one goes with the host chosen.
        val choices = if (f.id.isEmpty()) usable else usable.filter { it.accountId == f.accountId && it.vaultId == f.vaultId }
        ForwardDialog(f, choices, onDismiss = { editing = null }) { chosen ->
            val host = choices.firstOrNull { it.id == chosen.hostId }
            val saved = if (f.id.isEmpty() && host != null) {
                chosen.copy(
                    accountId = host.accountId, vaultId = host.vaultId,
                    syncMode = if (host.accountId == null && app.accounts.list.value.isNotEmpty()) SyncMode.DEVICE_ONLY else chosen.syncMode,
                )
            } else chosen
            try {
                app.core.saveForward(saved)
                forwards = load()
                app.accounts.sync()
                editing = null
            } catch (e: TermoakException) {
                scope.launch { snackbar.showSnackbar(e.userMessage(resources, R.string.error_save_failed)) }
            }
        }
    }
    deleting?.let { f ->
        ConfirmDialog(
            stringResource(R.string.common_delete_named, f.label), stringResource(R.string.hosts_delete_text),
            stringResource(R.string.common_delete), destructive = true, onDismiss = { deleting = null },
        ) {
            runCatching { app.core.deleteForward(f.id, f.accountId) }
            forwards = load()
            app.accounts.sync()
        }
    }
}

/** "127.0.0.1:8080 → db:5432 via web-1" and the like. */
@Composable
private fun route(f: PortForward, host: String): String {
    val bind = "${f.bindAddress}:${if (f.bindPort == 0u) "*" else f.bindPort.toString()}"
    val dest = "${f.destHost ?: "localhost"}:${f.destPort ?: 0u}"
    return when (f.kind) {
        ForwardKind.LOCAL -> stringResource(R.string.forwards_route_local, bind, dest, host)
        ForwardKind.REMOTE -> stringResource(R.string.forwards_route_remote, host, bind, dest)
        ForwardKind.DYNAMIC -> stringResource(R.string.forwards_route_dynamic, bind, host)
    }
}

@Composable
private fun ForwardRow(f: PortForward, host: SshHost?, onClick: () -> Unit, onDelete: (() -> Unit)?) {
    val hostLabel = host?.label ?: "?"
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                when (f.kind) {
                    ForwardKind.LOCAL -> "L"
                    ForwardKind.REMOTE -> "R"
                    ForwardKind.DYNAMIC -> "D"
                },
                style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    f.label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (f.autoStart) Pill(stringResource(R.string.forwards_auto), Brand.Green)
            }
            Text(
                route(f, hostLabel), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
        if (onDelete != null) {
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, stringResource(R.string.common_delete), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Icon(Icons.Outlined.Lock, stringResource(R.string.vault_use_only), Modifier.padding(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ForwardDialog(f: PortForward, hosts: List<SshHost>, onDismiss: () -> Unit, onSave: (PortForward) -> Unit) {
    var label by remember { mutableStateOf(f.label) }
    var hostId by remember { mutableStateOf(f.hostId) }
    var kind by remember { mutableStateOf(f.kind) }
    var bindAddress by remember { mutableStateOf(f.bindAddress) }
    var bindPort by remember { mutableStateOf(if (f.id.isEmpty() && f.bindPort == 0u) "" else f.bindPort.toString()) }
    var destHost by remember { mutableStateOf(f.destHost ?: "") }
    var destPort by remember { mutableStateOf(f.destPort?.toString() ?: "") }
    var autoStart by remember { mutableStateOf(f.autoStart) }

    val bind = bindPort.trim().ifEmpty { "0" }.toUIntOrNull()?.takeIf { it <= 65535u }
    val dPort = destPort.trim().toUIntOrNull()?.takeIf { it in 1u..65535u }
    val needsDest = kind != ForwardKind.DYNAMIC
    val valid = label.isNotBlank() && bind != null && hostId.isNotEmpty() && (!needsDest || (destHost.isNotBlank() && dPort != null))
    val kinds = listOf(
        ForwardKind.LOCAL to R.string.forwards_kind_local,
        ForwardKind.REMOTE to R.string.forwards_kind_remote,
        ForwardKind.DYNAMIC to R.string.forwards_kind_dynamic,
    )
    val number = KeyboardOptions(keyboardType = KeyboardType.Number)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (f.id.isEmpty()) R.string.forwards_new else R.string.forwards_edit)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    label, { label = it }, Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text(stringResource(R.string.common_name)) },
                    placeholder = { Text(stringResource(R.string.forwards_label_placeholder)) },
                )
                Picker(
                    stringResource(R.string.forwards_ssh_host), hosts.firstOrNull { it.id == hostId }?.label, null,
                    hosts.map { it.id to it.label },
                ) { hostId = it }
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    kinds.forEachIndexed { i, (k, text) ->
                        SegmentedButton(kind == k, { kind = k }, SegmentedButtonDefaults.itemShape(i, kinds.size)) {
                            Text(stringResource(text), maxLines = 1)
                        }
                    }
                }
                Text(
                    stringResource(
                        when (kind) {
                            ForwardKind.LOCAL -> R.string.forwards_kind_local_hint
                            ForwardKind.REMOTE -> R.string.forwards_kind_remote_hint
                            ForwardKind.DYNAMIC -> R.string.forwards_kind_dynamic_hint
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        bindAddress, { bindAddress = it.trim() }, Modifier.weight(1.6f), singleLine = true,
                        label = { Text(stringResource(R.string.forwards_bind_address)) },
                    )
                    OutlinedTextField(
                        bindPort, { bindPort = it.filter(Char::isDigit) }, Modifier.weight(1f), singleLine = true,
                        label = { Text(stringResource(R.string.common_port)) }, placeholder = { Text("0") },
                        keyboardOptions = number, isError = bind == null,
                    )
                }
                if (needsDest) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            destHost, { destHost = it.trim() }, Modifier.weight(1.6f), singleLine = true,
                            label = { Text(stringResource(R.string.forwards_destination)) },
                        )
                        OutlinedTextField(
                            destPort, { destPort = it.filter(Char::isDigit) }, Modifier.weight(1f), singleLine = true,
                            label = { Text(stringResource(R.string.common_port)) }, keyboardOptions = number,
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable { autoStart = !autoStart },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(autoStart, { autoStart = it })
                    Text(stringResource(R.string.forwards_auto_start), style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                onSave(
                    f.copy(
                        label = label.trim(), hostId = hostId, kind = kind,
                        bindAddress = bindAddress.ifBlank { "127.0.0.1" }, bindPort = bind ?: 0u,
                        destHost = if (needsDest) destHost else null, destPort = if (needsDest) dPort else null,
                        autoStart = autoStart,
                    ),
                )
            }) { Text(stringResource(R.string.common_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}
