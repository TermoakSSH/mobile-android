package com.termoak.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.termoak.app.R
import com.termoak.app.TermoakApp
import com.termoak.app.term.LocalTerminal
import com.termoak.app.term.ServerTerminal
import com.termoak.app.term.TermSession
import com.termoak.app.term.TermState
import com.termoak.ffi.SessionShareInfo
import com.termoak.ffi.ShareChanges
import com.termoak.ffi.ShareInvite
import com.termoak.ffi.ShareKind
import com.termoak.ffi.ShareOptions
import com.termoak.ffi.ShareTarget
import com.termoak.ffi.Team
import com.termoak.ffi.TermoakCore
import kotlinx.coroutines.launch

/** Where a share sheet sends its invitations: a server session or a local terminal shared through the server. */
interface ShareBackend {
    /** Makes sure the terminal is shared (a local one starts its relay). */
    suspend fun prepare()
    suspend fun invite(target: ShareTarget, options: ShareOptions): ShareInvite
    suspend fun list(): List<SessionShareInfo>
    suspend fun update(shareId: String, changes: ShareChanges): SessionShareInfo
    suspend fun revoke(shareId: String)
    /** Every invitation is revoked and everyone else leaves. */
    suspend fun stop()
}

private class ServerShareBackend(private val core: TermoakCore, private val sessionId: String) : ShareBackend {
    override suspend fun prepare() = Unit
    override suspend fun invite(target: ShareTarget, options: ShareOptions) = core.shareServerSessionWith(sessionId, target, options)
    override suspend fun list() = core.listServerSessionShares(sessionId)
    override suspend fun update(shareId: String, changes: ShareChanges) = core.updateServerSessionShare(sessionId, shareId, changes)
    override suspend fun revoke(shareId: String) = core.revokeServerSessionShare(sessionId, shareId)
    override suspend fun stop() {
        core.stopSharingServerSession(sessionId)
    }
}

private class RelayShareBackend(private val terminal: LocalTerminal, private val title: String) : ShareBackend {
    private fun shared() = terminal.sharedTerminal ?: throw IllegalStateException()

    /** The relay starts with the first invitation (opening the sheet shares nothing yet). */
    override suspend fun prepare() {
        if (terminal.state.value != TermState.Running) throw IllegalStateException()
    }
    override suspend fun invite(target: ShareTarget, options: ShareOptions): ShareInvite {
        terminal.shareOrThrow(title) ?: throw IllegalStateException()
        return shared().invite(target, options)
    }
    override suspend fun list() = terminal.sharedTerminal?.listInvites().orEmpty()
    override suspend fun update(shareId: String, changes: ShareChanges) = shared().updateInvite(shareId, changes)
    override suspend fun revoke(shareId: String) = shared().revokeInvite(shareId)
    override suspend fun stop() = terminal.stopSharingByHand()
}

/** The terminal can be shared from this device: your server session, or a connected local terminal (with an account). */
fun canShare(session: TermSession, loggedIn: Boolean, isOwner: Boolean): Boolean = when (session) {
    is ServerTerminal -> isOwner && session.sessionId != null
    is LocalTerminal -> loggedIn
    else -> false
}

private enum class Target { PEOPLE, TEAM, LINK }

/** Expiry choices, in minutes (`null`: never). */
private val Expiries = listOf<Long?>(null, 60, 24 * 60, 7 * 24 * 60)

@Composable
private fun expiryLabel(minutes: Long?): String = when (minutes) {
    null -> stringResource(R.string.share_expiry_never)
    60L -> stringResource(R.string.share_expiry_hour)
    24L * 60 -> stringResource(R.string.share_expiry_day)
    else -> stringResource(R.string.share_expiry_week)
}

