package com.termoak.app.term

import com.termoak.app.R
import com.termoak.app.UiText
import com.termoak.app.uiText
import com.termoak.ffi.CommandSuggestion
import com.termoak.ffi.KeyModifiers
import com.termoak.ffi.LineTracker
import com.termoak.ffi.PromptField
import com.termoak.ffi.ScreenEvent
import com.termoak.ffi.TerminalKey
import com.termoak.ffi.TerminalScreen
import com.termoak.ffi.commandEchoed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/** What the terminal needs to ask the user. */
sealed class Pending {
    /** First connection to a host: trust its key? */
    class HostKey(
        val host: String,
        val keyType: String,
        val fingerprint: String,
        val answer: (Boolean) -> Unit,
    ) : Pending()

    /** Password, passphrase or keyboard-interactive questions (`null`: cancel). */
    class Credentials(
        val title: UiText,
        val instructions: String,
        val fields: List<PromptField>,
        val answer: (List<String>?) -> Unit,
    ) : Pending()
}

sealed class TermState {
    /** Sleeping tab: a server session that hasn't been attached yet. */
    data object Asleep : TermState()
    data class Connecting(val message: UiText) : TermState()
    data object Running : TermState()
    data class Closed(val message: UiText) : TermState()
}

/** Something typed in a terminal, to repeat it in others (broadcast input). */
sealed class TermInput {
    data class Text(val text: String, val modifiers: KeyModifiers) : TermInput()
    data class Key(val key: TerminalKey, val modifiers: KeyModifiers) : TermInput()
    data class Paste(val text: String) : TermInput()
    /** A key of a hardware keyboard, already resolved (each terminal encodes it with its own modes). */
    data class Stroke(val stroke: KeyStroke) : TermInput()
}

/** The first, generic connecting step ("Connecting…"), before the real ones. */
val InitialConnecting = TermState.Connecting(uiText(R.string.term_connecting))

/**
 * A terminal open in the app: local (SSH from the phone) or a session that
 * lives on the server. It keeps the emulator ([TerminalScreen]) and what has
 * to be asked to the user. Callbacks arrive on background threads.
 */
