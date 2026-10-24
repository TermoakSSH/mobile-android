package com.termoak.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.termoak.app.R
import com.termoak.app.TermoakApp
import com.termoak.app.userMessage
import com.termoak.ffi.SecretChange
import com.termoak.ffi.SecurityKeyAlgorithm
import com.termoak.ffi.SecurityKeyInfo
import com.termoak.ffi.SecurityKeyOptions
import com.termoak.ffi.SshKey
import com.termoak.ffi.SyncMode
import com.termoak.ffi.TermoakException
import com.termoak.ffi.securityKeyAuthorizedKeysLine
import kotlinx.coroutines.launch

/** The form of a new key on a security key (the desktop's "New security key"). */
data class SkForm(
    val name: String = "",
    val algorithm: SecurityKeyAlgorithm = SecurityKeyAlgorithm.ED25519,
    val application: String = "ssh:",
    val user: String = "",
    val resident: Boolean = false,
    val verify: Boolean = false,
    val noTouch: Boolean = false,
    val device: String = "",
) {
    /** What is wrong with it, or `null`. */
    fun problem(): Int? = when {
        !application.startsWith("ssh:") || application.length > 128 -> R.string.sk_application_invalid
        user.toByteArray(Charsets.UTF_8).size > 32 -> R.string.sk_user_too_long
        else -> null
    }

    fun options(): SecurityKeyOptions = SecurityKeyOptions(
        algorithm = algorithm,
        application = application.trim().takeIf { it.isNotEmpty() && it != "ssh:" },
        user = user.trim().ifEmpty { null },
        resident = resident,
        verifyRequired = verify,
        noTouchRequired = noTouch,
        device = device.trim().take(64).ifEmpty { null },
    )
}

/** The fields of [SkForm] (also drawn by the screenshot tests). */
@Composable
fun SecurityKeyFormFields(form: SkForm, advanced: Boolean, onAdvanced: (Boolean) -> Unit, onChange: (SkForm) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.sk_new_intro), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(form.name, { onChange(form.copy(name = it)) }, Modifier.fillMaxWidth(), singleLine = true,
            label = { Text(stringResource(R.string.common_name)) }, placeholder = { Text(stringResource(R.string.sk_name_placeholder)) })
        Picker(
            stringResource(R.string.keys_detail_type),
            stringResource(if (form.algorithm == SecurityKeyAlgorithm.ED25519) R.string.sk_type_ed25519 else R.string.sk_type_ecdsa),
            null,
            listOf(
                SecurityKeyAlgorithm.ED25519 to stringResource(R.string.sk_type_ed25519),
                SecurityKeyAlgorithm.ECDSA_P256 to stringResource(R.string.sk_type_ecdsa),
            ),
        ) { onChange(form.copy(algorithm = it)) }
        CheckRow(stringResource(R.string.sk_resident_label), stringResource(R.string.sk_resident_hint), form.resident) {
            onChange(form.copy(resident = it))
        }
        CheckRow(stringResource(R.string.sk_verify_label), null, form.verify) { onChange(form.copy(verify = it)) }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onAdvanced(!advanced) }.padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.sk_advanced), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            Icon(if (advanced) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null)
        }
        if (advanced) {
            CheckRow(stringResource(R.string.sk_no_touch_label), stringResource(R.string.sk_no_touch_hint), form.noTouch) {
                onChange(form.copy(noTouch = it))
            }
            if (form.noTouch) {
                Text(stringResource(R.string.sk_no_touch_warning), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            OutlinedTextField(
                form.application, { onChange(form.copy(application = it.trim())) }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text(stringResource(R.string.sk_application)) },
                supportingText = { Text(stringResource(R.string.sk_application_hint)) },
                isError = form.problem() == R.string.sk_application_invalid,
            )
            OutlinedTextField(
                form.user, { onChange(form.copy(user = it)) }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text(stringResource(R.string.sk_user_label)) }, placeholder = { Text("openssh") },
                supportingText = { Text(stringResource(R.string.sk_user_hint)) },
                isError = form.problem() == R.string.sk_user_too_long,
            )
            OutlinedTextField(
                form.device, { onChange(form.copy(device = it)) }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text(stringResource(R.string.sk_device_label)) }, placeholder = { Text(stringResource(R.string.sk_device_placeholder)) },
            )
        }
        form.problem()?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun CheckRow(title: String, hint: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked, onChange)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

