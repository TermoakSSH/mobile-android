package com.termoak.app.files

/**
 * What the in-app preview of a remote file shows (like iOS's QuickLook, for
 * the usual kinds): text (it can also be edited and saved back), an image
 * or a PDF. Pure, for the JVM tests.
 */
enum class PreviewKind {
    TEXT, IMAGE, PDF;

    companion object {
        /** Up to this size a file is previewed (it is downloaded first). */
        const val MAX_BYTES = 20L * 1024 * 1024
        /** Text larger than this is shown read-only, and only its start. */
        const val MAX_TEXT_BYTES = 1L * 1024 * 1024

        private val TextNames = setOf(
            "dockerfile", "makefile", "vagrantfile", "gemfile", "procfile", "readme", "license", "changelog", "authorized_keys",
            "known_hosts", "hosts", "fstab", "crontab", "config", "hostname", "passwd", "group", "sudoers",
        )
        private val TextExtensions = setOf(
            "log", "txt", "md", "conf", "cfg", "ini", "yml", "yaml", "toml", "env", "sh", "bash", "zsh", "fish", "py", "rb",
            "js", "mjs", "ts", "go", "rs", "c", "h", "cc", "cpp", "hpp", "kt", "kts", "java", "php", "sql", "csv", "tsv",
            "service", "timer", "socket", "properties", "list", "json", "xml", "html", "htm", "css", "scss", "lua", "pl",
            "swift", "gradle", "lock", "pem", "pub", "crt", "csr", "key", "rules", "nginx", "vim", "gitignore", "dockerignore",
            "editorconfig", "tf", "hcl", "rst", "tex", "diff", "patch", "out", "err", "sample", "dist", "bak",
        )
        private val ImageExtensions = setOf("png", "jpg", "jpeg", "gif", "webp", "bmp", "heic", "heif", "ico")

        /** How [name] can be previewed (by its name or MIME type); `null`: not here (open it with another app). */
        fun of(name: String, mime: String?, size: Long): PreviewKind? {
            if (size > MAX_BYTES) return null
            val lower = name.lowercase()
            val ext = lower.substringAfterLast('.', "")
            return when {
                ext in ImageExtensions || (mime?.startsWith("image/") == true && mime != "image/svg+xml") -> IMAGE
                ext == "pdf" || mime == "application/pdf" -> PDF
                ext in TextExtensions || mime?.startsWith("text/") == true || mime == "application/json" ||
                    mime == "application/xml" || mime == "application/javascript" -> TEXT
                // Dotfiles (.bashrc, .profile) and the usual files without an extension.
                lower.startsWith('.') && !lower.substring(1).contains('.') -> TEXT
                lower in TextNames || (ext.isEmpty() && size <= 64 * 1024) -> TEXT
                else -> null
            }
        }

        /** Bytes that aren't text: a NUL in the first 8 KB. */
        fun looksBinary(start: ByteArray): Boolean = start.take(8192).any { it == 0.toByte() }
    }
}
