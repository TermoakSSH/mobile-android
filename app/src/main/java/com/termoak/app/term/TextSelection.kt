package com.termoak.app.term

/**
 * Selecting text with a finger on the visible screen (double tap: a word,
 * triple tap: a line; held: the word under the finger), with the cells of a
 * row ([Suggestions.cells]). Pure, for the JVM tests.
 */
object TextSelection {
    /** Characters that join a word besides letters and digits: paths, URLs, options, addresses. */
    private const val WORD_CHARS = "-_./~:@%+=?&#"

    private fun isWord(cell: String): Boolean =
        cell.isNotEmpty() && cell.isNotBlank() && cell.all { it.isLetterOrDigit() || it in WORD_CHARS || it.code > 0x7f }

    /**
     * The columns of the word at [col] (a wide character's second, empty
     * cell belongs to it); `null` on a blank cell.
     */
    fun wordAt(cells: Array<String>, col: Int): IntRange? {
        if (col !in cells.indices) return null
        // An empty cell is the second half of a wide character: the character before it.
        var c = col
        if (cells[c].isEmpty() && c > 0) c--
        val half = if (c + 1 < cells.size && cells[c + 1].isEmpty()) 1 else 0
        if (cells[c].isBlank()) return null
        // A lone symbol (a bracket, a quote...) selects itself.
        if (!isWord(cells[c])) return c..c + half
        var start = c
        while (true) {
            start = when {
                start >= 1 && isWord(cells[start - 1]) -> start - 1
                start >= 2 && cells[start - 1].isEmpty() && isWord(cells[start - 2]) -> start - 2
                else -> break
            }
        }
        var end = c + half
        while (end + 1 < cells.size && isWord(cells[end + 1])) {
            end++
            if (end + 1 < cells.size && cells[end + 1].isEmpty()) end++
        }
        return start..end
    }

    /** The row's text without trailing spaces: its first and last columns (`null` for an empty row). */
    fun lineAt(cells: Array<String>): IntRange? {
        val last = cells.indexOfLast { it.isNotBlank() }
        if (last < 0) return null
        val end = if (last + 1 < cells.size && cells[last + 1].isEmpty()) last + 1 else last
        return 0..end
    }

    /** Selection of the whole screen: from the first cell to the last. */
    fun all(cols: Int, rows: Int): Pair<Pair<Int, Int>, Pair<Int, Int>> = (0 to 0) to ((cols - 1).coerceAtLeast(0) to (rows - 1).coerceAtLeast(0))
}
