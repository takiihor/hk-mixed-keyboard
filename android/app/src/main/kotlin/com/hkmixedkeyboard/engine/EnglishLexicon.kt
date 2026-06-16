package com.hkmixedkeyboard.engine

/**
 * Single source of truth for the small built-in English vocabulary used by both
 * the [Classifier] (candidate classification) and the commit layer
 * (com.hkmixedkeyboard.commit.CommitController) for commit-target selection.
 *
 * These lists previously lived, duplicated, in both classes and could drift apart.
 */
object EnglishLexicon {

    // Two-or-three letter tokens that should be treated as English even though they
    // collide with short Quick codes (e.g. "ok", "mtr", "hkd").
    val SHORT_WHITELIST = setOf(
        "ok", "no", "go", "hi", "pm", "am", "ai", "it", "hr", "cv", "id", "ot",
        "dm", "ig", "fb", "tg", "qr", "kpi", "pdf", "doc", "ppt", "tax",
        "mtr", "fps", "mpf", "hk", "hkd", "usd"
    )

    // Canonical casing for acronyms typed in lowercase.
    val CANONICAL_CASE = mapOf(
        "mtr" to "MTR", "fps" to "FPS", "mpf" to "MPF", "hkd" to "HKD",
        "usd" to "USD", "pdf" to "PDF", "qr" to "QR"
    )

    // Whole words recognised as committable English. Includes SHORT_WHITELIST plus
    // a few high-frequency words and their completion prefixes.
    val ENGLISH_WORDS = SHORT_WHITELIST + setOf(
        "send",
        "happy", "meeting", "reply", "confirm", "check", "call", "file", "email",
        "hap", "mee", "con", "rep", "che"
    )

    // Prefix → full word for built-in autocompletion of common English words.
    val HIGH_FREQ_COMPLETIONS = mapOf(
        "hap" to "happy", "mee" to "meeting", "con" to "confirm",
        "rep" to "reply", "che" to "check", "cal" to "call"
    )
}
