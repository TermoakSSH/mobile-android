package com.termoak.app.files

import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.termoak.app.R
import com.termoak.app.TermoakApp
import com.termoak.app.UiText
import com.termoak.app.toUiText
import com.termoak.app.uiText
import com.termoak.app.term.Pending
import com.termoak.app.term.askChangedKey
import com.termoak.ffi.AuthHandler
import com.termoak.ffi.AuthPromptKind
import com.termoak.ffi.AuthRequest
import com.termoak.ffi.HostKeyChange
import com.termoak.ffi.HostKeyChangeHandler
import com.termoak.ffi.RemoteFile
import com.termoak.ffi.RemoteFileKind
import com.termoak.ffi.TermoakException
import com.termoak.ffi.TransferHandle
import com.termoak.ffi.TransferListener
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** An upload or a download, with its progress. */
data class Transfer(
    val id: Long,
    val name: String,
    val upload: Boolean,
    val done: Long = 0,
    val total: Long? = null,
    val status: Status = Status.WAITING,
    val error: UiText? = null,
) {
    enum class Status { WAITING, PREPARING, RUNNING, DONE, FAILED, CANCELLED }

    val fraction: Float? get() = total?.takeIf { it > 0 }?.let { (done.toFloat() / it).coerceIn(0f, 1f) }
    val active: Boolean get() = status == Status.WAITING || status == Status.PREPARING || status == Status.RUNNING
}

/** What a download is for. */
sealed class DownloadTarget {
    /** Open it with another app. */
    data object Open : DownloadTarget()
    /** Share it (send to another app). */
    data object Share : DownloadTarget()
    /** The Downloads folder (Android 10+). */
    data object Downloads : DownloadTarget()
    /** A file picked with "Save to…" (system file picker). */
    data class SaveAs(val uri: Uri) : DownloadTarget()
    /** The in-app preview (text, image, PDF). */
    data class Preview(val kind: PreviewKind) : DownloadTarget()
}

/** A remote file downloaded for the in-app preview. */
data class FilePreviewState(val file: RemoteFile, val local: File, val kind: PreviewKind)

/** A file on the device to hand to another app (open or share). */
data class LocalFile(val file: File, val mime: String, val share: Boolean)

/** Files on the device to share together (several selected files; folders as .zip). */
data class LocalFiles(val files: List<LocalFile>)

/** Files picked to upload, with their names; [conflicts]: names that already exist in the folder. */
data class UploadRequest(val files: List<Pair<Uri, String>>, val folder: String, val conflicts: List<String>)

/**
 * State of the remote file browser (SFTP): the folder on screen, its
 * operations and the transfers. It lives as long as the Files screen (its
 * back stack entry): leaving it cancels the transfers and closes the
 * connection if the browser opened it.
 */
class FilesViewModel(private val app: TermoakApp, private val sourceId: String) : ViewModel(), AuthHandler, HostKeyChangeHandler {
    private val source = FileSources.get(sourceId)
    val title: String = source?.title.orEmpty()
    private var fs: RemoteFs? = null

    private val _path = MutableStateFlow("")
    val path: StateFlow<String> = _path
    private val _entries = MutableStateFlow<List<RemoteFile>>(emptyList())
    val entries: StateFlow<List<RemoteFile>> = _entries
    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading
    private val _ready = MutableStateFlow(false)
    /** Connected and the first folder listed: the actions can be used. */
    val ready: StateFlow<Boolean> = _ready
    private val _failure = MutableStateFlow<UiText?>(null)
    /** Couldn't connect or list the first folder (shown instead of the list, with Retry). */
    val failure: StateFlow<UiText?> = _failure
    private val _pending = MutableStateFlow<Pending?>(null)
    /** Host key or password to ask while connecting from the phone. */
    val pending: StateFlow<Pending?> = _pending
    private val _transfers = MutableStateFlow<List<Transfer>>(emptyList())
    val transfers: StateFlow<List<Transfer>> = _transfers
    private val _messages = MutableSharedFlow<UiText>(extraBufferCapacity = 8)
    /** Snackbars (errors and "Saved to Downloads"). */
    val messages: SharedFlow<UiText> = _messages
    private val _opened = MutableSharedFlow<LocalFile>(extraBufferCapacity = 4)
    /** Downloaded to open or share: the screen starts the other app. */
    val opened: SharedFlow<LocalFile> = _opened
    private val _sharedMany = MutableSharedFlow<LocalFiles>(extraBufferCapacity = 2)
    /** Several files downloaded to share together. */
    val sharedMany: SharedFlow<LocalFiles> = _sharedMany
    private val _preview = MutableStateFlow<FilePreviewState?>(null)
    /** The file shown in the preview (downloaded), or `null`. */
    val preview: StateFlow<FilePreviewState?> = _preview
    private val _uploadAsk = MutableStateFlow<UploadRequest?>(null)
    /** Picked files whose names already exist in the folder: replace them or keep both? */
    val uploadAsk: StateFlow<UploadRequest?> = _uploadAsk

