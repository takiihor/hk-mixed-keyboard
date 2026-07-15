package com.hkmixedkeyboard

import android.app.Activity
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.textclassifier.TextClassifier
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isCompletelyDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withHint
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hkmixedkeyboard.settings.CustomWordActivity
import com.hkmixedkeyboard.settings.DictionaryExportActivity
import com.hkmixedkeyboard.settings.OpenSourceLicensesActivity
import com.hkmixedkeyboard.settings.PrivacyPolicyActivity
import com.hkmixedkeyboard.settings.SettingsActivity
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsReleaseRegressionTest {
    @Test
    fun customWordDisplayFieldIsCompletelyVisibleAndClickable() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        ActivityScenario.launch(CustomWordActivity::class.java).use {
            onView(withHint(context.getString(R.string.custom_display_hint)))
                .check(matches(isCompletelyDisplayed()))
                .perform(click())
        }
    }

    @Test
    fun clearingPersonalDictionaryRequiresConfirmationAndCanBeCancelled() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        ActivityScenario.launch(SettingsActivity::class.java).use {
            onView(withText(context.getString(R.string.clear_personal_dictionary)))
                .perform(scrollTo())
            onView(withText(context.getString(R.string.clear_personal_dictionary)))
                .perform(click())
            onView(withText(context.getString(R.string.clear_title)))
                .check(matches(isDisplayed()))
            onView(withText(context.getString(R.string.cancel))).perform(click())
        }
    }

    @Test
    fun setupExposesSeparateEnableSelectActionsAndPracticeField() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        ActivityScenario.launch(SettingsActivity::class.java).use {
            onView(withText(context.getString(R.string.setup_enable))).check(matches(isDisplayed()))
            onView(withText(context.getString(R.string.setup_select))).check(matches(isDisplayed()))
            onView(withHint(context.getString(R.string.practice_hint)))
                .check(matches(isDisplayed()))
        }
    }

    @Test
    fun leafSettingsScreensPlaceFirstContentBelowSystemBars() {
        assertFirstContentBelowSystemBars(CustomWordActivity::class.java)
        assertFirstContentBelowSystemBars(DictionaryExportActivity::class.java)
        assertFirstContentBelowSystemBars(PrivacyPolicyActivity::class.java)
        assertFirstContentBelowSystemBars(OpenSourceLicensesActivity::class.java)
    }

    @Test
    fun settingsWindowExcludesUnneededFrameworkScrollCapture() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals(
                    View.SCROLL_CAPTURE_HINT_EXCLUDE_DESCENDANTS,
                    activity.window.decorView.scrollCaptureHint
                )
            }
        }
    }

    @Test
    fun legalDocumentSelectionIsCancelledBeforeTheWindowPauses() {
        assertLifecycleSafeSelection(PrivacyPolicyActivity::class.java)
        assertLifecycleSafeSelection(OpenSourceLicensesActivity::class.java)
    }

    private fun assertLifecycleSafeSelection(activityClass: Class<out Activity>) {
        ActivityScenario.launch(activityClass).use { scenario ->
            scenario.onActivity { activity ->
                val text = findOnlyTextView(activity)
                assertTrue(text.isTextSelectable)
                assertSame(TextClassifier.NO_OP, text.textClassifier)
            }

            scenario.moveToState(androidx.lifecycle.Lifecycle.State.STARTED)
            scenario.onActivity { activity ->
                assertFalse(findOnlyTextView(activity).isTextSelectable)
            }

            scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
            scenario.onActivity { activity ->
                val text = findOnlyTextView(activity)
                assertTrue(text.isTextSelectable)
                assertSame(TextClassifier.NO_OP, text.textClassifier)
            }
        }
    }

    private fun findOnlyTextView(activity: Activity): TextView {
        val root = activity.findViewById<ViewGroup>(android.R.id.content)
        val texts = mutableListOf<TextView>()
        fun collect(view: View) {
            if (view is TextView) texts += view
            if (view is ViewGroup) {
                for (index in 0 until view.childCount) collect(view.getChildAt(index))
            }
        }
        collect(root)
        assertEquals("${activity.javaClass.simpleName} legal text count", 1, texts.size)
        return texts.single()
    }

    private fun assertFirstContentBelowSystemBars(activityClass: Class<out Activity>) {
        ActivityScenario.launch(activityClass).use { scenario ->
            scenario.onActivity { activity ->
                val content = activity.findViewById<ViewGroup>(android.R.id.content)
                val root = content.getChildAt(0)
                val firstContent = if (root is ScrollView) root.getChildAt(0) else root
                val target = if (firstContent is ViewGroup) firstContent.getChildAt(0) else firstContent
                val screenPosition = IntArray(2)
                target.getLocationOnScreen(screenPosition)
                val topInset = ViewCompat.getRootWindowInsets(target)
                    ?.getInsets(WindowInsetsCompat.Type.systemBars())
                    ?.top ?: 0
                assertTrue(
                    "${activityClass.simpleName} first content starts at ${screenPosition[1]}, " +
                        "above system-bar bottom $topInset",
                    screenPosition[1] >= topInset
                )
            }
        }
    }
}
