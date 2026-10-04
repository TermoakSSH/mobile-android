package com.termoak.app.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.termoak.app.R
import com.termoak.app.TermoakApp
import com.termoak.ffi.HostProxy
import com.termoak.ffi.HostSettings
import com.termoak.ffi.ProxyKind
import com.termoak.ffi.SecretChange
import com.termoak.ffi.SshHost
import com.termoak.ffi.SyncMode
import com.termoak.ffi.TermoakException
import kotlinx.coroutines.launch

private enum class Auth(@StringRes val label: Int) {
    PASSWORD(R.string.common_password), KEY(R.string.host_auth_key), IDENTITY(R.string.common_identity)
}

@Composable
fun HostEditor(app: TermoakApp, hostId: String?, onClose: () -> Unit) {
    val resources = LocalResources.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val original = remember { hostId?.let { runCatching { app.core.getHost(it) }.getOrNull() } }
    val keys = remember { runCatching { app.core.listKeys() }.getOrDefault(emptyList()) }
    val identities = remember { runCatching { app.core.listIdentities() }.getOrDefault(emptyList()) }
    val groups = remember { runCatching { app.core.listGroups() }.getOrDefault(emptyList()) }

    var label by remember { mutableStateOf(original?.label ?: "") }
    var address by remember { mutableStateOf(original?.address ?: "") }
    var port by remember { mutableStateOf(original?.settings?.port?.toString() ?: "") }
    var user by remember { mutableStateOf(original?.settings?.username ?: "") }
    var auth by remember {
        mutableStateOf(
            when {
                original?.settings?.identityId != null -> Auth.IDENTITY
                original?.settings?.keyId != null -> Auth.KEY
                else -> Auth.PASSWORD
            },
        )
    }
    var password by remember { mutableStateOf("") }
    var clearPassword by remember { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }
    var keyId by remember { mutableStateOf(original?.settings?.keyId) }
    var identityId by remember { mutableStateOf(original?.settings?.identityId) }
    var groupId by remember { mutableStateOf(original?.groupId) }
    var tags by remember { mutableStateOf(original?.tags?.joinToString(", ") ?: "") }
    var notes by remember { mutableStateOf(original?.notes ?: "") }
    var deviceOnly by remember { mutableStateOf(original?.syncMode == SyncMode.DEVICE_ONLY) }
    var favorite by remember { mutableStateOf(original?.favorite ?: false) }
    val proxy0 = original?.settings?.proxy
    var proxyKind by remember { mutableStateOf(proxy0?.kind) }
    var proxyHost by remember { mutableStateOf(proxy0?.host ?: "") }
    var proxyPort by remember { mutableStateOf(proxy0?.port?.toString() ?: "") }
    var proxyUser by remember { mutableStateOf(proxy0?.username ?: "") }
    var proxyPassword by remember { mutableStateOf("") }
    val hadProxyPassword = remember { original?.let { runCatching { app.core.hostHasProxyPassword(it.id) }.getOrDefault(false) } ?: false }
    var clearProxyPassword by remember { mutableStateOf(false) }
    var jumps by remember { mutableStateOf(original?.settings?.jumpHostIds ?: emptyList()) }
    val otherHosts = remember { runCatching { app.core.listHosts() }.getOrDefault(emptyList()).filter { it.id != original?.id } }

    fun save() {
        val base = original ?: SshHost(label = "", address = "")
        val host = base.copy(
            label = label.trim().ifEmpty { address.trim() },
            address = address.trim(),
            groupId = groupId,
            tags = tags.split(',').map { it.trim() }.filter { it.isNotEmpty() },
            notes = notes.trim(),
            favorite = favorite,
            syncMode = if (deviceOnly) SyncMode.DEVICE_ONLY else SyncMode.SYNCED,
            settings = (original?.settings ?: HostSettings()).copy(
                port = port.toUIntOrNull(),
                username = user.trim().ifEmpty { null },
                keyId = if (auth == Auth.KEY) keyId else null,
                identityId = if (auth == Auth.IDENTITY) identityId else null,
                jumpHostIds = jumps.ifEmpty { null },
                proxy = proxyKind?.let { k ->
                    HostProxy(kind = k, host = proxyHost.trim(), port = proxyPort.toUIntOrNull() ?: 0u,
                        username = proxyUser.trim().ifEmpty { null })
                },
            ),
        )
        if (proxyKind != null && (proxyHost.isBlank() || (proxyPort.toUIntOrNull() ?: 0u) == 0u)) {
            scope.launch { snackbar.showSnackbar(resources.getString(R.string.host_proxy_incomplete)) }
            return
        }
        val secret = when {
            auth != Auth.PASSWORD -> if (original?.hasPassword == true) SecretChange.Clear else SecretChange.Keep
            password.isNotEmpty() -> SecretChange.Set(password)
            clearPassword -> SecretChange.Clear
            else -> SecretChange.Keep
        }
        try {
            val saved = app.core.saveHost(host, secret)
            val proxySecret = when {
                proxyKind == null -> if (hadProxyPassword) SecretChange.Clear else SecretChange.Keep
                proxyPassword.isNotEmpty() -> SecretChange.Set(proxyPassword)
                clearProxyPassword -> SecretChange.Clear
                else -> SecretChange.Keep
            }
            app.core.setHostProxyPassword(saved.id, proxySecret)
            app.account.sync()
            onClose()
        } catch (e: TermoakException) {
            scope.launch { snackbar.showSnackbar(e.message ?: resources.getString(R.string.error_save_failed)) }
        }
    }

    val choose = stringResource(R.string.common_choose)
    val noGroup = stringResource(R.string.host_no_group)
    val noProxy = stringResource(R.string.host_no_proxy)
    ScreenScaffold(
        title = stringResource(if (original == null) R.string.hosts_new_host else R.string.host_edit_title),
        navigationIcon = {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back)) }
        },
        actions = {
            TextButton(onClick = { save() }, enabled = address.isNotBlank()) { Text(stringResource(R.string.common_save)) }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding()
                .verticalScroll(rememberScrollState()).padding(bottom = 32.dp),
        ) {
            // Header that looks like the host will in the list.
            Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                HostTile(label.ifBlank { address.ifBlank { "?" } }, original?.os, original?.color, size = 52.dp)
                Column(Modifier.padding(start = 16.dp)) {
                    Text(
                        label.ifBlank { address.ifBlank { stringResource(R.string.hosts_new_host) } },
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        listOfNotNull("ssh", user.ifBlank { null }).joinToString(", "),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            FormSection(stringResource(R.string.host_section_general)) {
                FormField(
                    stringResource(R.string.host_address), address, { address = it.trim() },
                    placeholder = stringResource(R.string.host_address_placeholder), keyboard = KeyboardType.Uri,
                )
                FormDivider()
                FormField(stringResource(R.string.host_label), label, { label = it }, placeholder = address.ifBlank { "web-1" })
                FormDivider()
                FormPicker(
                    stringResource(R.string.host_group), groups.firstOrNull { it.id == groupId }?.name ?: noGroup,
                    listOf<Pair<String?, String>>(null to noGroup) + groups.map { it.id to it.name },
                ) { groupId = it }
                FormDivider()
                FormField(stringResource(R.string.host_tags), tags, { tags = it }, placeholder = "prod, web")
            }

            FormSection("SSH") {
                FormField(
                    stringResource(R.string.common_port), port, { port = it.filter(Char::isDigit).take(5) },
                    placeholder = "22", keyboard = KeyboardType.Number,
                )
            }

            FormSection(stringResource(R.string.host_section_credentials)) {
                FormField(stringResource(R.string.common_username), user, { user = it.trim() }, placeholder = "root")
                FormDivider()
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(12.dp)) {
                    Auth.entries.forEachIndexed { i, a ->
                        SegmentedButton(
                            selected = auth == a, onClick = { auth = a },
                            shape = SegmentedButtonDefaults.itemShape(i, Auth.entries.size),
                        ) { Text(stringResource(a.label)) }
                    }
                }
                FormDivider()
                when (auth) {
                    Auth.PASSWORD -> {
                        FormField(
                            stringResource(R.string.common_password), password, { password = it; clearPassword = false },
                            placeholder = stringResource(
                                if (original?.hasPassword == true && !clearPassword) R.string.common_saved_secret
                                else R.string.host_password_placeholder,
                            ),
                            keyboard = KeyboardType.Password, secret = !showPassword,
                            trailing = {
                                IconButton(onClick = { showPassword = !showPassword }) {
                                    Icon(
                                        if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                        stringResource(R.string.common_show),
                                    )
                                }
                            },
                        )
                        if (original?.hasPassword == true && !clearPassword && password.isEmpty()) {
                            TextButton(onClick = { clearPassword = true }, modifier = Modifier.padding(start = 4.dp)) {
                                Text(stringResource(R.string.host_clear_password), color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    Auth.KEY -> if (keys.isEmpty()) {
                        FormNote(stringResource(R.string.host_no_keys))
                    } else {
                        FormPicker(
                            stringResource(R.string.common_key),
                            keys.firstOrNull { it.id == keyId }?.let { "${it.label} · ${it.algorithm}" } ?: choose,
                            keys.map { it.id to "${it.label} · ${it.algorithm}" },
                        ) { keyId = it }
                    }
                    Auth.IDENTITY -> if (identities.isEmpty()) {
                        FormNote(stringResource(R.string.host_no_identities))
                    } else {
                        FormPicker(
                            stringResource(R.string.common_identity),
                            identities.firstOrNull { it.id == identityId }?.let { "${it.label} (${it.username})" } ?: choose,
                            identities.map { it.id to "${it.label} (${it.username})" },
                        ) { identityId = it }
                    }
                }
            }

            FormSection(stringResource(R.string.host_section_jumps)) {
                if (jumps.isEmpty()) {
                    FormNote(stringResource(R.string.host_no_jumps))
                }
                jumps.forEachIndexed { i, id ->
                    val h = otherHosts.firstOrNull { it.id == id }
                    ListItem(
                        headlineContent = { Text("${i + 1}. ${h?.label ?: stringResource(R.string.host_deleted_host)}") },
                        supportingContent = { h?.let { Text(it.address) } },
                        trailingContent = {
                            IconButton(onClick = { jumps = jumps.filterIndexed { j, _ -> j != i } }) {
                                Icon(Icons.Outlined.Close, stringResource(R.string.host_remove))
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                    FormDivider()
                }
                val candidates = otherHosts.filter { it.id !in jumps }
                if (candidates.isNotEmpty()) {
                    FormPicker(
                        stringResource(R.string.host_add_jump), stringResource(R.string.host_choose_host),
                        candidates.map { it.id to it.label },
                    ) { jumps = jumps + it }
                }
            }

            FormSection(stringResource(R.string.host_section_proxy)) {
                FormPicker(
                    stringResource(R.string.host_proxy_type),
                    when (proxyKind) {
                        null -> noProxy
                        ProxyKind.SOCKS5 -> "SOCKS5"
                        ProxyKind.SOCKS4 -> "SOCKS4"
                        ProxyKind.HTTP -> "HTTP (CONNECT)"
                    },
                    listOf<Pair<ProxyKind?, String>>(
                        null to noProxy, ProxyKind.SOCKS5 to "SOCKS5", ProxyKind.SOCKS4 to "SOCKS4",
                        ProxyKind.HTTP to "HTTP (CONNECT)",
                    ),
                ) { proxyKind = it }
                if (proxyKind != null) {
                    FormDivider()
                    FormField(
                        stringResource(R.string.host_proxy_address), proxyHost, { proxyHost = it.trim() },
                        placeholder = stringResource(R.string.host_proxy_address_placeholder), keyboard = KeyboardType.Uri,
                    )
                    FormDivider()
                    FormField(
                        stringResource(R.string.common_port), proxyPort, { proxyPort = it.filter(Char::isDigit).take(5) },
                        placeholder = if (proxyKind == ProxyKind.HTTP) "8080" else "1080", keyboard = KeyboardType.Number,
                    )
                    FormDivider()
                    FormField(
                        stringResource(R.string.common_username), proxyUser, { proxyUser = it.trim() },
                        placeholder = stringResource(R.string.common_optional),
                    )
                    FormDivider()
                    FormField(
                        stringResource(R.string.common_password), proxyPassword, { proxyPassword = it; clearProxyPassword = false },
                        placeholder = stringResource(
                            if (hadProxyPassword && !clearProxyPassword) R.string.common_saved_secret else R.string.common_optional,
                        ),
                        keyboard = KeyboardType.Password, secret = true,
                    )
                    if (hadProxyPassword && !clearProxyPassword && proxyPassword.isEmpty()) {
                        TextButton(onClick = { clearProxyPassword = true }, modifier = Modifier.padding(start = 4.dp)) {
                            Text(stringResource(R.string.host_clear_proxy_password), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            Text(
                stringResource(R.string.host_proxy_note),
                Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            FormSection(stringResource(R.string.common_options)) {
                FormSwitch(stringResource(R.string.host_favorite), stringResource(R.string.host_favorite_hint), favorite) {
                    favorite = it
                }
                FormDivider()
                FormSwitch(stringResource(R.string.common_device_only), stringResource(R.string.host_device_only_hint), deviceOnly) {
                    deviceOnly = it
                }
                FormDivider()
                FormField(
                    stringResource(R.string.host_notes), notes, { notes = it },
                    placeholder = stringResource(R.string.common_optional), singleLine = false,
                )
            }
        }
    }
}

// ----- Forms in the style of Termius: fields grouped in cards -----

@Composable
fun FormSection(title: String, content: @Composable () -> Unit) {
    SectionLabel(title)
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
    ) { Column { content() } }
}

@Composable
fun FormDivider() = HorizontalDivider(Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)

@Composable
fun FormNote(text: String) {
    Text(text, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun FormField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    placeholder: String = "",
    keyboard: KeyboardType = KeyboardType.Text,
    secret: Boolean = false,
    singleLine: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
) {
    TextField(
        value, onChange, Modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        singleLine = singleLine,
        trailingIcon = trailing,
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
    )
}

@Composable
fun FormSwitch(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        modifier = Modifier.clickable { onChange(!checked) },
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = { Switch(checked, onChange) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
fun <T> FormPicker(label: String, selected: String, options: List<Pair<T, String>>, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        ListItem(
            modifier = Modifier.clickable { open = true },
            overlineContent = { Text(label) },
            headlineContent = { Text(selected) },
            trailingContent = { Icon(Icons.Outlined.ArrowDropDown, null) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
        DropdownMenu(open, { open = false }) {
            options.forEach { (value, text) -> DropdownMenuItem({ Text(text) }, { onSelect(value); open = false }) }
        }
    }
}

/** Simple picker: a read-only field that opens a menu. */
@Composable
fun <T> Picker(
    label: String,
    selected: String?,
    empty: String?,
    options: List<Pair<T, String>>,
    onSelect: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    if (options.isEmpty() && empty != null) {
        Text(empty, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    Box {
        OutlinedTextField(
            selected ?: "", {}, Modifier.fillMaxWidth(),
            label = { Text(label) }, readOnly = true, singleLine = true,
            placeholder = { Text(stringResource(R.string.common_choose)) },
            trailingIcon = { Icon(Icons.Outlined.ArrowDropDown, null) },
        )
        // Transparent layer that gets the tap (the field is read-only).
        Box(Modifier.matchParentSize().padding(top = 8.dp).then(Modifier.clickableNoRipple { open = true }))
        DropdownMenu(open, { open = false }) {
            options.forEach { (value, text) ->
                DropdownMenuItem({ Text(text) }, { onSelect(value); open = false })
            }
        }
    }
}

@Composable
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier =
    clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick,
    )
