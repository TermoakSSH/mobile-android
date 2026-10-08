package com.termoak.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CopilotChipLabelTest {
    @Test
    fun commands() {
        assertEquals("make", CopilotChipLabel.command("make"))
        assertEquals("ls -l", CopilotChipLabel.command("\n  ls -l  \nmore"))
        assertNull(CopilotChipLabel.command(null))
        assertNull(CopilotChipLabel.command("  \n "))
        assertEquals("a".repeat(31) + "…", CopilotChipLabel.command("a".repeat(40)))
        assertEquals("a".repeat(32), CopilotChipLabel.command("a".repeat(32)))
    }

    @Test
    fun selectionLines() {
        assertEquals(1, CopilotChipLabel.lines("one"))
        assertEquals(2, CopilotChipLabel.lines("one\ntwo\n\n"))
        assertEquals(0, CopilotChipLabel.lines("\n"))
        assertEquals(3, CopilotChipLabel.lines("a\n\nb"))
    }
}
