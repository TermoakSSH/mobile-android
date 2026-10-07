package com.termoak.app.ui

import android.net.Uri
import androidx.annotation.StringRes
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.termoak.app.MainActivity
import com.termoak.app.R
import com.termoak.app.TermoakApp
import com.termoak.app.data.JoinLinkRef
import com.termoak.app.term.ShareNotice
import com.termoak.app.term.TermSession
import com.termoak.app.term.TermState
import com.termoak.ffi.AccountStatus
import kotlinx.coroutines.launch

object Routes {
    const val WELCOME = "welcome"
    const val HOSTS = "hosts"
    const val GROUP = "group/{id}?account={account}"
    const val CONNECTIONS = "connections"
    const val AI = "ai"
    const val SETTINGS = "settings"
    const val AI_KEYS = "settings/ai"
    const val TERMINAL = "terminal"
    const val HOST_EDIT = "host/{id}?account={account}"
    const val AI_TASK = "ai/{id}?account={account}"
    const val AI_NEW = "ai-new"
    /** The keychain; `action` opens the generate (`generate`) or import (`import`) dialog. */
    const val KEYS = "keys?action={action}"
    const val SNIPPETS = "snippets"
    const val KNOWN_HOSTS = "known-hosts"
    const val FORWARDS = "forwards"
    const val IMPORT = "import"
    /** Add account (or sign in again): `mode` [LoginMode], prefilled `server` and `email`. */
    const val LOGIN = "login?mode={mode}&server={server}&email={email}"
    const val VERIFY_EMAIL = "verify-email"
    const val ACCOUNTS = "accounts"
    const val VAULTS = "vaults"
    const val VAULT = "vault/{account}/{id}"
    /** Joining a shared session with an invitation link. */
    const val JOIN = "join?server={server}&token={token}"

    fun hostEdit(id: String?, account: String? = null) = "host/${id ?: "new"}" + (account?.let { "?account=$it" } ?: "")
    fun group(id: String, account: String? = null) = "group/$id" + (account?.let { "?account=$it" } ?: "")
    fun aiTask(id: String, account: String? = null) = "ai/$id" + (account?.let { "?account=$it" } ?: "")
    fun vault(account: String, id: String) = "vault/$account/$id"
    fun login(mode: String? = null, server: String? = null, email: String? = null): String =
        listOfNotNull(mode?.let { "mode=$it" }, server?.let { "server=${Uri.encode(it)}" }, email?.let { "email=${Uri.encode(it)}" })
            .joinToString("&").let { if (it.isEmpty()) "login" else "login?$it" }
    fun join(link: JoinLinkRef) = "join?server=${Uri.encode(link.server)}&token=${Uri.encode(link.token)}"
    fun keys(action: String? = null) = if (action == null) "keys" else "keys?action=$action"
}

/** The app's main destinations: bottom bar on phones, navigation rail on tablets. */
private enum class Tab(val route: String, @StringRes val label: Int, val icon: ImageVector, val selectedIcon: ImageVector) {
    VAULT(Routes.HOSTS, R.string.nav_vault, Icons.Outlined.Dns, Icons.Filled.Dns),
    CONNECTIONS(Routes.CONNECTIONS, R.string.nav_connections, Icons.Outlined.Terminal, Icons.Filled.Terminal),
    AI(Routes.AI, R.string.section_ai, Icons.Outlined.AutoAwesome, Icons.Filled.AutoAwesome),
    SETTINGS(Routes.SETTINGS, R.string.section_settings, Icons.Outlined.Settings, Icons.Filled.Settings),
}

/** The Vault sections (hosts, keychain, tunnels...) and the folders of hosts. */
private val VaultRoutes = setOf(Routes.HOSTS, Routes.GROUP, Routes.KEYS, Routes.FORWARDS, Routes.SNIPPETS, Routes.KNOWN_HOSTS)

