package com.termoak.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.termoak.app.R
import com.termoak.app.TermoakApp
import com.termoak.app.data.AccessRules
import com.termoak.app.data.AccessTexts
import com.termoak.app.data.shownEmail
import com.termoak.app.userMessage
import com.termoak.ffi.AccessMode
import com.termoak.ffi.AccessPolicyInfo
import com.termoak.ffi.AccessPolicyRecord
import com.termoak.ffi.AccessTargetKind
import com.termoak.ffi.ItemFilter
import com.termoak.ffi.TermoakException
import com.termoak.ffi.VaultMember
import kotlinx.coroutines.launch

/** Who may approve: a user of the vault. */
data class Approver(val userId: String, val name: String)

/** The policy that applies to a target without its own: its nearest group's, else the vault's (`null`: direct). */
fun inheritedPolicy(
    kind: AccessTargetKind,
    targetId: String,
    vaultId: String,
    groupOf: (String) -> String?,
    parentOf: (String) -> String?,
    policies: List<AccessPolicyRecord>,
): AccessPolicyRecord? {
    if (kind == AccessTargetKind.VAULT) return null
    var g = if (kind == AccessTargetKind.HOST) groupOf(targetId) else parentOf(targetId)
    var hops = 0
    while (g != null && hops++ < 16) {
        policies.firstOrNull { it.targetKind == AccessTargetKind.GROUP && it.targetId == g }?.let { return it }
        g = parentOf(g)
    }
    return policies.firstOrNull { it.targetKind == AccessTargetKind.VAULT && it.targetId == vaultId }
}

/**
 * The access policy of a host, a group or the whole vault (vault Admins):
 * Direct or With approval (approvers, longest window, how long a request
 * waits, self-approval, Admins too), recording, certificate options; what
 * applies when it has none; remove it.
 */
@Composable
fun AccessPolicyScreen(app: TermoakApp, nav: NavHostController, accountId: String, vaultId: String, kind: String, targetId: String) {
    val res = LocalResources.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val handle = remember(accountId) { app.accounts.handle(accountId) }
    val targetKind = AccessRules.kindOf(kind)
    val filter = remember(accountId) { ItemFilter(accountIds = listOf(accountId), vaultIds = null, includeDevice = false) }
    val groups = remember { runCatching { app.core.listGroups(filter) }.getOrDefault(emptyList()).filter { it.vaultId == vaultId } }
    val host = remember { if (targetKind == AccessTargetKind.HOST) runCatching { app.core.getHost(targetId, accountId) }.getOrNull() else null }
    val vault = AccessUi.vaultOf(app, accountId, vaultId)
    val name = when (targetKind) {
        AccessTargetKind.HOST -> host?.label ?: stringResource(R.string.access_policy_missing_target)
        AccessTargetKind.GROUP -> groups.firstOrNull { it.id == targetId }?.name ?: stringResource(R.string.access_policy_missing_target)
        else -> vault?.let { vaultName(it) } ?: ""
    }
    var policies by remember { mutableStateOf<List<AccessPolicyRecord>?>(null) }
    var members by remember { mutableStateOf<List<VaultMember>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    suspend fun load() {
        try {
            policies = handle?.accessPolicies(vaultId)
            error = null
        } catch (e: TermoakException) {
            error = e.userMessage(res, R.string.access_policy_load_failed)
        }
    }
    LaunchedEffect(vaultId) {
        load()
        members = runCatching { handle?.vaultMembers(vaultId) }.getOrNull().orEmpty()
    }
    ScreenScaffold(
        title = stringResource(
            when (targetKind) {
                AccessTargetKind.HOST -> R.string.access_policy_title_host
                AccessTargetKind.GROUP -> R.string.access_policy_title_group
                else -> R.string.access_policy_title_vault
            },
            name,
        ),
        navigationIcon = {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back)) }
        },
    ) { padding ->
        val list = policies
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            when {
                error != null -> FormNote(error ?: "")
                list == null -> CircularProgressIndicator(Modifier.padding(20.dp).size(24.dp), strokeWidth = 2.dp)
                else -> {
                    val own = list.firstOrNull { it.targetKind == targetKind && it.targetId == targetId }
                    val inherited = if (own == null) {
                        inheritedPolicy(
                            targetKind, targetId, vaultId,
                            groupOf = { host?.groupId }, parentOf = { id -> groups.firstOrNull { it.id == id }?.parentId }, policies = list,
                        )
                    } else null
                    if (own != null) {
                        FormHint(stringResource(R.string.access_policy_own))
                    } else if (targetKind != AccessTargetKind.VAULT) {
                        val from = inherited?.let { r ->
                            when (r.targetKind) {
                                AccessTargetKind.GROUP -> res.getString(R.string.access_target_group, groups.firstOrNull { it.id == r.targetId }?.name ?: "")
                                else -> res.getString(R.string.access_policy_the_vault)
                            }
                        }
                        FormHint(
                            when {
                                inherited == null -> stringResource(R.string.access_policy_none)
                                inherited.policy.mode == AccessMode.APPROVAL -> stringResource(R.string.access_policy_inherits_approval, from ?: "")
                                else -> stringResource(R.string.access_policy_inherits_direct, from ?: "")
                            },
                        )
                    }
                    val approvers = members.filter { it.userId != null && (it.kind == com.termoak.ffi.VaultMemberKind.USER || it.implicit) }
                        .mapNotNull { m -> m.userId?.let { Approver(it, m.name.ifBlank { m.email?.let(::shownEmail) ?: it }) } }
                        .distinctBy { it.userId }
                    AccessPolicyForm(own?.policy ?: inherited?.policy?.copy(approvers = emptyList()) ?: AccessPolicyInfo(mode = AccessMode.APPROVAL), approvers, saving, hasOwn = own != null,
                        onSave = { p ->
                            scope.launch {
                                saving = true
                                try {
                                    handle?.setAccessPolicy(vaultId, targetKind, targetId, p)
                                    load()
                                    app.accounts.sync(accountId)
                                    snackbar.showSnackbar(res.getString(R.string.access_policy_saved))
                                } catch (e: TermoakException) {
                                    snackbar.showSnackbar(e.userMessage(res, R.string.access_policy_load_failed))
                                } finally {
                                    saving = false
                                }
                            }
                        },
                        onRemove = {
                            val id = own?.id ?: return@AccessPolicyForm
                            scope.launch {
                                try {
                                    handle?.deleteAccessPolicy(vaultId, id)
                                    load()
                                    app.accounts.sync(accountId)
                                    snackbar.showSnackbar(res.getString(R.string.access_policy_removed))
                                } catch (e: TermoakException) {
                                    snackbar.showSnackbar(e.userMessage(res, R.string.access_policy_load_failed))
                                }
                            }
                        },
                    )
                    if (targetKind == AccessTargetKind.VAULT) FormHint(stringResource(R.string.access_policy_vault_hint))
                }
            }
        }
    }
}

