package org.comon.studio

sealed interface BackgroundSelectUiIntent {
    data object LoadBackgrounds : BackgroundSelectUiIntent
    data class ImportBackground(val fileUri: String) : BackgroundSelectUiIntent
    data class SelectBackground(val backgroundId: String) : BackgroundSelectUiIntent
    data class EnterDeleteMode(val initialBackgroundId: String) : BackgroundSelectUiIntent
    data object ExitDeleteMode : BackgroundSelectUiIntent
    data class ToggleBackgroundSelection(val backgroundId: String) : BackgroundSelectUiIntent
    data object DeleteSelectedBackgrounds : BackgroundSelectUiIntent
}
