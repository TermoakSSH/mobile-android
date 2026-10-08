package com.termoak.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.termoak.app.R
import com.termoak.app.TermoakApp
import com.termoak.app.data.Prefs
import com.termoak.app.data.uid
import com.termoak.app.term.BarAction
import com.termoak.app.term.BarKey
import com.termoak.app.term.KeyGroup
import com.termoak.app.term.TermSession
import com.termoak.app.term.TerminalFont
import com.termoak.ffi.CommandHistoryItem
import com.termoak.ffi.Snippet
import com.termoak.ffi.snippetVariables
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Tabs of the quick access panel (the stored names are the iOS app's). */
internal enum class QuickPanelTab(val key: String, val icon: ImageVector, val title: Int) {
    KEYS("teclas", Icons.Outlined.GridView, R.string.quick_panel_tab_keys),
    SNIPPETS("snippets", Icons.Outlined.Code, R.string.quick_panel_tab_snippets),
    HISTORY("historial", Icons.Outlined.History, R.string.quick_panel_tab_history),
    APPEARANCE("apariencia", Icons.Outlined.Palette, R.string.quick_panel_tab_appearance),
    ;

    companion object {
        fun of(key: String?) = entries.firstOrNull { it.key == key } ?: KEYS
    }
}

/** Name of a group: the user's, or the built-in one in the app's language. */
@Composable
internal fun keyGroupTitle(g: KeyGroup): String {
    if (g.renamed) return g.name
    return when (g.id) {
        "basicas" -> stringResource(R.string.keys_group_basic)
        "flechas" -> stringResource(R.string.keys_group_arrows)
        "tmux" -> stringResource(R.string.keys_group_tmux)
        "simbolos" -> stringResource(R.string.keys_group_symbols)
        "control" -> stringResource(R.string.keys_group_control)
        "funciones" -> stringResource(R.string.keys_group_functions)
        KeyGroup.CUSTOM -> stringResource(R.string.keys_group_custom)
        else -> g.name
    }
}

/**
 * The quick access panel, as on iOS: key groups, snippets (search and
 * folders by their first tag), this host's command history and the
 * terminal's appearance, one tap away. On phones it takes the keyboard's
 * place, with the tabs at the bottom and a button back to the keyboard; in
 * the desktop layout it is a side panel with the tabs on top. It opens on
 * the last tab used.
 */
@Composable
internal fun QuickPanel(
    app: TermoakApp,
    session: TermSession,
    side: Boolean,
    panes: Int,
    openCount: Int,
    onKeyboard: () -> Unit,
    onCustomize: () -> Unit,
    onPaste: () -> Unit,
    onSnippet: (name: String, text: String, run: Boolean, target: SnippetTarget) -> Unit,
    modifier: Modifier = Modifier,
) {
    var tab by remember { mutableStateOf(QuickPanelTab.of(app.prefs.quickPanelTab)) }
    fun select(t: QuickPanelTab) {
        tab = t
        app.prefs.quickPanelTab = t.key
    }

    @Composable
    fun Tabs() {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            QuickPanelTab.entries.forEach { t ->
                val on = t == tab
                val title = stringResource(t.title)
                Box(
                    Modifier.weight(1f).height(38.dp).clip(RoundedCornerShape(10.dp))
                        .background(if (on) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else TermBarBg)
                        .clickable(onClickLabel = title) { select(t) }
                        .semantics { contentDescription = title },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(t.icon, null, Modifier.size(19.dp), tint = if (on) MaterialTheme.colorScheme.primary else TermKeyFg.copy(alpha = 0.7f))
                }
            }
            if (!side) {
                Box(Modifier.padding(horizontal = 6.dp).width(1.dp).height(22.dp).background(TermKeyFg.copy(alpha = 0.2f)))
                val back = stringResource(R.string.quick_panel_back_to_keyboard)
                Box(
                    Modifier.size(52.dp, 38.dp).clip(RoundedCornerShape(10.dp)).clickable(onClickLabel = back, onClick = onKeyboard)
                        .semantics { contentDescription = back },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Outlined.Keyboard, null, Modifier.size(19.dp), tint = TermKeyFg.copy(alpha = 0.7f)) }
            }
        }
    }

    Column(modifier.background(TermBarBg)) {
        if (side) Tabs()
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                QuickPanelTab.KEYS -> KeysTab(app, session, onCustomize, onPaste)
                QuickPanelTab.SNIPPETS -> SnippetsTab(app, panes, openCount, onSnippet)
                QuickPanelTab.HISTORY -> HistoryTab(session)
                QuickPanelTab.APPEARANCE -> AppearanceTab(app)
            }
        }
        if (!side) Tabs()
    }
}

