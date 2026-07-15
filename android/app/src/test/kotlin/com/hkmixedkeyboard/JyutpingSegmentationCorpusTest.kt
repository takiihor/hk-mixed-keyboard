package com.hkmixedkeyboard

import com.hkmixedkeyboard.decoder.JyutpingSegmenter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Validates syllable reachability against the real Jyutping corpus. Segmentation
 * is intentionally separate from phrase composition: a successful character-only
 * segmentation is not phrase-level language evidence.
 */
class JyutpingSegmentationCorpusTest {

    private val corpusDir = "src/main/assets/corpus"

    // Syllable keys are built exactly the way CorpusLoader does.
    private val readings: Map<String, List<String>> by lazy {
        readJyutping("$corpusDir/jyutping.csv")
            .filter { it.second.length == 1 }
            .groupBy { it.first }
            .mapValues { (_, rows) -> rows.sortedByDescending { it.third }.map { it.second } }
    }

    private val seg by lazy { JyutpingSegmenter(readings.keys) }

    @Test
    fun `neihou reaches two canonical syllables`() {
        assumeTrue(readings.isNotEmpty())
        assertEquals(listOf("nei", "hou"), seg.segment("neihou"))
    }

    @Test
    fun `ngodei reaches two canonical syllables`() {
        assumeTrue(readings.isNotEmpty())
        assertEquals(listOf("ngo", "dei"), seg.segment("ngodei"))
    }

    @Test
    fun `a non-dictionary phrase that the old engine missed now segments`() {
        assumeTrue(readings.isNotEmpty())
        // This proves syllable reachability only; production must not synthesize
        // a phrase merely because the two character readings can be segmented.
        assertEquals(emptyList<Triple<String, String, Double>>(),
            readJyutping("$corpusDir/jyutping.csv").filter { it.first == "hounei" })
        assertNotNull(seg.segment("hounei"))
    }
}
