package com.hkmixedkeyboard.commit

import com.hkmixedkeyboard.decoder.CandidateType
import com.hkmixedkeyboard.decoder.DecodeCandidate
import com.hkmixedkeyboard.decoder.SourceSchema
import com.hkmixedkeyboard.memory.IUserMemory

class CommitController(
    private val memory: IUserMemory,
    private val ctx: ImeContext
) {

    // ── Public event handlers ────────────────────────────────────────────────

    fun onKeyPress(letter: String, state: ImeStateData): CommitOutput {
        var s = state.copy(lastAutoCommit = null)
        var committedText: String? = null
        var memoryWrite = MemoryWriteDecision(false)

        if (s.buffer.length >= Thresholds.MAX_BUFFER_LEN) {
            val flush = commitLiteralBuffer(s.buffer, learn = false, state = s)
            s = flush.newState
            committedText = flush.committedText
            memoryWrite = flush.memoryWrite
        }

        val newBuf = s.buffer + letter
        return CommitOutput(
            committedText = committedText,
            newState = s.copy(buffer = newBuf, imeState = ImeState.COMPOSING),
            memoryWrite = memoryWrite
        )
    }

    fun onCandidateTap(candidate: DecodeCandidate, state: ImeStateData): CommitOutput {
        val s = state.copy(lastAutoCommit = null)
        return doCommitCandidate(candidate, s)
    }

    fun onBackspace(state: ImeStateData, cursorJustAfterAutoCommit: Boolean = true): CommitOutput {
        if (state.buffer.isNotEmpty()) {
            val newBuf = state.buffer.dropLast(1)
            return CommitOutput(
                committedText = null,
                newState = state.copy(buffer = newBuf,
                    imeState = if (newBuf.isEmpty()) ImeState.IDLE else ImeState.COMPOSING),
                memoryWrite = MemoryWriteDecision(false)
            )
        }

        val lac = state.lastAutoCommit
        if (lac != null && cursorJustAfterAutoCommit) {
            return CommitOutput(
                committedText = null,
                deletedBefore = lac.text.length,
                newState = state.copy(
                    buffer = lac.originalBuffer,
                    lastAutoCommit = null,
                    imeState = ImeState.COMPOSING
                ),
                memoryWrite = MemoryWriteDecision(false)
            )
        }

        // Delete char before cursor (delegated to host in real IME)
        return CommitOutput(
            committedText = null,
            deletedBefore = 1,
            newState = state,
            memoryWrite = MemoryWriteDecision(false)
        )
    }

    fun onSpace(
        state: ImeStateData,
        autoCommitCandidate: DecodeCandidate? = null
    ): CommitOutput {
        if (autoCommitCandidate != null && isExactCandidateForActiveScheme(
                autoCommitCandidate,
                state.buffer
            )
        ) {
            val committedText = canonicalText(autoCommitCandidate)
            return doCommitCandidate(
                autoCommitCandidate,
                state.copy(lastAutoCommit = null),
                AutoCommitRecord(committedText, state.buffer)
            )
        }
        if (state.buffer.isEmpty()) {
            return doCommitRaw(" ", resetContext = false, state = state)
        }

        val flushed = commitLiteralBuffer(state.buffer, learn = true, state = state)
        val raw = doCommitRaw(" ", resetContext = true, state = flushed.newState)
        return raw.copy(
            committedText = (flushed.committedText ?: "") + " ",
            memoryWrite = flushed.memoryWrite
        )
    }

    fun onPunctuation(
        p: String,
        state: ImeStateData,
        precedingContext: PrecedingContext = PrecedingContext.NEUTRAL,
        autoCommitCandidate: DecodeCandidate? = null
    ): CommitOutput {
        var s = state
        var committedPrefix = ""
        var memoryWrite = MemoryWriteDecision(false)
        // Default the punctuation width to the surrounding text. An active
        // composition (committed just below) is a stronger signal and overrides it
        // with the language the segment actually committed as.
        var punctuationContext = precedingContext

        if (state.buffer.isNotEmpty()) {
            val candidateToCommit = autoCommitCandidate?.takeIf {
                isExactCandidateForActiveScheme(it, state.buffer)
            }
            val committed = if (candidateToCommit != null) {
                doCommitCandidate(candidateToCommit, s.copy(lastAutoCommit = null))
            } else {
                commitLiteralBuffer(state.buffer, learn = true, state = s)
            }
            committedPrefix = committed.committedText.orEmpty()
            memoryWrite = committed.memoryWrite
            s = committed.newState
            punctuationContext = if (candidateToCommit != null) {
                PrecedingContext.CJK
            } else {
                PrecedingContext.LATIN
            }
        }

        val glyph = punctuationFor(p, punctuationContext)
        val raw = doCommitRaw(glyph, resetContext = isSentenceTerminator(glyph), state = s)
        return raw.copy(
            committedText = committedPrefix + glyph,
            memoryWrite = memoryWrite
        )
    }

    // English context → half-width ASCII punctuation; Chinese/neutral → full-width.
    private fun punctuationFor(canonical: String, context: PrecedingContext): String =
        if (context == PrecedingContext.LATIN) FULLWIDTH_TO_HALFWIDTH[canonical] ?: canonical
        else canonical

    fun onEnter(state: ImeStateData): CommitOutput {
        if (ctx.enterPolicy == EnterPolicy.ALWAYS_PASS_THROUGH) {
            return CommitOutput(
                committedText = "\n",
                newState = state.idle(),
                memoryWrite = MemoryWriteDecision(false)
            )
        }

        if (state.buffer.isNotEmpty()) {
            val flushed = commitLiteralBuffer(state.buffer, learn = false, state = state)
            if (ctx.enterPolicy == EnterPolicy.COMMIT_THEN_SWALLOW) {
                return flushed.copy(swallowEnter = true)
            }
            return flushed.copy(committedText = (flushed.committedText ?: "") + "\n")
        }

        return CommitOutput(
            committedText = "\n",
            newState = state,
            memoryWrite = MemoryWriteDecision(false)
        )
    }

    // ── Commit primitives ────────────────────────────────────────────────────

    private fun doCommitCandidate(
        target: DecodeCandidate,
        state: ImeStateData,
        lac: AutoCommitRecord? = null
    ): CommitOutput {
        val shouldLearn = !ctx.isSensitiveField
        if (shouldLearn) memory.record(state.buffer, target, isSensitive = false)

        val newPrev = if (ctx.isSensitiveField) null else target.text
        return CommitOutput(
            committedText = canonicalText(target),
            newState = state.copy(
                buffer = "",
                lastAutoCommit = lac,
                prevCommitted = newPrev,
                imeState = ImeState.PREDICTING
            ),
            memoryWrite = MemoryWriteDecision(shouldLearn, target)
        )
    }

    private fun commitLiteralBuffer(buffer: String, learn: Boolean, state: ImeStateData): CommitOutput {
        val cand = DecodeCandidate(buffer, "", SourceSchema.ENGLISH, CandidateType.EN_LITERAL, 0.0, false)
        if (!ctx.isSensitiveField && learn) memory.record(buffer, cand, false)

        return CommitOutput(
            committedText = buffer,
            newState = state.copy(
                buffer = "",
                prevCommitted = if (ctx.isSensitiveField) null else buffer,
                imeState = ImeState.PREDICTING
            ),
            memoryWrite = MemoryWriteDecision(!ctx.isSensitiveField && learn, cand)
        )
    }

    private fun doCommitRaw(text: String, resetContext: Boolean, state: ImeStateData): CommitOutput {
        val newState = if (resetContext || isSentenceTerminator(text)) {
            state.copy(prevCommitted = null, imeState = ImeState.IDLE)
        } else {
            state.copy(imeState = if (state.imeState == ImeState.PREDICTING) ImeState.PREDICTING else ImeState.IDLE)
        }
        return CommitOutput(
            committedText = text,
            newState = newState,
            memoryWrite = MemoryWriteDecision(false)
        )
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun canonicalText(cand: DecodeCandidate): String {
        val lower = cand.text.lowercase()
        return CANONICAL_CASE[lower] ?: cand.text
    }

    private fun isExactCandidateForActiveScheme(
        candidate: DecodeCandidate,
        buffer: String
    ): Boolean {
        val expectedSource = when (ctx.scheme) {
            com.hkmixedkeyboard.decoder.Scheme.QUICK -> SourceSchema.QUICK
            com.hkmixedkeyboard.decoder.Scheme.CANGJIE -> SourceSchema.CANGJIE
            com.hkmixedkeyboard.decoder.Scheme.JYUTPING -> SourceSchema.JYUTPING
            com.hkmixedkeyboard.decoder.Scheme.PINYIN -> SourceSchema.PINYIN
            com.hkmixedkeyboard.decoder.Scheme.MIXED_EXPERIMENTAL ->
                SourceSchema.MIXED_PHRASE
        }
        return candidate.sourceSchema == expectedSource &&
            candidate.code.equals(buffer, ignoreCase = true)
    }

    companion object {
        private val CANONICAL_CASE = com.hkmixedkeyboard.engine.EnglishLexicon.CANONICAL_CASE
    }
}

private fun isSentenceTerminator(p: String) = p in SENTENCE_TERMINATORS
