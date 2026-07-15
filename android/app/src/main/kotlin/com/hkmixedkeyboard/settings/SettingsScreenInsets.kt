package com.hkmixedkeyboard.settings

import android.app.Activity
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/** Applies edge-to-edge system-bar padding to the programmatic settings screens. */
object SettingsScreenInsets {
    fun apply(activity: Activity, root: View, contentPadding: Int) {
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        WindowInsetsControllerCompat(activity.window, root).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                contentPadding,
                contentPadding + bars.top,
                contentPadding,
                contentPadding + bars.bottom
            )
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }
}
