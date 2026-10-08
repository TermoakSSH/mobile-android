package com.termoak.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FiberManualRecord
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import androidx.navigation.NavHostController
import com.termoak.app.MainActivity
import com.termoak.app.R
import com.termoak.app.TermoakApp
import com.termoak.app.data.AccountView
import com.termoak.app.data.HostProtocol
import com.termoak.app.data.QuickTarget
import com.termoak.app.data.canWrite
import com.termoak.app.data.isTelnet
import com.termoak.app.data.uid
import com.termoak.app.data.useOnly
import com.termoak.app.term.ServerTerminal
import com.termoak.app.term.TermSession
import com.termoak.app.userMessage
import com.termoak.ffi.AccountInfo
import com.termoak.ffi.HostGroup
import com.termoak.ffi.HostSettings
import com.termoak.ffi.SecretChange
import com.termoak.ffi.Snippet
import com.termoak.ffi.SshHost
import com.termoak.ffi.SyncMode
import com.termoak.ffi.TransferMode
import com.termoak.ffi.VaultInfo
import com.termoak.ffi.VaultKind
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
    var tunnelsOf by remember { mutableStateOf<SshHost?>(null) }
    var deletingGroup by remember { mutableStateOf<HostGroup?>(null) }
    var creating by remember { mutableStateOf(false) }
    var editingGroup by remember { mutableStateOf<HostGroup?>(null) }
    // Multi-select: a long press starts it, a tap adds or removes a host.
    var selected by rememberSaveable { mutableStateOf(listOf<String>()) }
    val selecting = selected.isNotEmpty()
    // Hosts to move to a group (the selected ones, or one from its menu).
    var moving by remember { mutableStateOf<List<SshHost>?>(null) }
    var bulkDelete by remember { mutableStateOf(false) }
    var bulkSnippet by remember { mutableStateOf(false) }
    // Move to… / Copy to… (another vault, This device or another account).
    var transfer by remember { mutableStateOf<TransferRequest?>(null) }
    var runSnippet by remember { mutableStateOf<Snippet?>(null) }
    val grid = rememberLazyGridState()
    val maxPanes = rememberMaxPanes()
    // Desktop layout (wide windows): the desktop's Hosts view, with the editor in a panel on the right.
    val desktopWindow = LocalDesktop.current
    val desktop = desktopWindow && groupId == null
    // The host in the editor panel: its id ("new" for a new one) and account.
    var editing by rememberSaveable { mutableStateOf<Pair<String, String?>?>(null) }
    // The group of a new host in the desktop layout's editor (the chip chosen).
    var newInGroup by remember { mutableStateOf<HostGroup?>(null) }
    // The group chip of the desktop view: "all", "fav", "none" or a group's uid.
    var chip by rememberSaveable { mutableStateOf(CHIP_ALL) }
    // Keyboard: Ctrl+F (and Ctrl+Shift+K from anywhere) goes to the search box.
    val searchFocus = remember { FocusRequester() }
    fun focusSearch() {
        scope.launch {
            grid.scrollToItem(0)
            withFrameNanos { }
            runCatching { searchFocus.requestFocus() }
        }
    }
    val searchAsked by KeyShortcuts.hostSearch.collectAsState()
    LaunchedEffect(searchAsked) {
        if (searchAsked) {
            KeyShortcuts.hostSearch.value = false
            focusSearch()
        }
    }
    UnhandledKeyHandler { e ->
        if (e.isCtrlPressed && !e.isShiftPressed && e.keyCode == android.view.KeyEvent.KEYCODE_F) {
            focusSearch()
            true
        } else {
            false
        }
    }
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
    fun inSharedVault(h: SshHost): Boolean = vaultOf(h)?.let { it.kind != VaultKind.PERSONAL } == true
    LaunchedEffect(syncError) { syncError?.let { snackbar.showSnackbar(it.resolve(resources)) } }

    fun connect(host: SshHost, onServer: Boolean, record: Boolean? = null) {
        (context as? MainActivity)?.askNotificationPermission()
        if (onServer) app.sessions.openOnServer(host, record) else connectHost(app, host)
        nav.navigate(Routes.TERMINAL) { launchSingleTop = true }
    }
    /** The host's editor: a panel on the right in the desktop layout, a screen otherwise. */
    fun edit(host: SshHost?) {
        if (desktop) {
            // A new host while a group's chip is chosen: in that group.
            newInGroup = if (host == null) groups.firstOrNull { it.uid == chip } else null
            editing = (host?.id ?: NEW_HOST) to (host?.accountId ?: newInGroup?.accountId)
        } else nav.navigate(Routes.hostEdit(host?.id, host?.accountId))
    }
    /** A new host: inside the group on screen, if any. */
    fun newHost() {
        if (groupId != null) nav.navigate(Routes.hostEdit(null, groupAccount, groupId)) else edit(null)
    }
    /** A new terminal for [host] beside the one on screen (or in the split view already open). */
    fun connectInSplit(host: SshHost) {
        (context as? MainActivity)?.askNotificationPermission()
        val split = app.sessions.split.value
        val before = app.sessions.active.value?.takeIf { app.sessions.get(it) != null }
        val base = if (split.on) split.panes else listOfNotNull(before)
        val opened = connectHost(app, host)
        if (base.isNotEmpty() && maxPanes >= 2) {
            app.sessions.setSplit(base.take(minOf(maxPanes, com.termoak.app.term.SplitState.MAX_PANES) - 1) + opened.id)
            app.sessions.select(opened.id)
        }
        nav.showTerminal()
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
    /** A terminal for each of [chosen] ([split]: side by side, as many as fit, on a wide screen). */
    fun connectAll(chosen: List<SshHost>, split: Boolean) {
        if (chosen.isEmpty()) return
        (context as? MainActivity)?.askNotificationPermission()
        val opened = chosen.map { connectHost(app, it) }
        if (split && maxPanes >= 2 && opened.size >= 2) app.sessions.setSplit(opened.take(maxPanes).map { it.id })
        app.sessions.select(opened.first().id)
        nav.navigate(Routes.TERMINAL) { launchSingleTop = true }
    }
    /** Connect N: a terminal for each selected host (side by side on a wide screen). */
    fun connectSelected() {
        val chosen = hosts.filter { it.uid in selected }
        if (chosen.isEmpty()) return
        connectAll(chosen, split = true)
        selected = emptyList()
    }
    /** The hosts of a group and of the groups inside it, in list order. */
    fun hostsIn(g: HostGroup): List<SshHost> {
        val ids = mutableSetOf(g.id)
        val pending = ArrayDeque(listOf(g))
        while (pending.isNotEmpty()) {
            val next = pending.removeLast()
            groups.filter { it.parentId == next.id && it.accountId == g.accountId && it.id !in ids }.forEach {
                ids += it.id
                pending += it
            }
        }
        return hosts.filter { it.accountId == g.accountId && it.groupId in ids }
            .sortedWith(compareByDescending<SshHost> { it.favorite }.thenBy { it.label.lowercase() })
    }
    /** Connects to an address typed in the search (a saved host with it, or a new one). */
    fun quickConnectTo(target: QuickTarget) {
        val host = runCatching { quickConnectHost(app, hosts, target) }
            .onFailure { scope.launch { snackbar.showSnackbar(it.userMessage(resources, R.string.error_save_failed)) } }
            .getOrNull() ?: return
        query = ""
        reload()
        connect(host, false)
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

    /**
     * What can be done with [host]: the sheet of its "⋮" on phones, its
     * context menu in the desktop layout ([menu], which adds connect in a
     * split view, move to group and select).
     */
    @Composable
    fun hostActions(host: SshHost, menu: Boolean): List<ItemAction> = buildList {
        add(ItemAction(Icons.Outlined.Terminal, stringResource(R.string.hosts_connect), 0) { connect(host, false) })
        if (menu && maxPanes >= 2) {
            add(ItemAction(Icons.Outlined.GridView, stringResource(R.string.hosts_connect_split), 0) { connectInSplit(host) })
        }
        // Server sessions: on the host's account, which has to be signed in.
        val hostAccount = accountList.firstOrNull { it.id == host.accountId }
        // Telnet hosts: no server sessions or SFTP.
        if (hostAccount?.status == com.termoak.ffi.AccountStatus.ACTIVE && !host.isTelnet) {
            add(ItemAction(Icons.Outlined.CloudQueue, stringResource(R.string.hosts_connect_on_server), 0) { connect(host, true) })
            // Recorded on the server, for its activity and download (the host's setting may already record every session).
            if (host.settings.recordSessions != true) {
                add(ItemAction(Icons.Outlined.FiberManualRecord, stringResource(R.string.hosts_connect_on_server_recorded), 0) { connect(host, true, record = true) })
            }
        }
        if (!host.isTelnet) {
            add(ItemAction(Icons.Outlined.Folder, stringResource(R.string.files_sftp), 0) { nav.openFiles(filesSourceOf(app, host)) })
            // Its tunnels: start, stop, stats (tunnels go over SSH).
            add(ItemAction(Icons.Outlined.SwapHoriz, stringResource(R.string.section_tunnels), 0) { tunnelsOf = host })
        }
        val writable = host.access.canWrite()
        add(
            ItemAction(
                if (writable) Icons.Outlined.Edit else Icons.Outlined.Visibility,
                stringResource(if (writable) R.string.common_edit else R.string.hosts_view), 1,
            ) { edit(host) },
        )
        if (writable) {
            add(
                ItemAction(
                    if (host.favorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                    stringResource(if (host.favorite) R.string.hosts_favorite_remove else R.string.hosts_favorite_add), 1,
                ) { save(host.copy(favorite = !host.favorite)) },
            )
            val copyLabel = stringResource(R.string.hosts_copy_label, host.label)
            add(ItemAction(Icons.Outlined.ContentCopy, stringResource(R.string.hosts_duplicate), 1) {
                save(host.copy(id = "", label = copyLabel, favorite = false))
            })
        }
        add(ItemAction(Icons.Outlined.Link, stringResource(R.string.hosts_copy_address), 1) { copyAddress(host) })
        if (menu && writable) {
            add(ItemAction(Icons.Outlined.FolderOpen, stringResource(R.string.bulk_move), 2) { moving = listOf(host) })
        }
        if (accountList.isNotEmpty()) {
            if (writable) {
                add(ItemAction(Icons.AutoMirrored.Outlined.DriveFileMove, stringResource(R.string.transfer_move_to), 2) {
                    transferOf(TransferMode.MOVE, listOf(host))
                })
            }
            if (!host.access.useOnly()) {
                add(ItemAction(Icons.Outlined.ContentPaste, stringResource(R.string.transfer_copy_to), 2) {
                    transferOf(TransferMode.COPY, listOf(host))
                })
            }
        }
        if (menu) add(ItemAction(Icons.Outlined.Checklist, stringResource(R.string.hosts_select), 2) { toggle(host) })
        if (writable) {
            add(ItemAction(Icons.Outlined.Delete, stringResource(R.string.common_delete), 3, danger = true) { deleting = host })
        }
    }

    /** What can be done with group [g] (its sheet on phones, its menu in the desktop layout). */
    @Composable
    fun actionsOfGroup(g: HostGroup): List<ItemAction> = buildList {
        // A tab to each host inside (and in its subgroups); side by side on a wide screen.
        val inside = hostsIn(g)
        if (inside.isNotEmpty()) {
            add(ItemAction(Icons.Outlined.Terminal, pluralStringResource(R.plurals.hosts_group_connect_all, inside.size, inside.size), 0) {
                connectAll(inside, split = false)
            })
            if (maxPanes >= 2 && inside.size >= 2) {
                add(ItemAction(Icons.Outlined.GridView, stringResource(R.string.hosts_group_split_all), 0) { connectAll(inside, split = true) })
            }
        }
        if (g.access.canWrite()) {
            add(ItemAction(Icons.Outlined.DriveFileRenameOutline, stringResource(R.string.hosts_rename), 0) { editingGroup = g })
            if (accountList.isNotEmpty()) {
                add(ItemAction(Icons.AutoMirrored.Outlined.DriveFileMove, stringResource(R.string.transfer_move_to), 0) {
                    transfer = TransferRequest(TransferMode.MOVE, listOf(TransferItem(g.accountId, g.id, g.vaultId)))
                })
            }
        }
        if (accountList.isNotEmpty() && !g.access.useOnly()) {
            add(ItemAction(Icons.Outlined.ContentPaste, stringResource(R.string.transfer_copy_to), 0) {
                transfer = TransferRequest(TransferMode.COPY, listOf(TransferItem(g.accountId, g.id, g.vaultId)))
            })
        }
        if (g.access.canWrite()) {
            add(ItemAction(Icons.Outlined.Delete, stringResource(R.string.hosts_delete_group), 1, danger = true) { deletingGroup = g })
        }
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
                full { SearchField(query, searchFocus) { query = it } }
                if (hosts.isEmpty() && groups.isEmpty()) {
                    full {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            EmptyState(
                                Icons.Outlined.Dns,
                                stringResource(if (syncing) R.string.common_syncing else R.string.hosts_empty_title),
                                stringResource(if (loggedIn == true) R.string.hosts_empty_text_synced else R.string.hosts_empty_text_local),
                                Modifier.height(420.dp),
                                action = stringResource(R.string.hosts_new_host),
                                onAction = { newHost() },
                            )
                            // Or bring them from an OpenSSH config (as on iOS).
                            TextButton(onClick = { nav.navigate(Routes.IMPORT) }) {
                                Icon(Icons.Outlined.Description, null, Modifier.size(18.dp))
                                Text(stringResource(R.string.hosts_import_ssh_config), Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                } else if (groupId != null && visibleGroups.isEmpty() && visibleHosts.isEmpty() && !searching) {
                    full {
                        EmptyState(
                            Icons.Outlined.Folder, stringResource(R.string.hosts_group_empty_title),
                            stringResource(R.string.hosts_group_empty_text), Modifier.height(360.dp),
                            action = stringResource(R.string.hosts_new_host), onAction = { newHost() },
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
                            val gv = vaults.firstOrNull { it.id == g.vaultId && it.accountId == g.accountId }
                            GroupRow(
                                g, countIn(g),
                                onClick = {
                                    if (selecting) {
                                        val ids = hostsIn(g, hosts, groups).map { it.uid }
                                        selected = if (ids.all { it in selected }) selected - ids.toSet() else (selected + ids).distinct()
                                    } else {
                                        nav.navigate(Routes.group(g.id, g.accountId))
                                    }
                                },
                                onMore = { groupActions = g },
                                vault = if (!showVaults) null else if (g.accountId == null) stringResource(R.string.vault_this_device) else gv?.let { vaultName(it) },
                                vaultColor = if (g.accountId == null) MaterialTheme.colorScheme.onSurfaceVariant else vaultColor(gv),
                                account = if (showAccounts) accountList.firstOrNull { it.id == g.accountId } else null,
                            )
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
                                onDelete = if (host.access.canWrite()) ({ deleting = host }) else null,
                                vault = if (!showVaults) null else if (host.accountId == null) stringResource(R.string.vault_this_device) else v?.let { vaultName(it) },
                                vaultColor = if (host.accountId == null) MaterialTheme.colorScheme.onSurfaceVariant else vaultColor(v),
                                account = if (showAccounts) accountList.firstOrNull { it.id == host.accountId } else null,
                            )
                        }
                    }
                }
                if (visibleHosts.isEmpty() && searching) {
                    // An address typed in the search: quick connect to it.
                    val target = QuickTarget.parse(query)
                    full {
                        EmptyState(
                            Icons.Outlined.SearchOff, stringResource(R.string.hosts_no_match, query),
                            stringResource(R.string.hosts_no_match_text), Modifier.height(320.dp),
                            action = target?.let { stringResource(R.string.quick_connect_to, it.display()) },
                            onAction = { target?.let { quickConnectTo(it) } },
                        )
                    }
                }
            }
        }
    }

    val count = selected.size
    // The desktop view: sections per group (or the chip chosen), or the search results.
    val desktopView = if (desktop) {
        desktopHostsView(
            hosts, groups, query, chip, stringResource(R.string.hosts_results), stringResource(R.string.hosts_favorites),
            stringResource(R.string.host_no_group),
        )
    } else null
    // Multi-select: what can be done with the selected hosts.
    val selectionActions: @Composable RowScope.() -> Unit = {
        IconButton(onClick = {
            val ids = (desktopView?.shown ?: visibleHosts).map { it.uid }
            selected = if (ids.all { it in selected }) selected - ids.toSet() else (selected + ids).distinct()
        }) { Icon(Icons.Outlined.SelectAll, stringResource(R.string.bulk_select_all)) }
        IconButton(onClick = { bulkDelete = true }) { Icon(Icons.Outlined.Delete, stringResource(R.string.common_delete)) }
        Box {
            var menu by remember { mutableStateOf(false) }
            IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.common_more)) }
            DropdownMenu(menu, { menu = false }) {
                DropdownMenuItem(
                    { Text(stringResource(R.string.bulk_move)) }, { menu = false; moving = hosts.filter { it.uid in selected } },
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
    }

    if (desktopView != null) {
        // ----- Desktop layout: header, group chips, host cards and the editor panel -----
        BackHandler(editing != null && !selecting) { editing = null }
        DesktopHosts(
            app, nav, hosts = hosts, groups = groups, view = desktopView, query = query, onQuery = { query = it }, searchFocus = searchFocus,
            onChip = { chip = it }, grid = grid, loggedIn = loggedIn == true, syncing = syncing,
            selecting = selecting, selectedCount = count, selectionActions = selectionActions,
            onClearSelection = { selected = emptyList() },
            onServerCount = onServer.size, onServerSessions = { openServerSessions() },
            onNewHost = { edit(null) }, onNewGroup = { newGroup() }, onQuickConnect = { quickConnectTo(it) },
            countIn = { countIn(it) },
            groupMenu = { g, dismiss -> MenuItems(actionsOfGroup(g), dismiss) },
            editor = editing?.let { (id, account) ->
                @Composable {
                    key(id, account) {
                        HostEditor(
                            app, id.takeIf { it != NEW_HOST }, account, panel = true, newInGroup = newInGroup?.id?.takeIf { id == NEW_HOST },
                            onClose = { editing = null; reload() },
                            onConnect = { host -> editing = null; reload(); connect(host, false) },
                        )
                    }
                }
            },
        ) { host ->
            val v = vaultOf(host)
            DesktopHostCard(
                host, connected = host.uid in connectedHosts,
                selected = if (selecting) host.uid in selected else null,
                editing = editing?.let { it.first == host.id && it.second == host.accountId } == true,
                onClick = { if (selecting) toggle(host) else connect(host, false) },
                onCtrlClick = { toggle(host) },
                onTag = { query = it },
                onDelete = if (host.access.canWrite()) ({ deleting = host }) else null,
                vault = if (!showVaults) null else if (host.accountId == null) stringResource(R.string.vault_this_device) else v?.let { vaultName(it) },
                vaultColor = if (host.accountId == null) MaterialTheme.colorScheme.onSurfaceVariant else vaultColor(v),
                account = if (showAccounts) accountList.firstOrNull { it.id == host.accountId } else null,
            ) { dismiss -> MenuItems(hostActions(host, menu = true), dismiss) }
        }
    } else if (selecting) {
        ScreenScaffold(
            title = pluralStringResource(R.plurals.bulk_selected, count, count),
            navigationIcon = {
                IconButton(onClick = { selected = emptyList() }) { Icon(Icons.Outlined.Close, stringResource(R.string.bulk_clear)) }
            },
            actions = selectionActions,
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
    // The window got narrower with the editor panel open: the editor as a screen.
    LaunchedEffect(desktop, editing) {
        val (id, account) = editing ?: return@LaunchedEffect
        if (desktop) return@LaunchedEffect
        editing = null
        nav.navigate(Routes.hostEdit(id.takeIf { it != NEW_HOST }, account))
    }

    // ----- Sheets and dialogs -----

    if (creating) {
        ModalBottomSheet(onDismissRequest = { creating = false }) {
            SheetAction(Icons.Outlined.Dns, stringResource(R.string.hosts_new_host)) {
                creating = false; newHost()
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
                HostTile(host, size = 48.dp)
                Column(Modifier.padding(start = 16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(host.label, style = MaterialTheme.typography.titleMedium)
                        if (host.isTelnet) TelnetBadge(Modifier.padding(start = 8.dp))
                    }
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
            hostActions(host, menu = false).forEach { a ->
                SheetAction(a.icon, a.label, danger = a.danger) { actionsFor = null; a.run() }
            }
            Spacer(Modifier.navigationBarsPadding().height(24.dp))
        }
    }
    groupActions?.let { g ->
        ModalBottomSheet(onDismissRequest = { groupActions = null }) {
            Text(g.name, Modifier.padding(horizontal = 24.dp, vertical = 8.dp), style = MaterialTheme.typography.titleMedium)
            actionsOfGroup(g).forEach { a ->
                SheetAction(a.icon, a.label, danger = a.danger) { groupActions = null; a.run() }
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
        var place by remember(g) { mutableStateOf(com.termoak.app.data.Place(g.accountId, g.vaultId)) }
        AlertDialog(
            onDismissRequest = { editingGroup = null },
            title = { Text(stringResource(if (g.id.isEmpty()) R.string.hosts_new_group else R.string.hosts_rename)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.common_name)) }, singleLine = true)
                    // A new group at the top: where it goes (inside a group, its parent's place).
                    if (g.id.isEmpty() && g.parentId == null) PlacePicker(app, place) { place = it }
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    val saved = if (g.id.isEmpty() && g.parentId == null) {
                        app.accounts.rememberPlace(place)
                        g.copy(accountId = place.account, vaultId = place.vault, syncMode = place.syncMode(accountList.isNotEmpty()))
                    } else g
                    runCatching { app.core.saveGroup(saved.copy(name = name.trim())) }
                        .onFailure { scope.launch { snackbar.showSnackbar(it.userMessage(resources, R.string.error_save_failed)) } }
                    editingGroup = null
                    reload()
                    app.accounts.sync()
                }) { Text(stringResource(R.string.common_save)) }
            },
            dismissButton = { TextButton(onClick = { editingGroup = null }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
    tunnelsOf?.let { host -> HostTunnelsSheet(app, host) { tunnelsOf = null } }
    deleting?.let { host ->
        ConfirmDialog(
            title = stringResource(R.string.hosts_delete_title, host.label),
            // A host of a shared vault is deleted for everyone in it (as on iOS).
            text = stringResource(if (inSharedVault(host)) R.string.hosts_delete_text_shared else R.string.hosts_delete_text),
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
    moving?.let { chosen ->
        // Groups live in a place: only those of the hosts' own place (all in one).
        val place = chosen.map { it.accountId to it.vaultId }.distinct().singleOrNull()
        if (place == null) {
            LaunchedEffect(Unit) {
                moving = null
                snackbar.showSnackbar(resources.getString(R.string.bulk_move_one_place))
            }
            return
        }
        MoveToGroupDialog(groups.filter { it.accountId == place.first && it.vaultId == place.second }, chosen.size, onDismiss = { moving = null }) { target ->
            moving = null
            var failed = 0
            chosen.forEach { h ->
                runCatching { app.core.saveHost(h.copy(groupId = target), SecretChange.Keep) }.onFailure { failed++ }
            }
            if (chosen.all { it.uid in selected }) selected = emptyList()
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
            text = pluralStringResource(R.plurals.bulk_delete_text, count, count) +
                (if (hosts.any { it.uid in selected && inSharedVault(it) }) "\n\n" + stringResource(R.string.hosts_delete_text_shared) else ""),
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
    // The server doesn't open Telnet sessions: a Strict vault's Telnet host says so in its tab.
    if (isStrictUseOnly(app, host) && !host.isTelnet) app.sessions.openOnServer(host) else app.sessions.openLocal(host)

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
private fun SearchField(query: String, focus: FocusRequester, onChange: (String) -> Unit) {
    TextField(
        query, onChange,
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).focusRequester(focus),
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

/** "ssh, user" as in Termius ("telnet" for Telnet hosts, with the port if it isn't the protocol's). */
@Composable
private fun subtitle(host: SshHost): String {
    val user = host.settings.username?.takeIf { it.isNotBlank() }
    val port = host.settings.port?.takeIf { it != HostProtocol.defaultPort(host.protocol) }
        ?.let { stringResource(R.string.hosts_port, it.toString()) }
    return listOfNotNull(host.protocol.ifBlank { HostProtocol.SSH }.lowercase(), user, port).joinToString(", ")
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
    /** Delete (or Backspace) on the focused row asks to delete it; `null`: it can't be. */
    onDelete: (() -> Unit)? = null,
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
            // Keyboard: Enter connects (the click); the Menu key or Shift+F10 opens the host's actions.
            .onKeyEvent { e ->
                val down = e.type == KeyEventType.KeyDown
                val menu = down && (e.key == Key.Menu || (e.key == Key.F10 && e.isShiftPressed))
                if (menu) onMore()
                // Delete: the host's delete confirmation (as on iOS).
                val delete = down && onDelete != null && (e.key == Key.Delete || e.key == Key.Backspace)
                if (delete) onDelete?.invoke()
                menu || delete
            }
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
                HostTile(host, size = 44.dp)
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
                if (host.isTelnet) TelnetBadge(Modifier.padding(start = 6.dp))
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
private fun GroupRow(
    group: HostGroup,
    count: Int,
    onClick: () -> Unit,
    onMore: () -> Unit,
    /** Its vault (or "This device") and its account, like the hosts' (as on iOS). */
    vault: String? = null,
    vaultColor: Color = MaterialTheme.colorScheme.primary,
    account: AccountInfo? = null,
) {
    val tint = group.color?.let { runCatching { Color(it.toColorInt()) }.getOrNull() } ?: MaterialTheme.colorScheme.primary
    Row(
        Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onMore)
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(tint.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.Folder, null, Modifier.size(24.dp), tint = tint) }
            if (account != null) {
                Box(Modifier.align(Alignment.TopStart).offset((-4).dp, (-4).dp)) { AccountAvatar(account, 16.dp) }
            }
        }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(group.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    pluralStringResource(R.plurals.hosts_count, count, count), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                vault?.let { VaultChip(it, vaultColor, useOnly = group.access.useOnly()) }
            }
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

/** The id in the editor panel of a host that doesn't exist yet. */
private const val NEW_HOST = "new"

// The group chips of the desktop layout (otherwise, a group's uid).
private const val CHIP_ALL = "all"
private const val CHIP_FAVORITES = "fav"
private const val CHIP_NO_GROUP = "none"

/**
 * Something that can be done with a host or a group, for its sheet (phones)
 * or its menu (desktop layout, with a line between [section]s).
 */
class ItemAction(val icon: ImageVector, val label: String, val section: Int, val danger: Boolean = false, val run: () -> Unit)

/** [actions] as the items of a menu. */
@Composable
fun MenuItems(actions: List<ItemAction>, dismiss: () -> Unit) {
    actions.forEachIndexed { i, a ->
        if (i > 0 && actions[i - 1].section != a.section) HorizontalDivider()
        val color = if (a.danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        DropdownMenuItem(
            { Text(a.label, color = color) }, { dismiss(); a.run() },
            leadingIcon = { Icon(a.icon, null, tint = if (a.danger) color else MaterialTheme.colorScheme.onSurfaceVariant) },
        )
    }
}

/** A part of the desktop Hosts view: a group (or "No group", or results) and its hosts. */
private class HostSection(val key: String, val group: HostGroup?, val title: String?, val hosts: List<SshHost>)

/**
 * What the desktop Hosts view shows: the [sections] for the search or the
 * chosen [chip] (the one in effect), the groups for the chips (sorted by
 * their path, "Parent / Child" in [paths]) and the hosts without a group.
 */
private class DesktopHostsView(
    val sections: List<HostSection>,
    val groups: List<HostGroup>,
    val paths: Map<String, String>,
    val loose: List<SshHost>,
    val chip: String,
) {
    /** The hosts on screen, in order (for "Select all"). */
    val shown: List<SshHost> get() = sections.flatMap { it.hosts }
}

private fun desktopHostsView(
    hosts: List<SshHost>,
    groups: List<HostGroup>,
    query: String,
    chip: String,
    results: String,
    favorites: String,
    noGroup: String,
): DesktopHostsView {
    val q = query.trim().lowercase()
    val order = compareByDescending<SshHost> { it.favorite }.thenBy { it.label.lowercase() }
    fun groupOf(h: SshHost): HostGroup? = groups.firstOrNull { it.id == h.groupId && it.accountId == h.accountId }
    fun parentOf(g: HostGroup): HostGroup? = groups.firstOrNull { it.id == g.parentId && it.accountId == g.accountId }
    fun pathOf(g: HostGroup): List<HostGroup> = generateSequence(g) { parentOf(it) }.take(16).toList().asReversed()
    val paths = groups.associate { g -> g.uid to pathOf(g).joinToString(" / ") { it.name } }
    val sorted = groups.sortedBy { g -> paths[g.uid].orEmpty().lowercase() }
    val chosenGroup = groups.firstOrNull { it.uid == chip }
    val current = if (chip == CHIP_FAVORITES || chip == CHIP_NO_GROUP || chosenGroup != null) chip else CHIP_ALL
    val loose = hosts.filter { groupOf(it) == null }.sortedWith(order)
    val sections: List<HostSection> = when {
        q.isNotEmpty() -> listOf(
            HostSection(
                "results", null, results,
                hosts.filter { h ->
                    listOf(h.label, h.address, h.settings.username ?: "", h.tags.joinToString(" ")).any { it.lowercase().contains(q) }
                }.sortedWith(order),
            ),
        )
        current == CHIP_FAVORITES -> listOf(HostSection("fav", null, favorites, hosts.filter { it.favorite }.sortedBy { it.label.lowercase() }))
        current == CHIP_NO_GROUP -> listOf(HostSection("none", null, noGroup, loose))
        else -> {
            val shown = if (chosenGroup == null) sorted else sorted.filter { g -> pathOf(g).any { it.uid == chosenGroup.uid } }
            shown.map { g ->
                HostSection(
                    "g" + g.uid, g, paths[g.uid],
                    hosts.filter { it.groupId == g.id && it.accountId == g.accountId }.sortedWith(order),
                )
            }.filter { it.hosts.isNotEmpty() || it.group?.uid == chosenGroup?.uid } +
                if (chosenGroup == null && loose.isNotEmpty()) listOf(HostSection("none", null, noGroup, loose)) else emptyList()
        }
    }
    return DesktopHostsView(sections, sorted, paths, loose, current)
}

/**
 * The Hosts view of the desktop layout (hosts.rs of the desktop app): the
 * header with the search, Import, Group and New host; the group chips; the
 * hosts as cards in sections per group; and [editor] in a panel on the
 * right (over the whole area when the window is narrow).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DesktopHosts(
    app: TermoakApp,
    nav: NavHostController,
    hosts: List<SshHost>,
    groups: List<HostGroup>,
    view: DesktopHostsView,
    query: String,
    onQuery: (String) -> Unit,
    searchFocus: FocusRequester,
    onChip: (String) -> Unit,
    grid: LazyGridState,
    loggedIn: Boolean,
    syncing: Boolean,
    selecting: Boolean,
    selectedCount: Int,
    selectionActions: @Composable RowScope.() -> Unit,
    onClearSelection: () -> Unit,
    onServerCount: Int,
    onServerSessions: () -> Unit,
    onNewHost: () -> Unit,
    onNewGroup: () -> Unit,
    onQuickConnect: (QuickTarget) -> Unit,
    countIn: (HostGroup) -> Int,
    groupMenu: @Composable (HostGroup, () -> Unit) -> Unit,
    editor: (@Composable () -> Unit)?,
    card: @Composable (SshHost) -> Unit,
) {
    val searching = query.isNotBlank()
    val sections = view.sections
    val sorted = view.groups
    val current = view.chip
    val loose = view.loose
    val attention = attentionAccount(app)
    val noGroup = stringResource(R.string.host_no_group)
    fun pathOf(g: HostGroup): String = view.paths[g.uid] ?: g.name

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // The editor beside the hosts when both fit; otherwise over them.
            val editorWidth = 440.dp
            val editorOver = editor != null && maxWidth - editorWidth < 380.dp
            Row(Modifier.fillMaxSize()) {
                if (!editorOver) {
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        // ----- Header (or what to do with the selected hosts) -----
                        BoxWithConstraints(Modifier.fillMaxWidth()) {
                            val roomy = maxWidth >= 860.dp
                            val labels = maxWidth >= 560.dp
                            if (selecting) {
                                Row(
                                    Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(start = 12.dp, end = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    IconButton(onClick = onClearSelection) { Icon(Icons.Outlined.Close, stringResource(R.string.bulk_clear)) }
                                    Text(
                                        pluralStringResource(R.plurals.bulk_selected, selectedCount, selectedCount),
                                        Modifier.weight(1f).padding(start = 4.dp), style = MaterialTheme.typography.titleMedium,
                                    )
                                    selectionActions()
                                }
                            } else {
                                Column {
                                    Row(
                                        Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(start = 24.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(stringResource(R.string.section_hosts), style = MaterialTheme.typography.titleLarge)
                                            Text(
                                                pluralStringResource(R.plurals.hosts_desktop_subtitle, hosts.size, hosts.size),
                                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        if (roomy) DesktopSearchField(query, searchFocus, onQuery, Modifier.width(280.dp))
                                        if (loggedIn) {
                                            IconButton(onClick = { app.accounts.sync() }, enabled = !syncing) {
                                                if (syncing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                                else Icon(Icons.Outlined.Sync, stringResource(R.string.common_sync))
                                            }
                                        }
                                        HeaderButton(Icons.Outlined.FileDownload, stringResource(R.string.hosts_import), labels) {
                                            nav.navigate(Routes.IMPORT)
                                        }
                                        HeaderButton(Icons.Outlined.CreateNewFolder, stringResource(R.string.hosts_group_button), labels, onClick = onNewGroup)
                                        Button(onClick = onNewHost, contentPadding = PaddingValues(horizontal = 14.dp)) {
                                            Icon(Icons.Outlined.Add, null, Modifier.size(18.dp))
                                            if (labels) Text(stringResource(R.string.hosts_new_host), Modifier.padding(start = 6.dp))
                                        }
                                    }
                                    if (!roomy) {
                                        DesktopSearchField(query, searchFocus, onQuery, Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 8.dp))
                                    }
                                }
                            }
                        }
                        // ----- Group chips -----
                        if (groups.isNotEmpty() || hosts.any { it.favorite }) {
                            Row(
                                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                GroupChip(stringResource(R.string.hosts_all), hosts.size, current == CHIP_ALL && !searching) { onChip(CHIP_ALL) }
                                GroupChip(stringResource(R.string.hosts_favorites), hosts.count { it.favorite }, current == CHIP_FAVORITES && !searching) {
                                    onChip(CHIP_FAVORITES)
                                }
                                sorted.forEach { g ->
                                    GroupChip(pathOf(g), countIn(g), current == g.uid && !searching) { onChip(g.uid) }
                                }
                                if (loose.isNotEmpty()) {
                                    GroupChip(noGroup, loose.size, current == CHIP_NO_GROUP && !searching) { onChip(CHIP_NO_GROUP) }
                                }
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        // ----- Hosts -----
                        PullToRefreshBox(
                            isRefreshing = syncing,
                            onRefresh = { if (loggedIn) app.accounts.sync() },
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                        ) {
                            LazyVerticalGrid(
                                GridCells.Adaptive(minSize = 280.dp),
                                Modifier.fillMaxSize(),
                                state = grid,
                                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 32.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                attention?.let { a -> full { AccountAttention(app, nav, a) } }
                                full { UpdateBanner() }
                                if (loggedIn && onServerCount > 0) full { ServerSessionsNotice(onServerCount, onServerSessions) }
                                if (hosts.isEmpty() && groups.isEmpty()) {
                                    full {
                                        EmptyState(
                                            Icons.Outlined.Dns,
                                            stringResource(if (syncing) R.string.common_syncing else R.string.hosts_empty_title),
                                            stringResource(if (loggedIn) R.string.hosts_empty_text_synced else R.string.hosts_empty_text_local),
                                            Modifier.height(420.dp),
                                            action = stringResource(R.string.hosts_new_host), onAction = onNewHost,
                                        )
                                    }
                                }
                                sections.forEach { sec ->
                                    if (sec.title != null) {
                                        item(key = "h" + sec.key, span = { GridItemSpan(maxLineSpan) }) {
                                            DesktopSectionHeader(sec, groupMenu)
                                        }
                                    }
                                    items(sec.hosts, key = { sec.key + "/" + it.uid }) { card(it) }
                                    if (sec.hosts.isEmpty() && sec.group != null) {
                                        full {
                                            Text(
                                                stringResource(R.string.hosts_group_empty_title), Modifier.padding(vertical = 8.dp),
                                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                                if (searching && sections.all { it.hosts.isEmpty() }) {
                                    val target = QuickTarget.parse(query)
                                    full {
                                        EmptyState(
                                            Icons.Outlined.SearchOff, stringResource(R.string.hosts_no_match, query),
                                            stringResource(R.string.hosts_no_match_text), Modifier.height(320.dp),
                                            action = target?.let { stringResource(R.string.quick_connect_to, it.display()) },
                                            onAction = { target?.let(onQuickConnect) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                if (editor != null) {
                    if (!editorOver) VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Box(if (editorOver) Modifier.weight(1f).fillMaxHeight() else Modifier.width(editorWidth).fillMaxHeight()) {
                        // Its own top bar, not the main screens' header.
                        androidx.compose.runtime.CompositionLocalProvider(LocalDesktop provides false) { editor() }
                    }
                }
            }
        }
    }
}

/** A button of the desktop header: outlined, with its name when there is room. */
@Composable
private fun HeaderButton(icon: ImageVector, text: String, label: Boolean, onClick: () -> Unit) {
    if (!label) {
        IconButton(onClick = onClick) { Icon(icon, text) }
        return
    }
    OutlinedButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 12.dp)) {
        Icon(icon, null, Modifier.size(18.dp))
        Text(text, Modifier.padding(start = 6.dp), maxLines = 1)
    }
}

/** "Production · 4" */
@Composable
private fun GroupChip(name: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected, onClick, { Text("$name · $count", maxLines = 1) })
}

/** The title of a section of hosts: its folder, name, how many and the group's menu. */
@Composable
private fun DesktopSectionHeader(sec: HostSection, groupMenu: @Composable (HostGroup, () -> Unit) -> Unit) {
    val g = sec.group
    val tint = g?.color?.let { runCatching { Color(it.toColorInt()) }.getOrNull() } ?: MaterialTheme.colorScheme.primary
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(if (g == null && sec.key == "none") Icons.Outlined.FolderOff else Icons.Outlined.Folder, null, Modifier.size(20.dp), tint = tint)
        Text(sec.title.orEmpty(), Modifier.padding(start = 8.dp), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            pluralStringResource(R.plurals.hosts_count, sec.hosts.size, sec.hosts.size), Modifier.padding(start = 8.dp),
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (g != null) {
            Box {
                var menu by remember { mutableStateOf(false) }
                IconButton(onClick = { menu = true }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.MoreHoriz, stringResource(R.string.common_options), Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DropdownMenu(menu, { menu = false }) { groupMenu(g) { menu = false } }
            }
        }
    }
}

/** The search box of the desktop header (smaller than the phone's). */
@Composable
private fun DesktopSearchField(query: String, focus: FocusRequester, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier.height(40.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Search, null, Modifier.size(18.dp), tint = muted)
        Box(Modifier.weight(1f).padding(start = 8.dp), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) {
                Text(stringResource(R.string.desktop_search_hosts), color = muted, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            }
            BasicTextField(
                query, onChange, Modifier.fillMaxWidth().focusRequester(focus), singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            )
        }
        if (query.isNotEmpty()) {
            IconButton(onClick = { onChange("") }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Outlined.Close, stringResource(R.string.hosts_search_clear), Modifier.size(16.dp), tint = muted)
            }
        }
    }
}

/**
 * A host as a card of the desktop's Hosts view: avatar, name, user@address,
 * system and tags. Tap connects (selects while selecting), Ctrl+click
 * selects, right click or a long press opens its menu where it was asked
 * for (the "⋮" too).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DesktopHostCard(
    host: SshHost,
    connected: Boolean,
    selected: Boolean?,
    editing: Boolean,
    onClick: () -> Unit,
    onCtrlClick: () -> Unit,
    onTag: (String) -> Unit,
    onDelete: (() -> Unit)?,
    vault: String?,
    vaultColor: Color,
    account: AccountInfo?,
    menu: @Composable (dismiss: () -> Unit) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    var menuOpen by remember { mutableStateOf(false) }
    // Where the menu opens: where it was right-clicked, or at the "⋮" (`null`).
    var menuAt by remember { mutableStateOf<IntOffset?>(null) }
    val dismiss = { menuOpen = false }
    val highlight = selected == true || editing
    Box {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (selected == true) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(
                Modifier.fillMaxWidth()
                    .mouseActions(
                        onSecondary = { at -> menuAt = IntOffset(at.x.toInt(), at.y.toInt()); menuOpen = true },
                        onCtrlClick = onCtrlClick,
                    )
                    // Keyboard: Enter connects (the click); the Menu key or Shift+F10 opens the menu.
                    .onKeyEvent { e ->
                        val down = e.type == KeyEventType.KeyDown
                        val key = down && (e.key == Key.Menu || (e.key == Key.F10 && e.isShiftPressed))
                        if (key) { menuAt = null; menuOpen = true }
                        // Delete: the host's delete confirmation (as on iOS).
                        val delete = down && onDelete != null && (e.key == Key.Delete || e.key == Key.Backspace)
                        if (delete) onDelete?.invoke()
                        key || delete
                    }
                    .combinedClickable(onClick = onClick, onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        menuAt = null
                        menuOpen = true
                    })
                    .padding(start = 12.dp, top = 12.dp, end = 2.dp, bottom = 12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        if (selected == true) {
                            Box(
                                Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Outlined.Check, stringResource(R.string.bulk_selected_cd), tint = MaterialTheme.colorScheme.onPrimary) }
                        } else {
                            HostTile(host, size = 40.dp, twoInitials = true)
                        }
                        if (account != null && selected != true) {
                            Box(Modifier.align(Alignment.TopStart).offset((-4).dp, (-4).dp)) { AccountAvatar(account, 16.dp) }
                        }
                        if (connected) {
                            Box(
                                Modifier.align(Alignment.BottomEnd).offset(3.dp, 3.dp).size(14.dp).clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceContainerLow).padding(2.dp).clip(CircleShape).background(Brand.Green),
                            )
                        }
                    }
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                host.label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            if (host.favorite) {
                                Icon(Icons.Filled.Star, stringResource(R.string.hosts_favorite), Modifier.padding(start = 4.dp).size(14.dp), tint = Brand.Amber)
                            }
                            if (host.access.useOnly()) {
                                Icon(
                                    Icons.Outlined.Lock, stringResource(R.string.vault_use_only), Modifier.padding(start = 4.dp).size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Text(
                            hostAddress(host), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Box {
                        IconButton(onClick = { menuAt = null; menuOpen = true }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Outlined.MoreVert, stringResource(R.string.common_options), Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        DropdownMenu(menuOpen && menuAt == null, dismiss) { menu(dismiss) }
                    }
                }
                val os = osBadge(host.os)
                if (os != null || host.tags.isNotEmpty() || vault != null || host.isTelnet) {
                    Row(
                        Modifier.padding(top = 10.dp, end = 10.dp).fillMaxWidth().clipToBounds(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (host.isTelnet) TelnetBadge()
                        os?.let { (name, color) ->
                            Text(
                                name, Modifier.clip(RoundedCornerShape(4.dp)).background(color.copy(alpha = 0.18f)).padding(horizontal = 5.dp, vertical = 1.dp),
                                style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1,
                            )
                        }
                        host.osVersion?.takeIf { it.isNotBlank() }?.let {
                            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                        vault?.let { VaultChip(it, vaultColor, useOnly = host.access.useOnly()) }
                        host.tags.take(3).forEach { TagChip(it) { onTag(it) } }
                        if (host.tags.size > 3) TagChip("+${host.tags.size - 3}", onClick = null)
                    }
                }
            }
        }
        menuAt?.let { at -> ContextMenuAt(at, menuOpen, dismiss) { menu(dismiss) } }
    }
}
