package com.termoak.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.termoak.app.R
import com.termoak.app.userMessage
import com.termoak.ffi.AccountHandle
import com.termoak.ffi.AuditEvent
import com.termoak.ffi.TermoakException
import com.termoak.ffi.VaultMember
import kotlinx.coroutines.launch

/** Texts of a vault's audit (JVM tests). */
internal object VaultAudit {
    /** Entries per page. */
    const val PAGE = 50u

    /** The text of an action of the log (`null`: unknown, the action itself is shown). */
    fun actionText(action: String): Int? = when (action.replace('-', '_')) {
        "secret.reveal" -> R.string.vault_audit_secret_reveal
        "secret.use" -> R.string.vault_audit_secret_use
        "session.open" -> R.string.vault_audit_session_open
        "vault.create" -> R.string.vault_audit_vault_create
        "vault.delete" -> R.string.vault_audit_vault_delete
        "vault.member_add" -> R.string.vault_audit_member_add
        "vault.member_removed" -> R.string.vault_audit_member_removed
        "vault.role_changed" -> R.string.vault_audit_role_changed
        "vault.transfer" -> R.string.vault_audit_transfer
        "vault.update" -> R.string.vault_audit_update
        else -> null
    }

    /** Who did it: a member's email or name for its user id (`user:<id>` too), or the actor itself. */
    fun who(actor: String, members: List<VaultMember>): String {
        val id = actor.removePrefix("user:")
        val m = members.firstOrNull { it.userId == id || it.id == id } ?: return actor
        return m.email ?: m.name.ifEmpty { actor }
    }
}

/**
 * What happened in a vault (the server's audit log, for its managers), as
 * on iOS: what, who and when, newest first, 50 at a time.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VaultActivitySheet(handle: AccountHandle, vaultId: String, vaultName: String, members: List<VaultMember>, onDismiss: () -> Unit) {
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    var events by remember { mutableStateOf<List<AuditEvent>>(emptyList()) }
    var more by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    suspend fun load(next: Boolean) {
        loading = true
        try {
            val list = handle.vaultAudit(vaultId, VaultAudit.PAGE, if (next) events.lastOrNull()?.id else null)
            events = if (next) events + list else list
            more = list.size.toUInt() == VaultAudit.PAGE
            error = null
        } catch (e: TermoakException) {
            error = e.userMessage(resources, R.string.vault_activity_failed)
        } finally {
            loading = false
        }
    }
    LaunchedEffect(vaultId) { load(next = false) }
    val locale = LocalConfiguration.current.locales[0]
    val format = remember(locale) { java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT, locale) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Text(stringResource(R.string.vault_activity), style = MaterialTheme.typography.titleLarge)
            Text(
                vaultName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            LazyColumn(Modifier.heightIn(max = 520.dp).padding(top = 12.dp)) {
                error?.let { item { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) } }
                if (error == null && events.isEmpty() && !loading) {
                    item { Muted(stringResource(R.string.vault_activity_empty)) }
                }
                items(events, key = { it.id }) { e ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text(VaultAudit.actionText(e.action)?.let { stringResource(it) } ?: e.action, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "${VaultAudit.who(e.actor, members)} · ${format.format(java.util.Date(e.createdAt))}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        when {
                            loading -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            more -> TextButton(onClick = { scope.launch { load(next = true) } }) { Text(stringResource(R.string.vault_activity_more)) }
                            error != null -> TextButton(onClick = { scope.launch { load(next = false) } }) { Text(stringResource(R.string.activity_retry)) }
                        }
                    }
                }
            }
        }
    }
}
