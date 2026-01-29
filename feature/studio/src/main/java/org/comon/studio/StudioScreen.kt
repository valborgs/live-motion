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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.comon.common.di.LocalAppContainer
import org.comon.live2d.LAppMinimumLive2DManager
import org.comon.live2d.Live2DScreen
import org.comon.tracking.TrackingError

// 디자인 컬러 정의
private val ControlPanelBackground = Color(0xFF1A1A2E)
private val ButtonDefaultColor = Color(0xFF2D2D44)
private val AccentBlue = Color(0xFF4A9FF5)
private val AccentPurple = Color(0xFF7C4DFF)
private val AccentCyan = Color(0xFF00BCD4)
private val AccentMagenta = Color(0xFFE040FB)
private val TextPrimary = Color(0xFFFFFFFF)
private val TextSecondary = Color(0xFFB0B0C3)

@Composable
fun StudioScreen(
    modelId: String,
    onBack: () -> Unit,
    onError: (String) -> Unit = {},
    viewModel: StudioViewModel = run {
        val container = LocalAppContainer.current
        viewModel(
            factory = StudioViewModel.Factory(
                container.faceTrackerFactory,
                container.getModelMetadataUseCase,
                container.createMapFacePoseUseCase()
            )
        )
    }
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // 에러 상세 다이얼로그 상태
    var showErrorDetailDialog by remember { mutableStateOf(false) }
    var currentErrorDetail by remember { mutableStateOf<String?>(null) }

    // 초기화
    LaunchedEffect(modelId) {
        viewModel.initialize(lifecycleOwner, modelId)
    }

    // 실시간 트래킹 데이터 (별도 collect)
    val facePose by viewModel.facePose.collectAsStateWithLifecycle()
    val landmarks by viewModel.faceLandmarks.collectAsStateWithLifecycle()

    // UI 상태 (단일 State)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // 트래킹 에러 발생 시 스낵바 표시
    LaunchedEffect(uiState.trackingError) {
        uiState.trackingError?.let { error ->
            val errorMessage = when (error) {
                is TrackingError.FaceLandmarkerInitError -> error.message
                is TrackingError.CameraError -> error.message
                is TrackingError.MediaPipeRuntimeError -> error.message
            }
            currentErrorDetail = errorMessage
            scope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = "트래킹 오류가 발생했습니다",
                    actionLabel = "자세히",
                    duration = SnackbarDuration.Long
                )
                if (result == SnackbarResult.ActionPerformed) {
                    showErrorDetailDialog = true
                }
                viewModel.clearTrackingError()
            }
        }
    }

    // faceParams 계산
    val faceParams = remember(facePose, landmarks) {
        viewModel.mapFaceParams(facePose, landmarks.isNotEmpty())
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
                isZoomEnabled = uiState.isZoomEnabled,
                isMoveEnabled = uiState.isMoveEnabled,
                onModelLoaded = { viewModel.onModelLoaded() },
                onModelLoadError = { error ->
                    onError(error)
                    onBack()
                }
            )

            // 모델 로딩 오버레이
            if (uiState.isModelLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color.White)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "모델 로딩 중...",
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }

            // 캘리브레이션 오버레이
            if (!uiState.isModelLoading && uiState.isCalibrating) {
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

                        if (uiState.expressionsFolder != null) {
                            StudioIconButton(
                                emoji = "😊",
                                text = "감정",
                                onClick = { viewModel.showExpressionDialog() },
                                accentColor = AccentPurple
                            )
                        }

                        if (uiState.motionsFolder != null) {
                            StudioIconButton(
                                emoji = "🎬",
                                text = "모션",
                                onClick = { viewModel.showMotionDialog() },
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
                            text = if (uiState.isGpuEnabled) "GPU" else "CPU",
                            emoji = if (uiState.isGpuEnabled) "🚀" else "💻",
                            checked = uiState.isGpuEnabled,
                            onCheckedChange = { viewModel.setGpuEnabled(it) },
                            activeColor = AccentBlue
                        )
                        StudioToggleButton(
                            text = "확대",
                            emoji = "🔍",
                            checked = uiState.isZoomEnabled,
                            onCheckedChange = { viewModel.toggleZoom() },
                            activeColor = AccentPurple
                        )
                        StudioToggleButton(
                            text = "이동",
                            emoji = "↕️",
                            checked = uiState.isMoveEnabled,
                            onCheckedChange = { viewModel.toggleMove() },
                            activeColor = AccentMagenta
                        )
                        StudioToggleButton(
                            text = "프리뷰",
                            emoji = "📷",
                            checked = uiState.isPreviewVisible,
                            onCheckedChange = { viewModel.togglePreview() },
                            activeColor = AccentCyan
                        )
                    }
                }

                // 프리뷰 영역 (고정 크기)
                if (uiState.isPreviewVisible) {
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
                                    viewModel.attachPreview(surfaceProvider)
                                }
                            },
                            onRelease = {
                                viewModel.detachPreview()
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

        // 스낵바 호스트
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    // Dialogs
    when (uiState.dialogState) {
        is StudioViewModel.DialogState.Expression -> {
            FileListDialog(
                title = "감정 목록",
                files = uiState.expressionFiles,
                onDismiss = { viewModel.dismissDialog() },
                onFileSelected = { fileName ->
                    LAppMinimumLive2DManager.getInstance()
                        .startExpression("${uiState.expressionsFolder}/$fileName")
                    viewModel.dismissDialog()
                }
            )
        }
        is StudioViewModel.DialogState.Motion -> {
            FileListDialog(
                title = "모션 목록",
                files = uiState.motionFiles,
                onDismiss = { viewModel.dismissDialog() },
                onFileSelected = { fileName ->
                    LAppMinimumLive2DManager.getInstance()
                        .startMotion("${uiState.motionsFolder}/$fileName")
                    viewModel.dismissDialog()
                }
            )
        }
        StudioViewModel.DialogState.None -> { /* No dialog */ }
    }

    // 트래킹 에러 상세 다이얼로그
    if (showErrorDetailDialog && currentErrorDetail != null) {
        TrackingErrorDetailDialog(
            errorMessage = currentErrorDetail!!,
            onDismiss = { showErrorDetailDialog = false }
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

@Composable
private fun TrackingErrorDetailDialog(
    errorMessage: String,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = ControlPanelBackground)
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Text(
                    text = "트래킹 에러 상세",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(16.dp))

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = ButtonDefaultColor,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        modifier = Modifier.padding(12.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("확인", color = TextPrimary)
                }
            }
        }
    }
}
