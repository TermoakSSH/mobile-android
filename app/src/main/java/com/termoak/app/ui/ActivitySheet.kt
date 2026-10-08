package com.termoak.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.termoak.app.R
import com.termoak.app.TermoakApp
import com.termoak.ffi.AuthorPeriod
import com.termoak.ffi.SessionActivity
import com.termoak.ffi.TermoakException

/** What the activity sheet shows. */
private sealed interface ActivityLoad {
    data object Loading : ActivityLoad
    /** The session was not recorded. */
    data object NotRecorded : ActivityLoad
    data class Failed(val message: String?) : ActivityLoad
    data class Done(val activity: SessionActivity) : ActivityLoad
}

/**
 * Activity of one of your server sessions: who had the keyboard and when,
 * from the author marks of its recording. Only who typed, not what.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivitySheet(app: TermoakApp, sessionId: String, title: String, onDismiss: () -> Unit, accountId: String? = null) {
    var load by remember(sessionId) { mutableStateOf<ActivityLoad>(ActivityLoad.Loading) }
    var attempt by remember(sessionId) { mutableIntStateOf(0) }
    LaunchedEffect(sessionId, attempt) {
        load = ActivityLoad.Loading
        load = try {
            (accountId?.let { app.core.account(it).sessionActivity(sessionId) } ?: app.core.sessionActivity(sessionId))?.let { ActivityLoad.Done(it) } ?: ActivityLoad.NotRecorded
        } catch (e: TermoakException) {
            ActivityLoad.Failed(e.message)
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Text(stringResource(R.string.activity_title), style = MaterialTheme.typography.titleLarge)
            Text(
                title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Box(Modifier.padding(top = 16.dp)) {
                when (val l = load) {
                    ActivityLoad.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Muted(stringResource(R.string.activity_loading), Modifier.padding(start = 12.dp))
                    }
                    ActivityLoad.NotRecorded -> Muted(stringResource(R.string.activity_not_recorded))
                    is ActivityLoad.Failed -> Column {
                        Text(
                            stringResource(R.string.activity_failed, l.message ?: ""),
                            color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium,
                        )
                        TextButton(onClick = { attempt++ }) { Text(stringResource(R.string.activity_retry)) }
                    }
                    is ActivityLoad.Done -> ActivityList(l.activity)
                }
            }
        }
    }
}

@Composable
private fun ActivityList(activity: SessionActivity) {
    val locale = LocalConfiguration.current.locales[0]
    Column {
        Muted(
            activity.startedAt?.let {
                val date = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT, locale)
                    .format(java.util.Date(it))
                stringResource(R.string.activity_started, date)
            } ?: stringResource(R.string.activity_hint),
        )
        if (activity.periods.isEmpty()) {
            Muted(stringResource(R.string.activity_none), Modifier.padding(top = 12.dp))
            return@Column
        }
        LazyColumn(Modifier.padding(top = 8.dp).heightIn(max = 420.dp)) {
            items(activity.periods) { p ->
                PeriodRow(p)
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun PeriodRow(p: AuthorPeriod) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            p.toSecs?.let { "${clock(p.fromSecs)} – ${clock(it)}" } ?: stringResource(R.string.activity_from, clock(p.fromSecs)),
            Modifier.width(112.dp), fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(Modifier.weight(1f)) {
            Text(
                p.name.ifBlank { stringResource(R.string.activity_unknown) },
                style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                stringResource(
                    when (p.kind) {
                        "owner" -> R.string.share_role_owner
                        "guest" -> R.string.share_kind_guest
                        "ai" -> R.string.activity_kind_ai
                        else -> R.string.activity_kind_user
                    },
                ),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun Muted(text: String, modifier: Modifier = Modifier) =
    Text(text, modifier, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

/** `mm:ss`, or `h:mm:ss` from an hour on. */
private fun clock(secs: Double): String {
    val s = secs.coerceAtLeast(0.0).toLong()
    val (h, m, sec) = Triple(s / 3600, s / 60 % 60, s % 60)
    return if (h > 0) String.format(java.util.Locale.ROOT, "%d:%02d:%02d", h, m, sec)
    else String.format(java.util.Locale.ROOT, "%02d:%02d", m, sec)
}
