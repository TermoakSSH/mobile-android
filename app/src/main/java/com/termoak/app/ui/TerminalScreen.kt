package com.termoak.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CellTower
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloseFullscreen
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.KeyboardCommandKey
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.TextDecrease
import androidx.compose.material.icons.outlined.TextIncrease
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavHostController
import com.termoak.app.R
import com.termoak.app.TermoakApp
import com.termoak.app.UiText
import com.termoak.app.asString
import com.termoak.app.data.Prefs
import com.termoak.app.data.uid
import com.termoak.app.term.GestureMode
import com.termoak.app.term.InitialConnecting
import com.termoak.app.term.KeyStroke
import com.termoak.app.term.Latency
import com.termoak.app.term.LiveShare
import com.termoak.app.term.LocalTerminal
import com.termoak.app.term.Paste
import com.termoak.app.term.Pending
import com.termoak.app.term.ServerTerminal
import com.termoak.app.term.Shortcut
import com.termoak.app.term.SpecialKey
import com.termoak.app.term.SuggestionMode
import com.termoak.app.term.TermSession
import com.termoak.app.term.TermState
import com.termoak.app.term.TerminalFont
import com.termoak.app.term.TerminalView
import com.termoak.ffi.Snippet
import com.termoak.ffi.TerminalKey
import com.termoak.ffi.snippetVariables
import kotlinx.coroutines.launch

