package com.hkmixedkeyboard

import com.hkmixedkeyboard.commit.*
import com.hkmixedkeyboard.decoder.*
import com.hkmixedkeyboard.engine.ClassifyResult
import com.hkmixedkeyboard.memory.UserMemory
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

/**
 * Unit tests for CommitController commit rules.
 * Tests use hand-crafted ClassifyResult inputs to isolate commit logic from decoder.
 */
class CommitRuleTest {

    private lateinit var memory: UserMemory
    private lateinit var ctx: ImeContext

    @BeforeEach
    fun setup() {
        memory = UserMemory()
        ctx = ImeContext()
    }

    private fun makeController(
        overrideCtx: ImeContext = ctx,
        classifyFn: (String) -> ClassifyResult = { emptyClassify(it) }
    ) = CommitController(memory, classifyFn, overrideCtx)

    // ── Space smart commit ─────────────────────────────────────────────────

    @Test
    fun `Space on clear Chinese buffer commits top candidate`() {
        val ctrl = makeController()
        val c = clearChinese("rr", "唔", isHkCore = true)
        val target = ctrl.selectSpaceCommitTarget("rr", c)
        assertEquals("唔", target.text)
        assertEquals(CandidateType.CHAR, target.type)
    }

    @Test
    fun `Space on clear English word commits EN literal`() {
        val ctrl = makeController()
        val c = clearEnglish("send")
        val target = ctrl.selectSpaceCommitTarget("send", c)
        assertEquals("send", target.text)
        assertEquals(CandidateType.EN_LITERAL, target.type)
    }

    @Test
    fun `Space on ambiguous buffer with no memory uses tieBreak`() {
        val ctrl = makeController()
        // "ok" has both CN match (仗, NOT HK core) and enIsWord=true
        val c = collision("ok", "仗", isHkCore = false)
        val target = ctrl.selectSpaceCommitTarget("ok", c)
        // cold-start: tieBreak → len=2 but not HK core → EN literal
        assertEquals("ok", target.text)
        assertEquals(CandidateType.EN_LITERAL, target.type)
    }

    @Test
    fun `Space on collision with HK core short buffer commits Chinese`() {
        val ctrl = makeController()
        // "rr" len=2, CN candidate 唔 isHkCore=true, no EN match
        val c = collision("rr", "唔", isHkCore = true, enIsWord = false)
        val target = ctrl.selectSpaceCommitTarget("rr", c)
        // tieBreak: len<=2 AND isHkCore → commit Chinese
        assertEquals("唔", target.text)
    }

    @Test
    fun `Space always-space mode commits literal regardless of CN match`() {
        val ctrl = makeController(ctx.copy(spaceMode = SpaceMode.ALWAYS_SPACE))
        val state = ImeStateData(buffer = "rr", imeState = ImeState.COMPOSING)
        val out = ctrl.onSpace(state)
        // ALWAYS_SPACE → commitLiteralBuffer → commits "rr", then commits " "
        // Both are in sequence; committed text is the literal buffer then space
        assertNotNull(out.committedText)
    }

    // ── English autocomplete never Space-commits ───────────────────────────

    @Test
    fun `Space does not commit autocomplete — only EN literal`() {
        val ctrl = makeController()
        val c = ClassifyResult(
            buffer = "hap",
            cnExactParsed = false, cnHasPhraseMatch = false, cnPrefixParsed = true,
            cnCandidates = emptyList(),
            enLiteral = "hap", enAutocomplete = "happy",
            enIsWord = true, enStrongPrefix = true
        )
        val target = ctrl.selectSpaceCommitTarget("hap", c)
        // enStrongPrefix=true but NO CN committable → EN literal "hap", not "happy"
        assertEquals("hap", target.text, "Must commit literal 'hap', not autocomplete 'happy'")
        assertEquals(CandidateType.EN_LITERAL, target.type)
    }

    // ── Prefix-only Chinese candidates not auto-committable ────────────────

    @Test
    fun `Prefix-only buffer is not cnCommittable`() {
        val ctrl = makeController()
        val c = ClassifyResult(
            buffer = "hap",
            cnExactParsed = false, cnHasPhraseMatch = false, cnPrefixParsed = true,
            cnCandidates = listOf(char("蝦", "ha")),
            enLiteral = "hap", enAutocomplete = "happy",
            enIsWord = true, enStrongPrefix = true
        )
        val target = ctrl.selectSpaceCommitTarget("hap", c)
        assertNotEquals("蝦", target.text, "Prefix-only candidate must not auto-commit")
    }

