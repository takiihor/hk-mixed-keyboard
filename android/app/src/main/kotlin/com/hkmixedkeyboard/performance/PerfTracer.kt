package com.hkmixedkeyboard.performance

import android.util.Log
import com.hkmixedkeyboard.BuildConfig

/**
 * Lightweight performance tracer gated by BuildConfig.PERF_TRACING.
 * Avoids string building work when disabled.
 */
object PerfTracer {
    private const val TAG = "HkIme.Perf"

    fun mark(event: String, payload: () -> String = { "" }) {
        if (!BuildConfig.PERF_TRACING) return
        try {
            val extra = payload()
            if (extra.isEmpty()) Log.d(TAG, event) else Log.d(TAG, "$event $extra")
        } catch (_: Throwable) {
            // Never crash tracing
        }
    }

    fun <T> time(section: String, work: () -> T): T {
        if (!BuildConfig.PERF_TRACING) return work()
        val start = System.nanoTime()
        return try {
            work()
        } finally {
            val durUs = (System.nanoTime() - start) / 1000
            Log.d(TAG, "$section us=$durUs")
        }
    }
}
