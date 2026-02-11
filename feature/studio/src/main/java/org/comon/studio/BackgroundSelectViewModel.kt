package org.comon.studio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.comon.domain.model.BackgroundSource
import org.comon.domain.usecase.DeleteBackgroundsUseCase
import org.comon.domain.usecase.GetAllBackgroundsUseCase
import org.comon.domain.usecase.ImportBackgroundUseCase
import org.comon.storage.SelectedBackgroundStore
import javax.inject.Inject

@HiltViewModel
class BackgroundSelectViewModel @Inject constructor(
    private val getAllBackgroundsUseCase: GetAllBackgroundsUseCase,
    private val importBackgroundUseCase: ImportBackgroundUseCase,
    private val deleteBackgroundsUseCase: DeleteBackgroundsUseCase,
    private val selectedBackgroundStore: SelectedBackgroundStore,
) : ViewModel() {

    data class UiState(
        val backgrounds: List<BackgroundSource> = emptyList(),
        val selectedBackgroundId: String = SelectedBackgroundStore.DEFAULT_ID,
        val isLoading: Boolean = false,
        val importProgress: Float? = null,
        val isDeleteMode: Boolean = false,
        val selectedForDeletion: Set<String> = emptySet(),
        val isDeleting: Boolean = false,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _uiEffect = Channel<BackgroundSelectUiEffect>()
    val uiEffect = _uiEffect.receiveAsFlow()

    init {
        loadBackgrounds()
        viewModelScope.launch {
            selectedBackgroundStore.selectedBackgroundIdFlow.collect { id ->
                _uiState.update { it.copy(selectedBackgroundId = id) }
            }
        }
    }

    fun onIntent(intent: BackgroundSelectUiIntent) {
        when (intent) {
            is BackgroundSelectUiIntent.LoadBackgrounds -> loadBackgrounds()
            is BackgroundSelectUiIntent.ImportBackground -> importBackground(intent.fileUri)
            is BackgroundSelectUiIntent.SelectBackground -> selectBackground(intent.backgroundId)
            is BackgroundSelectUiIntent.EnterDeleteMode -> enterDeleteMode(intent.initialBackgroundId)
            is BackgroundSelectUiIntent.ExitDeleteMode -> exitDeleteMode()
            is BackgroundSelectUiIntent.ToggleBackgroundSelection -> toggleBackgroundSelection(intent.backgroundId)
            is BackgroundSelectUiIntent.DeleteSelectedBackgrounds -> deleteSelectedBackgrounds()
        }
    }

    private fun loadBackgrounds() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            getAllBackgroundsUseCase()
                .onSuccess { backgrounds ->
                    _uiState.update { it.copy(backgrounds = backgrounds, isLoading = false) }
                }
                .onError { error ->
                    _uiState.update { it.copy(isLoading = false) }
                    _uiEffect.trySend(BackgroundSelectUiEffect.ShowErrorWithDetail(
                        displayMessage = "오류가 발생했습니다",
                        detailMessage = error.message
                    ))
                }
        }
    }

    private fun selectBackground(backgroundId: String) {
        viewModelScope.launch {
            selectedBackgroundStore.saveSelectedBackgroundId(backgroundId)
        }
    }

    private fun importBackground(fileUri: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(importProgress = 0f) }

            importBackgroundUseCase(fileUri) { progress ->
                _uiState.update { it.copy(importProgress = progress) }
            }.onSuccess {
                _uiState.update { it.copy(importProgress = null) }
                loadBackgrounds()
            }.onError { error ->
                _uiState.update { it.copy(importProgress = null) }
                _uiEffect.trySend(BackgroundSelectUiEffect.ShowErrorWithDetail(
                    displayMessage = "오류가 발생했습니다",
                    detailMessage = error.message
                ))
            }
        }
    }

    private fun enterDeleteMode(initialBackgroundId: String) {
        _uiState.update {
            it.copy(
                isDeleteMode = true,
                selectedForDeletion = setOf(initialBackgroundId)
            )
        }
    }

    private fun exitDeleteMode() {
        _uiState.update {
            it.copy(
                isDeleteMode = false,
                selectedForDeletion = emptySet()
            )
        }
    }

    private fun toggleBackgroundSelection(backgroundId: String) {
        _uiState.update { state ->
            val newSelection = if (backgroundId in state.selectedForDeletion) {
                state.selectedForDeletion - backgroundId
            } else {
                state.selectedForDeletion + backgroundId
            }
            state.copy(selectedForDeletion = newSelection)
        }
    }

    private fun deleteSelectedBackgrounds() {
        val selectedIds = _uiState.value.selectedForDeletion
        if (selectedIds.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isDeleting = true) }

            deleteBackgroundsUseCase(selectedIds)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isDeleting = false,
                            isDeleteMode = false,
                            selectedForDeletion = emptySet()
                        )
                    }
                    // 선택된 배경이 삭제된 경우 기본값으로 리셋
                    val currentSelected = _uiState.value.selectedBackgroundId
                    if (currentSelected in selectedIds) {
                        selectedBackgroundStore.saveSelectedBackgroundId(SelectedBackgroundStore.DEFAULT_ID)
                    }
                    loadBackgrounds()
                }
                .onError { error ->
                    _uiState.update { it.copy(isDeleting = false) }
                    _uiEffect.trySend(BackgroundSelectUiEffect.ShowErrorWithDetail(
                        displayMessage = "삭제 중 오류가 발생했습니다",
                        detailMessage = error.message
                    ))
                }
        }
    }
}
