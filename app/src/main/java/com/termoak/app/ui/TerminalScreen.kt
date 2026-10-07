package com.termoak.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.outlined.CellTower
import androidx.compose.material.icons.outlined.CloseFullscreen
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LocalContentColor
import com.termoak.app.term.Paste
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.KeyboardCommandKey
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.TextDecrease
import androidx.compose.material.icons.outlined.TextIncrease
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavHostController
import com.termoak.app.R
import com.termoak.app.data.uid
import com.termoak.app.TermoakApp
import com.termoak.app.UiText
import com.termoak.app.asString
import com.termoak.app.data.Prefs
import com.termoak.app.term.InitialConnecting
import com.termoak.app.term.KeyStroke
import com.termoak.app.term.Shortcut
import com.termoak.app.term.SpecialKey
import com.termoak.app.term.LocalTerminal
import com.termoak.app.term.Pending
import com.termoak.app.term.ServerTerminal
import com.termoak.app.term.TermSession
import com.termoak.app.term.TermState
import com.termoak.app.term.TerminalView
import com.termoak.ffi.Snippet
import com.termoak.ffi.TerminalKey
import com.termoak.ffi.snippetVariables
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val TermBg = Color(0xFF12151D)
private val BarBg = Color(0xFF1A1F2B)
private val KeyBg = Color(0xFF252C3B)
private val KeyFg = Color(0xFFD6DBE4)
internal val TermBarBg = BarBg
internal val TermKeyBg = KeyBg
internal val TermKeyFg = KeyFg

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    // Window size class (it changes when a foldable folds or unfolds; the sessions stay).
    val maxPanes = rememberMaxPanes()
    val wide = maxPanes >= 2

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
            Shortcut.NEW_TAB -> nav.goTab(Routes.HOSTS)
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
            views = views,
            onFocus = { if (s.id != app.sessions.active.value) app.sessions.select(s.id) },
            onPaste = { requestPaste(s, it) },
            pasteClipboard = { paste(s) },
            copyScreen = { copyScreen(s) },
            copySelection = { v -> copySelection(v) },
            onParticipants = { showParticipants = true },
        )
    }

    // The terminal makes room for its own keyboard (fewer rows). On a phone the
    // copilot's keyboard goes over it instead: the terminal keeps its size (no
    // resize sent to the server, nothing reflowed) and only the copilot moves
    // up; also while that keyboard goes away after closing the copilot, so the
    // terminal isn't resized twice.
    val imeVisible = WindowInsets.isImeVisible
    var copilotIme by remember { mutableStateOf(false) }
    LaunchedEffect(imeVisible, copilotOpen, wide) {
        when {
            imeVisible && copilotOpen && !wide -> copilotIme = true
            !imeVisible -> copilotIme = false
            !copilotOpen -> { delay(600); copilotIme = false }
        }
    }
    val imeOverTerminal = !wide && (copilotOpen || copilotIme)

    Box(Modifier.fillMaxSize().background(TermBg).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxSize().then(if (imeOverTerminal) Modifier else Modifier.imePadding())) {
            Column(Modifier.weight(1f).fillMaxHeight()) {
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
                        Text(
                            when (val s = state) {
                                is TermState.Connecting -> if (live.waiting != null) stringResource(R.string.share_waiting_short)
                                else s.message.asString()
                                TermState.Running -> when {
                                    !live.isOwner && live.canWrite -> stringResource(R.string.share_you_have_keyboard)
                                    live.driverLabel() != null -> stringResource(R.string.share_is_typing, live.driverLabel()!!)
                                    !live.isOwner -> stringResource(R.string.share_view_only)
                                    live.others.isNotEmpty() -> pluralStringResource(
                                        R.plurals.share_watching, live.others.size, live.others.size,
                                    )
                                    else -> stringResource(
                                        if (session.persistent) R.string.term_server_session else R.string.term_ssh_from_phone,
                                    )
                                }
                                is TermState.Closed -> stringResource(R.string.term_disconnected)
                                TermState.Asleep -> stringResource(R.string.term_not_connected)
                            },
                            color = KeyFg.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall, maxLines = 1,
                        )
                    }
                    if (live.others.isNotEmpty() || live.pendingRequests > 0) {
                        ParticipantsChip(live) { showParticipants = true }
                    }
                    // Split view (tablets, unfolded foldables); a maximized pane goes back to the grid.
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
                    IconButton(onClick = {
                        if (copilotOpen) {
                            closeCopilot()
                        } else {
                            copilotOpen = true
                            if (!wide) focusedView()?.hideKeyboard()
                        }
                    }) {
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
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem({ Text(stringResource(R.string.common_paste)) }, { menu = false; paste(session) },
                                leadingIcon = { Icon(Icons.Outlined.ContentPaste, null) })
                            DropdownMenuItem({ Text(stringResource(R.string.term_copy_screen)) }, { menu = false; copyScreen(session) },
                                leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) })
                            DropdownMenuItem({ Text(stringResource(R.string.term_font_bigger)) }, { app.prefs.setFontSize(fontSize + 1) },
                                leadingIcon = { Icon(Icons.Outlined.TextIncrease, null) })
                            DropdownMenuItem({ Text(stringResource(R.string.term_font_smaller)) }, { app.prefs.setFontSize(fontSize - 1) },
                                leadingIcon = { Icon(Icons.Outlined.TextDecrease, null) })
                            DropdownMenuItem({ Text(stringResource(R.string.kb_shortcuts)) }, { menu = false; KeyShortcuts.sheet.value = true },
                                leadingIcon = { Icon(Icons.Outlined.KeyboardCommandKey, null) })
                            if (splitShown) {
                                DropdownMenuItem(
                                    { Text(stringResource(if (split.broadcast) R.string.split_broadcast_stop else R.string.split_broadcast)) },
                                    { menu = false; app.sessions.setBroadcast(!split.broadcast) },
                                    leadingIcon = { Icon(Icons.Outlined.CellTower, null, tint = if (split.broadcast) Brand.Amber else LocalContentColor.current) },
                                )
                            }
                            // Files over this terminal's connection (or through the server for a server session).
                            val canBrowse = when (session) {
                                is LocalTerminal -> state == TermState.Running
                                is ServerTerminal -> live.isOwner && session.hostId != null
                                else -> false
                            }
                            if (canBrowse) {
                                DropdownMenuItem({ Text(stringResource(R.string.files_sftp)) }, {
                                    menu = false
                                    filesSourceOf(session, title ?: session.label)?.let { nav.openFiles(it) }
                                }, leadingIcon = { Icon(Icons.Outlined.Folder, null) })
                            }
                            HorizontalDivider()
                            if (sharable) {
                                DropdownMenuItem({ Text(stringResource(R.string.share_action)) }, { menu = false; showShare = true },
                                    leadingIcon = { Icon(Icons.Outlined.PersonAdd, null) })
                            }
                            if (live.participants.size > 1 || !live.isOwner) {
                                DropdownMenuItem({ Text(stringResource(R.string.share_participants)) }, { menu = false; showParticipants = true },
                                    leadingIcon = { Icon(Icons.Outlined.Group, null) })
                            }
                            if (live.ended == null) {
                                DropdownMenuItem({ Text(stringResource(R.string.term_reconnect)) }, { menu = false; session.reconnect() },
                                    leadingIcon = { Icon(Icons.Outlined.Refresh, null) })
                            }
                            if (session is ServerTerminal && live.isOwner) {
                                DropdownMenuItem(
                                    { Text(stringResource(R.string.term_terminate_server_session), color = MaterialTheme.colorScheme.error) },
                                    { menu = false; terminating = session },
                                    leadingIcon = { Icon(Icons.Outlined.PowerSettingsNew, null, tint = MaterialTheme.colorScheme.error) },
                                )
                            }
                            DropdownMenuItem(
                                { Text(stringResource(if (session.persistent && live.isOwner) R.string.term_close_tab_keep else R.string.common_close)) },
                                { menu = false; app.sessions.close(session.id) },
                                leadingIcon = { Icon(Icons.Outlined.Close, null) },
                            )
                        }
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

                // ----- Broadcast banner (orange, like the desktop's) -----
                if (broadcasting) BroadcastBanner(panes.size) { app.sessions.setBroadcast(false) }

                // ----- Terminal(s) -----
                Box(Modifier.weight(1f).fillMaxWidth()) {
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
                }
                KeyboardStrip(session, live)
                // With a hardware keyboard the key bar goes away (unless the setting keeps it).
                if (live.canWrite && (!hardwareKeyboard || keepKeyBar)) ExtraKeys(session)
            }
            if (wide && copilotOpen) {
                VerticalDivider(color = KeyBg)
                CopilotPanel(
                    app, session, host, onClose = { closeCopilot() }, onLogin = { nav.navigate(Routes.login()) },
                    onAiSettings = { nav.navigate(Routes.AI_KEYS) },
                    modifier = Modifier.width(380.dp),
                )
            }
        }
        if (!wide) {
            AnimatedVisibility(copilotOpen, enter = fadeIn(), exit = fadeOut()) {
                Box(
                    Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f))
                        .clickable(remember { MutableInteractionSource() }, indication = null) { closeCopilot() },
                )
            }
            AnimatedVisibility(
                copilotOpen,
                // Above the keyboard, so its box to write in is never under it.
                Modifier.align(Alignment.CenterEnd).fillMaxWidth(0.85f).fillMaxHeight().imePadding(),
                enter = slideInHorizontally { it },
                exit = slideOutHorizontally { it },
            ) {
                CopilotPanel(
                    app, session, host, onClose = { closeCopilot() }, onLogin = { nav.navigate(Routes.login()) },
                    onAiSettings = { nav.navigate(Routes.AI_KEYS) },
                    modifier = Modifier.fillMaxSize(), swipeToClose = true,
                )
            }
        }
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
            when (target) {
                SnippetTarget.THIS -> {
                    // Typed here (and, while broadcasting, in the other panes too).
                    session.paste(text)
                    if (run) session.key(TerminalKey.Enter)
                }
                SnippetTarget.PANES -> app.snippetRuns.onSessions(sn.name, text, run, panes.map { it.id })
                SnippetTarget.ALL_OPEN -> app.snippetRuns.onSessions(sn.name, text, run, sessions.map { it.id })
            }
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
        AndroidView(
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
                v.setFontSize(fontSize)
                v.onTouched = onFocus
                v.onContextMenu = { x, y -> onFocus(); menuAt = IntOffset(x.toInt(), y.toInt()); longPressMenu = true }
                v.onPasteText = onPaste
                // Watching only: no keyboard (nothing would reach the terminal).
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

/** Keys missing from the phone keyboard, in a scrollable row (like Termius). Ctrl and Alt apply to the next key. */
@Composable
private fun ExtraKeys(session: TermSession) {
    val ctrl by session.ctrl.collectAsState()
    val alt by session.alt.collectAsState()
    Row(
        Modifier.fillMaxWidth().background(BarBg).horizontalScroll(rememberScrollState())
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val w = Modifier.widthIn(min = 44.dp)
        Key("esc", w) { session.key(TerminalKey.Escape) }
        Key("tab", w) { session.key(TerminalKey.Tab) }
        Key("ctrl", w, active = ctrl) { session.ctrl.value = !ctrl }
        Key("alt", w, active = alt) { session.alt.value = !alt }
        Key("←", w) { session.key(TerminalKey.Left) }
        Key("↑", w) { session.key(TerminalKey.Up) }
        Key("↓", w) { session.key(TerminalKey.Down) }
        Key("→", w) { session.key(TerminalKey.Right) }
        for (c in listOf("/", "-", "|", "~", "*", "&", ";", ":", "$", ">", "<", "'", "\"")) Key(c, w) { session.text(c) }
        Key("home", w, small = true) { session.key(TerminalKey.Home) }
        Key("end", w, small = true) { session.key(TerminalKey.End) }
        Key("pgup", w, small = true) { session.key(TerminalKey.PageUp) }
        Key("pgdn", w, small = true) { session.key(TerminalKey.PageDown) }
        for (n in 1..12) Key("F$n", w, small = true) { session.key(TerminalKey.Function(n.toUByte())) }
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
        HostTile(session.label, host?.os, host?.color, size = 64.dp)
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

@Composable
private fun Key(label: String, modifier: Modifier, active: Boolean = false, small: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier.height(40.dp).clip(RoundedCornerShape(8.dp))
            .background(if (active) MaterialTheme.colorScheme.primary else KeyBg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label, color = if (active) Color.White else KeyFg,
            fontFamily = FontFamily.Monospace, fontSize = if (small) 12.sp else 15.sp,
        )
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
private enum class SnippetTarget { THIS, PANES, ALL_OPEN }

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
        }
        LazyColumn(Modifier.padding(bottom = 24.dp)) {
            items(snippets, key = { it.uid }) { sn ->
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
