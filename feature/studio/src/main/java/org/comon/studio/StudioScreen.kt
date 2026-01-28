package org.comon.studio

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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

// 디자인 컬러 정의
private val ControlPanelBackground = Color(0xFF1A1A2E)
private val ButtonDefaultColor = Color(0xFF2D2D44)
private val ButtonHoverColor = Color(0xFF3D3D5C)
private val AccentBlue = Color(0xFF4A9FF5)
private val AccentPurple = Color(0xFF7C4DFF)
private val AccentCyan = Color(0xFF00BCD4)
private val AccentMagenta = Color(0xFFE040FB)
private val TextPrimary = Color(0xFFFFFFFF)
private val TextSecondary = Color(0xFFB0B0C3)

@Composable
fun StudioScreen(
    modelId: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    val faceTracker = remember { FaceTracker(context, lifecycleOwner) }
    
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

    LaunchedEffect(Unit) {
        faceTracker.setupFaceLandmarker(useGpu = false)
        faceTracker.startCamera()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 상단 모델 뷰 영역 (8)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.8f)
        ) {
            Live2DScreen(
                modifier = Modifier.fillMaxSize(),
                modelId = modelId,
                faceParams = faceParams,
                isZoomEnabled = isZoomEnabled,
                isMoveEnabled = isMoveEnabled
            )

            // 캘리브레이션 오버레이
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
        }

        // 하단 설정 영역 (2)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.2f)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            ControlPanelBackground.copy(alpha = 0.95f),
                            ControlPanelBackground
                        )
                    )
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 버튼 레이아웃 영역 (프리뷰 제외 남은 공간)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
                ) {
                    // 1열: 뒤로가기, 감정, 모션 버튼
                    Row(
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StudioIconButton(
                            emoji = "⬅️",
                            text = "뒤로",
                            onClick = onBack
                        )
                        
                        if (expressionsFolder != null) {
                            StudioIconButton(
                                emoji = "😊",
                                text = "감정",
                                onClick = { showExpressionDialog = true },
                                accentColor = AccentPurple
                            )
                        }
                        
                        if (motionsFolder != null) {
                            StudioIconButton(
                                emoji = "🎬",
                                text = "모션",
                                onClick = { showMotionDialog = true },
                                accentColor = AccentBlue
                            )
                        }
                    }

                    // 2열: 토글 버튼들
                    Row(
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StudioToggleButton(
                            text = if (isGpuEnabled) "GPU" else "CPU",
                            emoji = if (isGpuEnabled) "🚀" else "💻",
                            checked = isGpuEnabled,
                            onCheckedChange = { faceTracker.setGpuEnabled(it) },
                            activeColor = AccentBlue
                        )
                        StudioToggleButton(
                            text = "확대",
                            emoji = "🔍",
                            checked = isZoomEnabled,
                            onCheckedChange = { isZoomEnabled = it },
                            activeColor = AccentPurple
                        )
                        StudioToggleButton(
                            text = "이동",
                            emoji = "↕️",
                            checked = isMoveEnabled,
                            onCheckedChange = { isMoveEnabled = it },
                            activeColor = AccentMagenta
                        )
                        StudioToggleButton(
                            text = "프리뷰",
                            emoji = "📷",
                            checked = isPreviewVisible,
                            onCheckedChange = { isPreviewVisible = it },
                            activeColor = AccentCyan
                        )
                    }
                }

                // 프리뷰 영역 (고정 크기)
                if (isPreviewVisible) {
                    Spacer(modifier = Modifier.width(12.dp))
                    Box(
                        modifier = Modifier
                            .size(100.dp, 130.dp)
                            .clip(RoundedCornerShape(12.dp))
                    ) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
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
                            modifier = Modifier.fillMaxSize()
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
                }
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
}

@Composable
private fun StudioIconButton(
    emoji: String,
    text: String,
    onClick: () -> Unit,
    accentColor: Color = ButtonDefaultColor
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = accentColor.copy(alpha = 0.8f)
        ),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 4.dp,
            pressedElevation = 2.dp
        )
    ) {
        Text(
            text = emoji,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            color = TextPrimary,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
private fun StudioToggleButton(
    text: String,
    emoji: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    activeColor: Color
) {
    val backgroundColor = if (checked) activeColor.copy(alpha = 0.85f) else ButtonDefaultColor
    
    Surface(
        onClick = { onCheckedChange(!checked) },
        shape = RoundedCornerShape(12.dp),
        color = backgroundColor,
        shadowElevation = if (checked) 6.dp else 2.dp,
        modifier = Modifier.height(44.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = emoji,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = text,
                color = if (checked) TextPrimary else TextSecondary,
                style = MaterialTheme.typography.labelMedium
            )
        }
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
            colors = CardDefaults.cardColors(containerColor = ControlPanelBackground)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
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
                            colors = ButtonDefaults.buttonColors(containerColor = ButtonDefaultColor),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(file, color = TextPrimary)
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("닫기", color = TextPrimary)
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
