package com.hkmixedkeyboard

import com.hkmixedkeyboard.commit.ImeContext
import com.hkmixedkeyboard.commit.ImeState
import com.hkmixedkeyboard.commit.ImeStateData
import com.hkmixedkeyboard.decoder.CandidateType
import com.hkmixedkeyboard.decoder.DecodeCandidate
import com.hkmixedkeyboard.decoder.DecodeResult
import com.hkmixedkeyboard.decoder.DecoderContract
import com.hkmixedkeyboard.decoder.Scheme
import com.hkmixedkeyboard.decoder.SourceSchema
import com.hkmixedkeyboard.decoder.PinyinEntry
import com.hkmixedkeyboard.decoder.PinyinLexicon
import com.hkmixedkeyboard.engine.Classifier
import com.hkmixedkeyboard.engine.CandidateDisplayPolicy
import com.hkmixedkeyboard.ime.PinyinImePolicy
import com.hkmixedkeyboard.memory.MemoryIndex
import com.hkmixedkeyboard.memory.MemoryStalenessPolicy
import com.hkmixedkeyboard.memory.MemorySuggestion
import com.hkmixedkeyboard.memory.UserMemory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PinyinImeIntegrationTest {

    @Test
    fun `Space commits an exact Pinyin candidate without appending a space`() {
        val candidate = pinyin("你好", "nihao", CandidateType.PHRASE)
        val ctrl = makeCtrl(memory = UserMemory(), ctx = ImeContext(scheme = Scheme.PINYIN))

        val out = ctrl.onSpace(
            ImeStateData(buffer = "nihao", imeState = ImeState.COMPOSING),
            PinyinImePolicy.spaceCandidate(
                scheme = Scheme.PINYIN,
                buffer = "nihao",
                candidates = listOf(candidate)
            )
        )

        assertEquals("你好", out.committedText)
        assertEquals("你好", out.newState.prevCommitted)
    }

    @Test
    fun `Space commits a segmented Pinyin candidate`() {
        val candidate = pinyin("你們", "nimen", CandidateType.PHRASE)

        assertEquals(
            candidate,
            PinyinImePolicy.spaceCandidate(
                scheme = Scheme.PINYIN,
                buffer = "nimen",
                candidates = listOf(candidate)
            )
        )
    }

    @Test
    fun `Pinyin prefix candidate remains tap only`() {
        val longerPhrase = pinyin("香港人", "xianggangren", CandidateType.PHRASE)

        assertNull(
            PinyinImePolicy.spaceCandidate(
                scheme = Scheme.PINYIN,
                buffer = "xiang",
                candidates = listOf(longerPhrase)
            )
        )
    }

    @Test
    fun `Pinyin display is Chinese first and suppresses English completions`() {
        assertTrue(PinyinImePolicy.isChineseFirst(Scheme.PINYIN, phraseExact = false))
        assertFalse(PinyinImePolicy.shouldOfferEnglishCompletions(Scheme.PINYIN))
    }

    @Test
    fun `classifier preserves low frequency exact Pinyin ahead of high frequency prefix`() {
        val exact = pinyin("向", "xiang", CandidateType.CHAR).copy(frequency = 0.1)
        val prefix = pinyin("香港", "xianggang", CandidateType.PHRASE).copy(frequency = 1.0)
        val decoder = fixedDecoder(exact, prefix)

        val result = Classifier(decoder).classify("xiang", Scheme.PINYIN)

        assertEquals(listOf("向", "香港"), result.cnCandidates.map { it.text })
    }

    @Test
    fun `classifier keeps Quick frequency ranking unchanged`() {
        val low = cnChar("低", "rr", freq = 0.1)
        val high = cnChar("高", "rr", freq = 1.0)

        val result = Classifier(fixedDecoder(low, high)).classify("rr", Scheme.QUICK)

        assertEquals(listOf("高", "低"), result.cnCandidates.map { it.text })
    }

    @Test
    fun `Pinyin learned suggestions retain Chinese and remove English`() {
        val chinese = MemorySuggestion(
            pinyin("你好", "nihao", CandidateType.PHRASE), 3, isExactBuffer = true
        )
        val english = MemorySuggestion(enLiteralCand("night"), 5)
        val mislabeledEnglish = MemorySuggestion(cnChar("hello", "nihao"), 4)

        val result = PinyinImePolicy.filterLearnedSuggestions(
            Scheme.PINYIN,
            listOf(english, mislabeledEnglish, chinese)
        )

        assertEquals(listOf("你好"), result.map { it.candidate.text })
    }

    @Test
    fun `Quick learned suggestions retain English`() {
        val english = MemorySuggestion(enLiteralCand("night"), 5)

        assertEquals(
            listOf(english),
            PinyinImePolicy.filterLearnedSuggestions(Scheme.QUICK, listOf(english))
        )
    }

    @Test
    fun `Pinyin warm policy rejects an empty lexicon`() {
        assertFalse(PinyinImePolicy.isCorpusReady(PinyinLexicon(emptyList())))
        assertTrue(
            PinyinImePolicy.isCorpusReady(
                PinyinLexicon(listOf(PinyinEntry("mao", "貓", 1.0)))
            )
        )
    }

    @Test
    fun `learned exact Pinyin leads display and Space commits the same choice`() {
        val memory = MemoryIndex().apply {
            record("ni", pinyin("妳", "ni", CandidateType.CHAR))
        }
        val learned = memory.suggestions("ni", limit = 8)
        val decoded = listOf(pinyin("你", "ni", CandidateType.CHAR))

        val display = pinyinDisplay("ni", learned, decoded)
        val space = PinyinImePolicy.spaceCandidate(
            Scheme.PINYIN, "ni", decoded, learned
        )

        assertEquals("妳", display.first().text)
        assertEquals(SourceSchema.PINYIN, display.first().sourceSchema)
        assertEquals("妳", space?.text)
        assertEquals(SourceSchema.PINYIN, space?.sourceSchema)
    }

    @Test
    fun `tapping learned Pinyin display preserves provenance for the next composition`() {
        val memory = MemoryIndex().apply {
            record("ni", pinyin("妳", "ni", CandidateType.CHAR))
        }
        val decoded = listOf(pinyin("你", "ni", CandidateType.CHAR))
        val firstDisplay = pinyinDisplay("ni", memory.suggestions("ni", 8), decoded)

        memory.record("ni", firstDisplay.first())
        val nextLearned = memory.suggestions("ni", 8)
        val nextDisplay = pinyinDisplay("ni", nextLearned, decoded)
        val nextSpace = PinyinImePolicy.spaceCandidate(
            Scheme.PINYIN, "ni", decoded, nextLearned
        )

        assertEquals(SourceSchema.PINYIN, firstDisplay.first().sourceSchema)
        assertTrue(nextLearned.single().isExactBuffer)
        assertEquals(SourceSchema.PINYIN, nextLearned.single().candidate.sourceSchema)
        assertEquals("妳", nextDisplay.first().text)
        assertEquals("妳", nextSpace?.text)
        assertFalse(
            MemoryStalenessPolicy(quickContains = { false }, purgeVariant = { false })
                .isStale(nextLearned.single().candidate)
        )
    }

    @Test
    fun `learned longer Pinyin prefix neither leads nor auto commits`() {
        val memory = MemoryIndex().apply {
            record("nihao", pinyin("你好", "nihao", CandidateType.PHRASE))
        }
        val learned = memory.suggestions("ni", limit = 8)
        val decoded = listOf(pinyin("你", "ni", CandidateType.CHAR))

        val display = pinyinDisplay("ni", learned, decoded)
        val space = PinyinImePolicy.spaceCandidate(
            Scheme.PINYIN, "ni", decoded, learned
        )

        assertFalse(learned.single().isExactBuffer)
        assertEquals("你", display.first().text)
        assertEquals("你", space?.text)
    }

    @Test
    fun `Quick sourced exact memory cannot affect Pinyin display or Space`() {
        val memory = MemoryIndex().apply {
            record("ni", cnChar("泥", "ni"))
        }
        val learned = memory.suggestions("ni", limit = 8)
        val decoded = listOf(pinyin("你", "ni", CandidateType.CHAR))

        val display = pinyinDisplay("ni", learned, decoded)
        val space = PinyinImePolicy.spaceCandidate(
            Scheme.PINYIN, "ni", decoded, learned
        )

        assertTrue(learned.single().isExactBuffer)
        assertEquals(SourceSchema.QUICK, learned.single().candidate.sourceSchema)
        assertEquals("你", display.first().text)
        assertEquals("你", space?.text)
    }

    @Test
    fun `Quick display keeps its existing English completion behavior`() {
        assertFalse(PinyinImePolicy.isChineseFirst(Scheme.QUICK, phraseExact = false))
        assertTrue(PinyinImePolicy.shouldOfferEnglishCompletions(Scheme.QUICK))
    }

    @Test
    fun `candidate-assisted Space commits the raw Quick buffer without whitespace`() {
        val ctrl = makeCtrl(memory = UserMemory(), ctx = ImeContext(scheme = Scheme.QUICK))

        val out = ctrl.onSpace(
            ImeStateData(buffer = "rr", imeState = ImeState.COMPOSING),
            pinyin("唔", "rr", CandidateType.CHAR)
        )

        assertEquals("rr", out.committedText)
    }

    @Test
    fun `Enter in Pinyin mode commits the literal Latin buffer`() {
        val ctrl = makeCtrl(memory = UserMemory(), ctx = ImeContext(scheme = Scheme.PINYIN))

        val out = ctrl.onEnter(
            ImeStateData(buffer = "nihao", imeState = ImeState.COMPOSING)
        )

        assertEquals("nihao", out.committedText)
    }

    private fun pinyin(text: String, code: String, type: CandidateType) = DecodeCandidate(
        text = text,
        code = code,
        sourceSchema = SourceSchema.PINYIN,
        type = type,
        frequency = 1.0,
        isHkCore = false
    )

    private fun fixedDecoder(vararg candidates: DecodeCandidate) = object : DecoderContract {
        override val schemeName = "fixed"
        override fun isSchemeAvailable(scheme: Scheme) = true
        override fun decode(buffer: String, scheme: Scheme) = DecodeResult(
            buffer = buffer,
            scheme = scheme,
            consumedLen = buffer.length,
            isExactCode = true,
            isPrefixOnly = false,
            candidates = candidates.toList()
        )
    }

    private fun pinyinDisplay(
        buffer: String,
        learned: List<MemorySuggestion>,
        decoded: List<DecodeCandidate>
    ) = CandidateDisplayPolicy().order(
        buffer = buffer,
        learned = PinyinImePolicy.filterLearnedSuggestions(Scheme.PINYIN, learned),
        english = emptyList(),
        decoded = decoded,
        literal = enLiteralCand(buffer),
        chineseFirst = true
    )
}
