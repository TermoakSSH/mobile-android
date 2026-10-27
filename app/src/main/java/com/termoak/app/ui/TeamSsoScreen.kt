package com.termoak.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.termoak.app.R
import com.termoak.app.TermoakApp
import com.termoak.app.data.AccountTeams
import com.termoak.app.data.E2eeBook
import com.termoak.app.data.SsoRules
import com.termoak.app.userMessage
import com.termoak.ffi.SsoSettings
import com.termoak.ffi.TeamDomainInfo
import com.termoak.ffi.TermoakException
import kotlinx.coroutines.launch

/** A copyable line (monospace) with copy and share. */
@Composable
fun CopyLine(label: String, value: String, tag: String, onCopy: () -> Unit, onShare: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(value, Modifier.weight(1f).testTag(tag), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
            IconButton(onClick = onCopy) { Icon(Icons.Outlined.ContentCopy, stringResource(R.string.common_copy)) }
            onShare?.let { IconButton(onClick = it) { Icon(Icons.Outlined.Share, null) } }
        }
    }
}

/** The provider form (stateless). */
@Composable
fun SsoFormContent(
    f: SsoRules.Form,
    onChange: (SsoRules.Form) -> Unit,
    hasSecret: Boolean,
    redirectUri: String,
    canEnforce: Boolean,
    error: String?,
    busy: Boolean,
    configured: Boolean,
    onCopy: (String) -> Unit,
    onSave: () -> Unit,
    onRemove: () -> Unit,
) {
    val fields = SsoRules.fields(f.preset)
    FormSection(stringResource(R.string.sso_provider_title)) {
        FormPicker(stringResource(R.string.sso_field_preset), stringResource(SsoRules.presetLabel(f.preset)), SsoRules.PRESETS.map { it to stringResource(SsoRules.presetLabel(it)) }) {
            onChange(f.copy(preset = it, requireEmailVerified = SsoRules.fields(it).requireEmailVerified))
        }
        FormHint(stringResource(fields.help))
        CopyLine(stringResource(R.string.sso_field_redirect_uri), redirectUri, "sso-redirect", onCopy = { onCopy(redirectUri) })
        FormDivider()
        if (fields.issuer) {
            FormField(stringResource(R.string.sso_field_issuer), f.issuer, { onChange(f.copy(issuer = it)) }, placeholder = fields.issuerExample, keyboard = KeyboardType.Uri)
            FormDivider()
        }
        if (fields.tenant) {
            FormField(stringResource(R.string.sso_field_tenant), f.tenant, { onChange(f.copy(tenant = it)) })
            FormDivider()
        }
        FormField(stringResource(R.string.sso_field_client_id), f.clientId, { onChange(f.copy(clientId = it)) }, placeholder = stringResource(R.string.sso_field_client_id_placeholder))
        FormDivider()
        FormField(
            stringResource(R.string.sso_field_client_secret), f.clientSecret, { onChange(f.copy(clientSecret = it)) }, secret = true,
            placeholder = stringResource(R.string.sso_field_client_secret_placeholder),
            error = stringResource(if (hasSecret) R.string.sso_field_client_secret_stored else R.string.sso_field_client_secret_hint), isError = false,
        )
        FormDivider()
        FormField(stringResource(R.string.sso_field_scopes), f.scopes, { onChange(f.copy(scopes = it)) }, error = stringResource(R.string.sso_field_scopes_hint), isError = false)
        FormDivider()
        FormField(stringResource(R.string.sso_field_email_claim), f.emailClaim, { onChange(f.copy(emailClaim = it)) }, placeholder = "email", error = stringResource(R.string.sso_field_email_claim_hint), isError = false)
    }
    FormSection(stringResource(R.string.sso_rules_title)) {
        FormSwitch(stringResource(R.string.sso_rule_enabled), stringResource(R.string.sso_rule_enabled_hint), f.enabled) { onChange(f.copy(enabled = it)) }
        FormDivider()
        FormSwitch(stringResource(R.string.sso_rule_email_verified), stringResource(R.string.sso_rule_email_verified_hint), f.requireEmailVerified) { onChange(f.copy(requireEmailVerified = it)) }
        FormDivider()
        FormSwitch(
            stringResource(R.string.sso_rule_enforce),
            stringResource(R.string.sso_rule_enforce_hint) + if (!canEnforce) " " + stringResource(R.string.sso_rule_enforce_needs_domain) else "",
            f.enforce && canEnforce,
        ) { if (canEnforce) onChange(f.copy(enforce = it)) }
        FormDivider()
        FormSwitch(stringResource(R.string.sso_rule_owner_password), stringResource(R.string.sso_rule_owner_password_hint), f.ownerPasswordAllowed) { onChange(f.copy(ownerPasswordAllowed = it)) }
        FormDivider()
        FormSwitch(stringResource(R.string.sso_rule_auto_create), stringResource(R.string.sso_rule_auto_create_hint), f.autoCreate) { onChange(f.copy(autoCreate = it)) }
        FormDivider()
        FormSwitch(stringResource(R.string.sso_rule_auto_join), stringResource(R.string.sso_rule_auto_join_hint), f.autoJoin) { onChange(f.copy(autoJoin = it)) }
        FormDivider()
        FormSwitch(stringResource(R.string.sso_rule_satisfies_2fa), stringResource(R.string.sso_rule_satisfies_2fa_hint), f.satisfies2fa) { onChange(f.copy(satisfies2fa = it)) }
    }
    error?.let { Column(Modifier.padding(16.dp)) { MessageBox(it, isError = true) } }
    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = onSave, enabled = !busy, modifier = Modifier.testTag("sso-save")) {
            if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(stringResource(R.string.sso_save))
        }
        if (configured) {
            OutlinedButton(onClick = onRemove, enabled = !busy) { Text(stringResource(R.string.sso_remove), color = MaterialTheme.colorScheme.error) }
        }
    }
}

