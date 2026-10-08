package com.termoak.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.termoak.app.R
import com.termoak.app.TermoakApp
import com.termoak.app.term.BarAction
import com.termoak.app.term.BarKey
import com.termoak.app.term.BarSpecial
import com.termoak.app.term.BarStep
import com.termoak.app.term.KeyCombination
import com.termoak.app.term.KeyGroup
import com.termoak.app.term.KeyboardLayout

/** Readable text of what a key does (the editor's lists). */
@Composable
internal fun describeKey(action: BarAction): String = when (action) {
    is BarAction.Modifier -> stringResource(R.string.keys_describe_modifier, if (action.ctrl) "ctrl" else "alt")
    BarAction.Paste -> stringResource(R.string.keys_describe_paste)
    BarAction.Ai -> stringResource(R.string.keys_describe_ai)
    is BarAction.Steps -> KeyCombination.describe(action.steps)
}

/** [list] with the item at [i] moved by [by] (−1 up, +1 down). */
private fun <T> List<T>.moved(i: Int, by: Int): List<T> {
    val j = i + by
    if (i !in indices || j !in indices) return this
    return toMutableList().also { val x = it.removeAt(i); it.add(j, x) }
}

/**
 * "Customize" (Settings → Terminal → Keys above the keyboard, or the quick
 * panel's button), as on iOS: the bar above the keyboard (which keys and in
 * what order) and the groups of the quick panel (order, shown or hidden,
 * their keys), with custom keys and a reset to the default keys.
 */
