package com.termoak.app.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.termoak.app.R
import com.termoak.app.data.AiProposal
import com.termoak.ffi.AiCommandSuggestion
import com.termoak.ffi.AiHostRun
import com.termoak.ffi.AiTaskStatus
import com.termoak.ffi.CommandFailure
import com.termoak.ffi.LastCommandInfo
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Screenshots of pieces new in this version, on the JVM (Robolectric draws
 * them with Android's graphics): the AI's chip and card over a terminal,
 * Files' selection and shared-files bars, and an AI task's per-host table
 * and plan. The PNGs are left in app/build/screenshots/phase2/.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// The plain Application: TermoakApp loads the engine (a native library for Android only).
@Config(application = Application::class, sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi")
class Phase2ScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun save(name: String): Bitmap {
        rule.waitForIdle()
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        File("build/screenshots/phase2/$name.png").apply { parentFile!!.mkdirs() }
            .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return bitmap
    }

    private fun string(id: Int, vararg args: Any) = rule.activity.getString(id, *args)

    @Test
    fun terminalAi() {
        val last = LastCommandInfo(command = "make", exitCode = 2, output = "make: *** No rule to make target 'x'.", failure = CommandFailure.Exit(2))
        val suggestion = AiCommandSuggestion("make all", "Builds the default target.", "write", "claude")
        rule.setContent {
            MaterialTheme(darkColorScheme()) {
                Column(Modifier.fillMaxWidth().background(Color(0xFF12151D)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FailedCommandChip(last, onExplain = {}, onFix = {}, onDismiss = {})
                    AiProposalCard(AiProposal("# build it", AiProposal.State.Ready(suggestion)), onType = {}, onDismiss = {})
                    AiProposalCard(AiProposal(null, AiProposal.State.Asking), onType = {}, onDismiss = {})
                }
            }
        }
        rule.onNodeWithText(string(R.string.terminal_ai_chip_failed_exit, 2)).assertExists()
        rule.onNodeWithText(string(R.string.terminal_ai_insert)).assertExists()
        rule.onNodeWithText(string(R.string.terminal_ai_risk_write)).assertExists()
        val shot = save("terminal-ai")
        assertTrue(shot.width > 0 && shot.height > 0)
    }

    @Test
    fun filesBars() {
        rule.setContent {
            MaterialTheme {
                Column {
                    IncomingBar(3, ready = true, onUpload = {}, onCancel = {})
                    SelectionBar(count = 2, all = false, onAll = {}, onShare = {}, onDownload = {}, onMove = {}, onDelete = {}, onClose = {})
                }
            }
        }
        rule.onNodeWithText(rule.activity.resources.getQuantityString(R.plurals.files_selected, 2, 2)).assertExists()
        rule.onNodeWithText(string(R.string.files_upload_here)).assertExists()
        save("files-bars")
    }

    @Test
    fun aiTaskHostsAndPlan() {
        val runs = listOf(
            AiHostRun("h1", "web-1", "t1", AiTaskStatus.COMPLETED, "nginx restarted", null, 65_000, 12_000, 0u),
            AiHostRun("h2", "web-2", "t2", AiTaskStatus.WAITING_APPROVAL, null, null, null, 0, 1u),
            AiHostRun("h3", "db-1", "t3", AiTaskStatus.FAILED, null, "connection refused", 800, 0, 0u),
        )
        rule.setContent {
            MaterialTheme {
                Column {
                    ApprovedPlan("1. Check nginx\n2. Restart it", edited = true)
                    HostRunsTable(runs) {}
                }
            }
        }
        rule.onNodeWithText("web-2").assertExists()
        rule.onNodeWithText("1 min 5 s · $0.01").assertExists()
        save("ai-task-hosts")
    }
}
