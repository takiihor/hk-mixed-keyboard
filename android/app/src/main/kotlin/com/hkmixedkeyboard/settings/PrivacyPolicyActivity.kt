package com.hkmixedkeyboard.settings

import android.os.Bundle
import android.view.textclassifier.TextClassifier
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
    private lateinit var policyText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()

        val contentPadding = (16 * resources.displayMetrics.density).toInt()
        policyText = TextView(this).apply {
            this.text = getString(com.hkmixedkeyboard.R.string.corpus_loading)
            textSize = 13f
            setTextClassifier(TextClassifier.NO_OP)
            setTextIsSelectable(true)
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(contentPadding, contentPadding, contentPadding, contentPadding)
            addView(policyText)
        }

        setContentView(ScrollView(this).apply { addView(root) })
        SettingsScreenInsets.apply(this, root, contentPadding)
        lifecycleScope.launch {
            val body = withContext(Dispatchers.IO) {
                runCatching {
                    assets.open("legal/privacy_policy.txt").bufferedReader().use { it.readText() }
                }
            }
            policyText.text = body.getOrElse {
                getString(com.hkmixedkeyboard.R.string.privacy_load_failed)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        policyText.setTextIsSelectable(true)
    }

    override fun onPause() {
        // End any pending selection animation before this activity loses its window token.
        policyText.setTextIsSelectable(false)
        super.onPause()
    }
}
