package com.termoak.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.termoak.app.R
import com.termoak.app.data.uid
import com.termoak.app.TermoakApp
import com.termoak.app.asString
import com.termoak.app.term.SnippetRuns
import com.termoak.ffi.HostGroup
import com.termoak.ffi.Snippet
import com.termoak.ffi.SshHost
import com.termoak.ffi.renderSnippet
import com.termoak.ffi.snippetVariables

/** Vault snippets to choose one from (to run it on the selected hosts). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnippetPickerSheet(app: TermoakApp, onDismiss: () -> Unit, onPick: (Snippet) -> Unit) {
    val snippets = remember { runCatching { app.core.listSnippets(app.accounts.filter()) }.getOrDefault(emptyList()).sortedBy { it.name.lowercase() } }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(stringResource(R.string.multi_choose_snippet), Modifier.padding(horizontal = 24.dp), style = MaterialTheme.typography.titleLarge)
        if (snippets.isEmpty()) {
            Text(stringResource(R.string.snippets_none_yet), Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LazyColumn(Modifier.padding(bottom = 24.dp)) {
            items(snippets, key = { it.uid }) { sn ->
                ListItem(
                    modifier = Modifier.clickable { onPick(sn) },
                    headlineContent = { Text(sn.name) },
                    supportingContent = {
                        Text(
                            sn.description.ifBlank { sn.script }, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            fontFamily = if (sn.description.isBlank()) FontFamily.Monospace else null,
                        )
                    },
                )
            }
        }
    }
}

/**
 * "Run on several servers": the hosts (or whole groups) or the open
 * terminals to send [snippet] to, pasted or run. A terminal opens for each
 * host that has none; the summary ([SnippetRunSummary]) follows how it goes.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RunSnippetSheet(
    app: TermoakApp,
    snippet: Snippet,
    initialHosts: Set<String> = emptySet(),
    onDismiss: () -> Unit,
    onStarted: () -> Unit,
) {
    val hosts = remember { runCatching { app.core.listHosts(app.accounts.filter()) }.getOrDefault(emptyList()).sortedBy { it.label.lowercase() } }
    val groups = remember { runCatching { app.core.listGroups(app.accounts.filter()) }.getOrDefault(emptyList()).sortedBy { it.name.lowercase() } }
    val open by app.sessions.list.collectAsState()
    var onOpen by remember { mutableStateOf(initialHosts.isEmpty() && hosts.isEmpty() && open.isNotEmpty()) }
    var chosenHosts by remember { mutableStateOf(initialHosts) }
    var chosenTerminals by remember { mutableStateOf(open.map { it.id }.toSet()) }
    var filling by remember { mutableStateOf<Boolean?>(null) }

    fun start(text: String, run: Boolean) {
        if (onOpen) {
            app.snippetRuns.onSessions(snippet.name, text, run, open.map { it.id }.filter { it in chosenTerminals })
        } else {
            app.snippetRuns.onHosts(snippet.name, text, run, hosts.filter { it.uid in chosenHosts })
        }
        onStarted()
    }
    fun go(run: Boolean) {
        if (snippetVariables(snippet.script).isEmpty()) start(snippet.script, run) else filling = run
    }
    val count = if (onOpen) chosenTerminals.count { id -> open.any { it.id == id } } else chosenHosts.size

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp)) {
            Text(stringResource(R.string.multi_run_title, snippet.name), style = MaterialTheme.typography.titleLarge)
            Text(
                snippet.script, Modifier.padding(top = 4.dp), fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3, overflow = TextOverflow.Ellipsis,
            )
            if (open.isNotEmpty()) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    SegmentedButton(!onOpen, { onOpen = false }, SegmentedButtonDefaults.itemShape(0, 2)) {
                        Text(stringResource(R.string.multi_tab_hosts))
                    }
                    SegmentedButton(onOpen, { onOpen = true }, SegmentedButtonDefaults.itemShape(1, 2)) {
                        Text(stringResource(R.string.multi_tab_open, open.size), maxLines = 1)
                    }
                }
            }
        }
        if (!onOpen) {
            if (hosts.isEmpty()) {
                Text(stringResource(R.string.multi_no_hosts), Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                // Whole groups at once.
                FlowRow(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val all = hosts.map { it.uid }.toSet()
                    FilterChip(chosenHosts.containsAll(all), {
                        chosenHosts = if (chosenHosts.containsAll(all)) emptySet() else all
                    }, { Text(stringResource(R.string.multi_all)) })
                    groups.forEach { g ->
                        val ids = groupHosts(g, hosts, groups).map { it.uid }.toSet()
                        if (ids.isNotEmpty()) {
                            FilterChip(chosenHosts.containsAll(ids), {
                                chosenHosts = if (chosenHosts.containsAll(ids)) chosenHosts - ids else chosenHosts + ids
                            }, { Text("${g.name} · ${ids.size}") })
                        }
                    }
                }
                LazyColumn(Modifier.heightIn(max = 340.dp)) {
                    items(hosts, key = { it.uid }) { h ->
                        val checked = h.uid in chosenHosts
                        HostChoice(h, checked) { chosenHosts = if (checked) chosenHosts - h.uid else chosenHosts + h.uid }
                    }
                }
            }
        } else {
            LazyColumn(Modifier.heightIn(max = 380.dp)) {
                items(open, key = { it.id }) { s ->
                    val checked = s.id in chosenTerminals
                    val title by s.title.collectAsState()
                    val state by s.state.collectAsState()
                    ListItem(
                        modifier = Modifier.clickable { chosenTerminals = if (checked) chosenTerminals - s.id else chosenTerminals + s.id },
                        leadingContent = { Checkbox(checked, null) },
                        headlineContent = { Text(title ?: s.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        trailingContent = { StatusDot(stateColor(state)) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }
        }
        HorizontalDivider()
        Text(
            stringResource(if (onOpen) R.string.multi_explain_open else R.string.multi_explain_hosts),
            Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (count == 0) {
                Text(
                    stringResource(R.string.multi_choose_some), Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                )
            }
            OutlinedButton(onClick = { go(false) }, enabled = count > 0) {
                Icon(Icons.Outlined.ContentPaste, null, Modifier.size(18.dp))
                Text(stringResource(R.string.common_paste), Modifier.padding(start = 6.dp))
            }
            Button(onClick = { go(true) }, enabled = count > 0) {
                Icon(Icons.Outlined.PlayArrow, null, Modifier.size(18.dp))
                Text(pluralStringResource(R.plurals.multi_run_n, count, count), Modifier.padding(start = 6.dp))
            }
        }
        Spacer(Modifier.navigationBarsPadding().height(16.dp))
    }
    filling?.let { run ->
        SnippetVariablesDialog(snippet, if (run) stringResource(R.string.snippets_run) else stringResource(R.string.common_paste),
            onDismiss = { filling = null }) { text ->
            filling = null
            start(text, run)
        }
    }
}

@Composable
private fun HostChoice(h: SshHost, checked: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked, null)
        HostTile(h, size = 32.dp)
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(h.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                (h.settings.username?.let { "$it@" } ?: "") + h.address, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Hosts of group [groupId] and of its subgroups. */
private fun groupHosts(group: HostGroup, hosts: List<SshHost>, groups: List<HostGroup>): List<SshHost> =
    hosts.filter { it.groupId == group.id && it.accountId == group.accountId } +
        groups.filter { it.parentId == group.id && it.accountId == group.accountId }.flatMap { groupHosts(it, hosts, groups) }

