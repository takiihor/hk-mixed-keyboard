package com.hkmixedkeyboard

import android.app.Activity
import android.app.Application
import android.os.Build
import android.os.Bundle
import android.os.StrictMode
import android.view.View
import androidx.annotation.RequiresApi

/** Applies lifecycle stability policy and enables broad diagnostics in testable builds. */
class HkApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            registerActivityLifecycleCallbacks(ScrollCaptureExclusion)
        }
        if (!BuildConfig.DEBUG) return
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .detectCustomSlowCalls()
                .detectResourceMismatches()
                .penaltyLog()
                .build()
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectActivityLeaks()
                .detectLeakedSqlLiteObjects()
                .detectLeakedRegistrationObjects()
                .detectFileUriExposure()
                .detectContentUriWithoutPermission()
                .detectCleartextNetwork()
                .detectUntaggedSockets()
                .penaltyLog()
                .build()
        )
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private object ScrollCaptureExclusion : ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, state: Bundle?) {
            // Settings pages do not need long-screenshot capture. Excluding the
            // window also avoids the platform ScrollCaptureConnection teardown
            // race exercised by rapid lifecycle stress on Android 12+.
            activity.window.decorView.scrollCaptureHint =
                View.SCROLL_CAPTURE_HINT_EXCLUDE_DESCENDANTS
        }

        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityResumed(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }
}
