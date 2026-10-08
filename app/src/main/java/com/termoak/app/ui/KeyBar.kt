package com.termoak.app.ui

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.automirrored.outlined.KeyboardReturn
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.termoak.app.R
import com.termoak.app.term.BarAction
import com.termoak.app.term.BarKey
import com.termoak.app.term.KeyboardLayout
import com.termoak.app.term.SuggestionMode
import com.termoak.app.term.TermSession
import com.termoak.app.term.TerminalView
import com.termoak.ffi.CommandSuggestion
import com.termoak.ffi.SuggestionSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Material picture of a key's icon (the iOS app's symbol names, as stored in the layout). */
internal fun barIcon(name: String?): ImageVector? = when (name) {
    "arrow.left" -> Icons.AutoMirrored.Outlined.ArrowBack
    "arrow.right" -> Icons.AutoMirrored.Outlined.ArrowForward
    "arrow.up" -> Icons.Outlined.ArrowUpward
    "arrow.down" -> Icons.Outlined.ArrowDownward
    "return" -> Icons.AutoMirrored.Outlined.KeyboardReturn
    "delete.left" -> Icons.AutoMirrored.Outlined.Backspace
    "doc.on.clipboard" -> Icons.Outlined.ContentPaste
    "sparkles" -> Icons.Outlined.AutoAwesome
    else -> null
}

/** What a key shows: its label, or Paste in the app's language. */
@Composable
internal fun barKeyTitle(key: BarKey): String = when (key.id) {
    BarKey.PASTE_ID -> stringResource(R.string.common_paste)
    BarKey.AI_ID -> stringResource(R.string.keys_ai)
    else -> key.label
}

/** Icon of where a suggestion comes from. */
internal fun suggestionIcon(source: SuggestionSource): ImageVector = when (source) {
    SuggestionSource.HISTORY -> Icons.Outlined.History
    SuggestionSource.SNIPPET -> Icons.Outlined.Code
    SuggestionSource.COMMAND -> Icons.Outlined.Terminal
}

/**
 * The key bar above the keyboard, as on iOS: the keys of the layout (Settings
 * → Terminal → Keys above the keyboard) in a scrolling row, the cursor
 * button at the start ("With a button"), the grid button at the end that
 * opens the quick access panel and, with "In the key bar", the suggestions
 * first. Ctrl and Alt stay pressed until the next key; arrows and delete
 * repeat while held.
 */
@Composable
internal fun KeyBar(
    session: TermSession,
    layout: KeyboardLayout,
    suggestionMode: SuggestionMode,
    cursorButton: Boolean,
    panelOpen: Boolean,
    onPaste: () -> Unit,
    onPanel: () -> Unit,
) {
    val ctrl by session.ctrl.collectAsState()
    val alt by session.alt.collectAsState()
    val cursorByButton by session.cursorByButton.collectAsState()
    val suggestions by session.suggestions.collectAsState()
    val scroll = rememberScrollState()
    val chips = if (suggestionMode == SuggestionMode.BAR) suggestions else emptyList()
    // New suggestions: back to the start, where they are.
    LaunchedEffect(chips.isNotEmpty()) { if (chips.isNotEmpty()) scroll.scrollTo(0) }
    Row(Modifier.fillMaxWidth().background(TermBarBg).height(52.dp), verticalAlignment = Alignment.CenterVertically) {
        if (cursorButton) {
            val label = stringResource(R.string.term_move_cursor)
            Box(
                Modifier.padding(start = 6.dp).size(44.dp, 40.dp).clip(RoundedCornerShape(8.dp))
                    .background(if (cursorByButton) MaterialTheme.colorScheme.primary else TermKeyBg)
                    .toggleable(cursorByButton, role = Role.Switch) { session.cursorByButton.value = it }
                    .semantics { contentDescription = label },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (cursorByButton) Icons.Filled.TouchApp else Icons.Outlined.TouchApp, null, Modifier.size(20.dp),
                    tint = if (cursorByButton) Color.White else TermKeyFg,
                )
            }
        }
        Row(
            Modifier.weight(1f).horizontalScroll(scroll).padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (chips.isNotEmpty()) {
                chips.forEach { s -> SuggestionChip(s) { session.accept(s) } }
                Box(Modifier.width(1.dp).height(30.dp).background(TermKeyFg.copy(alpha = 0.2f)))
            }
            layout.bar.forEach { key ->
                val active = when (val a = key.action) {
                    is BarAction.Modifier -> if (a.ctrl) ctrl else alt
                    else -> false
                }
                BarKeyButton(key, active, Modifier.widthIn(min = 40.dp).height(40.dp)) { session.press(key, onPaste) }
            }
        }
        val panel = stringResource(R.string.quick_panel)
        Box(
            Modifier.padding(end = 6.dp).size(44.dp, 40.dp).clip(RoundedCornerShape(8.dp))
                .background(if (panelOpen) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f) else TermKeyBg)
                .clickable(onClickLabel = panel, onClick = onPanel)
                .semantics { contentDescription = panel },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.GridView, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        }
    }
}

/**
 * A key of the bar or the panel. A tap presses it; a key that repeats
 * (arrows, delete) repeats after 0.4 s held, every 70 ms, like a keyboard.
 */
