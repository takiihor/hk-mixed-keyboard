package com.hkmixedkeyboard

import android.content.Context
import android.content.ContextWrapper
import com.hkmixedkeyboard.commit.ImeContext
import com.hkmixedkeyboard.commit.ImeState
import com.hkmixedkeyboard.commit.ImeStateData
import com.hkmixedkeyboard.decoder.CharEntry
import com.hkmixedkeyboard.decoder.CandidateType
import com.hkmixedkeyboard.decoder.CorpusBackedDecoder
import com.hkmixedkeyboard.decoder.CorpusLoader
import com.hkmixedkeyboard.decoder.MixedPhraseEntry
import com.hkmixedkeyboard.decoder.PinyinDecoder
import com.hkmixedkeyboard.decoder.PinyinLexicon
import com.hkmixedkeyboard.decoder.Scheme
import com.hkmixedkeyboard.decoder.SourceSchema
import com.hkmixedkeyboard.ime.CandidateCommitPolicy
import com.hkmixedkeyboard.memory.UserMemory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MixedPhraseSuggestionTest {

    @Test
    fun `Quick exact trigger surfaces a mixed phrase as a tap target`() {
        val candidate = decoder().decode("send", Scheme.QUICK).candidates.single()

        assertEquals("send返", candidate.text)
        assertEquals(SourceSchema.MIXED_PHRASE, candidate.sourceSchema)
        assertEquals(CandidateType.MIXED_PHRASE, candidate.type)
    }

    @Test
    fun `mixed phrase remains tap-only while Quick Space commits the raw buffer`() {
        val candidate = decoder().decode("send", Scheme.QUICK).candidates.single()
        val state = ImeStateData(buffer = "send", imeState = ImeState.COMPOSING)
        val controller = makeCtrl(
            memory = UserMemory(),
            ctx = ImeContext(scheme = Scheme.QUICK)
        )

        assertFalse(CandidateCommitPolicy.isEligibleForSpace(candidate, Scheme.QUICK, "send"))
        assertEquals("send", controller.onSpace(state, candidate).committedText)
        assertEquals("send.", controller.onPunctuation("。", state, autoCommitCandidate = candidate).committedText)
    }

    @Test
    fun `Jyutping and Pinyin buffers never receive English mixed triggers`() {
        val decoder = decoder()

        assertTrue(decoder.decode("send", Scheme.JYUTPING).candidates.none {
            it.sourceSchema == SourceSchema.MIXED_PHRASE
        })
        assertTrue(decoder.decode("send", Scheme.PINYIN).candidates.none {
            it.sourceSchema == SourceSchema.MIXED_PHRASE
        })
    }

    private fun decoder(): CorpusBackedDecoder = CorpusBackedDecoder(loader())

    private fun loader(): CorpusLoader {
        val context = unsafe.javaClass
            .getMethod("allocateInstance", Class::class.java)
            .invoke(unsafe, ContextWrapper::class.java) as Context
        return CorpusLoader(context).also { loader ->
            loader.setLazy("chars", emptyList<CharEntry>())
            loader.setLazy("phrases", emptyList<com.hkmixedkeyboard.decoder.PhraseEntry>())
            loader.setLazy("mixedPhrases", listOf(MixedPhraseEntry("send", "send返", 1.0)))
            loader.setLazy("englishAssist", emptyList<com.hkmixedkeyboard.decoder.EnglishAssistEntry>())
            loader.setLazy("jyutping", emptyList<com.hkmixedkeyboard.decoder.JyutpingEntry>())
            loader.setLazy("pinyinDecoder", PinyinDecoder(PinyinLexicon(emptyList())))
        }
    }

    private fun CorpusLoader.setLazy(name: String, value: Any) {
        val field = CorpusLoader::class.java.getDeclaredField("${name}\$delegate")
        field.isAccessible = true
        field.set(this, lazyOf(value))
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
