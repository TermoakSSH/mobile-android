package com.termoak.app.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import androidx.navigation.NavHostController
import com.termoak.app.R
import com.termoak.app.TermoakApp
import com.termoak.app.data.canEdit
import com.termoak.app.userMessage
import com.termoak.ffi.AccountStatus
import com.termoak.ffi.ItemFilter
import com.termoak.ffi.ItemRef
import com.termoak.ffi.NewVault
import com.termoak.ffi.TermoakException
import com.termoak.ffi.TransferMode
import com.termoak.ffi.TransferResult
import com.termoak.ffi.VaultChanges
import com.termoak.ffi.VaultInfo
import com.termoak.ffi.VaultKind
import com.termoak.ffi.VaultMember
import com.termoak.ffi.VaultMemberKind
import com.termoak.ffi.VaultMemberTarget
import com.termoak.ffi.VaultRole
import kotlinx.coroutines.launch
import org.json.JSONArray

/** Colors offered for a vault (the hosts' palette). */
private val VaultColors = listOf("#4f7cff", "#30a46c", "#f5a524", "#e5484d", "#8e4ec6", "#0ea5e9", "#d6409f", "#12a594")

/** An icon offered for a vault: the name stored in it (the iOS app's), its picture and its label. */
private class VaultIconChoice(val name: String, val icon: ImageVector, @param:StringRes val label: Int)

/** Icons offered for a vault, in the iOS app's order and with its names. */
private val VaultIconChoices = listOf(
    VaultIconChoice("vault", Icons.Outlined.Security, R.string.vault_icon_vault),
    VaultIconChoice("folder", Icons.Outlined.Folder, R.string.vault_icon_folder),
    VaultIconChoice("server", Icons.Outlined.Dns, R.string.vault_icon_server),
    VaultIconChoice("cloud", Icons.Outlined.Cloud, R.string.vault_icon_cloud),
    VaultIconChoice("briefcase", Icons.Outlined.Work, R.string.vault_icon_briefcase),
    VaultIconChoice("house", Icons.Outlined.Home, R.string.vault_icon_house),
    VaultIconChoice("star", Icons.Outlined.StarOutline, R.string.vault_icon_star),
    VaultIconChoice("key", Icons.Outlined.Key, R.string.vault_icon_key),
    VaultIconChoice("terminal", Icons.Outlined.Terminal, R.string.vault_icon_terminal),
    VaultIconChoice("globe", Icons.Outlined.Public, R.string.vault_icon_globe),
    VaultIconChoice("bolt", Icons.Outlined.Bolt, R.string.vault_icon_bolt),
    VaultIconChoice("person", Icons.Outlined.Group, R.string.vault_icon_person),
)

/**
 * Pictures of the icon names other apps store (the desktop's Lucide names, the
 * web's, and this app's earlier ones), so a vault looks the same everywhere.
 */
private val VaultIconAliases = mapOf(
    "lock" to Icons.Outlined.Lock,
    "work" to Icons.Outlined.Work,
    "home" to Icons.Outlined.Home,
    "code" to Icons.Outlined.Code,
    "shield" to Icons.Outlined.Security,
    "shield-check" to Icons.Outlined.VerifiedUser,
    "team" to Icons.Outlined.Groups,
    "users" to Icons.Outlined.Groups,
    "business" to Icons.Outlined.Business,
    "building-2" to Icons.Outlined.Business,
    "database" to Icons.Outlined.Storage,
    "key-round" to Icons.Outlined.Key,
    "box" to Icons.Outlined.Inventory2,
    "zap" to Icons.Outlined.Bolt,
)

private fun iconNamed(name: String?): ImageVector? =
    name?.let { n -> VaultIconChoices.firstOrNull { it.name == n }?.icon ?: VaultIconAliases[n] }

fun vaultIcon(v: VaultInfo): ImageVector = iconNamed(v.icon) ?: when (v.kind) {
    VaultKind.PERSONAL -> Icons.Outlined.Person
    VaultKind.TEAM -> Icons.Outlined.Groups
    else -> Icons.Outlined.Folder
}

