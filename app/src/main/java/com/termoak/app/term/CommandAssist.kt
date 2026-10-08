package com.termoak.app.term

import com.termoak.ffi.CommandHistoryItem
import com.termoak.ffi.CommandSuggestion
import com.termoak.ffi.ScreenLine
import com.termoak.ffi.TermoakCore

/**
 * Where the ways to continue the command being typed are suggested
 * (Settings → Terminal → Command suggestions; the iOS app's setting and keys).
 */
enum class SuggestionMode(val key: String) {
    /** Like on the desktop: dimmed text after the cursor and a list to pick from. */
    CURSOR("cursor"),
    /** In the key bar, at the start. */
    BAR("barra"),
    OFF("desactivadas");

    companion object {
        fun of(key: String?): SuggestionMode = entries.firstOrNull { it.key == key } ?: CURSOR
    }
}

/**
 * The engine's autocompletion and command history for the terminals (set
 * on each one by [Sessions]). The history only lives on this device.
 */
class CommandAssist(private val core: TermoakCore, private val modeOf: () -> SuggestionMode) {
    val mode: SuggestionMode get() = modeOf()

    /** Ways to continue [line] on [hostId] (whose system is [os], for the package manager). */
    fun complete(hostId: String?, os: String?, line: String): List<CommandSuggestion> =
        runCatching { core.completeCommand(hostId, os, line, LIMIT.toUInt()) }.getOrDefault(emptyList())

    /** Saves a command run on [hostId] (not the ones that look like they hold secrets). */
    fun record(hostId: String, command: String) {
        runCatching { core.recordCommand(hostId, command) }
    }

    fun history(hostId: String?, query: String): List<CommandHistoryItem> =
        runCatching { core.commandHistory(hostId, query, 200u) }.getOrDefault(emptyList())

    fun clear(hostId: String?) {
        runCatching { core.clearCommandHistory(hostId) }
    }

    companion object {
        const val LIMIT = 6
    }
}

/** The pure parts of the suggestions (JVM tests). */
object Suggestions {
    /**
     * While new suggestions come, the ones that still fit what is typed now
     * ([typed]) stay, with the rest to insert shortened (the iOS app's rule).
     */
    fun narrow(list: List<CommandSuggestion>, typed: String): List<CommandSuggestion> = list.mapNotNull { s ->
        if (s.text.startsWith(typed) && s.text.length > typed.length) s.copy(insert = s.text.substring(typed.length)) else null
    }

    /** The cells of a row of the screen (a wide character takes its cell and an empty one). */
    fun cells(line: ScreenLine?, cols: Int): Array<String> {
        val cells = Array(cols) { " " }
        line?.runs?.forEach { run ->
            val c = run.col.toInt()
            if (run.wide) {
                if (c < cols) cells[c] = run.text
                if (c + 1 < cols) cells[c + 1] = ""
            } else {
                var i = 0
                var col = c
                while (i < run.text.length && col < cols) {
                    val next = run.text.offsetByCodePoints(i, 1)
                    cells[col] = run.text.substring(i, next)
                    i = next
                    col++
                }
            }
        }
        return cells
    }

    /**
     * Text of the cursor's line on screen, joining the rows above it that
     * are full to the last column (a long line wrapped by the terminal), and
     * without trailing spaces.
     */
    fun cursorLine(lines: List<ScreenLine>, row: Int, cols: Int): String {
        if (row !in lines.indices || cols <= 0) return ""
        var text = cells(lines[row], cols).joinToString("").trimEnd()
        var r = row
        while (r > 0) {
            val above = cells(lines[r - 1], cols)
            if (above.last().isBlank()) break
            text = above.joinToString("") + text
            r--
        }
        return text
    }
}
