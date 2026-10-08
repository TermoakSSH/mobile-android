package com.termoak.app.files

import com.termoak.ffi.RemoteFile
import com.termoak.ffi.RemoteFileKind
import com.termoak.ffi.SshSession
import com.termoak.ffi.TermoakCore
import com.termoak.ffi.TransferHandle
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

/**
 * The SFTP operations, over the phone's SSH connection or through the server
 * (the same ones both ways: Strict hosts and server sessions too). Transfers
 * take a [TransferHandle] whose `cancel()` stops them in the engine (the call
 * then fails with `Cancelled`).
 */
interface RemoteFs {
    suspend fun home(): String
    suspend fun list(path: String): List<RemoteFile>
    /** The file's details as they are now (a link: its target's). */
    suspend fun stat(path: String): RemoteFile
    suspend fun download(remote: String, local: File, listener: TransferListener, cancel: TransferHandle? = null)
    suspend fun upload(local: File, remote: String, listener: TransferListener, cancel: TransferHandle? = null)
    /** A whole file in memory (editors, viewers); fails above [maxBytes] (0: the engine's 16 MiB). */
    suspend fun read(path: String, maxBytes: Long = 0): ByteArray
    /** Creates or overwrites a file with [data]. */
    suspend fun write(path: String, data: ByteArray)
    suspend fun mkdir(path: String)
    suspend fun rename(from: String, to: String)
    suspend fun delete(path: String, recursive: Boolean)
    suspend fun chmod(path: String, mode: Int)
    /** Releases the connection (it is only closed if the browser opened it). */
    fun close()
}

class SshFs(private val session: SshSession, private val own: Boolean) : RemoteFs {
    override suspend fun home() = session.sftpHome()
    override suspend fun list(path: String) = session.sftpList(path)
    override suspend fun stat(path: String) = session.sftpStat(path)
    override suspend fun download(remote: String, local: File, listener: TransferListener, cancel: TransferHandle?) {
        session.sftpDownload(remote, local.path, listener, cancel)
    }
    override suspend fun upload(local: File, remote: String, listener: TransferListener, cancel: TransferHandle?) {
        session.sftpUpload(local.path, remote, listener, cancel)
    }
    override suspend fun read(path: String, maxBytes: Long) = session.sftpRead(path, maxBytes.toULong())
    override suspend fun write(path: String, data: ByteArray) = session.sftpWrite(path, data)
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

/** SFTP done by the server of the host's account (it makes the connection: Strict hosts, hosts only it reaches). */
class ServerFs(private val core: TermoakCore, private val hostId: String, private val accountId: String?) : RemoteFs {
    override suspend fun home() = core.serverSftpHome(hostId, accountId)
    override suspend fun list(path: String) = core.serverSftpList(hostId, path, accountId)
    override suspend fun stat(path: String) = core.serverSftpStat(hostId, path, accountId)
    override suspend fun download(remote: String, local: File, listener: TransferListener, cancel: TransferHandle?) {
        core.serverSftpDownload(hostId, remote, local.path, listener, accountId, cancel)
    }
    override suspend fun upload(local: File, remote: String, listener: TransferListener, cancel: TransferHandle?) {
        core.serverSftpUpload(hostId, local.path, remote, listener, accountId, cancel)
    }
    override suspend fun read(path: String, maxBytes: Long) = core.serverSftpRead(hostId, path, maxBytes.toULong(), accountId)
    override suspend fun write(path: String, data: ByteArray) {
        core.serverSftpWrite(hostId, path, data, accountId)
    }
    override suspend fun mkdir(path: String) = core.serverSftpMkdir(hostId, path, false, accountId)
    override suspend fun rename(from: String, to: String) = core.serverSftpRename(hostId, from, to, accountId)
    override suspend fun delete(path: String, recursive: Boolean) = core.serverSftpDelete(hostId, path, recursive, accountId)
    override suspend fun chmod(path: String, mode: Int) = core.serverSftpChmod(hostId, path, mode.toUInt(), accountId)
    override fun close() {}
}

/** The engine's entry as the listing needs it. */
fun RemoteFile.item(): FileItem = FileItem(
    name = name, path = path, dir = kind == RemoteFileKind.DIR, link = kind == RemoteFileKind.SYMLINK,
    size = size.toLong(), modified = modified,
)
