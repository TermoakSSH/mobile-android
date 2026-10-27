package com.termoak.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.termoak.app.R
import com.termoak.app.TermoakApp
import com.termoak.app.data.E2eeBook
import com.termoak.app.data.E2eeRules
import com.termoak.app.data.shownEmail
import com.termoak.app.userMessage
import com.termoak.ffi.E2eeMember
import com.termoak.ffi.E2eeVaultState
import com.termoak.ffi.ItemFilter
import com.termoak.ffi.TermoakException
import com.termoak.ffi.VaultInfo
import com.termoak.ffi.VaultRole
import kotlinx.coroutines.launch

/** The private networks warning (§9.3): their keys stay readable by the server. */
@Composable
fun NetworksE2eeWarning(count: Int, modifier: Modifier = Modifier) {
    if (count <= 0) return
    WarningBox(pluralStringResource(R.plurals.vault_e2ee_networks_warning, count, count), modifier)
}

@Composable
fun WarningBox(text: String, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), color = Brand.Amber.copy(alpha = 0.14f), shape = MaterialTheme.shapes.medium) {
        androidx.compose.foundation.layout.Row(Modifier.padding(12.dp)) {
            Icon(Icons.Outlined.WarningAmber, null, tint = Brand.Amber)
            Text(text, Modifier.padding(start = 10.dp), style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** A member with the fingerprint to compare (and the key-changed warning). */
@Composable
fun MemberFingerprint(m: E2eeMember, me: Boolean, checked: Boolean?, onCheck: ((Boolean) -> Unit)?, action: (@Composable () -> Unit)? = null) {
    ListItem(
        modifier = if (onCheck != null && checked != null) Modifier.clickable { onCheck(!checked) } else Modifier,
        leadingContent = if (checked != null) ({ Checkbox(checked, onCheck, enabled = onCheck != null) }) else null,
        headlineContent = { Text(if (me) stringResource(R.string.vault_e2ee_you, shownEmail(m.email)) else m.name.ifBlank { shownEmail(m.email) }) },
        supportingContent = {
            Column {
                if (!me && m.name.isNotBlank()) Text(shownEmail(m.email))
                Text(m.fingerprint, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                if (m.keyChanged) Text(stringResource(R.string.vault_e2ee_key_changed), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        trailingContent = action,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

/** What turning it on changes (and what keeps working). */
@Composable
private fun Consequences(on: Boolean) {
    FormSection(stringResource(if (on) R.string.vault_e2ee_on_consequences_title else R.string.vault_e2ee_consequences_title)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                R.string.vault_e2ee_consequence_reveal, R.string.vault_e2ee_consequence_server_sessions, R.string.vault_e2ee_consequence_jit,
                R.string.vault_e2ee_consequence_ai, R.string.vault_e2ee_consequence_moves, R.string.vault_e2ee_consequence_certificates,
                R.string.vault_e2ee_consequence_metadata,
            ).forEach { Text("• " + stringResource(it), style = MaterialTheme.typography.bodySmall) }
        }
    }
}

/**
 * A vault's Encryption page (stateless: the screenshots draw it). Off: who
 * gets the key now with their fingerprints (Admins), what changes, the
 * networks warning, Turn on. On: key version, your state, rotate when
 * someone lost access, members waiting with their fingerprints, Turn off.
 */
@Composable
fun VaultE2eeContent(
    v: VaultInfo,
    keyState: E2eeRules.KeyState,
    myFingerprint: String?,
    meId: String?,
    state: E2eeVaultState?,
    pending: List<E2eeMember>,
    networks: Int,
    selected: Set<String>,
    busy: Boolean,
    onToggle: (String, Boolean) -> Unit,
    onTurnOn: () -> Unit,
    onTurnOff: () -> Unit,
    onRotate: () -> Unit,
    onConfirm: (E2eeMember) -> Unit,
    onSetUp: () -> Unit,
    onUnlock: () -> Unit,
) {
    val manager = v.role == VaultRole.MANAGER
    val e = v.e2ee
    if (e == null) {
        FormNote(stringResource(R.string.vault_e2ee_off_intro))
        if (!manager) {
            FormNote(stringResource(R.string.vault_e2ee_managers_only))
            return
        }
        when (keyState) {
            E2eeRules.KeyState.SET_UP_FIRST -> {
                WarningBox(stringResource(R.string.vault_e2ee_setup_first))
                Button(onClick = onSetUp, Modifier.padding(16.dp)) { Text(stringResource(R.string.e2ee_setup_button)) }
                return
            }
            E2eeRules.KeyState.UNLOCK_FIRST -> {
                WarningBox(stringResource(R.string.vault_e2ee_unlock_first))
                Button(onClick = onUnlock, Modifier.padding(16.dp)) { Text(stringResource(R.string.e2ee_unlock_button)) }
                return
            }
            else -> Unit
        }
        Consequences(on = false)
        NetworksE2eeWarning(networks)
        val st = state
        if (st == null) {
            CircularProgressIndicator(Modifier.padding(20.dp).size(24.dp), strokeWidth = 2.dp)
            return
        }
        SectionLabel(stringResource(R.string.vault_e2ee_recipients_title))
        FormHint(stringResource(R.string.vault_e2ee_recipients_hint))
        st.recipients.forEach { m ->
            val me = m.userId == meId
            MemberFingerprint(m, me, checked = me || m.userId in selected, onCheck = if (me) null else ({ onToggle(m.userId, it) }))
        }
        if (st.withoutKey.isNotEmpty()) {
            FormHint(pluralStringResource(R.plurals.vault_e2ee_without_key, st.withoutKey.size, st.withoutKey.size))
        }
        if (st.items > 0u) FormHint(pluralStringResource(R.plurals.vault_e2ee_items, st.items.toInt(), st.items.toInt()))
        Button(onClick = onTurnOn, enabled = !busy, modifier = Modifier.padding(16.dp).fillMaxWidth().testTag("e2ee-turn-on")) {
            if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(stringResource(R.string.vault_e2ee_turn_on))
        }
        return
    }
    FormSection(stringResource(R.string.vaults_tab_encryption)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            E2eeBadge(E2eeRules.Badge.E2EE)
            Text(stringResource(R.string.vault_e2ee_enabled), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.vault_e2ee_key_version, e.keyVersion.toString()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            when (keyState) {
                E2eeRules.KeyState.NO_KEY_ROLE -> Text(stringResource(R.string.vault_e2ee_no_key_role), style = MaterialTheme.typography.bodySmall)
                E2eeRules.KeyState.SET_UP_FIRST -> Text(stringResource(R.string.vault_e2ee_setup_to_get_key), style = MaterialTheme.typography.bodySmall)
                E2eeRules.KeyState.WAITING -> Text(
                    stringResource(R.string.vault_e2ee_waiting_for_key, myFingerprint.orEmpty()), style = MaterialTheme.typography.bodySmall,
                )
                E2eeRules.KeyState.UNLOCK_FIRST -> Text(stringResource(R.string.vault_e2ee_unlock_first), style = MaterialTheme.typography.bodySmall)
                else -> Unit
            }
        }
    }
    when (keyState) {
        E2eeRules.KeyState.SET_UP_FIRST -> Button(onClick = onSetUp, Modifier.padding(16.dp)) { Text(stringResource(R.string.e2ee_setup_button)) }
        E2eeRules.KeyState.UNLOCK_FIRST -> Button(onClick = onUnlock, Modifier.padding(16.dp)) { Text(stringResource(R.string.e2ee_unlock_button)) }
        else -> Unit
    }
    val holder = keyState == E2eeRules.KeyState.HOLDER
    if (e.rekeyNeeded) {
        WarningBox(stringResource(R.string.vault_e2ee_rekey_needed))
        if (holder) {
            OutlinedButton(onClick = onRotate, enabled = !busy, modifier = Modifier.padding(horizontal = 16.dp).testTag("e2ee-rotate-vault")) {
                Text(stringResource(R.string.vault_e2ee_rotate))
            }
        }
    }
    if (holder && pending.isNotEmpty()) {
        SectionLabel(stringResource(R.string.vault_e2ee_pending_title))
        FormHint(stringResource(R.string.vault_e2ee_pending_hint))
        pending.forEach { m ->
            MemberFingerprint(m, false, null, null) {
                TextButton(onClick = { onConfirm(m) }, enabled = !busy, modifier = Modifier.testTag("confirm-${m.userId}")) {
                    Text(stringResource(R.string.vault_e2ee_confirm))
                }
            }
        }
    }
    Consequences(on = true)
    NetworksE2eeWarning(networks)
    if (holder && !e.rekeyNeeded) {
        OutlinedButton(onClick = onRotate, enabled = !busy, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth()) {
            Text(stringResource(R.string.vault_e2ee_rotate))
        }
    }
    if (manager && holder) {
        OutlinedButton(onClick = onTurnOff, enabled = !busy, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().testTag("e2ee-turn-off")) {
            Text(stringResource(R.string.vault_e2ee_turn_off), color = MaterialTheme.colorScheme.error)
        }
    }
}

/** Vault → Encryption (0.7 phase 5c). */
@Composable
fun VaultE2eeScreen(app: TermoakApp, nav: NavHostController, accountId: String, vaultId: String) {
    val res = LocalResources.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val vaults by app.accounts.vaults.collectAsState()
    val v = vaults.firstOrNull { it.id == vaultId && it.accountId == accountId }
    val handle = remember(accountId) { app.accounts.handle(accountId) }
    val me = app.accounts.account(accountId)?.userId
    var state by remember { mutableStateOf<E2eeVaultState?>(null) }
    var pending by remember { mutableStateOf<List<E2eeMember>>(emptyList()) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var busy by remember { mutableStateOf(false) }
    var turningOff by remember { mutableStateOf(false) }
    var settingUp by remember { mutableStateOf(false) }
    var unlocking by remember { mutableStateOf(false) }
    val st = E2eeBook.status(accountId)
    val keyState = v?.let { E2eeRules.keyState(it, st?.setUp == true, st?.unlocked == true) } ?: E2eeRules.KeyState.OFF
    val networks = remember(vaultId, vaults) {
        runCatching { app.core.listNetworks(ItemFilter(accountIds = listOf(accountId), vaultIds = listOf(vaultId), includeDevice = false)).size }.getOrDefault(0)
    }
    suspend fun load() {
        val h = handle ?: return
        val vault = app.accounts.vaults.value.firstOrNull { it.id == vaultId } ?: return
        val unlocked = E2eeBook.unlocked(accountId)
        if (vault.e2ee == null && vault.role == VaultRole.MANAGER && unlocked) {
            state = runCatching { h.e2eeVaultState(vaultId) }.getOrNull()
            selected = state?.recipients?.map { it.userId }?.toSet().orEmpty()
        }
        pending = if (vault.e2ee?.hasWrap == true && unlocked && (vault.e2ee?.pendingMembers ?: 0u) > 0u) {
            runCatching { h.e2eePendingMembers(vaultId) }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
    }
    LaunchedEffect(vaultId, v?.e2ee, st?.unlocked) { load() }
    fun act(f: suspend () -> String?) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                val msg = f()
                app.accounts.syncNow(accountId)
                load()
                msg?.let { snackbar.showSnackbar(it) }
            } catch (e: TermoakException) {
                snackbar.showSnackbar(e.userMessage(res, R.string.vault_save_failed))
            } finally {
                busy = false
            }
        }
    }
    ScreenScaffold(
        title = stringResource(R.string.vaults_tab_encryption),
        subtitle = v?.name,
        navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back)) } },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            if (v == null) {
                FormNote(stringResource(R.string.vault_gone_text))
                return@Column
            }
            VaultE2eeContent(
                v, keyState, st?.key?.fingerprint, me, state, pending, networks, selected, busy,
                onToggle = { id, on -> selected = if (on) selected + id else selected - id },
                onTurnOn = {
                    act {
                        val include = (selected + listOfNotNull(me)).toList()
                        handle?.e2eeEnableVault(vaultId, include)
                        res.getString(R.string.vault_e2ee_enabled)
                    }
                },
                onTurnOff = { turningOff = true },
                onRotate = { act { handle?.e2eeRotateVault(vaultId)?.let { res.getString(R.string.vault_e2ee_rotated, it.toString()) } } },
                onConfirm = { m ->
                    act {
                        val n = handle?.e2eeAddMembers(vaultId, listOf(m.userId))?.toInt() ?: 0
                        res.getQuantityString(R.plurals.vault_e2ee_confirmed, n, n)
                    }
                },
                onSetUp = { settingUp = true },
                onUnlock = { unlocking = true },
            )
        }
    }
    if (settingUp) PhraseFlowDialog(app, accountId, PhraseKind.SETUP) { settingUp = false }
    if (unlocking) UnlockDialog(app, accountId) { unlocking = false }
    val vault = v ?: return
    if (turningOff) {
        var typed by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { turningOff = false },
            title = { Text(stringResource(R.string.vault_e2ee_off_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.vault_e2ee_off_message))
                    OutlinedTextField(
                        typed, { typed = it }, Modifier.fillMaxWidth().testTag("e2ee-off-name"), singleLine = true,
                        label = { Text(stringResource(R.string.vault_e2ee_off_type, vault.name)) },
                        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                        isError = typed.isNotEmpty() && typed.trim() != vault.name,
                    )
                }
            },
            confirmButton = {
                TextButton(enabled = typed.trim() == vault.name && !busy, onClick = {
                    turningOff = false
                    act {
                        handle?.e2eeDisableVault(vaultId)
                        res.getString(R.string.vault_e2ee_disabled)
                    }
                }) { Text(stringResource(R.string.vault_e2ee_turn_off), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { turningOff = false }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
}