/**
 * Share sheet, like Termius': invite someone by email, a team or with a link
 * (view only or can ask for the keyboard, expiry, waiting room, automatic
 * keyboard), the active invitations (edit or revoke) and "Stop sharing".
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ShareSheet(app: TermoakApp, session: TermSession, title: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val backend = remember(session) {
        when (session) {
            is ServerTerminal -> ServerShareBackend(app.core, session.sessionId ?: "")
            is LocalTerminal -> RelayShareBackend(session, title)
            else -> null
        }
    } ?: return
    var ready by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var shares by remember { mutableStateOf<List<SessionShareInfo>>(emptyList()) }
    var teams by remember { mutableStateOf<List<Team>?>(null) }

    var target by remember { mutableStateOf(Target.LINK) }
    var email by remember { mutableStateOf("") }
    var teamId by remember { mutableStateOf<String?>(null) }
    var control by remember { mutableStateOf(false) }
    var expiry by remember { mutableStateOf<Long?>(null) }
    var approval by remember { mutableStateOf(true) }
    var autoGrant by remember { mutableStateOf(false) }
    var created by remember { mutableStateOf<ShareInvite?>(null) }
    var done by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<SessionShareInfo?>(null) }
    var revoking by remember { mutableStateOf<SessionShareInfo?>(null) }
    var stopping by remember { mutableStateOf(false) }
    val live by session.live.collectAsState()

    fun fail(e: Throwable, fallback: Int) {
        error = e.message?.takeIf { it.isNotBlank() && e !is IllegalStateException } ?: resources.getString(fallback)
    }
    suspend fun reload() {
        runCatching { backend.list() }.onSuccess { list -> shares = list.filter { it.active } }
    }
    LaunchedEffect(backend) {
        runCatching { backend.prepare() }
            .onSuccess { ready = true; reload() }
            .onFailure { fail(it, R.string.share_not_available) }
    }
    LaunchedEffect(target) {
        // Links wait for your approval by default; people and teams don't.
        approval = target == Target.LINK
        created = null
        done = null
        if (target == Target.TEAM && teams == null) {
            teams = runCatching { app.core.listTeams() }.getOrDefault(emptyList())
            if (teamId == null) teamId = teams?.firstOrNull()?.id
        }
    }

    fun invite() {
        val t = when (target) {
            Target.PEOPLE -> email.trim().takeIf { it.contains('@') }?.let { ShareTarget.User(it) }
            Target.TEAM -> teamId?.let { ShareTarget.Team(it) }
            Target.LINK -> ShareTarget.Link
        } ?: return
        scope.launch {
            busy = true
            error = null
            try {
                val invite = backend.invite(t, ShareOptions(control, expiry, approval, control && autoGrant))
                if (target == Target.LINK) {
                    created = invite
                } else {
                    done = resources.getString(R.string.share_invited)
                    email = ""
                }
                reload()
            } catch (e: Exception) {
                fail(e, R.string.share_invite_failed)
            } finally {
                busy = false
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Column(Modifier.padding(horizontal = 24.dp)) {
                Text(stringResource(R.string.share_title, title), style = MaterialTheme.typography.titleLarge, maxLines = 2,
                    overflow = TextOverflow.Ellipsis)
                Text(
                    stringResource(if (session is LocalTerminal) R.string.share_subtitle_local else R.string.share_subtitle_server),
                    Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            error?.let {
                Text(it, Modifier.padding(horizontal = 24.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium)
            }
            if (!ready) {
                if (error == null) {
                    Row(Modifier.fillMaxWidth().padding(32.dp), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() }
                }
                return@Column
            }

            // ----- Who -----
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
                Target.entries.forEachIndexed { i, t ->
                    SegmentedButton(
                        selected = target == t, onClick = { target = t },
                        shape = SegmentedButtonDefaults.itemShape(i, Target.entries.size),
                        icon = {},
                        label = {
                            Text(stringResource(when (t) {
                                Target.PEOPLE -> R.string.share_target_people
                                Target.TEAM -> R.string.share_target_team
                                Target.LINK -> R.string.share_target_link
                            }), maxLines = 1)
                        },
                    )
                }
            }
            when (target) {
                Target.PEOPLE -> OutlinedTextField(
                    email, { email = it; done = null },
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    label = { Text(stringResource(R.string.share_email)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                )
                Target.TEAM -> {
                    val list = teams
                    when {
                        list == null -> CircularProgressIndicator(Modifier.padding(horizontal = 24.dp).size(24.dp))
                        list.isEmpty() -> Text(
                            stringResource(R.string.share_no_teams), Modifier.padding(horizontal = 24.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        else -> FlowRow(Modifier.padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            list.forEach { team ->
                                FilterChip(teamId == team.id, { teamId = team.id }, { Text(team.name) })
                            }
                        }
                    }
                }
                Target.LINK -> Text(
                    stringResource(R.string.share_link_hint), Modifier.padding(horizontal = 24.dp),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ----- Permission -----
            SectionLabel(stringResource(R.string.share_permission))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                listOf(false, true).forEachIndexed { i, c ->
                    SegmentedButton(
                        selected = control == c, onClick = { control = c },
                        shape = SegmentedButtonDefaults.itemShape(i, 2),
                        label = { Text(stringResource(if (c) R.string.share_perm_control else R.string.share_perm_view), maxLines = 1) },
                    )
                }
            }
            SectionLabel(stringResource(R.string.share_expiry))
            FlowRow(Modifier.padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Expiries.forEach { m -> FilterChip(expiry == m, { expiry = m }, { Text(expiryLabel(m)) }) }
            }
            SwitchRow(stringResource(R.string.share_require_approval), stringResource(R.string.share_require_approval_hint), approval) {
                approval = it
            }
            SwitchRow(
                stringResource(R.string.share_auto_grant), stringResource(R.string.share_auto_grant_hint),
                control && autoGrant, enabled = control,
            ) { autoGrant = it }

            Row(Modifier.padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = { invite() },
                    enabled = !busy && when (target) {
                        Target.PEOPLE -> email.contains('@')
                        Target.TEAM -> teamId != null
                        Target.LINK -> true
                    },
                ) {
                    Text(stringResource(if (target == Target.LINK) R.string.share_create_link else R.string.share_invite))
                }
                if (busy) CircularProgressIndicator(Modifier.padding(start = 16.dp).size(20.dp), strokeWidth = 2.dp)
                done?.let { Text(it, Modifier.padding(start = 16.dp), color = Brand.Green) }
            }

            created?.let { invite ->
                val link = invite.link ?: invite.appLink ?: return@let
                Surface(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = RoundedCornerShape(12.dp),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.share_link_ready), style = MaterialTheme.typography.titleSmall)
                        Text(link, Modifier.padding(vertical = 8.dp), fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall)
                        Text(stringResource(R.string.share_link_once), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = {
                                context.getSystemService(ClipboardManager::class.java)
                                    ?.setPrimaryClip(ClipData.newPlainText("link", link))
                                done = resources.getString(R.string.share_link_copied)
                            }) {
                                Icon(Icons.Outlined.ContentCopy, null, Modifier.size(18.dp))
                                Text(stringResource(R.string.share_copy), Modifier.padding(start = 8.dp))
                            }
                            Button(onClick = {
                                val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                                    .putExtra(Intent.EXTRA_SUBJECT, resources.getString(R.string.share_link_subject, title))
                                    .putExtra(Intent.EXTRA_TEXT, link)
                                runCatching { context.startActivity(Intent.createChooser(send, null)) }
                            }) {
                                Icon(Icons.Outlined.Share, null, Modifier.size(18.dp))
                                Text(stringResource(R.string.share_send), Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                }
            }

            // ----- Active invitations -----
            if (shares.isNotEmpty()) {
                SectionLabel(stringResource(R.string.share_active))
                shares.forEach { s ->
                    ShareRow(s, onClick = { editing = s }, onRevoke = { revoking = s })
                }
            }
            val others = live.others.isNotEmpty()
            if (shares.isNotEmpty() || others) {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { stopping = true }, Modifier.padding(horizontal = 12.dp)) {
                    Text(stringResource(R.string.share_stop), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    editing?.let { s ->
        EditShareDialog(s, onDismiss = { editing = null }) { changes ->
            scope.launch {
                runCatching { backend.update(s.id, changes) }.onFailure { fail(it, R.string.share_update_failed) }
                reload()
            }
        }
    }
    revoking?.let { s ->
        ConfirmDialog(
            title = stringResource(R.string.share_revoke_title),
            text = stringResource(R.string.share_revoke_text),
            confirm = stringResource(R.string.share_revoke),
            destructive = true,
            onDismiss = { revoking = null },
        ) {
            scope.launch {
                runCatching { backend.revoke(s.id) }.onFailure { fail(it, R.string.share_update_failed) }
                reload()
            }
        }
    }
    if (stopping) {
        ConfirmDialog(
            title = stringResource(R.string.share_stop_title),
            text = stringResource(R.string.share_stop_text),
            confirm = stringResource(R.string.share_stop),
            destructive = true,
            onDismiss = { stopping = false },
        ) {
            scope.launch {
                runCatching { backend.stop() }
                    .onSuccess { onDismiss() }
                    .onFailure { fail(it, R.string.share_update_failed) }
            }
        }
    }
}

@Composable
private fun SwitchRow(title: String, hint: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    ListItem(
        modifier = Modifier.clickable(enabled = enabled) { onChange(!checked) }.padding(horizontal = 8.dp),
        headlineContent = { Text(title) },
        supportingContent = { Text(hint) },
        trailingContent = { Switch(checked, onChange, enabled = enabled) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

/** When an invitation expires, in the app language. */
@Composable
private fun expiresText(at: Long?): String {
    if (at == null) return stringResource(R.string.share_no_expiry)
    val ms = if (at < 10_000_000_000L) at * 1000 else at
    if (ms <= System.currentTimeMillis()) return stringResource(R.string.share_expired)
    val whenText = java.text.DateFormat.getDateTimeInstance(
        java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT, LocalConfiguration.current.locales[0],
    ).format(java.util.Date(ms))
    return stringResource(R.string.share_expires_at, whenText)
}