// ----- Keys -----

@Composable
private fun KeysTab(app: TermoakApp, session: TermSession, onCustomize: () -> Unit, onPaste: () -> Unit) {
    val layout by app.prefs.keyboardLayout.collectAsState()
    val ctrl by session.ctrl.collectAsState()
    val alt by session.alt.collectAsState()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // On wide screens, two groups per row (like the desktop).
        val perRow = if (maxWidth >= 560.dp) 2 else 1
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(10.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                Modifier.fillMaxWidth().height(36.dp).clip(RoundedCornerShape(8.dp)).background(TermKeyFg.copy(alpha = 0.08f))
                    .clickable(onClick = onCustomize),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Settings, null, Modifier.size(16.dp), tint = TermKeyFg)
                Text(stringResource(R.string.quick_panel_customize), Modifier.padding(start = 6.dp), color = TermKeyFg,
                    style = MaterialTheme.typography.labelLarge)
            }
            layout.groups.filter { it.visible && it.keys.isNotEmpty() }.chunked(perRow).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    row.forEach { g ->
                        KeyGroupGrid(g, Modifier.weight(1f), isActive = { k ->
                            when (val a = k.action) {
                                is BarAction.Modifier -> if (a.ctrl) ctrl else alt
                                else -> false
                            }
                        }) { session.press(it, onPaste) }
                    }
                    if (row.size < perRow) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

/** A group in rows of four slots; long keys take two. */
@Composable
private fun KeyGroupGrid(group: KeyGroup, modifier: Modifier, isActive: (BarKey) -> Boolean, onPress: (BarKey) -> Unit) {
    val rows = remember(group.keys) {
        val out = mutableListOf(mutableListOf<BarKey>())
        var slots = 0
        for (k in group.keys) {
            val n = if (k.wide) 2 else 1
            if (slots + n > 4) {
                out += mutableListOf<BarKey>()
                slots = 0
            }
            out.last() += k
            slots += n
        }
        out
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            keyGroupTitle(group).uppercase(), color = TermKeyFg.copy(alpha = 0.5f),
            style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold,
        )
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                var used = 0
                row.forEach { k ->
                    val n = if (k.wide) 2 else 1
                    used += n
                    BarKeyButton(k, isActive(k), Modifier.weight(n.toFloat()).height(40.dp)) { onPress(k) }
                }
                if (used < 4) Spacer(Modifier.weight((4 - used).toFloat()))
            }
        }
    }
}

// ----- Snippets -----

/** Internal folder of the snippets without tags (shown with a translated name). */
private const val NO_FOLDER = "\u0000no-folder"

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SnippetsTab(app: TermoakApp, panes: Int, openCount: Int, onSnippet: (String, String, Boolean, SnippetTarget) -> Unit) {
    val list = remember {
        runCatching { app.core.listSnippets(app.accounts.filter()) }.getOrDefault(emptyList()).sortedBy { it.name.lowercase() }
    }
    var search by remember { mutableStateOf("") }
    val expanded = remember { mutableStateListOf<String>() }
    var filling by remember { mutableStateOf<Triple<Snippet, Boolean, SnippetTarget>?>(null) }
    fun use(sn: Snippet, run: Boolean, target: SnippetTarget) {
        if (snippetVariables(sn.script).isEmpty()) onSnippet(sn.name, sn.script, run, target) else filling = Triple(sn, run, target)
    }
    val filtered = SnippetFolders.filter(list, search)
    Column(Modifier.fillMaxSize()) {
        PanelSearch(search) { search = it }
        if (list.isEmpty()) {
            EmptyHint(stringResource(R.string.quick_panel_snippets_empty))
            return@Column
        }
        val folders = SnippetFolders.group(filtered, NO_FOLDER)
        LazyColumn(Modifier.fillMaxSize()) {
            if (search.isNotBlank() || folders.size <= 1) {
                items(filtered, key = { it.uid }) { SnippetRow(it, panes, openCount, ::use) }
            } else {
                folders.forEach { (name, snippets) ->
                    item(key = "folder:$name") {
                        val open = name in expanded
                        Row(
                            Modifier.fillMaxWidth().clickable { if (open) expanded -= name else expanded += name }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Outlined.Folder, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                            Text(
                                if (name == NO_FOLDER) stringResource(R.string.quick_panel_snippets_no_folder) else name,
                                Modifier.weight(1f).padding(start = 10.dp), color = TermKeyFg, style = MaterialTheme.typography.titleSmall,
                            )
                            Text("${snippets.size}", color = TermKeyFg.copy(alpha = 0.6f), style = MaterialTheme.typography.labelMedium)
                            Icon(
                                if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null,
                                Modifier.padding(start = 4.dp).size(18.dp), tint = TermKeyFg.copy(alpha = 0.6f),
                            )
                        }
                    }
                    if (name in expanded) {
                        items(snippets, key = { "$name/${it.uid}" }) {
                            Box(Modifier.padding(start = 18.dp)) { SnippetRow(it, panes, openCount, ::use) }
                        }
                    }
                }
            }
        }
    }
    filling?.let { (sn, run, target) ->
        SnippetVariablesDialog(
            sn, stringResource(if (run) R.string.snippets_run else R.string.common_paste), onDismiss = { filling = null },
        ) { text ->
            filling = null
            onSnippet(sn.name, text, run, target)
        }
    }
}

