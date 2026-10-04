package com.termoak.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.termoak.app.R
import com.termoak.app.TermoakApp
import com.termoak.app.asString
import com.termoak.app.term.TermState
import com.termoak.ffi.ServerSession
import com.termoak.ffi.ServerSessionList
import com.termoak.ffi.ServerSessionState
import com.termoak.ffi.TermoakException
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionsScreen(app: TermoakApp, nav: NavHostController) {
    val resources = LocalResources.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val loggedIn by app.account.loggedIn.collectAsState()
    val local by app.sessions.list.collectAsState()
    var server by remember { mutableStateOf<ServerSessionList?>(null) }
    var loading by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf<ServerSession?>(null) }
    val hosts = remember { runCatching { app.core.listHosts() }.getOrDefault(emptyList()).associateBy { it.id } }
    val untitled = stringResource(R.string.common_session)

    fun reload() {
        if (loggedIn != true) return
        scope.launch {
            loading = true
            try {
                server = app.core.listServerSessions()
            } catch (e: TermoakException) {
                snackbar.showSnackbar(e.message ?: resources.getString(R.string.sessions_load_failed))
            } finally {
                loading = false
            }
        }
    }
    LaunchedEffect(loggedIn) { reload() }
    LaunchedEffect(Unit) { app.account.changes.collect { if (it == "session" || it == "lagged") reload() } }

    fun attach(s: ServerSession) {
        app.sessions.attach(s.id, s.title.ifBlank { s.hostId?.let { hosts[it]?.label } ?: untitled }, s.hostId)
        nav.navigate(Routes.TERMINAL) { launchSingleTop = true }
    }

    ScreenScaffold(title = stringResource(R.string.section_sessions)) { padding ->
        PullToRefreshBox(isRefreshing = loading, onRefresh = { reload() }, modifier = Modifier.fillMaxSize().padding(padding)) {
            val active = server?.active.orEmpty()
            val shared = server?.shared.orEmpty()
            val recent = server?.recent.orEmpty().take(15)
            if (local.isEmpty() && active.isEmpty() && shared.isEmpty() && recent.isEmpty()) {
                EmptyState(
                    Icons.Outlined.Terminal,
                    stringResource(R.string.sessions_empty_title),
                    stringResource(if (loggedIn == true) R.string.sessions_empty_text_synced else R.string.sessions_empty_text_local),
                )
                return@PullToRefreshBox
            }
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                if (local.isNotEmpty()) {
                    item { SectionLabel(stringResource(R.string.sessions_open_here)) }
                    items(local, key = { it.id }) { s ->
                        val state by s.state.collectAsState()
                        val title by s.title.collectAsState()
                        ListItem(
                            modifier = Modifier.clickable { app.sessions.select(s.id); nav.navigate(Routes.TERMINAL) { launchSingleTop = true } },
                            headlineContent = { Text(title ?: s.label) },
                            supportingContent = {
                                Text(
                                    when (val st = state) {
                                        TermState.Running -> stringResource(
                                            if (s.persistent) R.string.sessions_connected_server else R.string.sessions_connected_ssh,
                                        )
                                        is TermState.Connecting -> st.message.asString()
                                        is TermState.Closed -> st.message.asString()
                                        TermState.Asleep -> stringResource(R.string.sessions_asleep)
                                    },
                                    maxLines = 1,
                                )
                            },
                            leadingContent = {
                                Icon(
                                    if (s.persistent) Icons.Outlined.CloudQueue else Icons.Outlined.PhoneAndroid, null,
                                    tint = when (state) {
                                        TermState.Running -> Brand.Green
                                        is TermState.Connecting -> Brand.Amber
                                        is TermState.Closed -> MaterialTheme.colorScheme.error
                                        TermState.Asleep -> MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            },
                            trailingContent = {
                                IconButton(onClick = { app.sessions.close(s.id) }) {
                                    Icon(Icons.Outlined.Close, stringResource(R.string.common_close))
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                    }
                }
                if (loggedIn == true && active.isNotEmpty()) {
                    item { SectionLabel(stringResource(R.string.sessions_on_server)) }
                    items(active, key = { "a" + it.id }) { s ->
                        ServerSessionRow(s, hosts[s.hostId ?: ""]?.label, onClick = { attach(s) }, onClose = { closing = s })
                    }
                }
                if (loggedIn == true && shared.isNotEmpty()) {
                    item { SectionLabel(stringResource(R.string.sessions_shared)) }
                    items(shared, key = { "s" + it.id }) { s ->
                        ServerSessionRow(s, hosts[s.hostId ?: ""]?.label, onClick = { attach(s) }, onClose = null)
                    }
                }
                if (loggedIn == true && recent.isNotEmpty()) {
                    item { SectionLabel(stringResource(R.string.sessions_recent)) }
                    items(recent, key = { "r" + it.id }) { r ->
                        ListItem(
                            headlineContent = { Text(r.title.ifBlank { hosts[r.hostId ?: ""]?.label ?: untitled }) },
                            supportingContent = {
                                Text(
                                    listOfNotNull(recentStatus(r.status), relativeTime(r.endedAt ?: r.createdAt), r.error)
                                        .joinToString(" · "),
                                    maxLines = 1,
                                )
                            },
                            leadingContent = { Icon(Icons.Outlined.History, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
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
                runCatching { app.core.closeServerSession(s.id) }
                    .onFailure { snackbar.showSnackbar(it.message ?: resources.getString(R.string.sessions_close_failed)) }
                reload()
            }
        }
    }
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
private fun ServerSessionRow(s: ServerSession, hostLabel: String?, onClick: () -> Unit, onClose: (() -> Unit)?) {
    val (text, color) = when (val st = s.state) {
        is ServerSessionState.Running -> stringResource(R.string.session_state_running) to Brand.Green
        is ServerSessionState.Connecting -> st.message to Brand.Amber
        is ServerSessionState.HostOffline -> stringResource(R.string.session_state_host_offline) to Brand.Amber
        is ServerSessionState.Closed -> (st.reason ?: stringResource(R.string.session_state_closed)) to MaterialTheme.colorScheme.error
    }
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(s.title.ifBlank { hostLabel ?: stringResource(R.string.common_session) }) },
        supportingContent = {
            val viewers = s.viewers.size.takeIf { it > 1 }?.let { pluralStringResource(R.plurals.sessions_viewers, it, it) }
            Text(listOfNotNull(text, relativeTime(s.createdAt), viewers).joinToString(" · "), maxLines = 1)
        },
        leadingContent = {
            Box {
                Icon(if (onClose == null) Icons.Outlined.Group else Icons.Outlined.CloudQueue, null, tint = color)
            }
        },
        trailingContent = {
            if (onClose != null) {
                IconButton(onClick = onClose) {
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
