package com.termoak.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddLink
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.termoak.app.R
import com.termoak.app.TermoakApp
import com.termoak.app.asString
import com.termoak.app.data.displayName
import com.termoak.app.term.Elapsed
import com.termoak.app.term.TermSession
import com.termoak.app.term.TermState
import com.termoak.app.userMessage
import com.termoak.ffi.AccountInfo
import com.termoak.ffi.ItemFilter
import com.termoak.ffi.ParticipantKind
import com.termoak.ffi.ServerSession
import com.termoak.ffi.ServerSessionList
import com.termoak.ffi.ServerSessionState
import com.termoak.ffi.SessionAccess
import com.termoak.ffi.SshHost
import com.termoak.ffi.TermoakException
import kotlinx.coroutines.launch

/**
 * Connections, like Termius': the terminals open on this phone (tap to go
 * back to one, swipe or ✕ to close it) and, with an account, the sessions
 * that live on the server, the ones shared with you and the recent ones.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionsScreen(app: TermoakApp, nav: NavHostController) {
    val resources = LocalResources.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val loggedIn by app.accounts.loggedIn.collectAsState()
    val local by app.sessions.list.collectAsState()
    // The server sessions of each signed-in account.
    var lists by remember { mutableStateOf<List<Pair<AccountInfo, ServerSessionList>>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf<ServerSession?>(null) }
    var closingAll by remember { mutableStateOf(false) }
    var joining by remember { mutableStateOf(false) }
    // Session whose activity (who typed) is open: id and title.
    var activity by remember { mutableStateOf<Pair<String, String>?>(null) }
    // One of your server sessions being shared (invitations), as on iOS.
    var sharing by remember { mutableStateOf<ServerSession?>(null) }
    // A recording being downloaded (session id), to share or save it.
    var downloading by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    // Hosts of every account (and This device), by account and id.
    val hosts = remember { runCatching { app.core.listHosts(ItemFilter()) }.getOrDefault(emptyList()).associateBy { it.accountId to it.id } }
    val sessionAccount = remember(lists) {
        lists.flatMap { (a, l) -> (l.active + l.shared).map { it.id to a.id } + l.recent.map { it.id to a.id } }.toMap()
    }
    fun hostOf(id: String, hostId: String?): SshHost? = hostId?.let { hosts[sessionAccount[id] to it] }
    fun hostOf(s: ServerSession): SshHost? = hostOf(s.id, s.hostId)
    val untitled = stringResource(R.string.common_session)

    fun reload() {
        if (loggedIn != true) return
        scope.launch {
            loading = true
            try {
                lists = app.accounts.active().mapNotNull { a -> app.accounts.handle(a.id)?.let { a to it.listServerSessions() } }
            } catch (e: TermoakException) {
                snackbar.showSnackbar(e.userMessage(resources, R.string.sessions_load_failed))
            } finally {
                loading = false
            }
        }
    }
    LaunchedEffect(loggedIn) { reload() }
    LaunchedEffect(Unit) { app.accounts.changes.collect { if (it == "session" || it == "lagged") reload() } }

    val currentAccount = remember(lists) { runCatching { app.core.currentAccount()?.id }.getOrNull() }
    /** Downloads a recording (`.cast`, asciicast) and hands it to another app to share or save it. */
    fun downloadRecording(id: String, title: String) {
        if (downloading != null) return
        downloading = id
        scope.launch {
            val file = java.io.File(Recordings.folder(context), Recordings.fileName(title, id))
            try {
                app.core.downloadRecording(id, file.path, null)
                if (!Recordings.share(context, file)) snackbar.showSnackbar(resources.getString(R.string.recording_share_failed))
            } catch (e: TermoakException) {
                file.parentFile?.deleteRecursively()
                snackbar.showSnackbar(e.userMessage(resources, R.string.sessions_recording_download_failed))
            } finally {
                downloading = null
            }
        }
    }

    fun openTab(s: TermSession) {
        app.sessions.select(s.id)
        nav.navigate(Routes.TERMINAL) { launchSingleTop = true }
    }
    fun attach(s: ServerSession) {
        app.sessions.attach(
            s.id, s.title.ifBlank { hostOf(s)?.label ?: untitled }, s.hostId,
            owner = s.access == SessionAccess.OWNER, accountId = sessionAccount[s.id],
        )
        nav.navigate(Routes.TERMINAL) { launchSingleTop = true }
    }

    ScreenScaffold(
        title = stringResource(R.string.nav_connections),
        large = true,
        actions = {
            IconButton(onClick = { joining = true }) { Icon(Icons.Outlined.AddLink, stringResource(R.string.join_with_link)) }
            if (local.isNotEmpty()) {
                IconButton(onClick = { closingAll = true }) { Icon(Icons.Outlined.LinkOff, stringResource(R.string.connections_close_all)) }
            }
        },
    ) { padding ->
        PullToRefreshBox(isRefreshing = loading, onRefresh = { reload() }, modifier = Modifier.fillMaxSize().padding(padding)) {
            val active = lists.flatMap { it.second.active }
            val shared = lists.flatMap { it.second.shared }
            val recent = lists.flatMap { it.second.recent }.sortedByDescending { it.endedAt ?: it.createdAt }.take(15)
            // With several accounts, each session says which one it is on.
            val several = lists.size > 1
            fun accountLabel(id: String): String? =
                if (several) lists.firstOrNull { it.first.id == sessionAccount[id] }?.first?.displayName else null
            if (local.isEmpty() && active.isEmpty() && shared.isEmpty() && recent.isEmpty()) {
                Column(Modifier.fillMaxSize()) {
                    EmptyState(
                        Icons.Outlined.Terminal,
                        stringResource(R.string.sessions_empty_title),
                        stringResource(if (loggedIn == true) R.string.sessions_empty_text_synced else R.string.sessions_empty_text_local),
                        modifier = Modifier.weight(1f),
                        action = stringResource(R.string.connections_open_vault),
                        onAction = { nav.goTab(Routes.HOSTS) },
                    )
                    TextButton(onClick = { joining = true }, Modifier.align(Alignment.CenterHorizontally).padding(bottom = 24.dp)) {
                        Icon(Icons.Outlined.AddLink, null, Modifier.size(18.dp))
                        Text(stringResource(R.string.join_with_link), Modifier.padding(start = 8.dp))
                    }
                }
                return@PullToRefreshBox
            }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                if (local.isNotEmpty()) {
                    item { SectionLabel(stringResource(R.string.sessions_open_here)) }
                    items(local, key = { it.id }) { s ->
                        SwipeToClose(Icons.Outlined.Close, onSwiped = { app.sessions.close(s.id) }) {
                            LocalSessionRow(s, s.hostId?.let { hosts[s.accountId to it] }, onClick = { openTab(s) }, onClose = { app.sessions.close(s.id) })
                        }
                    }
                }
                if (loggedIn == true && active.isNotEmpty()) {
                    item { SectionLabel(stringResource(R.string.sessions_on_server)) }
                    items(active, key = { "a" + it.id }) { s ->
                        SwipeToClose(Icons.Outlined.PowerSettingsNew, resetAfter = true, onSwiped = { closing = s }) {
                            ServerSessionRow(
                                s, hostOf(s), onClick = { attach(s) }, onClose = { closing = s },
                                onActivity = { activity = s.id to s.title.ifBlank { hostOf(s)?.label ?: untitled } },
                                onShare = { sharing = s },
                                account = accountLabel(s.id),
                            )
                        }
                    }
                }
                if (loggedIn == true && shared.isNotEmpty()) {
                    item { SectionLabel(stringResource(R.string.sessions_shared)) }
                    items(shared, key = { "s" + it.id }) { s ->
                        ServerSessionRow(s, hostOf(s), onClick = { attach(s) }, onClose = null, account = accountLabel(s.id))
                    }
                }
                item {
                    ListItem(
                        modifier = Modifier.clickable { joining = true },
                        headlineContent = { Text(stringResource(R.string.join_with_link)) },
                        supportingContent = { Text(stringResource(R.string.join_with_link_row)) },
                        leadingContent = {
                            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                                Icon(Icons.Outlined.AddLink, null, tint = MaterialTheme.colorScheme.primary)
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
                if (loggedIn == true && recent.isNotEmpty()) {
                    item { SectionLabel(stringResource(R.string.sessions_recent)) }
                    items(recent, key = { "r" + it.id }) { r ->
                        val title = r.title.ifBlank { hostOf(r.id, r.hostId)?.label ?: untitled }
                        ListItem(
                            // Recorded: who typed and when.
                            modifier = if (r.recording) Modifier.clickable { activity = r.id to title } else Modifier,
                            headlineContent = { Text(title) },
                            supportingContent = {
                                Text(
                                    listOfNotNull(recentStatus(r.status), relativeTime(r.endedAt ?: r.createdAt), accountLabel(r.id), r.error)
                                        .joinToString(" · "),
                                    maxLines = 1,
                                )
                            },
                            leadingContent = {
                                Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Outlined.History, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            },
                            trailingContent = if (r.recording) {
                                {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        // The .cast file (asciicast), of the sessions of the current account (the engine downloads those).
                                        if (downloading == r.id) {
                                            CircularProgressIndicator(Modifier.padding(12.dp).size(20.dp), strokeWidth = 2.dp)
                                        } else if (sessionAccount[r.id] == currentAccount) {
                                            IconButton(onClick = { downloadRecording(r.id, title) }, enabled = downloading == null) {
                                                Icon(
                                                    Icons.Outlined.FileDownload, stringResource(R.string.sessions_recording_download), Modifier.size(20.dp),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        }
                                        IconButton(onClick = { activity = r.id to title }) {
                                            Icon(
                                                Icons.Outlined.Timeline, stringResource(R.string.activity_open), Modifier.size(20.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            } else null,
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                    }
                }
            }
        }
    }

    closing?.let { s ->
        ConfirmDialog(
            title = if (s.title.isBlank()) stringResource(R.string.term_terminate_title)
            else stringResource(R.string.sessions_terminate_named, s.title),
            text = stringResource(R.string.sessions_terminate_text),
            confirm = stringResource(R.string.sessions_terminate),
            destructive = true,
            onDismiss = { closing = null },
        ) {
            scope.launch {
                runCatching { sessionAccount[s.id]?.let { app.core.account(it).closeServerSession(s.id) } ?: app.core.closeServerSession(s.id) }
                    .onFailure { snackbar.showSnackbar(it.userMessage(resources, R.string.sessions_close_failed)) }
                reload()
            }
        }
    }
    activity?.let { (id, title) ->
        ActivitySheet(app, id, title, onDismiss = { activity = null }, accountId = sessionAccount[id])
    }
    sharing?.let { s ->
        val people = s.participants.size.takeIf { it > 0 } ?: s.viewers.size
        ServerSessionShareSheet(
            app, s.id, sessionAccount[s.id], s.title.ifBlank { hostOf(s)?.label ?: untitled }, othersInside = people > 1,
        ) { sharing = null }
    }
    if (joining) {
        JoinLinkDialog(onDismiss = { joining = false }) { link ->
            joining = false
            nav.navigate(Routes.join(link))
        }
    }
    if (closingAll) {
        ConfirmDialog(
            title = pluralStringResource(R.plurals.connections_close_all_title, local.size, local.size),
            text = stringResource(R.string.connections_close_all_text),
            confirm = stringResource(R.string.connections_close_all),
            onDismiss = { closingAll = false },
        ) { app.sessions.closeAll() }
    }
}

/** A row that closes (or asks to) when swiped to either side. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToClose(icon: ImageVector, resetAfter: Boolean = false, onSwiped: () -> Unit, content: @Composable () -> Unit) {
    val scope = rememberCoroutineScope()
    val state = rememberSwipeToDismissBoxState()
    SwipeToDismissBox(
        state,
        backgroundContent = {
            val end = state.dismissDirection == SwipeToDismissBoxValue.EndToStart
            Row(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer).padding(horizontal = 24.dp),
                horizontalArrangement = if (end) Arrangement.End else Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically,
            ) { Icon(icon, null, tint = MaterialTheme.colorScheme.onErrorContainer) }
        },
        onDismiss = {
            onSwiped()
            // Ending a server session asks first: the row comes back in the meantime.
            if (resetAfter) scope.launch { state.reset() }
        },
    ) {
        Box(Modifier.background(MaterialTheme.colorScheme.surface)) { content() }
    }
}

/** Host tile with a dot of the given color on its corner. */
@Composable
private fun StatusTile(host: SshHost?, label: String, dot: Color) {
    Box {
        HostTile(host?.label ?: label, host?.os, host?.color, size = 44.dp, icon = host?.icon)
        Box(
            Modifier.align(Alignment.BottomEnd).offset(3.dp, 3.dp).size(14.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface).padding(2.dp).clip(CircleShape).background(dot),
        )
    }
}

@Composable
private fun LocalSessionRow(s: TermSession, host: SshHost?, onClick: () -> Unit, onClose: () -> Unit) {
    val state by s.state.collectAsState()
    val title by s.title.collectAsState()
    val status = when (val st = state) {
        TermState.Running -> stringResource(
            when {
                s.persistent -> R.string.sessions_connected_server
                s is com.termoak.app.term.LocalTerminal && s.telnet -> R.string.sessions_connected_telnet
                else -> R.string.sessions_connected_ssh
            },
        )
        is TermState.Connecting -> st.message.asString()
        is TermState.Closed -> st.message.asString()
        TermState.Asleep -> stringResource(R.string.sessions_asleep)
    }
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(title ?: s.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (s.persistent) Icons.Outlined.CloudQueue else Icons.Outlined.PhoneAndroid, null,
                    Modifier.size(14.dp).padding(end = 2.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    listOfNotNull(
                        host?.let { (it.settings.username?.let { u -> "$u@" } ?: "") + it.address }?.takeIf { title != null },
                        status,
                        relativeTime(s.openedAt),
                    ).joinToString(" · "),
                    Modifier.padding(start = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        },
        leadingContent = { StatusTile(host, s.label, stateColor(state)) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Connected for… (a timer, as on iOS).
                if (state == TermState.Running) {
                    val since = s.connectedAt
                    if (since != null) {
                        val now by produceState(System.currentTimeMillis(), since) {
                            while (true) {
                                value = System.currentTimeMillis()
                                kotlinx.coroutines.delay(1000)
                            }
                        }
                        Text(
                            Elapsed.format(now - since), style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace,
                        )
                    }
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Outlined.Close, stringResource(R.string.common_close), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

/** Status of a finished session (`connecting`, `running`, `closed` or `failed`), translated when known. */
@Composable
private fun recentStatus(status: String): String = when (status) {
    "connecting" -> stringResource(R.string.session_state_connecting)
    "running" -> stringResource(R.string.session_state_running)
    "closed" -> stringResource(R.string.session_state_closed)
    "failed" -> stringResource(R.string.session_state_failed)
    else -> status
}

@Composable
private fun ServerSessionRow(
    s: ServerSession,
    host: SshHost?,
    onClick: () -> Unit,
    onClose: (() -> Unit)?,
    onActivity: (() -> Unit)? = null,
    /** Share it (invitations), without opening it. */
    onShare: (() -> Unit)? = null,
    /** The account it is on, with several accounts. */
    account: String? = null,
) {
    val (text, color) = when (val st = s.state) {
        is ServerSessionState.Running -> stringResource(R.string.session_state_running) to Brand.Green
        is ServerSessionState.Connecting -> st.message to Brand.Amber
        is ServerSessionState.HostOffline -> stringResource(R.string.session_state_host_offline) to Brand.Amber
        is ServerSessionState.Closed -> (st.reason ?: stringResource(R.string.session_state_closed)) to MaterialTheme.colorScheme.error
    }
    val title = s.title.ifBlank { host?.label ?: stringResource(R.string.common_session) }
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            val people = s.participants.size.takeIf { it > 0 } ?: s.viewers.size
            val viewers = people.takeIf { it > 1 }?.let { pluralStringResource(R.plurals.sessions_viewers, it, it) }
            // Shared with you: who shares it and what you can do.
            // Servers before 0.3 don't send `owner_name`: the owner among the participants.
            val owner = s.ownerName ?: s.participants.firstOrNull { it.kind == ParticipantKind.OWNER }?.name?.takeIf { it.isNotBlank() }
            val sharedBy = if (s.access == SessionAccess.OWNER) null else listOfNotNull(
                owner?.let { stringResource(R.string.sessions_shared_by, it) },
                stringResource(if (s.access == SessionAccess.CONTROL) R.string.share_perm_control else R.string.share_perm_view),
            ).joinToString(" · ")
            Column {
                if (sharedBy != null) {
                    Text(sharedBy, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.primary)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (onClose == null) Icons.Outlined.Group else Icons.Outlined.CloudQueue, null,
                        Modifier.size(14.dp).padding(end = 2.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        listOfNotNull(text, relativeTime(s.createdAt), viewers, account).joinToString(" · "),
                        Modifier.padding(start = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        leadingContent = { StatusTile(host, title, color) },
        trailingContent = {
            Row {
                if (onShare != null) {
                    IconButton(onClick = onShare) {
                        Icon(
                            Icons.Outlined.PersonAdd, stringResource(R.string.share_action), Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (onActivity != null) {
                    IconButton(onClick = onActivity) {
                        Icon(
                            Icons.Outlined.Timeline, stringResource(R.string.activity_open), Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (onClose != null) IconButton(onClick = onClose) {
                    Icon(
                        Icons.Outlined.PowerSettingsNew, stringResource(R.string.sessions_terminate), Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