/** The domains (stateless): each with its TXT record, state, Verify and Remove; add one. */
@Composable
fun DomainsContent(
    domains: List<TeamDomainInfo>?,
    newDomain: String,
    onNewDomain: (String) -> Unit,
    busy: Boolean,
    onAdd: () -> Unit,
    onVerify: (TeamDomainInfo) -> Unit,
    onRemove: (TeamDomainInfo) -> Unit,
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit,
) {
    SectionLabel(stringResource(R.string.sso_domains_title))
    FormHint(stringResource(R.string.sso_domains_hint))
    when {
        domains == null -> CircularProgressIndicator(Modifier.padding(20.dp).size(24.dp), strokeWidth = 2.dp)
        domains.isEmpty() -> FormNote(stringResource(R.string.sso_domains_none))
        else -> domains.forEach { d ->
            Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium) {
                Column(Modifier.padding(vertical = 8.dp)) {
                    Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(d.domain, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        when (SsoRules.domainState(d)) {
                            SsoRules.DomainState.VERIFIED -> Pill(stringResource(R.string.sso_domain_verified), Brand.Green)
                            SsoRules.DomainState.FAILED -> Pill(stringResource(R.string.sso_domain_failed, d.lastError.orEmpty()).take(40), Brand.Red)
                            SsoRules.DomainState.PENDING -> Pill(stringResource(R.string.sso_domain_pending), Brand.Amber)
                        }
                    }
                    if (!d.verified) {
                        FormHint(stringResource(R.string.sso_domain_txt_hint))
                        CopyLine(stringResource(R.string.sso_domain_txt_name), d.recordName, "txt-name-${d.domain}", onCopy = { onCopy(d.recordName) })
                        CopyLine(stringResource(R.string.sso_domain_txt_value), d.recordValue, "txt-value-${d.domain}", onCopy = { onCopy(d.recordValue) })
                        val zone = SsoRules.zoneLine(d)
                        CopyLine(stringResource(R.string.sso_domain_txt_zone), zone, "txt-zone-${d.domain}", onCopy = { onCopy(zone) }, onShare = { onShare(zone) })
                        if (!d.lastError.isNullOrBlank()) FormHint(stringResource(R.string.sso_domain_failed, d.lastError.orEmpty()))
                    }
                    Row(Modifier.padding(horizontal = 8.dp)) {
                        if (!d.verified) TextButton(onClick = { onVerify(d) }, enabled = !busy, modifier = Modifier.testTag("verify-${d.domain}")) { Text(stringResource(R.string.sso_domain_verify)) }
                        TextButton(onClick = { onRemove(d) }, enabled = !busy) {
                            Icon(Icons.Outlined.Delete, null, Modifier.size(18.dp))
                            Text(stringResource(R.string.sso_domain_remove), Modifier.padding(start = 4.dp))
                        }
                    }
                }
            }
        }
    }
    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            newDomain, { onNewDomain(it.trim().lowercase()) }, Modifier.weight(1f).testTag("new-domain"), singleLine = true,
            placeholder = { Text("example.com") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
            isError = newDomain.isNotEmpty() && !SsoRules.domainValid(newDomain),
        )
        TextButton(onClick = onAdd, enabled = !busy && SsoRules.domainValid(newDomain)) { Text(stringResource(R.string.sso_domain_add)) }
    }
}

