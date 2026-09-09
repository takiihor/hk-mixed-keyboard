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
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeProvider
import com.hkmixedkeyboard.BuildConfig

class KeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    interface KeyListener {
        fun onKey(label: String)
        fun onKeyLongPress(label: String)
        fun onSpaceSwipe(delta: Int)
        /** A held backspace has escalated from characters to whole words. */
        fun onBackspaceWord() {}
    }

    var keyListener: KeyListener? = null
    var showCangjieRoots: Boolean = true
    var vibrationEnabled: Boolean = true

    // Shared low-latency haptic engine, injected by the IME service. Null only in
    // isolated tests, where typing falls back to the standard View haptic.
    var haptics: TypingHapticEngine? = null

    // Label drawn on the space bar (the active input scheme).
    var spaceLabel: String = "空格"
    /** Whether the digits row is drawn; iOS keyboards do not have one. */
    var showNumberRow: Boolean = true
        set(value) {
            if (field == value) return
            field = value
            if (width > 0 && height > 0) buildCells(width.toFloat(), height.toFloat())
            requestLayout()
            invalidate()
        }
    /** Label and accent for the return key, from the focused field's action. */
    var enterAction: com.hkmixedkeyboard.ime.EnterKeyAction.Action =
        com.hkmixedkeyboard.ime.EnterKeyAction.Action("↵", accented = false)
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }
    /** True while a hold-and-drag on space is steering the caret. */
    private var cursorMode = false

    // Live indicator drawn on the scheme-switch key (速 = 速成, 粵 = 粵拼).
    var modeLabel: String = "速"

    // Shift state, driven by the IME. When active the letter faces render uppercase
    // and the ⇧ key is highlighted; `locked` (caps-lock) is shown a touch stronger.
    var shiftActive: Boolean = false
    var shiftLocked: Boolean = false

    // Key label constants for special keys
    companion object {
        const val KEY_BACKSPACE = KeyboardLayout.KEY_BACKSPACE
        const val KEY_SHIFT = KeyboardLayout.KEY_SHIFT
        const val KEY_ENTER = KeyboardLayout.KEY_ENTER
        const val KEY_SPACE = KeyboardLayout.KEY_SPACE
        /** Shown on the space bar while hold-and-drag is steering the caret. */
        const val CURSOR_MODE_LABEL = "◂ 移動游標 ▸"
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
    // Stable virtual IDs follow the immutable keyboard layout order: 1 maps to the
    // first key, 2 to the second, and so on. The cells are rebuilt only from that
    // same layout when size changes.
    private val keyboardAccessibilityProvider = KeyboardAccessibilityProvider()
    private var accessibilityFocusedVirtualId = View.NO_ID
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
            onSwipe = { keyListener?.onSpaceSwipe(it) },
            onCursorModeChanged = { active ->
                cursorMode = active
                // A pulse on entry is the other half of the iOS affordance: the
                // keyboard going blank is seen, the tick is felt.
                if (active) haptic()
                invalidate()
            }
        )
    }

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        isFocusable = true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        buildCells(w.toFloat(), h.toFloat())
        // The virtual node bounds are the cell rects, so a relayout invalidates
        // everything an accessibility service has cached about this keyboard.
        if (isAttachedToWindow) {
            sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredHeight =
            KeyboardLayout.keyboardHeightPx(resources.displayMetrics.density, showNumberRow)
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
        for (cell in KeyboardLayout.buildCells(w, h, showNumberRow)) {
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
                isEnter && enterAction.accented && themeColors.enterActionBackground != 0 ->
                    themeColors.enterActionBackground
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

            // iOS blanks every keycap while the space bar is acting as a trackpad,
            // and that — not a changed space label — is what tells the user the
            // keyboard has become a cursor surface. Only the space bar keeps text.
            if (cursorMode && label != KEY_SPACE) continue

            // Letter faces follow the shift state; everything else renders verbatim.
            val shown = when {
                isLetter(label) && !shiftActive -> label.lowercase()
                isEnter -> enterAction.label
                else -> label
            }

            when {
                label == KEY_SPACE -> {
                    paintSpace.color = themeColors.hint
                    canvas.drawText(if (cursorMode) CURSOR_MODE_LABEL else spaceLabel, cx,
                        cy - (paintSpace.ascent() + paintSpace.descent()) / 2, paintSpace)
                }
                label == KEY_MODE -> {
                    paintLabel.color = themeColors.label
                    canvas.drawText(modeLabel, cx,
                        cy - (paintLabel.ascent() + paintLabel.descent()) / 2, paintLabel)
                }
                showCangjieRoots && KeyboardAccessibilityLabels.cangjieRootFor(label) != null -> {
                    // Centred Cangjie root; Latin hint anchored inside the top-right.
                    paintRoot.color = themeColors.label
                    canvas.drawText(KeyboardAccessibilityLabels.cangjieRootFor(label)!!, cx,
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
                    paintLabel.color = when {
                        isEnter && enterAction.accented && themeColors.enterActionLabel != 0 ->
                            themeColors.enterActionLabel
                        isEnter -> themeColors.enterLabel
                        else -> themeColors.label
                    }
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
            // Every hold-gesture key except space resolves the same way, so this
            // must not be a hard-coded list: a key added to usesHoldGesture but
            // missed here never releases, and its pending long press fires on what
            // the user meant as a tap.
            if (label == KEY_SPACE) {
                spaceGestureController.release(releasedInside = inside)
            } else if (inside) {
                holdController.release()
            } else {
                holdController.cancel()
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
            KEY_BACKSPACE -> holdController.pressRepeating(
                action = { emitKey(KEY_BACKSPACE) },
                onEscalate = { keyListener?.onBackspaceWord() }
            )
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
            KEY_MODE -> holdController.pressLong(
                tap = { emitKey(KEY_MODE) },
                longPress = { keyListener?.onKeyLongPress(KEY_MODE) }
            )
            KEY_SPACE -> {
                spaceGestureController.press(startX = x)
            }
            // 。and the digits: tap commits the face, hold commits the alternate.
            else -> if (MainKeyboardLongPressPolicy.longPressTextFor(label) != null) {
                holdController.pressLong(
                    tap = { emitKey(MainKeyboardLongPressPolicy.shortPressTextFor(label)) },
                    longPress = { keyListener?.onKeyLongPress(label) }
                )
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

    override fun getAccessibilityNodeProvider(): AccessibilityNodeProvider =
        keyboardAccessibilityProvider

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = KeyboardView::class.java.name
        info.contentDescription = "鍵盤"
        info.isScrollable = false
        for (index in cells.indices) info.addChild(this, virtualIdFor(index))
    }

    override fun onDetachedFromWindow() {
        cancelActiveTouches()
        accessibilityFocusedVirtualId = View.NO_ID
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

    private fun virtualIdFor(cellIndex: Int): Int = cellIndex + 1

    private fun cellForVirtualId(virtualViewId: Int): KeyCell? =
        cells.getOrNull(virtualViewId - 1)

    private fun accessibilityDescription(cell: KeyCell): String =
        KeyboardAccessibilityLabels.descriptionFor(
            label = cell.def.label,
            showCangjieRoots = showCangjieRoots,
            spaceLabel = spaceLabel,
            modeLabel = modeLabel,
            shiftActive = shiftActive,
            shiftLocked = shiftLocked
        )

    @Suppress("DEPRECATION") // Platform virtual-node creation remains required on minSdk 26.
    private fun sendVirtualAccessibilityEvent(virtualViewId: Int, eventType: Int) {
        val cell = cellForVirtualId(virtualViewId) ?: return
        val event = AccessibilityEvent.obtain(eventType).apply {
            packageName = context.packageName
            className = android.widget.Button::class.java.name
            contentDescription = accessibilityDescription(cell)
            text.add(accessibilityDescription(cell))
            setSource(this@KeyboardView, virtualViewId)
        }
        parent?.requestSendAccessibilityEvent(this, event) ?: sendAccessibilityEventUnchecked(event)
    }

    @Suppress("DEPRECATION") // Platform virtual-node creation remains required on minSdk 26.
    private inner class KeyboardAccessibilityProvider : AccessibilityNodeProvider() {
        override fun createAccessibilityNodeInfo(virtualViewId: Int): AccessibilityNodeInfo? =
            when (virtualViewId) {
                HOST_VIEW_ID -> createHostNode()
                else -> createKeyNode(virtualViewId)
            }

        override fun findAccessibilityNodeInfosByText(
            searched: String?,
            virtualViewId: Int
        ): List<AccessibilityNodeInfo> {
            val query = searched?.toString()?.trim().orEmpty()
            if (query.isEmpty()) return emptyList()
            return cells.mapIndexedNotNull { index, cell ->
                if (accessibilityDescription(cell).contains(query, ignoreCase = true)) {
                    createKeyNode(virtualIdFor(index))
                } else {
                    null
                }
            }
        }

        override fun findFocus(focus: Int): AccessibilityNodeInfo? =
            if (focus == AccessibilityNodeInfo.FOCUS_ACCESSIBILITY &&
                accessibilityFocusedVirtualId != View.NO_ID) {
                createKeyNode(accessibilityFocusedVirtualId)
            } else {
                null
            }

        override fun performAction(
            virtualViewId: Int,
            action: Int,
            arguments: android.os.Bundle?
        ): Boolean {
            val cell = cellForVirtualId(virtualViewId) ?: return false
            return when (action) {
                AccessibilityNodeInfo.ACTION_CLICK -> {
                    emitKey(cell.def.label)
                    sendVirtualAccessibilityEvent(virtualViewId, AccessibilityEvent.TYPE_VIEW_CLICKED)
                    true
                }
                AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS -> {
                    if (accessibilityFocusedVirtualId == virtualViewId) return false
                    val previous = accessibilityFocusedVirtualId
                    accessibilityFocusedVirtualId = virtualViewId
                    if (previous != View.NO_ID) {
                        sendVirtualAccessibilityEvent(
                            previous,
                            AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED
                        )
                    }
                    sendVirtualAccessibilityEvent(
                        virtualViewId,
                        AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED
                    )
                    true
                }
                AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS -> {
                    if (accessibilityFocusedVirtualId != virtualViewId) return false
                    accessibilityFocusedVirtualId = View.NO_ID
                    sendVirtualAccessibilityEvent(
                        virtualViewId,
                        AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED
                    )
                    true
                }
                else -> false
            }
        }

        private fun createHostNode(): AccessibilityNodeInfo = AccessibilityNodeInfo.obtain().apply {
            setSource(this@KeyboardView)
            packageName = context.packageName
            className = KeyboardView::class.java.name
            contentDescription = "鍵盤"
            isEnabled = this@KeyboardView.isEnabled
            isFocusable = true
            for (index in cells.indices) addChild(this@KeyboardView, virtualIdFor(index))
        }

        private fun createKeyNode(virtualViewId: Int): AccessibilityNodeInfo? {
            val cell = cellForVirtualId(virtualViewId) ?: return null
            return AccessibilityNodeInfo.obtain().apply {
                setSource(this@KeyboardView, virtualViewId)
                setParent(this@KeyboardView)
                packageName = context.packageName
                className = android.widget.Button::class.java.name
                contentDescription = accessibilityDescription(cell)
                setBoundsInParent(
                    Rect(
                        cell.rect.left.toInt(),
                        cell.rect.top.toInt(),
                        cell.rect.right.toInt(),
                        cell.rect.bottom.toInt()
                    )
                )
                isVisibleToUser = this@KeyboardView.isShown
                isEnabled = this@KeyboardView.isEnabled
                isFocusable = true
                isClickable = true
                addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK)
                if (accessibilityFocusedVirtualId == virtualViewId) {
                    isAccessibilityFocused = true
                    addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLEAR_ACCESSIBILITY_FOCUS)
                } else {
                    addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_ACCESSIBILITY_FOCUS)
                }
            }
        }
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
