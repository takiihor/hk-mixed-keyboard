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
import androidx.lifecycle.lifecycleScope
import com.hkmixedkeyboard.memory.CustomWordEntity
import com.hkmixedkeyboard.memory.UserMemoryDatabase
import com.hkmixedkeyboard.util.Csv
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.FilterInputStream
import java.io.InputStream
import java.io.IOException

/**
 * Export / import personal dictionary (custom words) as CSV.
 * Format: display,quick_code
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

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }

        root.addView(TextView(this).apply {
            text = "匯出 / 匯入個人詞庫"
            textSize = 20f
            setPadding(0, 0, 0, dp(16))
        })

        root.addView(TextView(this).apply {
            text = "格式: CSV — 每行「詞語,quick碼」\n例: 我哋,qirp"
            textSize = 13f
            setTextColor(0xFF64748B.toInt())
            setPadding(0, 0, 0, dp(16))
        })

        root.addView(Button(this).apply {
            text = "匯出為 CSV"
            setOnClickListener {
                exportLauncher.launch("hk_custom_words.csv")
            }
            layoutParams = lp()
        })

        root.addView(Button(this).apply {
            text = "從 CSV 匯入"
            setOnClickListener { importLauncher.launch("text/*") }
            layoutParams = lp()
        })

        setContentView(root)
    }

    private fun writeExport(uri: Uri) {
        lifecycleScope.launch {
            try {
                // Stream I/O (and the DAO read) on IO, off the UI thread, so a large
                // dictionary can't ANR the activity.
                val count = withContext(Dispatchers.IO) {
                    val words = dao.loadAll()
                    val out = contentResolver.openOutputStream(uri)
                        ?: throw IOException("無法開啟輸出檔案")
                    out.bufferedWriter().use { w ->
                        w.write("display,quick_code\n")
                        words.forEach { word ->
                            w.write("${Csv.escape(word.display)},${Csv.escape(word.quickCode)}\n")
                        }
                    }
                    words.size
                }
                Toast.makeText(this@DictionaryExportActivity,
                    "已匯出 $count 個詞語", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@DictionaryExportActivity,
                    "匯出失敗: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun readImport(uri: Uri) {
        lifecycleScope.launch {
            try {
                val (imported, skipped) = withContext(Dispatchers.IO) {
                    rejectOversizedProviderFile(uri)
                    // Existing (display, quickCode) pairs, so re-importing the same file
                    // doesn't pile up duplicates — the PK autogenerates, so the REPLACE
                    // conflict strategy never actually fires on a content match.
                    val seen = dao.loadAll().mapTo(HashSet()) { it.display to it.quickCode }
                    val toInsert = mutableListOf<CustomWordEntity>()
                    var skip = 0
                    val input = contentResolver.openInputStream(uri)
                        ?: throw IOException("無法開啟檔案")
                    SizeLimitedInputStream(input, CustomWordValidator.MAX_IMPORT_BYTES)
                        .bufferedReader().use { reader ->
                            while (true) {
                                val line = reader.readBoundedImportLine() ?: break
                                if (line.exceedsLimit) { skip++; continue }
                                val trimmed = line.text.trim()
                                if (trimmed.isBlank() || trimmed.startsWith("#")) continue

                                val cols = Csv.split(trimmed)
                                if (cols.size != 2 || isHeader(cols)) {
                                    if (cols.size != 2) skip++
                                    continue
                                }
                                val valid = when (val result = CustomWordValidator.validate(cols[0], cols[1])) {
                                    is CustomWordValidator.Result.Invalid -> {
                                        skip++
                                        continue
                                    }
                                    is CustomWordValidator.Result.Valid -> result
                                }
                                // seen.add returns false when the pair is already present
                                // (on disk or earlier in this file) → skip the duplicate.
                                if (!seen.add(valid.display to valid.quickCode) ||
                                    !CustomWordValidator.canAcceptImportRow(toInsert.size)) {
                                    skip++
                                    continue
                                }
                                toInsert.add(
                                    CustomWordEntity(
                                        display = valid.display,
                                        quickCode = valid.quickCode
                                    )
                                )
                            }
                        }
                    if (toInsert.isNotEmpty()) {
                        dao.insertAll(toInsert)
                        KeyboardSettings.bumpCustomWordsToken(this@DictionaryExportActivity)
                    }
                    toInsert.size to skip
                }
                Toast.makeText(this@DictionaryExportActivity,
                    "已匯入 $imported 個詞語，略過 $skipped 行", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@DictionaryExportActivity,
                    "匯入失敗: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun rejectOversizedProviderFile(uri: Uri) {
        contentResolver.openAssetFileDescriptor(uri, "r")?.use { descriptor ->
            if (descriptor.length > CustomWordValidator.MAX_IMPORT_BYTES) {
                throw IOException("匯入檔案不可超過 1 MiB")
            }
        }
    }

    private fun isHeader(columns: List<String>): Boolean =
        columns[0].equals("display", ignoreCase = true) &&
            columns[1].equals("quick_code", ignoreCase = true)

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
        private val byteLimit: Long
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
            if (bytesRead > byteLimit) throw IOException("匯入檔案不可超過 1 MiB")
        }
    }

    private fun lp() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT).also { it.bottomMargin = dp(8) }

    private fun dp(n: Int) = (n * resources.displayMetrics.density + 0.5f).toInt()
}
