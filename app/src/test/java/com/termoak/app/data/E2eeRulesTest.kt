package com.termoak.app.data

import com.termoak.ffi.E2eeRecoveryKeyCheck
import com.termoak.ffi.VaultE2eeInfo
import com.termoak.ffi.VaultInfo
import com.termoak.ffi.VaultKind
import com.termoak.ffi.VaultRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The pure rules of the E2EE screens (0.7 phase 5c), as the desktop's tests. */
class E2eeRulesTest {
    /** A made-up phrase (never a real account's). */
    private val phrase = "abandon math mimic master filter design carbon crystal rookie group knife wrap absurd much snack melt grid rough chapter fever rubber humble room trophy"
    private val words = listOf("abandon", "ability", "able", "absurd", "carbon", "chapter", "crystal", "design", "fever", "filter", "grid", "group", "humble", "knife", "master", "math", "melt", "mimic", "much", "rookie", "room", "rough", "rubber", "snack", "trophy", "wrap").sorted()

    @Test
    fun numberedGridAndDocument() {
        val n = E2eeRules.numberedWords(phrase)
        assertEquals(24, n.size)
        assertEquals(1 to "abandon", n.first())
        assertEquals(24 to "trophy", n.last())
        val doc = E2eeRules.document(phrase, "Termoak recovery phrase", "Account: ana@example.com on https://t.example", "Made on 2026-10-27", "Keep it offline.")
        assertTrue(doc, doc.contains(" 1. abandon"))
        assertTrue(doc.contains("24. trophy"))
        assertEquals(6, E2eeRules.documentLines(phrase).size)
        val page = E2eeRules.printPage(phrase, "T", "a<b>@x", "d", "k")
        assertTrue(page.contains("a&lt;b&gt;@x"))
        assertTrue(page.contains("<span>24</span> trophy"))
    }

    @Test
    fun confirmationWords() {
        for (seed in listOf(1L, 2L, 99L, Long.MAX_VALUE, -1L, 0xdeadbeefL)) {
            val p = E2eeRules.confirmPositions(seed)
            assertEquals(3, p.size)
            assertTrue(p[0] < p[1] && p[1] < p[2] && p[2] < 24)
        }
        // The desktop's xorshift64* gives the same positions for the same seed (so the rule is shared).
        assertEquals(E2eeRules.confirmPositions(42L), E2eeRules.confirmPositions(42L))
        val pos = listOf(0, 5, 23)
        assertTrue(E2eeRules.confirmMatches(phrase, pos, listOf("ABANDON", " design ", "trophy")))
        assertTrue(E2eeRules.confirmMatches(phrase, pos, listOf("aban", "desi", "trop")))
        assertFalse(E2eeRules.confirmMatches(phrase, pos, listOf("aba", "desi", "trop")))
        assertFalse(E2eeRules.confirmMatches(phrase, pos, listOf("math", "design", "trophy")))
        assertFalse(E2eeRules.confirmMatches(phrase, pos, listOf("abandon", "design")))
    }

    private fun check(valid: Boolean, reason: String = "", words: Int = 0, word: Int = 0): (String) -> E2eeRecoveryKeyCheck =
        { E2eeRecoveryKeyCheck(valid, reason, words.toUInt(), word.toUInt()) }

    @Test
    fun liveFeedback() {
        assertEquals(E2eeRules.Feedback.Empty, E2eeRules.feedback("  ", check(false, "word_count"), words))
        assertEquals(E2eeRules.Feedback.Valid, E2eeRules.feedback(phrase, check(true), words))
        // Typing: fine so far, the last word may be unfinished.
        assertEquals(E2eeRules.Feedback.Typing(3), E2eeRules.feedback("abandon math mi", check(false, "word_count", 3), words))
        // An unknown word is said at once, with its number (1-based).
        assertEquals(E2eeRules.Feedback.Problem("unknown_word", 2, 3), E2eeRules.feedback("abandon qqqq math", check(false, "word_count", 3), words))
        // A finished word that starts no word.
        assertEquals(2, E2eeRules.firstUnknownWord("abandon zz ", words))
        assertNull(E2eeRules.firstUnknownWord("abandon ma", words))
        // Four-letter prefixes count as words.
        assertNull(E2eeRules.firstUnknownWord("aban math mimi ", words))
        // 24 words: the core's reason.
        assertEquals("checksum", (E2eeRules.feedback(phrase, check(false, "checksum", 24), words) as E2eeRules.Feedback.Problem).reason)
        assertEquals("old_format", (E2eeRules.feedback("A".repeat(56), check(false, "old_format"), words) as E2eeRules.Feedback.Problem).reason)
        // Numbering, capitals and line breaks are not words.
        assertEquals(listOf("abandon", "math"), E2eeRules.tokens("1) ABANDON\n2) Math."))
    }

