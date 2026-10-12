package com.termoak.app.store

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ActivityScenario
import com.termoak.app.R
import com.termoak.app.data.Prefs
import com.termoak.app.data.ThemeMode
import com.termoak.app.ui.Brand
import com.termoak.app.ui.TermoakTheme
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * The store's feature graphic (1024×500, per language) and its 512×512 icon,
 * from the app's own logo, launcher icon layers, theme colours and fonts.
 * Run with the store screenshots (scripts/screenshots.sh); PNGs in
 * app/build/store/<lang>/featureGraphic.png and app/build/store/icon.png.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = android.app.Application::class, sdk = [34])
class StoreGraphicsTest(private val lang: StoreScreenshotTest.Lang) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun params(): List<Array<Any>> = StoreScreenshotTest.Lang.entries.map { arrayOf<Any>(it) }
    }

    @get:Rule val compose = createEmptyComposeRule()

    private fun enabled() = assumeTrue("store graphics: run scripts/screenshots.sh", System.getProperty("termoak.store") == "1")

    @Test
    fun featureGraphic() {
        enabled()
        RuntimeEnvironment.setQualifiers("${lang.qualifier}-w1024dp-h500dp-land-mdpi")
        val es = lang == StoreScreenshotTest.Lang.ES
        val tagline = if (es) "Cliente SSH con sesiones que no se cortan" else "SSH client with sessions that never drop"
        val features = if (es) listOf("Bóveda cifrada", "SFTP", "Vista dividida", "Copiloto IA")
        else listOf("Encrypted vault", "SFTP", "Split view", "AI copilot")
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario.onActivity { a ->
            Prefs(a).setTheme(ThemeMode.DARK)
            a.setContent {
                TermoakTheme(Prefs(a)) {
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.linearGradient(listOf(Color(0xFF182216), Color(0xFF12151D), Color(0xFF0F121A))),
                        ),
                    ) {
                        // A soft glow of the olive's green behind the logo.
                        Box(
                            Modifier.fillMaxSize().background(
                                Brush.radialGradient(
                                    listOf(Color(0x403FB27F), Color(0x00000000)),
                                    center = androidx.compose.ui.geometry.Offset(230f, 250f), radius = 420f,
                                ),
                            ),
                        )
                        Row(Modifier.fillMaxSize().padding(start = 84.dp, end = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painterResource(R.drawable.logo), null,
                                Modifier.size(236.dp).clip(RoundedCornerShape(52.dp)),
                            )
                            Spacer(Modifier.width(52.dp))
                            Column(verticalArrangement = Arrangement.Center) {
                                Text(
                                    "Termoak", color = Color.White, fontSize = 92.sp, fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.displayLarge, letterSpacing = (-1).sp,
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(tagline, color = Color(0xFFD6DBE4), fontSize = 30.sp, lineHeight = 38.sp)
                                Spacer(Modifier.height(22.dp))
                                Text(
                                    "$ ssh ane@web-1", color = Brand.Green, fontSize = 22.sp, fontFamily = FontFamily.Monospace,
                                )
                                Spacer(Modifier.height(10.dp))
                                Text(features.joinToString("  ·  "), color = Color(0xFF8E97AB), fontSize = 21.sp)
                            }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        check(bitmap.width == 1024 && bitmap.height == 500) { "feature graphic: ${bitmap.width}×${bitmap.height}" }
        Png.writeOpaque(bitmap, File("build/store/${lang.dir}/featureGraphic.png"))
        scenario.close()
    }

    /**
     * The 512×512 store icon: the launcher icon's layers (its background
     * colour and foreground), the part a launcher shows (the inner 72 of
     * 108 dp), full square; the store rounds its corners.
     */
    @Test
    fun icon() {
        enabled()
        if (lang != StoreScreenshotTest.Lang.EN) return
        val context = RuntimeEnvironment.getApplication()
        val fg = BitmapFactory.decodeResource(context.resources, R.drawable.ic_launcher_foreground, BitmapFactory.Options().apply { inScaled = false })
        val out = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(context.getColor(R.color.icon_background))
        val inset = fg.width * 18 / 108
        canvas.drawBitmap(
            fg, Rect(inset, inset, fg.width - inset, fg.height - inset), Rect(0, 0, 512, 512),
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
        )
        Png.writeOpaque(out, File("build/store/icon.png"))
    }
}
