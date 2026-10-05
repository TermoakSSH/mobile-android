package com.termoak.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.termoak.app.R
import com.termoak.app.term.LiveShare
import com.termoak.app.term.ServerTerminal
import com.termoak.app.term.ShareEnd
import com.termoak.app.term.ShareWaiting
import com.termoak.app.term.TermSession
import com.termoak.ffi.ParticipantKind
import com.termoak.ffi.SessionAccess
import com.termoak.ffi.SessionParticipant
import kotlinx.coroutines.delay
import java.util.Locale

private val AvatarPalette = listOf(
    Color(0xFF4F7CFF), Color(0xFF3FB27F), Color(0xFFE8A33D), Color(0xFF9B6BFF),
    Color(0xFFE5534B), Color(0xFF2BA6B5), Color(0xFFD9640F), Color(0xFFC2185B),
)

/** Round avatar with the person's initial, in a color that stays the same for them. */
@Composable
fun Avatar(name: String, key: String, size: Dp = 36.dp, driver: Boolean = false) {
    val bg = AvatarPalette[Math.floorMod(key.hashCode(), AvatarPalette.size)]
    Box(
        Modifier.size(size).then(if (driver) Modifier.border(2.dp, Brand.Green, CircleShape) else Modifier)
            .padding(if (driver) 2.dp else 0.dp).clip(CircleShape).background(bg),
        contentAlignment = Alignment.Center,
    ) {
        val initial = name.trim().firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?"
        Text(initial, color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.42f).sp)
    }
}

/** The people in a shared terminal, at a glance (top bar): avatars, count and a dot for pending requests. */
@Composable
fun ParticipantsChip(live: LiveShare, onClick: () -> Unit) {
    val inside = live.inside
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(TermKeyBg).clickable(onClick = onClick)
            .padding(start = 4.dp, end = 10.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            inside.take(3).forEachIndexed { i, p ->
                Box(Modifier.offset(x = (i * 14).dp)) { Avatar(p.name, p.id, 24.dp, driver = p.isDriver && !p.you) }
            }
        }
        Text(
            "${inside.size}", Modifier.padding(start = (6 + 14 * (inside.size.coerceAtMost(3) - 1)).dp),
            color = TermKeyFg, style = MaterialTheme.typography.labelLarge,
        )
        if (live.pendingRequests > 0) {
            Box(Modifier.padding(start = 6.dp).size(8.dp).clip(CircleShape).background(Brand.Amber))
        }
    }
}

@Composable
private fun accessLabel(p: SessionParticipant): String = when {
    p.kind == ParticipantKind.OWNER || p.access == SessionAccess.OWNER -> stringResource(R.string.share_role_owner)
    p.access == SessionAccess.CONTROL -> stringResource(R.string.share_perm_control)
    else -> stringResource(R.string.share_perm_view)
}

/** How long "Give control" hands the keyboard over, in minutes (`null`: until you take it back). */
private val ControlDurations = listOf<UInt?>(null, 5u, 15u, 30u, 60u)

/** Milliseconds left until [until] (ms since the epoch), ticking every second; `null` without a limit. */
@Composable
fun rememberTimeLeft(until: Long?): Long? {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(until) {
        while (until != null) {
            now = System.currentTimeMillis()
            if (now >= until) break
            delay(1_000 - (until - now) % 1_000 + 1)
        }
    }
    return until?.let { (it - now).coerceAtLeast(0) }
}

/** "4:59", "1:02:03". */
fun formatTimeLeft(ms: Long): String {
    val total = (ms + 999) / 1_000
    val h = total / 3_600
    val m = total % 3_600 / 60
    val sec = total % 60
    return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, sec) else String.format(Locale.ROOT, "%d:%02d", m, sec)
}

/** "4:59 left" for a timed grant of the keyboard; `null` without a limit. */
@Composable
fun timeLeftText(until: Long?): String? = rememberTimeLeft(until)?.let { stringResource(R.string.share_time_left, formatTimeLeft(it)) }

