package com.hkmixedkeyboard.commit

import com.hkmixedkeyboard.decoder.CandidateType
import com.hkmixedkeyboard.decoder.DecodeCandidate
import com.hkmixedkeyboard.decoder.SourceSchema
import com.hkmixedkeyboard.engine.ClassifyResult
import com.hkmixedkeyboard.memory.IUserMemory

class CommitController(
    private val memory: IUserMemory,
    private val classify: (String) -> ClassifyResult,
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
            candidateBar = emptyList(),
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
                candidateBar = emptyList(),
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
            val flushed = commitLiteralBuffer(state.buffer, learn = true, state = state)
            val raw = doCommitRaw(" ", resetContext = false, state = flushed.newState)
            return raw.copy(
                committedText = (flushed.committedText ?: "") + " ",
                memoryWrite = flushed.memoryWrite
            )
        }

        val c = classify(state.buffer)
        val target = selectSpaceCommitTarget(state.buffer, c)
        val lac = AutoCommitRecord(target.text, state.buffer)
        // When committing English literal on space in SMART mode, also insert a trailing space
        // and exit composition so users don't need a second tap to separate words.
        // 粵拼: Space confirms the top Cantonese candidate and clears the buffer so
        // the next syllable starts fresh — with NO space character between characters
        // (求 + 其 → 求其, normal Chinese text). Only the English-literal fallback
        // below inserts a trailing space, since that path is genuine Latin text.
        if (target.type == CandidateType.EN_LITERAL) {
            val committed = doCommitCandidate(target, state, lac)
            val withSpace = doCommitRaw(" ", resetContext = true, state = committed.newState)
            return withSpace.copy(
                committedText = (committed.committedText ?: "") + " ",
                memoryWrite = committed.memoryWrite
            )
        }
        return doCommitCandidate(target, state, lac)
    }

    fun onPunctuation(
        p: String,
        state: ImeStateData,
        precedingContext: PrecedingContext = PrecedingContext.NEUTRAL
    ): CommitOutput {
        var s = state
        var committedPrefix = ""
        var memoryWrite = MemoryWriteDecision(false)
        // Default the punctuation width to the surrounding text. An active
        // composition (committed just below) is a stronger signal and overrides it
        // with the language the segment actually committed as.
        var punctuationContext = precedingContext

        if (state.buffer.isNotEmpty()) {
            val c = classify(state.buffer)
            val target = selectPunctuationCommitTarget(state.buffer, c)
            val lac = AutoCommitRecord(target.text, state.buffer)
            val committed = doCommitCandidate(target, s, lac)
            committedPrefix = committed.committedText.orEmpty()
            memoryWrite = committed.memoryWrite
            s = committed.newState
            punctuationContext =
                if (target.type == CandidateType.EN_LITERAL) PrecedingContext.LATIN
                else PrecedingContext.CJK
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
            candidateBar = emptyList(),
            memoryWrite = MemoryWriteDecision(false)
        )
    }

    // ── Commit target selection ──────────────────────────────────────────────

    fun selectSpaceCommitTarget(buffer: String, c: ClassifyResult): DecodeCandidate {
        // 粵拼: the user is romanizing Cantonese, so Space commits the Chinese
        // candidate whenever one parses exactly — including short syllables like
        // "ng"/"m"/"go" that the Quick length heuristic below would force to English.
        if (ctx.scheme == com.hkmixedkeyboard.decoder.Scheme.JYUTPING) {
            val cn = topCn(c)
            return if ((c.cnExactParsed || c.cnHasPhraseMatch) && cn != null) cn else enLiteral(c)
        }

        // Short input (≤ 2 Latin letters, e.g. "hi", "st", "ha") stays English on
        // Space. Two letters collide with 2-letter Quick codes, but a user typing two
        // letters then Space almost always wants the English text, not an auto-picked
        // Chinese character. The Chinese candidate is still one tap away in the bar,
        // and the Space already adds a trailing space + exits composing (see onSpace's
        // EN_LITERAL branch), so it doubles as "one Space = space + leave English".
        // Longer codes (phrases) keep auto-committing Chinese below.
        if (buffer.length <= 2) return enLiteral(c)

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
        private val SHORT_WHITELIST = com.hkmixedkeyboard.engine.EnglishLexicon.SHORT_WHITELIST
        private val CANONICAL_CASE = com.hkmixedkeyboard.engine.EnglishLexicon.CANONICAL_CASE
    }
}

private fun isSentenceTerminator(p: String) = p in SENTENCE_TERMINATORS
