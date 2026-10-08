package com.termoak.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.termoak.app.R
import com.termoak.app.TermoakApp
import com.termoak.app.data.AccountTeams
import com.termoak.app.data.TeamPerson
import com.termoak.app.userMessage
import com.termoak.ffi.AccountInfo
import com.termoak.ffi.AccountStatus
import com.termoak.ffi.TermoakException
import kotlinx.coroutines.launch
import org.json.JSONArray

/** A team you belong to on a server, with your role there (`null`: a server admin who isn't a member). */
private data class TeamInfo(val id: String, val accountId: String, val name: String, val role: String?, val members: Int)


/** The teams of every signed-in account (the server's /api/v1/teams), or `null` for an account that couldn't be read. */
private suspend fun loadTeams(app: TermoakApp, a: AccountInfo): List<TeamInfo>? = runCatching {
    val arr = JSONArray(app.accounts.handle(a.id)?.apiGet("/api/v1/teams") ?: return null)
    (0 until arr.length()).map { i ->
        val t = arr.getJSONObject(i)
        TeamInfo(
            t.getString("id"), a.id, t.optString("name"),
            t.optString("role").takeIf { it.isNotEmpty() && it != "null" }, t.optInt("member_count"),
        )
    }.sortedBy { it.name.lowercase() }
}.getOrNull()

private fun teamsOf(app: TermoakApp, accountId: String): AccountTeams? = app.accounts.handle(accountId)?.let { AccountTeams(it) }

@Composable
private fun teamRoleText(role: String?): String = when (role) {
    "owner" -> stringResource(R.string.teams_role_owner)
    "admin" -> stringResource(R.string.teams_role_admin)
    "member" -> stringResource(R.string.teams_role_member)
    else -> stringResource(R.string.teams_role_none)
}

/**
 * Teams (Settings, or the sidebar of the desktop layout), as on iOS and the
 * desktop: the ones you belong to on each signed-in server, with your role;
 * create one (you own it); open one to see its members, add people by email
 * with a role, change roles and remove them (admins; only owners appoint
 * owners), rename it, delete it (owners) or leave it. Sessions and vaults
 * are shared with them from their own screens.
 */
