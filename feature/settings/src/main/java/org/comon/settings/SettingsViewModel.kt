package org.comon.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import org.comon.domain.model.AppLanguage
import org.comon.domain.model.ThemeMode
import org.comon.domain.model.TrackingSensitivity
import org.comon.storage.ThemeLocalDataSource
import org.comon.storage.TrackingSettingsLocalDataSource
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val trackingSettingsLocalDataSource: TrackingSettingsLocalDataSource,
    private val themeLocalDataSource: ThemeLocalDataSource
) : ViewModel() {

    data class SettingsUiState(
        val yaw: Float = 1.0f,
        val pitch: Float = 1.0f,
        val roll: Float = 1.0f,
        val smoothing: Float = 0.4f,
        val themeMode: ThemeMode = ThemeMode.SYSTEM,
        val appLanguage: AppLanguage = AppLanguage.SYSTEM
    )

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        // 현재 앱 언어 읽기
        val currentLocales = AppCompatDelegate.getApplicationLocales()
        val currentTag = if (currentLocales.isEmpty) "" else currentLocales.toLanguageTags()
        _uiState.update { it.copy(appLanguage = AppLanguage.fromLocaleTag(currentTag)) }

        viewModelScope.launch {
            trackingSettingsLocalDataSource.sensitivityFlow.collect { sensitivity ->
                _uiState.update {
                    it.copy(
                        yaw = sensitivity.yaw,
                        pitch = sensitivity.pitch,
                        roll = sensitivity.roll,
                        smoothing = sensitivity.smoothing
                    )
                }
            }
        }
        viewModelScope.launch {
            themeLocalDataSource.themeModeFlow.collect { mode ->
                _uiState.update { it.copy(themeMode = mode) }
            }
        }
    }

    fun onIntent(intent: SettingsUiIntent) {
        when (intent) {
            is SettingsUiIntent.UpdateYaw -> updateSensitivity(yaw = intent.value)
            is SettingsUiIntent.UpdatePitch -> updateSensitivity(pitch = intent.value)
            is SettingsUiIntent.UpdateRoll -> updateSensitivity(roll = intent.value)
            is SettingsUiIntent.UpdateSmoothing -> updateSensitivity(smoothing = intent.value)
            is SettingsUiIntent.UpdateThemeMode -> updateThemeMode(intent.mode)
            is SettingsUiIntent.UpdateLanguage -> updateLanguage(intent.language)
            is SettingsUiIntent.ResetToDefault -> resetToDefault()
        }
    }

    private fun updateSensitivity(
        yaw: Float = _uiState.value.yaw,
        pitch: Float = _uiState.value.pitch,
        roll: Float = _uiState.value.roll,
        smoothing: Float = _uiState.value.smoothing
    ) {
        val sensitivity = TrackingSensitivity(yaw = yaw, pitch = pitch, roll = roll, smoothing = smoothing)
        _uiState.update { it.copy(yaw = yaw, pitch = pitch, roll = roll, smoothing = smoothing) }
        viewModelScope.launch {
            trackingSettingsLocalDataSource.saveSensitivity(sensitivity)
        }
    }

    private fun updateThemeMode(mode: ThemeMode) {
        _uiState.update { it.copy(themeMode = mode) }
        viewModelScope.launch {
            themeLocalDataSource.saveThemeMode(mode)
        }
    }

    private fun updateLanguage(language: AppLanguage) {
        _uiState.update { it.copy(appLanguage = language) }
        val localeList = if (language == AppLanguage.SYSTEM) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(language.localeTag)
        }
        AppCompatDelegate.setApplicationLocales(localeList)
    }

    private fun resetToDefault() {
        updateSensitivity(yaw = 1.0f, pitch = 1.0f, roll = 1.0f, smoothing = 0.4f)
    }
}
