package com.termoak.app.term

/**
 * When a paste asks first (the desktop's terminal/paste.rs): each line of a
 * paste runs as a command when it reaches the shell, unless the program
 * turned on bracketed paste (then nothing runs until Enter is pressed).
 */
object Paste {
    /** Lines of a paste, not counting a final line break (a copied line usually ends with one). */
    fun lineCount(text: String): Int {
        val t = text.replace("\r\n", "\n").replace('\r', '\n').trimEnd('\n')
        return if (t.isEmpty()) 0 else t.split('\n').size
    }

    /** Only with the option on, for more than one line and without bracketed paste. */
    fun needsConfirmation(text: String, confirm: Boolean, bracketed: Boolean): Boolean =
        confirm && !bracketed && lineCount(text) > 1

    /** First lines of a paste for the confirmation dialog (long lines cut). */
    fun preview(text: String, maxLines: Int = 8): String {
        val lines = text.trimEnd('\r', '\n').split('\n').map { it.trimEnd('\r') }
        val out = lines.take(maxLines).map { if (it.length > 120) it.take(119) + "…" else it }.toMutableList()
        if (lines.size > maxLines) out += "…"
        return out.joinToString("\n")
    }
}
