package org.comon.settings

import org.comon.domain.model.AppLanguage
import org.comon.domain.model.ThemeMode

sealed interface SettingsUiIntent {
    data class UpdateYaw(val value: Float) : SettingsUiIntent
    data class UpdatePitch(val value: Float) : SettingsUiIntent
    data class UpdateRoll(val value: Float) : SettingsUiIntent
    data class UpdateSmoothing(val value: Float) : SettingsUiIntent
    data class UpdateThemeMode(val mode: ThemeMode) : SettingsUiIntent
    data class UpdateLanguage(val language: AppLanguage) : SettingsUiIntent
    data object ResetToDefault : SettingsUiIntent
}
