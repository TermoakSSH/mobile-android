package com.termoak.app.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.navigation.NavHostController
import com.termoak.app.R
import com.termoak.app.TermoakApp
import com.termoak.app.data.AccessTexts
import com.termoak.app.data.AuditRules
import com.termoak.app.data.E2eeBook
import com.termoak.app.data.shownEmail
import com.termoak.app.userMessage
import com.termoak.ffi.AuditEvent
import com.termoak.ffi.AuditScope
import com.termoak.ffi.AuditSinkInfo
import com.termoak.ffi.HttpMethod
import com.termoak.ffi.TermoakException
import kotlinx.coroutines.launch
import java.io.File

/** An audit entry in words (stateless). */
@Composable
fun AuditRow(e: AuditEvent, who: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(AuditRules.actionText(e.action)?.let { stringResource(it) } ?: e.action, style = MaterialTheme.typography.bodyLarge)
        Text(
            "$who · ${AccessTexts.dateTime(e.createdAt)}", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        val detail = listOfNotNull(e.target?.takeIf { it.isNotBlank() }, AuditRules.detailSummary(e.detailJson).ifEmpty { null }).joinToString(" · ")
        if (detail.isNotEmpty()) {
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** The filter bar (stateless). */
@Composable
fun AuditFilterBar(f: AuditRules.Filters, showActor: Boolean, onChange: (AuditRules.Filters) -> Unit, onSearch: () -> Unit) {
    Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AuditRules.Preset.entries.forEach { p ->
                FilterChip(f.preset == p, { onChange(f.copy(preset = p)) }, { Text(stringResource(p.label)) }, Modifier.testTag("preset-${p.name}"))
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AuditRules.Range.entries.forEach { r ->
                FilterChip(f.range == r, { onChange(f.copy(range = r)) }, { Text(stringResource(r.label)) })
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                f.text, { onChange(f.copy(text = it)) }, Modifier.weight(1f), singleLine = true,
                label = { Text(stringResource(R.string.audit_filter_text)) }, placeholder = { Text(stringResource(R.string.audit_filter_text_placeholder)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            )
            IconButton(onClick = onSearch) { Icon(Icons.Outlined.Search, stringResource(R.string.audit_filter_apply)) }
        }
        if (showActor) {
            OutlinedTextField(
                f.actor, { onChange(f.copy(actor = it)) }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text(stringResource(R.string.audit_filter_actor)) }, placeholder = { Text(stringResource(R.string.audit_filter_actor_placeholder)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, autoCorrectEnabled = false), keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            )
        }
    }
}

/** A sink (stateless): where, its state, test, pause/resume, delete. */
@Composable
fun SinkRow(s: AuditSinkInfo, busy: Boolean, onTest: () -> Unit, onToggle: () -> Unit, onDelete: () -> Unit) {
    val state = AuditRules.sinkState(s)
    ListItem(
        headlineContent = { Text(s.name) },
        supportingContent = {
            Column {
                Text(AuditRules.sinkTarget(s), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    when (state) {
                        AuditRules.SinkState.PAUSED -> stringResource(R.string.audit_sink_state_disabled)
                        AuditRules.SinkState.FAILING -> stringResource(R.string.audit_sink_state_failing, s.failures.toString(), s.lastError.orEmpty())
                        AuditRules.SinkState.DELIVERED -> stringResource(R.string.audit_sink_state_delivered, AccessTexts.dateTime(s.lastSuccessAt ?: 0))
                        AuditRules.SinkState.WAITING -> stringResource(R.string.audit_sink_state_waiting)
                    },
                    color = when (state) {
                        AuditRules.SinkState.FAILING -> MaterialTheme.colorScheme.error
                        AuditRules.SinkState.DELIVERED -> Brand.Green
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        trailingContent = {
            Row {
                IconButton(onClick = onTest, enabled = !busy && s.enabled, modifier = Modifier.testTag("sink-test-${s.id}")) { Icon(Icons.Outlined.Send, stringResource(R.string.audit_sink_test)) }
                IconButton(onClick = onToggle, enabled = !busy) {
                    Icon(if (s.enabled) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, stringResource(if (s.enabled) R.string.audit_sink_state_disabled else R.string.audit_sink_enabled))
                }
                IconButton(onClick = onDelete, enabled = !busy) { Icon(Icons.Outlined.Delete, stringResource(R.string.common_delete)) }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

/** Team retention and sinks (stateless). */
@Composable
fun AuditSettingsContent(
    retention: AuditRules.RetentionView?,
    retentionText: String,
    onRetentionText: (String) -> Unit,
    onSaveRetention: () -> Unit,
    sinks: List<AuditSinkInfo>?,
    busy: Boolean,
    onTest: (AuditSinkInfo) -> Unit,
    onToggle: (AuditSinkInfo) -> Unit,
    onDelete: (AuditSinkInfo) -> Unit,
) {
    Column {
        if (retention != null) {
            FormSection(stringResource(R.string.audit_retention_title)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.audit_retention_hint, retention.defaultDays.toString(), retention.maxDays.toString()),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    val invalid = AuditRules.parseRetention(retentionText, retention.maxDays) is AuditRules.Retention.Invalid
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            retentionText, { onRetentionText(it.filter(Char::isDigit)) }, Modifier.weight(1f).testTag("retention"), singleLine = true,
                            label = { Text(stringResource(R.string.audit_retention_days)) },
                            placeholder = { Text(stringResource(R.string.audit_retention_default, retention.defaultDays.toString())) },
                            isError = invalid,
                            supportingText = if (invalid) ({ Text(stringResource(R.string.audit_retention_invalid, retention.maxDays.toString())) }) else null,
                            keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        )
                        TextButton(onClick = onSaveRetention, enabled = !busy && !invalid) { Text(stringResource(R.string.common_save)) }
                    }
                }
            }
        }
        SectionLabel(stringResource(R.string.audit_sinks_title))
        FormHint(stringResource(R.string.audit_sinks_hint))
        when {
            sinks == null -> CircularProgressIndicator(Modifier.padding(20.dp).size(24.dp), strokeWidth = 2.dp)
            sinks.isEmpty() -> FormNote(stringResource(R.string.audit_sinks_none))
            else -> sinks.forEach { s -> SinkRow(s, busy, { onTest(s) }, { onToggle(s) }, { onDelete(s) }) }
        }
        FormHint(stringResource(R.string.audit_sinks_add_elsewhere))
    }
}

/** Hands an exported file to another app (share sheet). */
internal fun shareFile(context: Context, file: File, mime: String, title: String) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri)
        .apply { clipData = ClipData.newRawUri(file.name, uri) }
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching { context.startActivity(Intent.createChooser(send, title)) }
}

/** The audit scope of a route (`me`, `team`, `vault`). */
private fun scopeOf(s: String): AuditScope = when (s) {
    "team" -> AuditScope.TEAM
    "vault" -> AuditScope.VAULT
    "server" -> AuditScope.SERVER
    else -> AuditScope.ME
}

/**
 * The extended audit (0.7 phase 5c): your activity (Settings → Security),
 * a team's (Teams → a team → Audit, owners and admins) or a vault's
 * (Admins): presets, time range, person, text, load more, export as
 * CSV/JSON/NDJSON shared to another app; for teams also retention and the
 * SIEM sinks (state, test, pause, delete; adding them stays on the desktop
 * and the web).
 */
@Composable
fun AuditScreen(app: TermoakApp, nav: NavHostController, accountId: String, scopeName: String, id: String?, name: String?) {
    val res = LocalResources.current
    val context = LocalContext.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val handle = remember(accountId) { app.accounts.handle(accountId) }
    val auditScope = scopeOf(scopeName)
    val features = E2eeBook.audit(accountId)
    val team = auditScope == AuditScope.TEAM
    var tab by remember { mutableStateOf(0) }
    var filters by remember { mutableStateOf(AuditRules.Filters()) }
    var entries by remember { mutableStateOf<List<AuditEvent>>(emptyList()) }
    var more by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var names by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var exportMenu by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    var retention by remember { mutableStateOf<AuditRules.RetentionView?>(null) }
    var retentionText by remember { mutableStateOf("") }
    var sinks by remember { mutableStateOf<List<AuditSinkInfo>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<AuditSinkInfo?>(null) }
    val scopeId = if (auditScope == AuditScope.ME) null else id

    suspend fun load(next: Boolean) {
        val h = handle ?: return
        loading = true
        try {
            val before = if (next) entries.lastOrNull()?.id else null
            val page = h.auditSearch(auditScope, filters.query(System.currentTimeMillis(), before), scopeId)
            entries = if (next) entries + page else page
            more = AuditRules.nextBefore(entries, page.size) != null
            error = null
        } catch (e: TermoakException) {
            error = e.userMessage(res, R.string.vault_activity_failed)
        } finally {
            loading = false
        }
    }
    suspend fun loadSettings() {
        val h = handle ?: return
        val t = id ?: return
        retention = runCatching { AuditRules.retentionView(h.apiRequest(HttpMethod.GET, "/api/v1/teams/$t/audit/settings", null)) }.getOrNull()
        retentionText = retention?.days?.toString().orEmpty()
        sinks = runCatching { h.auditSinks(AuditScope.TEAM, t) }.getOrDefault(emptyList())
    }
    LaunchedEffect(accountId, scopeName, id) {
        val h = handle
        if (h != null) {
            names = runCatching {
                when (auditScope) {
                    AuditScope.TEAM -> h.listTeamMembers(id!!).associate { it.userId to shownEmail(it.email) }
                    AuditScope.VAULT -> h.vaultMembers(id!!).mapNotNull { m -> m.userId?.let { it to (m.email?.let(::shownEmail) ?: m.name) } }.toMap()
                    else -> app.accounts.account(accountId)?.let { a -> a.userId?.let { u -> mapOf(u to res.getString(R.string.vault_e2ee_you, shownEmail(a.email))) } }.orEmpty()
                }
            }.getOrDefault(emptyMap())
        }
        if (team) loadSettings()
    }
    // The first page, and again when a preset or a range is chosen (they apply at once).
    LaunchedEffect(accountId, scopeName, id, filters.preset, filters.range) { load(next = false) }
    fun export(format: String) {
        val h = handle ?: return
        exporting = true
        scope.launch {
            try {
                val dir = File(context.cacheDir, "audit").apply { mkdirs() }
                dir.listFiles()?.forEach { it.delete() }
                val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT).format(java.util.Date())
                val file = File(dir, AuditRules.exportName(name ?: scopeName, format, date))
                h.auditExport(auditScope, filters.exportQuery(System.currentTimeMillis()), format, file.absolutePath, scopeId)
                shareFile(context, file, AuditRules.mime(format), file.name)
            } catch (e: TermoakException) {
                snackbar.showSnackbar(e.userMessage(res, R.string.vault_activity_failed))
            } finally {
                exporting = false
            }
        }
    }
    fun sinkAction(f: suspend () -> String?) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                f()?.let { snackbar.showSnackbar(it) }
                loadSettings()
            } catch (e: TermoakException) {
                snackbar.showSnackbar(e.userMessage(res, R.string.vault_save_failed))
            } finally {
                busy = false
            }
        }
    }
    val title = stringResource(
        when (auditScope) {
            AuditScope.TEAM -> R.string.audit_team_title
            AuditScope.VAULT -> R.string.vault_activity
            else -> R.string.audit_account_title
        },
    )
    ScreenScaffold(
        title = title,
        subtitle = name,
        navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back)) } },
        actions = {
            if (features == null || features.exports.isNotEmpty()) {
                Box {
                    IconButton(onClick = { exportMenu = true }, enabled = !exporting, modifier = Modifier.testTag("audit-export")) {
                        if (exporting) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Outlined.FileDownload, stringResource(R.string.audit_export))
                    }
                    DropdownMenu(exportMenu, { exportMenu = false }) {
                        (features?.exports ?: AuditRules.FORMATS).forEach { f ->
                            DropdownMenuItem({ Text(f.uppercase()) }, { exportMenu = false; export(f) })
                        }
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (team) {
                TabRow(tab) {
                    Tab(tab == 0, { tab = 0 }, text = { Text(stringResource(R.string.audit_team_title)) })
                    Tab(tab == 1, { tab = 1 }, text = { Text(stringResource(R.string.audit_sinks_title)) }, modifier = Modifier.testTag("audit-settings-tab"))
                }
            }
            if (team && tab == 1) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    AuditSettingsContent(
                        retention, retentionText, { retentionText = it },
                        onSaveRetention = {
                            val max = retention?.maxDays ?: return@AuditSettingsContent
                            val r = AuditRules.parseRetention(retentionText, max) as? AuditRules.Retention.Ok ?: return@AuditSettingsContent
                            sinkAction {
                                handle?.setTeamAuditRetention(id!!, r.days)
                                res.getString(R.string.audit_retention_saved)
                            }
                        },
                        sinks, busy,
                        onTest = { s ->
                            sinkAction {
                                val t = handle?.testAuditSink(AuditScope.TEAM, s.id, id) ?: return@sinkAction null
                                if (t.ok) res.getString(R.string.audit_sink_test_ok) else res.getString(R.string.audit_sink_test_failed, t.error.orEmpty())
                            }
                        },
                        onToggle = { s -> sinkAction { handle?.updateAuditSink(AuditScope.TEAM, s.id, id, enabled = !s.enabled); null } },
                        onDelete = { deleting = it },
                    )
                }
                return@Column
            }
            LazyColumn(Modifier.fillMaxSize().testTag("audit-list")) {
                item {
                    AuditFilterBar(filters, showActor = auditScope != AuditScope.ME, onChange = { filters = it }) { scope.launch { load(next = false) } }
                    HorizontalDivider(Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                }
                error?.let { item { FormNote(it) } }
                if (error == null && entries.isEmpty() && !loading) item { FormNote(stringResource(R.string.audit_empty)) }
                items(entries, key = { it.id }) { e -> AuditRow(e, AuditRules.actor(e.actor, names)) }
                item {
                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center) {
                        when {
                            loading -> CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                            more -> TextButton(onClick = { scope.launch { load(next = true) } }) { Text(stringResource(R.string.audit_load_more)) }
                            entries.isNotEmpty() -> Text(
                                androidx.compose.ui.res.pluralStringResource(R.plurals.audit_count, entries.size, entries.size),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
    deleting?.let { s ->
        ConfirmDialog(
            stringResource(R.string.audit_sink_delete_title, s.name), stringResource(R.string.audit_sink_delete_message),
            stringResource(R.string.common_delete), destructive = true, onDismiss = { deleting = null },
        ) { sinkAction { handle?.deleteAuditSink(AuditScope.TEAM, s.id, id); null } }
    }
}
