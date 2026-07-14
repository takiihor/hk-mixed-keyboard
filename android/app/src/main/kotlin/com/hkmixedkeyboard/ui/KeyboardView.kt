package com.hkmixedkeyboard.ui

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import com.hkmixedkeyboard.BuildConfig

class KeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    interface KeyListener {
        fun onKey(label: String)
        fun onKeyLongPress(label: String)
        fun onSpaceSwipe(delta: Int)
    }

    var keyListener: KeyListener? = null
    var showCangjieRoots: Boolean = true
    var vibrationEnabled: Boolean = true

    // Shared low-latency haptic engine, injected by the IME service. Null only in
    // isolated tests, where typing falls back to the standard View haptic.
    var haptics: TypingHapticEngine? = null

    // Label drawn on the space bar (the active input scheme).
    var spaceLabel: String = "空格"

    // Live indicator drawn on the scheme-switch key (速 = 速成, 粵 = 粵拼).
    var modeLabel: String = "速"

    // Shift state, driven by the IME. When active the letter faces render uppercase
    // and the ⇧ key is highlighted; `locked` (caps-lock) is shown a touch stronger.
    var shiftActive: Boolean = false
    var shiftLocked: Boolean = false

    private val CANGJIE = mapOf(
        "A" to "日", "B" to "月", "C" to "金", "D" to "木", "E" to "水",
        "F" to "火", "G" to "土", "H" to "竹", "I" to "戈", "J" to "十",
        "K" to "大", "L" to "中", "M" to "一", "N" to "弓", "O" to "人",
        "P" to "心", "Q" to "手", "R" to "口", "S" to "尸", "T" to "廿",
        "U" to "山", "V" to "女", "W" to "田", "X" to "難", "Y" to "卜",
        "Z" to "重"
    )

    // Key label constants for special keys
    companion object {
        const val KEY_BACKSPACE = KeyboardLayout.KEY_BACKSPACE
        const val KEY_SHIFT = KeyboardLayout.KEY_SHIFT
        const val KEY_ENTER = KeyboardLayout.KEY_ENTER
        const val KEY_SPACE = KeyboardLayout.KEY_SPACE
        const val KEY_EMOJI = KeyboardLayout.KEY_EMOJI
        const val KEY_SYMBOL = KeyboardLayout.KEY_SYMBOL
        const val KEY_MODE = KeyboardLayout.KEY_MODE
        const val KEY_COMMA = KeyboardLayout.KEY_COMMA
        const val KEY_PERIOD = KeyboardLayout.KEY_PERIOD
        const val KEY_QUESTION = KeyboardLayout.KEY_QUESTION
        const val KEY_EXCLAIM = KeyboardLayout.KEY_EXCLAIM
        private const val LATENCY_LOG_TAG = "HkIme.Latency"
        // Hoisted out of isSpecial() so onDraw doesn't allocate a Set per key per frame.
        private val SPECIAL_KEYS = setOf(
            KEY_BACKSPACE, KEY_SHIFT, KEY_ENTER, KEY_EMOJI, KEY_SYMBOL, KEY_MODE
        )
    }

    private data class KeyCell(
        val def: KeyboardLayout.KeyDef,
        // Visual rect used for drawing
        val rect: RectF,
        // Expanded hit rect to improve touch sensitivity and near-miss recovery
        val hitRect: RectF
    )

    private val cells = mutableListOf<KeyCell>()
    // Per-pointer press tracking. A single shared "pressed key" dropped characters
    // during fast two-finger typing (rollover): the second finger's DOWN overwrote
    // the first finger's key before its UP could emit it. Mapping pointerId → cell
    // lets every finger resolve and emit independently.
    private val pointerCells = android.util.SparseArray<KeyCell>()
    // Pointer that owns the active single-finger gesture (backspace auto-repeat,
    // 符/？！ long-press, or the space-bar cursor swipe); -1 when none is active.
    private var gesturePointerId = -1
    private val holdController = HoldActionController(
        object : HoldActionController.Scheduler {
            private val handler = Handler(Looper.getMainLooper())

            override fun schedule(
                delayMs: Long,
                action: () -> Unit
            ): HoldActionController.Cancellable {
                val runnable = Runnable(action)
                handler.postDelayed(runnable, delayMs)
                return HoldActionController.Cancellable { handler.removeCallbacks(runnable) }
            }
        }
    )

    private val paintBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val paintLabel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    private val paintRoot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    private val paintHint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.RIGHT
    }
    private val paintSpace = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    private val paintPopBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val paintPopLabel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    private val paintKeyShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    var themeColors: KeyboardThemeColors = KeyboardThemeColors.from(context)
        set(value) {
            field = value
            invalidate()
        }

    private val density = resources.displayMetrics.density
    private val cornerRadius = 9f * density
    private val keyMargin = 2.5f * density
    // Extra hit slop in px applied around each key for detection (does not affect
    // drawing). Generous so light / slightly-off taps still register; the enlarged
    // rects overlap in the gaps, and cellForDown() resolves overlaps by nearest key
    // centre so the intended key is still chosen.
    private val hitInflation = 8f * density
    // Fallback selection radius (px) when a tap lands in a gap outside every hit
    // rect: snap to the nearest key centre within this distance.
    private val nearestSelectRadius = 28f * density
    // Space-bar swipe: pixels of horizontal travel per cursor step.
    private val cursorStepPx = 18f * density

    private var unitH = 0f
    private val spaceGestureController by lazy {
        SpaceGestureController(
            holdController = holdController,
            cursorStepPx = cursorStepPx,
            onTap = { emitKey(KEY_SPACE) },
            onLongPress = { keyListener?.onKeyLongPress(KEY_SPACE) },
            onSwipe = { keyListener?.onSpaceSwipe(it) }
        )
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        buildCells(w.toFloat(), h.toFloat())
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredHeight = KeyboardLayout.keyboardHeightPx(resources.displayMetrics.density)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)
        val measuredHeight = when (heightMode) {
            MeasureSpec.EXACTLY -> heightSize
            MeasureSpec.AT_MOST -> minOf(desiredHeight, heightSize).coerceAtLeast(suggestedMinimumHeight)
            else -> desiredHeight.coerceAtLeast(suggestedMinimumHeight)
        }
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), measuredHeight)
    }

    private fun buildCells(w: Float, h: Float) {
        cells.clear()
        for (cell in KeyboardLayout.buildCells(w, h)) {
            val drawRect = RectF(
                cell.bounds.left + keyMargin,
                cell.bounds.top + keyMargin,
                cell.bounds.right - keyMargin,
                cell.bounds.bottom - keyMargin
            )
            val hitRect = RectF(
                drawRect.left - hitInflation,
                drawRect.top - hitInflation,
                drawRect.right + hitInflation,
                drawRect.bottom + hitInflation
            )
            cells += KeyCell(cell.key, drawRect, hitRect)
        }
        unitH = h / KeyboardLayout.totalHeightWeight
        // Preserve the previous 56dp-row visual sizes while compacting row geometry.
        paintLabel.textSize = KeyboardTypographyPolicy.MAIN_LABEL_TEXT_SIZE_SP * density
        paintRoot.textSize = KeyboardTypographyPolicy.CANGJIE_ROOT_TEXT_SIZE_SP * density
        paintHint.textSize = KeyboardTypographyPolicy.LATIN_HINT_TEXT_SIZE_SP * density
        paintSpace.textSize = KeyboardTypographyPolicy.SPACE_LABEL_TEXT_SIZE_SP * density
        paintPopLabel.textSize = KeyboardTypographyPolicy.POPUP_LABEL_TEXT_SIZE_SP * density
    }

    override fun onDraw(canvas: Canvas) {
        for (cell in cells) {
            val label = cell.def.label
            val pressed = isPressed(cell)
            val isEnter = label == KEY_ENTER
            val bg = when {
                pressed && isSpecial(label) -> themeColors.pressedSpecialKeyBackground
                pressed -> themeColors.pressedKeyBackground
                isEnter -> themeColors.enterKeyBackground
                label == KEY_SHIFT && shiftActive -> themeColors.enterKeyBackground
                label == KEY_SPACE -> themeColors.spaceKeyBackground
                isSpecial(label) -> themeColors.specialKeyBackground
                else -> themeColors.keyBackground
            }
            if (android.graphics.Color.alpha(themeColors.keyShadow) > 0) {
                paintKeyShadow.color = themeColors.keyShadow
                canvas.drawRoundRect(
                    cell.rect.left,
                    cell.rect.top + density,
                    cell.rect.right,
                    cell.rect.bottom + density,
                    cornerRadius,
                    cornerRadius,
                    paintKeyShadow
                )
            }
            paintBg.color = bg
            canvas.drawRoundRect(cell.rect, cornerRadius, cornerRadius, paintBg)

            val cx = cell.rect.centerX()
            val cy = cell.rect.centerY()

            // Letter faces follow the shift state; everything else renders verbatim.
            val shown = if (isLetter(label) && !shiftActive) label.lowercase() else label

            when {
                label == KEY_SPACE -> {
                    paintSpace.color = themeColors.hint
                    canvas.drawText(spaceLabel, cx,
                        cy - (paintSpace.ascent() + paintSpace.descent()) / 2, paintSpace)
                }
                label == KEY_MODE -> {
                    paintLabel.color = themeColors.label
                    canvas.drawText(modeLabel, cx,
                        cy - (paintLabel.ascent() + paintLabel.descent()) / 2, paintLabel)
                }
                showCangjieRoots && CANGJIE.containsKey(label) -> {
                    // Centred Cangjie root; Latin hint anchored inside the top-right.
                    paintRoot.color = themeColors.label
                    canvas.drawText(CANGJIE[label]!!, cx,
                        cy - (paintRoot.ascent() + paintRoot.descent()) / 2, paintRoot)
                    paintHint.color = themeColors.hint
                    canvas.drawText(
                        shown,
                        KeyboardTypographyPolicy.latinHintX(cell.rect.right, density),
                        KeyboardTypographyPolicy.latinHintBaseline(
                            cell.rect.top,
                            density,
                            paintHint.ascent()
                        ),
                        paintHint
                    )
                }
                label == KEY_SHIFT && shiftActive -> {
                    paintLabel.color = themeColors.enterLabel
                    canvas.drawText(if (shiftLocked) "⇪" else label, cx,
                        cy - (paintLabel.ascent() + paintLabel.descent()) / 2, paintLabel)
                }
                else -> {
                    paintLabel.color = if (isEnter) themeColors.enterLabel else themeColors.label
                    canvas.drawText(shown, cx,
                        cy - (paintLabel.ascent() + paintLabel.descent()) / 2, paintLabel)
                }
            }
        }

        // Draw popup bubbles on top of everything else — one per pressed finger.
        for (i in 0 until pointerCells.size()) {
            val cell = pointerCells.valueAt(i)
            if (shouldShowPopup(cell.def.label)) drawKeyPopup(canvas, cell)
        }
    }

    private fun isPressed(cell: KeyCell): Boolean {
        for (i in 0 until pointerCells.size()) {
            if (pointerCells.valueAt(i) == cell) return true
        }
        return false
    }

    private fun shouldShowPopup(label: String): Boolean =
        label != KEY_SPACE && label != KEY_ENTER &&
        label != KEY_SHIFT && label != KEY_BACKSPACE &&
        label != KEY_EMOJI && label != KEY_SYMBOL && label != KEY_MODE

    private fun drawKeyPopup(canvas: Canvas, cell: KeyCell) {
        val popW = cell.rect.width() * 1.3f
        val popH = cell.rect.height() * 0.75f
        val cx = cell.rect.centerX()
        val popLeft = (cx - popW / 2f).coerceIn(0f, width.toFloat() - popW)
        val popTop = cell.rect.top - popH - keyMargin
        if (popTop < 0f) return  // number row: no space above, skip

        val popRect = RectF(popLeft, popTop, popLeft + popW, popTop + popH)
        paintPopBg.color = themeColors.popupBackground
        canvas.drawRoundRect(popRect, cornerRadius, cornerRadius, paintPopBg)

        val label = cell.def.label
        val shown = if (isLetter(label) && !shiftActive) label.lowercase() else label
        paintPopLabel.color = themeColors.popupLabel
        val pcy = popRect.centerY()
        canvas.drawText(shown, cx, pcy - (paintPopLabel.ascent() + paintPopLabel.descent()) / 2, paintPopLabel)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                // Perf: mark touch arrival on UI thread
                if (com.hkmixedkeyboard.BuildConfig.PERF_TRACING) {
                    android.util.Log.d(LATENCY_LOG_TAG, "touch_received ns=" + SystemClock.elapsedRealtimeNanos())
                }
                val idx = event.actionIndex
                onPointerDown(event.getPointerId(idx), event.getX(idx), event.getY(idx))
                invalidate()
                performClick()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val idx = event.actionIndex
                onPointerUp(event.getPointerId(idx), event.getX(idx), event.getY(idx))
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                // A MOVE batches every active pointer; advance each one independently.
                for (i in 0 until event.pointerCount) {
                    onPointerMove(event.getPointerId(i), event.getX(i), event.getY(i))
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                cancelActiveTouches()
                invalidate()
            }
        }
        return true
    }

    private fun onPointerDown(pointerId: Int, x: Float, y: Float) {
        val cell = cellForDown(x, y) ?: return
        pointerCells.put(pointerId, cell)
        haptic()
        val label = cell.def.label
        if (KeyTouchPolicy.usesHoldGesture(label)) {
            // Backspace repeat, 符/？！ long-press and the space swipe are inherently
            // single-finger; let only the first such finger drive the shared gesture.
            if (gesturePointerId == -1) {
                gesturePointerId = pointerId
                beginGesture(label, x)
            }
        } else if (KeyTouchPolicy.emitsOnPress(label)) {
            emitKey(label)
        }
    }

    private fun onPointerUp(pointerId: Int, x: Float, y: Float) {
        val cell = pointerCells.get(pointerId) ?: return
        pointerCells.remove(pointerId)
        val label = cell.def.label
        val inside = cell.hitRect.contains(x, y)
        if (pointerId == gesturePointerId) {
            when (label) {
                KEY_BACKSPACE, KEY_QUESTION, KEY_PERIOD, KEY_SYMBOL ->
                    if (inside) holdController.release() else holdController.cancel()
                KEY_SPACE -> {
                    spaceGestureController.release(releasedInside = inside)
                }
            }
            gesturePointerId = -1
        } else if (KeyTouchPolicy.emitsOnRelease(label, releasedInside = inside)) {
            emitKey(label)
        }
    }

    private fun onPointerMove(pointerId: Int, x: Float, y: Float) {
        val cell = pointerCells.get(pointerId) ?: return
        if (pointerId == gesturePointerId && cell.def.label == KEY_SPACE) {
            val verticallyInside = y >= cell.hitRect.top && y <= cell.hitRect.bottom
            spaceGestureController.move(x, holdEligible = verticallyInside)
            return
        }
        // Finger slid off the key it pressed: drop it (and cancel a gesture it owned).
        if (!cell.hitRect.contains(x, y)) {
            if (pointerId == gesturePointerId) {
                cancelGesture()
                gesturePointerId = -1
            }
            pointerCells.remove(pointerId)
            invalidate()
        }
    }

    private fun beginGesture(label: String, x: Float) {
        when (label) {
            KEY_BACKSPACE -> holdController.pressRepeating { emitKey(KEY_BACKSPACE) }
            KEY_SYMBOL -> holdController.pressLong(
                tap = { emitKey(KEY_SYMBOL) },
                longPress = { keyListener?.onKeyLongPress(KEY_SYMBOL) }
            )
            KEY_QUESTION -> holdController.pressLong(
                tap = { emitKey(KEY_QUESTION) },
                longPress = {
                    keyListener?.onKeyLongPress(KEY_QUESTION)
                    emitKey(KEY_EXCLAIM)
                }
            )
            KEY_PERIOD -> holdController.pressLong(
                tap = { emitKey(MainKeyboardLongPressPolicy.shortPressTextFor(KEY_PERIOD)) },
                longPress = { keyListener?.onKeyLongPress(KEY_PERIOD) }
            )
            KEY_SPACE -> {
                spaceGestureController.press(startX = x)
            }
        }
    }

    private fun cancelGesture() {
        spaceGestureController.cancel()
    }

    private fun cancelActiveTouches() {
        cancelGesture()
        gesturePointerId = -1
        pointerCells.clear()
    }

    override fun performClick(): Boolean { super.performClick(); return true }

    override fun onDetachedFromWindow() {
        cancelActiveTouches()
        // The engine is shared and owned by the IME service, so we don't release it
        // here; just stop any tick still in flight for this view.
        haptics?.cancel()
        super.onDetachedFromWindow()
    }

    // Down selection: among every key whose (enlarged) hit rect covers the touch,
    // pick the one whose centre is closest — this disambiguates the overlap zones the
    // inflated rects create, so a tap near a boundary still selects the nearest key.
    // If the touch lands in a gap covered by no hit rect, fall back to the nearest key
    // centre within nearestSelectRadius.
    private fun cellForDown(x: Float, y: Float): KeyCell? {
        var best: KeyCell? = null
        var bestDist2 = Float.MAX_VALUE
        for (c in cells) {
            if (!c.hitRect.contains(x, y)) continue
            val dx = x - c.rect.centerX()
            val dy = y - c.rect.centerY()
            val d2 = dx * dx + dy * dy
            if (d2 < bestDist2) {
                bestDist2 = d2
                best = c
            }
        }
        if (best != null) return best

        bestDist2 = nearestSelectRadius * nearestSelectRadius
        for (c in cells) {
            val dx = x - c.rect.centerX()
            val dy = y - c.rect.centerY()
            val d2 = dx * dx + dy * dy
            if (d2 < bestDist2) {
                bestDist2 = d2
                best = c
            }
        }
        return best
    }

    private fun emitKey(label: String) {
        if (com.hkmixedkeyboard.BuildConfig.PERF_TRACING) {
            android.util.Log.d(LATENCY_LOG_TAG, "key_dispatch ns=" + SystemClock.elapsedRealtimeNanos())
        }
        keyListener?.onKey(label)
    }

    private fun haptic() {
        val engine = haptics
        if (engine != null) {
            engine.perform(enabled = vibrationEnabled) {
                HapticFeedbackPolicy.performTyping(this, enabled = true)
            }
        } else {
            HapticFeedbackPolicy.performTyping(this, vibrationEnabled)
        }
    }

    // Debug-only latency logging removed from hot path; gated above by PERF_TRACING.

    private fun isLetter(label: String): Boolean =
        label.length == 1 && label[0] in 'A'..'Z'

    private fun isSpecial(label: String) = label in SPECIAL_KEYS
}
