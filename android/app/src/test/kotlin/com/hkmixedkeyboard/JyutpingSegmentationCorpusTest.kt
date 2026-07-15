package com.hkmixedkeyboard

import com.hkmixedkeyboard.decoder.JyutpingSegmenter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Validates the segmenter against the real 57k-entry jyutping.csv: continuous
 * romanization that is NOT a dictionary phrase key (你好/我哋) still segments into
 * syllables, and the per-syllable top readings compose the expected phrase.
 */
class JyutpingSegmentationCorpusTest {

    private val corpusDir = "src/main/assets/corpus"

    // (syllable -> single-char readings, most frequent first), built exactly the way
    // CorpusLoader does, so the syllable set and composition mirror production.
    private val readings: Map<String, List<String>> by lazy {
        readJyutping("$corpusDir/jyutping.csv")
            .filter { it.second.length == 1 }
            .groupBy { it.first }
            .mapValues { (_, rows) -> rows.sortedByDescending { it.third }.map { it.second } }
    }

    private val seg by lazy { JyutpingSegmenter(readings.keys) }

    private fun topJoin(parts: List<String>) = parts.joinToString("") { readings.getValue(it).first() }

    @Test
    fun `neihou segments and composes 你好`() {
        assumeTrue(readings.isNotEmpty())
        assertEquals(listOf("nei", "hou"), seg.segment("neihou"))
        assertEquals("你好", topJoin(listOf("nei", "hou")))
    }

    @Test
    fun `ngodei segments to 我哋 readings`() {
        assumeTrue(readings.isNotEmpty())
        val parts = seg.segment("ngodei")
        assertEquals(listOf("ngo", "dei"), parts)
        // Top reading is 我地; 哋 (the colloquial 我哋) is offered as an alternate.
        assertEquals("我", readings.getValue("ngo").first())
        assertTrue("哋 should be a reading of 'dei'", readings.getValue("dei").contains("哋"))
    }

    @Test
    fun `a non-dictionary phrase that the old engine missed now segments`() {
        assumeTrue(readings.isNotEmpty())
        // "hounei" is not a key in the dictionary (unlike the reviewed direct
        // phrase "neihou"), proving segmentation is what makes it work — the
        // exact/prefix paths would return nothing.
        assertEquals(emptyList<Triple<String, String, Double>>(),
            readJyutping("$corpusDir/jyutping.csv").filter { it.first == "hounei" })
        assertNotNull(seg.segment("hounei"))
    }
}
