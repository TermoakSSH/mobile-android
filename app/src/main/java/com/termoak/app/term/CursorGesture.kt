package com.termoak.app.term

import kotlin.math.abs
import kotlin.math.hypot

/**
 * How the cursor is moved with a finger (Settings → Terminal → Cursor
 * gestures, the iOS app's `GestureMode`). [key] is what is saved.
 */
enum class GestureMode(val key: String) {
    /** Hold and drag; a normal swipe scrolls. */
    HOLD("hold"),
    /** One finger moves the cursor; two scroll. */
    ONE_FINGER("one_finger"),
    /** One finger scrolls; two move the cursor. */
    TWO_FINGERS("two_fingers"),
    /** A button toggles one finger between moving the cursor and scrolling. */
    BUTTON("button"),
    OFF("off");

    companion object {
        val DEFAULT = HOLD
        fun of(key: String?): GestureMode = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}

/** What moves the cursor in a gesture, once the mode, the button and the terminal are taken into account. */
enum class CursorDrag {
    /** One finger held still for a moment and then dragged. */
    HOLD,
    /** One finger (two scroll). */
    ONE_FINGER,
    /** Two fingers side by side (one scrolls, two apart pinch). */
    TWO_FINGERS,
    /** Nothing: swiping only scrolls. */
    NONE;

