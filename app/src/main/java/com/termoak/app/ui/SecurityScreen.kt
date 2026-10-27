package com.termoak.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.PhonelinkLock
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.termoak.app.R
import com.termoak.app.TermoakApp
import com.termoak.app.asString
import com.termoak.app.data.AccessTexts
import com.termoak.app.data.E2eeBook
import com.termoak.app.data.E2eeRules
import com.termoak.app.data.E2eeTexts
import com.termoak.app.data.SsoBrowser
import com.termoak.app.data.SsoRules
import com.termoak.app.data.displayName
import com.termoak.app.userMessage
import com.termoak.ffi.AccountStatus
import com.termoak.ffi.E2eePairingRequest
import com.termoak.ffi.E2eeStatus
import com.termoak.ffi.HttpMethod
import com.termoak.ffi.TermoakException
import kotlinx.coroutines.launch
import org.json.JSONArray

/** A linked identity (`GET /me/sso`). */
data class LinkedIdentity(val teamId: String, val teamName: String?, val issuer: String, val email: String, val lastLoginAt: Long?) {
    companion object {
        fun parse(json: String): List<LinkedIdentity> = runCatching {
            val a = JSONArray(json)
            (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                LinkedIdentity(
                    o.optString("team_id"), o.optString("team_name").takeIf { it.isNotBlank() && !o.isNull("team_name") },
                    o.optString("issuer"), o.optString("email"), if (o.isNull("last_login_at")) null else o.optLong("last_login_at"),
                )
            }
        }.getOrDefault(emptyList())
    }
}

/** A team of yours whose single sign-on you can link (id, name). */
data class LinkableTeam(val id: String, val name: String)

/**
 * Settings → Security (0.7 phase 5c): end-to-end encryption of the account
 * (set up, unlock on this device, lock, passphrase, a new recovery phrase,
 * replace the key, reset; pairing requests of your other devices), linked
 * identities of your teams' single sign-on, and your activity.
 */
