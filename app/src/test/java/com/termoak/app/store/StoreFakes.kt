package com.termoak.app.store

import com.termoak.ffi.AccountHandle
import com.termoak.ffi.AccountInfo
import com.termoak.ffi.AiAccessInfo
import com.termoak.ffi.AiApproval
import com.termoak.ffi.AiPermissionMode
import com.termoak.ffi.AiProviders
import com.termoak.ffi.AiTask
import com.termoak.ffi.AuditEvent
import com.termoak.ffi.AuthHandler
import com.termoak.ffi.CommandEnded
import com.termoak.ffi.CommandHistoryItem
import com.termoak.ffi.CommandOutputEvent
import com.termoak.ffi.CommandSuggestion
import com.termoak.ffi.CommandWatcher
import com.termoak.ffi.ConnectionDetails
import com.termoak.ffi.EventSubscription
import com.termoak.ffi.FindStatus
import com.termoak.ffi.HostGroup
import com.termoak.ffi.HostKeyChangeHandler
import com.termoak.ffi.HostProbe
import com.termoak.ffi.HostReach
import com.termoak.ffi.HostSettings
import com.termoak.ffi.ItemFilter
import com.termoak.ffi.ItemRef
import com.termoak.ffi.KeyModifiers
import com.termoak.ffi.KnownHost
import com.termoak.ffi.LastCommandInfo
import com.termoak.ffi.LineTracker
import com.termoak.ffi.MouseEncoding
import com.termoak.ffi.MouseMode
import com.termoak.ffi.NoHandle
import com.termoak.ffi.PortForward
import com.termoak.ffi.RemoteFile
import com.termoak.ffi.RemoteOs
import com.termoak.ffi.ScreenCursor
import com.termoak.ffi.ScreenCursorShape
import com.termoak.ffi.ScreenEvent
import com.termoak.ffi.ScreenLine
import com.termoak.ffi.ScreenPoint
import com.termoak.ffi.ScreenRange
import com.termoak.ffi.ScreenRun
import com.termoak.ffi.ScreenSnapshot
import com.termoak.ffi.ServerEventListener
import com.termoak.ffi.ServerSessionList
import com.termoak.ffi.ServerUser
import com.termoak.ffi.SessionActivity
import com.termoak.ffi.Snippet
import com.termoak.ffi.SshHost
import com.termoak.ffi.SshIdentity
import com.termoak.ffi.SshKey
import com.termoak.ffi.SshSession
import com.termoak.ffi.SyncReport
import com.termoak.ffi.Team
import com.termoak.ffi.TerminalColors
import com.termoak.ffi.TerminalHandle
import com.termoak.ffi.TerminalKey
import com.termoak.ffi.TerminalListener
import com.termoak.ffi.TerminalModes
import com.termoak.ffi.TerminalScreen
import com.termoak.ffi.TerminalStatus
import com.termoak.ffi.TermoakCore
import com.termoak.ffi.TermoakException
import com.termoak.ffi.TwoFactorStatus
import com.termoak.ffi.UnsyncedChanges
import com.termoak.ffi.VaultInfo
import com.termoak.ffi.VaultMember
import kotlinx.coroutines.awaitCancellation

/*
 * Stand-ins for the engine (the Rust library, which can't load on the JVM):
 * the uniffi classes' "fake object" constructor (NoHandle) with the calls the
 * screens make answered from the demo data in [Demo]. Anything else still
 * goes to the native library and fails loudly.
 */

private fun ItemFilter?.accepts(accountId: String?, vaultId: String?): Boolean {
    if (this == null) return true
    if (accountIds != null && accountId !in accountIds!!) return false
    if (vaultIds != null && vaultId !in vaultIds!!) return false
    return accountId != null || includeDevice
}

class FakeCore : TermoakCore(NoHandle) {
    @Volatile var view: String? = null

    override fun accounts(): List<AccountInfo> = Demo.accounts
    override fun accountView(): String? = view
    override fun setAccountView(accountId: String?) {
        view = accountId
    }
    override fun account(accountId: String): AccountHandle =
        FakeAccount(Demo.accounts.first { it.id == accountId })
    override fun vaults(filter: ItemFilter?): List<VaultInfo> =
        Demo.vaults.filter { filter.accepts(it.accountId, it.id) }
    override fun unsyncedChanges(accountId: String): UnsyncedChanges = UnsyncedChanges(0UL)
    override fun setDeviceName(name: String) = Unit

