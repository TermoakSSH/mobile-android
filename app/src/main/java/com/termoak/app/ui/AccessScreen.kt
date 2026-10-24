package com.termoak.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.LockClock
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.TimerOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.termoak.app.R
import com.termoak.app.TermoakApp
import com.termoak.app.data.AccessBook
import com.termoak.app.data.AccessRules
import com.termoak.app.data.AccessTexts
import com.termoak.app.data.displayName
import com.termoak.app.data.shownEmail
import com.termoak.app.userMessage
import com.termoak.ffi.AccessRequestInfo
import com.termoak.ffi.AccessRequestStatus
import com.termoak.ffi.AccessTargetKind
import com.termoak.ffi.AccountStatus
import com.termoak.ffi.TermoakException
import kotlinx.coroutines.launch

/** What the Access screen opens with (a push notification, a link). */
data class AccessFocus(val requestId: String?, val action: String?)

/** A request in a list, with its account (several accounts: its name is shown). */
data class AccountRequest(val accountId: String, val accountName: String?, val r: AccessRequestInfo)

/** The lists of the Access screen. */
data class AccessLists(
    val waiting: List<AccountRequest>,
    val active: List<AccountRequest>,
    val recent: List<AccountRequest>,
    val mine: List<AccountRequest>,
)

/** The lists from [AccessBook] for [accounts] (`id` → name to show, `null` with one account). */
fun accessLists(accounts: List<Pair<String, String?>>, now: Long): AccessLists {
    val waiting = mutableListOf<AccountRequest>()
    val active = mutableListOf<AccountRequest>()
    val recent = mutableListOf<AccountRequest>()
    val mine = mutableListOf<AccountRequest>()
    for ((id, name) in accounts) {
        val a = AccessBook.approvals(id)
        AccessRules.waiting(a).forEach { waiting += AccountRequest(id, name, it) }
        AccessRules.endable(a, now).forEach { active += AccountRequest(id, name, it) }
        a.filter { it.status != AccessRequestStatus.PENDING && !(AccessRules.isActive(it, now) && it.canDecide) }
            .take(15).forEach { recent += AccountRequest(id, name, it) }
        AccessBook.mine(id).forEach { mine += AccountRequest(id, name, it) }
    }
    return AccessLists(
        waiting.sortedByDescending { it.r.createdAt }, active.sortedBy { it.r.endsAt ?: 0 },
        recent.sortedByDescending { it.r.decidedAt ?: it.r.createdAt }.take(15), mine.sortedByDescending { it.r.createdAt },
    )
}

/**
 * Access (0.7 phase 4d, as the desktop's Access section): temporary access
 * to hosts with approval. "Waiting for your decision" (approve with an
 * optional shorter window and a note, deny with a reason), the open
 * windows you can end early, recent decisions, and your own requests
 * (cancel, end). Read again on every `access_request` event. A push's
 * Approve/Deny only opens the matching confirmation here, behind the app
 * lock; nothing is decided from the notification.
 */