private val TermBg = Color(0xFF12151D)
private val BarBg = Color(0xFF1A1F2B)
private val KeyBg = Color(0xFF252C3B)
private val KeyFg = Color(0xFFD6DBE4)
internal val TermScreenBg = TermBg
internal val TermBarBg = BarBg
internal val TermKeyBg = KeyBg
internal val TermKeyFg = KeyFg

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun TerminalScreen(app: TermoakApp, nav: NavHostController) {
    val context = LocalContext.current
    val sessions by app.sessions.list.collectAsState()
    val activeId by app.sessions.active.collectAsState()
    val split by app.sessions.split.collectAsState()
    val fontSize by app.prefs.fontSize.collectAsState()
    val keepOn by app.prefs.keepScreenOn.collectAsState()
    val confirmPaste by app.prefs.confirmMultilinePaste.collectAsState()
    val hardwareKeyboard by app.keyboard.connected.collectAsState()
    val keepKeyBar by app.prefs.keyBarWithKeyboard.collectAsState()
    val gestureMode by app.prefs.cursorGestures.collectAsState()
    val keyLayout by app.prefs.keyboardLayout.collectAsState()
    val suggestionMode by app.prefs.commandSuggestions.collectAsState()
    val terminalFont by app.prefs.terminalFont.collectAsState()
    val session = sessions.firstOrNull { it.id == activeId } ?: sessions.lastOrNull()
    val clipboard = remember { context.getSystemService(ClipboardManager::class.java) }
    // The terminal views on screen (one, or one per pane): the keyboard goes to the focused one.
    val views = remember { mutableListOf<TerminalView>() }
    var showSnippets by remember { mutableStateOf(false) }
    var showSplitPicker by remember { mutableStateOf(false) }
    var terminating by remember { mutableStateOf<ServerTerminal?>(null) }
    var pasteAsk by remember { mutableStateOf<Pair<TermSession, String>?>(null) }
    // Copilot: side by side on tablets; on top, from the right, on phones.
    var copilotOpen by rememberSaveable { mutableStateOf(false) }
    // Quick access panel: in the keyboard's place on phones, beside the terminal in the desktop layout.
    var panelOpen by rememberSaveable { mutableStateOf(false) }
    // Window size class (it changes when a foldable folds or unfolds; the sessions stay).
    val maxPanes = rememberMaxPanes()
    val wide = maxPanes >= 2
    val desktop = LocalDesktop.current

    LaunchedEffect(sessions.isEmpty()) { if (sessions.isEmpty()) nav.popBackStack() }
    if (session == null) return
    // A sleeping tab attaches when opened.
    LaunchedEffect(session.id) { app.sessions.wake(session.id) }

    val state by session.state.collectAsState()
    val pending by session.pending.collectAsState()
    val title by session.title.collectAsState()
    val live by session.live.collectAsState()
    val loggedIn by app.accounts.loggedIn.collectAsState()
    val snackbar = LocalSnackbar.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    var showShare by remember { mutableStateOf(false) }
    var showParticipants by remember { mutableStateOf(false) }
    val sharable = canShare(session, loggedIn == true, live.isOwner)
    // Notices of this terminal ("You have the keyboard", an action that wasn't allowed...).
    LaunchedEffect(session.id) { session.toasts.collect { snackbar.showSnackbar(it.resolve(resources)) } }
    val host = remember(session.hostId) { session.hostId?.let { runCatching { app.core.getHost(it, session.accountId) }.getOrNull() } }

    // ----- Split view: the panes that fit this window (none on a phone) -----
    val panes = if (split.maximized != null) emptyList() else
        split.visible(maxPanes, session.id).mapNotNull { id -> sessions.firstOrNull { it.id == id } }
    val splitShown = panes.size >= 2 && session in panes
    val broadcasting = splitShown && split.broadcast
    // Broadcast input: what is typed in the focused pane goes to the other visible ones.
    val broadcastTargets = if (broadcasting) panes.filter { it.id != session.id } else emptyList()
    DisposableEffect(session, broadcastTargets) {
        session.onInput = if (broadcastTargets.isEmpty()) null else { input -> broadcastTargets.forEach { it.apply(input) } }
        onDispose { session.onInput = null }
    }

    val hostView = LocalView.current
    DisposableEffect(keepOn) {
        hostView.keepScreenOn = keepOn
        onDispose { hostView.keepScreenOn = false }
    }
    fun focusedView(): TerminalView? = views.firstOrNull { it.session === session } ?: views.firstOrNull()
    // When the panel closes, the AI stops and loses access to the terminal.
    fun closeCopilot() {
        copilotOpen = false
        app.copilot.stop(session)
    }
    BackHandler { if (copilotOpen) closeCopilot() else nav.popBackStack() }

    /** A snippet typed in [target] (and, while broadcasting, in the other panes), in the split's panes or in every open terminal. */
    fun useSnippet(target: TermSession, name: String, text: String, run: Boolean, to: SnippetTarget) {
        when (to) {
            SnippetTarget.THIS -> if (run) target.run(text) else target.paste(text)
            SnippetTarget.PANES -> app.snippetRuns.onSessions(name, text, run, panes.map { it.id })
            SnippetTarget.ALL_OPEN -> app.snippetRuns.onSessions(name, text, run, sessions.map { it.id })
        }
    }

    /** Pastes into [target], asking first for several lines (unless it uses bracketed paste). */
    fun requestPaste(target: TermSession, text: String) {
        if (text.isEmpty()) return
        if (Paste.needsConfirmation(text, confirmPaste, target.bracketedPaste)) pasteAsk = target to text else target.paste(text)
    }
    fun paste(target: TermSession) {
        clipboard?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.let { requestPaste(target, it.toString()) }
    }
    fun copyScreen(target: TermSession) {
        clipboard?.setPrimaryClip(ClipData.newPlainText("terminal", target.screen.screenText()))
    }
    /** Copies the mouse selection of [view] (if any) and clears it. */
    fun copySelection(view: TerminalView?): Boolean {
        val text = view?.selectedText()?.takeIf { it.isNotEmpty() } ?: return false
        clipboard?.setPrimaryClip(ClipData.newPlainText("terminal", text))
        view.clearSelection()
        return true
    }

    // ----- Hardware keyboard shortcuts (Ctrl+Shift+…) -----
    ShortcutHandler { shortcut ->
        // The ones that type or read text only while the terminal has the focus (not the copilot's box).
        val view = focusedView()
        val typing = views.any { it.isFocused }
        fun neighbour(step: Int) {
            val i = sessions.indexOf(session)
            if (i >= 0 && sessions.size > 1) app.sessions.select(sessions[(i + step + sessions.size) % sessions.size].id)
        }
        when (shortcut) {
            Shortcut.NEW_TAB -> if (desktop) DesktopUi.quickConnect.value = true else nav.goTab(Routes.HOSTS)
            Shortcut.CLOSE_TAB -> app.sessions.close(session.id)
            Shortcut.NEXT_TAB -> neighbour(1)
            Shortcut.PREV_TAB -> neighbour(-1)
            Shortcut.ZOOM_IN -> app.prefs.setFontSize(fontSize + 1)
            Shortcut.ZOOM_OUT -> app.prefs.setFontSize(fontSize - 1)
            Shortcut.ZOOM_RESET -> app.prefs.setFontSize(Prefs.DEFAULT_FONT)
            Shortcut.NEXT_PANE -> if (splitShown) {
                val next = panes[(panes.indexOf(session) + 1) % panes.size]
                app.sessions.select(next.id)
                views.firstOrNull { it.session === next }?.requestFocus()
            }
            Shortcut.COPY -> {
                if (!typing) return@ShortcutHandler false
                if (!copySelection(view)) copyScreen(session)
                scope.launch { snackbar.showSnackbar(resources.getString(R.string.term_copied)) }
            }
            Shortcut.PASTE -> {
                if (!typing) return@ShortcutHandler false
                paste(session)
            }
            Shortcut.SCROLL_PAGE_UP, Shortcut.SCROLL_PAGE_DOWN -> {
                if (!typing) return@ShortcutHandler false
                val up = shortcut == Shortcut.SCROLL_PAGE_UP
                // Full-screen programs (less, vim...) get Shift+PgUp/PgDn themselves.
                if (session.screen.alternateScreen()) {
                    session.stroke(KeyStroke.Special(if (up) SpecialKey.PAGE_UP else SpecialKey.PAGE_DOWN, shift = true))
                } else {
                    view?.scrollPage(up)
                }
            }
            Shortcut.SEARCH_HOSTS, Shortcut.SHORTCUTS -> return@ShortcutHandler false
        }
        true
    }

    /** A terminal (the whole screen, or a pane of the split view). */
    @Composable
    fun Pane(s: TermSession, focused: Boolean) {
        TerminalPane(
            app, nav, s, fontSize, focused,
            gestureMode = gestureMode,
            suggestionMode = suggestionMode,
            font = terminalFont,
            views = views,
            onFocus = { if (s.id != app.sessions.active.value) app.sessions.select(s.id) },
            onPaste = { requestPaste(s, it) },
            pasteClipboard = { paste(s) },
            copyScreen = { copyScreen(s) },
            copySelection = { v -> copySelection(v) },
            onParticipants = { showParticipants = true },
        )
    }

    // Files over this terminal's connection (or through the server for a server session).
    val canBrowse = when (session) {
        // Telnet has no SFTP.
        is LocalTerminal -> state == TermState.Running && !session.telnet
        is ServerTerminal -> live.isOwner && session.hostId != null
        else -> false
    }
    fun openFiles() {
        filesSourceOf(session, title ?: session.label)?.let { nav.openFiles(it) }
    }
    fun toggleCopilot() {
        if (copilotOpen) {
            closeCopilot()
        } else {
            copilotOpen = true
            if (!wide) focusedView()?.hideKeyboard()
        }
    }

    // The panel closes when the keyboard comes back (a tap on the terminal...).
    val imeVisible = WindowInsets.isImeVisible
    LaunchedEffect(imeVisible) { if (imeVisible && !desktop) panelOpen = false }
    // As tall as the keyboard was (the terminal keeps its size), at least 260 dp.
    val density = LocalDensity.current
    val imeHeight = WindowInsets.ime.getBottom(density)
    var lastIme by remember { mutableIntStateOf(0) }
    LaunchedEffect(imeHeight) { if (imeHeight > lastIme) lastIme = imeHeight }
    val panelHeight = with(density) { lastIme.toDp() - 52.dp }.coerceIn(260.dp, 420.dp)

    /** The quick access panel for [target] (keys, snippets, history, appearance). */
    @Composable
    fun QuickPanelHere(target: TermSession, side: Boolean, modifier: Modifier) {
        QuickPanel(
            app, target, side = side, panes = if (splitShown) panes.size else 0, openCount = sessions.size,
            onKeyboard = {
                panelOpen = false
                focusedView()?.showSoftKeyboard()
            },
            onCustomize = { nav.navigate(Routes.KEYBOARD) },
            onPaste = { paste(target) },
            onSnippet = { name, text, run, to -> useSnippet(target, name, text, run, to) },
            modifier = modifier,
        )
    }

    /** Split view (tablets, unfolded foldables); a maximized pane goes back to the grid. */
    @Composable
    fun SplitButton() {
        if (split.maximized != null && split.on && wide) {
            IconButton(onClick = { app.sessions.maximize(null) }) {
                Icon(Icons.Outlined.CloseFullscreen, stringResource(R.string.split_restore), tint = MaterialTheme.colorScheme.primary)
            }
        } else if (wide && sessions.size >= 2) {
            IconButton(onClick = { showSplitPicker = true }) {
                Icon(
                    Icons.Outlined.GridView, stringResource(R.string.split_title),
                    tint = if (splitShown) MaterialTheme.colorScheme.primary else KeyFg,
                )
            }
        }
    }

    /**
     * The terminal's menu. [inToolbar]: the desktop layout, whose toolbar
     * already has paste, copy, files and share.
     */
    @Composable
    fun MenuEntries(dismiss: () -> Unit, inToolbar: Boolean) {
        if (!inToolbar) {
            DropdownMenuItem({ Text(stringResource(R.string.common_paste)) }, { dismiss(); paste(session) },
                leadingIcon = { Icon(Icons.Outlined.ContentPaste, null) })
        }
        DropdownMenuItem({ Text(stringResource(R.string.term_copy_screen)) }, { dismiss(); copyScreen(session) },
            leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) })
        DropdownMenuItem({ Text(stringResource(R.string.term_font_bigger)) }, { app.prefs.setFontSize(fontSize + 1) },
            leadingIcon = { Icon(Icons.Outlined.TextIncrease, null) })
        DropdownMenuItem({ Text(stringResource(R.string.term_font_smaller)) }, { app.prefs.setFontSize(fontSize - 1) },
            leadingIcon = { Icon(Icons.Outlined.TextDecrease, null) })
        DropdownMenuItem({ Text(stringResource(R.string.kb_shortcuts)) }, { dismiss(); KeyShortcuts.sheet.value = true },
            leadingIcon = { Icon(Icons.Outlined.KeyboardCommandKey, null) })
        if (splitShown) {
            DropdownMenuItem(
                { Text(stringResource(if (split.broadcast) R.string.split_broadcast_stop else R.string.split_broadcast)) },
                { dismiss(); app.sessions.setBroadcast(!split.broadcast) },
                leadingIcon = { Icon(Icons.Outlined.CellTower, null, tint = if (split.broadcast) Brand.Amber else LocalContentColor.current) },
            )
        }
        if (canBrowse && !inToolbar) {
            DropdownMenuItem({ Text(stringResource(R.string.files_sftp)) }, {
                dismiss()
                openFiles()
            }, leadingIcon = { Icon(Icons.Outlined.Folder, null) })
        }
        HorizontalDivider()
        if (sharable && !inToolbar) {
            DropdownMenuItem({ Text(stringResource(R.string.share_action)) }, { dismiss(); showShare = true },
                leadingIcon = { Icon(Icons.Outlined.PersonAdd, null) })
        }
        if (live.participants.size > 1 || !live.isOwner) {
            DropdownMenuItem({ Text(stringResource(R.string.share_participants)) }, { dismiss(); showParticipants = true },
                leadingIcon = { Icon(Icons.Outlined.Group, null) })
        }
        if (live.ended == null) {
            DropdownMenuItem({ Text(stringResource(R.string.term_reconnect)) }, { dismiss(); session.reconnect() },
                leadingIcon = { Icon(Icons.Outlined.Refresh, null) })
        }
        if (session is ServerTerminal && live.isOwner) {
            DropdownMenuItem(
                { Text(stringResource(R.string.term_terminate_server_session), color = MaterialTheme.colorScheme.error) },
                { dismiss(); terminating = session },
                leadingIcon = { Icon(Icons.Outlined.PowerSettingsNew, null, tint = MaterialTheme.colorScheme.error) },
            )
        }
        DropdownMenuItem(
            { Text(stringResource(if (session.persistent && live.isOwner) R.string.term_close_tab_keep else R.string.common_close)) },
            { dismiss(); app.sessions.close(session.id) },
            leadingIcon = { Icon(Icons.Outlined.Close, null) },
        )
    }

    TerminalFrame(
        desktop = desktop,
        wide = wide,
        copilotOpen = copilotOpen,
        onCloseCopilot = { closeCopilot() },
        header = {
            if (desktop) {
                // ----- Desktop layout: the tabs are on top of the window; the terminal's toolbar -----
                DesktopTerminalToolbar(
                    session, state, title, live, sharable = sharable, canBrowse = canBrowse,
                    copilotOpen = copilotOpen, splitShown = splitShown, hardwareKeyboard = hardwareKeyboard,
                    splitButton = { SplitButton() },
                    cursorButton = { if (gestureMode == GestureMode.BUTTON && live.canWrite) CursorToggleButton(session) },
                    onReconnect = { session.reconnect() },
                    onFiles = { openFiles() },
                    onCopy = {
                        if (!copySelection(focusedView())) copyScreen(session)
                        scope.launch { snackbar.showSnackbar(resources.getString(R.string.term_copied)) }
                    },
                    onPaste = { paste(session) },
                    onCopilot = { toggleCopilot() },
                    onShare = { showShare = true },
                    onParticipants = { showParticipants = true },
                    onSnippets = { showSnippets = true },
                    onKeyboard = { focusedView()?.showSoftKeyboard() },
                    panelOpen = panelOpen,
                    onPanel = { panelOpen = !panelOpen },
                ) { dismiss -> MenuEntries(dismiss, inToolbar = true) }
            } else {
                // ----- Top bar -----
                Row(Modifier.fillMaxWidth().background(BarBg).height(52.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back), tint = KeyFg)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            title ?: session.label, color = KeyFg, style = MaterialTheme.typography.titleSmall,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                terminalStatus(session, state, live), Modifier.weight(1f, fill = false),
                                color = KeyFg.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall, maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            // The round trip to the host (terminals from the phone).
                            if (session is LocalTerminal) {
                                Text(" · ", color = KeyFg.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall)
                                LatencyText(session, state)
                            }
                        }
                    }
                    if (live.others.isNotEmpty() || live.pendingRequests > 0) {
                        ParticipantsChip(live) { showParticipants = true }
                    }
                    SplitButton()
                    if (gestureMode == GestureMode.BUTTON && live.canWrite) CursorToggleButton(session)
                    IconButton(onClick = { toggleCopilot() }) {
                        Icon(
                            Icons.Outlined.AutoAwesome, stringResource(R.string.copilot_title),
                            tint = if (copilotOpen) MaterialTheme.colorScheme.primary else KeyFg,
                        )
                    }
                    IconButton(onClick = { showSnippets = true }) {
                        Icon(Icons.Outlined.Code, stringResource(R.string.section_snippets), tint = KeyFg)
                    }
                    IconButton(onClick = { focusedView()?.showSoftKeyboard() }) {
                        Icon(Icons.Outlined.Keyboard, stringResource(R.string.term_keyboard), tint = KeyFg)
                    }
                    Box {
                        var menu by remember { mutableStateOf(false) }
                        IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.common_more), tint = KeyFg) }
                        DropdownMenu(menu, { menu = false }) { MenuEntries({ menu = false }, inToolbar = false) }
                    }
                }

                // ----- Tabs (always, with "+" to open another) -----
                Row(
                    Modifier.fillMaxWidth().background(BarBg).horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    sessions.forEach { s ->
                        SessionTab(s, s.id == session.id, inSplit = splitShown && s in panes, { app.sessions.select(s.id) }, { app.sessions.close(s.id) })
                    }
                    Box(
                        Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).clickable { nav.goTab(Routes.HOSTS) },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Outlined.Add, stringResource(R.string.term_open_another), Modifier.size(18.dp), tint = KeyFg) }
                }
            }

            // ----- Broadcast banner (orange, like the desktop's) -----
            if (broadcasting) BroadcastBanner(panes.size) { app.sessions.setBroadcast(false) }
        },
        terminal = {
            if (splitShown) {
                SplitPanes(
                    panes, session.id, split.broadcast,
                    onFocus = { app.sessions.select(it.id) },
                    onMaximize = { app.sessions.maximize(it.id) },
                    onRemove = { app.sessions.removePane(it.id) },
                    onBroadcast = { app.sessions.setBroadcast(!split.broadcast) },
                ) { s, focused -> Pane(s, focused) }
            } else {
                Pane(session, focused = true)
            }
        },
        footer = {
            KeyboardStrip(session, live)
            // With a hardware keyboard the key bar goes away (unless the setting keeps it).
            if (live.canWrite && (!hardwareKeyboard || keepKeyBar)) {
                KeyBar(
                    session, keyLayout, suggestionMode, cursorButton = gestureMode == GestureMode.BUTTON, panelOpen = panelOpen,
                    onPaste = { paste(session) },
                ) {
                    if (panelOpen) {
                        panelOpen = false
                        if (!desktop) focusedView()?.showSoftKeyboard()
                    } else {
                        panelOpen = true
                        if (!desktop) focusedView()?.hideKeyboard()
                    }
                }
            }
            // On phones the panel takes the keyboard's place.
            if (panelOpen && !desktop && live.canWrite) {
                QuickPanelHere(session, side = false, Modifier.fillMaxWidth().height(panelHeight))
            }
        },
        side = if (panelOpen && desktop && live.canWrite) ({ QuickPanelHere(session, side = true, Modifier.fillMaxSize()) }) else null,
    ) { modifier, overlay ->
        CopilotPanel(
            app, session, host, onClose = { closeCopilot() }, onLogin = { nav.navigate(Routes.login()) },
            onAiSettings = { nav.navigate(Routes.AI_KEYS) },
            modifier = modifier, swipeToClose = overlay,
        )
    }

    // ----- Dialogs -----
    pending?.let { PendingDialog(it) }
    pasteAsk?.let { (target, text) ->
        PasteConfirmDialog(
            text, onDismiss = { pasteAsk = null },
        ) { dontAsk ->
            if (dontAsk) app.prefs.setConfirmMultilinePaste(false)
            pasteAsk = null
            target.paste(text)
        }
    }
    if (showSnippets) {
        SnippetsSheet(
            app,
            panes = if (splitShown) panes.size else 0,
            openCount = sessions.size,
            onDismiss = { showSnippets = false },
        ) { sn, text, run, target ->
            showSnippets = false
            // Typed here (and, while broadcasting, in the other panes too), or in the panes or every open terminal.
            useSnippet(session, sn.name, text, run, target)
        }
    }
    if (showSplitPicker) {
        SplitPickerSheet(
            sessions, current = if (split.on) split.panes else listOf(session.id), max = maxPanes, splitOn = split.on,
            onDismiss = { showSplitPicker = false },
        ) { ids ->
            showSplitPicker = false
            app.sessions.setSplit(ids)
        }
    }
    if (showShare) {
        ShareSheet(app, session, title ?: session.label) { showShare = false }
    }
    if (showParticipants) {
        ParticipantsSheet(
            session, live,
            onShare = if (sharable) ({ showParticipants = false; showShare = true }) else null,
            onDismiss = { showParticipants = false },
        )
    }
    terminating?.let { s ->
        ConfirmDialog(
            title = stringResource(R.string.term_terminate_title),
            text = stringResource(R.string.term_terminate_text),
            confirm = stringResource(R.string.sessions_terminate),
            destructive = true,
            onDismiss = { terminating = null },
        ) {
            s.terminate()
            app.sessions.close(s.id)
        }
    }
}

