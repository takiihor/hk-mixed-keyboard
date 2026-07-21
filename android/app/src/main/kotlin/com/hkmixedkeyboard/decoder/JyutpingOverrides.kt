package com.hkmixedkeyboard.decoder

/**
 * Merges the reviewed Hong Kong ranking layer over the untouched source snapshot.
 *
 * An override row *replaces* the snapshot row for the same (jyutping, text) pair,
 * in either direction. Promotion lifts a common Cantonese candidate above the
 * snapshot's written-corpus frequencies; demotion moves a candidate that must not
 * lead — profanity keeps its entry and stays selectable, it just stops ranking
 * first. Frequency alone cannot express demotion, which is why the merge drops the
 * base row instead of appending beside it.
 */
object JyutpingOverrides {

    fun merge(base: List<JyutpingEntry>, overrides: List<JyutpingEntry>): List<JyutpingEntry> {
        if (overrides.isEmpty()) return base
        val overridden = overrides.mapTo(HashSet()) { it.jyutping to it.chinese }
        return base.filterNot { (it.jyutping to it.chinese) in overridden } + overrides
    }
}
