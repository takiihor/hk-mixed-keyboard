package com.hkmixedkeyboard

import com.hkmixedkeyboard.decoder.*
import org.junit.Assert.*
import org.junit.Test

/**
 * Hong Kong localization overrides for the English → Chinese assist.
 *
 * The raw CC-CEDICT gloss ranks generic Mandarin renderings first (bus → 汽車,
 * taxi → 出租車) and leaves common words empty (ok, fridge). The reviewed
 * `english_assist_overrides.csv` layer must surface the Hong Kong term first
 * while keeping the assist tap-only.
 */
class EnglishAssistHkOverrideTest {

    private val CORPUS_DIR = "src/main/assets/corpus"

    private fun firstAssist(word: String): String? {
        val result = buildFullCorpusDecoder(CORPUS_DIR).decode(word, Scheme.QUICK)
        return result.candidates
            .firstOrNull { it.sourceSchema == SourceSchema.ENGLISH_ASSIST }
            ?.text
    }

    @Test
    fun `bus surfaces the Hong Kong term 巴士 first`() {
        assertEquals("巴士", firstAssist("bus"))
    }

    @Test
    fun `taxi surfaces the Hong Kong term 的士 first`() {
        assertEquals("的士", firstAssist("taxi"))
    }

    @Test
    fun `fridge is no longer empty and surfaces 雪櫃`() {
        assertEquals("雪櫃", firstAssist("fridge"))
    }

    @Test
    fun `sorry surfaces a real apology instead of the CC-CEDICT noise 愴`() {
        assertEquals("對唔住", firstAssist("sorry"))
    }

    @Test
    fun `lift preserves the Hong Kong colloquial 𨋢 as a supplementary-plane term`() {
        val text = firstAssist("lift")
        assertEquals("𨋢", text)
        // Sanity: it is a single supplementary-plane code point, not two BMP chars.
        assertEquals(1, text!!.codePointCount(0, text.length))
    }

    @Test
    fun `overrides stay tap-only and never auto-commit on Space`() {
        val result = buildFullCorpusDecoder(CORPUS_DIR).decode("taxi", Scheme.QUICK)
        assertFalse("English assist must remain tap-only", result.isExactCode)
    }

    @Test
    fun `raw CC-CEDICT gloss remains available below the Hong Kong override`() {
        val texts = buildFullCorpusDecoder(CORPUS_DIR)
            .decode("bus", Scheme.QUICK).candidates.map { it.text }
        assertTrue("HK term present", texts.contains("巴士"))
        assertTrue("legacy gloss retained", texts.contains("汽車"))
        assertTrue("HK term ranks before legacy gloss",
            texts.indexOf("巴士") < texts.indexOf("汽車"))
    }
}
