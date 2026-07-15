package com.hkmixedkeyboard.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.hkmixedkeyboard.decoder.Scheme

// Restore the DataStore extension property that was accidentally removed
val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "hk_keyboard_settings")

object Keys {
    val SHOW_ROOTS     = booleanPreferencesKey("show_roots")
    val VIBRATION      = booleanPreferencesKey("vibration")
    val SOUND          = booleanPreferencesKey("sound")
    val JYUTPING_PRIMARY = booleanPreferencesKey("jyutping_primary")
    val INPUT_SCHEME = stringPreferencesKey("input_scheme")
    // 簡體輸出: convert committed Chinese to Simplified at the output boundary.
    val SIMPLIFIED_OUTPUT = booleanPreferencesKey("simplified_output")
    // Bumped whenever the user clears their dictionary, so the running IME can flush
    // its in-memory caches live instead of only on the next keyboard restart.
    val MEMORY_CLEAR_TOKEN = longPreferencesKey("memory_clear_token")
    // Bumped whenever custom words change. The IME reloads that table only on this
    // signal instead of querying Room every time the keyboard opens.
    val CUSTOM_WORDS_TOKEN = longPreferencesKey("custom_words_token")
    val KEYBOARD_THEME = stringPreferencesKey("keyboard_theme")
    val PINYIN_FUZZY = booleanPreferencesKey("pinyin_fuzzy")
    val KEYBOARD_HEIGHT_PERCENT = intPreferencesKey("keyboard_height_percent")
    val ONE_HANDED_MODE = stringPreferencesKey("one_handed_mode")
}

enum class KeyboardTheme {
    DARK,
    IOS_LIGHT
}

enum class OneHandedMode { OFF, LEFT, RIGHT }

class ChangeTokenTracker {
    private var lastToken: Long? = null

    fun hasChanged(token: Long): Boolean {
        val previous = lastToken
        lastToken = token
        return previous != null && previous != token
    }
}

data class KeyboardPrefs(
    val showRoots: Boolean = true,
    val vibration: Boolean = true,
    val sound: Boolean = false,
    val inputScheme: Scheme = Scheme.QUICK,
    val simplifiedOutput: Boolean = false,
    val memoryClearToken: Long = 0L,
    val customWordsToken: Long = 0L,
    val theme: KeyboardTheme = KeyboardTheme.DARK,
    val pinyinFuzzy: Boolean = false,
    val keyboardHeightPercent: Int = 100,
    val oneHandedMode: OneHandedMode = OneHandedMode.OFF
)

object KeyboardSettings {
    fun flow(ctx: Context): Flow<KeyboardPrefs> =
        ctx.settingsDataStore.data.map { p ->
            KeyboardPrefs(
                showRoots  = p[Keys.SHOW_ROOTS]  ?: true,
                vibration  = p[Keys.VIBRATION]   ?: true,
                sound      = p[Keys.SOUND]        ?: false,
                inputScheme = InputSchemePreference.resolve(
                    p[Keys.INPUT_SCHEME],
                    p[Keys.JYUTPING_PRIMARY]
                ),
                simplifiedOutput = p[Keys.SIMPLIFIED_OUTPUT] ?: false,
                memoryClearToken = p[Keys.MEMORY_CLEAR_TOKEN] ?: 0L,
                customWordsToken = p[Keys.CUSTOM_WORDS_TOKEN] ?: 0L,
                theme = KeyboardThemePreference.resolve(p[Keys.KEYBOARD_THEME]),
                pinyinFuzzy = p[Keys.PINYIN_FUZZY] ?: false,
                keyboardHeightPercent = (p[Keys.KEYBOARD_HEIGHT_PERCENT] ?: 100)
                    .coerceIn(85, 120),
                oneHandedMode = runCatching {
                    OneHandedMode.valueOf(p[Keys.ONE_HANDED_MODE] ?: OneHandedMode.OFF.name)
                }.getOrDefault(OneHandedMode.OFF)
            )
        }

    suspend fun setShowRoots(ctx: Context, v: Boolean) =
        ctx.settingsDataStore.edit { it[Keys.SHOW_ROOTS] = v }

    suspend fun setVibration(ctx: Context, v: Boolean) =
        ctx.settingsDataStore.edit { it[Keys.VIBRATION] = v }

    suspend fun setSound(ctx: Context, v: Boolean) =
        ctx.settingsDataStore.edit { it[Keys.SOUND] = v }

    suspend fun setInputScheme(ctx: Context, scheme: Scheme) =
        ctx.settingsDataStore.edit { it[Keys.INPUT_SCHEME] = InputSchemePreference.serialize(scheme) }

    suspend fun migrateLegacyInputScheme(ctx: Context) =
        ctx.settingsDataStore.edit { preferences ->
            InputSchemePreference.migrationValue(
                preferences[Keys.INPUT_SCHEME],
                preferences[Keys.JYUTPING_PRIMARY]
            )?.let { preferences[Keys.INPUT_SCHEME] = it }
        }