@Composable
fun KeyboardEditorScreen(app: TermoakApp, nav: NavHostController) {
    val layout by app.prefs.keyboardLayout.collectAsState()
    var groupId by remember { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(false) }
    var resetting by remember { mutableStateOf(false) }
    fun save(l: KeyboardLayout) = app.prefs.setKeyboardLayout(l)

    val group = layout.groups.firstOrNull { it.id == groupId }
    BackHandler(group != null) { groupId = null }
    if (group != null) {
        KeyGroupEditor(layout, group, onBack = { groupId = null }, onSave = ::save)
        return
    }
    ScreenScaffold(
        title = stringResource(R.string.quick_panel_customize),
        navigationIcon = {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back)) }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp)) {
            item { SectionLabel(stringResource(R.string.keyboard_editor_bar_header)) }
            items(layout.bar.size, key = { "bar:" + layout.bar[it].id }) { i ->
                val k = layout.bar[i]
                EditorKeyRow(
                    k,
                    onUp = if (i > 0) ({ save(layout.copy(bar = layout.bar.moved(i, -1))) }) else null,
                    onDown = if (i < layout.bar.lastIndex) ({ save(layout.copy(bar = layout.bar.moved(i, 1))) }) else null,
                    onRemove = { save(layout.copy(bar = layout.bar.filterIndexed { j, _ -> j != i })) },
                )
            }
            item {
                TextButton(onClick = { adding = true }, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Icon(Icons.Outlined.AddCircleOutline, null, Modifier.size(18.dp))
                    Text(stringResource(R.string.keyboard_editor_add_to_bar), Modifier.padding(start = 8.dp))
                }
                FormHint(stringResource(R.string.keyboard_editor_bar_footer))
            }
            item { SectionLabel(stringResource(R.string.keyboard_editor_groups_header)) }
            items(layout.groups.size, key = { "group:" + layout.groups[it].id }) { i ->
                val g = layout.groups[i]
                ListItem(
                    modifier = Modifier.clickable { groupId = g.id },
                    headlineContent = { Text(keyGroupTitle(g)) },
                    supportingContent = { Text(stringResource(R.string.keyboard_editor_group_keys) + " · ${g.keys.size}") },
                    leadingContent = {
                        if (!g.visible) Icon(Icons.Outlined.VisibilityOff, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            MoveButtons(
                                onUp = if (i > 0) ({ save(layout.copy(groups = layout.groups.moved(i, -1))) }) else null,
                                onDown = if (i < layout.groups.lastIndex) ({ save(layout.copy(groups = layout.groups.moved(i, 1))) }) else null,
                            )
                            Icon(Icons.Outlined.ChevronRight, null)
                        }
                    },
                )
            }
            item {
                FormHint(stringResource(R.string.keyboard_editor_groups_footer))
                OutlinedButton(onClick = { resetting = true }, modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                    Text(stringResource(R.string.keyboard_editor_reset), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
    if (adding) {
        KeyPickerDialog(layout, onDismiss = { adding = false }) { key, custom ->
            adding = false
            val groups = if (custom) {
                // Custom keys also go into the "My keys" group.
                layout.groups.map { if (it.id == KeyGroup.CUSTOM) it.copy(keys = it.keys + key) else it }
            } else layout.groups
            save(KeyboardLayout(layout.bar + key, groups))
        }
    }
    if (resetting) {
        ConfirmDialog(
            stringResource(R.string.keyboard_editor_reset_title), stringResource(R.string.keyboard_editor_reset_message),
            stringResource(R.string.keyboard_editor_reset_confirm), true, onDismiss = { resetting = false },
        ) { save(KeyboardLayout.STANDARD) }
    }
}

/** The keys of a group: shown in the panel or not, its name, order, removing, to the bar, and new keys. */
@Composable
private fun KeyGroupEditor(layout: KeyboardLayout, group: KeyGroup, onBack: () -> Unit, onSave: (KeyboardLayout) -> Unit) {
    var creating by remember { mutableStateOf(false) }
    val defaultTitle = keyGroupTitle(group.copy(name = ""))
    val title = keyGroupTitle(group)
    var name by remember(group.id) { mutableStateOf(title) }
    fun update(g: KeyGroup) = onSave(layout.copy(groups = layout.groups.map { if (it.id == g.id) g else it }))
    ScreenScaffold(
        title = keyGroupTitle(group),
        navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back)) }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp)) {
            item {
                FormSection(keyGroupTitle(group)) {
                    ListItem(
                        modifier = Modifier.clickable { update(group.copy(visible = !group.visible)) },
                        headlineContent = { Text(stringResource(R.string.keyboard_editor_group_show)) },
                        trailingContent = { Switch(group.visible, { update(group.copy(visible = it)) }) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                    FormDivider()
                    OutlinedTextField(
                        name,
                        {
                            name = it
                            // The default name in the app's language stays "not renamed".
                            update(group.copy(name = if (it.trim() == defaultTitle && group.id in KeyGroup.BuiltIn) "" else it.trim()))
                        },
                        Modifier.fillMaxWidth().padding(12.dp), label = { Text(stringResource(R.string.common_name)) }, singleLine = true,
                    )
                }
                SectionLabel(stringResource(R.string.keyboard_editor_group_keys))
            }
            items(group.keys.size, key = { group.keys[it].id }) { i ->
                val k = group.keys[i]
                val inBar = layout.bar.any { it.id == k.id }
                EditorKeyRow(
                    k,
                    onUp = if (i > 0) ({ update(group.copy(keys = group.keys.moved(i, -1))) }) else null,
                    onDown = if (i < group.keys.lastIndex) ({ update(group.copy(keys = group.keys.moved(i, 1))) }) else null,
                    onRemove = { update(group.copy(keys = group.keys.filterIndexed { j, _ -> j != i })) },
                    onToBar = if (inBar) null else ({ onSave(layout.copy(bar = layout.bar + k)) }),
                )
            }
            item {
                TextButton(onClick = { creating = true }, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Icon(Icons.Outlined.AddCircleOutline, null, Modifier.size(18.dp))
                    Text(stringResource(R.string.keyboard_editor_new_key), Modifier.padding(start = 8.dp))
                }
                FormHint(stringResource(R.string.keyboard_editor_group_footer))
            }
        }
    }
    if (creating) {
        NewKeyDialog(onDismiss = { creating = false }) { k ->
            creating = false
            update(group.copy(keys = group.keys + k))
        }
    }
}

/** A key in the editor's lists: how it looks, what it sends, and its buttons. */
@Composable
private fun EditorKeyRow(
    key: BarKey,
    onUp: (() -> Unit)?,
    onDown: (() -> Unit)?,
    onRemove: () -> Unit,
    onToBar: (() -> Unit)? = null,
) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 2.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        KeyCap(key)
        Text(
            describeKey(key.action), Modifier.weight(1f).padding(horizontal = 12.dp),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        if (onToBar != null) {
            IconButton(onClick = onToBar) { Icon(Icons.Outlined.Add, stringResource(R.string.keyboard_editor_group_to_bar)) }
        }
        MoveButtons(onUp, onDown)
        IconButton(onClick = onRemove) { Icon(Icons.Outlined.Close, stringResource(R.string.keyboard_editor_remove)) }
    }
}

