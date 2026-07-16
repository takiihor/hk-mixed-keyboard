package com.hkmixedkeyboard

import android.graphics.Rect
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hkmixedkeyboard.ui.KeyboardLayout
import com.hkmixedkeyboard.ui.KeyboardAccessibilityLabels
import com.hkmixedkeyboard.ui.KeyboardView
import com.hkmixedkeyboard.ui.SymbolEnterAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KeyboardAccessibilityNodeProviderTest {
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
