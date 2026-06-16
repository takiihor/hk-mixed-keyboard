package com.hkmixedkeyboard

import com.hkmixedkeyboard.decoder.*
import com.hkmixedkeyboard.engine.Classifier
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class DecodeHarnessTest {

    private val decoder = MockDecoder()
    private val classifier = Classifier(decoder)

    // ── Gate 1 Check 1: three parse states are derivable ──────────────────────

    @ParameterizedTest(name = "Quick parse states deterministic: \"{0}\"")
    @CsvSource(
        "hap", "send", "ok", "go", "mtr",
        "我", "你", "唔", "嘅", "唔該", "我哋"
    )
    fun `parse states are deterministic for all test buffers`(buffer: String) {
        val dr = decoder.decode(buffer, Scheme.QUICK)
        // Invoking twice must yield identical results
        val dr2 = decoder.decode(buffer, Scheme.QUICK)
        assertEquals(dr, dr2, "DecodeResult must be deterministic for \"$buffer\"")
        // States must be logically derivable (no exceptions thrown)
        @Suppress("UNUSED_VARIABLE")
        val states = Triple(dr.cnExactParsed, dr.cnHasPhraseMatch, dr.cnPrefixParsed)
    }

    // ── Gate 1 Check 2: candidate metadata is complete ────────────────────────

    @Test
    fun `all non-empty candidate lists have complete metadata`() {
        listOf("hap", "ok", "go", "mtr", "我", "你", "唔", "嘅", "唔該", "我哋").forEach { buf ->
            val dr = decoder.decode(buf, Scheme.QUICK)
            dr.candidates.forEach { c ->
                assertFalse(c.text.isEmpty(), "Candidate text must not be empty for \"$buf\"")
                assertNotNull(c.sourceSchema, "sourceSchema required for \"$buf\"")
                assertNotNull(c.type, "type required for \"$buf\"")
            }
        }
    }

    // ── Gate 1 Check 3: Quick scheme works ────────────────────────────────────

    @Test
    fun `Quick scheme is available`() {
        assertTrue(decoder.isSchemeAvailable(Scheme.QUICK))
    }

    @Test
    fun `Quick scheme returns valid result for HK core character 唔`() {
        val dr = decoder.decode("唔", Scheme.QUICK)
        assertTrue(dr.cnExactParsed, "唔 must cnExactParsed in Quick")
        assertTrue(dr.candidates.any { it.isHkCore }, "唔 must be flagged as HK core")
    }

    @Test
    fun `Quick scheme returns phrase match for 唔該`() {
        val dr = decoder.decode("唔該", Scheme.QUICK)
        assertTrue(dr.cnHasPhraseMatch, "唔該 must cnHasPhraseMatch in Quick")
        assertTrue(dr.candidates.any { it.type == CandidateType.PHRASE })
    }

    // ── Gate 1 Check 4: Cangjie scheme can be loaded ──────────────────────────

    @Test
    fun `Cangjie scheme is available in MockDecoder`() {
        assertTrue(decoder.isSchemeAvailable(Scheme.CANGJIE))
    }

    @Test
    fun `Cangjie decode of 我 returns correct source schema`() {
        val dr = decoder.decode("我", Scheme.CANGJIE)
        assertTrue(dr.cnExactParsed)
        assertTrue(dr.candidates.any { it.sourceSchema == SourceSchema.CANGJIE })
    }

    @Test
    fun `RimeDecoderSpike reports unavailability clearly`() {
        val spike = RimeDecoderSpike()
        assertFalse(spike.isSchemeAvailable(Scheme.QUICK))
        assertFalse(spike.isSchemeAvailable(Scheme.CANGJIE))
        val reason = RimeDecoderSpike.failureReason()
        assertTrue(reason.isNotEmpty(), "Failure reason must be documented")
    }

    // ── Gate 1 Check 5: prefix-only buffers not auto-committable ──────────────

    @Test
    fun `prefix-only buffer hap is not auto-committable`() {
        val dr = decoder.decode("hap", Scheme.QUICK)
        assertTrue(dr.cnPrefixParsed, "hap must be prefix-only")
        assertFalse(dr.isExactCode, "hap must not be exact code")
    }

    @Test
    fun `prefix-only buffer mtr is not auto-committable`() {
        val dr = decoder.decode("mtr", Scheme.QUICK)
        assertTrue(dr.cnPrefixParsed, "mtr must be prefix-only")
        assertFalse(dr.isExactCode, "mtr must not be exact code")
    }

    // ── English side ──────────────────────────────────────────────────────────

    @Test
    fun `send is classified as English word`() {
        val cr = classifier.classify("send", Scheme.QUICK)
        assertTrue(cr.enIsWord, "send must be an English word")
        assertFalse(cr.cnExactParsed, "send must not exact-parse as Chinese in Quick")
    }

    @Test
    fun `ok is classified as English via SHORT_WHITELIST`() {
        val cr = classifier.classify("ok", Scheme.QUICK)
        assertTrue(cr.enIsWord, "ok must be on SHORT_WHITELIST")
    }

    @Test
    fun `mtr canonical form is MTR`() {
        assertEquals("MTR", classifier.canonicalForm("mtr"))
        assertEquals("MTR", classifier.canonicalForm("MTR"))
    }

    @Test
    fun `hap has strong English prefix (happy)`() {
        val cr = classifier.classify("hap", Scheme.QUICK)
        assertTrue(cr.enStrongPrefix, "hap must trigger strongEnglishPrefix")
        assertEquals("happy", cr.enAutocomplete)
    }

    // ── HK core character flags ───────────────────────────────────────────────

    @Test
    fun `HK core characters are flagged correctly`() {
        listOf("唔", "嘅").forEach { char ->
            val dr = decoder.decode(char, Scheme.QUICK)
            assertTrue(dr.candidates.any { it.isHkCore }, "\"$char\" must have isHkCore=true")
        }
    }

    @Test
    fun `phrase candidates 唔該 and 我哋 are HK core`() {
        listOf("唔該", "我哋").forEach { phrase ->
            val dr = decoder.decode(phrase, Scheme.QUICK)
            assertTrue(dr.candidates.any { it.isHkCore && it.type == CandidateType.PHRASE },
                "\"$phrase\" must be a PHRASE with isHkCore=true")
        }
    }

    // ── Collision cases (EN vs CN) ────────────────────────────────────────────

    @Test
    fun `go has both Chinese match and English word flag`() {
        val dr = decoder.decode("go", Scheme.QUICK)
        val cr = classifier.classify("go", Scheme.QUICK)
        assertTrue(dr.cnExactParsed, "go has a Quick character match")
        assertTrue(cr.enIsWord, "go is also an English word — requires tieBreak")
    }

    @Test
    fun `send has no Chinese match — pure English`() {
        val dr = decoder.decode("send", Scheme.QUICK)
        assertFalse(dr.cnExactParsed)
        assertFalse(dr.cnHasPhraseMatch)
        assertFalse(dr.cnPrefixParsed)
    }
}
