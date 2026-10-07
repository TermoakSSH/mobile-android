package com.termoak.app.term

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.InputType
import android.util.TypedValue
import android.view.GestureDetector
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import com.termoak.ffi.KeyModifiers
import com.termoak.ffi.ScreenCursorShape
import com.termoak.ffi.TerminalKey
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max

/**
 * Draws the emulator screen and feeds it the keyboard. It is a classic view
 * (not Compose) because it needs its own connection to the system keyboard to
 * receive the keys as they are.
 */
private val NoModifiers = KeyModifiers(shift = false, alt = false, ctrl = false)

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

    /**
     * Watching a shared terminal without the keyboard: the system keyboard
     * stays hidden (nothing typed would reach the terminal).
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

    /** Redraw request this view leaves on its session (only it removes it: another pane may show the same one later). */
    private val redraw: () -> Unit = { postInvalidateOnAnimation() }

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
        val snap = s.screen.snapshot()
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
        selectionRange()?.let { (from, to) ->
            fill.color = snap.cursorColor.toInt()
            fill.alpha = 0x55
            val cols = snap.cols.toInt()
            for (row in from.second..to.second) {
                val c0 = if (row == from.second) from.first else 0
                val c1 = if (row == to.second) to.first else cols - 1
                val y = top + row * cellHeight
                canvas.drawRect(left + c0 * cellWidth, y, left + (c1 + 1) * cellWidth, y + cellHeight, fill)
            }
            fill.alpha = 0xFF
        }
        snap.cursor?.let { c ->
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
        // Viewing the scrollback: mark on the right edge.
        if (snap.displayOffset > 0u) {
            fill.color = snap.cursorColor.toInt()
            fill.alpha = 0x80
            canvas.drawRect(width - 3 * density, 0f, width.toFloat(), 24 * density, fill)
            fill.alpha = 0xFF
        }
    }

    // ----- Touch: keyboard, scrolling and zoom -----

    private var scrollRemainder = 0f
    private var scaling = false
    private val scale = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
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
    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            // A tap on a link opens it; otherwise it shows the keyboard.
            if (openLinkAt(e.x, e.y)) return true
            showKeyboard()
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            if (!scaling) {
                performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                onContextMenu(e.x, e.y)
            }
        }

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
            val s = session ?: return false
            if (scaling) return true
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
                    // In vim, less, htop...: arrow keys.
                    // Only here: scrolling isn't typing, it isn't broadcast to other panes.
                    val key = TermInput.Key(if (lines > 0) TerminalKey.Down else TerminalKey.Up, NoModifiers)
                    repeat(abs(lines)) { s.apply(key) }
                } else {
                    s.screen.scroll(-lines)
                    invalidate()
                }
            }
            return true
        }
    })

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        // A mouse (or a trackpad's click) stays a mouse until the button goes up.
        if (event.actionMasked == MotionEvent.ACTION_DOWN) mouseGesture = isMouse(event)
        if (mouseGesture) {
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) mouseGesture = false
            return mouse(event)
        }
        scale.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            scaling = false
            onTouched()
        }
        return gestures.onTouchEvent(event) || super.onTouchEvent(event)
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
            private var composing: String? = null

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
            is KeyResult.Action -> {
                onShortcut(r.shortcut)
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

    /** Selection with the mouse: anchor and end cells (column, row) of the visible screen. */
    private var selAnchor: Pair<Int, Int>? = null
    private var selEnd: Pair<Int, Int>? = null
    private var selecting = false
    private var dragged = false
    /** Button held down that the remote program is told about. */
    private var remoteButton: MouseEncoder.Button? = null
    private var lastCell: Pair<Int, Int>? = null
    private var wheelRemainder = 0f

    /** First and last selected cells, in reading order. */
    private fun selectionRange(): Pair<Pair<Int, Int>, Pair<Int, Int>>? {
        val a = selAnchor ?: return null
        val b = selEnd ?: return null
        if (a == b) return null
        return if (a.second < b.second || (a.second == b.second && a.first <= b.first)) a to b else b to a
    }

    val hasSelection: Boolean get() = selectionRange() != null

    fun clearSelection() {
        val had = hasSelection
        selAnchor = null
        selEnd = null
        selecting = false
        if (had) {
            onSelectionChanged(false)
            invalidate()
        }
    }

    /** The selected text (lines without trailing spaces), or `null` without a selection. */
    fun selectedText(): String? {
        val (from, to) = selectionRange() ?: return null
        val snap = session?.screen?.snapshot() ?: return null
        val cols = snap.cols.toInt()
        val out = StringBuilder()
        for (row in from.second..to.second) {
            val cells = Array(cols) { " " }
            snap.lines.getOrNull(row)?.runs?.forEach { run ->
                val c = run.col.toInt()
                if (run.wide) {
                    if (c < cols) cells[c] = run.text
                    if (c + 1 < cols) cells[c + 1] = ""
                } else {
                    var i = 0
                    var col = c
                    while (i < run.text.length && col < cols) {
                        val next = run.text.offsetByCodePoints(i, 1)
                        cells[col] = run.text.substring(i, next)
                        i = next
                        col++
                    }
                }
            }
            val c0 = if (row == from.second) from.first else 0
            val c1 = if (row == to.second) to.first else cols - 1
            if (row > from.second) out.append('\n')
            out.append(cells.slice(c0..c1.coerceAtMost(cols - 1)).joinToString("").trimEnd())
        }
        return out.toString()
    }

    /** Scrolls the history a page (Shift+PgUp / Shift+PgDn). */
    fun scrollPage(up: Boolean) {
        val s = session ?: return
        val rows = s.screen.rows().toInt().coerceAtLeast(2)
        clearSelection()
        s.screen.scroll(if (up) rows - 1 else -(rows - 1))
        invalidate()
    }

    /** The remote program asked for the mouse, and Shift isn't held (Shift: select here anyway). */
    private fun remoteMouse(e: MotionEvent): Boolean =
        (session?.modes?.mouse ?: MouseTracking.OFF) != MouseTracking.OFF && e.metaState and KeyEvent.META_SHIFT_ON == 0

    private fun report(button: MouseEncoder.Button, kind: MouseEncoder.Kind, cell: Pair<Int, Int>, e: MotionEvent) {
        val s = session ?: return
        val tracking = s.modes.mouse
        if (!MouseEncoder.wants(tracking, kind, held = remoteButton != null && button != MouseEncoder.Button.NONE)) return
        MouseEncoder.encode(
            button, kind, cell.first, cell.second, s.modes.encoding, tracking,
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
                        selAnchor = cell
                        selEnd = cell
                        selecting = true
                        dragged = false
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
                } else if (selecting && cell != selEnd) {
                    val before = hasSelection
                    selEnd = cell
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
                        val key = TermInput.Key(if (lines > 0) TerminalKey.Up else TerminalKey.Down, NoModifiers)
                        repeat(abs(lines)) { s.apply(key) }
                    }
                    else -> {
                        clearSelection()
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
        if (s != null && event.actionMasked == MotionEvent.ACTION_HOVER_MOVE && s.modes.mouse == MouseTracking.ANY && remoteMouse(event)) {
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
