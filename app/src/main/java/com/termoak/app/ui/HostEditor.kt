package com.termoak.app.ui

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FormatColorReset
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import com.termoak.app.R
import com.termoak.app.TermoakApp
import com.termoak.ffi.AccountInfo
import com.termoak.ffi.HostProxy
import com.termoak.ffi.ItemFilter
import com.termoak.ffi.TransferMode
import com.termoak.ffi.VaultInfo
import com.termoak.app.data.Place
import com.termoak.app.data.canEdit
import com.termoak.app.data.canWrite
import com.termoak.app.data.place
import com.termoak.app.data.uid
import com.termoak.app.data.useOnly
import com.termoak.app.userMessage
import androidx.compose.runtime.collectAsState
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

/** Colors offered for a host (the desktop's). */
private val HostColors = listOf("#4f7cff", "#30a46c", "#f5a524", "#e5484d", "#8e4ec6", "#0ea5e9", "#d6409f", "#12a594")

/** Fields of the host editor that can show an error under them. */
internal enum class HostField(val advanced: Boolean = false) {
    ADDRESS, PORT, KEEPALIVE(true), ENV(true), PROXY_ADDRESS(true), PROXY_PORT(true)
}

/**
 * Checks of the host editor (the desktop's host_editor.rs `parse_form`):
 * every error at once, so each one shows under its field.
 */
internal object HostForm {
    /** A TCP port (1-65535). */
    fun port(text: String): UInt? = text.trim().toUIntOrNull()?.takeIf { it in 1u..65535u }

    /** `KEY=value` lines (blank lines ignored); the first wrong line on error. */
    fun env(text: String): Result<Map<String, String>> {
        val env = linkedMapOf<String, String>()
        for (raw in text.lines()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            val eq = line.indexOf('=')
            val key = if (eq > 0) line.substring(0, eq).trim() else ""
            if (key.isEmpty() || key.any { it.isWhitespace() }) return Result.failure(IllegalArgumentException(line))
            env[key] = line.substring(eq + 1).trim()
        }
        return Result.success(env)
    }

    /** Comma-separated tags, without empty ones or repetitions. */
    fun tags(text: String): List<String> = text.split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinct()

    fun envText(env: Map<String, String>): String = env.entries.joinToString("\n") { "${it.key}=${it.value}" }

    /** Does the host use anything of the "Advanced" section? (Then it starts open.) */
    fun hasAdvanced(s: HostSettings): Boolean =
        !s.jumpHostIds.isNullOrEmpty() || s.proxy != null || s.agentForwarding == true || s.keepaliveSecs != null ||
            s.startupSnippetId != null || s.env.isNotEmpty() || s.recordSessions == true || !s.term.isNullOrBlank() ||
            s.theme != null
}