    @Test
    fun suggestions() {
        assertEquals(listOf("abandon", "ability", "able", "absurd"), E2eeRules.suggestions("ab", words))
        assertEquals(listOf("room", "rough", "rookie").sorted(), E2eeRules.suggestions("knife ro", words).sorted())
        assertTrue(E2eeRules.suggestions("knife ", words).isEmpty())
        assertTrue(E2eeRules.suggestions("knife", words).isEmpty())
        assertEquals("abandon math ", E2eeRules.complete("abandon ma", "math"))
        assertEquals("1) abandon ", E2eeRules.complete("1) aba", "abandon"))
    }

    @Test
    fun passphraseStrength() {
        assertEquals(E2eeRules.Strength.TOO_SHORT, E2eeRules.strength("short"))
        assertEquals(E2eeRules.Strength.WEAK, E2eeRules.strength("aaaaaaaaaaaa"))
        assertTrue(E2eeRules.strength("correct horse battery staple") >= E2eeRules.Strength.GOOD)
        assertTrue(E2eeRules.strength("Tr0ub4dor&3xyz!Q") >= E2eeRules.Strength.GOOD)
        assertEquals("1:10", E2eeRules.pairingLeft(70_000, 0))
        assertEquals("0:00", E2eeRules.pairingLeft(0, 5_000))
    }

    private fun vault(e2ee: VaultE2eeInfo?, role: VaultRole = VaultRole.EDITOR, id: String = "v1") = VaultInfo(
        id, "a1", "Ops", "", VaultKind.TEAM, role, "t1", "Team", "Team", null, null, 2L, 3L, true, null, null, e2ee,
    )

    private fun e(version: UInt = 2u, may: Boolean = true, wrap: Boolean = true, rekey: Boolean = false, pending: UInt = 0u) =
        VaultE2eeInfo(version, may, wrap, rekey, pending, 0L)

    @Test
    fun badgesStatesAndRotation() {
        assertEquals(E2eeRules.Badge.NONE, E2eeRules.badge(vault(null), unlocked = true))
        assertEquals(E2eeRules.Badge.E2EE, E2eeRules.badge(vault(e()), unlocked = true))
        assertEquals(E2eeRules.Badge.LOCKED, E2eeRules.badge(vault(e()), unlocked = false))
        assertEquals(E2eeRules.Badge.LOCKED, E2eeRules.badge(vault(e(wrap = false)), unlocked = true))
        // Use only / Connect only: no key for them, nothing "locked".
        assertEquals(E2eeRules.Badge.E2EE, E2eeRules.badge(vault(e(may = false, wrap = false), VaultRole.USE_ONLY), unlocked = false))

        assertEquals(E2eeRules.KeyState.OFF, E2eeRules.keyState(vault(null), setUp = true, unlocked = true))
        assertEquals(E2eeRules.KeyState.NO_KEY_ROLE, E2eeRules.keyState(vault(e(may = false)), setUp = true, unlocked = true))
        assertEquals(E2eeRules.KeyState.SET_UP_FIRST, E2eeRules.keyState(vault(e(wrap = false)), setUp = false, unlocked = false))
        assertEquals(E2eeRules.KeyState.WAITING, E2eeRules.keyState(vault(e(wrap = false)), setUp = true, unlocked = true))
        assertEquals(E2eeRules.KeyState.UNLOCK_FIRST, E2eeRules.keyState(vault(e()), setUp = true, unlocked = false))
        assertEquals(E2eeRules.KeyState.HOLDER, E2eeRules.keyState(vault(e()), setUp = true, unlocked = true))

        val flagged = vault(e(rekey = true), id = "v2")
        val notMine = vault(e(rekey = true, wrap = false), id = "v3")
        assertEquals(listOf("v2"), E2eeRules.toRotate(listOf(vault(e()), flagged, notMine), unlocked = true).map { it.id })
        assertTrue(E2eeRules.toRotate(listOf(flagged), unlocked = false).isEmpty())
        val waiting = vault(e(pending = 2u), id = "v4")
        assertEquals(listOf("v4"), E2eeRules.pendingVaults(listOf(vault(e()), waiting), unlocked = true).map { it.id })
        assertTrue(E2eeRules.canConfirm(waiting, unlocked = true))
        assertFalse(E2eeRules.canConfirm(waiting, unlocked = false))
    }
}
