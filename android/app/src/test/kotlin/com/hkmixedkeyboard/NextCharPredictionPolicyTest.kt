package com.hkmixedkeyboard

import com.hkmixedkeyboard.decoder.DecodeCandidate
import com.hkmixedkeyboard.decoder.NextCharPredictionPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NextCharPredictionPolicyTest {

    private fun next(vararg texts: String): List<DecodeCandidate> =
        texts.map { cnChar(it, "") }

    // Keys are what nextCharIndex holds: phrase prefixes of 1..3 characters.
    // The policy matches them against *suffixes* of the committed run, so this
    // fixture deliberately covers all three lengths of one run's tail.
    private val index = mapOf(
        "我" to next("哋", "們"),
        "我哋" to next("今", "去"),
        "我哋今" to next("日", "晚"),
        "哋今" to next("日", "朝"),
        "今" to next("日", "年"),
        "今日" to next("去", "會"),
        "日" to next("本", "子")
    )

    // ── Key selection ─────────────────────────────────────────────────────

    @Test
    fun `keys are suffixes, longest first, capped at the index key length`() {
        assertEquals(listOf("哋今日", "今日", "日"), NextCharPredictionPolicy.lookupKeys("我哋今日"))
        assertEquals(listOf("我哋", "哋"), NextCharPredictionPolicy.lookupKeys("我哋"))
        assertEquals(listOf("我"), NextCharPredictionPolicy.lookupKeys("我"))
        assertEquals(emptyList<String>(), NextCharPredictionPolicy.lookupKeys(""))
    }

    @Test
    fun `keys never split a supplementary character`() {
        // 𠮟 (U+20B9F) is a surrogate pair — HKSCS is full of these.
        val run = "唔該𠮟"
        val keys = NextCharPredictionPolicy.lookupKeys(run)

        assertEquals(listOf("唔該𠮟", "該𠮟", "𠮟"), keys)
        keys.forEach { key ->
            assertTrue("key $key splits a surrogate pair", key.none { it.isSurrogate() && key.length == 1 })
        }
    }

    // ── The bug this policy exists to fix ─────────────────────────────────

    @Test
    fun `a run longer than the longest key still predicts`() {
        // Was the shipped behaviour: index["我哋今日"] is null, so predictions
        // died for the rest of the sentence.
        val out = NextCharPredictionPolicy.predict(index, "我哋今日")

        assertEquals(listOf("去", "會", "本", "子"), out.map { it.text })
    }

    @Test
    fun `committing a four-character phrase still predicts`() {
        val four = mapOf("政府" to next("部"))
        val out = NextCharPredictionPolicy.predict(four, "香港政府")

        assertEquals(listOf("部"), out.map { it.text })
    }

    // ── Ordering and de-duplication ───────────────────────────────────────

    @Test
    fun `more specific context ranks first`() {
        // Suffixes of "我哋今" are 我哋今 -> 哋今 -> 今, and each contributes in
        // that order: 日/晚, then 朝 (日 already seen), then 年.
        val out = NextCharPredictionPolicy.predict(index, "我哋今")

        assertEquals(listOf("日", "晚", "朝", "年"), out.map { it.text })
    }

    @Test
    fun `a character offered by two suffixes keeps its most specific position`() {
        // 日 is offered by all three suffixes. It must take the slot the longest
        // one gives it, and appear exactly once.
        val out = NextCharPredictionPolicy.predict(index, "我哋今")

        assertEquals(0, out.indexOfFirst { it.text == "日" })
        assertEquals(1, out.count { it.text == "日" })
    }

    // ── Custom-word continuations (自訂詞庫) ───────────────────────────────

    @Test
    fun `a custom word becomes reachable as a continuation`() {
        val custom = NextCharPredictionPolicy.indexOf(listOf("曬冷"))

        assertEquals(listOf("冷"), NextCharPredictionPolicy.predict(custom, "曬").map { it.text })
    }

    @Test
    fun `custom continuations cover every prefix length up to the key cap`() {
        val custom = NextCharPredictionPolicy.indexOf(listOf("我哋今日"))

        assertEquals(listOf("哋"), NextCharPredictionPolicy.predict(custom, "我").map { it.text })
        assertEquals(listOf("今"), NextCharPredictionPolicy.predict(custom, "我哋").map { it.text })
        assertEquals(listOf("日"), NextCharPredictionPolicy.predict(custom, "我哋今").map { it.text })
    }

    @Test
    fun `single-character custom words contribute no continuation`() {
        assertTrue(NextCharPredictionPolicy.indexOf(listOf("冧")).isEmpty())
    }

    @Test
    fun `custom continuations outrank the corpus for the same prefix`() {
        val custom = NextCharPredictionPolicy.indexOf(listOf("我叻"))
        val merged = NextCharPredictionPolicy.predict(custom, "我") +
            NextCharPredictionPolicy.predict(index, "我")

        assertEquals("叻", merged.first().text)
        assertTrue(merged.map { it.text }.containsAll(listOf("叻", "哋", "們")))
    }

    @Test
    fun `custom words built from supplementary characters stay intact`() {
        val custom = NextCharPredictionPolicy.indexOf(listOf("𠮟人"))

        assertEquals(listOf("人"), NextCharPredictionPolicy.predict(custom, "𠮟").map { it.text })
    }

    @Test
    fun `an unknown run yields nothing rather than throwing`() {
        assertEquals(emptyList<String>(), NextCharPredictionPolicy.predict(index, "零零零").map { it.text })
        assertEquals(emptyList<String>(), NextCharPredictionPolicy.predict(index, "").map { it.text })
        assertEquals(emptyList<String>(), NextCharPredictionPolicy.predict(emptyMap(), "我").map { it.text })
    }
}
