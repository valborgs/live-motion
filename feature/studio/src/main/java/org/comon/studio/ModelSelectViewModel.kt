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
import org.comon.domain.model.ModelSource
import org.comon.domain.usecase.DeleteExternalModelsUseCase
import org.comon.domain.usecase.GetAllModelsUseCase
import org.comon.domain.usecase.ImportExternalModelUseCase
import javax.inject.Inject

/**
 * 모델 선택 화면의 상태 관리 및 비즈니스 로직을 담당하는 ViewModel.
 *
 * ## 책임
 * - Asset 및 External 모델 목록 조회
 * - SAF를 통한 외부 모델 가져오기 (검증, 캐싱)
 * - 모델 삭제 (다중 선택 지원)
 * - 삭제 모드 상태 관리
 *
 * ## MVI 패턴
 * - Intent: [ModelSelectUiIntent]를 통해 사용자 액션 전달
 * - State: [UiState]로 단일 UI 상태 관리
 * - Effect: [uiEffect]로 일회성 이벤트 처리 (스낵바 등)
 *
 * @property getAllModelsUseCase 모든 모델 목록 조회 UseCase
 * @property importExternalModelUseCase 외부 모델 가져오기 UseCase
 * @property deleteExternalModelsUseCase 외부 모델 삭제 UseCase
 */
@HiltViewModel
class ModelSelectViewModel @Inject constructor(
    private val getAllModelsUseCase: GetAllModelsUseCase,
    private val importExternalModelUseCase: ImportExternalModelUseCase,
    private val deleteExternalModelsUseCase: DeleteExternalModelsUseCase
) : ViewModel() {

    /**
     * 모델 선택 화면의 UI 상태.
     *
     * @property models 표시할 모델 목록 (Asset + External)
     * @property isLoading 모델 목록 로딩 중 여부
     * @property importProgress 모델 가져오기 진행률 (0.0~1.0, null이면 가져오기 중 아님)
     * @property isDeleteMode 삭제 모드 활성화 여부
     * @property selectedModelIds 삭제를 위해 선택된 모델 ID 집합
     * @property isDeleting 삭제 진행 중 여부
     */
    data class UiState(
        val models: List<ModelSource> = emptyList(),
        val isLoading: Boolean = false,
        val importProgress: Float? = null, // null이면 가져오기 중 아님
        val isDeleteMode: Boolean = false,
        val selectedModelIds: Set<String> = emptySet(),
        val isDeleting: Boolean = false
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _uiEffect = Channel<ModelSelectUiEffect>()
    val uiEffect = _uiEffect.receiveAsFlow()

    init {
        loadModels()
    }

    /**
     * UI Intent를 처리합니다.
     *
     * MVI 패턴에서 사용자 액션(Intent)을 받아 적절한 상태 변경을 수행합니다.
     *
     * @param intent 처리할 사용자 의도
     */
    fun onIntent(intent: ModelSelectUiIntent) {
        when (intent) {
            is ModelSelectUiIntent.LoadModels -> loadModels()
            is ModelSelectUiIntent.ImportModel -> importModel(intent.folderUri)
            is ModelSelectUiIntent.EnterDeleteMode -> enterDeleteMode(intent.initialModelId)
            is ModelSelectUiIntent.ExitDeleteMode -> exitDeleteMode()
            is ModelSelectUiIntent.ToggleModelSelection -> toggleModelSelection(intent.modelId)
            is ModelSelectUiIntent.DeleteSelectedModels -> deleteSelectedModels()
        }
    }

    /**
     * 모든 모델(Asset + External)을 로드합니다.
     */
    private fun loadModels() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            getAllModelsUseCase()
                .onSuccess { models ->
                    _uiState.update { it.copy(models = models, isLoading = false) }
                }
                .onError { error ->
                    _uiState.update { it.copy(isLoading = false) }
                    _uiEffect.trySend(ModelSelectUiEffect.ShowErrorWithDetail(
                        displayMessage = "오류가 발생했습니다",
                        detailMessage = error.message
                    ))
                }
        }
    }

    /**
     * 외부 모델을 가져옵니다.
     * @param folderUri SAF document tree URI
     */
    private fun importModel(folderUri: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(importProgress = 0f) }

            importExternalModelUseCase(folderUri) { progress ->
                _uiState.update { it.copy(importProgress = progress) }
            }.onSuccess {
                _uiState.update { it.copy(importProgress = null) }
                loadModels() // 목록 새로고침
            }.onError { error ->
                _uiState.update { it.copy(importProgress = null) }
                _uiEffect.trySend(ModelSelectUiEffect.ShowErrorWithDetail(
                    displayMessage = "오류가 발생했습니다",
                    detailMessage = error.message
                ))
            }
        }
    }


    /**
     * 삭제 모드를 활성화합니다.
     * @param initialModelId 처음 선택할 모델 ID (길게 눌렀을 때)
     */
    private fun enterDeleteMode(initialModelId: String) {
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
    private fun exitDeleteMode() {
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
    private fun toggleModelSelection(modelId: String) {
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
    private fun deleteSelectedModels() {
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
                    _uiState.update { it.copy(isDeleting = false) }
                    _uiEffect.trySend(ModelSelectUiEffect.ShowErrorWithDetail(
                        displayMessage = "삭제 중 오류가 발생했습니다",
                        detailMessage = error.message
                    ))
                }
        }
    }

}