@Composable
internal fun BarKeyButton(key: BarKey, active: Boolean, modifier: Modifier, onPress: () -> Unit) {
    val view = LocalView.current
    val press by rememberUpdatedState(onPress)
    val scope = rememberCoroutineScope()
    val title = barKeyTitle(key)
    val icon = barIcon(key.icon)
    Box(
        modifier.clip(RoundedCornerShape(8.dp))
            .background(if (active) MaterialTheme.colorScheme.primary else TermKeyBg)
            .pointerInput(key.id, key.repeats) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var repeater: Job? = null
                    var repeated = false
                    if (key.repeats) {
                        repeater = scope.launch {
                            delay(400)
                            repeated = true
                            while (true) {
                                press()
                                delay(70)
                            }
                        }
                    }
                    val up = waitForUpOrCancellation()
                    repeater?.cancel()
                    // A tap (not a swipe of the bar, which cancels it) presses the key once.
                    if (up != null && !repeated) {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        press()
                    }
                }
            }
            .semantics {
                contentDescription = title
                role = Role.Button
                onClick { press(); true }
            }
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(19.dp), tint = if (active) Color.White else TermKeyFg)
        } else {
            Text(
                title, color = if (active) Color.White else TermKeyFg, maxLines = 1,
                fontFamily = FontFamily.Monospace, fontSize = if (title.length > 3) 13.sp else 15.sp,
            )
        }
    }
}

/** A suggestion in the key bar: what was typed dimmed and the rest in the accent color. */
@Composable
private fun SuggestionChip(s: CommandSuggestion, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    val label = stringResource(R.string.suggestions_accessibility, s.text)
    Row(
        Modifier.height(40.dp).widthIn(max = 240.dp).clip(RoundedCornerShape(8.dp))
            .background(accent.copy(alpha = 0.14f))
            .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
            .clickable(onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label }
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(suggestionIcon(s.source), null, Modifier.size(13.dp), tint = TermKeyFg.copy(alpha = 0.55f))
        Text(
            suggestionText(s, accent), Modifier.padding(start = 6.dp), maxLines = 1, overflow = TextOverflow.StartEllipsis,
            fontFamily = FontFamily.Monospace, fontSize = 14.sp, fontWeight = FontWeight.Medium,
        )
    }
}

/** The suggestion with what is already typed dimmed and the rest in [accent]. */
internal fun suggestionText(s: CommandSuggestion, accent: Color) = buildAnnotatedString {
    withStyle(SpanStyle(color = TermKeyFg.copy(alpha = 0.55f))) { append(s.text.dropLast(s.insert.length)) }
    withStyle(SpanStyle(color = accent)) { append(s.insert) }
}

/**
 * Suggestions next to the cursor, like on the desktop and on iOS: a list
 * above the cursor's line (below if it doesn't fit), aligned with the start
 * of what was typed; a tap types the rest. (→ accepts the first one.)
 */
@Composable
internal fun BoxScope.CursorSuggestionList(view: TerminalView, list: List<CommandSuggestion>, onPick: (CommandSuggestion) -> Unit) {
    val cell = view.cursorCell() ?: return
    val (cellWidth, _) = view.cellSize
    val density = LocalDensity.current
    val rows = list.take(5)
    val rowHeight = with(density) { 38.dp.toPx() }
    val listWidth = with(density) { 360.dp.toPx() }.coerceAtMost(view.width - with(density) { 8.dp.toPx() })
    val height = rows.size * rowHeight + with(density) { 8.dp.toPx() }
    val typed = (rows.first().text.length - rows.first().insert.length).coerceAtLeast(0)
    val margin = with(density) { 4.dp.toPx() }
    val x = (cell.left - typed * cellWidth).coerceAtMost(view.width - listWidth - margin).coerceAtLeast(margin)
    val above = cell.top - height - with(density) { 6.dp.toPx() }
    val y = if (above >= 0) above else cell.bottom + with(density) { 6.dp.toPx() }
    val accent = MaterialTheme.colorScheme.primary
    Column(
        Modifier.offset { IntOffset(x.toInt(), y.toInt()) }.width(with(density) { listWidth.toDp() })
            .shadow(8.dp, RoundedCornerShape(10.dp)).clip(RoundedCornerShape(10.dp)).background(TermBarBg)
            .border(1.dp, TermKeyFg.copy(alpha = 0.15f), RoundedCornerShape(10.dp)).padding(vertical = 4.dp),
    ) {
        rows.forEachIndexed { i, s ->
            val label = stringResource(R.string.suggestions_accessibility, s.text)
            Row(
                Modifier.fillMaxWidth().height(38.dp).background(if (i == 0) accent.copy(alpha = 0.12f) else Color.Transparent)
                    .clickable(onClickLabel = label) { onPick(s) }.semantics { contentDescription = label }.padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(suggestionIcon(s.source), null, Modifier.size(13.dp), tint = TermKeyFg.copy(alpha = 0.55f))
                Text(
                    suggestionText(s, accent), Modifier.weight(1f).padding(start = 8.dp), maxLines = 1,
                    overflow = TextOverflow.StartEllipsis, fontFamily = FontFamily.Monospace, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                )
                if (s.description.isNotBlank()) {
                    Text(
                        s.description, Modifier.padding(start = 6.dp).widthIn(max = 120.dp), color = TermKeyFg.copy(alpha = 0.45f),
                        style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
