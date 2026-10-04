package com.termoak.app.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Badge
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.termoak.app.R
import com.termoak.app.TermoakApp
import com.termoak.app.term.TermSession
import com.termoak.app.term.TermState
import kotlinx.coroutines.launch

object Routes {
    const val WELCOME = "welcome"
    const val HOSTS = "hosts"
    const val GROUP = "group/{id}"
    const val SESSIONS = "sessions"
    const val AI = "ai"
    const val SETTINGS = "settings"
    const val AI_KEYS = "settings/ai"
    const val TERMINAL = "terminal"
    const val HOST_EDIT = "host/{id}"
    const val AI_TASK = "ai/{id}"
    const val AI_NEW = "ai-new"
    const val KEYS = "keys"
    const val SNIPPETS = "snippets"
    const val KNOWN_HOSTS = "known-hosts"
    const val LOGIN = "login"

    fun hostEdit(id: String?) = "host/${id ?: "new"}"
    fun group(id: String) = "group/$id"
    fun aiTask(id: String) = "ai/$id"
}

/** Opens the side menu from each section's top bar. */
val LocalOpenDrawer = staticCompositionLocalOf<() -> Unit> { {} }

private data class Section(val route: String, @StringRes val label: Int, val icon: ImageVector)

private val VaultSections = listOf(
    Section(Routes.HOSTS, R.string.section_hosts, Icons.Outlined.Dns),
    Section(Routes.KEYS, R.string.section_keychain, Icons.Outlined.Key),
    Section(Routes.SNIPPETS, R.string.section_snippets, Icons.Outlined.Code),
    Section(Routes.KNOWN_HOSTS, R.string.section_known_hosts, Icons.Outlined.VerifiedUser),
)
private val ServerSections = listOf(
    Section(Routes.SESSIONS, R.string.section_sessions, Icons.Outlined.History),
    Section(Routes.AI, R.string.section_ai, Icons.Outlined.AutoAwesome),
)
private val TopLevel = (VaultSections + ServerSections).map { it.route } + Routes.SETTINGS

@Composable
fun AppRoot(app: TermoakApp) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val loggedIn by app.account.loggedIn.collectAsState()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route

    if (loggedIn == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val start = remember { if (loggedIn == true || app.prefs.skippedLogin) Routes.HOSTS else Routes.WELCOME }

    CompositionLocalProvider(
        LocalSnackbar provides snackbar,
        LocalOpenDrawer provides { scope.launch { drawer.open() } },
    ) {
        ModalNavigationDrawer(
            drawerState = drawer,
            gesturesEnabled = route in TopLevel,
            drawerContent = { Drawer(app, route, drawer) { r -> nav.goTab(r) } },
        ) {
            Scaffold(
                snackbarHost = { SnackbarHost(snackbar) },
                // The terminal handles its own insets; the rest leaves room for the gesture bar.
                contentWindowInsets = if (route == Routes.TERMINAL) WindowInsets(0, 0, 0, 0) else WindowInsets.navigationBars,
                bottomBar = {
                    if (route != Routes.TERMINAL && route != Routes.WELCOME) {
                        TerminalsBar(app) { nav.navigate(Routes.TERMINAL) { launchSingleTop = true } }
                    }
                },
            ) { padding ->
                NavHost(nav, startDestination = start, modifier = Modifier.padding(padding).consumeWindowInsets(padding)) {
                    composable(Routes.WELCOME) {
                        LoginScreen(app, welcome = true, onDone = { nav.goTab(Routes.HOSTS, clear = true) }, onBack = null)
                    }
                    composable(Routes.LOGIN) {
                        LoginScreen(app, welcome = false, onDone = { nav.popBackStack() }, onBack = { nav.popBackStack() })
                    }
                    composable(Routes.HOSTS) { HostsScreen(app, nav, groupId = null) }
                    composable(Routes.GROUP) { e -> HostsScreen(app, nav, groupId = e.arguments?.getString("id")) }
                    composable(Routes.SESSIONS) { SessionsScreen(app, nav) }
                    composable(Routes.AI) { AiScreen(app, nav) }
                    composable(Routes.SETTINGS) { SettingsScreen(app, nav) }
                    composable(Routes.AI_KEYS) { AiKeysScreen(app, nav) }
                    composable(Routes.TERMINAL) { TerminalScreen(app, nav) }
                    composable(Routes.HOST_EDIT) { e ->
                        HostEditor(app, e.arguments?.getString("id")?.takeIf { it != "new" }) { nav.popBackStack() }
                    }
                    composable(Routes.AI_NEW) { NewAiTaskScreen(app, nav) }
                    composable(Routes.AI_TASK) { e -> AiTaskScreen(app, nav, e.arguments?.getString("id") ?: "") }
                    composable(Routes.KEYS) { KeysScreen(app) }
                    composable(Routes.SNIPPETS) { SnippetsScreen(app) }
                    composable(Routes.KNOWN_HOSTS) { KnownHostsScreen(app) }
                }
            }
        }
    }
    LaunchedEffect(loggedIn) {
        if (loggedIn == false && route == Routes.AI) nav.goTab(Routes.HOSTS)
        if (loggedIn == false && route == Routes.AI_KEYS) nav.popBackStack()
    }
    // Server sessions already open: on startup or sign-in, as sleeping tabs.
    LaunchedEffect(loggedIn) {
        if (loggedIn == true) app.sessions.loadServerSessions() else app.sessions.forgetServerSessions()
    }
    LaunchedEffect(Unit) {
        app.account.changes.collect {
            if ((it == "session" || it == "lagged") && app.account.loggedIn.value == true) app.sessions.loadServerSessions()
        }
    }
}

