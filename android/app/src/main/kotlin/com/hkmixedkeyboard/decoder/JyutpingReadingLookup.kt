package com.hkmixedkeyboard.decoder

import com.hkmixedkeyboard.util.Csv
import java.io.Reader

class JyutpingReadingLookup private constructor(
    private val readings: Map<String, String>
) {
    fun readingFor(text: String): String? = readings[text]

    companion object {
        fun empty() = JyutpingReadingLookup(emptyMap())

        fun from(reader: Reader): JyutpingReadingLookup {
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
                val jyutping = fields[1]
                if (text.isNotEmpty() && jyutping.isNotEmpty()) {
                    readings.putIfAbsent(text, jyutping)
                }
            }
            return JyutpingReadingLookup(readings)
        }
    }
}