/**
 * "New security key": the key is made on the device (the sheet asks to
 * tap or plug it in, its PIN when needed, and a touch), saved where you
 * choose and then its authorized_keys line is shown.
 */
@Composable
fun NewSecurityKeyDialog(app: TermoakApp, onDismiss: () -> Unit, onCreated: (SshKey) -> Unit) {
    val resources = LocalResources.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    var form by remember { mutableStateOf(SkForm()) }
    var advanced by remember { mutableStateOf(false) }
    var place by remember { mutableStateOf(app.accounts.defaultPlace()) }
    var busy by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.sk_new_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!app.securityKeys.available) {
                    Text(stringResource(R.string.sk_unavailable), color = MaterialTheme.colorScheme.error)
                }
                SecurityKeyFormFields(form, advanced, { advanced = it }) { form = it }
                PlacePicker(app, place) { place = it }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && form.problem() == null && app.securityKeys.available, onClick = {
                busy = true
                scope.launch {
                    try {
                        val name = form.name.trim().ifEmpty { resources.getString(R.string.sk_badge) }
                        var key = app.core.createSecurityKey(
                            name, form.options(), "$name (Termoak)", if (place.device) SyncMode.DEVICE_ONLY else SyncMode.SYNCED,
                            place.account, place.vault, app.securityKeys.flow,
                        )
                        // The name the device gave, when none was typed.
                        val reported = app.securityKeys.flow.lastDevice
                        val info = key.securityKey
                        if (form.device.isBlank() && reported != null && info != null && info.device.isNullOrBlank()) {
                            key = runCatching { app.core.saveKey(key.copy(securityKey = info.copy(device = reported)), SecretChange.Keep) }.getOrDefault(key)
                        }
                        app.accounts.rememberPlace(place)
                        app.accounts.sync()
                        snackbar.showSnackbar(resources.getString(R.string.sk_created, key.label))
                        onCreated(key)
                    } catch (e: TermoakException) {
                        snackbar.showSnackbar(resources.getString(R.string.sk_failed, e.userMessage(resources, R.string.sk_error_failed)))
                    } finally {
                        busy = false
                    }
                }
            }) { Text(stringResource(if (busy) R.string.sk_creating else R.string.sk_create)) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.common_cancel)) } },
    )
}

/** The key just made: its authorized_keys line to copy to the servers. */
@Composable
fun SecurityKeyCreatedDialog(key: SshKey, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(key.label) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.sk_authorized_hint_new), style = MaterialTheme.typography.bodyMedium)
                AuthorizedLine(key)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) } },
    )
}

/** The line for authorized_keys (with the options the key's flags need) and Copy. */
@Composable
fun AuthorizedLine(key: SshKey) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val line = remember(key) { authorizedLine(key) }
    Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.small) {
        SelectionContainer {
            Text(line, Modifier.padding(10.dp).testTag("sk-authorized-line"), fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        }
    }
    TextButton(onClick = {
        context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(resources.getString(R.string.sk_authorized_keys), line))
        scope.launch { snackbar.showSnackbar(resources.getString(R.string.sk_line_copied)) }
    }) {
        Icon(Icons.Outlined.ContentCopy, null, Modifier.padding(end = 6.dp))
        Text(stringResource(R.string.sk_copy_line))
    }
}

/** A key's authorized_keys line: with `no-touch-required` / `verify-required` for a security key. */
fun authorizedLine(key: SshKey): String =
    if (key.securityKey != null) runCatching { SkLines.line(key.publicKey, key.securityKey) }.getOrDefault(key.publicKey)
    else key.publicKey

/** The engine's authorized_keys line of a security key (a stand-in in the JVM tests). */
object SkLines {
    @Volatile var line: (String, SecurityKeyInfo?) -> String = { k, i -> securityKeyAuthorizedKeysLine(k, i) }
}

