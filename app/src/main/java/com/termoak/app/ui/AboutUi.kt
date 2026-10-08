package com.termoak.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.termoak.app.R
import com.termoak.app.TermoakApp

/** A piece of software in the app and its license (a bundled text, or its page). */
private class Component(val name: String, val license: String, val url: String, val asset: String? = null)

private val Components = listOf(
    Component("Termoak for Android", "GNU AGPL v3", "https://github.com/TermoakSSH/mobile-android"),
    Component("Termoak core", "GNU AGPL v3", "https://github.com/TermoakSSH/core"),
    Component("Jetpack Compose, AndroidX", "Apache 2.0", "https://developer.android.com/jetpack/androidx"),
    Component("Kotlin, kotlinx.coroutines", "Apache 2.0", "https://github.com/Kotlin/kotlinx.coroutines"),
    Component("JNA", "Apache 2.0 / LGPL 2.1", "https://github.com/java-native-access/jna"),
    Component("Simple Icons", "CC0 1.0", "https://simpleicons.org"),
    Component("Lucide", "ISC", "https://lucide.dev"),
    Component("JetBrains Mono", "SIL OFL 1.1", "https://github.com/JetBrains/JetBrainsMono", "fonts/OFL-jetbrainsmono.txt"),
    Component("Fira Code", "SIL OFL 1.1", "https://github.com/tonsky/FiraCode", "fonts/OFL-firacode.txt"),
    Component("Source Code Pro", "SIL OFL 1.1", "https://github.com/adobe-fonts/source-code-pro", "fonts/OFL-sourcecodepro.txt"),
)

/** "Licenses" (as on iOS): the software in the app; the fonts' full licenses come with it. */
@Composable
fun LicensesDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var text by remember { mutableStateOf<Pair<String, String>?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.about_licenses)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Components.forEach { c ->
                    ListItem(
                        modifier = Modifier.clickable {
                            val bundled = c.asset?.let { a -> runCatching { context.assets.open(a).bufferedReader().use { it.readText() } }.getOrNull() }
                            if (bundled != null) text = c.name to bundled else openUrl(context, c.url)
                        },
                        headlineContent = { Text(c.name) },
                        supportingContent = { Text(c.license) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
                Text(
                    stringResource(R.string.about_licenses_footer), Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) } },
    )
    text?.let { (title, body) ->
        AlertDialog(
            onDismissRequest = { text = null },
            title = { Text(title) },
            text = {
                SelectionContainer(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                    Text(body, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }
            },
            confirmButton = { TextButton(onClick = { text = null }) { Text(stringResource(R.string.common_close)) } },
        )
    }
}

/** How this phone appears in the accounts: chosen here, else its maker and model. */
@Composable
fun DeviceNameDialog(app: TermoakApp, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(app.prefs.deviceName.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.about_device_name)) },
        text = {
            Column {
                OutlinedTextField(
                    name, { name = it.take(64) }, Modifier.fillMaxWidth(), singleLine = true,
                    placeholder = { Text(app.systemDeviceName) },
                )
                Text(
                    stringResource(R.string.about_device_name_footer), Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                app.prefs.deviceName = name
                runCatching { app.core.setDeviceName(app.prefs.deviceName ?: app.systemDeviceName) }
                onDismiss()
            }) { Text(stringResource(R.string.common_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}
