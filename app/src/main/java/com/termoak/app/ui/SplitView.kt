package com.termoak.app.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.outlined.CellTower
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloseFullscreen
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.RemoveFromQueue
import androidx.compose.material.icons.outlined.ViewSidebar
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.termoak.app.R
import com.termoak.app.term.SplitState
import com.termoak.app.term.TermSession

/**
 * How many terminals fit side by side in this window (Material window size
 * classes): 4 when it is expanded (≥ 840 dp: tablets, unfolded foldables in
 * landscape), 2 when it is medium (600–840 dp) and 1 on phones. It follows
 * the window as it changes (folding, unfolding, multi-window).
 */
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun rememberMaxPanes(): Int {
    val activity = LocalActivity.current ?: return 1
    return when (calculateWindowSizeClass(activity).widthSizeClass) {
        WindowWidthSizeClass.Expanded -> SplitState.MAX_PANES
        WindowWidthSizeClass.Medium -> 2
        else -> 1
    }
}

/** What a pane's menu does. */
class PaneActions(
    val onFocus: (TermSession) -> Unit,
    val onMaximize: (TermSession) -> Unit,
    val onRemove: (TermSession) -> Unit,
    val onClose: (TermSession) -> Unit,
    val onBroadcast: () -> Unit,
    val onExclude: (TermSession) -> Unit,
    val onFocusMode: (Boolean) -> Unit,
)

/**
 * The panes of the split view in a grid (2 side by side, or one above the
 * other in a tall window; 3 as 2 + 1; 4 as 2 × 2) or, in focus mode, the
 * focused one big on the left and the others in a column (the iOS app's
 * and the desktop's). Each one has a small toolbar: broadcast, maximize and
 * a menu (focus, focus mode, leave out of the broadcast, take out of the
 * split, close). [onRows] tells the panes per row, for Ctrl+Alt+arrows.
 */
@Composable
fun SplitPanes(
    panes: List<TermSession>,
    focusedId: String,
    split: SplitState,
    actions: PaneActions,
    onRows: (List<Int>) -> Unit = {},
    content: @Composable (TermSession, Boolean) -> Unit,
) {
    val broadcast = split.broadcast
    @Composable
    fun Pane(s: TermSession, modifier: Modifier) {
        key(s.id) {
            PaneFrame(
                s, focused = s.id == focusedId, broadcast = broadcast,
                receives = broadcast && s.id != focusedId && s.id !in split.excluded,
                excluded = broadcast && s.id in split.excluded, focusMode = split.focusMode,
                modifier = modifier, actions = actions,
            ) { content(s, s.id == focusedId) }
        }
    }
    val focused = panes.firstOrNull { it.id == focusedId }
    if (split.focusMode && focused != null && panes.size >= 2) {
        LaunchedEffect(panes.size) { onRows(listOf(panes.size)) }
        Row(Modifier.fillMaxSize().padding(2.dp)) {
            Pane(focused, Modifier.weight(0.7f).fillMaxHeight())
            Column(Modifier.weight(0.3f).fillMaxHeight()) {
                panes.filter { it.id != focusedId }.forEach { s -> Pane(s, Modifier.weight(1f).fillMaxWidth()) }
            }
        }
        return
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val rows = if (panes.size == 2 && maxHeight > maxWidth * 1.2f) listOf(1, 1) else SplitState.gridRows(panes.size)
        LaunchedEffect(rows) { onRows(rows) }
        val layout = buildList {
            var start = 0
            for (count in rows) {
                add(panes.subList(start, start + count))
                start += count
            }
        }
        Column(Modifier.fillMaxSize().padding(2.dp)) {
            layout.forEach { row ->
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    row.forEach { s -> Pane(s, Modifier.weight(1f).fillMaxHeight()) }
                }
            }
        }
    }
}

