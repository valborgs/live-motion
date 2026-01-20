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
            val isGpuEnabled by faceTracker.isGpuEnabled.collectAsStateWithLifecycle()
            
            // 확대/이동 모드 상태
            var isZoomEnabled by remember { androidx.compose.runtime.mutableStateOf(false) }
            var isMoveEnabled by remember { androidx.compose.runtime.mutableStateOf(false) }
            var isPreviewVisible by remember { androidx.compose.runtime.mutableStateOf(true) }
            
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
                        "ParamMouthForm" to 0f,
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
                    faceParams = faceParams,
                    isZoomEnabled = isZoomEnabled,
                    isMoveEnabled = isMoveEnabled
                )

                // 카메라 시작 (프리뷰와 독립적으로 한 번만 실행)
                LaunchedEffect(Unit) {
                    faceTracker.startCamera()
                }

                // 프리뷰가 보일 때만 PreviewView 표시 및 연결
                if (isPreviewVisible) {
                    AndroidView(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(bottom = 48.dp, end = 24.dp)
                            .size(120.dp, 160.dp),
                        factory = { ctx ->
                            androidx.camera.view.PreviewView(ctx).apply {
                                scaleType = androidx.camera.view.PreviewView.ScaleType.FILL_CENTER
                                // 프리뷰 연결
                                faceTracker.attachPreview(surfaceProvider)
                            }
                        },
                        onRelease = {
                            // 프리뷰 해제 (View가 제거될 때)
                            faceTracker.detachPreview()
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
                
                // GPU/CPU 전환 토글 버튼 (오른쪽 상단)
                Column(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 48.dp, end = 16.dp),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                ) {
                    // GPU/CPU 토글
                    Row(
                        modifier = Modifier
                            .background(
                                color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isGpuEnabled) "GPU 🚀" else "CPU",
                            color = androidx.compose.ui.graphics.Color.White,
                            style = androidx.compose.material3.MaterialTheme.typography.labelSmall
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        androidx.compose.material3.Switch(
                            checked = isGpuEnabled,
                            onCheckedChange = { faceTracker.setGpuEnabled(it) },
                            modifier = Modifier.height(24.dp)
                        )
                    }
                    
                    // 확대 토글
                    Row(
                        modifier = Modifier
                            .background(
                                color = if (isZoomEnabled) 
                                    androidx.compose.ui.graphics.Color.Blue.copy(alpha = 0.7f)
                                else 
                                    androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🔍 확대",
                            color = androidx.compose.ui.graphics.Color.White,
                            style = androidx.compose.material3.MaterialTheme.typography.labelSmall
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        androidx.compose.material3.Switch(
                            checked = isZoomEnabled,
                            onCheckedChange = { isZoomEnabled = it },
                            modifier = Modifier.height(24.dp)
                        )
                    }
                    
                    // 이동 토글
                    Row(
                        modifier = Modifier
                            .background(
                                color = if (isMoveEnabled) 
                                    androidx.compose.ui.graphics.Color.Magenta.copy(alpha = 0.7f)
                                else 
                                    androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "↕️ 이동",
                            color = androidx.compose.ui.graphics.Color.White,
                            style = androidx.compose.material3.MaterialTheme.typography.labelSmall
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        androidx.compose.material3.Switch(
                            checked = isMoveEnabled,
                            onCheckedChange = { isMoveEnabled = it },
                            modifier = Modifier.height(24.dp)
                        )
                    }
                    
                    // 카메라 프리뷰 토글
                    Row(
                        modifier = Modifier
                            .background(
                                color = if (isPreviewVisible) 
                                    androidx.compose.ui.graphics.Color.Cyan.copy(alpha = 0.7f)
                                else 
                                    androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "📷 프리뷰",
                            color = androidx.compose.ui.graphics.Color.White,
                            style = androidx.compose.material3.MaterialTheme.typography.labelSmall
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        androidx.compose.material3.Switch(
                            checked = isPreviewVisible,
                            onCheckedChange = { isPreviewVisible = it },
                            modifier = Modifier.height(24.dp)
                        )
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