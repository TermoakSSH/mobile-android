package com.termoak.app.term

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.os.SystemClock
import android.text.InputType
import android.util.TypedValue
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewConfiguration
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import androidx.annotation.VisibleForTesting
import androidx.core.graphics.withTranslation
import com.termoak.ffi.KeyModifiers
import com.termoak.ffi.MouseMode
import com.termoak.ffi.ScreenCursorShape
import com.termoak.ffi.ScreenPoint
import com.termoak.ffi.ScreenRange
import com.termoak.ffi.TerminalKey
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max

/**
 * Draws the emulator screen and feeds it the keyboard. It is a classic view
 * (not Compose) because it needs its own connection to the system keyboard to
 * receive the keys as they are.
 */
private val NoModifiers = KeyModifiers(shift = false, alt = false, ctrl = false)

/**
 * A cell of the grid, screen and scrollback (the engine's `ScreenPoint`):
 * [line] 0 is the top of the screen when scrolled to the bottom, negative
 * lines are the scrollback. A selection made of these stays on its text
 * while the view scrolls.
 */
private data class GridPos(val col: Int, val line: Int) : Comparable<GridPos> {
    override fun compareTo(other: GridPos): Int = compareValuesBy(this, other, { it.line }, { it.col })
    fun point() = ScreenPoint(line, col.coerceAtLeast(0).toUInt())
}

private fun ScreenPoint.pos() = GridPos(col.toInt(), line)

/** Cursor blink: half a period (ms). */
private const val BLINK_MS = 530L
/** The current find match, over the text (amber). */
private const val FIND_CURRENT = 0x99E5A50A.toInt()

