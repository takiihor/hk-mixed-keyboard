package com.hkmixedkeyboard.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Restore the DataStore extension property that was accidentally removed
val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "hk_keyboard_settings")

object Keys {
    val SHOW_ROOTS     = booleanPreferencesKey("show_roots")
    val VIBRATION      = booleanPreferencesKey("vibration")
    val SOUND          = booleanPreferencesKey("sound")
    val JYUTPING_PRIMARY = booleanPreferencesKey("jyutping_primary")
    // 簡體輸出: convert committed Chinese to Simplified at the output boundary.
    val SIMPLIFIED_OUTPUT = booleanPreferencesKey("simplified_output")
    // Bumped whenever the user clears their dictionary, so the running IME can flush
    // its in-memory caches live instead of only on the next keyboard restart.
    val MEMORY_CLEAR_TOKEN = longPreferencesKey("memory_clear_token")
    // Bumped whenever custom words change. The IME reloads that table only on this
    // signal instead of querying Room every time the keyboard opens.
    val CUSTOM_WORDS_TOKEN = longPreferencesKey("custom_words_token")
}

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
    val jyutpingPrimary: Boolean = false,
    val simplifiedOutput: Boolean = false,
    val memoryClearToken: Long = 0L,
    val customWordsToken: Long = 0L
)

object KeyboardSettings {
    fun flow(ctx: Context): Flow<KeyboardPrefs> =
        ctx.settingsDataStore.data.map { p ->
            KeyboardPrefs(
                showRoots  = p[Keys.SHOW_ROOTS]  ?: true,
                vibration  = p[Keys.VIBRATION]   ?: true,
                sound      = p[Keys.SOUND]        ?: false,
                jyutpingPrimary = p[Keys.JYUTPING_PRIMARY] ?: false,
                simplifiedOutput = p[Keys.SIMPLIFIED_OUTPUT] ?: false,
                memoryClearToken = p[Keys.MEMORY_CLEAR_TOKEN] ?: 0L,
                customWordsToken = p[Keys.CUSTOM_WORDS_TOKEN] ?: 0L
            )
        }

    suspend fun setShowRoots(ctx: Context, v: Boolean) =
        ctx.settingsDataStore.edit { it[Keys.SHOW_ROOTS] = v }

    suspend fun setVibration(ctx: Context, v: Boolean) =
        ctx.settingsDataStore.edit { it[Keys.VIBRATION] = v }

    suspend fun setSound(ctx: Context, v: Boolean) =
        ctx.settingsDataStore.edit { it[Keys.SOUND] = v }

    suspend fun setJyutpingPrimary(ctx: Context, v: Boolean) =
        ctx.settingsDataStore.edit { it[Keys.JYUTPING_PRIMARY] = v }

    suspend fun setSimplifiedOutput(ctx: Context, v: Boolean) =
        ctx.settingsDataStore.edit { it[Keys.SIMPLIFIED_OUTPUT] = v }

    // Monotonic counters rather than timestamps: two bumps within the same
    // millisecond would produce equal tokens and ChangeTokenTracker would miss the
    // second change.
    suspend fun bumpMemoryClearToken(ctx: Context) =
        ctx.settingsDataStore.edit { it[Keys.MEMORY_CLEAR_TOKEN] = (it[Keys.MEMORY_CLEAR_TOKEN] ?: 0L) + 1 }

    suspend fun bumpCustomWordsToken(ctx: Context) =
        ctx.settingsDataStore.edit { it[Keys.CUSTOM_WORDS_TOKEN] = (it[Keys.CUSTOM_WORDS_TOKEN] ?: 0L) + 1 }
}
