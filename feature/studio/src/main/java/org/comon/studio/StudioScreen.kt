package org.comon.studio

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.window.Dialog
import org.comon.live2d.LAppMinimumLive2DManager
import android.content.res.AssetManager
import java.io.IOException
import org.comon.domain.model.FacePose
import org.comon.live2d.Live2DScreen
import org.comon.tracking.FaceToLive2DMapper
import org.comon.tracking.FaceTracker

@Composable
fun StudioScreen(
    modelId: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    // 모델 로딩 로직은 이제 Live2DScreen 내부에서 처리됨 (GLThread 안정성 위해)

    val faceTracker = remember { FaceTracker(context, lifecycleOwner) }
    
    // FaceTracker 생명주기 관리
    DisposableEffect(faceTracker) {
        onDispose {
            faceTracker.stop()
        }
    }
    
    val mapper = remember { FaceToLive2DMapper() }
    val facePose by faceTracker.facePose.collectAsStateWithLifecycle()
    val isCalibrating by faceTracker.isCalibratingUI.collectAsStateWithLifecycle()
    val landmarks by faceTracker.faceLandmarks.collectAsStateWithLifecycle()
    val isGpuEnabled by faceTracker.isGpuEnabled.collectAsStateWithLifecycle()
    
    var isZoomEnabled by remember { mutableStateOf(false) }
    var isMoveEnabled by remember { mutableStateOf(false) }
    var isPreviewVisible by remember { mutableStateOf(true) }

    // Expressions & Motions State
    var expressionsFolder by remember { mutableStateOf<String?>(null) }
    var motionsFolder by remember { mutableStateOf<String?>(null) }
    var expressionFiles by remember { mutableStateOf<List<String>>(emptyList()) }
    var motionFiles by remember { mutableStateOf<List<String>>(emptyList()) }
    var showExpressionDialog by remember { mutableStateOf(false) }
    var showMotionDialog by remember { mutableStateOf(false) }

    LaunchedEffect(modelId) {
        expressionsFolder = findAssetFolder(context.assets, modelId, "expressions")
        if (expressionsFolder != null) {
            expressionFiles = context.assets.list("$modelId/$expressionsFolder")?.toList() ?: emptyList()
        }

        motionsFolder = findAssetFolder(context.assets, modelId, "motions")
        if (motionsFolder != null) {
            motionFiles = context.assets.list("$modelId/$motionsFolder")?.toList() ?: emptyList()
        }
    }
    
    val faceParams = remember(facePose, landmarks) {
        if (landmarks.isEmpty()) {
            mapper.reset()
            mapOf(
                "ParamAngleX" to 0f, "ParamAngleY" to 0f, "ParamAngleZ" to 0f,
                "ParamEyeLOpen" to 1f, "ParamEyeROpen" to 1f, "LipSync" to 0f,
                "ParamMouthForm" to 0f, "ParamBodyAngleX" to 0f, "ParamBodyAngleY" to 0f,
                "ParamBodyAngleZ" to 0f, "ParamEyeBallX" to 0f, "ParamEyeBallY" to 0f
            )
        } else {
            mapper.map(facePose)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Live2DScreen(
            modifier = Modifier.fillMaxSize(),
            modelId = modelId,
            faceParams = faceParams,
            isZoomEnabled = isZoomEnabled,
            isMoveEnabled = isMoveEnabled
        )

        // LaunchedEffect 내에서 카메라와 랜드마커를 순차적으로 초기화
        LaunchedEffect(Unit) {
            faceTracker.setupFaceLandmarker(useGpu = false)
            faceTracker.startCamera()
        }

        if (isPreviewVisible) {
            AndroidView(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 48.dp, end = 24.dp)
                    .size(120.dp, 160.dp),
                factory = { ctx ->
                    androidx.camera.view.PreviewView(ctx).apply {
                        scaleType = androidx.camera.view.PreviewView.ScaleType.FILL_CENTER
                        faceTracker.attachPreview(surfaceProvider)
                    }
                },
                onRelease = {
                    faceTracker.detachPreview()
                }
            )

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
                        color = Color.Cyan,
                        radius = 2f,
                        center = androidx.compose.ui.geometry.Offset(x, y),
                        alpha = 0.8f
                    )
                }
            }
        }

        if (isCalibrating) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "얼굴 보정 중입니다...\n5초 동안 정면을 응시해 주세요.",
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
        
        // 뒤로가기 버튼 (상단 왼쪽)
        Button(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 48.dp, start = 16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Black.copy(alpha = 0.6f))
        ) {
            Text("⬅️ 뒤로")
        }

        // Top Control Bar (Expressions & Motions)
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (expressionsFolder != null) {
                Button(
                    onClick = { showExpressionDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Black.copy(alpha = 0.6f))
                ) {
                    Text("감정")
                }
            }
            if (motionsFolder != null) {
                Button(
                    onClick = { showMotionDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Black.copy(alpha = 0.6f))
                ) {
                    Text("모션")
                }
            }
        }

        // Expression Dialog
        if (showExpressionDialog) {
            FileListDialog(
                title = "감정 목록",
                files = expressionFiles,
                onDismiss = { showExpressionDialog = false },
                onFileSelected = { fileName ->
                    LAppMinimumLive2DManager.getInstance().startExpression("$expressionsFolder/$fileName")
                    showExpressionDialog = false
                }
            )
        }

        // Motion Dialog
        if (showMotionDialog) {
            FileListDialog(
                title = "모션 목록",
                files = motionFiles,
                onDismiss = { showMotionDialog = false },
                onFileSelected = { fileName ->
                    LAppMinimumLive2DManager.getInstance().startMotion("$motionsFolder/$fileName")
                    showMotionDialog = false
                }
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 48.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 구 UI 요소들 (토글 등)
            ControlToggle(text = if (isGpuEnabled) "GPU 🚀" else "CPU", checked = isGpuEnabled, onCheckedChange = { faceTracker.setGpuEnabled(it) })
            ControlToggle(text = "🔍 확대", checked = isZoomEnabled, onCheckedChange = { isZoomEnabled = it }, activeColor = Color.Blue)
            ControlToggle(text = "↕️ 이동", checked = isMoveEnabled, onCheckedChange = { isMoveEnabled = it }, activeColor = Color.Magenta)
            ControlToggle(text = "📷 프리뷰", checked = isPreviewVisible, onCheckedChange = { isPreviewVisible = it }, activeColor = Color.Cyan)
        }
    }
}

@Composable
fun ControlToggle(
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    activeColor: Color = Color.Transparent
) {
    Row(
        modifier = Modifier
            .background(
                color = if (checked && activeColor != Color.Transparent) activeColor.copy(alpha = 0.7f) else Color.Black.copy(alpha = 0.6f),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            color = Color.White,
            style = MaterialTheme.typography.labelSmall
        )
        Spacer(modifier = Modifier.width(4.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.height(24.dp)
        )
    }
}

@Composable
fun FileListDialog(
    title: String,
    files: List<String>,
    onDismiss: () -> Unit,
    onFileSelected: (String) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 400.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.Black,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                androidx.compose.foundation.lazy.LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(files.size) { index ->
                        val file = files[index]
                        Button(
                            onClick = { onFileSelected(file) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.LightGray)
                        ) {
                            Text(file, color = Color.Black)
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
                ) {
                    Text("닫기")
                }
            }
        }
    }
}

private fun findAssetFolder(assetManager: AssetManager, modelId: String, targetFolder: String): String? {
    return try {
        val files = assetManager.list(modelId) ?: return null
        files.firstOrNull { it.equals(targetFolder, ignoreCase = true) }
    } catch (e: IOException) {
        null
    }
}