    /** Files shared into the app from another one, waiting for "Upload here" (in the folder chosen). */
    private val _incoming = MutableStateFlow<List<Uri>>(emptyList())
    val incoming: StateFlow<List<Uri>> = _incoming

    fun setIncoming(uris: List<Uri>) {
        _incoming.value = uris
    }

    /** "Upload here": the shared files go to the folder on screen. */
    fun uploadIncoming() {
        val uris = _incoming.value
        _incoming.value = emptyList()
        pickedForUpload(uris)
    }

    private val _showHidden = MutableStateFlow(app.prefs.filesShowHidden)
    val showHidden: StateFlow<Boolean> = _showHidden
    private val _sort = MutableStateFlow(app.prefs.filesSort)
    val sort: StateFlow<FileSort> = _sort
    private val _descending = MutableStateFlow(app.prefs.filesSortDescending)
    val descending: StateFlow<Boolean> = _descending

    private val context get() = app.applicationContext
    /** Downloads to open or share (pruned when a browser opens). */
    private val openDir = File(app.cacheDir, "sftp")
    /** Copies of the files picked to upload, until they are uploaded. */
    private val stagingDir = File(app.cacheDir, "sftp-upload")

    private val ids = AtomicLong()
    private val jobs = mutableMapOf<Long, Job>()
    /** The engine's handle of each running transfer: Cancel stops it there at once. */
    private val handles = mutableMapOf<Long, TransferHandle>()
    private val retries = mutableMapOf<Long, () -> Unit>()
    private val staged = mutableMapOf<Long, File>()
    /** At most two transfers at once; the rest wait their turn. */
    private val slots = Semaphore(2)

    init {
        viewModelScope.launch(Dispatchers.IO) { prune() }
        open()
    }

    // ----- Connection and folders -----

    /** Connects (if needed) and lists the home folder. */
    fun open() {
        val src = source
        if (src == null) {
            _loading.value = false
            _failure.value = uiText(R.string.files_closed)
            return
        }
        _failure.value = null
        _loading.value = true
        viewModelScope.launch {
            try {
                val f = fs ?: when (src) {
                    is FilesSource.Terminal -> SshFs(src.session, own = false)
                    is FilesSource.Server -> ServerFs(app.core, src.hostId, src.accountId)
                    is FilesSource.Connect -> SshFs(
                        app.core.connect(src.hostId, this@FilesViewModel, src.accountId, keyChanged = this@FilesViewModel), own = true,
                    )
                }.also { fs = it }
                val start = _path.value.ifEmpty { f.home() }
                _entries.value = f.list(start)
                _path.value = start
                _ready.value = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _failure.value = e.toUiText(R.string.files_connect_failed)
            } finally {
                _loading.value = false
            }
        }
    }

