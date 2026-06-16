package com.hkmixedkeyboard

import com.hkmixedkeyboard.decoder.DecodeResult
import com.hkmixedkeyboard.decoder.DecoderContract
import com.hkmixedkeyboard.decoder.Scheme
import com.hkmixedkeyboard.engine.Classifier
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
        assertEquals("Rr", result.enLiteral)
    }

    @Test
    fun `classifier initial capital autocomplete follows typed pattern`() {
        val result = Classifier(emptyDecoder()).classify("Hap", Scheme.QUICK)

        assertEquals("Happy", result.enAutocomplete)
    }

    @Test
    fun `classifier uppercase autocomplete follows typed pattern`() {
        val result = Classifier(emptyDecoder()).classify("HAP", Scheme.QUICK)

        assertEquals("HAPPY", result.enAutocomplete)
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
