package com.termoak.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavHostController
import com.termoak.app.R
import com.termoak.app.TermoakApp
import com.termoak.app.UiText
import com.termoak.app.asString
import com.termoak.app.data.ConfRead
import com.termoak.app.data.HostAddress
import com.termoak.app.data.NetPeer
import com.termoak.app.data.NetStatus
import com.termoak.app.data.NetState
import com.termoak.app.data.NetworkBook
import com.termoak.app.data.NetworkItem
import com.termoak.app.data.Networks
import com.termoak.app.data.NetworksApi
import com.termoak.app.data.canEdit
import com.termoak.app.data.displayName
import com.termoak.app.toUiText
import com.termoak.app.uiText
import com.termoak.ffi.AccountInfo
import com.termoak.ffi.AccountStatus
import com.termoak.ffi.ItemFilter
import com.termoak.ffi.VaultInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/*
 * Settings → Accounts → Private networks (0.7, NETWORKS.md §8.1), like the
 * web's Account → Networks and the desktop's Networks section: the WireGuard
 * networks of every vault of an account whose server has `features.networks`
 * (never This device), their state in the server, the plan's usage, adding
 * or editing one from a .conf (pasted or opened) or with a key generated on
 * the server, Test (up + waiting for the handshake), bring down, its devices
 * → add a host, delete. Changes go through the REST API and reach the local
 * stores by sync.
 */

/** How often the states are asked again while the screen is open. */
private const val STATE_EVERY_MS = 15_000L

/** The name and the state of a network as a coloured pill. */
@Composable
internal fun NetStatePill(state: NetState, short: Boolean = true, modifier: Modifier = Modifier, kind: String? = null) {
    Pill(stateLabel(state, short, kind), stateColor(state), modifier)
}

@Composable
internal fun stateLabel(state: NetState, short: Boolean = false, kind: String? = null): String = stringResource(stateRes(state, short, kind))

/** The state's name; a tailnet says "Connecting to the tailnet…" while it starts. */
internal fun stateRes(state: NetState, short: Boolean = false, kind: String? = null): Int = when (state) {
    NetState.DOWN -> R.string.networks_state_down
    NetState.STARTING -> if (kind == Networks.TAILSCALE) R.string.networks_ts_state_starting else R.string.networks_state_starting
    NetState.WAITING -> if (short) R.string.networks_state_waiting_short else R.string.networks_state_waiting
    NetState.UP -> R.string.networks_state_up
    NetState.NEEDS_AUTH -> if (short) R.string.networks_state_needs_auth_short else R.string.networks_state_needs_auth
    NetState.NEEDS_LOGIN -> if (short) R.string.networks_state_login_short else R.string.networks_state_login
    NetState.NEEDS_APPROVAL -> if (short) R.string.networks_state_approval_short else R.string.networks_state_approval
    NetState.ERROR -> R.string.networks_state_error
    NetState.UNKNOWN -> R.string.networks_state_unknown
}

@Composable
internal fun stateHint(state: NetState, kind: String? = null): String = stringResource(
    when (state) {
        NetState.DOWN -> if (kind == Networks.TAILSCALE) R.string.networks_ts_state_down_hint else R.string.networks_state_down_hint
        NetState.STARTING -> if (kind == Networks.TAILSCALE) R.string.networks_ts_state_starting_hint else R.string.networks_state_starting_hint
        NetState.WAITING -> R.string.networks_state_waiting_hint
        NetState.UP -> if (kind == Networks.TAILSCALE) R.string.networks_ts_state_up_hint else R.string.networks_state_up_hint
        NetState.NEEDS_AUTH -> R.string.networks_state_needs_auth_hint
        NetState.NEEDS_LOGIN -> R.string.networks_state_login_hint
        NetState.NEEDS_APPROVAL -> R.string.networks_state_approval_hint
        NetState.ERROR -> R.string.networks_state_error_hint
        NetState.UNKNOWN -> R.string.networks_state_unknown_hint
    },
)

@Composable
internal fun stateColor(state: NetState): Color = when (state) {
    NetState.UP -> Brand.Green
    NetState.WAITING, NetState.NEEDS_AUTH, NetState.NEEDS_LOGIN, NetState.NEEDS_APPROVAL, NetState.STARTING -> Brand.Amber
    NetState.ERROR -> Brand.Red
    NetState.DOWN, NetState.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
}

/** "Connected · 100.64.0.3 · 4 devices" ([short]: without "Connected"). */
@Composable
internal fun tailnetSummaryText(status: NetStatus, short: Boolean = false): String {
    val resources = LocalResources.current
    val summary = Networks.tailnetSummary(status) { resources.getQuantityString(R.plurals.networks_ts_devices, it, it) }
    return if (short) summary else stringResource(R.string.networks_ts_connected, summary)
}

/** Opens a tailnet's login page in the browser (`false`: no app can). */
internal fun openLoginPage(context: Context, url: String): Boolean = runCatching {
    context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(url)))
}.isSuccess

internal fun copyText(context: Context, label: String, text: String) {
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(label, text))
}

