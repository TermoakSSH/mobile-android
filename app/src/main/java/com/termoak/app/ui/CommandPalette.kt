package com.termoak.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CellTower
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tab
import androidx.compose.material.icons.outlined.TextDecrease
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.TextIncrease
import androidx.compose.material.icons.outlined.ViewSidebar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavHostController
import com.termoak.app.R
import com.termoak.app.TermoakApp
import com.termoak.app.data.PaletteCommand
import com.termoak.app.data.PaletteKey
import com.termoak.app.data.Prefs
import com.termoak.app.data.QuickTarget
import com.termoak.app.data.ThemeMode
import com.termoak.app.data.isTelnet
import com.termoak.app.data.uid
import com.termoak.app.userMessage
import com.termoak.ffi.PaletteEntry
import com.termoak.ffi.PaletteKind
import com.termoak.ffi.ServerSession
import com.termoak.ffi.SshHost
import com.termoak.ffi.TermoakException
import com.termoak.ffi.paletteRank
import com.termoak.ffi.paletteRemember
import com.termoak.ffi.snippetVariables
import kotlinx.coroutines.launch

/** An entry of the palette and what it does. */
private class PaletteItem(val entry: PaletteEntry, val icon: ImageVector, val host: SshHost? = null, val run: () -> Unit)

/**
 * Quick connect extended into a command palette like the desktop's (Ctrl+K
 * outside the terminal, Ctrl+Shift+P, Ctrl+Shift+T and the tab bar's "+"):
 * find a host, an open tab, a session on the server, a snippet (run in the
 * terminal in view) or an action of the app; ↑/↓ choose, Enter opens it,
 * Esc closes. The ranking is the engine's (paletteRank, fuzzy, the ones
 * chosen lately first), and an address typed (`user@host:port`,
 * `telnet://…`) that no host matches connects to it.
 */
