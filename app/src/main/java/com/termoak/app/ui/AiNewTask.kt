package com.termoak.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.termoak.app.R
import com.termoak.app.TermoakApp
import com.termoak.app.data.AiTaskForm
import com.termoak.app.data.isTelnet
import com.termoak.ffi.AiPermissionMode
import com.termoak.ffi.AiProvider
import com.termoak.ffi.AiProviders
import com.termoak.ffi.AiTaskRequest
import com.termoak.ffi.TermoakException
import kotlinx.coroutines.launch

/** Where a new task runs. */
private enum class AiTarget { HOSTS, GROUP, TAG }

/** Why a provider can't be used, in the app's language by its stable code (the server's text for an unknown one). */
@Composable
internal fun providerReason(p: AiProvider): String? {
    if (p.available) return null
    return when (p.reasonCode) {
        "not_configured" -> stringResource(R.string.ai_provider_reason_not_configured)
        "own_key_required" -> stringResource(R.string.ai_provider_reason_own_key)
        "plan" -> stringResource(R.string.ai_provider_reason_plan)
        else -> p.reason
    }
}

/**
 * A new AI task with the options of server 0.6, like the desktop and iOS:
 * the request and its permissions, where it runs (some hosts, a group with
 * its subgroups, or a tag, with how many hosts each reaches), one
 * conversation per host when it reaches several, "Plan before acting", and
 * the provider, model and effort (the server's list, saying why one isn't
 * available). It runs on the AI section's account, with its hosts.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun NewAiTaskScreen(app: TermoakApp, nav: NavHostController) {
    val resources = LocalResources.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val account = remember { app.accounts.aiAccount() }
    val handle = remember(account?.id) { account?.let { app.accounts.handle(it.id) } }
    // The AI's tools reach hosts over SSH: not Telnet hosts.
    val hosts = remember {
        account?.let { a ->
            runCatching { app.core.listHosts(app.accounts.scopeFilter(a.id)) }.getOrDefault(emptyList())
                .filter { it.accountId == a.id && !it.isTelnet }
        }.orEmpty().sortedBy { it.label.lowercase() }
    }
    val groups = remember {
        account?.let { a ->
            runCatching { app.core.listGroups(app.accounts.scopeFilter(a.id)) }.getOrDefault(emptyList()).filter { it.accountId == a.id }
        }.orEmpty().sortedBy { it.name.lowercase() }
    }
    val tags = remember(hosts) { hosts.flatMap { it.tags }.distinct().sortedBy { it.lowercase() } }
    var prompt by remember { mutableStateOf("") }
    var target by remember { mutableStateOf(AiTarget.HOSTS) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var groupId by remember { mutableStateOf<String?>(null) }
    var tag by remember { mutableStateOf<String?>(null) }
    var fanOut by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf(AiPermissionMode.ASK) }
    var planFirst by remember { mutableStateOf(false) }
    var providers by remember { mutableStateOf<AiProviders?>(null) }
    var providerKey by remember { mutableStateOf<String?>(null) }
    var model by remember { mutableStateOf<String?>(null) }
    var effort by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(handle) { providers = runCatching { handle?.listAiProviders() }.getOrNull() }

    fun hostsInGroup(id: String): Int {
        val ids = AiTaskForm.groupAndSubgroups(groups, id)
        return hosts.count { it.groupId in ids }
    }
    val reach = when (target) {
        AiTarget.HOSTS -> selected.size
        AiTarget.GROUP -> groupId?.let { hostsInGroup(it) } ?: 0
        AiTarget.TAG -> tag?.let { t -> hosts.count { t in it.tags } } ?: 0
    }
    val provider = providers?.providers?.firstOrNull { it.key == providerKey }
    val unavailable = provider != null && !provider.available

    ScreenScaffold(
        title = stringResource(R.string.ai_new_task),
        navigationIcon = {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back)) }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().navigationBarsPadding()
                .verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                prompt, { prompt = it }, Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.ai_prompt_label)) },
                placeholder = { Text(stringResource(R.string.ai_prompt_placeholder)) },
                minLines = 4,
            )
            // ----- Where it runs -----
            Text(stringResource(R.string.ai_new_where), style = MaterialTheme.typography.titleSmall)
            val targets = buildList {
                add(AiTarget.HOSTS to R.string.section_hosts)
                if (groups.isNotEmpty()) add(AiTarget.GROUP to R.string.ai_new_target_group)
                if (tags.isNotEmpty()) add(AiTarget.TAG to R.string.ai_new_target_tag)
            }
            if (targets.size > 1) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    targets.forEachIndexed { i, (t, label) ->
                        SegmentedButton(target == t, { target = t }, SegmentedButtonDefaults.itemShape(i, targets.size)) { Text(stringResource(label)) }
                    }
                }
            }
            when (target) {
                AiTarget.HOSTS -> if (hosts.isEmpty()) {
                    Text(stringResource(R.string.ai_no_hosts), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        hosts.forEach { h ->
                            FilterChip(
                                selected = h.id in selected,
                                onClick = { selected = if (h.id in selected) selected - h.id else selected + h.id },
                                label = { Text(h.label) },
                            )
                        }
                    }
                }
                AiTarget.GROUP -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    groups.forEach { g ->
                        val n = hostsInGroup(g.id)
                        FilterChip(
                            selected = groupId == g.id, onClick = { groupId = if (groupId == g.id) null else g.id },
                            label = { Text(g.name + " · " + pluralStringResource(R.plurals.hosts_count, n, n)) },
                        )
                    }
                }
                AiTarget.TAG -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    tags.forEach { t ->
                        val n = hosts.count { t in it.tags }
                        FilterChip(
                            selected = tag == t, onClick = { tag = if (tag == t) null else t },
                            label = { Text("#$t · " + pluralStringResource(R.plurals.hosts_count, n, n)) },
                        )
                    }
                }
            }
            Text(
                stringResource(
                    when (target) {
                        AiTarget.HOSTS -> R.string.ai_new_where_hosts_hint
                        AiTarget.GROUP -> R.string.ai_new_where_group_hint
                        AiTarget.TAG -> R.string.ai_new_where_tag_hint
                    },
                ),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (reach > 1) {
                SwitchLine(pluralStringResource(R.plurals.ai_fan_out, reach, reach), stringResource(if (fanOut) R.string.ai_fan_out_hint else R.string.ai_fan_out_off_hint), fanOut) {
                    fanOut = it
                }
            }
            // ----- Permissions and plan -----
            Text(stringResource(R.string.common_permissions), style = MaterialTheme.typography.titleSmall)
            PermissionModeSelector(mode, { mode = it })
            Text(stringResource(permissionHint(mode)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SwitchLine(stringResource(R.string.ai_plan_first), stringResource(R.string.ai_plan_first_hint), planFirst) { planFirst = it }
            // ----- Provider, model and effort -----
            providers?.let { list ->
                val shown = list.providers.filter { !it.hidden }
                if (shown.isNotEmpty()) {
                    Text(stringResource(R.string.ai_model_section), style = MaterialTheme.typography.titleSmall)
                    val default = list.providers.firstOrNull { it.key == list.defaultProvider }
                    val defaultName = default?.let { stringResource(R.string.ai_provider_default_named, it.label) } ?: stringResource(R.string.ai_provider_default)
                    val unavailableNames = shown.associate { it.key to stringResource(R.string.ai_provider_unavailable, it.label) }
                    Picker(
                        stringResource(R.string.ai_provider), provider?.let { if (it.available) it.label else unavailableNames[it.key] } ?: defaultName, null,
                        listOf<Pair<String?, String>>(null to defaultName) + shown.map { it.key to (if (it.available) it.label else unavailableNames.getValue(it.key)) },
                    ) {
                        providerKey = it
                        model = null
                    }
                    provider?.let { p ->
                        providerReason(p)?.let { reason ->
                            Text(reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            if (p.reasonCode == "own_key_required") {
                                TextButton(onClick = { nav.navigate(Routes.AI_KEYS) }) { Text(stringResource(R.string.ai_keys_open)) }
                            }
                        }
                        if (p.models.isNotEmpty()) {
                            val defaultModel = p.defaultModel?.let { stringResource(R.string.ai_model_default_named, it) } ?: stringResource(R.string.ai_model_default)
                            Picker(
                                stringResource(R.string.ai_model), model ?: defaultModel, null,
                                listOf<Pair<String?, String>>(null to defaultModel) + p.models.map { it to it },
                            ) { model = it }
                        }
                    }
                    val efforts = listOf<Pair<String?, String>>(
                        null to stringResource(R.string.ai_effort_default),
                        "low" to stringResource(R.string.ai_effort_low),
                        "medium" to stringResource(R.string.ai_effort_medium),
                        "high" to stringResource(R.string.ai_effort_high),
                    )
                    Picker(stringResource(R.string.ai_effort), efforts.first { it.first == effort }.second, null, efforts) { effort = it }
                }
            }
            Button(
                onClick = {
                    busy = true
                    scope.launch {
                        try {
                            val h = handle ?: throw TermoakException.NotLoggedIn("")
                            val request = AiTaskRequest(
                                prompt = prompt.trim(), mode = mode,
                                provider = AiTaskForm.provider(providerKey, model), effort = effort,
                                hostIds = if (target == AiTarget.HOSTS) hosts.map { it.id }.filter { it in selected } else emptyList(),
                                groupId = groupId.takeIf { target == AiTarget.GROUP },
                                tag = tag?.trim()?.takeIf { target == AiTarget.TAG && it.isNotEmpty() },
                                fanOut = fanOut && reach > 1,
                                planFirst = planFirst,
                            )
                            val id = h.createAiTask(request).id
                            nav.popBackStack()
                            nav.navigate(Routes.aiTask(id, account?.id))
                        } catch (e: TermoakException) {
                            snackbar.showAiError(e, R.string.ai_create_failed, resources) { nav.navigate(Routes.AI_KEYS) }
                        } finally {
                            busy = false
                        }
                    }
                },
                enabled = prompt.isNotBlank() && !busy && !unavailable,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) { Text(stringResource(if (busy) R.string.ai_creating else R.string.ai_start)) }
        }
    }
}

@Composable
private fun SwitchLine(title: String, hint: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable { onChange(!on) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(on, onChange)
    }
}
