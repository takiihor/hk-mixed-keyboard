package com.hkmixedkeyboard.decoder

/**
 * Placeholder for the real librime JNI bridge.
 *
 * Gate 1 requirement: Cangjie schema can be loaded, even if not default.
 * This spike documents what the real implementation must provide and
 * why it cannot be exercised without the native .so library.
 *
 * To wire real librime:
 *   1. Build librime as a shared library (librime.so) for arm64-v8a / x86_64.
 *   2. Implement rime_bridge.cpp exposing:
 *        Java_com_hkmixedkeyboard_decoder_RimeDecoder_nativeDecode(...)
 *   3. Replace this class with RimeDecoder that calls System.loadLibrary("rime_bridge").
 *
 * Expected native contract:
 *   nativeDecode(buffer: String, schemeId: String): String (JSON of DecodeResult)
 *
 * Known limitations of this spike:
 *   - No actual librime.so is present; all calls return UNAVAILABLE.
 *   - Schema loading cannot be verified without the binary.
 *   - JNI bridge code is not compiled (CMakeLists.txt not yet wired).
 */
class RimeDecoderSpike : DecoderContract {

    override val schemeName = "RimeDecoderSpike (no native lib)"

    override fun isSchemeAvailable(scheme: Scheme): Boolean = false

    override fun decode(buffer: String, scheme: Scheme): DecodeResult {
        // Real implementation would call: nativeDecode(buffer, scheme.rimeId)
        // Returning empty result with a clear signal so Gate 1 report captures the gap.
        return DecodeResult(
            buffer = buffer,
            scheme = scheme,
            consumedLen = 0,
            isExactCode = false,
            isPrefixOnly = false,
            candidates = listOf(
                DecodeCandidate(
                    text = "[RIME_UNAVAILABLE]",
                    code = "",
                    sourceSchema = SourceSchema.QUICK,
                    type = CandidateType.EN_LITERAL,
                    frequency = 0.0,
                    isHkCore = false
                )
            )
        )
    }

    companion object {
        fun failureReason(): String =
            "librime native library not present. " +
            "Build rime_bridge.cpp with CMake targeting the host or Android ABI, " +
            "then replace RimeDecoderSpike with RimeDecoder."
    }
}