/** Accounts whose server has private networks (active, as last read). */
private fun networkAccounts(accounts: List<AccountInfo>): List<AccountInfo> =
    accounts.filter { it.status == AccountStatus.ACTIVE && NetworkBook.supported(it.id) }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NetworksScreen(app: TermoakApp, nav: NavHostController, accountId: String?) {
    val accounts by app.accounts.list.collectAsState()
    val allVaults by app.accounts.vaults.collectAsState()
    val resources = LocalResources.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    // The features can be stale: read /info again on entering (NETWORKS.md §8.1).
    var featuresRead by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        accounts.filter { it.status == AccountStatus.ACTIVE }.forEach { NetworkBook.refreshFeatures(it.id, app.accounts.handle(it.id)) }
        featuresRead = true
    }
    val shown = networkAccounts(accounts)
    var chosen by remember { mutableStateOf(accountId) }
    val account = shown.firstOrNull { it.id == chosen } ?: shown.firstOrNull()
    val api = remember(account?.id) { account?.let { a -> app.accounts.handle(a.id)?.let { NetworksApi(it) } } }
    val vaults = allVaults.filter { it.accountId == account?.id }
    val editorVaults = vaults.filter { it.role.canEdit() }

    var networks by remember(account?.id) { mutableStateOf<List<NetworkItem>?>(null) }
    var loadError by remember(account?.id) { mutableStateOf<UiText?>(null) }
    var usage by remember(account?.id) { mutableStateOf<Pair<Int, Int?>?>(null) }
    var selected by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<NetworkItem?>(null) }
    var adding by remember { mutableStateOf(false) }
    // Tailscale: the key choice the form starts with ("Use another key").
    var formKeyMode by remember { mutableStateOf<KeyMode?>(null) }
    var keyShown by remember { mutableStateOf<Pair<NetworkItem, Boolean>?>(null) }
    var peersOf by remember { mutableStateOf<NetworkItem?>(null) }
    var addHost by remember { mutableStateOf<AddHostAt?>(null) }
    // A tailnet whose login (or approval) is followed every 2 s, and one to test once the list shows it.
    var watching by remember { mutableStateOf<String?>(null) }
    var autoTest by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<NetworkItem?>(null) }
    var replacing by remember { mutableStateOf<NetworkItem?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }

    fun say(text: UiText) {
        scope.launch { snackbar.showSnackbar(text.resolve(resources)) }
    }

    suspend fun load() {
        val a = api ?: return
        try {
            val list = a.list()
            networks = list
            loadError = null
            usage = a.usage()
            list.forEach { n -> NetworkBook.refreshStatus(a, n.id) }
        } catch (e: Exception) {
            loadError = e.toUiText(R.string.networks_load_failed)
            if (networks == null) networks = emptyList()
        }
    }

    fun reload() {
        scope.launch { load() }
    }

    /** After a change: the local stores get it by sync (the editors' pickers). */
    fun changed() {
        account?.let { app.accounts.sync(it.id) }
        reload()
    }

    LaunchedEffect(api) { load() }
    // The states while the screen is open (never brings a network up).
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(api, networks?.map { it.id }) {
        val a = api ?: return@LaunchedEffect
        val ids = networks?.map { it.id } ?: return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                delay(STATE_EVERY_MS)
                ids.forEach { NetworkBook.refreshStatus(a, it, force = true) }
            }
        }
    }

    fun vaultLabel(id: String?): String =
        vaults.firstOrNull { it.id == id }?.let { com.termoak.app.data.Accounts.vaultLabel(context, it) } ?: resources.getString(R.string.networks_vault_unknown)

    fun canEdit(n: NetworkItem): Boolean = vaults.firstOrNull { it.id == n.vaultId }?.role?.canEdit() == true

    /** The server joins networks of [n]'s kind (a Tailscale network on a server without it: "Not on this server"). */
    fun available(n: NetworkItem): Boolean = NetworkBook.kindSupported(account?.id, n.kind)

    fun summary(st: com.termoak.app.data.NetStatus): String =
        Networks.tailnetSummary(st) { resources.getQuantityString(R.plurals.networks_ts_devices, it, it) }

    /** What Test found in a tailnet. */
    fun tailnetResult(n: NetworkItem, st: com.termoak.app.data.NetStatus): UiText = when (Networks.state(st, n.kind)) {
        NetState.UP -> uiText(R.string.networks_ts_test_ok, n.name, summary(st))
        NetState.NEEDS_LOGIN -> uiText(R.string.networks_ts_test_login, n.name)
        NetState.NEEDS_APPROVAL -> uiText(R.string.networks_ts_test_approval, n.name, st.nodeName ?: st.nodeDnsName?.trimEnd('.') ?: "—")
        NetState.STARTING -> uiText(R.string.networks_ts_test_starting, n.name)
        NetState.ERROR -> uiText(R.string.networks_ts_test_error, n.name, (st.message ?: "").replaceFirstChar { it.uppercase() })
        else -> uiText(R.string.networks_test_state, n.name, resources.getString(stateRes(Networks.state(st, n.kind), kind = n.kind)))
    }

    fun test(n: NetworkItem) {
        val a = api ?: return
        busy = n.id
        scope.launch {
            try {
                val st = a.test(n.id, n.kind) { NetworkBook.setStatus(n.id, it) }
                if (n.isTailscale) {
                    say(tailnetResult(n, st))
                    // A login or an approval: follow it while the screen is open.
                    if (st.state == "needs_auth" && canEdit(n)) watching = n.id
                    return@launch
                }
                say(
                    when (Networks.state(st, n.kind)) {
                        NetState.UP -> uiText(R.string.networks_test_ok, n.name)
                        NetState.WAITING -> uiText(R.string.networks_test_no_handshake, n.name)
                        NetState.ERROR -> uiText(R.string.networks_test_error, n.name, st.message ?: "")
                        else -> uiText(R.string.networks_test_state, n.name, resources.getString(stateRes(Networks.state(st, n.kind))))
                    },
                )
            } catch (e: Exception) {
                say(Networks.errorFor(e, n, R.string.networks_test_failed))
            } finally {
                busy = null
            }
        }
    }

    // The login (or approval) of a tailnet: its state every 2 s until it is no longer waiting, 5 minutes at most,
    // while the screen is open (also while the browser is in front). If it goes idle meanwhile (down), it stops.
    LaunchedEffect(watching, api) {
        val id = watching ?: return@LaunchedEffect
        val a = api ?: return@LaunchedEffect
        val last = a.watchLogin(id) { NetworkBook.setStatus(id, it) }
        val n = networks?.firstOrNull { it.id == id }
        if (last != null && n != null && last.state == "up") say(uiText(R.string.networks_ts_test_ok, n.name, summary(last)))
        if (watching == id) watching = null
    }

    // A new tailnet or a new key: Test once the list (reloaded after saving) shows it.
    LaunchedEffect(autoTest, networks) {
        val id = autoTest ?: return@LaunchedEffect
        val n = networks?.firstOrNull { it.id == id } ?: return@LaunchedEffect
        autoTest = null
        test(n)
    }

    fun login(n: NetworkItem, url: String) {
        if (!openLoginPage(context, url)) say(uiText(R.string.networks_ts_login_no_browser))
        watching = n.id
    }

    fun down(n: NetworkItem) {
        val a = api ?: return
        busy = n.id
        scope.launch {
            try {
                a.down(n.id)
                NetworkBook.refreshStatus(a, n.id, force = true)
                say(uiText(R.string.networks_down_done, n.name))
            } catch (e: Exception) {
                say(Networks.errorFor(e, n, R.string.networks_action_failed))
            } finally {
                busy = null
            }
        }
    }

    fun generateKey(n: NetworkItem, replace: Boolean) {
        val a = api ?: return
        busy = n.id
        scope.launch {
            try {
                val key = a.generateKey(n.id, replace)
                val updated = n.copy(publicKey = key, hasSecret = true)
                networks = networks?.map { if (it.id == n.id) updated else it }
                keyShown = updated to true
                changed()
            } catch (e: Exception) {
                say(e.toUiText(R.string.networks_action_failed))
            } finally {
                busy = null
            }
        }
    }

    ScreenScaffold(
        title = stringResource(R.string.networks_title),
        subtitle = account?.takeIf { shown.size > 1 }?.displayName,
        navigationIcon = {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back)) }
        },
        actions = {
            if (account != null) {
                IconButton(onClick = { reload() }) { Icon(Icons.Outlined.Refresh, stringResource(R.string.networks_refresh)) }
            }
        },
        floatingActionButton = {
            if (account != null && editorVaults.isNotEmpty()) {
                FloatingActionButton(onClick = { adding = true }, modifier = Modifier.testTag("networks-add")) {
                    Icon(Icons.Outlined.Add, stringResource(R.string.networks_add))
                }
            }
        },
    ) { padding ->
        if (account == null) {
            if (featuresRead) {
                EmptyState(
                    Icons.Outlined.Hub, stringResource(R.string.networks_unsupported), stringResource(R.string.networks_unsupported_hint),
                    Modifier.padding(padding),
                )
            } else {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
            return@ScreenScaffold
        }
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(bottom = 96.dp)) {
            // Several accounts with networks: which one.
            if (shown.size > 1) {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    shown.forEach { a ->
                        FilterChip(a.id == account.id, { chosen = a.id; selected = null }, label = { Text(a.displayName) }, leadingIcon = { AccountAvatar(a, 18.dp) })
                    }
                }
            }
            Text(
                stringResource(R.string.networks_subtitle), Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            usage?.let { (used, max) -> UsageCard(used, max) }
            val list = networks
            when {
                list == null -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                list.isEmpty() && loadError == null -> EmptyState(
                    Icons.Outlined.Hub, stringResource(R.string.networks_empty),
                    stringResource(if (NetworkBook.kindSupported(account.id, Networks.TAILSCALE)) R.string.networks_ts_empty_hint else R.string.networks_empty_hint),
                    Modifier.heightIn(min = 320.dp),
                    action = stringResource(R.string.networks_add).takeIf { editorVaults.isNotEmpty() },
                    onAction = { adding = true },
                )
                else -> {
                    SectionLabel(pluralStringResource(R.plurals.networks_count, list.size, list.size))
                    list.forEach { n ->
                        NetworkRow(n, vaultLabel(n.vaultId), busy == n.id, available(n)) { selected = n.id }
                    }
                }
            }
            loadError?.let {
                Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { MessageBox(it.asString(), isError = true) }
            }
        }
    }

    networks?.firstOrNull { it.id == selected }?.let { n ->
        // A login or an approval started elsewhere: follow it while its page is open.
        val st = NetworkBook.state(n.id, n.kind)
        LaunchedEffect(n.id, st) {
            if ((st == NetState.NEEDS_LOGIN || st == NetState.NEEDS_APPROVAL) && canEdit(n) && available(n) && watching == null) watching = n.id
        }
        NetworkSheet(
            app, n, vaultLabel(n.vaultId), canEdit(n), busy == n.id, account?.id, available(n), watching == n.id,
            onDismiss = { selected = null },
            onTest = { test(n) },
            onDown = { down(n) },
            onEdit = { selected = null; formKeyMode = null; editing = n },
            onDelete = { deleting = n },
            onKey = { keyShown = n to false },
            onGenerate = { if (n.hasSecret) replacing = n else generateKey(n, false) },
            onPeers = { peersOf = n },
            onCopied = { say(uiText(R.string.networks_key_copied)) },
            onLogin = { url -> login(n, url) },
            onOtherKey = { selected = null; formKeyMode = KeyMode.NEW; editing = n },
        )
    }
    if (adding || editing != null) {
        val target = editing
        NetworkForm(
            target, if (target == null) editorVaults else emptyList(), NetworkBook.kinds(account?.id), formKeyMode,
            onDismiss = { adding = false; editing = null; formKeyMode = null },
        ) { plan ->
            val a = api ?: return@NetworkForm uiText(R.string.error_not_logged_in)
            try {
                val saved = if (target == null) a.create(plan.body) else a.update(target.id, plan.body)
                var shownKey: NetworkItem? = null
                var keyError: UiText? = null
                if (plan.generate) {
                    try {
                        val key = a.generateKey(saved.id, plan.replace)
                        shownKey = saved.copy(publicKey = key, hasSecret = true)
                    } catch (e: Exception) {
                        keyError = e.toUiText(R.string.networks_action_failed)
                    }
                }
                adding = false
                editing = null
                formKeyMode = null
                say(keyError?.let { uiText(R.string.networks_saved_without_key, it.resolve(resources)) }
                    ?: uiText(if (target == null) R.string.networks_created else R.string.networks_saved, saved.name))
                selected = saved.id
                shownKey?.let { keyShown = it to true }
                account?.let { app.accounts.sync(it.id) }
                if (plan.test) {
                    // Test it once the list (read again) shows it.
                    scope.launch {
                        load()
                        autoTest = saved.id
                    }
                } else {
                    NetworkBook.refreshStatus(a, saved.id, force = true)
                    reload()
                }
                null
            } catch (e: Exception) {
                Networks.saveErrorText(e)
            }
        }
    }
    keyShown?.let { (n, generated) ->
        ServerKeySheet(
            n, generated, canTest = canEdit(n),
            onCopied = { say(uiText(R.string.networks_key_copied)) },
            onTest = { keyShown = null; test(n) },
            onDismiss = { keyShown = null },
        )
    }
    peersOf?.let { n ->
        PeersSheet(
            api, n, canEdit(n), onDismiss = { peersOf = null },
            onAdd = { address, label -> peersOf = null; addHost = AddHostAt(n, address, label) },
            onAddedAll = { added, failures ->
                failures.forEach { say(it) }
                if (added > 0) {
                    say(UiText.Plural(R.plurals.networks_ts_peers_added_all, added, listOf(added)))
                    account?.let { app.accounts.sync(it.id) }
                }
            },
        )
    }
    addHost?.let { at ->
        val n = at.network
        AddNetworkHostDialog(n, at.address, at.label, onDismiss = { addHost = null }) { body ->
            val a = api ?: return@AddNetworkHostDialog uiText(R.string.error_not_logged_in)
            try {
                val label = a.addHost(body)
                addHost = null
                say(uiText(R.string.networks_host_added, label))
                account?.let { app.accounts.sync(it.id) }
                // A tailnet: back to its devices, the new host marked.
                if (n.isTailscale) peersOf = n
                null
            } catch (e: Exception) {
                e.toUiText(R.string.networks_host_add_failed)
            }
        }
    }
    deleting?.let { n ->
        val users = remember(n.id) { hostsUsing(app, account?.id, n) }
        ConfirmDialog(
            title = stringResource(R.string.networks_delete_title, n.name),
            text = stringResource(R.string.networks_delete_message) +
                (if (n.isTailscale) " " + stringResource(R.string.networks_ts_delete_note) else "") +
                (if (users > 0) "\n\n" + pluralStringResource(R.plurals.networks_delete_hosts, users, users) else ""),
            confirm = stringResource(R.string.common_delete), destructive = true, onDismiss = { deleting = null },
        ) {
            val a = api ?: return@ConfirmDialog
            scope.launch {
                try {
                    a.delete(n.id)
                    selected = null
                    say(uiText(R.string.networks_delete_done, n.name))
                    changed()
                } catch (e: Exception) {
                    say(e.toUiText(R.string.networks_action_failed))
                }
            }
        }
    }
    replacing?.let { n ->
        ConfirmDialog(
            title = stringResource(R.string.networks_key_replace_title),
            text = stringResource(R.string.networks_key_replace_message),
            confirm = stringResource(R.string.networks_key_replace_ok), destructive = true, onDismiss = { replacing = null },
        ) { generateKey(n, true) }
    }
}

