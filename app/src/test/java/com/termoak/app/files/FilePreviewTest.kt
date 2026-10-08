package com.termoak.app.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which remote files the app previews itself, and how. */
class FilePreviewTest {
    @Test
    fun kinds() {
        assertEquals(PreviewKind.TEXT, PreviewKind.of("nginx.conf", null, 2_000))
        assertEquals(PreviewKind.TEXT, PreviewKind.of("app.LOG", "application/octet-stream", 2_000))
        assertEquals(PreviewKind.TEXT, PreviewKind.of(".bashrc", null, 3_000))
        assertEquals(PreviewKind.TEXT, PreviewKind.of("Dockerfile", null, 1_000_000))
        assertEquals(PreviewKind.TEXT, PreviewKind.of("data.json", "application/json", 10))
        assertEquals(PreviewKind.IMAGE, PreviewKind.of("logo.PNG", null, 50_000))
        assertEquals(PreviewKind.PDF, PreviewKind.of("report.pdf", "application/pdf", 50_000))
        // Small files without an extension are tried as text; big ones and archives aren't.
        assertEquals(PreviewKind.TEXT, PreviewKind.of("motd", null, 300))
        assertNull(PreviewKind.of("dump", null, 5_000_000))
        assertNull(PreviewKind.of("backup.tar.gz", "application/gzip", 1_000))
        assertNull(PreviewKind.of("drawing.svg", "image/svg+xml", 1_000))
        assertNull(PreviewKind.of("huge.log", null, PreviewKind.MAX_BYTES + 1))
    }

    @Test
    fun binary() {
        assertTrue(PreviewKind.looksBinary(byteArrayOf(0x7f, 0x45, 0x4c, 0x46, 0, 1)))
        assertFalse(PreviewKind.looksBinary("héllo\nworld".toByteArray()))
    }
}