@Composable
fun AccessScreen(app: TermoakApp, nav: NavHostController, focus: AccessFocus? = null, mineFirst: Boolean = false) {
    val res = LocalResources.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val accountList by app.accounts.list.collectAsState()
    val supported = accountList.filter { it.status == AccountStatus.ACTIVE && AccessBook.supported(it.id) }
    val several = supported.size > 1
    var tab by remember { mutableIntStateOf(if (mineFirst) 1 else 0) }
    var loading by remember { mutableStateOf(false) }
    var approving by remember { mutableStateOf<AccountRequest?>(null) }
    var denying by remember { mutableStateOf<AccountRequest?>(null) }
    var revoking by remember { mutableStateOf<AccountRequest?>(null) }
    fun reload() {
        scope.launch {
            loading = true
            supported.forEach { AccessBook.refresh(it.id) }
            loading = false
        }
    }
    LaunchedEffect(supported.map { it.id }) { reload() }
    // Live: the book is read again on each event; the countdowns tick.
    supported.forEach { AccessBook.revision(it.id) }
    val now = rememberClock(1_000)
    val lists = accessLists(supported.map { it.id to (if (several) it.displayName else null) }, now)
    // Opened from a notification: its confirmation (never decided by the notification itself).
    LaunchedEffect(focus, lists.waiting.size, lists.mine.size) {
        val f = focus ?: return@LaunchedEffect
        val id = f.requestId ?: return@LaunchedEffect
        val w = lists.waiting.firstOrNull { it.r.id == id }
        when {
            w != null && f.action == "APPROVE" -> approving = w
            w != null && f.action == "DENY" -> denying = w
            lists.mine.any { it.r.id == id } -> tab = 1
        }
    }
    fun act(done: Int, block: suspend () -> AccessRequestInfo?, accountId: String) {
        scope.launch {
            try {
                block()?.let { AccessBook.remember(accountId, it, mineToo = it.requesterId == app.accounts.account(accountId)?.userId) }
                AccessBook.refresh(accountId)
                AccessBook.rehost(accountId)
                snackbar.showSnackbar(res.getString(done))
            } catch (e: TermoakException) {
                snackbar.showSnackbar(e.userMessage(res, R.string.access_check_failed))
            }
        }
    }
    ScreenScaffold(
        title = stringResource(R.string.access_title),
        subtitle = stringResource(R.string.access_subtitle),
        navigationIcon = {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back)) }
        },
        actions = {
            if (supported.isNotEmpty()) {
                IconButton(onClick = { reload() }, enabled = !loading) {
                    if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Outlined.Refresh, stringResource(R.string.common_refresh))
                }
            }
        },
    ) { padding ->
        if (supported.isEmpty()) {
            EmptyState(Icons.Outlined.LockClock, stringResource(R.string.access_none_title), stringResource(R.string.access_none_hint), Modifier.padding(padding))
            return@ScreenScaffold
        }
        AccessContent(
            lists, now, tab, Modifier.padding(padding), onTab = { tab = it },
            onApprove = { approving = it }, onDeny = { denying = it }, onRevoke = { revoking = it },
            onCancel = { ar -> act(R.string.access_cancelled, { app.accounts.handle(ar.accountId)?.cancelAccessRequest(ar.r.id) }, ar.accountId) },
        )
    }
    approving?.let { ar ->
        ApproveDialog(ar.r, onDismiss = { approving = null }) { minutes, note ->
            approving = null
            act(R.string.access_approved, {
                app.accounts.handle(ar.accountId)?.approveAccessRequest(
                    ar.r.id, minutes.takeIf { it < ar.r.minutes.toInt() }?.toUInt(), note.trim().ifEmpty { null },
                )
            }, ar.accountId)
        }
    }
    denying?.let { ar ->
        ReasonDialog(
            stringResource(R.string.access_deny_title), summary(ar.r), stringResource(R.string.access_deny_reason),
            stringResource(R.string.access_deny_reason_placeholder), stringResource(R.string.access_deny_ok), destructive = true,
            onDismiss = { denying = null },
        ) { reason ->
            denying = null
            act(R.string.access_denied, { app.accounts.handle(ar.accountId)?.denyAccessRequest(ar.r.id, reason.trim().ifEmpty { null }) }, ar.accountId)
        }
    }
    revoking?.let { ar ->
        ReasonDialog(
            stringResource(R.string.access_revoke_title), stringResource(R.string.access_revoke_hint), stringResource(R.string.access_deny_reason),
            stringResource(R.string.access_deny_reason_placeholder), stringResource(R.string.access_revoke_ok), destructive = true,
            onDismiss = { revoking = null },
        ) { reason ->
            revoking = null
            act(R.string.access_revoked, { app.accounts.handle(ar.accountId)?.revokeAccessRequest(ar.r.id, reason.trim().ifEmpty { null }) }, ar.accountId)
        }
    }
}