/** Where "Add host" starts: the network, an address (a device's, or a subnet's start) and a name. */
private data class AddHostAt(val network: NetworkItem, val address: HostAddress?, val label: String?)

/** Hosts of the account (in the local stores) that go through [n]: their own network or their groups'. */
private fun hostsUsing(app: TermoakApp, accountId: String?, n: NetworkItem): Int {
    if (accountId == null) return 0
    val filter = ItemFilter(accountIds = listOf(accountId), vaultIds = null, includeDevice = false)
    val hosts = runCatching { app.core.listHosts(filter) }.getOrDefault(emptyList())
    val groups = runCatching { app.core.listGroups(filter) }.getOrDefault(emptyList())
    return hosts.count { h ->
        h.vaultId == n.vaultId && Networks.effective(h.settings.networkId, h.groupId, NetworkBook.links(groups, accountId, h.vaultId)) == n.id
    }
}

/** "Networks in your plan: 3 of 10". */
@Composable
internal fun UsageCard(used: Int, max: Int?) {
    CardBox {
        Column(Modifier.padding(16.dp).testTag("networks-usage")) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.networks_usage_label), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                Text(
                    if (max != null) stringResource(R.string.networks_usage, used, max) else stringResource(R.string.networks_usage_unlimited, used),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (max != null && max > 0) {
                LinearProgressIndicator(
                    progress = { (used.toFloat() / max).coerceIn(0f, 1f) },
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    color = if (used >= max) Brand.Amber else MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                stringResource(if (max != null && used >= max) R.string.networks_usage_full else R.string.networks_usage_note),
                Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * A network of the list: name, kind and vault, its state, and the last
 * handshake and traffic (a tailnet: its address and devices, its control
 * server). [available]: the server joins networks of its kind.
 */
@Composable
internal fun NetworkRow(n: NetworkItem, vault: String, busy: Boolean, available: Boolean = true, onClick: () -> Unit) {
    val status = NetworkBook.status(n.id)
    val state = if (NetworkBook.isUnknown(n.id)) NetState.UNKNOWN else Networks.state(status, n.kind)
    CardBox(Modifier.testTag("network-${n.name}")) {
        Row(Modifier.clickable(onClick = onClick).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Hub, null, tint = stateColor(state).takeIf { state == NetState.UP && available } ?: MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(n.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${Networks.kindLabel(n.kind)} · $vault", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                val facts = if (n.isTailscale) {
                    listOfNotNull(
                        status?.takeIf { state == NetState.UP && available }?.let { tailnetSummaryText(it, short = true) },
                        stringResource(R.string.networks_ts_control_line, Networks.controlServerLabel(n.tailscale?.controlUrl)),
                        if (n.secretHidden) null else stringResource(if (n.hasSecret) R.string.networks_ts_with_key else R.string.networks_ts_without_key),
                        traffic(status?.rxBytes, status?.txBytes)?.takeIf { available },
                    )
                } else {
                    listOfNotNull(
                        status?.lastHandshakeAt?.let { stringResource(R.string.networks_handshake, relativeTime(it)) }
                            ?: stringResource(R.string.networks_no_handshake).takeIf { state == NetState.WAITING },
                        traffic(status?.rxBytes, status?.txBytes),
                        stringResource(R.string.networks_no_key).takeIf { n.isWireguard && !n.hasSecret && !n.secretHidden },
                    )
                }
                if (facts.isNotEmpty()) {
                    Text(
                        facts.joinToString(" · "), Modifier.padding(top = 2.dp).testTag("network-facts-${n.name}"), style = MaterialTheme.typography.bodySmall,
                        color = if (n.isWireguard && !n.hasSecret && !n.secretHidden) Brand.Amber else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            when {
                busy -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                // A kind this server can't join (a Tailscale network on an older server).
                !available -> Pill(stringResource(R.string.networks_state_cannot_join), MaterialTheme.colorScheme.onSurfaceVariant, Modifier.padding(start = 8.dp))
                else -> NetStatePill(state, modifier = Modifier.padding(start = 8.dp), kind = n.kind)
            }
        }
    }
}

/** "↓ 1.2 MB · ↑ 30 kB", or `null` without traffic. */
@Composable
private fun traffic(rx: Long?, tx: Long?): String? {
    if ((rx ?: 0) == 0L && (tx ?: 0) == 0L) return null
    return stringResource(R.string.networks_traffic, Networks.bytes(rx ?: 0), Networks.bytes(tx ?: 0))
}

/** A network's page: its state and actions, the server's key, its configuration and devices. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NetworkSheet(
    app: TermoakApp,
    n: NetworkItem,
    vault: String,
    editor: Boolean,
    busy: Boolean,
    accountId: String?,
    available: Boolean,
    watching: Boolean,
    onDismiss: () -> Unit,
    onTest: () -> Unit,
    onDown: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onKey: () -> Unit,
    onGenerate: () -> Unit,
    onPeers: () -> Unit,
    onCopied: () -> Unit,
    onLogin: (String) -> Unit,
    onOtherKey: () -> Unit,
) {
    val users = remember(n.id) { hostsUsing(app, accountId, n) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        NetworkDetail(
            n, vault, editor, busy, users, onTest, onDown, onEdit, onDelete, onKey, onGenerate, onPeers, onCopied,
            available = available, watching = watching, onLogin = onLogin, onOtherKey = onOtherKey,
        )
    }
}

/** What [NetworkSheet] shows (also drawn alone by the screenshot tests). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NetworkDetail(
    n: NetworkItem,
    vault: String,
    editor: Boolean,
    busy: Boolean,
    users: Int,
    onTest: () -> Unit,
    onDown: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onKey: () -> Unit,
    onGenerate: () -> Unit,
    onPeers: () -> Unit,
    onCopied: () -> Unit,
    available: Boolean = true,
    watching: Boolean = false,
    onLogin: (String) -> Unit = {},
    onOtherKey: () -> Unit = {},
) {
    val context = LocalContext.current
    val status = NetworkBook.status(n.id)
    val state = if (NetworkBook.isUnknown(n.id)) NetState.UNKNOWN else Networks.state(status, n.kind)
    run {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).testTag("network-sheet")) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(n.name, style = MaterialTheme.typography.titleLarge)
                    Text(
                        "${Networks.kindLabel(n.kind)} · " + stringResource(R.string.networks_in_vault, vault),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (available) NetStatePill(state, short = false, kind = n.kind)
                else Pill(stringResource(R.string.networks_state_cannot_join), MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (n.description.isNotBlank()) Text(n.description, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium)
            // State in the server.
            Text(stringResource(R.string.networks_detail_state), Modifier.padding(top = 16.dp), style = MaterialTheme.typography.titleSmall)
            if (!available) {
                Note(stringResource(if (n.isTailscale) R.string.networks_ts_unsupported else R.string.error_unsupported_kind), warn = true)
            } else if (n.isTailscale) {
                TailnetState(n, status, state, editor, watching, onLogin, onOtherKey)
            } else {
                Text(
                    stateHint(state) + (status?.message?.takeIf { state == NetState.ERROR }?.let { " ($it)" } ?: ""),
                    Modifier.padding(top = 2.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Fact(stringResource(R.string.networks_detail_handshake), status?.lastHandshakeAt?.let { relativeTime(it) } ?: stringResource(R.string.networks_detail_never))
                traffic(status?.rxBytes, status?.txBytes)?.let { Fact(stringResource(R.string.networks_detail_traffic), it) }
                status?.nodeAddresses?.takeIf { it.isNotEmpty() }?.let { Fact(stringResource(R.string.networks_detail_node_addresses), it.joinToString(", ")) }
                status?.streams?.takeIf { it > 0 }?.let { Fact(stringResource(R.string.networks_detail_streams), it.toString()) }
            }
            if (editor && available) {
                FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onTest, enabled = !busy, modifier = Modifier.testTag("network-test")) {
                        if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        else Icon(Icons.Outlined.NetworkCheck, null, Modifier.size(18.dp))
                        Text(stringResource(R.string.networks_test), Modifier.padding(start = 6.dp))
                    }
                    OutlinedButton(onClick = onDown, enabled = !busy && state != NetState.DOWN) {
                        Icon(Icons.Outlined.PowerSettingsNew, null, Modifier.size(18.dp))
                        Text(stringResource(R.string.networks_down), Modifier.padding(start = 6.dp))
                    }
                }
                Text(
                    stringResource(if (n.isTailscale) R.string.networks_ts_test_hint else R.string.networks_test_hint), Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // A tailnet's settings.
            if (n.isTailscale) {
                val ts = n.tailscale ?: com.termoak.app.data.TsConfig()
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                Text(stringResource(R.string.networks_detail_configuration), style = MaterialTheme.typography.titleSmall)
                Fact(stringResource(R.string.networks_ts_detail_control), ts.controlUrl ?: "Tailscale")
                if (!n.secretHidden) {
                    Fact(
                        stringResource(R.string.networks_ts_detail_key),
                        stringResource(if (n.hasSecret) R.string.networks_ts_detail_with_key else R.string.networks_ts_detail_without_key),
                    )
                }
                Fact(stringResource(R.string.networks_ts_detail_hostname), ts.hostname ?: stringResource(R.string.networks_ts_form_hostname_auto, Networks.defaultHostname(n.id)))
                Fact(stringResource(R.string.networks_ts_detail_tags), ts.tags.joinToString(", ").ifEmpty { stringResource(R.string.networks_ts_detail_key_tags) })
                Fact(
                    stringResource(R.string.networks_ts_detail_ephemeral),
                    stringResource(
                        when (ts.ephemeral) {
                            true -> R.string.networks_ts_detail_ephemeral_yes
                            false -> R.string.networks_ts_detail_ephemeral_no
                            null -> R.string.networks_ts_detail_ephemeral_default
                        },
                    ),
                )
            }
            // The server's key (WireGuard).
            if (n.isWireguard) {
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                Text(stringResource(R.string.networks_key_title), style = MaterialTheme.typography.titleSmall)
                when {
                    n.publicKey != null -> {
                        SelectionContainer {
                            Text(n.publicKey, Modifier.padding(top = 6.dp).testTag("network-public-key"), fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { copyText(context, n.name, n.publicKey); onCopied() }) {
                                Icon(Icons.Outlined.ContentCopy, null, Modifier.size(16.dp))
                                Text(stringResource(R.string.common_copy), Modifier.padding(start = 6.dp))
                            }
                            TextButton(onClick = onKey) {
                                Icon(Icons.Outlined.Description, null, Modifier.size(16.dp))
                                Text(stringResource(R.string.networks_key_peer_block), Modifier.padding(start = 6.dp))
                            }
                        }
                    }
                    n.hasSecret || n.secretHidden -> Note(stringResource(R.string.networks_key_pending))
                    else -> Note(stringResource(R.string.networks_key_missing), warn = true)
                }
                if (editor) {
                    TextButton(onClick = onGenerate, enabled = !busy, modifier = Modifier.testTag("network-generate")) {
                        Icon(Icons.Outlined.Key, null, Modifier.size(16.dp))
                        Text(
                            stringResource(if (n.hasSecret) R.string.networks_key_replace else R.string.networks_key_generate),
                            Modifier.padding(start = 6.dp),
                        )
                    }
                }
                Note(stringResource(R.string.networks_key_private_note))
                // Its configuration.
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                Text(stringResource(R.string.networks_detail_configuration), style = MaterialTheme.typography.titleSmall)
                Fact(stringResource(R.string.networks_detail_addresses), n.addresses.joinToString(", ").ifEmpty { "—" })
                if (n.dns.isNotEmpty()) Fact(stringResource(R.string.networks_detail_dns), n.dns.joinToString(", "))
                n.peers.forEach { p ->
                    Column(Modifier.padding(top = 8.dp)) {
                        Text(
                            stringResource(R.string.networks_detail_peer) + " " + p.publicKey.take(10) + "…",
                            style = MaterialTheme.typography.labelLarge,
                        )
                        p.endpoint?.let { Fact(stringResource(R.string.networks_detail_endpoint), it) }
                        if (p.allowedIps.isNotEmpty()) Fact(stringResource(R.string.networks_detail_allowed_ips), p.allowedIps.joinToString(", "))
                        p.keepalive?.let { Fact(stringResource(R.string.networks_detail_keepalive), "$it s") }
                    }
                }
            }
            // Hosts that use it, and its devices to add more.
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            Text(
                if (users > 0) pluralStringResource(R.plurals.networks_detail_hosts, users, users) else stringResource(R.string.networks_detail_no_hosts),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (editor) {
                if (available) {
                    OutlinedButton(onClick = onPeers, modifier = Modifier.padding(top = 8.dp).testTag("network-peers")) {
                        Icon(Icons.Outlined.Devices, null, Modifier.size(18.dp))
                        Text(stringResource(if (n.isTailscale) R.string.networks_ts_action_devices else R.string.networks_peers_show), Modifier.padding(start = 6.dp))
                    }
                }
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (available) {
                        OutlinedButton(onClick = onEdit, modifier = Modifier.testTag("network-edit")) {
                            Icon(Icons.Outlined.Edit, null, Modifier.size(18.dp))
                            Text(stringResource(R.string.common_edit), Modifier.padding(start = 6.dp))
                        }
                    }
                    TextButton(onClick = onDelete) {
                        Icon(Icons.Outlined.Delete, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                        Text(stringResource(R.string.common_delete), Modifier.padding(start = 6.dp), color = MaterialTheme.colorScheme.error)
                    }
                }
            } else {
                Note(stringResource(R.string.networks_detail_read_only))
            }
            Spacer(Modifier.navigationBarsPadding().height(24.dp))
        }
    }
}

/**
 * A tailnet's state on its page: connected (its address, devices, control
 * server, tailnet, MagicDNS, the server's node), connecting, the login
 * (Editors open the login page; Use-only members wait for one), the
 * approval, or the error with "Use another key".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TailnetState(
    n: NetworkItem,
    status: NetStatus?,
    state: NetState,
    editor: Boolean,
    watching: Boolean,
    onLogin: (String) -> Unit,
    onOtherKey: () -> Unit,
) {
    when (state) {
        NetState.UP -> {
            val st = status ?: return
            Text(tailnetSummaryText(st), Modifier.padding(top = 2.dp).testTag("tailnet-summary"), style = MaterialTheme.typography.bodyMedium, color = Brand.Green)
            Fact(stringResource(R.string.networks_ts_detail_control), Networks.controlServerLabel(st.controlUrl ?: n.tailscale?.controlUrl))
            st.tailnet?.let { Fact(stringResource(R.string.networks_ts_detail_tailnet), it) }
            st.magicDnsSuffix?.let { Fact(stringResource(R.string.networks_ts_detail_magic_dns), it) }
            listOfNotNull(st.nodeName, st.nodeDnsName?.trimEnd('.')).distinct().takeIf { it.isNotEmpty() }?.let {
                Fact(stringResource(R.string.networks_ts_detail_node), it.joinToString(" · "))
            }
            st.nodeAddresses.takeIf { it.isNotEmpty() }?.let { Fact(stringResource(R.string.networks_detail_node_addresses), it.joinToString(", ")) }
            st.nodeId?.let { Fact(stringResource(R.string.networks_ts_detail_node_id), it) }
            st.ephemeral?.let {
                Fact(stringResource(R.string.networks_ts_detail_ephemeral), stringResource(if (it) R.string.networks_ts_detail_ephemeral_yes else R.string.networks_ts_detail_ephemeral_no))
            }
            traffic(st.rxBytes, st.txBytes)?.let { Fact(stringResource(R.string.networks_detail_traffic), it) }
            st.streams?.takeIf { it > 0 }?.let { Fact(stringResource(R.string.networks_detail_streams), it.toString()) }
        }
        NetState.NEEDS_LOGIN -> {
            val url = status?.authUrl
            if (editor && url != null) {
                StateBox(stringResource(R.string.networks_ts_login_title), Brand.Amber, Modifier.testTag("tailnet-login")) {
                    Text(stringResource(R.string.networks_ts_login_text), style = MaterialTheme.typography.bodySmall)
                    if (watching) {
                        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                            Text(stringResource(R.string.networks_ts_login_waiting), Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Button(onClick = { onLogin(url) }, modifier = Modifier.padding(top = 8.dp).testTag("tailnet-login-open")) {
                        Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, Modifier.size(16.dp))
                        Text(stringResource(R.string.networks_ts_login_open), Modifier.padding(start = 6.dp))
                    }
                }
            } else {
                StateBox(stringResource(R.string.networks_ts_login_title), Brand.Blue, Modifier.testTag("tailnet-login-wait")) {
                    Text(stringResource(R.string.networks_ts_login_use_only), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        NetState.NEEDS_APPROVAL -> StateBox(stringResource(R.string.networks_ts_approval_title), Brand.Amber, Modifier.testTag("tailnet-approval")) {
            Text(
                stringResource(R.string.networks_ts_approval_text, status?.nodeName ?: status?.nodeDnsName?.trimEnd('.') ?: "—"),
                style = MaterialTheme.typography.bodySmall,
            )
            if (watching) {
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                    Text(stringResource(R.string.networks_ts_login_waiting), Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        NetState.ERROR -> StateBox(stringResource(R.string.networks_state_error), Brand.Red, Modifier.testTag("tailnet-error")) {
            val message = status?.message?.trim()?.takeIf { it.isNotEmpty() }
            Text((message ?: stringResource(R.string.networks_state_error_hint)).replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodySmall)
            status?.lastError?.takeIf { message == null || !message.contains(it) }?.let {
                Text(stringResource(R.string.networks_ts_error_details, it), Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            }
            if (editor) {
                OutlinedButton(onClick = onOtherKey, modifier = Modifier.padding(top = 8.dp).testTag("tailnet-other-key")) {
                    Icon(Icons.Outlined.Key, null, Modifier.size(16.dp))
                    Text(stringResource(R.string.networks_ts_error_other_key), Modifier.padding(start = 6.dp))
                }
            }
        }
        else -> Text(
            stateHint(state, Networks.TAILSCALE), Modifier.padding(top = 2.dp),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A tinted box with a title (login, approval, error). */
@Composable
private fun StateBox(title: String, color: Color, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    androidx.compose.material3.Surface(
        color = color.copy(alpha = 0.12f), shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth().padding(top = 6.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = color)
            Spacer(Modifier.height(4.dp))
            content()
        }
    }
}

@Composable
private fun Fact(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Text(label, Modifier.width(140.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun Note(text: String, warn: Boolean = false) {
    Text(
        text, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodySmall,
        color = if (warn) Brand.Amber else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** The server's public key: copy it, and the `[Peer]` block for the other side. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServerKeySheet(n: NetworkItem, generated: Boolean, canTest: Boolean, onCopied: () -> Unit, onTest: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        ServerKeyContent(n, generated, canTest, onCopied, onTest, onDismiss)
    }
}

/** The server's public key, the steps and the `[Peer]` block (inside [ServerKeySheet]). */
@Composable
internal fun ServerKeyContent(n: NetworkItem, generated: Boolean, canTest: Boolean, onCopied: () -> Unit, onTest: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val key = n.publicKey.orEmpty()
    val snippet = Networks.peerSnippet(n.name, key, n.addresses)
    run {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).testTag("server-key-sheet")) {
            Text(
                if (generated) stringResource(R.string.networks_key_generated_title) else stringResource(R.string.networks_key_title_of, n.name),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                stringResource(R.string.networks_key_description), Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(stringResource(R.string.networks_key_public), Modifier.padding(top = 16.dp), style = MaterialTheme.typography.labelLarge)
            SelectionContainer { Text(key, Modifier.padding(top = 4.dp), fontFamily = FontFamily.Monospace, fontSize = 14.sp) }
            TextButton(onClick = { copyText(context, n.name, key); onCopied() }, modifier = Modifier.testTag("server-key-copy")) {
                Icon(Icons.Outlined.ContentCopy, null, Modifier.size(16.dp))
                Text(stringResource(R.string.networks_key_copy), Modifier.padding(start = 6.dp))
            }
            Text("1. " + stringResource(R.string.networks_key_step_peer), style = MaterialTheme.typography.bodyMedium)
            Text(
                "2. " + stringResource(R.string.networks_key_step_allowed, n.addresses.joinToString(", ").ifEmpty { "—" }),
                Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyMedium,
            )
            Text("3. " + stringResource(R.string.networks_key_step_test), Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.networks_key_snippet), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                TextButton(onClick = { copyText(context, n.name, snippet); onCopied() }) {
                    Icon(Icons.Outlined.ContentCopy, null, Modifier.size(16.dp))
                    Text(stringResource(R.string.common_copy), Modifier.padding(start = 6.dp))
                }
            }
            androidx.compose.material3.Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.medium) {
                SelectionContainer {
                    Text(snippet, Modifier.fillMaxWidth().padding(12.dp).testTag("server-key-snippet"), fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                }
            }
            Note(stringResource(R.string.networks_key_private_note))
            Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (canTest) {
                    Button(onClick = onTest) {
                        Icon(Icons.Outlined.NetworkCheck, null, Modifier.size(18.dp))
                        Text(stringResource(R.string.networks_test), Modifier.padding(start = 6.dp))
                    }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) }
            }
            Spacer(Modifier.navigationBarsPadding().height(24.dp))
        }
    }
}

/**
 * Devices of a network (`/peers`, it brings it up) with "Add host at …". A
 * tailnet: its devices with "Add host" (by MagicDNS name), "Add a host in"
 * each subnet they route, the ones already added marked and "Add N hosts".
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun PeersSheet(
    api: NetworksApi?,
    n: NetworkItem,
    editor: Boolean,
    onDismiss: () -> Unit,
    onAdd: (HostAddress?, String?) -> Unit,
    onAddedAll: (Int, List<UiText>) -> Unit,
) {
    val resources = LocalResources.current
    var peers by remember(n.id) { mutableStateOf<List<NetPeer>?>(null) }
    var hosts by remember(n.id) { mutableStateOf<List<com.termoak.app.data.HostRef>>(emptyList()) }
    var error by remember(n.id) { mutableStateOf<UiText?>(null) }
    var needsLogin by remember(n.id) { mutableStateOf(false) }
    var adding by remember(n.id) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    suspend fun load() {
        try {
            val list = api?.peers(n.id) ?: emptyList()
            if (n.isTailscale) hosts = api?.hosts(n.vaultId) ?: emptyList()
            peers = list
        } catch (e: Exception) {
            val info = com.termoak.app.data.ApiErrors.of(e)
            needsLogin = n.isTailscale && Networks.needsLogin(info?.code, info?.detailsJson)
            error = Networks.errorFor(e, n, R.string.networks_peers_failed)
            peers = emptyList()
        }
    }
    LaunchedEffect(n.id) { load() }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).testTag("peers-sheet")) {
            Text(stringResource(R.string.networks_peers_title, n.name), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(
                    when {
                        n.isTailscale -> R.string.networks_ts_peers_hint
                        n.isWireguard -> R.string.networks_peers_hint_wireguard
                        else -> R.string.networks_peers_hint
                    },
                ),
                Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val list = peers
            when {
                list == null -> Row(Modifier.padding(vertical = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text(stringResource(R.string.networks_peers_loading), Modifier.padding(start = 12.dp))
                }
                error != null -> Box(Modifier.padding(vertical = 12.dp)) {
                    if (needsLogin) StateBox(stringResource(R.string.networks_ts_peers_needs_login), Brand.Amber) { Text(error!!.asString(), style = MaterialTheme.typography.bodySmall) }
                    else MessageBox(error!!.asString(), isError = true)
                }
                n.isTailscale -> TailnetDevices(
                    list, hosts, n, editor, adding,
                    onAdd = { p -> onAdd(p.deviceAddress?.let { HostAddress(it, true, it) }, p.name) },
                    onAddIn = { route -> onAdd(Networks.hostAddresses(NetPeer(addresses = listOf(route))).firstOrNull(), null) },
                    onAddAll = { fresh ->
                        val a = api ?: return@TailnetDevices
                        adding = true
                        scope.launch {
                            var added = 0
                            val failures = mutableListOf<UiText>()
                            for (p in fresh) {
                                val body = Networks.deviceHostBody(p, n) ?: continue
                                try {
                                    a.addHost(body)
                                    added++
                                } catch (e: Exception) {
                                    failures += uiText(R.string.networks_ts_peers_add_failed, p.title, e.toUiText(R.string.networks_host_add_failed).resolve(resources))
                                }
                            }
                            hosts = a.hosts(n.vaultId)
                            adding = false
                            onAddedAll(added, failures)
                        }
                    },
                )
                list.isEmpty() -> Text(stringResource(R.string.networks_peers_empty), Modifier.padding(vertical = 16.dp))
                else -> list.forEach { p ->
                    Column(Modifier.padding(top = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(p.title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Pill(
                                stringResource(if (p.online == true) R.string.networks_peers_online else R.string.networks_peers_offline),
                                if (p.online == true) Brand.Green else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        val meta = listOfNotNull(
                            p.addresses.joinToString(", ").ifEmpty { null },
                            p.lastSeen?.let { stringResource(R.string.networks_peers_seen, relativeTime(it)) },
                        )
                        if (meta.isNotEmpty()) {
                            Text(meta.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (editor) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Networks.hostAddresses(p).forEach { a ->
                                    OutlinedButton(onClick = { onAdd(a, null) }, modifier = Modifier.testTag("add-host-${a.source}")) {
                                        Icon(Icons.Outlined.Add, null, Modifier.size(16.dp))
                                        Text(
                                            if (a.exact) stringResource(R.string.networks_peers_add_at, a.address)
                                            else stringResource(R.string.networks_peers_add_in, a.source),
                                            Modifier.padding(start = 4.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (editor) {
                TextButton(onClick = { onAdd(null, null) }, modifier = Modifier.padding(top = 8.dp)) {
                    Icon(Icons.Outlined.Add, null, Modifier.size(16.dp))
                    Text(stringResource(R.string.networks_add_host), Modifier.padding(start = 6.dp))
                }
            }
            Spacer(Modifier.navigationBarsPadding().height(24.dp))
        }
    }
}

/**
 * The devices of a tailnet (inside [PeersSheet]; drawn alone by the
 * screenshot tests): online first, with their MagicDNS name, addresses, OS,
 * tags and routes; "Add host", "Add a host in <subnet>" for each route
 * (never `/0`), "Added: <host>" for the ones already added, and "Add N
 * hosts" for the rest.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TailnetDevices(
    peers: List<NetPeer>,
    hosts: List<com.termoak.app.data.HostRef>,
    n: NetworkItem,
    editor: Boolean,
    adding: Boolean,
    onAdd: (NetPeer) -> Unit,
    onAddIn: (String) -> Unit,
    onAddAll: (List<NetPeer>) -> Unit,
) {
    if (peers.isEmpty()) {
        Text(stringResource(R.string.networks_ts_peers_empty), Modifier.padding(top = 16.dp), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(R.string.networks_ts_peers_empty_text), Modifier.padding(top = 4.dp, bottom = 8.dp), style = MaterialTheme.typography.bodySmall)
        return
    }
    val fresh = Networks.newDevices(peers, n.id, hosts)
    if (editor && fresh.size > 1) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.networks_ts_peers_add_all_hint), Modifier.weight(1f).padding(end = 8.dp),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = { onAddAll(fresh) }, enabled = !adding, modifier = Modifier.testTag("tailnet-add-all")) {
                if (adding) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                else Icon(Icons.Outlined.Add, null, Modifier.size(16.dp))
                Text(pluralStringResource(R.plurals.networks_ts_peers_add_all, fresh.size, fresh.size), Modifier.padding(start = 6.dp))
            }
        }
    }
    Networks.sortDevices(peers).forEach { p ->
        val host = Networks.importedHost(p, n.id, hosts)
        Column(Modifier.padding(top = 12.dp).testTag("device-${p.title}")) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(p.name ?: p.bareDnsName ?: p.title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Pill(
                    stringResource(if (p.online == true) R.string.networks_peers_online else R.string.networks_ts_peers_offline),
                    if (p.online == true) Brand.Green else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val meta = listOfNotNull(
                p.bareDnsName,
                p.addresses.joinToString(", ").ifEmpty { null },
                p.os,
                p.lastSeen?.takeIf { p.online != true }?.let { stringResource(R.string.networks_peers_seen, relativeTime(it)) },
                p.hostRoutes.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.networks_ts_peers_routes, it.joinToString(", ")) },
            )
            if (meta.isNotEmpty()) {
                Text(meta.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (p.tags.isNotEmpty()) {
                FlowRow(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) { p.tags.forEach { Pill(it, Brand.Blue) } }
            }
            if (editor) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    when {
                        host != null -> Text(
                            "✓ " + stringResource(R.string.networks_ts_peers_imported, host.label), Modifier.padding(vertical = 10.dp).testTag("device-imported"),
                            style = MaterialTheme.typography.labelLarge, color = Brand.Green,
                        )
                        p.deviceAddress != null -> OutlinedButton(onClick = { onAdd(p) }, modifier = Modifier.testTag("device-add-${p.deviceAddress}")) {
                            Icon(Icons.Outlined.Add, null, Modifier.size(16.dp))
                            Text(stringResource(R.string.networks_ts_peers_add), Modifier.padding(start = 4.dp))
                        }
                    }
                    p.hostRoutes.forEach { r ->
                        OutlinedButton(onClick = { onAddIn(r) }, modifier = Modifier.testTag("device-add-in-$r")) {
                            Icon(Icons.Outlined.Add, null, Modifier.size(16.dp))
                            Text(stringResource(R.string.networks_peers_add_in, r), Modifier.padding(start = 4.dp))
                        }
                    }
                }
            }
        }
    }
    if (editor) {
        Text(
            stringResource(R.string.networks_ts_peers_note), Modifier.padding(top = 12.dp),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * "Add a host in Office": its address (a peer's, or one inside its subnet),
 * name, port and user; `POST /hosts` in the network's vault with the network
 * chosen. [onAdd] returns the error to show, or `null` when it was added.
 */
@Composable
private fun AddNetworkHostDialog(n: NetworkItem, at: HostAddress?, initialLabel: String? = null, onDismiss: () -> Unit, onAdd: suspend (String) -> UiText?) {
    val scope = rememberCoroutineScope()
    var address by remember { mutableStateOf(at?.address ?: "") }
    var label by remember { mutableStateOf(initialLabel?.takeIf { it.isNotBlank() } ?: at?.takeIf { it.exact }?.address ?: "") }
    var labelTouched by remember { mutableStateOf(initialLabel != null) }
    var port by remember { mutableStateOf("22") }
    var user by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<UiText?>(null) }
    var busy by remember { mutableStateOf(false) }
    val complete = address.isNotBlank() && !address.trim().endsWith(".") && address.trim().none { it.isWhitespace() }
    val portValue = port.toIntOrNull()?.takeIf { it in 1..65535 }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.networks_add_host_title, n.name)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.networks_add_host_hint), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    address, { v -> address = v.trim(); if (!labelTouched) label = v.trim() }, Modifier.fillMaxWidth().testTag("add-host-address"),
                    label = { Text(stringResource(R.string.networks_add_host_address)) }, singleLine = true,
                    supportingText = when {
                        at != null && !at.exact -> { { Text(stringResource(R.string.networks_add_host_in_subnet, at.source)) } }
                        n.isTailscale -> { { Text(stringResource(R.string.networks_ts_address_hint)) } }
                        else -> null
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                OutlinedTextField(
                    label, { label = it; labelTouched = true }, Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.common_name)) }, singleLine = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        user, { user = it.trim() }, Modifier.weight(1f), label = { Text(stringResource(R.string.common_username)) },
                        placeholder = { Text("root") }, singleLine = true,
                    )
                    OutlinedTextField(
                        port, { port = it.filter(Char::isDigit).take(5) }, Modifier.width(96.dp), label = { Text(stringResource(R.string.common_port)) },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
                error?.let { MessageBox(it.asString(), isError = true) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && complete && portValue != null,
                onClick = {
                    busy = true
                    scope.launch {
                        error = onAdd(Networks.hostBody(label.trim().ifEmpty { address.trim() }, address.trim(), portValue ?: 22, user.ifBlank { null }, n))
                        busy = false
                    }
                },
            ) { Text(stringResource(R.string.networks_add_host_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.common_cancel)) } },
    )
}