    override fun listHosts(filter: ItemFilter?): List<SshHost> = Demo.hosts.filter { filter.accepts(it.accountId, it.vaultId) }
    override fun listGroups(filter: ItemFilter?): List<HostGroup> = Demo.groups.filter { filter.accepts(it.accountId, it.vaultId) }
    override fun listKeys(filter: ItemFilter?): List<SshKey> = Demo.keys.filter { filter.accepts(it.accountId, it.vaultId) }
    override fun listIdentities(filter: ItemFilter?): List<SshIdentity> = emptyList()
    override fun listSnippets(filter: ItemFilter?): List<Snippet> = Demo.snippets.filter { filter.accepts(it.accountId, it.vaultId) }
    override fun listForwards(hostId: String?, filter: ItemFilter?): List<PortForward> = emptyList()
    override fun listKnownHosts(filter: ItemFilter?): List<KnownHost> = emptyList()
    override fun listMemories(filter: ItemFilter?): List<com.termoak.ffi.AiMemory> = emptyList()

    override fun getHost(id: String, accountId: String?): SshHost =
        Demo.hosts.firstOrNull { it.id == id } ?: throw TermoakException.NotFound(id)
    override fun getKey(id: String, accountId: String?): SshKey =
        Demo.keys.firstOrNull { it.id == id } ?: throw TermoakException.NotFound(id)
    override fun effectiveSettings(hostId: String, accountId: String?): HostSettings =
        Demo.hosts.firstOrNull { it.id == hostId }?.settings ?: HostSettings()
    override fun hostHasProxyPassword(id: String, accountId: String?): Boolean = false

    override fun commandHistory(hostId: String?, query: String, limit: UInt): List<CommandHistoryItem> = emptyList()
    override fun completeCommand(hostId: String?, os: String?, line: String, limit: UInt): List<CommandSuggestion> = emptyList()
    override fun recordCommand(hostId: String, command: String): Boolean = true

    override suspend fun probeHosts(hosts: List<ItemRef>, off: List<String>, concurrency: UInt): List<HostProbe> =
        hosts.map { ref ->
            val ms = Demo.latency[Demo.hosts.firstOrNull { it.id == ref.id }?.label]
            HostProbe(
                ref.id, ref.accountId, if (ms != null) HostReach.UP else HostReach.DOWN, ms?.toUInt(),
                Demo.NOW, null, null, null,
            )
        }

    override suspend fun sessionActivity(sessionId: String): SessionActivity? = null

    override suspend fun connectTerminal(
        hostId: String,
        cols: UInt,
        rows: UInt,
        auth: AuthHandler,
        listener: TerminalListener,
        accountId: String?,
        telnetAutoLogin: Boolean,
        record: Boolean,
        keyChanged: HostKeyChangeHandler?,
    ): TerminalHandle = FakeTerminalHandle(getHost(hostId, accountId))

    override suspend fun connect(hostId: String, auth: AuthHandler, accountId: String?, keyChanged: HostKeyChangeHandler?): SshSession =
        FakeSsh(getHost(hostId, accountId))
}

class FakeEvents : EventSubscription(NoHandle) {
    override fun unsubscribe() = Unit
    override fun close() = Unit
}

class FakeAccount(private val info: AccountInfo) : AccountHandle(NoHandle) {
    override fun id(): String = info.id
    override fun info(): AccountInfo = info
    override suspend fun refreshInfo(): AccountInfo = info
    override suspend fun syncNow(): SyncReport = SyncReport(0UL, 0UL, 42L, info.id)
    override suspend fun subscribeEvents(listener: ServerEventListener): EventSubscription = FakeEvents()
    override suspend fun registerPushToken(platform: com.termoak.ffi.PushPlatform, token: String, sandbox: Boolean): Boolean = false
    override suspend fun currentUser(): ServerUser =
        ServerUser("u-" + info.id.takeLast(4), info.email, info.name, false, false, true, Demo.NOW - 90L * 86_400_000, "en")
    override suspend fun twoFactorStatus(): TwoFactorStatus = TwoFactorStatus(true, 8u)
    override suspend fun listTeams(): List<Team> = Demo.teams.filter { info.id == Demo.A1 }
    override suspend fun listVaults(): List<VaultInfo> = Demo.vaults.filter { it.accountId == info.id }
    override suspend fun vaultMembers(vaultId: String): List<VaultMember> = Demo.members(vaultId)
    override suspend fun vaultAudit(vaultId: String, limit: UInt, before: Long?): List<AuditEvent> = emptyList()
    override suspend fun listPendingApprovals(): List<AiApproval> =
        if (info.id == Demo.A1) Demo.aiTasks().flatMap { it.pendingApprovals } else emptyList()
    override suspend fun listAiTasks(limit: UInt): List<AiTask> = if (info.id == Demo.A1) Demo.aiTasks() else emptyList()
    override suspend fun getAiTask(taskId: String): AiTask = Demo.aiTasks().first { it.id == taskId }
    override suspend fun aiAccess(): AiAccessInfo = Demo.aiAccess
    override suspend fun listAiProviders(): AiProviders = Demo.aiProviders
    override suspend fun listServerSessions(): ServerSessionList = ServerSessionList(emptyList(), emptyList(), emptyList())
    override suspend fun setAiTaskMode(taskId: String, mode: AiPermissionMode) = Unit
}

