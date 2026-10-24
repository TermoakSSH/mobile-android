package com.termoak.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.VideoLibrary
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.termoak.app.EngineObjects
import com.termoak.app.R
import com.termoak.app.TermoakApp
import com.termoak.app.data.AccessTexts
import com.termoak.app.data.Asciicast
import com.termoak.app.data.shownEmail
import com.termoak.app.userMessage
import com.termoak.ffi.ScreenSnapshot
import com.termoak.ffi.TermoakException
import com.termoak.ffi.VaultRecording
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * A vault's recorded sessions (vault Admins, teams security): server
 * sessions and the terminals the apps recorded and uploaded. Each one can
 * be downloaded (`.cast`, audited by the server) to share or save, or
 * played here.
 */
@Composable
fun VaultRecordingsScreen(app: TermoakApp, nav: NavHostController, accountId: String, vaultId: String) {
    val res = LocalResources.current
    val context = LocalContext.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val handle = remember(accountId) { app.accounts.handle(accountId) }
    var list by remember { mutableStateOf<List<VaultRecording>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf<String?>(null) }
    var playing by remember { mutableStateOf<Pair<String, Asciicast>?>(null) }
    LaunchedEffect(vaultId) {
        try {
            list = handle?.vaultRecordings(vaultId)?.sortedByDescending { it.createdAt }
        } catch (e: TermoakException) {
            error = e.userMessage(res, R.string.recordings_load_failed)
        }
    }
    /** Downloads one to the shared cache, then [use]s the file. */
    fun download(r: VaultRecording, use: suspend (File) -> Unit) {
        val h = handle ?: return
        scope.launch {
            working = r.sessionId
            try {
                val file = File(Recordings.folder(context), Recordings.fileName(r.title, r.sessionId))
                h.downloadVaultRecording(vaultId, r.sessionId, file.path, null, null)
                use(file)
            } catch (e: TermoakException) {
                snackbar.showSnackbar(e.userMessage(res, R.string.recordings_download_failed))
            } finally {
                working = null
            }
        }
    }
    ScreenScaffold(
        title = stringResource(R.string.vaults_tab_recordings),
        subtitle = AccessUi.vaultOf(app, accountId, vaultId)?.let { vaultName(it) },
        navigationIcon = {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back)) }
        },
    ) { padding ->
        val l = list
        Column(Modifier.fillMaxSize().padding(padding)) {
            FormHint(stringResource(R.string.recordings_hint))
            when {
                error != null -> FormNote(error ?: "")
                l == null -> CircularProgressIndicator(Modifier.padding(20.dp).size(24.dp), strokeWidth = 2.dp)
                l.isEmpty() -> EmptyState(Icons.Outlined.VideoLibrary, stringResource(R.string.recordings_empty), stringResource(R.string.recordings_hint))
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(l, key = { it.sessionId }) { r ->
                        RecordingRow(r, busy = working == r.sessionId,
                            onPlay = {
                                download(r) { f ->
                                    val cast = withContext(Dispatchers.IO) { Asciicast.parse(f.readText()) }
                                    if (cast == null) snackbar.showSnackbar(res.getString(R.string.recordings_error_format)) else playing = r.title to cast
                                }
                            },
                            onShare = { download(r) { f -> Recordings.share(context, f) } },
                        )
                    }
                }
            }
        }
    }
    playing?.let { (title, cast) -> RecordingPlayerDialog(title, cast) { playing = null } }
}

