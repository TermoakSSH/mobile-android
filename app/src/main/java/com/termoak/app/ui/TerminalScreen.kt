package com.termoak.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Keyboard
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavHostController
import com.termoak.app.R
import com.termoak.app.TermoakApp
import com.termoak.app.UiText
import com.termoak.app.asString
import com.termoak.app.term.InitialConnecting
import com.termoak.app.term.Pending
import com.termoak.app.term.ServerTerminal
import com.termoak.app.term.TermSession
import com.termoak.app.term.TermState
import com.termoak.app.term.TerminalView
import com.termoak.ffi.Snippet
import com.termoak.ffi.TerminalKey
import com.termoak.ffi.renderSnippet
import com.termoak.ffi.snippetVariables
import kotlinx.coroutines.delay

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
    val fontSize by app.prefs.fontSize.collectAsState()
    val keepOn by app.prefs.keepScreenOn.collectAsState()
    val vibrate by app.prefs.vibrateOnBell.collectAsState()
    val session = sessions.firstOrNull { it.id == activeId } ?: sessions.lastOrNull()
    val clipboard = remember { context.getSystemService(ClipboardManager::class.java) }
    var view by remember { mutableStateOf<TerminalView?>(null) }
    var showSnippets by remember { mutableStateOf(false) }
    var longPressMenu by remember { mutableStateOf(false) }
    var terminating by remember { mutableStateOf<ServerTerminal?>(null) }
    // Copilot: side by side on tablets; on top, from the right, on phones.
    var copilotOpen by rememberSaveable { mutableStateOf(false) }
    val wide = LocalConfiguration.current.screenWidthDp >= 600

    LaunchedEffect(sessions.isEmpty()) { if (sessions.isEmpty()) nav.popBackStack() }
    if (session == null) return
    // A sleeping tab attaches when opened.
    LaunchedEffect(session.id) { app.sessions.wake(session.id) }

    val state by session.state.collectAsState()
    val pending by session.pending.collectAsState()
    val title by session.title.collectAsState()
    val live by session.live.collectAsState()
    val loggedIn by app.account.loggedIn.collectAsState()
    val snackbar = LocalSnackbar.current
    val resources = LocalResources.current
    var showShare by remember { mutableStateOf(false) }
    var showParticipants by remember { mutableStateOf(false) }
    val sharable = canShare(session, loggedIn == true, live.isOwner)
    // Notices of this terminal ("You have the keyboard", an action that wasn't allowed...).
    LaunchedEffect(session.id) { session.toasts.collect { snackbar.showSnackbar(it.resolve(resources)) } }
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
    val host = remember(session.hostId) { session.hostId?.let { runCatching { app.core.getHost(it) }.getOrNull() } }

    val hostView = LocalView.current
    DisposableEffect(keepOn) {
        hostView.keepScreenOn = keepOn
        onDispose { hostView.keepScreenOn = false }
    }
    DisposableEffect(session, vibrate) {
        session.onCopy = { text -> clipboard?.setPrimaryClip(ClipData.newPlainText("terminal", text)) }
        session.onBell = {
            if (vibrate) context.getSystemService(Vibrator::class.java)
                ?.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
        }
        onDispose { session.onBell = {} }
    }
    // When the panel closes, the AI stops and loses access to the terminal.
    fun closeCopilot() {
        copilotOpen = false
        app.copilot.stop(session)
    }
    BackHandler { if (copilotOpen) closeCopilot() else nav.popBackStack() }

    fun copyScreen() {
        clipboard?.setPrimaryClip(ClipData.newPlainText("terminal", session.screen.screenText()))
    }
    fun paste() {
        clipboard?.primaryClip?.getItemAt(0)?.coerceToText(context)?.let { session.paste(it.toString()) }
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
                    IconButton(onClick = {
                        if (copilotOpen) {
                            closeCopilot()
                        } else {
                            copilotOpen = true
                            if (!wide) view?.hideKeyboard()
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
                    IconButton(onClick = { view?.showKeyboard() }) {
                        Icon(Icons.Outlined.Keyboard, stringResource(R.string.term_keyboard), tint = KeyFg)
                    }
                    Box {
                        var menu by remember { mutableStateOf(false) }
                        IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.common_more), tint = KeyFg) }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem({ Text(stringResource(R.string.common_paste)) }, { menu = false; paste() },
                                leadingIcon = { Icon(Icons.Outlined.ContentPaste, null) })
                            DropdownMenuItem({ Text(stringResource(R.string.term_copy_screen)) }, { menu = false; copyScreen() },
                                leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) })
                            DropdownMenuItem({ Text(stringResource(R.string.term_font_bigger)) }, { app.prefs.setFontSize(fontSize + 1) },
                                leadingIcon = { Icon(Icons.Outlined.TextIncrease, null) })
                            DropdownMenuItem({ Text(stringResource(R.string.term_font_smaller)) }, { app.prefs.setFontSize(fontSize - 1) },
                                leadingIcon = { Icon(Icons.Outlined.TextDecrease, null) })
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
                    sessions.forEach { s -> SessionTab(s, s.id == session.id, { app.sessions.select(s.id) }, { app.sessions.close(s.id) }) }
                    Box(
                        Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).clickable { nav.goTab(Routes.HOSTS) },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Outlined.Add, stringResource(R.string.term_open_another), Modifier.size(18.dp), tint = KeyFg) }
                }

                // ----- Terminal -----
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    AndroidView(
                        factory = { ctx ->
                            TerminalView(ctx).apply {
                                setPadding(12, 6, 12, 6)
                                onFontSizeChanged = { app.prefs.setFontSize(it) }
                                onLongPress = { longPressMenu = true }
                                view = this
                                post { showKeyboard() }
                            }
                        },
                        update = { v ->
                            v.session = session
                            v.setFontSize(fontSize)
                            // Watching only: no keyboard (nothing would reach the terminal).
                            v.readOnly = !live.canWrite
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                    DropdownMenu(longPressMenu, { longPressMenu = false }) {
                        DropdownMenuItem({ Text(stringResource(R.string.common_paste)) }, { longPressMenu = false; paste() },
                            leadingIcon = { Icon(Icons.Outlined.ContentPaste, null) })
                        DropdownMenuItem({ Text(stringResource(R.string.term_copy_screen)) }, { longPressMenu = false; copyScreen() },
                            leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) })
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
                    RequestBanners(session, live, Modifier.align(Alignment.TopCenter)) { showParticipants = true }
                }
                KeyboardStrip(session, live)
                if (live.canWrite) ExtraKeys(session)
            }
            if (wide && copilotOpen) {
                VerticalDivider(color = KeyBg)
                CopilotPanel(
                    app, session, host, onClose = { closeCopilot() }, onLogin = { nav.navigate(Routes.LOGIN) },
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
                    app, session, host, onClose = { closeCopilot() }, onLogin = { nav.navigate(Routes.LOGIN) },
                    onAiSettings = { nav.navigate(Routes.AI_KEYS) },
                    modifier = Modifier.fillMaxSize(), swipeToClose = true,
                )
            }
        }
    }

    // ----- Dialogs -----
    when (val p = pending) {
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
        null -> Unit
    }
    if (showSnippets) {
        SnippetsSheet(app, onDismiss = { showSnippets = false }) { text, run ->
            session.paste(text)
            if (run) session.key(TerminalKey.Enter)
            showSnippets = false
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

@Composable
private fun SessionTab(s: TermSession, selected: Boolean, onClick: () -> Unit, onClose: () -> Unit) {
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

/** Vault snippets: pasted into the terminal (and optionally run). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SnippetsSheet(app: TermoakApp, onDismiss: () -> Unit, onUse: (String, Boolean) -> Unit) {
    val snippets = remember { runCatching { app.core.listSnippets() }.getOrDefault(emptyList()).sortedBy { it.name.lowercase() } }
    var filling by remember { mutableStateOf<Snippet?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(stringResource(R.string.section_snippets), Modifier.padding(horizontal = 24.dp), style = MaterialTheme.typography.titleLarge)
        if (snippets.isEmpty()) {
            Text(
                stringResource(R.string.snippets_none_yet),
                Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LazyColumn(Modifier.padding(bottom = 24.dp)) {
            items(snippets, key = { it.id }) { sn ->
                ListItem(
                    headlineContent = { Text(sn.name) },
                    supportingContent = {
                        Text(sn.description.ifBlank { sn.script }, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            fontFamily = if (sn.description.isBlank()) FontFamily.Monospace else null)
                    },
                    trailingContent = {
                        Row {
                            TextButton(onClick = { use(sn, false, onUse) { filling = it } }) { Text(stringResource(R.string.common_paste)) }
                            Button(onClick = { use(sn, true, onUse) { filling = it } }) { Text(stringResource(R.string.snippets_run)) }
                        }
                    },
                )
            }
        }
    }
    filling?.let { sn -> SnippetVariablesDialog(sn, onDismiss = { filling = null }) { text -> onUse(text, false) } }
}

private fun use(sn: Snippet, run: Boolean, onUse: (String, Boolean) -> Unit, needsVars: (Snippet) -> Unit) {
    if (snippetVariables(sn.script).isEmpty()) onUse(sn.script, run) else needsVars(sn)
}

@Composable
private fun SnippetVariablesDialog(sn: Snippet, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    val names = remember(sn) { snippetVariables(sn.script) }
    val values = remember(sn) { names.map { mutableStateOf("") } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(sn.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                names.forEachIndexed { i, n ->
                    OutlinedTextField(values[i].value, { values[i].value = it }, label = { Text(n) }, singleLine = true)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val rendered = runCatching {
                    renderSnippet(sn.script, names.zip(values.map { it.value }).toMap())
                }.getOrDefault(sn.script)
                onDone(rendered)
            }) { Text(stringResource(R.string.common_paste)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}
