package org.comon.studio

import androidx.camera.core.Preview.SurfaceProvider
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.comon.domain.common.DomainException
import org.comon.domain.model.FacePose
import org.comon.domain.model.Live2DParams
import org.comon.domain.usecase.GetModelMetadataUseCase
import org.comon.domain.usecase.MapFacePoseUseCase
import org.comon.tracking.FaceTracker
import org.comon.tracking.FaceTrackerFactory
import org.comon.tracking.TrackingError

class StudioViewModel(
    private val faceTrackerFactory: FaceTrackerFactory,
    private val getModelMetadataUseCase: GetModelMetadataUseCase,
    private val mapFacePoseUseCase: MapFacePoseUseCase
) : ViewModel() {

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // FaceTracker (configuration change에서 생존)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    private var faceTracker: FaceTracker? = null

    // FaceTracker 초기화 전에 attachPreview가 호출된 경우 대기
    private var pendingSurfaceProvider: SurfaceProvider? = null

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 실시간 트래킹 데이터 (30fps) - 별도 Flow
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    private val _facePose = MutableStateFlow(FacePose())
    val facePose: StateFlow<FacePose> = _facePose.asStateFlow()

    private val _faceLandmarks = MutableStateFlow<List<NormalizedLandmark>>(emptyList())
    val faceLandmarks: StateFlow<List<NormalizedLandmark>> = _faceLandmarks.asStateFlow()

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // UI 상태 (단일 State 객체)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    private val _uiState = MutableStateFlow(StudioUiState())
    val uiState: StateFlow<StudioUiState> = _uiState.asStateFlow()

    data class StudioUiState(
        // 모델 로딩 상태
        val isModelLoading: Boolean = true,

        // 트래킹 상태
        val isCalibrating: Boolean = false,
        val isGpuEnabled: Boolean = false,
        val trackingError: TrackingError? = null,

        // 도메인 에러 (UseCase에서 발생)
        val domainError: DomainException? = null,

        // UI 토글
        val isZoomEnabled: Boolean = false,
        val isMoveEnabled: Boolean = false,
        val isPreviewVisible: Boolean = true,

        // 다이얼로그
        val dialogState: DialogState = DialogState.None,

        // 모델 메타데이터
        val expressionsFolder: String? = null,
        val motionsFolder: String? = null,
        val expressionFiles: List<String> = emptyList(),
        val motionFiles: List<String> = emptyList()
    )

    sealed class DialogState {
        data object None : DialogState()
        data object Expression : DialogState()
        data object Motion : DialogState()
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 초기화
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    fun initialize(lifecycleOwner: LifecycleOwner, modelId: String) {
        if (faceTracker == null) {
            faceTracker = faceTrackerFactory.create(lifecycleOwner).also { tracker ->
                // FaceTracker의 StateFlow를 ViewModel로 전파
                viewModelScope.launch {
                    tracker.facePose.collect { pose ->
                        _facePose.value = pose
                    }
                }
                viewModelScope.launch {
                    tracker.faceLandmarks.collect { landmarks ->
                        _faceLandmarks.value = landmarks
                    }
                }
                viewModelScope.launch {
                    tracker.isCalibratingUI.collect { calibrating ->
                        _uiState.update { it.copy(isCalibrating = calibrating) }
                    }
                }
                viewModelScope.launch {
                    tracker.isGpuEnabled.collect { gpu ->
                        _uiState.update { it.copy(isGpuEnabled = gpu) }
                    }
                }
                viewModelScope.launch {
                    tracker.error.collect { error ->
                        _uiState.update { it.copy(trackingError = error) }
                    }
                }

                // FaceLandmarker 및 카메라 시작
                tracker.setupFaceLandmarker(useGpu = false)
                tracker.startCamera()

                // 대기 중이던 프리뷰 연결
                pendingSurfaceProvider?.let { surfaceProvider ->
                    tracker.attachPreview(surfaceProvider)
                    pendingSurfaceProvider = null
                }
            }
        }

        loadModelMetadata(modelId)
    }

    private fun loadModelMetadata(modelId: String) {
        getModelMetadataUseCase(modelId)
            .onSuccess { metadata ->
                _uiState.update {
                    it.copy(
                        expressionsFolder = metadata.expressionsFolder,
                        motionsFolder = metadata.motionsFolder,
                        expressionFiles = metadata.expressionFiles,
                        motionFiles = metadata.motionFiles,
                        domainError = null
                    )
                }
            }
            .onError { error ->
                _uiState.update { it.copy(domainError = error) }
            }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Face Params 계산 (UseCase 사용)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    fun mapFaceParams(facePose: FacePose, hasLandmarks: Boolean): Map<String, Float> {
        val params = mapFacePoseUseCase(facePose, hasLandmarks)
        return params.params
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // UI Actions
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    fun toggleZoom() {
        _uiState.update { it.copy(isZoomEnabled = !it.isZoomEnabled) }
    }

    fun toggleMove() {
        _uiState.update { it.copy(isMoveEnabled = !it.isMoveEnabled) }
    }

    fun togglePreview() {
        _uiState.update { it.copy(isPreviewVisible = !it.isPreviewVisible) }
    }

    fun setGpuEnabled(enabled: Boolean) {
        faceTracker?.setGpuEnabled(enabled)
    }

    fun showExpressionDialog() {
        _uiState.update { it.copy(dialogState = DialogState.Expression) }
    }

    fun showMotionDialog() {
        _uiState.update { it.copy(dialogState = DialogState.Motion) }
    }

    fun dismissDialog() {
        _uiState.update { it.copy(dialogState = DialogState.None) }
    }

    fun onModelLoaded() {
        _uiState.update { it.copy(isModelLoading = false) }
    }

    fun clearTrackingError() {
        faceTracker?.clearError()
        _uiState.update { it.copy(trackingError = null) }
    }

    fun clearDomainError() {
        _uiState.update { it.copy(domainError = null) }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Preview 연결/해제
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    fun attachPreview(surfaceProvider: SurfaceProvider) {
        val tracker = faceTracker
        if (tracker != null) {
            tracker.attachPreview(surfaceProvider)
        } else {
            // FaceTracker 초기화 전이면 대기열에 저장
            pendingSurfaceProvider = surfaceProvider
        }
    }

    fun detachPreview() {
        pendingSurfaceProvider = null
        faceTracker?.detachPreview()
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Cleanup
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    override fun onCleared() {
        super.onCleared()
        faceTracker?.stop()
        faceTracker = null
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Factory (의존성 주입)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    class Factory(
        private val faceTrackerFactory: FaceTrackerFactory,
        private val getModelMetadataUseCase: GetModelMetadataUseCase,
        private val mapFacePoseUseCase: MapFacePoseUseCase
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(StudioViewModel::class.java)) {
                return StudioViewModel(
                    faceTrackerFactory,
                    getModelMetadataUseCase,
                    mapFacePoseUseCase
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
