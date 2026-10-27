package com.termoak.app.data

import com.termoak.ffi.E2eeRecoveryKeyCheck
import com.termoak.ffi.VaultInfo

/**
 * The pure rules of the E2EE screens (0.7 phase 5c, as the desktop's
 * `e2ee.rs`): the recovery phrase in a numbered grid, the three words asked
 * back, live feedback while a phrase is typed (the core's
 * `e2ee_recovery_key_check` plus the word list), word suggestions,
 * passphrase strength, the pairing countdown, vault badges and when this
 * device rotates a vault key by itself.
 */
object E2eeRules {
    const val WORDS = E2eeTexts.RECOVERY_WORDS

    /** The shortest E2EE passphrase the core accepts. */
    const val MIN_PASSPHRASE = 8

    /** The words of a phrase, numbered from 1 ("1 abandon"). */
    fun numberedWords(phrase: String): List<Pair<Int, String>> =
        phrase.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.mapIndexed { i, w -> (i + 1) to w }

    /** Three different positions (0-based, sorted) to confirm, from a seed (xorshift64*, as the desktop). */
    fun confirmPositions(seed: Long): List<Int> {
        val out = mutableListOf<Int>()
        var x = seed or 1L
        while (out.size < 3) {
            x = x xor (x ushr 12)
            x = x xor (x shl 25)
            x = x xor (x ushr 27)
            val p = java.lang.Long.remainderUnsigned(x * 0x2545F4914F6CDD1DL, WORDS.toLong()).toInt()
            if (p !in out) out += p
        }
        return out.sorted()
    }

    /** One typed word matches a word of the phrase: case and spaces do not matter, its first four letters are enough. */
    fun wordMatches(word: String, typed: String): Boolean {
        val t = typed.trim().lowercase()
        return word == t || (t.length >= 4 && word.startsWith(t))
    }

    /** Whether the typed words match the phrase at those positions. */
    fun confirmMatches(phrase: String, positions: List<Int>, typed: List<String>): Boolean {
        val words = phrase.trim().split(Regex("\\s+"))
        return positions.size == typed.size && positions.zip(typed).all { (p, t) -> words.getOrNull(p)?.let { wordMatches(it, t) } == true }
    }

    /** The typed tokens of a phrase (numbering, punctuation and line breaks ignored). */
    fun tokens(text: String): List<String> = text.lowercase().split(Regex("[^\\p{L}]+")).filter { it.isNotEmpty() }

    /** What to say while a recovery phrase is typed. */
    sealed interface Feedback {
        data object Empty : Feedback
        data object Valid : Feedback
        /** Words typed so far (nothing wrong yet). */
        data class Typing(val words: Int) : Feedback
        /** Refused: the core's reason, the word (1-based) and how many were typed. */
        data class Problem(val reason: String, val word: Int, val words: Int) : Feedback
    }

    /**
     * Live feedback: [check] is the core's `e2ee_recovery_key_check`,
     * [wordlist] its 2048 words (sorted). While fewer than 24 words are there
     * it only complains about a word that is no recovery word (the last one
     * may still be being typed).
     */
    fun feedback(text: String, check: (String) -> E2eeRecoveryKeyCheck, wordlist: List<String>): Feedback {
        val toks = tokens(text)
        if (toks.isEmpty()) return Feedback.Empty
        val c = check(text)
        if (c.valid) return Feedback.Valid
        if (c.reason == "word_count" && toks.size < WORDS) {
            val bad = firstUnknownWord(text, wordlist)
            return if (bad != null) Feedback.Problem("unknown_word", bad, toks.size) else Feedback.Typing(toks.size)
        }
        return Feedback.Problem(c.reason, c.word.toInt(), c.words.toInt().takeIf { it > 0 } ?: toks.size)
    }

    /** The first typed word (1-based) that is no recovery word nor the start of exactly one (the last may be unfinished). */
    fun firstUnknownWord(text: String, wordlist: List<String>): Int? {
        val toks = tokens(text)
        val endsOpen = text.lastOrNull()?.isLetter() == true
        toks.forEachIndexed { i, t ->
            val known = wordlist.binarySearch(t) >= 0 || (t.length >= 4 && wordlist.count { it.startsWith(t) } == 1)
            if (known) return@forEachIndexed
            val last = i == toks.lastIndex
            if (last && endsOpen && wordlist.any { it.startsWith(t) }) return@forEachIndexed
            return i + 1
        }
        return null
    }

    /** Suggestions for the word being typed (the last token, while the text does not end in a space). */
    fun suggestions(text: String, wordlist: List<String>, max: Int = 4): List<String> {
        if (text.isEmpty() || !text.last().isLetter()) return emptyList()
        val t = tokens(text).lastOrNull() ?: return emptyList()
        if (t.length < 1) return emptyList()
        val list = wordlist.filter { it.startsWith(t) }
        return if (list.size == 1 && list[0] == t) emptyList() else list.take(max)
    }

    /** The text with its last (unfinished) word replaced by [word] and a space after it. */
    fun complete(text: String, word: String): String {
        val i = text.indexOfLast { !it.isLetter() }
        return text.substring(0, i + 1) + word + " "
    }

