package com.termoak.app.term

import com.termoak.app.term.SplitState.Direction.DOWN
import com.termoak.app.term.SplitState.Direction.LEFT
import com.termoak.app.term.SplitState.Direction.RIGHT
import com.termoak.app.term.SplitState.Direction.UP
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Moving between the split view's panes (Ctrl+Alt+arrows) and who gets the broadcast. */
class SplitNavigationTest {
    @Test
    fun sideBySide() {
        assertEquals(1, SplitState.neighbor(2, 0, RIGHT))
        assertEquals(0, SplitState.neighbor(2, 1, RIGHT)) // wraps
        assertEquals(1, SplitState.neighbor(2, 0, LEFT))
        // One row: no up or down.
        assertNull(SplitState.neighbor(2, 0, UP))
        // One above the other (a tall window): up and down.
        assertEquals(1, SplitState.neighbor(2, 0, DOWN, rows = listOf(1, 1)))
        assertEquals(0, SplitState.neighbor(2, 1, DOWN, rows = listOf(1, 1)))
        // Alone in its row: left and right walk the list.
        assertEquals(1, SplitState.neighbor(2, 0, RIGHT, rows = listOf(1, 1)))
    }

    @Test
    fun grids() {
        // 3: two on top, one below.
        assertEquals(listOf(2, 1), SplitState.gridRows(3))
        assertEquals(2, SplitState.neighbor(3, 0, DOWN))
        assertEquals(2, SplitState.neighbor(3, 1, DOWN))
        assertEquals(1, SplitState.neighbor(3, 2, UP)) // the centre falls on the second (as on iOS)
        assertEquals(0, SplitState.neighbor(3, 2, RIGHT)) // alone: the list
        // 4: 2 × 2.
        assertEquals(3, SplitState.neighbor(4, 1, DOWN))
        assertEquals(2, SplitState.neighbor(4, 3, LEFT))
        assertEquals(1, SplitState.neighbor(4, 3, UP))
        assertNull(SplitState.neighbor(1, 0, RIGHT))
        assertNull(SplitState.neighbor(4, 7, RIGHT))
    }

    @Test
    fun focusModeWalksTheList() {
        assertEquals(1, SplitState.neighbor(3, 0, DOWN, focusMode = true))
        assertEquals(2, SplitState.neighbor(3, 0, LEFT, focusMode = true))
        assertEquals(0, SplitState.neighbor(3, 2, RIGHT, focusMode = true))
    }

    @Test
    fun broadcastReceivers() {
        val split = SplitState(panes = listOf("a", "b", "c"), broadcast = true, excluded = setOf("c"))
        assertEquals(listOf("b"), split.receivers(listOf("a", "b", "c"), "a"))
        // An excluded pane still types when focused; the others get it.
        assertEquals(listOf("a", "b"), split.receivers(listOf("a", "b", "c"), "c"))
        assertEquals(emptyList<String>(), split.copy(broadcast = false).receivers(listOf("a", "b"), "a"))
    }
}
