package com.hkmixedkeyboard.engine

/**
 * Hand-curated English vocabulary specific to Hong Kong.
 *
 * This object used to carry three more lists — ENGLISH_WORDS and
 * HIGH_FREQ_COMPLETIONS alongside these — which fed only the classifier's
 * English half. Nothing read that half, so a six-entry hardcoded completion
 * table (hap → happy) influenced nothing while EnglishCompletionIndex served
 * the bar from 20,330 words. They are gone; what remains is live.
 */
object EnglishLexicon {

    /**
     * Short tokens a Hong Kong typist means as English even though they collide
     * with Quick codes, mapped to canonical casing where the token is an acronym.
     * Fed to [EnglishCompletionIndex], which the CC-CEDICT-derived word list
     * cannot supply: "mtr" and "hkd" are not English dictionary headwords.
     */
    val LOCAL_TOKENS: Map<String, String?> = mapOf(
        "mtr" to "MTR", "fps" to "FPS", "mpf" to "MPF", "hkd" to "HKD",
        "usd" to "USD", "pdf" to "PDF", "qr" to "QR", "kpi" to "KPI",
        "cv" to "CV", "id" to "ID", "hr" to "HR", "ai" to "AI",
        "doc" to null, "ppt" to null, "tax" to null, "hk" to "HK",
        "ok" to null, "dm" to "DM", "ig" to "IG", "fb" to "FB", "tg" to "TG"
    )

    /** Canonical casing for acronyms typed in lowercase, used at commit time. */
    val CANONICAL_CASE: Map<String, String> =
        LOCAL_TOKENS.filterValues { it != null }.mapValues { it.value!! }
}
