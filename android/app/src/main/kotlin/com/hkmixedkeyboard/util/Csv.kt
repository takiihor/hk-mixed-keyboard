package com.hkmixedkeyboard.util

/** Minimal RFC-4180 CSV helpers shared by corpus loading and dictionary import/export. */
object Csv {

    /**
     * Split one CSV line on commas while respecting double-quoted fields, so a field
     * that legitimately contains a comma (wrapped in quotes) isn't split. A doubled
     * "" inside a quoted field is an escaped quote. Fields are trimmed.
     */
    fun split(line: String): List<String> {
        val cols = ArrayList<String>(6)
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                inQuotes && c == '"' && i + 1 < line.length && line[i + 1] == '"' -> {
                    sb.append('"'); i++
                }
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> { cols.add(sb.toString().trim()); sb.setLength(0) }
                else -> sb.append(c)
            }
            i++
        }
        cols.add(sb.toString().trim())
        return cols
    }

    /** Quote a field for output if it contains a comma, quote, or newline. */
    fun escape(field: String): String =
        if (field.any { it == ',' || it == '"' || it == '\n' || it == '\r' })
            "\"" + field.replace("\"", "\"\"") + "\""
        else field
}
