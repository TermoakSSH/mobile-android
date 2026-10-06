package com.termoak.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.termoak.app.AppLanguage
import com.termoak.app.BuildConfig
import com.termoak.app.R
import com.termoak.app.TermoakApp
import com.termoak.app.data.Prefs
import com.termoak.app.data.ThemeMode
import com.termoak.ffi.TwoFactorStatus
import com.termoak.ffi.libraryVersion
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(app: TermoakApp, nav: NavHostController) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val loggedIn by app.account.loggedIn.collectAsState()
    val verification by app.account.verification.collectAsState()
    val server by app.account.serverUrl.collectAsState()
    val user by app.account.user.collectAsState()
    val online by app.account.online.collectAsState()
    val syncing by app.account.syncing.collectAsState()
    val lastSync by app.account.lastSync.collectAsState()
    val fontSize by app.prefs.fontSize.collectAsState()
    val keepOn by app.prefs.keepScreenOn.collectAsState()
    val vibrate by app.prefs.vibrateOnBell.collectAsState()
    val confirmPaste by app.prefs.confirmMultilinePaste.collectAsState()
    val theme by app.prefs.theme.collectAsState()
    var twoFactor by remember { mutableStateOf<TwoFactorStatus?>(null) }
    val checkUpdates by app.prefs.checkUpdates.collectAsState()
    val latest by app.updates.latest.collectAsState()
    // Checked from this screen: says "latest version" when there's nothing newer.
    var checked by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    val languages = remember { AppLanguage.available(context) }
    // Changing it recreates the screen, so it is read once.
    val language = remember { AppLanguage.chosen() }
    var choosingLanguage by remember { mutableStateOf(false) }

    LaunchedEffect(loggedIn) {
        twoFactor = if (loggedIn == true) runCatching { app.core.twoFactorStatus() }.getOrNull() else null
    }

    fun open(url: String) = context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    // Updates come from the server signed in to, or from termoak.com.
    val updateServer = server.takeIf { loggedIn == true || verification != null }

    ScreenScaffold(title = stringResource(R.string.section_settings), large = true) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            // ----- Account -----
            SectionLabel(stringResource(R.string.settings_account))
            CardBox {
                if (loggedIn == true) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            HostTile(user ?: "?", null, size = 44.dp)
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(user ?: stringResource(R.string.drawer_signed_in), style = MaterialTheme.typography.titleMedium)
                                Text(server?.removePrefix("https://") ?: "", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            StatusDot(if (online) Brand.Green else Brand.Amber)
                        }
                        val status = when {
                            syncing -> stringResource(R.string.common_syncing)
                            lastSync != null -> stringResource(R.string.settings_synced_ago, relativeTime(lastSync))
                            else -> stringResource(R.string.settings_never_synced)
                        }
                        Text(
                            if (online) stringResource(R.string.settings_sync_live, status) else status,
                            Modifier.padding(top = 10.dp), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { app.account.sync() }, enabled = !syncing) {
                                Text(stringResource(R.string.common_sync))
                            }
                            OutlinedButton(onClick = { server?.let { open("$it/app/account") } }) {
                                Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, Modifier.padding(end = 6.dp))
                                Text(stringResource(R.string.settings_my_account))
                            }
                        }
                    }
                } else if (verification != null) {
                    Column(Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.verify_pending), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.verify_prompt, verification?.email ?: ""),
                            Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(onClick = { nav.navigate(Routes.VERIFY_EMAIL) }, modifier = Modifier.padding(top = 12.dp)) {
                            Text(stringResource(R.string.verify_enter_code))
                        }
                    }
                } else {
                    Column(Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.settings_no_server), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.settings_no_server_text),
                            Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(onClick = { nav.navigate(Routes.LOGIN) }, modifier = Modifier.padding(top = 12.dp)) {
                            Icon(Icons.AutoMirrored.Outlined.Login, null, Modifier.padding(end = 8.dp))
                            Text(stringResource(R.string.common_sign_in))
                        }
                    }
                }
            }
            if (loggedIn == true) {
                twoFactor?.let { tf ->
                    val codes = tf.recoveryCodesLeft.toInt()
                    Row0(
                        Icons.Outlined.Security, stringResource(R.string.settings_two_factor),
                        if (tf.enabled) pluralStringResource(R.plurals.settings_two_factor_on, codes, codes)
                        else stringResource(R.string.settings_two_factor_off),
                        trailing = {
                            Pill(
                                stringResource(if (tf.enabled) R.string.settings_two_factor_active else R.string.settings_two_factor_inactive),
                                if (tf.enabled) Brand.Green else Brand.Amber,
                            )
                        },
                    ) { server?.let { open("$it/app/account") } }
                }
                Row0(Icons.Outlined.AutoAwesome, stringResource(R.string.section_ai), stringResource(R.string.settings_ai_hint)) {
                    nav.navigate(Routes.AI_KEYS)
                }
            }

            // ----- Vault -----
            SectionLabel(stringResource(R.string.settings_vault))
            Row0(Icons.Outlined.Key, stringResource(R.string.section_keychain), stringResource(R.string.settings_keychain_hint)) {
                nav.goTab(Routes.keys())
            }
            Row0(Icons.Outlined.Code, stringResource(R.string.section_snippets), stringResource(R.string.settings_snippets_hint)) {
                nav.goTab(Routes.SNIPPETS)
            }

            // ----- Terminal -----
            SectionLabel(stringResource(R.string.settings_terminal))
            Column(Modifier.padding(horizontal = 20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.settings_font_size), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.settings_font_size_value, fontSize.toInt()), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Slider(fontSize, { app.prefs.setFontSize(it) }, valueRange = Prefs.MIN_FONT..Prefs.MAX_FONT, steps = 15)
                Text(stringResource(R.string.settings_font_size_hint), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SwitchRow(stringResource(R.string.settings_keep_screen_on), stringResource(R.string.settings_keep_screen_on_hint), keepOn) {
                app.prefs.setKeepScreenOn(it)
            }
            SwitchRow(stringResource(R.string.settings_vibrate_bell), stringResource(R.string.settings_vibrate_bell_hint), vibrate) {
                app.prefs.setVibrateOnBell(it)
            }
            SwitchRow(stringResource(R.string.settings_confirm_paste), stringResource(R.string.settings_confirm_paste_hint), confirmPaste) {
                app.prefs.setConfirmMultilinePaste(it)
            }

            // ----- Appearance -----
            SectionLabel(stringResource(R.string.settings_appearance))
            val modes = listOf(
                ThemeMode.SYSTEM to R.string.settings_theme_system,
                ThemeMode.DARK to R.string.settings_theme_dark,
                ThemeMode.LIGHT to R.string.settings_theme_light,
            )
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                modes.forEachIndexed { i, (m, label) ->
                    SegmentedButton(theme == m, { app.prefs.setTheme(m) }, SegmentedButtonDefaults.itemShape(i, modes.size)) {
                        Text(stringResource(label))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row0(
                Icons.Outlined.Language, stringResource(R.string.settings_language),
                languages.firstOrNull { it.tag == language }?.name ?: stringResource(R.string.settings_language_system),
            ) { choosingLanguage = true }

            // ----- About -----
            SectionLabel(stringResource(R.string.settings_about))
            val update = latest?.takeIf { it.isNewer }
            Row0(
                Icons.Outlined.SystemUpdate,
                when {
                    checking -> stringResource(R.string.settings_update_checking)
                    update != null -> stringResource(R.string.settings_update_available, update.version)
                    checked -> stringResource(R.string.settings_update_latest)
                    else -> stringResource(R.string.settings_update_check_now)
                },
                stringResource(R.string.settings_version, BuildConfig.VERSION_NAME, runCatching { libraryVersion() }.getOrDefault("?")),
                trailing = {
                    update?.let { u ->
                        Button(onClick = { openUpdate(context, u) }) { Text(stringResource(R.string.settings_download)) }
                    }
                },
            ) {
                if (checking) return@Row0
                checking = true
                scope.launch {
                    val ok = app.updates.check(updateServer)
                    checking = false
                    checked = ok
                    if (!ok) {
                        snackbar.showSnackbar(
                            resources.getString(R.string.settings_update_failed, (updateServer ?: BuildConfig.DEFAULT_SERVER).removePrefix("https://")),
                        )
                    }
                }
            }
            SwitchRow(
                stringResource(R.string.settings_update_check),
                stringResource(R.string.settings_update_auto_hint, (updateServer ?: BuildConfig.DEFAULT_SERVER).removePrefix("https://")),
                checkUpdates,
            ) { app.prefs.setCheckUpdates(it) }
            Row0(
                Icons.Outlined.Info, stringResource(R.string.settings_website),
                (server ?: BuildConfig.DEFAULT_SERVER).removePrefix("https://"),
            ) {
                open(server ?: BuildConfig.DEFAULT_SERVER)
            }
            if (loggedIn == true) {
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                Row0(
                    Icons.AutoMirrored.Filled.Logout, stringResource(R.string.settings_sign_out),
                    stringResource(R.string.settings_sign_out_hint), danger = true,
                ) {
                    app.sessions.closeAll()
                    app.account.logout()
                    scope.launch { snackbar.showSnackbar(resources.getString(R.string.settings_signed_out)) }
                }
            }
        }
    }

    if (choosingLanguage) {
        LanguageDialog(languages, language, onDismiss = { choosingLanguage = false }) { tag ->
            choosingLanguage = false
            if (tag != language) AppLanguage.choose(app, tag)
        }
    }
}

/** "System default" and every language the app is translated into. */
@Composable
private fun LanguageDialog(
    languages: List<AppLanguage.Language>,
    selected: String?,
    onDismiss: () -> Unit,
    onSelect: (String?) -> Unit,
) {
    val options = listOf<Pair<String?, String>>(null to stringResource(R.string.settings_language_system)) +
        languages.map { it.tag to it.name }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_language)) },
        text = {
            Column {
                options.forEach { (tag, name) ->
                    Row(
                        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable { onSelect(tag) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = tag == selected, onClick = { onSelect(tag) })
                        Text(name, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

@Composable
private fun Row0(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    danger: Boolean = false,
    trailing: @Composable (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(title, color = if (danger) MaterialTheme.colorScheme.error else Color.Unspecified) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(icon, null, tint = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) },
        trailingContent = trailing,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        modifier = Modifier.clickable { onChange(!checked) },
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = { Switch(checked, onChange) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}
