package com.termoak.app.term

import com.termoak.app.term.CursorGesture.Action
import com.termoak.app.term.CursorGesture.HapticKind
import com.termoak.app.term.CursorGesture.Phase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CursorGestureTest {
    /** 2 px per dp; touch slop 8 dp; long press 400 ms. */
    private val density = 2f
    private fun px(dp: Float) = dp * density
    private fun gesture() = CursorGesture(density, touchSlop = px(8f), longPressMs = 400)

    private fun List<Action>.arrows() = filterIsInstance<Action.Arrow>().map { it.direction }
    private fun List<Action>.haptics() = filterIsInstance<Action.Haptic>().map { it.kind }

    // Where fingers go down.
    private val x0 = 500f
    private val y0 = 800f

    @Test
    fun modes() {
        assertEquals(GestureMode.HOLD, GestureMode.DEFAULT)
        for (m in GestureMode.entries) assertEquals(m, GestureMode.of(m.key))
        assertEquals(GestureMode.HOLD, GestureMode.of(null))
        assertEquals(GestureMode.HOLD, GestureMode.of("whatever"))
    }

    @Test
    fun whatMovesTheCursor() {
        assertEquals(CursorDrag.HOLD, CursorDrag.of(GestureMode.HOLD, cursorByButton = false, suspended = false))
        assertEquals(CursorDrag.ONE_FINGER, CursorDrag.of(GestureMode.ONE_FINGER, cursorByButton = false, suspended = false))
        assertEquals(CursorDrag.TWO_FINGERS, CursorDrag.of(GestureMode.TWO_FINGERS, cursorByButton = false, suspended = false))
        assertEquals(CursorDrag.NONE, CursorDrag.of(GestureMode.BUTTON, cursorByButton = false, suspended = false))
        assertEquals(CursorDrag.ONE_FINGER, CursorDrag.of(GestureMode.BUTTON, cursorByButton = true, suspended = false))
        assertEquals(CursorDrag.NONE, CursorDrag.of(GestureMode.OFF, cursorByButton = true, suspended = false))
        // Read-only or zoomed out: nothing, whatever the mode.
        for (m in GestureMode.entries) {
            assertEquals(CursorDrag.NONE, CursorDrag.of(m, cursorByButton = true, suspended = true))
        }
    }

    @Test
    fun levels() {
        assertEquals(1, CursorGesture.levelFor(0f))
        assertEquals(1, CursorGesture.levelFor(79.9f))
        assertEquals(2, CursorGesture.levelFor(80f))
        assertEquals(2, CursorGesture.levelFor(189f))
        assertEquals(3, CursorGesture.levelFor(190f))
        assertEquals(3, CursorGesture.levelFor(1000f))
    }

    // ----- Hold and drag -----

    @Test
    fun holdQuickSwipeScrolls() {
        val g = gesture()
        g.down(x0, y0, 0, CursorDrag.HOLD)
        assertEquals(CursorGesture.HOLD_DELAY_MS, g.deadline)
        val out = g.move(x0, y0 - px(40f), 0f, 1, 120)
        assertTrue(out.isEmpty())
        assertTrue(g.scrolls)
        assertNull(g.deadline)
        // No menu later on.
        assertTrue(g.tick(1000).isEmpty())
        g.up()
        assertFalse(g.tapped)
    }

    @Test
    fun holdThenDragSendsArrows() {
        val g = gesture()
        g.down(x0, y0, 0, CursorDrag.HOLD)
        // A little movement within the slop doesn't decide anything.
        assertTrue(g.move(x0 + px(3f), y0, 0f, 1, 100).isEmpty())
        assertTrue(g.tick(299).isEmpty())
        assertEquals(listOf(HapticKind.START), g.tick(300).haptics())
        assertEquals(Phase.ARMED, g.phase)
        assertEquals(CursorGesture.HOLD_MENU_MS, g.deadline)
        val out = g.move(x0 + px(26f), y0, 0f, 1, 400)
        assertEquals(Phase.CURSOR, g.phase)
        assertFalse(g.scrolls)
        // Measured from where the finger went down: one arrow, and the direction's tick (not START again).
        assertEquals(listOf(ArrowDirection.RIGHT), out.arrows())
        assertEquals(listOf(HapticKind.DIRECTION), out.haptics())
        assertEquals(CursorPad(ArrowDirection.RIGHT, 1), g.pad)
        assertNull(g.deadline)
        g.up()
        assertFalse(g.tapped)
        assertNull(g.pad)
    }

    @Test
    fun holdStillOpensTheMenu() {
        val g = gesture()
        g.down(x0, y0, 0, CursorDrag.HOLD)
        g.tick(300)
        assertTrue(g.tick(599).isEmpty())
        assertEquals(listOf(Action.Menu(x0, y0)), g.tick(600))
        assertEquals(Phase.MENU, g.phase)
        // Moving after the menu opened does nothing.
        assertTrue(g.move(x0 + px(100f), y0, 0f, 1, 700).isEmpty())
        assertFalse(g.scrolls)
        g.up()
        assertFalse(g.tapped)
    }

    @Test
    fun holdLetGoWhileArmedIsNothing() {
        val g = gesture()
        g.down(x0, y0, 0, CursorDrag.HOLD)
        g.tick(300)
        g.up()
        assertFalse(g.tapped)
        assertNull(g.deadline)
    }

    @Test
    fun holdDragAfterTheDelayEvenIfTheTimerIsLate() {
        val g = gesture()
        g.down(x0, y0, 0, CursorDrag.HOLD)
        val out = g.move(x0, y0 + px(30f), 0f, 1, 350)
        assertEquals(Phase.CURSOR, g.phase)
        assertEquals(listOf(HapticKind.START, HapticKind.DIRECTION), out.haptics())
        assertEquals(listOf(ArrowDirection.DOWN), out.arrows())
    }

    @Test
    fun tap() {
        for (drag in CursorDrag.entries) {
            val g = gesture()
            g.down(x0, y0, 0, drag)
            g.move(x0 + px(2f), y0, 0f, 1, 50)
            g.up()
            assertTrue(drag.name, g.tapped)
        }
    }

    @Test
    fun holdTwoFingersScroll() {
        val g = gesture()
        g.down(x0, y0, 0, CursorDrag.HOLD)
        g.pointerDown(x0 + 50f, y0, 100f, 2, 30)
        assertTrue(g.scrolls)
        assertNull(g.deadline)
        assertTrue(g.pinchBegins())
        assertTrue(g.tick(1000).isEmpty())
    }

    // ----- Other modes: the long press -----

    @Test
    fun longPressWithoutHolding() {
        for (drag in listOf(CursorDrag.ONE_FINGER, CursorDrag.TWO_FINGERS, CursorDrag.NONE)) {
            val g = gesture()
            g.down(x0, y0, 1000, drag)
            assertEquals(1400L, g.deadline)
            assertTrue(g.tick(1399).isEmpty())
            assertEquals(listOf(Action.Menu(x0, y0)), g.tick(1400))
            g.up()
            assertFalse(g.tapped)
        }
    }

    @Test
    fun secondFingerCancelsTheMenu() {
        val g = gesture()
        g.down(x0, y0, 0, CursorDrag.NONE)
        g.pointerDown(x0, y0, 100f, 2, 100)
        assertNull(g.deadline)
        assertTrue(g.tick(1000).isEmpty())
    }

    // ----- One finger for the cursor -----

    @Test
    fun oneFingerMovesTheCursor() {
        val g = gesture()
        g.down(x0, y0, 0, CursorDrag.ONE_FINGER)
        // Past the slop: the cursor takes it, the pad shows before the first arrow.
        val start = g.move(x0 - px(10f), y0, 0f, 1, 50)
        assertEquals(Phase.CURSOR, g.phase)
        assertEquals(listOf(HapticKind.START), start.haptics())
        assertTrue(start.arrows().isEmpty())
        assertEquals(CursorPad(ArrowDirection.LEFT, 1), g.pad)
        val out = g.move(x0 - px(52f), y0, 0f, 1, 80)
        assertEquals(listOf(ArrowDirection.LEFT, ArrowDirection.LEFT), out.arrows())
        assertFalse(g.scrolls)
    }

    @Test
    fun oneFingerModeTwoFingersScroll() {
        val g = gesture()
        g.down(x0, y0, 0, CursorDrag.ONE_FINGER)
        g.pointerDown(x0 + 40f, y0, 80f, 2, 20)
        assertTrue(g.scrolls)
        assertTrue(g.move(x0 + 40f, y0 - px(100f), 80f, 2, 60).isEmpty())
        assertTrue(g.scrolls)
    }

    @Test
    fun aSecondFingerEndsTheCursor() {
        val g = gesture()
        g.down(x0, y0, 0, CursorDrag.ONE_FINGER)
        g.move(x0 + px(30f), y0, 0f, 1, 50)
        assertEquals(Phase.CURSOR, g.phase)
        g.pointerDown(x0, y0, 100f, 2, 60)
        assertEquals(Phase.DONE, g.phase)
        assertNull(g.pad)
        assertTrue(g.move(x0 + px(200f), y0, 100f, 2, 80).isEmpty())
        assertFalse(g.scrolls)
        assertFalse(g.pinchBegins())
    }

    // ----- Two fingers for the cursor -----

    @Test
    fun twoFingerModeOneFingerScrolls() {
        val g = gesture()
        g.down(x0, y0, 0, CursorDrag.TWO_FINGERS)
        assertTrue(g.move(x0, y0 + px(20f), 0f, 1, 40).isEmpty())
        assertTrue(g.scrolls)
    }

    @Test
    fun twoFingersSideBySideMoveTheCursor() {
        val g = gesture()
        g.down(x0, y0, 0, CursorDrag.TWO_FINGERS)
        g.pointerDown(x0 + 100f, y0, 200f, 2, 20)
        assertEquals(Phase.PENDING_TWO, g.phase)
        assertNull(g.deadline)
        // The center moves 30 dp right, the fingers stay about as far apart.
        val out = g.move(x0 + 100f + px(30f), y0, 203f, 2, 80)
        assertEquals(Phase.CURSOR, g.phase)
        assertEquals(listOf(ArrowDirection.RIGHT), out.arrows())
        assertEquals(listOf(HapticKind.START, HapticKind.DIRECTION), out.haptics())
        // Now a pinch doesn't zoom.
        assertFalse(g.pinchBegins())
        assertFalse(g.scrolls)
        // Lifting one finger ends it.
        g.pointerUp()
        assertEquals(Phase.DONE, g.phase)
        assertTrue(g.move(x0 + px(200f), y0, 0f, 1, 120).isEmpty())
    }

    @Test
    fun twoFingersApartPinch() {
        val g = gesture()
        g.down(x0, y0, 0, CursorDrag.TWO_FINGERS)
        g.pointerDown(x0 + 100f, y0, 200f, 2, 20)
        // The center barely moves, the fingers move apart.
        val out = g.move(x0 + 100f + 4f, y0, 200f + px(20f), 2, 80)
        assertTrue(out.isEmpty())
        assertEquals(Phase.PINCH, g.phase)
        assertTrue(g.pinchBegins())
        // Not the cursor afterwards, however the fingers move.
        assertTrue(g.move(x0 + px(200f), y0, 200f + px(20f), 2, 120).isEmpty())
    }

    @Test
    fun theScaleDetectorCanDecideFirst() {
        val g = gesture()
        g.down(x0, y0, 0, CursorDrag.TWO_FINGERS)
        g.pointerDown(x0 + 100f, y0, 200f, 2, 20)
        assertTrue(g.pinchBegins())
        assertEquals(Phase.PINCH, g.phase)
        assertTrue(g.move(x0 + px(100f), y0, 200f, 2, 80).isEmpty())
    }

    @Test
    fun aThirdFingerEndsTwoFingers() {
        val g = gesture()
        g.down(x0, y0, 0, CursorDrag.TWO_FINGERS)
        g.pointerDown(x0 + 100f, y0, 200f, 2, 20)
        g.pointerDown(x0 + 100f, y0, 200f, 3, 30)
        assertEquals(Phase.DONE, g.phase)
    }

    // ----- Off -----

    @Test
    fun offOnlyScrolls() {
        val g = gesture()
        g.down(x0, y0, 0, CursorDrag.NONE)
        assertTrue(g.move(x0 + px(300f), y0, 0f, 1, 500).isEmpty())
        assertTrue(g.scrolls)
        val two = gesture()
        two.down(x0, y0, 0, CursorDrag.NONE)
        two.pointerDown(x0, y0, 100f, 2, 10)
        assertTrue(two.scrolls)
        assertTrue(two.pinchBegins())
    }

    // ----- Arrows: speed, direction, axes -----

    /** A one-finger drag straight to the right, already in the cursor. */
    private fun dragging(): CursorGesture = gesture().also {
        it.down(x0, y0, 0, CursorDrag.ONE_FINGER)
        it.move(x0 + px(9f), y0, 0f, 1, 10)
    }

    @Test
    fun acceleration() {
        val g = dragging()
        // Level 1: an arrow every 26 dp; at 104 dp travelled (≥ 80) it goes up to level 2.
        val first = g.move(x0 + px(104f), y0, 0f, 1, 100)
        assertEquals(4, first.arrows().size)
        assertEquals(listOf(HapticKind.DIRECTION, HapticKind.LEVEL), first.haptics())
        assertEquals(CursorPad(ArrowDirection.RIGHT, 2), g.pad)
        // Level 2: every 13 dp; at 195 dp (≥ 190), level 3.
        val second = g.move(x0 + px(195f), y0, 0f, 1, 200)
        assertEquals(7, second.arrows().size)
        assertEquals(listOf(HapticKind.LEVEL), second.haptics())
        assertEquals(3, g.pad?.level)
        // Level 3: every 6 dp.
        val third = g.move(x0 + px(255f), y0, 0f, 1, 300)
        assertEquals(10, third.arrows().size)
        assertTrue(third.haptics().isEmpty())
    }

    @Test
    fun accelerationInOneGo() {
        val g = dragging()
        val out = g.move(x0 + px(255f), y0, 0f, 1, 100)
        assertEquals(21, out.arrows().size)
        assertTrue(out.arrows().all { it == ArrowDirection.RIGHT })
        assertEquals(listOf(HapticKind.DIRECTION, HapticKind.LEVEL, HapticKind.LEVEL), out.haptics())
        assertEquals(3, g.pad?.level)
    }

    @Test
    fun turningBackStartsSlowAgain() {
        val g = dragging()
        g.move(x0 + px(104f), y0, 0f, 1, 100)
        assertEquals(2, g.pad?.level)
        // Back 26 dp from where the last arrow left the anchor: left, level 1 again.
        val out = g.move(x0 + px(104f - 26f), y0, 0f, 1, 200)
        assertEquals(listOf(ArrowDirection.LEFT), out.arrows())
        assertEquals(listOf(HapticKind.DIRECTION), out.haptics())
        assertEquals(CursorPad(ArrowDirection.LEFT, 1), g.pad)
    }

    @Test
    fun upAndDownWalkTheHistory() {
        val g = gesture()
        g.down(x0, y0, 0, CursorDrag.ONE_FINGER)
        assertEquals(listOf(ArrowDirection.UP, ArrowDirection.UP), g.move(x0, y0 - px(52f), 0f, 1, 50).arrows())
        // Back down past the anchor.
        assertEquals(listOf(ArrowDirection.DOWN), g.move(x0, y0 - px(26f), 0f, 1, 100).arrows())
    }

    @Test
    fun theOtherAxisDoesNotAddUp() {
        val g = dragging()
        // Right with some drift down: only right arrows.
        assertEquals(listOf(ArrowDirection.RIGHT), g.move(x0 + px(26f), y0 + px(20f), 0f, 1, 50).arrows())
        // The drift was dropped: drifting a bit more is still nothing vertical.
        assertTrue(g.move(x0 + px(30f), y0 + px(40f), 0f, 1, 80).arrows().isEmpty())
    }

    @Test
    fun cancel() {
        val g = dragging()
        g.cancel()
        assertEquals(Phase.IDLE, g.phase)
        assertFalse(g.tapped)
        assertNull(g.pad)
        assertNull(g.deadline)
    }
}