    suspend fun setSimplifiedOutput(ctx: Context, v: Boolean) =
        ctx.settingsDataStore.edit { it[Keys.SIMPLIFIED_OUTPUT] = v }

    suspend fun setTheme(ctx: Context, theme: KeyboardTheme) =
        ctx.settingsDataStore.edit { it[Keys.KEYBOARD_THEME] = KeyboardThemePreference.serialize(theme) }

    suspend fun setPinyinFuzzy(ctx: Context, enabled: Boolean) =
        ctx.settingsDataStore.edit { it[Keys.PINYIN_FUZZY] = enabled }

    suspend fun setKeyboardHeightPercent(ctx: Context, percent: Int) =
        ctx.settingsDataStore.edit { it[Keys.KEYBOARD_HEIGHT_PERCENT] = percent.coerceIn(85, 120) }

    suspend fun setOneHandedMode(ctx: Context, mode: OneHandedMode) =
        ctx.settingsDataStore.edit { it[Keys.ONE_HANDED_MODE] = mode.name }

    // Monotonic counters rather than timestamps: two bumps within the same
    // millisecond would produce equal tokens and ChangeTokenTracker would miss the
    // second change.
    suspend fun bumpMemoryClearToken(ctx: Context) =
        ctx.settingsDataStore.edit { it[Keys.MEMORY_CLEAR_TOKEN] = (it[Keys.MEMORY_CLEAR_TOKEN] ?: 0L) + 1 }

    suspend fun bumpCustomWordsToken(ctx: Context) =
        ctx.settingsDataStore.edit { it[Keys.CUSTOM_WORDS_TOKEN] = (it[Keys.CUSTOM_WORDS_TOKEN] ?: 0L) + 1 }
}

/** Pure policy for parsing the persisted keyboard theme. */
object KeyboardThemePreference {
    fun resolve(stored: String?): KeyboardTheme = when (stored) {
        "ios_light" -> KeyboardTheme.IOS_LIGHT
        "dark" -> KeyboardTheme.DARK
        else -> KeyboardTheme.DARK
    }

    fun serialize(theme: KeyboardTheme): String = when (theme) {
        KeyboardTheme.DARK -> "dark"
        KeyboardTheme.IOS_LIGHT -> "ios_light"
    }

    fun label(theme: KeyboardTheme): String = when (theme) {
        KeyboardTheme.DARK -> "深色"
        KeyboardTheme.IOS_LIGHT -> "淺色（iPhone 風格）"
    }

    fun accessibilityDescription(theme: KeyboardTheme, selected: Boolean): String = when {
        theme == KeyboardTheme.DARK && selected -> "深色鍵盤主題，已選取"
        theme == KeyboardTheme.DARK -> "深色鍵盤主題，未選取"
        selected -> "淺色 iPhone 風格鍵盤主題，已選取"
        else -> "淺色 iPhone 風格鍵盤主題，未選取"
    }
}

/** Pure policy for parsing the persisted scheme and migrating the legacy boolean. */
object InputSchemePreference {
    fun resolve(stored: String?, legacyJyutpingPrimary: Boolean?): Scheme = when (stored) {
        null -> if (legacyJyutpingPrimary == true) Scheme.JYUTPING else Scheme.QUICK
        "quick" -> Scheme.QUICK
        "jyutping" -> Scheme.JYUTPING
        "pinyin" -> Scheme.PINYIN
        else -> Scheme.QUICK
    }

    fun serialize(scheme: Scheme): String = when (scheme) {
        Scheme.JYUTPING -> "jyutping"
        Scheme.PINYIN -> "pinyin"
        else -> "quick"
    }

    fun migrationValue(stored: String?, legacyJyutpingPrimary: Boolean?): String? =
        if (stored != null) null else serialize(resolve(null, legacyJyutpingPrimary))

    fun next(scheme: Scheme): Scheme = when (scheme) {
        Scheme.QUICK -> Scheme.JYUTPING
        Scheme.JYUTPING -> Scheme.PINYIN
        else -> Scheme.QUICK
    }

    fun name(scheme: Scheme): String = when (scheme) {
        Scheme.JYUTPING -> "粵拼"
        Scheme.PINYIN -> "拼音"
        else -> "速成"
    }

    fun shortLabel(scheme: Scheme): String = when (scheme) {
        Scheme.JYUTPING -> "粵"
        Scheme.PINYIN -> "拼"
        else -> "速"
    }

    fun showsCangjieRoots(scheme: Scheme): Boolean =
        scheme == Scheme.QUICK || scheme == Scheme.CANGJIE

    fun isRomanization(scheme: Scheme): Boolean =
        scheme == Scheme.JYUTPING || scheme == Scheme.PINYIN
}
