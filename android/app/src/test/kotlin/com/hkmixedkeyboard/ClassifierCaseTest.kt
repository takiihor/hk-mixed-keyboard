package com.hkmixedkeyboard

import com.hkmixedkeyboard.decoder.DecodeResult
import com.hkmixedkeyboard.decoder.DecoderContract
import com.hkmixedkeyboard.decoder.Scheme
import com.hkmixedkeyboard.engine.Classifier
import com.hkmixedkeyboard.engine.InputCasePolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class ClassifierCaseTest {
    @Test
    fun `classifier lowercases decoder lookup while preserving visible English case`() {
        var decodedBuffer: String? = null
        val decoder = object : DecoderContract {
            override val schemeName = "RecordingDecoder"

            override fun decode(buffer: String, scheme: Scheme): DecodeResult {
                decodedBuffer = buffer
                return DecodeResult(
                    buffer = buffer,
                    scheme = scheme,
                    consumedLen = buffer.length,
                    isExactCode = false,
                    isPrefixOnly = false,
                    candidates = emptyList()
                )
            }

            override fun isSchemeAvailable(scheme: Scheme) = true
        }

        val result = Classifier(decoder).classify("Rr", Scheme.QUICK)

        assertEquals("rr", decodedBuffer)
        assertEquals("Rr", result.buffer)
    }

    // Case-pattern completion moved out of the classifier, whose English half was
    // computed and never read. The live path is EnglishCompletionIndex, which
    // applies the same InputCasePolicy rule to the bar's completions.

    @Test
    fun `initial capital completion follows the typed pattern`() {
        assertEquals("Happy", InputCasePolicy.applyPattern("happy", "Hap"))
    }

    @Test
    fun `uppercase completion follows the typed pattern`() {
        assertEquals("HAPPY", InputCasePolicy.applyPattern("happy", "HAP"))
    }

    @Test
    fun `lowercase typing leaves the completion alone`() {
        assertEquals("happy", InputCasePolicy.applyPattern("happy", "hap"))
    }

    private fun emptyDecoder() = object : DecoderContract {
        override val schemeName = "EmptyDecoder"

        override fun decode(buffer: String, scheme: Scheme) = DecodeResult(
            buffer = buffer,
            scheme = scheme,
            consumedLen = buffer.length,
            isExactCode = false,
            isPrefixOnly = false,
            candidates = emptyList()
        )

        override fun isSchemeAvailable(scheme: Scheme) = true
    }
}
