package org.comon.studio

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.comon.domain.model.BackgroundSource
import org.comon.domain.model.FacePose
import org.comon.domain.model.FacePoseSmoothingState
import org.comon.domain.model.ModelSource
import org.comon.domain.model.TrackingSensitivity
import org.comon.domain.usecase.GetAllBackgroundsUseCase
import org.comon.domain.usecase.GetModelMetadataUseCase
import org.comon.domain.usecase.MapFacePoseUseCase
import org.comon.live2d.LAppMinimumDelegate
import org.comon.live2d.Live2DUiEffect
import org.comon.storage.SelectedBackgroundStore
import org.comon.storage.TrackingSettingsLocalDataSource
import org.comon.studio.recording.MediaSplitter
import org.comon.studio.recording.ModelRecorder
import org.comon.tracking.FaceTracker
import org.comon.tracking.FaceTrackerFactory
import org.comon.tracking.TrackingError
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
    @ApplicationContext private val appContext: Context,
    private val faceTrackerFactory: FaceTrackerFactory,
    private val getModelMetadataUseCase: GetModelMetadataUseCase,
    private val mapFacePoseUseCase: MapFacePoseUseCase,
    private val trackingSettingsLocalDataSource: TrackingSettingsLocalDataSource,
    private val selectedBackgroundStore: SelectedBackgroundStore,
    private val getAllBackgroundsUseCase: GetAllBackgroundsUseCase,
) : ViewModel() {

    private companion object {
        const val TAG = "StudioViewModel"
    }

    // EMA 스무딩 상태 (ViewModel에서 관리)
    private var smoothingState = FacePoseSmoothingState()

    // 트래킹 감도 (DataStore에서 실시간 수집)
    private var currentSensitivity = TrackingSensitivity()

    // 배경 소스 목록 캐시
    private var backgroundSources: List<BackgroundSource> = emptyList()

    init {
        viewModelScope.launch {
            trackingSettingsLocalDataSource.sensitivityFlow.collect { sensitivity ->
                currentSensitivity = sensitivity
            }
        }
        loadBackgroundPath()
    }

    private fun loadBackgroundPath() {
        viewModelScope.launch {
            // 배경 목록 로드
            getAllBackgroundsUseCase().onSuccess { backgrounds ->
                backgroundSources = backgrounds
            }

            // 선택된 배경 ID 구독
            selectedBackgroundStore.selectedBackgroundIdFlow.collect { selectedId ->
                val path = resolveBackgroundPath(selectedId)
                _uiState.update { it.copy(backgroundPath = path) }
            }
        }
    }

    private fun resolveBackgroundPath(selectedId: String): String? {
        return when {
            selectedId == SelectedBackgroundStore.DEFAULT_ID -> null
            selectedId.startsWith("asset_") -> {
                val fileName = selectedId.removePrefix("asset_")
                "backgrounds/$fileName"
            }
            else -> {
                // External background — find from cached sources
                val external = backgroundSources.firstOrNull {
                    it is BackgroundSource.External && it.id == selectedId
                } as? BackgroundSource.External
                external?.background?.cachePath
            }
        }
    }

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
    // 버퍼 용량: 녹화 Surface 설정 등 즉시 전달이 중요한 이벤트의 손실을 방지
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    private val _live2dEffect = Channel<Live2DUiEffect>(capacity = Channel.BUFFERED)
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
        val motionFiles: List<String> = emptyList(),

        // 배경
        val backgroundPath: String? = null,

        // 녹화
        val isRecordingMode: Boolean = false,
        val recordingState: ModelRecorder.RecordingState = ModelRecorder.RecordingState.IDLE,
        val recordingElapsedMs: Long = 0L,
    )

    sealed class DialogState {
        data object None : DialogState()
        data object Expression : DialogState()
        data object Motion : DialogState()
        data object SplitConfirm : DialogState()
        data class SplitProgress(val progress: Float) : DialogState()
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
        val (params, newState) = mapFacePoseUseCase(facePose, smoothingState, hasLandmarks, currentSensitivity)
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
            // 녹화 관련
            is StudioUiIntent.ToggleRecordingMode -> toggleRecordingMode()
            is StudioUiIntent.StartRecording -> requestStartRecording()
            is StudioUiIntent.StopRecording -> stopRecording()
            is StudioUiIntent.TogglePauseRecording -> togglePauseRecording()
            is StudioUiIntent.OnAudioPermissionResult -> onAudioPermissionResult(intent.granted)
            is StudioUiIntent.OnSaveLocationSelected -> onSaveLocationSelected(intent.uriString)
            is StudioUiIntent.OnSurfaceSizeAvailable -> onSurfaceSizeAvailable(intent.width, intent.height)
            // 영상/음성 분리 관련
            is StudioUiIntent.ConfirmSplit -> confirmSplit()
            is StudioUiIntent.DeclineSplit -> declineSplit()
            is StudioUiIntent.CancelSplit -> cancelSplit()
            is StudioUiIntent.OnSplitFolderSelected -> onSplitFolderSelected(intent.uriString)
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
    // Recording
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    private val modelRecorder = ModelRecorder()
    private var recordingTimerJob: Job? = null
    private var glSurfaceWidth: Int = 0
    private var glSurfaceHeight: Int = 0
    private var pendingAudioPermissionGranted: Boolean = false
    private var pendingTempFile: File? = null

    // 영상/음성 분리 관련
    private var mediaSplitter: MediaSplitter? = null
    private var splitJob: Job? = null
    private var pendingSplitResult: MediaSplitter.SplitResult? = null
    private var pendingBaseFileName: String? = null

    private fun toggleRecordingMode() {
        val current = _uiState.value
        if (current.isRecordingMode) {
            // 녹화 중이면 먼저 정지
            if (current.recordingState != ModelRecorder.RecordingState.IDLE) {
                stopRecording()
            }
            _uiState.update {
                it.copy(isRecordingMode = false)
            }
        } else {
            _uiState.update {
                it.copy(isRecordingMode = true)
            }
        }
    }

    private fun requestStartRecording() {
        // 오디오 권한 요청 Effect 전송
        _uiEffect.trySend(StudioUiEffect.RequestAudioPermission)
    }

    private fun onAudioPermissionResult(granted: Boolean) {
        pendingAudioPermissionGranted = granted
        viewModelScope.launch {
            startRecordingInternal(hasAudioPermission = granted)
        }
    }

    private suspend fun startRecordingInternal(hasAudioPermission: Boolean) {
        if (glSurfaceWidth <= 0 || glSurfaceHeight <= 0) {
            _uiEffect.trySend(StudioUiEffect.ShowSnackbar("녹화를 시작할 수 없습니다: 화면 크기를 확인할 수 없습니다"))
            return
        }

        val surface = modelRecorder.prepare(
            context = appContext,
            width = glSurfaceWidth,
            height = glSurfaceHeight,
            hasAudioPermission = hasAudioPermission,
            onMaxDuration = {
                // MediaRecorder 콜백은 메인 스레드가 아닐 수 있음
                viewModelScope.launch {
                    handleMaxDurationReached()
                }
            },
            onError = { message ->
                viewModelScope.launch {
                    handleRecordingError(message)
                }
            },
        )

        if (surface == null) {
            _uiEffect.trySend(StudioUiEffect.ShowSnackbar("녹화 준비에 실패했습니다"))
            return
        }

        // Surface를 Live2DUiEffect 채널로 GL 렌더러에 직접 전달
        // (Compose 재구성을 거치지 않아 빠르게 GL 스레드에 도달)
        val sendResult = _live2dEffect.trySend(Live2DUiEffect.SetRecordingSurface(surface))
        Log.d(TAG, "SetRecordingSurface trySend result: $sendResult, surface=$surface")

        // GL 스레드에서 Surface가 연결되고 최소 1프레임이 렌더링될 시간 확보
        // (채널 수신 -> Compose collect -> queueEvent -> GL onDrawFrame 한 사이클)
        delay(500)

        if (!modelRecorder.start()) {
            _live2dEffect.trySend(Live2DUiEffect.SetRecordingSurface(null))
            return
        }

        if (!hasAudioPermission) {
            _uiEffect.trySend(StudioUiEffect.ShowSnackbar("마이크 권한이 거부되어 음성 없이 녹화합니다"))
        }

        _uiState.update {
            it.copy(
                recordingState = ModelRecorder.RecordingState.RECORDING,
                recordingElapsedMs = 0L,
            )
        }

        startRecordingTimer()
    }

    private fun stopRecording() {
        // 이미 IDLE이면 중복 호출 방지 (타이머와 MediaRecorder 콜백이 동시에 호출할 수 있음)
        if (_uiState.value.recordingState == ModelRecorder.RecordingState.IDLE) return

        stopRecordingTimer()

        // UI 상태를 즉시 IDLE로 업데이트 (사용자 피드백 즉각 반영)
        _uiState.update {
            it.copy(
                recordingState = ModelRecorder.RecordingState.IDLE,
                recordingElapsedMs = 0L,
            )
        }

        // GL 렌더러에서 인코더 Surface를 먼저 분리한 후 MediaRecorder를 정지해야 함.
        // Surface 분리 전에 stop()을 호출하면 GL 스레드가 아직 인코더에 프레임을
        // 쓰는 중이라 -1007 (INVALID_OPERATION) 에러가 발생할 수 있음.
        _live2dEffect.trySend(Live2DUiEffect.SetRecordingSurface(null))

        viewModelScope.launch {
            // GL 스레드에서 Surface 분리가 완료될 시간 확보
            // (채널 수신 → Compose collect → queueEvent → GL 스레드 처리)
            delay(300)

            val tempFile = withContext(Dispatchers.IO) {
                val file = modelRecorder.stop()
                modelRecorder.release()
                file
            }

            if (tempFile != null && tempFile.exists() && tempFile.length() > 0) {
                pendingTempFile = tempFile
                val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                pendingBaseFileName = "LiveMotion_${dateFormat.format(Date())}"

                val hasAudio = withContext(Dispatchers.IO) { hasAudioTrack(tempFile) }
                if (hasAudio) {
                    // 오디오 트랙이 있으면 분리 확인 다이얼로그 표시
                    _uiState.update { it.copy(dialogState = DialogState.SplitConfirm) }
                } else {
                    // 오디오 트랙이 없으면 기존 단일 파일 저장 플로우
                    _uiEffect.trySend(StudioUiEffect.RequestSaveLocation("${pendingBaseFileName}.mp4"))
                }
            } else {
                withContext(Dispatchers.IO) { modelRecorder.deleteTempFile() }
                _uiEffect.trySend(StudioUiEffect.ShowSnackbar("녹화 파일 생성에 실패했습니다"))
            }
        }
    }

    private fun togglePauseRecording() {
        val currentState = _uiState.value.recordingState
        when (currentState) {
            ModelRecorder.RecordingState.RECORDING -> {
                if (modelRecorder.pause()) {
                    stopRecordingTimer()
                    _uiState.update { it.copy(recordingState = ModelRecorder.RecordingState.PAUSED) }
                }
            }
            ModelRecorder.RecordingState.PAUSED -> {
                if (modelRecorder.resume()) {
                    startRecordingTimer()
                    _uiState.update { it.copy(recordingState = ModelRecorder.RecordingState.RECORDING) }
                }
            }
            else -> { /* Ignore */ }
        }
    }

    private fun handleMaxDurationReached() {
        _uiEffect.trySend(StudioUiEffect.ShowSnackbar("최대 녹화 시간(5분)에 도달했습니다"))
        stopRecording()
    }

    private fun handleRecordingError(message: String) {
        stopRecordingTimer()
        _live2dEffect.trySend(Live2DUiEffect.SetRecordingSurface(null))
        modelRecorder.release()
        _uiState.update {
            it.copy(
                recordingState = ModelRecorder.RecordingState.IDLE,
                recordingElapsedMs = 0L,
            )
        }
        _uiEffect.trySend(StudioUiEffect.ShowErrorWithDetail(
            displayMessage = "녹화 중 오류가 발생했습니다",
            detailMessage = message,
        ))
    }

    private fun onSaveLocationSelected(uriString: String?) {
        val tempFile = pendingTempFile
        if (uriString == null || tempFile == null) {
            // 사용자가 저장을 취소한 경우
            pendingTempFile?.let {
                modelRecorder.deleteTempFile()
            }
            pendingTempFile = null
            if (uriString == null) {
                _uiEffect.trySend(StudioUiEffect.ShowSnackbar("저장이 취소되었습니다"))
            }
            return
        }

        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val uri = uriString.toUri()
                    appContext.contentResolver.openOutputStream(uri)?.use { output ->
                        tempFile.inputStream().use { input ->
                            input.copyTo(output)
                        }
                    } ?: throw Exception("출력 스트림을 열 수 없습니다")

                    // 성공 시 임시 파일 삭제
                    tempFile.delete()
                }
                pendingTempFile = null
                _uiEffect.trySend(StudioUiEffect.RecordingSaved("녹화 파일이 저장되었습니다"))
            } catch (e: Exception) {
                _uiEffect.trySend(StudioUiEffect.ShowErrorWithDetail(
                    displayMessage = "파일 저장에 실패했습니다",
                    detailMessage = e.localizedMessage ?: "알 수 없는 오류",
                ))
                // 임시 파일 유지 (재시도 가능)
            }
        }
    }

    private fun onSurfaceSizeAvailable(width: Int, height: Int) {
        glSurfaceWidth = width
        glSurfaceHeight = height
    }

    private fun startRecordingTimer() {
        recordingTimerJob?.cancel()
        recordingTimerJob = viewModelScope.launch {
            while (true) {
                delay(100)
                val elapsed = _uiState.value.recordingElapsedMs + 100
                _uiState.update { it.copy(recordingElapsedMs = elapsed) }

                // 5분 타이머 안전장치 (MediaRecorder의 maxDuration과 이중 보호)
                if (elapsed >= ModelRecorder.MAX_DURATION_MS) {
                    handleMaxDurationReached()
                    break
                }
            }
        }
    }

    private fun stopRecordingTimer() {
        recordingTimerJob?.cancel()
        recordingTimerJob = null
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 영상/음성 분리
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private fun hasAudioTrack(file: File): Boolean {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(file.absolutePath)
            (0 until extractor.trackCount).any { i ->
                val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)
                mime?.startsWith("audio/") == true
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to check audio track", e)
            false
        } finally {
            extractor.release()
        }
    }

    private fun confirmSplit() {
        val tempFile = pendingTempFile ?: return
        val baseFileName = pendingBaseFileName ?: return

        val splitter = MediaSplitter(appContext.cacheDir)
        mediaSplitter = splitter

        _uiState.update { it.copy(dialogState = DialogState.SplitProgress(0f)) }

        splitJob = viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    splitter.split(
                        sourceFile = tempFile,
                        onProgress = { progress ->
                            _uiState.update { it.copy(dialogState = DialogState.SplitProgress(progress)) }
                        },
                    )
                }
                pendingSplitResult = result
                _uiState.update { it.copy(dialogState = DialogState.None) }
                _uiEffect.trySend(StudioUiEffect.RequestSplitFolderLocation(baseFileName))
            } catch (e: CancellationException) {
                // 사용자가 취소 → SplitConfirm 다이얼로그 재표시
                withContext(Dispatchers.IO) { splitter.cleanupTempFiles() }
                _uiState.update { it.copy(dialogState = DialogState.SplitConfirm) }
            } catch (e: Exception) {
                Log.e(TAG, "Split failed", e)
                withContext(Dispatchers.IO) { splitter.cleanupTempFiles() }
                _uiState.update { it.copy(dialogState = DialogState.None) }
                _uiEffect.trySend(StudioUiEffect.ShowErrorWithDetail(
                    displayMessage = "영상/음성 분리에 실패했습니다",
                    detailMessage = e.localizedMessage ?: "알 수 없는 오류",
                ))
            }
        }
    }

    private fun declineSplit() {
        _uiState.update { it.copy(dialogState = DialogState.None) }
        // 기존 단일 파일 저장 플로우
        val fileName = "${pendingBaseFileName}.mp4"
        _uiEffect.trySend(StudioUiEffect.RequestSaveLocation(fileName))
    }

    private fun cancelSplit() {
        splitJob?.cancel()
        splitJob = null
    }

    private fun onSplitFolderSelected(uriString: String?) {
        val splitResult = pendingSplitResult
        val baseFileName = pendingBaseFileName
        val originalFile = pendingTempFile

        if (uriString == null || splitResult == null || baseFileName == null) {
            // 사용자가 폴더 선택을 취소
            cleanupAllTempFiles()
            if (uriString == null) {
                _uiEffect.trySend(StudioUiEffect.ShowSnackbar("저장이 취소되었습니다"))
            }
            return
        }

        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val treeUri = uriString.toUri()
                    val parentDir = DocumentFile.fromTreeUri(appContext, treeUri)
                        ?: throw Exception("폴더를 열 수 없습니다")

                    // 날짜시간 폴더 생성
                    val folder = parentDir.createDirectory(baseFileName)
                        ?: throw Exception("폴더를 생성할 수 없습니다")

                    // 원본 파일 복사
                    originalFile?.let { srcFile ->
                        val originalDoc = folder.createFile("video/mp4", "${baseFileName}.mp4")
                            ?: throw Exception("원본 파일을 생성할 수 없습니다")
                        appContext.contentResolver.openOutputStream(originalDoc.uri)?.use { output ->
                            srcFile.inputStream().use { input ->
                                input.copyTo(output)
                            }
                        } ?: throw Exception("원본 출력 스트림을 열 수 없습니다")
                    }

                    // 영상 파일 복사
                    val videoDoc = folder.createFile("video/mp4", "${baseFileName}_video.mp4")
                        ?: throw Exception("영상 파일을 생성할 수 없습니다")
                    appContext.contentResolver.openOutputStream(videoDoc.uri)?.use { output ->
                        splitResult.videoFile.inputStream().use { input ->
                            input.copyTo(output)
                        }
                    } ?: throw Exception("영상 출력 스트림을 열 수 없습니다")

                    // 음성 파일 복사
                    splitResult.audioFile?.let { audioFile ->
                        val audioDoc = folder.createFile("audio/mp4", "${baseFileName}_audio.m4a")
                            ?: throw Exception("음성 파일을 생성할 수 없습니다")
                        appContext.contentResolver.openOutputStream(audioDoc.uri)?.use { output ->
                            audioFile.inputStream().use { input ->
                                input.copyTo(output)
                            }
                        } ?: throw Exception("음성 출력 스트림을 열 수 없습니다")
                    }

                    // 성공 시 모든 임시 파일 정리
                    cleanupAllTempFiles()
                }
                _uiEffect.trySend(StudioUiEffect.SplitSaved("영상/음성이 분리 저장되었습니다"))

            } catch (e: Exception) {
                Log.e(TAG, "Failed to save split files", e)
                _uiEffect.trySend(StudioUiEffect.ShowErrorWithDetail(
                    displayMessage = "분리 파일 저장에 실패했습니다",
                    detailMessage = e.localizedMessage ?: "알 수 없는 오류",
                ))
                // 임시 파일 유지 (재시도 가능)
            }
        }
    }

    private fun cleanupAllTempFiles() {
        pendingTempFile?.delete()
        pendingTempFile = null
        mediaSplitter?.cleanupTempFiles()
        mediaSplitter = null
        pendingSplitResult = null
        pendingBaseFileName = null
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Cleanup
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    override fun onCleared() {
        super.onCleared()

        // 녹화 정리
        // onCleared에서는 viewModelScope가 취소되므로 channel send / delay 불가.
        // release()가 내부적으로 stop + 예외 처리를 수행하므로 직접 호출만으로 충분.
        stopRecordingTimer()
        modelRecorder.release()
        modelRecorder.deleteTempFile()
        pendingTempFile?.delete()

        // 분리 관련 정리
        splitJob?.cancel()
        mediaSplitter?.cleanupTempFiles()

        faceTracker?.stop()
        faceTracker = null
        // Live2D 리소스 정리 (view, textureManager, model, CubismFramework.dispose)
        // ViewModel은 NavBackStackEntry에 바인딩되므로 predictive back 제스처 중에는
        // cleared되지 않고, 실제 네비게이션 완료 시에만 호출됨
        LAppMinimumDelegate.getInstance().onStop()
    }

}