@Composable
private fun ShareRow(s: SessionShareInfo, onClick: () -> Unit, onRevoke: () -> Unit) {
    val (icon, name) = when (s.kind) {
        ShareKind.USER -> Icons.Outlined.Person to (s.userName?.takeIf { it.isNotBlank() } ?: s.userEmail ?: "?")
        ShareKind.TEAM -> Icons.Outlined.Groups to (s.teamName ?: stringResource(R.string.share_target_team))
        ShareKind.LINK -> Icons.Outlined.Link to stringResource(R.string.share_kind_link)
    }
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) },
        headlineContent = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(
                listOfNotNull(
                    stringResource(if (s.control) R.string.share_perm_control else R.string.share_perm_view),
                    expiresText(s.expiresAt),
                    if (s.requireApproval) stringResource(R.string.share_asks_first) else null,
                    if (s.control && s.autoGrant) stringResource(R.string.share_auto_grant_short) else null,
                    s.participants.toInt().takeIf { it > 0 }?.let { pluralStringResource(R.plurals.share_people_inside, it, it) },
                ).joinToString(" · "),
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            IconButton(onClick = onRevoke) { Icon(Icons.Outlined.Close, stringResource(R.string.share_revoke)) }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

/** Changes an invitation live: permission, waiting room, automatic keyboard and expiry. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun EditShareDialog(s: SessionShareInfo, onDismiss: () -> Unit, onSave: (ShareChanges) -> Unit) {
    var control by remember { mutableStateOf(s.control) }
    var approval by remember { mutableStateOf(s.requireApproval) }
    var autoGrant by remember { mutableStateOf(s.autoGrant) }
    // `KEEP` (-1): leave the expiry as it is.
    var expiry by remember { mutableStateOf<Long?>(KEEP) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.share_edit_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf(false, true).forEachIndexed { i, c ->
                        SegmentedButton(
                            selected = control == c, onClick = { control = c },
                            shape = SegmentedButtonDefaults.itemShape(i, 2),
                            label = { Text(stringResource(if (c) R.string.share_perm_control else R.string.share_perm_view), maxLines = 1) },
                        )
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.share_require_approval), Modifier.weight(1f))
                    Switch(approval, { approval = it })
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.share_auto_grant), Modifier.weight(1f))
                    Switch(control && autoGrant, { autoGrant = it }, enabled = control)
                }
                Text(stringResource(R.string.share_expiry), Modifier.padding(top = 12.dp), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(expiry == KEEP, { expiry = KEEP }, { Text(stringResource(R.string.share_expiry_keep)) })
                    Expiries.forEach { m -> FilterChip(expiry == m, { expiry = m }, { Text(expiryLabel(m)) }) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    ShareChanges(
                        control = control.takeIf { it != s.control },
                        expiresInMinutes = expiry?.takeIf { it != KEEP },
                        noExpiry = expiry == null,
                        requireApproval = approval.takeIf { it != s.requireApproval },
                        autoGrant = (control && autoGrant).takeIf { it != s.autoGrant },
                    ),
                )
                onDismiss()
            }) { Text(stringResource(R.string.common_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

private const val KEEP = -1L
