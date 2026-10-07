package com.termoak.app.term

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class TabOrderTest {
    private val tabs = listOf("a", "b", "c", "d")

    @Test
    fun movesLeftAndRight() {
        assertEquals(listOf("b", "a", "c", "d"), TabOrder.move(tabs, 0, 1))
        assertEquals(listOf("a", "c", "b", "d"), TabOrder.move(tabs, 2, 1))
        assertEquals(listOf("b", "c", "d", "a"), TabOrder.move(tabs, 0, 3))
        assertEquals(listOf("d", "a", "b", "c"), TabOrder.move(tabs, 3, 0))
    }

    @Test
    fun clampsAndKeepsTheListWhenNothingMoves() {
        assertEquals(listOf("b", "c", "d", "a"), TabOrder.move(tabs, 0, 99))
        assertEquals(listOf("b", "a", "c", "d"), TabOrder.move(tabs, 1, -5))
        assertSame(tabs, TabOrder.move(tabs, 2, 2))
        assertSame(tabs, TabOrder.move(tabs, 7, 0))
    }

    @Test
    fun dragStaysUntilPastTheMiddleOfTheNeighbour() {
        val widths = listOf(100, 120, 80, 100)
        assertEquals(1 to 39f, TabOrder.drag(1, 39f, widths, 4f))
        assertEquals(1 to -50f, TabOrder.drag(1, -50f, widths, 4f))
        // Past half of c (80): it takes c's place, 84 px further on.
        assertEquals(2 to (41f - 84f), TabOrder.drag(1, 41f, widths, 4f))
        // Past half of a (100) to the left.
        assertEquals(0 to (-51f + 104f), TabOrder.drag(1, -51f, widths, 4f))
    }

    @Test
    fun dragCanJumpSeveralTabsAndStopsAtTheEnds() {
        val widths = listOf(100, 100, 100, 100)
        // 2.6 tabs to the right of a: past b, c and d's middles.
        assertEquals(3 to (270f - 312f), TabOrder.drag(0, 270f, widths, 4f))
        assertEquals(3 to 500f - 312f, TabOrder.drag(0, 500f, widths, 4f))
        assertEquals(0 to -40f, TabOrder.drag(0, -40f, widths, 4f))
        assertEquals(0 to (-400f + 312f), TabOrder.drag(3, -400f, widths, 4f))
    }
}
