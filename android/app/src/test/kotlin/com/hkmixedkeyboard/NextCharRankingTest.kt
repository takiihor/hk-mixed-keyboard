package com.hkmixedkeyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Ranking of next-character continuations, checked against the shipped phrase
 * corpus by rebuilding the index the way CorpusLoader does.
 *
 * The two rules under test pull against each other: breadth stops a continuation
 * attested by one frequent phrase outranking one attested by twenty, and hk_core
 * stops that same breadth rule quietly replacing Cantonese continuations with
 * Mandarin ones.
 */
class NextCharRankingTest {

    private class Support {
        var best = 0.0
        var total = 0.0
        var hkCore = false
        val score get() = best + BREADTH_WEIGHT * (total - best)
    }

    private val index: Map<String, List<Pair<String, Support>>> by lazy {
        val acc = HashMap<String, HashMap<String, Support>>()
        File("src/main/assets/corpus/hk_core_phrases.csv").forEachLine { line ->
            val t = line.trim()
            if (t.startsWith("#") || t.isBlank()) return@forEachLine
            val cols = t.split(",")
            if (cols.size < 4 || cols[0] == "phrase") return@forEachLine
            val phrase = cols[0]
            val freq = cols[2].toDoubleOrNull() ?: 0.0
            val hkCore = cols[3].trim() == "1"
            if (phrase.length < 2) return@forEachLine
            for (i in 1..minOf(3, phrase.length - 1)) {
                val bucket = acc.getOrPut(phrase.substring(0, i)) { HashMap() }
                for (end in intArrayOf(i + 1, phrase.length)) {
                    val support = bucket.getOrPut(phrase.substring(i, end)) { Support() }
                    support.best = maxOf(support.best, freq)
                    support.total += freq
                    support.hkCore = support.hkCore || hkCore
                }
            }
        }
        acc.mapValues { (_, nexts) ->
            nexts.entries
                .sortedWith(
                    compareByDescending<Map.Entry<String, Support>> { if (it.value.hkCore) 1 else 0 }
                        .thenByDescending { if (it.key.length == 1) 1 else 0 }
                        .thenByDescending { it.value.score }
                )
                .map { it.key to it.value }
        }
    }

    private fun top(prefix: String, n: Int) = index[prefix].orEmpty().take(n).map { it.first }

    @Test
    fun `Cantonese plural 哋 keeps its slot ahead of Mandarin 們`() {
        listOf("我", "你", "佢").forEach { pronoun ->
            val ranked = top(pronoun, 20)
            val dei = ranked.indexOf("哋")
            val men = ranked.indexOf("們")

            assertTrue("$pronoun offers no 哋: $ranked", dei >= 0)
            assertTrue(
                "$pronoun ranks 們 ($men) above 哋 ($dei): $ranked",
                men < 0 || dei < men
            )
        }
    }

    @Test
    fun `everyday Cantonese continuations lead 唔`() {
        assertTrue("唔 -> ${top("唔", 5)}", top("唔", 5).containsAll(listOf("該", "係")))
    }

    @Test
    fun `breadth lifts a widely attested continuation over a one-off`() {
        // 可以 appears across dozens of phrases; ranking on the single best phrase
        // alone pushed it below continuations seen exactly once.
        assertTrue("可 -> ${top("可", 4)}", top("可", 4).contains("以"))
        assertTrue("如 -> ${top("如", 3)}", top("如", 3).contains("何"))
    }

    @Test
    fun `an hk_core continuation is never ranked below a non-core one`() {
        // Only 21 of the corpus's 39,906 phrases are marked hk_core, so just a
        // handful of prefixes mix the two classes — but those handful are exactly
        // the Cantonese ones (我哋, 你哋, 唔該 …) this keyboard exists to protect.
        var mixed = 0
        index.forEach { (prefix, ranked) ->
            val lastCore = ranked.indexOfLast { it.second.hkCore }
            val firstNonCore = ranked.indexOfFirst { !it.second.hkCore }
            if (lastCore >= 0 && firstNonCore >= 0) {
                assertTrue(
                    "$prefix ranks a non-core continuation above an hk_core one",
                    lastCore < firstNonCore
                )
                mixed++
            }
        }
        assertTrue("expected the known mixed prefixes, saw $mixed", mixed >= 5)
    }

    // ── Word-level completions ────────────────────────────────────────────

    @Test
    fun `a prefix offers the rest of the word, not just the next character`() {
        val words = index["香"].orEmpty().map { it.first }.filter { it.length > 1 }

        assertTrue("香 offers no word completion: $words", words.isNotEmpty())
        assertTrue("香 -> $words", words.contains("港人"))
    }

    @Test
    fun `single characters still lead the word completions`() {
        val ranked = index["香"].orEmpty().map { it.first }
        val firstWord = ranked.indexOfFirst { it.length > 1 }
        val lastChar = ranked.indexOfLast { it.length == 1 }

        assertTrue("香 -> $ranked", firstWord > 0)
        assertTrue("a word completion precedes a character: $ranked", lastChar < firstWord)
    }

    @Test
    fun `a continuation is never listed twice`() {
        // For a two-character phrase the "next character" and the "rest of the
        // word" are the same string, so the two must collapse into one entry.
        listOf("香", "我", "你", "電").forEach { prefix ->
            val ranked = index[prefix].orEmpty().map { it.first }
            assertEquals("$prefix has duplicates: $ranked", ranked.size, ranked.distinct().size)
        }
    }

    @Test
    fun `word completions reach a useful share of prefixes`() {
        val withWords = index.count { (_, ranked) -> ranked.any { it.first.length > 1 } }

        assertTrue("only $withWords prefixes offer a word completion", withWords > 2_000)
    }

    private companion object {
        const val BREADTH_WEIGHT = 0.15
    }
}