class FakeTerminalHandle(private val host: SshHost) : TerminalHandle(NoHandle) {
    override fun closeTerminal() = Unit
    override fun close() = Unit
    override fun isTelnet(): Boolean = false
    override suspend fun latencyMs(timeoutMs: UInt): Double = (Demo.latency[host.label] ?: 24).toDouble()
    override fun protocol(): String = "ssh"
    override fun recordingPath(): String? = null
    override fun resize(cols: UInt, rows: UInt) = Unit
    override fun session(): SshSession = FakeSsh(host)
    override fun snapshot(): ByteArray = ByteArray(0)
    override fun status(): TerminalStatus = TerminalStatus.Running
    override fun textTail(maxChars: UInt): String = ""
    override fun write(data: ByteArray) = Unit
    override fun writeText(text: String) = Unit
}

class FakeSsh(private val host: SshHost) : SshSession(NoHandle) {
    override fun accountId(): String? = host.accountId
    override fun hostId(): String = host.id
    override fun isClosed(): Boolean = false
    override fun isTelnet(): Boolean = false
    override fun protocol(): String = "ssh"
    override fun close() = Unit
    override fun details(): ConnectionDetails = ConnectionDetails(
        host.label, host.address, 22u, host.settings.username ?: "root", "ssh-ed25519",
        "SHA256:Zt4yJ8o1q3bq0wJmP5kX9Vh2cQx7nL6sAe1rT0uYw2E", null, emptyList(),
    )
    override suspend fun detectOs(): String? = host.os
    override suspend fun detectOsInfo(): RemoteOs? = null
    override suspend fun disconnect() = Unit
    override suspend fun sftpHome(): String = Demo.FILES_HOME
    override suspend fun sftpList(path: String): List<RemoteFile> = Demo.files(path)
    /** A download under way: its progress, and it keeps going. */
    override suspend fun sftpDownload(
        remotePath: String,
        localPath: String,
        listener: com.termoak.ffi.TransferListener?,
        cancel: com.termoak.ffi.TransferHandle?,
    ): ULong {
        val f = Demo.files(remotePath.substringBeforeLast('/')).firstOrNull { it.path == remotePath }
        val total = f?.size ?: 1_000_000uL
        val done = (total.toDouble() * Demo.progress(remotePath.substringAfterLast('/'))).toULong()
        listener?.onProgress(done, total)
        hang()
    }

    override suspend fun sftpStat(path: String): RemoteFile = Demo.files(path.substringBeforeLast('/').ifEmpty { "/" })
        .firstOrNull { it.path == path } ?: Demo.dir(path)
}

class FakeTransferHandle : com.termoak.ffi.TransferHandle(NoHandle) {
    override fun cancel() = Unit
    override fun isCancelled(): Boolean = false
    override fun close() = Unit
}

/** The engine's command watcher: nothing ends while the screenshots are taken. */
class FakeWatcher : CommandWatcher(NoHandle) {
    override fun enter(command: String?, prompt: String?): Boolean = false
    override fun idle(alternateScreen: Boolean, beforeCursor: String, afterBlank: Boolean): CommandEnded? = null
    override fun integrated(): Boolean = true
    override fun lastCommand(): LastCommandInfo? = null
    override fun output(data: ByteArray, alternateScreen: Boolean): CommandOutputEvent = CommandOutputEvent(false, null)
    override fun reset() = Unit
    override fun waitingForPrompt(): Boolean = false
}

