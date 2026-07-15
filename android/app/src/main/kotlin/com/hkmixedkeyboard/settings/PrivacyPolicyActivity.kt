package com.hkmixedkeyboard.settings

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Displays the privacy policy (assets/legal/privacy_policy.txt) inside the app.
 * Google Play requires a privacy policy for any input-method app; this in-app
 * copy mirrors the hosted PRIVACY_POLICY.md and is reachable from Settings.
 */
class PrivacyPolicyActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()

        val contentPadding = (16 * resources.displayMetrics.density).toInt()
        val text = TextView(this).apply {
            this.text = getString(com.hkmixedkeyboard.R.string.corpus_loading)
            textSize = 13f
            setTextIsSelectable(true)
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(contentPadding, contentPadding, contentPadding, contentPadding)
            addView(text)
        }

        setContentView(ScrollView(this).apply { addView(root) })
        SettingsScreenInsets.apply(this, root, contentPadding)
        lifecycleScope.launch {
            val body = withContext(Dispatchers.IO) {
                runCatching {
                    assets.open("legal/privacy_policy.txt").bufferedReader().use { it.readText() }
                }
            }
            text.text = body.getOrElse {
                getString(com.hkmixedkeyboard.R.string.privacy_load_failed)
            }
        }
    }
}
