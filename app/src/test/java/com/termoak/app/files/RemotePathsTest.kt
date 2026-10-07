package com.termoak.app.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemotePathsTest {
    @Test
    fun parentAndChild() {
        assertEquals("/", RemotePaths.parent("/"))
        assertEquals("/", RemotePaths.parent("/etc"))
        assertEquals("/etc", RemotePaths.parent("/etc/nginx"))
        assertEquals("/etc", RemotePaths.parent("/etc/nginx/"))
        assertEquals("/etc/hosts", RemotePaths.child("/etc", "hosts"))
        assertEquals("/hosts", RemotePaths.child("/", "hosts"))
        assertEquals("nginx", RemotePaths.name("/etc/nginx/"))
        assertEquals("/", RemotePaths.name("/"))
    }

    @Test
    fun crumbs() {
        assertEquals(listOf("/" to "/"), RemotePaths.crumbs("/"))
        assertEquals(
            listOf("/" to "/", "home" to "/home", "ana" to "/home/ana"),
            RemotePaths.crumbs("/home/ana"),
        )
    }

    @Test
    fun names() {
        assertTrue(RemotePaths.invalidName(""))
        assertTrue(RemotePaths.invalidName("  "))
        assertTrue(RemotePaths.invalidName(".."))
        assertTrue(RemotePaths.invalidName("a/b"))
        assertFalse(RemotePaths.invalidName(".bashrc"))
        assertEquals("a.txt", RemotePaths.freeName("a.txt", setOf("b.txt")))
        assertEquals("a (1).txt", RemotePaths.freeName("a.txt", setOf("a.txt")))
        assertEquals("a (2).txt", RemotePaths.freeName("a.txt", setOf("a.txt", "a (1).txt")))
        assertEquals(".env (1)", RemotePaths.freeName(".env", setOf(".env")))
        assertEquals("Makefile (1)", RemotePaths.freeName("Makefile", setOf("Makefile")))
    }

    @Test
    fun permissions() {
        assertEquals("644", RemotePaths.octal(0b110_100_100))
        assertEquals("755", RemotePaths.octal(0x41ED)) // directory bits are dropped
        assertEquals("rwxr-x---", RemotePaths.symbolic(0b111_101_000))
        assertEquals(0b111_101_101, RemotePaths.parseOctal("0755"))
        assertNull(RemotePaths.parseOctal("789"))
        assertNull(RemotePaths.parseOctal(""))
    }

    private fun f(name: String, dir: Boolean = false, size: Long = 0, modified: Long? = null) =
        FileItem(name, "/x/$name", dir, link = false, size = size, modified = modified)

    @Test
    fun arrange() {
        val all = listOf(
            f("b.txt", size = 10, modified = 3), f("A.log", size = 30, modified = 1), f(".hidden", size = 5),
            f("zdir", dir = true, modified = 9), f("adir", dir = true, modified = 2),
        )
        fun names(list: List<FileItem>) = list.map { it.name }
        assertEquals(
            listOf("adir", "zdir", "A.log", "b.txt"),
            names(FileListing.arrange(all, { it }, FileSort.NAME, descending = false, showHidden = false)),
        )
        assertEquals(
            listOf("adir", "zdir", ".hidden", "A.log", "b.txt"),
            names(FileListing.arrange(all, { it }, FileSort.NAME, descending = false, showHidden = true)),
        )
        // Folders stay first in any order.
        assertEquals(
            listOf("zdir", "adir", "A.log", "b.txt"),
            names(FileListing.arrange(all, { it }, FileSort.SIZE, descending = true, showHidden = false)),
        )
        assertEquals(
            listOf("zdir", "adir", "b.txt", "A.log"),
            names(FileListing.arrange(all, { it }, FileSort.DATE, descending = true, showHidden = false)),
        )
        assertEquals(
            listOf("A.log"),
            names(FileListing.arrange(all, { it }, FileSort.NAME, descending = false, showHidden = false, query = " LOG ")),
        )
    }
}