class FakeLineTracker : LineTracker(NoHandle) {
    override fun atEnd(): Boolean = true
    override fun current(): String? = null
    override fun feed(data: ByteArray): String? = null
    override fun forget() = Unit
    override fun reset() = Unit
}

/**
 * The terminal emulator: its screen is [Demo.terminal]'s ANSI text for the
 * host (SGR colours, bold, underline and inverse), laid out for the size the
 * view gives it, in the colours of the theme the app sets.
 */
class FakeScreen : TerminalScreen(NoHandle) {
    @Volatile private var cols = 80
    @Volatile private var rows = 24
    @Volatile private var colors: TerminalColors = Themes.colors(Themes.DEFAULT)
    /** What the screen shows for a size (set when the terminal connects). */
    @Volatile var content: (cols: Int, rows: Int) -> List<String> = { _, _ -> emptyList() }

    override fun alternateScreen(): Boolean = false
    override fun character(ch: String, modifiers: KeyModifiers): ByteArray = ch.toByteArray()
    override fun clearFind() = Unit
    override fun colors(): TerminalColors = colors
    override fun cols(): UInt = cols.toUInt()
    override fun rows(): UInt = rows.toUInt()
    override fun feed(data: ByteArray): List<ScreenEvent> = emptyList()
    override fun find(query: String, caseSensitive: Boolean, regex: Boolean): FindStatus = findStatus()
    override fun findStatus(): FindStatus = FindStatus(0u, false, null, null, null, false)
    override fun findStep(older: Boolean): FindStatus = findStatus()
    override fun historySize(): UInt = 0u
    override fun key(key: TerminalKey, modifiers: KeyModifiers): ByteArray = ByteArray(0)
    override fun lineAt(point: ScreenPoint): ScreenRange = ScreenRange(point, point, "")
    override fun linkAt(row: UInt, col: UInt): String? = null
    override fun modes(): TerminalModes = TerminalModes(
        MouseMode.OFF, MouseEncoding.DEFAULT, true, false, false, false, false, false, true, false,
    )
    override fun paste(text: String): ByteArray = text.toByteArray()
    override fun pointAt(row: UInt, col: UInt): ScreenPoint = ScreenPoint(row.toInt(), col)
    override fun reset() = Unit
    override fun resize(cols: UInt, rows: UInt) {
        this.cols = cols.toInt()
        this.rows = rows.toInt()
    }
    override fun screenText(): String = content(cols, rows).joinToString("\n") { Ansi.plain(it) }
    override fun scroll(lines: Int) = Unit
    override fun scrollToBottom() = Unit
    override fun scrollToLine(line: Int) = Unit
    override fun setColors(colors: TerminalColors) {
        this.colors = colors
    }
    override fun setTheme(id: String): Boolean {
        colors = Themes.colors(id)
        return true
    }
    override fun textRange(start: ScreenPoint, end: ScreenPoint, block: Boolean): String = ""
    override fun wordAt(point: ScreenPoint): ScreenRange? = null

    override fun snapshot(): ScreenSnapshot {
        val c = colors
        // Each line of text, wrapped at the screen's width as a terminal does.
        val wrapped = content(cols, rows).flatMap { line -> split(Ansi.runs(line, c, Int.MAX_VALUE), maxOf(1, Ansi.plain(line).length)) }
        val shown = wrapped.takeLast(rows)
        val lines = shown.map { ScreenLine(it) } + List(rows - shown.size) { ScreenLine(emptyList()) }
        // The cursor after the last line with text (the prompt).
        val last = shown.lastOrNull()
        val col = last?.lastOrNull()?.let { (it.col + it.cells).toInt() } ?: 0
        val cursor = ScreenCursor((shown.size - 1).coerceAtLeast(0).toUInt(), col.coerceAtMost(cols - 1).toUInt(), ScreenCursorShape.BLOCK, false)
        return ScreenSnapshot(cols.toUInt(), rows.toUInt(), lines, cursor, 0u, c.background or Ansi.OPAQUE, c.foreground or Ansi.OPAQUE, c.cursor or Ansi.OPAQUE)
    }

