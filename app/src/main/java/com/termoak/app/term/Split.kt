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
    /** Panes left out of the broadcast (they don't get what is typed elsewhere). */
    val excluded: Set<String> = emptySet(),
    /** Focus mode: the focused pane big on the left, the others in a column on the right. */
    val focusMode: Boolean = false,
) {
    val on: Boolean get() = panes.size >= 2

    /** The panes that get what is typed in [focused] while broadcasting (not itself, nor the excluded ones). */
    fun receivers(visible: List<String>, focused: String): List<String> =
        if (!broadcast) emptyList() else visible.filter { it != focused && it !in excluded }

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

    /** Directions to move the focus between panes (Ctrl+Alt+arrows). */
    enum class Direction { LEFT, RIGHT, UP, DOWN }

    companion object {
        /** Most panes in the grid (2 × 2). */
        const val MAX_PANES = 4

        /** Row and column of pane [ix] in the grid of [n] panes ([rows]: the panes per row). */
        private fun position(rows: List<Int>, ix: Int): Pair<Int, Int>? {
            var start = 0
            rows.forEachIndexed { row, count ->
                if (ix < start + count) return row to ix - start
                start += count
            }
            return null
        }

        /**
         * The pane reached from [from] moving in [dir] in a grid of [n] panes
         * with [rows] panes per row (the iOS app's and the desktop's rule):
         * wrapping at the edges; up and down pick the pane of the other row
         * whose centre is closest. In [focusMode] the arrows walk the list.
         */
        fun neighbor(n: Int, from: Int, dir: Direction, rows: List<Int> = gridRows(n), focusMode: Boolean = false): Int? {
            if (n < 2 || from !in 0 until n) return null
            if (focusMode) {
                return when (dir) {
                    Direction.LEFT, Direction.UP -> (from + n - 1) % n
                    Direction.RIGHT, Direction.DOWN -> (from + 1) % n
                }
            }
            val (row, col) = position(rows, from) ?: return null
            val starts = rows.runningFold(0) { acc, c -> acc + c }
            return when (dir) {
                Direction.LEFT, Direction.RIGHT -> {
                    val count = rows[row]
                    // Alone in its row: left and right walk the whole list.
                    if (count < 2) return if (dir == Direction.LEFT) (from + n - 1) % n else (from + 1) % n
                    starts[row] + if (dir == Direction.LEFT) (col + count - 1) % count else (col + 1) % count
                }
                Direction.UP, Direction.DOWN -> {
                    if (rows.size < 2) return null
                    val target = if (dir == Direction.UP) (row + rows.size - 1) % rows.size else (row + 1) % rows.size
                    val centre = (col + 0.5) / rows[row]
                    val count = rows[target]
                    starts[target] + minOf((centre * count).toInt(), count - 1)
                }
            }
        }

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
