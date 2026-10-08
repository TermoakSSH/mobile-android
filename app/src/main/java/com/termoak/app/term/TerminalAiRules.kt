package com.termoak.app.term

/**
 * Pure rules of the AI in the terminal (no engine, no UI), the iOS app's and
 * the desktop's: what is sent about a command that failed, how a `# request`
 * line is erased before typing the AI's command, and how the AI's risk reads.
 * JVM tests.
 */
object TerminalAiRules {
    /**
     * A command and the end of its output as the AI gets it: `$ command`,
     * the output and the exit status. Redact it before sending (`redactSecrets`).
     */
    fun forAi(command: String?, output: String, exitCode: Int?): String {
        val s = StringBuilder()
        if (command != null) s.append("$ ").append(command).append('\n')
        if (output.isNotBlank()) s.append(output.trimEnd('\n', '\r')).append('\n')
        if (exitCode != null) s.append("(exit status ").append(exitCode).append(")\n")
        return s.toString().trimEnd()
    }

    /** Backspaces (DEL) that erase a typed line, one per character, as the shells' line editors delete them. */
    fun eraseBytes(line: String): ByteArray = ByteArray(line.codePointCount(0, line.length)) { 0x7f }

    /** What is sent is a bracketed paste (the shell doesn't run its lines). */
    fun isBracketedPaste(data: ByteArray): Boolean {
        val start = "\u001b[200~".toByteArray()
        return data.size >= start.size && start.indices.all { data[it] == start[it] }
    }

    /** The prompt in front of the typed line: the cursor's line on screen without what was typed (all of it when that isn't known). */
    fun prompt(screenLine: String, typed: String?): String {
        if (typed.isNullOrEmpty()) return screenLine
        var line = screenLine
        while (line.endsWith(" ") && !typed.endsWith(" ")) line = line.dropLast(1)
        return if (line.endsWith(typed)) line.dropLast(typed.length) else screenLine
    }
}

/** How risky the AI says its proposed command is (`AiCommandSuggestion.risk`). */
enum class AiCommandRisk {
    READ, WRITE, DANGEROUS;

    /** Asks before typing it. */
    val needsConfirmation: Boolean get() = this == DANGEROUS

    companion object {
        /** Unknown values count as changing the system, like the desktop. */
        fun of(raw: String?): AiCommandRisk = when (raw?.trim()?.lowercase()) {
            "read" -> READ
            "dangerous" -> DANGEROUS
            else -> WRITE
        }
    }
}
