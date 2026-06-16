package com.hkmixedkeyboard.settings

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
import kotlinx.coroutines.launch

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
            val words = dao.loadAll()
            try {
                contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { w ->
                    w.write("display,quick_code\n")
                    words.forEach { word ->
                        w.write("${Csv.escape(word.display)},${Csv.escape(word.quickCode)}\n")
                    }
                }
                runOnUiThread {
                    Toast.makeText(this@DictionaryExportActivity,
                        "已匯出 ${words.size} 個詞語", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@DictionaryExportActivity,
                        "匯出失敗: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun readImport(uri: Uri) {
        lifecycleScope.launch {
            val toInsert = mutableListOf<CustomWordEntity>()
            var skipped = 0
            try {
                contentResolver.openInputStream(uri)?.bufferedReader()?.forEachLine { line ->
                    val trimmed = line.trim()
                    if (trimmed.isBlank() || trimmed.startsWith("#") || trimmed.startsWith("display"))
                        return@forEachLine
                    val cols = Csv.split(trimmed)
                    if (cols.size >= 2) {
                        val display = cols[0].trim()
                        val code    = cols[1].trim().lowercase()
                        if (display.isNotBlank() && code.isNotBlank())
                            toInsert.add(CustomWordEntity(display = display, quickCode = code))
                        else skipped++
                    } else skipped++
                }
                toInsert.forEach { dao.insert(it) }
                val imported = toInsert.size
                runOnUiThread {
                    Toast.makeText(this@DictionaryExportActivity,
                        "已匯入 $imported 個詞語，略過 $skipped 行", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@DictionaryExportActivity,
                        "匯入失敗: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun lp() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT).also { it.bottomMargin = dp(8) }

    private fun dp(n: Int) = (n * resources.displayMetrics.density + 0.5f).toInt()
}