/** The second line of the terminal's bar: connecting, who has the keyboard, who watches, or what it is. */
@Composable
private fun terminalStatus(session: TermSession, state: TermState, live: LiveShare): String = when (state) {
    is TermState.Connecting -> if (live.waiting != null) stringResource(R.string.share_waiting_short)
    else state.message.asString()
    TermState.Running -> when {
        !live.isOwner && live.canWrite -> stringResource(R.string.share_you_have_keyboard)
        live.driverLabel() != null -> stringResource(R.string.share_is_typing, live.driverLabel()!!)
        !live.isOwner -> stringResource(R.string.share_view_only)
        live.others.isNotEmpty() -> pluralStringResource(
            R.plurals.share_watching, live.others.size, live.others.size,
        )
        else -> stringResource(
            when {
                session.persistent -> R.string.term_server_session
                session is LocalTerminal && session.telnet -> R.string.term_telnet_from_phone
                else -> R.string.term_ssh_from_phone
            },
        )
    }
    is TermState.Closed -> stringResource(R.string.term_disconnected)
    TermState.Asleep -> stringResource(R.string.term_not_connected)
}

/**
 * The terminal's toolbar in the desktop layout (the desktop's terminal
 * header): state and name, Local / Server / Shared, what is going on, and
 * Files, Copy, Paste, AI and Share with their names when there is room.
 */
