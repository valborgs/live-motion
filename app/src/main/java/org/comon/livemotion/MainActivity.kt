package org.comon.livemotion

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.comon.livemotion.demo.minimum.LAppMinimumDelegate
import org.comon.livemotion.tracking.FaceToLive2DMapper
import org.comon.livemotion.tracking.FaceTracker
import org.comon.livemotion.ui.theme.LiveMotionTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LiveMotionTheme {
                MainContent()
            }
        }
    }

    @Composable
    fun MainContent() {
        val context = LocalContext.current
        val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
        
        var hasCameraPermission by remember {
            mutableStateOf(
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
            )
        }

        val launcher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
            onResult = { granted -> hasCameraPermission = granted }
        )

        LaunchedEffect(Unit) {
            if (!hasCameraPermission) {
                launcher.launch(Manifest.permission.CAMERA)
            }
        }

        if (hasCameraPermission) {
            val faceTracker = remember { FaceTracker(context, lifecycleOwner) }
            val mapper = remember { FaceToLive2DMapper() }
            val facePose by faceTracker.facePose.collectAsStateWithLifecycle()
            val isCalibrating by faceTracker.isCalibratingUI.collectAsStateWithLifecycle()
            val landmarks by faceTracker.faceLandmarks.collectAsStateWithLifecycle()
            
            // Compose state로 변환된 파라미터
            val faceParams = remember(facePose, landmarks) {
                if (landmarks.isEmpty()) {
                    mapper.reset()
                    // 얼굴 소실 시 모든 파라미터를 기본 위치(정면)로 강제 리셋
                    mapOf(
                        "ParamAngleX" to 0f,
                        "ParamAngleY" to 0f,
                        "ParamAngleZ" to 0f,
                        "ParamEyeLOpen" to 1f,
                        "ParamEyeROpen" to 1f,
                        "ParamMouthOpenY" to 0f,
                        "ParamBodyAngleX" to 0f,
                        "ParamBodyAngleY" to 0f,
                        "ParamBodyAngleZ" to 0f,
                        "ParamEyeBallX" to 0f,
                        "ParamEyeBallY" to 0f
                    )
                } else {
                    mapper.map(facePose)
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                // Background: Live2D Screen
                Live2DScreen(
                    modifier = Modifier.fillMaxSize(),
                    faceParams = faceParams
                )

                // Overlay Bottom Right: Camera Preview
                AndroidView(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 48.dp, end = 24.dp)
                        .size(120.dp, 160.dp),
                    factory = { ctx ->
                        androidx.camera.view.PreviewView(ctx).apply {
                            scaleType = androidx.camera.view.PreviewView.ScaleType.FILL_CENTER
                            faceTracker.startCamera(surfaceProvider)
                        }
                    }
                )

                // 랜드마크 오버레이 Canvas
                androidx.compose.foundation.Canvas(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 48.dp, end = 24.dp)
                        .size(120.dp, 160.dp)
                ) {
                    val canvasWidth = size.width
                    val canvasHeight = size.height
                    
                    landmarks.forEach { landmark ->
                        // FaceTracker에서 이미 90도 회전 보정이 완료된 정방향 데이터를 줌
                        // PreviewView가 미러링된 상태이므로 (1.0 - x) 적용하여 좌우 반전
                        val x = (1.0f - landmark.x()) * canvasWidth
                        val y = landmark.y() * canvasHeight
                        
                        drawCircle(
                            color = androidx.compose.ui.graphics.Color.Cyan,
                            radius = 2f,
                            center = androidx.compose.ui.geometry.Offset(x, y),
                            alpha = 0.8f
                        )
                    }
                }

                // 보정 중 오버레이 메시지
                if (isCalibrating) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            androidx.compose.material3.CircularProgressIndicator(color = androidx.compose.ui.graphics.Color.White)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "얼굴 보정 중입니다...\n5초 동안 정면을 응시해 주세요.",
                                color = androidx.compose.ui.graphics.Color.White,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                style = androidx.compose.material3.MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("카메라 권한이 필요합니다.")
            }
        }
    }

    override fun onStart() {
        super.onStart()
        LAppMinimumDelegate.getInstance().onStart(this)
    }

    override fun onStop() {
        super.onStop()
        LAppMinimumDelegate.getInstance().onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        LAppMinimumDelegate.getInstance().onDestroy()
    }
}