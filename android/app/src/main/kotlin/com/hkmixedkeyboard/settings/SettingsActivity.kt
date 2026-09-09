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
import android.graphics.drawable.GradientDrawable
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.lifecycle.lifecycleScope
import com.hkmixedkeyboard.BuildConfig
import com.hkmixedkeyboard.decoder.Scheme
import com.hkmixedkeyboard.memory.UserMemoryDatabase
import com.hkmixedkeyboard.ui.CantoneseNotation
import com.hkmixedkeyboard.ui.KeyboardThemeColors
import com.hkmixedkeyboard.ui.toColors
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

        root.addView(header("鍵盤主題"))
        val themeGroup = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val themeRows = mutableMapOf<KeyboardTheme, LinearLayout>()
        var applyingThemeHydration = false
        val themeButtons = mutableMapOf<KeyboardTheme, RadioButton>()
        KeyboardTheme.entries.forEach { theme ->
            val button = RadioButton(this).apply {
                id = android.view.View.generateViewId()
                text = KeyboardThemePreference.label(theme)
            }
            val row = themeOptionRow(theme, button)
            themeRows[theme] = row
            button.setOnCheckedChangeListener { _, checked ->
                if (!checked || applyingThemeHydration) return@setOnCheckedChangeListener
                themeButtons.forEach { (candidate, other) ->
                    if (candidate != theme && other.isChecked) other.isChecked = false
                }
                themeRows.forEach { (candidate, row) ->
                    row.contentDescription = KeyboardThemePreference.accessibilityDescription(
                        candidate, candidate == theme
                    )
                }
                lifecycleScope.launch { KeyboardSettings.setTheme(this@SettingsActivity, theme) }
            }
            row.setOnClickListener {
                if (!button.isChecked) button.isChecked = true
            }
            themeGroup.addView(row)
            themeButtons[theme] = button
        }
        root.addView(themeGroup)

        val simpSwitch = addSwitch(root, "簡體輸出（打繁出簡）", false) { v ->
            lifecycleScope.launch { KeyboardSettings.setSimplifiedOutput(this@SettingsActivity, v) }
        }

        root.addView(header("學習提示"))
        root.addView(label("在候選欄上方顯示所選字詞的注音，幫助學習發音。" +
            "只作顯示，不影響選字或輸出。"))
        val jyutpingHintSwitch = addSwitch(root, "顯示粵拼（粵語注音）", false) { v ->
            lifecycleScope.launch { KeyboardSettings.setJyutpingHint(this@SettingsActivity, v) }
        }
        root.addView(label("粵語注音格式"))
        val notationGroup = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
        var applyingNotationHydration = false
        val notationButtons = CantoneseNotation.entries.associateWith { notation ->
            RadioButton(this).apply {
                id = android.view.View.generateViewId()
                text = CantoneseNotationPreference.label(notation)
                notationGroup.addView(this)
            }
        }
        notationGroup.setOnCheckedChangeListener { _, checkedId ->
            if (applyingNotationHydration) return@setOnCheckedChangeListener
            val notation = notationButtons.entries.firstOrNull { it.value.id == checkedId }?.key
                ?: return@setOnCheckedChangeListener
            lifecycleScope.launch {
                KeyboardSettings.setCantoneseNotation(this@SettingsActivity, notation)
            }
        }
        root.addView(notationGroup)

        val pinyinHintSwitch = addSwitch(root, "顯示拼音（普通話注音）", false) { v ->
            lifecycleScope.launch { KeyboardSettings.setPinyinHint(this@SettingsActivity, v) }
        }

        root.addView(header("直接輸入"))
        root.addView(label("終端機、SSH 及遠端桌面需要逐鍵直接輸入英文，" +
            "不經組字緩衝。自動偵測失效時可在此強制開啟或關閉。"))
        val directGroup = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
        var applyingDirectHydration = false
        val directButtons = DirectInputMode.entries.associateWith { mode ->
            RadioButton(this).apply {
                id = android.view.View.generateViewId()
                text = DirectInputPreference.label(mode)
                directGroup.addView(this)
            }
        }
        directGroup.setOnCheckedChangeListener { _, checkedId ->
            if (applyingDirectHydration) return@setOnCheckedChangeListener
            val mode = directButtons.entries.firstOrNull { it.value.id == checkedId }?.key
                ?: return@setOnCheckedChangeListener
            lifecycleScope.launch { KeyboardSettings.setDirectInput(this@SettingsActivity, mode) }
        }
        root.addView(directGroup)

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
            jyutpingHintSwitch.isChecked = prefs.jyutpingHint
            pinyinHintSwitch.isChecked = prefs.pinyinHint
            applyingNotationHydration = true
            try {
                notationButtons[prefs.cantoneseNotation]?.isChecked = true
            } finally {
                applyingNotationHydration = false
            }
            applyingDirectHydration = true
            try {
                directButtons[prefs.directInput]?.isChecked = true
            } finally {
                applyingDirectHydration = false
            }
            applyingThemeHydration = true
            try {
                themeButtons[prefs.theme]?.isChecked = true
                themeRows.forEach { (theme, row) ->
                    row.contentDescription = KeyboardThemePreference.accessibilityDescription(
                        theme, theme == prefs.theme
                    )
                }
            } finally {
                applyingThemeHydration = false
            }
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

    private fun themeOptionRow(theme: KeyboardTheme, button: RadioButton): LinearLayout {
        val colors = theme.toColors()
        val preview = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(6), dp(6), dp(6), dp(6))
            setBackgroundColor(colors.keyboardBackground)
            layoutParams = LinearLayout.LayoutParams(0, dp(52), 1f).apply {
                setMargins(dp(8), dp(4), 0, dp(4))
            }
        }
        preview.addView(previewKey("A", colors.keyBackground, colors.label))
        preview.addView(previewKey("中", colors.keyBackground, colors.label))
        preview.addView(previewKey("⇧", colors.specialKeyBackground, colors.label))

        val description = TextView(this).apply {
            text = when (theme) {
                KeyboardTheme.DARK -> "現有深色鍵盤配色"
                KeyboardTheme.IOS_LIGHT -> "淺灰背景、白色字元鍵、灰色功能鍵"
            }
            textSize = 12f
            setTextColor(colors.label)
        }
        val details = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(button)
            addView(description)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, dp(4))
            addView(details)
            addView(preview)
            isClickable = true
            isFocusable = true
        }
    }

    private fun previewKey(label: String, background: Int, textColor: Int): TextView =
        TextView(this).apply {
            text = label
            gravity = android.view.Gravity.CENTER
            textSize = 13f
            setTextColor(textColor)
            this.background = GradientDrawable().apply {
                setColor(background)
                cornerRadius = dp(6).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(0, dp(38), 1f).apply {
                setMargins(dp(2), 0, dp(2), 0)
            }
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