@Composable
fun RecordingRow(r: VaultRecording, busy: Boolean, onPlay: () -> Unit, onShare: () -> Unit) {
    ListItem(
        headlineContent = { Text(r.title.ifBlank { r.sessionId.take(8) }) },
        supportingContent = {
            Text(
                listOfNotNull(
                    stringResource(R.string.recordings_by, r.ownerEmail?.let(::shownEmail) ?: r.ownerId.take(8), AccessTexts.dateTime(r.createdAt)),
                    stringResource(R.string.recordings_from_device).takeIf { r.kind == "device" },
                ).joinToString(" · "),
            )
        },
        trailingContent = {
            if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else Row {
                IconButton(onClick = onPlay) { Icon(Icons.Outlined.PlayArrow, stringResource(R.string.recordings_play)) }
                IconButton(onClick = onShare) { Icon(Icons.Outlined.Share, stringResource(R.string.recordings_share)) }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

/**
 * Plays a recording into a terminal emulator (the engine's), honouring its
 * size changes: play/pause, from the start, 1–8×; long pauses are cut.
 */
@Composable
fun RecordingPlayerDialog(title: String, cast: Asciicast, onDismiss: () -> Unit) {
    val screen = remember { EngineObjects.newScreen().apply { resize(cast.width.toUInt(), cast.height.toUInt()) } }
    var snapshot by remember { mutableStateOf<ScreenSnapshot?>(null) }
    var index by remember { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf(true) }
    var speed by remember { mutableDoubleStateOf(1.0) }
    var restart by remember { mutableIntStateOf(0) }
    LaunchedEffect(restart) {
        if (restart > 0) {
            screen.reset()
            screen.resize(cast.width.toUInt(), cast.height.toUInt())
            index = 0
            playing = true
        }
        snapshot = runCatching { screen.snapshot() }.getOrNull()
    }
    LaunchedEffect(playing, restart, speed) {
        while (playing && index < cast.events.size) {
            val ev = cast.events[index]
            val prev = if (index == 0) 0.0 else cast.events[index - 1].time
            delay(Asciicast.waitMs(prev, ev.time, speed))
            when (ev) {
                is Asciicast.Event.Output -> runCatching { screen.feed(ev.data.toByteArray()) }
                is Asciicast.Event.Resize -> runCatching { screen.resize(ev.cols.toUInt(), ev.rows.toUInt()) }
            }
            index++
            snapshot = runCatching { screen.snapshot() }.getOrNull()
        }
        if (index >= cast.events.size) playing = false
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.recordings_player_title, title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val snap = snapshot
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 420.dp).background(Color(0xFF000000L or (snap?.background?.toLong() ?: 0x101418L)), RoundedCornerShape(8.dp))
                        .horizontalScroll(rememberScrollState()).padding(8.dp),
                ) {
                    snap?.let { s -> Text(screenText(s), fontFamily = FontFamily.Monospace, fontSize = 9.sp, lineHeight = 11.sp, softWrap = false) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = { playing = !playing }) {
                        Icon(if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, stringResource(if (playing) R.string.recordings_pause else R.string.recordings_play))
                    }
                    IconButton(onClick = { restart++ }) { Icon(Icons.Outlined.Replay, stringResource(R.string.recordings_restart)) }
                    Asciicast.SPEEDS.forEach { s ->
                        TextButton(onClick = { speed = s }) {
                            Text("${s.toInt()}×", fontWeight = if (s == speed) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
                Text(
                    "%d:%02d / %d:%02d".format(
                        (cast.events.getOrNull(index - 1)?.time ?: 0.0).toInt() / 60, (cast.events.getOrNull(index - 1)?.time ?: 0.0).toInt() % 60,
                        cast.duration.toInt() / 60, cast.duration.toInt() % 60,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) } },
    )
}

/** The screen's rows with their colours, bold and so on. */
fun screenText(s: ScreenSnapshot): AnnotatedString = buildAnnotatedString {
    s.lines.forEachIndexed { i, line ->
        var col = 0
        line.runs.forEach { run ->
            val c = run.col.toInt()
            if (c > col) append(" ".repeat(c - col))
            withStyle(
                SpanStyle(
                    color = Color(0xFF000000L or run.fg.toLong()),
                    background = run.bg?.let { Color(0xFF000000L or it.toLong()) } ?: Color.Unspecified,
                    fontWeight = if (run.bold) FontWeight.Bold else null,
                ),
            ) { append(run.text) }
            col = c + run.cells.toInt()
        }
        if (i < s.lines.lastIndex) append('\n')
    }
}
