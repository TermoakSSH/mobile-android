package com.termoak.app.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Build
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.termoak.app.R
import com.termoak.app.TermoakApp
import com.termoak.app.asString
import com.termoak.app.files.DownloadTarget
import com.termoak.app.files.FileListing
import com.termoak.app.files.FileSort
import com.termoak.app.files.FilesSource
import com.termoak.app.files.FileSources
import com.termoak.app.files.FilesViewModel
import com.termoak.app.files.LocalFile
import com.termoak.app.files.RemotePaths
import com.termoak.app.files.Transfer
import com.termoak.app.files.UploadRequest
import com.termoak.app.files.item
import com.termoak.app.term.LocalTerminal
import com.termoak.app.term.ServerTerminal
import com.termoak.app.term.TermSession
import com.termoak.ffi.RemoteFile
import com.termoak.ffi.RemoteFileKind
import com.termoak.ffi.SshHost
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/** Opens the file browser for [source]. */
fun NavHostController.openFiles(source: FilesSource) {
    navigate(Routes.files(FileSources.put(source)))
}

/**
 * Files of a host from the Vault: SFTP from the phone, or through the server
 * for hosts in a Strict vault where you are Use only (like connecting).
 */
fun filesSourceOf(app: TermoakApp, host: SshHost): FilesSource =
    if (isStrictUseOnly(app, host)) FilesSource.Server(host.label, host.id, host.accountId)
    else FilesSource.Connect(host.label, host.id, host.accountId)

/**
 * Files of an open terminal: over its own SSH connection, or through the
 * server for your server sessions (`null`: none, e.g. a session shared with you).
 */
fun filesSourceOf(session: TermSession, title: String): FilesSource? = when (session) {
    is LocalTerminal -> session.connection()?.let { FilesSource.Terminal(title, it) }
    is ServerTerminal -> session.hostId?.takeIf { session.live.value.isOwner }?.let {
        FilesSource.Server(title, it, session.accountId)
    }
    else -> null
}