    /** Lists [newPath]; [quiet]: no message if it can't (a link that isn't a folder). Returns whether it could. */
    suspend fun go(newPath: String, quiet: Boolean = false): Boolean {
        val f = fs ?: return false
        _loading.value = true
        return try {
            _entries.value = f.list(newPath)
            _path.value = newPath
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!quiet) _messages.tryEmit(e.toUiText(R.string.files_list_failed))
            false
        } finally {
            _loading.value = false
        }
    }

    fun navigate(newPath: String) {
        viewModelScope.launch { go(newPath) }
    }

    fun reload() = navigate(_path.value)

    /** "Go to folder…": an absolute path, or `~` / `~/…` from the home folder. */
    fun goTo(text: String) {
        val t = text.trim()
        viewModelScope.launch {
            val p = if (t == "~" || t.startsWith("~/")) {
                val home = runCatching { fs?.home() }.getOrNull() ?: return@launch
                RemotePaths.child(home, t.removePrefix("~").trimStart('/')).trimEnd('/').ifEmpty { "/" }
            } else t
            go(p)
        }
    }

    fun up() {
        val p = _path.value
        if (p.isNotEmpty() && p != "/") navigate(RemotePaths.parent(p))
    }

    fun setShowHidden(on: Boolean) {
        _showHidden.value = on
        app.prefs.filesShowHidden = on
    }

    /** Sorts by [by]; the same one again turns the order around. */
    fun setSort(by: FileSort) {
        if (_sort.value == by) {
            _descending.value = !_descending.value
        } else {
            _sort.value = by
            // Size and date: the biggest and newest first.
            _descending.value = by != FileSort.NAME
        }
        app.prefs.filesSort = _sort.value
        app.prefs.filesSortDescending = _descending.value
    }

    // ----- Operations -----

    private fun perform(@androidx.annotation.StringRes failed: Int, action: suspend (RemoteFs) -> Unit) {
        val f = fs ?: return
        viewModelScope.launch {
            try {
                action(f)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.tryEmit(e.toUiText(failed))
            }
            go(_path.value, quiet = true)
        }
    }

    fun createFolder(name: String) = perform(R.string.files_new_folder_failed) {
        it.mkdir(RemotePaths.child(_path.value, name.trim()))
    }

    /** A new, empty file in the folder on screen (not over an existing one). */
    fun createFile(name: String) = perform(R.string.files_new_file_failed) { f ->
        val n = name.trim()
        if (_entries.value.any { it.name == n }) error("exists")
        f.write(RemotePaths.child(_path.value, n), ByteArray(0))
    }

    /** The details of [file] as they are now (Info; a link: its target's), or `null` if it can't be read. */
    suspend fun stat(file: RemoteFile): RemoteFile? {
        val f = fs ?: return null
        return try {
            f.stat(file.path)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    /** Closes the preview (its download goes away). */
    fun closePreview() {
        val p = _preview.value ?: return
        _preview.value = null
        viewModelScope.launch(Dispatchers.IO) { p.local.parentFile?.deleteRecursively() }
    }

    /** Saves the edited text of the previewed file back to the server (over it). Whether it went well. */
    suspend fun saveText(text: String): Boolean {
        val p = _preview.value ?: return false
        val f = fs ?: return false
        return try {
            val bytes = text.toByteArray(Charsets.UTF_8)
            withContext(Dispatchers.IO) { p.local.writeBytes(bytes) }
            f.write(p.file.path, bytes)
            _messages.tryEmit(uiText(R.string.files_saved, p.file.name))
            go(_path.value, quiet = true)
            true
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            _messages.tryEmit(e.toUiText(R.string.files_upload_failed))
            false
        }
    }

    fun rename(file: RemoteFile, name: String) = perform(R.string.files_rename_failed) {
        it.rename(file.path, RemotePaths.child(RemotePaths.parent(file.path), name.trim()))
    }

    fun delete(file: RemoteFile) = perform(R.string.files_delete_failed) {
        it.delete(file.path, recursive = file.item().dir)
    }

    fun chmod(file: RemoteFile, mode: Int) = perform(R.string.files_permissions_failed) { it.chmod(file.path, mode) }

    // ----- Several at once (Select) -----

    /** Deletes [files] (folders with what they have inside), one question for all. */
    fun deleteAll(files: List<RemoteFile>) = perform(R.string.files_delete_failed) { f ->
        for (file in files) f.delete(file.path, recursive = file.item().dir)
    }

    /** Moves [files] to [folder] (an absolute path, or `~/…`), keeping their names. */
    fun moveAll(files: List<RemoteFile>, folder: String) = perform(R.string.files_move_failed) { f ->
        val t = folder.trim()
        val to = if (t == "~" || t.startsWith("~/")) RemotePaths.child(f.home(), t.removePrefix("~").trimStart('/')).trimEnd('/').ifEmpty { "/" } else t
        for (file in files) {
            val dest = RemotePaths.child(to, file.name)
            if (dest != file.path) f.rename(file.path, dest)
        }
    }

    /**
     * Shares [files] together (one transfer with the progress of all; folders
     * walked and zipped), or saves each in Downloads.
     */
    fun downloadAll(files: List<RemoteFile>, target: DownloadTarget) {
        if (files.isEmpty()) return
        if (target != DownloadTarget.Share) {
            files.forEach { if (it.kind == RemoteFileKind.DIR) downloadFolder(it, target) else download(it, target) }
            return
        }
        if (files.size == 1) {
            val one = files[0]
            if (one.kind == RemoteFileKind.DIR) downloadFolder(one, target) else download(one, target)
            return
        }
        val f = fs ?: return
        val id = ids.incrementAndGet()
        val dir = File(openDir, "$id-${System.currentTimeMillis()}")
        val name = context.resources.getQuantityString(R.plurals.files_selected, files.size, files.size)
        val start = {
            transfer(id) { listener, handle ->
                val plan = files.map { it to if (it.kind == RemoteFileKind.DIR) walk(f, it.path) else listOf(it) }
                val total = plan.sumOf { (_, list) -> list.sumOf { it.size.toLong() } }
                val progress = Combined(listener, total)
                val out = plan.map { (item, list) ->
                    if (item.kind == RemoteFileKind.DIR) {
                        val tree = File(File(dir, "tree"), item.name)
                        fetchTree(f, item.path, list, tree, progress, handle)
                        val zip = File(dir, "${item.name}.zip")
                        withContext(Dispatchers.IO) { zipFolder(tree, zip) }
                        LocalFile(zip, "application/zip", share = true)
                    } else {
                        val local = File(dir, item.name)
                        progress.file(item.size.toLong()) { l -> f.download(item.path, local, l, handle) }
                        LocalFile(local, mimeOf(item.name), share = true)
                    }
                }
                File(dir, "tree").deleteRecursively()
                _sharedMany.tryEmit(LocalFiles(out))
            }
        }
        add(Transfer(id, name, upload = false), start)
    }

    /**
     * A folder as a .zip: walked and downloaded whole (one transfer with the
     * progress of all its files), zipped here, then shared or saved like a file.
     */
    fun downloadFolder(folder: RemoteFile, target: DownloadTarget) {
        val f = fs ?: return
        val id = ids.incrementAndGet()
        val dir = File(openDir, "$id-${System.currentTimeMillis()}")
        val zipName = "${folder.name}.zip"
        val start = {
            transfer(id) { listener, handle ->
                val list = walk(f, folder.path)
                val tree = File(File(dir, "tree"), folder.name)
                fetchTree(f, folder.path, list, tree, Combined(listener, list.sumOf { it.size.toLong() }), handle)
                val zip = File(dir, zipName)
                withContext(Dispatchers.IO) {
                    zipFolder(tree, zip)
                    File(dir, "tree").deleteRecursively()
                }
                deliver(zip, zipName, "application/zip", target)
            }
        }
        add(Transfer(id, zipName, upload = false), start)
    }

    /** Every file under [path] (not following links: they could loop), with its path. */
    private suspend fun walk(f: RemoteFs, path: String): List<RemoteFile> {
        val out = mutableListOf<RemoteFile>()
        for (e in f.list(path)) {
            when (e.kind) {
                RemoteFileKind.DIR -> out += walk(f, e.path)
                RemoteFileKind.FILE -> out += e
                else -> Unit
            }
            if (out.size > MAX_FOLDER_FILES) error("too many files")
        }
        return out
    }

    /** Downloads [files] (from under [root]) to the same places under [into]; empty folders aren't kept. */
    private suspend fun fetchTree(f: RemoteFs, root: String, files: List<RemoteFile>, into: File, progress: Combined, handle: TransferHandle) {
        withContext(Dispatchers.IO) { into.mkdirs() }
        for (file in files) {
            val rel = file.path.removePrefix(root).trimStart('/')
            // Never outside the folder (a name with ..).
            if (rel.isEmpty() || rel.split('/').any { it == ".." || it.isEmpty() }) continue
            val local = File(into, rel)
            withContext(Dispatchers.IO) { local.parentFile?.mkdirs() }
            progress.file(file.size.toLong()) { l -> f.download(file.path, local, l, handle) }
        }
    }

    private fun zipFolder(folder: File, zip: File) {
        val base = folder.parentFile ?: folder
        ZipOutputStream(zip.outputStream().buffered()).use { out ->
            folder.walkTopDown().filter { it.isFile }.forEach { file ->
                out.putNextEntry(ZipEntry(file.relativeTo(base).invariantSeparatorsPath))
                file.inputStream().use { it.copyTo(out) }
                out.closeEntry()
            }
        }
    }

    /** Hands a downloaded file to where it was asked to go. */
    private suspend fun deliver(local: File, name: String, mime: String, target: DownloadTarget) {
        when (target) {
            DownloadTarget.Open, DownloadTarget.Share ->
                _opened.tryEmit(LocalFile(local, mime, share = target == DownloadTarget.Share))
            DownloadTarget.Downloads -> {
                if (Build.VERSION.SDK_INT < 29) error("Downloads needs Android 10")
                saveToDownloads(local, name)
                local.parentFile?.deleteRecursively()
                _messages.tryEmit(uiText(R.string.files_saved_downloads, name))
            }
            is DownloadTarget.SaveAs -> {
                copyTo(local, target.uri)
                local.parentFile?.deleteRecursively()
                _messages.tryEmit(uiText(R.string.files_saved, name))
            }
            is DownloadTarget.Preview -> Unit
        }
    }

    // ----- Transfers -----

    /** Downloads [file] to open it, share it or save it on the device. */
    fun download(file: RemoteFile, target: DownloadTarget) {
        val f = fs ?: return
        val id = ids.incrementAndGet()
        val local = File(File(openDir, "$id-${System.currentTimeMillis()}"), file.name)
        val start = {
            transfer(id) { listener, handle ->
                local.parentFile?.mkdirs()
                f.download(file.path, local, listener, handle)
                if (target is DownloadTarget.Preview) {
                    _preview.value?.local?.parentFile?.deleteRecursively()
                    _preview.value = FilePreviewState(file, local, target.kind)
                } else {
                    deliver(local, file.name, mimeOf(file.name), target)
                }
            }
        }
        add(Transfer(id, file.name, upload = false), start)
    }

    /**
     * Files picked to upload to the folder on screen: if some names already
     * exist there, asks first ([uploadAsk]); otherwise they go up.
     */
    fun pickedForUpload(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val folder = _path.value
        viewModelScope.launch {
            val files = withContext(Dispatchers.IO) { uris.map { it to displayName(it) } }
            val existing = _entries.value.map { it.name }.toSet()
            val conflicts = files.map { it.second }.filter { it in existing }
            val request = UploadRequest(files, folder, conflicts)
            if (conflicts.isEmpty()) upload(request, replace = true) else _uploadAsk.value = request
        }
    }

    fun cancelUpload() {
        _uploadAsk.value = null
    }

    /** Uploads [request]: replacing the files with the same name, or with a free name ("a (1).txt"). */
    fun upload(request: UploadRequest, replace: Boolean) {
        _uploadAsk.value = null
        val f = fs ?: return
        val taken = _entries.value.map { it.name }.toMutableSet()
        for ((uri, picked) in request.files) {
            val name = if (replace) picked else RemotePaths.freeName(picked, taken)
            taken += name
            val remote = RemotePaths.child(request.folder, name)
            val id = ids.incrementAndGet()
            val start = {
                transfer(id) { listener, handle ->
                    // The engine reads a file: a copy of the picked document first.
                    val local = staged[id]?.takeIf { it.exists() } ?: run {
                        setStatus(id, Transfer.Status.PREPARING)
                        val copy = File(File(stagingDir, id.toString()), name)
                        withContext(Dispatchers.IO) {
                            copy.parentFile?.mkdirs()
                            context.contentResolver.openInputStream(uri)?.use { input ->
                                copy.outputStream().use { input.copyTo(it) }
                            } ?: error("can't read the file")
                        }
                        staged[id] = copy
                        copy
                    }
                    setStatus(id, Transfer.Status.RUNNING)
                    f.upload(local, remote, listener, handle)
                    staged.remove(id)?.parentFile?.deleteRecursively()
                    if (_path.value == request.folder) go(request.folder, quiet = true)
                }
            }
            add(Transfer(id, name, upload = true), start)
        }
    }

    /** Stops a transfer: in the engine at once (its handle), and its turn if it was waiting. */
    fun cancel(id: Long) {
        handles[id]?.cancel()
        jobs[id]?.cancel()
    }

    fun retry(id: Long) {
        val run = retries[id] ?: return
        _transfers.update { list -> list.map { if (it.id == id) it.copy(status = Transfer.Status.WAITING, done = 0, error = null) else it } }
        run()
    }

    /** Removes a finished, failed or cancelled transfer from the list. */
    fun dismiss(id: Long) {
        _transfers.update { list -> list.filterNot { it.id == id && !it.active } }
        retries.remove(id)
        staged.remove(id)?.parentFile?.deleteRecursively()
    }

    private fun add(t: Transfer, run: () -> Unit) {
        _transfers.update { it + t }
        retries[t.id] = run
        run()
    }

    private fun setStatus(id: Long, status: Transfer.Status) {
        _transfers.update { list -> list.map { if (it.id == id) it.copy(status = status) else it } }
    }

    /** Runs a transfer in its turn, with progress, and leaves it done, failed or cancelled. */
    private fun transfer(id: Long, action: suspend (TransferListener, TransferHandle) -> Unit) {
        val handle = TransferHandle()
        handles[id] = handle
        jobs[id] = viewModelScope.launch {
            val listener = ProgressRelay { done, total ->
                _transfers.update { list ->
                    list.map { if (it.id == id) it.copy(done = done, total = total ?: it.total, status = Transfer.Status.RUNNING) else it }
                }
            }
            try {
                slots.withPermit {
                    setStatus(id, Transfer.Status.RUNNING)
                    action(listener, handle)
                }
                _transfers.update { list -> list.map { if (it.id == id) it.copy(status = Transfer.Status.DONE, done = it.total ?: it.done) else it } }
                retries.remove(id)
                // Done ones leave the list after a moment.
                delay(4_000)
                _transfers.update { list -> list.filterNot { it.id == id && it.status == Transfer.Status.DONE } }
            } catch (e: CancellationException) {
                setStatus(id, Transfer.Status.CANCELLED)
                throw e
            } catch (_: TermoakException.Cancelled) {
                // Stopped with its handle: no error to show.
                setStatus(id, Transfer.Status.CANCELLED)
            } catch (e: Exception) {
                val why = e.toUiText(if (_transfers.value.firstOrNull { it.id == id }?.upload == true) R.string.files_upload_failed else R.string.files_download_failed)
                _transfers.update { list -> list.map { if (it.id == id) it.copy(status = Transfer.Status.FAILED, error = why) else it } }
            } finally {
                jobs.remove(id)
                handles.remove(id)?.close()
            }
        }
    }

    // ----- The device's files -----

    private fun displayName(uri: Uri): String {
        val name = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')
        // Never a path: only the last part, and something valid.
        return name?.substringAfterLast('/')?.takeUnless { RemotePaths.invalidName(it) } ?: "file"
    }

    private suspend fun copyTo(local: File, uri: Uri) = withContext(Dispatchers.IO) {
        val out = runCatching { context.contentResolver.openOutputStream(uri, "wt") }.getOrNull()
            ?: context.contentResolver.openOutputStream(uri, "w")
        out?.use { stream -> local.inputStream().use { it.copyTo(stream) } }
            ?: error("can't write the file")
    }

    @RequiresApi(29)
    private suspend fun saveToDownloads(local: File, name: String) = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeOf(name))
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("can't create the file")
        try {
            resolver.openOutputStream(uri)?.use { out -> local.inputStream().use { it.copyTo(out) } } ?: error("can't write the file")
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
    }

    /** Opened files older than a day and abandoned upload copies. */
    private fun prune() {
        val old = System.currentTimeMillis() - 24 * 3600 * 1000L
        openDir.listFiles()?.filter { it.lastModified() < old }?.forEach { it.deleteRecursively() }
        stagingDir.listFiles()?.filter { it.lastModified() < old }?.forEach { it.deleteRecursively() }
    }

    override fun onCleared() {
        staged.values.forEach { it.parentFile?.deleteRecursively() }
        fs?.close()
        fs = null
        FileSources.remove(sourceId)
    }

    // ----- AuthHandler (connecting from the phone; each question on its own thread) -----

    override fun onHostKey(host: String, port: UInt, keyType: String, fingerprint: String): Boolean {
        val answer = CompletableDeferred<Boolean>()
        _pending.value = Pending.HostKey(if (port == 22u) host else "$host:$port", keyType, fingerprint) { answer.complete(it) }
        return runBlocking { withTimeoutOrNull(30_000) { answer.await() } ?: false }.also { _pending.value = null }
    }

    override fun onHostKeyChanged(change: HostKeyChange): Boolean = askChangedKey(_pending, change)

    override fun onPrompt(request: AuthRequest): List<String>? {
        val answer = CompletableDeferred<List<String>?>()
        val title = request.title.takeIf { it.isNotBlank() }?.let { UiText.Raw(it) } ?: when (request.kind) {
            AuthPromptKind.PASSWORD -> uiText(R.string.term_password_for, request.host)
            AuthPromptKind.PASSPHRASE -> uiText(R.string.term_key_passphrase)
            AuthPromptKind.KEYBOARD_INTERACTIVE -> UiText.Raw(request.host)
        }
        _pending.value = Pending.Credentials(title, request.instructions, request.fields) { answer.complete(it) }
        return runBlocking { answer.await() }.also { _pending.value = null }
    }

    companion object {
        /** Most files a folder download walks (beyond that, it stops). */
        private const val MAX_FOLDER_FILES = 10_000

        /** MIME type for other apps, by the extension (text for the usual config and log files). */
        fun mimeOf(name: String): String {
            val ext = name.substringAfterLast('.', "").lowercase()
            if (ext.isEmpty()) return "application/octet-stream"
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)?.let { return it }
            return if (ext in TextExtensions) "text/plain" else "application/octet-stream"
        }

        private val TextExtensions = setOf(
            "log", "txt", "md", "conf", "cfg", "ini", "yml", "yaml", "toml", "env", "sh", "bash", "zsh", "py", "rb",
            "js", "ts", "go", "rs", "c", "h", "cpp", "kt", "java", "php", "sql", "csv", "service", "properties", "list",
        )
    }
}

/**
 * The progress of several files as one transfer: [file] runs a download with
 * a listener that adds what came before.
 */
private class Combined(private val listener: TransferListener, private val total: Long) {
    @Volatile private var before = 0L

    suspend fun file(size: Long, run: suspend (TransferListener) -> Unit) {
        val base = before
        run(object : TransferListener {
            override fun onProgress(transferred: ULong, total: ULong?) {
                listener.onProgress((base + transferred.toLong()).toULong(), this@Combined.total.toULong())
            }
        })
        before = base + size
        listener.onProgress(before.toULong(), total.toULong())
    }
}

/** Passes the engine's progress (background thread) on, at most about ten times a second. */
private class ProgressRelay(private val onChange: (Long, Long?) -> Unit) : TransferListener {
    @Volatile private var last = 0L

    override fun onProgress(transferred: ULong, total: ULong?) {
        val now = System.nanoTime()
        if (now - last < 100_000_000L && transferred != total) return
        last = now
        onChange(transferred.toLong(), total?.toLong())
    }
}
