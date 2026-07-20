package com.hkmixedkeyboard.settings

import android.app.Activity
import android.os.Build
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/** Applies edge-to-edge system-bar padding to the programmatic settings screens. */
object SettingsScreenInsets {
    fun apply(activity: Activity, root: View, contentPadding: Int) {
        WindowInsetsControllerCompat(activity.window, root).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            // Legacy inset dispatch is unreliable for programmatic children of a
            // ScrollView. Let the platform fit the content window on API 26-28.
            WindowCompat.setDecorFitsSystemWindows(activity.window, true)
            root.setPadding(contentPadding, contentPadding, contentPadding, contentPadding)
            return
        }
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        // Listen on android.R.id.content, which receives insets reliably even
        // when [root] is nested inside a ScrollView.
        val insetHost = activity.findViewById<View>(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(insetHost) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            root.setPadding(
                contentPadding,
                contentPadding + bars.top,
                contentPadding,
                contentPadding + bars.bottom
            )
            insets
        }
        if (insetHost.isAttachedToWindow) {
            ViewCompat.requestApplyInsets(insetHost)
        } else {
            insetHost.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(view: View) {
                    view.removeOnAttachStateChangeListener(this)
                    ViewCompat.requestApplyInsets(view)
                }

                override fun onViewDetachedFromWindow(view: View) = Unit
            })
        }
    }
}