    companion object {
        /**
         * [cursorByButton]: in [GestureMode.BUTTON], the button is on.
         * [suspended]: watching without the keyboard, or zoomed out on a
         * terminal wider than the view (one finger pans there).
         */
        fun of(mode: GestureMode, cursorByButton: Boolean, suspended: Boolean): CursorDrag = when {
            suspended -> NONE
            else -> when (mode) {
                GestureMode.HOLD -> HOLD
                GestureMode.ONE_FINGER -> ONE_FINGER
                GestureMode.TWO_FINGERS -> TWO_FINGERS
                GestureMode.BUTTON -> if (cursorByButton) ONE_FINGER else NONE
                GestureMode.OFF -> NONE
            }
        }
    }
}

enum class ArrowDirection { UP, DOWN, LEFT, RIGHT }

/** Direction and speed (1–3) of the cursor drag in progress, for the pad drawn over the terminal. */
data class CursorPad(val direction: ArrowDirection, val level: Int)

/**
 * Moving the cursor with a finger, as a state machine without Android (the
 * view feeds it its touches and does what it answers): it sends arrows,
 * which move the cursor (left/right) or walk the shell history (up/down).
 * Dragging further in the same direction goes faster (three levels). It
 * also decides, for the view, whether a touch is a tap, a scroll, a pinch
 * or the long-press menu, so they never overlap with the cursor.
 *
 * - [CursorDrag.HOLD]: if the finger moves before [HOLD_DELAY_MS], it
 *   scrolls; held that long it arms (a haptic tick) and dragging then sends
 *   arrows; held still until [HOLD_MENU_MS] it opens the menu.
 * - [CursorDrag.ONE_FINGER] / [CursorDrag.TWO_FINGERS]: dragging with that
 *   many fingers sends arrows; the other number scrolls. Two fingers that
 *   move apart or together pinch instead.
 * - [CursorDrag.NONE]: swiping only scrolls.
 *
 * Positions in px; [density] turns the distances (in dp, like the iOS
 * app's points) into px. Times in ms (`MotionEvent.getEventTime`).
 */
class CursorGesture(
    private val density: Float,
    /** Movement (px) before a touch stops being a tap (the system's touch slop). */
    private val touchSlop: Float,
    /** Long press without cursor gestures by holding (the system's timeout). */
    private val longPressMs: Long,
) {
    sealed interface Action {
        data class Arrow(val direction: ArrowDirection) : Action
        data class Haptic(val kind: HapticKind) : Action
        /** The long-press menu, where the finger went down. */
        data class Menu(val x: Float, val y: Float) : Action
    }

    enum class HapticKind {
        /** The cursor took the finger (armed by holding, or a drag started). */
        START,
        /** The arrows changed direction. */
        DIRECTION,
        /** One level faster. */
        LEVEL,
    }

    enum class Phase {
        IDLE,
        /** One finger down, not decided yet. */
        PENDING,
        /** Held still long enough: dragging moves the cursor ([CursorDrag.HOLD]). */
        ARMED,
        /** Two fingers down, not decided yet ([CursorDrag.TWO_FINGERS]). */
        PENDING_TWO,
        CURSOR,
        SCROLL,
        PINCH,
        MENU,
        /** Over (a finger more or less in the middle of a cursor drag): the rest is ignored. */
        DONE,
    }

    var phase = Phase.IDLE
        private set
    private var drag = CursorDrag.NONE
    /** Fingers the cursor drag uses. */
    private var cursorFingers = 1
    private var startX = 0f
    private var startY = 0f
    private var startTime = 0L
    private var startSpan = 0f
    private var anchorX = 0f
    private var anchorY = 0f
    private var direction: ArrowDirection? = null
    /** Distance travelled in the current direction (to go up a level). */
    private var travelled = 0f
    private var level = 1

    /** The pad to draw while dragging the cursor (`null`: none). */
    var pad: CursorPad? = null
        private set

    /** The gesture that just ended was a tap (for the view's tap: keyboard or link). */
    var tapped = false
        private set

    /** One or more fingers scroll (the view's scroll: history, arrows in full-screen programs, or panning). */
    val scrolls: Boolean get() = phase == Phase.SCROLL

    /** When [tick] has something to do (`null`: nothing pending). */
    val deadline: Long?
        get() = when (phase) {
            Phase.PENDING -> startTime + if (drag == CursorDrag.HOLD) HOLD_DELAY_MS else longPressMs
            Phase.ARMED -> startTime + HOLD_MENU_MS
            else -> null
        }

    private fun dp(v: Float) = v * density

    /** The first finger went down. */
    fun down(x: Float, y: Float, time: Long, drag: CursorDrag): List<Action> {
        reset()
        this.drag = drag
        startX = x
        startY = y
        startTime = time
        tapped = false
        phase = Phase.PENDING
        return emptyList()
    }

    /**
     * Another finger went down: now [pointers], whose center is ([x], [y])
     * and [span] apart.
     */
    fun pointerDown(x: Float, y: Float, span: Float, pointers: Int, time: Long): List<Action> {
        when (phase) {
            Phase.PENDING, Phase.ARMED -> if (drag == CursorDrag.TWO_FINGERS && pointers == 2) {
                phase = Phase.PENDING_TWO
                startX = x
                startY = y
                startSpan = span
                startTime = time
            } else {
                // Two fingers scroll (and pinch) in every other mode.
                phase = Phase.SCROLL
            }
            Phase.CURSOR, Phase.PENDING_TWO -> end()
            else -> Unit
        }
        return emptyList()
    }

    /** A finger went up and others stay. */
    fun pointerUp(): List<Action> {
        when (phase) {
            Phase.CURSOR, Phase.PENDING_TWO -> end()
            else -> Unit
        }
        return emptyList()
    }

    /** The fingers moved: their center ([x], [y]), [span] apart (two or more). */
    fun move(x: Float, y: Float, span: Float, pointers: Int, time: Long): List<Action> {
        val out = mutableListOf<Action>()
        when (phase) {
            Phase.PENDING -> if (hypot(x - startX, y - startY) > touchSlop) {
                when (drag) {
                    CursorDrag.ONE_FINGER -> startCursor(1, out)
                    // Held long enough even if the timer hasn't said so yet.
                    CursorDrag.HOLD -> if (time - startTime >= HOLD_DELAY_MS) startCursor(1, out) else phase = Phase.SCROLL
                    else -> phase = Phase.SCROLL
                }
                if (phase == Phase.CURSOR) drag(x, y, out)
            }
            Phase.ARMED -> if (hypot(x - startX, y - startY) > touchSlop) {
                // The haptic was already given when it armed.
                startCursor(1, out, haptic = false)
                drag(x, y, out)
            }
            Phase.PENDING_TWO -> if (pointers == 2) {
                val moved = hypot(x - startX, y - startY)
                val spread = abs(span - startSpan)
                when {
                    // Side by side: the cursor. Moving apart or together: a pinch.
                    moved > touchSlop && spread < moved / 2 -> {
                        startCursor(2, out)
                        drag(x, y, out)
                    }
                    spread > touchSlop -> phase = Phase.PINCH
                }
            }
            Phase.CURSOR -> if (pointers == cursorFingers) drag(x, y, out)
            else -> Unit
        }
        return out
    }

    /** The last finger went up. */
    fun up(): List<Action> {
        tapped = phase == Phase.PENDING
        reset()
        return emptyList()
    }

    /** The system took the touches (a parent view, a dialog...). */
    fun cancel() {
        tapped = false
        reset()
    }

    /** Time passed with the finger still: arm the cursor, or open the menu. */
    fun tick(time: Long): List<Action> {
        val due = deadline ?: return emptyList()
        if (time < due) return emptyList()
        return when (phase) {
            Phase.PENDING -> if (drag == CursorDrag.HOLD) {
                phase = Phase.ARMED
                listOf(Action.Haptic(HapticKind.START))
            } else {
                phase = Phase.MENU
                listOf(Action.Menu(startX, startY))
            }
            Phase.ARMED -> {
                phase = Phase.MENU
                listOf(Action.Menu(startX, startY))
            }
            else -> emptyList()
        }
    }

    /**
     * A pinch is starting (the view's scale detector): allowed unless the
     * fingers already move the cursor or opened the menu. From then on they
     * don't move the cursor.
     */
    fun pinchBegins(): Boolean = when (phase) {
        Phase.CURSOR, Phase.MENU, Phase.DONE -> false
        Phase.PENDING, Phase.ARMED, Phase.PENDING_TWO -> {
            phase = Phase.PINCH
            true
        }
        else -> true
    }

    private fun startCursor(fingers: Int, out: MutableList<Action>, haptic: Boolean = true) {
        phase = Phase.CURSOR
        cursorFingers = fingers
        // Measured from where the fingers went down, not from where it was recognized.
        anchorX = startX
        anchorY = startY
        direction = null
        travelled = 0f
        level = 1
        if (haptic) out += Action.Haptic(HapticKind.START)
    }

    private fun end() {
        phase = Phase.DONE
        pad = null
    }

    private fun reset() {
        phase = Phase.IDLE
        pad = null
        direction = null
        travelled = 0f
        level = 1
    }

    private fun drag(x: Float, y: Float, out: MutableList<Action>) {
        val dx = x - anchorX
        val dy = y - anchorY
        val horizontal = abs(dx) >= abs(dy)
        val delta = if (horizontal) dx else dy
        fun dirOf(d: Float) = if (horizontal) {
            if (d < 0) ArrowDirection.LEFT else ArrowDirection.RIGHT
        } else {
            if (d < 0) ArrowDirection.UP else ArrowDirection.DOWN
        }
        if (abs(delta) < dp(STEPS_DP[level - 1])) {
            if (pad == null && hypot(dx, dy) > dp(PAD_AFTER_DP)) pad = CursorPad(direction ?: dirOf(delta), level)
            return
        }
        val newDirection = dirOf(delta)
        if (newDirection != direction) {
            direction = newDirection
            travelled = 0f
            level = 1
            out += Action.Haptic(HapticKind.DIRECTION)
        }
        var remaining = abs(delta)
        val sign = if (delta < 0) -1f else 1f
        while (remaining >= dp(STEPS_DP[level - 1])) {
            val step = dp(STEPS_DP[level - 1])
            out += Action.Arrow(newDirection)
            remaining -= step
            travelled += step
            if (horizontal) anchorX += sign * step else anchorY += sign * step
            val newLevel = levelFor(travelled / density)
            if (newLevel != level) {
                level = newLevel
                out += Action.Haptic(HapticKind.LEVEL)
            }
        }
        // The other axis doesn't add up: it can be corrected without changing direction.
        if (horizontal) anchorY = y else anchorX = x
        pad = CursorPad(newDirection, level)
    }

    companion object {
        /** Held this long without moving, one finger arms the cursor ([CursorDrag.HOLD]). */
        const val HOLD_DELAY_MS = 300L
        /** Held still this long, it opens the menu instead ([CursorDrag.HOLD]). */
        const val HOLD_MENU_MS = 600L
        /** Distance (dp) per arrow at each level. */
        val STEPS_DP = floatArrayOf(26f, 13f, 6f)
        /** Distance travelled (dp) in one direction from which levels 2 and 3 start. */
        val LEVELS_AT_DP = floatArrayOf(80f, 190f)
        /** The pad shows once the finger moved this far (dp). */
        const val PAD_AFTER_DP = 6f

        /** Speed level (1–3) after travelling [dp] in the same direction. */
        fun levelFor(dp: Float): Int = when {
            dp >= LEVELS_AT_DP[1] -> 3
            dp >= LEVELS_AT_DP[0] -> 2
            else -> 1
        }
    }
}
