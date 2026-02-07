package org.comon.settings

sealed interface SettingsUiIntent {
    data class UpdateYaw(val value: Float) : SettingsUiIntent
    data class UpdatePitch(val value: Float) : SettingsUiIntent
    data class UpdateRoll(val value: Float) : SettingsUiIntent
    data object ResetToDefault : SettingsUiIntent
}