@Composable
private fun DesktopTerminalToolbar(
    session: TermSession,
    state: TermState,
    title: String?,
    live: LiveShare,
    sharable: Boolean,
    canBrowse: Boolean,
    copilotOpen: Boolean,
    splitShown: Boolean,
    hardwareKeyboard: Boolean,
    splitButton: @Composable () -> Unit,
    cursorButton: @Composable () -> Unit,
    onReconnect: () -> Unit,
    onFiles: () -> Unit,
    onCopy: () -> Unit,
    onPaste: () -> Unit,
    onCopilot: () -> Unit,
    onShare: () -> Unit,
    onParticipants: () -> Unit,
    onSnippets: () -> Unit,
    onKeyboard: () -> Unit,
    panelOpen: Boolean,
    onPanel: () -> Unit,
    menuEntries: @Composable (dismiss: () -> Unit) -> Unit,
) {
    // A terminal from this device shared through the server.
    val sharedLocally = (session as? LocalTerminal)?.sharedId?.collectAsState()?.value
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().background(BarBg)) {
        // Narrow (the copilot open, a small window): icons only, like the desktop's panes.
        val labels = maxWidth >= 900.dp
        Row(Modifier.fillMaxWidth().height(44.dp).padding(start = 14.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            StatusDot(stateColor(state), 8.dp)
            Text(
                title ?: session.label, Modifier.padding(start = 8.dp).widthIn(max = 260.dp), color = KeyFg,
                style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            val (kind, kindColor) = when {
                session is ServerTerminal && !live.isOwner -> stringResource(R.string.term_kind_shared) to Brand.Green
                session.persistent -> stringResource(R.string.term_kind_server) to ServerKindColor
                else -> stringResource(R.string.term_kind_local) to Brand.Blue
            }
            Pill(kind, kindColor, Modifier.padding(start = 8.dp))
            if (session is LocalTerminal && session.telnet) Pill("Telnet", Brand.Amber, Modifier.padding(start = 4.dp))
            if (sharedLocally != null) Pill(stringResource(R.string.term_kind_shared), Brand.Green, Modifier.padding(start = 4.dp))
            // The round trip to the host, next to the kind (terminals from this device).
            if (session is LocalTerminal) LatencyPill(session, state, Modifier.padding(start = 4.dp))
            Text(
                terminalStatus(session, state, live), Modifier.weight(1f).padding(horizontal = 10.dp),
                color = KeyFg.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (live.others.isNotEmpty() || live.pendingRequests > 0) ParticipantsChip(live, onParticipants)
            if (state is TermState.Closed && live.ended == null) {
                Button(onClick = onReconnect, Modifier.padding(horizontal = 4.dp).height(32.dp), contentPadding = PaddingValues(horizontal = 12.dp)) {
                    Icon(Icons.Outlined.Refresh, null, Modifier.size(16.dp))
                    if (labels) Text(stringResource(R.string.term_reconnect), Modifier.padding(start = 6.dp), fontSize = 13.sp)
                }
            }
            splitButton()
            cursorButton()
            if (canBrowse) ToolbarButton(Icons.Outlined.Folder, stringResource(R.string.term_files), labels, onClick = onFiles)
            ToolbarButton(Icons.Outlined.ContentCopy, stringResource(R.string.term_copy), labels && !splitShown, onClick = onCopy)
            ToolbarButton(Icons.Outlined.ContentPaste, stringResource(R.string.common_paste), labels && !splitShown, enabled = live.canWrite, onClick = onPaste)
            ToolbarButton(Icons.Outlined.AutoAwesome, stringResource(R.string.section_ai), labels, active = copilotOpen, onClick = onCopilot)
            if (sharable) ToolbarButton(Icons.Outlined.PersonAdd, stringResource(R.string.term_share), labels, onClick = onShare)
            IconButton(onClick = onSnippets) {
                Icon(Icons.Outlined.Code, stringResource(R.string.section_snippets), tint = KeyFg)
            }
            // The quick access panel, beside the terminal.
            if (live.canWrite) {
                IconButton(onClick = onPanel) {
                    Icon(Icons.Outlined.GridView, stringResource(R.string.quick_panel), tint = if (panelOpen) MaterialTheme.colorScheme.primary else KeyFg)
                }
            }
            // Without a hardware keyboard, the on-screen one.
            if (!hardwareKeyboard) {
                IconButton(onClick = onKeyboard) { Icon(Icons.Outlined.Keyboard, stringResource(R.string.term_keyboard), tint = KeyFg) }
            }
            Box {
                var menu by remember { mutableStateOf(false) }
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.common_more), tint = KeyFg) }
                DropdownMenu(menu, { menu = false }) { menuEntries { menu = false } }
            }
        }
    }
}

