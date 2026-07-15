package com.hkmixedkeyboard.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeProvider
import androidx.core.content.ContextCompat
import com.hkmixedkeyboard.R

/**
 * Renders a [SymbolKeyboardSpec] page and turns touch positions into selected
 * key specs. Symbol content, labels, colours, geometry policy, and IME actions
 * deliberately live outside this view.
 */
class SymbolPageView(context: Context) : View(context) {

    var onKeyTap: ((SymbolKeySpec) -> Unit)? = null

    // Transitional callbacks preserve source compatibility for the panel wiring
    // while the IME is migrated to [onKeyTap].
    var onSymbolTap: ((String) -> Unit)? = null
    var onSpace: (() -> Unit)? = null
    var onBackspace: (() -> Unit)? = null
    var onEnter: (() -> Unit)? = null
    var onClose: (() -> Unit)? = null

    var vibrationEnabled: Boolean = true
    var haptics: TypingHapticEngine? = null

    var themeColors: KeyboardThemeColors = KeyboardThemeColors.from(context)
        set(value) {
            field = value
            invalidate()
        }

    var symbolPage: SymbolPage = SymbolPage.COMMON
        set(value) {
            if (field == value) return
            field = value
            rebuildKeys()
            contentDescription = localizedPageAnnouncement(value)
            announceForAccessibility(localizedPageAnnouncement(value))
            invalidate()
        }

    var enterAction: SymbolEnterAction = SymbolEnterAction.RETURN
        set(value) {
            if (field == value) return
            field = value
            rebuildKeys()
            invalidate()
        }