/**
 * What the form sends: the body, and whether the server then generates (or
 * replaces) the key; [test]: bring it up once saved (a new tailnet, or a
 * new auth key).
 */
data class NetworkPlan(val body: String, val generate: Boolean, val replace: Boolean, val test: Boolean = false)

/** The auth key choice of the Tailscale form. */
enum class KeyMode {
    /** Creating: optional key. Editing: replace the stored one (or add one). */
    NEW,
    /** Editing a network with a key: keep it (no `secret` in the PUT). */
    KEEP,
    /** Editing a network with a key: remove it (`secret: null`). */
    REMOVE,
    /** Editing a network without a key: still none. */
    NONE,
}

/**
 * Add or edit a WireGuard network: kind (Tailscale and ZeroTier coming
 * soon), name, vault (new ones), description and the `.conf` (pasted,
 * opened with the system's file picker, or from the template), read as it
 * is typed; without a PrivateKey the server generates the key. Editing
 * shows the configuration without keys: the stored key stays unless a
 * PrivateKey line is added or a new key is generated. [onSave] returns the
 * error to show, or `null` when it was saved.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun NetworkForm(
    editing: NetworkItem?,
    vaults: List<VaultInfo>,
    kinds: List<String>,
    keyMode: KeyMode?,
    onDismiss: () -> Unit,
    onSave: suspend (NetworkPlan) -> UiText?,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
        NetworkFormPage(editing, vaults, onDismiss, onSave, kinds = kinds, initialKeyMode = keyMode)
    }
}

/** The form's page (inside [NetworkForm]'s full-screen dialog). */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun NetworkFormPage(
    editing: NetworkItem?,
    vaults: List<VaultInfo>,
    onDismiss: () -> Unit,
    onSave: suspend (NetworkPlan) -> UiText?,
    initialConf: String? = null,
    /** Kinds the server joins (`features.network_kinds`). */
    kinds: List<String> = listOf(Networks.WIREGUARD),
    /** Tailscale, editing: the key choice to start with ("Use another key": [KeyMode.NEW]). */
    initialKeyMode: KeyMode? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyNote = stringResource(R.string.networks_form_conf_key_kept)
    var kind by remember { mutableStateOf(editing?.kind ?: Networks.WIREGUARD) }
    val tailscaleOffered = Networks.TAILSCALE in kinds
    // Tailscale / Headscale.
    val ts0 = editing?.tailscale ?: com.termoak.app.data.TsConfig()
    var headscale by remember { mutableStateOf(ts0.controlUrl != null) }
    var controlUrl by remember { mutableStateOf(ts0.controlUrl ?: "") }
    val keyModes = when {
        editing == null -> listOf(KeyMode.NEW)
        editing.hasSecret -> listOf(KeyMode.KEEP, KeyMode.NEW, KeyMode.REMOVE)
        else -> listOf(KeyMode.NONE, KeyMode.NEW)
    }
    var keyMode by remember { mutableStateOf(initialKeyMode?.takeIf { it in keyModes } ?: keyModes.first()) }
    var authKey by remember { mutableStateOf("") }
    var hostname by remember { mutableStateOf(ts0.hostname ?: "") }
    var tagsText by remember { mutableStateOf(ts0.tags.joinToString(", ")) }
    var advanced by remember { mutableStateOf(ts0.hostname != null || ts0.tags.isNotEmpty() || ts0.ephemeral != null) }
    // `ephemeral` stays as stored (null = the default) unless the switch is touched.
    var ephemeralTouched by remember { mutableStateOf(false) }
    var ephemeralValue by remember { mutableStateOf(ts0.ephemeral ?: false) }
    val keyWillExist = when (keyMode) {
        KeyMode.KEEP -> editing?.hasSecret == true
        KeyMode.NEW -> editing != null || authKey.isNotBlank()
        else -> false
    }
    val ephemeralShown = if (ephemeralTouched || ts0.ephemeral != null) ephemeralValue else keyWillExist
    var name by remember { mutableStateOf(editing?.name ?: "") }
    var description by remember { mutableStateOf(editing?.description ?: "") }
    var vault by remember { mutableStateOf(vaults.firstOrNull { it.kind == com.termoak.ffi.VaultKind.PERSONAL }?.id ?: vaults.firstOrNull()?.id) }
    var conf by remember { mutableStateOf(initialConf ?: editing?.let { Networks.confText(it, keyNote) } ?: "") }
    val read = remember(conf) { conf.takeIf { it.isNotBlank() }?.let { Networks.check(it) } }
    val parsed = (read as? ConfRead.Ok)?.conf
    val hasSecret = editing?.hasSecret == true
    // Without a PrivateKey the server makes one (unless the stored one stays); with one, the switch can ignore it.
    var generateChoice by remember { mutableStateOf(false) }
    val confKey = parsed?.privateKey != null
    val generate = if (confKey || hasSecret) generateChoice else true
    var error by remember { mutableStateOf<UiText?>(null) }
    var busy by remember { mutableStateOf(false) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val fileName = runCatching {
            context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()
        scope.launch {
            val bytes = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { s -> s.readNBytesCompat(Networks.MAX_CONF_BYTES + 1) }
                }.getOrNull()
            }
            when {
                bytes == null -> error = uiText(R.string.networks_form_import_failed)
                bytes.size > Networks.MAX_CONF_BYTES -> error = uiText(R.string.networks_form_import_too_big)
                else -> {
                    conf = bytes.decodeToString()
                    error = null
                    if (name.isBlank()) fileName?.substringBeforeLast('.')?.takeIf { it.isNotBlank() }?.let { name = it }
                }
            }
        }
    }

    fun tailscalePlan(): NetworkPlan? {
        if (name.isBlank()) { error = uiText(R.string.networks_form_error_name); return null }
        if (editing == null && vault == null) { error = uiText(R.string.networks_form_no_vault); return null }
        val url = if (headscale) Networks.cleanControlUrl(controlUrl) else null
        val tags = Networks.parseTags(tagsText)
        val key = authKey.trim()
        val problem = Networks.tailscaleProblem(url, hostname, tags, key.takeIf { keyMode == KeyMode.NEW })
            ?: com.termoak.app.data.TsProblem.KEY_MISSING.takeIf { keyMode == KeyMode.NEW && editing != null && key.isEmpty() }
        if (problem != null) { error = Networks.tsProblemText(problem); return null }
        val secret = when {
            keyMode == KeyMode.NEW && key.isNotEmpty() -> com.termoak.app.data.SecretChange.Set(key)
            keyMode == KeyMode.REMOVE -> com.termoak.app.data.SecretChange.Clear
            else -> com.termoak.app.data.SecretChange.Keep
        }
        val ts = com.termoak.app.data.TsConfig(
            controlUrl = url, hostname = hostname.trim().ifEmpty { null }, tags = tags,
            ephemeral = if (ephemeralTouched) ephemeralValue else ts0.ephemeral,
        )
        return NetworkPlan(
            Networks.tailscaleBody(editing?.json, if (editing == null) vault else null, name.trim(), description.trim(), ts, secret),
            generate = false, replace = false,
            // A new tailnet or a new key: bring it up to see how it went.
            test = editing == null || secret is com.termoak.app.data.SecretChange.Set,
        )
    }

    fun plan(): NetworkPlan? {
        if (kind == Networks.TAILSCALE) return tailscalePlan()
        val c = when (val r = read) {
            null -> { error = uiText(R.string.networks_form_error_conf); return null }
            is ConfRead.Bad -> { error = Networks.confErrorText(r.error); return null }
            is ConfRead.Ok -> r.conf
        }
        if (name.isBlank()) { error = uiText(R.string.networks_form_error_name); return null }
        if (editing == null && vault == null) { error = uiText(R.string.networks_form_no_vault); return null }
        if (c.privateKey == null && c.presharedKey != null && hasSecret && !generate) {
            error = uiText(R.string.networks_form_error_psk_needs_key); return null
        }
        val secret = when {
            c.privateKey != null && !generate -> c.privateKey to c.presharedKey
            // A preshared key alone for a new one (the server's key comes next).
            generate && c.presharedKey != null && editing == null -> null to c.presharedKey
            else -> null
        }
        return NetworkPlan(
            Networks.body(editing?.json, if (editing == null) vault else null, name.trim(), description.trim(), c, secret),
            generate = generate,
            replace = generate && hasSecret,
        )
    }

    run {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(if (editing == null) R.string.networks_form_new_title else R.string.networks_form_edit_title)) },
                    navigationIcon = { IconButton(onClick = onDismiss, enabled = !busy) { Icon(Icons.Outlined.Close, stringResource(R.string.common_close)) } },
                    actions = {
                        TextButton(
                            enabled = !busy,
                            modifier = Modifier.testTag("network-save"),
                            onClick = {
                                val p = plan() ?: return@TextButton
                                busy = true
                                scope.launch {
                                    error = onSave(p)
                                    busy = false
                                }
                            },
                        ) {
                            if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            else Text(stringResource(R.string.common_save))
                        }
                    },
                )
            },
        ) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                error?.let { MessageBox(it.asString(), isError = true) }
                // The kind: WireGuard, Tailscale when the server joins it; ZeroTier in a later version.
                Text(stringResource(R.string.networks_form_kind), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        kind == Networks.WIREGUARD, { kind = Networks.WIREGUARD; error = null }, enabled = editing == null || editing.isWireguard,
                        label = { Text("WireGuard") }, modifier = Modifier.testTag("network-kind-wireguard"),
                    )
                    FilterChip(
                        kind == Networks.TAILSCALE, { kind = Networks.TAILSCALE; error = null },
                        enabled = if (editing != null) editing.isTailscale else tailscaleOffered,
                        label = {
                            Text(
                                if (tailscaleOffered || editing?.isTailscale == true) stringResource(R.string.networks_ts_kind_option)
                                else stringResource(R.string.networks_form_kind_soon, "Tailscale"),
                            )
                        },
                        modifier = Modifier.testTag("network-kind-tailscale"),
                    )
                    FilterChip(false, {}, enabled = false, label = { Text(stringResource(R.string.networks_form_kind_soon, "ZeroTier")) })
                }
                OutlinedTextField(
                    name, { name = it.take(100) }, Modifier.fillMaxWidth().testTag("network-name"),
                    label = { Text(stringResource(R.string.common_name)) }, placeholder = { Text(stringResource(R.string.networks_form_name_placeholder)) },
                    singleLine = true,
                )
                if (editing == null) {
                    Picker(
                        stringResource(R.string.networks_form_vault),
                        vaults.firstOrNull { it.id == vault }?.let { com.termoak.app.data.Accounts.vaultLabel(context, it) },
                        stringResource(R.string.networks_form_no_vault),
                        vaults.map { it.id to com.termoak.app.data.Accounts.vaultLabel(context, it) },
                    ) { vault = it }
                    Text(stringResource(R.string.networks_form_vault_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                // An end-to-end encrypted vault: the network's key stays readable by the server (E2EE-SSO-AUDIT §9.3).
                val vaultOf = vaults.firstOrNull { it.id == (editing?.vaultId ?: vault) }
                if (vaultOf?.e2ee != null) {
                    Text(
                        stringResource(R.string.vault_e2ee_network_form_warning), Modifier.testTag("network-e2ee-warning"),
                        style = MaterialTheme.typography.bodySmall, color = Brand.Amber,
                    )
                }
                OutlinedTextField(
                    description, { description = it }, Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.networks_form_description)) },
                    placeholder = { Text(stringResource(R.string.networks_form_description_placeholder)) }, singleLine = true,
                )
                if (kind == Networks.TAILSCALE) {
                    TailscaleFields(
                        editing = editing, headscale = headscale, onHeadscale = { headscale = it },
                        controlUrl = controlUrl, onControlUrl = { controlUrl = it },
                        keyModes = keyModes, keyMode = keyMode, onKeyMode = { keyMode = it },
                        authKey = authKey, onAuthKey = { authKey = it },
                        advanced = advanced, onAdvanced = { advanced = it },
                        hostname = hostname, onHostname = { hostname = it.trim().take(63) },
                        tags = tagsText, onTags = { tagsText = it },
                        ephemeral = ephemeralShown, ephemeralSet = ephemeralTouched || ts0.ephemeral != null, keyWillExist = keyWillExist,
                        onEphemeral = { ephemeralTouched = true; ephemeralValue = it },
                    )
                    Spacer(Modifier.height(24.dp))
                    return@Column
                }
                Text(
                    stringResource(if (editing == null) R.string.networks_form_conf else R.string.networks_form_conf_edit),
                    Modifier.padding(top = 4.dp), style = MaterialTheme.typography.labelLarge,
                )
                Text(stringResource(R.string.networks_form_conf_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pick.launch(arrayOf("*/*")) }, modifier = Modifier.testTag("network-import")) {
                        Icon(Icons.Outlined.FileOpen, null, Modifier.size(16.dp))
                        Text(stringResource(R.string.networks_form_import), Modifier.padding(start = 6.dp))
                    }
                    OutlinedButton(onClick = {
                        val clip = context.getSystemService(ClipboardManager::class.java)?.primaryClip
                        clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()?.let { conf = it }
                    }) {
                        Icon(Icons.Outlined.ContentPaste, null, Modifier.size(16.dp))
                        Text(stringResource(R.string.common_paste), Modifier.padding(start = 6.dp))
                    }
                    if (editing == null) {
                        TextButton(onClick = { conf = Networks.CONF_TEMPLATE }) { Text(stringResource(R.string.networks_form_template)) }
                    }
                }
                OutlinedTextField(
                    conf, { conf = it }, Modifier.fillMaxWidth().heightIn(min = 220.dp).testTag("network-conf"),
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    placeholder = { Text("[Interface]\nAddress = 10.0.0.2/32\n…\n\n[Peer]\nPublicKey = …\nEndpoint = vpn.example.com:51820\nAllowedIPs = 10.0.0.0/24", fontFamily = FontFamily.Monospace) },
                    isError = read is ConfRead.Bad,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false),
                )
                // What was read, or why it can't be used.
                when (val r = read) {
                    is ConfRead.Bad -> Text(Networks.confErrorText(r.error).asString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    is ConfRead.Ok -> Column(Modifier.testTag("network-conf-ok"), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            stringResource(R.string.networks_form_conf_ok, r.conf.addresses.joinToString(", "), r.conf.peers.size),
                            style = MaterialTheme.typography.bodySmall, color = Brand.Green,
                        )
                        Text(
                            stringResource(
                                when {
                                    confKey && !generate -> R.string.networks_form_key_from_conf
                                    generate && hasSecret -> R.string.networks_form_key_replaced
                                    generate -> R.string.networks_form_key_generated
                                    else -> R.string.networks_form_key_kept
                                },
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (r.conf.presharedKey != null && (!generate || editing == null)) {
                            Text(stringResource(R.string.networks_form_psk_from_conf), style = MaterialTheme.typography.bodySmall)
                        }
                        if (r.conf.ignored.isNotEmpty()) {
                            Text(
                                stringResource(R.string.networks_form_ignored, r.conf.ignored.joinToString(", ")),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (r.conf.peers.all { it.keepalive == null }) {
                            Text(
                                stringResource(R.string.networks_form_keepalive_hint), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    null -> Unit
                }
                if (confKey || hasSecret) {
                    Row(
                        Modifier.fillMaxWidth().clickable { generateChoice = !generateChoice }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(if (confKey) R.string.networks_form_generate_instead else R.string.networks_form_generate_new),
                            Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                        )
                        Switch(generateChoice, { generateChoice = it }, Modifier.testTag("network-generate-switch"))
                    }
                }
                Text(
                    stringResource(R.string.networks_form_secret_note), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/**
 * The Tailscale part of the network form: control server (Tailscale or
 * Headscale with its URL), the auth key (optional; editing keeps, replaces
 * or removes it) with where to make it, and Advanced: hostname, tags and
 * "ephemeral" (the default said under it until it is changed).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TailscaleFields(
    editing: NetworkItem?,
    headscale: Boolean,
    onHeadscale: (Boolean) -> Unit,
    controlUrl: String,
    onControlUrl: (String) -> Unit,
    keyModes: List<KeyMode>,
    keyMode: KeyMode,
    onKeyMode: (KeyMode) -> Unit,
    authKey: String,
    onAuthKey: (String) -> Unit,
    advanced: Boolean,
    onAdvanced: (Boolean) -> Unit,
    hostname: String,
    onHostname: (String) -> Unit,
    tags: String,
    onTags: (String) -> Unit,
    ephemeral: Boolean,
    ephemeralSet: Boolean,
    keyWillExist: Boolean,
    onEphemeral: (Boolean) -> Unit,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    var showKey by remember { mutableStateOf(false) }
    Text(stringResource(R.string.networks_ts_form_control), Modifier.padding(top = 4.dp), style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(!headscale, { onHeadscale(false) }, label = { Text(stringResource(R.string.networks_ts_form_control_tailscale)) }, modifier = Modifier.testTag("ts-control-tailscale"))
        FilterChip(headscale, { onHeadscale(true) }, label = { Text(stringResource(R.string.networks_ts_form_control_headscale)) }, modifier = Modifier.testTag("ts-control-headscale"))
    }
    if (headscale) {
        OutlinedTextField(
            controlUrl, onControlUrl, Modifier.fillMaxWidth().testTag("ts-control-url"),
            label = { Text(stringResource(R.string.networks_ts_form_control_url)) }, placeholder = { Text("https://headscale.example.com") },
            supportingText = { Text(stringResource(R.string.networks_ts_form_control_url_hint)) }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
        )
    }
    // The auth key.
    Text(stringResource(R.string.networks_ts_form_key), Modifier.padding(top = 4.dp), style = MaterialTheme.typography.labelLarge)
    if (keyModes.size > 1) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            keyModes.forEach { m ->
                FilterChip(
                    keyMode == m, { onKeyMode(m) }, modifier = Modifier.testTag("ts-key-${m.name.lowercase()}"),
                    label = {
                        Text(
                            stringResource(
                                when (m) {
                                    KeyMode.KEEP -> R.string.networks_ts_form_key_keep
                                    KeyMode.NEW -> if (editing?.hasSecret == true) R.string.networks_ts_form_key_new else R.string.networks_ts_form_key_add
                                    KeyMode.REMOVE -> R.string.networks_ts_form_key_remove
                                    KeyMode.NONE -> R.string.networks_ts_form_key_none
                                },
                            ),
                        )
                    },
                )
            }
        }
        Text(
            stringResource(
                when (keyMode) {
                    KeyMode.KEEP -> R.string.networks_ts_form_key_keep_hint
                    KeyMode.NEW -> R.string.networks_ts_form_key_new_hint
                    KeyMode.REMOVE -> R.string.networks_ts_form_key_remove_hint
                    KeyMode.NONE -> R.string.networks_ts_form_key_none_hint
                },
            ),
            style = MaterialTheme.typography.bodySmall, color = muted,
        )
    }
    if (keyMode == KeyMode.NEW) {
        OutlinedTextField(
            authKey, { onAuthKey(it.trim()) }, Modifier.fillMaxWidth().testTag("ts-auth-key"),
            label = { Text(stringResource(if (editing != null) R.string.networks_ts_form_key_new_label else R.string.networks_ts_form_key_optional)) },
            placeholder = { Text(if (headscale) "hskey-auth-…" else "tskey-auth-…", fontFamily = FontFamily.Monospace) },
            singleLine = true, textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            visualTransformation = if (showKey) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
            trailingIcon = {
                IconButton(onClick = { showKey = !showKey }) {
                    Icon(if (showKey) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, stringResource(R.string.common_show))
                }
            },
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.networks_ts_form_key_hint), style = MaterialTheme.typography.bodySmall, color = muted)
            Text(
                stringResource(if (headscale) R.string.networks_ts_form_key_hint_headscale else R.string.networks_ts_form_key_hint_tailscale),
                style = MaterialTheme.typography.bodySmall, color = muted,
                fontFamily = if (headscale) FontFamily.Monospace else null,
            )
            if (editing == null) Text(stringResource(R.string.networks_ts_form_key_hint_none), style = MaterialTheme.typography.bodySmall, color = muted)
        }
    }
    // Advanced: hostname, tags, ephemeral.
    Row(
        Modifier.fillMaxWidth().clickable { onAdvanced(!advanced) }.padding(vertical = 6.dp).testTag("ts-advanced"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.networks_ts_form_advanced), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
        Icon(if (advanced) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null)
    }
    if (advanced) {
        val auto = Networks.defaultHostname(editing?.id)
        OutlinedTextField(
            hostname, onHostname, Modifier.fillMaxWidth().testTag("ts-hostname"),
            label = { Text(stringResource(R.string.networks_ts_form_hostname)) },
            placeholder = { Text(stringResource(R.string.networks_ts_form_hostname_auto, auto)) },
            supportingText = { Text(stringResource(R.string.networks_ts_form_hostname_hint, auto)) }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false),
        )
        OutlinedTextField(
            tags, onTags, Modifier.fillMaxWidth().testTag("ts-tags"),
            label = { Text(stringResource(R.string.networks_ts_form_tags)) }, placeholder = { Text("tag:termoak") },
            supportingText = { Text(stringResource(R.string.networks_ts_form_tags_hint)) }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false),
        )
        Row(
            Modifier.fillMaxWidth().clickable { onEphemeral(!ephemeral) }.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.networks_ts_form_ephemeral), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Switch(ephemeral, { onEphemeral(it) }, Modifier.testTag("ts-ephemeral"))
        }
        Text(
            stringResource(
                when {
                    ephemeralSet -> R.string.networks_ts_form_ephemeral_set
                    keyWillExist -> R.string.networks_ts_form_ephemeral_default_on
                    else -> R.string.networks_ts_form_ephemeral_default_off
                },
            ),
            Modifier.testTag("ts-ephemeral-note"), style = MaterialTheme.typography.bodySmall, color = muted,
        )
    }
    Text(stringResource(R.string.networks_ts_form_secret_note), style = MaterialTheme.typography.bodySmall, color = muted)
}

/** `readNBytes` (Java 11) is not on every Android version: reads at most [max] bytes. */
private fun java.io.InputStream.readNBytesCompat(max: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buf = ByteArray(8192)
    while (out.size() < max) {
        val n = read(buf, 0, minOf(buf.size, max - out.size()))
        if (n < 0) break
        out.write(buf, 0, n)
    }
    return out.toByteArray()
}