/** Screens with the bottom bar (the rest are details, full screen). */
private val TopLevel = VaultRoutes + setOf(Routes.CONNECTIONS, Routes.AI, Routes.SETTINGS)

/** Screens without any app navigation (not even the rail on tablets). */
private val Immersive = setOf(Routes.TERMINAL, Routes.WELCOME, Routes.VERIFY_EMAIL, Routes.LOGIN)

/** Details that slide in from the side, over the tab they belong to. */
private val Details = setOf(
    Routes.GROUP, Routes.HOST_EDIT, Routes.IMPORT, Routes.AI_TASK, Routes.AI_NEW, Routes.AI_KEYS, Routes.LOGIN, Routes.JOIN,
    Routes.ACCOUNTS, Routes.VAULTS, Routes.VAULT,
)

private fun tabOf(route: String?): Tab? = when (route) {
    in VaultRoutes, Routes.HOST_EDIT, Routes.IMPORT -> Tab.VAULT
    Routes.CONNECTIONS -> Tab.CONNECTIONS
    Routes.AI, Routes.AI_TASK, Routes.AI_NEW -> Tab.AI
    Routes.SETTINGS, Routes.AI_KEYS, Routes.ACCOUNTS, Routes.VAULTS, Routes.VAULT -> Tab.SETTINGS
    else -> null
}

// ----- Transitions: fade through between tabs, slide for the details -----

private fun fadeThroughIn(): EnterTransition =
    fadeIn(tween(210, delayMillis = 70)) + scaleIn(tween(210, delayMillis = 70), initialScale = 0.97f)

private fun fadeThroughOut(): ExitTransition = fadeOut(tween(90))

private fun AnimatedContentTransitionScope<NavBackStackEntry>.slideIn(): EnterTransition =
    slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(260)) + fadeIn(tween(260))

private fun AnimatedContentTransitionScope<NavBackStackEntry>.slideOut(): ExitTransition =
    slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(220)) + fadeOut(tween(220))

