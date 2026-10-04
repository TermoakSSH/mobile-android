package com.termoak.app.term

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.InputType
import android.util.TypedValue
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
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
    /** Long press: copy/paste menu. */
    var onLongPress: () -> Unit = {}

    var session: TermSession? = null
        set(value) {
            if (field === value) return
            field?.onScreenChanged = {}
            field = value
            value?.onScreenChanged = { postInvalidateOnAnimation() }
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

    override fun onDetachedFromWindow() {
        session?.onScreenChanged = {}
        super.onDetachedFromWindow()
    }

    private fun resizeToView() {
        if (width == 0 || height == 0 || cellWidth == 0f) return
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
            val next = (fontSp * detector.scaleFactor).coerceIn(8f, 24f)
            if (abs(next - fontSp) >= 0.25f) setFontSize(next)
            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            onFontSizeChanged(fontSp)
        }
    })
    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            // A tap on a link opens it; otherwise it shows the keyboard.
            val s = session
            if (s != null && cellWidth > 0f) {
                val col = ((e.x - paddingLeft) / cellWidth).toInt().coerceAtLeast(0)
                val row = ((e.y - paddingTop) / cellHeight).toInt().coerceAtLeast(0)
                s.screen.linkAt(row.toUInt(), col.toUInt())?.let { url ->
                    runCatching {
                        context.startActivity(
                            android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                    return true
                }
            }
            showKeyboard()
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            if (!scaling) {
                performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                onLongPress()
            }
        }

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
            val s = session ?: return false
            if (scaling) return true
            scrollRemainder += dy
            val lines = (scrollRemainder / cellHeight).toInt()
            if (lines != 0) {
                scrollRemainder -= lines * cellHeight
                if (s.screen.alternateScreen()) {
                    // In vim, less, htop...: arrow keys.
                    val key = if (lines > 0) TerminalKey.Down else TerminalKey.Up
                    repeat(abs(lines)) { s.key(key) }
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
        scale.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_DOWN) scaling = false
        return gestures.onTouchEvent(event) || super.onTouchEvent(event)
    }

    fun showKeyboard() {
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
                sendText(text.toString())
                return true
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
                if (event.action == KeyEvent.ACTION_DOWN) onKeyDown(event.keyCode, event)
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

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val s = session ?: return super.onKeyDown(keyCode, event)
        if (keyCode == KeyEvent.KEYCODE_BACK) return super.onKeyDown(keyCode, event)
        if (event.isCtrlPressed) s.ctrl.value = true
        if (event.isAltPressed) s.alt.value = true
        val key: TerminalKey? = when (keyCode) {
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> TerminalKey.Enter
            KeyEvent.KEYCODE_DEL -> TerminalKey.Backspace
            KeyEvent.KEYCODE_FORWARD_DEL -> TerminalKey.Delete
            KeyEvent.KEYCODE_TAB -> TerminalKey.Tab
            KeyEvent.KEYCODE_ESCAPE -> TerminalKey.Escape
            KeyEvent.KEYCODE_DPAD_UP -> TerminalKey.Up
            KeyEvent.KEYCODE_DPAD_DOWN -> TerminalKey.Down
            KeyEvent.KEYCODE_DPAD_LEFT -> TerminalKey.Left
            KeyEvent.KEYCODE_DPAD_RIGHT -> TerminalKey.Right
            KeyEvent.KEYCODE_MOVE_HOME -> TerminalKey.Home
            KeyEvent.KEYCODE_MOVE_END -> TerminalKey.End
            KeyEvent.KEYCODE_PAGE_UP -> TerminalKey.PageUp
            KeyEvent.KEYCODE_PAGE_DOWN -> TerminalKey.PageDown
            KeyEvent.KEYCODE_INSERT -> TerminalKey.Insert
            in KeyEvent.KEYCODE_F1..KeyEvent.KEYCODE_F12 ->
                TerminalKey.Function((keyCode - KeyEvent.KEYCODE_F1 + 1).toUByte())
            else -> null
        }
        if (key != null) {
            s.key(key, event.isShiftPressed)
            return true
        }
        // Physical keyboard: the character without Ctrl/Alt (those go as modifiers).
        val unicode = event.getUnicodeChar(event.metaState and (KeyEvent.META_CTRL_MASK or KeyEvent.META_ALT_MASK).inv())
        if (unicode > 0) {
            s.text(String(Character.toChars(unicode)))
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}
