package com.hkmixedkeyboard

import android.app.Activity
import android.os.Build
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.textclassifier.TextClassifier
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
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
import org.junit.Assert.assertNotEquals
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
    fun appOwnedTextFieldsAvoidTheLegacySpellingSuggestionPopup() {
        ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val fields = findEditTexts(activity)
                assertEquals(1, fields.size)
                assertNoSystemSuggestions(fields.single())
            }
        }

        ActivityScenario.launch(CustomWordActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val fields = findEditTexts(activity)
                assertEquals(2, fields.size)
                fields.forEach(::assertNoSystemSuggestions)
                assertEquals(
                    EditorInfo.IME_ACTION_NEXT,
                    fields[0].imeOptions and EditorInfo.IME_MASK_ACTION
                )
                assertEquals(
                    EditorInfo.IME_ACTION_DONE,
                    fields[1].imeOptions and EditorInfo.IME_MASK_ACTION
                )
            }
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

    @Test
    fun settingsThemeDescriptionsUseSettingsForegroundAndSwitchRowsAreLabelledControls() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val expectedDescriptionColor = 0xFF475569.toInt()
                val previewDescriptions = findTextViews(activity).filter { text ->
                    text.text.toString() in setOf(
                        context.getString(R.string.theme_dark_preview),
                        context.getString(R.string.theme_light_preview)
                    )
                }
                assertEquals(2, previewDescriptions.size)
                previewDescriptions.forEach { description ->
                    assertEquals(expectedDescriptionColor, description.currentTextColor)
                    assertNotEquals(
                        0xFFE8EAED.toInt(),
                        description.currentTextColor
                    )
                }

                val rows = findSettingSwitchRows(activity)
                assertEquals(5, rows.size)
                rows.forEach { (row, label, control) ->
                    assertNotEquals(View.NO_ID, control.id)
                    assertEquals(label.text.toString(), control.contentDescription?.toString())
                    assertEquals(control.id, label.labelFor)

                    val before = rows.map { it.control.isChecked }
                    assertTrue(row.performClick())
                    rows.forEachIndexed { index, candidate ->
                        assertEquals(
                            if (candidate.control === control) !before[index] else before[index],
                            candidate.control.isChecked
                        )
                    }
                    assertTrue(row.performClick())
                }
            }
        }
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

    private fun findEditTexts(activity: Activity): List<EditText> {
        val root = activity.findViewById<ViewGroup>(android.R.id.content)
        val fields = mutableListOf<EditText>()
        fun collect(view: View) {
            if (view is EditText) fields += view
            if (view is ViewGroup) {
                for (index in 0 until view.childCount) collect(view.getChildAt(index))
            }
        }
        collect(root)
        return fields
    }

    private data class SettingSwitchRow(
        val row: LinearLayout,
        val label: TextView,
        val control: SwitchCompat
    )

    private fun findSettingSwitchRows(activity: Activity): List<SettingSwitchRow> {
        val rows = mutableListOf<SettingSwitchRow>()
        fun collect(view: View) {
            if (view is LinearLayout) {
                val controls = (0 until view.childCount)
                    .map(view::getChildAt)
                    .filterIsInstance<SwitchCompat>()
                val labels = (0 until view.childCount)
                    .map(view::getChildAt)
                    .filterIsInstance<TextView>()
                    .filterNot { it is SwitchCompat }
                if (controls.size == 1 && labels.size == 1) {
                    rows += SettingSwitchRow(view, labels.single(), controls.single())
                }
            }
            if (view is ViewGroup) {
                for (index in 0 until view.childCount) collect(view.getChildAt(index))
            }
        }
        collect(activity.findViewById(android.R.id.content))
        return rows
    }

    private fun findTextViews(activity: Activity): List<TextView> {
        val texts = mutableListOf<TextView>()
        fun collect(view: View) {
            if (view is TextView) texts += view
            if (view is ViewGroup) {
                for (index in 0 until view.childCount) collect(view.getChildAt(index))
            }
        }
        collect(activity.findViewById(android.R.id.content))
        return texts
    }

    private fun assertNoSystemSuggestions(field: EditText) {
        assertEquals(
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS,
            field.inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        )
        assertSame(TextClassifier.NO_OP, field.textClassifier)
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