abstract class TermSession(
    val label: String,
    val hostId: String?,
    /** Account of the host or the server session (`null`: This device, or the current account). */
    val accountId: String? = null,
) {
    val id: String = UUID.randomUUID().toString()
    /** When the tab was opened (for the Connections list). */
    val openedAt: Long = System.currentTimeMillis()
    val screen = TerminalScreen(80u, 24u, 0u)
    /** Modes the emulator doesn't expose: mouse reporting and the application keypad. */
    val modes = ModeTracker()
    protected val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    protected val _state = MutableStateFlow<TermState>(InitialConnecting)
    val state: StateFlow<TermState> = _state
    /** Since when it is connected (ms), `null` while not (the Connections list's timer, as on iOS). */
    @Volatile var connectedAt: Long? = null
        private set

    init {
        scope.launch {
            state.collect { st ->
                when (st) {
                    TermState.Running -> if (connectedAt == null) connectedAt = System.currentTimeMillis()
                    else -> connectedAt = null
                }
            }
        }
    }
    protected val _pending = MutableStateFlow<Pending?>(null)
    val pending: StateFlow<Pending?> = _pending
    protected val _title = MutableStateFlow<String?>(null)
    val title: StateFlow<String?> = _title
    protected val _live = MutableStateFlow(LiveShare())
    /** Who else is in it and who has the keyboard (shared terminals). */
    val live: StateFlow<LiveShare> = _live
    private val _toasts = MutableSharedFlow<UiText>(extraBufferCapacity = 8)
    /** Short notices for whoever looks at this terminal ("You have the keyboard"...). */
    val toasts: SharedFlow<UiText> = _toasts
    /** Requests and notices for the whole app (set by [Sessions]). */
    @Volatile var onShareNotice: (ShareNotice) -> Unit = {}

    protected fun toast(text: UiText) {
        _toasts.tryEmit(text)
    }

    /** Ctrl and Alt from the key bar: they apply to the next key press. */
    val ctrl = MutableStateFlow(false)
    val alt = MutableStateFlow(false)
    /** Cursor gestures "With a button": one finger moves the cursor (instead of scrolling). */
    val cursorByButton = MutableStateFlow(false)

    // ----- Command suggestions and history (the iOS app's) -----

    /** Autocompletion and history (set by [Sessions]; `null`: none). */
    @Volatile var assist: CommandAssist? = null
    /** The host's system (`SshHost.os`), to suggest the right package manager. */
    @Volatile var hostOs: String? = null
    /** What is typed, to know the command sent with Enter and to complete it. */
    private val line = LineTracker()
    private val _suggestions = MutableStateFlow<List<CommandSuggestion>>(emptyList())
    /** How to continue the line being typed (history, snippets, commands). */
    val suggestions: StateFlow<List<CommandSuggestion>> = _suggestions
    private val _awaitingEcho = MutableStateFlow(false)
    /** Something was typed and the shell hasn't shown it yet: the dimmed rest hides so it doesn't jump. */
    val awaitingEcho: StateFlow<Boolean> = _awaitingEcho
    /** Look for suggestions as soon as the echo of what was typed arrives. */
    @Volatile private var suggestAfterEcho = false
    private val query = AtomicInteger()

    /** Needs a redraw (from any thread). */
    @Volatile var onScreenChanged: () -> Unit = {}
    /** The remote program wants to copy text (OSC 52). */
    @Volatile var onCopy: (String) -> Unit = {}
    @Volatile var onBell: () -> Unit = {}

    /** Sleeping: it attaches when first opened ([Sessions.wake]). */
    val asleep: Boolean get() = _state.value == TermState.Asleep

    /** Leaves the tab sleeping, without connecting (before [start]). */
    fun sleep() {
        _state.value = TermState.Asleep
    }

    /** Lives on the server: closing the tab only detaches. */
    abstract val persistent: Boolean

    abstract fun start()
    protected abstract fun send(bytes: ByteArray)
    protected abstract fun resizeRemote(cols: UInt, rows: UInt)
    /** Drops the connection (a server session stays alive). */
    protected abstract fun release()

    /** Connects again after closing. */
    open fun reconnect() {
        release()
        screen.reset()
        modes.reset()
        forgetLine()
        _state.value = TermState.Connecting(uiText(R.string.term_reconnecting))
        start()
    }

    /** Processes terminal output (terminal thread). */
    protected fun output(data: ByteArray) {
        modes.feed(data)
        for (event in screen.feed(data)) {
            when (event) {
                is ScreenEvent.Write -> send(event.data)
                is ScreenEvent.Title -> _title.value = event.title.ifBlank { null }
                is ScreenEvent.ResetTitle -> _title.value = null
                is ScreenEvent.Copy -> onCopy(event.text)
                is ScreenEvent.Bell -> onBell()
            }
        }
        if (_awaitingEcho.value) _awaitingEcho.value = false
        if (suggestAfterEcho) {
            suggestAfterEcho = false
            requestSuggestions()
        }
        onScreenChanged()
    }

    private fun modifiers(): KeyModifiers {
        val m = KeyModifiers(shift = false, alt = alt.value, ctrl = ctrl.value)
        ctrl.value = false
        alt.value = false
        return m
    }

    /** Typing reaches the terminal (not while someone else has the keyboard). */
    val canType: Boolean get() = _live.value.canWrite

    fun write(bytes: ByteArray) {
        if (bytes.isEmpty() || _state.value != TermState.Running || !canType) return
        screen.scrollToBottom()
        trackLine(bytes)
        send(bytes)
        typed()
    }

    /**
     * Saves in the history the line sent with Enter, only if the screen
     * showed it as it is (so passwords and lines the shell changed on its
     * own are not saved).
     */
    private fun trackLine(bytes: ByteArray) {
        if (screen.alternateScreen()) {
            // vim, less, htop...: there is no shell line.
            line.forget()
            return
        }
        val a = assist
        val pending = line.current()
        val echoed = a != null && !pending.isNullOrEmpty() && commandEchoed(pending, cursorLine(), true)
        val sent = line.feed(bytes)
        val host = hostId
        if (a != null && echoed && sent != null && sent == pending && host != null) {
            scope.launch(Dispatchers.IO) { a.record(host, sent) }
        }
    }

    /** After typing: suggestions come with the echo; meanwhile the ones that still fit stay. */
    private fun typed() {
        if ((assist?.mode ?: SuggestionMode.OFF) == SuggestionMode.OFF) {
            if (_suggestions.value.isNotEmpty()) _suggestions.value = emptyList()
            return
        }
        suggestAfterEcho = true
        _awaitingEcho.value = true
        val l = line.current()
        _suggestions.value = if (l != null && line.atEnd() && l.isNotBlank()) Suggestions.narrow(_suggestions.value, l) else emptyList()
    }

    /** Text of the cursor's line on screen (wrapped rows joined). */
    private fun cursorLine(): String {
        val snap = screen.snapshot()
        val cursor = snap.cursor ?: return ""
        return Suggestions.cursorLine(snap.lines, cursor.row.toInt(), snap.cols.toInt())
    }

    /**
     * Suggestions for the typed line: only if it is known, the cursor is at
     * its end and the screen shows it. Computed off the terminal's thread;
     * discarded if more was typed meanwhile.
     */
    private fun requestSuggestions() {
        val a = assist
        val current = line.current()
        if (a == null || a.mode == SuggestionMode.OFF || _state.value != TermState.Running || screen.alternateScreen() ||
            !line.atEnd() || current.isNullOrBlank() || !commandEchoed(current, cursorLine(), true)
        ) {
            if (_suggestions.value.isNotEmpty()) _suggestions.value = emptyList()
            return
        }
        val n = query.incrementAndGet()
        val host = hostId
        val os = hostOs
        scope.launch(Dispatchers.Default) {
            val r = a.complete(host, os, current)
            if (n == query.get() && line.current() != null) _suggestions.value = r
        }
    }

    /** New, unknown line (after reconnecting). */
    private fun forgetLine() {
        line.reset()
        query.incrementAndGet()
        _suggestions.value = emptyList()
    }

    /**
     * "Clear": the scrollback goes and, outside full-screen programs, the
     * screen too, with Ctrl+L to the shell so it draws its prompt again at
     * the top (the desktop's Clear). Nothing is typed in a read-only terminal.
     */
    fun clear() {
        val full = screen.alternateScreen()
        // ED 3 (the scrollback) and, at the shell, home and ED 2: only this screen, not sent anywhere.
        runCatching { screen.feed((if (full) "\u001b[3J" else "\u001b[H\u001b[2J\u001b[3J").toByteArray()) }
        line.reset()
        _suggestions.value = emptyList()
        if (!full) write(byteArrayOf(0x0c))
        onScreenChanged()
    }

    /** Types the rest of a suggestion (without Enter). */
    fun accept(s: CommandSuggestion) = input(TermInput.Text(s.insert, KeyModifiers(shift = false, alt = false, ctrl = false)))

    /**
     * → with suggestions next to the cursor accepts the first one, like on
     * the desktop. Whether it did.
     */
    fun acceptFirstSuggestion(): Boolean {
        if (assist?.mode != SuggestionMode.CURSOR || ctrl.value || alt.value) return false
        val first = _suggestions.value.firstOrNull() ?: return false
        accept(first)
        return true
    }

    /**
     * Runs a command (history, snippet): types it and presses Enter. It is
     * saved in the history directly (we know what it is) and the line starts over.
     */
    fun run(command: String) {
        paste(command)
        key(TerminalKey.Enter)
        line.reset()
        query.incrementAndGet()
        _suggestions.value = emptyList()
        val a = assist
        val host = hostId
        if (a != null && host != null && !command.contains('\n')) scope.launch(Dispatchers.IO) { a.record(host, command.trim()) }
    }

    /**
     * A key of the key bar or the quick panel. Ctrl and Alt stay pressed
     * until the next key, which they apply to (its first step). Paste is
     * the screen's ([onPaste]), which asks first for several lines.
     */
    fun press(key: BarKey, onPaste: () -> Unit) {
        when (val a = key.action) {
            is BarAction.Modifier -> if (a.ctrl) ctrl.value = !ctrl.value else alt.value = !alt.value
            BarAction.Paste -> {
                takeStickyModifiers()
                onPaste()
            }
            is BarAction.Steps -> {
                if (a.steps == listOf(BarStep.Special(BarSpecial.RIGHT)) && acceptFirstSuggestion()) return
                val (c, al) = takeStickyModifiers()
                keyInputs(a.steps, c, al).forEach { input(it) }
            }
        }
    }

    /**
     * Mirror of what is typed here (keys, text and pastes), for broadcast
     * input in the split view: the other panes apply it with [apply].
     */
    @Volatile var onInput: ((TermInput) -> Unit)? = null

    fun text(text: String) = input(TermInput.Text(text, modifiers()))

    fun key(key: TerminalKey, shift: Boolean = false) = input(TermInput.Key(key, modifiers().copy(shift = shift)))

    fun paste(text: String) = input(TermInput.Paste(text))

    /**
     * An arrow from the cursor gestures: encoded like the key bar's (the
     * application cursor mode included), broadcast like typing, and without
     * taking the key bar's Ctrl and Alt.
     */
    fun arrow(key: TerminalKey) = input(TermInput.Key(key, KeyModifiers(shift = false, alt = false, ctrl = false)))

    /** A key of a hardware keyboard (with the key bar's Ctrl and Alt already applied). */
    fun stroke(stroke: KeyStroke) = input(TermInput.Stroke(stroke))

    /** Takes the key bar's Ctrl and Alt for a key of a hardware keyboard. */
    fun takeStickyModifiers(): Pair<Boolean, Boolean> {
        val m = ctrl.value to alt.value
        ctrl.value = false
        alt.value = false
        return m
    }

    /** The modes that change what keys send, now. */
    fun keyModes(): KeyModes {
        // The emulator applies application cursor keys: an arrow tells which mode it's in.
        val up = runCatching { screen.key(TerminalKey.Up, KeyModifiers(shift = false, alt = false, ctrl = false)) }.getOrNull()
        return KeyModes(appCursor = up != null && up.size >= 2 && up[1] == 'O'.code.toByte(), appKeypad = modes.appKeypad)
    }

    private fun input(input: TermInput) {
        apply(input)
        onInput?.invoke(input)
    }

    /**
     * Types [input] here, encoded for this terminal's own modes (cursor
     * keys, bracketed paste...): what another pane broadcasts.
     */
    fun apply(input: TermInput) {
        when (input) {
            is TermInput.Text -> if (input.modifiers.ctrl || input.modifiers.alt) {
                input.text.codePoints().forEach { cp -> write(screen.character(String(Character.toChars(cp)), input.modifiers)) }
            } else {
                write(input.text.toByteArray())
            }
            is TermInput.Key -> write(screen.key(input.key, input.modifiers))
            is TermInput.Paste -> write(screen.paste(input.text))
            is TermInput.Stroke -> write(KeyEncoder.encode(input.stroke, keyModes()))
        }
    }

    /**
     * The program asked for bracketed paste (modern shells, vim...): a paste
     * is not run line by line until Enter is pressed.
     */
    val bracketedPaste: Boolean
        get() = runCatching { screen.paste("x") }.getOrNull()?.let { b ->
            b.size > 6 && b[0] == 0x1b.toByte() && String(b, 1, 5, Charsets.US_ASCII) == "[200~"
        } ?: false

    open fun resize(cols: Int, rows: Int) {
        if (cols.toUInt() == screen.cols() && rows.toUInt() == screen.rows()) return
        screen.resize(cols.toUInt(), rows.toUInt())
        resizeRemote(cols.toUInt(), rows.toUInt())
        onScreenChanged()
    }

    // ----- Owner actions on a shared terminal (no-ops if it isn't shared) -----

    open fun allowJoin(participantId: String) {}
    open fun denyJoin(participantId: String) {}
    /** Hands the keyboard over for [minutes] (1-240), or until it is given back or taken (`null`). */
    open fun grantControl(participantId: String, minutes: UInt? = null) {}
    open fun denyControl(participantId: String) {}
    open fun takeControl() {}
    /** Sends someone away; with [block], their invitation is revoked too. */
    open fun kick(participantId: String, block: Boolean) {}

    open fun close() {
        release()
        scope.cancel()
        screen.close()
        line.close()
    }
}
