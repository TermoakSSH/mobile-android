package com.termoak.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.LockClock
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.termoak.app.R
import com.termoak.app.TermoakApp
import com.termoak.app.data.AccessBook
import com.termoak.app.data.AccessRules
import com.termoak.app.data.AccessTexts
import com.termoak.app.data.isTelnet
import com.termoak.app.userMessage
import com.termoak.ffi.AccessRequestInfo
import com.termoak.ffi.AccessTargetKind
import com.termoak.ffi.HostGroup
import com.termoak.ffi.ItemFilter
import com.termoak.ffi.SshHost
import com.termoak.ffi.TermoakException
import com.termoak.ffi.VaultInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Teams security helpers the screens share (0.7 phase 4d): a host's gates
 * and authentication, where its terminal opens, the access chips of the
 * host rows, the countdown, the 2FA banner and the Request access dialog.
 */
object AccessUi {
    /** The vault of an item of an account (`null`: This device, or not synced). */
    fun vaultOf(app: TermoakApp, accountId: String?, vaultId: String?): VaultInfo? =
        if (accountId == null) null else app.accounts.vaults.value.firstOrNull { it.id == vaultId && it.accountId == accountId }

    /** What the vault's policy lets you do with its items (everything for This device's). */
    fun gates(app: TermoakApp, accountId: String?, vaultId: String?): AccessRules.Gates {
        val v = vaultOf(app, accountId, vaultId) ?: return AccessRules.Gates.ALL
        return AccessRules.gates(v.role, v.policy)
    }

    fun gates(app: TermoakApp, host: SshHost): AccessRules.Gates = gates(app, host.accountId, host.vaultId)

    /** The groups of an item's vault, as the inheritance sees them. */
    fun groupAuths(groups: List<HostGroup>, accountId: String?, vaultId: String?): List<AccessRules.GroupAuth> =
        groups.filter { it.accountId == accountId && it.vaultId == vaultId }.map { AccessRules.GroupAuth(it.id, it.name, it.parentId, it.settings.auth) }

    /** The groups of the account read from the store (when the caller has none). */
    fun groupsOf(app: TermoakApp, accountId: String?): List<HostGroup> =
        if (accountId == null) emptyList()
        else runCatching { app.core.listGroups(ItemFilter(accountIds = listOf(accountId), vaultIds = null, includeDevice = false)) }.getOrDefault(emptyList())

    /** The effective authentication of a host. */
    fun hostAuth(app: TermoakApp, host: SshHost, groups: List<HostGroup>? = null): AccessRules.EffectiveAuth =
        AccessRules.effectiveAuth(host.settings.auth, host.groupId, groupAuths(groups ?: groupsOf(app, host.accountId), host.accountId, host.vaultId))

    /** "Team certificate" applies to the host, its vault's owner has a CA and its server has teams security. */
    fun teamCertificateUsable(app: TermoakApp, host: SshHost, groups: List<HostGroup>? = null): Boolean =
        host.accountId != null && AccessBook.supported(host.accountId) && gates(app, host).certificates &&
            hostAuth(app, host, groups).teamCertificate

    /** Where a terminal (or the files) of [host] opens: from the phone, or through the server. */
    fun route(app: TermoakApp, host: SshHost): AccessRules.Route {
        val strict = vaultOf(app, host.accountId, host.vaultId)?.strict == true
        val noSecrets = AccessRules.noSecrets(host.access)
        return AccessRules.route(host.access, strict, host.isTelnet, noSecrets && teamCertificateUsable(app, host))
    }
}

/** The current time, ticking every second near [endsAt] (the last two minutes), else every 15 s. */
@Composable
fun rememberNow(endsAt: Long?): Long {
    var now by remember { mutableLongStateOf(AccessBook.now()) }
    LaunchedEffect(endsAt) {
        while (true) {
            now = AccessBook.now()
            val left = endsAt?.let { AccessRules.remainingSecs(it, now) } ?: return@LaunchedEffect
            if (left <= 0) return@LaunchedEffect
            delay(AccessRules.tickMs(left))
        }
    }
    return now
}

/** The current time, ticking every [periodMs] (lists with several countdowns). */
@Composable
fun rememberClock(periodMs: Long): Long {
    var now by remember { mutableLongStateOf(AccessBook.now()) }
    LaunchedEffect(periodMs) {
        while (true) {
            now = AccessBook.now()
            delay(periodMs)
        }
    }
    return now
}

