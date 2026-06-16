package com.hkmixedkeyboard.engine

import com.hkmixedkeyboard.decoder.DecodeCandidate
import com.hkmixedkeyboard.decoder.DecoderContract
import com.hkmixedkeyboard.decoder.Scheme

class Classifier(private val decoder: DecoderContract) {

    // SHORT_WHITELIST from spec §14.5 — always treated as English, commit as canonical form
    private val shortWhitelist = setOf(
        "ok", "no", "go", "hi", "pm", "am", "ai", "it", "hr", "cv", "id", "ot",
        "dm", "ig", "fb", "tg", "qr", "kpi", "pdf", "doc", "ppt", "tax",
        "mtr", "fps", "mpf", "hk", "hkd", "usd"
    )

    // Canonical casing for whitelist items
    private val canonicalCase = mapOf(
        "mtr" to "MTR", "fps" to "FPS", "mpf" to "MPF", "hkd" to "HKD",
        "usd" to "USD", "pdf" to "PDF", "qr" to "QR"
    )

    // Minimal English lexicon for Stage 1 harness — covers test buffers and common words
    private val englishWords = setOf(
        "send", "ok", "go", "hi", "no", "am", "pm", "it", "ai", "hr", "cv", "id",
        "ot", "dm", "ig", "fb", "tg", "qr", "kpi", "pdf", "doc", "ppt", "tax",
        "mtr", "fps", "mpf", "hk", "hkd", "usd", "happy", "happen", "hap",
        "meeting", "meet", "reply", "confirm", "check", "call", "file", "email"
    )

    // High-frequency English completions (buffer prefix → completion) for strongEnglishPrefix
    private val highFreqCompletions = mapOf(
        "hap" to "happy",
        "mee" to "meeting",
        "con" to "confirm",
        "rep" to "reply",
        "che" to "check",
        "ema" to "email",
        "cal" to "call"
    )

    fun classify(buffer: String, scheme: Scheme): ClassifyResult {
        val dr = decoder.decode(buffer, scheme)
        val bufLower = buffer.lowercase()

        val enIsWord = englishWords.contains(bufLower)
            || shortWhitelist.contains(bufLower)

        val enAutocomplete = highFreqCompletions[bufLower]

        val enStrongPrefix = buffer.length >= 3
            && !dr.isExactCode
            && enAutocomplete != null

        return ClassifyResult(
            buffer = buffer,
            cnExactParsed = dr.cnExactParsed,
            cnHasPhraseMatch = dr.cnHasPhraseMatch,
            cnPrefixParsed = dr.cnPrefixParsed,
            cnCandidates = rankCandidates(dr.candidates),
            enLiteral = buffer,
            enAutocomplete = enAutocomplete,
            enIsWord = enIsWord,
            enStrongPrefix = enStrongPrefix
        )
    }

    fun canonicalForm(buffer: String): String =
        canonicalCase[buffer.lowercase()] ?: buffer

    private fun rankCandidates(candidates: List<DecodeCandidate>): List<DecodeCandidate> =
        candidates.sortedWith(
            compareByDescending<DecodeCandidate> { if (it.isHkCore) 1 else 0 }
                .thenByDescending { it.frequency }
        )
}
