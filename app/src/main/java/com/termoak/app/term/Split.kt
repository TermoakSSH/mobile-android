package com.termoak.app.term

/**
 * Split view of the terminal screen on tablets and unfolded foldables
 * (the desktop's panes.rs): several open terminals side by side or in a
 * grid. It lives in [Sessions], so it survives navigation and folding: on a
 * phone-sized window only the focused terminal shows, and the grid comes back
 * when the window is wide again.
 *
 * The focused pane is the active terminal ([Sessions.active]).
 */
data class SplitState(
    /** Terminals in the grid, in order (empty or one: no split). */
    val panes: List<String> = emptyList(),
    /** A pane shown alone for a while (the others stay in the split). */
    val maximized: String? = null,
    /** What is typed in the focused pane also goes to the other visible panes. */
    val broadcast: Boolean = false,
) {
    val on: Boolean get() = panes.size >= 2

    /**
     * The panes that fit in [max] (4 on wide windows, 2 on medium ones), the
     * focused one always among them.
     */
    fun visible(max: Int, focused: String?): List<String> {
        if (max < 2 || !on) return emptyList()
        if (panes.size <= max) return panes
        val first = panes.take(max)
        if (focused == null || focused in first || focused !in panes) return first
        return panes.take(max - 1) + focused
    }

    companion object {
        /** Most panes in the grid (2 × 2). */
        const val MAX_PANES = 4

        /** Panes per row, from top to bottom: 2 side by side, 3 as 2 + 1, 4 as 2 × 2. */
        fun gridRows(n: Int): List<Int> {
            if (n <= 0) return emptyList()
            val cols = (1..n).first { it * it >= n }
            val rows = (n + cols - 1) / cols
            val base = n / rows
            val extra = n % rows
            return (0 until rows).map { if (it < extra) base + 1 else base }
        }
    }
}
