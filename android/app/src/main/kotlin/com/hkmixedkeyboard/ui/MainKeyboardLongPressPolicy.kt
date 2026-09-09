package com.hkmixedkeyboard.ui

/** Resolves the small set of main-keyboard actions that differ between tap and hold. */
object MainKeyboardLongPressPolicy {
    fun shortPressTextFor(label: String): String = label

    /**
     * What a held key commits instead of its face.
     *
     * The symbol pages have had rich long-press alternates for a while; the main
     * layer had exactly one (。 to a full stop), so an iPhone user's habit of
     * holding a key to reach its variant found nothing. The digits carry the
     * symbols they sit above on a hardware keyboard — the same pairing iOS uses
     * on its number page — which puts the common ones a hold away instead of a
     * trip through 符.
     */
    fun longPressTextFor(label: String): String? = when (label) {
        KeyboardLayout.KEY_PERIOD -> "."
        else -> DIGIT_ALTERNATES[label]
    }

    private val DIGIT_ALTERNATES = mapOf(
        "1" to "!", "2" to "@", "3" to "#", "4" to "$", "5" to "%",
        "6" to "^", "7" to "&", "8" to "*", "9" to "(", "0" to ")"
    )
}