@Composable
fun SecurityScreen(app: TermoakApp, nav: NavHostController, accountIdArg: String?, pairingArg: String?) {
    val res = LocalResources.current
    val context = LocalContext.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val deviceAuth = rememberDeviceAuth()
    val list by app.accounts.list.collectAsState()
    val accounts = list.filter { it.status == AccountStatus.ACTIVE && E2eeBook.features(it.id).let { f -> f != null && (f.e2ee || f.sso || f.audit != null) } }
    var accountId by remember { mutableStateOf(accountIdArg ?: app.accounts.current.value?.id) }
    val id = accounts.firstOrNull { it.id == accountId }?.id ?: accounts.firstOrNull()?.id
    var phraseFlow by remember { mutableStateOf<PhraseKind?>(null) }
    var unlocking by remember { mutableStateOf(false) }
    var passphraseDialog by remember { mutableStateOf<Boolean?>(null) }
    var removingPassphrase by remember { mutableStateOf(false) }
    var newRecoveryAsk by remember { mutableStateOf(false) }
    var rotateAsk by remember { mutableStateOf(false) }
    var resetting by remember { mutableStateOf(false) }
    var reviewing by remember { mutableStateOf<E2eePairingRequest?>(null) }
    var identities by remember { mutableStateOf<List<LinkedIdentity>?>(null) }
    var linkable by remember { mutableStateOf<List<LinkableTeam>>(emptyList()) }
    var unlinking by remember { mutableStateOf<LinkedIdentity?>(null) }
    var linkingTeam by remember { mutableStateOf<String?>(null) }

    suspend fun loadIdentities(acc: String) {
        val h = app.accounts.handle(acc) ?: return
        if (!E2eeBook.ssoSupported(acc)) {
            identities = null
            return
        }
        val ids = runCatching { LinkedIdentity.parse(h.apiRequest(HttpMethod.GET, "/api/v1/me/sso", null)) }.getOrDefault(emptyList())
        identities = ids
        linkable = runCatching {
            h.listTeams().mapNotNull { t ->
                val s = runCatching { h.teamSso(t.id) }.getOrNull()
                if (s?.enabled == true && ids.none { it.teamId == t.id }) LinkableTeam(t.id, t.name) else null
            }
        }.getOrDefault(emptyList())
    }
    LaunchedEffect(id) {
        val acc = id ?: return@LaunchedEffect
        if (E2eeBook.supported(acc)) E2eeBook.refresh(acc)
        loadIdentities(acc)
    }
    // A pairing request named by a notification: its dialog.
    LaunchedEffect(pairingArg, id) {
        val acc = id ?: return@LaunchedEffect
        val p = pairingArg ?: return@LaunchedEffect
        E2eeBook.refresh(acc)
        reviewing = E2eeBook.pairings(acc).firstOrNull { it.id == p }
    }
    // A link comes back from the browser.
    LaunchedEffect(Unit) {
        SsoBrowser.callbacks.collect { url ->
            val p = SsoBrowser.pending.value as? SsoBrowser.Pending.Link ?: return@collect
            SsoBrowser.clear()
            linkingTeam = null
            when (val r = SsoRules.linkResult(url, p.state)) {
                SsoRules.LinkResult.Linked -> snackbar.showSnackbar(res.getString(R.string.sso_linked))
                is SsoRules.LinkResult.Failed -> snackbar.showSnackbar(
                    (E2eeTexts.error(r.code) ?: com.termoak.app.UiText.Res(R.string.error_sso_failed)).resolve(res),
                )
            }
            loadIdentities(p.accountId)
        }
    }
    fun link(acc: String, team: LinkableTeam) {
        val h = app.accounts.handle(acc) ?: return
        linkingTeam = team.id
        scope.launch {
            try {
                val state = SsoRules.token()
                val verifier = SsoRules.token()
                val body = SsoRules.linkBody(team.id, state, verifier, android.os.Build.MODEL ?: "Android")
                val v = org.json.JSONObject(h.apiRequest(HttpMethod.POST, "/api/v1/me/sso/link", body))
                SsoBrowser.begin(SsoBrowser.Pending.Link(acc, team.id, state))
                if (!SsoBrowser.open(context, v.getString("authorize_url"))) {
                    SsoBrowser.clear()
                    linkingTeam = null
                }
            } catch (e: Exception) {
                linkingTeam = null
                snackbar.showSnackbar(e.userMessage(res, R.string.error_sso_failed))
            }
        }
    }
    fun gated(then: () -> Unit) = deviceAuth.unlock(res.getString(R.string.e2ee_auth_reason)) { ok -> if (ok) then() }

    ScreenScaffold(
        title = stringResource(R.string.settings_page_security),
        subtitle = id?.let { accountEmail(app, it) },
        navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back)) } },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            if (id == null) {
                FormNote(stringResource(R.string.settings_security_signed_out_hint))
                return@Column
            }
            if (accounts.size > 1) {
                FormSection(stringResource(R.string.settings_account)) {
                    FormPicker(stringResource(R.string.settings_account), accounts.first { it.id == id }.displayName, accounts.map { it.id to it.displayName }) { accountId = it }
                }
            }
            if (E2eeBook.supported(id)) {
                val st = E2eeBook.status(id)
                E2eeSectionContent(
                    st, E2eeBook.pairings(id), E2eeBook.waiting(id), System.currentTimeMillis(),
                    onSetup = { phraseFlow = PhraseKind.SETUP },
                    onUnlock = { unlocking = true },
                    onLock = {
                        scope.launch {
                            try {
                                app.accounts.handle(id)?.e2eeLock()
                                E2eeBook.refresh(id)
                                app.accounts.itemsChangedHere()
                                snackbar.showSnackbar(res.getString(R.string.e2ee_locked_toast))
                            } catch (e: TermoakException) {
                                snackbar.showSnackbar(e.userMessage(res, R.string.e2ee_title))
                            }
                        }
                    },
                    onPassphrase = { passphraseDialog = st?.hasPassphrase == true },
                    onRemovePassphrase = { removingPassphrase = true },
                    onNewRecovery = { newRecoveryAsk = true },
                    onRotate = { rotateAsk = true },
                    onReset = { gated { resetting = true } },
                    onReview = { reviewing = it },
                )
            } else {
                FormSection(stringResource(R.string.e2ee_title)) { FormNote(stringResource(R.string.settings_security_no_e2ee)) }
            }
            if (E2eeBook.ssoSupported(id)) {
                IdentitiesContent(identities, linkable, linkingTeam, onUnlink = { unlinking = it }, onLink = { link(id, it) })
            }
            if (E2eeBook.audit(id) != null) {
                SectionLabel(stringResource(R.string.settings_page_activity))
                ListItem(
                    modifier = Modifier.clickable { nav.navigate(Routes.audit(id, "me", id, null)) },
                    headlineContent = { Text(stringResource(R.string.audit_account_title)) },
                    supportingContent = { Text(stringResource(R.string.settings_subtitle_activity)) },
                    leadingContent = { Icon(Icons.Outlined.History, null) },
                    trailingContent = { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }
    }

    val acc = id ?: return
    phraseFlow?.let { kind -> PhraseFlowDialog(app, acc, kind) { phraseFlow = null; scope.launch { E2eeBook.refresh(acc) } } }
    if (unlocking) UnlockDialog(app, acc) { unlocking = false }
    passphraseDialog?.let { change -> PassphraseDialog(app, acc, change) { passphraseDialog = null } }
    reviewing?.let { p -> PairingDialog(app, acc, p) { reviewing = null } }
    if (resetting) ResetDialog(app, acc) { resetting = false }
    if (removingPassphrase) {
        ConfirmDialog(
            stringResource(R.string.e2ee_passphrase_remove_title), stringResource(R.string.e2ee_passphrase_remove_message),
            stringResource(R.string.e2ee_passphrase_remove), destructive = true, onDismiss = { removingPassphrase = false },
        ) {
            scope.launch {
                try {
                    app.accounts.handle(acc)?.e2eeRemovePassphrase()
                    E2eeBook.refresh(acc)
                    snackbar.showSnackbar(res.getString(R.string.e2ee_passphrase_removed))
                } catch (e: TermoakException) {
                    snackbar.showSnackbar(e.userMessage(res, R.string.e2ee_title))
                }
            }
        }
    }
    if (newRecoveryAsk) {
        ConfirmDialog(
            stringResource(R.string.e2ee_recovery_new_title), stringResource(R.string.e2ee_recovery_new_message),
            stringResource(R.string.e2ee_recovery_new_confirm), onDismiss = { newRecoveryAsk = false },
        ) { gated { phraseFlow = PhraseKind.NEW_RECOVERY } }
    }
    if (rotateAsk) {
        ConfirmDialog(
            stringResource(R.string.e2ee_rotate_title), stringResource(R.string.e2ee_rotate_message),
            stringResource(R.string.e2ee_rotate_confirm), destructive = true, onDismiss = { rotateAsk = false },
        ) { gated { phraseFlow = PhraseKind.ROTATE } }
    }
    unlinking?.let { i ->
        ConfirmDialog(
            stringResource(R.string.sso_unlink_title), stringResource(R.string.sso_unlink_message, i.teamName ?: i.issuer),
            stringResource(R.string.sso_unlink), destructive = true, onDismiss = { unlinking = null },
        ) {
            scope.launch {
                try {
                    app.accounts.handle(acc)?.apiRequest(HttpMethod.DELETE, "/api/v1/me/sso/${i.teamId}", null)
                    snackbar.showSnackbar(res.getString(R.string.sso_unlinked))
                } catch (e: TermoakException) {
                    snackbar.showSnackbar(e.userMessage(res, R.string.error_sso_failed))
                }
                loadIdentities(acc)
            }
        }
    }
}

