package com.termoak.app.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.termoak.app.BuildConfig
import com.termoak.app.R
import com.termoak.app.TermoakApp
import com.termoak.app.data.ImportPlan
import com.termoak.app.userMessage
import com.termoak.ffi.ExportFormat
import com.termoak.ffi.ExportResult
import com.termoak.ffi.ExportScope
import com.termoak.ffi.ItemFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Export hosts, like the desktop: from This device or a vault (all its
 * groups or one), as Termoak JSON (hosts, groups, tags, identities, keys and
 * snippets; passwords and private keys only when asked, encrypted with a
 * passphrase of 8 characters or more) or CSV (for spreadsheets, never with
 * secrets), then shared or saved as a file.
 */
@Composable
fun ExportScreen(app: TermoakApp, onDone: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    var place by remember { mutableStateOf(app.accounts.defaultPlace()) }
    var groupId by remember { mutableStateOf<String?>(null) }
    var format by remember { mutableStateOf(ExportFormat.TERMOAK_JSON) }
    var secrets by remember { mutableStateOf(false) }
    var pass1 by remember { mutableStateOf("") }
    var pass2 by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<Pair<ExportResult, File>?>(null) }
    val groups = remember(place) {
        val filter = ItemFilter(accountIds = listOfNotNull(place.account), vaultIds = place.vault?.let { listOf(it) }, includeDevice = place.device)
        val list = runCatching { app.core.listGroups(filter) }.getOrDefault(emptyList())
            .filter { it.accountId == place.account && (place.device || place.vault == null || it.vaultId == place.vault) }
        val byId = list.associateBy { it.id }
        fun path(g: com.termoak.ffi.HostGroup, d: Int = 0): String =
            g.parentId?.let { byId[it] }?.takeIf { d < 16 }?.let { path(it, d + 1) + " / " + g.name } ?: g.name
        list.map { it.id to path(it) }.sortedBy { it.second.lowercase() }
    }
    val withSecrets = secrets && format == ExportFormat.TERMOAK_JSON
    val problem = if (withSecrets) ImportPlan.passphraseProblem(pass1, pass2) else null
    val saveAs = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val r = result ?: return@rememberLauncherForActivityResult
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openOutputStream(uri, "w")?.use { it.write(r.first.data) } != null }.getOrDefault(false)
            }
            error = if (ok) null else resources.getString(R.string.export_save_failed)
        }
    }

    fun share(r: ExportResult, file: File) {
        val uri = runCatching { FileProvider.getUriForFile(context, "${context.packageName}.files", file) }.getOrNull() ?: return
        val intent = Intent(Intent.ACTION_SEND).setType(r.mimeType).putExtra(Intent.EXTRA_STREAM, uri)
            .apply { clipData = ClipData.newRawUri(file.name, uri) }
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            context.startActivity(Intent.createChooser(intent, file.name))
        } catch (_: ActivityNotFoundException) {
            error = resources.getString(R.string.files_no_app)
        }
    }

    ScreenScaffold(
        title = stringResource(R.string.export_title),
        navigationIcon = {
            IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back)) }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val done = result
            if (done != null) {
                val (r, file) = done
                Text(pluralStringResource(R.plurals.export_done, r.hosts.toInt(), r.hosts.toInt()), style = MaterialTheme.typography.titleMedium)
                if (r.hiddenSecrets > 0u) {
                    val n = r.hiddenSecrets.toInt()
                    Text(pluralStringResource(R.plurals.export_hidden_secrets, n, n), style = MaterialTheme.typography.bodySmall, color = Brand.Amber)
                }
                Text(file.name, style = Mono)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { share(r, file) }) { Text(stringResource(R.string.export_share)) }
                    OutlinedButton(onClick = { app.appLock.expectReturn(); saveAs.launch(file.name) }) { Text(stringResource(R.string.files_save_as)) }
                }
                OutlinedButton(onClick = onDone) { Text(stringResource(R.string.common_done)) }
            } else {
                Text(stringResource(R.string.export_from), style = MaterialTheme.typography.titleSmall)
                PlacePicker(app, place) { place = it; groupId = null }
                val all = stringResource(R.string.export_all_groups)
                Picker(
                    stringResource(R.string.import_group_label), groups.firstOrNull { it.first == groupId }?.second ?: all, null,
                    listOf<Pair<String?, String>>(null to all) + groups,
                ) { groupId = it }
                Text(stringResource(R.string.export_format), style = MaterialTheme.typography.titleSmall)
                val formats = listOf(ExportFormat.TERMOAK_JSON to "Termoak JSON", ExportFormat.CSV to "CSV")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    formats.forEachIndexed { i, (f, label) ->
                        SegmentedButton(format == f, { format = f }, SegmentedButtonDefaults.itemShape(i, formats.size)) { Text(label) }
                    }
                }
                Text(
                    stringResource(if (format == ExportFormat.CSV) R.string.export_intro_csv else R.string.export_intro_json),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (format == ExportFormat.TERMOAK_JSON) {
                    FormSwitch(stringResource(R.string.export_include_secrets), stringResource(R.string.export_passphrase_hint), secrets) { secrets = it }
                    if (secrets) {
                        OutlinedTextField(
                            pass1, { pass1 = it }, Modifier.fillMaxWidth(), singleLine = true,
                            label = { Text(stringResource(R.string.import_passphrase)) }, visualTransformation = PasswordVisualTransformation(),
                        )
                        OutlinedTextField(
                            pass2, { pass2 = it }, Modifier.fillMaxWidth(), singleLine = true,
                            label = { Text(stringResource(R.string.export_passphrase_repeat)) }, visualTransformation = PasswordVisualTransformation(),
                            isError = pass2.isNotEmpty() && problem != null,
                            supportingText = problem?.takeIf { pass2.isNotEmpty() }?.let { p ->
                                {
                                    Text(
                                        stringResource(
                                            if (p == ImportPlan.PassphraseProblem.SHORT) R.string.export_passphrase_short else R.string.export_passphrase_mismatch,
                                        ),
                                    )
                                }
                            },
                        )
                    }
                }
                Button(
                    onClick = {
                        busy = true
                        error = null
                        scope.launch {
                            try {
                                val r = app.core.exportHosts(
                                    format,
                                    ExportScope(accountId = place.account, vaultId = place.vault, deviceOnly = place.device, groupId = groupId),
                                    includeSecrets = withSecrets,
                                    passphrase = pass1.takeIf { withSecrets },
                                    app = "Termoak for Android ${BuildConfig.VERSION_NAME}",
                                )
                                val file = withContext(Dispatchers.IO) {
                                    val dir = File(context.cacheDir, "export").apply { deleteRecursively(); mkdirs() }
                                    File(dir, r.fileName.substringAfterLast('/').ifBlank { "termoak-hosts" }).apply { writeBytes(r.data) }
                                }
                                result = r to file
                            } catch (e: Exception) {
                                if (e is kotlinx.coroutines.CancellationException) throw e
                                error = e.userMessage(resources, R.string.export_failed)
                            } finally {
                                busy = false
                            }
                        }
                    },
                    Modifier.fillMaxWidth(), enabled = !busy && problem == null,
                ) { Text(stringResource(R.string.export_run)) }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(24.dp))
        }
    }
}
