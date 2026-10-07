package com.termoak.app.files

/**
 * Remote (POSIX) paths and folder listings of the file browser, without
 * Android or the engine: what the browser shows, in which order, and where
 * each name goes. Tested on the JVM (RemotePathsTest).
 */
object RemotePaths {
    /** Parent folder (`/` for `/` and for a top-level entry). */
    fun parent(path: String): String {
        val p = path.trimEnd('/')
        if (p.isEmpty()) return "/"
        val i = p.lastIndexOf('/')
        return if (i <= 0) "/" else p.substring(0, i)
    }

    /** [name] inside the folder [dir]. */
    fun child(dir: String, name: String): String =
        if (dir.endsWith('/')) dir + name else "$dir/$name"

    /** Last part of a path (the path itself for `/`). */
    fun name(path: String): String {
        val p = path.trimEnd('/')
        return if (p.isEmpty()) "/" else p.substringAfterLast('/')
    }

    /**
     * Breadcrumb: (label, path) for `/` and each folder down to [path].
     * Relative or empty paths only give `/`.
     */
    fun crumbs(path: String): List<Pair<String, String>> {
        val out = mutableListOf("/" to "/")
        var acc = ""
        for (part in path.split('/').filter { it.isNotEmpty() }) {
            acc += "/$part"
            out += part to acc
        }
        return out
    }

    /** A name that can't be used for a new folder or a rename: empty, `.`/`..`, with `/` or a NUL byte. */
    fun invalidName(name: String): Boolean {
        val n = name.trim()
        return n.isEmpty() || n == "." || n == ".." || '/' in n || '\u0000' in n
    }

    /** Permissions as octal text (`644`, `4755`), at most the 12 permission bits. */
    fun octal(mode: Int): String = Integer.toOctalString(mode and 0xFFF)

    /** `rwxr-xr-x` of the 9 permission bits. */
    fun symbolic(mode: Int): String = buildString {
        for (shift in intArrayOf(6, 3, 0)) {
            val bits = (mode shr shift) and 7
            append(if (bits and 4 != 0) 'r' else '-')
            append(if (bits and 2 != 0) 'w' else '-')
            append(if (bits and 1 != 0) 'x' else '-')
        }
    }

    /** Parses octal permissions typed by hand (`755`, `0644`); `null` if it isn't valid. */
    fun parseOctal(text: String): Int? {
        val t = text.trim()
        if (t.isEmpty() || t.length > 4 || t.any { it !in '0'..'7' }) return null
        return t.toInt(8)
    }

    /** A free name for [name] in a folder that already has [taken]: `a.txt`, `a (1).txt`... */
    fun freeName(name: String, taken: Set<String>): String {
        if (name !in taken) return name
        val dot = name.lastIndexOf('.').takeIf { it > 0 } ?: name.length
        val base = name.substring(0, dot)
        val ext = name.substring(dot)
        var i = 1
        while ("$base ($i)$ext" in taken) i++
        return "$base ($i)$ext"
    }
}

/** How the browser orders a folder. */
enum class FileSort { NAME, SIZE, DATE }

/** An entry of a remote folder, as the browser needs it (from the engine's `RemoteFile`). */
data class FileItem(
    val name: String,
    val path: String,
    val dir: Boolean,
    val link: Boolean,
    val size: Long,
    /** Seconds since 1970. */
    val modified: Long?,
)

object FileListing {
    /**
     * What a folder shows: without hidden files (`.name`) unless
     * [showHidden], filtered by [query] (in the name, any case), folders
     * first and then by [sort] ([descending]: the other way round). Ties go
     * by name.
     */
    fun <T> arrange(
        entries: List<T>,
        item: (T) -> FileItem,
        sort: FileSort,
        descending: Boolean,
        showHidden: Boolean,
        query: String = "",
    ): List<T> {
        val q = query.trim().lowercase()
        val byName = compareBy<T, String>(String.CASE_INSENSITIVE_ORDER) { item(it).name }.thenBy { item(it).name }
        val main: Comparator<T> = when (sort) {
            FileSort.NAME -> byName
            FileSort.SIZE -> compareBy<T> { item(it).size }.then(byName)
            FileSort.DATE -> compareBy<T> { item(it).modified ?: Long.MIN_VALUE }.then(byName)
        }
        val ordered = if (descending) main.reversed() else main
        return entries
            .filter { e -> item(e).name.let { (showHidden || !it.startsWith('.')) && (q.isEmpty() || it.lowercase().contains(q)) } }
            .sortedWith(compareBy<T> { if (item(it).dir) 0 else 1 }.then(ordered))
    }
}