@Composable
private fun PaneFrame(
    session: TermSession,
    focused: Boolean,
    broadcast: Boolean,
    receives: Boolean,
    excluded: Boolean,
    focusMode: Boolean,
    modifier: Modifier,
    actions: PaneActions,
    content: @Composable () -> Unit,
) {
    val state by session.state.collectAsState()
    val title by session.title.collectAsState()
    val shape = RoundedCornerShape(8.dp)
    // Broadcasting: the panes that type get an orange border (thicker on the focused one); the excluded ones don't.
    val color = when {
        broadcast && !excluded -> Brand.Amber
        focused -> MaterialTheme.colorScheme.primary
        else -> TermKeyBg
    }
    var menu by remember { mutableStateOf(false) }
    Column(
        modifier.padding(2.dp).clip(shape).border(if (focused || receives) 2.dp else 1.dp, color, shape),
    ) {
        Row(
            Modifier.fillMaxWidth().height(32.dp)
                .background(if (broadcast && !excluded) Brand.Amber.copy(alpha = 0.18f) else TermBarBg)
                .clickable { actions.onFocus(session) }.padding(start = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusDot(stateColor(state), 7.dp)
            Text(
                title ?: session.label, Modifier.weight(1f).padding(start = 8.dp),
                color = TermKeyFg.copy(alpha = if (focused) 1f else 0.7f), fontSize = 12.sp,
                fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (receives) {
                Icon(Icons.Outlined.CellTower, stringResource(R.string.split_broadcast_receives), Modifier.size(14.dp), tint = Brand.Amber)
            } else if (excluded) {
                Icon(Icons.AutoMirrored.Outlined.VolumeOff, stringResource(R.string.split_broadcast_excluded), Modifier.size(14.dp),
                    tint = TermKeyFg.copy(alpha = 0.6f))
            }
            PaneButton(
                Icons.Outlined.CellTower,
                stringResource(if (broadcast) R.string.split_broadcast_stop else R.string.split_broadcast),
                tint = if (broadcast) Brand.Amber else TermKeyFg.copy(alpha = 0.7f), onClick = actions.onBroadcast,
            )
            PaneButton(Icons.Outlined.OpenInFull, stringResource(R.string.split_maximize)) { actions.onMaximize(session) }
            Box {
                PaneButton(Icons.Outlined.MoreVert, stringResource(R.string.split_pane_actions)) { menu = true }
                DropdownMenu(menu, { menu = false }) {
                    if (!focused) {
                        DropdownMenuItem({ Text(stringResource(R.string.split_pane_focus)) }, { menu = false; actions.onFocus(session) },
                            leadingIcon = { Icon(Icons.Outlined.CenterFocusStrong, null) })
                    }
                    if (focusMode && focused) {
                        DropdownMenuItem({ Text(stringResource(R.string.split_focus_mode_off)) }, { menu = false; actions.onFocusMode(false) },
                            leadingIcon = { Icon(Icons.Outlined.CloseFullscreen, null) })
                    } else {
                        DropdownMenuItem(
                            { Text(stringResource(R.string.split_focus_mode)) },
                            { menu = false; actions.onFocus(session); actions.onFocusMode(true) },
                            leadingIcon = { Icon(Icons.Outlined.ViewSidebar, null) },
                        )
                    }
                    if (broadcast) {
                        DropdownMenuItem(
                            { Text(stringResource(if (excluded) R.string.split_broadcast_include else R.string.split_broadcast_exclude)) },
                            { menu = false; actions.onExclude(session) },
                            leadingIcon = { Icon(if (excluded) Icons.Outlined.CellTower else Icons.AutoMirrored.Outlined.VolumeOff, null) },
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem({ Text(stringResource(R.string.split_remove)) }, { menu = false; actions.onRemove(session) },
                        leadingIcon = { Icon(Icons.Outlined.RemoveFromQueue, null) })
                    DropdownMenuItem(
                        { Text(stringResource(if (session.persistent) R.string.term_close_tab_keep else R.string.common_close)) },
                        { menu = false; actions.onClose(session) },
                        leadingIcon = { Icon(Icons.Outlined.Close, null) },
                    )
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) { content() }
    }
}

@Composable
private fun PaneButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    tint: Color = TermKeyFg.copy(alpha = 0.7f),
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, modifier = Modifier.size(32.dp)) {
        Icon(icon, description, Modifier.size(16.dp), tint = tint)
    }
}

/** Over the split view while broadcasting: where the typing goes, and Stop. */
@Composable
fun BroadcastBanner(count: Int, onStop: () -> Unit) {
    Surface(color = Brand.Amber, contentColor = Color.Black) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.CellTower, null, Modifier.size(16.dp))
            Column(Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 4.dp)) {
                Text(
                    pluralStringResource(R.plurals.split_broadcasting, count, count),
                    style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
                )
                Text(
                    stringResource(R.string.split_broadcast_hint), style = MaterialTheme.typography.labelSmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(onClick = onStop) { Text(stringResource(R.string.split_broadcast_stop_short), color = Color.Black) }
        }
    }
}

/** Which open terminals go side by side (up to [max]); or back to a single one. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitPickerSheet(
    sessions: List<TermSession>,
    current: List<String>,
    max: Int,
    splitOn: Boolean,
    onDismiss: () -> Unit,
    onApply: (List<String>) -> Unit,
) {
    // Starts with what is in the split (or the terminal on screen and the next ones).
    var chosen by remember {
        val ids = sessions.map { it.id }
        val start = current.filter { it in ids }
        mutableStateOf((start + ids.filter { it !in start }).take(if (splitOn) start.size.coerceAtMost(max) else max.coerceAtMost(2)))
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp)) {
            Text(stringResource(R.string.split_title), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.split_pick_hint, max), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LazyColumn(Modifier.heightIn(max = 420.dp).padding(top = 8.dp)) {
            items(sessions, key = { it.id }) { s ->
                val title by s.title.collectAsState()
                val state by s.state.collectAsState()
                val checked = s.id in chosen
                val enabled = checked || chosen.size < max
                ListItem(
                    modifier = Modifier.clickable(enabled = enabled) { chosen = if (checked) chosen - s.id else chosen + s.id },
                    leadingContent = { Checkbox(checked, null, enabled = enabled) },
                    headlineContent = { Text(title ?: s.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    trailingContent = {
                        val at = chosen.indexOf(s.id)
                        if (at >= 0) Text("${at + 1}", color = MaterialTheme.colorScheme.primary) else StatusDot(stateColor(state))
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
            if (splitOn) TextButton(onClick = { onApply(emptyList()) }) { Text(stringResource(R.string.split_single)) }
            Button(onClick = { onApply(chosen) }, enabled = chosen.size >= 2) {
                Text(pluralStringResource(R.plurals.split_show, chosen.size, chosen.size))
            }
        }
        Spacer(Modifier.navigationBarsPadding().height(16.dp))
    }
}