/** "Load from security key": the resident keys stored on it that are not here yet. */
@Composable
fun LoadResidentDialog(app: TermoakApp, onDismiss: () -> Unit, onLoaded: () -> Unit) {
    val resources = LocalResources.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    var verify by remember { mutableStateOf(false) }
    var device by remember { mutableStateOf("") }
    var place by remember { mutableStateOf(app.accounts.defaultPlace()) }
    var busy by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.sk_load_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LoadResidentFields(verify, { verify = it }, device, { device = it }, app.securityKeys.available)
                PlacePicker(app, place) { place = it }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && app.securityKeys.available, onClick = {
                busy = true
                scope.launch {
                    try {
                        val keys = app.core.importResidentSecurityKeys(
                            null, verify, device.trim().take(64).ifEmpty { app.securityKeys.flow.lastDevice },
                            if (place.device) SyncMode.DEVICE_ONLY else SyncMode.SYNCED, place.account, place.vault, app.securityKeys.flow,
                        )
                        app.accounts.rememberPlace(place)
                        app.accounts.sync()
                        snackbar.showSnackbar(
                            if (keys.isEmpty()) resources.getString(R.string.sk_loaded_none)
                            else resources.getQuantityString(R.plurals.sk_loaded, keys.size, keys.size),
                        )
                        onLoaded()
                    } catch (e: TermoakException) {
                        snackbar.showSnackbar(resources.getString(R.string.sk_load_failed, e.userMessage(resources, R.string.sk_error_failed)))
                    } finally {
                        busy = false
                    }
                }
            }) { Text(stringResource(if (busy) R.string.sk_loading else R.string.sk_load)) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.common_cancel)) } },
    )
}

/** The fields of "Load from security key" (also drawn by the screenshot tests). */
@Composable
fun LoadResidentFields(verify: Boolean, onVerify: (Boolean) -> Unit, device: String, onDevice: (String) -> Unit, available: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!available) Text(stringResource(R.string.sk_unavailable), color = MaterialTheme.colorScheme.error)
        Text(stringResource(R.string.sk_load_intro), style = MaterialTheme.typography.bodyMedium)
        CheckRow(stringResource(R.string.sk_load_verify), stringResource(R.string.sk_load_verify_hint), verify, onVerify)
        OutlinedTextField(
            device, onDevice, Modifier.fillMaxWidth(), singleLine = true,
            label = { Text(stringResource(R.string.sk_device_label)) }, placeholder = { Text(stringResource(R.string.sk_device_placeholder)) },
        )
    }
}

/** "Security key · YubiKey 5C NFC" under a key's name. */
@Composable
fun SecurityKeyBadge(info: SecurityKeyInfo) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(6.dp)) {
        Text(
            info.device?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.sk_imported_details, it) } ?: stringResource(R.string.sk_badge),
            Modifier.padding(horizontal = 6.dp, vertical = 2.dp).testTag("sk-badge"),
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

/**
 * A security key's details on its page: application, touch, PIN, stored on
 * the key, user, the device's name (editable), the authorized_keys line and
 * what Termoak keeps (the key handle, never a private key).
 */
@Composable
fun SecurityKeySection(key: SshKey, info: SecurityKeyInfo, editable: Boolean, device: String, onDevice: (String) -> Unit) {
    FormSection(stringResource(R.string.sk_section)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SkLine(stringResource(R.string.sk_application), info.application)
            SkLine(stringResource(R.string.sk_touch), stringResource(if (info.touchRequired) R.string.sk_touch_required else R.string.sk_touch_not_required))
            SkLine(stringResource(R.string.sk_pin), stringResource(if (info.verifyRequired) R.string.sk_pin_required_flag else R.string.sk_pin_not_required))
            if (info.resident) SkLine(stringResource(R.string.sk_resident), stringResource(R.string.keys_detail_yes))
            info.user?.takeIf { it.isNotBlank() }?.let { SkLine(stringResource(R.string.sk_user), it) }
            OutlinedTextField(
                device, onDevice, Modifier.fillMaxWidth().testTag("sk-device"), enabled = editable, singleLine = true,
                label = { Text(stringResource(R.string.sk_device_label)) }, placeholder = { Text(stringResource(R.string.sk_device_placeholder)) },
            )
            if (!info.touchRequired) {
                Text(stringResource(R.string.sk_no_touch_warning), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Text(stringResource(R.string.sk_authorized_keys), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            AuthorizedLine(key)
            Text(stringResource(R.string.sk_authorized_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    FormHint(stringResource(R.string.sk_handle_note))
}

@Composable
private fun SkLine(title: String, value: String) {
    Row {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
