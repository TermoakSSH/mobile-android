package com.termoak.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.termoak.app.TermoakApp
import com.termoak.app.term.TermChrome
import com.termoak.app.term.TermSession
import com.termoak.app.term.TerminalThemes
import com.termoak.ffi.TerminalThemeInfo

/**
 * The colours of the terminal screen's bars, keys and panels: the theme of
 * the terminal in view (Compose state: what reads them is redrawn when it
 * changes).
 */
object TermChromeState {
    var current by mutableStateOf(TermChrome.DEFAULT)
}

/**
 * The theme of [session]'s terminal: its host's (with what its groups set,
 * the engine's effective settings) by the engine's rule, or the app's
 * ([appTheme]). Server sessions of a host follow it too.
 */
internal fun themeFor(app: TermoakApp, session: TermSession, appTheme: String): String {
    val host = session.hostId?.let { id ->
        runCatching { app.core.effectiveSettings(id, session.accountId).theme }.getOrNull()
            ?: runCatching { app.core.getHost(id, session.accountId).settings.theme }.getOrNull()
    }
    return TerminalThemes.forHost(host, appTheme)
}

/** The terminal themes as swatches (a bit of each one's text and colours), the chosen one outlined. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TerminalThemePicker(app: TermoakApp, modifier: Modifier = Modifier) {
    val selected by app.prefs.terminalTheme.collectAsState()
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TerminalThemes.all.forEach { t -> ThemeSwatch(t, t.id == selected) { app.prefs.setTerminalTheme(t.id) } }
    }
}

@Composable
private fun ThemeSwatch(t: TerminalThemeInfo, chosen: Boolean, onClick: () -> Unit) {
    val c = t.colors
    fun color(argb: UInt) = Color(argb.toInt() or 0xFF000000.toInt())
    val shape = RoundedCornerShape(10.dp)
    Column(
        Modifier.width(104.dp).clip(shape)
            .border(if (chosen) 2.dp else 1.dp, if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, shape)
            .background(color(c.background))
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { selected = chosen }
            .padding(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$", color = color(c.ansi.getOrNull(2) ?: c.foreground), fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            Text(" ls", color = color(c.foreground), fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            Box(Modifier.padding(start = 2.dp).size(6.dp, 12.dp).background(color(c.cursor)))
        }
        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            for (i in 1..6) Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(color(c.ansi.getOrNull(i) ?: c.foreground)))
        }
        Text(
            t.name, Modifier.padding(top = 4.dp), color = color(c.foreground), fontSize = 11.sp, maxLines = 1,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