@Composable
fun AppRoot(app: TermoakApp) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val loggedIn by app.accounts.loggedIn.collectAsState()
    val accountList by app.accounts.list.collectAsState()
    val verification by app.accounts.verification.collectAsState()
    val uploadOffer by app.accounts.uploadOffer.collectAsState()
    val layoutNotice by app.accounts.layoutNotice.collectAsState()
    val approvals by app.accounts.pendingApprovals.collectAsState()
    val open by app.sessions.list.collectAsState()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route

    if (loggedIn == null) {
        // On the theme's background (the window's is the dark one of the launch): the status bar's icons go by the theme.
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    val start = remember { if (loggedIn == true || accountList.isNotEmpty() || app.prefs.skippedLogin) Routes.HOSTS else Routes.WELCOME }
    val current = tabOf(route)
    fun select(tab: Tab) {
        if (tab == current) {
            // Again on the tab it's in: back to its first screen.
            if (tab == Tab.VAULT) nav.goVault(Routes.HOSTS) else nav.popBackStack(tab.route, inclusive = false)
        } else {
            nav.goTab(tab.route)
        }
    }
    fun badge(tab: Tab): Int = when (tab) {
        Tab.CONNECTIONS -> open.size
        Tab.AI -> approvals
        else -> 0
    }

    CompositionLocalProvider(LocalSnackbar provides snackbar) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= 600.dp
            val showBar = !wide && route in TopLevel
            val barState = remember { MutableTransitionState(showBar) }
            barState.targetState = showBar
            val showRail = wide && route != null && route !in Immersive
            // Open terminals at hand (except on Connections, which lists them).
            val showTerminals = route != Routes.TERMINAL && route != Routes.WELCOME && route != Routes.VERIFY_EMAIL &&
                route != Routes.CONNECTIONS
            Scaffold(
                snackbarHost = { SnackbarHost(snackbar) },
                // The terminal handles its own insets; the rest leaves room for the gesture bar.
                contentWindowInsets = if (route == Routes.TERMINAL) WindowInsets(0, 0, 0, 0) else WindowInsets.navigationBars,
                bottomBar = bar@{
                    // Nothing at all when there's nothing to show: the content then keeps clear of the gesture bar.
                    if (!barState.currentState && !barState.targetState && !(showTerminals && open.isNotEmpty())) return@bar
                    Column {
                        if (showTerminals) {
                            TerminalsBar(app, aboveBar = showBar) { nav.navigate(Routes.TERMINAL) { launchSingleTop = true } }
                        }
                        AnimatedVisibility(
                            barState,
                            enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                            exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
                        ) {
                            NavigationBar {
                                Tab.entries.forEach { tab ->
                                    NavigationBarItem(
                                        selected = tab == current,
                                        onClick = { select(tab) },
                                        icon = { TabIcon(tab, tab == current, badge(tab)) },
                                        label = { Text(stringResource(tab.label), maxLines = 1) },
                                    )
                                }
                            }
                        }
                    }
                },
            ) { padding ->
                Row(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
                    AnimatedVisibility(showRail, enter = expandHorizontally() + fadeIn(), exit = shrinkHorizontally() + fadeOut()) {
                        Row {
                            NavigationRail(
                                Modifier.fillMaxHeight(),
                                header = {
                                    Image(
                                        painterResource(R.drawable.logo), stringResource(R.string.app_name),
                                        Modifier.padding(vertical = 12.dp).size(36.dp).clip(RoundedCornerShape(9.dp)),
                                    )
                                },
                            ) {
                                Spacer(Modifier.height(8.dp))
                                Tab.entries.forEach { tab ->
                                    NavigationRailItem(
                                        selected = tab == current,
                                        onClick = { select(tab) },
                                        icon = { TabIcon(tab, tab == current, badge(tab)) },
                                        label = { Text(stringResource(tab.label), maxLines = 1) },
                                        modifier = Modifier.padding(vertical = 4.dp),
                                    )
                                }
                            }
                            // Thin line between the rail and the content.
                            Box(Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                        }
                    }
                    NavHost(
                        nav, startDestination = start, modifier = Modifier.weight(1f),
                        enterTransition = { if (targetState.destination.route in Details) slideIn() else fadeThroughIn() },
                        exitTransition = { fadeThroughOut() },
                        popEnterTransition = { fadeThroughIn() },
                        popExitTransition = { if (initialState.destination.route in Details) slideOut() else fadeThroughOut() },
                    ) {
                        composable(Routes.WELCOME) {
                            LoginScreen(app, welcome = true, onDone = { nav.goTab(Routes.HOSTS, clear = true) }, onBack = null)
                        }
                        composable(
                            Routes.LOGIN,
                            arguments = listOf(
                                navArgument("mode") { type = NavType.StringType; nullable = true; defaultValue = null },
                                navArgument("server") { type = NavType.StringType; nullable = true; defaultValue = null },
                                navArgument("email") { type = NavType.StringType; nullable = true; defaultValue = null },
                            ),
                        ) { e ->
                            LoginScreen(
                                app, welcome = false, onDone = { nav.popBackStack() }, onBack = { nav.popBackStack() },
                                mode = e.arguments?.getString("mode"), prefillServer = e.arguments?.getString("server"),
                                prefillEmail = e.arguments?.getString("email"),
                            )
                        }
                        composable(Routes.VERIFY_EMAIL) {
                            VerifyEmailScreen(
                                app,
                                onDone = { nav.goTab(Routes.HOSTS, clear = true) },
                                onDifferentEmail = {
                                    if (app.accounts.list.value.isEmpty()) nav.goTab(Routes.WELCOME, clear = true)
                                    else nav.goTab(Routes.HOSTS, clear = true)
                                },
                            )
                        }
                        composable(Routes.ACCOUNTS) { ManageAccountsScreen(app, nav) }
                        composable(Routes.VAULTS) { VaultsScreen(app, nav) }
                        composable(Routes.VAULT) { e ->
                            VaultScreen(app, nav, e.arguments?.getString("account").orEmpty(), e.arguments?.getString("id").orEmpty())
                        }
                        composable(Routes.HOSTS) { HostsScreen(app, nav, groupId = null) }
                        composable(
                            Routes.GROUP,
                            arguments = listOf(navArgument("account") { type = NavType.StringType; nullable = true; defaultValue = null }),
                        ) { e -> HostsScreen(app, nav, groupId = e.arguments?.getString("id"), groupAccount = e.arguments?.getString("account")) }
                        composable(Routes.CONNECTIONS) { ConnectionsScreen(app, nav) }
                        composable(Routes.AI) { AiScreen(app, nav) }
                        composable(Routes.SETTINGS) { SettingsScreen(app, nav) }
                        composable(Routes.AI_KEYS) { AiKeysScreen(app, nav) }
                        // The terminal keeps a plain fade, as before.
                        composable(
                            Routes.TERMINAL,
                            enterTransition = { fadeIn(tween(250)) },
                            exitTransition = { fadeOut(tween(250)) },
                            popEnterTransition = { fadeIn(tween(250)) },
                            popExitTransition = { fadeOut(tween(250)) },
                        ) { TerminalScreen(app, nav) }
                        composable(
                            Routes.HOST_EDIT,
                            arguments = listOf(navArgument("account") { type = NavType.StringType; nullable = true; defaultValue = null }),
                        ) { e ->
                            val activity = LocalActivity.current
                            HostEditor(
                                app, e.arguments?.getString("id")?.takeIf { it != "new" }, e.arguments?.getString("account"),
                                onClose = { nav.popBackStack() },
                                onConnect = { host ->
                                    (activity as? MainActivity)?.askNotificationPermission()
                                    nav.popBackStack()
                                    connectHost(app, host)
                                    nav.navigate(Routes.TERMINAL) { launchSingleTop = true }
                                },
                            )
                        }
                        composable(Routes.AI_NEW) { NewAiTaskScreen(app, nav) }
                        composable(
                            Routes.AI_TASK,
                            arguments = listOf(navArgument("account") { type = NavType.StringType; nullable = true; defaultValue = null }),
                        ) { e -> AiTaskScreen(app, nav, e.arguments?.getString("id") ?: "", e.arguments?.getString("account")) }
                        composable(
                            Routes.KEYS,
                            arguments = listOf(navArgument("action") { type = NavType.StringType; nullable = true; defaultValue = null }),
                        ) { e -> KeysScreen(app, nav, e.arguments?.getString("action")) }
                        composable(Routes.SNIPPETS) { SnippetsScreen(app, nav) }
                        composable(Routes.KNOWN_HOSTS) { KnownHostsScreen(app, nav) }
                        composable(Routes.FORWARDS) { ForwardsScreen(app, nav) }
                        composable(Routes.IMPORT) { ImportScreen(app) { nav.popBackStack() } }
                        composable(
                            Routes.JOIN,
                            arguments = listOf(
                                navArgument("server") { type = NavType.StringType; defaultValue = "" },
                                navArgument("token") { type = NavType.StringType; defaultValue = "" },
                            ),
                        ) { e ->
                            JoinScreen(app, nav, e.arguments?.getString("server").orEmpty(), e.arguments?.getString("token").orEmpty())
                        }
                    }
                }
            }
        }
        // A snippet sent to several terminals: how it goes (from any screen).
        SnippetRunSummary(app, nav)
        // After the first account: upload This-device items; after the update: the new layout.
        uploadOffer?.let { UploadDeviceItemsDialog(app, it) }
        if (layoutNotice && uploadOffer == null) LayoutNoticeDialog(app)
    }
    LaunchedEffect(loggedIn) {
        if (loggedIn == false && route == Routes.AI) nav.goTab(Routes.HOSTS)
        if (loggedIn == false && route == Routes.AI_KEYS) nav.popBackStack()
    }
    // The account must verify its email (after signing in, or the server said
    // so): open the code screen, except over a terminal in use.
    LaunchedEffect(verification != null) {
        val now = nav.currentDestination?.route
        if (verification == null || now == Routes.VERIFY_EMAIL || now == Routes.TERMINAL) return@LaunchedEffect
        nav.navigate(Routes.VERIFY_EMAIL) {
            // The sign-in form is done: back from the code screen doesn't return to it.
            if (now == Routes.LOGIN || now == Routes.WELCOME) popUpTo(now) { inclusive = true }
            launchSingleTop = true
        }
    }
    // A new version of the app, once the account is known (at most once a day):
    // from the server signed in to, or from termoak.com.
    LaunchedEffect(loggedIn != null) {
        if (loggedIn == null) return@LaunchedEffect
        app.updates.checkIfDue(app.accounts.current.value?.serverUrl)
    }
    // Server sessions already open: on startup or sign-in (of each account), as sleeping tabs.
    val activeIds = accountList.filter { it.status == AccountStatus.ACTIVE }.map { it.id }.toSet()
    LaunchedEffect(activeIds) {
        app.sessions.forgetServerSessions()
        if (activeIds.isNotEmpty()) app.sessions.loadServerSessions()
    }
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    // Notices of the syncs: vaults shared with you or lost, changes discarded.
    LaunchedEffect(Unit) {
        app.accounts.notices.collect { scope.launch { snackbar.showSnackbar(it.resolve(resources), withDismissAction = true, duration = SnackbarDuration.Long) } }
    }
    // An invitation link opened from outside the app.
    val link by app.pendingLink.collectAsState()
    LaunchedEffect(link) {
        val l = link ?: return@LaunchedEffect
        app.pendingLink.value = null
        nav.navigate(Routes.join(l)) { launchSingleTop = true }
    }
    // Sharing notices: snackbars (unless that terminal is on screen) and taps on notifications.
    fun openNotice(n: ShareNotice) {
        val tab = n.tabId?.let { app.sessions.get(it) } ?: n.sessionId?.let { app.sessions.bySessionId(it) }
        when {
            tab != null -> app.sessions.select(tab.id)
            n.sessionId != null -> app.sessions.attach(
                n.sessionId, n.title.ifBlank { resources.getString(R.string.common_session) }, null,
                owner = n.kind == ShareNotice.Kind.JOIN_REQUEST || n.kind == ShareNotice.Kind.CONTROL_REQUEST,
                accountId = n.accountId,
            )
            else -> return
        }
        nav.navigate(Routes.TERMINAL) { launchSingleTop = true }
    }
    LaunchedEffect(Unit) {
        app.shareNotices.notices.collect { n ->
            val active = app.sessions.active.value?.let { app.sessions.get(it) }
            val onScreen = nav.currentDestination?.route == Routes.TERMINAL && active != null &&
                (active.id == n.tabId || (n.sessionId != null && app.sessions.bySessionId(n.sessionId)?.id == active.id))
            if (onScreen) return@collect
            val title = n.title.ifBlank { resources.getString(R.string.common_session) }
            val text = when (n.kind) {
                ShareNotice.Kind.JOIN_REQUEST -> resources.getString(R.string.share_notice_join, n.name, title)
                ShareNotice.Kind.CONTROL_REQUEST -> resources.getString(R.string.share_notice_control, n.name, title)
                ShareNotice.Kind.SHARED_WITH_YOU -> resources.getString(R.string.share_notice_shared, n.name, title)
                ShareNotice.Kind.CONTROL_GRANTED -> resources.getString(R.string.share_notice_granted, title)
                ShareNotice.Kind.CONTROL_REVOKED -> resources.getString(R.string.share_notice_revoked, title)
            }
            val action = if (n.kind == ShareNotice.Kind.CONTROL_REVOKED) null else resources.getString(R.string.common_open)
            scope.launch {
                val result = snackbar.showSnackbar(text, actionLabel = action, withDismissAction = true, duration = SnackbarDuration.Long)
                if (result == SnackbarResult.ActionPerformed) openNotice(n)
            }
        }
    }
    val opened by app.shareNotices.openRequest.collectAsState()
    LaunchedEffect(opened) {
        val n = opened ?: return@LaunchedEffect
        app.shareNotices.openRequest.value = null
        openNotice(n)
    }
    LaunchedEffect(Unit) {
        app.accounts.changes.collect {
            if ((it == "session" || it == "lagged") && app.accounts.loggedIn.value == true) app.sessions.loadServerSessions()
        }
    }
}