    /** How strong an E2EE passphrase looks (a hint, not a rule: the core only asks for 8 characters). */
    enum class Strength { TOO_SHORT, WEAK, FAIR, GOOD, STRONG }

    fun strength(p: String): Strength {
        val len = p.codePointCount(0, p.length)
        if (len < MIN_PASSPHRASE) return Strength.TOO_SHORT
        val classes = listOf(
            p.any { it.isLowerCase() }, p.any { it.isUpperCase() }, p.any { it in '0'..'9' }, p.any { !it.isLetterOrDigit() },
        ).count { it }
        val words = p.split(Regex("\\s+")).count { it.length >= 3 }
        if (p.toSet().size <= 3) return Strength.WEAK
        return when (len / 4 + classes + words.coerceAtMost(4)) {
            in 0..3 -> Strength.WEAK
            in 4..5 -> Strength.FAIR
            in 6..8 -> Strength.GOOD
            else -> Strength.STRONG
        }
    }

    /** "9:12" left of a pairing (ms times). */
    fun pairingLeft(expiresAt: Long, now: Long): String {
        val secs = ((expiresAt - now) / 1000).coerceAtLeast(0)
        return "%d:%02d".format(secs / 60, secs % 60)
    }

    /** The badge of a vault. */
    enum class Badge { NONE, E2EE, LOCKED }

    /**
     * End-to-end encrypted vaults show a badge; it says "locked" when this
     * device can't open its secrets yet (not unlocked here, or no key of the
     * vault for you yet). Members whose role gets no key are not "locked".
     */
    fun badge(v: VaultInfo, unlocked: Boolean): Badge {
        val e = v.e2ee ?: return Badge.NONE
        return if (e.mayHoldKey && (!unlocked || !e.hasWrap)) Badge.LOCKED else Badge.E2EE
    }

    /** What the vault's Encryption page says about you and its key. */
    enum class KeyState { OFF, HOLDER, NO_KEY_ROLE, SET_UP_FIRST, UNLOCK_FIRST, WAITING }

    fun keyState(v: VaultInfo, setUp: Boolean, unlocked: Boolean): KeyState {
        val e = v.e2ee ?: return KeyState.OFF
        return when {
            !e.mayHoldKey -> KeyState.NO_KEY_ROLE
            !setUp -> KeyState.SET_UP_FIRST
            !e.hasWrap -> KeyState.WAITING
            !unlocked -> KeyState.UNLOCK_FIRST
            else -> KeyState.HOLDER
        }
    }

    /** Vaults this unlocked device should rotate now: flagged `rekey_needed` and holding the key. */
    fun toRotate(vaults: List<VaultInfo>, unlocked: Boolean): List<VaultInfo> =
        if (!unlocked) emptyList() else vaults.filter { it.e2ee?.rekeyNeeded == true && it.e2ee?.hasWrap == true }

    /** Vaults where members wait for a key holder (this device holds the key). */
    fun pendingVaults(vaults: List<VaultInfo>, unlocked: Boolean): List<VaultInfo> =
        if (!unlocked) emptyList() else vaults.filter { (it.e2ee?.pendingMembers ?: 0u) > 0u && it.e2ee?.hasWrap == true }

    /** Who confirms a pending member: a key holder of that vault. */
    fun canConfirm(v: VaultInfo, unlocked: Boolean): Boolean = unlocked && v.e2ee?.hasWrap == true

    /** The recovery document's grid: four words per line ("  1. abandon   2. ability …"). */
    fun documentLines(phrase: String): List<String> =
        numberedWords(phrase).chunked(4).map { row -> row.joinToString("  ") { (n, w) -> "%2d. %-10s".format(n, w) }.trimEnd() }

    /** The saved recovery document: title, who it is for, the date, the grid and what to do with it. */
    fun document(phrase: String, title: String, account: String, date: String, keep: String): String =
        buildString {
            append(title).append("\n\n").append(account).append('\n').append(date).append("\n\n")
            documentLines(phrase).forEach { append(it).append('\n') }
            append('\n').append(keep).append('\n')
        }

    /** The same as a page to print. */
    fun printPage(phrase: String, title: String, account: String, date: String, keep: String): String {
        fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
        val cells = numberedWords(phrase).joinToString("") { (n, w) -> "<li><span>$n</span> ${esc(w)}</li>" }
        return "<!doctype html><meta charset=utf-8><title>${esc(title)}</title>" +
            "<style>body{font-family:sans-serif;margin:2em;color:#111}" +
            "ol{list-style:none;padding:0;display:grid;grid-template-columns:repeat(4,1fr);gap:.6em 1.2em;font:1.1em monospace}" +
            "li span{display:inline-block;width:2em;color:#777;text-align:right;margin-right:.4em}p{line-height:1.5}</style>" +
            "<h1>${esc(title)}</h1><p>${esc(account)}<br>${esc(date)}</p><ol>$cells</ol><p>${esc(keep)}</p>"
    }
}