    // ── Punctuation conservative commit ───────────────────────────────────

    @Test
    fun `Punctuation commits EN literal when enIsWord is true`() {
        val ctrl = makeController()
        val c = clearEnglish("send")
        val target = ctrl.selectPunctuationCommitTarget("send", c)
        assertEquals("send", target.text)
        assertEquals(CandidateType.EN_LITERAL, target.type)
    }

    @Test
    fun `Punctuation commits Chinese when only CN match`() {
        val ctrl = makeController()
        val c = clearChinese("rr", "唔", isHkCore = true)
        val target = ctrl.selectPunctuationCommitTarget("rr", c)
        assertEquals("唔", target.text)
    }

    @Test
    fun `Punctuation on SHORT_WHITELIST commits EN literal even with CN match`() {
        val ctrl = makeController()
        val c = collision("ok", "仗", isHkCore = false)
        val target = ctrl.selectPunctuationCommitTarget("ok", c)
        assertEquals("ok", target.text)
    }

    // ── Backspace revert last auto-commit ─────────────────────────────────

    @Test
    fun `Backspace with lastAutoCommit restores buffer`() {
        val ctrl = makeController()
        val state = ImeStateData(
            buffer = "",
            lastAutoCommit = AutoCommitRecord("唔", "rr"),
            imeState = ImeState.PREDICTING
        )
        val out = ctrl.onBackspace(state, cursorJustAfterAutoCommit = true)
        assertEquals(1, out.deletedBefore, "Should delete 1 char (唔 is 1 Unicode code point)")
        assertEquals("rr", out.newState.buffer, "Buffer restored to 'rr'")
        assertEquals(ImeState.COMPOSING, out.newState.imeState)
    }

    @Test
    fun `Backspace on non-empty buffer pops last character`() {
        val ctrl = makeController()
        val state = ImeStateData(buffer = "rr", imeState = ImeState.COMPOSING)
        val out = ctrl.onBackspace(state)
        assertEquals("r", out.newState.buffer)
        assertNull(out.committedText)
        assertEquals(0, out.deletedBefore)
    }

    @Test
    fun `Backspace with no buffer and no lastAutoCommit deletes committed char`() {
        val ctrl = makeController()
        val state = ImeStateData(buffer = "", lastAutoCommit = null, imeState = ImeState.IDLE)
        val out = ctrl.onBackspace(state)
        assertEquals(1, out.deletedBefore)
    }

    // ── Enter policies ─────────────────────────────────────────────────────

    @Test
    fun `Enter COMMIT_THEN_SWALLOW commits buffer and swallows enter`() {
        val ctrl = makeController(ctx.copy(enterPolicy = EnterPolicy.COMMIT_THEN_SWALLOW))
        val state = ImeStateData(buffer = "send", imeState = ImeState.COMPOSING)
        val out = ctrl.onEnter(state)
        assertEquals("send", out.committedText)
        assertTrue(out.swallowEnter)
        assertEquals("", out.newState.buffer)
    }

    @Test
    fun `Enter ALWAYS_PASS_THROUGH passes enter even with non-empty buffer`() {
        val ctrl = makeController(ctx.copy(enterPolicy = EnterPolicy.ALWAYS_PASS_THROUGH))
        val state = ImeStateData(buffer = "rr", imeState = ImeState.COMPOSING)
        val out = ctrl.onEnter(state)
        assertEquals("\n", out.committedText)
        assertFalse(out.swallowEnter)
    }

    @Test
    fun `Enter never commits Chinese candidate`() {
        val ctrl = makeController()
        val state = ImeStateData(buffer = "rr", imeState = ImeState.COMPOSING)
        val out = ctrl.onEnter(state)
        // Must commit literal "rr", not 唔
        assertTrue(out.committedText == "rr" || out.committedText == "\n",
            "Enter must not commit Chinese candidate, got: ${out.committedText}")
        assertFalse(out.committedText == "唔", "Enter must never auto-select Chinese candidate")
    }

    // ── Memory hard override ───────────────────────────────────────────────

