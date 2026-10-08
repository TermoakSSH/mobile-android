package com.termoak.app.data

import com.termoak.ffi.HostGroup
import com.termoak.ffi.HostSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** What a host gets from its groups: the nearest group wins (the engine's rule). */
class GroupDefaultsTest {
    @Test
    fun nearestGroupWins() {
        val groups = listOf(
            HostGroup(id = "top", name = "Prod", settings = HostSettings(username = "deploy", port = 2222u, env = mapOf("A" to "1"))),
            HostGroup(id = "web", name = "Web", parentId = "top", settings = HostSettings(port = 22u, recordSessions = true, env = mapOf("B" to "2"))),
            HostGroup(id = "other", name = "Other", settings = HostSettings(username = "x")),
        )
        val s = GroupDefaults.inherited("web", groups)
        assertEquals("deploy", s.username)
        assertEquals(22u, s.port)
        assertEquals(true, s.recordSessions)
        assertEquals(mapOf("A" to "1", "B" to "2"), s.env)
        assertNull(GroupDefaults.inherited(null, groups).username)
        assertEquals("x", GroupDefaults.inherited("other", groups).username)
    }

    @Test
    fun loopsDontHang() {
        val groups = listOf(HostGroup(id = "a", name = "A", parentId = "b"), HostGroup(id = "b", name = "B", parentId = "a", settings = HostSettings(term = "vt100")))
        assertEquals("vt100", GroupDefaults.inherited("a", groups).term)
    }
}
