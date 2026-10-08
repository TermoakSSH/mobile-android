package com.termoak.app.data

/**
 * `~/.ssh/authorized_keys` for "Install on a host" (like ssh-copy-id), as
 * the iOS app does it: a key counts as there when a line has the same type
 * and base64, whatever its options or comment. Pure, for the JVM tests.
 */
object AuthorizedKeys {
    /** Where OpenSSH reads them, relative to the home folder. */
    const val FOLDER = ".ssh"
    const val FILE = ".ssh/authorized_keys"

    /** The largest file read (1 MiB). */
    const val MAX_BYTES = 1L shl 20

    /** `type base64` of a public key line, without the options before it or the comment after. */
    fun identity(line: String): String? {
        val words = line.split(' ', '\t').filter { it.isNotEmpty() }
        // The key type is the first word that looks like one (options such as from="…" or no-pty can come first).
        val i = words.indexOfFirst { it.startsWith("ssh-") || it.startsWith("ecdsa-sha2-") || it.startsWith("sk-") }
        if (i < 0 || i + 1 >= words.size) return null
        return "${words[i]} ${words[i + 1]}"
    }

    /** The file already has this key (any comment or options). */
    fun contains(existing: String, publicKey: String): Boolean {
        val wanted = identity(publicKey) ?: return false
        return existing.lines().any { raw ->
            val l = raw.trim()
            !l.startsWith("#") && identity(l) == wanted
        }
    }

    /** The file with the key added at the end, on its own line. */
    fun appending(existing: String, publicKey: String): String {
        val key = publicKey.trim()
        if (existing.isEmpty()) return key + "\n"
        return existing + (if (existing.endsWith("\n")) "" else "\n") + key + "\n"
    }
}