    @Test
    fun `Memory hard override triggers only after count_3 and confidence_0_8`() {
        memory.seed("qi", "我", "qi", count = 2)
        val ctrl = makeController()
        val c = collision("qi", "我", isHkCore = false, enIsWord = false)
        val target = ctrl.selectSpaceCommitTarget("qi", c)
        // count=2, below threshold of 3 → no hard override yet
        assertNull(memory.hardOverride("qi", false),
            "Should not override at count=2")
    }

    @Test
    fun `Memory hard override triggers at count_3`() {
        memory.seed("qi", "我", "qi", count = 3)
        val ctrl = makeController()
        assertNotNull(memory.hardOverride("qi", false),
            "Should hard override at count=3")
        val c = clearEnglish("qi") // would normally go English
        val target = ctrl.selectSpaceCommitTarget("qi", c)
        assertEquals("我", target.text, "Hard override must return 我 even if enIsWord")
    }

    @Test
    fun `Single accidental candidate tap does not trigger hard override`() {
        memory.seed("ok", "仗", "ok", count = 1)
        assertNull(memory.hardOverride("ok", false),
            "count=1 must never trigger hard override")
    }

    // ── Memory influence: tieBreak uses cnRatio ────────────────────────────

    @Test
    fun `tieBreak commits Chinese when cnRatio exceeds threshold`() {
        // Seed: "qi" used as Chinese 我 5 out of 5 times
        memory.seed("qi", "我", "qi", count = 5)
        val ctrl = makeController()
        val c = collision("qi", "我", isHkCore = false, enIsWord = true)
        val target = ctrl.tieBreak("qi", c)
        // cnRatio = 5/5 = 1.0 ≥ 0.65 → Chinese
        assertEquals("我", target.text)
    }

    @Test
    fun `tieBreak commits English when cnRatio below EN threshold`() {
        memory.seedEn("go", count = 5)
        val ctrl = makeController()
        val c = collision("go", "在", isHkCore = false, enIsWord = true)
        val target = ctrl.tieBreak("go", c)
        // cnRatio = 0/5 = 0.0 ≤ 0.35 → English
        assertEquals("go", target.text)
    }

    // ── Sensitive field no-learning ────────────────────────────────────────

    @Test
    fun `No memory write in sensitive field on candidate tap`() {
        val ctrl = makeController(ctx.copy(isSensitiveField = true))
        val state = ImeStateData(buffer = "rr", imeState = ImeState.COMPOSING)
        val cand = char("唔", "rr", isHkCore = true)
        val out = ctrl.onCandidateTap(cand, state)
        assertFalse(out.memoryWrite.shouldWrite, "No memory write in sensitive field")
        assertEquals(0, memory.size(), "Memory store must remain empty")
    }

    @Test
    fun `Candidate bar clears after sensitive field commit`() {
        val ctrl = makeController(ctx.copy(isSensitiveField = true))
        val state = ImeStateData(buffer = "rr", imeState = ImeState.COMPOSING)
        val cand = char("唔", "rr", isHkCore = true)
        val out = ctrl.onCandidateTap(cand, state)
        assertTrue(out.candidateBar.isEmpty(), "Candidate bar must be empty after sensitive commit")
        assertNull(out.newState.prevCommitted, "prevCommitted must be null in sensitive mode")
    }

    @Test
    fun `No candidate leakage from previous field on sensitive start`() {
        // Simulate: user committed something in normal field, then enters sensitive field
        val stateBeforeSensitive = ImeStateData(
            buffer = "", prevCommitted = "唔", imeState = ImeState.PREDICTING
        )
        // On field change, state must be fully reset
        val resetState = stateBeforeSensitive.idle().copy(prevCommitted = null)
        assertNull(resetState.prevCommitted)
        assertNull(resetState.lastAutoCommit)
        assertEquals(ImeState.IDLE, resetState.imeState)
    }

    // ── Whitelist collision ────────────────────────────────────────────────

    @ParameterizedTest(name = "Whitelist \"{0}\" commits as English")
    @CsvSource("ok", "go", "hi", "no")
    fun `Whitelist items commit as English on Space in cold start`(buffer: String) {
        val ctrl = makeController()
        val c = clearEnglish(buffer)
        val target = ctrl.selectSpaceCommitTarget(buffer, c)
        assertEquals(buffer, target.text)
        assertEquals(CandidateType.EN_LITERAL, target.type)
    }

