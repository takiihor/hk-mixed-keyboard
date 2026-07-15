package com.hkmixedkeyboard.settings

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Displays bundled open-source data notices and license texts.
 */
class OpenSourceLicensesActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()

        val notice = runCatching {
            val files = listOf(
                "NOTICE.txt",
                "GPL-3.0.txt",
                "CC-BY-SA-4.0.txt",
                "CC-BY-4.0.txt",
                "ODbL-1.0.txt",
                "Apache-2.0.txt"
            )
            files.joinToString(separator = "\n\n\n") { file ->
                assets.open("licenses/$file").bufferedReader().use { it.readText() }
            }
        }.getOrElse { "Failed to load notices: ${it.message}" }

        val contentPadding = (16 * resources.displayMetrics.density).toInt()
        val text = TextView(this).apply {
            this.text = notice
            textSize = 12f
            typeface = android.graphics.Typeface.MONOSPACE
            setTextIsSelectable(true)
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(contentPadding, contentPadding, contentPadding, contentPadding)
            addView(text)
        }

        setContentView(ScrollView(this).apply { addView(root) })
        SettingsScreenInsets.apply(this, root, contentPadding)
    }
}