/** The policy form (the screenshot tests draw it with data). */
@Composable
fun AccessPolicyForm(
    initial: AccessPolicyInfo,
    approvers: List<Approver>,
    saving: Boolean,
    hasOwn: Boolean,
    onSave: (AccessPolicyInfo) -> Unit,
    onRemove: () -> Unit,
) {
    var mode by remember(initial) { mutableStateOf(if (initial.mode == AccessMode.DIRECT) AccessMode.DIRECT else AccessMode.APPROVAL) }
    var chosen by remember(initial) { mutableStateOf(initial.approvers.toSet()) }
    var max by remember(initial) { mutableStateOf(initial.maxMinutes.toString()) }
    var ttl by remember(initial) { mutableStateOf(initial.requestTtlMinutes.toString()) }
    var self by remember(initial) { mutableStateOf(initial.allowSelfApproval) }
    var managers by remember(initial) { mutableStateOf(initial.requireForManagers) }
    var record by remember(initial) { mutableStateOf(initial.recordSessions) }
    var principals by remember(initial) { mutableStateOf(initial.principals?.joinToString(", ").orEmpty()) }
    var certTtl by remember(initial) { mutableStateOf(initial.certTtlMinutes?.toString().orEmpty()) }
    var customExt by remember(initial) { mutableStateOf(initial.extensions != null) }
    var extensions by remember(initial) { mutableStateOf(initial.extensions?.toSet() ?: CERT_EXTENSIONS.take(3).toSet()) }
    var source by remember(initial) { mutableStateOf(initial.sourceAddress.orEmpty()) }
    var force by remember(initial) { mutableStateOf(initial.forceCommand.orEmpty()) }
    val maxOk = AccessRules.minutes(max, 1..10080)
    val ttlOk = AccessRules.minutes(ttl, 5..10080)
    val certTtlOk = certTtl.isBlank() || AccessRules.minutes(certTtl, 1..1440) != null
    val res = LocalResources.current
    FormSection(stringResource(R.string.access_policy_mode_label)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(12.dp)) {
            SegmentedButton(mode == AccessMode.DIRECT, { mode = AccessMode.DIRECT }, SegmentedButtonDefaults.itemShape(0, 2)) {
                Text(stringResource(R.string.access_policy_summary_direct))
            }
            SegmentedButton(mode == AccessMode.APPROVAL, { mode = AccessMode.APPROVAL }, SegmentedButtonDefaults.itemShape(1, 2), Modifier.testTag("mode-approval")) {
                Text(stringResource(R.string.access_policy_mode_approval), maxLines = 1)
            }
        }
        FormHint(stringResource(R.string.access_policy_mode_hint))
        if (mode == AccessMode.APPROVAL) {
            FormDivider()
            FormField(stringResource(R.string.access_policy_max), max, { max = it.filter(Char::isDigit).take(5) }, keyboard = KeyboardType.Number,
                error = if (maxOk == null) stringResource(R.string.access_policy_error_max) else null,
                placeholder = stringResource(R.string.access_policy_max_hint))
            maxOk?.let { FormHint(AccessTexts.duration(res, it)) }
            FormField(stringResource(R.string.access_policy_ttl), ttl, { ttl = it.filter(Char::isDigit).take(5) }, keyboard = KeyboardType.Number,
                error = if (ttlOk == null) stringResource(R.string.access_policy_error_ttl) else null,
                placeholder = stringResource(R.string.access_policy_ttl_hint))
            FormDivider()
            FormSwitch(stringResource(R.string.access_policy_managers), stringResource(R.string.access_policy_managers_hint), managers) { managers = it }
            FormSwitch(stringResource(R.string.access_policy_self_approval), stringResource(R.string.access_policy_self_approval_hint), self) { self = it }
        }
        FormDivider()
        FormSwitch(stringResource(R.string.access_policy_record), stringResource(R.string.access_policy_record_hint), record) { record = it }
    }
    if (mode == AccessMode.APPROVAL) {
        FormSection(stringResource(R.string.access_policy_approvers)) {
            FormHint(
                if (chosen.isEmpty()) stringResource(R.string.access_policy_approvers_hint)
                else pluralStringResource(R.plurals.access_policy_approvers_count, chosen.size, chosen.size),
            )
            approvers.forEach { a ->
                ListItem(
                    modifier = Modifier.clickable { chosen = if (a.userId in chosen) chosen - a.userId else chosen + a.userId },
                    headlineContent = { Text(a.name) },
                    trailingContent = { Checkbox(a.userId in chosen, { chosen = if (it) chosen + a.userId else chosen - a.userId }) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }
    }
    FormSection(stringResource(R.string.access_policy_cert_overrides)) {
        FormField(stringResource(R.string.cert_fields_principals), principals, { principals = it }, mono = true, placeholder = stringResource(R.string.cert_fields_inherit))
        FormHint(stringResource(R.string.cert_fields_preview_inherit))
        FormField(stringResource(R.string.cert_fields_ttl), certTtl, { certTtl = it.filter(Char::isDigit).take(4) }, keyboard = KeyboardType.Number,
            placeholder = stringResource(R.string.cert_fields_inherit), error = if (!certTtlOk) stringResource(R.string.cert_fields_error_number, certTtl) else null)
        FormSwitch(stringResource(R.string.cert_fields_extensions_custom), stringResource(R.string.cert_fields_extensions), customExt) { customExt = it }
        if (customExt) ExtensionPicker(extensions) { extensions = it }
        FormField(stringResource(R.string.cert_fields_source_address), source, { source = it }, mono = true, placeholder = "10.0.0.0/8")
        FormField(stringResource(R.string.cert_fields_force_command), force, { force = it.take(1024) }, mono = true,
            placeholder = stringResource(R.string.cert_fields_force_command_placeholder))
        FormHint(stringResource(R.string.cert_fields_force_command_hint))
    }
    val ok = (mode == AccessMode.DIRECT || (maxOk != null && ttlOk != null)) && certTtlOk
    Button(
        onClick = {
            onSave(
                AccessPolicyInfo(
                    mode = mode,
                    approvers = if (mode == AccessMode.APPROVAL) approvers.map { it.userId }.filter { it in chosen } else emptyList(),
                    maxMinutes = (maxOk ?: initial.maxMinutes.toInt()).toUInt(),
                    requestTtlMinutes = (ttlOk ?: initial.requestTtlMinutes.toInt()).toUInt(),
                    allowSelfApproval = self && mode == AccessMode.APPROVAL,
                    requireForManagers = managers && mode == AccessMode.APPROVAL,
                    principals = AccessRules.templates(principals).ifEmpty { null },
                    certTtlMinutes = certTtl.trim().toIntOrNull()?.toUInt(),
                    extensions = if (customExt) CERT_EXTENSIONS.filter { it in extensions } else null,
                    sourceAddress = source.trim().ifEmpty { null },
                    forceCommand = force.trim().ifEmpty { null },
                    recordSessions = record,
                ),
            )
        },
        enabled = ok && !saving, modifier = Modifier.padding(16.dp).fillMaxWidth().testTag("policy-save"),
    ) { Text(stringResource(R.string.access_policy_save)) }
    if (hasOwn) {
        OutlinedButton(onClick = onRemove, modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
            Icon(Icons.Outlined.Delete, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
            Text(stringResource(R.string.access_policy_remove), Modifier.padding(start = 8.dp), color = MaterialTheme.colorScheme.error)
        }
    }
}

/**
 * A vault's access policies (vault Admins): the whole vault's and each
 * group's and host's, with their mode; open one to change it. Groups and
 * hosts get theirs from "Access policy…" in their menu.
 */
@Composable
fun VaultAccessScreen(app: TermoakApp, nav: NavHostController, accountId: String, vaultId: String) {
    val res = LocalResources.current
    val handle = remember(accountId) { app.accounts.handle(accountId) }
    var policies by remember { mutableStateOf<List<AccessPolicyRecord>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val filter = remember(accountId) { ItemFilter(accountIds = listOf(accountId), vaultIds = null, includeDevice = false) }
    val groups = remember { runCatching { app.core.listGroups(filter) }.getOrDefault(emptyList()) }
    val hosts = remember { runCatching { app.core.listHosts(filter) }.getOrDefault(emptyList()) }
    val vault = AccessUi.vaultOf(app, accountId, vaultId)
    LaunchedEffect(vaultId) {
        try {
            policies = handle?.accessPolicies(vaultId)
        } catch (e: TermoakException) {
            error = e.userMessage(res, R.string.access_policy_load_failed)
        }
    }
    ScreenScaffold(
        title = stringResource(R.string.access_policy_needed_title),
        subtitle = vault?.let { vaultName(it) },
        navigationIcon = {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back)) }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            FormHint(stringResource(R.string.access_policy_vault_hint))
            val list = policies
            when {
                error != null -> FormNote(error ?: "")
                list == null -> CircularProgressIndicator(Modifier.padding(20.dp).size(24.dp), strokeWidth = 2.dp)
                else -> {
                    val vaultPolicy = list.firstOrNull { it.targetKind == AccessTargetKind.VAULT }
                    PolicyRow(
                        stringResource(R.string.access_policy_target_vault),
                        vaultPolicy?.let { policySummary(it.policy) } ?: stringResource(R.string.access_policy_summary_direct),
                    ) { nav.navigate(Routes.accessPolicy(accountId, vaultId, "vault", vaultId)) }
                    list.filter { it.targetKind != AccessTargetKind.VAULT }.forEach { r ->
                        val label = when (r.targetKind) {
                            AccessTargetKind.GROUP -> stringResource(R.string.access_policy_target_group, groups.firstOrNull { it.id == r.targetId }?.name ?: stringResource(R.string.access_policy_missing_target))
                            else -> stringResource(R.string.access_policy_target_host, hosts.firstOrNull { it.id == r.targetId }?.label ?: stringResource(R.string.access_policy_missing_target))
                        }
                        PolicyRow(label, policySummary(r.policy)) {
                            nav.navigate(Routes.accessPolicy(accountId, vaultId, AccessRules.kindName(r.targetKind), r.targetId))
                        }
                    }
                    if (list.isEmpty()) FormHint(stringResource(R.string.access_policy_empty))
                    FormHint(stringResource(R.string.access_policy_where_hint))
                }
            }
        }
    }
}

@Composable
private fun PolicyRow(title: String, summary: String, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        trailingContent = { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

/** "Approval · up to 1 h" / "Direct" (+ "Recorded"). */
@Composable
fun policySummary(p: AccessPolicyInfo): String {
    val res = LocalResources.current
    val base = if (p.mode == AccessMode.DIRECT) stringResource(R.string.access_policy_summary_direct)
    else stringResource(R.string.access_policy_summary_approval, AccessTexts.duration(res, p.maxMinutes.toInt()))
    return if (p.recordSessions) base + " · " + stringResource(R.string.access_recorded) else base
}
