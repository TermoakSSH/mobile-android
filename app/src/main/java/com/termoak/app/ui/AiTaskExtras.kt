package com.termoak.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.termoak.app.R
import com.termoak.app.data.AiTaskForm
import com.termoak.app.userMessage
import com.termoak.ffi.AccountHandle
import com.termoak.ffi.AiHostRun
import com.termoak.ffi.AiRunbook
import kotlinx.coroutines.launch
import java.util.Locale

/** The hosts of a task with one conversation per host: each one's state, result, time, cost and approvals waiting. */
@Composable
internal fun HostRunsTable(runs: List<AiHostRun>, onOpen: (AiHostRun) -> Unit) {
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Text(
                pluralStringResource(R.plurals.hosts_count, runs.size, runs.size), Modifier.padding(horizontal = 12.dp),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                stringResource(R.string.ai_hosts_hint), Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            runs.forEach { run -> HostRunRow(run) { onOpen(run) } }
        }
    }
}

@Composable
private fun HostRunRow(run: AiHostRun, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(run.label.ifBlank { run.hostId }, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val (text, color) = statusStyle(run.status)
                Pill(text, color)
                if (run.pendingApprovals > 0u) {
                    val n = run.pendingApprovals.toInt()
                    Pill(pluralStringResource(R.plurals.ai_hosts_approvals, n, n), Brand.Amber)
                }
            }
            (run.error ?: run.summary)?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis,
                    color = if (run.error != null) Brand.Red else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val details = listOfNotNull(
                run.durationMs?.let { AiTaskForm.duration(it) },
                run.costMicros.takeIf { it > 0 }?.let { String.format(Locale.ROOT, "$%.2f", it / 1_000_000.0) },
            ).joinToString(" · ")
            if (details.isNotEmpty()) {
                Text(details, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The plan the task follows (approved, or edited and approved). */
@Composable
internal fun ApprovedPlan(plan: String, edited: Boolean) {
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.AutoMirrored.Outlined.List, null, Modifier.size(18.dp))
                Text(stringResource(R.string.ai_plan_approved), style = MaterialTheme.typography.titleSmall)
                if (edited) Pill(stringResource(R.string.ai_plan_edited), Brand.Blue)
            }
            SelectionContainer {
                Text(plan, Modifier.padding(top = 6.dp), fontFamily = FontFamily.Monospace, fontSize = 13.sp)
            }
        }
    }
}

/**
 * "Save as runbook": the commands the task ran as a snippet (tags ai and
 * runbook) in your personal vault, with a name to review first.
 */
@Composable
internal fun RunbookDialog(api: AccountHandle, taskId: String, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    var runbook by remember { mutableStateOf<AiRunbook?>(null) }
    var name by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    LaunchedEffect(taskId) {
        try {
            val r = api.getRunbook(taskId)
            runbook = r
            name = r.name
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            error = e.userMessage(resources, R.string.ai_runbook_failed)
        }
    }
    val r = runbook
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ai_runbook_save)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    r == null && error == null -> CircularProgressIndicator(Modifier.size(24.dp))
                    r != null && r.steps == 0u -> Text(stringResource(R.string.ai_runbook_empty))
                    r != null -> {
                        OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.common_name)) }, singleLine = true)
                        Text(stringResource(R.string.ai_runbook_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = RoundedCornerShape(8.dp)) {
                            SelectionContainer {
                                Text(r.script, Modifier.heightIn(max = 260.dp).padding(10.dp), fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                            }
                        }
                        if (r.variables.isNotEmpty()) {
                            Text(r.variables.joinToString(" ") { "{{$it}}" }, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !saving && (r?.steps ?: 0u) > 0u,
                onClick = {
                    saving = true
                    error = null
                    scope.launch {
                        try {
                            api.saveRunbook(taskId, null, name.trim().ifEmpty { null })
                            onSaved()
                        } catch (e: Exception) {
                            if (e is kotlinx.coroutines.CancellationException) throw e
                            error = e.userMessage(resources, R.string.ai_runbook_failed)
                        } finally {
                            saving = false
                        }
                    }
                },
            ) { Text(stringResource(R.string.common_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}
