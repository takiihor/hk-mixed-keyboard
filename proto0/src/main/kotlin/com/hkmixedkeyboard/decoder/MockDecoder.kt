package com.hkmixedkeyboard.decoder

/**
 * Deterministic mock for Gate 1 validation.
 * Data reflects realistic Quick/Cangjie semantics without requiring librime.
 *
 * Quick code = first Cangjie radical key + last Cangjie radical key (2 letters only).
 * Cangjie code = full sequence of radical keys (2–5 letters).
 *
 * Direct Chinese character input (e.g. "我") is handled via reverse-lookup table
 * to test that the system can identify source, code, and isHkCore metadata.
 */
class MockDecoder : DecoderContract {

    override val schemeName = "MockDecoder"

    // Romanized buffer → DecodeResult for Quick scheme test cases
    private val quickLookup: Map<String, DecodeResult> = buildMap {
        // "hap" — Quick prefix; H=竹 A=日 P=心; 3 letters → not a valid Quick code (Quick = 2 letters)
        // isPrefixOnly=true because "ha" could be a 2-letter Quick prefix
        put("hap", DecodeResult(
            buffer = "hap", scheme = Scheme.QUICK,
            consumedLen = 2,         // consumed "ha" as possible Quick code
            isExactCode = false,
            isPrefixOnly = true,
            candidates = listOf(
                DecodeCandidate("蝦", "ha", SourceSchema.QUICK, CandidateType.CHAR, 0.40, false),
                DecodeCandidate("俠", "ho", SourceSchema.QUICK, CandidateType.CHAR, 0.15, false)
            )
        ))

        // "send" — common English word; S=尸 E=水 N=弓 D=木; 4 letters → no Quick code match
        // consumedLen = buffer.length: decoder processed whole buffer, found no Chinese interpretation.
        // cnPrefixParsed = isPrefixOnly || consumedLen < buffer.length = false || (4 < 4) = false ✓
        put("send", DecodeResult(
            buffer = "send", scheme = Scheme.QUICK,
            consumedLen = 4,
            isExactCode = false,
            isPrefixOnly = false,
            candidates = emptyList()
        ))

        // "ok" — on SHORT_WHITELIST; O=人 K=大 → Quick "ok" could match 仗 but whitelist wins
        put("ok", DecodeResult(
            buffer = "ok", scheme = Scheme.QUICK,
            consumedLen = 2,
            isExactCode = true,
            isPrefixOnly = false,
            candidates = listOf(
                DecodeCandidate("仗", "ok", SourceSchema.QUICK, CandidateType.CHAR, 0.05, false)
            )
        ))

        // "go" — common English word; G=土 O=人 → Quick "go" has Chinese match, but enIsWord wins
        put("go", DecodeResult(
            buffer = "go", scheme = Scheme.QUICK,
            consumedLen = 2,
            isExactCode = true,
            isPrefixOnly = false,
            candidates = listOf(
                DecodeCandidate("在", "go", SourceSchema.QUICK, CandidateType.CHAR, 0.30, false)
            )
        ))

        // "mtr" — SHORT_WHITELIST (MTR); M=一 T=廿 R=口; 3 letters → prefix only
        put("mtr", DecodeResult(
            buffer = "mtr", scheme = Scheme.QUICK,
            consumedLen = 2,
            isExactCode = false,
            isPrefixOnly = true,
            candidates = listOf(
                DecodeCandidate("貓", "mt", SourceSchema.QUICK, CandidateType.CHAR, 0.10, false)
            )
        ))

        // Direct Chinese character inputs — reverse-lookup by character
        // 我 — Quick code QO (手人); common character
        put("我", DecodeResult(
            buffer = "我", scheme = Scheme.QUICK,
            consumedLen = 1,
            isExactCode = true,
            isPrefixOnly = false,
            candidates = listOf(
                DecodeCandidate("我", "qo", SourceSchema.QUICK, CandidateType.CHAR, 0.95, false)
            )
        ))

        // 你 — Quick code OI (人戈); common character
        put("你", DecodeResult(
            buffer = "你", scheme = Scheme.QUICK,
            consumedLen = 1,
            isExactCode = true,
            isPrefixOnly = false,
            candidates = listOf(
                DecodeCandidate("你", "oi", SourceSchema.QUICK, CandidateType.CHAR, 0.92, false)
            )
        ))

        // 唔 — Quick code RO (口人); HK core Cantonese negative particle
        put("唔", DecodeResult(
            buffer = "唔", scheme = Scheme.QUICK,
            consumedLen = 1,
            isExactCode = true,
            isPrefixOnly = false,
            candidates = listOf(
                DecodeCandidate("唔", "ro", SourceSchema.QUICK, CandidateType.CHAR, 0.97, true)
            )
        ))

        // 嘅 — Quick code RV (口女); HK core possessive/genitive particle
        put("嘅", DecodeResult(
            buffer = "嘅", scheme = Scheme.QUICK,
            consumedLen = 1,
            isExactCode = true,
            isPrefixOnly = false,
            candidates = listOf(
                DecodeCandidate("嘅", "rv", SourceSchema.QUICK, CandidateType.CHAR, 0.97, true)
            )
        ))

        // 唔該 — Quick phrase: Quick(唔)=ro + Quick(該)=io → "roio"
        put("唔該", DecodeResult(
            buffer = "唔該", scheme = Scheme.QUICK,
            consumedLen = 2,
            isExactCode = true,
            isPrefixOnly = false,
            candidates = listOf(
                DecodeCandidate("唔該", "roio", SourceSchema.QUICK, CandidateType.PHRASE, 0.96, true)
            )
        ))

        // 我哋 — Quick phrase: Quick(我)=qo + Quick(哋)=rp → "qorp"
        put("我哋", DecodeResult(
            buffer = "我哋", scheme = Scheme.QUICK,
            consumedLen = 2,
            isExactCode = true,
            isPrefixOnly = false,
            candidates = listOf(
                DecodeCandidate("我哋", "qorp", SourceSchema.QUICK, CandidateType.PHRASE, 0.90, true)
            )
        ))
    }