/**
 * The latency of a terminal from the phone, measured every few seconds
 * while it is on screen (the app in the foreground) and connected; `null`
 * while unknown.
 */
@Composable
private fun rememberLatency(session: LocalTerminal, state: TermState): Double? {
    var ms by remember(session) { mutableStateOf<Double?>(null) }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    val running = state == TermState.Running
    LaunchedEffect(session, running) {
        ms = null
        if (!running) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            while (true) {
                ms = session.latencyMs()
                kotlinx.coroutines.delay(Latency.INTERVAL_MS)
            }
        }
    }
    return ms
}

/** Color of a latency: gray when fine (or unknown), amber from 150 ms, red from 400 ms. */
private fun latencyColor(ms: Double?): Color = when (Latency.level(ms)) {
    Latency.Level.UNKNOWN, Latency.Level.GOOD -> KeyFg.copy(alpha = 0.6f)
    Latency.Level.FAIR -> Brand.Amber
    Latency.Level.POOR -> Brand.Red
}

/** "42 ms" in the phone's terminal bar. */
@Composable
private fun LatencyText(session: LocalTerminal, state: TermState) {
    val ms = rememberLatency(session, state)
    val description = latencyDescription(ms)
    Text(
        Latency.format(ms), Modifier.semantics { contentDescription = description },
        color = latencyColor(ms), style = MaterialTheme.typography.labelSmall, maxLines = 1,
    )
}

/** "42 ms" as a badge of the desktop toolbar. */
@Composable
private fun LatencyPill(session: LocalTerminal, state: TermState, modifier: Modifier = Modifier) {
    val ms = rememberLatency(session, state)
    val description = latencyDescription(ms)
    Pill(Latency.format(ms), latencyColor(ms).copy(alpha = 1f), modifier.semantics { contentDescription = description })
}

/** For TalkBack: "Latency: 42 ms". */
@Composable
private fun latencyDescription(ms: Double?): String = stringResource(R.string.term_latency, Latency.format(ms))

/** Server sessions in the toolbar (the desktop's "info" color). */
private val ServerKindColor = Color(0xFF2BA6B5)