/**
 * Remote file browser (SFTP), like the iOS app's: browse, sort, show hidden
 * files, open or share a file with other apps, save it on the device,
 * upload from the device, create folders, rename, delete and change
 * permissions, with the transfers and their progress. On wide windows the
 * selected file's details go in a second pane.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesScreen(app: TermoakApp, nav: NavHostController, sourceId: String) {
    val vm: FilesViewModel = viewModel(key = sourceId) { FilesViewModel(app, sourceId) }
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val path by vm.path.collectAsState()
    val entries by vm.entries.collectAsState()
    val loading by vm.loading.collectAsState()
    val ready by vm.ready.collectAsState()
    val failure by vm.failure.collectAsState()
    val pending by vm.pending.collectAsState()
    val transfers by vm.transfers.collectAsState()
    val showHidden by vm.showHidden.collectAsState()
    val sort by vm.sort.collectAsState()
    val descending by vm.descending.collectAsState()
    val uploadAsk by vm.uploadAsk.collectAsState()

    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var actionsFor by remember { mutableStateOf<RemoteFile?>(null) }
    var selected by remember { mutableStateOf<RemoteFile?>(null) }
    var newFolder by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<RemoteFile?>(null) }
    var deleting by remember { mutableStateOf<RemoteFile?>(null) }
    var permissions by remember { mutableStateOf<RemoteFile?>(null) }
    var saving by remember { mutableStateOf<RemoteFile?>(null) }
    val searchFocus = remember { FocusRequester() }

    // The search belongs to the folder; the selection too.
    LaunchedEffect(path) {
        query = ""
        searching = false
        selected = null
    }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it.resolve(resources)) } }
    LaunchedEffect(Unit) {
        vm.opened.collect { f ->
            if (!handOver(context, f)) snackbar.showSnackbar(resources.getString(R.string.files_no_app))
        }
    }
    BackHandler(searching) { searching = false; query = "" }

    val pickUpload = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        vm.pickedForUpload(uris)
    }
    val saveAs = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val f = saving
        saving = null
        if (uri != null && f != null) vm.download(f, DownloadTarget.SaveAs(uri))
    }
    fun copyPath(p: String) {
        context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(p, p))
        scope.launch { snackbar.showSnackbar(resources.getString(R.string.files_path_copied)) }
    }

    val shown = remember(entries, sort, descending, showHidden, query) {
        FileListing.arrange(entries, { it.item() }, sort, descending, showHidden, query)
    }

    /** The actions on a file or folder (sheet on phones, details pane on wide windows). */
    val actions = FileActions(
        open = { f -> vm.download(f, DownloadTarget.Open) },
        share = { f -> vm.download(f, DownloadTarget.Share) },
        download = if (Build.VERSION.SDK_INT >= 29) ({ f -> vm.download(f, DownloadTarget.Downloads) }) else null,
        saveAs = { f -> saving = f; saveAs.launch(f.name) },
        rename = { f -> renaming = f },
        permissions = if (vm.canChmod) ({ f -> permissions = f }) else null,
        copyPath = { f -> copyPath(f.path) },
        delete = { f -> deleting = f },
    )

    ScreenScaffold(
        title = vm.title,
        subtitle = path.takeIf { it.isNotEmpty() },
        navigationIcon = {
            IconButton(onClick = { nav.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back))
            }
        },
        actions = {
            IconButton(onClick = { searching = !searching; if (!searching) query = "" }, enabled = ready) {
                Icon(Icons.Outlined.Search, stringResource(R.string.files_search))
            }
            IconButton(onClick = { pickUpload.launch(arrayOf("*/*")) }, enabled = ready) {
                Icon(Icons.Outlined.FileUpload, stringResource(R.string.files_upload))
            }
            Box {
                var menu by remember { mutableStateOf(false) }
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.common_more)) }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(
                        { Text(stringResource(R.string.files_new_folder)) }, { menu = false; newFolder = true },
                        leadingIcon = { Icon(Icons.Outlined.CreateNewFolder, null) }, enabled = ready,
                    )
                    DropdownMenuItem(
                        { Text(stringResource(R.string.files_refresh)) }, { menu = false; vm.reload() },
                        leadingIcon = { Icon(Icons.Outlined.Refresh, null) }, enabled = ready,
                    )
                    HorizontalDivider()
                    for ((by, label) in listOf(
                        FileSort.NAME to R.string.files_sort_name,
                        FileSort.SIZE to R.string.files_sort_size,
                        FileSort.DATE to R.string.files_sort_date,
                    )) {
                        DropdownMenuItem(
                            { Text(stringResource(label)) }, { vm.setSort(by) },
                            leadingIcon = { if (sort == by) Icon(Icons.AutoMirrored.Outlined.Sort, null) },
                            trailingIcon = {
                                if (sort == by) {
                                    Text(
                                        stringResource(if (descending) R.string.files_sort_descending else R.string.files_sort_ascending),
                                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        { Text(stringResource(R.string.files_show_hidden)) }, { vm.setShowHidden(!showHidden) },
                        leadingIcon = { Icon(if (showHidden) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff, null) },
                        trailingIcon = { if (showHidden) Icon(Icons.Outlined.Check, null) },
                    )
                    DropdownMenuItem(
                        { Text(stringResource(R.string.files_copy_path)) }, { menu = false; copyPath(path) },
                        leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) }, enabled = path.isNotEmpty(),
                    )
                }
            }
        },
        header = {
            Column {
                Breadcrumbs(path, loading, onUp = { vm.up() }, onGo = { vm.navigate(it) })
                if (searching) {
                    LaunchedEffect(Unit) { runCatching { searchFocus.requestFocus() } }
                    OutlinedTextField(
                        query, { query = it },
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).focusRequester(searchFocus),
                        placeholder = { Text(stringResource(R.string.files_search_hint)) },
                        leadingIcon = { Icon(Icons.Outlined.Search, null) },
                        trailingIcon = {
                            IconButton(onClick = { searching = false; query = "" }) { Icon(Icons.Outlined.Close, stringResource(R.string.common_close)) }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    )
                }
            }
        },
    ) { padding ->
        BoxWithConstraints(
            Modifier.fillMaxSize().padding(padding).imePadding()
                // Keyboard: Alt+↑ / Alt+← up a folder, F5 or Ctrl+R reloads.
                .onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when {
                        e.isAltPressed && (e.key == Key.DirectionUp || e.key == Key.DirectionLeft) -> { vm.up(); true }
                        e.key == Key.F5 || (e.isCtrlPressed && e.key == Key.R) -> { vm.reload(); true }
                        e.isCtrlPressed && e.key == Key.F -> { searching = true; true }
                        else -> false
                    }
                },
        ) {
            val wide = maxWidth >= 720.dp
            Row(Modifier.fillMaxSize()) {
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        val fail = failure
                        if (fail != null) {
                            FailurePanel(fail.asString()) { vm.open() }
                        } else {
                            FileList(
                                shown, entries.isEmpty(), loading, ready, query, selected?.path.takeIf { wide },
                                onRefresh = { vm.reload() },
                                onOpen = { f ->
                                    when (f.kind) {
                                        RemoteFileKind.DIR -> vm.navigate(f.path)
                                        // A link may point to a folder: try to enter it.
                                        RemoteFileKind.SYMLINK -> scope.launch {
                                            if (!vm.go(f.path, quiet = true)) { if (wide) selected = f else actionsFor = f }
                                        }
                                        else -> if (wide) selected = f else actionsFor = f
                                    }
                                },
                                onMore = { f -> if (wide) selected = f else actionsFor = f },
                            )
                        }
                    }
                    if (!wide && transfers.isNotEmpty()) {
                        HorizontalDivider()
                        TransfersList(transfers, vm, Modifier.heightIn(max = 220.dp).navigationBarsPadding())
                    }
                }
                if (wide) {
                    VerticalDivider()
                    Column(Modifier.width(360.dp).fillMaxHeight()) {
                        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                            val f = selected
                            if (f != null) {
                                FileDetails(f)
                                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                                ActionList(f, actions) { }
                            } else {
                                Text(
                                    stringResource(R.string.files_select_hint), Modifier.padding(24.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                        if (transfers.isNotEmpty()) {
                            HorizontalDivider()
                            Text(
                                stringResource(R.string.files_transfers), Modifier.padding(start = 16.dp, top = 10.dp),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            TransfersList(transfers, vm, Modifier.heightIn(max = 320.dp).navigationBarsPadding())
                        }
                    }
                }
            }
        }
    }

    // ----- Sheets and dialogs -----

    actionsFor?.let { f ->
        ModalBottomSheet(onDismissRequest = { actionsFor = null }) {
            FileDetails(f)
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            ActionList(f, actions) { actionsFor = null }
            Spacer(Modifier.navigationBarsPadding().padding(bottom = 16.dp))
        }
    }
    pending?.let { PendingDialog(it) }
    if (newFolder) {
        NameDialog(stringResource(R.string.files_new_folder), "", stringResource(R.string.files_create), onDismiss = { newFolder = false }) {
            newFolder = false
            vm.createFolder(it)
        }
    }
    renaming?.let { f ->
        NameDialog(stringResource(R.string.files_rename), f.name, stringResource(R.string.files_rename), onDismiss = { renaming = null }) {
            renaming = null
            if (it.trim() != f.name) vm.rename(f, it)
        }
    }
    deleting?.let { f ->
        ConfirmDialog(
            title = stringResource(R.string.common_delete_named, f.name),
            text = stringResource(if (f.kind == RemoteFileKind.DIR) R.string.files_delete_folder_text else R.string.files_delete_file_text),
            confirm = stringResource(R.string.common_delete), destructive = true, onDismiss = { deleting = null },
        ) {
            if (selected?.path == f.path) selected = null
            vm.delete(f)
        }
    }
    permissions?.let { f ->
        PermissionsDialog(f, onDismiss = { permissions = null }) { mode ->
            permissions = null
            vm.chmod(f, mode)
        }
    }
    uploadAsk?.let { UploadConflictDialog(it, vm) }
}

