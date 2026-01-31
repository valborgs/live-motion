package org.comon.studio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.comon.domain.model.ModelSource
import org.comon.domain.usecase.DeleteExternalModelsUseCase
import org.comon.domain.usecase.GetAllModelsUseCase
import org.comon.domain.usecase.ImportExternalModelUseCase

/**
 * 모델 선택 화면의 ViewModel
 */
class ModelSelectViewModel(
    private val getAllModelsUseCase: GetAllModelsUseCase,
    private val importExternalModelUseCase: ImportExternalModelUseCase,
    private val deleteExternalModelsUseCase: DeleteExternalModelsUseCase
) : ViewModel() {

    /**
     * UI 상태
     */
    data class UiState(
        val models: List<ModelSource> = emptyList(),
        val isLoading: Boolean = false,
        val importProgress: Float? = null, // null이면 가져오기 중 아님
        val error: String? = null,
        val isDeleteMode: Boolean = false,
        val selectedModelIds: Set<String> = emptySet(),
        val isDeleting: Boolean = false
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        loadModels()
    }

    /**
     * 모든 모델(Asset + External)을 로드합니다.
     */
    fun loadModels() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            getAllModelsUseCase()
                .onSuccess { models ->
                    _uiState.update { it.copy(models = models, isLoading = false) }
                }
                .onError { error ->
                    _uiState.update { it.copy(error = error.message, isLoading = false) }
                }
        }
    }

    /**
     * 외부 모델을 가져옵니다.
     * @param folderUri SAF document tree URI
     */
    fun importModel(folderUri: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(importProgress = 0f) }

            importExternalModelUseCase(folderUri) { progress ->
                _uiState.update { it.copy(importProgress = progress) }
            }.onSuccess {
                _uiState.update { it.copy(importProgress = null) }
                loadModels() // 목록 새로고침
            }.onError { error ->
                _uiState.update {
                    it.copy(importProgress = null, error = error.message)
                }
            }
        }
    }

    /**
     * 에러 메시지를 초기화합니다.
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    /**
     * 삭제 모드를 활성화합니다.
     * @param initialModelId 처음 선택할 모델 ID (길게 눌렀을 때)
     */
    fun enterDeleteMode(initialModelId: String) {
        _uiState.update {
            it.copy(
                isDeleteMode = true,
                selectedModelIds = setOf(initialModelId)
            )
        }
    }

    /**
     * 삭제 모드를 종료합니다.
     */
    fun exitDeleteMode() {
        _uiState.update {
            it.copy(
                isDeleteMode = false,
                selectedModelIds = emptySet()
            )
        }
    }

    /**
     * 모델 선택 상태를 토글합니다.
     */
    fun toggleModelSelection(modelId: String) {
        _uiState.update { state ->
            val newSelection = if (modelId in state.selectedModelIds) {
                state.selectedModelIds - modelId
            } else {
                state.selectedModelIds + modelId
            }
            state.copy(selectedModelIds = newSelection)
        }
    }

    /**
     * 선택된 모델들을 삭제합니다.
     */
    fun deleteSelectedModels() {
        val selectedIds = _uiState.value.selectedModelIds.toList()
        if (selectedIds.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isDeleting = true) }

            deleteExternalModelsUseCase(selectedIds)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isDeleting = false,
                            isDeleteMode = false,
                            selectedModelIds = emptySet()
                        )
                    }
                    loadModels() // 목록 새로고침
                }
                .onError { error ->
                    _uiState.update {
                        it.copy(
                            isDeleting = false,
                            error = error.message
                        )
                    }
                }
        }
    }

    /**
     * Factory for creating ModelSelectViewModel
     */
    class Factory(
        private val getAllModelsUseCase: GetAllModelsUseCase,
        private val importExternalModelUseCase: ImportExternalModelUseCase,
        private val deleteExternalModelsUseCase: DeleteExternalModelsUseCase
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ModelSelectViewModel(
                getAllModelsUseCase,
                importExternalModelUseCase,
                deleteExternalModelsUseCase
            ) as T
        }
    }
}
