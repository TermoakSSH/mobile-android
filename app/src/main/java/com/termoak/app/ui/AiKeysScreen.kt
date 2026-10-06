package com.termoak.app.ui

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.termoak.app.R
import com.termoak.app.TermoakApp
import com.termoak.app.UiText
import com.termoak.app.aiSetupError
import com.termoak.app.asString
import com.termoak.app.toUiText
import com.termoak.ffi.AiAccessInfo
import com.termoak.ffi.AiKeyInfo
import com.termoak.ffi.AiKeyProvider
import com.termoak.ffi.TermoakException
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * Shows [e] in the snackbar. A missing API key or spent AI credit gets its
 * translated message and an action that runs [openAiSettings] (Settings → AI);
 * any other error shows its own message or, without one, [fallback].
 */
suspend fun SnackbarHostState.showAiError(
    e: Throwable,
    @StringRes fallback: Int,
    resources: Resources,
    openAiSettings: () -> Unit,
) {
    val setup = aiSetupError(e)
    if (setup == null) {
        showSnackbar(e.message?.takeIf { it.isNotBlank() } ?: resources.getString(fallback))
        return
    }
    val result = showSnackbar(
        resources.getString(setup), actionLabel = resources.getString(R.string.ai_keys_open),
        withDismissAction = true, duration = SnackbarDuration.Long,
    )
    if (result == SnackbarResult.ActionPerformed) openAiSettings()
}

private fun usd(amount: Double, locale: Locale): String =
    NumberFormat.getCurrencyInstance(locale).apply { currency = Currency.getInstance("USD") }.format(amount)

