package com.hkmixedkeyboard.settings

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.lifecycle.lifecycleScope
import com.hkmixedkeyboard.BuildConfig
import com.hkmixedkeyboard.decoder.Scheme
import com.hkmixedkeyboard.memory.UserMemoryDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()

        val contentPadding = dp(16)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(contentPadding, contentPadding, contentPadding, contentPadding)
        }

        root.addView(appIcon())
        root.addView(header("HK Mixed Keyboard 設定"))

        root.addView(Button(this).apply {
            text = "在設定中啟用此鍵盤"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
            }
        })

        root.addView(spacer())
        root.addView(header("輸入設定"))

        // Switches are created with placeholder state then updated once prefs load
        val rootsSwitch = addSwitch(root, "顯示倉頡字根", false) { v ->
            lifecycleScope.launch { KeyboardSettings.setShowRoots(this@SettingsActivity, v) }
        }
        val vibSwitch = addSwitch(root, "按鍵震動", false) { v ->
            lifecycleScope.launch { KeyboardSettings.setVibration(this@SettingsActivity, v) }
        }
        val soundSwitch = addSwitch(root, "按鍵聲音", false) { v ->
            lifecycleScope.launch { KeyboardSettings.setSound(this@SettingsActivity, v) }
        }
        val schemeGroup = RadioGroup(this).apply {
            orientation = RadioGroup.HORIZONTAL
        }
        val schemeButtons = listOf(
            Scheme.QUICK to "速成",
            Scheme.JYUTPING to "粵拼",
            Scheme.PINYIN to "拼音"
        ).associate { (scheme, label) ->
            scheme to RadioButton(this).apply {
                id = android.view.View.generateViewId()
                text = label
                schemeGroup.addView(this)
            }
        }
        val schemeSelection = InputSchemeSelectionCoordinator()
        var applyingHydration = false
        schemeGroup.setOnCheckedChangeListener { _, checkedId ->
            if (applyingHydration) return@setOnCheckedChangeListener
            val scheme = schemeButtons.entries.firstOrNull { it.value.id == checkedId }?.key
                ?: return@setOnCheckedChangeListener
            when (val action = schemeSelection.onUserSelection(scheme)) {
                is InputSchemeSelectionCoordinator.Action.Persist -> lifecycleScope.launch {
                    KeyboardSettings.setInputScheme(this@SettingsActivity, action.scheme)
                }
                else -> Unit
            }
        }
        root.addView(label("主要輸入法"))
        root.addView(schemeGroup)
        val simpSwitch = addSwitch(root, "簡體輸出（打繁出簡）", false) { v ->
            lifecycleScope.launch { KeyboardSettings.setSimplifiedOutput(this@SettingsActivity, v) }
        }

        // Load current prefs and apply to switches
        lifecycleScope.launch {
            try {
                KeyboardSettings.migrateLegacyInputScheme(this@SettingsActivity)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("SettingsActivity", "Legacy scheme migration failed; using fallback", e)
            }
            val prefs = KeyboardSettings.flow(this@SettingsActivity).first()
            rootsSwitch.isChecked = prefs.showRoots
            vibSwitch.isChecked   = prefs.vibration
            soundSwitch.isChecked = prefs.sound
            when (val action = schemeSelection.onHydrated(prefs.inputScheme)) {
                is InputSchemeSelectionCoordinator.Action.ApplyToUi -> {
                    applyingHydration = true
                    try {
                        schemeButtons[action.scheme]?.isChecked = true
                    } finally {
                        applyingHydration = false
                    }
                }
                else -> Unit
            }
            simpSwitch.isChecked  = prefs.simplifiedOutput
        }

        root.addView(spacer())
        root.addView(header("個人詞庫"))

        root.addView(Button(this).apply {
            text = "管理自訂詞語"
            setOnClickListener {
                startActivity(android.content.Intent(this@SettingsActivity,
                    CustomWordActivity::class.java))
            }
        })

        root.addView(Button(this).apply {
            text = "匯出 / 匯入詞庫"
            setOnClickListener {
                startActivity(android.content.Intent(this@SettingsActivity,
                    DictionaryExportActivity::class.java))
            }
        })

        root.addView(spacer())
        root.addView(header("私隱"))

        root.addView(Button(this).apply {
            text = "清除所有個人詞庫"
            setOnClickListener {
                lifecycleScope.launch {
                    val db = UserMemoryDatabase.get(this@SettingsActivity)
                    db.dao().clearAll()
                    db.customWordDao().clearAll()
                    // Signal the running keyboard to flush its live caches immediately.
                    KeyboardSettings.bumpMemoryClearToken(this@SettingsActivity)
                    KeyboardSettings.bumpCustomWordsToken(this@SettingsActivity)
                    Toast.makeText(this@SettingsActivity, "詞庫已清除", Toast.LENGTH_SHORT).show()
                }
            }
        })

        root.addView(Button(this).apply {
            text = getString(com.hkmixedkeyboard.R.string.privacy_policy_label)
            setOnClickListener {
                startActivity(android.content.Intent(this@SettingsActivity,
                    PrivacyPolicyActivity::class.java))
            }
        })

        root.addView(spacer())
        root.addView(header("關於"))

        root.addView(Button(this).apply {
            text = getString(com.hkmixedkeyboard.R.string.licenses_label)
            setOnClickListener {
                startActivity(android.content.Intent(this@SettingsActivity,
                    OpenSourceLicensesActivity::class.java))
            }
        })

        root.addView(spacer())
        root.addView(label("版本: ${BuildConfig.VERSION_NAME} (build ${BuildConfig.BUILD_NUMBER})"))
        root.addView(label("建置時間: ${BuildConfig.BUILD_TIME}"))
        root.addView(label("備註: ${BuildConfig.BUILD_REMARK}"))

        val scrollRoot = ScrollView(this).apply {
            addView(root)
        }
        setContentView(scrollRoot)
        applySystemBarInsets(root, contentPadding)
    }

    private fun applySystemBarInsets(root: android.view.View, contentPadding: Int) {
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            v.setPadding(
                contentPadding,
                contentPadding + bars.top,
                contentPadding,
                contentPadding + bars.bottom
            )
            insets
        }
        androidx.core.view.ViewCompat.requestApplyInsets(root)
    }

    // App profile picture (the launcher art), centred at the top of Settings.
    private fun appIcon(): android.widget.ImageView {
        val size = (96 * resources.displayMetrics.density).toInt()
        return android.widget.ImageView(this).apply {
            setImageResource(com.hkmixedkeyboard.R.mipmap.ic_launcher)
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            contentDescription = getString(com.hkmixedkeyboard.R.string.app_name)
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                gravity = android.view.Gravity.CENTER_HORIZONTAL
                bottomMargin = 24
            }
        }
    }

    private fun header(text: String) = TextView(this).apply {
        this.text = text; textSize = 18f
        setPadding(0, 16, 0, 8)
    }

    private fun label(text: String) = TextView(this).apply {
        this.text = text; textSize = 13f
        setPadding(0, 4, 0, 4)
    }

    private fun spacer() = android.view.View(this).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 1
        ).also { it.setMargins(0, 16, 0, 16) }
        setBackgroundColor(0xFFE2E8F0.toInt())
    }

    private fun dp(n: Int) = (n * resources.displayMetrics.density + 0.5f).toInt()

    private fun addSwitch(
        parent: LinearLayout,
        labelText: String,
        default: Boolean,
        onChange: (Boolean) -> Unit
    ): SwitchCompat {
        val sw = SwitchCompat(this).apply {
            isChecked = default
            setOnCheckedChangeListener { _, checked -> onChange(checked) }
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 12, 0, 12)
            addView(TextView(this@SettingsActivity).apply {
                text = labelText; textSize = 15f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(sw)
        }
        parent.addView(row)
        return sw
    }
}