/** "Until 15:42 · 42 min", "Waiting for approval", "Approval needed": a host's access window. */
@Composable
fun AccessWindowChip(window: AccessBook.HostWindow, modifier: Modifier = Modifier) {
    val res = LocalResources.current
    when (window) {
        is AccessBook.HostWindow.Open -> {
            val now = rememberNow(window.endsAt)
            val left = AccessRules.remainingSecs(window.endsAt, now)
            Pill(
                stringResource(R.string.access_chip_open, AccessTexts.clock(window.endsAt), AccessTexts.left(res, left)),
                if (AccessRules.endingSoon(left)) Brand.Amber else Brand.Green, modifier,
            )
        }
        AccessBook.HostWindow.Pending -> Pill(stringResource(R.string.access_chip_pending), Brand.Amber, modifier)
        AccessBook.HostWindow.Needed -> Pill(stringResource(R.string.access_chip_needed), MaterialTheme.colorScheme.onSurfaceVariant, modifier)
    }
}

/** "Recorded": the team records the sessions on this host. */
@Composable
fun RecordedChip(modifier: Modifier = Modifier) {
    Pill("● " + stringResource(R.string.access_recorded), Brand.Red, modifier)
}

/** The host's access window, "Recorded" and "Connect only" chips for its row (nothing when none applies). */
@Composable
fun HostAccessChips(app: TermoakApp, host: SshHost) {
    val w = AccessBook.windowOf(host.accountId, host.id)
    val gates = AccessUi.gates(app, host)
    val recorded = gates.recorded || AccessBook.host(host.accountId, host.id)?.recordRequired == true
    w?.let { AccessWindowChip(it) }
    if (recorded) RecordedChip()
    if (host.access == com.termoak.ffi.ItemAccess.CONNECT_ONLY) ConnectOnlyBadge()
    // End-to-end encrypted and still sealed on this device (0.7 phase 5c).
    if (host.e2eeLocked) E2eeLockedChip()
}

/** Whether [HostAccessChips] has something to show. */
fun hasAccessChips(app: TermoakApp, host: SshHost): Boolean =
    AccessBook.windowOf(host.accountId, host.id) != null || AccessUi.gates(app, host).recorded ||
        AccessBook.host(host.accountId, host.id)?.recordRequired == true || host.access == com.termoak.ffi.ItemAccess.CONNECT_ONLY ||
        host.e2eeLocked

/**
 * The teams that require two-step verification from you (`team_2fa` of
 * `/me`): before the deadline, "Turn it on before…"; after it, their vaults
 * are hidden. With a link to set it up.
 */
@Composable
fun Team2faBanner(app: TermoakApp, nav: NavHostController, modifier: Modifier = Modifier) {
    val all = AccessBook.team2faAll()
    if (all.isEmpty()) return
    val blocked = all.filter { it.second.blocked }
    val shown = blocked.ifEmpty { all }
    val teams = shown.joinToString(", ") { it.second.teamName }
    val account = shown.first().first
    Team2faCard(
        title = stringResource(if (blocked.isNotEmpty()) R.string.team_2fa_blocked_title else R.string.team_2fa_title, teams),
        detail = if (blocked.isNotEmpty()) stringResource(R.string.team_2fa_blocked_detail)
        else stringResource(R.string.team_2fa_detail, AccessTexts.date(shown.minOf { it.second.deadline })),
        blocked = blocked.isNotEmpty(),
        modifier = modifier,
    ) { nav.navigate(Routes.twoFactor(account)) }
}

@Composable
fun Team2faCard(title: String, detail: String, blocked: Boolean, modifier: Modifier = Modifier, onSetup: () -> Unit) {
    val color = if (blocked) MaterialTheme.colorScheme.error else Brand.Amber
    Surface(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        color = color.copy(alpha = 0.12f), shape = MaterialTheme.shapes.medium,
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Outlined.Security, null, tint = color)
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = onSetup, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                    Text(stringResource(R.string.team_2fa_setup))
                }
            }
        }
    }
}

/**
 * An AlertDialog, or the same title, text and buttons drawn in place
 * ([inline]: the screenshot tests, where dialog windows can't be captured).
 */
@Composable
fun MaybeInlineDialog(
    inline: Boolean,
    onDismissRequest: () -> Unit,
    icon: (@Composable () -> Unit)? = null,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable () -> Unit,
) {
    if (!inline) {
        AlertDialog(onDismissRequest = onDismissRequest, icon = icon, title = title, text = text, confirmButton = confirmButton, dismissButton = dismissButton)
        return
    }
    Surface(Modifier.padding(24.dp), shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            icon?.let { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { it() } }
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalTextStyle provides MaterialTheme.typography.headlineSmall) { title() }
            text()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                dismissButton()
                confirmButton()
            }
        }
    }
}

