package com.termoak.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.termoak.app.R
import com.termoak.app.TermoakApp
import com.termoak.app.data.AccessBook
import com.termoak.app.data.AccessRules
import com.termoak.app.data.AccessTexts
import com.termoak.app.data.AccountTeams
import com.termoak.app.data.displayName
import com.termoak.app.data.shownEmail
import com.termoak.app.userMessage
import com.termoak.ffi.AccountStatus
import com.termoak.ffi.AiApprovalLevel
import com.termoak.ffi.IssuedCertificateInfo
import com.termoak.ffi.TeamComplianceMember
import com.termoak.ffi.TeamSecurityPolicy
import com.termoak.ffi.TermoakException
import kotlinx.coroutines.launch

/** The certificate extensions a policy can choose (OpenSSH's names). */
val CERT_EXTENSIONS = listOf("permit-pty", "permit-port-forwarding", "permit-agent-forwarding", "permit-X11-forwarding", "permit-user-rc")

/**
 * Teams → a team → Security (servers with teams security, 0.7 phase 4d):
 * the team's SSH certificate authority, its policy (2FA with a grace
 * period, revealing secrets, the AI and its approvals, private networks,
 * mandatory recording, certificate defaults) and, for owners and admins,
 * every member's 2FA state and certificate principal. Members who can't
 * change them see them.
 */