/**
 * Teams → a team → Single sign-on (0.7 phase 5c): owners and admins set
 * the provider (Google Workspace, Microsoft Entra ID, Keycloak, Authentik,
 * any OpenID Connect provider), the redirect URI to register there, a
 * write-only client secret, the rules (enforce, owner's break-glass
 * password, accounts created and added, SSO counts as 2FA) and the domains
 * with their TXT records to copy or share; members see the summary.
 */
@Composable
fun TeamSsoScreen(app: TermoakApp, nav: NavHostController, accountId: String, teamId: String, teamName: String, myRole: String?) {
    val res = LocalResources.current
    val context = LocalContext.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val handle = remember(accountId) { app.accounts.handle(accountId) }
    val manage = AccountTeams.canManage(myRole)
    var settings by remember { mutableStateOf<SsoSettings?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var form by remember { mutableStateOf(SsoRules.Form.newFor("generic")) }
    var domains by remember { mutableStateOf<List<TeamDomainInfo>?>(null) }
    var newDomain by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var removing by remember { mutableStateOf(false) }
    var removingDomain by remember { mutableStateOf<TeamDomainInfo?>(null) }
    val server = app.accounts.account(accountId)?.serverUrl.orEmpty()
    suspend fun load() {
        val h = handle ?: return
        settings = runCatching { h.teamSso(teamId) }.getOrNull()
        settings?.let { form = SsoRules.Form.of(it) }
        if (manage) domains = runCatching { h.teamDomains(teamId) }.getOrDefault(emptyList())
        loaded = true
    }
    LaunchedEffect(teamId) { load() }
    fun copy(text: String) {
        val cm = context.getSystemService(android.content.ClipboardManager::class.java)
        cm?.setPrimaryClip(android.content.ClipData.newPlainText("termoak", text))
        scope.launch { snackbar.showSnackbar(res.getString(R.string.sso_copied)) }
    }
    fun act(f: suspend () -> String?) {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            try {
                f()?.let { snackbar.showSnackbar(it) }
                load()
            } catch (e: TermoakException) {
                error = e.userMessage(res, R.string.error_sso_failed)
                snackbar.showSnackbar(error.orEmpty())
            } finally {
                busy = false
            }
        }
    }
    ScreenScaffold(
        title = stringResource(R.string.sso_title),
        subtitle = teamName,
        navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back)) } },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            if (!E2eeBook.ssoSupported(accountId)) {
                FormNote(stringResource(R.string.sso_no_public_url))
                return@Column
            }
            if (!loaded) {
                CircularProgressIndicator(Modifier.padding(20.dp).size(24.dp), strokeWidth = 2.dp)
                return@Column
            }
            FormNote(stringResource(R.string.sso_intro))
            if (!manage) {
                val s = settings
                FormNote(
                    if (s == null || !s.enabled) stringResource(R.string.sso_member_none)
                    else stringResource(
                        R.string.sso_member_summary, stringResource(SsoRules.presetLabel(s.preset)),
                        stringResource(if (s.enforce) R.string.sso_member_enforced else R.string.sso_member_optional),
                    ),
                )
                return@Column
            }
            val state = SsoRules.teamState(settings)
            Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Pill(
                    stringResource(
                        when (state) {
                            SsoRules.TeamState.NOT_SET_UP -> R.string.sso_state_not_set_up
                            SsoRules.TeamState.OFF -> R.string.sso_state_off
                            SsoRules.TeamState.ON -> R.string.sso_state_on
                            SsoRules.TeamState.ENFORCED -> R.string.sso_state_enforced
                        },
                    ),
                    if (state == SsoRules.TeamState.ON || state == SsoRules.TeamState.ENFORCED) Brand.Green else Brand.Amber,
                )
            }
            val canEnforce = SsoRules.canEnforce(domains.orEmpty())
            SsoFormContent(
                form, { form = it }, settings?.hasClientSecret == true,
                settings?.redirectUri?.ifEmpty { null } ?: "${server.trimEnd('/')}/api/v1/auth/sso/callback",
                canEnforce, error, busy, settings != null, onCopy = { copy(it) },
                onSave = {
                    when (val c = SsoRules.check(form, settings?.hasClientSecret == true, canEnforce)) {
                        is SsoRules.Checked.Missing -> error = res.getString(c.text)
                        is SsoRules.Checked.Ok -> act {
                            handle?.setTeamSso(teamId, c.settings, c.secret)
                            form = form.copy(clientSecret = "")
                            res.getString(if (c.settings.enabled) R.string.sso_saved_on else R.string.sso_saved)
                        }
                    }
                },
                onRemove = { removing = true },
            )
            DomainsContent(
                domains, newDomain, { newDomain = it }, busy,
                onAdd = { act { handle?.addTeamDomain(teamId, newDomain.trimEnd('.')); newDomain = ""; res.getString(R.string.sso_domain_added) } },
                onVerify = { d ->
                    act {
                        val r = handle?.verifyTeamDomain(teamId, d.domain)
                        if (r?.verified == true) res.getString(R.string.sso_domain_verified_toast, d.domain)
                        else res.getString(R.string.sso_domain_not_yet, d.domain, r?.lastError.orEmpty())
                    }
                },
                onRemove = { removingDomain = it },
                onCopy = { copy(it) },
                onShare = { shareText(context, teamName, it) },
            )
        }
    }
    if (removing) {
        ConfirmDialog(
            stringResource(R.string.sso_remove_title), stringResource(R.string.sso_remove_message, teamName),
            stringResource(R.string.sso_remove), destructive = true, onDismiss = { removing = false },
        ) { act { handle?.deleteTeamSso(teamId); form = SsoRules.Form.newFor("generic"); res.getString(R.string.sso_removed) } }
    }
    removingDomain?.let { d ->
        val last = d.verified && domains.orEmpty().count { it.verified } == 1 && settings?.enforce == true
        ConfirmDialog(
            stringResource(R.string.sso_domain_remove_title, d.domain),
            stringResource(R.string.sso_domain_remove_message) + if (last) "\n\n" + stringResource(R.string.sso_domain_remove_last) else "",
            stringResource(R.string.sso_domain_remove), destructive = true, onDismiss = { removingDomain = null },
        ) { act { handle?.removeTeamDomain(teamId, d.domain); null } }
    }
}
