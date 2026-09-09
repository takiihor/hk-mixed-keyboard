package com.hkmixedkeyboard

import com.hkmixedkeyboard.decoder.PinyinDiacritics
import com.hkmixedkeyboard.decoder.YaleRomanization
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RomanizationNotationTest {

    // ── Pinyin: numbers to the diacritics schools teach ───────────────────

    @Test
    fun `tone marks land on the vowel the standard rule picks`() {
        assertEquals("xiāng gǎng", PinyinDiacritics.format("xiang1 gang3"))
        assertEquals("zhōng wén", PinyinDiacritics.format("zhong1 wen2"))
        assertEquals("yín háng", PinyinDiacritics.format("yin2 hang2"))
    }

    @Test
    fun `an a always takes the mark`() {
        assertEquals("gǎng", PinyinDiacritics.format("gang3"))
        assertEquals("xiǎng", PinyinDiacritics.format("xiang3"))
    }

    @Test
    fun `ou marks the o, other pairs mark the last vowel`() {
        assertEquals("dōu", PinyinDiacritics.format("dou1"))
        assertEquals("huì", PinyinDiacritics.format("hui4"))
        assertEquals("liù", PinyinDiacritics.format("liu4"))
        assertEquals("guǒ", PinyinDiacritics.format("guo3"))
    }

    @Test
    fun `neutral tone is unmarked rather than written as a five`() {
        assertEquals("de", PinyinDiacritics.format("de5"))
        assertEquals("tā men", PinyinDiacritics.format("ta1 men5"))
        assertEquals("xiān sheng", PinyinDiacritics.format("xian1 sheng5"))
    }

    @Test
    fun `u umlaut keeps its own marked forms`() {
        assertEquals("lǜ", PinyinDiacritics.format("lü4"))
        assertEquals("nǚ", PinyinDiacritics.format("nü3"))
    }

    @Test
    fun `a malformed reading yields nothing rather than a guess`() {
        assertNull(PinyinDiacritics.format("xiang"))
        assertNull(PinyinDiacritics.format("xiang9"))
        assertNull(PinyinDiacritics.format(""))
        assertNull(PinyinDiacritics.format("xiang1 gang"))
    }

    // ── Yale, derived from Jyutping ───────────────────────────────────────

    @Test
    fun `initials follow Yale spelling`() {
        // j is a y sound, z is j, c is ch — the three that read wrong in Jyutping.
        assertEquals("yāt", YaleRomanization.fromJyutping("jat1"))
        assertEquals("jī", YaleRomanization.fromJyutping("zi1"))
        assertEquals("chī", YaleRomanization.fromJyutping("ci1"))
    }

    @Test
    fun `the rounded front vowel becomes eu`() {
        assertEquals("hēung góng", YaleRomanization.fromJyutping("hoeng1 gong2"))
        assertEquals("chēun", YaleRomanization.fromJyutping("ceon1"))
        assertEquals("sēui", YaleRomanization.fromJyutping("seoi1"))
    }

    @Test
    fun `jyu collapses to yu rather than doubling the y`() {
        assertEquals("yùh", YaleRomanization.fromJyutping("jyu4"))
        assertEquals("yuht", YaleRomanization.fromJyutping("jyut6"))
        assertEquals("syū", YaleRomanization.fromJyutping("syu1"))
    }

    @Test
    fun `low tones take a trailing h`() {
        // Yale tone 5 is an acute, not a caron: 你 is néih.
        assertEquals("néih", YaleRomanization.fromJyutping("nei5"))
        assertEquals("ngóh", YaleRomanization.fromJyutping("ngo5"))
        assertEquals("hah", YaleRomanization.fromJyutping("ha6"))
        assertEquals("hàh", YaleRomanization.fromJyutping("ha4"))
    }

    @Test
    fun `the tone h sits after the vowel, before any final consonant`() {
        assertEquals("yuht", YaleRomanization.fromJyutping("jyut6"))
        assertEquals("sahp", YaleRomanization.fromJyutping("sap6"))
        assertEquals("hohk", YaleRomanization.fromJyutping("hok6"))
        assertEquals("yàhn", YaleRomanization.fromJyutping("jan4"))
    }

    @Test
    fun `unaccented tones carry no mark`() {
        assertEquals("sam", YaleRomanization.fromJyutping("sam3"))
        // Tone 6 adds the h but no accent, and it still sits after the vowel.
        assertEquals("sahm", YaleRomanization.fromJyutping("sam6"))
    }

    @Test
    fun `a syllabic nasal keeps its tone letter without an accent`() {
        assertEquals("mh", YaleRomanization.fromJyutping("m4"))
        assertEquals("ngh", YaleRomanization.fromJyutping("ng5"))
    }

    @Test
    fun `an unconvertible reading yields nothing rather than an invented spelling`() {
        assertNull(YaleRomanization.fromJyutping("hoeng"))
        assertNull(YaleRomanization.fromJyutping("hoeng9"))
        assertNull(YaleRomanization.fromJyutping(""))
    }
}
