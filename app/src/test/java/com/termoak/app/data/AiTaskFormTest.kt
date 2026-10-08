package com.termoak.app.data

import com.termoak.ffi.HostGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AiTaskFormTest {
    @Test
    fun providerAndModel() {
        assertNull(AiTaskForm.provider(null, "x"))
        assertNull(AiTaskForm.provider("", null))
        assertEquals("claude", AiTaskForm.provider("claude", null))
        assertEquals("claude", AiTaskForm.provider("claude", " "))
        assertEquals("gpt::gpt-5", AiTaskForm.provider("gpt", "gpt-5"))
    }

    @Test
    fun groupsReachTheirSubgroups() {
        val groups = listOf(
            HostGroup(id = "a", name = "A"), HostGroup(id = "b", name = "B", parentId = "a"),
            HostGroup(id = "c", name = "C", parentId = "b"), HostGroup(id = "d", name = "D"),
        )
        assertEquals(setOf("a", "b", "c"), AiTaskForm.groupAndSubgroups(groups, "a"))
        assertEquals(setOf("b", "c"), AiTaskForm.groupAndSubgroups(groups, "b"))
        assertEquals(setOf("d"), AiTaskForm.groupAndSubgroups(groups, "d"))
    }

    @Test
    fun durations() {
        assertEquals("800 ms", AiTaskForm.duration(800))
        assertEquals("5 s", AiTaskForm.duration(5_400))
        assertEquals("2 min", AiTaskForm.duration(120_000))
        assertEquals("1 min 5 s", AiTaskForm.duration(65_000))
    }
}