/** [text] with the time left of the timed grant after it, if any. */
@Composable
private fun withTimeLeft(text: String, until: Long?): String = listOfNotNull(text, timeLeftText(until)).joinToString(" · ")

@Composable
private fun durationLabel(minutes: UInt?): String =
    if (minutes == null) stringResource(R.string.share_control_until_taken) else stringResource(R.string.share_control_minutes, minutes.toInt())

/** The choices of "Give control" (how long), as menu items under a small heading. */
@Composable
private fun ControlDurationItems(onPick: (UInt?) -> Unit) {
    Text(
        stringResource(R.string.share_control_how_long), Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    ControlDurations.forEach { m -> DropdownMenuItem({ Text(durationLabel(m)) }, { onPick(m) }) }
}

/** "Give control" button that asks for how long first. */
@Composable
private fun GiveControlButton(onPick: (UInt?) -> Unit) {
    Box {
        var open by remember { mutableStateOf(false) }
        Button(onClick = { open = true }) { Text(stringResource(R.string.share_give_control)) }
        DropdownMenu(open, { open = false }) { ControlDurationItems { open = false; onPick(it) } }
    }
}

/**
 * Who is in the terminal: name, avatar, who drives, who asked for the
 * keyboard. The owner can let people in, hand over or take back the
 * keyboard and send someone away; guests ask for (or give back) the keyboard.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParticipantsSheet(session: TermSession, live: LiveShare, onShare: (() -> Unit)?, onDismiss: () -> Unit) {
    var kicking by remember { mutableStateOf<Pair<SessionParticipant, Boolean>?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.share_participants), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            if (onShare != null) {
                TextButton(onClick = onShare) {
                    Icon(Icons.Outlined.Share, null, Modifier.size(18.dp))
                    Text(stringResource(R.string.share_invite_more), Modifier.padding(start = 6.dp))
                }
            }
        }
        // Your own keyboard.
        if (live.isOwner && live.guestDriving) {
            OwnerDriverRow(live.driverLabel().orEmpty(), live.driverUntil) { session.takeControl() }
        } else if (!live.isOwner && session is ServerTerminal) {
            GuestControlRow(session, live)
        }
        LazyColumn(Modifier.padding(bottom = 24.dp)) {
            val list = live.participants.sortedWith(
                compareByDescending<SessionParticipant> { it.waiting }
                    .thenByDescending { it.kind == ParticipantKind.OWNER }
                    .thenBy { it.since },
            )
            if (list.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.share_nobody_yet), Modifier.padding(24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(list, key = { it.id }) { p -> ParticipantRow(session, live, p) { block -> kicking = p to block } }
        }
    }
    kicking?.let { (p, block) ->
        ConfirmDialog(
            title = stringResource(if (block) R.string.share_kick_block_title else R.string.share_kick_title, p.name),
            text = stringResource(if (block) R.string.share_kick_block_text else R.string.share_kick_text),
            confirm = stringResource(if (block) R.string.share_kick_block else R.string.share_kick),
            destructive = true,
            onDismiss = { kicking = null },
        ) { session.kick(p.id, block) }
    }
}

@Composable
private fun OwnerDriverRow(name: String, until: Long?, onTake: () -> Unit) {
    Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Keyboard, null)
            Text(withTimeLeft(stringResource(R.string.share_driver_is, name), until), Modifier.weight(1f).padding(horizontal = 12.dp))
            Button(onClick = onTake) { Text(stringResource(R.string.share_take_back)) }
        }
    }
}

@Composable
private fun GuestControlRow(session: ServerTerminal, live: LiveShare) {
    if (live.waiting != null || live.ended != null) return
    Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (live.canWrite) Icons.Outlined.Keyboard else Icons.Outlined.Visibility, null)
            Text(
                when {
                    live.canWrite -> withTimeLeft(stringResource(R.string.share_you_have_keyboard), live.driverUntil)
                    live.controlRequested -> stringResource(R.string.share_waiting_for_keyboard)
                    live.access == SessionAccess.CONTROL -> stringResource(R.string.share_view_only_can_ask)
                    else -> stringResource(R.string.share_view_only)
                },
                Modifier.weight(1f).padding(horizontal = 12.dp),
            )
            when {
                live.canWrite -> OutlinedButton(onClick = { session.releaseControl() }) { Text(stringResource(R.string.share_give_back)) }
                live.controlRequested -> TextButton(onClick = { session.releaseControl() }) { Text(stringResource(R.string.common_cancel)) }
                live.access == SessionAccess.CONTROL ->
                    Button(onClick = { session.requestControl() }) { Text(stringResource(R.string.share_request_control)) }
            }
        }
    }
}

@Composable
private fun ParticipantRow(session: TermSession, live: LiveShare, p: SessionParticipant, onKick: (Boolean) -> Unit) {
    val owner = live.isOwner && !p.you && p.kind != ParticipantKind.OWNER
    ListItem(
        leadingContent = { Avatar(p.name, p.id, 40.dp, driver = p.isDriver) },
        headlineContent = {
            Text(
                if (p.you) stringResource(R.string.share_you, p.name) else p.name,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Column {
                Text(
                    listOfNotNull(
                        accessLabel(p),
                        if (p.kind == ParticipantKind.GUEST) stringResource(R.string.share_kind_guest) else null,
                        p.devices.toInt().takeIf { it > 1 }?.let { pluralStringResource(R.plurals.share_devices, it, it) },
                        if (p.devices == 0u && !p.waiting) stringResource(R.string.share_reconnecting) else null,
                        if (p.since > 0) relativeTime(p.since) else null,
                    ).joinToString(" · "),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                when {
                    p.waiting -> Pill(stringResource(R.string.share_waiting_to_join), Brand.Amber, Modifier.padding(top = 4.dp))
                    p.isDriver -> Pill(
                        withTimeLeft(stringResource(R.string.share_typing), live.driverUntil.takeIf { live.driver == p.id }),
                        Brand.Green, Modifier.padding(top = 4.dp),
                    )
                    p.requestedControl -> Pill(stringResource(R.string.share_asked_keyboard), Brand.Amber, Modifier.padding(top = 4.dp))
                }
            }
        },
        trailingContent = if (!owner) null else {
            {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    when {
                        p.waiting -> {
                            TextButton(onClick = { session.denyJoin(p.id) }) { Text(stringResource(R.string.share_deny)) }
                            Button(onClick = { session.allowJoin(p.id) }) { Text(stringResource(R.string.share_let_in)) }
                        }
                        p.requestedControl -> {
                            TextButton(onClick = { session.denyControl(p.id) }) { Text(stringResource(R.string.share_deny)) }
                            GiveControlButton { session.grantControl(p.id, it) }
                        }
                    }
                    if (!p.waiting) ParticipantMenu(session, p, onKick)
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
private fun ParticipantMenu(session: TermSession, p: SessionParticipant, onKick: (Boolean) -> Unit) {
    Box {
        var open by remember { mutableStateOf(false) }
        // "Give control" turns the menu into the list of durations.
        var durations by remember { mutableStateOf(false) }
        IconButton(onClick = { durations = false; open = true }) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.common_more)) }
        DropdownMenu(open, { open = false }) {
            if (durations) {
                ControlDurationItems { open = false; session.grantControl(p.id, it) }
            } else {
                if (p.isDriver) {
                    DropdownMenuItem({ Text(stringResource(R.string.share_take_back)) }, { open = false; session.takeControl() },
                        leadingIcon = { Icon(Icons.Outlined.Keyboard, null) })
                    // Granting it again changes the time.
                    DropdownMenuItem({ Text(stringResource(R.string.share_change_time)) }, { durations = true },
                        leadingIcon = { Icon(Icons.Outlined.Timer, null) })
                } else if (p.access == SessionAccess.CONTROL) {
                    DropdownMenuItem({ Text(stringResource(R.string.share_give_control)) }, { durations = true },
                        leadingIcon = { Icon(Icons.Outlined.Keyboard, null) })
                }
                DropdownMenuItem({ Text(stringResource(R.string.share_kick)) }, { open = false; onKick(false) },
                    leadingIcon = { Icon(Icons.Outlined.PersonRemove, null) })
                DropdownMenuItem(
                    { Text(stringResource(R.string.share_kick_block), color = MaterialTheme.colorScheme.error) },
                    { open = false; onKick(true) },
                    leadingIcon = { Icon(Icons.Outlined.Block, null, tint = MaterialTheme.colorScheme.error) },
                )
            }
        }
    }
}

/** Owner: people waiting to get in or asking for the keyboard, over the terminal. */
@Composable
fun RequestBanners(session: TermSession, live: LiveShare, modifier: Modifier = Modifier, onMore: () -> Unit) {
    if (!live.isOwner || live.pendingRequests == 0) return
    val shown = (live.joinRequests.map { it to true } + live.controlRequests.map { it to false }).take(2)
    Column(modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        shown.forEach { (p, join) ->
            Surface(color = TermBarBg, shape = RoundedCornerShape(12.dp), shadowElevation = 4.dp) {
                Row(Modifier.padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(p.name, p.id, 28.dp)
                    Text(
                        stringResource(if (join) R.string.share_wants_to_join else R.string.share_wants_keyboard, p.name),
                        Modifier.weight(1f).padding(horizontal = 10.dp), color = TermKeyFg,
                        style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    TextButton(onClick = { if (join) session.denyJoin(p.id) else session.denyControl(p.id) }) {
                        Text(stringResource(R.string.share_deny), color = TermKeyFg.copy(alpha = 0.8f))
                    }
                    if (join) {
                        Button(onClick = { session.allowJoin(p.id) }) { Text(stringResource(R.string.share_let_in)) }
                    } else {
                        GiveControlButton { session.grantControl(p.id, it) }
                    }
                }
            }
        }
        val more = live.pendingRequests - shown.size
        if (more > 0) {
            TextButton(onClick = onMore) { Text(pluralStringResource(R.plurals.share_more_requests, more, more), color = TermKeyFg) }
        }
    }
}

/**
 * Bottom strip of a shared terminal: guests see that they are only watching
 * (and can ask for the keyboard) or that they have it (and can give it
 * back); the owner sees who has the keyboard and can take it back.
 */
@Composable
fun KeyboardStrip(session: TermSession, live: LiveShare) {
    if (live.waiting != null || live.ended != null) return
    val text: String
    var action: Pair<String, () -> Unit>? = null
    var icon = Icons.Outlined.Visibility
    val guest = session as? ServerTerminal
    when {
        live.isOwner -> {
            if (!live.guestDriving) return
            icon = Icons.Outlined.Keyboard
            text = withTimeLeft(stringResource(R.string.share_driver_is, live.driverLabel().orEmpty()), live.driverUntil)
            action = stringResource(R.string.share_take_back) to { session.takeControl() }
        }
        guest == null -> return
        live.canWrite -> {
            icon = Icons.Outlined.Keyboard
            text = withTimeLeft(stringResource(R.string.share_you_have_keyboard), live.driverUntil)
            action = stringResource(R.string.share_give_back) to { guest.releaseControl() }
        }
        else -> {
            val who = live.driverLabel()
            text = listOfNotNull(
                stringResource(R.string.share_view_only),
                who?.let { stringResource(R.string.share_is_typing, it) },
                if (who != null) timeLeftText(live.driverUntil) else null,
            ).joinToString(" · ")
            action = when {
                live.controlRequested -> stringResource(R.string.share_cancel_request) to { guest.releaseControl() }
                live.access == SessionAccess.CONTROL -> stringResource(R.string.share_request_control) to { guest.requestControl() }
                else -> null
            }
        }
    }
    Row(
        Modifier.fillMaxWidth().background(TermBarBg).height(44.dp).padding(start = 14.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(18.dp), tint = if (live.canWrite || live.isOwner) Brand.Green else TermKeyFg.copy(alpha = 0.7f))
        Text(
            if (live.controlRequested && !live.canWrite) stringResource(R.string.share_waiting_for_keyboard) else text,
            Modifier.weight(1f).padding(horizontal = 10.dp), color = TermKeyFg,
            style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        action?.let { (label, run) -> TextButton(onClick = run) { Text(label) } }
    }
}

/** Waiting room: until the owner lets you in. Guests can change the name the owner sees. */
@Composable
fun BoxScope.WaitingRoom(session: ServerTerminal, waiting: ShareWaiting, guestName: String?, onRename: (String) -> Unit, onLeave: () -> Unit) {
    var renaming by remember { mutableStateOf(false) }
    Column(
        Modifier.align(Alignment.Center).fillMaxWidth().padding(24.dp)
            .background(TermBarBg, RoundedCornerShape(20.dp)).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Outlined.HourglassTop, null, Modifier.size(48.dp), tint = Brand.Amber)
        Text(
            stringResource(R.string.share_waiting_title), Modifier.padding(top = 16.dp), color = TermKeyFg,
            style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center,
        )
        Text(
            stringResource(R.string.share_waiting_text, waiting.owner.ifBlank { "?" }, waiting.title.ifBlank { session.label }),
            Modifier.padding(top = 8.dp), color = TermKeyFg.copy(alpha = 0.75f),
            style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
        )
        if (!guestName.isNullOrBlank()) {
            Text(
                stringResource(R.string.share_joining_as, guestName), Modifier.padding(top = 12.dp),
                color = TermKeyFg.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall,
            )
        }
        androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 20.dp).clip(RoundedCornerShape(50)))
        Row(Modifier.padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (guestName != null) OutlinedButton(onClick = { renaming = true }) { Text(stringResource(R.string.share_change_name)) }
            TextButton(onClick = onLeave) { Text(stringResource(R.string.share_leave)) }
        }
    }
    if (renaming) {
        var name by remember { mutableStateOf(guestName.orEmpty()) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text(stringResource(R.string.share_change_name)) },
            text = {
                OutlinedTextField(name, { name = it.take(40) }, label = { Text(stringResource(R.string.join_your_name)) }, singleLine = true)
            },
            confirmButton = {
                TextButton(onClick = { onRename(name.trim()); renaming = false }, enabled = name.isNotBlank()) {
                    Text(stringResource(R.string.common_save))
                }
            },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
}

/** End screen: the server sent you away (revoked, kicked, expired, session ended, not let in). */
@Composable
fun BoxScope.EndPanel(end: ShareEnd, onClose: () -> Unit) {
    val (icon, title, text) = when (end.code) {
        "revoked" -> Triple(Icons.Outlined.LinkOff, R.string.share_end_revoked, R.string.share_end_revoked_text)
        "kicked" -> Triple(Icons.Outlined.PersonRemove, R.string.share_end_kicked, R.string.share_end_kicked_text)
        "expired" -> Triple(Icons.Outlined.HourglassTop, R.string.share_end_expired, R.string.share_end_expired_text)
        "session_ended" -> Triple(Icons.Outlined.Group, R.string.share_end_session_ended, R.string.share_end_session_ended_text)
        "join_denied" -> Triple(Icons.Outlined.Block, R.string.share_end_join_denied, R.string.share_end_join_denied_text)
        else -> Triple(Icons.Outlined.Block, R.string.share_end_forbidden, R.string.share_end_forbidden_text)
    }
    Column(
        Modifier.align(Alignment.Center).fillMaxWidth().padding(24.dp)
            .background(TermBarBg, RoundedCornerShape(20.dp)).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, Modifier.size(48.dp), tint = Brand.Red)
        Text(stringResource(title), Modifier.padding(top = 16.dp), color = TermKeyFg, style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center)
        Text(stringResource(text), Modifier.padding(top = 8.dp), color = TermKeyFg.copy(alpha = 0.75f),
            style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        Button(onClick = onClose, Modifier.padding(top = 20.dp)) { Text(stringResource(R.string.share_close_tab)) }
    }
}