@Composable
fun CommandPaletteDialog(app: TermoakApp, nav: NavHostController, onDismiss: () -> Unit, onConnect: (SshHost) -> Unit) {
    val resources = LocalResources.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val tabs by app.sessions.list.collectAsState()
    val active by app.sessions.active.collectAsState()
    val split by app.sessions.split.collectAsState()
    val fontSize by app.prefs.fontSize.collectAsState()
    val theme by app.prefs.theme.collectAsState()
    val systemDark = isSystemInDarkTheme()
    val maxPanes = rememberMaxPanes()
    val loggedIn by app.accounts.loggedIn.collectAsState()
    val hosts = remember {
        runCatching { app.core.listHosts(app.accounts.filter()) }.getOrDefault(emptyList())
            .sortedWith(compareByDescending<SshHost> { it.favorite }.thenBy { it.label.lowercase() })
    }
    val snippets = remember {
        runCatching { app.core.listSnippets(app.accounts.filter()) }.getOrDefault(emptyList())
            .filter { runCatching { snippetVariables(it.script) }.getOrDefault(emptyList()).isEmpty() }
    }
    var serverSessions by remember { mutableStateOf<List<Pair<String, ServerSession>>>(emptyList()) }
    LaunchedEffect(Unit) {
        serverSessions = app.accounts.active().flatMap { a ->
            runCatching { app.accounts.handle(a.id)?.listServerSessions()?.active.orEmpty() }.getOrDefault(emptyList()).map { a.id to it }
        }
    }
    var query by remember { mutableStateOf("") }
    var highlight by remember { mutableIntStateOf(0) }
    var recent by remember { mutableStateOf(app.prefs.paletteRecent) }
    val listState = rememberLazyListState()
    val focus = remember { FocusRequester() }
    val terminalShown = nav.currentDestination?.route == Routes.TERMINAL
    val current = tabs.firstOrNull { it.id == active }

    fun close() = onDismiss()
    val items = buildList {
        // Open tabs.
        tabs.forEach { s ->
            val title = s.title.value ?: s.label
            add(PaletteItem(PaletteEntry(PaletteKey.tab(s.id), PaletteKind.TAB, title, resources.getString(R.string.palette_kind_tab)), Icons.Outlined.Tab) {
                app.sessions.select(s.id)
                nav.showTerminal()
            })
        }
        // Saved hosts.
        hosts.forEach { h ->
            add(
                PaletteItem(
                    PaletteEntry(PaletteKey.host(h.uid), PaletteKind.HOST, h.label, hostAddress(h), h.tags + h.address),
                    Icons.Outlined.Bolt, host = h,
                ) { onConnect(h) },
            )
        }
        // Sessions running on the server.
        serverSessions.forEach { (account, s) ->
            add(
                PaletteItem(
                    PaletteEntry(PaletteKey.session("$account/${s.id}"), PaletteKind.SESSION, s.title.ifBlank { s.id }, resources.getString(R.string.palette_kind_session)),
                    Icons.Outlined.CloudQueue,
                ) {
                    app.sessions.attach(s.id, s.title.ifBlank { resources.getString(R.string.common_session) }, s.hostId, accountId = account)
                    nav.showTerminal()
                },
            )
        }
        // Snippets: run in the terminal in view.
        if (current != null) {
            snippets.forEach { sn ->
                add(PaletteItem(PaletteEntry(PaletteKey.snippet(sn.uid), PaletteKind.SNIPPET, sn.name, sn.script.lines().firstOrNull().orEmpty(), sn.tags), Icons.Outlined.Code) {
                    current.run(sn.script)
                    nav.showTerminal()
                })
            }
        }
        // Actions of the app.
        val state = PaletteCommand.State(
            tabs = tabs.size, terminalShown = terminalShown, splitAvailable = maxPanes >= 2, splitActive = split.on && terminalShown,
        )
        PaletteCommand.available(state).forEach { c ->
            val (title, icon) = when (c) {
                PaletteCommand.HOME -> resources.getString(R.string.palette_cmd_home) to Icons.Outlined.Home
                PaletteCommand.NEXT_TAB -> resources.getString(R.string.kb_next_tab) to Icons.AutoMirrored.Outlined.ArrowForward
                PaletteCommand.CLOSE_TAB -> resources.getString(R.string.kb_close_tab) to Icons.Outlined.Close
                PaletteCommand.ADD_TO_SPLIT -> resources.getString(R.string.split_add_pane) to Icons.Outlined.GridView
                PaletteCommand.FOCUS_MODE -> resources.getString(R.string.split_focus_mode) to Icons.Outlined.ViewSidebar
                PaletteCommand.BROADCAST -> resources.getString(R.string.split_broadcast) to Icons.Outlined.CellTower
                PaletteCommand.ZOOM_IN -> resources.getString(R.string.kb_zoom_in) to Icons.Outlined.TextIncrease
                PaletteCommand.ZOOM_OUT -> resources.getString(R.string.kb_zoom_out) to Icons.Outlined.TextDecrease
                PaletteCommand.ZOOM_RESET -> resources.getString(R.string.kb_zoom_reset) to Icons.Outlined.TextFields
                PaletteCommand.TOGGLE_THEME -> resources.getString(R.string.palette_cmd_toggle_theme) to Icons.Outlined.Contrast
            }
            add(PaletteItem(PaletteEntry(c.key, PaletteKind.COMMAND, title, resources.getString(R.string.palette_kind_command), c.keywords), icon) {
                when (c) {
                    PaletteCommand.HOME -> nav.goTab(Routes.HOSTS)
                    PaletteCommand.NEXT_TAB -> {
                        val i = tabs.indexOfFirst { it.id == active }
                        if (tabs.isNotEmpty()) app.sessions.select(tabs[(i + 1).mod(tabs.size)].id)
                        nav.showTerminal()
                    }
                    PaletteCommand.CLOSE_TAB -> active?.let { app.sessions.close(it) }
                    PaletteCommand.ADD_TO_SPLIT -> {
                        app.sessions.addPane(maxPanes)
                        nav.showTerminal()
                    }
                    PaletteCommand.FOCUS_MODE -> app.sessions.setFocusMode(!split.focusMode)
                    PaletteCommand.BROADCAST -> app.sessions.setBroadcast(!split.broadcast)
                    PaletteCommand.ZOOM_IN -> app.prefs.setFontSize(fontSize + 1)
                    PaletteCommand.ZOOM_OUT -> app.prefs.setFontSize(fontSize - 1)
                    PaletteCommand.ZOOM_RESET -> app.prefs.setFontSize(Prefs.DEFAULT_FONT)
                    PaletteCommand.TOGGLE_THEME -> {
                        val dark = theme == ThemeMode.DARK || (theme == ThemeMode.SYSTEM && systemDark)
                        app.prefs.setTheme(if (dark) ThemeMode.LIGHT else ThemeMode.DARK)
                    }
                }
            })
        }
        // Go to each section.
        val sections = buildList {
            add(Routes.HOSTS to R.string.nav_vault)
            add(Routes.keys() to R.string.section_keychain)
            add(Routes.SNIPPETS to R.string.section_snippets)
            add(Routes.FORWARDS to R.string.section_tunnels)
            add(Routes.KNOWN_HOSTS to R.string.section_known_hosts)
            add(Routes.CONNECTIONS to R.string.nav_connections)
            if (loggedIn == true) add(Routes.AI to R.string.section_ai)
            add(Routes.SETTINGS to R.string.section_settings)
        }
        sections.forEach { (route, label) ->
            val name = resources.getString(label)
            add(
                PaletteItem(
                    PaletteEntry(PaletteKey.go(route), PaletteKind.COMMAND, resources.getString(R.string.palette_go_to, name), "", listOf("go", name)),
                    Icons.AutoMirrored.Outlined.ArrowForward,
                ) { nav.goTab(route) },
            )
        }
    }
    val shown = remember(query, items.size, recent) {
        runCatching { paletteRank(query.trim(), items.map { it.entry }, recent) }.getOrDefault(emptyList())
            .mapNotNull { m -> items.getOrNull(m.index.toInt())?.let { it to m.hits.map(UInt::toInt).toSet() } }
    }
    // An address typed when no saved host matches the search.
    val target = if (query.isNotBlank() && shown.none { it.first.entry.kind == PaletteKind.HOST }) QuickTarget.parse(query) else null
    val rows = (if (target != null) 1 else 0) + shown.size

    fun choose(item: PaletteItem) {
        if (PaletteKey.remembered(item.entry.key)) {
            recent = runCatching { paletteRemember(recent, item.entry.key) }.getOrDefault(recent)
            app.prefs.paletteRecent = recent
        }
        close()
        item.run()
    }
    fun connectTo(t: QuickTarget) {
        try {
            onConnect(quickConnectHost(app, hosts, t))
        } catch (e: TermoakException) {
            scope.launch { snackbar.showSnackbar(e.userMessage(resources, R.string.error_save_failed)) }
        }
    }
    fun enter() {
        if (target != null && highlight == 0) connectTo(target)
        else shown.getOrNull(highlight - (if (target != null) 1 else 0))?.first?.let(::choose)
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    LaunchedEffect(query) { highlight = 0 }
    LaunchedEffect(highlight) { if (rows > 0) listState.animateScrollToItem(highlight.coerceIn(0, rows - 1)) }

    Dialog(onDismissRequest = ::close) {
        Surface(
            Modifier.widthIn(max = 600.dp).fillMaxWidth().heightIn(max = 600.dp),
            shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(Modifier.padding(vertical = 12.dp)) {
                OutlinedTextField(
                    query, { query = it },
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).focusRequester(focus)
                        .onPreviewKeyEvent { e ->
                            if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when (e.key) {
                                Key.DirectionDown -> { if (rows > 0) highlight = (highlight + 1).coerceAtMost(rows - 1); true }
                                Key.DirectionUp -> { highlight = (highlight - 1).coerceAtLeast(0); true }
                                Key.Escape -> { close(); true }
                                Key.Enter, Key.NumPadEnter -> { enter(); true }
                                else -> false
                            }
                        },
                    placeholder = { Text(stringResource(R.string.palette_placeholder)) },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go, keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
                    keyboardActions = KeyboardActions(onGo = { enter() }),
                )
                if (rows == 0) {
                    Text(
                        stringResource(if (items.isEmpty()) R.string.hosts_empty_title else R.string.palette_no_matches),
                        Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LazyColumn(state = listState) {
                    if (target != null) {
                        item(key = "quick") {
                            PaletteRow(
                                Icons.Outlined.Bolt, stringResource(R.string.quick_connect_to, target.display()),
                                stringResource(if (target.telnet) R.string.quick_connect_detail_telnet else R.string.quick_connect_detail),
                                emptySet(), highlight == 0,
                            ) { connectTo(target) }
                        }
                    }
                    itemsIndexed(shown, key = { _, it -> it.first.entry.key }) { i, (item, hits) ->
                        val row = i + if (target != null) 1 else 0
                        PaletteRow(
                            item.icon, item.entry.title, item.entry.detail, hits, highlight == row,
                            tile = item.host,
                        ) { choose(item) }
                    }
                }
            }
        }
    }
}

/** A row: its icon (a host's tile), the title with the letters found in bold, and its detail. */
@Composable
private fun PaletteRow(icon: ImageVector, title: String, detail: String, hits: Set<Int>, highlighted: Boolean, tile: SshHost? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .background(if (highlighted) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (tile != null) {
            HostTile(tile, size = 32.dp, twoInitials = true)
        } else {
            Box(Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            val accent = MaterialTheme.colorScheme.primary
            val text = buildAnnotatedString {
                var i = 0
                title.codePoints().forEach { cp ->
                    val ch = String(Character.toChars(cp))
                    if (i in hits) withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = accent)) { append(ch) } else append(ch)
                    i++
                }
            }
            Text(text, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (detail.isNotEmpty()) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (tile?.isTelnet == true) TelnetBadge(Modifier.padding(start = 8.dp))
    }
}