/** What can be done with a file or folder (`null`: not available here). */
private class FileActions(
    val open: (RemoteFile) -> Unit,
    val share: (RemoteFile) -> Unit,
    val download: ((RemoteFile) -> Unit)?,
    val saveAs: (RemoteFile) -> Unit,
    val rename: (RemoteFile) -> Unit,
    val permissions: ((RemoteFile) -> Unit)?,
    val copyPath: (RemoteFile) -> Unit,
    val delete: (RemoteFile) -> Unit,
)

@Composable
private fun ActionList(f: RemoteFile, a: FileActions, done: () -> Unit) {
    fun run(action: (RemoteFile) -> Unit): () -> Unit = { done(); action(f) }
    if (f.kind != RemoteFileKind.DIR) {
        SheetAction(Icons.AutoMirrored.Outlined.OpenInNew, stringResource(R.string.files_open_with), onClick = run(a.open))
        SheetAction(Icons.Outlined.Share, stringResource(R.string.files_share), onClick = run(a.share))
        a.download?.let { SheetAction(Icons.Outlined.FileDownload, stringResource(R.string.files_download), onClick = run(it)) }
        SheetAction(Icons.Outlined.SaveAlt, stringResource(R.string.files_save_as), onClick = run(a.saveAs))
    }
    SheetAction(Icons.Outlined.DriveFileRenameOutline, stringResource(R.string.files_rename), onClick = run(a.rename))
    a.permissions?.let { SheetAction(Icons.Outlined.Lock, stringResource(R.string.common_permissions), onClick = run(it)) }
    SheetAction(Icons.Outlined.ContentCopy, stringResource(R.string.files_copy_path), onClick = run(a.copyPath))
    SheetAction(Icons.Outlined.Delete, stringResource(R.string.common_delete), danger = true, onClick = run(a.delete))
}

