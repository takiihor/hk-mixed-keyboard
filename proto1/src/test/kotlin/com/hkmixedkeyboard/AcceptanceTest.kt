package com.hkmixedkeyboard

import com.hkmixedkeyboard.commit.*
import com.hkmixedkeyboard.decoder.*
import com.hkmixedkeyboard.engine.Classifier
import com.hkmixedkeyboard.memory.UserMemory
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Acceptance test suite — Groups A through E.
 * Uses ExtendedMockDecoder + Classifier to simulate actual user typing.
 *
 * Measures:
 *   - Space mis-commit rate  (Gate 2: ≤ 2%)
 *   - EN literal preservation rate  (Gate 2: ≥ 99%)
 *   - Sensitive memory violation  (Gate 2: = 0)
 */
class AcceptanceTest {

    // ── Profiles ─────────────────────────────────────────────────────────────

    private fun coldMemory() = UserMemory()

    private fun warmMemory() = UserMemory().also { m ->
        m.seed("qirp", "我哋", "qirp", count = 5, isHkCore = true)   // 我哋 often selected
        m.seedEn("ok", count = 5)                                      // ok often literal
        m.seedEn("mtr", count = 5)                                     // MTR often literal
        m.seedEn("send", count = 5)                                    // send often literal
    }

    private fun makeCtrl(memory: UserMemory, sensitive: Boolean = false): CommitController {
        val decoder = ExtendedMockDecoder()
        val classifier = Classifier(decoder)
        val ctx = ImeContext(isSensitiveField = sensitive)
        return CommitController(memory, { buf -> classifier.classify(buf, Scheme.QUICK) }, ctx)
    }

    // ── Space commit oracle ────────────────────────────────────────────────

    /** Simulate: type buffer, press Space. Returns what was committed. */
    private fun typeAndSpace(buffer: String, memory: UserMemory, sensitive: Boolean = false): String {
        val ctrl = makeCtrl(memory, sensitive)
        val decoder = ExtendedMockDecoder()
        val classifier = Classifier(decoder)
        val c = classifier.classify(buffer, Scheme.QUICK)

        val state = ImeStateData(buffer = buffer, imeState = ImeState.COMPOSING)
        val out = ctrl.onSpace(state)
        return out.committedText ?: ""
    }

    // ── Group A: HK Core Characters / Phrases ─────────────────────────────

    data class SpaceCase(val buffer: String, val expectedText: String, val group: String)

    private val groupA = listOf(
        SpaceCase("qirv",   "我嘅",   "A"),   // phrase
        SpaceCase("onrp",   "你哋",   "A"),   // phrase
        SpaceCase("ogrp",   "佢哋",   "A"),   // phrase
        SpaceCase("rrio",   "唔該",   "A"),   // phrase
        SpaceCase("bmbnnr", "冇問題", "A"),   // phrase
        SpaceCase("bgbm",   "有冇",   "A"),   // phrase
        SpaceCase("rudm",   "咗未",   "A"),   // phrase
        SpaceCase("rhnt",   "喺邊",   "A"),   // phrase
        SpaceCase("raor",   "嗰個",   "A"),   // phrase
        SpaceCase("rtre",   "啲嘢",   "A"),   // phrase
    )

    // ── Group B: Mixed Chinese-English ─────────────────────────────────────

    private val groupB = listOf(
        // English tokens in mixed phrases — must commit as EN literal
        SpaceCase("send",    "send",    "B"),
        SpaceCase("confirm", "confirm", "B"),
        SpaceCase("reply",   "reply",   "B"),
        SpaceCase("check",   "check",   "B"),
        SpaceCase("email",   "email",   "B"),
        SpaceCase("call",    "call",    "B"),
        SpaceCase("file",    "file",    "B"),
        SpaceCase("meeting", "meeting", "B"),
    )

    // ── Group C: Whitelist Collisions ──────────────────────────────────────

    // Spec §14.5: canonical casing applied at commit time (mtr → MTR etc.)
    private val groupC = listOf(
        SpaceCase("ok",  "ok",  "C"),   // SHORT_WHITELIST; CN match is not HK core; no canonical override
        SpaceCase("go",  "go",  "C"),   // enIsWord + CN match; len=2 but not HK core → EN
        SpaceCase("mtr", "MTR", "C"),   // SHORT_WHITELIST → canonical MTR
        SpaceCase("fps", "FPS", "C"),   // → canonical FPS
        SpaceCase("mpf", "MPF", "C"),
        SpaceCase("pdf", "PDF", "C"),
        SpaceCase("hkd", "HKD", "C"),
        SpaceCase("usd", "USD", "C"),
    )

