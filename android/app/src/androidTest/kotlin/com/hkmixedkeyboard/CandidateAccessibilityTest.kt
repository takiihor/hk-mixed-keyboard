package com.hkmixedkeyboard

import android.content.Context
import android.content.ContextWrapper
import android.os.HandlerThread
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hkmixedkeyboard.decoder.CandidateType
import com.hkmixedkeyboard.decoder.DecodeCandidate
import com.hkmixedkeyboard.decoder.SourceSchema
import com.hkmixedkeyboard.commit.ImeContext
import com.hkmixedkeyboard.ime.HkImeService
import com.hkmixedkeyboard.ime.PasswordSurfaceState
import com.hkmixedkeyboard.settings.SettingsActivity
import com.hkmixedkeyboard.ui.CandidateBarView
import com.hkmixedkeyboard.ui.KeyboardSurface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CandidateAccessibilityTest {
    @Test
    fun safeModeActionStaysInsideNarrowViewport() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val bar = CandidateBarView(context).apply { vibrationEnabled = false }
            bar.showSafeMode(
                CandidateBarView.AuxiliaryAction(
                    label = "ABC",
                    contentDescription = "Switch to alphabet password keyboard",
                    onClick = {}
                )
            )
            val row = bar.getChildAt(0) as LinearLayout
            (row.getChildAt(0) as TextView).text =
                "Safe mode for a sensitive password field with a long status message"
            val viewportWidth = 220
            val viewportHeight = 144

            bar.measure(
                View.MeasureSpec.makeMeasureSpec(viewportWidth, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(viewportHeight, View.MeasureSpec.EXACTLY)
            )
            bar.layout(0, 0, viewportWidth, viewportHeight)

            val action = row.getChildAt(1)
            assertEquals(viewportWidth, row.measuredWidth)
            assertTrue(action.right <= viewportWidth)
        }
    }

    @Test
    fun manualPinActionSurvivesInputAndDispatchesClickBeforeRemoval() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val uiAutomation = instrumentation.uiAutomation
        val actionDescription = instrumentation.targetContext.getString(
            R.string.password_pin_return_a11y
        )
        var serviceForCleanup: HkImeService? = null
        lateinit var bar: CandidateBarView
        lateinit var passwordState: PasswordSurfaceState
        lateinit var action: TextView
        try {
            ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    val service = HkImeService().also { serviceForCleanup = it }
                    ContextWrapper::class.java.getDeclaredMethod(
                        "attachBaseContext",
                        Context::class.java
                    ).apply { isAccessible = true }.invoke(service, activity)
                    bar = CandidateBarView(activity).apply { vibrationEnabled = false }
                    HkImeService::class.java.getDeclaredField("candidateBar").apply {
                        isAccessible = true
                        set(service, bar)
                    }
                    HkImeService::class.java.getDeclaredField("imeCtx").apply {
                        isAccessible = true
                        set(service, ImeContext(isSensitiveField = true))
                    }
                    passwordState = HkImeService::class.java
                        .getDeclaredField("passwordSurfaceState")
                        .apply { isAccessible = true }
                        .get(service) as PasswordSurfaceState
                    passwordState.startEditor(KeyboardSurface.TEXT_PASSWORD)
                    passwordState.enterManualPin()
                    val parent = FrameLayout(activity)
                    activity.setContentView(parent)
                    parent.addView(bar)
                    assertTrue(bar.isAttachedToWindow)

                    HkImeService::class.java.getDeclaredMethod("refreshSensitiveStatus")
                        .apply { isAccessible = true }
                        .invoke(service)
                    val row = bar.getChildAt(0) as LinearLayout
                    assertEquals(2, row.childCount)

                    HkImeService::class.java
                        .getDeclaredMethod("clearCompositionAfterStandaloneInsert")
                        .apply { isAccessible = true }
                        .invoke(service)
                    assertEquals(2, row.childCount)
                    action = row.getChildAt(1) as TextView
                }

                val clickEvent = uiAutomation.executeAndWaitForEvent(
                    {
                        instrumentation.runOnMainSync {
                            assertTrue(action.performClick())
                            assertEquals(
                                KeyboardSurface.TEXT_PASSWORD,
                                passwordState.visibleSurface
                            )
                        }
                    },
                    { event ->
                        event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED &&
                            event.contentDescription?.toString() == actionDescription
                    },
                    2_000
                )
                assertEquals(AccessibilityEvent.TYPE_VIEW_CLICKED, clickEvent.eventType)
                assertEquals(actionDescription, clickEvent.contentDescription.toString())
                instrumentation.waitForIdleSync()
                scenario.onActivity {
                    val row = bar.getChildAt(0) as LinearLayout
                    assertEquals(1, row.childCount)
                    assertTrue(
                        (0 until row.childCount).none {
                            (row.getChildAt(it) as? TextView)?.text?.toString() == "ABC"
                        }
                    )
                }
            }
        } finally {
            serviceForCleanup?.let { service ->
                instrumentation.runOnMainSync {
                    listOf("decodeThread", "warmThread").forEach { fieldName ->
                        (HkImeService::class.java.getDeclaredField(fieldName)
                            .apply { isAccessible = true }
                            .get(service) as HandlerThread).quitSafely()
                    }
                }
            }
        }
    }

    @Test
    fun safeModeWithoutActionKeepsSingleMessageChild() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val bar = CandidateBarView(context)

            bar.showSafeMode()

            val row = bar.getChildAt(0) as LinearLayout
            val viewportWidth = 220
            bar.measure(
                View.MeasureSpec.makeMeasureSpec(viewportWidth, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(144, View.MeasureSpec.EXACTLY)
            )
            bar.layout(0, 0, viewportWidth, 144)

            assertEquals(1, row.childCount)
            assertEquals(viewportWidth, row.measuredWidth)
            assertEquals(
                context.getString(R.string.safe_mode_active),
                (row.getChildAt(0) as TextView).text.toString()
            )
        }
    }

    @Test
    fun safeModeActionIsAccessibleAndInvokesCallbackOnce() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val bar = CandidateBarView(context).apply { vibrationEnabled = false }
            var clickCount = 0

            bar.showSafeMode(
                CandidateBarView.AuxiliaryAction(
                    label = "ABC",
                    contentDescription = "Switch to alphabet password keyboard",
                    onClick = { clickCount++ }
                )
            )

            val row = bar.getChildAt(0) as LinearLayout
            assertEquals(2, row.childCount)
            val action = row.getChildAt(1) as TextView
            assertEquals("ABC", action.text.toString())
            assertEquals("Switch to alphabet password keyboard", action.contentDescription)
            assertTrue(action.isClickable)
            assertTrue(action.isFocusable)

            action.performClick()

            assertEquals(1, clickCount)
        }
    }

    @Test
    fun candidateExposesPositionReadingSelectionAndOperableTarget() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val bar = CandidateBarView(
                context
            )
            bar.setCandidates(
                listOf(
                    DecodeCandidate(
                        "喺", "hai", SourceSchema.JYUTPING, CandidateType.CHAR,
                        1.0, true, annotation = "hai6"
                    )
                )
            )
            bar.measure(
                View.MeasureSpec.makeMeasureSpec(220, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(48, View.MeasureSpec.EXACTLY)
            )
            bar.layout(0, 0, 220, 48)
            val row = bar.getChildAt(0) as LinearLayout
            val candidate = row.getChildAt(0)
            assertTrue(candidate.contentDescription.contains(
                context.getString(R.string.candidate_position, 1, 1, "喺")
            ))
            assertTrue(candidate.contentDescription.contains("hai6"))
            assertTrue(candidate.isSelected)
            assertTrue(candidate.isClickable && candidate.isFocusable)
            assertTrue(row.measuredWidth > bar.width)
            assertTrue(bar.canScrollHorizontally(1))
        }
    }
}
