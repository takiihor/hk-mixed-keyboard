package com.hkmixedkeyboard.ime

import com.hkmixedkeyboard.decoder.DecodeCandidate


sealed interface CandidateCommitIntent {
    data object Space : CandidateCommitIntent
    data class Punctuation(val canonical: String) : CandidateCommitIntent
}


/** Coordinates a commit key press with the matching asynchronous decode. */
class CandidateCommitIntentController {
    data class Key(val buffer: String, val session: Long, val generation: Long)
    class Token internal constructor(internal val key: Key, internal val requestId: Long)
    data class Resolution(
        val intent: CandidateCommitIntent,
        val candidate: DecodeCandidate?
    )

    sealed class Action {
        data object AwaitDecode : Action()
        data class Ready(val token: Token) : Action()
    }

    private data class Cached(val key: Key, val candidate: DecodeCandidate?)
    private data class Pending(
        val key: Key,
        val requestId: Long,
        val intent: CandidateCommitIntent
    )

    private var cached: Cached? = null
    private var pending: Pending? = null
    private var nextRequestId = 0L

    @Synchronized
    fun request(
        intent: CandidateCommitIntent,
        buffer: String,
        session: Long,
        generation: Long
    ): Action {
        val key = Key(buffer, session, generation)
        val request = Pending(key, ++nextRequestId, intent)
        pending = request
        val ready = cached?.takeIf { it.key == key } ?: return Action.AwaitDecode
        return Action.Ready(Token(ready.key, request.requestId))
    }

    @Synchronized
    fun onDecoded(
        buffer: String,
        session: Long,
        generation: Long,
        candidate: DecodeCandidate?
    ): Token? {
        val key = Key(buffer, session, generation)
        val activeRequest = pending
        if (activeRequest != null && activeRequest.key != key) return null
        cached = Cached(key, candidate)
        val request = activeRequest ?: return null
        return Token(key, request.requestId)
    }

    @Synchronized
    fun consume(token: Token): Resolution? {
        val request = pending
        if (request?.key != token.key || request.requestId != token.requestId) return null
        val ready = cached?.takeIf { it.key == token.key } ?: return null
        pending = null
        return Resolution(request.intent, ready.candidate)
    }

    @Synchronized
    fun cancel() {
        pending = null
        cached = null
    }
}
