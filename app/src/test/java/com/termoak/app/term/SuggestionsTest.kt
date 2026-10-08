package com.termoak.app.term

import com.termoak.ffi.CommandSuggestion
import com.termoak.ffi.ScreenLine
import com.termoak.ffi.ScreenRun
import com.termoak.ffi.SuggestionSource
import org.junit.Assert.assertEquals
import org.junit.Test

/** Command suggestions: the ones that still fit while typing, and the cursor's line on screen. */
class SuggestionsTest {
    private fun s(text: String, insert: String) = CommandSuggestion(text, insert, "", SuggestionSource.HISTORY)

    private fun line(vararg runs: Pair<Int, String>) = ScreenLine(
        runs.map { (col, text) -> ScreenRun(col.toUInt(), text.length.toUInt(), text, 0u, null, false, false, false, false, false) },
    )

    @Test
    fun narrowKeepsWhatStillFits() {
        val list = listOf(s("git status", "tatus"), s("git stash", "tash"), s("gist", "t"))
        assertEquals(listOf(s("git status", "tus"), s("git stash", "sh")), Suggestions.narrow(list, "git sta"))
        assertEquals(listOf(s("git status", "us")), Suggestions.narrow(list, "git stat"))
        // Typed all of it: nothing left to insert.
        assertEquals(emptyList<CommandSuggestion>(), Suggestions.narrow(list, "git status"))
        assertEquals(emptyList<CommandSuggestion>(), Suggestions.narrow(list, "ls"))
    }

    @Test
    fun theCursorsLine() {
        val cols = 10
        val lines = listOf(
            line(0 to "first"),
            line(0 to "\$ echo abc"), // full to the last column: wrapped
            line(0 to "def  "),
        )
        assertEquals("\$ echo abcdef", Suggestions.cursorLine(lines, 2, cols))
        assertEquals("first", Suggestions.cursorLine(lines, 0, cols))
        assertEquals("", Suggestions.cursorLine(lines, 5, cols))
        // A wide character takes two cells.
        assertEquals("a漢b", Suggestions.cursorLine(listOf(ScreenLine(listOf(
            ScreenRun(0u, 1u, "a", 0u, null, false, false, false, false, false),
            ScreenRun(1u, 2u, "漢", 0u, null, false, false, false, false, true),
            ScreenRun(3u, 1u, "b", 0u, null, false, false, false, false, false),
        ))), 0, cols))
    }
}
