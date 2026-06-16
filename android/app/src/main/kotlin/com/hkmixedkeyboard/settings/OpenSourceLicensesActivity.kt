package com.hkmixedkeyboard.settings

import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Displays the open-source data attributions (assets/licenses/NOTICE.txt) for the
 * bundled dictionary / frequency data (RIME Cangjie5, RIME Essay, CC-CEDICT,
 * rime-cantonese). Required to satisfy the GPL / CC-BY-SA / CC-BY / ODbL
 * attribution terms of those data sets.
 */
class OpenSourceLicensesActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val notice = runCatching {
            assets.open("licenses/NOTICE.txt").bufferedReader().use { it.readText() }
        }.getOrElse { "Failed to load notices: ${it.message}" }

        val text = TextView(this).apply {
            this.text = notice
            textSize = 12f
            typeface = android.graphics.Typeface.MONOSPACE
            setTextIsSelectable(true)
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
        }

        setContentView(ScrollView(this).apply { addView(text) })
    }
}