    // ── Group D: Phrase-first ──────────────────────────────────────────────

    private val groupD = listOf(
        SpaceCase("rrio", "唔該", "D"),
        SpaceCase("rroi", "唔係", "D"),
        SpaceCase("rrvd", "唔好", "D"),
        SpaceCase("onrp", "你哋", "D"),
        SpaceCase("qirp", "我哋", "D"),
        SpaceCase("bmbnnr", "冇問題", "D"),
        SpaceCase("ynrrynvi", "可唔可以", "D"),
        SpaceCase("yurt", "遲啲", "D"),
        SpaceCase("hiri", "等陣", "D"),
    )

    // ── Gate 2 metrics calculation ─────────────────────────────────────────

    private fun runGroup(cases: List<SpaceCase>, memory: UserMemory, label: String): GroupResult {
        var pass = 0
        var fail = 0
        val failures = mutableListOf<String>()

        for (case in cases) {
            val actual = typeAndSpace(case.buffer, memory)
            if (actual == case.expectedText) {
                pass++
            } else {
                fail++
                failures += "  [FAIL] buffer=\"${case.buffer}\" expected=\"${case.expectedText}\" got=\"$actual\""
            }
        }

        return GroupResult(label, pass, fail, failures)
    }

    data class GroupResult(val label: String, val pass: Int, val fail: Int, val failures: List<String>) {
        val total get() = pass + fail
        val misCommitRate get() = if (total == 0) 0.0 else fail.toDouble() / total
    }

    // ── Cold-start profile ─────────────────────────────────────────────────

    @Test
    fun `Cold profile — Group A HK core characters and phrases`() {
        val r = runGroup(groupA, coldMemory(), "A-cold")
        printResult(r)
        assertTrue(r.misCommitRate <= 0.02, "Group A mis-commit rate ${r.misCommitRate} exceeds 2%\n${r.failures.joinToString("\n")}")
    }

    @Test
    fun `Cold profile — Group B mixed English tokens`() {
        val r = runGroup(groupB, coldMemory(), "B-cold")
        printResult(r)
        // EN literal preservation: all B cases must commit as English
        assertTrue(r.fail == 0, "Group B EN literal preservation failed:\n${r.failures.joinToString("\n")}")
    }

    @Test
    fun `Cold profile — Group C whitelist collisions`() {
        val r = runGroup(groupC, coldMemory(), "C-cold")
        printResult(r)
        assertTrue(r.misCommitRate <= 0.02, "Group C mis-commit rate ${r.misCommitRate} exceeds 2%\n${r.failures.joinToString("\n")}")
    }

    @Test
    fun `Cold profile — Group D phrase-first`() {
        val r = runGroup(groupD, coldMemory(), "D-cold")
        printResult(r)
        assertTrue(r.misCommitRate <= 0.02, "Group D mis-commit rate ${r.misCommitRate} exceeds 2%\n${r.failures.joinToString("\n")}")
    }

    // ── Warm profile ───────────────────────────────────────────────────────

    @Test
    fun `Warm profile — Group A still commits Chinese correctly`() {
        val r = runGroup(groupA, warmMemory(), "A-warm")
        printResult(r)
        assertTrue(r.misCommitRate <= 0.02, "Group A warm mis-commit rate exceeds 2%\n${r.failures.joinToString("\n")}")
    }

    @Test
    fun `Warm profile — Group B English tokens still preserved`() {
        val r = runGroup(groupB, warmMemory(), "B-warm")
        printResult(r)
        assertTrue(r.fail == 0, "Warm Group B EN literal preservation failed:\n${r.failures.joinToString("\n")}")
    }

    @Test
    fun `Warm profile — Group C whitelist collisions still correct`() {
        val r = runGroup(groupC, warmMemory(), "C-warm")
        printResult(r)
        assertTrue(r.misCommitRate <= 0.02, "Warm Group C mis-commit rate exceeds 2%\n${r.failures.joinToString("\n")}")
    }

    @Test
    fun `Warm profile — memory hard override for 我哋 works`() {
        val memory = warmMemory() // 我哋 seeded with count=5
        val ctrl = makeCtrl(memory)
        val decoder = ExtendedMockDecoder()
        val classifier = Classifier(decoder)
        val c = classifier.classify("qirp", Scheme.QUICK)
        val target = ctrl.selectSpaceCommitTarget("qirp", c)
        assertEquals("我哋", target.text, "Warm memory should hard-override for 我哋")
    }