/** Settings → AI: who pays for the AI (credit, own keys) and your own API keys, one card per provider. */
@Composable
fun AiKeysScreen(app: TermoakApp, nav: NavHostController) {
    val resources = LocalResources.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val loggedIn by app.accounts.loggedIn.collectAsState()
    var access by remember { mutableStateOf<AiAccessInfo?>(null) }
    var keys by remember { mutableStateOf<List<AiKeyInfo>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    suspend fun load() {
        loading = true
        try {
            access = app.core.aiAccess()
            keys = app.core.listAiKeys()
        } catch (e: TermoakException) {
            snackbar.showSnackbar(e.message?.takeIf { it.isNotBlank() } ?: resources.getString(R.string.ai_keys_load_failed))
        } finally {
            loading = false
        }
    }
    LaunchedEffect(loggedIn) { if (loggedIn == true) load() }

    ScreenScaffold(
        title = stringResource(R.string.section_ai),
        navigationIcon = {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back)) }
        },
    ) { padding ->
        val info = access
        if (info == null) {
            if (loading) {
                Column(Modifier.fillMaxSize().padding(padding), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(Modifier.padding(32.dp))
                }
            } else {
                EmptyState(
                    Icons.Outlined.CloudOff, stringResource(R.string.ai_keys_load_failed), "",
                    Modifier.padding(padding), action = stringResource(R.string.ai_keys_retry),
                    onAction = { scope.launch { load() } },
                )
            }
            return@ScreenScaffold
        }
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
        ) {
            AccessCard(info)
            SectionLabel(stringResource(R.string.ai_keys_providers))
            info.providers.forEach { p ->
                key(p.provider) { ProviderKeyCard(app, p, keys.firstOrNull { it.provider == p.provider }) { load() } }
            }
            Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                Icon(Icons.Outlined.Lock, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    stringResource(R.string.ai_keys_privacy), Modifier.padding(start = 8.dp),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Who pays for the AI: your own keys only, or the server's AI with this month's credit. */
@Composable
private fun AccessCard(info: AiAccessInfo) {
    val locale = LocalConfiguration.current.locales[0]
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    CardBox(Modifier.padding(top = 8.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val credit = info.creditUsd
            when {
                !info.serverAi -> {
                    Text(stringResource(R.string.ai_keys_own_only), style = MaterialTheme.typography.titleSmall)
                    if (info.ownKeys.isEmpty()) {
                        Text(stringResource(R.string.ai_keys_own_only_none), style = MaterialTheme.typography.bodySmall, color = secondary)
                    }
                }
                credit != null -> {
                    Text(
                        stringResource(R.string.ai_keys_credit_used, usd(info.spentUsd, locale), usd(credit, locale)),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    val used = if (credit > 0) (info.spentUsd / credit).toFloat().coerceIn(0f, 1f) else 1f
                    LinearProgressIndicator(
                        progress = { used },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        color = when {
                            used >= 1f -> Brand.Red
                            used >= 0.8f -> Brand.Amber
                            else -> MaterialTheme.colorScheme.primary
                        },
                    )
                    val left = info.remainingUsd ?: (credit - info.spentUsd).coerceAtLeast(0.0)
                    Text(stringResource(R.string.ai_keys_credit_left, usd(left, locale)), style = MaterialTheme.typography.bodySmall, color = secondary)
                }
                else -> {
                    Text(stringResource(R.string.ai_keys_server_ai), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.ai_keys_spent, usd(info.spentUsd, locale)), style = MaterialTheme.typography.bodySmall, color = secondary)
                }
            }
            if (info.serverAi && info.ownKeys.isNotEmpty()) {
                Text(stringResource(R.string.ai_keys_own_first), style = MaterialTheme.typography.bodySmall, color = Brand.Green)
            }
        }
    }
}

/** Result of the last action on a card: what to show and whether it went well. */
private data class Feedback(val text: UiText, val ok: Boolean)

/** One provider: its saved key (if any), a field for a new one, the model and Test · Save · Delete. */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ProviderKeyCard(app: TermoakApp, provider: AiKeyProvider, saved: AiKeyInfo?, onChanged: suspend () -> Unit) {
    val scope = rememberCoroutineScope()
    var key by remember(provider.provider) { mutableStateOf("") }
    var model by remember(provider.provider, saved?.updatedAt) { mutableStateOf(saved?.model.orEmpty()) }
    var modelsOpen by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var feedback by remember(provider.provider) { mutableStateOf<Feedback?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val models = remember(provider) { (listOfNotNull(provider.defaultModel) + provider.models).distinct() }
    val defaultLabel = provider.defaultModel?.let { stringResource(R.string.ai_keys_model_default_named, it) }
        ?: stringResource(R.string.ai_keys_model_default)

    fun act(action: suspend () -> Feedback) {
        busy = true
        feedback = null
        scope.launch {
            feedback = try {
                action()
            } finally {
                busy = false
                testing = false
            }
        }
    }

    CardBox {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(provider.label, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                if (saved != null) {
                    Pill(stringResource(R.string.ai_keys_saved), Brand.Green)
                } else {
                    Pill(stringResource(R.string.common_no_key), MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (saved != null) {
                Text(
                    stringResource(R.string.ai_keys_saved_line, saved.hint, saved.model ?: defaultLabel),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedTextField(
                key, { key = it; feedback = null }, Modifier.fillMaxWidth(),
                label = { Text(stringResource(if (saved != null) R.string.ai_keys_key_replace else R.string.ai_keys_key_label)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Password,
                ),
            )
            ExposedDropdownMenuBox(modelsOpen, { modelsOpen = it && models.isNotEmpty() }) {
                OutlinedTextField(
                    model, { model = it }, Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
                    label = { Text(stringResource(R.string.ai_keys_model_label)) },
                    placeholder = { Text(defaultLabel) },
                    supportingText = { Text(stringResource(R.string.ai_keys_model_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                    trailingIcon = {
                        if (models.isNotEmpty()) {
                            ExposedDropdownMenuDefaults.TrailingIcon(
                                modelsOpen, Modifier.menuAnchor(ExposedDropdownMenuAnchorType.SecondaryEditable),
                            )
                        }
                    },
                )
                ExposedDropdownMenu(modelsOpen, { modelsOpen = false }) {
                    models.forEach { m ->
                        DropdownMenuItem(
                            text = { Text(if (m == provider.defaultModel) stringResource(R.string.ai_keys_model_default_named, m) else m) },
                            onClick = { model = m; modelsOpen = false },
                            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                        )
                    }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton(
                    onClick = {
                        testing = true
                        act {
                            try {
                                val r = app.core.testAiKey(provider.provider, key.trim().ifEmpty { null })
                                val reason = r.error?.takeIf { it.isNotBlank() } ?: r.status?.let { "HTTP $it" }
                                when {
                                    r.ok -> Feedback(UiText.Res(R.string.ai_keys_test_ok), true)
                                    reason != null -> Feedback(UiText.Res(R.string.ai_keys_test_rejected, listOf(reason)), false)
                                    else -> Feedback(UiText.Res(R.string.ai_keys_test_rejected_plain), false)
                                }
                            } catch (e: TermoakException) {
                                Feedback(e.toUiText(R.string.ai_keys_test_failed), false)
                            }
                        }
                    },
                    enabled = !busy && (key.isNotBlank() || saved != null),
                ) { Text(stringResource(if (testing) R.string.ai_keys_testing else R.string.ai_keys_test)) }
                Button(
                    onClick = {
                        act {
                            try {
                                // Empty field with a saved key: only the model changes.
                                app.core.setAiKey(provider.provider, key.trim().ifEmpty { null }, model.trim().ifEmpty { null })
                                key = ""
                                onChanged()
                                Feedback(UiText.Res(R.string.ai_keys_saved_ok), true)
                            } catch (e: TermoakException) {
                                Feedback(e.toUiText(R.string.error_save_failed), false)
                            }
                        }
                    },
                    enabled = !busy && (key.isNotBlank() || (saved != null && model.trim() != saved.model.orEmpty())),
                ) { Text(stringResource(R.string.common_save)) }
                if (saved != null) {
                    TextButton(onClick = { confirmDelete = true }, enabled = !busy) {
                        Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            feedback?.let { f ->
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        if (f.ok) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline, null, Modifier.size(16.dp),
                        tint = if (f.ok) Brand.Green else MaterialTheme.colorScheme.error,
                    )
                    Text(
                        f.text.asString(), Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodySmall,
                        color = if (f.ok) Brand.Green else MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.ai_keys_delete_title, provider.label),
            text = stringResource(R.string.ai_keys_delete_text),
            confirm = stringResource(R.string.common_delete),
            destructive = true,
            onDismiss = { confirmDelete = false },
        ) {
            act {
                try {
                    app.core.deleteAiKey(provider.provider)
                    key = ""
                    onChanged()
                    Feedback(UiText.Res(R.string.ai_keys_deleted), true)
                } catch (e: TermoakException) {
                    Feedback(e.toUiText(R.string.ai_keys_delete_failed), false)
                }
            }
        }
    }
}