@Composable
private fun TabIcon(tab: Tab, selected: Boolean, badge: Int) {
    BadgedBox(badge = { if (badge > 0) Badge { Text("$badge") } }) {
        Icon(if (selected) tab.selectedIcon else tab.icon, null)
    }
}

/** Open terminals, always at hand above the bottom bar (like Termius' connections bar). */
@Composable
private fun TerminalsBar(app: TermoakApp, aboveBar: Boolean, onOpen: () -> Unit) {
    val sessions by app.sessions.list.collectAsState()
    if (sessions.isEmpty()) return
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 3.dp) {
        Row(
            Modifier.fillMaxWidth().then(if (aboveBar) Modifier else Modifier.navigationBarsPadding())
                .clickable(onClick = onOpen).padding(start = 16.dp, end = 8.dp).height(48.dp),
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
            StatusDot(stateColor(state), 7.dp)
        }
        Spacer(Modifier.width(6.dp))
        Text(
            title ?: s.label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 140.dp),
        )
    }
}

/** Color of a terminal's state: connected, connecting, closed or sleeping. */
@Composable
fun stateColor(state: TermState) = when (state) {
    TermState.Running -> Brand.Green
    is TermState.Connecting -> Brand.Amber
    is TermState.Closed -> MaterialTheme.colorScheme.error
    TermState.Asleep -> MaterialTheme.colorScheme.onSurfaceVariant
}

