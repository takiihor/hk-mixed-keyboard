package com.hkmixedkeyboard.performance

import android.util.Log
import com.hkmixedkeyboard.BuildConfig

object LatencyLogger {
    private const val TAG = "HkIme.Latency"
    private var keyDownMs = 0L

    fun keyDown() {
        if (BuildConfig.SHOW_DEBUG_PANEL) keyDownMs = System.currentTimeMillis()
    }

    fun visualFeedback() {
        if (BuildConfig.SHOW_DEBUG_PANEL) log("visual_feedback", keyDownMs)
    }

    fun decodeStart() {
        if (BuildConfig.SHOW_DEBUG_PANEL) log("decode_start", keyDownMs)
    }

    fun decodeEnd(bufLen: Int, candidateCount: Int, scheme: String) {
        if (!BuildConfig.SHOW_DEBUG_PANEL) return
        val ms = System.currentTimeMillis() - keyDownMs
        Log.d(TAG, "decode_end ms=$ms buf_len=$bufLen candidates=$candidateCount scheme=$scheme")
    }

    fun firstCandidateRender() {
        if (BuildConfig.SHOW_DEBUG_PANEL) log("first_candidate_render", keyDownMs)
    }

    private fun log(event: String, since: Long) {
        val ms = System.currentTimeMillis() - since
        Log.d(TAG, "$event ms=$ms")
    }
}
