package com.termoak.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.termoak.app.R
import com.termoak.app.TermoakApp
import com.termoak.app.data.ImportPlan
import com.termoak.app.userMessage
import com.termoak.ffi.CsvColumn
import com.termoak.ffi.CsvField
import com.termoak.ffi.CsvMapping
import com.termoak.ffi.DuplicatePolicy
import com.termoak.ffi.HostGroup
import com.termoak.ffi.ImportDuplicate
import com.termoak.ffi.ImportFormat
import com.termoak.ffi.ImportHostPreview
import com.termoak.ffi.ImportOptions
import com.termoak.ffi.ImportPreview
import com.termoak.ffi.ImportSummary
import com.termoak.ffi.ImportWarningInfo
import com.termoak.ffi.ItemFilter
import com.termoak.ffi.SshConfigImportOptions
import com.termoak.ffi.SshConfigImportReport
import com.termoak.ffi.detectImportFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A group of the target to import into: none (its top level), one of its groups, or a new one with a name. */
private sealed class GroupChoice {
    data object None : GroupChoice()
    data class Existing(val id: String) : GroupChoice()
    data object New : GroupChoice()
}

/** The app a file comes from (names aren't translated). */
private fun ImportFormat.title(): String = when (this) {
    ImportFormat.AUTO -> "?"
    ImportFormat.TERMOAK_JSON -> "Termoak JSON"
    ImportFormat.CSV -> "CSV"
    ImportFormat.SSH_CONFIG -> "OpenSSH (ssh_config)"
    ImportFormat.TERMIUS -> "Termius"
    ImportFormat.PUTTY -> "PuTTY"
    ImportFormat.MOBA_XTERM -> "MobaXterm"
    ImportFormat.SECURE_CRT -> "SecureCRT"
    ImportFormat.ZOC -> "ZOC"
}

private val CsvField.key: String get() = name.lowercase()

@Composable
private fun CsvField.title(): String = stringResource(
    when (this) {
        CsvField.LABEL -> R.string.import_field_label
        CsvField.ADDRESS -> R.string.import_field_address
        CsvField.PORT -> R.string.import_field_port
        CsvField.USER -> R.string.import_field_user
        CsvField.GROUP -> R.string.import_field_group
        CsvField.TAGS -> R.string.import_field_tags
        CsvField.NOTES -> R.string.import_field_notes
        CsvField.PASSWORD -> R.string.import_field_password
        CsvField.PROTOCOL -> R.string.import_field_protocol
    },
)

/** Groups of a place with their paths ("Prod / Web"), sorted. */
private fun groupPaths(list: List<HostGroup>): List<Pair<String, String>> {
    val byId = list.associateBy { it.id }
    fun path(g: HostGroup, depth: Int = 0): String {
        val parent = g.parentId?.let { byId[it] }
        return if (parent == null || depth > 16) g.name else path(parent, depth + 1) + " / " + g.name
    }
    return list.map { it.id to path(it) }.sortedBy { it.second.lowercase() }
}

