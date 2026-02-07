package org.comon.storage

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.comon.domain.model.ThemeMode

private val Context.themeDataStore by preferencesDataStore(name = "theme_settings")

class ThemeLocalDataSource(private val context: Context) {

    companion object {
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
    }

    val themeModeFlow: Flow<ThemeMode> =
        context.themeDataStore.data.map { prefs ->
            val name = prefs[KEY_THEME_MODE] ?: ThemeMode.SYSTEM.name
            try {
                ThemeMode.valueOf(name)
            } catch (_: IllegalArgumentException) {
                ThemeMode.SYSTEM
            }
        }

    suspend fun saveThemeMode(themeMode: ThemeMode) {
        context.themeDataStore.edit { prefs ->
            prefs[KEY_THEME_MODE] = themeMode.name
        }
    }
}
