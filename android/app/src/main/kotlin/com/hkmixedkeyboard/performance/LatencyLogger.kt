package com.hkmixedkeyboard.performance

import android.util.Log
import com.hkmixedkeyboard.BuildConfig

/**
 * Per-keystroke latency, logged under `HkIme.Latency`.
 *
 * Gated by its own BuildConfig flag rather than SHOW_DEBUG_PANEL: measuring
 * typing latency must not also draw a debug panel, because that would change
 * what is rendered on the very frame being measured.
 */
object LatencyLogger {
    private const val TAG = "HkIme.Latency"

    // Written on the UI thread at key-down and read from the decode worker, so
    // the timestamp has to be published across threads to be meaningful.
    @Volatile private var keyDownMs = 0L

    fun keyDown() {
        if (BuildConfig.LATENCY_LOGGING) keyDownMs = System.currentTimeMillis()
    }

    fun visualFeedback() {
        if (BuildConfig.LATENCY_LOGGING) log("visual_feedback", keyDownMs)
    }

    fun decodeStart() {
        if (BuildConfig.LATENCY_LOGGING) log("decode_start", keyDownMs)
    }

    fun decodeEnd(bufLen: Int, candidateCount: Int, scheme: String) {
        if (!BuildConfig.LATENCY_LOGGING) return
        val ms = System.currentTimeMillis() - keyDownMs
        Log.d(TAG, "decode_end ms=$ms buf_len=$bufLen candidates=$candidateCount scheme=$scheme")
    }

    fun firstCandidateRender() {
        if (BuildConfig.LATENCY_LOGGING) log("first_candidate_render", keyDownMs)
    }

    private fun log(event: String, since: Long) {
        val ms = System.currentTimeMillis() - since
        Log.d(TAG, "$event ms=$ms")
    }
}
