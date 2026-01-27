package org.comon.tracking

import android.content.Context
import android.util.Log
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
import kotlin.math.pow
import kotlin.math.sqrt

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
                .setErrorListener { error -> Log.e(TAG, "MediaPipe Error: ${error.message}") }
                .setNumFaces(1)
                .setOutputFaceBlendshapes(true) // 눈, 입 벌림 계산에 사용

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
                val cpuBaseOptions = BaseOptions.builder()
                    .setDelegate(Delegate.CPU)
                    .setModelAssetPath("face_landmarker.task")
                
                val cpuOptionsBuilder = FaceLandmarker.FaceLandmarkerOptions.builder()
                    .setBaseOptions(cpuBaseOptions.build())
                    .setRunningMode(RunningMode.LIVE_STREAM)
                    .setResultListener { result, _ -> processResult(result) }
                    .setErrorListener { error -> Log.e(TAG, "MediaPipe Error: ${error.message}") }
                    .setNumFaces(1)
                    .setOutputFaceBlendshapes(true)
                
                faceLandmarker = FaceLandmarker.createFromOptions(context, cpuOptionsBuilder.build())
                
                currentDelegate = Delegate.CPU
                _isGpuEnabled.value = false
                isInitializing = false
                
                val elapsedTime = System.currentTimeMillis() - startTime
                Log.i(TAG, "✅ FaceLandmarker 초기화 완료 - Delegate: CPU (GPU 폴백), 소요시간: ${elapsedTime}ms")
            } else {
                isInitializing = false
                throw e
            }
        }
    }

    // 카메라 관련 변수
    private var cameraProvider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var currentPreviewSurface: Preview.SurfaceProvider? = null

    /**
     * 카메라 시작 (프리뷰 없이 얼굴 추적만)
     * 프리뷰를 표시하려면 attachPreview()를 별도로 호출
     */
    fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()

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

            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                cameraProvider?.unbindAll()
                // 프리뷰와 이미지 분석 모두 바인딩
                cameraProvider?.bindToLifecycle(
                    lifecycleOwner, cameraSelector, preview, imageAnalyzer
                )
                Log.d(TAG, "📷 카메라 시작 완료")
            } catch (e: Exception) {
                Log.e(TAG, "Use case binding failed", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /**
     * 프리뷰 연결 (카메라가 이미 시작된 상태에서 호출)
     */
    fun attachPreview(surfaceProvider: Preview.SurfaceProvider) {
        currentPreviewSurface = surfaceProvider
        preview?.surfaceProvider = surfaceProvider
        Log.d(TAG, "📷 프리뷰 연결됨")
    }

    /**
     * 프리뷰 해제 (카메라는 계속 실행)
     */
    fun detachPreview() {
        preview?.surfaceProvider = null
        currentPreviewSurface = null
        Log.d(TAG, "📷 프리뷰 해제됨")
    }

    private fun analyzeImage(imageProxy: ImageProxy) {
        // 초기화 중이거나 FaceLandmarker가 없으면 프레임 건너뛰기
        if (isInitializing || faceLandmarker == null) {
            imageProxy.close()
            return
        }
        
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
        // 사용자 피드백 반영: 이전 보정 시 뒤집힌 현상을 해결하기 위해 180도 추가 회전 적용
        // 최종 공식 (90도 CCW): x' = y, y' = 1.0 - x
        val rotatedLandmarks = rawLandmarks.map { 
            NormalizedLandmark.create(it.y(), 1.0f - it.x(), it.z())
        }
        _faceLandmarks.value = rotatedLandmarks

        val blendshapesList = if (result.faceBlendshapes().isPresent) result.faceBlendshapes().get() else null
        val classifications = blendshapesList?.getOrNull(0) ?: return
        val scores = classifications.associate { it.categoryName() to it.score() }

        // 보정된 랜드마크로 Orientation (Euler angles) 추정
        var pose = calculatePose(rotatedLandmarks, scores)
        
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

    private fun calculatePose(landmarks: List<NormalizedLandmark>, scores: Map<String, Float>): FacePose {

        // 주요 포인트: 코(4), 턱(152), 왼눈(33), 오른눈(263)
        val nose = landmarks[4]
        val chin = landmarks[152]
        val leftEye = landmarks[33]
        val rightEye = landmarks[263]

        // 눈 사이 거리 (좌우 회전 계산용)
        val eyeDist = sqrt((rightEye.x() - leftEye.x()).pow(2) + (rightEye.y() - leftEye.y()).pow(2))
        
        // 안면 세로 거리 (상하 회전 정규화용: 좌우 회전 시에도 비교적 안정적)
        val faceHeight = sqrt((chin.x() - nose.x()).pow(2) + (chin.y() - nose.y()).pow(2))
        val normFactor = 1.0f / (faceHeight.coerceAtLeast(0.05f))

        // 1. Yaw (좌우 회전): 정규화된 값 (-1.0 ~ 1.0) 추출
        // 거울 모드: 전면 카메라이므로 방향 반전 (- 부호)
        val yawNorm = -(rightEye.z() - leftEye.z()) * 15f

        // 2. Pitch (상하 회전): Z좌표(깊이) 기반 계산
        // Y좌표는 Yaw 회전 시 원근법 왜곡이 심하므로 Z좌표 사용
        // 고개를 숙이면 코끝이 앞으로(Z 감소), 들면 뒤로(Z 증가)
        val noseBridge = landmarks[6]  // 코 다리 (미간 근처)
        val pitchZ = nose.z() - noseBridge.z()  // 코끝과 코 다리의 Z 차이
        val pitchNorm = pitchZ * 15f  // 민감도 조정

        // 3. Roll (기울기): 실측 각도를 정규화 (-1.0 ~ 1.0)
        // 거울 모드: 사용자와 같은 방향으로 기울어지도록 반전 (- 부호)
        val rollDeg = atan2(rightEye.y() - leftEye.y(), rightEye.x() - leftEye.x()) * (180 / Math.PI).toFloat()
        val rollNorm = -rollDeg / 20f

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
        val mouthRaw = scores["jawOpen"] ?: 0f
        
        // 입 벌림 임계값 적용: 작은 값(노이즈)은 0으로 처리
        // 0.15 이하는 닫힌 입으로 간주하고, 이후 값을 0~1로 재정규화
        val mouthThreshold = 0.15f
        val mouth = ((mouthRaw - mouthThreshold) / (1f - mouthThreshold)).coerceIn(0f, 1f)
        
        // mouthForm: 미소 정도 계산 (왼쪽/오른쪽 미소 평균)
        val mouthSmileL = scores["mouthSmileLeft"] ?: 0f
        val mouthSmileR = scores["mouthSmileRight"] ?: 0f
        val mouthForm = (mouthSmileL + mouthSmileR) / 2f

        return FacePose(
            yaw = yawNorm.coerceIn(-1.5f, 1.5f),
            pitch = pitchNorm.coerceIn(-1.5f, 1.5f),
            roll = rollNorm.coerceIn(-1.5f, 1.5f),
            eyeLOpen = 1f - eyeL,
            eyeROpen = 1f - eyeR,
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

    private fun scoresToOpen(blinkScore: Float?): Float {
        return blinkScore ?: 0f
    }

    fun stop() {
        faceLandmarker?.close()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "FaceTracker"
    }
}
