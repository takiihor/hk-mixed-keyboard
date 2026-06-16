package com.hkmixedkeyboard.commit

import com.hkmixedkeyboard.decoder.CandidateType
import com.hkmixedkeyboard.decoder.DecodeCandidate
import com.hkmixedkeyboard.decoder.SourceSchema
import com.hkmixedkeyboard.engine.ClassifyResult
import com.hkmixedkeyboard.memory.UserMemory

class CommitController(
    private val memory: UserMemory,
    private val classify: (String) -> ClassifyResult,
    private val ctx: ImeContext
) {

    // ── Public event handlers ────────────────────────────────────────────────

    fun onKeyPress(letter: String, state: ImeStateData): CommitOutput {
        var s = state.copy(lastAutoCommit = null)

        if (s.buffer.length >= Thresholds.MAX_BUFFER_LEN) {
            val flush = commitLiteralBuffer(s.buffer, learn = false, state = s)
            s = flush.newState
        }

        val newBuf = s.buffer + letter
        val cr = classify(newBuf)
        return CommitOutput(
            committedText = null,
            newState = s.copy(buffer = newBuf, imeState = ImeState.COMPOSING),
            candidateBar = cr.cnCandidates,
            memoryWrite = MemoryWriteDecision(false)
        )
    }

    fun onCandidateTap(candidate: DecodeCandidate, state: ImeStateData): CommitOutput {
        val s = state.copy(lastAutoCommit = null)
        return doCommitCandidate(candidate, s)
    }

    fun onBackspace(state: ImeStateData, cursorJustAfterAutoCommit: Boolean = true): CommitOutput {
        if (state.buffer.isNotEmpty()) {
            val newBuf = state.buffer.dropLast(1)
            val bar = if (newBuf.isEmpty()) emptyList() else classify(newBuf).cnCandidates
            return CommitOutput(
                committedText = null,
                newState = state.copy(buffer = newBuf,
                    imeState = if (newBuf.isEmpty()) ImeState.IDLE else ImeState.COMPOSING),
                candidateBar = bar,
                memoryWrite = MemoryWriteDecision(false)
            )
        }

        val lac = state.lastAutoCommit
        if (lac != null && cursorJustAfterAutoCommit) {
            val cr = classify(lac.originalBuffer)
            return CommitOutput(
                committedText = null,
                deletedBefore = lac.text.length,
                newState = state.copy(
                    buffer = lac.originalBuffer,
                    lastAutoCommit = null,
                    imeState = ImeState.COMPOSING
                ),
                candidateBar = cr.cnCandidates,
                memoryWrite = MemoryWriteDecision(false)
            )
        }

        // Delete char before cursor (delegated to host in real IME)
        return CommitOutput(
            committedText = null,
            deletedBefore = 1,
            newState = state,
            candidateBar = emptyList(),
            memoryWrite = MemoryWriteDecision(false)
        )
    }

    fun onSpace(state: ImeStateData): CommitOutput {
        if (state.buffer.isEmpty()) {
            return doCommitRaw(" ", resetContext = false, state = state)
        }

        if (ctx.spaceMode == SpaceMode.ALWAYS_SPACE) {
            val flushed = commitLiteralBuffer(state.buffer, learn = false, state = state)
            return doCommitRaw(" ", resetContext = false, state = flushed.newState)
        }

        val c = classify(state.buffer)
        val target = selectSpaceCommitTarget(state.buffer, c)
        val lac = AutoCommitRecord(target.text, state.buffer)
        return doCommitCandidate(target, state, lac)
    }

    fun onPunctuation(p: String, state: ImeStateData): CommitOutput {
        var s = state
        var lac: AutoCommitRecord? = null

        if (state.buffer.isNotEmpty()) {
            val c = classify(state.buffer)
            val target = selectPunctuationCommitTarget(state.buffer, c)
            lac = AutoCommitRecord(target.text, state.buffer)
            val committed = doCommitCandidate(target, s, lac)
            s = committed.newState
        }

        return doCommitRaw(p, resetContext = isSentenceTerminator(p), state = s)
    }

    fun onEnter(state: ImeStateData): CommitOutput {
        if (ctx.enterPolicy == EnterPolicy.ALWAYS_PASS_THROUGH) {
            return CommitOutput(
                committedText = "\n",
                newState = state.idle(),
                candidateBar = emptyList(),
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
            candidateBar = emptyList(),
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
            candidateBar = if (ctx.isSensitiveField) emptyList()
                           else buildPostCommitBar(target.text),
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
            candidateBar = if (ctx.isSensitiveField) emptyList()
                           else buildPostCommitBar(buffer),
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
            candidateBar = if (resetContext) emptyList() else emptyList(),
            memoryWrite = MemoryWriteDecision(false)
        )
    }

    // ── Commit target selection ──────────────────────────────────────────────

    fun selectSpaceCommitTarget(buffer: String, c: ClassifyResult): DecodeCandidate {
        memory.hardOverride(buffer, ctx.isSensitiveField)?.let { return it }

        val cnCommittable = c.cnExactParsed || c.cnHasPhraseMatch
        val enStrong = c.enIsWord || c.enStrongPrefix

        return when {
            cnCommittable && !enStrong -> topCn(c)!!
            enStrong && !cnCommittable -> enLiteral(c)
            cnCommittable && enStrong  -> tieBreak(buffer, c)
            else                       -> enLiteral(c)
        }
    }

    fun selectPunctuationCommitTarget(buffer: String, c: ClassifyResult): DecodeCandidate {
        val lower = buffer.lowercase()
        if (c.enIsWord || c.enStrongPrefix || SHORT_WHITELIST.contains(lower))
            return enLiteral(c)
        if (c.cnExactParsed || c.cnHasPhraseMatch)
            return topCn(c)!!
        return enLiteral(c)
    }

    fun tieBreak(buffer: String, c: ClassifyResult): DecodeCandidate {
        if (!ctx.isSensitiveField) {
            val r = memory.cnRatio(buffer)
            if (r >= Thresholds.CN_RATIO_THRESHOLD) return topCn(c)!!
            if (r <= Thresholds.EN_RATIO_THRESHOLD) return enLiteral(c)
        }

        val top = topCn(c)
        if (buffer.length <= 2 && top != null && top.isHkCore) return top
        return enLiteral(c)
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun topCn(c: ClassifyResult): DecodeCandidate? =
        c.cnCandidates.firstOrNull()

    private fun enLiteral(c: ClassifyResult) =
        DecodeCandidate(c.enLiteral, "", SourceSchema.ENGLISH, CandidateType.EN_LITERAL, 0.0, false)

    private fun canonicalText(cand: DecodeCandidate): String {
        val lower = cand.text.lowercase()
        return CANONICAL_CASE[lower] ?: cand.text
    }

    private fun buildPostCommitBar(lastText: String): List<DecodeCandidate> {
        // Stub: real implementation queries phrase dictionary for continuations.
        // Returns empty list here; tested via acceptance tests in a later pass.
        return emptyList()
    }

    companion object {
        private val SHORT_WHITELIST = setOf(
            "ok", "no", "go", "hi", "pm", "am", "ai", "it", "hr", "cv", "id", "ot",
            "dm", "ig", "fb", "tg", "qr", "kpi", "pdf", "doc", "ppt", "tax",
            "mtr", "fps", "mpf", "hk", "hkd", "usd"
        )
        private val CANONICAL_CASE = mapOf(
            "mtr" to "MTR", "fps" to "FPS", "mpf" to "MPF", "hkd" to "HKD",
            "usd" to "USD", "pdf" to "PDF", "qr" to "QR"
        )
    }
}

private fun isSentenceTerminator(p: String) = p in SENTENCE_TERMINATORS
