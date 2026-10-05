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

/** The first, generic connecting step ("Connecting…"), before the real ones. */
val InitialConnecting = TermState.Connecting(uiText(R.string.term_connecting))

/**
 * A terminal open in the app: local (SSH from the phone) or a session that
 * lives on the server. It keeps the emulator ([TerminalScreen]) and what has
 * to be asked to the user. Callbacks arrive on background threads.
 */
abstract class TermSession(val label: String, val hostId: String?) {
    val id: String = UUID.randomUUID().toString()
    /** When the tab was opened (for the Connections list). */
    val openedAt: Long = System.currentTimeMillis()
    val screen = TerminalScreen(80u, 24u, 0u)
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
        _state.value = TermState.Connecting(uiText(R.string.term_reconnecting))
        start()
    }

    /** Processes terminal output (terminal thread). */
    protected fun output(data: ByteArray) {
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

    fun text(text: String) {
        val m = modifiers()
        if (m.ctrl || m.alt) {
            text.codePoints().forEach { cp -> write(screen.character(String(Character.toChars(cp)), m)) }
        } else {
            write(text.toByteArray())
        }
    }

    fun key(key: TerminalKey, shift: Boolean = false) {
        write(screen.key(key, modifiers().copy(shift = shift)))
    }

    fun paste(text: String) = write(screen.paste(text))

    open fun resize(cols: Int, rows: Int) {
        if (cols.toUInt() == screen.cols() && rows.toUInt() == screen.rows()) return
        screen.resize(cols.toUInt(), rows.toUInt())
        resizeRemote(cols.toUInt(), rows.toUInt())
        onScreenChanged()
    }

    // ----- Owner actions on a shared terminal (no-ops if it isn't shared) -----

    open fun allowJoin(participantId: String) {}
    open fun denyJoin(participantId: String) {}
    open fun grantControl(participantId: String) {}
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
