package com.termoak.app.ui

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.abs

/**
 * Screenshots of the terminal screen's frame (TerminalFrame) on the JVM:
 * Robolectric draws it with Android's own graphics, with the system bars,
 * display cutout and keyboard given as window insets. A stand-in terminal
 * view fills its canvas like TerminalView does (`drawColor`), in the same
 * kind of AndroidView, so a terminal painting over the bars (the black bar
 * of 0.4.0 on phones) shows up here. The PNGs are left in
 * app/build/screenshots/terminal/ to look at; the checks read a few pixels.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// The plain Application: TermoakApp loads the engine (a native library for Android only).
@Config(application = Application::class, sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi")
class TerminalFrameScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    /** Window insets in dp. */
    private data class Bars(
        val status: Int = 0,
        val nav: Insets = Insets.NONE,
        val cutout: Insets = Insets.NONE,
        val ime: Int = 0,
    )

    private class Shot(val bitmap: Bitmap, val density: Float) {
        fun at(xDp: Int, yDp: Int): Int = bitmap.getPixel((xDp * density).toInt(), (yDp * density).toInt())
    }

    private fun render(
        name: String,
        bars: Bars,
        desktop: Boolean = false,
        wide: Boolean = false,
        copilotOpen: Boolean = false,
    ): Shot {
        val activity = rule.activity
        rule.runOnUiThread { activity.enableEdgeToEdge() }
        rule.setContent {
            MaterialTheme {
                TerminalFrame(
                    desktop = desktop,
                    wide = wide,
                    copilotOpen = copilotOpen,
                    onCloseCopilot = {},
                    header = { if (desktop) DesktopToolbar() else PhoneBars() },
                    terminal = { ClippedAndroidView(factory = ::FakeTerminalView, modifier = Modifier.fillMaxSize()) },
                    footer = { KeyBar() },
                ) { modifier, _ -> Copilot(modifier) }
            }
        }
        val d = activity.resources.displayMetrics.density
        fun px(dp: Int) = (dp * d).toInt()
        fun px(i: Insets) = Insets.of(px(i.left), px(i.top), px(i.right), px(i.bottom))
        val insets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, px(bars.status), 0, 0))
            .setInsets(WindowInsetsCompat.Type.navigationBars(), px(bars.nav))
            .setInsets(WindowInsetsCompat.Type.displayCutout(), px(bars.cutout))
            .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, px(bars.ime)))
            .setVisible(WindowInsetsCompat.Type.ime(), bars.ime > 0)
            .build()
        rule.runOnUiThread { ViewCompat.dispatchApplyWindowInsets(activity.window.decorView, insets) }
        rule.waitForIdle()
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        File("build/screenshots/terminal/$name.png").apply { parentFile!!.mkdirs() }
            .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return Shot(bitmap, d)
    }

    private fun assertColor(expected: Color, actual: Int, what: String) {
        val e = expected.toArgb()
        val close = listOf(16, 8, 0).all { shift -> abs((e shr shift and 0xFF) - (actual shr shift and 0xFF)) <= 6 }
        assertTrue("$what: expected #%06X, was #%06X".format(e and 0xFFFFFF, actual and 0xFFFFFF), close)
    }

    @Test
    fun phonePortrait() {
        val shot = render("phone-portrait", Bars(status = 48, nav = Insets.of(0, 0, 0, 24)))
        // Status bar: the screen's background; the terminal's bar and tabs under it, not the terminal's black.
        assertColor(TermScreenBg, shot.at(200, 20), "status bar")
        assertColor(TermBarBg, shot.at(200, 50), "top bar")
        assertColor(TermBarBg, shot.at(400, 102), "tabs")
        assertColor(FakeTerminalBg, shot.at(380, 400), "terminal")
        // The key bar right above the gesture bar.
        assertColor(TermBarBg, shot.at(408, 891 - 24 - 3), "key bar")
        assertColor(TermScreenBg, shot.at(200, 891 - 10), "gesture bar")
    }

    @Test
    fun phonePortraitKeyboard() {
        // The terminal's keyboard: the terminal gets shorter, the bars stay.
        val shot = render("phone-portrait-keyboard", Bars(status = 48, nav = Insets.of(0, 0, 0, 24), ime = 320))
        assertColor(TermBarBg, shot.at(200, 50), "top bar")
        assertColor(TermBarBg, shot.at(400, 102), "tabs")
        assertColor(FakeTerminalBg, shot.at(380, 300), "terminal")
        assertColor(TermBarBg, shot.at(408, 891 - 320 - 3), "key bar above the keyboard")
    }

    @Test
    fun phonePortraitCopilotKeyboard() {
        // The copilot's keyboard goes over the terminal (it keeps its size); the copilot stays above it.
        val shot = render(
            "phone-portrait-copilot-keyboard",
            Bars(status = 48, nav = Insets.of(0, 0, 0, 24), ime = 320),
            copilotOpen = true,
        )
        assertColor(CopilotBg, shot.at(380, 891 - 320 - 6), "copilot above the keyboard")
        // The terminal's key bar is still at the bottom (under the keyboard on a device).
        assertColor(TermBarBg.dimmed(), shot.at(30, 891 - 24 - 3), "key bar, under the scrim")
        assertColor(TermBarBg.dimmed(), shot.at(30, 50), "top bar, under the scrim")
    }

    @Test
    @Config(qualifiers = "w891dp-h411dp-land-xxhdpi")
    fun phoneLandscapeCutout() {
        // Punch-hole on the left, three-button navigation on the right.
        val shot = render(
            "phone-landscape-cutout",
            Bars(status = 24, nav = Insets.of(0, 0, 48, 0), cutout = Insets.of(40, 0, 0, 0)),
            wide = true,
        )
        assertColor(TermScreenBg, shot.at(20, 40), "cutout")
        assertColor(TermBarBg, shot.at(400, 26), "top bar")
        assertColor(TermScreenBg, shot.at(891 - 20, 200), "navigation bar")
        assertColor(FakeTerminalBg, shot.at(500, 200), "terminal")
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-land-xhdpi")
    fun tabletCopilot() {
        val shot = render("tablet-copilot", Bars(status = 24, nav = Insets.of(0, 0, 0, 48)), wide = true, copilotOpen = true)
        assertColor(TermBarBg, shot.at(400, 26), "top bar")
        assertColor(FakeTerminalBg, shot.at(400, 400), "terminal")
        assertColor(CopilotBg, shot.at(1280 - 100, 400), "copilot beside the terminal")
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-land-xhdpi")
    fun desktopKeyboard() {
        // Desktop layout: the window's tab bar (not here) keeps clear of the status bar.
        val shot = render("desktop-keyboard", Bars(status = 24, nav = Insets.of(0, 0, 0, 48), ime = 300), desktop = true, wide = true)
        assertColor(TermBarBg, shot.at(600, 2), "toolbar")
        assertColor(FakeTerminalBg, shot.at(600, 300), "terminal")
        assertColor(TermBarBg, shot.at(1270, 800 - 300 - 3), "key bar above the keyboard")
    }
}

private val FakeTerminalBg = Color(0xFF000000)
private val CopilotBg = Color(0xFF283042)

/** [this] under the copilot's scrim (black at 45 %). */
private fun Color.dimmed() = Color(red * 0.55f, green * 0.55f, blue * 0.55f)

/** Draws like TerminalView: the whole canvas in the terminal's background, then the lines. */
private class FakeTerminalView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 13f, resources.displayMetrics)
    }
    private val lines = listOf(
        "Welcome to Ubuntu 24.04 LTS" to 0xFFD6DBE4,
        "user@server:~$ ls" to 0xFF3FB27F,
        "Documents  projects  notes.txt" to 0xFF4F7CFF,
        "user@server:~$ " to 0xFF3FB27F,
    )

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(FakeTerminalBg.toArgb())
        val h = paint.fontSpacing
        lines.forEachIndexed { i, (text, color) ->
            paint.color = color.toInt()
            canvas.drawText(text, 12f, 6f + h * (i + 1), paint)
        }
    }
}

