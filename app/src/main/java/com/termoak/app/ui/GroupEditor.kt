package com.termoak.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.termoak.app.R
import com.termoak.app.TermoakApp
import com.termoak.app.data.Place
import com.termoak.ffi.HostGroup
import com.termoak.ffi.ItemFilter

/**
 * A group: its name, where a new top-level one goes, its colour, and its
 * defaults for the hosts inside it and its subgroups, like the desktop's
 * group settings (user, port, identity or key, keep-alive, TERM, agent
 * forwarding and recording: Not set / On / Off). A host's own value wins;
 * the engine applies them when connecting, the nearest group winning.
 */
@Composable
internal fun GroupEditorDialog(app: TermoakApp, group: HostGroup, onDismiss: () -> Unit, onSave: (HostGroup, Place?) -> Unit) {
    val accountList by app.accounts.list.collectAsState()
    val isNew = group.id.isEmpty()
    val topLevel = isNew && group.parentId == null
    var name by remember(group) { mutableStateOf(group.name) }
    var place by remember(group) { mutableStateOf(Place(group.accountId, group.vaultId)) }
    var color by remember(group) { mutableStateOf(group.color) }
    val s0 = group.settings
    var user by remember(group) { mutableStateOf(s0.username.orEmpty()) }
    var port by remember(group) { mutableStateOf(s0.port?.toString().orEmpty()) }
    var keepalive by remember(group) { mutableStateOf(s0.keepaliveSecs?.toString().orEmpty()) }
    var term by remember(group) { mutableStateOf(s0.term.orEmpty()) }
    var credential by remember(group) {
        mutableStateOf(s0.identityId?.let { "identity:$it" } ?: s0.keyId?.let { "key:$it" } ?: "")
    }
    var agent by remember(group) { mutableStateOf(s0.agentForwarding) }
    var record by remember(group) { mutableStateOf(s0.recordSessions) }
    // Identities and keys a host of the group can use: its vault's and This device's.
    val filter = ItemFilter(accountIds = listOfNotNull(place.account), vaultIds = place.vault?.let { listOf(it) }, includeDevice = true)
    val identities = remember(place) { runCatching { app.core.listIdentities(filter) }.getOrDefault(emptyList()).filter { place.reaches(it.accountId, it.vaultId) } }
    val keys = remember(place) { runCatching { app.core.listKeys(filter) }.getOrDefault(emptyList()).filter { place.reaches(it.accountId, it.vaultId) } }
    val notSet = stringResource(R.string.group_defaults_not_set)
    val choices = listOf<Pair<Boolean?, String>>(null to notSet, true to stringResource(R.string.group_defaults_on), false to stringResource(R.string.group_defaults_off))
    val credentials = listOf("" to notSet) + identities.map { "identity:${it.id}" to "${it.label} (${it.username})" } +
        keys.map { "key:${it.id}" to "${it.label} · ${it.algorithm}" }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (isNew) R.string.hosts_new_group else R.string.group_edit_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.common_name)) }, singleLine = true)
                // A new group at the top: where it goes (inside a group, its parent's place).
                if (topLevel && accountList.isNotEmpty()) PlacePicker(app, place) { place = it; credential = "" }
                Text(stringResource(R.string.group_color), style = MaterialTheme.typography.labelLarge)
                ColorChoice(color) { color = it }
                Text(stringResource(R.string.group_defaults), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.group_defaults_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(user, { user = it.trim() }, Modifier.weight(1f), label = { Text(stringResource(R.string.common_username)) }, singleLine = true)
                    OutlinedTextField(
                        port, { port = it.filter(Char::isDigit).take(5) }, Modifier.width(100.dp), label = { Text(stringResource(R.string.common_port)) },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
                Picker(stringResource(R.string.group_defaults_credential), credentials.firstOrNull { it.first == credential }?.second ?: notSet, null, credentials) {
                    credential = it
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        keepalive, { keepalive = it.filter(Char::isDigit).take(6) }, Modifier.weight(1f),
                        label = { Text(stringResource(R.string.editor_keepalive)) }, placeholder = { Text("30") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    OutlinedTextField(
                        term, { term = it.trim() }, Modifier.weight(1f), label = { Text(stringResource(R.string.editor_term)) },
                        placeholder = { Text("xterm-256color") }, singleLine = true,
                    )
                }
                Picker(stringResource(R.string.editor_agent_forwarding), choices.first { it.first == agent }.second, null, choices) { agent = it }
                Picker(stringResource(R.string.editor_record), choices.first { it.first == record }.second, null, choices) { record = it }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                val settings = s0.copy(
                    username = user.ifEmpty { null },
                    port = port.toUIntOrNull()?.takeIf { it in 1u..65535u },
                    keepaliveSecs = keepalive.toUIntOrNull(),
                    term = term.ifEmpty { null },
                    identityId = credential.removePrefix("identity:").takeIf { credential.startsWith("identity:") },
                    keyId = credential.removePrefix("key:").takeIf { credential.startsWith("key:") },
                    agentForwarding = agent,
                    recordSessions = record,
                )
                onSave(group.copy(name = name.trim(), color = color, settings = settings), if (topLevel) place else null)
            }) { Text(stringResource(R.string.common_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}