@Composable
fun TeamSecurityScreen(app: TermoakApp, nav: NavHostController, accountId: String, teamId: String, teamName: String, myRole: String?) {
    val res = LocalResources.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val handle = remember(accountId) { app.accounts.handle(accountId) }
    val manage = AccountTeams.canManage(myRole)
    var policy by remember { mutableStateOf<TeamSecurityPolicy?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var members by remember { mutableStateOf<List<TeamComplianceMember>?>(null) }
    var editingPrincipal by remember { mutableStateOf<TeamComplianceMember?>(null) }
    var saving by remember { mutableStateOf(false) }
    suspend fun loadMembers() {
        if (manage) members = runCatching { handle?.teamCompliance(teamId) }.getOrNull()
    }
    LaunchedEffect(teamId) {
        try {
            policy = handle?.teamPolicy(teamId)
        } catch (e: TermoakException) {
            error = e.userMessage(res, R.string.team_security_load_failed)
        }
        loadMembers()
    }
    ScreenScaffold(
        title = stringResource(R.string.team_security_title, teamName),
        navigationIcon = {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back)) }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            if (!manage) FormHint(stringResource(R.string.team_security_read_only))
            CaPanel(app, accountId, teamId, teamName)
            val p = policy
            when {
                error != null -> FormNote(error ?: "")
                p == null -> CircularProgressIndicator(Modifier.padding(20.dp).size(24.dp), strokeWidth = 2.dp)
                else -> TeamPolicyForm(p, manage, saving) { changed ->
                    scope.launch {
                        saving = true
                        try {
                            policy = handle?.setTeamPolicy(teamId, changed)
                            snackbar.showSnackbar(res.getString(R.string.team_security_saved))
                            app.accounts.sync(accountId)
                            loadMembers()
                        } catch (e: TermoakException) {
                            snackbar.showSnackbar(e.userMessage(res, R.string.team_security_load_failed))
                        } finally {
                            saving = false
                        }
                    }
                }
            }
            members?.let { list -> ComplianceSection(list) { editingPrincipal = it } }
        }
    }
    editingPrincipal?.let { m ->
        var value by remember { mutableStateOf(m.principal.orEmpty()) }
        val ok = value.isBlank() || AccessRules.principalValid(value.trim())
        AlertDialog(
            onDismissRequest = { editingPrincipal = null },
            title = { Text(stringResource(R.string.team_security_principal_title, shownEmail(m.email))) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.team_security_principal_message, AccessRules.defaultPrincipal(m.email)))
                    OutlinedTextField(
                        value, { value = it }, Modifier.fillMaxWidth(), singleLine = true,
                        label = { Text(stringResource(R.string.team_security_principal_label)) },
                        placeholder = { Text(AccessRules.defaultPrincipal(m.email)) }, isError = !ok,
                        supportingText = if (!ok) ({ Text(stringResource(R.string.team_security_principal_invalid)) }) else null,
                    )
                }
            },
            confirmButton = {
                TextButton(enabled = ok, onClick = {
                    editingPrincipal = null
                    scope.launch {
                        try {
                            handle?.setMemberPrincipal(teamId, m.userId, value.trim().ifEmpty { null })
                            loadMembers()
                            snackbar.showSnackbar(res.getString(R.string.team_security_principal_saved))
                        } catch (e: TermoakException) {
                            snackbar.showSnackbar(e.userMessage(res, R.string.team_security_load_failed))
                        }
                    }
                }) { Text(stringResource(R.string.common_save)) }
            },
            dismissButton = { TextButton(onClick = { editingPrincipal = null }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
}

/** The team policy form (read-only for members who can't change it). */
@Composable
fun TeamPolicyForm(p: TeamSecurityPolicy, manage: Boolean, saving: Boolean, onSave: (TeamSecurityPolicy) -> Unit) {
    var require2fa by remember(p) { mutableStateOf(p.require2fa) }
    var grace by remember(p) { mutableStateOf(p.graceDays.toString()) }
    var reveal by remember(p) { mutableStateOf(p.allowReveal) }
    var ai by remember(p) { mutableStateOf(p.aiAllowed) }
    var aiAlways by remember(p) { mutableStateOf(p.aiApproval == AiApprovalLevel.ALWAYS) }
    var networks by remember(p) { mutableStateOf(p.networksAllowed) }
    var record by remember(p) { mutableStateOf(p.recordSessions) }
    var defTtl by remember(p) { mutableStateOf(p.certificates.defaultTtlMinutes.toString()) }
    var maxTtl by remember(p) { mutableStateOf(p.certificates.maxTtlMinutes.toString()) }
    var principals by remember(p) { mutableStateOf(p.certificates.principals.joinToString(", ")) }
    var extensions by remember(p) { mutableStateOf(p.certificates.extensions.toSet()) }
    var source by remember(p) { mutableStateOf(p.certificates.sourceAddress.orEmpty()) }
    val graceOk = AccessRules.minutes(grace, 0..90)
    val defOk = AccessRules.minutes(defTtl, 1..1440)
    val maxOk = AccessRules.minutes(maxTtl, 1..1440)
    fun sw(title: Int, hint: Int, value: Boolean, set: (Boolean) -> Unit) = @Composable {
        if (manage) FormSwitch(stringResource(title), stringResource(hint), value, set)
        else ListItem(
            headlineContent = { Text(stringResource(title)) },
            supportingContent = { Text(stringResource(hint)) },
            trailingContent = { Pill(stringResource(if (value) R.string.settings_two_factor_active else R.string.settings_two_factor_inactive), if (value) Brand.Green else MaterialTheme.colorScheme.onSurfaceVariant) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
    FormSection(stringResource(R.string.team_security_policy_title)) {
        sw(R.string.team_security_require_2fa, R.string.team_security_require_2fa_hint, require2fa) { require2fa = it }()
        p.require2faSince?.takeIf { p.require2fa }?.let { FormHint(stringResource(R.string.team_security_require_2fa_since, AccessTexts.date(it))) }
        if (require2fa) {
            FormField(
                stringResource(R.string.team_security_grace_days), grace, { if (manage) grace = it.filter(Char::isDigit).take(2) },
                keyboard = androidx.compose.ui.text.input.KeyboardType.Number,
                error = if (graceOk == null) stringResource(R.string.team_security_error_grace) else null,
            )
        }
        FormDivider()
        sw(R.string.team_security_allow_reveal, R.string.team_security_allow_reveal_hint, reveal) { reveal = it }()
        FormDivider()
        sw(R.string.team_security_ai_allowed, R.string.team_security_ai_allowed_hint, ai) { ai = it }()
        if (ai) {
            val opts = listOf(false to stringResource(R.string.team_security_ai_approval_default), true to stringResource(R.string.team_security_ai_approval_always))
            if (manage) FormPicker(stringResource(R.string.team_security_ai_approval), opts.first { it.first == aiAlways }.second, opts) { aiAlways = it }
            else FormNote(stringResource(R.string.team_security_ai_approval) + ": " + opts.first { it.first == aiAlways }.second)
        }
        FormDivider()
        sw(R.string.team_security_networks, R.string.team_security_networks_hint, networks) { networks = it }()
        FormDivider()
        sw(R.string.team_security_record, R.string.team_security_record_hint, record) { record = it }()
    }
    FormSection(stringResource(R.string.team_security_cert_defaults)) {
        if (!manage) {
            FormNote(stringResource(R.string.team_security_ttl_summary) + ": " + stringResource(R.string.team_security_ttl_value, defTtl, maxTtl))
            FormNote(stringResource(R.string.cert_fields_principals) + ": " + principals)
        } else {
            FormField(stringResource(R.string.cert_fields_default_ttl), defTtl, { defTtl = it.filter(Char::isDigit).take(4) },
                keyboard = androidx.compose.ui.text.input.KeyboardType.Number,
                error = if (defOk == null) stringResource(R.string.cert_fields_error_number, defTtl) else null)
            FormField(stringResource(R.string.cert_fields_max_ttl), maxTtl, { maxTtl = it.filter(Char::isDigit).take(4) },
                keyboard = androidx.compose.ui.text.input.KeyboardType.Number,
                error = if (maxOk == null) stringResource(R.string.cert_fields_error_number, maxTtl) else null)
            FormField(stringResource(R.string.cert_fields_principals), principals, { principals = it }, mono = true, placeholder = "{login}")
            FormHint(stringResource(R.string.cert_fields_principals_hint))
            ExtensionPicker(extensions) { extensions = it }
            FormField(stringResource(R.string.cert_fields_source_address), source, { source = it }, mono = true, placeholder = "10.0.0.0/8")
            FormHint(stringResource(R.string.cert_fields_source_address_hint))
        }
    }
    if (manage) {
        val ok = graceOk != null && defOk != null && maxOk != null
        Button(
            onClick = {
                onSave(
                    p.copy(
                        require2fa = require2fa, graceDays = (graceOk ?: 7).toUInt(), allowReveal = reveal, aiAllowed = ai,
                        aiApproval = if (aiAlways) AiApprovalLevel.ALWAYS else AiApprovalLevel.DEFAULT,
                        networksAllowed = networks, recordSessions = record,
                        certificates = p.certificates.copy(
                            defaultTtlMinutes = (defOk ?: 60).toUInt(), maxTtlMinutes = (maxOk ?: 480).toUInt(),
                            principals = AccessRules.templates(principals).ifEmpty { listOf("{login}") },
                            extensions = CERT_EXTENSIONS.filter { it in extensions },
                            sourceAddress = source.trim().ifEmpty { null },
                        ),
                    ),
                )
            },
            enabled = ok && !saving, modifier = Modifier.padding(16.dp).fillMaxWidth().testTag("team-policy-save"),
        ) { Text(stringResource(R.string.team_security_save)) }
    }
}

/** Checkboxes of the certificate extensions (as switches). */
@Composable
fun ExtensionPicker(selected: Set<String>, onChange: (Set<String>) -> Unit) {
    Text(stringResource(R.string.cert_fields_extensions), Modifier.padding(start = 16.dp, top = 8.dp), style = MaterialTheme.typography.labelMedium)
    CERT_EXTENSIONS.forEach { e ->
        ListItem(
            modifier = Modifier.clickable { onChange(if (e in selected) selected - e else selected + e) },
            headlineContent = { Text(e, style = MaterialTheme.typography.bodyMedium) },
            trailingContent = { androidx.compose.material3.Checkbox(e in selected, { onChange(if (it) selected + e else selected - e) }) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
}

/** Members with their 2FA state under the policy and their principal. */
@Composable
fun ComplianceSection(list: List<TeamComplianceMember>, onPrincipal: (TeamComplianceMember) -> Unit) {
    FormSection(stringResource(R.string.team_security_compliance_title)) {
        val missing = list.count { !it.totpEnabled }
        FormNote(
            if (missing == 0) stringResource(R.string.team_security_compliance_all)
            else pluralStringResource(R.plurals.team_security_compliance_missing, missing, missing),
        )
        list.forEach { m ->
            ListItem(
                modifier = Modifier.clickable { onPrincipal(m) },
                headlineContent = { Text(m.name.ifBlank { shownEmail(m.email) }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = {
                    Text(
                        stringResource(R.string.team_security_compliance_principal, shownEmail(m.email), m.principal ?: AccessRules.defaultPrincipal(m.email)),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                },
                trailingContent = {
                    when {
                        m.totpEnabled -> Pill(stringResource(R.string.team_security_compliance_on), Brand.Green)
                        m.blocked -> Pill(stringResource(R.string.team_security_compliance_blocked), MaterialTheme.colorScheme.error)
                        m.deadline != null -> Pill(stringResource(R.string.team_security_compliance_until, AccessTexts.date(m.deadline!!)), Brand.Amber)
                        else -> Pill(stringResource(R.string.team_security_compliance_off), MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }
    }
}

/**
 * Settings → SSH certificates: your personal certificate authority (for the
 * hosts of your personal vault) and the certificates issued to you, per
 * account whose server has teams security.
 */
@Composable
fun CertificatesScreen(app: TermoakApp, nav: NavHostController, preferred: String?) {
    val accounts by app.accounts.list.collectAsState()
    val supported = accounts.filter { it.status == AccountStatus.ACTIVE && AccessBook.supported(it.id) }
    var accountId by remember { mutableStateOf(preferred?.takeIf { p -> supported.any { it.id == p } } ?: supported.firstOrNull()?.id) }
    ScreenScaffold(
        title = stringResource(R.string.settings_certificates),
        navigationIcon = {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back)) }
        },
    ) { padding ->
        val id = accountId
        if (id == null) {
            EmptyState(Icons.Outlined.CloudOff, stringResource(R.string.access_none_title), stringResource(R.string.access_none_hint), Modifier.padding(padding))
            return@ScreenScaffold
        }
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            if (supported.size > 1) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Picker(stringResource(R.string.vault_account), supported.firstOrNull { it.id == id }?.displayName, null, supported.map { it.id to it.displayName }) {
                        accountId = it
                    }
                }
            }
            androidx.compose.runtime.key(id) {
                CaPanel(app, id, null, null)
                MyCertificates(app, id)
            }
        }
    }
}

@Composable
private fun MyCertificates(app: TermoakApp, accountId: String) {
    val res = LocalResources.current
    var list by remember { mutableStateOf<List<IssuedCertificateInfo>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(accountId) {
        try {
            list = app.accounts.handle(accountId)?.myCertificates()?.sortedByDescending { it.createdAt }
        } catch (e: TermoakException) {
            error = e.userMessage(res, R.string.team_security_load_failed)
        }
    }
    FormSection(stringResource(R.string.certs_mine)) {
        val l = list
        when {
            error != null -> FormNote(error ?: "")
            l == null -> CircularProgressIndicator(Modifier.padding(16.dp).size(24.dp), strokeWidth = 2.dp)
            l.isEmpty() -> FormNote(stringResource(R.string.certs_mine_empty))
            else -> l.take(30).forEach { c -> IssuedCertificateRow(c, null, null) }
        }
    }
}
