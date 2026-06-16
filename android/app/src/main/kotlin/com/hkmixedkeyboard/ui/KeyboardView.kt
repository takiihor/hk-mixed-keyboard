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
import androidx.core.content.ContextCompat
import com.hkmixedkeyboard.BuildConfig
import com.hkmixedkeyboard.R

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
    }

    private data class KeyCell(
        val def: KeyboardLayout.KeyDef,
        // Visual rect used for drawing
        val rect: RectF,
        // Expanded hit rect to improve touch sensitivity and near-miss recovery
        val hitRect: RectF
    )

    private val cells = mutableListOf<KeyCell>()
    private var pressedKey: KeyCell? = null
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

    private val colorKeyBg      = ContextCompat.getColor(context, R.color.key_bg)
    private val colorSpecialBg  = ContextCompat.getColor(context, R.color.key_bg_special)
    private val colorSpaceBg    = ContextCompat.getColor(context, R.color.key_bg_space)
    private val colorPressedBg  = ContextCompat.getColor(context, R.color.key_bg_pressed)
    private val colorEnterBg    = ContextCompat.getColor(context, R.color.key_bg_enter)
    private val colorLabel      = ContextCompat.getColor(context, R.color.key_label)
    private val colorHint       = ContextCompat.getColor(context, R.color.key_radical)
    private val colorEnterLabel = ContextCompat.getColor(context, R.color.key_label_enter)
    private val colorPopBg      = ContextCompat.getColor(context, R.color.key_popup_bg)
    private val colorPopLabel   = ContextCompat.getColor(context, R.color.key_popup_label)

    private val density = resources.displayMetrics.density
    private val cornerRadius = 9f * density
    private val keyMargin = 2.5f * density
    private val hintInset = 5f * density
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
    private var spaceSwipeActive = false
    private var spaceSwipeStartX = 0f
    private var spaceSwipeLastStep = 0

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        buildCells(w.toFloat(), h.toFloat())
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
        // Size text relative to row height. The Cangjie root is the main glyph but
        // kept small so it does not dominate the key; the latin hint stays legible.
        paintLabel.textSize = (unitH * 0.30f).coerceIn(14f, 22f * density)
        paintHint.textSize = (unitH * 0.24f).coerceIn(10f, 15f * density)
        paintSpace.textSize = (unitH * 0.24f).coerceIn(12f, 16f * density)
        paintPopLabel.textSize = (unitH * 0.38f).coerceIn(18f, 26f * density)
    }

    override fun onDraw(canvas: Canvas) {
        for (cell in cells) {
            val label = cell.def.label
            val pressed = cell == pressedKey
            val isEnter = label == KEY_ENTER
            val bg = when {
                pressed -> colorPressedBg
                isEnter -> colorEnterBg
                label == KEY_SHIFT && shiftActive -> colorEnterBg
                label == KEY_SPACE -> colorSpaceBg
                isSpecial(label) -> colorSpecialBg
                else -> colorKeyBg
            }
            paintBg.color = bg
            canvas.drawRoundRect(cell.rect, cornerRadius, cornerRadius, paintBg)

            val cx = cell.rect.centerX()
            val cy = cell.rect.centerY()

            // Letter faces follow the shift state; everything else renders verbatim.
            val shown = if (isLetter(label) && !shiftActive) label.lowercase() else label

            when {
                label == KEY_SPACE -> {
                    paintSpace.color = colorHint
                    canvas.drawText(spaceLabel, cx,
                        cy - (paintSpace.ascent() + paintSpace.descent()) / 2, paintSpace)
                }
                label == KEY_MODE -> {
                    paintLabel.color = colorLabel
                    canvas.drawText(modeLabel, cx,
                        cy - (paintLabel.ascent() + paintLabel.descent()) / 2, paintLabel)
                }
                showCangjieRoots && CANGJIE.containsKey(label) -> {
                    // Big Cangjie root, centred; small latin hint in the top-right.
                    paintLabel.color = colorLabel
                    canvas.drawText(CANGJIE[label]!!, cx,
                        cy - (paintLabel.ascent() + paintLabel.descent()) / 2, paintLabel)
                    paintHint.color = colorHint
                    canvas.drawText(shown, cell.rect.right - hintInset,
                        cell.rect.top + hintInset - paintHint.ascent(), paintHint)
                }
                label == KEY_SHIFT && shiftActive -> {
                    paintLabel.color = colorEnterLabel
                    canvas.drawText(if (shiftLocked) "⇪" else label, cx,
                        cy - (paintLabel.ascent() + paintLabel.descent()) / 2, paintLabel)
                }
                else -> {
                    paintLabel.color = if (isEnter) colorEnterLabel else colorLabel
                    canvas.drawText(shown, cx,
                        cy - (paintLabel.ascent() + paintLabel.descent()) / 2, paintLabel)
                }
            }
        }

        // Draw popup bubble on top of everything else.
        pressedKey?.let { cell ->
            if (shouldShowPopup(cell.def.label)) drawKeyPopup(canvas, cell)
        }
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
        paintPopBg.color = colorPopBg
        canvas.drawRoundRect(popRect, cornerRadius, cornerRadius, paintPopBg)

        val label = cell.def.label
        val shown = if (isLetter(label) && !shiftActive) label.lowercase() else label
        paintPopLabel.color = colorPopLabel
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
                val cell = cellForDown(event.getX(idx), event.getY(idx))
                pressedKey = cell
                if (cell != null) haptic()
                invalidate()
                performClick()
                when (cell?.def?.label) {
                    KEY_BACKSPACE -> holdController.pressRepeating {
                        emitKey(KEY_BACKSPACE)
                    }
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
                    KEY_SPACE -> {
                        spaceSwipeActive = true
                        spaceSwipeStartX = event.getX(idx)
                        spaceSwipeLastStep = 0
                    }
                    null -> Unit
                    else -> if (KeyTouchPolicy.emitsOnPress(cell.def.label)) {
                        emitKey(cell.def.label)
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val idx = event.actionIndex
                val current = pressedKey
                val x = event.getX(idx)
                val y = event.getY(idx)
                if (current != null && current.hitRect.contains(x, y)) {
                    when (current.def.label) {
                        KEY_BACKSPACE, KEY_QUESTION, KEY_SYMBOL -> holdController.release()
                        KEY_SPACE -> {
                            val wasSwiped = spaceSwipeLastStep != 0
                            spaceSwipeActive = false
                            spaceSwipeLastStep = 0
                            if (!wasSwiped) emitKey(KEY_SPACE)
                        }
                        else -> if (KeyTouchPolicy.emitsOnRelease(current.def.label)) {
                            emitKey(current.def.label)
                        }
                    }
                } else {
                    holdController.cancel()
                    spaceSwipeActive = false
                    spaceSwipeLastStep = 0
                }
                pressedKey = null
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                val current = pressedKey
                if (current != null) {
                    if (current.def.label == KEY_SPACE && spaceSwipeActive) {
                        val dx = event.x - spaceSwipeStartX
                        val currentStep = (dx / cursorStepPx).toInt()
                        val delta = currentStep - spaceSwipeLastStep
                        if (delta != 0) {
                            spaceSwipeLastStep = currentStep
                            val dir = if (delta > 0) 1 else -1
                            repeat(kotlin.math.abs(delta)) { keyListener?.onSpaceSwipe(dir) }
                        }
                    } else if (!current.hitRect.contains(event.x, event.y)) {
                        holdController.cancel()
                        pressedKey = null
                        invalidate()
                    }
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                holdController.cancel()
                spaceSwipeActive = false
                spaceSwipeLastStep = 0
                pressedKey = null
                invalidate()
            }
        }
        return true
    }

    override fun performClick(): Boolean { super.performClick(); return true }

    override fun onDetachedFromWindow() {
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

    private fun isSpecial(label: String) = label in setOf(
        KEY_BACKSPACE, KEY_SHIFT, KEY_ENTER, KEY_EMOJI, KEY_SYMBOL, KEY_MODE
    )
}
