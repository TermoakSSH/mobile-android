package com.termoak.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.termoak.app.R
import com.termoak.app.TermoakApp
import com.termoak.ffi.AccountInfo
import com.termoak.ffi.AccountStatus
import kotlinx.coroutines.launch
import org.json.JSONArray

/** A team you belong to on a server, with your role there (`null`: a server admin who isn't a member). */
private data class TeamInfo(val id: String, val accountId: String, val name: String, val role: String?, val members: Int)

/** A member of a team. */
private data class TeamPerson(val email: String, val name: String, val role: String)

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

private suspend fun loadMembers(app: TermoakApp, team: TeamInfo): List<TeamPerson>? = runCatching {
    val arr = JSONArray(app.accounts.handle(team.accountId)?.apiGet("/api/v1/teams/${team.id}/members") ?: return null)
    (0 until arr.length()).map { i ->
        val m = arr.getJSONObject(i)
        TeamPerson(m.optString("email"), m.optString("name"), m.optString("role"))
    }
}.getOrNull()

@Composable
private fun teamRoleText(role: String?): String = when (role) {
    "owner" -> stringResource(R.string.teams_role_owner)
    "admin" -> stringResource(R.string.teams_role_admin)
    "member" -> stringResource(R.string.teams_role_member)
    else -> stringResource(R.string.teams_role_none)
}

/**
 * Teams (the sidebar of the desktop layout): the ones you belong to on each
 * signed-in server, with your role and their members. Sessions and vaults
 * are shared with them from their own screens; teams are created and
 * managed in the desktop app.
 */
@Composable
fun TeamsScreen(app: TermoakApp, nav: NavHostController) {
    val accounts by app.accounts.list.collectAsState()
    val active = accounts.filter { it.status == AccountStatus.ACTIVE }
    val scope = rememberCoroutineScope()
    var teams by remember { mutableStateOf<Map<String, List<TeamInfo>?>>(emptyMap()) }
    var loading by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf<TeamInfo?>(null) }
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
    open?.let { t -> TeamMembersDialog(app, t) { open = null } }
}

@Composable
private fun Note(text: String) {
    Text(
        text, Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** The members of a team and their roles. */
@Composable
private fun TeamMembersDialog(app: TermoakApp, team: TeamInfo, onDismiss: () -> Unit) {
    var members by remember { mutableStateOf<List<TeamPerson>?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(team) {
        val list = loadMembers(app, team)
        failed = list == null
        members = list.orEmpty()
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
                        items(list) { m ->
                            ListItem(
                                headlineContent = { Text(m.name.ifBlank { m.email }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                supportingContent = if (m.name.isNotBlank()) ({ Text(m.email, maxLines = 1, overflow = TextOverflow.Ellipsis) }) else null,
                                trailingContent = { Pill(teamRoleText(m.role), MaterialTheme.colorScheme.onSurfaceVariant) },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) } },
    )
}