    private data class RenderedKey(
        val spec: SymbolKeySpec,
        val geometry: SymbolKeyGeometry
    )

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }
    private val indicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val iconCache = mutableMapOf<SymbolKeyIcon, Drawable>()
    private val renderedKeys = mutableListOf<RenderedKey>()
    private var activeKey: RenderedKey? = null
    private var selectedAlternative: SymbolKeySpec? = null
    private var layout: SymbolKeyboardGeometry? = null
    private var popupEntries: List<RenderedKey> = emptyList()

    private val longPress = SymbolLongPressController(object : SymbolLongPressController.Scheduler {
        private val handler = Handler(Looper.getMainLooper())

        override fun schedule(delayMs: Long, action: () -> Unit): SymbolLongPressController.Cancellable {
            val runnable = Runnable(action)
            handler.postDelayed(runnable, delayMs)
            return SymbolLongPressController.Cancellable { handler.removeCallbacks(runnable) }
        }
    }).apply {
        onPopupVisibilityChanged = {
            rebuildPopupEntries()
            invalidate()
        }
    }
    private val backspaceHold = HoldActionController(object : HoldActionController.Scheduler {
        private val handler = Handler(Looper.getMainLooper())

        override fun schedule(delayMs: Long, action: () -> Unit): HoldActionController.Cancellable {
            val runnable = Runnable(action)
            handler.postDelayed(runnable, delayMs)
            return HoldActionController.Cancellable { handler.removeCallbacks(runnable) }
        }
    })

    private val accessibilityProvider = SymbolAccessibilityNodeProvider()

    init {
        isClickable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        contentDescription = localizedPageAnnouncement(symbolPage)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildKeys()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(themeColors.symbolKeyboardBackground)
        renderedKeys.forEach { drawKey(canvas, it, isPopup = false) }
        popupEntries.forEach { drawKey(canvas, it, isPopup = true) }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val key = keyAt(event.x, event.y) ?: return false
                activeKey = key
                selectedAlternative = null
                selectionHaptic()
                if (key.spec.role == SymbolKeyRole.BACKSPACE) {
                    backspaceHold.pressRepeating { dispatchKey(key.spec) }
                } else {
                    longPress.press(
                        key.spec,
                        onBase = ::dispatchKey,
                        onAlternative = ::dispatchKey
                    )
                }
                performClick()
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> handleMove(event.x, event.y)
            MotionEvent.ACTION_UP -> {
                val releasedOnBase = activeKey?.geometry?.hitRect?.contains(event.x, event.y) == true
                if (activeKey?.spec?.role == SymbolKeyRole.BACKSPACE) {
                    backspaceHold.release()
                } else {
                    longPress.release(releasedOnBase)
                }
                clearTouchState()
            }
            MotionEvent.ACTION_CANCEL -> {
                longPress.cancel()
                backspaceHold.cancel()
                clearTouchState()
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDetachedFromWindow() {
        longPress.cancel()
        backspaceHold.cancel()
        super.onDetachedFromWindow()
    }

    override fun getAccessibilityNodeProvider(): AccessibilityNodeProvider = accessibilityProvider

    private fun handleMove(x: Float, y: Float) {
        if (longPress.popupVisible) {
            val alternative = popupEntries.firstOrNull { it.geometry.hitRect.contains(x, y) }
            if (alternative == null) {
                longPress.cancel()
                clearTouchState()
            } else {
                selectedAlternative = alternative.spec
                longPress.selectAlternative(alternative.spec)
                invalidate()
            }
            return
        }

        val active = activeKey ?: return
        if (!active.geometry.hitRect.contains(x, y)) {
            if (active.spec.role == SymbolKeyRole.BACKSPACE) backspaceHold.cancel() else longPress.moveOutside()
            activeKey = null
            invalidate()
        }
    }

    private fun dispatchKey(key: SymbolKeySpec) {
        onKeyTap?.invoke(key) ?: when (key.role) {
            SymbolKeyRole.TEXT -> key.commitText?.let { onSymbolTap?.invoke(it) }
            SymbolKeyRole.SPACE -> onSpace?.invoke()
            SymbolKeyRole.BACKSPACE -> onBackspace?.invoke()
            SymbolKeyRole.ENTER -> onEnter?.invoke()
            SymbolKeyRole.RETURN_TO_ALPHABET -> onClose?.invoke()
            SymbolKeyRole.TOGGLE_PAGE -> Unit
        }
    }

    private fun clearTouchState() {
        activeKey = null
        selectedAlternative = null
        popupEntries = emptyList()
        invalidate()
    }

    private fun rebuildKeys() {
        val currentLayout = if (width > 0 && height > 0) {
            SymbolKeyboardGeometry.layout(width.toFloat(), height.toFloat(), resources.displayMetrics.density)
        } else {
            null
        }
        layout = currentLayout
        renderedKeys.clear()
        if (currentLayout == null) return

        val rows = SymbolKeyboardSpec.page(symbolPage).rows
        rows.forEachIndexed { rowIndex, row ->
            row.forEachIndexed { columnIndex, key ->
                renderedKeys += RenderedKey(key, currentLayout.symbolRows[rowIndex][columnIndex])
            }
        }
        SymbolKeyboardSpec.bottomKeys(symbolPage, enterAction.keySpec()).forEachIndexed { index, key ->
            renderedKeys += RenderedKey(key, currentLayout.functionRow[index])
        }
        rebuildPopupEntries()
    }

    private fun rebuildPopupEntries() {
        val anchor = activeKey ?: run {
            popupEntries = emptyList()
            return
        }
        if (!longPress.popupVisible || anchor.spec.longPressAlternatives.isEmpty()) {
            popupEntries = emptyList()
            return
        }
        val alternatives = anchor.spec.longPressAlternatives
        val gap = dp(2).toFloat()
        val outer = dp(4).toFloat()
        val available = (width - outer * 2f - gap * (alternatives.size - 1)).coerceAtLeast(0f)
        val cellWidth = minOf(maxOf(anchor.geometry.visualRect.width, dp(36).toFloat()), available / alternatives.size)
        val popupWidth = cellWidth * alternatives.size + gap * (alternatives.size - 1)
        val left = (anchor.geometry.hitRect.left + anchor.geometry.hitRect.width / 2f - popupWidth / 2f)
            .coerceIn(outer, (width - outer - popupWidth).coerceAtLeast(outer))
        val popupHeight = anchor.geometry.visualRect.height
        val preferredTop = anchor.geometry.visualRect.top - popupHeight - gap
        val top = if (preferredTop >= outer) preferredTop else anchor.geometry.visualRect.bottom + gap
        popupEntries = alternatives.mapIndexed { index, key ->
            val hit = SymbolRect(
                left + index * (cellWidth + gap),
                top,
                left + index * (cellWidth + gap) + cellWidth,
                top + popupHeight
            )
            RenderedKey(key, SymbolKeyGeometry(hit.inset(dp(1).toFloat(), dp(1).toFloat()), hit))
        }
    }

    private fun drawKey(canvas: Canvas, rendered: RenderedKey, isPopup: Boolean) {
        val key = rendered.spec
        val rect = rendered.geometry.visualRect
        val isPressed = if (isPopup) selectedAlternative == key else activeKey?.spec == key
        val keyBackground = when {
            isPopup -> themeColors.symbolPopupBackground
            isPressed && key.role == SymbolKeyRole.TEXT -> themeColors.symbolPressedKeyBackground
            isPressed -> themeColors.symbolPressedFunctionKeyBackground
            key.role == SymbolKeyRole.TEXT -> themeColors.symbolKeyBackground
            else -> themeColors.symbolFunctionKeyBackground
        }
        val radius = dp(9).toFloat()
        if (android.graphics.Color.alpha(themeColors.keyShadow) > 0) {
            paint.color = themeColors.keyShadow
            canvas.drawRoundRect(
                rect.left,
                rect.top + dp(1),
                rect.right,
                rect.bottom + dp(1),
                radius,
                radius,
                paint
            )
        }
        paint.color = keyBackground
        canvas.drawRoundRect(rect.toRectF(), radius, radius, paint)

        if (key.icon != null) {
            drawIcon(canvas, key.icon, rect)
        } else {
            drawLabel(canvas, key, rect, isPopup)
        }
    }

    private fun drawLabel(canvas: Canvas, key: SymbolKeySpec, rect: SymbolRect, isPopup: Boolean) {
        val textSize = if (key.role == SymbolKeyRole.TEXT || isPopup) {
            val base = if (key.label in COMPLEX_BRACKETS) 26f else 24f
            minOf(sp(base), rect.height * 0.55f) * (layout?.symbolTextScale ?: 1f)
        } else {
            minOf(sp(19f), rect.height * 0.44f)
        }
        labelPaint.textSize = textSize
        labelPaint.color = themeColors.symbolLabel
        val cx = rect.left + rect.width / 2f
        if (key.pageIndicator.isNotEmpty()) {
            drawCenteredText(canvas, key.label, cx, rect.top + rect.height * 0.40f, labelPaint)
            val dotY = rect.top + rect.height * 0.72f
            val dotRadius = dp(3).toFloat()
            val spacing = dp(12).toFloat()
            key.pageIndicator.forEachIndexed { index, active ->
                indicatorPaint.color = if (active) themeColors.symbolIndicatorActive else themeColors.symbolIndicatorInactive
                canvas.drawCircle(cx + (index - 0.5f) * spacing, dotY, dotRadius, indicatorPaint)
            }
        } else {
            drawCenteredText(canvas, key.label, cx, rect.top + rect.height / 2f, labelPaint)
        }
    }

    private fun drawIcon(canvas: Canvas, icon: SymbolKeyIcon, rect: SymbolRect) {
        val drawable = iconCache.getOrPut(icon) { ContextCompat.getDrawable(context, icon.drawableRes())!!.mutate() }
        drawable.setTint(themeColors.symbolLabel)
        val size = minOf(dp(28), (minOf(rect.width, rect.height) * 0.60f).toInt())
        val left = (rect.left + (rect.width - size) / 2f).toInt()
        val top = (rect.top + (rect.height - size) / 2f).toInt()
        drawable.setBounds(left, top, left + size, top + size)
        drawable.draw(canvas)
    }

    private fun drawCenteredText(canvas: Canvas, text: String, centerX: Float, centerY: Float, paint: Paint) {
        canvas.drawText(text, centerX, centerY - (paint.ascent() + paint.descent()) / 2f, paint)
    }

    private fun selectionHaptic() {
        val engine = haptics
        if (engine != null) {
            engine.perform(enabled = vibrationEnabled) {
                HapticFeedbackPolicy.performSelection(this, enabled = true)
            }
        } else {
            HapticFeedbackPolicy.performSelection(this, vibrationEnabled)
        }
    }

    private fun keyAt(x: Float, y: Float): RenderedKey? =
        renderedKeys.firstOrNull { it.geometry.hitRect.contains(x, y) }

    private fun SymbolRect.toRectF(): RectF = RectF(left, top, right, bottom)

    private fun SymbolRect.inset(horizontal: Float, vertical: Float): SymbolRect = SymbolRect(
        left + horizontal,
        top + vertical,
        right - horizontal,
        bottom - vertical
    )

    private fun SymbolKeyIcon.drawableRes(): Int = when (this) {
        SymbolKeyIcon.BACKSPACE -> R.drawable.ic_symbol_backspace
        SymbolKeyIcon.RETURN -> R.drawable.ic_symbol_return
        SymbolKeyIcon.SEARCH -> R.drawable.ic_symbol_search
        SymbolKeyIcon.SEND -> R.drawable.ic_symbol_send
        SymbolKeyIcon.NEXT -> R.drawable.ic_symbol_next
        SymbolKeyIcon.DONE -> R.drawable.ic_symbol_done
        SymbolKeyIcon.GO -> R.drawable.ic_symbol_go
    }

    private fun usesEnglishAccessibility(): Boolean =
        resources.configuration.locales[0].language == "en"

    private fun localizedPageAnnouncement(page: SymbolPage): String =
        SymbolKeyboardSpec.pageAnnouncement(page, english = usesEnglishAccessibility())

    private fun localizedKeyDescription(key: SymbolKeySpec): String =
        SymbolKeyboardSpec.accessibilityDescription(key, english = usesEnglishAccessibility())

    private inner class SymbolAccessibilityNodeProvider : AccessibilityNodeProvider() {
        override fun createAccessibilityNodeInfo(virtualViewId: Int): AccessibilityNodeInfo? {
            if (virtualViewId == View.NO_ID) {
                return AccessibilityNodeInfo.obtain(this@SymbolPageView).apply {
                    className = SymbolPageView::class.java.name
                    contentDescription = localizedPageAnnouncement(symbolPage)
                    renderedKeys.indices.forEach { addChild(this@SymbolPageView, it) }
                }
            }
            val key = renderedKeys.getOrNull(virtualViewId) ?: return null
            return AccessibilityNodeInfo.obtain().apply {
                setSource(this@SymbolPageView, virtualViewId)
                setParent(this@SymbolPageView)
                className = "android.widget.Button"
                contentDescription = localizedKeyDescription(key.spec)
                isClickable = true
                isEnabled = true
                isLongClickable = key.spec.longPressAlternatives.isNotEmpty()
                setBoundsInParent(key.geometry.hitRect.toAndroidRect())
                addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK)
                if (key.spec.longPressAlternatives.isNotEmpty()) addAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
            }
        }

        override fun findAccessibilityNodeInfosByText(text: String, virtualViewId: Int): List<AccessibilityNodeInfo> =
            renderedKeys.mapIndexedNotNull { index, key ->
                if (localizedKeyDescription(key.spec).contains(text, ignoreCase = true)) {
                    createAccessibilityNodeInfo(index)
                } else {
                    null
                }
            }

        override fun performAction(virtualViewId: Int, action: Int, arguments: android.os.Bundle?): Boolean {
            val key = renderedKeys.getOrNull(virtualViewId) ?: return false
            return when (action) {
                AccessibilityNodeInfo.ACTION_CLICK -> {
                    dispatchKey(key.spec)
                    sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_CLICKED)
                    true
                }
                AccessibilityNodeInfo.ACTION_LONG_CLICK -> {
                    if (key.spec.longPressAlternatives.isEmpty()) false
                    else {
                        announceForAccessibility(localizedKeyDescription(key.spec))
                        true
                    }
                }
                else -> false
            }
        }
    }

    private fun SymbolRect.toAndroidRect(): Rect = Rect(left.toInt(), top.toInt(), right.toInt(), bottom.toInt())

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
    private fun sp(value: Float): Float = value * resources.displayMetrics.scaledDensity

    private companion object {
        val COMPLEX_BRACKETS = setOf("〔", "〕", "〈", "〉", "【", "】", "〖", "〗", "（", "）")
    }
}