/**
 * Import hosts, like the desktop's import dialog and iOS: a file (or pasted
 * text) from Termoak (JSON), a CSV, Termius, PuTTY (.reg), MobaXterm,
 * SecureCRT, ZOC or an OpenSSH config, the format detected by the engine.
 * The preview lists each host with what will happen to it (new, already
 * there, repeated), a CSV's columns can be chosen, a Termoak export's
 * secrets opened with its passphrase, and everything goes into This device
 * or a vault, under a group if wanted.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ImportScreen(app: TermoakApp, onDone: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    var pasted by remember { mutableStateOf("") }
    var data by remember { mutableStateOf<ByteArray?>(null) }
    var fileName by remember { mutableStateOf("") }
    var format by remember { mutableStateOf(ImportFormat.AUTO) }
    var preview by remember { mutableStateOf<ImportPreview?>(null) }
    var hosts by remember { mutableStateOf<List<ImportHostPreview>>(emptyList()) }
    var warnings by remember { mutableStateOf<List<ImportWarningInfo>>(emptyList()) }
    var mapping by remember { mutableStateOf<CsvMapping?>(null) }
    var passphrase by remember { mutableStateOf<String?>(null) }
    var unlocked by remember { mutableStateOf(false) }
    var sshReport by remember { mutableStateOf<SshConfigImportReport?>(null) }
    var place by remember { mutableStateOf(app.accounts.defaultPlace()) }
    var group by remember { mutableStateOf<GroupChoice>(GroupChoice.None) }
    var newGroup by remember { mutableStateOf("") }
    var policy by remember { mutableStateOf(ImportPlan.Policy.SKIP) }
    var excluded by remember { mutableStateOf(setOf<UInt>()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var summary by remember { mutableStateOf<ImportSummary?>(null) }
    var sshDone by remember { mutableStateOf<SshConfigImportReport?>(null) }
    val ssh = format == ImportFormat.SSH_CONFIG

    val groups = remember(place) {
        val filter = ItemFilter(accountIds = listOfNotNull(place.account), vaultIds = place.vault?.let { listOf(it) }, includeDevice = place.device)
        groupPaths(
            runCatching { app.core.listGroups(filter) }.getOrDefault(emptyList())
                .filter { it.accountId == place.account && (place.device || place.vault == null || it.vaultId == place.vault) },
        )
    }
    val groupName = when (val g = group) {
        GroupChoice.None -> null
        is GroupChoice.Existing -> groups.firstOrNull { it.first == g.id }?.second
        GroupChoice.New -> newGroup.trim().ifEmpty { null }
    }
    fun sshOptions(dryRun: Boolean) = SshConfigImportOptions(
        dryRun = dryRun, group = groupName, deviceOnly = place.device && app.accounts.list.value.isNotEmpty(),
        accountId = place.account, vaultId = place.vault,
    )
    fun show(p: ImportPreview) {
        preview = p
        hosts = p.hosts()
        warnings = p.warnings()
        mapping = p.csvMapping()
        excluded = excluded.filter { i -> hosts.any { it.index == i } }.toSet()
    }

    /** Reads the preview again for the target, keeping the CSV columns chosen and the secrets opened. */
    suspend fun reread() {
        val bytes = data ?: return
        error = null
        if (ssh) {
            sshReport = runCatching { withContext(Dispatchers.IO) { app.core.importSshConfig(bytes.decodeToString(), sshOptions(true)) } }
                .onFailure { error = it.userMessage(resources, R.string.import_failed) }.getOrNull()
            return
        }
        busy = true
        try {
            var p = app.core.previewImport(bytes, fileName, format, place.account, place.vault, place.device)
            val m = mapping
            if (m != null && p.csvMapping() != null) p = p.withMapping(m)
            val pass = passphrase
            if (pass != null && p.needsPassphrase()) withContext(Dispatchers.Default) { p.unlock(pass) }?.let { p = it }
            show(p)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            preview = null
            hosts = emptyList()
            error = e.userMessage(resources, R.string.import_read_failed)
        } finally {
            busy = false
        }
    }

    fun load(bytes: ByteArray, name: String) {
        data = bytes
        fileName = name
        format = runCatching { detectImportFormat(bytes, name) }.getOrDefault(ImportFormat.AUTO)
        passphrase = null
        unlocked = false
        excluded = emptySet()
        mapping = null
        preview = null
        sshReport = null
        scope.launch { reread() }
    }
    LaunchedEffect(place, groupName) { if (data != null) reread() }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val name = runCatching {
            context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/') ?: "import"
        val bytes = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
        if (bytes == null) error = resources.getString(R.string.import_read_failed) else load(bytes, name)
    }

    fun dupOf(h: ImportHostPreview): ImportPlan.Dup = when (val d = h.duplicate) {
        null -> ImportPlan.Dup.None
        is ImportDuplicate.Existing -> ImportPlan.Dup.Existing(d.label)
        is ImportDuplicate.InFile -> ImportPlan.Dup.InFile
    }
    val statuses = hosts.map { ImportPlan.status(dupOf(it), policy, it.index !in excluded) }
    val count = if (ssh) sshReport?.hostsCreated?.size ?: 0 else ImportPlan.importCount(statuses)
    val needsAddress = mapping?.let { m -> m.columns.none { it.field == CsvField.ADDRESS } } == true

    fun run() {
        busy = true
        error = null
        scope.launch {
            try {
                if (ssh) {
                    val bytes = data ?: return@launch
                    sshDone = withContext(Dispatchers.IO) { app.core.importSshConfig(bytes.decodeToString(), sshOptions(false)) }
                } else {
                    val p = preview ?: return@launch
                    val options = ImportOptions(
                        accountId = place.account, vaultId = place.vault, deviceOnly = place.device && app.accounts.list.value.isNotEmpty(),
                        groupId = (group as? GroupChoice.Existing)?.id, groupName = if (group == GroupChoice.New) groupName else null,
                        duplicatePolicy = when (policy) {
                            ImportPlan.Policy.SKIP -> DuplicatePolicy.SKIP
                            ImportPlan.Policy.UPDATE -> DuplicatePolicy.UPDATE
                            ImportPlan.Policy.COPY -> DuplicatePolicy.COPY
                        },
                        excluded = excluded.sorted(),
                    )
                    summary = app.core.applyImport(p, options)
                }
                app.accounts.itemsChangedHere()
                place.account?.let { app.accounts.sync(it) }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                error = e.userMessage(resources, R.string.import_failed)
            } finally {
                busy = false
            }
        }
    }

    val finished = summary != null || sshDone != null
    ScreenScaffold(
        title = when {
            finished -> stringResource(R.string.import_done_title)
            data != null -> stringResource(R.string.import_from, format.title())
            else -> stringResource(R.string.import_hosts_title)
        },
        navigationIcon = {
            IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back)) }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when {
                finished -> {
                    ImportDone(summary, sshDone)
                    Button(onClick = onDone, Modifier.fillMaxWidth()) { Text(stringResource(R.string.common_done)) }
                }
                data == null -> {
                    // ----- Choosing what to import -----
                    OutlinedButton(onClick = { app.appLock.expectReturn(); pick.launch(arrayOf("*/*")) }) {
                        Icon(Icons.Outlined.FolderOpen, null, Modifier.padding(end = 8.dp))
                        Text(stringResource(R.string.import_choose_file))
                    }
                    Text(stringResource(R.string.import_formats), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.import_paste), style = MaterialTheme.typography.titleSmall)
                    OutlinedTextField(
                        pasted, { pasted = it }, Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(R.string.import_config_placeholder), style = Mono) },
                        textStyle = Mono, minLines = 6, maxLines = 14,
                    )
                    Text(stringResource(R.string.import_paste_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = { load(pasted.toByteArray(), "pasted.txt") }, enabled = pasted.isNotBlank()) {
                        Text(stringResource(R.string.import_next))
                    }
                }
                else -> {
                    // ----- The file -----
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Description, null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Text(fileName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(format.title(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { data = null; preview = null; hosts = emptyList(); sshReport = null }) {
                            Text(stringResource(R.string.import_choose_other))
                        }
                    }
                    // ----- Where it goes -----
                    Text(stringResource(R.string.import_target), style = MaterialTheme.typography.titleSmall)
                    PlacePicker(app, place) { place = it; group = GroupChoice.None }
                    val noneLabel = stringResource(R.string.import_group_none)
                    val newLabel = stringResource(R.string.import_group_new)
                    Picker(
                        stringResource(R.string.import_group_label),
                        when (val g = group) {
                            GroupChoice.None -> noneLabel
                            is GroupChoice.Existing -> groups.firstOrNull { it.first == g.id }?.second ?: noneLabel
                            GroupChoice.New -> newLabel
                        },
                        null,
                        listOf<Pair<GroupChoice, String>>(GroupChoice.None to noneLabel) +
                            groups.map { GroupChoice.Existing(it.first) to it.second } + (GroupChoice.New to newLabel),
                    ) { group = it }
                    if (group == GroupChoice.New) {
                        OutlinedTextField(
                            newGroup, { newGroup = it }, Modifier.fillMaxWidth(), singleLine = true,
                            label = { Text(stringResource(R.string.import_group_name)) },
                        )
                    }
                    Text(stringResource(R.string.import_target_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (ssh) {
                        sshReport?.let { SshConfigPreview(it) }
                    } else {
                        val p = preview
                        // ----- A Termoak export's secrets -----
                        if (p != null && !unlocked && p.needsPassphrase()) {
                            var text by remember { mutableStateOf("") }
                            var wrong by remember { mutableStateOf(false) }
                            Text(stringResource(R.string.import_secrets_locked), style = MaterialTheme.typography.bodySmall)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    text, { text = it; wrong = false }, Modifier.weight(1f), singleLine = true,
                                    label = { Text(stringResource(R.string.import_passphrase)) }, isError = wrong,
                                    supportingText = if (wrong) ({ Text(stringResource(R.string.import_wrong_passphrase)) }) else null,
                                    visualTransformation = PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                )
                                TextButton(enabled = text.isNotEmpty() && !busy, onClick = {
                                    scope.launch {
                                        busy = true
                                        val open = runCatching { withContext(Dispatchers.Default) { p.unlock(text) } }.getOrNull()
                                        busy = false
                                        if (open == null) wrong = true else {
                                            passphrase = text
                                            unlocked = true
                                            show(open)
                                        }
                                    }
                                }) { Text(stringResource(R.string.import_unlock)) }
                            }
                        }
                        if (unlocked) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.LockOpen, null, Modifier.size(16.dp), tint = Brand.Green)
                                Text(stringResource(R.string.import_secrets_unlocked), Modifier.padding(start = 6.dp), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        // ----- A CSV's columns -----
                        val m = mapping
                        if (p != null && m != null) CsvColumns(p, m) { next -> show(p.withMapping(next)) }
                        if (needsAddress) Text(stringResource(R.string.import_needs_address), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        // ----- Duplicates -----
                        if (hosts.any { it.duplicate != null }) {
                            Text(stringResource(R.string.import_duplicates), style = MaterialTheme.typography.titleSmall)
                            val options = listOf(
                                ImportPlan.Policy.SKIP to R.string.import_dup_skip,
                                ImportPlan.Policy.UPDATE to R.string.import_dup_update,
                                ImportPlan.Policy.COPY to R.string.import_dup_copy,
                            )
                            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                                options.forEachIndexed { i, (pol, label) ->
                                    SegmentedButton(policy == pol, { policy = pol }, SegmentedButtonDefaults.itemShape(i, options.size)) { Text(stringResource(label)) }
                                }
                            }
                        }
                        // ----- The hosts of the file -----
                        if (hosts.isNotEmpty()) {
                            Text(stringResource(R.string.import_file_hosts), style = MaterialTheme.typography.titleSmall)
                            hosts.forEachIndexed { i, h ->
                                val st = statuses[i]
                                Row(
                                    Modifier.fillMaxWidth().clickable { excluded = if (h.index in excluded) excluded - h.index else excluded + h.index },
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Checkbox(h.index !in excluded, { on -> excluded = if (on) excluded - h.index else excluded + h.index })
                                    Column(Modifier.weight(1f)) {
                                        Text(h.label.ifBlank { h.address }, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            listOfNotNull(
                                                (h.username?.let { "$it@" } ?: "") + h.address + (h.port?.let { ":$it" } ?: ""),
                                                h.group,
                                                statusText(st),
                                            ).joinToString(" · "),
                                            style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                            color = if (st.imports) MaterialTheme.colorScheme.onSurfaceVariant else Brand.Amber,
                                        )
                                    }
                                }
                            }
                        } else if (!busy && p != null) {
                            Text(stringResource(R.string.import_nothing))
                        }
                        if (warnings.isNotEmpty()) {
                            Text(stringResource(R.string.import_section_warnings), style = MaterialTheme.typography.titleSmall)
                            warnings.forEach { w ->
                                Text("· " + warningText(w), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Button(
                        onClick = { run() }, Modifier.fillMaxWidth(),
                        enabled = count > 0 && !busy && !needsAddress && (group != GroupChoice.New || groupName != null),
                    ) { Text(pluralStringResource(R.plurals.import_run_count, count, count)) }
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun statusText(s: ImportPlan.Status): String = when (s) {
    ImportPlan.Status.New -> stringResource(R.string.import_status_new)
    ImportPlan.Status.Unchecked -> stringResource(R.string.import_status_unchecked)
    is ImportPlan.Status.ExistingSkipped -> stringResource(R.string.import_status_dup_skip, s.name)
    is ImportPlan.Status.Updates -> stringResource(R.string.import_status_dup_update, s.name)
    is ImportPlan.Status.CopyOf -> stringResource(R.string.import_status_dup_copy, s.name)
    ImportPlan.Status.RepeatSkipped -> stringResource(R.string.import_status_repeat)
    ImportPlan.Status.RepeatCopy -> stringResource(R.string.import_status_repeat_copy)
}

/** A warning of the engine in the app's language, by its stable code (the English text when the code is new). */
@Composable
private fun warningText(w: ImportWarningInfo): String {
    val p = w.params
    fun v(k: String) = p[k].orEmpty()
    return when (w.code) {
        "not_ssh" -> stringResource(R.string.import_warn_not_ssh, v("name"), v("protocol"))
        "no_address" -> stringResource(R.string.import_warn_no_address, v("name"))
        "no_address_line" -> stringResource(R.string.import_warn_no_address_line, v("line"))
        "bad_port" -> stringResource(R.string.import_warn_bad_port, v("line"), v("port"))
        "proxy_unsupported" -> stringResource(R.string.import_warn_proxy_unsupported, v("name"))
        "key_file" -> stringResource(R.string.import_warn_key_file, v("name"), v("path"), v("error"))
        "key_without_private" -> stringResource(R.string.import_warn_key_without_private, v("name"))
        else -> w.message
    }
}

/** A CSV's columns: whether the first row has the names, and which field each column feeds (with an example). */
@Composable
private fun CsvColumns(p: ImportPreview, m: CsvMapping, onChange: (CsvMapping) -> Unit) {
    val names = remember(p) { runCatching { p.csvColumns() }.getOrDefault(emptyList()) }
    val sample = remember(p) { runCatching { p.csvSample(5u) }.getOrDefault(emptyList()) }
    val pairs = m.columns.map { it.field.key to it.column.toInt() }
    fun mappingOf(next: List<Pair<String, Int>>) = CsvMapping(
        m.hasHeader, next.mapNotNull { (f, c) -> CsvField.entries.firstOrNull { it.key == f }?.let { CsvColumn(it, c.toUInt()) } },
    )
    Text(stringResource(R.string.import_columns), style = MaterialTheme.typography.titleSmall)
    FormSwitch(stringResource(R.string.import_has_header), "", m.hasHeader) { onChange(m.copy(hasHeader = it)) }
    val none = stringResource(R.string.import_column_none)
    val fieldNames = CsvField.entries.associateWith { it.title() }
    names.forEachIndexed { i, name ->
        val field = ImportPlan.fieldOfColumn(i, pairs)?.let { k -> CsvField.entries.firstOrNull { it.key == k } }
        val example = ImportPlan.example(i, sample, m.hasHeader)
        Picker(
            listOfNotNull(name.ifBlank { "#${i + 1}" }, example).joinToString(" · "),
            field?.let { fieldNames.getValue(it) } ?: none, null,
            listOf<Pair<CsvField?, String>>(null to none) + CsvField.entries.map { it to fieldNames.getValue(it) },
        ) { f -> onChange(mappingOf(ImportPlan.assign(f?.key, i, pairs))) }
    }
}

/** What an OpenSSH config brings (its dry run). */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun SshConfigPreview(r: SshConfigImportReport) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (r.hostsCreated.isEmpty()) Text(stringResource(R.string.import_nothing), style = MaterialTheme.typography.bodyMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val primary = MaterialTheme.colorScheme.primary
            if (r.hostsCreated.isNotEmpty()) Pill(pluralStringResource(R.plurals.import_new_hosts, r.hostsCreated.size, r.hostsCreated.size), Brand.Green)
            if (r.jumpHostsCreated.isNotEmpty()) Pill(pluralStringResource(R.plurals.import_jump_hosts, r.jumpHostsCreated.size, r.jumpHostsCreated.size), primary)
            if (r.keysImported.isNotEmpty()) Pill(pluralStringResource(R.plurals.import_new_keys, r.keysImported.size, r.keysImported.size), primary)
            val tunnels = r.forwardsCreated.toInt()
            if (tunnels > 0) Pill(pluralStringResource(R.plurals.import_tunnels, tunnels, tunnels), primary)
            if (r.hostsSkipped.isNotEmpty()) Pill(pluralStringResource(R.plurals.import_skipped, r.hostsSkipped.size, r.hostsSkipped.size), Brand.Amber)
            if (r.warnings.isNotEmpty()) Pill(pluralStringResource(R.plurals.import_warnings, r.warnings.size, r.warnings.size), Brand.Amber)
        }
        if (r.hostsCreated.isNotEmpty()) PreviewList(stringResource(R.string.import_section_hosts), r.hostsCreated)
        if (r.hostsSkipped.isNotEmpty()) PreviewList(stringResource(R.string.import_section_skipped), r.hostsSkipped.map { "${it.alias}: ${it.reason}" })
        if (r.warnings.isNotEmpty()) PreviewList(stringResource(R.string.import_section_warnings), r.warnings)
    }
}

@Composable
private fun PreviewList(title: String, lines: List<String>) {
    Column {
        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp, bottom = 2.dp))
        lines.forEach { Text("· $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

/** What the import did. */
@Composable
private fun ImportDone(summary: ImportSummary?, ssh: SshConfigImportReport?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.CheckCircle, null, tint = Brand.Green)
        Text(stringResource(R.string.import_done_title), Modifier.padding(start = 8.dp), style = MaterialTheme.typography.titleMedium)
    }
    if (ssh != null) {
        Text(pluralStringResource(R.plurals.import_done, ssh.hostsCreated.size, ssh.hostsCreated.size))
        return
    }
    val s = summary ?: return
    val lines = listOfNotNull(
        s.created.toInt().takeIf { it > 0 }?.let { pluralStringResource(R.plurals.import_done_created, it, it) },
        s.updated.toInt().takeIf { it > 0 }?.let { pluralStringResource(R.plurals.import_done_updated, it, it) },
        s.skipped.toInt().takeIf { it > 0 }?.let { pluralStringResource(R.plurals.import_done_skipped, it, it) },
        s.groups.toInt().takeIf { it > 0 }?.let { pluralStringResource(R.plurals.import_done_groups, it, it) },
        s.keys.toInt().takeIf { it > 0 }?.let { pluralStringResource(R.plurals.import_done_keys, it, it) },
        s.keysReused.toInt().takeIf { it > 0 }?.let { pluralStringResource(R.plurals.import_done_keys_reused, it, it) },
        s.identities.toInt().takeIf { it > 0 }?.let { pluralStringResource(R.plurals.import_done_identities, it, it) },
        s.snippets.toInt().takeIf { it > 0 }?.let { pluralStringResource(R.plurals.import_done_snippets, it, it) },
    )
    lines.forEach { Text("· $it") }
    if (s.warnings.isNotEmpty()) {
        Text(stringResource(R.string.import_section_warnings), style = MaterialTheme.typography.titleSmall)
        s.warnings.forEach { Text("· " + warningText(it), style = MaterialTheme.typography.bodySmall) }
    }
}