    /** A line's runs in rows of [cols] cells. */
    private fun split(runs: List<ScreenRun>, length: Int): List<List<ScreenRun>> {
        val count = (length + cols - 1) / cols
        return List(count.coerceAtLeast(1)) { r ->
            val from = r * cols
            val to = from + cols
            runs.mapNotNull { run ->
                val s = maxOf(run.col.toInt(), from)
                val e = minOf((run.col + run.cells).toInt(), to)
                if (s >= e) null
                else run.copy(col = (s - from).toUInt(), cells = (e - s).toUInt(), text = run.text.substring(s - run.col.toInt(), e - run.col.toInt()))
            }
        }
    }
}

/** SGR escapes (`ESC [ … m`) in the demo screens, made into the engine's runs. */
object Ansi {
    /** The engine's colours are ARGB. */
    const val OPAQUE = 0xFF000000u

    private val sgr = Regex("\u001b\\[([0-9;]*)m")

    fun plain(s: String): String = s.replace(sgr, "")

    private fun xterm(n: Int, c: TerminalColors): UInt = when {
        n < 16 -> c.ansi[n]
        n < 232 -> {
            val i = n - 16
            val steps = intArrayOf(0, 95, 135, 175, 215, 255)
            ((steps[i / 36] shl 16) or (steps[i / 6 % 6] shl 8) or steps[i % 6]).toUInt()
        }
        else -> (8 + (n - 232) * 10).let { (it shl 16) or (it shl 8) or it }.toUInt()
    }

    fun runs(line: String, c: TerminalColors, cols: Int): List<ScreenRun> {
        val out = mutableListOf<ScreenRun>()
        var fg: UInt? = null
        var bg: UInt? = null
        var bold = false
        var italic = false
        var under = false
        var inverse = false
        var dim = false
        var col = 0
        val buf = StringBuilder()
        var start = 0
        fun flush() {
            if (buf.isEmpty()) return
            var f = fg ?: c.foreground
            var b = bg
            if (inverse) {
                val nf = b ?: c.background
                b = f
                f = nf
            }
            if (dim) f = blend(f, c.background)
            val text = buf.toString()
            out += ScreenRun(start.toUInt(), text.length.toUInt(), text, f or OPAQUE, b?.let { it or OPAQUE }, bold, italic, under, false, false)
            buf.clear()
        }
        var i = 0
        while (i < line.length && col < cols) {
            val m = sgr.find(line, i)?.takeIf { it.range.first == i }
            if (m != null) {
                flush()
                val codes = m.groupValues[1].split(';').map { it.toIntOrNull() ?: 0 }
                var k = 0
                while (k < codes.size) {
                    when (val code = codes[k]) {
                        0 -> { fg = null; bg = null; bold = false; italic = false; under = false; inverse = false; dim = false }
                        1 -> bold = true
                        2 -> dim = true
                        3 -> italic = true
                        4 -> under = true
                        7 -> inverse = true
                        22 -> { bold = false; dim = false }
                        23 -> italic = false
                        24 -> under = false
                        27 -> inverse = false
                        in 30..37 -> fg = c.ansi[code - 30]
                        in 90..97 -> fg = c.ansi[code - 90 + 8]
                        39 -> fg = null
                        in 40..47 -> bg = c.ansi[code - 40]
                        in 100..107 -> bg = c.ansi[code - 100 + 8]
                        49 -> bg = null
                        38, 48 -> {
                            val v = if (codes.getOrNull(k + 1) == 5) {
                                xterm(codes.getOrElse(k + 2) { 0 }, c).also { k += 2 }
                            } else {
                                ((codes.getOrElse(k + 2) { 0 } shl 16) or (codes.getOrElse(k + 3) { 0 } shl 8) or codes.getOrElse(k + 4) { 0 })
                                    .toUInt().also { k += 4 }
                            }
                            if (code == 38) fg = v else bg = v
                        }
                    }
                    k++
                }
                i = m.range.last + 1
                start = col
                continue
            }
            if (buf.isEmpty()) start = col
            buf.append(line[i])
            col++
            i++
        }
        flush()
        return out
    }

    private fun blend(a: UInt, b: UInt): UInt {
        fun ch(x: UInt, s: Int) = ((x shr s) and 0xFFu).toInt()
        fun mix(s: Int) = (ch(a, s) * 0.6 + ch(b, s) * 0.4).toInt()
        return ((mix(16) shl 16) or (mix(8) shl 8) or mix(0)).toUInt()
    }
}

/** Keeps a suspend call waiting (as a live connection would). */
suspend fun hang(): Nothing = awaitCancellation()
