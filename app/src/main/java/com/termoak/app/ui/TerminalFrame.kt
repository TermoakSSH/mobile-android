package com.termoak.app.ui

import android.content.Context
import android.view.View
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay

/**
 * The frame of the terminal screen: the bars on top ([header]: the phone's
 * bar and tabs, or the desktop layout's toolbar), the [terminal] (one, or the
 * split view), the bars under it ([footer]: who has the keyboard, the key
 * bar) and the [copilot].
 *
 * - It keeps clear of the status and navigation bars and of the display
 *   cutout (a punch-hole or notch; on the side in landscape). In the desktop
 *   layout the window's tab bar above it keeps clear of the top.
 * - The terminal makes room for its own keyboard (fewer rows). On a phone
 *   the copilot's keyboard goes over it instead: the terminal keeps its size
 *   (no resize sent to the server, nothing reflowed) and only the copilot
 *   moves up; also while that keyboard goes away after closing the copilot,
 *   so the terminal isn't resized twice.
 * - The copilot goes beside the terminal on [wide] windows and over it, from
 *   the right, on phones ([copilot]'s `overlay`).
 * - On phones the system bars get light icons: the terminal is dark in both themes.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TerminalFrame(
    desktop: Boolean,
    wide: Boolean,
    copilotOpen: Boolean,
    onCloseCopilot: () -> Unit,
    header: @Composable ColumnScope.() -> Unit,
    terminal: @Composable BoxScope.() -> Unit,
    footer: @Composable ColumnScope.() -> Unit,
    /** A panel beside the terminal (the quick panel of the desktop layout), `null`: none. */
    side: (@Composable () -> Unit)? = null,
    copilot: @Composable (modifier: Modifier, overlay: Boolean) -> Unit,
) {
    if (!desktop) DarkSystemBars()
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
    // The system bars and the display cutout; the keyboard is handled below.
    val bars = WindowInsets.systemBars.union(WindowInsets.displayCutout)
    val insets = if (desktop) bars.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom) else bars

    Box(Modifier.fillMaxSize().background(TermScreenBg).windowInsetsPadding(insets)) {
        Row(Modifier.fillMaxSize().then(if (imeOverTerminal) Modifier else Modifier.imePadding())) {
            Column(Modifier.weight(1f).fillMaxHeight()) {
                header()
                Box(Modifier.weight(1f).fillMaxWidth(), content = terminal)
                footer()
            }
            if (side != null) {
                VerticalDivider(color = TermKeyBg)
                Box(Modifier.width(320.dp).fillMaxHeight()) { side() }
            }
            if (wide && copilotOpen) {
                VerticalDivider(color = TermKeyBg)
                copilot(Modifier.width(380.dp), false)
            }
        }
        if (!wide) {
            AnimatedVisibility(copilotOpen, enter = fadeIn(), exit = fadeOut()) {
                Box(
                    Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f))
                        .clickable(remember { MutableInteractionSource() }, indication = null) { onCloseCopilot() },
                )
            }
            AnimatedVisibility(
                copilotOpen,
                // Above the keyboard, so its box to write in is never under it.
                Modifier.align(Alignment.CenterEnd).fillMaxWidth(0.85f).fillMaxHeight().imePadding(),
                enter = slideInHorizontally { it },
                exit = slideOutHorizontally { it },
            ) {
                copilot(Modifier.fillMaxSize(), true)
            }
        }
    }
}

/**
 * An [AndroidView] clipped to its place. Compose doesn't clip the views it
 * hosts (their holder sets `clipChildren = false`), so a view that fills its
 * whole canvas, like the terminal (`drawColor`), painted over everything
 * drawn before it: on phones the status bar, the terminal's bar (with the
 * copilot) and the tabs went black, only the key bar under it was left.
 */
@Composable
internal fun <T : View> ClippedAndroidView(
    factory: (Context) -> T,
    modifier: Modifier = Modifier,
    onRelease: (T) -> Unit = {},
    update: (T) -> Unit = {},
) {
    AndroidView(factory, modifier.clipToBounds(), onRelease = onRelease, update = update)
}
