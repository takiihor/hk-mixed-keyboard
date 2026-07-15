package com.hkmixedkeyboard

import android.content.Context
import android.content.ContextWrapper
import com.hkmixedkeyboard.commit.ImeStateData
import com.hkmixedkeyboard.decoder.CandidateType
import com.hkmixedkeyboard.decoder.ChineseAssistEntry
import com.hkmixedkeyboard.decoder.CorpusLoader
import com.hkmixedkeyboard.decoder.DecodeCandidate
import com.hkmixedkeyboard.decoder.Scheme
import com.hkmixedkeyboard.decoder.SourceSchema
import com.hkmixedkeyboard.engine.CandidateDisplayPolicy
import com.hkmixedkeyboard.ime.CandidateCommitPolicy
import com.hkmixedkeyboard.ui.CandidatePresentation
import com.hkmixedkeyboard.util.Csv
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ChineseEnglishAssistTest {

    @Test
    fun `reviewed Chinese assist maps 巴士 to bus`() {
        val row = File("src/main/assets/corpus/chinese_assist.csv").useLines { lines ->
            lines.filter { it.isNotBlank() && !it.startsWith("#") }
                .drop(1)
                .map(Csv::split)
                .single { it[0] == "巴士" }
        }

        assertEquals("bus", row[1])
    }

    @Test
    fun `CorpusLoader creates an exact Chinese to English index`() {
        val loader = bareLoader()
        loader.setLazy("chineseAssist", listOf(ChineseAssistEntry("巴士", "bus", 0.99)))

        val candidate = loader.chineseAssistIndex.getValue("巴士").single()
        assertEquals("bus", candidate.text)
        assertEquals(SourceSchema.CHINESE_ASSIST, candidate.sourceSchema)
    }

    @Test
    fun `translation follows Chinese predictions with a visible label`() {
        val prediction = DecodeCandidate(
            "站", "巴士", SourceSchema.QUICK, CandidateType.CHAR, 1.0, false
        )
        val translation = translationCandidate()

        val candidates = CandidateDisplayPolicy().orderPredictions(
            learned = emptyList(),
            decoded = listOf(prediction, translation)
        )

        assertEquals(listOf("站", "bus"), candidates.map { it.text })
        assertEquals("中→英 bus", CandidatePresentation.label(translation) { true })
    }

    @Test
    fun `tapping Chinese to English assist commits English and ends the prediction chain`() {
        val translation = translationCandidate()
        val output = makeCtrl().onCandidateTap(translation, ImeStateData())

        assertEquals("bus", output.committedText)
        assertFalse(CandidateCommitPolicy.continuesChinesePrediction(translation))
        assertFalse(CandidateCommitPolicy.isEligibleForSpace(translation, Scheme.QUICK, "巴士"))
        assertTrue(output.newState.buffer.isEmpty())
    }

    private fun translationCandidate() = DecodeCandidate(
        "bus", "巴士", SourceSchema.CHINESE_ASSIST,
        CandidateType.CHINESE_ASSIST, 0.99, false
    )

    private fun CorpusLoader.setLazy(name: String, value: Any) {
        val field = CorpusLoader::class.java.getDeclaredField("${name}\$delegate")
        field.isAccessible = true
        field.set(this, lazyOf(value))
    }

    private fun bareLoader(): CorpusLoader {
        val context = unsafe.javaClass
            .getMethod("allocateInstance", Class::class.java)
            .invoke(unsafe, ContextWrapper::class.java) as Context
        return CorpusLoader(context)
    }

    private companion object {
        val unsafe: Any by lazy {
            Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe").run {
                isAccessible = true
                get(null)
            }
        }
    }
}
