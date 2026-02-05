package org.comon.studio

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.comon.domain.model.FacePose
import org.comon.domain.model.FacePoseSmoothingState
import org.comon.domain.model.ModelSource
import org.comon.domain.usecase.GetModelMetadataUseCase
import org.comon.domain.usecase.MapFacePoseUseCase
import org.comon.live2d.LAppMinimumDelegate
import org.comon.live2d.Live2DUiEffect
import org.comon.tracking.FaceTracker
import org.comon.tracking.FaceTrackerFactory
import org.comon.tracking.TrackingError
import javax.inject.Inject

/**
 * Studio 화면의 상태 관리 및 비즈니스 로직을 담당하는 ViewModel.
 *
 * ## 책임
 * - 얼굴 추적 시스템 초기화 및 관리 (FaceTracker)
 * - 실시간 얼굴 포즈 데이터를 Live2D 파라미터로 변환
 * - Live2D 모델 메타데이터(표정/모션 파일) 로딩
 * - UI 상태 관리 (확대/이동 모드, 프리뷰 표시 등)
 *
 * ## 데이터 흐름
 * 1. [initialize] 호출로 FaceTracker 생성 및 시작
 * 2. FaceTracker에서 [facePose]와 [faceLandmarks] 수신
 * 3. [mapFaceParams]로 Live2D 파라미터 변환
 * 4. UI는 [uiState]를 관찰하여 렌더링
 *
 * ## MVI 패턴
 * - Intent: [StudioUiIntent]를 통해 사용자 액션 전달
 * - State: [StudioUiState]로 단일 UI 상태 관리
 * - Effect: [uiEffect]로 일회성 이벤트 처리
 *
 * @property faceTrackerFactory 얼굴 추적기 생성 팩토리
 * @property getModelMetadataUseCase 모델 메타데이터 조회 UseCase
 */
