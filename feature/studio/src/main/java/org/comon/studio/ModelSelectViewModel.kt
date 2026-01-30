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
import org.comon.domain.usecase.GetAllModelsUseCase
import org.comon.domain.usecase.ImportExternalModelUseCase

/**
 * 모델 선택 화면의 ViewModel
 */
class ModelSelectViewModel(
    private val getAllModelsUseCase: GetAllModelsUseCase,
    private val importExternalModelUseCase: ImportExternalModelUseCase
) : ViewModel() {

    /**
     * UI 상태
     */
    data class UiState(
        val models: List<ModelSource> = emptyList(),
        val isLoading: Boolean = false,
        val importProgress: Float? = null, // null이면 가져오기 중 아님
        val error: String? = null
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
     * Factory for creating ModelSelectViewModel
     */
    class Factory(
        private val getAllModelsUseCase: GetAllModelsUseCase,
        private val importExternalModelUseCase: ImportExternalModelUseCase
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ModelSelectViewModel(getAllModelsUseCase, importExternalModelUseCase) as T
        }
    }
}
