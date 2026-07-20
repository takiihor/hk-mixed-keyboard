package com.hkmixedkeyboard

import android.graphics.Rect
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hkmixedkeyboard.ui.SymbolKeySpec
import com.hkmixedkeyboard.ui.SymbolPageView
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SymbolAccessibilityNodeProviderTest {
    @Test
    fun symbolKeyboardExposesBoundedOperableVirtualKeys() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            var selected: SymbolKeySpec? = null
            val view = SymbolPageView(
                InstrumentationRegistry.getInstrumentation().targetContext
            ).apply {
                onKeyTap = { selected = it }
                measure(
                    View.MeasureSpec.makeMeasureSpec(480, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(280, View.MeasureSpec.EXACTLY)
                )
                layout(0, 0, measuredWidth, measuredHeight)
            }

            val provider = view.accessibilityNodeProvider
            assertNotNull(provider)
            val host = provider!!.createAccessibilityNodeInfo(
                AccessibilityNodeProvider.HOST_VIEW_ID
            )
            assertNotNull(host)
            assertTrue(host!!.childCount > 0)

            val first = provider.createAccessibilityNodeInfo(0)
            assertNotNull(first)
            val bounds = Rect()
            first!!.getBoundsInScreen(bounds)
            assertTrue(!bounds.isEmpty)
            assertTrue(first.actionList.any {
                it.id == AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK.id
            })
            assertTrue(!first.contentDescription.isNullOrBlank())
            assertTrue(provider.performAction(0, AccessibilityNodeInfo.ACTION_CLICK, null))
            assertNotNull(selected)
        }
    }
}
