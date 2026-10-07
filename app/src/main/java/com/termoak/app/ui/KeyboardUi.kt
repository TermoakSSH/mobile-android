package com.termoak.app.ui

import android.content.Context
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.KeyboardShortcutGroup
import android.view.KeyboardShortcutInfo
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.termoak.app.R
import com.termoak.app.term.Shortcut
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The app's keyboard shortcuts (HardwareKeys.shortcutOf): the activity
 * hands each one to the screens on top, newest first, until one takes it.
 * Main thread only.
 */
object KeyShortcuts {
    private val handlers = mutableListOf<(Shortcut) -> Boolean>()
    /** The "Keyboard shortcuts" sheet is open (Ctrl+/, the terminal's menu, Settings). */
    val sheet = MutableStateFlow(false)
    /** The Vault's search should take the focus (Ctrl+Shift+K). */
    val hostSearch = MutableStateFlow(false)

    fun add(handler: (Shortcut) -> Boolean) {
        handlers += handler
    }

    fun remove(handler: (Shortcut) -> Boolean) {
        handlers -= handler
    }

    /** Whether a screen took [shortcut]. */
    fun dispatch(shortcut: Shortcut): Boolean = handlers.toList().asReversed().any { it(shortcut) }

    private val keyHandlers = mutableListOf<(KeyEvent) -> Boolean>()

    fun addKeyHandler(handler: (KeyEvent) -> Boolean) {
        keyHandlers += handler
    }

    fun removeKeyHandler(handler: (KeyEvent) -> Boolean) {
        keyHandlers -= handler
    }

    /**
     * A key press nothing on screen took (no focused field or list wanted
     * it): the screens' own keys, like Ctrl+F or F5, work wherever the
     * focus is, without taking keys from the terminal or a text field.
     */
    fun dispatchUnhandled(event: KeyEvent): Boolean = keyHandlers.toList().asReversed().any { it(event) }

    /** Shortcuts that act once per press (not again while the keys are held). */
    val once = setOf(
        Shortcut.NEW_TAB, Shortcut.CLOSE_TAB, Shortcut.COPY, Shortcut.PASTE, Shortcut.SEARCH_HOSTS, Shortcut.SHORTCUTS,
    )
}

/** Takes the app's keyboard shortcuts while this is on screen ([handler] says whether it took one). */
@Composable
fun ShortcutHandler(handler: (Shortcut) -> Boolean) {
    val current by rememberUpdatedState(handler)
    DisposableEffect(Unit) {
        val h: (Shortcut) -> Boolean = { current(it) }
        KeyShortcuts.add(h)
        onDispose { KeyShortcuts.remove(h) }
    }
}

/** Takes key presses that nothing on screen took (see [KeyShortcuts.dispatchUnhandled]) while this is on screen. */
@Composable
fun UnhandledKeyHandler(handler: (KeyEvent) -> Boolean) {
    val current by rememberUpdatedState(handler)
    DisposableEffect(Unit) {
        val h: (KeyEvent) -> Boolean = { current(it) }
        KeyShortcuts.addKeyHandler(h)
        onDispose { KeyShortcuts.removeKeyHandler(h) }
    }
}

/** A key with modifiers, to show it. */
private data class Combo(val keyCode: Int, val ctrl: Boolean = false, val shift: Boolean = false, val alt: Boolean = false) {
    val modifiers: Int
        get() = (if (ctrl) KeyEvent.META_CTRL_ON else 0) or (if (shift) KeyEvent.META_SHIFT_ON else 0) or
            (if (alt) KeyEvent.META_ALT_ON else 0)
}

private class Help(@StringRes val label: Int, vararg val combos: Combo)

private class HelpGroup(@StringRes val title: Int, val items: List<Help>)

private fun ctrlShift(keyCode: Int) = Combo(keyCode, ctrl = true, shift = true)

