package com.hkmixedkeyboard.ime

import com.hkmixedkeyboard.decoder.Scheme

/**
 * Thread-safe ownership of the IME's scheduled, in-flight, and coalesced decode
 * work. The service owns Handler callbacks; this class owns only request state.
 */
class DecodeWorkCoordinator {
    data class Request(
        val buffer: String,
        val generation: Long,
        val session: Long,
        val scheme: Scheme = Scheme.QUICK
    )

    sealed class Action {
        data class Post(val request: Request) : Action()
        data object Coalesced : Action()
    }

    private var scheduled: Request? = null
    private var inFlight: Request? = null
    private var pending: Request? = null

    /** Schedules work now unless a decode is already in flight. */
    @Synchronized
    fun enqueue(request: Request): Action {
        if (inFlight != null) {
            pending = request
            return Action.Coalesced
        }
        scheduled = request
        return Action.Post(request)
    }

    /** Claims a posted request for decoding. Stale/replaced callbacks are ignored. */
    @Synchronized
    fun begin(request: Request): Boolean {
        if (scheduled != request) return false
        scheduled = null
        inFlight = request
        return true
    }

    /** Completes work and returns the most recent coalesced request, if any. */
    @Synchronized
    fun finish(request: Request): Request? {
        if (inFlight != request) return null
        inFlight = null
        val next = pending ?: return null
        pending = null
        scheduled = next
        return next
    }

    /** Invalidates all outstanding work; a late completion becomes a no-op. */
    @Synchronized
    fun cancel() {
        scheduled = null
        inFlight = null
        pending = null
    }
}
