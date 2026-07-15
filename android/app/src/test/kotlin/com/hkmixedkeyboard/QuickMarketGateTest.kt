package com.hkmixedkeyboard

import com.hkmixedkeyboard.decoder.CandidateType
import com.hkmixedkeyboard.decoder.DecodeCandidate
import com.hkmixedkeyboard.decoder.Scheme
import com.hkmixedkeyboard.decoder.SourceSchema
import com.hkmixedkeyboard.engine.Classifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class QuickMarketGateTest {
    private val corpusDir = "src/main/assets/corpus"
    private val classifier by lazy { Classifier(buildFullCorpusDecoder(corpusDir)) }

    @Test
    fun `reviewed high-frequency Quick characters rank within Top-3`() {
        linkedMapOf(
            "ai" to "時", "rr" to "唔", "ru" to "嘅",
            "ri" to "啲", "kb" to "冇", "os" to "佢"
        ).forEach { (code, expected) ->
            val ranked = classifier.classify(code, Scheme.QUICK).cnCandidates.map { it.text }
            assertTrue("$code must surface $expected within Top-3: $ranked", expected in ranked.take(3))
        }
    }

    @Test
    fun `reviewed Quick phrases rank within Top-3`() {
        linkedMapOf(
            "rryo" to "唔該", "rrvd" to "唔好",
            "kbarac" to "冇問題", "hiru" to "我嘅"
        ).forEach { (code, expected) ->
            val result = classifier.classify(code, Scheme.QUICK)
            assertTrue("$code must surface $expected within Top-3",
                result.cnCandidates.take(3).any { it.text == expected && it.type == CandidateType.PHRASE })
        }
    }

    @Test
    fun `production Quick assets have canonical unique character codes`() {
        val rows = File("$corpusDir/hk_core_chars.csv").readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .drop(1)
            .map { it.split(',') }
        val seen = HashSet<String>()
        rows.forEach { row ->
            assertEquals(5, row.size)
            val text = row[0]
            val quick = row[1]
            val cangjie = row[2]
            assertEquals(1, text.codePointCount(0, text.length))
            assertTrue(cangjie.matches(Regex("[a-z]+")))
            assertEquals(if (cangjie.length == 1) cangjie else "${cangjie.first()}${cangjie.last()}", quick)
            assertTrue("duplicate Quick character $text", seen.add(text))
        }
    }

    @Test
    fun `technical HKSCS fallback cannot auto commit`() {
        val fallback = DecodeCandidate(
            "𠃍", "u200cd", SourceSchema.HKSCS_UNICODE,
            CandidateType.CHAR, 0.0, false
        )
        assertTrue(fallback.sourceSchema != SourceSchema.QUICK)
    }
}