    // Cangjie scheme — same data, different source schema label; verifies schema can be loaded
    private val cangjieExtras: Map<String, DecodeResult> = buildMap {
        put("我", DecodeResult(
            buffer = "我", scheme = Scheme.CANGJIE,
            consumedLen = 1,
            isExactCode = true,
            isPrefixOnly = false,
            candidates = listOf(
                DecodeCandidate("我", "hqi", SourceSchema.CANGJIE, CandidateType.CHAR, 0.95, false)
            )
        ))
        put("唔", DecodeResult(
            buffer = "唔", scheme = Scheme.CANGJIE,
            consumedLen = 1,
            isExactCode = true,
            isPrefixOnly = false,
            candidates = listOf(
                DecodeCandidate("唔", "ror", SourceSchema.CANGJIE, CandidateType.CHAR, 0.97, true)
            )
        ))
    }

    override fun isSchemeAvailable(scheme: Scheme): Boolean = when (scheme) {
        Scheme.QUICK -> true
        Scheme.CANGJIE -> true
        Scheme.MIXED_EXPERIMENTAL -> false
    }

    override fun decode(buffer: String, scheme: Scheme): DecodeResult {
        return when (scheme) {
            Scheme.QUICK -> quickLookup[buffer] ?: unknownBuffer(buffer, scheme)
            Scheme.CANGJIE -> cangjieExtras[buffer] ?: unknownBuffer(buffer, scheme)
            Scheme.MIXED_EXPERIMENTAL -> unknownBuffer(buffer, scheme)
        }
    }

    private fun unknownBuffer(buffer: String, scheme: Scheme) = DecodeResult(
        buffer = buffer, scheme = scheme,
        consumedLen = 0,
        isExactCode = false,
        isPrefixOnly = false,
        candidates = emptyList()
    )
}
