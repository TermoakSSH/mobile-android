package com.termoak.app.term

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Selecting with a finger: the word under it, the line, everything. */
class TextSelectionTest {
    /** The cells of a row of [cols] (a wide character followed by its empty half when given as `漢`). */
    private fun row(text: String, cols: Int = 60): Array<String> {
        val cells = Array(cols) { " " }
        var c = 0
        text.forEach { ch ->
            cells[c++] = ch.toString()
            if (ch.code in 0x4E00..0x9FFF) cells[c++] = ""
        }
        return cells
    }

    @Test
    fun words() {
        val r = row("ls -la /var/log/nginx; echo ok")
        assertEquals(0..1, TextSelection.wordAt(r, 1))
        assertEquals(3..5, TextSelection.wordAt(r, 4))
        // Paths stay whole; the semicolon doesn't join.
        assertEquals(7..20, TextSelection.wordAt(r, 12))
        assertEquals(21..21, TextSelection.wordAt(r, 21))
        assertNull(TextSelection.wordAt(r, 2))
        assertNull(TextSelection.wordAt(r, 99))
        val url = row("see https://termoak.com/join/abc?x=1 now")
        assertEquals(4..35, TextSelection.wordAt(url, 10))
        val mail = row("to: ana@example.com.")
        assertEquals(4..19, TextSelection.wordAt(mail, 6))
    }

    @Test
    fun wideCharacters() {
        val r = row("a 漢字b c")
        // 漢 takes 2..3, 字 4..5, then b at 6.
        assertEquals(2..6, TextSelection.wordAt(r, 3))
        assertEquals(2..6, TextSelection.wordAt(r, 2))
        assertEquals(2..6, TextSelection.wordAt(r, 6))
    }

    @Test
    fun lines() {
        assertEquals(0..6, TextSelection.lineAt(row("  hello")))
        assertNull(TextSelection.lineAt(row("")))
        assertEquals(0..3, TextSelection.lineAt(row("ab漢")))
        assertEquals((0 to 0) to (79 to 23), TextSelection.all(80, 24))
    }
}