@SuppressLint("ViewConstructor")
class TerminalView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.MONOSPACE }
    private val fill = Paint()
    private var cellWidth = 0f
    private var cellHeight = 0f
    private var baseline = 0f
    private var fontSp = 13f
    private val density = resources.displayMetrics.density

    /** Font size changed with a pinch (to save it). */
    var onFontSizeChanged: (Float) -> Unit = {}
    /** Long press or right click: copy/paste menu, at that point of the view (px). */
    var onContextMenu: (x: Float, y: Float) -> Unit = { _, _ -> }
    /** Text was selected with the mouse, or the selection went away. */
    var onSelectionChanged: (Boolean) -> Unit = {}
    /** An app shortcut typed here (Ctrl+Shift+…) that nobody took before reaching the view. */
    var onShortcut: (Shortcut) -> Boolean = { false }
    /** A hardware keyboard is attached: focusing doesn't open the on-screen keyboard. */
    var hardwareKeyboard: () -> Boolean = { false }
    /** A finger went down on it (the split view focuses this pane). */
    var onTouched: () -> Unit = {}
    /**
     * Text of several lines pasted from the keyboard's clipboard: it goes
     * through the same confirmation as the Paste menu. Without it, it is typed.
     */
    var onPasteText: ((String) -> Unit)? = null

    /** How a finger moves the cursor (Settings → Terminal → Cursor gestures). */
    var gestureMode = GestureMode.DEFAULT

    /**
     * The rest of the first command suggestion, drawn dimmed right after the
     * cursor (Command suggestions "Next to the cursor"); `null`: none.
     */
    var ghostText: String? = null
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    /**
     * The cursor's cell in this view (px), while the end of the terminal is
     * on screen: where the suggestions list goes. `null` otherwise.
     */
    fun cursorCell(): RectF? {
        val s = session ?: return null
        val snap = s.screen.snapshot()
        if (snap.displayOffset > 0u) return null
        val c = snap.cursor ?: return null
        val x = paddingLeft + c.col.toInt() * cellWidth * fit - panX
        val y = paddingTop + c.row.toInt() * cellHeight * fit
        return RectF(x, y, x + cellWidth * fit, y + cellHeight * fit)
    }

    /**
     * What the keyboard is composing and hasn't sent yet (Chinese, Japanese,
     * Korean...): drawn at the cursor, underlined, until it is committed.
     */
    private var preedit: String? = null

    /** Size of a cell (px), as drawn now. */
    val cellSize: Pair<Float, Float> get() = cellWidth * fit to cellHeight * fit

    /**
     * Watching a shared terminal without the keyboard: the system keyboard
     * stays hidden (nothing typed would reach the terminal), and the cursor
     * gestures are off.
     */
    var readOnly = false
        set(value) {
            if (field == value) return
            field = value
            if (value) hideKeyboard()
        }

    /** Scale to fit a terminal wider than the view (guests follow the owner's size). */
    private var fit = 1f
    /**
     * Pinch zoom over the fit while the terminal is wider than the view
     * (1: the whole width; up to the real font size), with [panX] (px) to
     * scroll sideways.
     */
    private var zoom = 1f
    private var panX = 0f

    /** Width of the terminal at the real font size, and the room for it. */
    private fun widths(): Pair<Float, Float> =
        (session?.screen?.cols()?.toInt() ?: 0) * cellWidth to (width - paddingLeft - paddingRight).toFloat()

    /** The terminal doesn't fit the view's width (a guest following a wider owner). */
    private fun tooWide(): Boolean = widths().let { (need, avail) -> need > avail && avail > 0f }

    /** Last output or input (ms, uptime): the cursor is shown solid right after it, then blinks. */
    @Volatile private var activityAt = 0L

    /** Redraw request this view leaves on its session (only it removes it: another pane may show the same one later). */
    private val redraw: () -> Unit = {
        activityAt = SystemClock.uptimeMillis()
        postInvalidateOnAnimation()
    }

    var session: TermSession? = null
        set(value) {
            if (field === value) return
            field?.let { if (it.onScreenChanged === redraw) it.onScreenChanged = {} }
            field = value
            value?.onScreenChanged = redraw
            resizeToView()
            invalidate()
        }

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        setFontSize(fontSp)
    }

    /** The terminal's font (Settings → Terminal → Font); the grid is measured again. */
    fun setTypeface(typeface: Typeface) {
        if (paint.typeface === typeface) return
        paint.typeface = typeface
        setFontSize(fontSp)
    }

    fun setFontSize(sp: Float) {
        fontSp = sp
        paint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, resources.displayMetrics)
        cellWidth = paint.measureText("M")
        val fm = paint.fontMetrics
        cellHeight = floor(fm.descent - fm.ascent + 1)
        baseline = -fm.ascent
        resizeToView()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) = resizeToView()

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        session?.onScreenChanged = redraw
    }

    override fun onDetachedFromWindow() {
        session?.let { if (it.onScreenChanged === redraw) it.onScreenChanged = {} }
        removeCallbacks(cursorTick)
        cursor.cancel()
        super.onDetachedFromWindow()
    }

    private fun resizeToView() {
        if (width == 0 || height == 0 || cellWidth == 0f) return
        clearSelection()
        val cols = max(2, ((width - paddingLeft - paddingRight) / cellWidth).toInt())
        val rows = max(1, ((height - paddingTop - paddingBottom) / cellHeight).toInt())
        session?.resize(cols, rows)
    }

    // ----- Drawing -----

    override fun onDraw(canvas: Canvas) {
        val s = session ?: return
        // Only its own place: drawColor fills the whole clip, and the canvas it gets
        // from Compose isn't clipped to the view (AndroidView doesn't clip).
        val saved = canvas.save()
        canvas.clipRect(0, 0, width, height)
        try {
            drawScreen(canvas, s)
        } finally {
            canvas.restoreToCount(saved)
        }
    }

    private fun drawScreen(canvas: Canvas, s: TermSession) {
        followOutput(s)
        val snap = s.screen.snapshot()
        lastOffset = snap.displayOffset.toInt()
        canvas.drawColor(snap.background.toInt())
        val left = paddingLeft.toFloat()
        val top = paddingTop.toFloat()
        val (need, avail) = widths()
        if (need > avail && avail > 0f) {
            fit = minOf(1f, avail / need * zoom)
        } else {
            fit = 1f
            zoom = 1f
        }
        panX = panX.coerceIn(0f, max(0f, need * fit - avail))
        canvas.save()
        if (panX > 0f) canvas.translate(-panX, 0f)
        if (fit < 1f) canvas.scale(fit, fit, left, top)
        snap.lines.forEachIndexed { row, line ->
            val y = top + row * cellHeight
            for (run in line.runs) {
                val x = left + run.col.toInt() * cellWidth
                run.bg?.let {
                    fill.color = it.toInt()
                    canvas.drawRect(x, y, x + run.cells.toInt() * cellWidth, y + cellHeight, fill)
                }
                paint.color = run.fg.toInt()
                paint.isFakeBoldText = run.bold
                paint.textSkewX = if (run.italic) -0.2f else 0f
                paint.isUnderlineText = run.underline
                paint.isStrikeThruText = run.strike
                if (run.wide) {
                    canvas.drawText(run.text, x, y + baseline, paint)
                } else {
                    // Cell by cell: the font width doesn't always match the grid.
                    var i = 0
                    var col = 0
                    while (i < run.text.length) {
                        val next = run.text.offsetByCodePoints(i, 1)
                        if (run.text[i] != ' ' || run.underline || run.strike) {
                            canvas.drawText(run.text, i, next, x + col * cellWidth, y + baseline, paint)
                        }
                        i = next
                        col++
                    }
                }
            }
        }
        paint.isUnderlineText = false
        paint.isStrikeThruText = false
        val selection = selectionColor(s)
        // Find matches on screen (the current one in amber).
        for (h in snap.highlights) {
            fill.color = if (h.current) FIND_CURRENT else selection
            val x = left + h.col.toInt() * cellWidth
            val y = top + h.row.toInt() * cellHeight
            canvas.drawRect(x, y, x + h.cells.toInt() * cellWidth, y + cellHeight, fill)
        }
        selectionRange()?.let { (from, to) ->
            fill.color = selection
            val cols = snap.cols.toInt()
            val rows = snap.rows.toInt()
            val off = lastOffset
            // Only the lines on screen (a selection can go far into the scrollback).
            for (line in maxOf(from.line, -off)..minOf(to.line, rows - 1 - off)) {
                val c0 = if (line == from.line) from.col else 0
                val c1 = if (line == to.line) to.col else cols - 1
                val y = top + (line + off) * cellHeight
                canvas.drawRect(left + c0 * cellWidth, y, left + (c1 + 1) * cellWidth, y + cellHeight, fill)
            }
        }
        fill.alpha = 0xFF
        // The keyboard's text being composed, at the cursor (over the cell), underlined.
        val composing = preedit
        if (composing != null && snap.displayOffset == 0u) {
            snap.cursor?.let { c ->
                val x = left + c.col.toInt() * cellWidth
                val y = top + c.row.toInt() * cellHeight
                val w = paint.measureText(composing)
                fill.color = snap.background.toInt()
                canvas.drawRect(x, y, x + w, y + cellHeight, fill)
                paint.color = snap.foreground.toInt()
                paint.isFakeBoldText = false
                paint.textSkewX = 0f
                canvas.drawText(composing, x, y + baseline, paint)
                fill.color = snap.cursorColor.toInt()
                canvas.drawRect(x, y + cellHeight - 2 * density, x + w, y + cellHeight - density, fill)
            }
        }
        // The rest of the suggestion, dimmed after the cursor (only while the end is on screen).
        val ghost = ghostText
        if (ghost != null && preedit == null && snap.displayOffset == 0u) {
            snap.cursor?.let { c ->
                val x = left + c.col.toInt() * cellWidth
                val y = top + c.row.toInt() * cellHeight
                val room = (snap.cols.toInt() - c.col.toInt()).coerceAtLeast(0)
                paint.color = snap.foreground.toInt()
                paint.alpha = 0x66
                paint.isFakeBoldText = false
                paint.textSkewX = 0f
                var i = 0
                var col = 0
                while (i < ghost.length && col < room) {
                    val next = ghost.offsetByCodePoints(i, 1)
                    canvas.drawText(ghost, i, next, x + col * cellWidth, y + baseline, paint)
                    i = next
                    col++
                }
                paint.alpha = 0xFF
            }
        }
        snap.cursor?.takeIf { cursorVisible(it.blinking) }?.let { c ->
            val x = left + c.col.toInt() * cellWidth
            val y = top + c.row.toInt() * cellHeight
            fill.color = snap.cursorColor.toInt()
            when (c.shape) {
                ScreenCursorShape.BEAM -> canvas.drawRect(x, y, x + 2 * density, y + cellHeight, fill)
                ScreenCursorShape.UNDERLINE -> canvas.drawRect(x, y + cellHeight - 2 * density, x + cellWidth, y + cellHeight, fill)
                ScreenCursorShape.HOLLOW -> {
                    fill.style = Paint.Style.STROKE
                    fill.strokeWidth = density
                    canvas.drawRect(x, y, x + cellWidth, y + cellHeight, fill)
                    fill.style = Paint.Style.FILL
                }
                ScreenCursorShape.BLOCK -> {
                    fill.alpha = 0x99
                    canvas.drawRect(x, y, x + cellWidth, y + cellHeight, fill)
                    fill.alpha = 0xFF
                }
            }
        }
        canvas.restore()
        if (touchSelection) drawHandles(canvas, snap.cursorColor.toInt())
        cursor.pad?.let { drawCursorPad(canvas, it, snap.background.toInt(), snap.cursorColor.toInt()) }
        // Viewing the scrollback: mark on the right edge.
        if (snap.displayOffset > 0u) {
            fill.color = snap.cursorColor.toInt()
            fill.alpha = 0x80
            canvas.drawRect(width - 3 * density, 0f, width.toFloat(), 24 * density, fill)
            fill.alpha = 0xFF
        }
    }

    /**
     * The cursor of a program that asked for a blinking one (DECSCUSR,
     * `CSI ? 12 h`) blinks while this view has the focus, solid right after
     * any output or typing.
     */
    private fun cursorVisible(blinking: Boolean): Boolean {
        if (!blinking || !hasFocus()) return true
        val t = SystemClock.uptimeMillis() - activityAt
        postInvalidateDelayed(BLINK_MS - t % BLINK_MS)
        return (t / BLINK_MS) % 2 == 0L
    }

    private var selectionTheme: String? = "?"
    private var selectionArgb = 0x553FB27F

    /** The theme's selection colour (also for find matches), see-through. */
    private fun selectionColor(s: TermSession): Int {
        if (selectionTheme != s.themeId) {
            selectionTheme = s.themeId
            val c = runCatching { s.screen.colors().selection.toInt() }.getOrDefault(0x3FB27F)
            // An opaque colour would hide the text: some transparency.
            selectionArgb = if ((c ushr 24) == 0xFF || (c ushr 24) == 0) (c and 0xFFFFFF) or 0x80000000.toInt() else c
        }
        return selectionArgb
    }

    // ----- Touch: keyboard, scrolling and zoom -----

    private var scrollRemainder = 0f
    private var scaling = false
    private val scale = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            // Two fingers that already move the cursor don't zoom.
            if (!cursor.pinchBegins()) return false
            scaling = true
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            if (tooWide()) {
                // Zoom in on the owner's wider terminal (the font stays as it is).
                val (need, avail) = widths()
                val before = fit
                zoom = (zoom * detector.scaleFactor).coerceIn(1f, need / avail)
                val after = minOf(1f, avail / need * zoom)
                // Keep the point under the fingers in place.
                val x = detector.focusX - paddingLeft
                panX = ((x + panX) / before * after - x).coerceIn(0f, max(0f, need * after - avail))
                fit = after
                invalidate()
                return true
            }
            val next = (fontSp * detector.scaleFactor).coerceIn(8f, 24f)
            if (abs(next - fontSp) >= 0.25f) setFontSize(next)
            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            if (tooWide()) return
            onFontSizeChanged(fontSp)
        }
    })

    /**
     * A tap (the cursor gestures said so: one finger, still, let go before
     * holding did anything). Double tap: the word; triple tap: the line (with
     * the copy menu). Counted here rather than by GestureDetector, whose
     * double tap listener swallows the second tap's single tap.
     */
    private fun tap(e: MotionEvent) {
        val cell = cellAt(e.x, e.y)
        val last = lastTapCell
        taps = if (last != null && e.eventTime - lastTapAt < ViewConfiguration.getDoubleTapTimeout() &&
            last.second == cell.second && abs(last.first - cell.first) <= 2
        ) taps + 1 else 1
        lastTapAt = e.eventTime
        lastTapCell = cell
        if (taps >= 2) {
            if (select(cell, taps > 2)) onContextMenu(e.x, e.y)
            return
        }
        // A tap while text is selected only lets go of it.
        if (hasSelection) {
            clearSelection()
            return
        }
        // The program asked for the mouse (htop, mc, vim with mouse=a...): a tap is a click.
        if (clickRemote(cell)) {
            showKeyboard()
            return
        }
        // A tap on a link opens it; otherwise it shows the keyboard.
        if (openLinkAt(e.x, e.y)) return
        showKeyboard()
    }

    /** Selects the word (or, with [line], the line) at a cell: double and triple tap, holding. Tests listen here. */
    @VisibleForTesting
    internal var select: (cell: Pair<Int, Int>, line: Boolean) -> Boolean = { cell, line ->
        if (line) selectLineAt(cell) else selectWordAt(cell)
    }

    /** Where the cursor gestures' arrows go: the session (it encodes and broadcasts them). Tests listen here. */
    @VisibleForTesting
    internal var sendArrow: (TerminalKey) -> Unit = { key -> session?.arrow(key) }

    /** What the finger on the terminal is doing now (tests). */
    @VisibleForTesting
    internal val gesturePhase: CursorGesture.Phase get() = cursor.phase

    // Only scrolling: taps, holding and the menu come from the cursor gestures (CursorGesture).
    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
            val s = session ?: return false
            // The fingers move the cursor (or held to open the menu).
            if (scaling || !cursor.scrolls) return true
            // Zoomed in on a wider terminal: sideways.
            if (tooWide() && abs(dx) > abs(dy)) {
                panX += dx
                invalidate()
                return true
            }
            scrollRemainder += dy
            val lines = (scrollRemainder / cellHeight).toInt()
            if (lines != 0) {
                scrollRemainder -= lines * cellHeight
                if (s.screen.alternateScreen()) {
                    clearSelection()
                    // In vim, less, htop...: arrow keys.
                    // Only here: scrolling isn't typing, it isn't broadcast to other panes.
                    // (The cursor gestures' arrows are: they are typing.)
                    val key = TermInput.Key(if (lines > 0) TerminalKey.Down else TerminalKey.Up, NoModifiers)
                    repeat(abs(lines)) { s.apply(key) }
                } else {
                    s.screen.scroll(-lines)
                    invalidate()
                }
            }
            return true
        }
    }).apply {
        // The long-press menu comes from the cursor gestures (later when holding moves the cursor),
        // and taps are counted in tap(): no double tap listener, which would also keep a quick
        // swipe right after a tap from scrolling.
        setIsLongpressEnabled(false)
        setOnDoubleTapListener(null)
    }

    // ----- Cursor gestures: arrows by dragging (CursorGesture) -----

    private val cursor = ViewConfiguration.get(context).let {
        CursorGesture(density, it.scaledTouchSlop.toFloat(), ViewConfiguration.getLongPressTimeout().toLong())
    }
    private val cursorTick = Runnable {
        cursorActions(cursor.tick(SystemClock.uptimeMillis()))
        scheduleCursorTick()
    }

    private fun scheduleCursorTick() {
        removeCallbacks(cursorTick)
        cursor.deadline?.let { postDelayed(cursorTick, (it - SystemClock.uptimeMillis()).coerceAtLeast(0)) }
    }

    /** What moves the cursor now: the setting, the button, and whether there is a cursor to move. */
    private fun cursorDrag(): CursorDrag =
        CursorDrag.of(gestureMode, session?.cursorByButton?.value == true, suspended = readOnly || tooWide())

    private fun feedCursor(e: MotionEvent) {
        var cx = 0f
        var cy = 0f
        for (i in 0 until e.pointerCount) {
            cx += e.getX(i)
            cy += e.getY(i)
        }
        cx /= e.pointerCount
        cy /= e.pointerCount
        val span = if (e.pointerCount >= 2) hypot(e.getX(1) - e.getX(0), e.getY(1) - e.getY(0)) else 0f
        val padBefore = cursor.pad
        val actions = when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> cursor.down(e.x, e.y, e.eventTime, cursorDrag())
            MotionEvent.ACTION_POINTER_DOWN -> cursor.pointerDown(cx, cy, span, e.pointerCount, e.eventTime)
            MotionEvent.ACTION_MOVE -> cursor.move(cx, cy, span, e.pointerCount, e.eventTime)
            MotionEvent.ACTION_POINTER_UP -> cursor.pointerUp()
            MotionEvent.ACTION_UP -> cursor.up()
            MotionEvent.ACTION_CANCEL -> {
                cursor.cancel()
                emptyList()
            }
            else -> emptyList()
        }
        cursorActions(actions)
        if (cursor.pad != padBefore) invalidate()
        scheduleCursorTick()
    }

    private fun cursorActions(actions: List<CursorGesture.Action>) {
        for (a in actions) {
            when (a) {
                is CursorGesture.Action.Arrow -> if (!readOnly) sendArrow(
                    when (a.direction) {
                        ArrowDirection.UP -> TerminalKey.Up
                        ArrowDirection.DOWN -> TerminalKey.Down
                        ArrowDirection.LEFT -> TerminalKey.Left
                        ArrowDirection.RIGHT -> TerminalKey.Right
                    },
                )
                is CursorGesture.Action.Haptic -> performHapticFeedback(
                    when (a.kind) {
                        CursorGesture.HapticKind.START -> HapticFeedbackConstants.VIRTUAL_KEY
                        CursorGesture.HapticKind.DIRECTION -> HapticFeedbackConstants.CLOCK_TICK
                        CursorGesture.HapticKind.LEVEL -> HapticFeedbackConstants.CONTEXT_CLICK
                    },
                )
                is CursorGesture.Action.Menu -> {
                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    // Held on text: its word is selected, with handles to change the selection.
                    if (!hasSelection) select(cellAt(a.x, a.y), false)
                    onContextMenu(a.x, a.y)
                }
            }
        }
    }

    private val padPath = Path()
    private val padRect = RectF()
    private val padStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    /**
     * The pad while dragging the cursor (top left): the four arrows, the
     * active one in the cursor's color with one, two or three chevrons
     * depending on the speed.
     */
    private fun drawCursorPad(canvas: Canvas, pad: CursorPad, background: Int, accent: Int) {
        val dark = android.graphics.Color.luminance(background) < 0.5f
        val size = 96 * density
        val left = 16 * density
        val top = 16 * density
        padRect.set(left, top, left + size, top + size)
        fill.color = if (dark) 0xD9303034.toInt() else 0xD9EDEDF0.toInt()
        canvas.drawRoundRect(padRect, 18 * density, 18 * density, fill)
        val cx = padRect.centerX()
        val cy = padRect.centerY()
        val stroke = padStroke
        // Not `density` inside withTranslation: that would be the Canvas's.
        val dp = density
        val idle = if (dark) 0x99FFFFFF.toInt() else 0x80000000.toInt()
        for (d in ArrowDirection.entries) {
            val active = d == pad.direction
            val (ox, oy, angle) = when (d) {
                ArrowDirection.RIGHT -> Triple(30f, 0f, 0f)
                ArrowDirection.DOWN -> Triple(0f, 30f, 90f)
                ArrowDirection.LEFT -> Triple(-30f, 0f, 180f)
                ArrowDirection.UP -> Triple(0f, -30f, 270f)
            }
            stroke.color = if (active) accent else idle
            val count = if (active) pad.level else 1
            canvas.withTranslation(cx + ox * dp, cy + oy * dp) {
                rotate(angle)
                // Chevrons side by side, centered on the arrow's place.
                val gap = 6 * dp
                val first = -(count - 1) * gap / 2
                for (i in 0 until count) {
                    val x = first + i * gap
                    padPath.reset()
                    padPath.moveTo(x - 3 * dp, -6 * dp)
                    padPath.lineTo(x + 3 * dp, 0f)
                    padPath.lineTo(x - 3 * dp, 6 * dp)
                    drawPath(padPath, stroke)
                }
            }
        }
        fill.alpha = 0xFF
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        // A mouse (or a trackpad's click) stays a mouse until the button goes up.
        if (event.actionMasked == MotionEvent.ACTION_DOWN) mouseGesture = isMouse(event)
        if (mouseGesture) {
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) mouseGesture = false
            return mouse(event)
        }
        // Dragging a handle of the selection.
        if (dragHandle(event)) return true
        // First: it decides whether the fingers scroll, pinch or move the cursor.
        feedCursor(event)
        scale.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            scaling = false
            onTouched()
        }
        gestures.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP && cursor.tapped && !scaling) tap(event)
        // Every touch is ours, from its first finger: a view that doesn't take the ACTION_DOWN gets
        // nothing more of the gesture (neither from Android nor from Compose's AndroidView), and the
        // cursor gestures, waiting for a finger that never moves nor lifts, opened the menu.
        return true
    }

    /**
     * Focuses the terminal and opens the on-screen keyboard, except with a
     * hardware keyboard attached (then it only takes the focus).
     */
    fun showKeyboard() {
        if (readOnly) return
        requestFocus()
        if (!hardwareKeyboard()) context.getSystemService(InputMethodManager::class.java)?.showSoftInput(this, 0)
    }

    /** The keyboard button: the on-screen keyboard even with a hardware one. */
    fun showSoftKeyboard() {
        if (readOnly) return
        requestFocus()
        context.getSystemService(InputMethodManager::class.java)?.showSoftInput(this, 0)
    }

    fun hideKeyboard() {
        context.getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(windowToken, 0)
    }

    /**
     * Leaving the terminal for another screen: if the keyboard is this
     * view's, it is hidden and the view lets go of the focus. Whether it was.
     */
    fun releaseKeyboard(): Boolean {
        val imm = context.getSystemService(InputMethodManager::class.java) ?: return false
        if (!hasFocus() && !imm.isActive(this)) return false
        imm.hideSoftInputFromWindow(windowToken, 0)
        clearFocus()
        return true
    }

    // ----- Keyboard -----

    override fun onCheckIsTextEditor() = true

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        // No suggestions or autocorrect: every key as it is.
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN or
            EditorInfo.IME_FLAG_NO_EXTRACT_UI or
            EditorInfo.IME_ACTION_NONE
        return object : BaseInputConnection(this, false) {
            private var composing: String?
                get() = preedit
                set(value) {
                    preedit = value?.takeIf { it.isNotEmpty() }
                    invalidate()
                }

            override fun commitText(text: CharSequence, newCursorPosition: Int): Boolean {
                composing = null
                val t = text.toString()
                // Several lines at once: a paste from the keyboard's clipboard, not typing.
                val paste = onPasteText
                if (paste != null && Paste.lineCount(t) > 1) paste(t) else sendText(t)
                return true
            }

            override fun performContextMenuAction(id: Int): Boolean {
                if (id == android.R.id.paste || id == android.R.id.pasteAsPlainText) {
                    val clip = context.getSystemService(android.content.ClipboardManager::class.java)?.primaryClip
                    val t = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
                    val paste = onPasteText
                    if (!t.isNullOrEmpty()) {
                        if (paste != null) paste(t) else session?.paste(t)
                    }
                    return true
                }
                return super.performContextMenuAction(id)
            }

            override fun setComposingText(text: CharSequence, newCursorPosition: Int): Boolean {
                composing = text.toString()
                return true
            }

            override fun finishComposingText(): Boolean {
                composing?.let { sendText(it) }
                composing = null
                return true
            }

            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                repeat(max(1, beforeLength)) { session?.key(TerminalKey.Backspace) }
                return true
            }

            override fun sendKeyEvent(event: KeyEvent): Boolean {
                when (event.action) {
                    KeyEvent.ACTION_DOWN -> onKeyDown(event.keyCode, event)
                    KeyEvent.ACTION_UP -> taken.remove(event.keyCode)
                }
                return true
            }
        }
    }

    private fun sendText(text: String) {
        val s = session ?: return
        // The keyboard's Enter arrives as "\n".
        text.split('\n').forEachIndexed { i, part ->
            if (i > 0) s.key(TerminalKey.Enter)
            if (part.isNotEmpty()) s.text(part)
        }
    }

    /** Hardware keyboard: layout, AltGr, dead keys and modifiers (HardwareKeys). */
    private val keys = HardwareKeys()
    /** Keys whose press was taken here: their release too (so Esc doesn't also go Back). */
    private val taken = mutableSetOf<Int>()

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val s = session ?: return super.onKeyDown(keyCode, event)
        if (keyCode == KeyEvent.KEYCODE_BACK) return super.onKeyDown(keyCode, event)
        // Ctrl+Enter on a `# request` line: the AI proposes the command (typed, never run).
        if ((keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) && event.isCtrlPressed &&
            !event.isAltPressed && !event.isShiftPressed && !event.isMetaPressed
        ) {
            val line = s.typedLine()
            if (line != null && runCatching { com.termoak.ffi.nlRequest(line) }.getOrNull() != null) {
                s.onAiRequest()
                taken += keyCode
                return true
            }
        }
        // → with a suggestion next to the cursor types its rest, like on the desktop.
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && event.hasNoModifiers() && s.acceptFirstSuggestion()) {
            taken += keyCode
            return true
        }
        // Ctrl and Alt of the key bar apply to this key too.
        val (stickyCtrl, stickyAlt) = s.ctrl.value to s.alt.value
        val press = event.toKeyPress().let { it.copy(ctrl = it.ctrl || stickyCtrl, alt = it.alt || stickyAlt) }
        val handled = when (val r = keys.press(press, event.layout())) {
            is KeyResult.Send -> {
                if (stickyCtrl || stickyAlt) s.takeStickyModifiers()
                clearSelection()
                r.strokes.forEach { s.stroke(it) }
                true
            }
            // Shortcuts normally go through the activity first; here when nobody took them.
            // The split view's Ctrl+Alt ones type as keys when nothing takes them.
            is KeyResult.Action -> {
                if (!onShortcut(r.shortcut) && r.shortcut.typesWhenFree) {
                    (keys.press(press, event.layout(), shortcuts = false) as? KeyResult.Send)?.let { send ->
                        if (stickyCtrl || stickyAlt) s.takeStickyModifiers()
                        send.strokes.forEach { s.stroke(it) }
                    }
                }
                true
            }
            KeyResult.Consumed -> true
            KeyResult.Unhandled -> false
        }
        if (!handled) return super.onKeyDown(keyCode, event)
        taken += keyCode
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (taken.remove(keyCode)) return true
        return super.onKeyUp(keyCode, event)
    }

    // ----- Mouse and trackpad -----

    /** Cell (column, row) under a point of the view. */
    private fun cellAt(x: Float, y: Float): Pair<Int, Int> {
        val cols = session?.screen?.cols()?.toInt() ?: 1
        val rows = session?.screen?.rows()?.toInt() ?: 1
        if (cellWidth == 0f || cellHeight == 0f) return 0 to 0
        val col = ((x - paddingLeft + panX) / fit / cellWidth).toInt().coerceIn(0, cols - 1)
        val row = ((y - paddingTop) / fit / cellHeight).toInt().coerceIn(0, rows - 1)
        return col to row
    }

    /** Opens the link under a point, if there is one. */
    private fun openLinkAt(x: Float, y: Float): Boolean {
        val s = session ?: return false
        val (col, row) = cellAt(x, y)
        val url = s.screen.linkAt(row.toUInt(), col.toUInt()) ?: return false
        runCatching {
            context.startActivity(
                android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
        return true
    }

    private fun isMouse(e: MotionEvent): Boolean =
        e.isFromSource(InputDevice.SOURCE_MOUSE) && (e.getToolType(0) == MotionEvent.TOOL_TYPE_MOUSE || e.buttonState != 0)

    /** The touch gesture in progress comes from a mouse. */
    private var mouseGesture = false

    /** The selection (mouse or finger): anchor and end cells of the grid (screen and scrollback). */
    private var selAnchor: GridPos? = null
    private var selEnd: GridPos? = null
    /** Display offset of the last frame (to place grid lines on screen). */
    private var lastOffset = 0
    /** The session's output and scrollback growth when the selection was last placed. */
    private var selSerial = 0L
    private var selShift = 0L
    private var selecting = false
    private var dragged = false
    /** Button held down that the remote program is told about. */
    private var remoteButton: MouseEncoder.Button? = null
    private var lastCell: Pair<Int, Int>? = null
    private var wheelRemainder = 0f

    /** First and last selected cells, in reading order. */
    private fun selectionRange(): Pair<GridPos, GridPos>? {
        val a = selAnchor ?: return null
        val b = selEnd ?: return null
        // A click without dragging selects nothing; a finger can select a single cell.
        if (a == b && !touchSelection) return null
        return if (a <= b) a to b else b to a
    }

    /** The grid cell under a point of the view (with the current scroll). */
    private fun gridAt(x: Float, y: Float): GridPos {
        val (col, row) = cellAt(x, y)
        return session?.let { runCatching { it.screen.pointAt(row.toUInt(), col.toUInt()).pos() }.getOrNull() } ?: GridPos(col, row - lastOffset)
    }

    /** Marks where the session's output was when the selection was placed. */
    private fun anchorSelection() {
        val s = session ?: return
        selSerial = s.outputSerial
        selShift = s.historyShift
    }

    /**
     * New output pushes the text up: the selection goes with it. Once the
     * scrollback is full that can't be followed, and the selection goes.
     */
    private fun followOutput(s: TermSession) {
        if (selAnchor == null || s.outputSerial == selSerial) return
        val shift = s.historyShift
        if (shift >= 0 && selShift >= 0) {
            val d = (shift - selShift).toInt()
            if (d != 0) {
                selAnchor = selAnchor?.let { it.copy(line = it.line - d) }
                selEnd = selEnd?.let { it.copy(line = it.line - d) }
            }
            selSerial = s.outputSerial
            selShift = shift
        } else if (hasSelection) {
            post { clearSelection() }
        }
    }

    val hasSelection: Boolean get() = selectionRange() != null

    fun clearSelection() {
        val had = hasSelection
        selAnchor = null
        selEnd = null
        selecting = false
        touchSelection = false
        handle = 0
        if (had) {
            onSelectionChanged(false)
            invalidate()
        }
    }

    /** The selected text (wrapped lines joined, without trailing spaces), or `null` without a selection. */
    fun selectedText(): String? {
        val (from, to) = selectionRange() ?: return null
        val s = session ?: return null
        return runCatching { s.screen.textRange(from.point(), to.point(), false) }.getOrNull()
    }

    // ----- Selection by touch (screen and scrollback) -----

    /** The selection was made with a finger: it has handles. */
    private var touchSelection = false
    /** The handle being dragged: 1 the start, 2 the end, 0 none. */
    private var handle = 0
    private var taps = 0
    private var lastTapAt = 0L
    private var lastTapCell: Pair<Int, Int>? = null
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** The tick of a selection that changes (Android 8.1+; before, the clock's). */
    private fun handleTick(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) HapticFeedbackConstants.TEXT_HANDLE_MOVE else HapticFeedbackConstants.CLOCK_TICK

    private fun setTouchSelection(from: GridPos, to: GridPos) {
        val before = hasSelection
        selAnchor = from
        selEnd = to
        selecting = false
        touchSelection = true
        anchorSelection()
        if (before != hasSelection || hasSelection) onSelectionChanged(hasSelection)
        performHapticFeedback(handleTick())
        invalidate()
    }

    private fun setTouchSelection(r: ScreenRange) = setTouchSelection(r.start.pos(), r.end.pos())

    /** The grid point of a cell (column, row) on screen. */
    private fun pointOf(cell: Pair<Int, Int>): ScreenPoint? =
        session?.let { runCatching { it.screen.pointAt(cell.second.toUInt(), cell.first.toUInt()) }.getOrNull() }

    /** Selects the word at [cell] (double tap, holding), across wrapped lines. Whether there was one. */
    fun selectWordAt(cell: Pair<Int, Int>): Boolean {
        val s = session ?: return false
        val p = pointOf(cell) ?: return false
        val word = runCatching { s.screen.wordAt(p) }.getOrNull() ?: return false
        setTouchSelection(word)
        return true
    }

    /** Selects the line at [cell] (triple tap), wrapped lines joined. */
    fun selectLineAt(cell: Pair<Int, Int>): Boolean {
        val s = session ?: return false
        val p = pointOf(cell) ?: return false
        val line = runCatching { s.screen.lineAt(p) }.getOrNull()?.takeIf { it.text.isNotBlank() } ?: return false
        setTouchSelection(line)
        return true
    }

    /** "Select" in the menu: the word at that point of the view (px). */
    fun selectWordAtPoint(x: Float, y: Float): Boolean = selectWordAt(cellAt(x, y))

    /** "Select all": the whole terminal, scrollback included. */
    fun selectAll() {
        val s = session ?: return
        val history = runCatching { s.screen.historySize().toInt() }.getOrDefault(0)
        setTouchSelection(GridPos(0, -history), GridPos(s.screen.cols().toInt() - 1, s.screen.rows().toInt() - 1))
    }

    /**
     * Where the handles go (view px): under the start of the first cell and
     * the end of the last one; `null` for an end that is off screen.
     */
    private fun handlePoints(): Pair<PointF?, PointF?>? {
        val (from, to) = selectionRange() ?: return null
        val rows = session?.screen?.rows()?.toInt() ?: return null
        val left = paddingLeft.toFloat()
        val top = paddingTop.toFloat()
        fun x(col: Int) = left + col * cellWidth * fit - panX
        fun at(col: Int, line: Int): PointF? {
            val row = line + lastOffset
            return if (row in 0 until rows) PointF(x(col), top + (row + 1) * cellHeight * fit) else null
        }
        return at(from.col, from.line) to at(to.col + 1, to.line)
    }

    private fun drawHandles(canvas: Canvas, color: Int) {
        val (start, end) = handlePoints() ?: return
        handlePaint.color = color
        val r = 9 * density
        for ((p, isStart) in listOf(start to true, end to false)) {
            if (p == null) continue
            // A drop hanging from the text's corner, leaning outwards.
            val cx = if (isStart) p.x - r * 0.7f else p.x + r * 0.7f
            val cy = p.y + r
            canvas.drawCircle(cx, cy, r, handlePaint)
            canvas.drawRect(if (isStart) cx else p.x, p.y, if (isStart) p.x else cx, cy, handlePaint)
        }
    }

    /** Which handle is under ([x], [y]): 1 start, 2 end, 0 none. */
    private fun handleAt(x: Float, y: Float): Int {
        if (!touchSelection) return 0
        val (start, end) = handlePoints() ?: return 0
        val reach = 28 * density
        val r = 9 * density
        fun near(p: PointF?, dx: Float) = p != null && hypot(x - (p.x + dx), y - (p.y + r)) <= reach
        return when {
            near(end, r * 0.7f) -> 2
            near(start, -r * 0.7f) -> 1
            else -> 0
        }
    }

    /** A finger on a handle moves that end of the selection; let go, the copy menu comes back. */
    private fun dragHandle(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                handle = handleAt(e.x, e.y)
                if (handle == 0) return false
                // The other end stays put: it becomes the anchor.
                val (from, to) = selectionRange() ?: return false.also { handle = 0 }
                if (handle == 1) {
                    selAnchor = to
                    selEnd = from
                } else {
                    selAnchor = from
                    selEnd = to
                }
                parent?.requestDisallowInterceptTouchEvent(true)
                onTouched()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (handle == 0) return false
                // Above the finger, so the text being selected can be seen.
                val y = e.y - 24 * density
                // At the top or the bottom edge: the view scrolls through the scrollback.
                val s = session
                if (s != null && !s.screen.alternateScreen()) {
                    val edge = cellHeight * fit
                    when {
                        y < paddingTop + edge / 2 -> s.screen.scroll(1)
                        e.y > height - paddingBottom - edge / 2 -> s.screen.scroll(-1)
                    }
                    lastOffset = runCatching { s.screen.snapshot().displayOffset.toInt() }.getOrDefault(lastOffset)
                }
                val next = gridAt(e.x, y)
                if (next != selEnd) {
                    selEnd = next
                    performHapticFeedback(handleTick())
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (handle == 0) return false
                handle = 0
                if (e.actionMasked == MotionEvent.ACTION_UP && hasSelection) onContextMenu(e.x, e.y)
                return true
            }
        }
        return handle != 0
    }

    /** A tap as a left click when the remote program asked for the mouse. Whether it was sent. */
    private fun clickRemote(cell: Pair<Int, Int>): Boolean {
        val s = session ?: return false
        val modes = s.screen.modes()
        if (readOnly || tooWide() || modes.mouseMode == MouseMode.OFF) return false
        for (kind in listOf(MouseEncoder.Kind.PRESS, MouseEncoder.Kind.RELEASE)) {
            if (!MouseEncoder.wants(modes.mouseMode, kind, held = kind == MouseEncoder.Kind.RELEASE)) continue
            MouseEncoder.encode(
                MouseEncoder.Button.LEFT, kind, cell.first, cell.second, modes.mouseEncoding,
                shift = false, alt = false, ctrl = false,
            )?.let { s.write(it) }
        }
        return true
    }

    /** Scrolls the history a page (Shift+PgUp / Shift+PgDn). */
    fun scrollPage(up: Boolean) {
        val s = session ?: return
        val rows = s.screen.rows().toInt().coerceAtLeast(2)
        s.screen.scroll(if (up) rows - 1 else -(rows - 1))
        invalidate()
    }

    /** The remote program asked for the mouse, and Shift isn't held (Shift: select here anyway). */
    private fun remoteMouse(e: MotionEvent): Boolean =
        mouseMode() != MouseMode.OFF && e.metaState and KeyEvent.META_SHIFT_ON == 0

    private fun mouseMode(): MouseMode = session?.let { runCatching { it.screen.modes().mouseMode }.getOrNull() } ?: MouseMode.OFF

    private fun report(button: MouseEncoder.Button, kind: MouseEncoder.Kind, cell: Pair<Int, Int>, e: MotionEvent) {
        val s = session ?: return
        val modes = s.screen.modes()
        if (!MouseEncoder.wants(modes.mouseMode, kind, held = remoteButton != null && button != MouseEncoder.Button.NONE)) return
        MouseEncoder.encode(
            button, kind, cell.first, cell.second, modes.mouseEncoding,
            shift = false, alt = e.metaState and KeyEvent.META_ALT_ON != 0, ctrl = e.metaState and KeyEvent.META_CTRL_ON != 0,
        )?.let { s.write(it) }
    }

    /** Mouse clicks and drags: to the remote program when it asked for the mouse; otherwise select and menu. */
    private fun mouse(e: MotionEvent): Boolean {
        val cell = cellAt(e.x, e.y)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                onTouched()
                requestFocus()
                val button = when {
                    e.buttonState and MotionEvent.BUTTON_SECONDARY != 0 -> MouseEncoder.Button.RIGHT
                    e.buttonState and MotionEvent.BUTTON_TERTIARY != 0 -> MouseEncoder.Button.MIDDLE
                    else -> MouseEncoder.Button.LEFT
                }
                if (remoteMouse(e)) {
                    clearSelection()
                    remoteButton = button
                    lastCell = cell
                    report(button, MouseEncoder.Kind.PRESS, cell, e)
                    return true
                }
                when (button) {
                    MouseEncoder.Button.RIGHT -> onContextMenu(e.x, e.y)
                    MouseEncoder.Button.LEFT -> {
                        clearSelection()
                        val at = gridAt(e.x, e.y)
                        selAnchor = at
                        selEnd = at
                        anchorSelection()
                        selecting = true
                        dragged = false
                        touchSelection = false
                    }
                    else -> Unit
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val held = remoteButton
                if (held != null) {
                    if (cell != lastCell) {
                        lastCell = cell
                        report(held, MouseEncoder.Kind.MOTION, cell, e)
                    }
                } else if (selecting && gridAt(e.x, e.y) != selEnd) {
                    val before = hasSelection
                    selEnd = gridAt(e.x, e.y)
                    dragged = true
                    if (before != hasSelection) onSelectionChanged(hasSelection)
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP -> {
                val held = remoteButton
                if (held != null) {
                    report(held, MouseEncoder.Kind.RELEASE, cell, e)
                    remoteButton = null
                } else if (selecting) {
                    selecting = false
                    // A click without dragging: a link opens; otherwise nothing is selected.
                    if (!dragged) {
                        clearSelection()
                        openLinkAt(e.x, e.y)
                    }
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                remoteButton = null
                selecting = false
            }
        }
        return true
    }

    /** Mouse wheel and trackpad scrolling. */
    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        val s = session ?: return super.onGenericMotionEvent(event)
        if (!event.isFromSource(InputDevice.SOURCE_CLASS_POINTER)) return super.onGenericMotionEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_SCROLL -> {
                // Three lines per notch (trackpads give fractions).
                wheelRemainder += event.getAxisValue(MotionEvent.AXIS_VSCROLL) * 3f
                val lines = wheelRemainder.toInt()
                if (lines == 0) return true
                wheelRemainder -= lines
                val cell = cellAt(event.x, event.y)
                when {
                    remoteMouse(event) -> {
                        val button = if (lines > 0) MouseEncoder.Button.WHEEL_UP else MouseEncoder.Button.WHEEL_DOWN
                        repeat(abs(lines)) { report(button, MouseEncoder.Kind.PRESS, cell, event) }
                    }
                    // In vim, less, htop...: arrow keys (not typed in other panes).
                    s.screen.alternateScreen() -> {
                        clearSelection()
                        val key = TermInput.Key(if (lines > 0) TerminalKey.Up else TerminalKey.Down, NoModifiers)
                        repeat(abs(lines)) { s.apply(key) }
                    }
                    else -> {
                        s.screen.scroll(lines)
                        invalidate()
                    }
                }
                return true
            }
        }
        return super.onGenericMotionEvent(event)
    }

    /** Programs that follow every movement (`CSI ? 1003 h`) also get the pointer moving without a button. */
    override fun onHoverEvent(event: MotionEvent): Boolean {
        val s = session
        if (s != null && event.actionMasked == MotionEvent.ACTION_HOVER_MOVE && mouseMode() == MouseMode.MOTION && remoteMouse(event)) {
            val cell = cellAt(event.x, event.y)
            if (cell != lastCell) {
                lastCell = cell
                report(MouseEncoder.Button.NONE, MouseEncoder.Kind.MOTION, cell, event)
            }
            return true
        }
        return super.onHoverEvent(event)
    }
}
