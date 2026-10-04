package com.termoak.app.ui

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.AddComment
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.termoak.app.R
import com.termoak.app.TermoakApp
import com.termoak.app.asString
import com.termoak.app.data.CopilotChat
import com.termoak.app.data.Turn
import com.termoak.app.term.LocalTerminal
import com.termoak.app.term.TermSession
import com.termoak.ffi.AiTaskStatus
import com.termoak.ffi.SshHost
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private val Suggestions = listOf(
    R.string.copilot_suggestion_error,
    R.string.copilot_suggestion_resources,
    R.string.copilot_suggestion_slow,
)

/**
 * Copilot: chat with the server's AI bound to terminal [session]. One
 * conversation per tab ([TermoakApp.copilot]). With [swipeToClose] it closes
 * by swiping it to the right (on phones, where it goes on top).
 */
@Composable
fun CopilotPanel(
    app: TermoakApp,
    session: TermSession,
    host: SshHost?,
    onClose: () -> Unit,
    onLogin: () -> Unit,
    onAiSettings: () -> Unit,
    modifier: Modifier = Modifier,
    swipeToClose: Boolean = false,
) {
    val loggedIn by app.account.loggedIn.collectAsState()
    val online by app.account.online.collectAsState()
    val chat = remember(session.id) { app.copilot.chat(session.id) }
    val hostLabel = host?.label ?: session.label.takeIf { session.hostId != null }

    // Swipe right to close.
    var drag by remember { mutableFloatStateOf(0f) }
    val closeAt = with(LocalDensity.current) { 96.dp.toPx() }
    val dragModifier = if (swipeToClose) {
        Modifier
            .offset { IntOffset(drag.roundToInt(), 0) }
            .draggable(
                rememberDraggableState { d -> drag = (drag + d).coerceAtLeast(0f) },
                Orientation.Horizontal,
                onDragStopped = { if (drag > closeAt) onClose(); drag = 0f },
            )
    } else {
        Modifier
    }

    // Without live events (or while they arrive), it refreshes by itself only while working.
    LaunchedEffect(chat, chat.active, online) {
        while (chat.active) {
            delay(if (online) 20_000 else 4_000)
            chat.reload()
        }
    }

    Surface(
        modifier.fillMaxHeight().then(dragModifier),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 8.dp,
    ) {
        Column(Modifier.fillMaxSize()) {
            CopilotHeader(session, hostLabel, chat, onClose)
            HorizontalDivider()
            if (loggedIn != true) {
                EmptyState(
                    Icons.Outlined.CloudOff, stringResource(R.string.copilot_signed_out_title),
                    stringResource(R.string.copilot_signed_out_text),
                    Modifier.weight(1f), action = stringResource(R.string.common_sign_in), onAction = onLogin,
                )
                return@Column
            }
            CopilotConversation(chat, onAiSettings, Modifier.weight(1f))
            if (chat.active) {
                // Well in sight: to rein the AI in.
                FilledTonalButton(
                    onClick = { app.copilot.stop(session) },
                    Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 6.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) {
                    Icon(Icons.Filled.Stop, null, Modifier.size(20.dp))
                    Text(stringResource(R.string.copilot_stop), Modifier.padding(start = 8.dp))
                }
            }
            CopilotInput(chat) { text -> app.copilot.send(session, hostLabel, text) }
        }
    }
}

