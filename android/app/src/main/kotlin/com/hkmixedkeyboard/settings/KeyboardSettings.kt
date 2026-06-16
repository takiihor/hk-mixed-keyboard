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
    val SMART_SPACE    = booleanPreferencesKey("smart_space")
    val SHOW_ROOTS     = booleanPreferencesKey("show_roots")
    val VIBRATION      = booleanPreferencesKey("vibration")
    val SOUND          = booleanPreferencesKey("sound")
    val JYUTPING_PRIMARY = booleanPreferencesKey("jyutping_primary")
    // Bumped whenever the user clears their dictionary, so the running IME can flush
    // its in-memory caches live instead of only on the next keyboard restart.
    val MEMORY_CLEAR_TOKEN = longPreferencesKey("memory_clear_token")
}

data class KeyboardPrefs(
    val smartSpace: Boolean = true,
    val showRoots: Boolean = true,
    val vibration: Boolean = true,
    val sound: Boolean = false,
    val jyutpingPrimary: Boolean = false,
    val memoryClearToken: Long = 0L
)

object KeyboardSettings {
    fun flow(ctx: Context): Flow<KeyboardPrefs> =
        ctx.settingsDataStore.data.map { p ->
            KeyboardPrefs(
                smartSpace = p[Keys.SMART_SPACE] ?: true,
                showRoots  = p[Keys.SHOW_ROOTS]  ?: true,
                vibration  = p[Keys.VIBRATION]   ?: true,
                sound      = p[Keys.SOUND]        ?: false,
                jyutpingPrimary = p[Keys.JYUTPING_PRIMARY] ?: false,
                memoryClearToken = p[Keys.MEMORY_CLEAR_TOKEN] ?: 0L
            )
        }

    suspend fun setSmartSpace(ctx: Context, v: Boolean) =
        ctx.settingsDataStore.edit { it[Keys.SMART_SPACE] = v }

    suspend fun setShowRoots(ctx: Context, v: Boolean) =
        ctx.settingsDataStore.edit { it[Keys.SHOW_ROOTS] = v }

    suspend fun setVibration(ctx: Context, v: Boolean) =
        ctx.settingsDataStore.edit { it[Keys.VIBRATION] = v }

    suspend fun setSound(ctx: Context, v: Boolean) =
        ctx.settingsDataStore.edit { it[Keys.SOUND] = v }

    suspend fun setJyutpingPrimary(ctx: Context, v: Boolean) =
        ctx.settingsDataStore.edit { it[Keys.JYUTPING_PRIMARY] = v }

    suspend fun bumpMemoryClearToken(ctx: Context) =
        ctx.settingsDataStore.edit { it[Keys.MEMORY_CLEAR_TOKEN] = System.currentTimeMillis() }
}
