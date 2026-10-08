package com.termoak.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.termoak.app.R
import com.termoak.app.TermoakApp
import com.termoak.app.data.useOnly
import com.termoak.app.userMessage
import com.termoak.ffi.AiMemory
import com.termoak.ffi.ItemFilter
import com.termoak.ffi.SshHost
import com.termoak.ffi.TermoakException
import kotlinx.coroutines.launch

/**
 * The AI's memories of an account, as on iOS: facts it keeps between tasks
 * (about you or about a host), saved in its vault and synced. Add, edit
 * and delete. [accountId]: `null` for the AI section's account.
 */
@Composable
fun AiMemoriesScreen(app: TermoakApp, nav: NavHostController, accountId: String?) {
    val resources = LocalResources.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val owner = remember(accountId) { accountId ?: app.accounts.aiAccount()?.id }
    var memories by remember { mutableStateOf<List<AiMemory>>(emptyList()) }
    var hosts by remember { mutableStateOf<List<SshHost>>(emptyList()) }
    // The memory being edited (one with an empty id: a new one).
    var editing by remember { mutableStateOf<AiMemory?>(null) }
    var deleting by remember { mutableStateOf<AiMemory?>(null) }

    fun load() {
        val id = owner ?: return
        val filter = ItemFilter(accountIds = listOf(id), vaultIds = null, includeDevice = false)
        memories = runCatching { app.core.listMemories(filter) }.getOrDefault(emptyList()).sortedByDescending { it.updatedAt }
        hosts = runCatching { app.core.listHosts(filter) }.getOrDefault(emptyList()).sortedBy { it.label.lowercase() }
    }
    LaunchedEffect(owner) { load() }
    LaunchedEffect(Unit) { app.accounts.changes.collect { load() } }

    ScreenScaffold(
        title = stringResource(R.string.ai_memories),
        navigationIcon = {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back)) }
        },
        actions = {
            if (owner != null) {
                IconButton(onClick = { editing = AiMemory(content = "", accountId = owner) }) {
                    Icon(Icons.Outlined.Add, stringResource(R.string.ai_memories_new))
                }
            }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
            if (memories.isEmpty()) {
                item { Muted(stringResource(R.string.ai_memories_empty), Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) }
            }
            items(memories, key = { it.id }) { m ->
                val editable = !m.access.useOnly()
                val host = hosts.firstOrNull { it.id == m.hostId }
                ListItem(
                    modifier = if (editable) Modifier.clickable { editing = m } else Modifier,
                    headlineContent = { Text(m.content, maxLines = 4, overflow = TextOverflow.Ellipsis) },
                    supportingContent = host?.let {
                        {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Dns, null, Modifier.size(14.dp))
                                Text(it.label, Modifier.padding(start = 4.dp), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    },
                    leadingContent = { Icon(Icons.Outlined.Psychology, null, tint = MaterialTheme.colorScheme.primary) },
                    trailingContent = if (editable) {
                        { IconButton(onClick = { deleting = m }) { Icon(Icons.Outlined.Delete, stringResource(R.string.common_delete)) } }
                    } else null,
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
            item { FormHint(stringResource(R.string.ai_memories_footer)) }
        }
    }

    editing?.let { original ->
        var content by remember(original) { mutableStateOf(original.content) }
        var hostId by remember(original) { mutableStateOf(original.hostId) }
        var error by remember(original) { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(stringResource(if (original.id.isEmpty()) R.string.ai_memories_new else R.string.ai_memories_edit)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        content, { content = it }, Modifier.fillMaxWidth().heightIn(min = 120.dp),
                        placeholder = { Text(stringResource(R.string.ai_memories_content_hint)) },
                    )
                    val none = stringResource(R.string.ai_memories_no_host)
                    Picker(
                        stringResource(R.string.ai_memories_host),
                        hosts.firstOrNull { it.id == hostId }?.label ?: none, null,
                        listOf<Pair<String?, String>>(null to none) + hosts.map { it.id to it.label },
                    ) { hostId = it }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = {
                TextButton(enabled = content.isNotBlank(), onClick = {
                    try {
                        app.core.saveMemory(original.copy(content = content.trim(), hostId = hostId))
                        app.accounts.sync(owner)
                        editing = null
                        load()
                    } catch (e: TermoakException) {
                        error = e.userMessage(resources, R.string.error_save_failed)
                    }
                }) { Text(stringResource(R.string.common_save)) }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
    deleting?.let { m ->
        ConfirmDialog(
            stringResource(R.string.ai_memories_delete_title), m.content, stringResource(R.string.common_delete),
            destructive = true, onDismiss = { deleting = null },
        ) {
            try {
                app.core.deleteMemory(m.id, m.accountId)
                app.accounts.sync(owner)
            } catch (e: TermoakException) {
                val text = e.userMessage(resources, R.string.error_save_failed)
                scope.launch { snackbar.showSnackbar(text) }
            }
            load()
        }
    }
}
