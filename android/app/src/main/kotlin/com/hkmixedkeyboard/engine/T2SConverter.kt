package com.hkmixedkeyboard.engine

import java.io.BufferedReader

/**
 * Traditional → Simplified converter for the optional 簡體輸出 mode.
 *
 * Backed by a flat TSV asset (assets/t2s/t2s_map.tsv) derived from OpenCC's
 * TSCharacters + TSPhrases tables (Apache-2.0; see NOTICE). Conversion is a
 * greedy longest-match scan; multi-char phrase entries — including identity
 * entries like 乾隆→乾隆 — override char-level mappings, which is what keeps
 * 乾隆 intact while 乾杯 becomes 干杯.
 *
 * Every table entry maps to a same-length replacement (enforced when the asset
 * is generated), so converted output always has the character count the commit
 * pipeline recorded — backspace reverts (deleteSurroundingText counts) stay
 * correct.
 *
 * Internal IME state (dictionaries, user memory, prediction prefixes) stays
 * traditional; conversion happens only at the InputConnection boundary.
 * Cantonese-specific characters (嘅冇喺哋) have no simplified form and pass
 * through unchanged.
 *
 * Thread-safe: [load] swaps the whole immutable table via @Volatile; [convert]
 * on the main thread sees either the full table or none (no-op until loaded).
 */
class T2SConverter {

    @Volatile private var table: Map<String, String> = emptyMap()
    @Volatile private var maxKeyLength = 1

    val isLoaded: Boolean get() = table.isNotEmpty()

    /** Parses the TSV ("trad<TAB>simp" lines, # comments). Call off the main thread. */
    fun load(reader: BufferedReader) {
        val map = HashMap<String, String>(8192)
        var maxLen = 1
        reader.forEachLine { line ->
            if (line.isEmpty() || line.startsWith("#")) return@forEachLine
            val tab = line.indexOf('\t')
            if (tab <= 0 || tab == line.length - 1) return@forEachLine
            val trad = line.substring(0, tab)
            val simp = line.substring(tab + 1)
            if (trad.length != simp.length) return@forEachLine // defensive: same-length only
            map[trad] = simp
            if (trad.length > maxLen) maxLen = trad.length
        }
        maxKeyLength = maxLen
        table = map
    }

    /**
     * Converts [text] to Simplified. Returns [text] unchanged when the table
     * isn't loaded yet or nothing matches. Result always has text.length chars.
     */
    fun convert(text: String): String {
        val t = table
        if (t.isEmpty() || text.none { it.code > 0x2E80 }) return text
        val sb = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            var matched = false
            var len = minOf(maxKeyLength, text.length - i)
            while (len > 0) {
                val rep = t[text.substring(i, i + len)]
                if (rep != null) {
                    sb.append(rep)
                    i += len
                    matched = true
                    break
                }
                len--
            }
            if (!matched) {
                sb.append(text[i])
                i++
            }
        }
        return sb.toString()
    }
}