    // ── Group E: Privacy / Sensitive Fields ────────────────────────────────

    @Test
    fun `Sensitive field — no memory write on candidate tap`() {
        val memory = coldMemory()
        val ctrl = makeCtrl(memory, sensitive = true)
        val state = ImeStateData(buffer = "rr", imeState = ImeState.COMPOSING)
        val cand = DecodeCandidate("唔", "rr", SourceSchema.QUICK, CandidateType.CHAR, 0.97, true)
        val out = ctrl.onCandidateTap(cand, state)
        assertFalse(out.memoryWrite.shouldWrite, "Sensitive: memory write must be false")
        assertEquals(0, memory.size(), "Sensitive: memory store must remain empty")
    }

    @Test
    fun `Sensitive field — no memory write on Space commit`() {
        val memory = coldMemory()
        val ctrl = makeCtrl(memory, sensitive = true)
        val state = ImeStateData(buffer = "rr", imeState = ImeState.COMPOSING)
        val out = ctrl.onSpace(state)
        assertFalse(out.memoryWrite.shouldWrite)
        assertEquals(0, memory.size())
    }

    @Test
    fun `Sensitive field — no memory write on Enter commit`() {
        val memory = coldMemory()
        val ctrl = makeCtrl(memory, sensitive = true)
        val state = ImeStateData(buffer = "send", imeState = ImeState.COMPOSING)
        val out = ctrl.onEnter(state)
        assertFalse(out.memoryWrite.shouldWrite)
        assertEquals(0, memory.size())
    }

    @Test
    fun `Sensitive field — candidate bar is empty after commit`() {
        val memory = coldMemory()
        val ctrl = makeCtrl(memory, sensitive = true)
        val state = ImeStateData(buffer = "rr", imeState = ImeState.COMPOSING)
        val cand = DecodeCandidate("唔", "rr", SourceSchema.QUICK, CandidateType.CHAR, 0.97, true)
        val out = ctrl.onCandidateTap(cand, state)
        assertTrue(out.candidateBar.isEmpty())
    }

    @Test
    fun `Sensitive field — prevCommitted is null after commit`() {
        val memory = coldMemory()
        val ctrl = makeCtrl(memory, sensitive = true)
        val state = ImeStateData(buffer = "rr", imeState = ImeState.COMPOSING)
        val cand = DecodeCandidate("唔", "rr", SourceSchema.QUICK, CandidateType.CHAR, 0.97, true)
        val out = ctrl.onCandidateTap(cand, state)
        assertNull(out.newState.prevCommitted)
    }

    @Test
    fun `Sensitive field — hardOverride returns null`() {
        val memory = warmMemory()
        // Even with 5 seeded entries, sensitive field must block override
        val result = memory.hardOverride("qirp", isSensitive = true)
        assertNull(result, "hardOverride must return null in sensitive field")
    }

    // ── Overall Gate 2 summary ─────────────────────────────────────────────

    @Test
    fun `Gate 2 overall mis-commit rate across all cold-start cases`() {
        val allCases = groupA + groupB + groupC + groupD
        val memory = coldMemory()
        val r = runGroup(allCases, memory, "ALL-cold")

        println("\n═══════════════════════════════════════════════")
        println("GATE 2 — OVERALL METRICS (Cold-start)")
        println("═══════════════════════════════════════════════")
        println("  Total cases   : ${r.total}")
        println("  Passed        : ${r.pass}")
        println("  Failed        : ${r.fail}")
        println("  Mis-commit %  : ${"%.1f".format(r.misCommitRate * 100)}%  (limit: ≤ 2%)")
        if (r.failures.isNotEmpty()) {
            println("  Failures:")
            r.failures.forEach { println(it) }
        }
        println("  EN literal preservation: ${groupB.count { typeAndSpace(it.buffer, memory) == it.expectedText }} / ${groupB.size}")
        println()
        println("  Sensitive violation: 0  (verified by Group E tests)")
        println()
        val gate2Pass = r.misCommitRate <= 0.02
        println("  GATE 2: ${if (gate2Pass) "✓ PASSED" else "✗ FAILED"}")
        println("═══════════════════════════════════════════════")

        assertTrue(gate2Pass, "Gate 2 overall mis-commit rate ${r.misCommitRate} exceeds 2%")
    }

    private fun printResult(r: GroupResult) {
        println("Group ${r.label}: ${r.pass}/${r.total} pass  " +
            "mis-commit=${"%.1f".format(r.misCommitRate * 100)}%")
        r.failures.forEach { println(it) }
    }
}