private val Groups = listOf(
    HelpGroup(
        R.string.kb_section_terminal,
        listOf(
            Help(R.string.kb_new_tab, ctrlShift(KeyEvent.KEYCODE_T)),
            Help(R.string.kb_close_tab, ctrlShift(KeyEvent.KEYCODE_W)),
            Help(R.string.kb_next_tab, Combo(KeyEvent.KEYCODE_TAB, ctrl = true), ctrlShift(KeyEvent.KEYCODE_RIGHT_BRACKET)),
            Help(R.string.kb_prev_tab, ctrlShift(KeyEvent.KEYCODE_TAB), ctrlShift(KeyEvent.KEYCODE_LEFT_BRACKET)),
            Help(R.string.kb_copy, ctrlShift(KeyEvent.KEYCODE_C), Combo(KeyEvent.KEYCODE_INSERT, ctrl = true)),
            Help(R.string.kb_paste, ctrlShift(KeyEvent.KEYCODE_V), Combo(KeyEvent.KEYCODE_INSERT, shift = true)),
            Help(R.string.kb_zoom_in, ctrlShift(KeyEvent.KEYCODE_EQUALS)),
            Help(R.string.kb_zoom_out, ctrlShift(KeyEvent.KEYCODE_MINUS)),
            Help(R.string.kb_zoom_reset, ctrlShift(KeyEvent.KEYCODE_0)),
            Help(R.string.kb_next_pane, ctrlShift(KeyEvent.KEYCODE_O)),
            Help(R.string.kb_scroll_up, Combo(KeyEvent.KEYCODE_PAGE_UP, shift = true)),
            Help(R.string.kb_scroll_down, Combo(KeyEvent.KEYCODE_PAGE_DOWN, shift = true)),
        ),
    ),
    HelpGroup(
        R.string.kb_section_app,
        listOf(
            Help(R.string.kb_search_hosts, ctrlShift(KeyEvent.KEYCODE_K)),
            Help(R.string.kb_vault_search, Combo(KeyEvent.KEYCODE_F, ctrl = true)),
            Help(R.string.kb_shortcuts, Combo(KeyEvent.KEYCODE_SLASH, ctrl = true)),
            Help(R.string.kb_back, Combo(KeyEvent.KEYCODE_ESCAPE)),
        ),
    ),
    HelpGroup(
        R.string.kb_section_files,
        listOf(
            Help(R.string.kb_files_up, Combo(KeyEvent.KEYCODE_DPAD_UP, alt = true)),
            Help(R.string.kb_files_refresh, Combo(KeyEvent.KEYCODE_F5), Combo(KeyEvent.KEYCODE_R, ctrl = true)),
            Help(R.string.kb_files_search, Combo(KeyEvent.KEYCODE_F, ctrl = true)),
        ),
    ),
)

/** For Android's own list of shortcuts (Meta+/, or the keyboard's help key). */
fun keyboardShortcutGroups(context: Context): List<KeyboardShortcutGroup> = Groups.map { g ->
    KeyboardShortcutGroup(
        context.getString(g.title),
        g.items.flatMap { h -> h.combos.map { KeyboardShortcutInfo(context.getString(h.label), it.keyCode, it.modifiers) } },
    )
}

/** The name of a key, as printed on it. */
private fun keyName(context: Context, keyCode: Int): String = when (keyCode) {
    KeyEvent.KEYCODE_TAB -> context.getString(R.string.key_tab)
    KeyEvent.KEYCODE_PAGE_UP -> context.getString(R.string.key_page_up)
    KeyEvent.KEYCODE_PAGE_DOWN -> context.getString(R.string.key_page_down)
    KeyEvent.KEYCODE_INSERT -> context.getString(R.string.key_insert)
    KeyEvent.KEYCODE_ESCAPE -> context.getString(R.string.key_esc)
    KeyEvent.KEYCODE_DPAD_UP -> "↑"
    KeyEvent.KEYCODE_F5 -> "F5"
    else -> KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD).getDisplayLabel(keyCode).toString()
}

/** All the shortcuts, and what the mouse does in the terminal. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyboardShortcutsSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            Text(stringResource(R.string.kb_shortcuts), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.kb_intro), Modifier.padding(top = 4.dp, bottom = 8.dp),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            for (g in Groups) {
                Text(
                    stringResource(g.title), Modifier.padding(top = 16.dp, bottom = 4.dp),
                    style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
                )
                for (h in g.items) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(h.label), Modifier.weight(1f).padding(end = 12.dp), style = MaterialTheme.typography.bodyMedium)
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            h.combos.forEach { KeyCaps(it, context) }
                        }
                    }
                }
            }
            Text(
                stringResource(R.string.kb_section_mouse), Modifier.padding(top = 16.dp, bottom = 4.dp),
                style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
            )
            for (line in listOf(R.string.kb_mouse_wheel, R.string.kb_mouse_select, R.string.kb_mouse_shift)) {
                Text(stringResource(line), Modifier.padding(vertical = 4.dp), style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.navigationBarsPadding().height(24.dp))
        }
    }
}

@Composable
private fun KeyCaps(combo: Combo, context: Context) {
    val parts = listOfNotNull(
        stringResource(R.string.key_ctrl).takeIf { combo.ctrl },
        stringResource(R.string.key_shift).takeIf { combo.shift },
        stringResource(R.string.key_alt).takeIf { combo.alt },
        keyName(context, combo.keyCode),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        parts.forEach { p ->
            Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = RoundedCornerShape(6.dp)) {
                Text(
                    p, Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}
