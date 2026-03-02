package org.comon.tracking

import android.content.Context
import android.hardware.display.DisplayManager
import android.util.Log
import android.view.Display
import android.view.Surface
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.comon.domain.model.FacePose
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.atan2

/**
 * 트래킹 에러 타입
 */
sealed class TrackingError {
    data class FaceLandmarkerInitError(val message: String) : TrackingError()
    data class CameraError(val message: String) : TrackingError()
    data class MediaPipeRuntimeError(val message: String) : TrackingError()
}

/**
 * CameraX 프레임을 수신하여 MediaPipe Face Landmarker로 얼굴 데이터를 추출하는 클래스
 */
class FaceTracker(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner
) {
    private val _facePose = MutableStateFlow(FacePose())
    val facePose: StateFlow<FacePose> = _facePose

    // 얼굴 랜드마크 데이터를 UI에 전달하기 위한 Flow
    private val _faceLandmarks = MutableStateFlow<List<NormalizedLandmark>>(emptyList())
    val faceLandmarks: StateFlow<List<NormalizedLandmark>> = _faceLandmarks

    // UI에 보정 상태를 알리기 위한 Flow
    private val _isCalibratingUI = MutableStateFlow(false)
    val isCalibratingUI: StateFlow<Boolean> = _isCalibratingUI

    // GPU/CPU 가속 상태
    private val _isGpuEnabled = MutableStateFlow(false)  // 기본값: CPU
    val isGpuEnabled: StateFlow<Boolean> = _isGpuEnabled
    private var currentDelegate: Delegate = Delegate.CPU

    // 에러 상태 Flow
    private val _error = MutableStateFlow<TrackingError?>(null)
    val error: StateFlow<TrackingError?> = _error

    /**
     * 에러 상태 초기화 (에러 확인 후 호출)
     */
    fun clearError() {
        _error.value = null
    }
    
    // FaceLandmarker 초기화 중 플래그 (재초기화 중 분석 방지)
    @Volatile
    private var isInitializing = false

    private var faceLandmarker: FaceLandmarker? = null
    private var cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    // 자동 보정(Auto-Calibration) 관련 변수
    private var isCalibrated = false
    private var isCalibrating = false
    private var calibrationStartTime: Long = 0
    private var calibrationSampleCount = 0
    private var sumYaw = 0f
    private var sumPitch = 0f
    private var sumRoll = 0f
    
    // 계산된 오프셋
    private var offsetYaw = 0f
    private var offsetPitch = 0f
    private var offsetRoll = 0f

    // 얼굴 감지 유예 시간(Grace Period) 관련 변수
    private var lastFaceDetectedTime: Long = 0
    private val GRACE_PERIOD_MS = 3000L

    init {
        // init에서는 초기화하지 않고 Compose의 LaunchedEffect 등에서 명시적으로 호출하도록 함
    }

    /**
     * GPU/CPU 가속 전환
     * @param useGpu true면 GPU, false면 CPU 사용
     */
    fun setGpuEnabled(useGpu: Boolean) {
        if (_isGpuEnabled.value == useGpu) return
        
        Log.i(TAG, "🔄 Delegate 전환 요청: ${if (useGpu) "GPU" else "CPU"}")
        
        // 재초기화 중 분석 방지
        isInitializing = true
        
        // 기존 FaceLandmarker 정리
        faceLandmarker?.close()
        faceLandmarker = null
        
        // 새 delegate로 재초기화
        setupFaceLandmarker(useGpu = useGpu)
    }

    fun setupFaceLandmarker(useGpu: Boolean = true) {
        isInitializing = true
        val startTime = System.currentTimeMillis()
        
        // 요청된 delegate 설정
        val requestedDelegate = if (useGpu) Delegate.GPU else Delegate.CPU
        
        Log.i(TAG, "🔧 FaceLandmarker 초기화 시작 - 요청: ${if (useGpu) "GPU" else "CPU"}")
        
        val baseOptionsBuilder = BaseOptions.builder()
            .setDelegate(requestedDelegate)
            .setModelAssetPath("face_landmarker.task") // assets에 있어야 함

        try {
            val optionsBuilder = FaceLandmarker.FaceLandmarkerOptions.builder()
                .setBaseOptions(baseOptionsBuilder.build())
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setResultListener { result, _ -> processResult(result) }
                .setErrorListener { error ->
                    Log.e(TAG, "MediaPipe Error: ${error.message}")
                    _error.value = TrackingError.MediaPipeRuntimeError(error.message ?: "Unknown MediaPipe error")
                }
                .setNumFaces(1)
                .setOutputFaceBlendshapes(true) // 눈, 입 벌림 계산에 사용
                .setOutputFacialTransformationMatrixes(true) // Head Pose (Yaw, Pitch, Roll) 계산에 사용

            faceLandmarker = FaceLandmarker.createFromOptions(context, optionsBuilder.build())

            currentDelegate = requestedDelegate
            _isGpuEnabled.value = (currentDelegate == Delegate.GPU)
            isInitializing = false

            val elapsedTime = System.currentTimeMillis() - startTime
            Log.i(TAG, "✅ FaceLandmarker 초기화 완료 - Delegate: ${if (currentDelegate == Delegate.GPU) "GPU 🚀" else "CPU"}, 소요시간: ${elapsedTime}ms")
        } catch (e: Exception) {
            // GPU로 초기화 실패 시 CPU로 재시도
            if (requestedDelegate == Delegate.GPU) {
                Log.w(TAG, "⚠️ GPU로 FaceLandmarker 초기화 실패, CPU로 재시도: ${e.message}")
                try {
                    val cpuBaseOptions = BaseOptions.builder()
                        .setDelegate(Delegate.CPU)
                        .setModelAssetPath("face_landmarker.task")

                    val cpuOptionsBuilder = FaceLandmarker.FaceLandmarkerOptions.builder()
                        .setBaseOptions(cpuBaseOptions.build())
                        .setRunningMode(RunningMode.LIVE_STREAM)
                        .setResultListener { result, _ -> processResult(result) }
                        .setErrorListener { error ->
                            Log.e(TAG, "MediaPipe Error: ${error.message}")
                            _error.value = TrackingError.MediaPipeRuntimeError(error.message ?: "Unknown MediaPipe error")
                        }
                        .setNumFaces(1)
                        .setOutputFaceBlendshapes(true)
                        .setOutputFacialTransformationMatrixes(true)

                    faceLandmarker = FaceLandmarker.createFromOptions(context, cpuOptionsBuilder.build())

                    currentDelegate = Delegate.CPU
                    _isGpuEnabled.value = false
                    isInitializing = false

                    val elapsedTime = System.currentTimeMillis() - startTime
                    Log.i(TAG, "✅ FaceLandmarker 초기화 완료 - Delegate: CPU (GPU 폴백), 소요시간: ${elapsedTime}ms")
                } catch (cpuException: Exception) {
                    isInitializing = false
                    Log.e(TAG, "❌ FaceLandmarker 초기화 완전 실패: ${cpuException.message}")
                    _error.value = TrackingError.FaceLandmarkerInitError(
                        "얼굴 인식 초기화 실패: ${cpuException.message ?: "알 수 없는 오류"}"
                    )
                }
            } else {
                isInitializing = false
                Log.e(TAG, "❌ FaceLandmarker 초기화 실패: ${e.message}")
                _error.value = TrackingError.FaceLandmarkerInitError(
                    "얼굴 인식 초기화 실패: ${e.message ?: "알 수 없는 오류"}"
                )
            }
        }
    }

    // 카메라 관련 변수
    private var cameraProvider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null

    // 카메라 준비 상태
    @Volatile
    private var isCameraReady = false

    // 현재 프레임의 회전 각도 (landscape 대응용)
    @Volatile
    private var currentRotationDegrees = 0

    /**
     * 카메라 시작 (프리뷰 없이 얼굴 추적만)
     * 프리뷰를 표시하려면 attachPreview()를 별도로 호출
     */
    fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()

                val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

                // 전면 카메라 존재 여부 확인
                cameraProvider?.let {
                    if (!it.hasCamera(cameraSelector)) {
                        Log.e(TAG, "❌ 전면 카메라가 없습니다")
                        _error.value = TrackingError.CameraError(
                            "전면 카메라를 찾을 수 없습니다. 이 앱은 전면 카메라가 필요합니다."
                        )
                        return@addListener
                    }
                }

                // 프리뷰 UseCase (초기에는 surfaceProvider 없음)
                preview = Preview.Builder().build()

                // ImageAnalysis UseCase (얼굴 추적용)
                imageAnalyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor) { imageProxy ->
                            analyzeImage(imageProxy)
                        }
                    }

                cameraProvider?.unbindAll()
                // 프리뷰와 이미지 분석 모두 바인딩
                cameraProvider?.bindToLifecycle(
                    lifecycleOwner, cameraSelector, preview, imageAnalyzer
                )

                isCameraReady = true
                Log.d(TAG, "📷 카메라 시작 완료")
            } catch (e: Exception) {
                Log.e(TAG, "카메라 시작 실패", e)
                _error.value = TrackingError.CameraError(
                    "카메라 시작 실패: ${e.message ?: "알 수 없는 오류"}"
                )
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun analyzeImage(imageProxy: ImageProxy) {
        // 초기화 중이거나 FaceLandmarker가 없으면 프레임 건너뛰기
        if (isInitializing || faceLandmarker == null) {
            imageProxy.close()
            return
        }

        // 디스플레이 회전에 맞춰 targetRotation 갱신 (configChanges 대응)
        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
        val displayRotation = displayManager?.getDisplay(Display.DEFAULT_DISPLAY)?.rotation
            ?: Surface.ROTATION_0
        imageAnalyzer?.targetRotation = displayRotation

        // 현재 프레임의 rotationDegrees 저장 (processResult에서 사용)
        currentRotationDegrees = imageProxy.imageInfo.rotationDegrees

        val bitmap = imageProxy.toBitmap()
        val mpImage = BitmapImageBuilder(bitmap).build()

        // 카메라 센서의 회전 정보를 MediaPipe에 전달
        val imageProcessingOptions = ImageProcessingOptions.builder()
            .setRotationDegrees(imageProxy.imageInfo.rotationDegrees)
            .build()

        try {
            faceLandmarker?.detectAsync(mpImage, imageProcessingOptions, System.currentTimeMillis())
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ FaceLandmarker 분석 실패 (재초기화 중일 수 있음): ${e.message}")
        }
        imageProxy.close()
    }

    private fun processResult(result: FaceLandmarkerResult) {
        val landmarksList = result.faceLandmarks()
        val currentTime = System.currentTimeMillis()

        if (landmarksList.isEmpty()) {
            // 얼굴이 감지되지 않을 때 유예 시간(3초) 확인
            if (currentTime - lastFaceDetectedTime > GRACE_PERIOD_MS) {
                // 3초가 지나면 최종적으로 리셋
                _faceLandmarks.value = emptyList()
                _facePose.value = FacePose()
                
                if (isCalibrated || isCalibrating) {
                    resetCalibration()
                }
            } else {
                // 유예 시간 동안은 아무것도 하지 않음 (마지막 상태 유지)
            }
            return
        }

        // 얼굴이 감지됨
        lastFaceDetectedTime = currentTime

        val rawLandmarks = landmarksList[0]
        // 화면 방향에 따른 동적 랜드마크 회전 (portrait/landscape 모두 대응)
        val rotatedLandmarks = rawLandmarks.map { lm ->
            when (currentRotationDegrees) {
                0 -> NormalizedLandmark.create(lm.x(), lm.y(), lm.z())
                90 -> NormalizedLandmark.create(1.0f - lm.y(), lm.x(), lm.z())
                180 -> NormalizedLandmark.create(1.0f - lm.x(), 1.0f - lm.y(), lm.z())
                270 -> NormalizedLandmark.create(lm.y(), 1.0f - lm.x(), lm.z())
                else -> NormalizedLandmark.create(lm.y(), 1.0f - lm.x(), lm.z())
            }
        }
        _faceLandmarks.value = rotatedLandmarks

        val blendshapesList = if (result.faceBlendshapes().isPresent) result.faceBlendshapes().get() else null
        val classifications = blendshapesList?.getOrNull(0) ?: return
        val scores = classifications.associate { it.categoryName() to it.score() }

        // Transformation Matrix (4x4) 추출
        val matrixList = if (result.facialTransformationMatrixes().isPresent) result.facialTransformationMatrixes().get() else null
        val transformationMatrix = matrixList?.getOrNull(0)

        // 보정된 랜드마크로 Orientation (Euler angles) 추정
        var pose = calculatePose(rotatedLandmarks, scores, transformationMatrix)
        
        // 자동 보정 로직
        if (!isCalibrated) {
            if (!isCalibrating) {
                // 보정 시작
                isCalibrating = true
                _isCalibratingUI.value = true
                calibrationStartTime = currentTime
                Log.d(TAG, "[Calibration] Starting... Keep neutral pose for 5s.")
            }
            
            if (currentTime - calibrationStartTime < 5000) {
                // 데이터 수집 중
                sumYaw += pose.yaw
                sumPitch += pose.pitch
                sumRoll += pose.roll
                calibrationSampleCount++
            } else {
                // 데이터 수집 완료 및 오프셋 확정
                if (calibrationSampleCount > 0) {
                    offsetYaw = sumYaw / calibrationSampleCount
                    offsetPitch = sumPitch / calibrationSampleCount
                    offsetRoll = sumRoll / calibrationSampleCount
                    isCalibrated = true
                    isCalibrating = false
                    _isCalibratingUI.value = false
                    Log.d(TAG, "[Calibration] Finished. Offsets: Yaw=$offsetYaw, Pitch=$offsetPitch, Roll=$offsetRoll")
                }
            }
        }

        // 보정된 값 적용 (보정 후)
        if (isCalibrated) {
            pose = pose.copy(
                yaw = pose.yaw - offsetYaw,
                pitch = pose.pitch - offsetPitch,
                roll = pose.roll - offsetRoll
            )
        }
        
        Log.d(TAG, "FaceData: $pose")
        _facePose.value = pose
    }

    private fun resetCalibration() {
        isCalibrated = false
        isCalibrating = false
        _isCalibratingUI.value = false
        calibrationStartTime = 0
        calibrationSampleCount = 0
        sumYaw = 0f
        sumPitch = 0f
        sumRoll = 0f
        offsetYaw = 0f
        offsetPitch = 0f
        offsetRoll = 0f
        Log.d(TAG, "[Calibration] Reset due to face loss.")
    }

    private fun calculatePose(
        landmarks: List<NormalizedLandmark>, 
        scores: Map<String, Float>, 
        transformationMatrix: FloatArray?
    ): FacePose {
        var yawNorm: Float
        var pitchNorm: Float
        var rollNorm: Float

        if (transformationMatrix != null && transformationMatrix.size >= 16) {
            // Transformation Matrix (4x4, row-major 배열로 가정)에서 Rotation Matrix(3x3) 성분 추출
            // m00 m01 m02 m03
            // m10 m11 m12 m13
            // m20 m21 m22 m23
            // m30 m31 m32 m33

            // Roll (Z축 회전): atan2(m10, m00)
            val rollRad = Math.atan2(transformationMatrix[4].toDouble(), transformationMatrix[0].toDouble())
            
            // Pitch (X축 회전): asin(-m20)
            val pitchRad = Math.asin(-transformationMatrix[8].toDouble())
            
            // Yaw (Y축 회전): atan2(m21, m22)
            val yawRad = Math.atan2(transformationMatrix[9].toDouble(), transformationMatrix[10].toDouble())

            // MediaPipe Matrix -> 아바타 제어 방향에 맞게 스케일 및 부호 조정
            // 카메라에 비치는 거울 모드(사용자의 실제 움직임 방향과 화면상 아바타의 이동 방향 일치)
            val pitchDeg = Math.toDegrees(pitchRad).toFloat()
            val yawDeg = Math.toDegrees(yawRad).toFloat()
            val rollDeg = Math.toDegrees(rollRad).toFloat()

            // 경험적 민감도(Scale) 및 거울반전 부호 세팅
            // 기존 - 에서 + 로 변경하여 사용자와 거울처럼 동일한 방향으로 움직이도록 함
            yawNorm = (yawDeg / 40f)     
            pitchNorm = (pitchDeg / 40f) 
            rollNorm = (rollDeg / 40f)   
        } else {
            // Matrix 정보가 없을 경우 (Fallback) 기존 랜드마크 좌표 기반 계산 사용
            val nose = landmarks[4]
            val leftEye = landmarks[33]
            val rightEye = landmarks[263]
            val noseBridge = landmarks[6]

            yawNorm = -(rightEye.z() - leftEye.z()) * 15f
            val pitchZ = nose.z() - noseBridge.z()
            pitchNorm = pitchZ * 15f
            
            val rollDegFallback = atan2(rightEye.y() - leftEye.y(), rightEye.x() - leftEye.x()) * (180 / Math.PI).toFloat()
            rollNorm = -rollDegFallback / 20f
        }

        // ===========================================
        // 4. 시선 추적 (Iris Tracking)
        // ===========================================
        // MediaPipe Face Landmarker 눈동자 랜드마크:
        // - 왼쪽 눈동자 중심: 468
        // - 오른쪽 눈동자 중심: 473
        val (eyeBallX, eyeBallY) = calculateIrisPosition(landmarks)

        // ===========================================
        // 5. 개폐 정도 (Blendshapes)
        // ===========================================
        val eyeL = scores["eyeBlinkRight"] ?: 0f
        val eyeR = scores["eyeBlinkLeft"] ?: 0f
        val eyeWideL = scores["eyeWideRight"] ?: 0f
        val eyeWideR = scores["eyeWideLeft"] ?: 0f
        
        val mouthRaw = scores["jawOpen"] ?: 0f
        
        // 입 벌림 임계값 적용: 작은 값(노이즈)은 0으로 처리
        // 0.15 이하는 닫힌 입으로 간주하고, 이후 값을 0~1로 재정규화
        val mouthThreshold = 0.15f
        val mouth = ((mouthRaw - mouthThreshold) / (1f - mouthThreshold)).coerceIn(0f, 1f)
        
        // mouthForm: 미소 정도 계산 (왼쪽/오른쪽 미소 평균)
        val mouthSmileL = scores["mouthSmileLeft"] ?: 0f
        val mouthSmileR = scores["mouthSmileRight"] ?: 0f
        val mouthForm = (mouthSmileL + mouthSmileR) / 2f

        // 눈 뜬 정도 = (1 - 감은 정도) + (크게 뜬 정도 * 가중치)
        // eyeWide는 보통 0~1 사이 값이지만 잘 안 나오는 경향이 있어 가중치를 줌
        val openL = (1f - eyeL) + (eyeWideL * 0.8f)
        val openR = (1f - eyeR) + (eyeWideR * 0.8f)

        return FacePose(
            yaw = yawNorm.coerceIn(-1.5f, 1.5f),
            pitch = pitchNorm.coerceIn(-1.5f, 1.5f),
            roll = rollNorm.coerceIn(-1.5f, 1.5f),
            eyeLOpen = openL,
            eyeROpen = openR,
            mouthOpen = mouth,
            mouthForm = mouthForm.coerceIn(0f, 1f),
            eyeBallX = eyeBallX,
            eyeBallY = eyeBallY
        )
    }

    /**
     * Iris(눈동자) 랜드마크를 사용하여 시선 방향을 계산합니다.
     * 
     * MediaPipe Face Landmarker 눈동자 인덱스:
     * - 왼쪽 눈동자 중심: 468, 주변: 469~471
     * - 오른쪽 눈동자 중심: 473, 주변: 474~476
     * - 왼쪽 눈 외곽: outer=33, inner=133
     * - 오른쪽 눈 외곽: outer=263, inner=362
     * 
     * @return Pair(eyeBallX, eyeBallY) 정규화된 시선 좌표 (-1 ~ 1)
     */
    private fun calculateIrisPosition(landmarks: List<NormalizedLandmark>): Pair<Float, Float> {
        // 랜드마크가 충분하지 않으면 기본값 반환 (Face Landmarker는 478개 랜드마크 제공)
        if (landmarks.size < 478) {
            return Pair(0f, 0f)
        }

        // 왼쪽 눈
        val irisL = landmarks[468]      // 왼쪽 눈동자 중심
        val eyeLOuter = landmarks[33]   // 왼쪽 눈 바깥쪽
        val eyeLInner = landmarks[133]  // 왼쪽 눈 안쪽
        val eyeLTop = landmarks[159]    // 왼쪽 눈 위쪽
        val eyeLBottom = landmarks[145] // 왼쪽 눈 아래쪽

        // 오른쪽 눈
        val irisR = landmarks[473]      // 오른쪽 눈동자 중심
        val eyeROuter = landmarks[263]  // 오른쪽 눈 바깥쪽
        val eyeRInner = landmarks[362]  // 오른쪽 눈 안쪽
        val eyeRTop = landmarks[386]    // 오른쪽 눈 위쪽
        val eyeRBottom = landmarks[374] // 오른쪽 눈 아래쪽

        // 왼쪽 눈: 눈동자의 상대적 위치 계산
        val eyeLWidth = eyeLInner.x() - eyeLOuter.x()
        val eyeLHeight = eyeLBottom.y() - eyeLTop.y()
        val irisLRelX = if (eyeLWidth > 0.001f) {
            (irisL.x() - eyeLOuter.x()) / eyeLWidth
        } else 0.5f
        val irisLRelY = if (eyeLHeight > 0.001f) {
            (irisL.y() - eyeLTop.y()) / eyeLHeight
        } else 0.5f

        // 오른쪽 눈: 눈동자의 상대적 위치 계산
        val eyeRWidth = eyeROuter.x() - eyeRInner.x()
        val eyeRHeight = eyeRBottom.y() - eyeRTop.y()
        val irisRRelX = if (eyeRWidth > 0.001f) {
            (irisR.x() - eyeRInner.x()) / eyeRWidth
        } else 0.5f
        val irisRRelY = if (eyeRHeight > 0.001f) {
            (irisR.y() - eyeRTop.y()) / eyeRHeight
        } else 0.5f

        // 양쪽 눈 평균
        val avgRelX = (irisLRelX + irisRRelX) / 2f
        val avgRelY = (irisLRelY + irisRRelY) / 2f

        // 정규화: 중앙(0.5) 기준으로 -1 ~ 1 범위로 변환
        // 거울 모드: X축 반전 (- 부호)
        val eyeBallX = -((avgRelX - 0.5f) * 2f).coerceIn(-1f, 1f)
        // Y축: 위쪽이 양수, 아래쪽이 음수
        val eyeBallY = -((avgRelY - 0.5f) * 2f).coerceIn(-1f, 1f)

        return Pair(eyeBallX, eyeBallY)
    }

    fun stop() {
        faceLandmarker?.close()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "FaceTracker"
    }
}
