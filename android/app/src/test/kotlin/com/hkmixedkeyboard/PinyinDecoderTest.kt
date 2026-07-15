package com.hkmixedkeyboard

import com.hkmixedkeyboard.decoder.CandidateType
import com.hkmixedkeyboard.decoder.PinyinDecoder
import com.hkmixedkeyboard.decoder.PinyinEntry
import com.hkmixedkeyboard.decoder.PinyinLexicon
import com.hkmixedkeyboard.decoder.PinyinNormalizer
import com.hkmixedkeyboard.decoder.CorpusLoader
import com.hkmixedkeyboard.decoder.Scheme
import com.hkmixedkeyboard.decoder.SourceSchema
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PinyinDecoderTest {

    @Test
    fun `empty or wholly invalid Pinyin lexicon is not usable`() {
        assertFalse(PinyinLexicon(emptyList()).isUsable)
        assertFalse(PinyinLexicon(listOf(PinyinEntry("xx", "昔", 1.0))).isUsable)
    }

    @Test
    fun `Pinyin lexicon with a valid indexed entry is usable`() {
        assertTrue(PinyinLexicon(listOf(PinyinEntry("mao", "貓", 1.0))).isUsable)
    }

    @Test
    fun `normalizer accepts case and all supported u umlaut spellings`() {
        assertEquals("nv", PinyinNormalizer.normalize("NÜ"))
        assertEquals("nv", PinyinNormalizer.normalize("Nu:"))
        assertEquals("nv", PinyinNormalizer.normalize("NV"))
        assertEquals("ju", PinyinNormalizer.normalize("jv"))
        assertEquals("qu", PinyinNormalizer.normalize("qü"))
        assertEquals("xu", PinyinNormalizer.normalize("xu:"))
        assertEquals("yu", PinyinNormalizer.normalize("yv"))
    }

    @Test
    fun `normalizer accepts tones marks digits and syllable separators`() {
        assertEquals("mao", PinyinNormalizer.normalize("mǎo"))
        assertEquals("mao", PinyinNormalizer.normalize("mao3"))
        assertEquals("nihao", PinyinNormalizer.normalize("ni3 hao3"))
        assertEquals("xian", PinyinNormalizer.normalize("xi'an"))
        assertEquals("nv", PinyinNormalizer.normalize("nǚ"))
    }

    @Test
    fun `normalizer rejects invalid tone digits and non pinyin input`() {
        assertNull(PinyinNormalizer.normalize("mao0"))
        assertNull(PinyinNormalizer.normalize("mao6"))
        assertNull(PinyinNormalizer.normalize("ni_hao"))
        assertNull(PinyinNormalizer.normalize(""))
    }

    @Test
    fun `fuzzy variants are optional and strict result stays first`() {
        assertEquals(listOf("zong"), PinyinNormalizer.variants("zong", fuzzyEnabled = false))
        assertEquals("zong", PinyinNormalizer.variants("zong", fuzzyEnabled = true).first())
        assertTrue(PinyinNormalizer.variants("zong", fuzzyEnabled = true).contains("zhong"))
    }

    @Test
    fun `exact phrase is committable and preserves frequency order`() {
        val result = decoder(
            PinyinEntry("mao", "毛", 0.5),
            PinyinEntry("mao", "貓", 1.0),
            PinyinEntry("nihao", "你好", 0.8)
        ).decode("NIHAO")

        assertEquals(Scheme.PINYIN, result.scheme)
        assertTrue(result.isExactCode)
        assertTrue(result.cnHasPhraseMatch)
        assertFalse(result.isPrefixOnly)
        assertEquals(listOf("你好"), result.candidates.map { it.text })
        assertEquals(CandidateType.PHRASE, result.candidates.single().type)
        assertEquals(SourceSchema.PINYIN, result.candidates.single().sourceSchema)

        val cat = decoder(
            PinyinEntry("mao", "毛", 0.5),
            PinyinEntry("mao", "貓", 1.0)
        ).decode("mao")
        assertEquals(listOf("貓", "毛"), cat.candidates.map { it.text })
        assertTrue(cat.cnExactParsed)
    }

    @Test
    fun `duplicate key text rows collapse to the highest frequency`() {
        val result = decoder(
            PinyinEntry("xing", "行", 0.3),
            PinyinEntry("xing", "行", 0.7),
            PinyinEntry("hang", "行", 0.6)
        ).decode("xing")

        assertEquals(listOf("行"), result.candidates.map { it.text })
        assertEquals(0.7, result.candidates.single().frequency, 0.0)
    }

    @Test
    fun `equal frequency hash collisions have stable text ordering`() {
        val firstText = "一丠"
        val secondText = "丁丁"
        assertEquals(firstText.hashCode(), secondText.hashCode())

        val forward = decoder(
            PinyinEntry("ma", firstText, 0.3),
            PinyinEntry("ma", secondText, 0.3)
        ).decode("ma").candidates.map { it.text }
        val reversed = decoder(
            PinyinEntry("ma", secondText, 0.3),
            PinyinEntry("ma", firstText, 0.3)
        ).decode("ma").candidates.map { it.text }

        assertEquals(listOf(firstText, secondText), forward)
        assertEquals(forward, reversed)
    }

    @Test
    fun `prefix candidates are Chinese first and tap only`() {
        val result = decoder(
            PinyinEntry("xianggang", "香港", 0.9),
            PinyinEntry("xianggangren", "香港人", 0.8),
            PinyinEntry("xiang", "想", 0.7)
        ).decode("xiangga")

        assertFalse(result.isExactCode)
        assertTrue(result.isPrefixOnly)
        assertTrue(result.cnPrefixParsed)
        assertEquals(listOf("香港", "香港人"), result.candidates.map { it.text })
        assertTrue(result.candidates.all { it.sourceSchema == SourceSchema.PINYIN })
    }

    @Test
    fun `exact candidates stay first and append longer phrase prefixes`() {
        val result = decoder(
            PinyinEntry("xiang", "向", 0.6),
            PinyinEntry("xiang", "想", 0.7),
            PinyinEntry("xianggang", "香港", 0.9),
            PinyinEntry("xianggang", "想", 1.0)
        ).decode("xiang")

        assertTrue(result.isExactCode)
        assertFalse(result.isPrefixOnly)
        assertTrue(result.cnExactParsed)
        assertEquals(listOf("想", "向", "香港"), result.candidates.map { it.text })
    }

    @Test
    fun `non CJK corpus rows cannot become exact or segmented candidates`() {
        val decoder = decoder(
            PinyinEntry("p", "P", 1.0),
            PinyinEntry("q", "Q", 1.0),
            PinyinEntry("w", "w", 1.0),
            PinyinEntry("gaa", "𨳍", 0.5)
        )

        assertTrue(decoder.decode("p").candidates.isEmpty())
        assertTrue(decoder.decode("pq").candidates.isEmpty())
        assertEquals(listOf("𨳍"), decoder.decode("gaa").candidates.map { it.text })
        assertTrue(decoder.decode("gaa").cnExactParsed)
    }

    @Test
    fun `noncanonical placeholder readings cannot decode or compose`() {
        val decoder = decoder(
            PinyinEntry("xx", "昔", 1.0),
            PinyinEntry("ju", "居", 0.8),
            PinyinEntry("lv", "綠", 0.8)
        )

        assertTrue(decoder.decode("xx").candidates.isEmpty())
        assertTrue(decoder.decode("xxxx").candidates.isEmpty())
        assertEquals("居", decoder.decode("jv").candidates.first().text)
        assertEquals("綠", decoder.decode("lü").candidates.first().text)
    }

    @Test
    fun `corpus loader does not expose a retained raw Pinyin row list`() {
        assertTrue(CorpusLoader::class.java.methods.none { it.name == "getPinyin" })
    }

    @Test
    fun `valid continuous syllables compose a bounded fallback phrase`() {
        val result = decoder(
            PinyinEntry("ni", "你", 0.9),
            PinyinEntry("ni", "尼", 0.5),
            PinyinEntry("men", "們", 0.9),
            PinyinEntry("men", "門", 0.5)
        ).decode("nimen")

        assertTrue(result.isExactCode)
        assertTrue(result.cnHasPhraseMatch)
        assertEquals("你們", result.candidates.first().text)
        assertTrue(result.candidates.size <= 12)
        assertTrue(result.candidates.all { it.type == CandidateType.PHRASE })
    }

    @Test
    fun `invalid or over ceiling segmentation returns no candidates`() {
        val decoder = decoder(
            PinyinEntry("ni", "你", 0.9),
            PinyinEntry("men", "們", 0.9)
        )

        assertTrue(decoder.decode("nix").candidates.isEmpty())
        assertTrue(decoder.decode("nimen".repeat(14)).candidates.isNotEmpty())
        assertTrue(decoder.decode("nimen".repeat(15)).candidates.isEmpty())
        assertTrue(decoder.decode("ni0men").candidates.isEmpty())
    }

    @Test
    fun `one adjacent-key typo offers tap-only recovery`() {
        val result = decoder(
            PinyinEntry("hao", "好", 0.9),
            PinyinEntry("ni", "你", 0.9)
        ).decode("hso")

        assertEquals("好", result.candidates.first().text)
        assertFalse(result.isExactCode)
    }

    private fun decoder(vararg entries: PinyinEntry) =
        PinyinDecoder(PinyinLexicon(entries.toList()))
}