/** Square tile of a vault: its icon on its color. */
@Composable
fun VaultTile(v: VaultInfo, size: Dp = 40.dp) {
    val c = vaultColor(v)
    Box(Modifier.size(size).clip(RoundedCornerShape(size / 4)).background(c.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
        Icon(vaultIcon(v), null, Modifier.size(size * 0.55f), tint = c)
    }
}

@Composable
fun roleText(role: VaultRole): String = stringResource(
    when (role) {
        VaultRole.MANAGER -> R.string.vault_role_manager
        VaultRole.EDITOR -> R.string.vault_role_editor
        VaultRole.USE_ONLY -> R.string.vault_use_only
        VaultRole.UNKNOWN -> R.string.vault_role_unknown
    },
)

/** Kind and owner: "Personal", "Shared by Ana", "Team Ops". */
@Composable
private fun vaultKindText(v: VaultInfo): String = when (v.kind) {
    VaultKind.PERSONAL -> stringResource(R.string.vault_kind_personal)
    VaultKind.TEAM -> stringResource(R.string.vault_kind_team, v.teamName ?: v.ownerName ?: "")
    else -> if (v.role == VaultRole.MANAGER) stringResource(R.string.vault_kind_shared_mine)
    else stringResource(R.string.vault_kind_shared, v.ownerName ?: "")
}

// ----- Vault list -----

/** The vaults of every account: open one, or create a new one. */
@Composable
fun VaultsScreen(app: TermoakApp, nav: NavHostController) {
    val accounts by app.accounts.list.collectAsState()
    val vaults by app.accounts.vaults.collectAsState()
    var creating by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { app.accounts.sync() }
    val withVaults = accounts.filter { it.vaultsSupported && it.status != AccountStatus.UNVERIFIED }
    // Desktop layout: a section of the sidebar, without a way back.
    val desktop = LocalDesktop.current
    ScreenScaffold(
        title = stringResource(R.string.vaults_title),
        large = desktop,
        navigationIcon = {
            if (!desktop) {
                IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back)) }
            }
        },
        floatingActionButton = {
            if (withVaults.any { it.status == AccountStatus.ACTIVE }) {
                ExtendedFloatingActionButton(
                    onClick = { creating = true }, icon = { Icon(Icons.Outlined.Add, null) },
                    text = { Text(stringResource(R.string.vaults_new)) },
                )
            }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 96.dp)) {
            item {
                Text(
                    stringResource(R.string.vaults_explain), Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            accounts.forEach { a ->
                val mine = vaults.filter { it.accountId == a.id }
                if (accounts.size > 1) item(key = "a" + a.id) { SectionLabel(a.email) }
                if (!a.vaultsSupported) {
                    item(key = "nv" + a.id) {
                        Text(
                            stringResource(R.string.accounts_no_vaults), Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else if (mine.isEmpty()) {
                    item(key = "e" + a.id) {
                        Text(
                            stringResource(R.string.vaults_not_synced), Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(mine, key = { a.id + "/" + it.id }) { v ->
                    ListItem(
                        modifier = Modifier.clickable { nav.navigate(Routes.vault(v.accountId, v.id)) },
                        leadingContent = { VaultTile(v) },
                        headlineContent = { Text(vaultName(v), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = {
                            Text(
                                listOfNotNull(
                                    vaultKindText(v),
                                    pluralStringResource(R.plurals.hosts_count, v.hostCount.toInt(), v.hostCount.toInt()),
                                    v.memberCount.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.vault_members_count, it.toInt(), it.toInt()) },
                                ).joinToString(" · "),
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        },
                        trailingContent = {
                            Column(horizontalAlignment = Alignment.End) {
                                Pill(roleText(v.role), if (v.role == VaultRole.USE_ONLY) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary)
                                if (v.strict) {
                                    Text(
                                        stringResource(R.string.vault_strict), Modifier.padding(top = 2.dp),
                                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }
        }
    }
    if (creating) NewVaultDialog(app, onDismiss = { creating = false }) { v ->
        creating = false
        nav.navigate(Routes.vault(v.accountId, v.id))
    }
}

/** A team of an account (to own or share a vault). */
private data class TeamRef(val id: String, val name: String, val admin: Boolean)

private suspend fun teamsOf(app: TermoakApp, accountId: String): List<TeamRef> = runCatching {
    val arr = JSONArray(app.accounts.handle(accountId)?.apiGet("/api/v1/teams") ?: "[]")
    (0 until arr.length()).map { i ->
        val t = arr.getJSONObject(i)
        TeamRef(t.getString("id"), t.optString("name"), t.optString("role") in setOf("owner", "admin"))
    }
}.getOrDefault(emptyList())

/**
 * New vault, like the iOS app's: account (with several), name, description,
 * owner (you or a team you manage, with the team members' role), color
 * (automatic or one of the palette), icon, and Strict from the start.
 */
@Composable
private fun NewVaultDialog(app: TermoakApp, onDismiss: () -> Unit, onCreated: (VaultInfo) -> Unit) {
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val accounts = remember { app.accounts.active().filter { it.vaultsSupported } }
    var accountId by remember { mutableStateOf(app.accounts.current.value?.id?.takeIf { c -> accounts.any { it.id == c } } ?: accounts.firstOrNull()?.id) }
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var color by remember { mutableStateOf<String?>(null) }
    var icon by remember { mutableStateOf<String?>(null) }
    var strict by remember { mutableStateOf(false) }
    var teams by remember { mutableStateOf<List<TeamRef>>(emptyList()) }
    var teamId by remember { mutableStateOf<String?>(null) }
    var teamRole by remember { mutableStateOf(VaultRole.EDITOR) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(accountId) {
        teamId = null
        teams = accountId?.let { teamsOf(app, it) }.orEmpty().filter { it.admin }
    }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.vaults_new)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (accounts.size > 1) {
                    Picker(
                        stringResource(R.string.vault_account), accounts.firstOrNull { it.id == accountId }?.email, null,
                        accounts.map { it.id to it.email },
                    ) { accountId = it }
                }
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.common_name)) }, singleLine = true)
                OutlinedTextField(
                    description, { description = it }, Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.vault_description)) }, singleLine = true,
                )
                if (teams.isNotEmpty()) {
                    Picker(
                        stringResource(R.string.vault_owner),
                        teams.firstOrNull { it.id == teamId }?.name ?: stringResource(R.string.vault_owner_me), null,
                        listOf<Pair<String?, String>>(null to stringResource(R.string.vault_owner_me)) + teams.map { it.id to it.name },
                    ) { teamId = it }
                    if (teamId != null) {
                        Picker(
                            stringResource(R.string.vault_team_members_role), roleText(teamRole), null,
                            listOf(VaultRole.EDITOR to roleText(VaultRole.EDITOR), VaultRole.USE_ONLY to roleText(VaultRole.USE_ONLY)),
                        ) { teamRole = it }
                    }
                    Text(stringResource(R.string.vault_owner_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(stringResource(R.string.editor_color), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ColorDots(color, automatic = true) { color = it }
                Text(stringResource(R.string.vault_icon), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                VaultIconGrid(icon) { icon = it }
                Row(
                    Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable { strict = !strict },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.vault_strict), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Switch(strict, { strict = it })
                }
                Text(stringResource(R.string.vault_strict_new_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && name.isNotBlank() && accountId != null, onClick = {
                val id = accountId ?: return@TextButton
                busy = true
                error = null
                scope.launch {
                    try {
                        val v = app.accounts.handle(id)!!.createVault(
                            NewVault(
                                name = name.trim(), description = description.trim().ifEmpty { null }, color = color, icon = icon,
                                teamId = teamId, teamMemberRole = teamRole.takeIf { teamId != null }, strict = strict,
                            ),
                        )
                        app.accounts.refreshNow()
                        onCreated(v)
                    } catch (e: TermoakException) {
                        error = e.userMessage(resources, R.string.vault_save_failed)
                    } finally {
                        busy = false
                    }
                }
            }) { Text(stringResource(R.string.vaults_create)) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.common_cancel)) } },
    )
}

/** The palette; with [automatic], a dashed circle first for no color (the vault's default). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorDots(selected: String?, automatic: Boolean = false, onSelect: (String?) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (automatic) {
            val auto = stringResource(R.string.vault_color_auto)
            Box(
                Modifier.size(32.dp).clip(CircleShape)
                    .border(2.dp, if (selected == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape)
                    .clickable(onClickLabel = auto) { onSelect(null) }
                    .semantics { contentDescription = auto },
                contentAlignment = Alignment.Center,
            ) {
                if (selected == null) Icon(Icons.Outlined.Check, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
        VaultColors.forEach { hex ->
            val c = Color(hex.toColorInt())
            Box(
                Modifier.size(32.dp).clip(CircleShape).background(c).clickable { onSelect(hex) },
                contentAlignment = Alignment.Center,
            ) {
                if (hex.equals(selected, ignoreCase = true)) Icon(Icons.Outlined.Check, null, Modifier.size(18.dp), tint = Color.White)
            }
        }
    }
}

/** The vault icons as a grid of tiles, the chosen one highlighted. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VaultIconGrid(selected: String?, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        VaultIconChoices.forEach { choice ->
            val on = choice.name == selected
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                    .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest)
                    .clickable { onSelect(choice.name) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    choice.icon, stringResource(choice.label), Modifier.size(20.dp),
                    tint = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

// ----- One vault -----

/**
 * A vault: name, color and icon; its members (add by email or team, as
 * Editor or Use only; change the role; remove); the Strict switch; leave
 * it, or delete it after typing its name.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VaultScreen(app: TermoakApp, nav: NavHostController, accountId: String, vaultId: String) {
    val resources = LocalResources.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val vaults by app.accounts.vaults.collectAsState()
    val v = vaults.firstOrNull { it.id == vaultId && it.accountId == accountId }
    var members by remember { mutableStateOf<List<VaultMember>?>(null) }
    var membersError by remember { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<VaultMember?>(null) }
    var deleting by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    var iconMenu by remember { mutableStateOf(false) }
    val handle = remember(accountId) { app.accounts.handle(accountId) }

    suspend fun loadMembers() {
        val h = handle ?: return
        try {
            members = h.vaultMembers(vaultId)
            membersError = null
        } catch (e: TermoakException) {
            membersError = e.userMessage(resources, R.string.vault_members_failed)
        }
    }
    LaunchedEffect(vaultId) { if (v?.kind != VaultKind.PERSONAL) loadMembers() }

    fun update(changes: VaultChanges) {
        val h = handle ?: return
        scope.launch {
            try {
                h.updateVault(vaultId, changes)
                app.accounts.refreshNow()
            } catch (e: TermoakException) {
                snackbar.showSnackbar(e.userMessage(resources, R.string.vault_save_failed))
            }
        }
    }

    ScreenScaffold(
        title = v?.let { vaultName(it) } ?: stringResource(R.string.vaults_title),
        subtitle = v?.let { vaultKindText(it) },
        navigationIcon = {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back)) }
        },
    ) { padding ->
        if (v == null) {
            EmptyState(Icons.Outlined.Lock, stringResource(R.string.vault_gone_title), stringResource(R.string.vault_gone_text), Modifier.padding(padding))
            return@ScreenScaffold
        }
        val manager = v.role == VaultRole.MANAGER
        val personal = v.kind == VaultKind.PERSONAL
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box {
                    Box(Modifier.clip(RoundedCornerShape(14.dp)).clickable(enabled = manager) { iconMenu = true }) { VaultTile(v, 56.dp) }
                    DropdownMenu(iconMenu, { iconMenu = false }) {
                        DropdownMenuItem(
                            { Text(stringResource(R.string.vault_icon_default)) },
                            { iconMenu = false; update(VaultChanges(clearIcon = true)) },
                            leadingIcon = { Icon(vaultIcon(v.copy(icon = null)), null) },
                            trailingIcon = { if (v.icon == null) Icon(Icons.Outlined.Check, null) },
                        )
                        VaultIconChoices.forEach { choice ->
                            DropdownMenuItem(
                                { Text(stringResource(choice.label)) },
                                { iconMenu = false; update(VaultChanges(icon = choice.name)) },
                                leadingIcon = { Icon(choice.icon, null) },
                                trailingIcon = { if (v.icon == choice.name) Icon(Icons.Outlined.Check, null) },
                            )
                        }
                    }
                }
                Column(Modifier.padding(start = 16.dp).weight(1f)) {
                    Text(vaultName(v), style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (v.description.isNotBlank()) {
                        Text(v.description, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Text(
                        listOfNotNull(
                            roleText(v.role),
                            pluralStringResource(R.plurals.hosts_count, v.hostCount.toInt(), v.hostCount.toInt()),
                            stringResource(R.string.vault_strict).takeIf { v.strict },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (manager) {
                    IconButton(onClick = { renaming = true }) { Icon(Icons.Outlined.DriveFileRenameOutline, stringResource(R.string.vault_edit)) }
                }
            }
            if (manager) {
                FormSection(stringResource(R.string.editor_color)) {
                    Box(Modifier.padding(16.dp)) {
                        ColorDots(v.color, automatic = true) { update(if (it == null) VaultChanges(clearColor = true) else VaultChanges(color = it)) }
                    }
                }
            }
            if (v.role == VaultRole.USE_ONLY) UseOnlyNote()

            if (!personal) {
                SectionLabel(stringResource(R.string.vault_members)) {
                    if (manager) {
                        TextButton(onClick = { adding = true }) {
                            Icon(Icons.Outlined.PersonAdd, null, Modifier.size(18.dp))
                            Text(stringResource(R.string.vault_add_member), Modifier.padding(start = 6.dp))
                        }
                    }
                }
                val list = members
                when {
                    membersError != null -> FormNote(membersError ?: "")
                    list == null -> CircularProgressIndicator(Modifier.padding(20.dp).size(24.dp), strokeWidth = 2.dp)
                    else -> list.forEach { m ->
                        MemberRow(m, canManage = manager && !m.implicit, onRole = { role ->
                            scope.launch {
                                try {
                                    handle?.setVaultMemberRole(vaultId, m.id, role)
                                    loadMembers()
                                } catch (e: TermoakException) {
                                    snackbar.showSnackbar(e.userMessage(resources, R.string.vault_save_failed))
                                }
                            }
                        }, onRemove = { removing = m })
                    }
                }
                FormHint(stringResource(R.string.vault_roles_hint))

                if (manager) {
                    FormSection(stringResource(R.string.vault_security)) {
                        FormSwitch(stringResource(R.string.vault_strict), stringResource(R.string.vault_strict_hint), v.strict) {
                            update(VaultChanges(strict = it))
                        }
                        if (v.kind == VaultKind.TEAM) {
                            FormDivider()
                            val options = listOf<Pair<VaultRole?, String>>(
                                VaultRole.EDITOR to stringResource(R.string.vault_role_editor),
                                VaultRole.USE_ONLY to stringResource(R.string.vault_use_only),
                                null to stringResource(R.string.vault_team_no_access),
                            )
                            FormPicker(
                                stringResource(R.string.vault_team_members_role),
                                options.firstOrNull { it.first == v.teamMemberRole }?.second ?: options.last().second,
                                options,
                            ) { r -> update(if (r == null) VaultChanges(noTeamAccess = true) else VaultChanges(teamMemberRole = r)) }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                if (!manager) {
                    OutlinedButton(onClick = { leaving = true }, modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                        Icon(Icons.AutoMirrored.Filled.Logout, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                        Text(stringResource(R.string.vault_leave), Modifier.padding(start = 8.dp), color = MaterialTheme.colorScheme.error)
                    }
                } else {
                    OutlinedButton(onClick = { deleting = true }, modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                        Icon(Icons.Outlined.Delete, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                        Text(stringResource(R.string.vault_delete), Modifier.padding(start = 8.dp), color = MaterialTheme.colorScheme.error)
                    }
                }
            } else {
                FormHint(stringResource(R.string.vault_personal_hint))
            }
        }
    }

    val vault = v ?: return
    if (renaming) {
        // Name and (not for the personal vault) description.
        val personalVault = vault.kind == VaultKind.PERSONAL
        var name by remember { mutableStateOf(vault.name) }
        var description by remember { mutableStateOf(vault.description) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text(stringResource(R.string.vault_edit)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.common_name)) }, singleLine = true)
                    if (!personalVault) {
                        OutlinedTextField(
                            description, { description = it }, label = { Text(stringResource(R.string.vault_description)) },
                            singleLine = true,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    renaming = false
                    val newName = name.trim().takeIf { it != vault.name }
                    val newDescription = description.trim().takeIf { !personalVault && it != vault.description }
                    if (newName != null || newDescription != null) update(VaultChanges(name = newName, description = newDescription))
                }) {
                    Text(stringResource(R.string.common_save))
                }
            },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
    if (adding) {
        AddMemberDialog(app, accountId, onDismiss = { adding = false }) { target, role ->
            val h = handle ?: return@AddMemberDialog
            h.addVaultMember(vaultId, target, role)
            adding = false
            loadMembers()
            app.accounts.refreshNow()
        }
    }
    removing?.let { m ->
        ConfirmDialog(
            stringResource(R.string.vault_remove_member_title, m.email ?: m.name),
            stringResource(R.string.vault_remove_member_text, vaultName(vault)),
            stringResource(R.string.host_remove), destructive = true, onDismiss = { removing = null },
        ) {
            scope.launch {
                try {
                    handle?.removeVaultMember(vaultId, m.id)
                    loadMembers()
                    app.accounts.refreshNow()
                } catch (e: TermoakException) {
                    snackbar.showSnackbar(e.userMessage(resources, R.string.vault_save_failed))
                }
            }
        }
    }
    if (leaving) {
        ConfirmDialog(
            stringResource(R.string.vault_leave_title, vaultName(vault)), stringResource(R.string.vault_leave_text),
            stringResource(R.string.vault_leave), destructive = true, onDismiss = { leaving = false },
        ) {
            scope.launch {
                try {
                    handle?.leaveVault(vaultId)
                    app.accounts.sync(accountId)
                    nav.popBackStack()
                } catch (e: TermoakException) {
                    snackbar.showSnackbar(e.userMessage(resources, R.string.vault_leave_failed))
                }
            }
        }
    }
    if (deleting) {
        DeleteVaultDialog(vault, members?.size ?: vault.memberCount.toInt(), onDismiss = { deleting = false }) { typed ->
            handle?.deleteVault(vaultId, typed)
            deleting = false
            app.accounts.setVaultFilter(null)
            app.accounts.sync(accountId)
            nav.popBackStack()
            snackbar.showSnackbar(resources.getString(R.string.vault_deleted, vault.name))
        }
    }
}

@Composable
private fun MemberRow(m: VaultMember, canManage: Boolean, onRole: (VaultRole) -> Unit, onRemove: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    ListItem(
        leadingContent = {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (m.kind == VaultMemberKind.TEAM) Icons.Outlined.Group else Icons.Outlined.Person, null)
            }
        },
        headlineContent = { Text(m.name.ifBlank { m.email ?: "" }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(
                listOfNotNull(
                    m.email?.takeIf { it != m.name && m.name.isNotBlank() },
                    stringResource(R.string.vault_member_team).takeIf { m.kind == VaultMemberKind.TEAM },
                    stringResource(R.string.vault_member_implicit).takeIf { m.implicit },
                ).joinToString(" · ").ifEmpty { roleText(m.role) },
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            if (!canManage) {
                Pill(roleText(m.role), MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        TextButton(onClick = { menu = true }) { Text(roleText(m.role)) }
                        DropdownMenu(menu, { menu = false }) {
                            listOf(VaultRole.EDITOR, VaultRole.USE_ONLY).forEach { r ->
                                DropdownMenuItem(
                                    { Text(roleText(r)) }, { menu = false; if (r != m.role) onRole(r) },
                                    trailingIcon = { if (r == m.role) Icon(Icons.Outlined.Check, null) },
                                )
                            }
                        }
                    }
                    IconButton(onClick = onRemove) { Icon(Icons.Outlined.Close, stringResource(R.string.host_remove)) }
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

/** Share with a person (email) or a team you belong to, as Editor or Use only. */
@Composable
private fun AddMemberDialog(
    app: TermoakApp,
    accountId: String,
    onDismiss: () -> Unit,
    onAdd: suspend (VaultMemberTarget, VaultRole) -> Unit,
) {
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    var team by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var teams by remember { mutableStateOf<List<TeamRef>>(emptyList()) }
    var teamId by remember { mutableStateOf<String?>(null) }
    var role by remember { mutableStateOf(VaultRole.EDITOR) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(accountId) { teams = teamsOf(app, accountId) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.vault_add_member)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(!team, { team = false }, SegmentedButtonDefaults.itemShape(0, 2)) { Text(stringResource(R.string.vault_member_person)) }
                    SegmentedButton(team, { team = true }, SegmentedButtonDefaults.itemShape(1, 2)) { Text(stringResource(R.string.vault_member_team)) }
                }
                if (!team) {
                    OutlinedTextField(
                        email, { email = it.trim() }, Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.login_email)) }, singleLine = true,
                    )
                } else if (teams.isEmpty()) {
                    Text(stringResource(R.string.vault_no_teams), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Picker(stringResource(R.string.vault_member_team), teams.firstOrNull { it.id == teamId }?.name, null, teams.map { it.id to it.name }) {
                        teamId = it
                    }
                }
                Text(stringResource(R.string.vault_role), style = MaterialTheme.typography.titleSmall)
                listOf(VaultRole.EDITOR to R.string.vault_role_editor_hint, VaultRole.USE_ONLY to R.string.vault_role_use_only_hint).forEach { (r, hint) ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { role = r },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(role == r, { role = r })
                        Column {
                            Text(roleText(r))
                            Text(stringResource(hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            val target = if (team) teamId?.let { VaultMemberTarget.Team(it) } else email.takeIf { it.contains('@') }?.let { VaultMemberTarget.User(it) }
            TextButton(enabled = !busy && target != null, onClick = {
                val t = target ?: return@TextButton
                busy = true
                error = null
                scope.launch {
                    try {
                        onAdd(t, role)
                    } catch (e: TermoakException.NotFound) {
                        error = resources.getString(R.string.vault_member_not_found)
                    } catch (e: TermoakException) {
                        error = e.userMessage(resources, R.string.vault_save_failed)
                    } finally {
                        busy = false
                    }
                }
            }) { Text(stringResource(R.string.vault_share)) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.common_cancel)) } },
    )
}

/** Deleting a vault: what goes with it, and its name typed to confirm. */
@Composable
private fun DeleteVaultDialog(v: VaultInfo, members: Int, onDismiss: () -> Unit, onDelete: suspend (String) -> Unit) {
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    var typed by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        icon = { Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error) },
        title = { Text(stringResource(R.string.vault_delete_title, v.name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val hosts = v.hostCount.toInt()
                Text(
                    pluralStringResource(R.plurals.vault_delete_hosts, hosts, hosts) + " " +
                        pluralStringResource(R.plurals.vault_delete_members, members, members),
                )
                Text(stringResource(R.string.vault_delete_type, v.name), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(typed, { typed = it }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text(v.name) })
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && typed.trim() == v.name, onClick = {
                busy = true
                scope.launch {
                    try {
                        onDelete(typed.trim())
                    } catch (e: TermoakException) {
                        error = e.userMessage(resources, R.string.vault_delete_failed)
                        busy = false
                    }
                }
            }) { Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.common_cancel)) } },
    )
}

// ----- Move and copy -----

/** An item to move or copy: where it is and its id. */
data class TransferItem(val accountId: String?, val id: String, val vaultId: String?)

/** A move or copy the user asked for. */
data class TransferRequest(val mode: TransferMode, val items: List<TransferItem>)

/** A place to move or copy to: This device (`account == null`) or a vault of an account. */
private data class Destination(val account: String?, val vault: String?, val label: String, val detail: String?, val color: Color?)

/**
 * "Move to…" / "Copy to…": the destination (This device or a vault where
 * you are Editor), the plan of the move (dry run: "This will also move key
 * deploy") to confirm, and the move.
 */
@Composable
fun TransferFlow(app: TermoakApp, request: TransferRequest, onDismiss: () -> Unit, onDone: () -> Unit) {
    val resources = LocalResources.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val accounts by app.accounts.list.collectAsState()
    val vaults by app.accounts.vaults.collectAsState()
    val deviceLabel = stringResource(R.string.vault_this_device)
    val personal = stringResource(R.string.vault_personal)
    val destinations = remember(accounts, vaults) {
        val sources = request.items.map { it.accountId to it.vaultId }.toSet()
        val single = sources.singleOrNull()
        buildList {
            if (single == null || single.first != null) add(Destination(null, null, deviceLabel, null, null))
            accounts.filter { it.status != AccountStatus.UNVERIFIED }.forEach { a ->
                val detail = a.email.takeIf { accounts.size > 1 }
                if (!a.vaultsSupported) {
                    if (single?.first != a.id) add(Destination(a.id, null, a.email, null, null))
                } else {
                    vaults.filter { it.accountId == a.id && it.role.canEdit() }.forEach { v ->
                        if (single != (a.id to v.id) && !(single?.first == a.id && single.second == null && v.kind == VaultKind.PERSONAL)) {
                            val name = if (v.kind == VaultKind.PERSONAL) personal else v.name
                            add(Destination(a.id, v.id, name, detail, v.color?.let { c -> runCatching { Color(c.toColorInt()) }.getOrNull() }))
                        }
                    }
                }
            }
        }
    }
    var target by remember { mutableStateOf<Destination?>(null) }
    var plan by remember { mutableStateOf<TransferResult?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val names = remember { itemNames(app) }
    val groups = request.items.groupBy { it.accountId }

    suspend fun run(dest: Destination, dryRun: Boolean): TransferResult {
        val results = groups.map { (_, items) ->
            app.core.transfer(items.map { ItemRef(it.accountId, it.id) }, dest.account, dest.vault, request.mode, dryRun)
        }
        return TransferResult(
            moved = results.flatMap { it.moved }, copied = results.flatMap { it.copied }, reused = results.flatMap { it.reused },
            detached = results.flatMap { it.detached }, warnings = results.flatMap { it.warnings }, dryRun = dryRun,
        )
    }

    fun planFor(dest: Destination) {
        target = dest
        busy = true
        error = null
        scope.launch {
            try {
                plan = run(dest, dryRun = true)
            } catch (e: TermoakException) {
                error = e.userMessage(resources, R.string.transfer_failed)
            } finally {
                busy = false
            }
        }
    }

    val move = request.mode == TransferMode.MOVE
    val dest = target
    val p = plan
    if (dest == null || (p == null && error == null && !busy)) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(
                    pluralStringResource(
                        if (move) R.plurals.transfer_move_title else R.plurals.transfer_copy_title, request.items.size, request.items.size,
                    ),
                )
            },
            text = {
                LazyColumn(Modifier.heightIn(max = 400.dp)) {
                    if (destinations.isEmpty()) item { Text(stringResource(R.string.transfer_no_destination)) }
                    items(destinations, key = { "${it.account}/${it.vault}" }) { d ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { planFor(d) }.padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (d.account == null) {
                                Icon(Icons.Outlined.PhoneAndroid, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                                    Box(Modifier.size(12.dp).clip(CircleShape).background(d.color ?: MaterialTheme.colorScheme.primary))
                                }
                            }
                            Column(Modifier.padding(start = 12.dp)) {
                                Text(d.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                d.detail?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
        )
        return
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = {
            Text(
                stringResource(
                    if (move) R.string.transfer_confirm_move else R.string.transfer_confirm_copy,
                    pluralStringResource(R.plurals.transfer_items, request.items.size, request.items.size), dest.label,
                ),
            )
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (p == null && error == null) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                p?.let { r -> PlanText(r, request, names) }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && p != null, onClick = {
                busy = true
                error = null
                scope.launch {
                    try {
                        val r = run(dest, dryRun = false)
                        val n = request.items.size
                        onDone()
                        app.accounts.sync()
                        snackbar.showSnackbar(
                            resources.getQuantityString(if (move) R.plurals.transfer_moved else R.plurals.transfer_copied, n, n, dest.label),
                        )
                        if (r.warnings.isNotEmpty()) {
                            snackbar.showSnackbar(resources.getQuantityString(R.plurals.transfer_warnings, r.warnings.size, r.warnings.size))
                        }
                    } catch (e: TermoakException) {
                        error = e.userMessage(resources, R.string.transfer_failed)
                        busy = false
                    }
                }
            }) {
                if (busy && p != null) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text(stringResource(if (move) R.string.transfer_move else R.string.transfer_copy))
            }
        },
        dismissButton = {
            TextButton(onClick = { target = null; plan = null; error = null }, enabled = !busy) { Text(stringResource(R.string.common_back)) }
        },
    )
}

/** What the plan does besides the items chosen: dependencies moved or copied, reused, references cleared. */
@Composable
private fun PlanText(r: TransferResult, request: TransferRequest, names: Map<String, String>) {
    val chosen = request.items.map { it.id }.toSet()
    fun name(kind: String, id: String) = names[id] ?: kind
    val alsoMoved = r.moved.filter { it.id !in chosen }.map { name(it.kind, it.id) }
    val alsoCopied = r.copied.filter { it.fromId !in chosen }.map { name(it.kind, it.fromId) }
    val reused = r.reused.map { name(it.kind, it.fromId) }
    if (alsoMoved.isEmpty() && alsoCopied.isEmpty() && reused.isEmpty() && r.detached.isEmpty()) {
        Text(stringResource(R.string.transfer_plan_simple))
    }
    if (alsoMoved.isNotEmpty()) Text(stringResource(R.string.transfer_plan_moves, alsoMoved.joinToString(", ")))
    if (alsoCopied.isNotEmpty()) Text(stringResource(R.string.transfer_plan_copies, alsoCopied.joinToString(", ")))
    if (reused.isNotEmpty()) Text(stringResource(R.string.transfer_plan_reuses, reused.joinToString(", ")))
    if (r.detached.isNotEmpty()) {
        Text(
            stringResource(R.string.transfer_plan_detached, r.detached.joinToString(", ") { "${name(it.kind, it.id)} (${it.field})" }),
            color = Brand.Amber,
        )
    }
    if (request.mode == TransferMode.MOVE) {
        Text(stringResource(R.string.transfer_plan_ids), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Names of every item (for the plan), by id. */
private fun itemNames(app: TermoakApp): Map<String, String> = runCatching {
    val all = ItemFilter()
    buildMap {
        app.core.listHosts(all).forEach { put(it.id, it.label) }
        app.core.listGroups(all).forEach { put(it.id, it.name) }
        app.core.listKeys(all).forEach { put(it.id, it.label) }
        app.core.listIdentities(all).forEach { put(it.id, it.label) }
        app.core.listSnippets(all).forEach { put(it.id, it.name) }
        app.core.listForwards(null, all).forEach { put(it.id, it.label) }
        app.core.listKnownHosts(all).forEach { put(it.id, it.host) }
    }
}.getOrDefault(emptyMap())

/** A small marker (border) for chips of a vault. */
@Composable
fun VaultDot(v: VaultInfo?, size: Dp = 10.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(vaultColor(v)).border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape))
}
