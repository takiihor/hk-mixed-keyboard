package com.hkmixedkeyboard

import android.content.Context
import android.content.ContextWrapper
import com.hkmixedkeyboard.decoder.CharEntry
import com.hkmixedkeyboard.decoder.CorpusBackedDecoder
import com.hkmixedkeyboard.decoder.CorpusLoader
import com.hkmixedkeyboard.decoder.EnglishAssistEntry
import com.hkmixedkeyboard.decoder.JyutpingEntry
import com.hkmixedkeyboard.decoder.PinyinDecoder
import com.hkmixedkeyboard.decoder.PinyinEntry
import com.hkmixedkeyboard.decoder.PinyinLexicon
import com.hkmixedkeyboard.decoder.PhraseEntry
import com.hkmixedkeyboard.decoder.Scheme
import com.hkmixedkeyboard.decoder.SourceSchema
import com.hkmixedkeyboard.engine.CandidateDisplayPolicy
import com.hkmixedkeyboard.engine.Classifier
import com.hkmixedkeyboard.ime.CandidateCommitPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrossModeEnglishAssistTest {

    @Test
    fun `Jyutping retains its exact candidate before a tap-only English assist`() {
        val result = decoder().decode("ni", Scheme.JYUTPING)

        assertEquals(SourceSchema.JYUTPING, result.candidates.first().sourceSchema)
        val assist = result.candidates.single { it.sourceSchema == SourceSchema.ENGLISH_ASSIST }
        assertEquals("助理", assist.text)
        assertTrue(result.isExactCode)
        assertFalse(CandidateCommitPolicy.isEligibleForSpace(assist, Scheme.JYUTPING, "ni"))
    }

    @Test
    fun `Pinyin retains its exact candidate before a tap-only English assist`() {
        val result = decoder().decode("ni", Scheme.PINYIN)

        assertEquals(SourceSchema.PINYIN, result.candidates.first().sourceSchema)
        val assist = result.candidates.single { it.sourceSchema == SourceSchema.ENGLISH_ASSIST }
        assertEquals("助理", assist.text)
        assertTrue(result.isExactCode)
        assertFalse(CandidateCommitPolicy.isEligibleForSpace(assist, Scheme.PINYIN, "ni"))
    }

    @Test
    fun `romanization display keeps its own candidate ahead of English assist`() {
        val result = decoder().decode("ni", Scheme.PINYIN)

        val display = CandidateDisplayPolicy().order(
            buffer = "ni",
            learned = emptyList(),
            english = emptyList(),
            decoded = result.candidates,
            literal = enLiteralCand("ni"),
            chineseFirst = true
        )

        assertEquals(SourceSchema.PINYIN, display.first().sourceSchema)
        assertEquals(SourceSchema.ENGLISH_ASSIST, display.last { it.text == "助理" }.sourceSchema)
    }

    @Test
    fun `classifier cannot promote a high-frequency assist above Jyutping input`() {
        val result = Classifier(decoder()).classify("ni", Scheme.JYUTPING)

        assertEquals(SourceSchema.JYUTPING, result.cnCandidates.first().sourceSchema)
    }

    @Test
    fun `English assist accepts an apostrophe prefix even when Pinyin cannot parse it`() {
        val result = decoder().decode("can'", Scheme.PINYIN)

        assertFalse(result.isExactCode)
        assertTrue(result.candidates.any {
            it.text == "不能" && it.sourceSchema == SourceSchema.ENGLISH_ASSIST
        })
    }

    private fun decoder() = CorpusBackedDecoder(bareLoader().also { loader ->
        loader.setLazy("chars", emptyList<CharEntry>())
        loader.setLazy("phrases", emptyList<PhraseEntry>())
        loader.setLazy("mixedPhrases", emptyList<com.hkmixedkeyboard.decoder.MixedPhraseEntry>())
        loader.setLazy("englishAssist", listOf(
            EnglishAssistEntry("ni", "助理", 1.0),
            EnglishAssistEntry("can't", "不能", 1.0)
        ))
        loader.setLazy("jyutping", listOf(JyutpingEntry("ni", "你", 0.1)))
        loader.setLazy(
            "pinyinDecoder",
            PinyinDecoder(PinyinLexicon(listOf(PinyinEntry("ni", "你", 1.0))))
        )
    })

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