@Composable
private fun CopilotHeader(session: TermSession, hostLabel: String?, chat: CopilotChat, onClose: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Text(
                stringResource(R.string.copilot_title), Modifier.padding(start = 8.dp).weight(1f),
                style = MaterialTheme.typography.titleMedium,
            )
            IconButton(onClick = { chat.reset() }, enabled = !chat.empty) {
                Icon(Icons.Outlined.AddComment, stringResource(R.string.copilot_new_conversation))
            }
            IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, stringResource(R.string.common_close)) }
        }
        // Which terminal it is about.
        val (icon, text) = when {
            session.persistent -> Icons.Outlined.CloudQueue to (hostLabel ?: session.label)
            hostLabel != null -> Icons.Outlined.Dns to hostLabel
            else -> Icons.Outlined.Terminal to stringResource(R.string.copilot_this_terminal)
        }
        Row(Modifier.padding(top = 2.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(50),
            ) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text, Modifier.padding(start = 6.dp), style = MaterialTheme.typography.labelMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            }
            ModeMenu(chat)
        }
        // Whether the AI can type in this terminal.
        val shared = (session as? LocalTerminal)?.sharedId?.collectAsState()?.value
        val note = when {
            session.persistent -> R.string.copilot_terminal_access
            session is LocalTerminal -> when {
                chat.shareFailed -> R.string.copilot_no_access
                shared != null -> R.string.copilot_terminal_access
                // Not shared (yet, or it stopped when the AI was stopped): it is shared when asking.
                else -> R.string.copilot_will_share
            }
            else -> R.string.copilot_no_access
        }
        Text(
            stringResource(note), Modifier.padding(top = 6.dp, end = 12.dp),
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The conversation's permissions, at hand in the header (also mid-task). */
@Composable
private fun ModeMenu(chat: CopilotChat) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
            Icon(Icons.Outlined.Shield, null, Modifier.size(16.dp))
            Text(
                stringResource(permissionLabel(chat.mode)), Modifier.padding(start = 4.dp),
                style = MaterialTheme.typography.labelMedium, maxLines = 1,
            )
            Icon(Icons.Filled.ArrowDropDown, stringResource(R.string.common_permissions), Modifier.size(18.dp))
        }
        DropdownMenu(open, { open = false }) {
            PermissionModes.forEach { m ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(stringResource(permissionLabel(m)))
                            Text(
                                stringResource(permissionHint(m)), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    onClick = { open = false; if (m != chat.mode) chat.changeMode(m) },
                    leadingIcon = {
                        if (m == chat.mode) Icon(Icons.Filled.Check, null) else Spacer(Modifier.size(24.dp))
                    },
                )
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun CopilotConversation(chat: CopilotChat, onAiSettings: () -> Unit, modifier: Modifier) {
    val list = rememberLazyListState()
    if (chat.empty) {
        Column(modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.copilot_empty_title), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(R.string.copilot_empty_text),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Suggestions.forEach { id ->
                    val s = stringResource(id)
                    SuggestionChip(onClick = { chat.draft = s }, label = { Text(s) })
                }
            }
            Text(stringResource(R.string.common_permissions), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.labelLarge)
            PermissionModeSelector(chat.mode, { chat.changeMode(it) })
            Text(
                stringResource(permissionHint(chat.mode)),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ChatError(chat, onAiSettings)
        }
        return
    }

    val running = chat.active
    val working = (chat.status == AiTaskStatus.QUEUED || chat.status == AiTaskStatus.RUNNING || chat.sending != null) &&
        chat.liveText.isEmpty()
    // To the end when something new arrives (also the text being written).
    val tail = listOf(
        chat.turns.size, chat.liveText.length, chat.liveReasoning.length, chat.liveTools.size,
        chat.liveTools.count { it.output != null }, chat.approvals.size, chat.notices.size, working, chat.sending, chat.error,
    )
    LaunchedEffect(tail) {
        val n = list.layoutInfo.totalItemsCount
        if (n > 0) {
            list.scrollToItem(n - 1)
            list.scrollBy(100_000f)
        }
    }

    LazyColumn(modifier.fillMaxWidth(), state = list, contentPadding = PaddingValues(vertical = 8.dp)) {
        items(chat.turns.size) { i ->
            val t = chat.turns[i]
            // Output that arrived live and isn't stored yet.
            val shown = if (t is Turn.Tool && t.output == null) {
                chat.liveOutputs[t.id]?.let { t.copy(output = it.output, error = !it.ok) } ?: t
            } else {
                t
            }
            TurnView(shown, running = running)
        }
        chat.sending?.let { item { TurnView(Turn.User(it)) } }
        if (chat.liveReasoning.isNotBlank()) item { ReasoningText(chat.liveReasoning.trim()) }
        if (chat.liveText.isNotBlank()) item { TurnView(Turn.Assistant(chat.liveText.trim())) }
        items(chat.liveTools, key = { "lt" + it.callId }) { t ->
            ToolRow(t.tool, t.summary, t.output, error = !t.ok, running = t.output == null && running)
        }
        items(chat.notices) { n ->
            Text(
                n, Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall, color = Brand.Amber,
            )
        }
        items(chat.approvals, key = { "ap" + it.id }) { a ->
            ApprovalCard(
                stringResource(R.string.ai_asks_permission), a.summary, a.command,
                onDecide = { approve, always -> chat.decide(a.id, approve, always) },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
        if (working) {
            item {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                    Text(
                        stringResource(R.string.copilot_working), Modifier.padding(start = 8.dp),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (chat.error != null) item { Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { ChatError(chat, onAiSettings) } }
    }
}

/** The conversation's error; a missing API key or spent credit offers Settings → AI. */
@Composable
private fun ChatError(chat: CopilotChat, onAiSettings: () -> Unit) {
    val error = chat.error ?: return
    Column {
        ErrorLine(error.asString())
        if (chat.needsAiSetup) {
            OutlinedButton(onClick = onAiSettings, Modifier.padding(start = 20.dp, top = 8.dp)) {
                Icon(Icons.Outlined.Key, null, Modifier.size(18.dp))
                Text(stringResource(R.string.ai_keys_open), Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun ErrorLine(text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(Icons.Outlined.ErrorOutline, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
        Text(text, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun CopilotInput(chat: CopilotChat, onSend: (String) -> Unit) {
    fun send() {
        val text = chat.draft.trim()
        if (text.isEmpty() || chat.sending != null) return
        chat.draft = ""
        onSend(text)
    }
    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            chat.draft, { chat.draft = it }, Modifier.weight(1f),
            placeholder = {
                Text(stringResource(if (chat.empty) R.string.copilot_input_placeholder else R.string.ai_reply_placeholder))
            },
            maxLines = 5,
            shape = RoundedCornerShape(24.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { send() }),
        )
        IconButton(onClick = { send() }, enabled = chat.draft.isNotBlank() && chat.sending == null) {
            Icon(Icons.AutoMirrored.Filled.Send, stringResource(R.string.common_send), tint = MaterialTheme.colorScheme.primary)
        }
    }
}
