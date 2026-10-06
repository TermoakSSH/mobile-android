package com.termoak.app.term

import com.termoak.app.UiText
import com.termoak.ffi.SshHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * A snippet sent to several terminals at once: one per chosen host (an open
 * one is reused, otherwise it is opened) or the terminals already open. Each
 * terminal gets it once it is connected; [current] says how each one went,
 * for the summary.
 */
class SnippetRuns(private val sessions: Sessions) {
    enum class Status {
        /** Connecting (or a sleeping tab waking up). */
        WAITING,
        /** It asks something first (trust the host key, a password...): open it to answer. */
        NEEDS_ANSWER,
        SENT,
        /** Someone else has the keyboard of that shared terminal. */
        READ_ONLY,
        FAILED,
    }

    data class Target(
        val sessionId: String,
        val label: String,
        val status: Status,
        val error: UiText? = null,
    )

    data class Run(
        val name: String,
        /** Run (paste and Enter) or only paste. */
        val run: Boolean,
        val targets: List<Target>,
    ) {
        val done: Boolean get() = targets.none { it.status == Status.WAITING || it.status == Status.NEEDS_ANSWER }
        val sent: Int get() = targets.count { it.status == Status.SENT }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var jobs: List<Job> = emptyList()
    private val _current = MutableStateFlow<Run?>(null)
    /** The last run, while its summary is open. */
    val current: StateFlow<Run?> = _current

    /** Sends [text] to a terminal of each host, opening the ones that aren't open. */
    fun onHosts(name: String, text: String, run: Boolean, hosts: List<SshHost>) {
        val targets = hosts.distinctBy { it.id }.map { sessions.liveLocal(it.id) ?: sessions.openLocal(it, activate = false) }
        start(name, text, run, targets)
    }

    /** Sends [text] to terminals that are already open. */
    fun onSessions(name: String, text: String, run: Boolean, ids: List<String>) {
        start(name, text, run, ids.mapNotNull { sessions.get(it) })
    }

    /** Closes the summary (what is still waiting keeps going). */
    fun dismiss() {
        _current.value = null
    }

    private fun start(name: String, text: String, run: Boolean, targets: List<TermSession>) {
        jobs.forEach { it.cancel() }
        _current.value = Run(name, run, targets.map { Target(it.id, it.label, Status.WAITING) })
        jobs = targets.map { s ->
            scope.launch {
                sessions.wake(s.id)
                // Connected (or closed); meanwhile, whether it asks something.
                var connected = false
                combine(s.state, s.pending) { st, p -> st to p }.first { (st, p) ->
                    when (st) {
                        TermState.Running -> {
                            connected = true
                            true
                        }
                        is TermState.Closed -> {
                            set(s.id, Status.FAILED, st.message)
                            true
                        }
                        else -> {
                            set(s.id, if (p != null) Status.NEEDS_ANSWER else Status.WAITING)
                            false
                        }
                    }
                }
                if (!connected) return@launch
                // A moment for the shell to start (what arrives before is kept anyway).
                delay(400)
                if (!s.canType) {
                    set(s.id, Status.READ_ONLY)
                    return@launch
                }
                s.apply(TermInput.Paste(text))
                if (run) s.apply(TermInput.Key(com.termoak.ffi.TerminalKey.Enter, NoMods))
                set(s.id, Status.SENT)
            }
        }
    }

    private fun set(id: String, status: Status, error: UiText? = null) {
        _current.update { r ->
            r?.copy(targets = r.targets.map { if (it.sessionId == id) it.copy(status = status, error = error) else it })
        }
    }

    private companion object {
        val NoMods = com.termoak.ffi.KeyModifiers(shift = false, alt = false, ctrl = false)
    }
}
