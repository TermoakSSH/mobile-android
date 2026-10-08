package com.termoak.app.term

import com.termoak.app.R
import com.termoak.app.UiText
import com.termoak.app.uiText
import com.termoak.ffi.KeyModifiers
import com.termoak.ffi.PromptField
import com.termoak.ffi.ScreenEvent
import com.termoak.ffi.TerminalKey
import com.termoak.ffi.TerminalScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

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
        send(bytes)
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
    }
}
