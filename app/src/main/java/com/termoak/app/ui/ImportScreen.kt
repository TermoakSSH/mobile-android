package com.termoak.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.termoak.app.R
import com.termoak.app.TermoakApp
import com.termoak.ffi.SshConfigImportOptions
import com.termoak.ffi.SshConfigImportReport
import com.termoak.ffi.TermoakException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Imports hosts from an OpenSSH config (`~/.ssh/config`): pasted or picked
 * from a file, with a preview of what will be created first.
 */
@Composable
fun ImportScreen(app: TermoakApp, onDone: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    var text by rememberSaveable { mutableStateOf("") }
    var group by rememberSaveable { mutableStateOf("") }
    var deviceOnly by rememberSaveable { mutableStateOf(false) }
    var preview by remember { mutableStateOf<SshConfigImportReport?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun options(dryRun: Boolean) = SshConfigImportOptions(dryRun = dryRun, group = group.trim().ifEmpty { null }, deviceOnly = deviceOnly)

    fun run(dryRun: Boolean) {
        busy = true
        scope.launch {
            try {
                val report = withContext(Dispatchers.IO) { app.core.importSshConfig(text, options(dryRun)) }
                if (dryRun) {
                    preview = report
                } else {
                    app.account.sync()
                    snackbar.showSnackbar(
                        resources.getQuantityString(R.plurals.import_done, report.hostsCreated.size, report.hostsCreated.size),
                    )
                    onDone()
                }
            } catch (e: TermoakException) {
                snackbar.showSnackbar(e.message ?: resources.getString(R.string.import_failed))
            } finally {
                busy = false
            }
        }
    }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val read = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
        }.getOrNull()
        if (read == null) {
            scope.launch { snackbar.showSnackbar(resources.getString(R.string.import_read_failed)) }
        } else {
            text = read
            preview = null
        }
    }

    ScreenScaffold(
        title = stringResource(R.string.import_title),
        navigationIcon = {
            IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back)) }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.import_intro), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = { pick.launch(arrayOf("*/*")) }) {
                Icon(Icons.Outlined.FolderOpen, null, Modifier.padding(end = 8.dp))
                Text(stringResource(R.string.import_choose_file))
            }
            OutlinedTextField(
                text, { text = it; preview = null }, Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.import_config)) },
                placeholder = { Text(stringResource(R.string.import_config_placeholder), style = Mono) },
                textStyle = Mono, minLines = 6, maxLines = 14,
            )
            OutlinedTextField(
                group, { group = it; preview = null }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text(stringResource(R.string.import_group)) },
                placeholder = { Text(stringResource(R.string.import_group_placeholder)) },
            )
            FormSwitch(stringResource(R.string.common_device_only), stringResource(R.string.import_device_only_hint), deviceOnly) {
                deviceOnly = it; preview = null
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { run(dryRun = true) }, enabled = text.isNotBlank() && !busy) {
                    Text(stringResource(R.string.import_preview))
                }
                Button(
                    onClick = { run(dryRun = false) },
                    enabled = text.isNotBlank() && !busy && preview?.hostsCreated?.isNotEmpty() != false,
                ) { Text(stringResource(R.string.import_run)) }
            }
            preview?.let { r -> ImportPreview(r) }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ImportPreview(r: SshConfigImportReport) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (r.hostsCreated.isEmpty()) {
            Text(stringResource(R.string.import_nothing), style = MaterialTheme.typography.bodyMedium)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val primary = MaterialTheme.colorScheme.primary
            if (r.hostsCreated.isNotEmpty()) {
                Pill(pluralStringResource(R.plurals.import_new_hosts, r.hostsCreated.size, r.hostsCreated.size), Brand.Green)
            }
            if (r.jumpHostsCreated.isNotEmpty()) {
                Pill(pluralStringResource(R.plurals.import_jump_hosts, r.jumpHostsCreated.size, r.jumpHostsCreated.size), primary)
            }
            if (r.keysImported.isNotEmpty()) {
                Pill(pluralStringResource(R.plurals.import_new_keys, r.keysImported.size, r.keysImported.size), primary)
            }
            val tunnels = r.forwardsCreated.toInt()
            if (tunnels > 0) Pill(pluralStringResource(R.plurals.import_tunnels, tunnels, tunnels), primary)
            if (r.hostsSkipped.isNotEmpty()) {
                Pill(pluralStringResource(R.plurals.import_skipped, r.hostsSkipped.size, r.hostsSkipped.size), Brand.Amber)
            }
            if (r.warnings.isNotEmpty()) {
                Pill(pluralStringResource(R.plurals.import_warnings, r.warnings.size, r.warnings.size), Brand.Amber)
            }
        }
        if (r.hostsCreated.isNotEmpty()) {
            PreviewList(stringResource(R.string.import_section_hosts), r.hostsCreated)
        }
        if (r.hostsSkipped.isNotEmpty()) {
            PreviewList(stringResource(R.string.import_section_skipped), r.hostsSkipped.map { "${it.alias}: ${it.reason}" })
        }
        if (r.warnings.isNotEmpty()) {
            PreviewList(stringResource(R.string.import_section_warnings), r.warnings)
        }
    }
}

@Composable
private fun PreviewList(title: String, lines: List<String>) {
    Column {
        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp, bottom = 2.dp))
        lines.forEach {
            Text("· $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
