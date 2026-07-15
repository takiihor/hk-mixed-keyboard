package com.hkmixedkeyboard.settings

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.hkmixedkeyboard.memory.CustomWordEntity
import com.hkmixedkeyboard.decoder.Scheme
import com.hkmixedkeyboard.memory.UserMemoryDatabase
import com.hkmixedkeyboard.util.Csv
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.FilterInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.IOException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/**
 * Export / import personal dictionary (custom words) as CSV.
 * Format: scheme,display,code. Legacy display,quick_code rows remain supported.
 */
class DictionaryExportActivity : AppCompatActivity() {

    private val dao by lazy { UserMemoryDatabase.get(this).customWordDao() }

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        uri?.let { writeExport(it) }
    }

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { readImport(it) }
    }

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()

        val contentPadding = dp(16)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(contentPadding, contentPadding, contentPadding, contentPadding)
        }

        root.addView(TextView(this).apply {
            text = getString(com.hkmixedkeyboard.R.string.dictionary_transfer_title)
            textSize = 20f
            setPadding(0, 0, 0, dp(16))
        })

        root.addView(TextView(this).apply {
            text = getString(com.hkmixedkeyboard.R.string.dictionary_format)
            textSize = 13f
            setTextColor(0xFF64748B.toInt())
            setPadding(0, 0, 0, dp(16))
        })

        root.addView(Button(this).apply {
            text = getString(com.hkmixedkeyboard.R.string.export_csv)
            setOnClickListener {
                exportLauncher.launch("hk_custom_words.csv")
            }
            layoutParams = lp()
        })

        root.addView(Button(this).apply {
            text = getString(com.hkmixedkeyboard.R.string.import_csv)
            setOnClickListener {
                AlertDialog.Builder(this@DictionaryExportActivity)
                    .setTitle(com.hkmixedkeyboard.R.string.import_confirm_title)
                    .setMessage(com.hkmixedkeyboard.R.string.import_atomic_notice)
                    .setNegativeButton(com.hkmixedkeyboard.R.string.cancel, null)
                    .setPositiveButton(com.hkmixedkeyboard.R.string.choose_file) { _, _ ->
                        importLauncher.launch("text/*")
                    }
                    .show()
            }
            layoutParams = lp()
        })

        setContentView(root)
        SettingsScreenInsets.apply(this, root, contentPadding)
    }

    private fun writeExport(uri: Uri) {
        lifecycleScope.launch {
            try {
                // Stream I/O (and the DAO read) on IO, off the UI thread, so a large
                // dictionary can't ANR the activity.
                val count = withContext(Dispatchers.IO) {
                    val words = dao.loadAll()
                    val out = contentResolver.openOutputStream(uri)
                        ?: throw IOException(getString(com.hkmixedkeyboard.R.string.open_output_failed))
                    out.bufferedWriter().use { w ->
                        w.write("scheme,display,code\n")
                        words.forEach { word ->
                            w.write(
                                "${Csv.escape(word.scheme)},${Csv.escape(word.display)}," +
                                    "${Csv.escape(word.quickCode)}\n"
                            )
                        }
                    }
                    words.size
                }
                Toast.makeText(this@DictionaryExportActivity,
                    getString(com.hkmixedkeyboard.R.string.export_success, count), Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@DictionaryExportActivity,
                    getString(com.hkmixedkeyboard.R.string.export_failed, e.message), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun readImport(uri: Uri) {
        lifecycleScope.launch {
            try {
                val (imported, duplicates) = withContext(Dispatchers.IO) {
                    rejectOversizedProviderFile(uri)
                    // Existing (display, quickCode) pairs, so re-importing the same file
                    // doesn't pile up duplicates — the PK autogenerates, so the REPLACE
                    // conflict strategy never actually fires on a content match.
                    val seen = dao.loadAll().mapTo(HashSet()) {
                        Triple(it.scheme, it.display, it.quickCode)
                    }
                    val toInsert = mutableListOf<CustomWordEntity>()
                    var duplicateCount = 0
                    var dataRow = 0
                    val input = contentResolver.openInputStream(uri)
                        ?: throw IOException(getString(com.hkmixedkeyboard.R.string.open_input_failed))
                    val decoder = StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                    InputStreamReader(
                        SizeLimitedInputStream(
                            input,
                            CustomWordValidator.MAX_IMPORT_BYTES,
                            getString(com.hkmixedkeyboard.R.string.import_too_large)
                        ),
                        decoder
                    ).buffered().use { reader ->
                            while (true) {
                                val line = reader.readBoundedImportLine() ?: break
                                if (line.exceedsLimit) {
                                    throw IOException(getString(com.hkmixedkeyboard.R.string.import_line_too_long))
                                }
                                val trimmed = line.text.trim()
                                if (trimmed.isBlank() || trimmed.startsWith("#")) continue

                                val cols = Csv.split(trimmed)
                                if (isHeader(cols)) continue
                                dataRow++
                                if (!CustomWordValidator.canAcceptImportRow(dataRow - 1)) {
                                    throw IOException(getString(
                                        com.hkmixedkeyboard.R.string.import_too_many_rows,
                                        CustomWordValidator.MAX_IMPORT_ROWS
                                    ))
                                }
                                val (scheme, display, code) = when (cols.size) {
                                    2 -> Triple(Scheme.QUICK, cols[0], cols[1])
                                    3 -> Triple(
                                        parseScheme(cols[0])
                                            ?: throw IOException(getString(
                                                com.hkmixedkeyboard.R.string.import_bad_scheme,
                                                dataRow
                                            )),
                                        cols[1],
                                        cols[2]
                                    )
                                    else -> throw IOException(getString(
                                        com.hkmixedkeyboard.R.string.import_bad_columns,
                                        dataRow
                                    ))
                                }
                                if (display.trimStart().firstOrNull()
                                        ?.let(FORMULA_PREFIXES::contains) == true) {
                                    throw IOException(getString(
                                        com.hkmixedkeyboard.R.string.import_unsafe_text,
                                        dataRow
                                    ))
                                }
                                val valid = when (val result =
                                    CustomWordValidator.validate(scheme, display, code)) {
                                    is CustomWordValidator.Result.Invalid -> {
                                        throw IOException(getString(
                                            com.hkmixedkeyboard.R.string.import_row_error,
                                            dataRow,
                                            getString(validationMessageResource(result.error))
                                        ))
                                    }
                                    is CustomWordValidator.Result.Valid -> result
                                }
                                val identity = Triple(
                                    valid.scheme.name,
                                    valid.display,
                                    valid.quickCode
                                )
                                if (!seen.add(identity)) {
                                    duplicateCount++
                                    continue
                                }
                                toInsert.add(
                                    CustomWordEntity(
                                        display = valid.display,
                                        quickCode = valid.quickCode,
                                        scheme = valid.scheme.name
                                    )
                                )
                            }
                        }
                    if (toInsert.isNotEmpty()) {
                        dao.insertAll(toInsert)
                        KeyboardSettings.bumpCustomWordsToken(this@DictionaryExportActivity)
                    }
                    toInsert.size to duplicateCount
                }
                Toast.makeText(this@DictionaryExportActivity,
                    getString(com.hkmixedkeyboard.R.string.import_success, imported, duplicates), Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@DictionaryExportActivity,
                    getString(com.hkmixedkeyboard.R.string.import_failed, e.message), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun rejectOversizedProviderFile(uri: Uri) {
        contentResolver.openAssetFileDescriptor(uri, "r")?.use { descriptor ->
            if (descriptor.length > CustomWordValidator.MAX_IMPORT_BYTES) {
                throw IOException(getString(com.hkmixedkeyboard.R.string.import_too_large))
            }
        }
    }

    private fun isHeader(columns: List<String>): Boolean = when (columns.size) {
        2 -> columns[0].equals("display", ignoreCase = true) &&
            columns[1].equals("quick_code", ignoreCase = true)
        3 -> columns[0].equals("scheme", ignoreCase = true) &&
            columns[1].equals("display", ignoreCase = true) &&
            columns[2].equals("code", ignoreCase = true)
        else -> false
    }

    private fun parseScheme(raw: String): Scheme? = when (raw.trim().uppercase()) {
        "QUICK", "速成" -> Scheme.QUICK
        "JYUTPING", "粵拼" -> Scheme.JYUTPING
        "PINYIN", "拼音", "普通話拼音" -> Scheme.PINYIN
        else -> null
    }

    private data class ImportLine(val text: String, val exceedsLimit: Boolean)

    private fun BufferedReader.readBoundedImportLine(): ImportLine? {
        val line = StringBuilder()
        var exceedsLimit = false
        var sawCharacter = false
        while (true) {
            val next = read()
            if (next == -1) {
                if (!sawCharacter) return null
                break
            }
            sawCharacter = true
            if (next == '\n'.code) break
            if (next == '\r'.code) continue
            if (line.length < CustomWordValidator.MAX_IMPORT_LINE_CHARACTERS) {
                line.append(next.toChar())
            } else {
                exceedsLimit = true
            }
        }
        return ImportLine(line.toString(), exceedsLimit)
    }

    private class SizeLimitedInputStream(
        input: InputStream,
        private val byteLimit: Long,
        private val overflowMessage: String
    ) : FilterInputStream(input) {
        private var bytesRead = 0L

        override fun read(): Int = super.read().also { byte ->
            if (byte != -1) recordBytes(1)
        }

        override fun read(bytes: ByteArray, offset: Int, length: Int): Int =
            super.read(bytes, offset, length).also { count ->
                if (count > 0) recordBytes(count)
            }

        private fun recordBytes(count: Int) {
            bytesRead += count
            if (bytesRead > byteLimit) throw IOException(overflowMessage)
        }
    }

    private fun lp() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT).also { it.bottomMargin = dp(8) }

    private fun dp(n: Int) = (n * resources.displayMetrics.density + 0.5f).toInt()

    private companion object {
        val FORMULA_PREFIXES = setOf('=', '+', '-', '@')
    }
}

internal fun validationMessageResource(error: CustomWordValidator.Error): Int = when (error) {
    CustomWordValidator.Error.MISSING -> com.hkmixedkeyboard.R.string.custom_error_missing
    CustomWordValidator.Error.LINE_BREAK -> com.hkmixedkeyboard.R.string.custom_error_line_break
    CustomWordValidator.Error.DISPLAY_TOO_LONG -> com.hkmixedkeyboard.R.string.custom_error_display_length
    CustomWordValidator.Error.QUICK_CODE -> com.hkmixedkeyboard.R.string.custom_error_quick
    CustomWordValidator.Error.JYUTPING_CODE -> com.hkmixedkeyboard.R.string.custom_error_jyutping
    CustomWordValidator.Error.PINYIN_CODE -> com.hkmixedkeyboard.R.string.custom_error_pinyin
    CustomWordValidator.Error.SCHEME -> com.hkmixedkeyboard.R.string.custom_error_scheme
}
