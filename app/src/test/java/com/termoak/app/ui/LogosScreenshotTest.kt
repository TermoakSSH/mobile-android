package com.termoak.app.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.termoak.app.data.HostLogos
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Every host logo as the hosts show it (the systems and the generic icons
 * of the desktop's set), plus the automatic cases (detected system, the
 * initial) and the Telnet badge, in app/build/screenshots/logos/logos.png.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi")
class LogosScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @OptIn(ExperimentalLayoutApi::class)
    @Test
    fun allLogos() {
        rule.setContent {
            MaterialTheme {
                Column(Modifier.background(MaterialTheme.colorScheme.surface).padding(12.dp)) {
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        HostLogos.all.forEach { logo ->
                            Column(Modifier.width(60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                HostTile("web", null, size = 44.dp, icon = logo.id)
                                Text(logo.id, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 1)
                            }
                        }
                        // Automatic: the detected system, an unknown one, none (the initial); the desktop's initials.
                        listOf<Pair<String, @androidx.compose.runtime.Composable () -> Unit>>(
                            "os: debian" to { HostTile("db", "debian", size = 44.dp) },
                            "unknown" to { HostTile("web", "solaris", size = 44.dp) },
                            "initial" to { HostTile("web server", null, size = 44.dp) },
                            "initials" to { HostTile("web server", null, size = 44.dp, twoInitials = true) },
                            "later id" to { HostTile("web", "ubuntu", size = 44.dp, icon = "quantum") },
                        ).forEach { (name, tile) ->
                            Column(Modifier.width(60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                tile()
                                Text(name, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                            }
                        }
                        TelnetBadge(Modifier.padding(top = 12.dp))
                    }
                }
            }
        }
        rule.waitForIdle()
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/screenshots/logos").apply { mkdirs() }
        File(dir, "logos.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
