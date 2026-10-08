package com.termoak.app.ui

import androidx.activity.compose.LocalActivity
import androidx.annotation.StringRes
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.MenuOpen
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import androidx.navigation.NavHostController
import com.termoak.app.BuildConfig
import com.termoak.app.R
import com.termoak.app.TermoakApp
import com.termoak.app.data.uid
import com.termoak.app.data.HostProtocol
import com.termoak.app.data.isTelnet
import com.termoak.app.data.WideLayout
import com.termoak.app.term.ServerTerminal
import com.termoak.app.term.TabOrder
import com.termoak.app.term.TermSession
import com.termoak.app.term.TermState
import com.termoak.ffi.SshHost
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.rememberCoroutineScope
import com.termoak.app.data.QuickTarget
import com.termoak.app.userMessage
import com.termoak.ffi.HostSettings
import com.termoak.ffi.SecretChange
import com.termoak.ffi.SyncMode
import com.termoak.ffi.TermoakException
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.math.abs
import kotlin.math.roundToInt

// The layout of wide windows (≥ 840 dp: tablets, unfolded foldables,
// Chromebooks, Samsung DeX), like the desktop app (app.rs of
// TermoakSSH/desktop): a tab bar on top ("Home" and one tab per terminal),
// and on Home a fixed sidebar with the sections beside their content.
// Phones and medium windows keep the bottom bar or the navigation rail.

/** The window is wide enough for the desktop layout (it changes when a foldable folds or unfolds). */
val LocalDesktop = compositionLocalOf { false }

/**
 * Whether this window gets the desktop layout, as chosen in Settings →
 * Appearance ([mode], see [WideLayout]): by default only with Material's
 * Expanded width class (≥ 840 dp) and a height that isn't Compact (tablets,
 * unfolded foldables, Chromebooks; not phones in landscape). It follows the
 * window as it changes; the terminals live outside the composition.
 */
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun rememberDesktopLayout(mode: WideLayout): Boolean {
    val activity = LocalActivity.current ?: return false
    val size = calculateWindowSizeClass(activity)
    return mode.desktop(
        expandedWidth = size.widthSizeClass == WindowWidthSizeClass.Expanded,
        compactHeight = size.heightSizeClass == WindowHeightSizeClass.Compact,
    )
}

/** Requests to the desktop layout from anywhere (keyboard shortcuts, the terminal). */
object DesktopUi {
    /** The "+" of the tab bar: the quick connect dialog. */
    val quickConnect = MutableStateFlow(false)
}

/** Shows the terminal tab: back to it when it is already in the back stack, otherwise on top. */
fun NavHostController.showTerminal() {
    if (currentDestination?.route == Routes.TERMINAL) return
    if (!popBackStack(Routes.TERMINAL, inclusive = false)) navigate(Routes.TERMINAL) { launchSingleTop = true }
}

/**
 * Mouse and trackpad buttons, seen before the element's own clicks: right
 * click ([onSecondary], where it was pressed), Ctrl+click and middle click.
 * Touch goes on as usual.
 */
fun Modifier.mouseActions(
    onSecondary: ((Offset) -> Unit)? = null,
    onCtrlClick: (() -> Unit)? = null,
    onMiddle: (() -> Unit)? = null,
): Modifier = composed {
    val secondary by rememberUpdatedState(onSecondary)
    val ctrlClick by rememberUpdatedState(onCtrlClick)
    val middle by rememberUpdatedState(onMiddle)
    pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val e = awaitPointerEvent(PointerEventPass.Initial)
                if (e.type != PointerEventType.Press) continue
                val at = e.changes.firstOrNull()?.position ?: continue
                val action: (() -> Unit)? = when {
                    e.buttons.isSecondaryPressed -> secondary?.let { { it(at) } }
                    e.buttons.isTertiaryPressed -> middle
                    e.buttons.isPrimaryPressed && e.keyboardModifiers.isCtrlPressed -> ctrlClick
                    else -> null
                }
                if (action != null) {
                    e.changes.forEach { it.consume() }
                    action()
                }
            }
        }
    }
}

