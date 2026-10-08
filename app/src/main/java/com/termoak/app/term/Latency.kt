package com.termoak.app.term

/**
 * The latency badge of a terminal (the desktop's terminal/latency.rs): the
 * round trip to the host of a terminal from the phone, measured every
 * [INTERVAL_MS] while it is on screen and connected. Server sessions have no
 * measurement of their own in the library, so they don't show one.
 */
object Latency {
    /** Time between measurements. */
    const val INTERVAL_MS = 5_000L

    /** Below this it is shown as normal (gray). */
    private const val FAIR_FROM_MS = 150.0
    /** From this on it is shown in red (amber in between). */
    private const val POOR_FROM_MS = 400.0

    enum class Level { UNKNOWN, GOOD, FAIR, POOR }

    fun level(ms: Double?): Level = when {
        ms == null || ms.isNaN() || ms < 0 -> Level.UNKNOWN
        ms < FAIR_FROM_MS -> Level.GOOD
        ms < POOR_FROM_MS -> Level.FAIR
        else -> Level.POOR
    }

    /** `42 ms`, `<1 ms` or `—` while unknown. */
    fun format(ms: Double?): String = when {
        level(ms) == Level.UNKNOWN -> "—"
        ms!! < 1.0 -> "<1 ms"
        else -> "${ms.toLong()} ms"
    }
}

/** How long a terminal has been connected, as a timer (`4:07`, `1:02:09`), like iOS's `Text(date, style: .timer)`. */
object Elapsed {
    fun format(ms: Long): String {
        val total = (ms / 1000).coerceAtLeast(0)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) String.format(java.util.Locale.ROOT, "%d:%02d:%02d", h, m, s)
        else String.format(java.util.Locale.ROOT, "%d:%02d", m, s)
    }
}

/**
 * Reconnecting the terminals of the phone that the system or the network cut
 * while the app was away (the iOS app's rule): what to do with one when the
 * app comes back (or the network does).
 */
object AutoReconnect {
    /** After this long away, a terminal that still looks connected is checked (a keep-alive) before trusting it. */
    const val CHECK_AFTER_MS = 20_000L
    /** For this long after coming back, a terminal that was connected and drops reconnects by itself (the system tells late). */
    const val WINDOW_MS = 15_000L

    enum class Action { NONE, RECONNECT, WATCH, CHECK }

    /**
     * [connectedWhenLeaving]: it was connected when the app left; [closed] /
     * [connected]: its state now; [endedByProgram]: it closed with an exit
     * code (`exit`), which doesn't come back by itself.
     */
    fun onReturn(connectedWhenLeaving: Boolean, asleep: Boolean, closed: Boolean, connected: Boolean, endedByProgram: Boolean, awayMs: Long): Action = when {
        !connectedWhenLeaving || asleep -> Action.NONE
        closed -> if (endedByProgram) Action.NONE else Action.RECONNECT
        connected -> if (awayMs >= CHECK_AFTER_MS) Action.CHECK else Action.WATCH
        else -> Action.NONE
    }
}
