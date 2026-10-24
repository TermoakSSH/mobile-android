package com.termoak.app.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.termoak.app.R
import com.termoak.app.data.SnippetIssueView
import com.termoak.app.sk.SecurityKeys
import com.termoak.app.sk.SkFixtures
import com.termoak.app.sk.SkOperation
import com.termoak.app.sk.SkPurpose
import com.termoak.app.sk.SkStage
import com.termoak.app.uiText
import com.termoak.ffi.SecurityKeyAlgorithm
import com.termoak.ffi.SecurityKeyInfo
import com.termoak.ffi.SnippetVariable
import com.termoak.ffi.SnippetVariableKind
import com.termoak.ffi.SshKey
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.concurrent.CompletableFuture

/**
 * Security keys and snippet variables drawn on the JVM (Robolectric): the
 * sheet ("Tap or plug in", a server's request with "Not on this phone",
 * "Touch your security key", the PIN after a wrong one, creating a key),
 * the New security key form, Load from security key, a key's details with
 * its authorized_keys line, the snippet editor's variables and issues and
 * the run dialog's fields. The PNGs are left in
 * app/build/screenshots/security-keys/.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi")
class SecurityKeyScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun string(id: Int, vararg args: Any) = rule.activity.getString(id, *args)

    private fun save(name: String) {
        rule.waitForIdle()
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        File("build/screenshots/security-keys/$name.png").apply { parentFile!!.mkdirs() }
            .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun show(dark: Boolean = false, content: @Composable () -> Unit) {
        rule.setContent {
            MaterialTheme(colorScheme = appColorScheme(dark), typography = appTypography) {
                // As in the app: a surface gives the text its color (the sheet's, the screen's).
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical = 16.dp)) { content() }
                }
            }
        }
    }

    private fun sheet(sheet: SecurityKeys.Sheet, dark: Boolean = false) = show(dark) {
        SecurityKeySheetContent(sheet, onCancel = {}, onNotHere = {}, onPin = {})
    }

    private val sign = SkFixtures.signRequest(ByteArray(8))

    @Before
    fun lines() {
        SkLines.line = { k, i ->
            listOfNotNull(if (i?.touchRequired == false) "no-touch-required" else null, if (i?.verifyRequired == true) "verify-required" else null)
                .joinToString(",").let { if (it.isEmpty()) k else "$it $k" }
        }
    }

    @After
    fun reset() {
        SkLines.line = { k, i -> com.termoak.ffi.securityKeyAuthorizedKeysLine(k, i) }
    }

    @Test
    fun tapOrPlugIn() {
        sheet(SecurityKeys.Sheet(SkOperation(SkPurpose.Sign(sign)), SkStage.Insert(nfc = true)))
        rule.onNodeWithText(string(R.string.sk_sheet_insert_title_nfc)).assertExists()
        rule.onNodeWithText("deploy@web1 (10.0.0.5)").assertExists()
        rule.onNodeWithTag("sk-not-here").assertDoesNotExist()
        save("sheet-tap-or-plug-in")
    }

    @Test
    fun aServerRequest() {
        var notHere = 0
        rule.setContent {
            MaterialTheme(colorScheme = appColorScheme(true), typography = appTypography) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                    Column(Modifier.padding(vertical = 16.dp)) {
                    SecurityKeySheetContent(
                        SecurityKeys.Sheet(SkOperation(SkPurpose.Sign(sign.copy(purpose = "server_session"))), SkStage.Insert(nfc = true)),
                        onCancel = {}, onNotHere = { notHere++ }, onPin = {},
                    )
                    }
                }
            }
        }
        rule.onNodeWithText(string(R.string.sk_purpose_server_session)).assertExists()
        rule.onNodeWithText(string(R.string.sk_sheet_insert_forwarded)).assertExists()
        save("sheet-server-request-dark")
        rule.onNodeWithTag("sk-not-here").performClick()
        assertEquals(1, notHere)
    }

    @Test
    fun touch() {
        sheet(SecurityKeys.Sheet(SkOperation(SkPurpose.Sign(sign)), SkStage.Touch), dark = true)
        rule.onNodeWithText(string(R.string.sk_sheet_touch_title)).assertExists()
        rule.onNodeWithText("YubiKey · YubiKey 5C NFC").assertExists()
        save("sheet-touch-dark")
    }

    @Test
    fun pinAfterAWrongOne() {
        sheet(SecurityKeys.Sheet(SkOperation(SkPurpose.Sign(sign)), SkStage.Insert(true), SecurityKeys.PinAsk(true, 2, CompletableFuture())))
        rule.onNodeWithText(string(R.string.sk_pin_wrong)).assertExists()
        rule.onNodeWithText(rule.activity.resources.getQuantityString(R.plurals.sk_pin_retries, 2, 2)).assertExists()
        save("sheet-pin-retry")
    }

    @Test
    @Config(qualifiers = "es-w411dp-h891dp-port-xxhdpi")
    fun pinInSpanish() {
        sheet(SecurityKeys.Sheet(SkOperation(SkPurpose.Sign(sign.copy(purpose = "test"))), SkStage.Insert(true), SecurityKeys.PinAsk(false, null, CompletableFuture())))
        rule.onNodeWithText("PIN de la llave de seguridad").assertExists()
        rule.onNodeWithText("Una prueba de conexión desde el servidor").assertExists()
        save("sheet-pin-es")
    }

    @Test
    fun creatingAKey() {
        sheet(SecurityKeys.Sheet(SkOperation(SkPurpose.Create("ssh:work", SecurityKeyAlgorithm.ED25519)), SkStage.Touch))
        rule.onNodeWithText("ssh:work").assertExists()
        save("sheet-create")
    }

    @Test
    fun newKeyForm() {
        show {
            Column(Modifier.padding(horizontal = 24.dp)) {
                Text(string(R.string.sk_new_title), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                SecurityKeyFormFields(SkForm(name = "YubiKey (work)", resident = true, noTouch = true, user = "oihana"), advanced = true, onAdvanced = {}) {}
            }
        }
        rule.onNodeWithText(string(R.string.sk_no_touch_warning)).assertExists()
        save("new-key-form")
    }

    @Test
    @Config(qualifiers = "es-w411dp-h891dp-port-xxhdpi")
    fun newKeyFormInSpanishAndLoad() {
        show(dark = true) {
            Column(Modifier.padding(horizontal = 24.dp)) {
                SecurityKeyFormFields(SkForm(application = "work"), advanced = true, onAdvanced = {}) {}
                Spacer(Modifier.height(24.dp))
                Text(string(R.string.sk_load_title), style = MaterialTheme.typography.titleLarge)
                LoadResidentFields(verify = true, onVerify = {}, device = "", onDevice = {}, available = true)
            }
        }
        rule.onNodeWithText("La aplicación tiene que empezar por ssh: (como mucho 128 caracteres)").assertExists()
        save("new-key-and-load-es-dark")
    }

    @Test
    fun keyDetails() {
        val info = SecurityKeyInfo("ssh:", null, touchRequired = false, verifyRequired = true, resident = true, device = "YubiKey 5C NFC")
        val key = SshKey(
            id = "k1", label = "YubiKey (work)", algorithm = "sk-ssh-ed25519@openssh.com",
            publicKey = "sk-ssh-ed25519@openssh.com AAAAGnNrLXNzaC1lZDI1NTE5QG9wZW5zc2guY29tAAAAIKx3 phone",
            fingerprint = "SHA256:q0Zr9V2PZ7Wm0rYxk3X1p8vO1ZcY4A0G6m3uN5Q2s1E", hasPrivateKey = true, securityKey = info,
        )
        show(dark = true) {
            Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Key, null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.padding(start = 12.dp)) {
                    Text(key.label, style = MaterialTheme.typography.titleMedium)
                    Text(key.algorithm, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SecurityKeyBadge(info)
                }
            }
            SecurityKeySection(key, info, editable = true, device = "YubiKey 5C NFC") {}
        }
        rule.onNodeWithTag("sk-authorized-line").assertExists()
        rule.onNodeWithText("no-touch-required,verify-required", substring = true).assertExists()
        rule.onNodeWithText(string(R.string.sk_handle_note)).assertExists()
        save("key-details-dark")
    }

    private fun v(label: String, kind: SnippetVariableKind, default: String? = null, options: List<String> = emptyList(), host: Boolean = false) =
        SnippetVariable(label, kind, label, default, options, host)

    @Test
    fun snippetEditorAndRun() {
        val vars = listOf(
            v("host", SnippetVariableKind.BUILTIN, host = true), v("Branch", SnippetVariableKind.PROMPT, "main"),
            v("Env", SnippetVariableKind.SELECT, "dev", listOf("dev", "staging", "prod")), v("Token", SnippetVariableKind.SECRET),
        )
        val issues = listOf(SnippetIssueView(2, 5, "{{select:Region}}", uiText(R.string.snippets_issue_select_options)))
        show {
            Column(Modifier.padding(horizontal = 24.dp)) {
                Text(string(R.string.snippets_edit), style = MaterialTheme.typography.titleLarge)
                Text("git pull origin {{prompt:Branch|main}}\nkubectl {{select:Region}}", style = Mono)
                Spacer(Modifier.height(8.dp))
                SnippetVariablesPanel(vars, issues)
                Spacer(Modifier.height(24.dp))
                Text("Deploy", style = MaterialTheme.typography.titleLarge)
                SnippetFields(vars.drop(1), mapOf("Branch" to "release", "Env" to "staging", "Token" to "hunter22"), missing = null) { _, _ -> }
            }
        }
        rule.onNodeWithTag("snippet-issue").assertExists()
        rule.onNodeWithTag("snippet-secret").assertExists()
        save("snippet-editor-and-run")
    }

    @Test
    @Config(qualifiers = "es-w411dp-h891dp-port-xxhdpi")
    fun snippetRunInSpanishWithAMissingValue() {
        val vars = listOf(v("host", SnippetVariableKind.BUILTIN, host = true), v("Servicio", SnippetVariableKind.PROMPT))
        show(dark = true) {
            Column(Modifier.padding(horizontal = 24.dp)) {
                SnippetFields(vars, mapOf("host" to "", "Servicio" to ""), missing = "host") { _, _ -> }
            }
        }
        rule.onNodeWithText("Falta el valor de «host»").assertExists()
        save("snippet-run-missing-es-dark")
    }
}