@Composable
fun TeamsScreen(app: TermoakApp, nav: NavHostController) {
    val accounts by app.accounts.list.collectAsState()
    val active = accounts.filter { it.status == AccountStatus.ACTIVE }
    val scope = rememberCoroutineScope()
    var teams by remember { mutableStateOf<Map<String, List<TeamInfo>?>>(emptyMap()) }
    var loading by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf<TeamInfo?>(null) }
    var creating by remember { mutableStateOf(false) }
    fun reload() {
        scope.launch {
            loading = true
            teams = active.associate { it.id to loadTeams(app, it) }
            loading = false
        }
    }
    LaunchedEffect(active.map { it.id }) { reload() }
    val desktop = LocalDesktop.current
    ScreenScaffold(
        title = stringResource(R.string.teams_title),
        large = desktop,
        navigationIcon = {
            if (!desktop) {
                IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back)) }
            }
        },
        actions = {
            if (active.isNotEmpty()) {
                IconButton(onClick = { creating = true }) { Icon(Icons.Outlined.Add, stringResource(R.string.teams_new)) }
                IconButton(onClick = { reload() }, enabled = !loading) {
                    if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Outlined.Refresh, stringResource(R.string.common_refresh))
                }
            }
        },
    ) { padding ->
        if (active.isEmpty()) {
            EmptyState(
                Icons.Outlined.CloudOff, stringResource(R.string.teams_signed_out_title), stringResource(R.string.teams_signed_out_text),
                Modifier.padding(padding), action = stringResource(R.string.common_sign_in), onAction = { nav.navigate(Routes.login()) },
            )
            return@ScreenScaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Text(
                    stringResource(R.string.teams_explain), Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            active.forEach { a ->
                val list = teams[a.id]
                if (active.size > 1) item(key = "a" + a.id) { SectionLabel(a.email) }
                when {
                    a.id !in teams -> Unit
                    list == null -> item(key = "f" + a.id) { Note(stringResource(R.string.teams_load_failed)) }
                    list.isEmpty() -> item(key = "e" + a.id) { Note(stringResource(R.string.share_no_teams)) }
                }
                items(list.orEmpty(), key = { a.id + "/" + it.id }) { t ->
                    ListItem(
                        modifier = Modifier.clickable { open = t },
                        leadingContent = {
                            Box(
                                Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Outlined.Groups, null, tint = MaterialTheme.colorScheme.onPrimaryContainer) }
                        },
                        headlineContent = { Text(t.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text(pluralStringResource(R.plurals.vault_members_count, t.members, t.members)) },
                        trailingContent = { Pill(teamRoleText(t.role), MaterialTheme.colorScheme.primary) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }
        }
    }
    open?.let { t -> TeamMembersDialog(app, t, onChanged = { reload() }) { open = null } }
    if (creating) NewTeamDialog(app, active, onDismiss = { creating = false }) { creating = false; reload() }
}

/** A new team on one of your servers: you will be its owner. */
@Composable
private fun NewTeamDialog(app: TermoakApp, accounts: List<AccountInfo>, onDismiss: () -> Unit, onCreated: () -> Unit) {
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    var accountId by remember { mutableStateOf(app.accounts.current.value?.id?.takeIf { c -> accounts.any { it.id == c } } ?: accounts.first().id) }
    var name by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.teams_new)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (accounts.size > 1) {
                    Picker(stringResource(R.string.vault_account), accounts.firstOrNull { it.id == accountId }?.email, null,
                        accounts.map { it.id to it.email }) { accountId = it }
                }
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text(stringResource(R.string.common_name)) }, placeholder = { Text(stringResource(R.string.teams_name_placeholder)) })
                Text(stringResource(R.string.teams_new_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && name.isNotBlank(), onClick = {
                busy = true
                scope.launch {
                    try {
                        teamsOf(app, accountId)?.create(name)
                        onCreated()
                    } catch (e: TermoakException) {
                        error = e.userMessage(resources, R.string.teams_action_failed)
                    } finally {
                        busy = false
                    }
                }
            }) { Text(stringResource(R.string.teams_create)) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.common_cancel)) } },
    )
}

