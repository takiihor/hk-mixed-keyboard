package com.hkmixedkeyboard

import com.hkmixedkeyboard.commit.*
import com.hkmixedkeyboard.decoder.ExtendedMockDecoder
import com.hkmixedkeyboard.decoder.Scheme
import com.hkmixedkeyboard.engine.Classifier
import com.hkmixedkeyboard.memory.UserMemory

fun main() {
    println("═════════════════════════════════════════════")
    println("HK Mixed Keyboard — Prototype 1")
    println("Commit Rule Harness")
    println("═════════════════════════════════════════════\n")
    println("Run: ./gradlew test")
    println("Test report: build/reports/tests/test/index.html")
    println()

    val decoder = ExtendedMockDecoder()
    val classifier = Classifier(decoder)
    val memory = UserMemory()
    val ctx = ImeContext()
    val ctrl = CommitController(memory, { buf -> classifier.classify(buf, Scheme.QUICK) }, ctx)

    data class TypeCase(val buffer: String, val expected: String)
    val cases = listOf(
        TypeCase("rr", "唔"), TypeCase("rv", "嘅"), TypeCase("send", "send"),
        TypeCase("ok", "ok"), TypeCase("go", "go"), TypeCase("mtr", "mtr"),
        TypeCase("rrio", "唔該"), TypeCase("qirp", "我哋"), TypeCase("confirm", "confirm")
    )

    println("Space commit preview (cold-start):")
    println("─────────────────────────────────────────────")
    for (tc in cases) {
        val c = classifier.classify(tc.buffer, Scheme.QUICK)
        val target = ctrl.selectSpaceCommitTarget(tc.buffer, c)
        val status = if (target.text == tc.expected) "✓" else "✗"
        println("  $status buffer=\"${tc.buffer}\"  committed=\"${target.text}\"  expected=\"${tc.expected}\"")
    }
}
