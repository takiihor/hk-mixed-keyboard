package com.hkmixedkeyboard

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hkmixedkeyboard.decoder.CorpusBackedDecoder
import com.hkmixedkeyboard.decoder.CorpusLoader
import com.hkmixedkeyboard.decoder.Scheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JyutpingToneProductionTest {
    @Test
    fun packagedTonalEvidenceControlsProductionRanking() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val assetHeader = context.assets.open("jyutping_tonal_readings.csv")
            .bufferedReader()
            .useLines { lines ->
                lines.first { line -> line.isNotBlank() && !line.startsWith("#") }
            }
        assertTrue(assetHeader.contains("readings"))

        val decoder = CorpusBackedDecoder(CorpusLoader(context))
        val hai6 = decoder.decode("hai6", Scheme.JYUTPING)
        val hai1 = decoder.decode("hai1", Scheme.JYUTPING)

        assertEquals("係", hai6.candidates.first().text)
        assertEquals("hai6", hai6.candidates.first().annotation)
        assertEquals("閪", hai1.candidates.first().text)
        assertEquals("hai1", hai1.candidates.first().annotation)
    }
}