/** Up button and one button per folder of the path. */
@Composable
private fun Breadcrumbs(path: String, loading: Boolean, onUp: () -> Unit, onGo: (String) -> Unit) {
    val crumbs = RemotePaths.crumbs(path)
    val scroll = rememberScrollState()
    LaunchedEffect(path) { scroll.animateScrollTo(scroll.maxValue) }
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onUp, enabled = path.isNotEmpty() && path != "/") {
            Icon(Icons.Outlined.ArrowUpward, stringResource(R.string.files_parent_folder))
        }
        Row(Modifier.weight(1f).horizontalScroll(scroll), verticalAlignment = Alignment.CenterVertically) {
            if (path.isNotEmpty()) {
                crumbs.forEachIndexed { i, (label, target) ->
                    if (i > 1) Icon(Icons.Outlined.ChevronRight, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    val last = i == crumbs.lastIndex
                    Text(
                        label,
                        Modifier.clip(RoundedCornerShape(6.dp)).clickable(enabled = !last) { onGo(target) }
                            .padding(horizontal = 6.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (last) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                    )
                }
            }
        }
        if (loading) CircularProgressIndicator(Modifier.padding(horizontal = 12.dp).size(18.dp), strokeWidth = 2.dp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FileList(
    shown: List<RemoteFile>,
    folderEmpty: Boolean,
    loading: Boolean,
    ready: Boolean,
    query: String,
    selectedPath: String?,
    onRefresh: () -> Unit,
    onOpen: (RemoteFile) -> Unit,
    onMore: (RemoteFile) -> Unit,
) {
    // The pull indicator only for a pull (opening a folder shows the small one in the path bar).
    var pulled by remember { mutableStateOf(false) }
    LaunchedEffect(loading) { if (!loading) pulled = false }
    PullToRefreshBox(isRefreshing = pulled, onRefresh = { pulled = true; onRefresh() }, modifier = Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(shown, key = { it.path }) { f ->
                FileRow(f, f.path == selectedPath, onClick = { onOpen(f) }, onMore = { onMore(f) })
            }
        }
        when {
            !ready && loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            ready && !loading && shown.isEmpty() -> Text(
                if (query.isNotBlank() && !folderEmpty) stringResource(R.string.files_no_match, query.trim())
                else stringResource(R.string.files_empty_folder),
                Modifier.align(Alignment.Center).padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileRow(f: RemoteFile, selected: Boolean, onClick: () -> Unit, onMore: () -> Unit) {
    val dir = f.kind == RemoteFileKind.DIR
    Row(
        Modifier.fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onMore)
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            fileIcon(f), null, Modifier.size(26.dp),
            tint = if (dir) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(Modifier.weight(1f).padding(start = 16.dp)) {
            Text(f.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                fileDetail(f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onMore) {
            Icon(Icons.Outlined.MoreVert, stringResource(R.string.common_options), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Name, type and everything known about a file (top of its sheet, or the details pane). */
@Composable
private fun FileDetails(f: RemoteFile) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(fileIcon(f), null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
            Text(
                f.name, Modifier.padding(start = 14.dp), style = MaterialTheme.typography.titleMedium,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.padding(top = 8.dp))
        val context = LocalContext.current
        val kind = stringResource(
            when (f.kind) {
                RemoteFileKind.DIR -> R.string.files_kind_folder
                RemoteFileKind.FILE -> R.string.files_kind_file
                RemoteFileKind.SYMLINK -> R.string.files_kind_link
                RemoteFileKind.OTHER -> R.string.files_kind_other
            },
        )
        InfoLine(stringResource(R.string.files_info_kind), kind)
        if (f.kind != RemoteFileKind.DIR) {
            InfoLine(stringResource(R.string.files_info_size), "${Formatter.formatFileSize(context, f.size.toLong())} (${f.size})")
        }
        f.modified?.let { InfoLine(stringResource(R.string.files_info_modified), formatDate(it)) }
        val mode = f.mode?.toInt()
        if (f.modeString.isNotEmpty() || mode != null) {
            InfoLine(
                stringResource(R.string.common_permissions),
                listOfNotNull(f.modeString.takeIf { it.isNotEmpty() }, mode?.let { RemotePaths.octal(it) }).joinToString("  "),
                mono = true,
            )
        }
        if (f.owner != null || f.group != null) {
            InfoLine(stringResource(R.string.files_info_owner), listOfNotNull(f.owner, f.group).joinToString(":"))
        }
        InfoLine(stringResource(R.string.files_info_path), f.path, mono = true)
    }
}

@Composable
private fun InfoLine(label: String, value: String, mono: Boolean = false) {
    Row(Modifier.padding(vertical = 3.dp)) {
        Text(
            label, Modifier.width(110.dp), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
            fontFamily = if (mono) FontFamily.Monospace else null,
        )
    }
}

@Composable
private fun TransfersList(transfers: List<Transfer>, vm: FilesViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 4.dp)) {
        transfers.forEach { t ->
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (t.upload) Icons.Outlined.FileUpload else Icons.Outlined.FileDownload, null, Modifier.size(20.dp),
                    tint = when (t.status) {
                        Transfer.Status.FAILED -> MaterialTheme.colorScheme.error
                        Transfer.Status.DONE -> Brand.Green
                        else -> MaterialTheme.colorScheme.primary
                    },
                )
                Column(Modifier.weight(1f).padding(start = 12.dp, top = 6.dp, bottom = 6.dp)) {
                    Text(t.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val size = { n: Long -> Formatter.formatShortFileSize(context, n) }
                    Text(
                        when (t.status) {
                            Transfer.Status.WAITING -> stringResource(R.string.files_transfer_waiting)
                            Transfer.Status.PREPARING -> stringResource(R.string.files_transfer_preparing)
                            Transfer.Status.RUNNING -> t.total?.let { stringResource(R.string.files_transfer_progress, size(t.done), size(it)) }
                                ?: size(t.done)
                            Transfer.Status.DONE -> stringResource(if (t.upload) R.string.files_transfer_uploaded else R.string.files_transfer_downloaded)
                            Transfer.Status.FAILED -> t.error?.asString() ?: stringResource(R.string.files_transfer_failed)
                            Transfer.Status.CANCELLED -> stringResource(R.string.files_transfer_cancelled)
                        },
                        style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        color = if (t.status == Transfer.Status.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (t.active) {
                        val fr = t.fraction
                        if (fr != null && t.status == Transfer.Status.RUNNING) {
                            LinearProgressIndicator({ fr }, Modifier.fillMaxWidth().padding(top = 4.dp))
                        } else {
                            LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 4.dp))
                        }
                    }
                }
                if (t.active) {
                    IconButton(onClick = { vm.cancel(t.id) }) { Icon(Icons.Outlined.Close, stringResource(R.string.common_cancel)) }
                } else if (t.status != Transfer.Status.DONE) {
                    IconButton(onClick = { vm.retry(t.id) }) { Icon(Icons.Outlined.Replay, stringResource(R.string.files_retry)) }
                    IconButton(onClick = { vm.dismiss(t.id) }) { Icon(Icons.Outlined.Close, stringResource(R.string.files_dismiss)) }
                }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.FailurePanel(message: String, onRetry: () -> Unit) {
    Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Outlined.ErrorOutline, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.error)
        Text(message, Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodyLarge)
        Button(onClick = onRetry, Modifier.padding(top = 16.dp)) { Text(stringResource(R.string.files_retry)) }
    }
}

/** A name for a new folder or a rename (no `/`, not empty, not `.` or `..`). */
@Composable
private fun NameDialog(title: String, initial: String, confirm: String, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val valid = !RemotePaths.invalidName(name)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                name, { name = it }, Modifier.focusRequester(focus), label = { Text(stringResource(R.string.common_name)) },
                singleLine = true, isError = name.isNotEmpty() && !valid,
                supportingText = if (name.isNotEmpty() && !valid) ({ Text(stringResource(R.string.files_invalid_name)) }) else null,
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (valid) onDone(name) }),
            )
        },
        confirmButton = { TextButton(onClick = { onDone(name) }, enabled = valid) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

/** Unix permissions: read, write and execute for owner, group and others, and in octal. */
@Composable
private fun PermissionsDialog(f: RemoteFile, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var mode by remember { mutableIntStateOf((f.mode?.toInt() ?: 0b110_100_100) and 0xFFF) }
    var octal by remember { mutableStateOf(RemotePaths.octal(mode)) }
    val classes = listOf(R.string.files_perm_owner to 6, R.string.files_perm_group to 3, R.string.files_perm_others to 0)
    val rights = listOf(R.string.files_perm_read to 4, R.string.files_perm_write to 2, R.string.files_perm_execute to 1)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.common_permissions)) },
        text = {
            Column {
                Text(f.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.padding(top = 8.dp)) {
                    Spacer(Modifier.width(72.dp))
                    rights.forEach { (label, _) ->
                        Text(stringResource(label), Modifier.width(64.dp), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                }
                classes.forEach { (label, shift) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(label), Modifier.width(72.dp), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                        rights.forEach { (_, bit) ->
                            val mask = bit shl shift
                            Box(Modifier.width(64.dp)) {
                                Checkbox(mode and mask != 0, { on ->
                                    mode = if (on) mode or mask else mode and mask.inv()
                                    octal = RemotePaths.octal(mode)
                                })
                            }
                        }
                    }
                }
                OutlinedTextField(
                    octal, { t -> octal = t.filter { it.isDigit() }.take(4); RemotePaths.parseOctal(octal)?.let { mode = it } },
                    Modifier.padding(top = 8.dp).width(140.dp), label = { Text(stringResource(R.string.files_perm_octal)) },
                    singleLine = true, isError = RemotePaths.parseOctal(octal) == null,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Text(
                    RemotePaths.symbolic(mode), Modifier.padding(top = 4.dp), fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(mode) }, enabled = RemotePaths.parseOctal(octal) != null) { Text(stringResource(R.string.common_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

/** Some picked files already exist in the folder: replace them, keep both (renamed) or cancel. */
@Composable
private fun UploadConflictDialog(request: UploadRequest, vm: FilesViewModel) {
    val n = request.conflicts.size
    AlertDialog(
        onDismissRequest = { vm.cancelUpload() },
        title = { Text(pluralStringResource(R.plurals.files_replace_title, n, n)) },
        text = {
            Column {
                Text(stringResource(R.string.files_replace_text))
                Surface(
                    Modifier.padding(top = 10.dp).fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        request.conflicts.take(6).joinToString("\n") + if (n > 6) "\n…" else "",
                        Modifier.padding(10.dp), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = { vm.upload(request, replace = false) }) { Text(stringResource(R.string.files_keep_both)) }
                TextButton(onClick = { vm.upload(request, replace = true) }) {
                    Text(stringResource(R.string.files_replace), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        dismissButton = { TextButton(onClick = { vm.cancelUpload() }) { Text(stringResource(R.string.common_cancel)) } },
    )
}

/** Opens or shares a downloaded file with another app; `false` if no app can. */
private fun handOver(context: android.content.Context, f: LocalFile): Boolean {
    val uri = runCatching { FileProvider.getUriForFile(context, "${context.packageName}.files", f.file) }.getOrNull() ?: return false
    val intent = if (f.share) {
        Intent(Intent.ACTION_SEND).setType(f.mime).putExtra(Intent.EXTRA_STREAM, uri)
            .apply { clipData = ClipData.newRawUri(f.file.name, uri) }
    } else {
        Intent(Intent.ACTION_VIEW).setDataAndType(uri, f.mime)
    }.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    return try {
        context.startActivity(Intent.createChooser(intent, f.file.name))
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}

private fun fileIcon(f: RemoteFile): ImageVector = when (f.kind) {
    RemoteFileKind.DIR -> Icons.Outlined.Folder
    RemoteFileKind.SYMLINK -> Icons.Outlined.Link
    RemoteFileKind.OTHER -> Icons.AutoMirrored.Outlined.InsertDriveFile
    RemoteFileKind.FILE -> when (f.name.substringAfterLast('.', "").lowercase()) {
        "png", "jpg", "jpeg", "gif", "heic", "webp", "svg", "bmp" -> Icons.Outlined.Image
        "pdf" -> Icons.Outlined.PictureAsPdf
        "zip", "gz", "tgz", "xz", "bz2", "tar", "7z", "zst", "rar" -> Icons.Outlined.FolderZip
        "sh", "py", "rb", "js", "ts", "go", "rs", "c", "h", "cpp", "swift", "kt", "java", "php" -> Icons.Outlined.Code
        "log", "txt", "md", "conf", "cfg", "ini", "yml", "yaml", "json", "toml", "xml", "env" -> Icons.AutoMirrored.Outlined.Article
        else -> Icons.AutoMirrored.Outlined.InsertDriveFile
    }
}

/** Size (files), date and permissions, in one line. */
@Composable
private fun fileDetail(f: RemoteFile): String {
    val context = LocalContext.current
    val parts = mutableListOf<String>()
    if (f.kind == RemoteFileKind.FILE) parts += Formatter.formatShortFileSize(context, f.size.toLong())
    f.modified?.let { parts += formatDate(it) }
    if (f.modeString.isNotEmpty()) parts += f.modeString
    return parts.joinToString(" · ")
}

@Composable
private fun formatDate(seconds: Long): String {
    val locale = LocalConfiguration.current.locales[0]
    return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale).format(Date(seconds * 1000))
}