/** The vault menu: the account on top and the sections. */
@Composable
private fun Drawer(app: TermoakApp, route: String?, drawer: DrawerState, go: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val loggedIn by app.account.loggedIn.collectAsState()
    val user by app.account.user.collectAsState()
    val server by app.account.serverUrl.collectAsState()
    val online by app.account.online.collectAsState()
    val approvals by app.account.pendingApprovals.collectAsState()
    val open by app.sessions.list.collectAsState()

    fun select(r: String) {
        scope.launch { drawer.close() }
        if (r != route) go(r)
    }

    ModalDrawerSheet(Modifier.widthIn(max = 320.dp), drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            Row(Modifier.padding(start = 20.dp, top = 24.dp, end = 20.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.logo), null, Modifier.size(36.dp).clip(RoundedCornerShape(9.dp)))
                Column(Modifier.padding(start = 12.dp)) {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (loggedIn == true) server?.removePrefix("https://") ?: "" else stringResource(R.string.drawer_device_only),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            // Account
            Surface(
                Modifier.padding(horizontal = 12.dp).fillMaxWidth().clip(MaterialTheme.shapes.medium)
                    .clickable { select(if (loggedIn == true) Routes.SETTINGS else Routes.LOGIN) },
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    HostTile(user ?: "?", null, size = 36.dp)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(
                            if (loggedIn == true) user ?: stringResource(R.string.drawer_signed_in) else stringResource(R.string.common_sign_in),
                            style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            stringResource(
                                when {
                                    loggedIn != true -> R.string.drawer_sync_prompt
                                    online -> R.string.drawer_synced_live
                                    else -> R.string.drawer_synced
                                },
                            ),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (loggedIn == true) StatusDot(if (online) Brand.Green else Brand.Amber)
                }
            }
            DrawerLabel(stringResource(R.string.drawer_group_vault))
            VaultSections.forEach { s ->
                DrawerEntry(stringResource(s.label), s.icon, route == s.route || (s.route == Routes.HOSTS && route == Routes.GROUP)) {
                    select(s.route)
                }
            }
            DrawerLabel(stringResource(R.string.drawer_group_server))
            ServerSections.forEach { s ->
                DrawerEntry(stringResource(s.label), s.icon, route == s.route, badge = if (s.route == Routes.AI) approvals else 0) {
                    select(s.route)
                }
            }
            if (open.isNotEmpty()) {
                DrawerLabel(stringResource(R.string.drawer_group_terminals))
                DrawerEntry(pluralStringResource(R.plurals.drawer_terminals_open, open.size, open.size), Icons.Outlined.Terminal, false) {
                    scope.launch { drawer.close() }
                    go(Routes.TERMINAL)
                }
            }
            Spacer(Modifier.weight(1f))
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            DrawerEntry(stringResource(R.string.section_settings), Icons.Outlined.Settings, route == Routes.SETTINGS) {
                select(Routes.SETTINGS)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun DrawerLabel(text: String) {
    Text(
        text.uppercase(LocalConfiguration.current.locales[0]), Modifier.padding(start = 28.dp, top = 20.dp, bottom = 6.dp),
        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 1.sp,
    )
}

@Composable
private fun DrawerEntry(label: String, icon: ImageVector, selected: Boolean, badge: Int = 0, onClick: () -> Unit) {
    NavigationDrawerItem(
        label = { Text(label) },
        icon = { Icon(icon, null) },
        selected = selected,
        onClick = onClick,
        badge = { if (badge > 0) Badge { Text("$badge") } },
        modifier = Modifier.padding(horizontal = 12.dp).height(48.dp),
        colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent),
    )
}

/** Open terminals, always at hand at the bottom (like Termius' connections bar). */
@Composable
private fun TerminalsBar(app: TermoakApp, onOpen: () -> Unit) {
    val sessions by app.sessions.list.collectAsState()
    if (sessions.isEmpty()) return
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 3.dp) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().clickable(onClick = onOpen)
                .padding(start = 16.dp, end = 8.dp).height(52.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Terminal, null, tint = MaterialTheme.colorScheme.primary)
            Row(
                Modifier.weight(1f).padding(start = 12.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                sessions.forEach { s -> TerminalChip(s) { app.sessions.select(s.id); onOpen() } }
            }
        }
    }
}

@Composable
private fun TerminalChip(s: TermSession, onClick: () -> Unit) {
    val state by s.state.collectAsState()
    val title by s.title.collectAsState()
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state == TermState.Asleep) {
            Icon(Icons.Outlined.CloudQueue, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            StatusDot(
                when (state) {
                    TermState.Running -> Brand.Green
                    is TermState.Connecting -> Brand.Amber
                    is TermState.Closed, TermState.Asleep -> Brand.Red
                },
                7.dp,
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            title ?: s.label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 140.dp),
        )
    }
}

fun NavHostController.goTab(route: String, clear: Boolean = false) {
    navigate(route) {
        if (clear) {
            popUpTo(graph.id) { inclusive = true }
        } else {
            popUpTo(Routes.HOSTS) { saveState = true }
            restoreState = true
        }
        launchSingleTop = true
    }
}

/** Screen with a top bar in the app style. Without `navigationIcon`, it opens the menu. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenScaffold(
    title: String,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    subtitle: String? = null,
    // Screens without the bottom bar: leave room for the system gesture bar.
    avoidNavigationBar: Boolean = false,
    content: @Composable (PaddingValues) -> Unit,
) {
    val openDrawer = LocalOpenDrawer.current
    Scaffold(
        contentWindowInsets = if (avoidNavigationBar) WindowInsets.navigationBars else WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (subtitle != null) {
                            Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                navigationIcon = navigationIcon ?: {
                    IconButton(onClick = openDrawer) { Icon(Icons.Outlined.Menu, stringResource(R.string.common_menu)) }
                },
                actions = actions,
            )
        },
        floatingActionButton = floatingActionButton,
        content = content,
    )
}