/** Switches to a main tab, keeping the state of each one. */
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

/** Switches between the Vault sections (always over the hosts, so Back returns to them). */
fun NavHostController.goVault(route: String) {
    navigate(route) {
        popUpTo(Routes.HOSTS)
        launchSingleTop = true
    }
}

/**
 * Screen with a top bar in the app style. With [large], a big title (main
 * screens); [header] goes under the bar, pinned. The bar doesn't collapse:
 * a collapsing large title (LargeTopAppBar) opened every main screen with an
 * empty row over the title and the Vault's header further down, and moved it
 * all up on the first scroll.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenScaffold(
    title: String,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    subtitle: String? = null,
    large: Boolean = false,
    header: (@Composable () -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    val scroll = TopAppBarDefaults.pinnedScrollBehavior()
    // Flat bars (like Termius): a line under them when the content scrolls beneath.
    val colors = TopAppBarDefaults.topAppBarColors(scrolledContainerColor = MaterialTheme.colorScheme.surface)
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Column {
                val titleContent: @Composable () -> Unit = {
                    Column {
                        Text(
                            title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = if (large) MaterialTheme.typography.headlineSmall else LocalTextStyle.current,
                        )
                        if (subtitle != null) {
                            Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                TopAppBar(titleContent, navigationIcon = navigationIcon, actions = actions, colors = colors, scrollBehavior = scroll)
                header?.invoke()
                if (scroll.state.overlappedFraction > 0.01f) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                } else {
                    Spacer(Modifier.height(1.dp))
                }
            }
        },
        floatingActionButton = floatingActionButton,
        content = content,
    )
}