@Composable
fun HostEditor(app: TermoakApp, hostId: String?, accountId: String?, onClose: () -> Unit, onConnect: (SshHost) -> Unit) {
    val resources = LocalResources.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val original = remember { hostId?.let { runCatching { app.core.getHost(it, accountId) }.getOrNull() } }
    val s0 = original?.settings ?: HostSettings()
    val accountList by app.accounts.list.collectAsState()
    val vaults by app.accounts.vaults.collectAsState()
    // Where it is (or, for a new one, where it goes): This device or a vault.
    var place by remember { mutableStateOf(original?.place ?: app.accounts.defaultPlace()) }
    val useOnly = original?.access.useOnly()
    var moving by remember { mutableStateOf(false) }
    // References stay in the host's vault (or This device).
    val all = remember { ItemFilter() }
    val allKeys = remember { runCatching { app.core.listKeys(all) }.getOrDefault(emptyList()) }
    val allIdentities = remember { runCatching { app.core.listIdentities(all) }.getOrDefault(emptyList()) }
    val allGroups = remember { runCatching { app.core.listGroups(all) }.getOrDefault(emptyList()).sortedBy { it.name.lowercase() } }
    val allSnippets = remember { runCatching { app.core.listSnippets(all) }.getOrDefault(emptyList()).sortedBy { it.name.lowercase() } }
    val keys = allKeys.filter { place.reaches(it.accountId, it.vaultId) }
    val identities = allIdentities.filter { place.reaches(it.accountId, it.vaultId) }
    val groups = allGroups.filter { it.accountId == place.account && (place.vault == null || it.vaultId == place.vault) }
    val snippets = allSnippets.filter { place.reaches(it.accountId, it.vaultId) }

    var label by remember { mutableStateOf(original?.label ?: "") }
    var address by remember { mutableStateOf(original?.address ?: "") }
    var port by remember { mutableStateOf(s0.port?.toString() ?: "") }
    var user by remember { mutableStateOf(s0.username ?: "") }
    var auth by remember {
        mutableStateOf(
            when {
                s0.identityId != null -> Auth.IDENTITY
                s0.keyId != null -> Auth.KEY
                else -> Auth.PASSWORD
            },
        )
    }
    var password by remember { mutableStateOf("") }
    var clearPassword by remember { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }
    var keyId by remember { mutableStateOf(s0.keyId) }
    var identityId by remember { mutableStateOf(s0.identityId) }
    var groupId by remember { mutableStateOf(original?.groupId) }
    var tags by remember { mutableStateOf(original?.tags?.joinToString(", ") ?: "") }
    var color by remember { mutableStateOf(original?.color) }
    var notes by remember { mutableStateOf(original?.notes ?: "") }
    var deviceOnly by remember { mutableStateOf(original?.syncMode == SyncMode.DEVICE_ONLY) }
    var favorite by remember { mutableStateOf(original?.favorite ?: false) }
    val proxy0 = s0.proxy
    var proxyKind by remember { mutableStateOf(proxy0?.kind) }
    var proxyHost by remember { mutableStateOf(proxy0?.host ?: "") }
    var proxyPort by remember { mutableStateOf(proxy0?.port?.toString() ?: "") }
    var proxyUser by remember { mutableStateOf(proxy0?.username ?: "") }
    var proxyPassword by remember { mutableStateOf("") }
    val hadProxyPassword = remember { original?.let { runCatching { app.core.hostHasProxyPassword(it.id, it.accountId) }.getOrDefault(false) } ?: false }
    var clearProxyPassword by remember { mutableStateOf(false) }
    var jumps by remember { mutableStateOf(s0.jumpHostIds ?: emptyList()) }
    val allHosts = remember { runCatching { app.core.listHosts(all) }.getOrDefault(emptyList()).filter { it.uid != original?.uid } }
    val otherHosts = allHosts.filter { place.reaches(it.accountId, it.vaultId) }
    var agentForwarding by remember { mutableStateOf(s0.agentForwarding == true) }
    var keepalive by remember { mutableStateOf(s0.keepaliveSecs?.toString() ?: "") }
    var startupSnippet by remember { mutableStateOf(s0.startupSnippetId) }
    var env by remember { mutableStateOf(HostForm.envText(s0.env)) }
    var term by remember { mutableStateOf(s0.term ?: "") }
    var theme by remember { mutableStateOf(s0.theme) }
    var record by remember { mutableStateOf(s0.recordSessions == true) }
    var advancedOpen by remember { mutableStateOf(HostForm.hasAdvanced(s0)) }
    var errors by remember { mutableStateOf<Map<HostField, String>>(emptyMap()) }
    var deleting by remember { mutableStateOf(false) }

    val focusAddress = remember { FocusRequester() }
    val focusLabel = remember { FocusRequester() }
    val focusUser = remember { FocusRequester() }
    val focusPort = remember { FocusRequester() }
    val focusPassword = remember { FocusRequester() }
    // A new host: straight to the address.
    LaunchedEffect(Unit) { if (original == null) runCatching { focusAddress.requestFocus() } }

    fun clearError(field: HostField) {
        if (field in errors) errors = errors - field
    }

    /** Checks the form; with errors, shows them and returns `null`. */
    fun build(): SshHost? {
        val e = linkedMapOf<HostField, String>()
        val addr = address.trim()
        when {
            addr.isEmpty() -> e[HostField.ADDRESS] = resources.getString(R.string.editor_error_address)
            addr.any { it.isWhitespace() } -> e[HostField.ADDRESS] = resources.getString(R.string.editor_error_address_spaces)
        }
        val portValue = port.trim().takeIf { it.isNotEmpty() }?.let { HostForm.port(it) }
        if (port.isNotBlank() && portValue == null) e[HostField.PORT] = resources.getString(R.string.editor_error_port)
        val keepaliveValue = keepalive.trim().takeIf { it.isNotEmpty() }?.toUIntOrNull()
        if (keepalive.isNotBlank() && keepaliveValue == null) e[HostField.KEEPALIVE] = resources.getString(R.string.editor_error_keepalive)
        val envValue = HostForm.env(env).getOrElse {
            e[HostField.ENV] = resources.getString(R.string.editor_error_env, it.message ?: "")
            emptyMap()
        }
        var proxyPortValue: UInt? = null
        if (proxyKind != null) {
            if (proxyHost.isBlank()) e[HostField.PROXY_ADDRESS] = resources.getString(R.string.editor_error_proxy_address)
            proxyPortValue = HostForm.port(proxyPort)
            if (proxyPortValue == null) e[HostField.PROXY_PORT] = resources.getString(R.string.editor_error_proxy_port)
        }
        if (e.isNotEmpty()) {
            errors = e
            // It may be out of sight (or in the closed "Advanced"): the first one also as a notice.
            if (e.keys.any { it.advanced }) advancedOpen = true
            scope.launch { snackbar.showSnackbar(e.values.first()) }
            return null
        }
        errors = emptyMap()
        val base = original ?: SshHost(label = "", address = "")
        return base.copy(
            label = label.trim().ifEmpty { addr },
            address = addr,
            groupId = groupId,
            tags = HostForm.tags(tags),
            notes = notes.trim(),
            color = color,
            favorite = favorite,
            // With accounts, This device items stay "only on this phone"; a vault syncs.
            syncMode = when {
                accountList.isEmpty() -> if (deviceOnly) SyncMode.DEVICE_ONLY else SyncMode.SYNCED
                original != null -> original.syncMode
                place.device -> SyncMode.DEVICE_ONLY
                else -> SyncMode.SYNCED
            },
            accountId = original?.accountId ?: place.account,
            vaultId = original?.vaultId ?: place.vault,
            settings = s0.copy(
                port = portValue,
                username = user.trim().ifEmpty { null },
                keyId = if (auth == Auth.KEY) keyId else null,
                identityId = if (auth == Auth.IDENTITY) identityId else null,
                jumpHostIds = jumps.ifEmpty { null },
                startupSnippetId = startupSnippet,
                env = envValue,
                keepaliveSecs = keepaliveValue,
                agentForwarding = if (agentForwarding) true else null,
                term = term.trim().ifEmpty { null },
                theme = theme,
                recordSessions = if (record) true else null,
                proxy = proxyKind?.let { k ->
                    HostProxy(kind = k, host = proxyHost.trim(), port = proxyPortValue ?: 0u, username = proxyUser.trim().ifEmpty { null })
                },
            ),
        )
    }

    fun save(connect: Boolean) {
        val host = build() ?: return
        val secret = when {
            auth != Auth.PASSWORD -> if (original?.hasPassword == true) SecretChange.Clear else SecretChange.Keep
            password.isNotEmpty() -> SecretChange.Set(password)
            clearPassword -> SecretChange.Clear
            else -> SecretChange.Keep
        }
        if (useOnly) return
        try {
            val saved = app.core.saveHost(host, secret)
            if (original == null) app.accounts.rememberPlace(place)
            val proxySecret = when {
                proxyKind == null -> if (hadProxyPassword) SecretChange.Clear else SecretChange.Keep
                proxyPassword.isNotEmpty() -> SecretChange.Set(proxyPassword)
                clearProxyPassword -> SecretChange.Clear
                else -> SecretChange.Keep
            }
            app.core.setHostProxyPassword(saved.id, proxySecret, saved.accountId)
            app.accounts.sync()
            if (connect) onConnect(saved) else onClose()
        } catch (e: TermoakException) {
            scope.launch { snackbar.showSnackbar(e.userMessage(resources, R.string.error_save_failed)) }
        }
    }

    val choose = stringResource(R.string.common_choose)
    val noGroup = stringResource(R.string.host_no_group)
    val noProxy = stringResource(R.string.host_no_proxy)
    val none = stringResource(R.string.editor_none)
    ScreenScaffold(
        title = original?.label ?: stringResource(R.string.hosts_new_host),
        subtitle = if (original != null) stringResource(R.string.host_edit_title) else null,
        navigationIcon = {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back)) }
        },
        actions = {
            if (useOnly && original != null) {
                // Use only: it can't be changed, only used.
                TextButton(onClick = { onConnect(original) }) { Text(stringResource(R.string.hosts_connect)) }
            } else {
                TextButton(onClick = { save(connect = true) }) { Text(stringResource(R.string.hosts_connect)) }
                TextButton(onClick = { save(connect = false) }) { Text(stringResource(R.string.common_save)) }
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding()
                .verticalScroll(rememberScrollState()).padding(bottom = 32.dp),
        ) {
            // ----- Address (big) and label, like the desktop's editor -----
            Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                HostTile(label.ifBlank { address.ifBlank { "?" } }, original?.os, color, size = 52.dp)
                Column(Modifier.padding(start = 16.dp).weight(1f)) {
                    Text(
                        label.ifBlank { address.ifBlank { stringResource(R.string.hosts_new_host) } },
                        style = MaterialTheme.typography.titleLarge, maxLines = 1,
                    )
                    Text(
                        listOfNotNull("ssh", user.ifBlank { null }, port.takeIf { it.isNotBlank() && it != "22" }?.let { ":$it" })
                            .joinToString(", "),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    address, { address = it.trim(); clearError(HostField.ADDRESS) },
                    Modifier.fillMaxWidth().focusRequester(focusAddress),
                    label = { Text(stringResource(R.string.host_address)) },
                    placeholder = { Text(stringResource(R.string.host_address_placeholder)) },
                    leadingIcon = { Icon(Icons.Outlined.Dns, null) },
                    textStyle = MaterialTheme.typography.titleLarge,
                    singleLine = true,
                    isError = HostField.ADDRESS in errors,
                    supportingText = errors[HostField.ADDRESS]?.let { e -> @Composable { Text(e) } },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next, autoCorrectEnabled = false,
                    ),
                    keyboardActions = KeyboardActions(onNext = { runCatching { focusLabel.requestFocus() } }),
                )
                OutlinedTextField(
                    label, { label = it },
                    Modifier.fillMaxWidth().focusRequester(focusLabel),
                    label = { Text(stringResource(R.string.host_label)) },
                    placeholder = { Text(stringResource(R.string.editor_label_placeholder)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { runCatching { focusUser.requestFocus() } }),
                )
            }

            if (useOnly) UseOnlyNote()

            // ----- Where it is saved: This device or a vault (with accounts) -----
            if (accountList.isNotEmpty()) {
                FormSection(stringResource(R.string.editor_section_place)) {
                    val label = placeLabel(place, accountList, vaults)
                    if (original == null) {
                        val options = placeOptions(accountList, vaults)
                        FormPicker(stringResource(R.string.editor_place), label, options.map { it.first to it.second }) { place = it }
                    } else {
                        ListItem(
                            overlineContent = { Text(stringResource(R.string.editor_place)) },
                            headlineContent = { Text(label) },
                            trailingContent = if (original.access.canWrite()) {
                                { TextButton(onClick = { moving = true }) { Text(stringResource(R.string.transfer_move_to)) } }
                            } else null,
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                    }
                }
            }

            // ----- SSH: user, port and the credential -----
            FormSection("SSH") {
                Row {
                    Box(Modifier.weight(1f)) {
                        FormField(
                            stringResource(R.string.common_username), user, { user = it.trim() }, placeholder = "root",
                            modifier = Modifier.focusRequester(focusUser),
                            imeAction = ImeAction.Next, onIme = { runCatching { focusPort.requestFocus() } },
                        )
                    }
                    Box(Modifier.width(112.dp)) {
                        FormField(
                            stringResource(R.string.common_port), port,
                            { port = it.filter(Char::isDigit).take(5); clearError(HostField.PORT) },
                            placeholder = "22", keyboard = KeyboardType.Number,
                            modifier = Modifier.focusRequester(focusPort),
                            error = errors[HostField.PORT],
                            imeAction = if (auth == Auth.PASSWORD) ImeAction.Next else ImeAction.Done,
                            onIme = { if (auth == Auth.PASSWORD) runCatching { focusPassword.requestFocus() } else save(false) },
                        )
                    }
                }
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
                    Auth.PASSWORD -> if (useOnly) {
                        FormNote(stringResource(if (original?.hasPassword == true || original?.secretHidden == true) R.string.editor_password_hidden else R.string.editor_no_password))
                    } else {
                        FormField(
                            stringResource(R.string.common_password), password, { password = it; clearPassword = false },
                            placeholder = stringResource(
                                if (original?.hasPassword == true && !clearPassword) R.string.common_saved_secret
                                else R.string.host_password_placeholder,
                            ),
                            keyboard = KeyboardType.Password, secret = !showPassword,
                            modifier = Modifier.focusRequester(focusPassword),
                            imeAction = ImeAction.Done, onIme = { save(false) },
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

            // ----- Group, tags and color -----
            FormSection(stringResource(R.string.editor_section_group)) {
                FormPicker(
                    stringResource(R.string.host_group), groups.firstOrNull { it.id == groupId }?.name ?: noGroup,
                    listOf<Pair<String?, String>>(null to noGroup) + groups.map { it.id to it.name },
                ) { groupId = it }
                FormDivider()
                FormField(
                    stringResource(R.string.host_tags), tags, { tags = it }, placeholder = "prod, web",
                    imeAction = ImeAction.Done, onIme = { save(false) },
                )
                FormDivider()
                Text(
                    stringResource(R.string.editor_color), Modifier.padding(start = 16.dp, top = 12.dp),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ColorChoice(color) { color = it }
            }

            // ----- Advanced (collapsible) -----
            val advancedErrors = errors.keys.any { it.advanced }
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp).clickable { advancedOpen = !advancedOpen }
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(if (advancedOpen) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                    Text(stringResource(R.string.editor_section_advanced), style = MaterialTheme.typography.titleSmall)
                    if (!advancedOpen) {
                        Text(
                            stringResource(R.string.editor_advanced_summary), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (advancedErrors && !advancedOpen) {
                    Icon(Icons.Outlined.ErrorOutline, stringResource(R.string.editor_advanced_errors), tint = MaterialTheme.colorScheme.error)
                }
            }
            AnimatedVisibility(advancedOpen) {
                Column {
                    FormSection(stringResource(R.string.host_section_jumps)) {
                        if (jumps.isEmpty()) FormNote(stringResource(R.string.host_no_jumps))
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
                        ) {
                            proxyKind = it
                            clearError(HostField.PROXY_ADDRESS)
                            clearError(HostField.PROXY_PORT)
                        }
                        if (proxyKind != null) {
                            FormDivider()
                            FormField(
                                stringResource(R.string.host_proxy_address), proxyHost,
                                { proxyHost = it.trim(); clearError(HostField.PROXY_ADDRESS) },
                                placeholder = stringResource(R.string.host_proxy_address_placeholder), keyboard = KeyboardType.Uri,
                                error = errors[HostField.PROXY_ADDRESS], imeAction = ImeAction.Next,
                            )
                            FormDivider()
                            FormField(
                                stringResource(R.string.common_port), proxyPort,
                                { proxyPort = it.filter(Char::isDigit).take(5); clearError(HostField.PROXY_PORT) },
                                placeholder = if (proxyKind == ProxyKind.HTTP) "8080" else "1080", keyboard = KeyboardType.Number,
                                error = errors[HostField.PROXY_PORT], imeAction = ImeAction.Next,
                            )
                            FormDivider()
                            FormField(
                                stringResource(R.string.common_username), proxyUser, { proxyUser = it.trim() },
                                placeholder = stringResource(R.string.common_optional), imeAction = ImeAction.Next,
                            )
                            FormDivider()
                            FormField(
                                stringResource(R.string.common_password), proxyPassword, { proxyPassword = it; clearProxyPassword = false },
                                placeholder = stringResource(
                                    if (hadProxyPassword && !clearProxyPassword) R.string.common_saved_secret else R.string.common_optional,
                                ),
                                keyboard = KeyboardType.Password, secret = true, imeAction = ImeAction.Done, onIme = { save(false) },
                            )
                            if (hadProxyPassword && !clearProxyPassword && proxyPassword.isEmpty()) {
                                TextButton(onClick = { clearProxyPassword = true }, modifier = Modifier.padding(start = 4.dp)) {
                                    Text(stringResource(R.string.host_clear_proxy_password), color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                    FormHint(stringResource(R.string.host_proxy_note))

                    FormSection(stringResource(R.string.editor_section_connection)) {
                        FormSwitch(
                            stringResource(R.string.editor_agent_forwarding), stringResource(R.string.editor_agent_forwarding_hint),
                            agentForwarding,
                        ) { agentForwarding = it }
                        FormDivider()
                        FormField(
                            stringResource(R.string.editor_keepalive), keepalive,
                            { keepalive = it.filter(Char::isDigit).take(6); clearError(HostField.KEEPALIVE) },
                            placeholder = "30", keyboard = KeyboardType.Number,
                            error = errors[HostField.KEEPALIVE] ?: stringResource(R.string.editor_keepalive_hint),
                            isError = HostField.KEEPALIVE in errors,
                            imeAction = ImeAction.Done, onIme = { save(false) },
                        )
                    }

                    FormSection(stringResource(R.string.editor_section_terminal)) {
                        FormPicker(
                            stringResource(R.string.editor_startup_snippet),
                            snippets.firstOrNull { it.id == startupSnippet }?.name
                                ?: if (startupSnippet == null) none else stringResource(R.string.editor_deleted_snippet),
                            listOf<Pair<String?, String>>(null to none) + snippets.map { it.id to it.name },
                        ) { startupSnippet = it }
                        FormDivider()
                        FormField(
                            stringResource(R.string.editor_env), env, { env = it; clearError(HostField.ENV) },
                            placeholder = stringResource(R.string.editor_env_placeholder), singleLine = false,
                            error = errors[HostField.ENV], mono = true,
                        )
                        FormDivider()
                        FormField(
                            stringResource(R.string.editor_term), term, { term = it.trim() }, placeholder = "xterm-256color",
                            error = stringResource(R.string.editor_term_hint), isError = false,
                            imeAction = ImeAction.Done, onIme = { save(false) },
                        )
                        FormDivider()
                        val themes = listOf<Pair<String?, String>>(
                            null to stringResource(R.string.editor_theme_follow),
                            "dark" to stringResource(R.string.settings_theme_dark),
                            "light" to stringResource(R.string.settings_theme_light),
                        )
                        FormPicker(
                            stringResource(R.string.editor_theme),
                            themes.firstOrNull { it.first == theme }?.second ?: theme.orEmpty(),
                            themes,
                        ) { theme = it }
                        FormDivider()
                        FormSwitch(stringResource(R.string.editor_record), stringResource(R.string.editor_record_hint), record) {
                            record = it
                        }
                    }
                    FormHint(stringResource(R.string.editor_theme_note))
                }
            }

            // ----- Organization -----
            FormSection(stringResource(R.string.common_options)) {
                FormSwitch(stringResource(R.string.host_favorite), stringResource(R.string.host_favorite_hint), favorite) {
                    favorite = it
                }
                // Without accounts: whether to offer it for upload when an account is added.
                if (accountList.isEmpty()) {
                    FormDivider()
                    FormSwitch(stringResource(R.string.common_device_only), stringResource(R.string.host_device_only_hint), deviceOnly) {
                        deviceOnly = it
                    }
                }
                FormDivider()
                FormField(
                    stringResource(R.string.host_notes), notes, { notes = it },
                    placeholder = stringResource(R.string.common_optional), singleLine = false,
                )
            }

            if (original != null && !useOnly) {
                OutlinedButton(
                    onClick = { deleting = true },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp).fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.Delete, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                    Text(stringResource(R.string.editor_delete), Modifier.padding(start = 8.dp), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (deleting && original != null) {
        ConfirmDialog(
            title = stringResource(R.string.hosts_delete_title, original.label),
            text = stringResource(R.string.hosts_delete_text),
            confirm = stringResource(R.string.common_delete), destructive = true, onDismiss = { deleting = false },
        ) {
            val r = runCatching { app.core.deleteHost(original.id, original.accountId) }
            app.accounts.sync()
            scope.launch {
                snackbar.showSnackbar(
                    r.exceptionOrNull()?.userMessage(resources, R.string.error_save_failed) ?: resources.getString(R.string.hosts_deleted, original.label),
                )
            }
            onClose()
        }
    }
    if (moving && original != null) {
        TransferFlow(
            app, TransferRequest(TransferMode.MOVE, listOf(TransferItem(original.accountId, original.id, original.vaultId))),
            onDismiss = { moving = false },
        ) {
            moving = false
            onClose()
        }
    }
}

/** "Personal · ana@example.com", "Ops", "This device": a place for new items. */
@Composable
private fun placeLabel(p: Place, accounts: List<AccountInfo>, vaults: List<VaultInfo>): String {
    if (p.device) return stringResource(R.string.vault_this_device)
    val account = accounts.firstOrNull { it.id == p.account }
    val v = vaults.firstOrNull { it.id == p.vault && it.accountId == p.account }
    val name = v?.let { vaultName(it) } ?: stringResource(R.string.vault_personal)
    return if (accounts.size > 1 && account != null) "$name · ${account.email}" else name
}

/** Places a new item can go: This device and every vault where you are Editor. */
@Composable
private fun placeOptions(accounts: List<AccountInfo>, vaults: List<VaultInfo>): List<Pair<Place, String>> {
    val out = mutableListOf(Place.DEVICE to stringResource(R.string.vault_this_device))
    accounts.filter { it.status != com.termoak.ffi.AccountStatus.UNVERIFIED }.forEach { a ->
        val mine = vaults.filter { it.accountId == a.id && it.role.canEdit() }
        if (mine.isEmpty()) {
            val p = Place(a.id, null)
            out += p to placeLabel(p, accounts, vaults)
        } else {
            mine.forEach { v -> val p = Place(a.id, v.id); out += p to placeLabel(p, accounts, vaults) }
        }
    }
    return out
}

/** The host's color: the desktop's palette, or none (the system's or the name's). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorChoice(selected: String?, onSelect: (String?) -> Unit) {
    fun same(a: String?, b: String?) = a?.trim()?.removePrefix("#").equals(b?.trim()?.removePrefix("#"), ignoreCase = true)
    FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val noneSelected = selected == null
        Box(
            Modifier.size(34.dp).clip(CircleShape)
                .border(
                    if (noneSelected) 2.dp else 1.dp,
                    if (noneSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    CircleShape,
                )
                .clickable { onSelect(null) },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.FormatColorReset, stringResource(R.string.editor_color_none), Modifier.size(18.dp)) }
        val known = HostColors.any { same(it, selected) }
        (if (selected != null && !known) HostColors + selected else HostColors).forEach { hex ->
            val c = runCatching { Color(hex.toColorInt()) }.getOrNull() ?: return@forEach
            Box(
                Modifier.size(34.dp).clip(CircleShape).background(c).clickable { onSelect(hex) },
                contentAlignment = Alignment.Center,
            ) { if (same(hex, selected)) Icon(Icons.Outlined.Check, null, Modifier.size(18.dp), tint = Color.White) }
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

/** Small text under a form card. */
@Composable
fun FormHint(text: String) {
    Text(
        text, Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * Field of a form card. [error] shows under it (in red with [isError]; a
 * hint otherwise); [imeAction] and [onIme] set the keyboard's action key
 * (Next, Done...).
 */
@Composable
fun FormField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    keyboard: KeyboardType = KeyboardType.Text,
    secret: Boolean = false,
    singleLine: Boolean = true,
    error: String? = null,
    isError: Boolean = error != null,
    imeAction: ImeAction = ImeAction.Default,
    onIme: (() -> Unit)? = null,
    mono: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    val focus = LocalFocusManager.current
    TextField(
        value, onChange, modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 2,
        trailingIcon = trailing,
        isError = isError,
        supportingText = error?.let { e -> @Composable { Text(e) } },
        textStyle = if (mono) LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace) else LocalTextStyle.current,
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboard,
            imeAction = imeAction,
            autoCorrectEnabled = if (singleLine || mono || keyboard != KeyboardType.Text) false else null,
        ),
        keyboardActions = KeyboardActions(
            onNext = { if (onIme != null) onIme() else focus.moveFocus(FocusDirection.Down) },
            onDone = { if (onIme != null) onIme() else focus.clearFocus() },
        ),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            errorContainerColor = Color.Transparent,
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
