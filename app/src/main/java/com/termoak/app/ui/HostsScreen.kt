package com.termoak.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.termoak.app.MainActivity
import com.termoak.app.R
import com.termoak.app.TermoakApp
import com.termoak.app.term.ServerTerminal
import com.termoak.ffi.HostGroup
import com.termoak.ffi.HostSettings
import com.termoak.ffi.SecretChange
import com.termoak.ffi.SshHost
import kotlinx.coroutines.launch

/**
 * Hosts in the style of Termius: search box, groups as folders and every host
 * with its colored square. With [groupId], the content of a group.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostsScreen(app: TermoakApp, nav: NavHostController, groupId: String?) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val loggedIn by app.account.loggedIn.collectAsState()
    val syncing by app.account.syncing.collectAsState()
    val syncError by app.account.syncError.collectAsState()
    val onServer by app.sessions.onServer.collectAsState()

    var hosts by remember { mutableStateOf<List<SshHost>>(emptyList()) }
    var groups by remember { mutableStateOf<List<HostGroup>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var actionsFor by remember { mutableStateOf<SshHost?>(null) }
    var groupActions by remember { mutableStateOf<HostGroup?>(null) }
    var deleting by remember { mutableStateOf<SshHost?>(null) }
    var deletingGroup by remember { mutableStateOf<HostGroup?>(null) }
    var creating by remember { mutableStateOf(false) }
    var editingGroup by remember { mutableStateOf<HostGroup?>(null) }

    fun reload() {
        hosts = runCatching { app.core.listHosts() }.getOrDefault(emptyList())
        groups = runCatching { app.core.listGroups() }.getOrDefault(emptyList()).sortedBy { it.name.lowercase() }
    }
    LaunchedEffect(Unit) {
        reload()
        if (loggedIn == true && groupId == null) app.account.sync()
    }
    LaunchedEffect(Unit) { app.account.vaultChanged.collect { reload() } }
    LaunchedEffect(syncError) { syncError?.let { snackbar.showSnackbar(it.resolve(resources)) } }

    fun connect(host: SshHost, onServer: Boolean) {
        (context as? MainActivity)?.askNotificationPermission()
        if (onServer) app.sessions.openOnServer(host) else app.sessions.openLocal(host)
        nav.navigate(Routes.TERMINAL) { launchSingleTop = true }
    }
    /** From the server sessions notice: the first tab (the others, on top); if there's none, the list. */
    fun openServerSessions() {
        val ids = onServer.map { it.id }.toSet()
        val tab = app.sessions.list.value.firstOrNull { it is ServerTerminal && it.sessionId in ids }
        if (tab != null) {
            app.sessions.select(tab.id)
            nav.navigate(Routes.TERMINAL) { launchSingleTop = true }
        } else {
            nav.goTab(Routes.SESSIONS)
        }
    }
    fun save(host: SshHost) {
        runCatching { app.core.saveHost(host, SecretChange.Keep) }
            .onFailure { scope.launch { snackbar.showSnackbar(it.message ?: resources.getString(R.string.error_save_failed)) } }
        reload()
        app.account.sync()
    }

    val group = groups.firstOrNull { it.id == groupId }
    val q = query.trim().lowercase()
    val searching = q.isNotEmpty()
    // Searching: in all hosts; otherwise the group's (or the loose ones at the root).
    val visibleHosts = hosts.filter { h ->
        if (searching) listOf(h.label, h.address, h.settings.username ?: "", h.tags.joinToString(" ")).any { it.lowercase().contains(q) }
        else h.groupId == groupId || (groupId == null && groups.none { it.id == h.groupId })
    }.sortedWith(compareByDescending<SshHost> { it.favorite }.thenBy { it.label.lowercase() })
    val visibleGroups = if (searching) emptyList() else groups.filter { g ->
        g.parentId == groupId || (groupId == null && g.parentId != null && groups.none { it.id == g.parentId })
    }
    fun countIn(g: HostGroup): Int = hosts.count { it.groupId == g.id } + groups.filter { it.parentId == g.id }.sumOf { countIn(it) }

    ScreenScaffold(
        title = group?.name ?: stringResource(R.string.section_hosts),
        navigationIcon = if (groupId != null) {
            {
                IconButton(onClick = { nav.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back))
                }
            }
        } else {
            null
        },
        actions = {
            if (loggedIn == true) {
                IconButton(onClick = { app.account.sync() }, enabled = !syncing) {
                    if (syncing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Outlined.Sync, stringResource(R.string.common_sync))
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { creating = true }, shape = RoundedCornerShape(16.dp)) {
                Icon(Icons.Outlined.Add, stringResource(R.string.common_new))
            }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = syncing,
            onRefresh = { if (loggedIn == true) app.account.sync() else reload() },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                if (groupId == null && loggedIn == true && onServer.isNotEmpty()) {
                    item { ServerSessionsNotice(onServer.size) { openServerSessions() } }
                }
                item {
                    TextField(
                        query, { query = it },
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        placeholder = { Text(stringResource(R.string.hosts_search)) },
                        leadingIcon = { Icon(Icons.Outlined.Search, null) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ),
                    )
                }
                if (hosts.isEmpty() && groups.isEmpty()) {
                    item {
                        EmptyState(
                            Icons.Outlined.Dns,
                            stringResource(if (syncing) R.string.common_syncing else R.string.hosts_empty_title),
                            stringResource(if (loggedIn == true) R.string.hosts_empty_text_synced else R.string.hosts_empty_text_local),
                            Modifier.height(420.dp),
                            action = stringResource(R.string.hosts_new_host),
                            onAction = { nav.navigate(Routes.hostEdit(null)) },
                        )
                    }
                }
                if (visibleGroups.isNotEmpty()) {
                    item { SectionLabel(stringResource(R.string.hosts_groups)) }
                    visibleGroups.chunked(2).forEach { pair ->
                        item(key = "g" + pair.first().id) {
                            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                pair.forEach { g ->
                                    GroupCard(g, countIn(g), Modifier.weight(1f), { nav.navigate(Routes.group(g.id)) }, { groupActions = g })
                                }
                                if (pair.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
                if (visibleHosts.isNotEmpty()) {
                    item { SectionLabel(stringResource(if (searching) R.string.hosts_results else R.string.section_hosts)) }
                    items(visibleHosts, key = { it.id }) { host ->
                        HostRow(host, onClick = { connect(host, false) }, onMore = { actionsFor = host })
                    }
                } else if (searching) {
                    item {
                        Text(
                            stringResource(R.string.hosts_no_match, query), Modifier.fillMaxWidth().padding(32.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    // ----- Sheets and dialogs -----

    if (creating) {
        ModalBottomSheet(onDismissRequest = { creating = false }) {
            SheetAction(Icons.Outlined.Dns, stringResource(R.string.hosts_new_host)) {
                creating = false; nav.navigate(Routes.hostEdit(null))
            }
            SheetAction(Icons.Outlined.CreateNewFolder, stringResource(R.string.hosts_new_group)) {
                creating = false
                editingGroup = HostGroup(
                    id = "", name = "", parentId = groupId, color = null,
                    settings = HostSettings(), syncMode = null, updatedAt = 0L,
                )
            }
            Spacer(Modifier.navigationBarsPadding().height(24.dp))
        }
    }
    actionsFor?.let { host ->
        ModalBottomSheet(onDismissRequest = { actionsFor = null }) {
            Row(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                HostTile(host.label, host.os, host.color, size = 48.dp)
                Column(Modifier.padding(start = 16.dp)) {
                    Text(host.label, style = MaterialTheme.typography.titleMedium)
                    Text(
                        (host.settings.username?.let { "$it@" } ?: "") + host.address,
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SheetAction(Icons.Outlined.Terminal, stringResource(R.string.hosts_connect)) { actionsFor = null; connect(host, false) }
            if (loggedIn == true) {
                SheetAction(Icons.Outlined.CloudQueue, stringResource(R.string.hosts_connect_on_server)) {
                    actionsFor = null; connect(host, true)
                }
            }
            SheetAction(Icons.Outlined.Edit, stringResource(R.string.common_edit)) {
                actionsFor = null; nav.navigate(Routes.hostEdit(host.id))
            }
            SheetAction(
                if (host.favorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                stringResource(if (host.favorite) R.string.hosts_favorite_remove else R.string.hosts_favorite_add),
            ) { actionsFor = null; save(host.copy(favorite = !host.favorite)) }
            val copyLabel = stringResource(R.string.hosts_copy_label, host.label)
            SheetAction(Icons.Outlined.ContentCopy, stringResource(R.string.hosts_duplicate)) {
                actionsFor = null; save(host.copy(id = "", label = copyLabel, favorite = false))
            }
            SheetAction(Icons.Outlined.Delete, stringResource(R.string.common_delete), danger = true) {
                actionsFor = null; deleting = host
            }
            Spacer(Modifier.navigationBarsPadding().height(24.dp))
        }
    }
    groupActions?.let { g ->
        ModalBottomSheet(onDismissRequest = { groupActions = null }) {
            Text(g.name, Modifier.padding(horizontal = 24.dp, vertical = 8.dp), style = MaterialTheme.typography.titleMedium)
            SheetAction(Icons.Outlined.DriveFileRenameOutline, stringResource(R.string.hosts_rename)) {
                groupActions = null; editingGroup = g
            }
            SheetAction(Icons.Outlined.Delete, stringResource(R.string.hosts_delete_group), danger = true) {
                groupActions = null; deletingGroup = g
            }
            Spacer(Modifier.navigationBarsPadding().height(24.dp))
        }
    }
    editingGroup?.let { g ->
        var name by remember(g) { mutableStateOf(g.name) }
        AlertDialog(
            onDismissRequest = { editingGroup = null },
            title = { Text(stringResource(if (g.id.isEmpty()) R.string.hosts_new_group else R.string.hosts_rename)) },
            text = {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.common_name)) }, singleLine = true)
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    runCatching { app.core.saveGroup(g.copy(name = name.trim())) }
                        .onFailure { scope.launch { snackbar.showSnackbar(it.message ?: resources.getString(R.string.error_save_failed)) } }
                    editingGroup = null
                    reload()
                    app.account.sync()
                }) { Text(stringResource(R.string.common_save)) }
            },
            dismissButton = { TextButton(onClick = { editingGroup = null }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
    deleting?.let { host ->
        ConfirmDialog(
            title = stringResource(R.string.hosts_delete_title, host.label),
            text = stringResource(R.string.hosts_delete_text),
            confirm = stringResource(R.string.common_delete), destructive = true, onDismiss = { deleting = null },
        ) {
            runCatching { app.core.deleteHost(host.id) }
            reload()
            app.account.sync()
            scope.launch { snackbar.showSnackbar(resources.getString(R.string.hosts_deleted, host.label)) }
        }
    }
    deletingGroup?.let { g ->
        ConfirmDialog(
            title = stringResource(R.string.hosts_delete_group_title, g.name),
            text = stringResource(R.string.hosts_delete_group_text),
            confirm = stringResource(R.string.common_delete), destructive = true, onDismiss = { deletingGroup = null },
        ) {
            runCatching { app.core.deleteGroup(g.id) }
            reload()
            app.account.sync()
        }
    }
}

/** "ssh, user" as in Termius (with the port if it isn't 22). */
@Composable
private fun subtitle(host: SshHost): String {
    val user = host.settings.username?.takeIf { it.isNotBlank() }
    val port = host.settings.port?.takeIf { it != 22u }?.let { stringResource(R.string.hosts_port, it.toString()) }
    return listOfNotNull("ssh", user, port).joinToString(", ")
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HostRow(host: SshHost, onClick: () -> Unit, onMore: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onMore)
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HostTile(host.label, host.os, host.color)
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    host.label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (host.favorite) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Filled.Star, null, Modifier.size(14.dp), tint = Brand.Amber)
                }
            }
            Text(
                subtitle(host), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onMore) {
            Icon(Icons.Outlined.MoreHoriz, stringResource(R.string.common_options), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupCard(group: HostGroup, count: Int, modifier: Modifier, onClick: () -> Unit, onLongClick: () -> Unit) {
    Surface(
        modifier.clip(MaterialTheme.shapes.medium).combinedClickable(onClick = onClick, onLongClick = onLongClick),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(9.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.Folder, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary) }
            Column(Modifier.padding(start = 10.dp)) {
                Text(group.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    pluralStringResource(R.plurals.hosts_count, count, count), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun SheetAction(icon: ImageVector, text: String, danger: Boolean = false, onClick: () -> Unit) {
    val color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (danger) color else MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, Modifier.padding(start = 20.dp), style = MaterialTheme.typography.bodyLarge, color = color)
    }
}

/** Compact notice on Home: you have sessions running on the server. */
@Composable
private fun ServerSessionsNotice(count: Int, onOpen: () -> Unit) {
    Surface(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onOpen),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Row(Modifier.padding(start = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.CloudQueue, null, Modifier.size(18.dp))
            Text(
                pluralStringResource(R.plurals.hosts_server_sessions, count, count),
                Modifier.weight(1f).padding(start = 10.dp), style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = onOpen) { Text(stringResource(R.string.common_open)) }
        }
    }
}
