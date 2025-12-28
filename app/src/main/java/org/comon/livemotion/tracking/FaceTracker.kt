package org.comon.livemotion.tracking

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
        setupFaceLandmarker()
    }

    private fun setupFaceLandmarker() {
        val baseOptionsBuilder = BaseOptions.builder()
            .setDelegate(Delegate.CPU) // GPU 사용 시 초기화 이슈가 있을 수 있어 우선 CPU 권장
            .setModelAssetPath("face_landmarker.task") // assets에 있어야 함

        val optionsBuilder = FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(baseOptionsBuilder.build())
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setResultListener { result, _ -> processResult(result) }
            .setErrorListener { error -> Log.e(TAG, "MediaPipe Error: ${error.message}") }
            .setNumFaces(1)
            .setOutputFaceBlendshapes(true) // 눈, 입 벌림 계산에 사용

        faceLandmarker = FaceLandmarker.createFromOptions(context, optionsBuilder.build())
    }

    fun startCamera(previewSurface: Preview.SurfaceProvider) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewSurface
            }

            val imageAnalyzer = ImageAnalysis.Builder()
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
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner, cameraSelector, preview, imageAnalyzer
                )
            } catch (e: Exception) {
                Log.e(TAG, "Use case binding failed", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun analyzeImage(imageProxy: ImageProxy) {
        val bitmap = imageProxy.toBitmap()
        val mpImage = BitmapImageBuilder(bitmap).build()
        
        // 카메라 센서의 회전 정보를 MediaPipe에 전달
        val imageProcessingOptions = ImageProcessingOptions.builder()
            .setRotationDegrees(imageProxy.imageInfo.rotationDegrees)
            .build()
            
        faceLandmarker?.detectAsync(mpImage, imageProcessingOptions, System.currentTimeMillis())
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
        // Mirroring: 전면 카메라이므로 방향 반전
        val yawNorm = -(rightEye.z() - leftEye.z()) * 15f

        // 2. Pitch (상하 회전): 정규화된 값 (-1.0 ~ 1.0) 추출
        // Yaw 회전에 따른 원근 오차를 최소화하기 위해 눈 안쪽 구석(inner corner) 포인트 사용
        val eyeLInner = landmarks[133]
        val eyeRInner = landmarks[362]
        val eyeYCenter = (eyeLInner.y() + eyeRInner.y()) / 2
        
        // 고개를 좌우로 돌릴 때 원근법 때문에 눈 높이가 낮아 보이는 현상을 상쇄하기 위한 보정
        // Yaw 값이 클수록(좌우로 많이 돌릴수록) Pitch 값을 약간 들어올려(더해줌) 수평을 유지
        val yawCorrection = kotlin.math.abs(yawNorm) * 0.05f 
        
        val pitchPoint = (eyeYCenter - nose.y()) + yawCorrection
        // 민감도를 다시 조정하여 자연스러운 움직임 유도
        val pitchNorm = pitchPoint * 6f * normFactor

        // 3. Roll (기울기): 실측 각도를 정규화 (-1.0 ~ 1.0)
        // Mirroring: 방향 보정
        val rollDeg = atan2(rightEye.y() - leftEye.y(), rightEye.x() - leftEye.x()) * (180 / Math.PI).toFloat()
        val rollNorm = rollDeg / 20f // 거울 모드 대응을 위해 - 부호 제거

        // 개폐 정도 (Blendshapes)
        val eyeL = scores["eyeBlinkLeft"] ?: 0f
        val eyeR = scores["eyeBlinkRight"] ?: 0f
        val mouth = scores["jawOpen"] ?: 0f

        return FacePose(
            yaw = yawNorm.coerceIn(-1.5f, 1.5f),
            pitch = (pitchNorm + 0.1f).coerceIn(-1.5f, 1.5f),
            roll = rollNorm.coerceIn(-1.5f, 1.5f),
            eyeLOpen = 1f - eyeL,
            eyeROpen = 1f - eyeR,
            mouthOpen = mouth
        )
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
