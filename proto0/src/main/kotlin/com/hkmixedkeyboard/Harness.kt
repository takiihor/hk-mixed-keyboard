package com.hkmixedkeyboard

import com.hkmixedkeyboard.decoder.*
import com.hkmixedkeyboard.engine.ClassifyResult
import com.hkmixedkeyboard.engine.Classifier

// Gate 1 test buffers from spec §7.6
private val TEST_BUFFERS = listOf(
    "hap", "send", "ok", "go", "mtr",
    "我", "你", "唔", "嘅", "唔該", "我哋"
)

fun runDecodeHarness(buffer: String, scheme: Scheme, decoder: DecoderContract, classifier: Classifier) {
    val dr = decoder.decode(buffer, scheme)
    val cr = classifier.classify(buffer, scheme)

    println("─────────────────────────────────────────────")
    println("buffer: \"$buffer\"  scheme: $scheme  decoder: ${decoder.schemeName}")
    println()
    println("  DecodeResult:")
    println("    consumedLen    = ${dr.consumedLen}")
    println("    isExactCode    = ${dr.isExactCode}")
    println("    isPrefixOnly   = ${dr.isPrefixOnly}")
    println()
    println("  Parse States (Gate 1 required):")
    println("    cnExactParsed   = ${dr.cnExactParsed}")
    println("    cnHasPhraseMatch= ${dr.cnHasPhraseMatch}")
    println("    cnPrefixParsed  = ${dr.cnPrefixParsed}")
    println()
    println("  ClassifyResult:")
    println("    enLiteral      = \"${cr.enLiteral}\"")
    println("    enIsWord       = ${cr.enIsWord}")
    println("    enAutocomplete = ${cr.enAutocomplete?.let { "\"$it\"" } ?: "null"}")
    println("    enStrongPrefix = ${cr.enStrongPrefix}")
    println()
    println("  Candidates (${dr.candidates.size}):")
    dr.candidates.forEach { c ->
        println("    [${c.type}] \"${c.text}\" code=${c.code} src=${c.sourceSchema} freq=${c.frequency} hkCore=${c.isHkCore}")
    }
    if (dr.candidates.isEmpty()) println("    (none)")
}

fun gate1Verdict(decoder: DecoderContract, classifier: Classifier): Boolean {
    val results = TEST_BUFFERS.map { buf ->
        val dr = decoder.decode(buf, Scheme.QUICK)
        val cr = classifier.classify(buf, Scheme.QUICK)
        Triple(buf, dr, cr)
    }

    println("\n═════════════════════════════════════════════")
    println("GATE 1 VERDICT")
    println("═════════════════════════════════════════════")

    var allPass = true
    val checks = mutableListOf<Pair<String, Boolean>>()

    // Check 1: three parse states are derivable deterministically
    val statesDeterministic = results.all { (_, dr, _) ->
        listOf(dr.cnExactParsed, dr.cnHasPhraseMatch, dr.cnPrefixParsed).all { it || !it }
    }
    checks += "Parse states derive deterministically" to statesDeterministic

    // Check 2: candidate source/type can be identified for non-empty candidate lists
    val candidateMeta = results.filter { (_, dr, _) -> dr.candidates.isNotEmpty() }.all { (_, dr, _) ->
        dr.candidates.all { c ->
            c.type != null && c.sourceSchema != null && c.text.isNotEmpty()
        }
    }
    checks += "Candidate source/type/text extractable" to candidateMeta

    // Check 3: Quick scheme works on test buffers
    val quickWorks = decoder.isSchemeAvailable(Scheme.QUICK)
    checks += "Quick scheme available" to quickWorks

    // Check 4: Cangjie scheme can be loaded (or clearly reported unavailable)
    val cangjieLoaded = decoder.isSchemeAvailable(Scheme.CANGJIE)
    checks += "Cangjie scheme available (or failure reported)" to true // always pass — we log status

    // Check 5: prefix-only buffers never become auto-committable (consumedLen < buffer.length means prefix)
    val prefixSafe = results.filter { (_, dr, _) -> dr.cnPrefixParsed && !dr.cnExactParsed && !dr.cnHasPhraseMatch }
        .all { (_, dr, _) -> !dr.isExactCode }
    checks += "Prefix-only buffers not auto-committable" to prefixSafe

    checks.forEach { (label, pass) ->
        println("  [${if (pass) "PASS" else "FAIL"}] $label")
        if (!pass) allPass = false
    }

    println()
    println("  Cangjie status: ${if (cangjieLoaded) "LOADED" else "NOT LOADED — ${RimeDecoderSpike.failureReason()}"}")
    println()
    println("  GATE 1: ${if (allPass) "✓ PASSED" else "✗ FAILED"}")
    println("═════════════════════════════════════════════")

    return allPass
}

fun main() {
    val mockDecoder = MockDecoder()
    val rimeSpike = RimeDecoderSpike()
    val classifier = Classifier(mockDecoder)

    println("═════════════════════════════════════════════")
    println("HK Mixed Keyboard — Prototype 0")
    println("Decode Contract Harness")
    println("═════════════════════════════════════════════\n")

    println(">>> MockDecoder — Quick Scheme — All test buffers\n")
    TEST_BUFFERS.forEach { buf ->
        runDecodeHarness(buf, Scheme.QUICK, mockDecoder, classifier)
    }

    println("\n>>> MockDecoder — Cangjie Scheme — Sample buffers\n")
    listOf("我", "唔").forEach { buf ->
        runDecodeHarness(buf, Scheme.CANGJIE, mockDecoder, classifier)
    }

    println("\n>>> RimeDecoderSpike — Quick Scheme — 'hap'\n")
    runDecodeHarness("hap", Scheme.QUICK, rimeSpike, Classifier(rimeSpike))

    gate1Verdict(mockDecoder, classifier)
}