@HiltViewModel
class StudioViewModel @Inject constructor(
    private val faceTrackerFactory: FaceTrackerFactory,
    private val getModelMetadataUseCase: GetModelMetadataUseCase,
    private val mapFacePoseUseCase: MapFacePoseUseCase
) : ViewModel() {

    // EMA 스무딩 상태 (ViewModel에서 관리)
    private var smoothingState = FacePoseSmoothingState()

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // FaceTracker (configuration change에서 생존)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    private var faceTracker: FaceTracker? = null

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

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // UI Effect (일회성 이벤트)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    private val _uiEffect = Channel<StudioUiEffect>()
    val uiEffect = _uiEffect.receiveAsFlow()

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Live2D Effect (렌더링 관련 일회성 이벤트)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    private val _live2dEffect = Channel<Live2DUiEffect>()
    val live2dEffect = _live2dEffect.receiveAsFlow()

    data class StudioUiState(
        // 모델 로딩 상태
        val isModelLoading: Boolean = true,

        // 트래킹 상태
        val isCalibrating: Boolean = false,
        val isGpuEnabled: Boolean = false,

        // UI 토글
        val isGestureEnabled: Boolean = false,
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

    /**
     * ViewModel을 초기화합니다.
     *
     * FaceTracker를 생성하고 얼굴 추적을 시작합니다.
     * configuration change에서 FaceTracker를 재사용하므로 이미 초기화된 경우 스킵합니다.
     *
     * @param lifecycleOwner 카메라 생명주기 관리를 위한 LifecycleOwner
     * @param modelSource 로드할 Live2D 모델 소스 (Asset 또는 External)
     */
    fun initialize(lifecycleOwner: LifecycleOwner, modelSource: ModelSource) {
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
                        error?.let { trackingError ->
                            val errorMessage = when (trackingError) {
                                is TrackingError.FaceLandmarkerInitError -> trackingError.message
                                is TrackingError.CameraError -> trackingError.message
                                is TrackingError.MediaPipeRuntimeError -> trackingError.message
                            }
                            _uiEffect.trySend(StudioUiEffect.ShowErrorWithDetail(
                                displayMessage = "트래킹 오류가 발생했습니다",
                                detailMessage = errorMessage
                            ))
                            tracker.clearError()
                        }
                    }
                }

                // FaceLandmarker 및 카메라 시작
                tracker.setupFaceLandmarker(useGpu = false)
                tracker.startCamera()
            }
        }

        // 메타데이터 로드
        loadModelMetadata(modelSource)
    }

    private fun loadModelMetadata(modelSource: ModelSource) {
        viewModelScope.launch {
            getModelMetadataUseCase(modelSource)
                .onSuccess { metadata ->
                    _uiState.update {
                        it.copy(
                            expressionsFolder = metadata.expressionsFolder,
                            motionsFolder = metadata.motionsFolder,
                            expressionFiles = metadata.expressionFiles,
                            motionFiles = metadata.motionFiles
                        )
                    }
                }
                .onError { error ->
                    _uiEffect.trySend(StudioUiEffect.ShowSnackbar(error.message))
                }
        }
    }

    /**
     * 얼굴 포즈 데이터를 Live2D 파라미터 맵으로 변환합니다.
     *
     * EMA 스무딩이 적용되어 부드러운 애니메이션을 제공합니다.
     * 얼굴이 감지되지 않으면 기본값을 반환하고 스무딩 상태를 초기화합니다.
     *
     * @param facePose 얼굴 포즈 데이터 (yaw, pitch, roll, 눈, 입 등)
     * @param hasLandmarks 얼굴 랜드마크 감지 여부
     * @return Live2D 파라미터 맵 (ParamAngleX, ParamEyeLOpen 등)
     */
    fun mapFaceParams(facePose: FacePose, hasLandmarks: Boolean): Map<String, Float> {
        val (params, newState) = mapFacePoseUseCase(facePose, smoothingState, hasLandmarks)
        smoothingState = newState
        return params.params
    }

    /**
     * UI Intent를 처리합니다.
     *
     * MVI 패턴에서 사용자 액션(Intent)을 받아 적절한 상태 변경을 수행합니다.
     *
     * @param intent 처리할 사용자 의도
     */
    fun onIntent(intent: StudioUiIntent) {
        when (intent) {
            is StudioUiIntent.ToggleGesture -> toggleGesture()
            is StudioUiIntent.TogglePreview -> togglePreview()
            is StudioUiIntent.SetGpuEnabled -> setGpuEnabled(intent.enabled)
            is StudioUiIntent.ShowExpressionDialog -> showExpressionDialog()
            is StudioUiIntent.ShowMotionDialog -> showMotionDialog()
            is StudioUiIntent.DismissDialog -> dismissDialog()
            is StudioUiIntent.OnModelLoaded -> onModelLoaded()
            is StudioUiIntent.StartExpression -> startExpression(intent.path)
            is StudioUiIntent.ClearExpression -> clearExpression()
            is StudioUiIntent.StartMotion -> startMotion(intent.path)
            is StudioUiIntent.ClearMotion -> clearMotion()
            is StudioUiIntent.ResetTransform -> resetTransform()
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // UI Actions
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    private fun toggleGesture() {
        _uiState.update { it.copy(isGestureEnabled = !it.isGestureEnabled) }
    }

    private fun togglePreview() {
        _uiState.update { it.copy(isPreviewVisible = !it.isPreviewVisible) }
    }

    private fun setGpuEnabled(enabled: Boolean) {
        faceTracker?.setGpuEnabled(enabled)
    }

    private fun showExpressionDialog() {
        _uiState.update { it.copy(dialogState = DialogState.Expression) }
    }

    private fun showMotionDialog() {
        _uiState.update { it.copy(dialogState = DialogState.Motion) }
    }

    private fun dismissDialog() {
        _uiState.update { it.copy(dialogState = DialogState.None) }
    }

    private fun onModelLoaded() {
        _uiState.update { it.copy(isModelLoading = false) }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Live2D Actions
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    private fun startExpression(path: String) {
        _live2dEffect.trySend(Live2DUiEffect.StartExpression(path))
    }

    private fun clearExpression() {
        _live2dEffect.trySend(Live2DUiEffect.ClearExpression)
    }

    private fun startMotion(path: String) {
        _live2dEffect.trySend(Live2DUiEffect.StartMotion(path))
    }

    private fun clearMotion() {
        _live2dEffect.trySend(Live2DUiEffect.ClearMotion)
    }

    private fun resetTransform() {
        _live2dEffect.trySend(Live2DUiEffect.ResetTransform)
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Cleanup
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    override fun onCleared() {
        super.onCleared()
        faceTracker?.stop()
        faceTracker = null
        // Live2D 리소스 정리 (view, textureManager, model, CubismFramework.dispose)
        // ViewModel은 NavBackStackEntry에 바인딩되므로 predictive back 제스처 중에는
        // cleared되지 않고, 실제 네비게이션 완료 시에만 호출됨
        LAppMinimumDelegate.getInstance().onStop()
    }

}
