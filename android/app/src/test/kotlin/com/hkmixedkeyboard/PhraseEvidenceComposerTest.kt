package com.hkmixedkeyboard

import com.hkmixedkeyboard.decoder.CandidateType
import com.hkmixedkeyboard.decoder.DecodeCandidate
import com.hkmixedkeyboard.decoder.PhraseEvidenceComposer
import com.hkmixedkeyboard.decoder.SourceSchema
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhraseEvidenceComposerTest {
    @Test
    fun `character-only syllable paths cannot invent a phrase`() {
        val composer = PhraseEvidenceComposer(
            mapOf(
                "ni" to listOf(candidate("你", "ni")),
                "men" to listOf(candidate("們", "men"))
            )
        )

        assertTrue(composer.compose("nimen").isEmpty())
    }

    @Test
    fun `reviewed phrase evidence can combine with an exact character`() {
        val composer = PhraseEvidenceComposer(
            mapOf(
                "nihao" to listOf(candidate("你好", "nihao", phrase = true)),
                "ma" to listOf(candidate("嗎", "ma"))
            )
        )

        assertEquals(listOf("你好嗎"), composer.compose("nihaoma").map { it.text })
    }

    private fun candidate(text: String, code: String, phrase: Boolean = false) = DecodeCandidate(
        text = text,
        code = code,
        sourceSchema = SourceSchema.PINYIN,
        type = if (phrase) CandidateType.PHRASE else CandidateType.CHAR,
        frequency = 1.0,
        isHkCore = true
    )
}
