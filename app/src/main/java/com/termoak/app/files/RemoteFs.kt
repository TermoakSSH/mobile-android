package com.termoak.app.files

import com.termoak.ffi.RemoteFile
import com.termoak.ffi.RemoteFileKind
import com.termoak.ffi.SshSession
import com.termoak.ffi.TermoakCore
import com.termoak.ffi.TransferListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Where the browser's files come from (the iOS app's FileBrowser.Source). */
sealed class FilesSource {
    /** Shown as the screen's title (the host or the terminal). */
    abstract val title: String

    /** The SSH connection of an open terminal: it stays open when the browser closes. */
    class Terminal(override val title: String, val session: SshSession) : FilesSource()

    /** Connect from the phone (asking for the host key or a password if needed). */
    class Connect(override val title: String, val hostId: String, val accountId: String?) : FilesSource()

    /** SFTP done by the server of the host's account (Strict Use-only hosts, server sessions). */
    class Server(override val title: String, val hostId: String, val accountId: String?) : FilesSource()
}

/**
 * The sources of the open browsers, by id: the route only carries the id
 * (an open terminal's connection can't go in a route).
 */
object FileSources {
    private val sources = ConcurrentHashMap<String, FilesSource>()

    fun put(source: FilesSource): String = UUID.randomUUID().toString().also { sources[it] = source }

    fun get(id: String): FilesSource? = sources[id]

    fun remove(id: String) {
        sources.remove(id)
    }
}

/** The SFTP operations, over the phone's SSH connection or through the server. */
interface RemoteFs {
    /** Permissions can be changed (only over direct SSH). */
    val canChmod: Boolean
    suspend fun home(): String
    suspend fun list(path: String): List<RemoteFile>
    suspend fun download(remote: String, local: File, listener: TransferListener)
    suspend fun upload(local: File, remote: String, listener: TransferListener)
    suspend fun mkdir(path: String)
    suspend fun rename(from: String, to: String)
    suspend fun delete(path: String, recursive: Boolean)
    suspend fun chmod(path: String, mode: Int)
    /** Releases the connection (it is only closed if the browser opened it). */
    fun close()
}

class SshFs(private val session: SshSession, private val own: Boolean) : RemoteFs {
    override val canChmod = true
    override suspend fun home() = session.sftpHome()
    override suspend fun list(path: String) = session.sftpList(path)
    override suspend fun download(remote: String, local: File, listener: TransferListener) {
        session.sftpDownload(remote, local.path, listener)
    }
    override suspend fun upload(local: File, remote: String, listener: TransferListener) {
        session.sftpUpload(local.path, remote, listener)
    }
    override suspend fun mkdir(path: String) = session.sftpMkdir(path, false)
    override suspend fun rename(from: String, to: String) = session.sftpRename(from, to)
    override suspend fun delete(path: String, recursive: Boolean) = session.sftpRemove(path, recursive)
    override suspend fun chmod(path: String, mode: Int) = session.sftpChmod(path, mode.toUInt())
    override fun close() {
        // Our own reference to the connection; a terminal's one stays connected.
        val s = session
        CoroutineScope(Dispatchers.IO).launch {
            if (own) runCatching { s.disconnect() }
            s.close()
        }
    }
}

class ServerFs(private val core: TermoakCore, private val hostId: String, private val accountId: String?) : RemoteFs {
    override val canChmod = false
    override suspend fun home() = core.serverSftpHome(hostId, accountId)
    override suspend fun list(path: String) = core.serverSftpList(hostId, path, accountId)
    override suspend fun download(remote: String, local: File, listener: TransferListener) {
        core.serverSftpDownload(hostId, remote, local.path, listener, accountId)
    }
    override suspend fun upload(local: File, remote: String, listener: TransferListener) {
        core.serverSftpUpload(hostId, local.path, remote, listener, accountId)
    }
    override suspend fun mkdir(path: String) = core.serverSftpMkdir(hostId, path, false, accountId)
    override suspend fun rename(from: String, to: String) = core.serverSftpRename(hostId, from, to, accountId)
    override suspend fun delete(path: String, recursive: Boolean) = core.serverSftpDelete(hostId, path, recursive, accountId)
    /** Not offered: [canChmod] is false. */
    override suspend fun chmod(path: String, mode: Int) = throw UnsupportedOperationException()
    override fun close() {}
}

/** The engine's entry as the listing needs it. */
fun RemoteFile.item(): FileItem = FileItem(
    name = name, path = path, dir = kind == RemoteFileKind.DIR, link = kind == RemoteFileKind.SYMLINK,
    size = size.toLong(), modified = modified,
)
