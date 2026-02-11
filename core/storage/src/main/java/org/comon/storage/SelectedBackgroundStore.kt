package org.comon.storage

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.selectedBackgroundDataStore by preferencesDataStore(name = "selected_background")

class SelectedBackgroundStore(private val context: Context) {

    companion object {
        private val KEY_SELECTED_ID = stringPreferencesKey("selected_background_id")
        const val DEFAULT_ID = "default_white"
    }

    val selectedBackgroundIdFlow: Flow<String> =
        context.selectedBackgroundDataStore.data.map { prefs ->
            prefs[KEY_SELECTED_ID] ?: DEFAULT_ID
        }

    suspend fun saveSelectedBackgroundId(id: String) {
        context.selectedBackgroundDataStore.edit { prefs ->
            prefs[KEY_SELECTED_ID] = id
        }
    }
}
