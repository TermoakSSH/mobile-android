package com.termoak.app.data

/** How the copilot's context chips name what they carry (the iOS app's rules; JVM tests). */
object CopilotChipLabel {
    /** The command as the chip shows it: its first line, at most 32 characters ("…" at the end when cut). */
    fun command(command: String?): String? {
        val line = command?.lines()?.firstOrNull { it.isNotBlank() }?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return if (line.length > 32) line.take(31) + "…" else line
    }

    /** Lines of a selection (a final newline doesn't count). */
    fun lines(text: String): Int {
        val t = text.trimEnd('\n')
        return if (t.isEmpty()) 0 else t.split('\n').size
    }
}
