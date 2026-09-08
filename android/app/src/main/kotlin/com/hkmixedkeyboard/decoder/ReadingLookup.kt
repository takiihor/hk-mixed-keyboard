package com.hkmixedkeyboard.decoder

import com.hkmixedkeyboard.util.Csv
import java.io.Reader

/**
 * Exact `Chinese text -> toned romanization` lookup behind the learning hints.
 *
 * One instance per romanization (粵拼 from rime-cantonese, 拼音 from CC-CEDICT).
 * Both assets carry the same two-column shape, and both are exact-match only: a
 * phrase reading is never fabricated by joining possibly polyphonic characters,
 * so a miss returns null and the hint is simply not shown.
 */
class ReadingLookup private constructor(
    private val readings: Map<String, String>
) {
    fun readingFor(text: String): String? = readings[text]

    companion object {
        fun empty() = ReadingLookup(emptyMap())

        fun from(reader: Reader): ReadingLookup {
            val readings = LinkedHashMap<String, String>()
            var headerSkipped = false
            reader.buffered().forEachLine { line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEachLine
                if (!headerSkipped) {
                    headerSkipped = true
                    return@forEachLine
                }
                val fields = Csv.split(trimmed)
                if (fields.size < 2) return@forEachLine
                val text = fields[0]
                val reading = fields[1]
                if (text.isNotEmpty() && reading.isNotEmpty()) {
                    readings.putIfAbsent(text, reading)
                }
            }
            return ReadingLookup(readings)
        }
    }
}
