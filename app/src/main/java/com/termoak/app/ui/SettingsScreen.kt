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
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.KeyboardCommandKey
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Swipe
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
import com.termoak.app.data.WideLayout
import com.termoak.app.term.GestureMode
import com.termoak.ffi.AccountStatus
import com.termoak.ffi.TwoFactorStatus
import com.termoak.app.data.officialServer
import com.termoak.app.data.serverHost
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.ManageAccounts
import com.termoak.ffi.libraryVersion
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(app: TermoakApp, nav: NavHostController) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val loggedIn by app.accounts.loggedIn.collectAsState()
    val current by app.accounts.current.collectAsState()
    val accountList by app.accounts.list.collectAsState()
    // The current account's server, when it is signed in.
    val server = current?.takeIf { it.status == AccountStatus.ACTIVE }?.serverUrl
    val fontSize by app.prefs.fontSize.collectAsState()
    val keepOn by app.prefs.keepScreenOn.collectAsState()
    val vibrate by app.prefs.vibrateOnBell.collectAsState()
    val confirmPaste by app.prefs.confirmMultilinePaste.collectAsState()
    val keyBar by app.prefs.keyBarWithKeyboard.collectAsState()
    val theme by app.prefs.theme.collectAsState()
    val telnetAutoLogin by app.prefs.telnetAutoLogin.collectAsState()
    val wideLayout by app.prefs.wideLayout.collectAsState()
    var choosingLayout by remember { mutableStateOf(false) }
    val gestureMode by app.prefs.cursorGestures.collectAsState()
    var choosingGestures by remember { mutableStateOf(false) }
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

    LaunchedEffect(server) {
        twoFactor = if (server != null) runCatching { app.core.twoFactorStatus() }.getOrNull() else null
    }

    fun open(url: String) = context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    // Updates come from the current account's server, or from the official one.
    val updateServer = current?.serverUrl

    ScreenScaffold(title = stringResource(R.string.section_settings), large = true) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            // ----- Account -----
            SectionLabel(stringResource(R.string.settings_account))
            AccountsSettingsSection(app, nav)
            if (accountList.any { it.vaultsSupported }) {
                Row0(Icons.Outlined.Lock, stringResource(R.string.vaults_title), stringResource(R.string.vaults_hint)) {
                    nav.navigate(Routes.VAULTS)
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
                server?.let { url ->
                    Row0(Icons.AutoMirrored.Outlined.OpenInNew, stringResource(R.string.settings_my_account), serverHost(url)) {
                        open("$url/app/account")
                    }
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
            // The chosen mode and what it does (like the iOS app's explanation under the picker).
            Row0(
                Icons.Outlined.Swipe, stringResource(R.string.settings_cursor_gestures),
                stringResource(gestureModeName(gestureMode)) + "\n" + stringResource(gestureModeHint(gestureMode)),
            ) {
                choosingGestures = true
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
            SwitchRow(stringResource(R.string.settings_key_bar_keyboard), stringResource(R.string.settings_key_bar_keyboard_hint), keyBar) {
                app.prefs.setKeyBarWithKeyboard(it)
            }
            SwitchRow(stringResource(R.string.settings_telnet_auto_login), stringResource(R.string.settings_telnet_auto_login_hint), telnetAutoLogin) {
                app.prefs.setTelnetAutoLogin(it)
            }
            Row0(Icons.Outlined.KeyboardCommandKey, stringResource(R.string.kb_shortcuts), stringResource(R.string.settings_shortcuts_hint)) {
                KeyShortcuts.sheet.value = true
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
            Row0(Icons.Outlined.Devices, stringResource(R.string.settings_wide_layout), stringResource(wideLayoutName(wideLayout))) {
                choosingLayout = true
            }
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
                            resources.getString(R.string.settings_update_failed, serverHost(updateServer ?: officialServer)),
                        )
                    }
                }
            }
            SwitchRow(
                stringResource(R.string.settings_update_check),
                stringResource(R.string.settings_update_auto_hint, serverHost(updateServer ?: officialServer)),
                checkUpdates,
            ) { app.prefs.setCheckUpdates(it) }
            Row0(
                Icons.Outlined.Info, stringResource(R.string.settings_website),
                serverHost(server ?: officialServer),
            ) {
                open(server ?: officialServer)
            }
        }
    }

    if (choosingLayout) {
        WideLayoutDialog(wideLayout, onDismiss = { choosingLayout = false }) {
            choosingLayout = false
            app.prefs.setWideLayout(it)
        }
    }
    if (choosingGestures) {
        GestureModeDialog(gestureMode, onDismiss = { choosingGestures = false }) {
            choosingGestures = false
            app.prefs.setCursorGestures(it)
        }
    }
    if (choosingLanguage) {
        LanguageDialog(languages, language, onDismiss = { choosingLanguage = false }) { tag ->
            choosingLanguage = false
            if (tag != language) AppLanguage.choose(app, tag)
        }
    }
}

