package com.hkmixedkeyboard

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
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
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsReleaseRegressionTest {
    @Test
    fun customWordDisplayFieldIsCompletelyVisibleAndClickable() {
        ActivityScenario.launch(CustomWordActivity::class.java).use {
            onView(withHint("詞語（如: 我哋）"))
                .check(matches(isCompletelyDisplayed()))
                .perform(click())
        }
    }

    @Test
    fun clearingPersonalDictionaryRequiresConfirmationAndCanBeCancelled() {
        ActivityScenario.launch(SettingsActivity::class.java).use {
            onView(withText("清除所有個人詞庫")).perform(scrollTo(), click())
            onView(withText("確定清除所有個人詞庫？")).check(matches(isDisplayed()))
            onView(withText("取消")).perform(click())
        }
    }

    @Test
    fun setupExposesSeparateEnableSelectActionsAndPracticeField() {
        ActivityScenario.launch(SettingsActivity::class.java).use {
            onView(withText("在設定中啟用此鍵盤")).check(matches(isDisplayed()))
            onView(withText("選擇目前鍵盤")).check(matches(isDisplayed()))
            onView(withHint("在此安全練習：ai／nei5hou2／ni3hao3"))
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