@Composable
private fun Note(text: String) {
    Text(
        text, Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * A team: its members and their roles. Admins add people (by email, with a
 * role), change roles and remove them; owners also appoint owners, rename
 * and delete the team; anyone can leave it.
 */
@Composable
private fun TeamMembersDialog(app: TermoakApp, team: TeamInfo, onChanged: () -> Unit, onDismiss: () -> Unit) {
    val resources = LocalResources.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val api = remember(team) { teamsOf(app, team.accountId) }
    var members by remember { mutableStateOf<List<TeamPerson>?>(null) }
    var me by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    // Leaving or deleting: its title and text, and what it does.
    var confirming by remember { mutableStateOf<Triple<String, String, suspend () -> Unit>?>(null) }
    var removing by remember { mutableStateOf<TeamPerson?>(null) }
    val manage = AccountTeams.canManage(team.role)
    val roles = AccountTeams.assignable(team.role)
    LaunchedEffect(team) {
        val list = runCatching { api?.members(team.id) }.getOrNull()
        failed = list == null
        members = list.orEmpty()
        me = runCatching { api?.myId() }.getOrNull()
    }
    fun act(action: suspend () -> Unit) {
        scope.launch {
            try {
                action()
                onChanged()
            } catch (e: TermoakException) {
                snackbar.showSnackbar(e.userMessage(resources, R.string.teams_action_failed))
            }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Groups, null) },
        title = { Text(team.name) },
        text = {
            Column {
                val list = members
                when {
                    list == null -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                    failed -> Text(stringResource(R.string.teams_load_failed))
                    else -> LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                        items(list, key = { it.userId.ifEmpty { it.email } }) { m ->
                            val mine = m.userId == me
                            // Admins manage members and admins; owners are managed by owners.
                            val editable = manage && !mine && (team.role == AccountTeams.OWNER || m.role != AccountTeams.OWNER)
                            ListItem(
                                headlineContent = {
                                    Text(
                                        m.name.ifBlank { m.email } + if (mine) " (" + stringResource(R.string.teams_you) + ")" else "",
                                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                supportingContent = if (m.name.isNotBlank()) ({ Text(m.email, maxLines = 1, overflow = TextOverflow.Ellipsis) }) else null,
                                trailingContent = {
                                    if (editable) {
                                        Box {
                                            var menu by remember { mutableStateOf(false) }
                                            TextButton(onClick = { menu = true }) { Text(teamRoleText(m.role)) }
                                            DropdownMenu(menu, { menu = false }) {
                                                roles.forEach { r ->
                                                    DropdownMenuItem({ Text(teamRoleText(r)) }, {
                                                        menu = false
                                                        act { members = api?.setRole(team.id, m.userId, r) ?: members }
                                                    })
                                                }
                                                HorizontalDivider()
                                                DropdownMenuItem(
                                                    { Text(stringResource(R.string.teams_remove), color = MaterialTheme.colorScheme.error) },
                                                    { menu = false; removing = m },
                                                )
                                            }
                                        }
                                    } else {
                                        Pill(teamRoleText(m.role), MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            )
                        }
                    }
                }
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (manage) TextButton(onClick = { adding = true }) { Text(stringResource(R.string.teams_add_member)) }
                    if (manage) TextButton(onClick = { renaming = true }) { Text(stringResource(R.string.teams_rename)) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (team.role != null) {
                        TextButton(onClick = {
                            confirming = Triple(resources.getString(R.string.teams_leave_title, team.name), resources.getString(R.string.teams_leave_text)) {
                                api?.leave(team.id)
                                onDismiss()
                            }
                        }) { Text(stringResource(R.string.teams_leave), color = MaterialTheme.colorScheme.error) }
                    }
                    if (team.role == AccountTeams.OWNER) {
                        TextButton(onClick = {
                            confirming = Triple(resources.getString(R.string.teams_delete_title, team.name), resources.getString(R.string.teams_delete_text)) {
                                api?.delete(team.id)
                                onDismiss()
                            }
                        }) { Text(stringResource(R.string.teams_delete), color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) } },
    )
    if (adding) {
        var email by remember { mutableStateOf("") }
        var role by remember { mutableStateOf(AccountTeams.MEMBER) }
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text(stringResource(R.string.teams_add_member)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        email, { email = it.trim() }, Modifier.fillMaxWidth(), singleLine = true,
                        label = { Text(stringResource(R.string.login_email)) }, placeholder = { Text(stringResource(R.string.teams_email_placeholder)) },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Email, autoCorrectEnabled = false,
                        ),
                    )
                    Picker(stringResource(R.string.teams_role), teamRoleText(role), null, roles.map { it to teamRoleText(it) }) { role = it }
                    Text(stringResource(R.string.teams_add_member_hint), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                TextButton(enabled = email.contains('@'), onClick = {
                    adding = false
                    act {
                        members = api?.add(team.id, email, role) ?: members
                        snackbar.showSnackbar(resources.getString(R.string.teams_added, email))
                    }
                }) { Text(stringResource(R.string.teams_add_member)) }
            },
            dismissButton = { TextButton(onClick = { adding = false }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
    if (renaming) {
        var name by remember { mutableStateOf(team.name) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text(stringResource(R.string.teams_rename)) },
            text = { OutlinedTextField(name, { name = it }, singleLine = true, label = { Text(stringResource(R.string.common_name)) }) },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    renaming = false
                    act { api?.rename(team.id, name) }
                }) { Text(stringResource(R.string.common_save)) }
            },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
    removing?.let { m ->
        ConfirmDialog(
            stringResource(R.string.teams_remove_title, m.name.ifBlank { m.email }), stringResource(R.string.teams_remove_text),
            stringResource(R.string.teams_remove), destructive = true, onDismiss = { removing = null },
        ) {
            act {
                api?.remove(team.id, m.userId)
                members = members?.filterNot { it.userId == m.userId }
            }
        }
    }
    confirming?.let { (title, text, action) ->
        ConfirmDialog(title, text, stringResource(R.string.common_continue), destructive = true, onDismiss = { confirming = null }) { act { action() } }
    }
}
