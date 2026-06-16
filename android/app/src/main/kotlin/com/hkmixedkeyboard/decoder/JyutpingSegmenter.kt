package com.hkmixedkeyboard.decoder

/**
 * Segments a continuous toneless Jyutping romanization buffer into its constituent
 * syllables — e.g. "neihou" → ["nei", "hou"], "ngodei" → ["ngo", "dei"].
 *
 * Uses dynamic programming over a known syllable set, choosing the segmentation with
 * the **fewest** syllables (maximal-munch); ties prefer a longer leading syllable.
 * This matches how Cantonese romanization is normally read and avoids spurious splits
 * (e.g. it keeps "ngaa" whole rather than "ng" + "aa").
 *
 * Returns null when the buffer cannot be fully covered by valid syllables, or when it
 * is a single syllable (nothing to compose — the exact-match path already handles it).
 */
class JyutpingSegmenter(private val syllables: Set<String>) {

    private val maxSyllableLen: Int = syllables.maxOfOrNull { it.length } ?: 0

    fun segment(buffer: String): List<String>? {
        if (buffer.isEmpty() || maxSyllableLen == 0) return null
        val n = buffer.length

        // minCount[i] = fewest syllables that exactly cover buffer[0, i).
        // back[i]     = start index of the last syllable in that best cover.
        val minCount = IntArray(n + 1) { Int.MAX_VALUE }
        val back = IntArray(n + 1) { -1 }
        minCount[0] = 0

        for (i in 1..n) {
            val lo = maxOf(0, i - maxSyllableLen)
            // j ascending → longer trailing syllable first, so on a tie in syllable
            // count we keep the earliest j (the longest last syllable).
            for (j in lo until i) {
                if (minCount[j] == Int.MAX_VALUE) continue
                if (buffer.substring(j, i) in syllables) {
                    val candidate = minCount[j] + 1
                    if (candidate < minCount[i]) {
                        minCount[i] = candidate
                        back[i] = j
                    }
                }
            }
        }

        if (minCount[n] == Int.MAX_VALUE) return null

        val out = ArrayDeque<String>()
        var i = n
        while (i > 0) {
            val j = back[i]
            out.addFirst(buffer.substring(j, i))
            i = j
        }
        return if (out.size >= 2) out.toList() else null
    }
}
