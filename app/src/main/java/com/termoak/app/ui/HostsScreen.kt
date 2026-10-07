package com.termoak.app.ui

import android.content.ClipData
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.RadioButton
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.termoak.ffi.Snippet
import com.termoak.ffi.TransferMode
import com.termoak.ffi.VaultInfo
import com.termoak.ffi.AccountInfo
import com.termoak.app.data.uid
import com.termoak.app.data.useOnly
import com.termoak.app.data.canWrite
import com.termoak.app.data.AccountView
import com.termoak.app.term.TermSession
import com.termoak.app.userMessage
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Visibility
import android.content.ClipboardManager
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.core.graphics.toColorInt
import androidx.navigation.NavHostController
import com.termoak.app.MainActivity
import com.termoak.app.R
import com.termoak.app.TermoakApp
import com.termoak.app.term.ServerTerminal
import com.termoak.ffi.HostGroup
import com.termoak.ffi.HostSettings
import com.termoak.ffi.SecretChange
import com.termoak.ffi.SshHost
import com.termoak.ffi.SyncMode
import kotlinx.coroutines.launch

/**
 * Hosts in the style of Termius' vault: search box, groups as folder rows and
 * every host with the logo of its system. With [groupId], the content of a
 * group. On wide screens the rows flow into columns.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostsScreen(app: TermoakApp, nav: NavHostController, groupId: String?, groupAccount: String? = null) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val loggedIn by app.accounts.loggedIn.collectAsState()
    val syncing by app.accounts.syncing.collectAsState()
    val syncError by app.accounts.syncError.collectAsState()
    val onServer by app.sessions.onServer.collectAsState()
    val open by app.sessions.list.collectAsState()
    val accountList by app.accounts.list.collectAsState()
    val vaults by app.accounts.vaults.collectAsState()
    val view by app.accounts.view.collectAsState()

    var hosts by remember { mutableStateOf<List<SshHost>>(emptyList()) }
    var groups by remember { mutableStateOf<List<HostGroup>>(emptyList()) }
    var query by rememberSaveable { mutableStateOf("") }
    var actionsFor by remember { mutableStateOf<SshHost?>(null) }
    var groupActions by remember { mutableStateOf<HostGroup?>(null) }
    var deleting by remember { mutableStateOf<SshHost?>(null) }
    var deletingGroup by remember { mutableStateOf<HostGroup?>(null) }
    var creating by remember { mutableStateOf(false) }
    var editingGroup by remember { mutableStateOf<HostGroup?>(null) }
    // Multi-select: a long press starts it, a tap adds or removes a host.
    var selected by rememberSaveable { mutableStateOf(listOf<String>()) }
    val selecting = selected.isNotEmpty()
    var bulkMove by remember { mutableStateOf(false) }
    var bulkDelete by remember { mutableStateOf(false) }
    var bulkSnippet by remember { mutableStateOf(false) }
    // Move to… / Copy to… (another vault, This device or another account).
    var transfer by remember { mutableStateOf<TransferRequest?>(null) }
    var runSnippet by remember { mutableStateOf<Snippet?>(null) }
    val grid = rememberLazyGridState()
    val maxPanes = rememberMaxPanes()
    BackHandler(selecting) { selected = emptyList() }
    fun toggle(host: SshHost) {
        selected = if (host.uid in selected) selected - host.uid else selected + host.uid
    }

    fun reload() {
        // The account and vault shown (inside a group: that group's account).
        val filter = if (groupId != null) app.accounts.scopeFilter(groupAccount) else app.accounts.filter()
        hosts = runCatching { app.core.listHosts(filter) }.getOrDefault(emptyList())
            .filter { groupId == null || it.accountId == groupAccount }
        groups = runCatching { app.core.listGroups(filter) }.getOrDefault(emptyList())
            .filter { groupId == null || it.accountId == groupAccount }.sortedBy { it.name.lowercase() }
    }
    LaunchedEffect(Unit) {
        reload()
        if (loggedIn == true && groupId == null) app.accounts.sync()
    }
    LaunchedEffect(Unit) { app.accounts.itemsChanged.collect { reload() } }
    // Vault chips only when there is more than one vault; account avatars only with several accounts.
    val showVaults = groupId == null && app.accounts.vaultsInView().size > 1 ||
        (accountList.isNotEmpty() && hosts.any { it.accountId == null } && hosts.any { it.accountId != null })
    val showAccounts = accountList.size > 1 && view == AccountView.All
    fun vaultOf(h: SshHost): VaultInfo? = vaults.firstOrNull { it.id == h.vaultId && it.accountId == h.accountId }
    LaunchedEffect(syncError) { syncError?.let { snackbar.showSnackbar(it.resolve(resources)) } }

    fun connect(host: SshHost, onServer: Boolean) {
        (context as? MainActivity)?.askNotificationPermission()
        if (onServer) app.sessions.openOnServer(host) else connectHost(app, host)
        nav.navigate(Routes.TERMINAL) { launchSingleTop = true }
    }
    /** From the server sessions notice: the first tab (the others, on top); if there's none, Connections. */
    fun openServerSessions() {
        val ids = onServer.map { it.id }.toSet()
        val tab = app.sessions.list.value.firstOrNull { it is ServerTerminal && it.sessionId in ids }
        if (tab != null) {
            app.sessions.select(tab.id)
            nav.navigate(Routes.TERMINAL) { launchSingleTop = true }
        } else {
            nav.goTab(Routes.CONNECTIONS)
        }
    }
    /** Connect N: a terminal for each selected host (side by side on a wide screen). */
    fun connectSelected() {
        val chosen = hosts.filter { it.uid in selected }
        if (chosen.isEmpty()) return
        (context as? MainActivity)?.askNotificationPermission()
        val opened = chosen.map { connectHost(app, it) }
        if (maxPanes >= 2 && opened.size >= 2) app.sessions.setSplit(opened.take(maxPanes).map { it.id })
        app.sessions.select(opened.first().id)
        selected = emptyList()
        nav.navigate(Routes.TERMINAL) { launchSingleTop = true }
    }
    fun save(host: SshHost) {
        runCatching { app.core.saveHost(host, SecretChange.Keep) }
            .onFailure { scope.launch { snackbar.showSnackbar(it.userMessage(resources, R.string.error_save_failed)) } }
        reload()
        app.accounts.sync()
    }
    fun transferOf(mode: TransferMode, list: List<SshHost>) {
        transfer = TransferRequest(mode, list.map { TransferItem(it.accountId, it.id, it.vaultId) })
    }
    fun copyAddress(host: SshHost) {
        context.getSystemService(ClipboardManager::class.java)
            ?.setPrimaryClip(ClipData.newPlainText(host.label, host.address))
        scope.launch { snackbar.showSnackbar(resources.getString(R.string.hosts_address_copied)) }
    }
    fun newGroup() {
        // In a group: its account; at the root, where new items go.
        val place = if (groupId != null) com.termoak.app.data.Place(groupAccount, groups.firstOrNull { it.id == groupId }?.vaultId)
        else app.accounts.defaultPlace()
        editingGroup = HostGroup(
            id = "", name = "", parentId = groupId, color = null,
            settings = HostSettings(), syncMode = if (place.device && accountList.isNotEmpty()) SyncMode.DEVICE_ONLY else null,
            updatedAt = 0L, accountId = place.account, vaultId = place.vault,
        )
    }

    val group = groups.firstOrNull { it.id == groupId && it.accountId == groupAccount }
    val q = query.trim().lowercase()
    val searching = q.isNotEmpty()
    // Searching: in all hosts; otherwise the group's (or the loose ones at the root).
    val visibleHosts = hosts.filter { h ->
        if (searching) listOf(h.label, h.address, h.settings.username ?: "", h.tags.joinToString(" ")).any { it.lowercase().contains(q) }
        else h.groupId == groupId || (groupId == null && groups.none { it.id == h.groupId && it.accountId == h.accountId })
    }.sortedWith(compareByDescending<SshHost> { it.favorite }.thenBy { it.label.lowercase() })
    val visibleGroups = if (searching) emptyList() else groups.filter { g ->
        g.parentId == groupId || (groupId == null && g.parentId != null && groups.none { it.id == g.parentId && it.accountId == g.accountId })
    }
    fun countIn(g: HostGroup): Int = hosts.count { it.groupId == g.id && it.accountId == g.accountId } +
        groups.filter { it.parentId == g.id && it.accountId == g.accountId }.sumOf { countIn(it) }
    val connectedHosts = open.mapNotNull { s -> s.hostId?.let { com.termoak.app.data.uidOf(s.accountId, it) } }.toSet()
    // "All accounts" with several places: hosts grouped by account (This device first).
    val scopes: List<AccountInfo?> = if (showAccounts && groupId == null && !searching) {
        listOf<AccountInfo?>(null) + accountList
    } else listOf(null)
    fun inScope(scope: AccountInfo?, accountId: String?) = scopes.size == 1 || accountId == scope?.id

    val actions: @Composable RowScope.() -> Unit = {
        if (loggedIn == true) {
            IconButton(onClick = { app.accounts.sync() }, enabled = !syncing) {
                if (syncing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Icon(Icons.Outlined.Sync, stringResource(R.string.common_sync))
            }
        }
    }
    val fab: @Composable () -> Unit = {
        FloatingActionButton(onClick = { creating = true }, shape = RoundedCornerShape(16.dp)) {
            Icon(Icons.Outlined.Add, stringResource(R.string.common_new))
        }
    }
    val body: @Composable (PaddingValues) -> Unit = { padding ->
        PullToRefreshBox(
            isRefreshing = syncing,
            onRefresh = { if (loggedIn == true) app.accounts.sync() else reload() },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            LazyVerticalGrid(
                GridCells.Adaptive(minSize = 340.dp),
                Modifier.fillMaxSize(),
                state = grid,
                contentPadding = PaddingValues(bottom = 96.dp),
            ) {
                if (groupId == null && loggedIn == true && onServer.isNotEmpty()) {
                    full { ServerSessionsNotice(onServer.size) { openServerSessions() } }
                }
                full { SearchField(query) { query = it } }
                if (hosts.isEmpty() && groups.isEmpty()) {
                    full {
                        EmptyState(
                            Icons.Outlined.Dns,
                            stringResource(if (syncing) R.string.common_syncing else R.string.hosts_empty_title),
                            stringResource(if (loggedIn == true) R.string.hosts_empty_text_synced else R.string.hosts_empty_text_local),
                            Modifier.height(420.dp),
                            action = stringResource(R.string.hosts_new_host),
                            onAction = { nav.navigate(Routes.hostEdit(null)) },
                        )
                    }
                } else if (groupId != null && visibleGroups.isEmpty() && visibleHosts.isEmpty() && !searching) {
                    full {
                        EmptyState(
                            Icons.Outlined.Folder, stringResource(R.string.hosts_group_empty_title),
                            stringResource(R.string.hosts_group_empty_text), Modifier.height(360.dp),
                            action = stringResource(R.string.hosts_new_host), onAction = { nav.navigate(Routes.hostEdit(null)) },
                        )
                    }
                }
                scopes.forEach { sc ->
                    val scGroups = visibleGroups.filter { inScope(sc, it.accountId) }
                    val scHosts = visibleHosts.filter { inScope(sc, it.accountId) }
                    if (scopes.size > 1 && (scGroups.isNotEmpty() || scHosts.isNotEmpty())) {
                        full { ScopeLabel(sc) }
                    }
                    if (scGroups.isNotEmpty()) {
                        if (scopes.size == 1) full { SectionLabel(stringResource(R.string.hosts_groups)) }
                        items(scGroups, key = { "g" + it.uid }) { g ->
                            // While selecting, a group selects (or unselects) its hosts instead of opening.
                            GroupRow(g, countIn(g), {
                                if (selecting) {
                                    val ids = hostsIn(g, hosts, groups).map { it.uid }
                                    selected = if (ids.all { it in selected }) selected - ids.toSet() else (selected + ids).distinct()
                                } else {
                                    nav.navigate(Routes.group(g.id, g.accountId))
                                }
                            }, { groupActions = g })
                        }
                    }
                    if (scHosts.isNotEmpty()) {
                        if (scopes.size == 1) {
                            full { SectionLabel(stringResource(if (searching) R.string.hosts_results else R.string.section_hosts)) }
                        }
                        items(scHosts, key = { it.uid }) { host ->
                            val v = vaultOf(host)
                            HostRow(
                                host, connected = host.uid in connectedHosts,
                                selected = if (selecting) host.uid in selected else null,
                                onClick = { if (selecting) toggle(host) else connect(host, false) },
                                onLongClick = { toggle(host) },
                                onMore = { actionsFor = host }, onTag = { query = it },
                                vault = if (!showVaults) null else if (host.accountId == null) stringResource(R.string.vault_this_device) else v?.let { vaultName(it) },
                                vaultColor = if (host.accountId == null) MaterialTheme.colorScheme.onSurfaceVariant else vaultColor(v),
                                account = if (showAccounts) accountList.firstOrNull { it.id == host.accountId } else null,
                            )
                        }
                    }
                }
                if (visibleHosts.isEmpty() && searching) {
                    full {
                        EmptyState(
                            Icons.Outlined.SearchOff, stringResource(R.string.hosts_no_match, query),
                            stringResource(R.string.hosts_no_match_text), Modifier.height(320.dp),
                        )
                    }
                }
            }
        }
    }

    if (selecting) {
        val count = selected.size
        ScreenScaffold(
            title = pluralStringResource(R.plurals.bulk_selected, count, count),
            navigationIcon = {
                IconButton(onClick = { selected = emptyList() }) { Icon(Icons.Outlined.Close, stringResource(R.string.bulk_clear)) }
            },
            actions = {
                IconButton(onClick = {
                    val ids = visibleHosts.map { it.uid }
                    selected = if (ids.all { it in selected }) selected - ids.toSet() else (selected + ids).distinct()
                }) { Icon(Icons.Outlined.SelectAll, stringResource(R.string.bulk_select_all)) }
                IconButton(onClick = { bulkDelete = true }) { Icon(Icons.Outlined.Delete, stringResource(R.string.common_delete)) }
                Box {
                    var menu by remember { mutableStateOf(false) }
                    IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.common_more)) }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(
                            { Text(stringResource(R.string.bulk_move)) }, { menu = false; bulkMove = true },
                            leadingIcon = { Icon(Icons.AutoMirrored.Outlined.DriveFileMove, null) },
                        )
                        DropdownMenuItem(
                            { Text(stringResource(R.string.multi_run_snippet)) }, { menu = false; bulkSnippet = true },
                            leadingIcon = { Icon(Icons.Outlined.Code, null) },
                        )
                        if (accountList.isNotEmpty()) {
                            val chosen = hosts.filter { it.uid in selected }
                            if (chosen.all { it.access.canWrite() }) {
                                DropdownMenuItem(
                                    { Text(stringResource(R.string.transfer_move_to)) }, { menu = false; transferOf(TransferMode.MOVE, chosen) },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Outlined.DriveFileMove, null) },
                                )
                            }
                            if (chosen.none { it.access.useOnly() }) {
                                DropdownMenuItem(
                                    { Text(stringResource(R.string.transfer_copy_to)) }, { menu = false; transferOf(TransferMode.COPY, chosen) },
                                    leadingIcon = { Icon(Icons.Outlined.ContentPaste, null) },
                                )
                            }
                        }
                    }
                }
                Button(onClick = { connectSelected() }, Modifier.padding(end = 8.dp)) {
                    Text(pluralStringResource(R.plurals.bulk_connect, count, count))
                }
            },
            content = body,
        )
    } else if (groupId == null) {
        VaultScaffold(VaultSection.HOSTS, nav, actions = actions, floatingActionButton = fab, content = body)
    } else {
        ScreenScaffold(
            title = group?.name ?: stringResource(R.string.section_hosts),
            subtitle = group?.let { pluralStringResource(R.plurals.hosts_count, countIn(it), countIn(it)) },
            navigationIcon = {
                IconButton(onClick = { nav.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back))
                }
            },
            actions = actions,
            floatingActionButton = fab,
            content = body,
        )
    }

    // ----- Sheets and dialogs -----

    if (creating) {
        ModalBottomSheet(onDismissRequest = { creating = false }) {
            SheetAction(Icons.Outlined.Dns, stringResource(R.string.hosts_new_host)) {
                creating = false; nav.navigate(Routes.hostEdit(null))
            }
            SheetAction(Icons.Outlined.CreateNewFolder, stringResource(R.string.hosts_new_group)) {
                creating = false; newGroup()
            }
            SheetAction(Icons.Outlined.Key, stringResource(R.string.hosts_new_key)) {
                creating = false; nav.goVault(Routes.keys(KeysAction.GENERATE))
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SheetAction(Icons.Outlined.Description, stringResource(R.string.import_title), stringResource(R.string.hosts_import_config_hint)) {
                creating = false; nav.navigate(Routes.IMPORT)
            }
            SheetAction(Icons.Outlined.FileDownload, stringResource(R.string.keys_import_title)) {
                creating = false; nav.goVault(Routes.keys(KeysAction.IMPORT))
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
                        listOfNotNull((host.settings.username?.let { "$it@" } ?: "") + host.address, host.osVersion)
                            .joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            val v = vaultOf(host)
            if (host.access.useOnly() || (accountList.isNotEmpty() && (host.accountId == null || v != null))) {
                Row(Modifier.padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VaultChip(
                        if (host.accountId == null) stringResource(R.string.vault_this_device) else v?.let { vaultName(it) } ?: "",
                        if (host.accountId == null) MaterialTheme.colorScheme.onSurfaceVariant else vaultColor(v),
                        useOnly = host.access.useOnly(),
                    )
                    accountList.firstOrNull { it.id == host.accountId }?.takeIf { accountList.size > 1 }?.let {
                        Text(it.email, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (host.access.useOnly()) {
                Text(
                    stringResource(if (v?.strict == true) R.string.hosts_use_only_strict else R.string.hosts_use_only),
                    Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SheetAction(Icons.Outlined.Terminal, stringResource(R.string.hosts_connect)) { actionsFor = null; connect(host, false) }
            // Server sessions: on the host's account, which has to be signed in.
            val hostAccount = accountList.firstOrNull { it.id == host.accountId }
            if (hostAccount?.status == com.termoak.ffi.AccountStatus.ACTIVE) {
                SheetAction(Icons.Outlined.CloudQueue, stringResource(R.string.hosts_connect_on_server)) {
                    actionsFor = null; connect(host, true)
                }
            }
            SheetAction(Icons.Outlined.Folder, stringResource(R.string.files_sftp)) {
                actionsFor = null; nav.openFiles(filesSourceOf(app, host))
            }
            val writable = host.access.canWrite()
            SheetAction(if (writable) Icons.Outlined.Edit else Icons.Outlined.Visibility, stringResource(if (writable) R.string.common_edit else R.string.hosts_view)) {
                actionsFor = null; nav.navigate(Routes.hostEdit(host.id, host.accountId))
            }
            if (writable) {
                SheetAction(
                    if (host.favorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                    stringResource(if (host.favorite) R.string.hosts_favorite_remove else R.string.hosts_favorite_add),
                ) { actionsFor = null; save(host.copy(favorite = !host.favorite)) }
                val copyLabel = stringResource(R.string.hosts_copy_label, host.label)
                SheetAction(Icons.Outlined.ContentCopy, stringResource(R.string.hosts_duplicate)) {
                    actionsFor = null; save(host.copy(id = "", label = copyLabel, favorite = false))
                }
            }
            SheetAction(Icons.Outlined.Link, stringResource(R.string.hosts_copy_address)) {
                actionsFor = null; copyAddress(host)
            }
            if (accountList.isNotEmpty()) {
                if (writable) {
                    SheetAction(Icons.AutoMirrored.Outlined.DriveFileMove, stringResource(R.string.transfer_move_to)) {
                        actionsFor = null; transferOf(TransferMode.MOVE, listOf(host))
                    }
                }
                if (!host.access.useOnly()) {
                    SheetAction(Icons.Outlined.ContentPaste, stringResource(R.string.transfer_copy_to)) {
                        actionsFor = null; transferOf(TransferMode.COPY, listOf(host))
                    }
                }
            }
            if (writable) {
                SheetAction(Icons.Outlined.Delete, stringResource(R.string.common_delete), danger = true) {
                    actionsFor = null; deleting = host
                }
            }
            Spacer(Modifier.navigationBarsPadding().height(24.dp))
        }
    }
    groupActions?.let { g ->
        ModalBottomSheet(onDismissRequest = { groupActions = null }) {
            Text(g.name, Modifier.padding(horizontal = 24.dp, vertical = 8.dp), style = MaterialTheme.typography.titleMedium)
            if (g.access.canWrite()) {
                SheetAction(Icons.Outlined.DriveFileRenameOutline, stringResource(R.string.hosts_rename)) {
                    groupActions = null; editingGroup = g
                }
                if (accountList.isNotEmpty()) {
                    SheetAction(Icons.AutoMirrored.Outlined.DriveFileMove, stringResource(R.string.transfer_move_to)) {
                        groupActions = null
                        transfer = TransferRequest(TransferMode.MOVE, listOf(TransferItem(g.accountId, g.id, g.vaultId)))
                    }
                }
            }
            if (accountList.isNotEmpty() && !g.access.useOnly()) {
                SheetAction(Icons.Outlined.ContentPaste, stringResource(R.string.transfer_copy_to)) {
                    groupActions = null
                    transfer = TransferRequest(TransferMode.COPY, listOf(TransferItem(g.accountId, g.id, g.vaultId)))
                }
            }
            if (g.access.canWrite()) {
                SheetAction(Icons.Outlined.Delete, stringResource(R.string.hosts_delete_group), danger = true) {
                    groupActions = null; deletingGroup = g
                }
            }
            if (!g.access.canWrite()) {
                Text(
                    stringResource(R.string.vault_use_only_note), Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
                        .onFailure { scope.launch { snackbar.showSnackbar(it.userMessage(resources, R.string.error_save_failed)) } }
                    editingGroup = null
                    reload()
                    app.accounts.sync()
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
            val r = runCatching { app.core.deleteHost(host.id, host.accountId) }
            reload()
            app.accounts.sync()
            scope.launch {
                snackbar.showSnackbar(
                    r.exceptionOrNull()?.userMessage(resources, R.string.error_save_failed) ?: resources.getString(R.string.hosts_deleted, host.label),
                )
            }
        }
    }
    if (bulkMove) {
        val chosen = hosts.filter { it.uid in selected }
        // Groups live in a place: only those of the hosts' own place (all in one).
        val place = chosen.map { it.accountId to it.vaultId }.distinct().singleOrNull()
        if (place == null) {
            LaunchedEffect(Unit) {
                bulkMove = false
                snackbar.showSnackbar(resources.getString(R.string.bulk_move_one_place))
            }
            return
        }
        MoveToGroupDialog(groups.filter { it.accountId == place.first && it.vaultId == place.second }, chosen.size, onDismiss = { bulkMove = false }) { target ->
            bulkMove = false
            var failed = 0
            chosen.forEach { h ->
                runCatching { app.core.saveHost(h.copy(groupId = target), SecretChange.Keep) }.onFailure { failed++ }
            }
            selected = emptyList()
            reload()
            app.accounts.sync()
            val name = groups.firstOrNull { it.id == target }?.name ?: resources.getString(R.string.host_no_group)
            val moved = chosen.size - failed
            scope.launch { snackbar.showSnackbar(resources.getQuantityString(R.plurals.bulk_moved, moved, moved, name)) }
        }
    }
    if (bulkDelete) {
        val count = selected.size
        ConfirmDialog(
            title = pluralStringResource(R.plurals.bulk_delete_title, count, count),
            text = pluralStringResource(R.plurals.bulk_delete_text, count, count),
            confirm = stringResource(R.string.common_delete), destructive = true, onDismiss = { bulkDelete = false },
        ) {
            val chosen = hosts.filter { it.uid in selected }
            val deleted = chosen.count { h -> runCatching { app.core.deleteHost(h.id, h.accountId) }.isSuccess }
            selected = emptyList()
            reload()
            app.accounts.sync()
            scope.launch { snackbar.showSnackbar(resources.getQuantityString(R.plurals.bulk_deleted, deleted, deleted)) }
        }
    }
    if (bulkSnippet) {
        SnippetPickerSheet(app, onDismiss = { bulkSnippet = false }) { sn -> bulkSnippet = false; runSnippet = sn }
    }
    runSnippet?.let { sn ->
        RunSnippetSheet(app, sn, initialHosts = selected.toSet(), onDismiss = { runSnippet = null }) {
            runSnippet = null
            selected = emptyList()
        }
    }
    deletingGroup?.let { g ->
        ConfirmDialog(
            title = stringResource(R.string.hosts_delete_group_title, g.name),
            text = stringResource(R.string.hosts_delete_group_text),
            confirm = stringResource(R.string.common_delete), destructive = true, onDismiss = { deletingGroup = null },
        ) {
            runCatching { app.core.deleteGroup(g.id, g.accountId) }
                .onFailure { scope.launch { snackbar.showSnackbar(it.userMessage(resources, R.string.error_save_failed)) } }
            reload()
            app.accounts.sync()
        }
    }
    transfer?.let { t ->
        TransferFlow(app, t, onDismiss = { transfer = null }) {
            transfer = null
            selected = emptyList()
            reload()
        }
    }
}

/**
 * Connects to [host] from the phone, or through its server when it is in a
 * Strict vault where you are Use only (its secrets never leave the server).
 */
fun connectHost(app: TermoakApp, host: SshHost): TermSession =
    if (isStrictUseOnly(app, host)) app.sessions.openOnServer(host) else app.sessions.openLocal(host)

/** [host] is in a Strict vault where you are Use only: connections and files go through the server. */
fun isStrictUseOnly(app: TermoakApp, host: SshHost): Boolean =
    host.access.useOnly() && host.accountId != null &&
        app.accounts.vaults.value.any { it.id == host.vaultId && it.accountId == host.accountId && it.strict }

/** Title of the hosts of one place in "All accounts": This device, or an account. */
@Composable
private fun ScopeLabel(account: AccountInfo?) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (account == null) {
            Icon(Icons.Outlined.PhoneAndroid, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            AccountAvatar(account, 22.dp)
        }
        Text(
            account?.email ?: stringResource(R.string.vault_this_device), Modifier.padding(start = 10.dp),
            style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** An item across the whole row of the grid (titles, search, notices). */
private fun LazyGridScope.full(content: @Composable () -> Unit) {
    item(span = { GridItemSpan(maxLineSpan) }) { content() }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    TextField(
        query, onChange,
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        placeholder = { Text(stringResource(R.string.hosts_search)) },
        leadingIcon = { Icon(Icons.Outlined.Search, null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onChange("") }) { Icon(Icons.Outlined.Close, stringResource(R.string.hosts_search_clear)) }
            }
        },
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

/** "ssh, user" as in Termius (with the port if it isn't 22). */
@Composable
private fun subtitle(host: SshHost): String {
    val user = host.settings.username?.takeIf { it.isNotBlank() }
    val port = host.settings.port?.takeIf { it != 22u }?.let { stringResource(R.string.hosts_port, it.toString()) }
    return listOfNotNull("ssh", user, port).joinToString(", ")
}

/** Hosts of group [group] and of its subgroups. */
private fun hostsIn(group: HostGroup, hosts: List<SshHost>, groups: List<HostGroup>): List<SshHost> =
    hosts.filter { it.groupId == group.id && it.accountId == group.accountId } +
        groups.filter { it.parentId == group.id && it.accountId == group.accountId }.flatMap { hostsIn(it, hosts, groups) }

/**
 * A host. [selected] is `null` outside multi-select; inside, the tile turns
 * into a check mark when the host is selected.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HostRow(
    host: SshHost,
    connected: Boolean,
    selected: Boolean?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMore: () -> Unit,
    onTag: (String) -> Unit,
    /** Its vault (or "This device"), when there is more than one. */
    vault: String? = null,
    vaultColor: Color = MaterialTheme.colorScheme.primary,
    /** Its account, in "All accounts" with several. */
    account: AccountInfo? = null,
) {
    val haptics = LocalHapticFeedback.current
    Row(
        Modifier.fillMaxWidth()
            .background(if (selected == true) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onLongClick()
            })
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            if (selected == true) {
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Outlined.Check, stringResource(R.string.bulk_selected_cd), tint = MaterialTheme.colorScheme.onPrimary) }
            } else {
                HostTile(host.label, host.os, host.color, size = 44.dp)
            }
            // The account it belongs to (with several accounts).
            if (account != null && selected != true) {
                Box(Modifier.align(Alignment.TopStart).offset((-4).dp, (-4).dp)) { AccountAvatar(account, 16.dp) }
            }
            // An open terminal on this host: a dot on the corner of its tile.
            if (connected) {
                Box(
                    Modifier.align(Alignment.BottomEnd).offset(3.dp, 3.dp).size(14.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface).padding(2.dp).clip(CircleShape).background(Brand.Green),
                )
            }
        }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    host.label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (host.favorite) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Filled.Star, stringResource(R.string.hosts_favorite), Modifier.size(14.dp), tint = Brand.Amber)
                }
                if (host.access.useOnly()) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Outlined.Lock, stringResource(R.string.vault_use_only), Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(
                subtitle(host), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (host.tags.isNotEmpty() || vault != null) {
                Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    vault?.let { VaultChip(it, vaultColor, useOnly = host.access.useOnly()) }
                    host.tags.take(3).forEach { TagChip(it) { onTag(it) } }
                    if (host.tags.size > 3) TagChip("+${host.tags.size - 3}", onClick = null)
                }
            }
        }
        IconButton(onClick = onMore) {
            Icon(Icons.Outlined.MoreVert, stringResource(R.string.common_options), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Small tag of a host; tapping it searches for it. */
@Composable
private fun TagChip(text: String, onClick: (() -> Unit)?) {
    Text(
        text,
        Modifier.clip(RoundedCornerShape(6.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 6.dp, vertical = 1.dp),
        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupRow(group: HostGroup, count: Int, onClick: () -> Unit, onMore: () -> Unit) {
    val tint = group.color?.let { runCatching { Color(it.toColorInt()) }.getOrNull() } ?: MaterialTheme.colorScheme.primary
    Row(
        Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onMore)
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(tint.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.Folder, null, Modifier.size(24.dp), tint = tint) }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(group.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                pluralStringResource(R.plurals.hosts_count, count, count), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onMore) {
            Icon(Icons.Outlined.MoreVert, stringResource(R.string.common_options), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SheetAction(icon: ImageVector, text: String, hint: String? = null, danger: Boolean = false, onClick: () -> Unit) {
    val color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (danger) color else MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.padding(start = 20.dp)) {
            Text(text, style = MaterialTheme.typography.bodyLarge, color = color)
            if (hint != null) {
                Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Compact notice on the Vault: you have sessions running on the server. */
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

/** "Move to group": the groups (and "No group") to choose from. */
@Composable
private fun MoveToGroupDialog(groups: List<HostGroup>, count: Int, onDismiss: () -> Unit, onMove: (String?) -> Unit) {
    var target by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(pluralStringResource(R.plurals.bulk_move_title, count, count)) },
        text = {
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                val options = listOf<Pair<String?, String>>(null to "") + groups.map { it.id to it.name }
                items(options, key = { it.first ?: "" }) { (id, name) ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { target = id }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(target == id, { target = id })
                        Icon(
                            if (id == null) Icons.Outlined.FolderOff else Icons.Outlined.Folder, null,
                            Modifier.padding(end = 10.dp).size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(name.ifEmpty { stringResource(R.string.host_no_group) }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onMove(target) }) { Text(stringResource(R.string.bulk_move_action)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}
