package com.hkmixedkeyboard.decoder

/**
 * Extended mock decoder for proto1 commit rule testing.
 * Keys are romanized Quick codes the user would type, not raw characters.
 * Codes are internally consistent approximations sufficient for logic testing.
 */
class ExtendedMockDecoder : DecoderContract {

    override val schemeName = "ExtendedMockDecoder"

    private val quickData: Map<String, DecodeResult> = buildMap {

        // ── Single characters ───────────────────────────────────────────────

        // 我  Quick(手戈) = QI
        put("qi", result("qi", Scheme.QUICK, true, false, 4,
            char("我", "qi", 0.95, false)))

        // 你  Quick(人弓) = ON
        put("on", result("on", Scheme.QUICK, true, false, 2,
            char("你", "on", 0.92, false)))

        // 佢  Quick(人土) = OG
        put("og", result("og", Scheme.QUICK, true, false, 2,
            char("佢", "og", 0.88, false)))

        // 唔  Quick(口口) = RR  isHkCore
        put("rr", result("rr", Scheme.QUICK, true, false, 2,
            char("唔", "rr", 0.97, true)))

        // 嘅  Quick(口女) = RV  isHkCore
        put("rv", result("rv", Scheme.QUICK, true, false, 2,
            char("嘅", "rv", 0.97, true)))

        // 哋  Quick(口心) = RP  isHkCore
        put("rp", result("rp", Scheme.QUICK, true, false, 2,
            char("哋", "rp", 0.90, true)))

        // 咗  Quick(口山) = RU  isHkCore
        put("ru", result("ru", Scheme.QUICK, true, false, 2,
            char("咗", "ru", 0.88, true)))

        // 喺  Quick(口竹) = RH  isHkCore
        put("rh", result("rh", Scheme.QUICK, true, false, 2,
            char("喺", "rh", 0.88, true)))

        // 冇  Quick(月一) = BM  isHkCore
        put("bm", result("bm", Scheme.QUICK, true, false, 2,
            char("冇", "bm", 0.90, true)))

        // 啲  Quick(口廿) = RT  isHkCore
        put("rt", result("rt", Scheme.QUICK, true, false, 2,
            char("啲", "rt", 0.88, true)))

        // 嗰  Quick(口廿) = RA — using RA to avoid collision with rt
        put("ra", result("ra", Scheme.QUICK, true, false, 2,
            char("嗰", "ra", 0.82, true)))

        // 嘢  Quick(口水) = RE  isHkCore
        put("re", result("re", Scheme.QUICK, true, false, 2,
            char("嘢", "re", 0.85, true)))

        // 係  Quick(人弓) = already used by 你; use OI instead
        put("oi", result("oi", Scheme.QUICK, true, false, 2,
            char("係", "oi", 0.88, false)))

        // 有  Quick(月土) = BG
        put("bg", result("bg", Scheme.QUICK, true, false, 2,
            char("有", "bg", 0.90, false)))

        // 未  Quick(木一) = DM
        put("dm", result("dm", Scheme.QUICK, true, false, 2,
            char("未", "dm", 0.80, false)))

        // 邊  Quick(弓廿) = NT
        put("nt", result("nt", Scheme.QUICK, true, false, 2,
            char("邊", "nt", 0.78, false)))

        // 個  Quick(人口) = OR
        put("or", result("or", Scheme.QUICK, true, false, 2,
            char("個", "or", 0.85, false)))

        // ── Collision cases (CN exact match AND English word) ───────────────

        // "ok" — Quick(人大) = OK; also SHORT_WHITELIST
        put("ok", result("ok", Scheme.QUICK, true, false, 2,
            char("仗", "ok", 0.05, false)))

        // "go" — Quick(土人) = GO; also English word
        put("go", result("go", Scheme.QUICK, true, false, 2,
            char("在", "go", 0.30, false)))

        // ── Prefix-only cases ────────────────────────────────────────────────

        // "hap" — prefix; Quick(竹日) = HA consumed 2, 'p' leftover
        put("hap", result("hap", Scheme.QUICK, false, true, 2,
            char("蝦", "ha", 0.40, false),
            char("俠", "ho", 0.15, false)))

        // "mtr" — SHORT_WHITELIST; prefix Quick(一廿) = MT consumed 2
        put("mtr", result("mtr", Scheme.QUICK, false, true, 2,
            char("貓", "mt", 0.10, false)))

        // ── Pure English (no Chinese match) ──────────────────────────────────

        // "send" — consumed full buffer, no match
        put("send", result("send", Scheme.QUICK, false, false, 4))

        // "confirm" — consumed full buffer, no match
        put("confirm", result("confirm", Scheme.QUICK, false, false, 7))

        // "check" — consumed full buffer, no match
        put("check", result("check", Scheme.QUICK, false, false, 5))

        // "call" — consumed full buffer, no match
        put("call", result("call", Scheme.QUICK, false, false, 4))

        // "reply" — consumed full buffer, no match
        put("reply", result("reply", Scheme.QUICK, false, false, 5))

        // "meeting" — consumed full buffer, no match
        put("meeting", result("meeting", Scheme.QUICK, false, false, 7))

        // "email" — consumed full buffer, no match
        put("email", result("email", Scheme.QUICK, false, false, 5))

        // "file" — consumed full buffer, no match
        put("file", result("file", Scheme.QUICK, false, false, 4))

        // ── Phrases ──────────────────────────────────────────────────────────

        // 我嘅  = Quick(我) + Quick(嘅) = "qi" + "rv" = "qirv"
        put("qirv", result("qirv", Scheme.QUICK, true, false, 4,
            phrase("我嘅", "qirv", 0.92, true)))

        // 你哋  = "on" + "rp" = "onrp"
        put("onrp", result("onrp", Scheme.QUICK, true, false, 4,
            phrase("你哋", "onrp", 0.91, true)))

        // 佢哋  = "og" + "rp" = "ogrp"
        put("ogrp", result("ogrp", Scheme.QUICK, true, false, 4,
            phrase("佢哋", "ogrp", 0.88, true)))

        // 唔係  = "rr" + "oi" = "rroi"
        put("rroi", result("rroi", Scheme.QUICK, true, false, 4,
            phrase("唔係", "rroi", 0.90, true)))

        // 唔好  = "rr" + Quick(好)="vd" = "rrvd"
        put("rrvd", result("rrvd", Scheme.QUICK, true, false, 4,
            phrase("唔好", "rrvd", 0.88, true)))

        // 唔該  = "rr" + Quick(該)="io" = "rrio"
        put("rrio", result("rrio", Scheme.QUICK, true, false, 4,
            phrase("唔該", "rrio", 0.96, true)))

        // 我哋  = "qi" + "rp" = "qirp"
        put("qirp", result("qirp", Scheme.QUICK, true, false, 4,
            phrase("我哋", "qirp", 0.90, true)))

        // 冇問題 = "bm" + Quick(問)="bnr" → phrase code = "bm"+"bn"+"nr" is too long; use direct
        put("bmbnnr", result("bmbnnr", Scheme.QUICK, true, false, 6,
            phrase("冇問題", "bmbnnr", 0.85, true)))

        // 有冇  = "bg" + "bm" = "bgbm"
        put("bgbm", result("bgbm", Scheme.QUICK, true, false, 4,
            phrase("有冇", "bgbm", 0.85, false)))

        // 咗未  = "ru" + "dm" = "rudm"
        put("rudm", result("rudm", Scheme.QUICK, true, false, 4,
            phrase("咗未", "rudm", 0.82, true)))

        // 喺邊  = "rh" + "nt" = "rhnt"
        put("rhnt", result("rhnt", Scheme.QUICK, true, false, 4,
            phrase("喺邊", "rhnt", 0.80, true)))

        // 嗰個  = "ra" + "or" = "raor"
        put("raor", result("raor", Scheme.QUICK, true, false, 4,
            phrase("嗰個", "raor", 0.82, true)))

        // 啲嘢  = "rt" + "re" = "rtre"
        put("rtre", result("rtre", Scheme.QUICK, true, false, 4,
            phrase("啲嘢", "rtre", 0.80, true)))

        // 可唔可以 = Quick(可)="yn" Quick(唔)="rr" Quick(可)="yn" Quick(以)="vi" = "ynrrynvi"
        put("ynrrynvi", result("ynrrynvi", Scheme.QUICK, true, false, 8,
            phrase("可唔可以", "ynrrynvi", 0.80, true)))

        // 遲啲  = Quick(遲)="yu" + "rt" = "yurt"
        put("yurt", result("yurt", Scheme.QUICK, true, false, 4,
            phrase("遲啲", "yurt", 0.82, true)))

        // 等陣  = Quick(等)="hgr" (3-char, prefix) ... just use "hgri" → Quick = h+i
        put("hiri", result("hiri", Scheme.QUICK, true, false, 4,
            phrase("等陣", "hiri", 0.80, true)))
    }

    override fun isSchemeAvailable(scheme: Scheme) = scheme == Scheme.QUICK || scheme == Scheme.CANGJIE
    override fun decode(buffer: String, scheme: Scheme): DecodeResult =
        quickData[buffer] ?: emptyResult(buffer, scheme)

    private fun result(
        buffer: String, scheme: Scheme, exact: Boolean, prefix: Boolean, consumed: Int,
        vararg cands: DecodeCandidate
    ) = DecodeResult(buffer, scheme, consumed, exact, prefix, cands.toList())

    private fun emptyResult(buffer: String, scheme: Scheme) =
        DecodeResult(buffer, scheme, buffer.length, false, false, emptyList())

    private fun char(text: String, code: String, freq: Double, hkCore: Boolean) =
        DecodeCandidate(text, code, SourceSchema.QUICK, CandidateType.CHAR, freq, hkCore)

    private fun phrase(text: String, code: String, freq: Double, hkCore: Boolean) =
        DecodeCandidate(text, code, SourceSchema.QUICK, CandidateType.PHRASE, freq, hkCore)
}