@Composable
private fun MoveButtons(onUp: (() -> Unit)?, onDown: (() -> Unit)?) {
    IconButton(onClick = { onUp?.invoke() }, enabled = onUp != null) {
        Icon(Icons.Outlined.KeyboardArrowUp, stringResource(R.string.keyboard_editor_move_up))
    }
    IconButton(onClick = { onDown?.invoke() }, enabled = onDown != null) {
        Icon(Icons.Outlined.KeyboardArrowDown, stringResource(R.string.keyboard_editor_move_down))
    }
}

/** How a key looks on the bar. */
@Composable
private fun KeyCap(key: BarKey) {
    Box(
        Modifier.widthIn(min = 44.dp).heightIn(min = 30.dp).clip(RoundedCornerShape(7.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest).padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        val icon = barIcon(key.icon)
        if (icon != null) Icon(icon, barKeyTitle(key), Modifier.size(18.dp))
        else Text(barKeyTitle(key), fontFamily = FontFamily.Monospace, fontSize = 14.sp, maxLines = 1)
    }
}

/** Pick a key for the bar (from any group, not already on it) or create a new one. */
@Composable
private fun KeyPickerDialog(layout: KeyboardLayout, onDismiss: () -> Unit, onPick: (BarKey, custom: Boolean) -> Unit) {
    var creating by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.keyboard_editor_add_to_bar_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                TextButton(onClick = { creating = true }) {
                    Icon(Icons.Outlined.AddCircleOutline, null, Modifier.size(18.dp))
                    Text(stringResource(R.string.keyboard_editor_new_key_ellipsis), Modifier.padding(start = 8.dp))
                }
                layout.groups.forEach { g ->
                    val free = g.keys.filter { k -> layout.bar.none { it.id == k.id } }
                    if (free.isNotEmpty()) {
                        Text(
                            keyGroupTitle(g), Modifier.padding(top = 12.dp, bottom = 4.dp),
                            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                        )
                        free.forEach { k ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onPick(k, false) }.padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                KeyCap(k)
                                Text(
                                    describeKey(k.action), Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        HorizontalDivider(Modifier.padding(top = 6.dp))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
    if (creating) {
        NewKeyDialog(onDismiss = { creating = false }) { k ->
            creating = false
            onPick(k, true)
        }
    }
}

/** A custom key: its label, a combination (`ctrl+b`, `esc`, `^C`...), a text and, optionally, Enter at the end. */
@Composable
private fun NewKeyDialog(onDismiss: () -> Unit, onSave: (BarKey) -> Unit) {
    var label by remember { mutableStateOf("") }
    var combination by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    var enter by remember { mutableStateOf(false) }
    val parsed = KeyCombination.parse(combination)
    val steps = (parsed as? KeyCombination.Result.Ok)?.steps?.let { s ->
        s + listOfNotNull(BarStep.Text(text).takeIf { text.isNotEmpty() }, BarStep.Special(BarSpecial.ENTER).takeIf { enter })
    }
    val valid = label.isNotBlank() && !steps.isNullOrEmpty()
    val mono = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.keyboard_editor_new_key)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(label, { label = it }, Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.keyboard_editor_new_key_label)) }, singleLine = true)
                OutlinedTextField(
                    combination, { combination = it }, Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.keyboard_editor_new_key_combination)) }, singleLine = true,
                    placeholder = { Text("ctrl+b c") }, textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                    keyboardOptions = mono, isError = parsed is KeyCombination.Result.Error,
                    supportingText = {
                        Text(
                            (parsed as? KeyCombination.Result.Error)?.let { stringResource(R.string.keys_combination_error, it.part) }
                                ?: stringResource(R.string.keyboard_editor_new_key_combination_help),
                        )
                    },
                )
                OutlinedTextField(
                    text, { text = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.keyboard_editor_new_key_text)) },
                    singleLine = true, textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace), keyboardOptions = mono,
                )
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { enter = !enter },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.keyboard_editor_new_key_press_enter), Modifier.weight(1f))
                    Switch(enter, { enter = it })
                }
                if (!steps.isNullOrEmpty()) {
                    Text(
                        stringResource(R.string.keyboard_editor_new_key_sends, KeyCombination.describe(steps)),
                        style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { steps?.let { onSave(BarKey.custom(label, it)) } }) { Text(stringResource(R.string.common_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}
