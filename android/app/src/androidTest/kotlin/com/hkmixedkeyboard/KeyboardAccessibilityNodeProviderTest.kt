package com.hkmixedkeyboard

import android.graphics.Rect
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeProvider
import android.widget.FrameLayout
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hkmixedkeyboard.ui.HoldActionController
import com.hkmixedkeyboard.ui.KeyboardAccessibilityLabels
import com.hkmixedkeyboard.ui.KeyboardLayout
import com.hkmixedkeyboard.ui.KeyboardSurface
import com.hkmixedkeyboard.ui.KeyboardView
import com.hkmixedkeyboard.ui.SymbolEnterAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KeyboardAccessibilityNodeProviderTest {
    @Test
    fun numericPasswordActionChangeClearsVirtualAccessibilityFocus() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val accessibilityEvents = mutableListOf<Pair<Int, String>>()
            val expectedZeroDescription = KeyboardAccessibilityLabels.descriptionFor(
                "0",
                text = KeyboardAccessibilityLabels.from(instrumentation.targetContext)
            )
            val keyboard = KeyboardView(instrumentation.targetContext).apply {
                keyboardSurface = KeyboardSurface.NUMERIC_PASSWORD
                measure(
                    View.MeasureSpec.makeMeasureSpec(1_000, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY)
                )
                layout(0, 0, measuredWidth, measuredHeight)
            }
            val parent = object : FrameLayout(instrumentation.targetContext) {
                override fun requestSendAccessibilityEvent(
                    child: View,
                    event: AccessibilityEvent
                ): Boolean {
                    accessibilityEvents += event.eventType to event.contentDescription.toString()
                    return true
                }
            }
            parent.addView(keyboard)
            val provider = keyboard.accessibilityNodeProvider!!
            val zeroVirtualId = 10

            assertTrue(
                provider.performAction(
                    zeroVirtualId,
                    AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS,
                    null
                )
            )
            assertEquals(
                expectedZeroDescription,
                provider.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)!!
                    .contentDescription
            )

            keyboard.enterAction = SymbolEnterAction.DONE

            assertNull(provider.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY))
            assertEquals(
                expectedZeroDescription,
                accessibilityEvents.single {
                    it.first == AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED
                }.second
            )
            assertEquals(
                12,
                provider.createAccessibilityNodeInfo(AccessibilityNodeProvider.HOST_VIEW_ID)!!
                    .childCount
            )
        }
    }

    @Test
    fun numericPasswordActionChangeCancelsHeldKeyGesture() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val emitted = mutableListOf<String>()
        lateinit var keyboard: KeyboardView

        instrumentation.runOnMainSync {
            keyboard = KeyboardView(instrumentation.targetContext).apply {
                keyboardSurface = KeyboardSurface.NUMERIC_PASSWORD
                enterAction = SymbolEnterAction.DONE
                keyListener = object : KeyboardView.KeyListener {
                    override fun onKey(label: String) { emitted += label }
                    override fun onKeyLongPress(label: String) = Unit
                    override fun onSpaceSwipe(delta: Int) = Unit
                }
                measure(
                    View.MeasureSpec.makeMeasureSpec(1_000, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY)
                )
                layout(0, 0, measuredWidth, measuredHeight)
            }
            val backspace = KeyboardLayout.buildCells(
                1_000f,
                400f,
                KeyboardLayout.rowsFor(
                    KeyboardSurface.NUMERIC_PASSWORD,
                    showNextInputMethod = false,
                    enterAction = SymbolEnterAction.DONE
                )
            ).single { it.key.label == KeyboardLayout.KEY_BACKSPACE }.bounds
            val downTime = SystemClock.uptimeMillis()
            val down = MotionEvent.obtain(
                downTime,
                downTime,
                MotionEvent.ACTION_DOWN,
                backspace.centerX,
                (backspace.top + backspace.bottom) / 2f,
                0
            )
            try {
                keyboard.onTouchEvent(down)
            } finally {
                down.recycle()
            }
            assertEquals(listOf(KeyboardLayout.KEY_BACKSPACE), emitted)

            keyboard.enterAction = SymbolEnterAction.RETURN
        }

        SystemClock.sleep(HoldActionController.REPEAT_INITIAL_DELAY_MS + 100L)
        lateinit var emittedAfterDelay: List<String>
        instrumentation.runOnMainSync {
            emittedAfterDelay = emitted.toList()
            val cancelTime = SystemClock.uptimeMillis()
            val cancel = MotionEvent.obtain(
                cancelTime,
                cancelTime,
                MotionEvent.ACTION_CANCEL,
                0f,
                0f,
                0
            )
            try {
                keyboard.onTouchEvent(cancel)
            } finally {
                cancel.recycle()
            }
        }

        assertEquals(listOf(KeyboardLayout.KEY_BACKSPACE), emittedAfterDelay)
    }

    @Test
    fun numericPasswordGapRejectsTouchAndStaysOutsideVirtualKeyBounds() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val density = instrumentation.targetContext.resources.displayMetrics.density
            val width = (100f * density).toInt()
            val height = (60f * density).toInt()
            val emitted = mutableListOf<String>()
            val keyboard = KeyboardView(instrumentation.targetContext).apply {
                keyboardSurface = KeyboardSurface.NUMERIC_PASSWORD
                keyListener = object : KeyboardView.KeyListener {
                    override fun onKey(label: String) { emitted += label }
                    override fun onKeyLongPress(label: String) = Unit
                    override fun onSpaceSwipe(delta: Int) = Unit
                }
                measure(
                    View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
                )
                layout(0, 0, measuredWidth, measuredHeight)
            }

            val rows = KeyboardLayout.rowsFor(
                KeyboardSurface.NUMERIC_PASSWORD,
                showNextInputMethod = false
            )
            val logicalCells = KeyboardLayout.buildCells(
                width.toFloat(),
                height.toFloat(),
                rows
            )
            val zero = logicalCells.single { it.key.label == "0" }.bounds
            val seven = logicalCells.single { it.key.label == "7" }.bounds

            fun tap(x: Float, y: Float) {
                val downTime = SystemClock.uptimeMillis()
                val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0)
                val up = MotionEvent.obtain(downTime, downTime + 1, MotionEvent.ACTION_UP, x, y, 0)
                try {
                    keyboard.onTouchEvent(down)
                    keyboard.onTouchEvent(up)
                } finally {
                    down.recycle()
                    up.recycle()
                }
            }

            tap(zero.left - 1f, seven.bottom + 1f)
            tap(zero.left - 6f * density, seven.bottom + 6f * density)
            assertTrue("PIN bottom-left gap must not emit a key", emitted.isEmpty())

            val provider = keyboard.accessibilityNodeProvider!!
            fun boundsFor(label: String): Rect {
                val virtualId = logicalCells.indexOfFirst { it.key.label == label } + 1
                return Rect().also { bounds ->
                    provider.createAccessibilityNodeInfo(virtualId)!!.getBoundsInScreen(bounds)
                }
            }

            val zeroBounds = boundsFor("0")
            val sevenBounds = boundsFor("7")
            val backspaceBounds = boundsFor(KeyboardLayout.KEY_BACKSPACE)
            assertEquals(zero.left.toInt(), zeroBounds.left)
            assertEquals(zero.right.toInt(), zeroBounds.right)
            assertEquals(seven.bottom.toInt(), sevenBounds.bottom)
            assertEquals(
                logicalCells.single { it.key.label == KeyboardLayout.KEY_BACKSPACE }
                    .bounds.right.toInt(),
                backspaceBounds.right
            )
        }
    }

    @Test
    fun keyboardExposesVirtualKeysAndRoutesVirtualClickToKeyListener() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            var emitted: String? = null
            val keyboard = KeyboardView(instrumentation.targetContext).apply {
                keyListener = object : KeyboardView.KeyListener {
                    override fun onKey(label: String) { emitted = label }
                    override fun onKeyLongPress(label: String) = Unit
                    override fun onSpaceSwipe(delta: Int) = Unit
                }
                measure(
                    View.MeasureSpec.makeMeasureSpec(1_000, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.EXACTLY)
                )
                layout(0, 0, measuredWidth, measuredHeight)
            }

            val provider = keyboard.accessibilityNodeProvider
            assertNotNull(provider)
            val nonNullProvider = provider!!
            val host = nonNullProvider.createAccessibilityNodeInfo(
                AccessibilityNodeProvider.HOST_VIEW_ID
            )
            assertNotNull(host)
            assertEquals(KeyboardLayout.buildCells(1_000f, 500f).size, host!!.childCount)

            val firstKey = nonNullProvider.createAccessibilityNodeInfo(1)
            assertNotNull(firstKey)
            assertEquals(
                KeyboardAccessibilityLabels.descriptionFor(
                    "1",
                    text = KeyboardAccessibilityLabels.from(instrumentation.targetContext)
                ),
                firstKey!!.contentDescription
            )
            assertTrue(firstKey.actionList.any {
                it.id == AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK.id
            })
            val bounds = Rect()
            firstKey.getBoundsInScreen(bounds)
            assertTrue("virtual key must expose non-empty screen bounds", !bounds.isEmpty)

            assertTrue(nonNullProvider.performAction(
                1,
                AccessibilityNodeInfo.ACTION_CLICK,
                null
            ))
            assertEquals("1", emitted)
        }
    }

    @Test
    fun keyboardOmitsAccidentalActionsAndKeepsEditorActionAccessible() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val keyboard = KeyboardView(instrumentation.targetContext).apply {
                showNextInputMethodAction = true
                enterAction = SymbolEnterAction.NEXT
                measure(
                    View.MeasureSpec.makeMeasureSpec(1_000, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.EXACTLY)
                )
                layout(0, 0, measuredWidth, measuredHeight)
            }

            val provider = keyboard.accessibilityNodeProvider!!
            val settings = provider.findAccessibilityNodeInfosByText(
                instrumentation.targetContext.getString(R.string.key_a11y_settings),
                AccessibilityNodeProvider.HOST_VIEW_ID
            )
            val nextIme = provider.findAccessibilityNodeInfosByText(
                instrumentation.targetContext.getString(R.string.key_a11y_next_ime),
                AccessibilityNodeProvider.HOST_VIEW_ID
            )
            val nextField = provider.findAccessibilityNodeInfosByText(
                "Next",
                AccessibilityNodeProvider.HOST_VIEW_ID
            )

            assertTrue(settings.isNullOrEmpty())
            assertTrue(nextIme.isNullOrEmpty())
            assertEquals(1, nextField.orEmpty().size)
        }
    }
}