/** A target for a request: this host, its group or its vault (what the policy covers). */
data class RequestTarget(val kind: AccessTargetKind, val id: String, val label: String)

/** The targets the Request access dialog offers for a need: the host, and what its policy is set on. */
fun requestTargets(need: AccessRules.AccessNeed, hostLabel: String, groupName: String?, vaultName: String?): List<RequestTarget> {
    val list = mutableListOf<RequestTarget>()
    need.hostId?.let { list += RequestTarget(AccessTargetKind.HOST, it, hostLabel) }
    val t = need.targetId
    if (t != null && need.targetKind == AccessTargetKind.GROUP) list += RequestTarget(AccessTargetKind.GROUP, t, groupName ?: "")
    if (t != null && need.targetKind == AccessTargetKind.VAULT) list += RequestTarget(AccessTargetKind.VAULT, t, vaultName ?: "")
    return list
}

/**
 * "Request access to web1": how long (up to the policy's maximum), why, and
 * for what (only this host, or every host its policy covers). Sending it
 * makes the request; then the caller shows it waiting.
 */
@Composable
fun RequestAccessDialog(
    hostLabel: String,
    need: AccessRules.AccessNeed,
    targets: List<RequestTarget>,
    onDismiss: () -> Unit,
    inline: Boolean = false,
    onSend: suspend (RequestTarget, Int, String) -> Unit,
) {
    val res = LocalResources.current
    val scope = rememberCoroutineScope()
    val choices = remember(need.maxMinutes) { AccessRules.durationChoices(need.maxMinutes) }
    var minutes by remember { mutableStateOf(choices.firstOrNull { it >= 60 } ?: choices.last()) }
    var reason by remember { mutableStateOf("") }
    var target by remember { mutableStateOf(targets.firstOrNull()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val problem = AccessRules.requestProblem(minutes, need.maxMinutes, reason)
    MaybeInlineDialog(
        inline = inline,
        onDismissRequest = { if (!busy) onDismiss() },
        icon = { Icon(Icons.Outlined.LockClock, null) },
        title = { Text(stringResource(R.string.access_request_title, hostLabel)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.access_request_message, AccessTexts.duration(res, need.maxMinutes)), style = MaterialTheme.typography.bodyMedium)
                if (!need.canRequest) {
                    Text(stringResource(R.string.access_request_cannot), color = MaterialTheme.colorScheme.error)
                }
                Picker(
                    stringResource(R.string.access_request_duration), AccessTexts.duration(res, minutes), null,
                    choices.map { it to AccessTexts.duration(res, it) },
                ) { minutes = it }
                OutlinedTextField(
                    reason, { reason = it.take(600) }, Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.access_request_reason)) },
                    placeholder = { Text(stringResource(R.string.access_request_reason_placeholder)) },
                    minLines = 2, isError = problem == AccessRules.RequestProblem.REASON_LONG,
                    supportingText = if (problem == AccessRules.RequestProblem.REASON_LONG) ({ Text(stringResource(R.string.access_request_error_reason_long)) }) else null,
                )
                if (targets.size > 1) {
                    Text(stringResource(R.string.access_request_scope), style = MaterialTheme.typography.titleSmall)
                    targets.forEach { t ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { target = t },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(target == t, { target = t })
                            Text(
                                when (t.kind) {
                                    AccessTargetKind.GROUP -> stringResource(R.string.access_request_scope_group)
                                    AccessTargetKind.VAULT -> stringResource(R.string.access_request_scope_vault)
                                    else -> stringResource(R.string.access_request_scope_host, t.label)
                                },
                                maxLines = 2, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                Text(stringResource(R.string.access_request_after), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && problem == null && target != null && need.canRequest, onClick = {
                val t = target ?: return@TextButton
                busy = true
                error = null
                scope.launch {
                    try {
                        onSend(t, minutes, reason.trim())
                    } catch (e: TermoakException) {
                        error = e.userMessage(res, R.string.access_check_failed)
                    } finally {
                        busy = false
                    }
                }
            }) { Text(stringResource(R.string.access_request_send)) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.common_cancel)) } },
    )
}

