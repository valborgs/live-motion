package org.comon.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.comon.domain.model.TrackingSensitivity
import org.comon.storage.TrackingSettingsLocalDataSource
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val trackingSettingsLocalDataSource: TrackingSettingsLocalDataSource
) : ViewModel() {

    data class SettingsUiState(
        val yaw: Float = 1.0f,
        val pitch: Float = 1.0f,
        val roll: Float = 1.0f
    )

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            trackingSettingsLocalDataSource.sensitivityFlow.collect { sensitivity ->
                _uiState.update {
                    it.copy(
                        yaw = sensitivity.yaw,
                        pitch = sensitivity.pitch,
                        roll = sensitivity.roll
                    )
                }
            }
        }
    }

    fun onIntent(intent: SettingsUiIntent) {
        when (intent) {
            is SettingsUiIntent.UpdateYaw -> updateSensitivity(yaw = intent.value)
            is SettingsUiIntent.UpdatePitch -> updateSensitivity(pitch = intent.value)
            is SettingsUiIntent.UpdateRoll -> updateSensitivity(roll = intent.value)
            is SettingsUiIntent.ResetToDefault -> resetToDefault()
        }
    }

    private fun updateSensitivity(
        yaw: Float = _uiState.value.yaw,
        pitch: Float = _uiState.value.pitch,
        roll: Float = _uiState.value.roll
    ) {
        val sensitivity = TrackingSensitivity(yaw = yaw, pitch = pitch, roll = roll)
        _uiState.update { it.copy(yaw = yaw, pitch = pitch, roll = roll) }
        viewModelScope.launch {
            trackingSettingsLocalDataSource.saveSensitivity(sensitivity)
        }
    }

    private fun resetToDefault() {
        updateSensitivity(yaw = 1.0f, pitch = 1.0f, roll = 1.0f)
    }
}