/** A context menu where it was asked for ([at], in pixels inside the element): a zero-size anchor at that point. */
@Composable
fun ContextMenuAt(at: IntOffset, expanded: Boolean, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Box(Modifier.offset { at }) {
        DropdownMenu(expanded, onDismiss) { content() }
    }
}

// ----- Header and scaffold of the sections -----

/** Title, subtitle and actions of a section, like the desktop's ("Hosts · 7 hosts" and its buttons). */
@Composable
fun DesktopHeader(title: String, subtitle: String?, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(start = 24.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(
                    subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

/**
 * A main screen in the content area of the desktop layout: the header with
 * its actions, what goes under it ([header]) and the content, up to a
 * readable width.
 */
@Composable
fun DesktopScaffold(
    title: String,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    header: (@Composable () -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Column {
                DesktopHeader(title, subtitle, actions)
                header?.invoke()
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        },
        floatingActionButton = floatingActionButton,
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxHeight().widthIn(max = 1000.dp)) { content(padding) }
        }
    }
}

// ----- Sidebar -----

/** The sections of the sidebar, in the desktop's groups: Vault, Server and App. */
private enum class SideItem(val route: String, @StringRes val label: Int, val icon: ImageVector, val vault: Boolean = false) {
    HOSTS(Routes.HOSTS, R.string.section_hosts, VaultSection.HOSTS.icon, vault = true),
    KEYCHAIN(Routes.keys(), R.string.section_keychain, VaultSection.KEYCHAIN.icon, vault = true),
    SNIPPETS(Routes.SNIPPETS, R.string.section_snippets, VaultSection.SNIPPETS.icon, vault = true),
    TUNNELS(Routes.FORWARDS, R.string.section_tunnels, VaultSection.TUNNELS.icon, vault = true),
    KNOWN_HOSTS(Routes.KNOWN_HOSTS, R.string.section_known_hosts, VaultSection.KNOWN_HOSTS.icon, vault = true),
    AI(Routes.AI, R.string.section_ai, Icons.Outlined.AutoAwesome),
    SESSIONS(Routes.CONNECTIONS, R.string.sidebar_server_sessions, Icons.Outlined.CloudQueue),
    TEAMS(Routes.TEAMS, R.string.teams_title, Icons.Outlined.Groups),
    VAULTS(Routes.VAULTS, R.string.vaults_title, Icons.Outlined.Lock),
    SETTINGS(Routes.SETTINGS, R.string.section_settings, Icons.Outlined.Settings),
}

/** The sidebar section a screen belongs to (`null`: none, like the file browser). */
private fun sideItemOf(route: String?): SideItem? = when (route) {
    Routes.HOSTS, Routes.GROUP, Routes.HOST_EDIT, Routes.IMPORT -> SideItem.HOSTS
    Routes.KEYS -> SideItem.KEYCHAIN
    Routes.SNIPPETS -> SideItem.SNIPPETS
    Routes.FORWARDS -> SideItem.TUNNELS
    Routes.KNOWN_HOSTS -> SideItem.KNOWN_HOSTS
    Routes.AI, Routes.AI_TASK, Routes.AI_NEW -> SideItem.AI
    Routes.CONNECTIONS, Routes.JOIN -> SideItem.SESSIONS
    Routes.TEAMS -> SideItem.TEAMS
    Routes.VAULTS, Routes.VAULT -> SideItem.VAULTS
    Routes.SETTINGS, Routes.AI_KEYS, Routes.ACCOUNTS -> SideItem.SETTINGS
    else -> null
}

/**
 * The fixed sidebar of the Home tab: the app and its version, the account
 * switcher and the vault filter on top; Vault, Server and App sections;
 * the account and its sync at the bottom. Collapsed, only the icons.
 */
@Composable
fun DesktopSidebar(app: TermoakApp, nav: NavHostController, route: String?) {
    val collapsed by app.prefs.sidebarCollapsed.collectAsState()
    val approvals by app.accounts.pendingApprovals.collectAsState()
    val onServer by app.sessions.onServer.collectAsState()
    val width by animateDpAsState(if (collapsed) 72.dp else 248.dp, label = "sidebar")
    val current = sideItemOf(route)
    fun open(item: SideItem) {
        when {
            item.vault -> nav.goVault(item.route)
            route == item.route -> Unit
            // Again on the section it's in: back to its first screen.
            item == current && nav.popBackStack(item.route, inclusive = false) -> Unit
            else -> nav.goTab(item.route)
        }
    }
    Surface(Modifier.fillMaxHeight().width(width), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxSize()) {
            // ----- The app, and the button to collapse or expand the sidebar -----
            Row(
                Modifier.fillMaxWidth().padding(start = if (collapsed) 0.dp else 16.dp, top = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (collapsed) Arrangement.Center else Arrangement.Start,
            ) {
                if (collapsed) {
                    IconButton(onClick = { app.prefs.setSidebarCollapsed(false) }) {
                        Icon(Icons.Outlined.Menu, stringResource(R.string.sidebar_expand))
                    }
                } else {
                    Image(
                        painterResource(R.drawable.logo), null,
                        Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)),
                    )
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleSmall)
                        Text(
                            "v" + BuildConfig.VERSION_NAME, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { app.prefs.setSidebarCollapsed(true) }) {
                        Icon(
                            Icons.AutoMirrored.Outlined.MenuOpen, stringResource(R.string.sidebar_collapse),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (!collapsed) {
                // The account shown and its vaults (the attention banners go to the content).
                AccountSwitcher(app, nav, attention = false)
                VaultFilterMenu(app)
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 8.dp)) {
                SidebarGroup(stringResource(R.string.nav_vault), collapsed)
                for (item in listOf(SideItem.HOSTS, SideItem.KEYCHAIN, SideItem.SNIPPETS, SideItem.TUNNELS, SideItem.KNOWN_HOSTS)) {
                    SidebarItem(item, item == current, collapsed) { open(item) }
                }
                SidebarGroup(stringResource(R.string.sidebar_group_server), collapsed)
                SidebarItem(SideItem.AI, SideItem.AI == current, collapsed, badge = approvals) { open(SideItem.AI) }
                SidebarItem(SideItem.SESSIONS, SideItem.SESSIONS == current, collapsed, running = onServer.size) { open(SideItem.SESSIONS) }
                SidebarItem(SideItem.TEAMS, SideItem.TEAMS == current, collapsed) { open(SideItem.TEAMS) }
                SidebarItem(SideItem.VAULTS, SideItem.VAULTS == current, collapsed) { open(SideItem.VAULTS) }
                SidebarGroup(stringResource(R.string.sidebar_group_app), collapsed)
                SidebarItem(SideItem.SETTINGS, SideItem.SETTINGS == current, collapsed) { open(SideItem.SETTINGS) }
            }
            SidebarFooter(app, collapsed) { open(SideItem.SETTINGS) }
        }
    }
}

@Composable
private fun SidebarGroup(title: String, collapsed: Boolean) {
    if (collapsed) {
        HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant)
    } else {
        Text(
            title, Modifier.padding(start = 20.dp, top = 14.dp, bottom = 4.dp),
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * A section of the sidebar. [badge]: requests waiting (amber); [running]:
 * server sessions running (a green dot with how many).
 */
@Composable
private fun SidebarItem(item: SideItem, selected: Boolean, collapsed: Boolean, badge: Int = 0, running: Int = 0, onClick: () -> Unit) {
    val label = stringResource(item.label)
    val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 1.dp).clip(RoundedCornerShape(10.dp))
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .clickable(onClick = onClick).heightIn(min = 42.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (collapsed) Arrangement.Center else Arrangement.Start,
    ) {
        if (collapsed) {
            BadgedBox(badge = {
                when {
                    badge > 0 -> Badge(containerColor = Brand.Amber, contentColor = Color.Black) { Text("$badge") }
                    running > 0 -> Badge(containerColor = Brand.Green, contentColor = Color.White) { Text("$running") }
                }
            }) { Icon(item.icon, label, Modifier.size(20.dp), tint = tint) }
            return@Row
        }
        Icon(item.icon, null, Modifier.size(20.dp), tint = tint)
        Text(
            label, Modifier.weight(1f).padding(start = 14.dp), fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
        when {
            badge > 0 -> Badge(containerColor = Brand.Amber, contentColor = Color.Black) { Text("$badge") }
            running > 0 -> {
                val cd = pluralStringResource(R.plurals.sidebar_sessions_running, running, running)
                Row(Modifier.clearAndSetSemantics { contentDescription = cd }, verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(Brand.Green, 7.dp)
                    Text(
                        "$running", Modifier.padding(start = 5.dp), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** The account and its sync at the bottom of the sidebar (like the desktop's "No server · This device only"). */
@Composable
private fun SidebarFooter(app: TermoakApp, collapsed: Boolean, onClick: () -> Unit) {
    val loggedIn by app.accounts.loggedIn.collectAsState()
    val current by app.accounts.current.collectAsState()
    val syncing by app.accounts.syncing.collectAsState()
    val syncError by app.accounts.syncError.collectAsState()
    val signedIn = loggedIn == true
    val dot = when {
        signedIn && syncError == null -> Brand.Green
        signedIn -> Brand.Amber
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (collapsed) Arrangement.Center else Arrangement.Start,
    ) {
        StatusDot(dot, 8.dp)
        if (!collapsed) {
            Column(Modifier.padding(start = 10.dp)) {
                Text(
                    current?.takeIf { signedIn }?.email ?: stringResource(R.string.sidebar_no_server),
                    style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    when {
                        !signedIn -> stringResource(R.string.accounts_device_only)
                        syncing -> stringResource(R.string.common_syncing)
                        syncError != null -> stringResource(R.string.sidebar_not_synced)
                        else -> stringResource(R.string.sidebar_synced)
                    },
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                )
            }
        }
    }
}

// ----- Tab bar -----

/**
 * The tab bar of the desktop layout: "Home" and one tab per open terminal
 * (server sessions with a cloud, shared ones with people), each with its
 * state and a close button, and "+" for a quick connect. A long press (or
 * a mouse held down) drags a tab to another place; its menu (right click,
 * or a long press without moving) moves it too.
 */
@Composable
fun DesktopTabBar(app: TermoakApp, nav: NavHostController, route: String?) {
    val sessions by app.sessions.list.collectAsState()
    val activeId by app.sessions.active.collectAsState()
    val split by app.sessions.split.collectAsState()
    val onTerminal = route == Routes.TERMINAL
    // Dragging: which tab and how far from its place.
    var dragging by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val widths = remember { mutableStateMapOf<String, Int>() }
    val gap = with(LocalDensity.current) { 4.dp.toPx() }
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))) {
            Row(
                Modifier.fillMaxWidth().height(44.dp).horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TabChip(
                    selected = !onTerminal,
                    onClick = { if (onTerminal) nav.popBackStack() },
                ) {
                    Icon(Icons.Outlined.Dashboard, null, Modifier.size(16.dp))
                    Text(stringResource(R.string.desktop_home), Modifier.padding(start = 8.dp, end = 4.dp), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
                sessions.forEachIndexed { index, s ->
                    key(s.id) {
                        val drag = dragging == s.id
                        SessionTabChip(
                            s,
                            selected = onTerminal && s.id == activeId,
                            inSplit = split.on && s.id in split.panes,
                            canLeft = index > 0,
                            canRight = index < sessions.lastIndex,
                            modifier = Modifier.onSizeChanged { widths[s.id] = it.width }
                                .zIndex(if (drag) 1f else 0f)
                                .graphicsLayer { translationX = if (drag) dragOffset else 0f },
                            onClick = {
                                app.sessions.select(s.id)
                                nav.showTerminal()
                            },
                            onClose = { app.sessions.close(s.id) },
                            onMove = { step ->
                                val i = app.sessions.list.value.indexOf(s)
                                if (i >= 0) app.sessions.move(s.id, i + step)
                            },
                            onDragStart = { dragging = s.id; dragOffset = 0f },
                            onDrag = { dx ->
                                val list = app.sessions.list.value
                                val i = list.indexOf(s)
                                if (i >= 0) {
                                    val (to, left) = TabOrder.drag(i, dragOffset + dx, list.map { widths[it.id] ?: 0 }, gap)
                                    if (to != i) app.sessions.move(s.id, to)
                                    dragOffset = left
                                }
                            },
                            onDragEnd = { dragging = null; dragOffset = 0f },
                        )
                    }
                }
                Box(
                    Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).clickable { DesktopUi.quickConnect.value = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Add, stringResource(R.string.tabs_quick_connect), Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun TabChip(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier.height(32.dp).widthIn(max = 240.dp).clip(RoundedCornerShape(8.dp))
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .clickable(onClick = onClick).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun SessionTabChip(
    s: TermSession,
    selected: Boolean,
    inSplit: Boolean,
    canLeft: Boolean,
    canRight: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
    onClose: () -> Unit,
    onMove: (Int) -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    val state by s.state.collectAsState()
    val title by s.title.collectAsState()
    val live by s.live.collectAsState()
    val haptics = LocalHapticFeedback.current
    var menu by remember { mutableStateOf(false) }
    var menuAt by remember { mutableStateOf(IntOffset.Zero) }
    val drag by rememberUpdatedState(onDrag)
    val start by rememberUpdatedState(onDragStart)
    val end by rememberUpdatedState(onDragEnd)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Box(modifier) {
        Row(
            Modifier.height(32.dp).widthIn(max = 240.dp).clip(RoundedCornerShape(8.dp))
                .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                .mouseActions(
                    onSecondary = { at -> menuAt = IntOffset(at.x.roundToInt(), at.y.roundToInt()); menu = true },
                    onMiddle = onClose,
                )
                // A long press drags the tab (held without moving: its menu). Seen before the click,
                // which then doesn't happen.
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(pass = PointerEventPass.Initial)
                        // Released or moved (scrolling the bar) before the long press: a tap, or not ours.
                        val early = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                            do {
                                val c = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                            } while (c.pressed && !c.isConsumed && (c.position - down.position).getDistance() <= viewConfiguration.touchSlop)
                            true
                        }
                        if (early != null) return@awaitEachGesture
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        menuAt = IntOffset(down.position.x.roundToInt(), down.position.y.roundToInt())
                        start()
                        var moved = 0f
                        while (true) {
                            val c = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                            val dx = c.position.x - c.previousPosition.x
                            c.consume()
                            if (!c.pressed) break
                            if (dx != 0f) {
                                moved += abs(dx)
                                drag(dx)
                            }
                        }
                        end()
                        if (moved < viewConfiguration.touchSlop) menu = true
                    }
                }
                .clickable(onClick = onClick).padding(start = 10.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val shared = s is ServerTerminal && !live.isOwner
            val icon = when {
                inSplit -> Icons.Outlined.GridView
                shared -> Icons.Outlined.Groups
                s.persistent -> Icons.Outlined.CloudQueue
                else -> Icons.Outlined.Terminal
            }
            Icon(icon, null, Modifier.size(15.dp), tint = muted)
            if (state != TermState.Asleep) {
                Spacer(Modifier.width(6.dp))
                StatusDot(stateColor(state), 7.dp)
            }
            Text(
                title ?: s.label, Modifier.padding(start = 6.dp).weight(1f, fill = false),
                color = if (state == TermState.Asleep) muted else MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Outlined.Close, stringResource(R.string.common_close), Modifier.size(14.dp), tint = muted)
            }
        }
        ContextMenuAt(menuAt, menu, { menu = false }) {
            DropdownMenuItem(
                { Text(stringResource(R.string.tabs_move_left)) }, { menu = false; onMove(-1) }, enabled = canLeft,
                leadingIcon = { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, null) },
            )
            DropdownMenuItem(
                { Text(stringResource(R.string.tabs_move_right)) }, { menu = false; onMove(1) }, enabled = canRight,
                leadingIcon = { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null) },
            )
            HorizontalDivider()
            DropdownMenuItem(
                { Text(stringResource(R.string.common_close)) }, { menu = false; onClose() },
                leadingIcon = { Icon(Icons.Outlined.Close, null) },
            )
        }
    }
}

// ----- Quick connect -----

/**
 * The "+" of the tab bar (and Ctrl+Shift+T): find a host and open it in a
 * new tab, or type an address (`user@host:port`, `telnet://host:23`) to
 * connect to it (the desktop's host picker).
 */
@Composable
fun QuickConnectDialog(app: TermoakApp, onDismiss: () -> Unit, onConnect: (SshHost) -> Unit) {
    val resources = LocalResources.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val hosts = remember {
        runCatching { app.core.listHosts(app.accounts.filter()) }.getOrDefault(emptyList())
            .sortedWith(compareByDescending<SshHost> { it.favorite }.thenBy { it.label.lowercase() })
    }
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val q = query.trim().lowercase()
    val shown = if (q.isEmpty()) hosts else hosts.filter { h ->
        listOf(h.label, h.address, h.settings.username ?: "", h.tags.joinToString(" ")).any { it.lowercase().contains(q) }
    }
    // An address typed when no saved host matches the search.
    val target = if (shown.isEmpty()) QuickTarget.parse(query) else null
    fun connectTo(t: QuickTarget) {
        try {
            onConnect(quickConnectHost(app, hosts, t))
        } catch (e: TermoakException) {
            scope.launch { snackbar.showSnackbar(e.userMessage(resources, R.string.error_save_failed)) }
        }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            Modifier.widthIn(max = 560.dp).fillMaxWidth().heightIn(max = 560.dp),
            shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(Modifier.padding(vertical = 12.dp)) {
                Text(
                    stringResource(R.string.tabs_quick_connect), Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.titleMedium,
                )
                OutlinedTextField(
                    query, { query = it },
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).focusRequester(focus),
                    placeholder = { Text(stringResource(R.string.quick_connect_placeholder)) },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go, keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
                    keyboardActions = KeyboardActions(onGo = {
                        if (target != null) connectTo(target) else shown.firstOrNull()?.let(onConnect)
                    }),
                )
                if (target != null) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 8.dp).clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer).clickable { connectTo(target) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Bolt, null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(
                                stringResource(R.string.quick_connect_to, target.display()), style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                stringResource(if (target.telnet) R.string.quick_connect_detail_telnet else R.string.quick_connect_detail),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else if (shown.isEmpty()) {
                    Text(
                        if (hosts.isEmpty()) stringResource(R.string.hosts_empty_title) else stringResource(R.string.hosts_no_match, query.trim()),
                        Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LazyColumn {
                    items(shown, key = { it.uid }) { h ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onConnect(h) }.padding(horizontal = 20.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            HostTile(h, size = 34.dp, twoInitials = true)
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(h.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    hostAddress(h), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (h.isTelnet) TelnetBadge(Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        }
    }
}

/**
 * The host for an address typed in quick connect: the saved one with that
 * protocol, address, user and port, or a new one saved first where new
 * items go (so its password, fingerprint and history have a place).
 */
fun quickConnectHost(app: TermoakApp, hosts: List<SshHost>, t: QuickTarget): SshHost {
    hosts.firstOrNull { h ->
        h.address.equals(t.host, ignoreCase = true) &&
            HostProtocol.isTelnet(h.protocol) == t.telnet &&
            (t.user == null || h.settings.username == t.user) &&
            (h.settings.port ?: HostProtocol.defaultPort(h.protocol)) == t.effectivePort
    }?.let { return it }
    val place = app.accounts.defaultPlace()
    val host = SshHost(
        label = t.display(),
        address = t.host,
        settings = HostSettings(
            username = t.user,
            // Telnet hosts keep their port written out (see HostProtocol.portAfterSwitch).
            port = t.port ?: if (t.telnet) HostProtocol.defaultPort(t.protocol) else null,
        ),
        protocol = t.protocol,
        accountId = place.account,
        vaultId = place.vault,
        syncMode = if (place.device && app.accounts.list.value.isNotEmpty()) SyncMode.DEVICE_ONLY else null,
    )
    val saved = app.core.saveHost(host, SecretChange.Keep)
    app.accounts.sync()
    return saved
}

/** "user@address" and the port when it isn't the protocol's default (the desktop's host cards). */
fun hostAddress(h: SshHost): String =
    (h.settings.username?.takeIf { it.isNotBlank() }?.let { "$it@" } ?: "") + h.address +
        (h.settings.port?.takeIf { it != HostProtocol.defaultPort(h.protocol) }?.let { ":$it" } ?: "")

/** Opens [host] in a new terminal tab (from the phone, or through the server for Strict Use-only hosts). */
fun quickConnect(app: TermoakApp, nav: NavHostController, host: SshHost): TermSession {
    val s = connectHost(app, host)
    nav.showTerminal()
    return s
}