/** "Ana asks for web1 for 1 h: “deploy hotfix”". */
@Composable
fun summary(r: AccessRequestInfo): String = stringResource(
    R.string.access_summary, AccessRules.requesterName(r).let(::shownEmail), targetLabel(r),
    AccessTexts.duration(LocalResources.current, r.minutes.toInt()), r.reason,
)

/** The target of a request: the host's name, "Group Production", "Vault Ops". */
@Composable
fun targetLabel(r: AccessRequestInfo): String = when (r.targetKind) {
    AccessTargetKind.GROUP -> stringResource(R.string.access_target_group, r.targetLabel)
    AccessTargetKind.VAULT -> stringResource(R.string.access_target_vault, r.targetLabel)
    else -> r.targetLabel
}

/** The Access screen's lists (no engine: the screenshot tests draw it with data). */
@Composable
fun AccessContent(
    lists: AccessLists,
    now: Long,
    tab: Int,
    modifier: Modifier = Modifier,
    onTab: (Int) -> Unit,
    onApprove: (AccountRequest) -> Unit,
    onDeny: (AccountRequest) -> Unit,
    onRevoke: (AccountRequest) -> Unit,
    onCancel: (AccountRequest) -> Unit,
) {
    Column(modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = tab) {
            Tab(tab == 0, { onTab(0) }, text = {
                BadgedBox(badge = { if (lists.waiting.isNotEmpty()) Badge { Text("${lists.waiting.size}") } }) {
                    Text(stringResource(R.string.access_waiting), Modifier.padding(end = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            })
            Tab(tab == 1, { onTab(1) }, text = { Text(stringResource(R.string.access_mine), maxLines = 1) })
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
            if (tab == 0) {
                if (lists.waiting.isEmpty()) item { EmptyNote(stringResource(R.string.access_waiting_empty)) }
                items(lists.waiting, key = { "w" + it.r.id }) { ar ->
                    RequestCard(ar, now) {
                        OutlinedButton(onClick = { onDeny(ar) }, modifier = Modifier.testTag("deny-" + ar.r.id)) {
                            Icon(Icons.Outlined.Close, null, Modifier.size(16.dp))
                            Text(stringResource(R.string.access_deny_button), Modifier.padding(start = 6.dp))
                        }
                        Button(onClick = { onApprove(ar) }, modifier = Modifier.testTag("approve-" + ar.r.id)) {
                            Icon(Icons.Outlined.Check, null, Modifier.size(16.dp))
                            Text(stringResource(R.string.access_approve_button), Modifier.padding(start = 6.dp))
                        }
                    }
                }
                item { SectionLabel(stringResource(R.string.access_active)) }
                if (lists.active.isEmpty()) item { EmptyNote(stringResource(R.string.access_active_empty)) }
                items(lists.active, key = { "a" + it.r.id }) { ar ->
                    RequestCard(ar, now) {
                        OutlinedButton(onClick = { onRevoke(ar) }) {
                            Icon(Icons.Outlined.TimerOff, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                            Text(stringResource(R.string.access_revoke_button), Modifier.padding(start = 6.dp), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                if (lists.recent.isNotEmpty()) {
                    item { SectionLabel(stringResource(R.string.access_recent)) }
                    items(lists.recent, key = { "r" + it.r.id }) { ar -> RequestCard(ar, now, actions = null) }
                }
            } else {
                if (lists.mine.isEmpty()) item { EmptyNote(stringResource(R.string.access_mine_empty)) }
                items(lists.mine, key = { "m" + it.r.id }) { ar ->
                    val r = ar.r
                    val active = AccessRules.isActive(r, now)
                    RequestCard(ar, now, mine = true, actions = if (r.status == AccessRequestStatus.PENDING || active) ({
                        OutlinedButton(onClick = { onCancel(ar) }) {
                            Text(stringResource(if (active) R.string.access_end_button else R.string.access_request_cancel_request))
                        }
                    }) else null)
                }
            }
        }
    }
}

@Composable
private fun EmptyNote(text: String) {
    Text(
        text, Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** A request: who and what, how long, why, its state and window, and its actions. */
@Composable
fun RequestCard(ar: AccountRequest, now: Long, mine: Boolean = false, actions: (@Composable () -> Unit)?) {
    val r = ar.r
    val res = LocalResources.current
    val key = AccessRules.statusKey(r, now)
    CardBox {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(targetLabel(r), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Pill(
                    stringResource(AccessTexts.status(key)),
                    when (key) {
                        AccessRules.StatusKey.PENDING -> Brand.Amber
                        AccessRules.StatusKey.ACTIVE -> Brand.Green
                        AccessRules.StatusKey.DENIED, AccessRules.StatusKey.REVOKED -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            if (!mine) {
                Text(
                    listOfNotNull(AccessRules.requesterName(r).let(::shownEmail), ar.accountName).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else if (ar.accountName != null) {
                Text(ar.accountName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("“${r.reason}”", style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(R.string.access_asked_for, AccessTexts.duration(res, r.minutes.toInt())) +
                    if (r.status == AccessRequestStatus.PENDING) " · " + stringResource(R.string.access_waits_until, AccessTexts.clock(r.expiresAt)) else "",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val end = r.endsAt
            if (key == AccessRules.StatusKey.ACTIVE && end != null) {
                val left = AccessRules.remainingSecs(end, now)
                WindowLine(stringResource(R.string.access_window_until, AccessTexts.clock(end), AccessTexts.left(res, left)), AccessRules.endingSoon(left))
            }
            r.decidedByName?.takeIf { it.isNotBlank() && r.status != AccessRequestStatus.PENDING }?.let { who ->
                val note = r.decisionReason?.takeIf { it.isNotBlank() }
                Text(
                    if (note != null) stringResource(R.string.access_decision_note, who, note) else stringResource(R.string.access_decided_by, who),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (r.selfApproved) Text(stringResource(R.string.access_self_approved), style = MaterialTheme.typography.labelSmall, color = Brand.Amber)
            if (actions != null) {
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) { actions() }
            }
        }
    }
}

/**
 * "Approve access": the request, the window (the one asked for or shorter;
 * it starts now) and an optional note.
 */
@Composable
fun ApproveDialog(r: AccessRequestInfo, onDismiss: () -> Unit, inline: Boolean = false, onApprove: (Int, String) -> Unit) {
    val res = LocalResources.current
    val choices = remember(r.minutes) { AccessRules.approveChoices(r.minutes.toInt().coerceAtLeast(1)) }
    var minutes by remember { mutableIntStateOf(choices.last()) }
    var note by remember { mutableStateOf("") }
    MaybeInlineDialog(
        inline = inline,
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.access_approve_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(summary(r))
                Picker(stringResource(R.string.access_approve_window), AccessTexts.duration(res, minutes), null, choices.map { it to AccessTexts.duration(res, it) }) {
                    minutes = it
                }
                Text(stringResource(R.string.access_approve_window_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    note, { note = it.take(500) }, Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.access_approve_note)) },
                    placeholder = { Text(stringResource(R.string.access_approve_note_placeholder)) },
                )
            }
        },
        confirmButton = { TextButton(onClick = { onApprove(minutes, note) }) { Text(stringResource(R.string.access_approve_ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

/** Deny or end a window: a text and an optional reason the requester sees. */
@Composable
fun ReasonDialog(
    title: String,
    text: String,
    label: String,
    placeholder: String,
    confirm: String,
    destructive: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var reason by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text)
                OutlinedTextField(
                    reason, { reason = it.take(500) }, Modifier.fillMaxWidth(),
                    label = { Text(label) }, placeholder = { Text(placeholder) },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(reason) }) {
                Text(confirm, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}
