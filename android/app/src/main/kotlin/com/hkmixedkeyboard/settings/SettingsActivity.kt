package com.hkmixedkeyboard.settings

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.EditText
import android.widget.SeekBar
import android.widget.Toast
import android.graphics.drawable.GradientDrawable
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.lifecycle.lifecycleScope
import com.hkmixedkeyboard.BuildConfig
import com.hkmixedkeyboard.decoder.Scheme
import com.hkmixedkeyboard.memory.UserMemoryDatabase
import com.hkmixedkeyboard.ui.KeyboardThemeColors
import com.hkmixedkeyboard.ui.toColors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var enableStatus: TextView
    private lateinit var selectStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()

        val contentPadding = dp(16)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(contentPadding, contentPadding, contentPadding, contentPadding)
        }

        root.addView(appIcon())
        root.addView(header(getString(com.hkmixedkeyboard.R.string.settings_label)))

        enableStatus = label("")
        selectStatus = label("")
        root.addView(enableStatus)
        root.addView(Button(this).apply {
            text = getString(com.hkmixedkeyboard.R.string.setup_enable)
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
            }
        })
        root.addView(selectStatus)
        root.addView(Button(this).apply {
            text = getString(com.hkmixedkeyboard.R.string.setup_select)
            setOnClickListener {
                (getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager)
                    ?.showInputMethodPicker()
            }
        })
        root.addView(label(getString(com.hkmixedkeyboard.R.string.setup_teaching)))
        root.addView(EditText(this).apply {
            hint = getString(com.hkmixedkeyboard.R.string.practice_hint)
            minLines = 2
            contentDescription = getString(com.hkmixedkeyboard.R.string.practice_description)
            disableSystemTextSuggestions()
        })

        root.addView(spacer())
        root.addView(header(getString(com.hkmixedkeyboard.R.string.input_settings)))

        // Switches are created with placeholder state then updated once prefs load
        val rootsSwitch = addSwitch(root, getString(com.hkmixedkeyboard.R.string.show_roots), false) { v ->
            lifecycleScope.launch { KeyboardSettings.setShowRoots(this@SettingsActivity, v) }
        }
        val vibSwitch = addSwitch(root, getString(com.hkmixedkeyboard.R.string.key_vibration), false) { v ->
            lifecycleScope.launch { KeyboardSettings.setVibration(this@SettingsActivity, v) }
        }
        val soundSwitch = addSwitch(root, getString(com.hkmixedkeyboard.R.string.key_sound), false) { v ->
            lifecycleScope.launch { KeyboardSettings.setSound(this@SettingsActivity, v) }
        }
        val schemeGroup = RadioGroup(this).apply {
            orientation = RadioGroup.HORIZONTAL
        }
        val schemeButtons = listOf(
            Scheme.QUICK to getString(com.hkmixedkeyboard.R.string.quick_label),
            Scheme.JYUTPING to getString(com.hkmixedkeyboard.R.string.jyutping_label),
            Scheme.PINYIN to getString(com.hkmixedkeyboard.R.string.pinyin_label)
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
        root.addView(label(getString(com.hkmixedkeyboard.R.string.primary_input)))
        root.addView(schemeGroup)

        root.addView(header(getString(com.hkmixedkeyboard.R.string.keyboard_theme)))
        val themeGroup = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val themeRows = mutableMapOf<KeyboardTheme, LinearLayout>()
        var applyingThemeHydration = false
        val themeButtons = mutableMapOf<KeyboardTheme, RadioButton>()
        KeyboardTheme.entries.forEach { theme ->
            val button = RadioButton(this).apply {
                id = android.view.View.generateViewId()
                text = themeLabel(theme)
            }
            val row = themeOptionRow(theme, button)
            themeRows[theme] = row
            button.setOnCheckedChangeListener { _, checked ->
                if (!checked || applyingThemeHydration) return@setOnCheckedChangeListener
                themeButtons.forEach { (candidate, other) ->
                    if (candidate != theme && other.isChecked) other.isChecked = false
                }
                themeRows.forEach { (candidate, row) ->
                    row.contentDescription = themeAccessibilityDescription(candidate, candidate == theme)
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

        val simpSwitch = addSwitch(root, getString(com.hkmixedkeyboard.R.string.simplified_output), false) { v ->
            lifecycleScope.launch { KeyboardSettings.setSimplifiedOutput(this@SettingsActivity, v) }
        }
        val fuzzySwitch = addSwitch(root, getString(com.hkmixedkeyboard.R.string.pinyin_fuzzy), false) { v ->
            lifecycleScope.launch { KeyboardSettings.setPinyinFuzzy(this@SettingsActivity, v) }
        }

        root.addView(label(getString(com.hkmixedkeyboard.R.string.keyboard_height)))
        val heightSeek = SeekBar(this).apply { max = 35 }
        root.addView(heightSeek)
        val oneHandedGroup = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
        val oneHandedButtons = listOf(
            OneHandedMode.OFF to getString(com.hkmixedkeyboard.R.string.one_handed_off),
            OneHandedMode.LEFT to getString(com.hkmixedkeyboard.R.string.one_handed_left),
            OneHandedMode.RIGHT to getString(com.hkmixedkeyboard.R.string.one_handed_right)
        ).associate { (mode, text) ->
            mode to RadioButton(this).apply {
                id = android.view.View.generateViewId()
                this.text = text
                oneHandedGroup.addView(this)
            }
        }
        oneHandedGroup.setOnCheckedChangeListener { _, id ->
            oneHandedButtons.entries.firstOrNull { it.value.id == id }?.key?.let { mode ->
                lifecycleScope.launch { KeyboardSettings.setOneHandedMode(this@SettingsActivity, mode) }
            }
        }
        root.addView(oneHandedGroup)

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
            fuzzySwitch.isChecked = prefs.pinyinFuzzy
            heightSeek.progress = prefs.keyboardHeightPercent - 85
            oneHandedButtons[prefs.oneHandedMode]?.isChecked = true
            applyingThemeHydration = true
            try {
                themeButtons[prefs.theme]?.isChecked = true
                themeRows.forEach { (theme, row) ->
                    row.contentDescription = themeAccessibilityDescription(theme, theme == prefs.theme)
                }
            } finally {
                applyingThemeHydration = false
            }
        }
        heightSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) lifecycleScope.launch {
                    KeyboardSettings.setKeyboardHeightPercent(this@SettingsActivity, progress + 85)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })

        root.addView(spacer())
        root.addView(header(getString(com.hkmixedkeyboard.R.string.personal_dictionary)))

        root.addView(Button(this).apply {
            text = getString(com.hkmixedkeyboard.R.string.manage_custom_words)
            setOnClickListener {
                startActivity(android.content.Intent(this@SettingsActivity,
                    CustomWordActivity::class.java))
            }
        })

        root.addView(Button(this).apply {
            text = getString(com.hkmixedkeyboard.R.string.dictionary_transfer_label)
            setOnClickListener {
                startActivity(android.content.Intent(this@SettingsActivity,
                    DictionaryExportActivity::class.java))
            }
        })

        root.addView(spacer())
        root.addView(header(getString(com.hkmixedkeyboard.R.string.privacy_heading)))

        root.addView(Button(this).apply {
            text = getString(com.hkmixedkeyboard.R.string.clear_personal_dictionary)
            setOnClickListener { confirmClearPersonalDictionary() }
        })

        root.addView(Button(this).apply {
            text = getString(com.hkmixedkeyboard.R.string.privacy_policy_label)
            setOnClickListener {
                startActivity(android.content.Intent(this@SettingsActivity,
                    PrivacyPolicyActivity::class.java))
            }
        })

        root.addView(spacer())
        root.addView(header(getString(com.hkmixedkeyboard.R.string.about_heading)))

        root.addView(Button(this).apply {
            text = getString(com.hkmixedkeyboard.R.string.licenses_label)
            setOnClickListener {
                startActivity(android.content.Intent(this@SettingsActivity,
                    OpenSourceLicensesActivity::class.java))
            }
        })

        root.addView(spacer())
        root.addView(label(getString(com.hkmixedkeyboard.R.string.version_format,
            BuildConfig.VERSION_NAME, BuildConfig.BUILD_NUMBER)))
        root.addView(label(getString(com.hkmixedkeyboard.R.string.build_time_format, BuildConfig.BUILD_TIME)))
        root.addView(label(getString(com.hkmixedkeyboard.R.string.build_remark_format, BuildConfig.BUILD_REMARK)))

        val scrollRoot = ScrollView(this).apply {
            addView(root)
        }
        setContentView(scrollRoot)
        SettingsScreenInsets.apply(this, root, contentPadding)
    }

    override fun onResume() {
        super.onResume()
        if (!::enableStatus.isInitialized) return
        val inputMethodManager = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
        val enabledPackages = inputMethodManager?.enabledInputMethodList.orEmpty()
            .mapTo(HashSet()) { it.packageName }
        val selectedId = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.DEFAULT_INPUT_METHOD
        )
        val state = SetupStatePolicy.evaluate(packageName, enabledPackages, selectedId)
        enableStatus.setText(if (state.enabled) com.hkmixedkeyboard.R.string.setup_enabled else com.hkmixedkeyboard.R.string.setup_not_enabled)
        selectStatus.setText(if (state.selected) com.hkmixedkeyboard.R.string.setup_selected else com.hkmixedkeyboard.R.string.setup_not_selected)
        if (state.complete) Toast.makeText(this, com.hkmixedkeyboard.R.string.setup_complete, Toast.LENGTH_SHORT).show()
    }

    private fun confirmClearPersonalDictionary() {
        AlertDialog.Builder(this)
            .setTitle(com.hkmixedkeyboard.R.string.clear_title)
            .setMessage(com.hkmixedkeyboard.R.string.clear_irreversible)
            .setNegativeButton(com.hkmixedkeyboard.R.string.cancel, null)
            .setPositiveButton(com.hkmixedkeyboard.R.string.clear) { _, _ -> clearPersonalDictionary() }
            .show()
    }

    private fun clearPersonalDictionary() {
        lifecycleScope.launch {
            val db = UserMemoryDatabase.get(this@SettingsActivity)
            db.dao().clearAll()
            db.customWordDao().clearAll()
            // Signal the running keyboard to flush its live caches immediately.
            KeyboardSettings.bumpMemoryClearToken(this@SettingsActivity)
            KeyboardSettings.bumpCustomWordsToken(this@SettingsActivity)
            Toast.makeText(this@SettingsActivity, com.hkmixedkeyboard.R.string.dictionary_cleared, Toast.LENGTH_SHORT).show()
        }
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
            text = getString(when (theme) {
                KeyboardTheme.DARK -> com.hkmixedkeyboard.R.string.theme_dark_preview
                KeyboardTheme.IOS_LIGHT -> com.hkmixedkeyboard.R.string.theme_light_preview
            })
            textSize = 12f
            setTextColor(SettingsAccessibilityPolicy.previewDescriptionTextColor)
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
            id = android.view.View.generateViewId()
            contentDescription = SettingsAccessibilityPolicy.switchContentDescription(labelText)
            isChecked = default
            setOnCheckedChangeListener { _, checked -> onChange(checked) }
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 12, 0, 12)
            addView(TextView(this@SettingsActivity).apply {
                text = labelText; textSize = 15f
                labelFor = sw.id
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(sw)
            isClickable = true
            isFocusable = true
            setOnClickListener { sw.performClick() }
        }
        parent.addView(row)
        return sw
    }

    private fun themeLabel(theme: KeyboardTheme): String = getString(
        if (theme == KeyboardTheme.DARK) com.hkmixedkeyboard.R.string.theme_dark
        else com.hkmixedkeyboard.R.string.theme_light
    )

    private fun themeAccessibilityDescription(theme: KeyboardTheme, selected: Boolean): String =
        getString(
            com.hkmixedkeyboard.R.string.theme_state,
            themeLabel(theme),
            getString(if (selected) com.hkmixedkeyboard.R.string.selected
                else com.hkmixedkeyboard.R.string.not_selected)
        )
}

/** Accessibility values for the Settings surface, independent of keyboard palettes. */
object SettingsAccessibilityPolicy {
    const val previewDescriptionTextColor = 0xFF475569.toInt()

    fun switchContentDescription(label: String): String = label
}
