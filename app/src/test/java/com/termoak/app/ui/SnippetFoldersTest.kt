package com.termoak.app.ui

import com.termoak.ffi.Snippet
import org.junit.Assert.assertEquals
import org.junit.Test

/** The quick panel's snippets: search and folders by their first tag. */
class SnippetFoldersTest {
    private val a = Snippet(name = "Restart nginx", script = "sudo systemctl restart nginx", tags = listOf("web", "ops"))
    private val b = Snippet(name = "Disk", script = "df -h", tags = listOf("ops"))
    private val c = Snippet(name = "Uptime", script = "uptime")
    private val d = Snippet(name = "Logs", script = "journalctl -f", tags = listOf("Web"))

    @Test
    fun search() {
        val all = listOf(a, b, c, d)
        assertEquals(all, SnippetFolders.filter(all, "  "))
        assertEquals(listOf(a), SnippetFolders.filter(all, "NGINX"))
        // Tags count too.
        assertEquals(listOf(a, b), SnippetFolders.filter(all, "ops"))
    }

    @Test
    fun foldersByFirstTagWithoutTagsLast() {
        val folders = SnippetFolders.group(listOf(a, b, c, d), "none")
        assertEquals(listOf("ops", "web", "Web", "none"), folders.map { it.first })
        assertEquals(listOf(b), folders[0].second)
        assertEquals(listOf(c), folders.last().second)
    }
}
