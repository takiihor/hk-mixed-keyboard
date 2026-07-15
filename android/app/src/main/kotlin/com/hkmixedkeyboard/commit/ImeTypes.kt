package com.hkmixedkeyboard.commit

import com.hkmixedkeyboard.decoder.DecodeCandidate

enum class ImeState { IDLE, COMPOSING, PREDICTING }

enum class EnterPolicy { ALWAYS_PASS_THROUGH, COMMIT_THEN_SWALLOW, COMMIT_AND_SEND }

data class ImeContext(
    val scheme: com.hkmixedkeyboard.decoder.Scheme = com.hkmixedkeyboard.decoder.Scheme.QUICK,
    val isSensitiveField: Boolean = false,
    val enterPolicy: EnterPolicy = EnterPolicy.COMMIT_THEN_SWALLOW
)

data class AutoCommitRecord(val text: String, val originalBuffer: String)

data class ImeStateData(
    val buffer: String = "",
    val lastAutoCommit: AutoCommitRecord? = null,
    val prevCommitted: String? = null,
    val imeState: ImeState = ImeState.IDLE
) {
    fun withBuffer(b: String) = copy(buffer = b, imeState = if (b.isEmpty()) ImeState.IDLE else ImeState.COMPOSING)
    fun idle() = copy(buffer = "", lastAutoCommit = null, imeState = ImeState.IDLE)
    fun predicting(committed: String) = copy(buffer = "", imeState = ImeState.PREDICTING, prevCommitted = committed)
}

sealed class ImeEvent {
    data class KeyPress(val letter: String) : ImeEvent()
    data class CandidateTap(val candidate: DecodeCandidate) : ImeEvent()
    data object Space : ImeEvent()
    data object Backspace : ImeEvent()
    data class Punctuation(val p: String) : ImeEvent()
    data object Enter : ImeEvent()
}

data class MemoryWriteDecision(
    val shouldWrite: Boolean,
    val candidate: DecodeCandidate? = null
)

data class CommitOutput(
    val committedText: String?,           // text sent to app (null if nothing committed)
    val deletedBefore: Int = 0,           // chars deleted before cursor (backspace revert)
    val newState: ImeStateData,
    val memoryWrite: MemoryWriteDecision,
    val swallowEnter: Boolean = false     // true when Enter policy = COMMIT_THEN_SWALLOW
)

// Constants from spec §14.5
object Thresholds {
    const val MAX_BUFFER_LEN = 20
    // Both romanization corpora carry long, valid phrase keys. Keep their
    // bounded composition ceiling aligned with their corpus generators.
    const val ROMANIZATION_MAX_BUFFER_LEN = 72
    const val PINYIN_MAX_BUFFER_LEN = ROMANIZATION_MAX_BUFFER_LEN
    const val JYUTPING_MAX_BUFFER_LEN = ROMANIZATION_MAX_BUFFER_LEN

    /** Pinyin keys reach 67 and Jyutping keys reach 39 letters in pinned corpora. */
    fun maxBufferLength(scheme: com.hkmixedkeyboard.decoder.Scheme): Int =
        when (scheme) {
            com.hkmixedkeyboard.decoder.Scheme.PINYIN -> PINYIN_MAX_BUFFER_LEN
            com.hkmixedkeyboard.decoder.Scheme.JYUTPING -> JYUTPING_MAX_BUFFER_LEN
            else -> MAX_BUFFER_LEN
        }

    const val USER_MEM_OVERRIDE_MIN_COUNT = 3
    const val USER_MEM_OVERRIDE_MIN_CONF = 0.80
    const val CN_RATIO_THRESHOLD = 0.65
    const val EN_RATIO_THRESHOLD = 0.35
}

val SENTENCE_TERMINATORS = setOf("。", "？", "！", "?!", "？！", ".", "?", "!")
val NON_RESET_PUNCTUATION = setOf("，", "、", ":", "；", ",")

// Language of the text immediately before a punctuation key press, used to pick
// half-width (LATIN) vs full-width (CJK) punctuation. NEUTRAL means no clear
// signal — default to full-width, since this is a Chinese-first keyboard.
enum class PrecedingContext { LATIN, CJK, NEUTRAL }

// Full-width (Chinese) → half-width (English) punctuation. Keys are the canonical
// labels emitted by the punctuation keys; values are their ASCII equivalents.
val FULLWIDTH_TO_HALFWIDTH = mapOf("。" to ".", "，" to ",", "？" to "?", "！" to "!")