/**
 * Asks for access for [host] of [app]: the request through the account's
 * handle, then the host asked again (it waits now). Returns the request.
 */
suspend fun sendAccessRequest(app: TermoakApp, accountId: String, hostId: String?, target: RequestTarget, minutes: Int, reason: String): AccessRequestInfo {
    val h = app.accounts.handle(accountId) ?: throw TermoakException.NotLoggedIn("")
    val r = h.createAccessRequest(target.kind, target.id, minutes.toUInt(), reason)
    AccessBook.remember(accountId, r, mineToo = true)
    hostId?.let { AccessBook.ask(accountId, it, force = true) }
    return r
}

/** The labels of a need's group and vault, from the stores. */
fun needLabels(app: TermoakApp, accountId: String?, need: AccessRules.AccessNeed): Pair<String?, String?> {
    val group = if (need.targetKind == AccessTargetKind.GROUP && need.targetId != null) {
        AccessUi.groupsOf(app, accountId).firstOrNull { it.id == need.targetId }?.name
    } else null
    val vault = AccessUi.vaultOf(app, accountId, need.vaultId ?: need.targetId.takeIf { need.targetKind == AccessTargetKind.VAULT })?.name
    return group to vault
}

/** The Request access dialog for a host of the lists (its menu, or after an error). */
@Composable
fun RequestAccessFor(app: TermoakApp, host: SshHost, need: AccessRules.AccessNeed, onDismiss: () -> Unit, onSent: (AccessRequestInfo) -> Unit) {
    val accountId = host.accountId ?: return
    val (group, vault) = remember(need) { needLabels(app, accountId, need) }
    RequestAccessDialog(host.label, need, requestTargets(need, host.label, group, vault), onDismiss) { t, m, r ->
        onSent(sendAccessRequest(app, accountId, host.id, t, m, r))
    }
}

/** A small "pending" line with a timer icon (request waiting). */
@Composable
fun WaitingLine(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.HourglassTop, null, Modifier.size(16.dp), tint = Brand.Amber)
        Text(text, Modifier.padding(start = 6.dp), style = MaterialTheme.typography.bodySmall)
    }
}

/** A timer icon line (window open). */
@Composable
fun WindowLine(text: String, warn: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Timer, null, Modifier.size(16.dp), tint = if (warn) Brand.Amber else Brand.Green)
        Text(text, Modifier.padding(start = 6.dp), style = MaterialTheme.typography.bodySmall)
    }
}

/**
 * "Authentication" of a host or group (teams security): the group's (or
 * password and key), or "Team certificate" (a short-lived certificate from
 * the CA of the vault's team or owner). A later version's value is kept as
 * it is. Says what is inherited and warns when the vault's owner has no CA.
 */
@Composable
fun AuthField(
    own: String?,
    inherited: AccessRules.EffectiveAuth,
    certificates: Boolean,
    enabled: Boolean,
    outlined: Boolean,
    onChange: (String?) -> Unit,
) {
    val inheritText = stringResource(R.string.auth_field_inherit)
    val teamText = stringResource(R.string.auth_field_team_certificate)
    val options = buildList<Pair<String?, String>> {
        add(null to inheritText)
        add(AccessRules.TEAM_CERTIFICATE to teamText)
        if (own != null && own != AccessRules.TEAM_CERTIFICATE) add(own to stringResource(R.string.auth_field_other, own))
    }
    val selected = options.firstOrNull { it.first == own }?.second ?: inheritText
    if (outlined) {
        if (enabled) Picker(stringResource(R.string.auth_field_label), selected, null, options) { onChange(it) }
        else Text(stringResource(R.string.auth_field_label) + ": " + selected, style = MaterialTheme.typography.bodyMedium)
    } else {
        if (enabled) FormPicker(stringResource(R.string.auth_field_label), selected, options) { onChange(it) }
        else FormNote(selected)
    }
    val hint = when {
        own == AccessRules.TEAM_CERTIFICATE -> stringResource(R.string.auth_field_team_hint)
        own != null -> stringResource(R.string.auth_field_other_hint)
        inherited.teamCertificate && inherited.fromGroup != null -> stringResource(R.string.auth_field_inherited, inherited.fromGroup)
        else -> stringResource(R.string.auth_field_keys_hint)
    }
    val usesTeam = (own ?: inherited.auth) == AccessRules.TEAM_CERTIFICATE
    Column(if (outlined) Modifier else Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (usesTeam && !certificates) {
            Text(stringResource(R.string.auth_field_no_ca), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}