/** A button of the desktop toolbar: its icon, and its name when [label] (otherwise as a tooltip for accessibility). */
@Composable
private fun ToolbarButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    label: Boolean,
    active: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val color = when {
        !enabled -> KeyFg.copy(alpha = 0.35f)
        active -> MaterialTheme.colorScheme.primary
        else -> KeyFg
    }
    if (!label) {
        IconButton(onClick = onClick, enabled = enabled) { Icon(icon, text, tint = color) }
        return
    }
    TextButton(
        onClick = onClick, enabled = enabled, contentPadding = PaddingValues(horizontal = 10.dp),
        modifier = Modifier.height(36.dp),
    ) {
        Icon(icon, null, Modifier.size(18.dp), tint = color)
        Text(text, Modifier.padding(start = 6.dp), color = color, fontSize = 13.sp, maxLines = 1)
    }
}

/**
 * One terminal: the emulator view, its copy/paste menu and what goes over it
 * (connecting, disconnected, waiting room, requests). [focused]: the one the
 * keyboard types into (in the split view, the others are only watched until
 * tapped).
 */
@Composable
private fun TerminalPane(
    app: TermoakApp,
    nav: NavHostController,
    session: TermSession,
    fontSize: Float,
    focused: Boolean,
    gestureMode: GestureMode,
    suggestionMode: SuggestionMode,
    font: TerminalFont,
    views: MutableList<TerminalView>,
    onFocus: () -> Unit,
    onPaste: (String) -> Unit,
    pasteClipboard: () -> Unit,
    copyScreen: () -> Unit,
    copySelection: (TerminalView?) -> Boolean,
    onParticipants: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = remember { context.getSystemService(ClipboardManager::class.java) }
    val vibrate by app.prefs.vibrateOnBell.collectAsState()
    val state by session.state.collectAsState()
    val pending by session.pending.collectAsState()
    val live by session.live.collectAsState()
    var longPressMenu by remember { mutableStateOf(false) }
    // Where the menu opens (long press or right click), and the view with a mouse selection.
    var menuAt by remember { mutableStateOf(IntOffset.Zero) }
    val view = remember { arrayOfNulls<TerminalView>(1) }
    var hasSelection by remember { mutableStateOf(false) }
    val host = remember(session.hostId) { session.hostId?.let { runCatching { app.core.getHost(it, session.accountId) }.getOrNull() } }
    val suggestions by session.suggestions.collectAsState()
    val awaitingEcho by session.awaitingEcho.collectAsState()
    // Next to the cursor: the rest dimmed and a list to pick from (only the focused pane).
    val nearCursor = suggestionMode == SuggestionMode.CURSOR && focused && live.canWrite
    // Connection steps (like Termius' connection screen).
    val steps = remember(session.id) { androidx.compose.runtime.mutableStateListOf<UiText>() }
    var everRan by remember(session.id) { mutableStateOf(false) }
    LaunchedEffect(session.id) {
        session.state.collect { st ->
            when (st) {
                is TermState.Connecting -> if (st != InitialConnecting && steps.lastOrNull() != st.message) steps.add(st.message)
                TermState.Running -> everRan = true
                is TermState.Closed, TermState.Asleep -> Unit
            }
        }
    }
    DisposableEffect(session, vibrate) {
        session.onCopy = { text -> clipboard?.setPrimaryClip(ClipData.newPlainText("terminal", text)) }
        session.onBell = {
            if (vibrate) context.getSystemService(Vibrator::class.java)
                ?.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
        }
        onDispose { session.onBell = {} }
    }

    Box(Modifier.fillMaxSize()) {
        // Clipped: the view fills its whole canvas with the terminal's background.
        ClippedAndroidView(
            factory = { ctx ->
                TerminalView(ctx).apply {
                    setPadding(12, 6, 12, 6)
                    onFontSizeChanged = { app.prefs.setFontSize(it) }
                    hardwareKeyboard = { app.keyboard.connected.value }
                    onShortcut = { KeyShortcuts.dispatch(it) }
                    onSelectionChanged = { hasSelection = it }
                    views += this
                    view[0] = this
                    if (focused) post { showKeyboard() }
                }
            },
            update = { v ->
                v.session = session
                v.setTypeface(font.typeface(v.context))
                v.setFontSize(fontSize)
                v.ghostText = if (nearCursor && !awaitingEcho) suggestions.firstOrNull()?.insert else null
                v.onTouched = onFocus
                v.onContextMenu = { x, y -> onFocus(); menuAt = IntOffset(x.toInt(), y.toInt()); longPressMenu = true }
                v.onPasteText = onPaste
                v.gestureMode = gestureMode
                // Watching only: no keyboard (nothing would reach the terminal), no cursor gestures.
                v.readOnly = !live.canWrite
            },
            onRelease = { views -= it },
            modifier = Modifier.fillMaxSize(),
        )
        // The menu where it was asked for (a zero-size anchor at that point).
        Box(Modifier.offset { menuAt }) {
            DropdownMenu(longPressMenu, { longPressMenu = false }) {
                if (hasSelection) {
                    DropdownMenuItem({ Text(stringResource(R.string.term_copy)) }, { longPressMenu = false; copySelection(view[0]) },
                        leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) })
                }
                DropdownMenuItem({ Text(stringResource(R.string.common_paste)) }, { longPressMenu = false; pasteClipboard() },
                    leadingIcon = { Icon(Icons.Outlined.ContentPaste, null) })
                DropdownMenuItem({ Text(stringResource(R.string.term_copy_screen)) }, { longPressMenu = false; copyScreen() },
                    leadingIcon = { Icon(Icons.Outlined.SelectAll, null) })
            }
        }
        if (nearCursor && suggestions.isNotEmpty() && state == TermState.Running) {
            view[0]?.let { v -> CursorSuggestionList(v, suggestions) { session.accept(it) } }
        }
        val waiting = live.waiting
        val ended = live.ended
        if (ended != null) {
            EndPanel(ended) { app.sessions.close(session.id) }
        } else if (waiting != null && session is ServerTerminal) {
            WaitingRoom(
                session, waiting,
                guestName = if (session.asGuest) app.prefs.guestName.orEmpty() else null,
                onRename = { name -> app.prefs.guestName = name; session.setName(name) },
                onLeave = { app.sessions.close(session.id) },
            )
        } else when (val s = state) {
            is TermState.Connecting -> ConnectingPanel(session, host, steps, error = null)
            is TermState.Closed -> if (!everRan) {
                ConnectingPanel(session, host, steps, error = s.message.asString(),
                    onRetry = { steps.clear(); session.reconnect() },
                    onEdit = session.hostId?.let { id -> { app.sessions.close(session.id); nav.navigate(Routes.hostEdit(id)) } },
                    onClose = { app.sessions.close(session.id) })
            } else {
                Surface(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp),
                    color = BarBg, shape = RoundedCornerShape(16.dp),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.term_disconnected), color = KeyFg, style = MaterialTheme.typography.titleSmall)
                        Text(s.message.asString(), color = KeyFg.copy(alpha = 0.75f), style = MaterialTheme.typography.bodyMedium)
                        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { steps.clear(); session.reconnect() }) {
                                Icon(Icons.Outlined.Refresh, null, Modifier.size(18.dp))
                                Text(stringResource(R.string.term_reconnect), Modifier.padding(start = 8.dp))
                            }
                            OutlinedButton(onClick = { app.sessions.close(session.id) }) {
                                Text(stringResource(R.string.common_close))
                            }
                        }
                    }
                }
            }
            TermState.Running, TermState.Asleep -> Unit
        }
        // Another pane asks something (host key, password): it is answered once focused.
        if (!focused && pending != null) {
            Surface(
                Modifier.align(Alignment.TopCenter).padding(8.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onFocus),
                color = Brand.Amber, contentColor = Color.Black,
            ) {
                Text(
                    stringResource(R.string.split_needs_answer), Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
        RequestBanners(session, live, Modifier.align(Alignment.TopCenter), onParticipants)
        // "With a button" and the button on: so you know what one finger does.
        val cursorByButton by session.cursorByButton.collectAsState()
        if (gestureMode == GestureMode.BUTTON && cursorByButton && live.canWrite) {
            Row(
                Modifier.align(Alignment.TopEnd).padding(8.dp).clip(RoundedCornerShape(50))
                    .background(BarBg.copy(alpha = 0.85f)).padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.TouchApp, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                Text(
                    stringResource(R.string.term_cursor_mode_badge), Modifier.padding(start = 6.dp),
                    color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

/**
 * Cursor gestures "With a button": one finger moves the cursor (on) or
 * scrolls (off), in this terminal.
 */
@Composable
private fun CursorToggleButton(session: TermSession) {
    val on by session.cursorByButton.collectAsState()
    IconToggleButton(on, { session.cursorByButton.value = it }) {
        Icon(
            if (on) Icons.Filled.TouchApp else Icons.Outlined.TouchApp, stringResource(R.string.term_move_cursor),
            tint = if (on) MaterialTheme.colorScheme.primary else KeyFg,
        )
    }
}

@Composable
private fun SessionTab(s: TermSession, selected: Boolean, inSplit: Boolean, onClick: () -> Unit, onClose: () -> Unit) {
    val state by s.state.collectAsState()
    val title by s.title.collectAsState()
    val dot = when (state) {
        TermState.Running -> Brand.Green
        is TermState.Connecting -> Brand.Amber
        is TermState.Closed -> Brand.Red
        TermState.Asleep -> KeyFg.copy(alpha = 0.3f)
    }
    Row(
        Modifier.height(32.dp).clip(RoundedCornerShape(8.dp))
            .background(if (selected) KeyBg else Color.Transparent)
            .clickable(onClick = onClick).padding(start = 10.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (s.persistent) Icon(Icons.Outlined.CloudQueue, null, Modifier.size(14.dp), tint = KeyFg.copy(alpha = 0.7f))
        // In the split view on screen.
        if (inSplit) Icon(Icons.Outlined.GridView, null, Modifier.padding(end = 4.dp).size(12.dp), tint = KeyFg.copy(alpha = 0.7f))
        StatusDot(dot, 7.dp)
        Text(
            title ?: s.label, Modifier.padding(start = 6.dp).widthIn(max = 140.dp),
            color = if (state == TermState.Asleep) KeyFg.copy(alpha = 0.6f) else KeyFg,
            fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Outlined.Close, stringResource(R.string.common_close), Modifier.size(14.dp), tint = KeyFg.copy(alpha = 0.7f))
        }
    }
}

/** Connection screen: the host, a progress bar and the steps (or the error). */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.ConnectingPanel(
    session: TermSession,
    host: com.termoak.ffi.SshHost?,
    steps: List<UiText>,
    error: String?,
    onRetry: () -> Unit = {},
    onEdit: (() -> Unit)? = null,
    onClose: () -> Unit = {},
) {
    Column(
        Modifier.align(Alignment.Center).fillMaxWidth().padding(24.dp)
            .background(BarBg, RoundedCornerShape(20.dp)).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HostTile(session.label, host?.os, host?.color, size = 64.dp, icon = host?.icon)
        Text(session.label, Modifier.padding(top = 14.dp), color = KeyFg, style = MaterialTheme.typography.titleLarge)
        Text(
            (host?.settings?.username?.let { "$it@" } ?: "") +
                (host?.address ?: if (session.persistent) stringResource(R.string.term_server_session_lower) else ""),
            color = KeyFg.copy(alpha = 0.6f), style = MaterialTheme.typography.bodyMedium,
        )
        if (error == null) {
            androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 20.dp).clip(RoundedCornerShape(50)))
        }
        Column(Modifier.fillMaxWidth().padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            steps.forEachIndexed { i, step ->
                val last = i == steps.lastIndex
                Row(verticalAlignment = Alignment.CenterVertically) {
                    when {
                        last && error == null -> CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                        else -> Icon(Icons.Outlined.Check, null, Modifier.size(14.dp), tint = Brand.Green)
                    }
                    Text(step.asString(), Modifier.padding(start = 10.dp), color = KeyFg.copy(alpha = if (last) 1f else 0.6f),
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            if (error != null) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Outlined.ErrorOutline, null, Modifier.size(16.dp), tint = Brand.Red)
                    Text(error, Modifier.padding(start = 10.dp), color = KeyFg, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        if (error != null) {
            Row(Modifier.padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onRetry) { Text(stringResource(R.string.term_retry)) }
                if (onEdit != null) OutlinedButton(onClick = onEdit) { Text(stringResource(R.string.host_edit_title)) }
                TextButton(onClick = onClose) { Text(stringResource(R.string.common_close)) }
            }
        }
    }
}

/** What connecting asks: trust the host's key, or a password / passphrase / answers. */
@Composable
internal fun PendingDialog(p: Pending) {
    when (p) {
        is Pending.HostKey -> AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.term_trust_title, p.host)) },
            text = {
                Column {
                    Text(stringResource(R.string.term_trust_text))
                    Surface(
                        Modifier.padding(top = 12.dp).fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = RoundedCornerShape(8.dp),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(p.keyType, style = MaterialTheme.typography.labelMedium)
                            Text(p.fingerprint, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { p.answer(true) }) { Text(stringResource(R.string.term_trust_connect)) } },
            dismissButton = { TextButton(onClick = { p.answer(false) }) { Text(stringResource(R.string.common_cancel)) } },
        )
        is Pending.Credentials -> CredentialsDialog(p)
    }
}

@Composable
private fun CredentialsDialog(p: Pending.Credentials) {
    val answers = remember(p) { p.fields.map { mutableStateOf("") } }
    AlertDialog(
        onDismissRequest = { p.answer(null) },
        title = { Text(p.title.asString()) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (p.instructions.isNotBlank()) Text(p.instructions)
                p.fields.forEachIndexed { i, field ->
                    OutlinedTextField(
                        answers[i].value, { answers[i].value = it },
                        label = { Text(field.text.trim().trimEnd(':').ifEmpty { stringResource(R.string.term_answer) }) },
                        singleLine = true,
                        visualTransformation = if (field.echo) VisualTransformation.None else PasswordVisualTransformation(),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { p.answer(answers.map { it.value }) }) { Text(stringResource(R.string.common_ok)) } },
        dismissButton = { TextButton(onClick = { p.answer(null) }) { Text(stringResource(R.string.common_cancel)) } },
    )
}

/** Where a snippet from the terminal goes. */
internal enum class SnippetTarget { THIS, PANES, ALL_OPEN }

/**
 * Vault snippets: pasted into the terminal (and optionally run), into all
 * the panes of the split view ([panes] > 0) or into every open terminal.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SnippetsSheet(
    app: TermoakApp,
    panes: Int,
    openCount: Int,
    onDismiss: () -> Unit,
    onUse: (Snippet, String, Boolean, SnippetTarget) -> Unit,
) {
    val snippets = remember { runCatching { app.core.listSnippets(app.accounts.filter()) }.getOrDefault(emptyList()).sortedBy { it.name.lowercase() } }
    var filling by remember { mutableStateOf<Pair<Snippet, Boolean>?>(null) }
    var target by remember { mutableStateOf(SnippetTarget.THIS) }
    var query by remember { mutableStateOf("") }
    fun use(sn: Snippet, run: Boolean) {
        if (snippetVariables(sn.script).isEmpty()) onUse(sn, sn.script, run, target) else filling = sn to run
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(stringResource(R.string.section_snippets), Modifier.padding(horizontal = 24.dp), style = MaterialTheme.typography.titleLarge)
        if (panes > 0 || openCount > 1) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(target == SnippetTarget.THIS, { target = SnippetTarget.THIS }, { Text(stringResource(R.string.multi_target_this)) })
                if (panes > 0) {
                    FilterChip(target == SnippetTarget.PANES, { target = SnippetTarget.PANES },
                        { Text(stringResource(R.string.multi_target_panes, panes)) })
                }
                if (openCount > 1) {
                    FilterChip(target == SnippetTarget.ALL_OPEN, { target = SnippetTarget.ALL_OPEN },
                        { Text(stringResource(R.string.multi_target_all_open, openCount)) })
                }
            }
        }
        if (snippets.isEmpty()) {
            Text(
                stringResource(R.string.snippets_none_yet),
                Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            OutlinedTextField(
                query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
                placeholder = { Text(stringResource(R.string.common_search)) }, singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
            )
        }
        // Folders by the first tag (like the quick panel), unless searching.
        val shown = SnippetFolders.filter(snippets, query)
        val folders = SnippetFolders.group(shown, "")
        val noFolder = stringResource(R.string.quick_panel_snippets_no_folder)
        LazyColumn(Modifier.padding(bottom = 24.dp)) {
            folders.forEach { (folder, list) ->
                if (query.isBlank() && folders.size > 1) {
                    item(key = "folder:$folder") {
                        Text(
                            folder.ifEmpty { noFolder }, Modifier.padding(start = 24.dp, top = 12.dp, bottom = 4.dp),
                            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                items(list, key = { "$folder/${it.uid}" }) { sn ->
                    ListItem(
                        headlineContent = { Text(sn.name) },
                        supportingContent = {
                            Text(sn.description.ifBlank { sn.script }, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                fontFamily = if (sn.description.isBlank()) FontFamily.Monospace else null)
                        },
                        trailingContent = {
                            Row {
                                TextButton(onClick = { use(sn, false) }) { Text(stringResource(R.string.common_paste)) }
                                Button(onClick = { use(sn, true) }) { Text(stringResource(R.string.snippets_run)) }
                            }
                        },
                    )
                }
            }
        }
    }
    filling?.let { (sn, run) ->
        SnippetVariablesDialog(
            sn, stringResource(if (run) R.string.snippets_run else R.string.common_paste), onDismiss = { filling = null },
        ) { text ->
            filling = null
            onUse(sn, text, run, target)
        }
    }
}

/** "Paste N lines?": each line runs as a command when it reaches the shell. */
@Composable
private fun PasteConfirmDialog(text: String, onDismiss: () -> Unit, onPaste: (dontAskAgain: Boolean) -> Unit) {
    val lines = remember(text) { Paste.lineCount(text) }
    val preview = remember(text) { Paste.preview(text) }
    var dontAsk by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.ContentPaste, null) },
        title = { Text(pluralStringResource(R.plurals.paste_confirm_title, lines, lines)) },
        text = {
            Column {
                Text(stringResource(R.string.paste_confirm_text))
                Surface(
                    Modifier.padding(top = 12.dp).fillMaxWidth().heightIn(max = 220.dp),
                    color = TermBg, contentColor = KeyFg, shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        preview, Modifier.verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState()).padding(10.dp),
                        fontFamily = FontFamily.Monospace, fontSize = 12.sp, softWrap = false,
                    )
                }
                Row(
                    Modifier.padding(top = 8.dp).clip(RoundedCornerShape(8.dp)).clickable { dontAsk = !dontAsk }.padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(dontAsk, { dontAsk = it })
                    Text(stringResource(R.string.paste_confirm_dont_ask))
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPaste(dontAsk) }) { Text(stringResource(R.string.common_paste)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}
