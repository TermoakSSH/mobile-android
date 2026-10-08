package com.termoak.app.term

import android.app.Application
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.viewinterop.AndroidView
import com.termoak.app.term.CursorGesture.Phase
import com.termoak.ffi.TerminalKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

/**
 * The terminal's touch gestures as the phone delivers them: the view hosted
 * in Compose's AndroidView (like TerminalPane), touches dispatched to the
 * window with their timing, the clock advanced by Robolectric. Arrows,
 * selections and the menu are recorded through the view's hooks (no engine
 * on the JVM). 3 px per dp (xxhdpi): an arrow every 26 dp = 78 px at first.
 */
@RunWith(RobolectricTestRunner::class)
// The plain Application: TermoakApp loads the engine (a native library for Android only).
@Config(application = Application::class, sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi")
class TerminalViewGesturesTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var view: TerminalView
    private val arrows = mutableListOf<TerminalKey>()
    private val menus = mutableListOf<Pair<Float, Float>>()
    /** Cells selected, and whether it was the line (else the word). */
    private val selections = mutableListOf<Pair<Pair<Int, Int>, Boolean>>()
    private var fontSize: Float? = null
    private var originX = 0f
    private var originY = 0f
    private var downTime = 0L

    private val step = 78f

    private fun host(mode: GestureMode) {
        rule.setContent {
            AndroidView(
                {
                    TerminalView(it).apply {
                        gestureMode = mode
                        sendArrow = { key -> arrows += key }
                        select = { cell, line -> selections += cell to line; true }
                        onContextMenu = { x, y -> menus += x to y }
                        onFontSizeChanged = { size -> fontSize = size }
                        view = this
                    }
                },
                Modifier.fillMaxSize(),
            )
        }
        rule.waitForIdle()
        val at = IntArray(2)
        view.getLocationInWindow(at)
        originX = at[0].toFloat()
        originY = at[1].toFloat()
    }

    // ----- Fingers (positions in the view, px) -----

    private fun send(action: Int, points: List<Pair<Float, Float>>) {
        val props = Array(points.size) { i ->
            MotionEvent.PointerProperties().apply { id = i; toolType = MotionEvent.TOOL_TYPE_FINGER }
        }
        val coords = Array(points.size) { i ->
            MotionEvent.PointerCoords().apply {
                x = originX + points[i].first
                y = originY + points[i].second
                pressure = 1f
                size = 1f
            }
        }
        val e = MotionEvent.obtain(
            downTime, SystemClock.uptimeMillis(), action, points.size, props, coords,
            0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0,
        )
        rule.activity.window.decorView.dispatchTouchEvent(e)
        e.recycle()
    }

    private fun down(x: Float, y: Float) {
        downTime = SystemClock.uptimeMillis()
        send(MotionEvent.ACTION_DOWN, listOf(x to y))
    }

    private fun move(vararg points: Pair<Float, Float>) = send(MotionEvent.ACTION_MOVE, points.toList())
    private fun up(x: Float, y: Float) = send(MotionEvent.ACTION_UP, listOf(x to y))

    /** A second finger goes down (the first one stays at [first]). */
    private fun secondDown(first: Pair<Float, Float>, second: Pair<Float, Float>) =
        send(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), listOf(first, second))

    private fun secondUp(first: Pair<Float, Float>, second: Pair<Float, Float>) =
        send(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), listOf(first, second))

    /** Time passes (the view's timers run). */
    private fun hold(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    private fun tap(x: Float, y: Float) {
        down(x, y)
        hold(60)
        up(x, y)
    }

    private fun keyboardShown(): Boolean =
        shadowOf(rule.activity.getSystemService(InputMethodManager::class.java)).isSoftInputVisible

    /** Two fingers dragged from [a] and [b] by ([dx], [dy]) each, in [steps] moves, then lifted. */
    private fun twoFingers(a: Pair<Float, Float>, b: Pair<Float, Float>, dxA: Float, dxB: Float, dy: Float = 0f, steps: Int = 6) {
        down(a.first, a.second)
        hold(20)
        secondDown(a, b)
        var pa = a
        var pb = b
        for (i in 1..steps) {
            hold(16)
            pa = (a.first + dxA * i / steps) to (a.second + dy * i / steps)
            pb = (b.first + dxB * i / steps) to (b.second + dy * i / steps)
            move(pa, pb)
        }
        secondUp(pa, pb)
        up(pa.first, pa.second)
    }

    // ----- Hold and drag (the default) -----

    @Test
    fun holdThenDragSendsArrows() {
        host(GestureMode.HOLD)
        down(400f, 900f)
        hold(350)
        assertEquals(Phase.ARMED, view.gesturePhase)
        move(400f + 3 * step + 10f to 905f)
        assertEquals(List(3) { TerminalKey.Right }, arrows)
        // Back the other way: left.
        move(400f + step - 5f to 905f)
        assertEquals(List(2) { TerminalKey.Left }, arrows.drop(3))
        hold(1000)
        up(400f + step, 905f)
        // Never the menu, nor a tap's keyboard.
        assertTrue(menus.isEmpty())
        assertFalse(keyboardShown())
    }

    @Test
    fun holdThenDragUpWalksHistory() {
        host(GestureMode.HOLD)
        down(400f, 900f)
        hold(320)
        move(400f to 900f - 2 * step - 10f)
        up(400f, 900f - 2 * step - 10f)
        assertEquals(listOf(TerminalKey.Up, TerminalKey.Up), arrows)
        assertTrue(menus.isEmpty())
    }

    @Test
    fun quickSwipeScrolls() {
        host(GestureMode.HOLD)
        down(400f, 900f)
        hold(40)
        move(400f to 700f)
        assertEquals(Phase.SCROLL, view.gesturePhase)
        hold(30)
        move(400f to 400f)
        // Held after scrolling: still a scroll, never the cursor or the menu.
        hold(1000)
        move(600f to 400f)
        up(600f, 400f)
        assertTrue(arrows.isEmpty())
        assertTrue(menus.isEmpty())
        assertFalse(keyboardShown())
    }

    @Test
    fun stillHoldOpensTheMenuAt600ms() {
        host(GestureMode.HOLD)
        down(400f, 900f)
        // Past the system's long press (400–500 ms): nothing yet, holding still arms the cursor.
        hold(ViewConfiguration.getLongPressTimeout().toLong() + 50)
        assertTrue(menus.isEmpty())
        assertEquals(Phase.ARMED, view.gesturePhase)
        hold(600 - ViewConfiguration.getLongPressTimeout().toLong())
        assertEquals(1, menus.size)
        assertEquals(400f, menus[0].first, 1f)
        assertEquals(900f, menus[0].second, 1f)
        // Held on text: its word is selected (with handles), with the menu.
        assertEquals(1, selections.size)
        assertFalse(selections[0].second)
        // Moving afterwards doesn't send arrows; letting go isn't a tap.
        move(700f to 900f)
        up(700f, 900f)
        assertTrue(arrows.isEmpty())
        assertEquals(1, menus.size)
        assertFalse(keyboardShown())
    }

    @Test
    fun holdReleasedWithoutMovingDoesNothing() {
        host(GestureMode.HOLD)
        down(400f, 900f)
        hold(400)
        up(400f, 900f)
        hold(1000)
        assertTrue(arrows.isEmpty())
        assertTrue(menus.isEmpty())
        assertFalse(keyboardShown())
    }

    @Test
    fun tapShowsTheKeyboardAndNoMenu() {
        host(GestureMode.HOLD)
        tap(400f, 900f)
        hold(1000)
        assertTrue(keyboardShown())
        assertTrue(view.hasFocus())
        assertTrue(menus.isEmpty())
        assertTrue(selections.isEmpty())
        assertEquals(Phase.IDLE, view.gesturePhase)
    }

    @Test
    fun doubleTapSelectsTheWordTripleTapTheLine() {
        host(GestureMode.HOLD)
        tap(400f, 900f)
        hold(120)
        tap(402f, 902f)
        assertEquals(1, selections.size)
        assertFalse("double tap: the word", selections[0].second)
        assertEquals(1, menus.size)
        hold(120)
        tap(401f, 899f)
        assertEquals(2, selections.size)
        assertTrue("triple tap: the line", selections[1].second)
        assertEquals(2, menus.size)
        // Two taps far apart in time are two single taps.
        hold(1000)
        tap(400f, 900f)
        hold(1000)
        tap(400f, 900f)
        assertEquals(2, selections.size)
    }

    @Test
    fun swipeRightAfterATapScrolls() {
        host(GestureMode.HOLD)
        tap(400f, 900f)
        hold(100)
        down(400f, 900f)
        hold(30)
        move(400f to 600f)
        assertEquals(Phase.SCROLL, view.gesturePhase)
        up(400f, 600f)
        assertTrue(selections.isEmpty())
    }

    @Test
    fun pinchZoomsWithoutArrows() {
        host(GestureMode.HOLD)
        twoFingers(350f to 900f, 950f to 900f, dxA = -200f, dxB = 200f)
        assertTrue(arrows.isEmpty())
        assertTrue(menus.isEmpty())
        val size = fontSize
        assertNotNull("the text size changed", size)
        assertTrue("bigger text: $size", size!! > 13f)
    }

    @Test
    fun twoFingersScrollInHoldMode() {
        host(GestureMode.HOLD)
        down(350f, 900f)
        hold(20)
        secondDown(350f to 900f, 650f to 900f)
        move(350f to 700f, 650f to 700f)
        assertEquals(Phase.SCROLL, view.gesturePhase)
        hold(1000)
        secondUp(350f to 700f, 650f to 700f)
        up(350f, 700f)
        assertTrue(arrows.isEmpty())
        assertTrue(menus.isEmpty())
    }

    // ----- The other modes -----

    @Test
    fun oneFingerMovesTheCursorAtOnce() {
        host(GestureMode.ONE_FINGER)
        down(400f, 900f)
        hold(30)
        move(400f + 2 * step + 10f to 900f)
        up(400f + 2 * step + 10f, 900f)
        assertEquals(List(2) { TerminalKey.Right }, arrows)
        // Two fingers scroll.
        twoFingers(350f to 900f, 650f to 900f, dxA = 0f, dxB = 0f, dy = -300f)
        assertEquals(2, arrows.size)
        // Held still: the menu at the system's long press time.
        down(400f, 900f)
        hold(ViewConfiguration.getLongPressTimeout().toLong() + 20)
        assertEquals(1, menus.size)
        up(400f, 900f)
    }

    @Test
    fun twoFingersMoveTheCursorOneScrolls() {
        host(GestureMode.TWO_FINGERS)
        // One finger scrolls.
        down(400f, 900f)
        hold(30)
        move(700f to 900f)
        assertEquals(Phase.SCROLL, view.gesturePhase)
        up(700f, 900f)
        assertTrue(arrows.isEmpty())
        // Two side by side: the cursor.
        twoFingers(300f to 900f, 600f to 900f, dxA = 3 * step + 10f, dxB = 3 * step + 10f)
        assertEquals(List(3) { TerminalKey.Right }, arrows)
        assertEquals(null, fontSize)
        // Two moving apart: a pinch, no arrows.
        arrows.clear()
        twoFingers(350f to 900f, 950f to 900f, dxA = -200f, dxB = 200f)
        assertTrue(arrows.isEmpty())
        assertTrue((fontSize ?: 0f) > 13f)
        assertTrue(menus.isEmpty())
    }

    @Test
    fun offOnlyScrolls() = onlyScrolls(GestureMode.OFF)

    /** With a button, the button off (it is the session's: none here): like Off. */
    @Test
    fun buttonOffOnlyScrolls() = onlyScrolls(GestureMode.BUTTON)

    private fun onlyScrolls(mode: GestureMode) {
        host(mode)
        down(400f, 900f)
        hold(30)
        move(700f to 900f)
        assertEquals(Phase.SCROLL, view.gesturePhase)
        up(700f, 900f)
        // Held a moment and then dragged: still a scroll.
        down(400f, 900f)
        hold(ViewConfiguration.getLongPressTimeout().toLong() - 100)
        move(700f to 900f)
        assertEquals(Phase.SCROLL, view.gesturePhase)
        up(700f, 900f)
        assertTrue(arrows.isEmpty())
        assertTrue(menus.isEmpty())
        // Held still: the menu at the system's long press time.
        down(400f, 900f)
        hold(ViewConfiguration.getLongPressTimeout().toLong() + 20)
        assertEquals(1, menus.size)
        up(400f, 900f)
        assertTrue(arrows.isEmpty())
    }
}