/** The phone's terminal bar and tabs (as in TerminalScreen). */
@Composable
private fun PhoneBars() {
    Row(Modifier.fillMaxWidth().background(TermBarBg).height(52.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = {}) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = TermKeyFg) }
        Column(Modifier.weight(1f)) {
            Text("server", color = TermKeyFg, style = MaterialTheme.typography.titleSmall)
            Text("SSH from this phone", color = TermKeyFg.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall)
        }
        IconButton(onClick = {}) { Icon(Icons.Outlined.AutoAwesome, null, tint = TermKeyFg) }
        IconButton(onClick = {}) { Icon(Icons.Outlined.Code, null, tint = TermKeyFg) }
        IconButton(onClick = {}) { Icon(Icons.Outlined.Keyboard, null, tint = TermKeyFg) }
        IconButton(onClick = {}) { Icon(Icons.Outlined.MoreVert, null, tint = TermKeyFg) }
    }
    Row(
        Modifier.fillMaxWidth().background(TermBarBg).padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf("server" to true, "backup" to false).forEach { (name, selected) ->
            Box(
                Modifier.height(32.dp).background(if (selected) TermKeyBg else TermBarBg, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) { Text(name, color = TermKeyFg, fontSize = 13.sp) }
        }
        Icon(Icons.Outlined.Add, null, Modifier.size(32.dp).padding(7.dp), tint = TermKeyFg)
    }
}

/** The desktop layout's terminal toolbar (as in TerminalScreen). */
@Composable
private fun DesktopToolbar() {
    Row(Modifier.fillMaxWidth().background(TermBarBg).height(44.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("server", Modifier.weight(1f), color = TermKeyFg, style = MaterialTheme.typography.titleSmall)
        Icon(Icons.Outlined.AutoAwesome, null, tint = TermKeyFg)
    }
}

/** The key bar (as in TerminalScreen). */
@Composable
private fun KeyBar() {
    Row(
        Modifier.fillMaxWidth().background(TermBarBg).horizontalScroll(rememberScrollState()).padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        listOf("esc", "tab", "ctrl", "alt", "←", "↑", "↓", "→", "/", "-", "|", "~").forEach { key ->
            Box(Modifier.widthIn(min = 44.dp).height(40.dp).background(TermKeyBg, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                Text(key, color = TermKeyFg, fontFamily = FontFamily.Monospace, fontSize = 15.sp)
            }
        }
    }
}

/** The copilot panel, with its box to write in at the bottom. */
@Composable
private fun Copilot(modifier: Modifier) {
    Surface(modifier, color = CopilotBg) {
        Column(Modifier.padding(16.dp)) {
            Text("Copilot", color = TermKeyFg, style = MaterialTheme.typography.titleMedium)
            Box(Modifier.weight(1f))
            Box(Modifier.fillMaxWidth().height(48.dp).background(TermKeyBg, RoundedCornerShape(24.dp)).padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
                Text("Ask about this terminal…", color = TermKeyFg.copy(alpha = 0.6f))
            }
        }
    }
}