/** A snippet: a tap pastes it, ▶ runs it; held, the same for the split's panes or every open terminal. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SnippetRow(sn: Snippet, panes: Int, openCount: Int, use: (Snippet, Boolean, SnippetTarget) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                    .combinedClickable(onLongClick = { if (panes > 0 || openCount > 1) menu = true }) { use(sn, false, SnippetTarget.THIS) },
            ) {
                Text(sn.name, color = TermKeyFg, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
                Text(sn.script, color = TermKeyFg.copy(alpha = 0.6f), fontFamily = FontFamily.Monospace, fontSize = 12.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
            }
            DropdownMenu(menu, { menu = false }) {
                DropdownMenuItem({ Text(stringResource(R.string.common_paste)) }, { menu = false; use(sn, false, SnippetTarget.THIS) })
                DropdownMenuItem({ Text(stringResource(R.string.snippets_run)) }, { menu = false; use(sn, true, SnippetTarget.THIS) })
                if (panes > 0) {
                    DropdownMenuItem({ Text(stringResource(R.string.multi_target_panes, panes)) }, { menu = false; use(sn, true, SnippetTarget.PANES) })
                }
                if (openCount > 1) {
                    DropdownMenuItem(
                        { Text(stringResource(R.string.multi_target_all_open, openCount)) },
                        { menu = false; use(sn, true, SnippetTarget.ALL_OPEN) },
                    )
                }
            }
        }
        RunButton(stringResource(R.string.quick_panel_run, sn.name)) { use(sn, true, SnippetTarget.THIS) }
    }
}

// ----- History -----

@Composable
private fun HistoryTab(session: TermSession) {
    var search by remember { mutableStateOf("") }
    var commands by remember { mutableStateOf<List<CommandHistoryItem>>(emptyList()) }
    var clearing by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(search, reload) {
        commands = withContext(Dispatchers.IO) { session.assist?.history(session.hostId, search).orEmpty() }
    }
    Column(Modifier.fillMaxSize()) {
        PanelSearch(search) { search = it }
        if (commands.isEmpty()) {
            EmptyHint(
                if (search.isBlank()) stringResource(R.string.quick_panel_history_empty)
                else stringResource(R.string.quick_panel_history_no_match, search),
            )
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(commands, key = { it.command }) { c ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).clickable { session.paste(c.command) }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.History, null, Modifier.size(14.dp), tint = TermKeyFg.copy(alpha = 0.5f))
                        Text(
                            c.command, Modifier.weight(1f).padding(start = 10.dp), color = TermKeyFg, fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        if (c.uses > 1u) {
                            Text("×${c.uses}", Modifier.padding(horizontal = 6.dp), color = TermKeyFg.copy(alpha = 0.5f),
                                style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    RunButton(stringResource(R.string.quick_panel_run, c.command)) { session.run(c.command) }
                }
            }
            if (search.isBlank() && session.hostId != null) {
                item {
                    TextButton(onClick = { clearing = true }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text(stringResource(R.string.quick_panel_history_clear), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
    if (clearing) {
        ConfirmDialog(
            stringResource(R.string.quick_panel_history_clear_confirm), "", stringResource(R.string.common_delete), true,
            onDismiss = { clearing = false },
        ) {
            session.assist?.clear(session.hostId)
            reload++
        }
    }
}

// ----- Appearance -----

@Composable
private fun AppearanceTab(app: TermoakApp) {
    val fontSize by app.prefs.fontSize.collectAsState()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(10.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallKey(Icons.Outlined.Remove, stringResource(R.string.common_font_smaller)) { app.prefs.setFontSize(fontSize - 1) }
            Text("${fontSize.toInt()}", Modifier.width(34.dp), color = TermKeyFg, textAlign = TextAlign.Center)
            SmallKey(Icons.Outlined.Add, stringResource(R.string.common_font_larger)) { app.prefs.setFontSize(fontSize + 1) }
            if (fontSize != Prefs.DEFAULT_FONT) {
                TextButton(onClick = { app.prefs.setFontSize(Prefs.DEFAULT_FONT) }) { Text(stringResource(R.string.kb_zoom_reset)) }
            }
        }
        TerminalFontPicker(app)
    }
}

// ----- Shared -----

@Composable
private fun SmallKey(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp, 36.dp).clip(RoundedCornerShape(8.dp)).background(TermKeyBg).clickable(onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, Modifier.size(18.dp), tint = TermKeyFg) }
}

@Composable
private fun RunButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier.padding(start = 10.dp).size(34.dp, 30.dp).clip(RoundedCornerShape(8.dp)).background(TermKeyBg)
            .clickable(onClickLabel = label, onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Outlined.PlayArrow, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary) }
}

@Composable
private fun PanelSearch(text: String, onChange: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(10.dp).height(36.dp).clip(RoundedCornerShape(10.dp)).background(TermKeyFg.copy(alpha = 0.07f))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Search, null, Modifier.size(18.dp), tint = TermKeyFg.copy(alpha = 0.5f))
        Box(Modifier.weight(1f).padding(start = 8.dp)) {
            if (text.isEmpty()) Text(stringResource(R.string.common_search), color = TermKeyFg.copy(alpha = 0.4f))
            BasicTextField(
                text, onChange, Modifier.fillMaxWidth(), singleLine = true,
                textStyle = TextStyle(color = TermKeyFg, fontSize = 15.sp),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
            )
        }
        if (text.isNotEmpty()) {
            Icon(Icons.Outlined.Close, stringResource(R.string.hosts_search_clear), Modifier.size(18.dp).clickable { onChange("") },
                tint = TermKeyFg.copy(alpha = 0.5f))
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text, Modifier.fillMaxWidth().heightIn(min = 120.dp).padding(24.dp), color = TermKeyFg.copy(alpha = 0.6f),
        style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
    )
}

/** Snippets for the panel and the snippets sheet: search, and folders by the first tag. */
internal object SnippetFolders {
    fun filter(list: List<Snippet>, query: String): List<Snippet> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return list
        return list.filter { (it.name + " " + it.script + " " + it.tags.joinToString(" ")).lowercase().contains(q) }
    }

    /** Folders sorted by name, the one without tags ([none]) last. */
    fun group(list: List<Snippet>, none: String): List<Pair<String, List<Snippet>>> =
        list.groupBy { it.tags.firstOrNull() ?: none }.toList()
            .sortedWith(compareBy<Pair<String, List<Snippet>>> { it.first == none }.thenBy { it.first.lowercase() })
}

/** Name of a terminal font (the system one in the app's language). */
@Composable
internal fun terminalFontName(font: TerminalFont): String =
    if (font == TerminalFont.SYSTEM) stringResource(R.string.settings_terminal_font_system) else font.displayName

/** The terminal fonts as chips, each written in its own font. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TerminalFontPicker(app: TermoakApp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val selected by app.prefs.terminalFont.collectAsState()
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TerminalFont.entries.forEach { f ->
            val family = remember(f) { FontFamily(f.typeface(context)) }
            FilterChip(
                selected = f == selected, onClick = { app.prefs.setTerminalFont(f) },
                label = { Text(terminalFontName(f), fontFamily = family) },
            )
        }
    }
}