/** The E2EE part of Security (stateless: the screenshots draw it with made-up data). */
@Composable
fun E2eeSectionContent(
    st: E2eeStatus?,
    pairings: List<E2eePairingRequest>,
    waiting: Long,
    now: Long,
    onSetup: () -> Unit,
    onUnlock: () -> Unit,
    onLock: () -> Unit,
    onPassphrase: () -> Unit,
    onRemovePassphrase: () -> Unit,
    onNewRecovery: () -> Unit,
    onRotate: () -> Unit,
    onReset: () -> Unit,
    onReview: (E2eePairingRequest) -> Unit,
) {
    SectionLabel(stringResource(R.string.e2ee_title))
    Text(stringResource(R.string.e2ee_intro), Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.bodyMedium)
    FormHint(stringResource(R.string.e2ee_intro_secrets_only))
    if (st == null) {
        CircularProgressIndicator(Modifier.padding(20.dp).size(24.dp), strokeWidth = 2.dp)
        return
    }
    if (!st.setUp) {
        Button(onClick = onSetup, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().testTag("e2ee-setup")) {
            Text(stringResource(R.string.e2ee_setup_button))
        }
        return
    }
    FormSection(stringResource(R.string.e2ee_fingerprint)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(st.key?.fingerprint.orEmpty(), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("e2ee-fingerprint"))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (st.unlocked) Pill(stringResource(R.string.e2ee_state_unlocked), Brand.Green) else Pill(stringResource(R.string.e2ee_state_locked), Brand.Amber)
            }
            Text(
                stringResource(if (st.unlocked) R.string.e2ee_state_unlocked_hint else R.string.e2ee_state_locked_hint),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(
                    when {
                        st.hasPassphrase && st.hasRecoveryKey -> R.string.e2ee_methods_both
                        st.hasPassphrase -> R.string.e2ee_methods_passphrase
                        st.hasRecoveryKey -> R.string.e2ee_methods_recovery
                        else -> R.string.e2ee_methods_none
                    },
                ),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (st.lockedSecrets > 0u) {
                val n = st.lockedSecrets.toInt()
                Text(pluralStringResource(R.plurals.e2ee_locked_secrets, n, n), style = MaterialTheme.typography.bodySmall, color = Brand.Amber)
            }
            if (waiting > 0) {
                Text(pluralStringResource(R.plurals.e2ee_notice_waiting, waiting.toInt(), waiting.toInt()), style = MaterialTheme.typography.bodySmall, color = Brand.Amber)
            }
        }
    }
    if (pairings.isNotEmpty()) {
        SectionLabel(stringResource(R.string.e2ee_pairing_waiting_title))
        pairings.forEach { p ->
            ListItem(
                modifier = Modifier.clickable { onReview(p) }.testTag("pairing-${p.id}"),
                headlineContent = { Text(p.deviceName) },
                supportingContent = { Text(stringResource(R.string.e2ee_pairing_expires, E2eeRules.pairingLeft(p.expiresAt, now))) },
                leadingContent = { Icon(Icons.Outlined.PhonelinkLock, null, tint = Brand.Amber) },
                trailingContent = { TextButton(onClick = { onReview(p) }) { Text(stringResource(R.string.e2ee_pairing_review)) } },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }
    }
    FormSection(stringResource(R.string.settings_page_security)) {
        if (!st.unlocked) {
            SecurityRow(Icons.Outlined.LockOpen, stringResource(R.string.e2ee_unlock_button), "e2ee-unlock", onUnlock)
        } else {
            SecurityRow(Icons.Outlined.Lock, stringResource(R.string.e2ee_lock), "e2ee-lock", onLock)
            FormDivider()
            SecurityRow(Icons.Outlined.Password, stringResource(if (st.hasPassphrase) R.string.e2ee_passphrase_change else R.string.e2ee_passphrase_set), "e2ee-passphrase", onPassphrase)
            if (st.hasPassphrase) {
                FormDivider()
                SecurityRow(Icons.Outlined.Password, stringResource(R.string.e2ee_passphrase_remove), "e2ee-passphrase-remove", onRemovePassphrase)
            }
            FormDivider()
            SecurityRow(Icons.Outlined.Key, stringResource(R.string.e2ee_recovery_new), "e2ee-new-recovery", onNewRecovery)
            FormDivider()
            SecurityRow(Icons.Outlined.Sync, stringResource(R.string.e2ee_rotate_button), "e2ee-rotate", onRotate)
        }
        FormDivider()
        SecurityRow(Icons.Outlined.RestartAlt, stringResource(R.string.e2ee_reset_button), "e2ee-reset", onReset, danger = true)
    }
}

