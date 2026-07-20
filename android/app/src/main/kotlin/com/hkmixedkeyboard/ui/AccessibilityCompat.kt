package com.hkmixedkeyboard.ui

import android.graphics.Rect
import android.os.Build
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.view.ViewCompat

/** Small API-level bridge for custom-view accessibility nodes. */
internal object AccessibilityCompat {
    fun hostNode(host: View): AccessibilityNodeInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            AccessibilityNodeInfo(host)
        } else {
            legacyHostNode(host)
        }

    fun virtualNode(host: View, virtualId: Int): AccessibilityNodeInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            AccessibilityNodeInfo(host, virtualId)
        } else {
            legacyVirtualNode(host, virtualId)
        }

    fun event(eventType: Int): AccessibilityEvent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            AccessibilityEvent(eventType)
        } else {
            legacyEvent(eventType)
        }

    fun setBoundsInScreen(node: AccessibilityNodeInfo, host: View, localBounds: Rect) {
        val location = IntArray(2)
        host.getLocationOnScreen(location)
        node.setBoundsInScreen(
            Rect(localBounds).apply { offset(location[0], location[1]) }
        )
    }

    fun publishState(view: View, description: CharSequence) {
        ViewCompat.setStateDescription(view, description)
        view.sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
    }

    fun enablePoliteLiveRegion(view: View) {
        view.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
    }

    @Suppress("DEPRECATION")
    private fun legacyHostNode(host: View): AccessibilityNodeInfo =
        AccessibilityNodeInfo.obtain(host)

    @Suppress("DEPRECATION")
    private fun legacyVirtualNode(host: View, virtualId: Int): AccessibilityNodeInfo =
        AccessibilityNodeInfo.obtain(host, virtualId)

    @Suppress("DEPRECATION")
    private fun legacyEvent(eventType: Int): AccessibilityEvent =
        AccessibilityEvent.obtain(eventType)
}
