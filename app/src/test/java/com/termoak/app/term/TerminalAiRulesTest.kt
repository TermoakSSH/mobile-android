package com.termoak.app.term

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The AI in the terminal: what the AI gets, erasing a `# request`, the prompt and the risk (the iOS app's rules). */
class TerminalAiRulesTest {
    @Test
    fun failedCommandForTheAi() {
        assertEquals("$ make\nerror: no rule\n(exit status 2)", TerminalAiRules.forAi("make", "error: no rule\n", 2))
        assertEquals("oops", TerminalAiRules.forAi(null, "oops", null))
        assertEquals("$ ls\n(exit status 1)", TerminalAiRules.forAi("ls", "  \n", 1))
    }

    @Test
    fun erasingTheRequestLine() {
        assertArrayEquals(byteArrayOf(0x7f, 0x7f, 0x7f), TerminalAiRules.eraseBytes("# a"))
        // One per character, not per UTF-16 unit or byte.
        assertEquals(3, TerminalAiRules.eraseBytes("añ😀").size)
    }

    @Test
    fun bracketedPaste() {
        assertTrue(TerminalAiRules.isBracketedPaste("\u001b[200~ls\u001b[201~".toByteArray()))
        assertFalse(TerminalAiRules.isBracketedPaste("ls\r".toByteArray()))
        assertFalse(TerminalAiRules.isBracketedPaste(byteArrayOf(0x1b)))
    }

    @Test
    fun promptInFrontOfTheLine() {
        assertEquals("user@web:~$ ", TerminalAiRules.prompt("user@web:~$ ls -l", "ls -l"))
        assertEquals("user@web:~$ ", TerminalAiRules.prompt("user@web:~$ ls   ", "ls"))
        // Not known, or not what the screen shows: the whole line.
        assertEquals("$ x", TerminalAiRules.prompt("$ x", null))
        assertEquals("$ x", TerminalAiRules.prompt("$ x", "y"))
    }

    @Test
    fun risk() {
        assertEquals(AiCommandRisk.READ, AiCommandRisk.of("read"))
        assertEquals(AiCommandRisk.DANGEROUS, AiCommandRisk.of("Dangerous"))
        assertEquals(AiCommandRisk.WRITE, AiCommandRisk.of("whatever"))
        assertTrue(AiCommandRisk.of("dangerous").needsConfirmation)
        assertFalse(AiCommandRisk.of("write").needsConfirmation)
    }
}