@Composable
private fun SecurityRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, tag: String, onClick: () -> Unit, danger: Boolean = false) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick).testTag(tag),
        headlineContent = { Text(title, color = if (danger) MaterialTheme.colorScheme.error else Color.Unspecified) },
        leadingContent = { Icon(icon, null, tint = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

/** Linked identities (stateless). */
@Composable
fun IdentitiesContent(
    identities: List<LinkedIdentity>?,
    linkable: List<LinkableTeam>,
    linkingTeam: String?,
    onUnlink: (LinkedIdentity) -> Unit,
    onLink: (LinkableTeam) -> Unit,
) {
    SectionLabel(stringResource(R.string.sso_identities_title))
    FormHint(stringResource(R.string.sso_identities_hint))
    when {
        identities == null -> CircularProgressIndicator(Modifier.padding(20.dp).size(24.dp), strokeWidth = 2.dp)
        identities.isEmpty() -> FormNote(stringResource(R.string.sso_identities_none))
        else -> identities.forEach { i ->
            ListItem(
                headlineContent = { Text(i.teamName ?: i.issuer) },
                supportingContent = {
                    Text(
                        i.email + " · " + (i.lastLoginAt?.let { stringResource(R.string.sso_identities_last_login, i.issuer, AccessTexts.dateTime(it)) } ?: i.issuer),
                    )
                },
                leadingContent = { Icon(Icons.Outlined.Link, null, tint = Brand.Green) },
                trailingContent = { IconButton(onClick = { onUnlink(i) }) { Icon(Icons.Outlined.LinkOff, stringResource(R.string.sso_unlink)) } },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }
    }
    linkable.forEach { t ->
        OutlinedButton(
            onClick = { onLink(t) }, enabled = linkingTeam == null,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().testTag("sso-link-${t.id}"),
        ) {
            if (linkingTeam == t.id) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            else Text(stringResource(R.string.sso_link_with, t.name))
        }
    }
}