/**
 * Summary of a snippet sent to several terminals (for the whole app): how
 * each one went, live. A row opens its terminal (to answer a question, or to
 * see the output).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnippetRunSummary(app: TermoakApp, nav: NavHostController) {
    val run by app.snippetRuns.current.collectAsState()
    val r = run ?: return
    val maxPanes = rememberMaxPanes()
    fun open(ids: List<String>) {
        app.snippetRuns.dismiss()
        val live = ids.filter { app.sessions.get(it) != null }
        if (live.isEmpty()) return
        if (live.size >= 2 && maxPanes >= 2) app.sessions.setSplit(live.take(maxPanes))
        app.sessions.select(live.first())
        nav.navigate(Routes.TERMINAL) { launchSingleTop = true }
    }
    ModalBottomSheet(onDismissRequest = { app.snippetRuns.dismiss() }) {
        Column(Modifier.padding(horizontal = 24.dp)) {
            Text(stringResource(R.string.multi_summary_title, r.name), style = MaterialTheme.typography.titleLarge)
            Text(
                if (r.done) pluralStringResource(R.plurals.multi_summary_done, r.targets.size, r.sent, r.targets.size)
                else pluralStringResource(R.plurals.multi_summary_running, r.targets.size, r.sent, r.targets.size),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LazyColumn(Modifier.heightIn(max = 420.dp).padding(top = 8.dp)) {
            items(r.targets, key = { it.sessionId }) { t ->
                ListItem(
                    modifier = Modifier.clickable { open(listOf(t.sessionId)) },
                    leadingContent = { RunStatusIcon(t.status) },
                    headlineContent = { Text(t.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = {
                        Text(
                            t.error?.asString() ?: stringResource(
                                when (t.status) {
                                    SnippetRuns.Status.WAITING -> R.string.multi_status_waiting
                                    SnippetRuns.Status.NEEDS_ANSWER -> R.string.multi_status_needs_answer
                                    SnippetRuns.Status.SENT -> if (r.run) R.string.multi_status_ran else R.string.multi_status_pasted
                                    SnippetRuns.Status.READ_ONLY -> R.string.multi_status_read_only
                                    SnippetRuns.Status.FAILED -> R.string.multi_status_failed
                                },
                            ),
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
            TextButton(onClick = { app.snippetRuns.dismiss() }) { Text(stringResource(R.string.common_close)) }
            Button(onClick = { open(r.targets.map { it.sessionId }) }) {
                Text(pluralStringResource(R.plurals.multi_open_terminals, r.targets.size, r.targets.size))
            }
        }
        Spacer(Modifier.navigationBarsPadding().height(16.dp))
    }
}

@Composable
private fun RunStatusIcon(status: SnippetRuns.Status) {
    when (status) {
        SnippetRuns.Status.WAITING -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        SnippetRuns.Status.NEEDS_ANSWER -> Icon(Icons.AutoMirrored.Outlined.HelpOutline, null, tint = Brand.Amber)
        SnippetRuns.Status.SENT -> Icon(Icons.Outlined.CheckCircle, null, tint = Brand.Green)
        SnippetRuns.Status.READ_ONLY -> Icon(Icons.Outlined.Lock, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        SnippetRuns.Status.FAILED -> Icon(Icons.Outlined.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
    }
}

/** Values of a snippet's `{{variables}}`; [action] is the confirm button ("Paste", "Run"). */
@Composable
fun SnippetVariablesDialog(sn: Snippet, action: String, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    val names = remember(sn) { snippetVariables(sn.script) }
    val values = remember(sn) { names.map { mutableStateOf("") } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(sn.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                names.forEachIndexed { i, n ->
                    OutlinedTextField(values[i].value, { values[i].value = it }, label = { Text(n) }, singleLine = true)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val rendered = runCatching {
                    renderSnippet(sn.script, names.zip(values.map { it.value }).toMap())
                }.getOrDefault(sn.script)
                onDone(rendered)
            }) { Text(action) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}
