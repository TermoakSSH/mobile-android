package com.termoak.app.ui

import android.content.ClipboardManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.termoak.app.R
import com.termoak.app.TermoakApp
import com.termoak.app.data.InviteLinkRef
import com.termoak.app.data.JoinLinkRef
import com.termoak.app.term.LinkJoin
import com.termoak.ffi.LinkInvite
import com.termoak.ffi.SessionAccess
import com.termoak.ffi.TermoakException
import com.termoak.ffi.linkInviteInfo
import com.termoak.ffi.AccountStatus
import com.termoak.app.data.AccountView

/**
 * Joining a shared session with an invitation link: what it is (who shares
 * it, the permission, whether the owner lets people in), and the name to
 * show when not signed in to that server.
 */
@Composable
fun JoinScreen(app: TermoakApp, nav: NavHostController, server: String, token: String) {
    val accountList by app.accounts.list.collectAsState()
    // A signed-in account on the link's server joins with its name (the current one first).
    val match = accountList.filter { it.status == AccountStatus.ACTIVE && JoinLinkRef.sameServer(it.serverUrl, server) }
        .sortedByDescending { it.isCurrent }.firstOrNull()
    val user = match?.email
    var info by remember { mutableStateOf<LinkInvite?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf(app.prefs.guestName.orEmpty()) }
    // Signed in to that server, but joining as a guest anyway (as on iOS).
    var asGuest by remember { mutableStateOf(false) }
    val asAccount = match != null && !asGuest
    val fallback = stringResource(R.string.join_invalid)
    val untitled = stringResource(R.string.common_session)

    LaunchedEffect(server, token) {
        try {
            info = linkInviteInfo(server, token)
        } catch (e: TermoakException) {
            error = e.message?.takeIf { it.isNotBlank() } ?: fallback
        }
    }

    fun join() {
        val i = info ?: return
        val guestName = name.trim().take(40).ifEmpty { null }
        if (!asAccount) app.prefs.guestName = guestName
        // Joining with an account goes through the current one: make it current.
        if (match != null && !asGuest && !match.isCurrent) app.accounts.setView(AccountView.One(match.id))
        app.sessions.joinLink(
            LinkJoin(server, token, asAccount, if (asAccount) null else guestName),
            i.title.ifBlank { untitled },
            i.sessionId.ifBlank { null },
        )
        nav.navigate(Routes.TERMINAL) {
            popUpTo(Routes.JOIN) { inclusive = true }
            launchSingleTop = true
        }
    }

    ScreenScaffold(
        title = stringResource(R.string.join_title),
        navigationIcon = {
            IconButton(onClick = { nav.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back))
            }
        },
    ) { padding ->
        val i = info
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                error != null -> EmptyState(
                    Icons.Outlined.LinkOff, stringResource(R.string.join_unavailable), error.orEmpty(),
                    action = stringResource(R.string.common_close), onAction = { nav.popBackStack() },
                )
                i == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
                    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Avatar(i.owner, i.owner, 64.dp)
                        Text(
                            i.title.ifBlank { stringResource(R.string.common_session) }, Modifier.padding(top = 16.dp),
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Text(
                            stringResource(R.string.join_shared_by, i.owner.ifBlank { "?" }),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    CardBox {
                        Column {
                            InfoRow(
                                if (i.access == SessionAccess.CONTROL) Icons.Outlined.Keyboard else Icons.Outlined.Visibility,
                                stringResource(if (i.access == SessionAccess.CONTROL) R.string.share_perm_control else R.string.share_perm_view),
                                stringResource(if (i.access == SessionAccess.CONTROL) R.string.join_control_hint else R.string.join_view_hint),
                            )
                            if (i.requireApproval) {
                                InfoRow(Icons.Outlined.HourglassTop, stringResource(R.string.join_approval), stringResource(R.string.join_approval_hint))
                            }
                            if (i.participants > 0u) {
                                InfoRow(Icons.Outlined.Group, pluralStringResource(R.plurals.share_people_inside, i.participants.toInt(), i.participants.toInt()), null)
                            }
                            i.expiresAt?.let { at ->
                                val ms = if (at < 10_000_000_000L) at * 1000 else at
                                val text = java.text.DateFormat.getDateTimeInstance(
                                    java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT, LocalConfiguration.current.locales[0],
                                ).format(java.util.Date(ms))
                                InfoRow(Icons.Outlined.Schedule, stringResource(R.string.share_expires_at, text), null)
                            }
                        }
                    }
                    Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                        if (asAccount) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Person, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(stringResource(R.string.join_as_account, user ?: ""), Modifier.padding(start = 12.dp))
                            }
                            TextButton(onClick = { asGuest = true }) { Text(stringResource(R.string.join_as_guest_instead)) }
                        } else {
                            OutlinedTextField(
                                name, { name = it.take(40) }, Modifier.fillMaxWidth(),
                                label = { Text(stringResource(R.string.join_your_name)) },
                                supportingText = { Text(stringResource(R.string.join_name_hint)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                                keyboardActions = KeyboardActions(onGo = { join() }),
                            )
                        }
                        Button(onClick = { join() }, Modifier.fillMaxWidth().padding(top = 16.dp)) {
                            Text(stringResource(if (i.requireApproval) R.string.join_ask_to_join else R.string.join_join))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(icon: ImageVector, title: String, text: String?) {
    ListItem(
        leadingContent = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) },
        headlineContent = { Text(title) },
        supportingContent = text?.let { { Text(it) } },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

/**
 * Asks for an invitation link (pasted, or already on the clipboard) and opens
 * it; an invitation to create an account opens the sign-up form instead.
 */
@Composable
fun JoinLinkDialog(onDismiss: () -> Unit, onJoin: (JoinLinkRef) -> Unit) {
    val app = LocalContext.current.applicationContext as com.termoak.app.TermoakApp
    val context = LocalContext.current
    var text by remember {
        val clip = context.getSystemService(ClipboardManager::class.java)?.primaryClip
            ?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
        mutableStateOf(clip?.takeIf { JoinLinkRef.parse(it) != null || InviteLinkRef.parse(it) != null }.orEmpty())
    }
    var invalid by remember { mutableStateOf(false) }
    fun go() {
        val link = JoinLinkRef.parse(text)
        val invite = if (link == null) InviteLinkRef.parse(text) else null
        when {
            link != null -> onJoin(link)
            invite != null -> {
                onDismiss()
                app.pendingInvite.value = invite
            }
            else -> invalid = true
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.join_with_link)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.join_with_link_text))
                OutlinedTextField(
                    text, { text = it; invalid = false }, Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.join_link)) },
                    isError = invalid,
                    supportingText = if (invalid) ({ Text(stringResource(R.string.join_invalid_link)) }) else null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { go() }),
                )
            }
        },
        confirmButton = { TextButton(onClick = { go() }, enabled = text.isNotBlank()) { Text(stringResource(R.string.join_continue)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}