@androidx.annotation.StringRes
private fun wideLayoutName(mode: WideLayout): Int = when (mode) {
    WideLayout.AUTO -> R.string.settings_wide_layout_auto
    WideLayout.PHONE -> R.string.settings_wide_layout_phone
    WideLayout.DESKTOP -> R.string.settings_wide_layout_desktop
}

/** "Layout on wide screens": Automatic, Phone layout or Desktop layout, each with what it does. */
@Composable
private fun WideLayoutDialog(selected: WideLayout, onDismiss: () -> Unit, onSelect: (WideLayout) -> Unit) {
    val options = listOf(
        WideLayout.AUTO to R.string.settings_wide_layout_auto_hint,
        WideLayout.PHONE to R.string.settings_wide_layout_phone_hint,
        WideLayout.DESKTOP to R.string.settings_wide_layout_desktop_hint,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_wide_layout)) },
        text = {
            Column {
                options.forEach { (mode, hint) ->
                    Row(
                        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable { onSelect(mode) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = mode == selected, onClick = { onSelect(mode) })
                        Column(Modifier.padding(start = 8.dp)) {
                            Text(stringResource(wideLayoutName(mode)), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                stringResource(hint), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

@androidx.annotation.StringRes
private fun gestureModeName(mode: GestureMode): Int = when (mode) {
    GestureMode.HOLD -> R.string.settings_gestures_hold
    GestureMode.ONE_FINGER -> R.string.settings_gestures_one_finger
    GestureMode.TWO_FINGERS -> R.string.settings_gestures_two_fingers
    GestureMode.BUTTON -> R.string.settings_gestures_button
    GestureMode.OFF -> R.string.settings_gestures_off
}

@androidx.annotation.StringRes
private fun gestureModeHint(mode: GestureMode): Int = when (mode) {
    GestureMode.HOLD -> R.string.settings_gestures_hold_hint
    GestureMode.ONE_FINGER -> R.string.settings_gestures_one_finger_hint
    GestureMode.TWO_FINGERS -> R.string.settings_gestures_two_fingers_hint
    GestureMode.BUTTON -> R.string.settings_gestures_button_hint
    GestureMode.OFF -> R.string.settings_gestures_off_hint
}

/** "Cursor gestures": how a finger moves the cursor, each mode with what it does (the iOS app's picker). */
@Composable
private fun GestureModeDialog(selected: GestureMode, onDismiss: () -> Unit, onSelect: (GestureMode) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_cursor_gestures)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                GestureMode.entries.forEach { mode ->
                    Row(
                        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable { onSelect(mode) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = mode == selected, onClick = { onSelect(mode) })
                        Column(Modifier.padding(start = 8.dp)) {
                            Text(stringResource(gestureModeName(mode)), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                stringResource(gestureModeHint(mode)), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
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
