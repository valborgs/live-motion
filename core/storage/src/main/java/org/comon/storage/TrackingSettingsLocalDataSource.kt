package org.comon.storage

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.comon.domain.model.TrackingSensitivity

private val Context.trackingSettingsDataStore by preferencesDataStore(name = "tracking_settings")

class TrackingSettingsLocalDataSource(private val context: Context) {

    companion object {
        private val KEY_YAW = floatPreferencesKey("sensitivity_yaw")
        private val KEY_PITCH = floatPreferencesKey("sensitivity_pitch")
        private val KEY_ROLL = floatPreferencesKey("sensitivity_roll")
    }

    val sensitivityFlow: Flow<TrackingSensitivity> =
        context.trackingSettingsDataStore.data.map { prefs ->
            TrackingSensitivity(
                yaw = prefs[KEY_YAW] ?: 1.0f,
                pitch = prefs[KEY_PITCH] ?: 1.0f,
                roll = prefs[KEY_ROLL] ?: 1.0f
            )
        }

    suspend fun saveSensitivity(sensitivity: TrackingSensitivity) {
        context.trackingSettingsDataStore.edit { prefs ->
            prefs[KEY_YAW] = sensitivity.yaw
            prefs[KEY_PITCH] = sensitivity.pitch
            prefs[KEY_ROLL] = sensitivity.roll
        }
    }
}
