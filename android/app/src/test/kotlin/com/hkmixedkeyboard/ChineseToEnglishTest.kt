package com.hkmixedkeyboard

import com.hkmixedkeyboard.decoder.CandidateType
import com.hkmixedkeyboard.decoder.DecodeCandidate
import com.hkmixedkeyboard.decoder.SourceSchema
import com.hkmixedkeyboard.engine.CandidateDisplayPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 中英互相建議, Chinese -> English. The English -> Chinese direction shipped long
 * ago; nothing ever mapped the other way, even though the rows to do it were
 * already parsed and cached.
 */
class ChineseToEnglishTest {

    private val policy = CandidateDisplayPolicy()

    private fun gloss(word: String, chinese: String) =
        DecodeCandidate(word, chinese, SourceSchema.ENGLISH, CandidateType.EN_LITERAL, 0.6, false)

    private fun nextChar(text: String) =
        DecodeCandidate(text, "", SourceSchema.QUICK, CandidateType.CHAR, 0.5, false)

    // ── Ordering ──────────────────────────────────────────────────────────

    @Test
    fun `glosses follow the continuations rather than displacing them`() {
        val out = policy.orderPredictions(
            learned = emptyList(),
            decoded = listOf("會", "區", "員").map(::nextChar),
            english = listOf(gloss("discuss", "討論")),
            limit = 15
        ).map { it.text }

        // Fewer continuations than the leading block, so the gloss simply follows.
        assertEquals(listOf("會", "區", "員", "discuss"), out)
    }

    @Test
    fun `a gloss stays inside the visible strip when many continuations exist`() {
        val many = (1..15).map { nextChar("字$it") }
        val out = policy.orderPredictions(
            learned = emptyList(),
            decoded = many,
            english = listOf(gloss("discuss", "討論"), gloss("discussion", "討論")),
            limit = 15
        )

        assertEquals(15, out.size)
        assertEquals(
            "gloss must sit just inside the strip, not at the far end",
            CandidateDisplayPolicy.LEADING_PREDICTIONS,
            out.indexOfFirst { it.text == "discuss" }
        )
        assertTrue(
            "continuations must still lead",
            out.take(CandidateDisplayPolicy.LEADING_PREDICTIONS).none { it.type == CandidateType.EN_LITERAL }
        )
    }

    @Test
    fun `no glosses leaves prediction behaviour unchanged`() {
        val decoded = listOf("會", "區").map(::nextChar)

        assertEquals(
            policy.orderPredictions(emptyList(), decoded, limit = 15).map { it.text },
            listOf("會", "區")
        )
    }

    // ── The bundled data the inverted index is built from ─────────────────

    private val assistRows: List<Pair<String, String>> by lazy {
        val out = mutableListOf<Pair<String, String>>()
        listOf("english_assist.csv", "english_assist_overrides.csv").forEach { name ->
            File("src/main/assets/corpus/$name").forEachLine { line ->
                val t = line.trim()
                if (t.startsWith("#") || t.isBlank()) return@forEachLine
                val cols = t.split(",")
                if (cols.size >= 3 && cols[0] != "english") out += cols[0] to cols[1]
            }
        }
        out
    }

    @Test
    fun `common Chinese words have an English gloss to offer back`() {
        val byChinese = assistRows.groupBy({ it.second }, { it.first })

        // Proper nouns such as 香港 are deliberately absent from the assist corpus,
        // so the reversible set is everyday vocabulary rather than place names.
        assertTrue("討論 -> ${byChinese["討論"]}", byChinese["討論"].orEmpty().contains("discuss"))
        assertTrue("會議 -> ${byChinese["會議"]}", byChinese["會議"].orEmpty().contains("meeting"))
        assertTrue("電腦 -> ${byChinese["電腦"]}", byChinese["電腦"].orEmpty().contains("computer"))
        assertTrue("銀行 -> ${byChinese["銀行"]}", byChinese["銀行"].orEmpty().contains("bank"))
    }

    @Test
    fun `the inverted direction covers a useful share of the corpus`() {
        val multiChar = assistRows.map { it.second }.filter { it.length >= 2 }.toSet()

        assertTrue(
            "expected thousands of reversible words, got ${multiChar.size}",
            multiChar.size > 10_000
        )
    }
}
