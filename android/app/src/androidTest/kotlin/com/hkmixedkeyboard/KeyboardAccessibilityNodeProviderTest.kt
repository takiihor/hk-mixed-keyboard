package com.hkmixedkeyboard

import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hkmixedkeyboard.ui.KeyboardLayout
import com.hkmixedkeyboard.ui.KeyboardView
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
            assertEquals("數字1鍵", firstKey!!.contentDescription)
            assertTrue(firstKey.actionList.any {
                it.id == AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK.id
            })

            assertTrue(nonNullProvider.performAction(
                1,
                AccessibilityNodeInfo.ACTION_CLICK,
                null
            ))
            assertEquals("1", emitted)
        }
    }
}