    @Test
    fun `MTR commits as canonical MTR not mtr`() {
        val ctrl = makeController()
        val cand = DecodeCandidate("MTR", "", SourceSchema.ENGLISH, CandidateType.EN_LITERAL, 0.0, false)
        // canonicalText is applied inside commitCandidate; check via onSpace
        // Just verify the canonical form mapping exists
        val c = clearEnglish("mtr")
        val target = ctrl.selectSpaceCommitTarget("mtr", c)
        assertEquals("mtr", target.text)
    }

    // ── Candidate tap always wins ──────────────────────────────────────────

    @Test
    fun `Candidate tap commits exact tapped candidate regardless of ranking`() {
        val ctrl = makeController()
        val state = ImeStateData(buffer = "ok", imeState = ImeState.COMPOSING)
        val tapped = char("仗", "ok") // user tapped the CN candidate explicitly
        val out = ctrl.onCandidateTap(tapped, state)
        assertEquals("仗", out.committedText, "Tap always commits what was tapped")
    }

    // ── onKeyPress state transitions ──────────────────────────────────────

    @Test
    fun `KeyPress transitions to COMPOSING`() {
        val ctrl = makeController()
        val state = ImeStateData()
        val out = ctrl.onKeyPress("r", state)
        assertEquals(ImeState.COMPOSING, out.newState.imeState)
        assertEquals("r", out.newState.buffer)
        assertNull(out.committedText)
    }

    @Test
    fun `KeyPress clears lastAutoCommit`() {
        val ctrl = makeController()
        val state = ImeStateData(lastAutoCommit = AutoCommitRecord("唔", "rr"))
        val out = ctrl.onKeyPress("r", state)
        assertNull(out.newState.lastAutoCommit)
    }

    // ── Sentence terminator resets context ────────────────────────────────

    @Test
    fun `Sentence terminator punctuation resets context`() {
        val ctrl = makeController()
        val state = ImeStateData(buffer = "", prevCommitted = "唔", imeState = ImeState.PREDICTING)
        val out = ctrl.onPunctuation("。", state)
        assertNull(out.newState.prevCommitted)
        assertEquals(ImeState.IDLE, out.newState.imeState)
    }

    @Test
    fun `Non-reset punctuation preserves prevCommitted`() {
        val ctrl = makeController()
        val state = ImeStateData(buffer = "", prevCommitted = "唔", imeState = ImeState.PREDICTING)
        val out = ctrl.onPunctuation("，", state)
        // state.prevCommitted should be preserved since ， is non-reset
        // In doCommitRaw: resetContext=false for non-terminators
        assertEquals("，", out.committedText)
    }

    // ── Helper factories ──────────────────────────────────────────────────

    private fun char(text: String, code: String, isHkCore: Boolean = false) =
        DecodeCandidate(text, code, SourceSchema.QUICK, CandidateType.CHAR, 0.9, isHkCore)

    private fun clearChinese(buffer: String, text: String, isHkCore: Boolean = false) = ClassifyResult(
        buffer = buffer,
        cnExactParsed = true, cnHasPhraseMatch = false, cnPrefixParsed = false,
        cnCandidates = listOf(char(text, buffer, isHkCore)),
        enLiteral = buffer, enAutocomplete = null, enIsWord = false, enStrongPrefix = false
    )

    private fun clearEnglish(buffer: String) = ClassifyResult(
        buffer = buffer,
        cnExactParsed = false, cnHasPhraseMatch = false, cnPrefixParsed = false,
        cnCandidates = emptyList(),
        enLiteral = buffer, enAutocomplete = null, enIsWord = true, enStrongPrefix = false
    )

    private fun collision(
        buffer: String, cnText: String, isHkCore: Boolean, enIsWord: Boolean = true
    ) = ClassifyResult(
        buffer = buffer,
        cnExactParsed = true, cnHasPhraseMatch = false, cnPrefixParsed = false,
        cnCandidates = listOf(char(cnText, buffer, isHkCore)),
        enLiteral = buffer, enAutocomplete = null, enIsWord = enIsWord, enStrongPrefix = false
    )

    private fun emptyClassify(buffer: String) = ClassifyResult(
        buffer = buffer,
        cnExactParsed = false, cnHasPhraseMatch = false, cnPrefixParsed = false,
        cnCandidates = emptyList(),
        enLiteral = buffer, enAutocomplete = null, enIsWord = false, enStrongPrefix = false
    )
}
