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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.comon.ui.theme.LiveMotionTheme
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.comon.domain.model.ModelSource
import org.comon.live2d.LAppMinimumLive2DManager
import org.comon.live2d.Live2DScreen
import org.comon.tracking.TrackingError

@Composable
fun StudioScreen(
    modelSource: ModelSource,
    onBack: () -> Unit,
    onError: (String) -> Unit = {},
    viewModel: StudioViewModel = hiltViewModel()
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // 에러 상세 다이얼로그 상태
    var showErrorDetailDialog by remember { mutableStateOf(false) }
    var currentErrorDetail by remember { mutableStateOf<String?>(null) }

    // 초기화
    LaunchedEffect(modelSource) {
        viewModel.initialize(lifecycleOwner, modelSource)
    }

    // 실시간 트래킹 데이터 (별도 collect)
    val facePose by viewModel.facePose.collectAsStateWithLifecycle()
    val landmarks by viewModel.faceLandmarks.collectAsStateWithLifecycle()

    // UI 상태 (단일 State)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val snackbarMessage = stringResource(R.string.studio_snackbar_tracking_error)
    val snackbarAction = stringResource(R.string.snackbar_action_detail)

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
                    message = snackbarMessage,
                    actionLabel = snackbarAction,
                    duration = SnackbarDuration.Long
                )
                if (result == SnackbarResult.ActionPerformed) {
                    showErrorDetailDialog = true
                }
                viewModel.onIntent(StudioUiIntent.ClearTrackingError)
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
                modelSource = modelSource,
                faceParams = faceParams,
                isZoomEnabled = uiState.isZoomEnabled,
                isMoveEnabled = uiState.isMoveEnabled,
                onModelLoaded = { viewModel.onIntent(StudioUiIntent.OnModelLoaded) },
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
                            stringResource(R.string.studio_model_loading),
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
                            stringResource(R.string.studio_calibrating),
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
                            MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.95f),
                            MaterialTheme.colorScheme.surfaceContainer
                        )
                    )
                )
                .navigationBarsPadding()
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
                            text = stringResource(R.string.studio_back),
                            onClick = onBack
                        )

                        if (uiState.expressionsFolder != null) {
                            StudioIconButton(
                                emoji = "😊",
                                text = stringResource(R.string.studio_expression),
                                onClick = { viewModel.onIntent(StudioUiIntent.ShowExpressionDialog) },
                                accentColor = MaterialTheme.colorScheme.primary
                            )
                        }

                        if (uiState.motionsFolder != null) {
                            StudioIconButton(
                                emoji = "🎬",
                                text = stringResource(R.string.studio_motion),
                                onClick = { viewModel.onIntent(StudioUiIntent.ShowMotionDialog) },
                                accentColor = MaterialTheme.colorScheme.secondary
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
                            text = if (uiState.isGpuEnabled) stringResource(R.string.studio_gpu) else stringResource(R.string.studio_cpu),
                            emoji = if (uiState.isGpuEnabled) "🚀" else "💻",
                            checked = uiState.isGpuEnabled,
                            onCheckedChange = { viewModel.onIntent(StudioUiIntent.SetGpuEnabled(it)) },
                            activeColor = MaterialTheme.colorScheme.primary
                        )
                        StudioToggleButton(
                            text = stringResource(R.string.studio_zoom),
                            emoji = "🔍",
                            checked = uiState.isZoomEnabled,
                            onCheckedChange = { viewModel.onIntent(StudioUiIntent.ToggleZoom) },
                            activeColor = MaterialTheme.colorScheme.secondary
                        )
                        StudioToggleButton(
                            text = stringResource(R.string.studio_move),
                            emoji = "↕️",
                            checked = uiState.isMoveEnabled,
                            onCheckedChange = { viewModel.onIntent(StudioUiIntent.ToggleMove) },
                            activeColor = MaterialTheme.colorScheme.primary
                        )
                        StudioToggleButton(
                            text = stringResource(R.string.studio_preview),
                            emoji = "📷",
                            checked = uiState.isPreviewVisible,
                            onCheckedChange = { viewModel.onIntent(StudioUiIntent.TogglePreview) },
                            activeColor = MaterialTheme.colorScheme.tertiary
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
    val resetLabel = stringResource(R.string.dialog_reset)
    when (uiState.dialogState) {
        is StudioViewModel.DialogState.Expression -> {
            FileListDialog(
                title = stringResource(R.string.dialog_expression_title),
                files = listOf(resetLabel) + uiState.expressionFiles,
                onDismiss = { viewModel.onIntent(StudioUiIntent.DismissDialog) },
                onFileSelected = { fileName ->
                    if (fileName == resetLabel) {
                        LAppMinimumLive2DManager.getInstance().clearExpression()
                    } else {
                        LAppMinimumLive2DManager.getInstance()
                            .startExpression("${uiState.expressionsFolder}/$fileName")
                    }
                    viewModel.onIntent(StudioUiIntent.DismissDialog)
                }
            )
        }
        is StudioViewModel.DialogState.Motion -> {
            FileListDialog(
                title = stringResource(R.string.dialog_motion_title),
                files = listOf(resetLabel) + uiState.motionFiles,
                onDismiss = { viewModel.onIntent(StudioUiIntent.DismissDialog) },
                onFileSelected = { fileName ->
                    if (fileName == resetLabel) {
                        LAppMinimumLive2DManager.getInstance().clearMotion()
                    } else {
                        LAppMinimumLive2DManager.getInstance()
                            .startMotion("${uiState.motionsFolder}/$fileName")
                    }
                    viewModel.onIntent(StudioUiIntent.DismissDialog)
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
    accentColor: Color = MaterialTheme.colorScheme.surfaceVariant
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = accentColor
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
            color = MaterialTheme.colorScheme.onSurface,
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
    val backgroundColor = if (checked) activeColor else MaterialTheme.colorScheme.surfaceVariant

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
                color = if (checked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
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
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
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
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(file, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(stringResource(R.string.button_close), color = MaterialTheme.colorScheme.onPrimary)
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
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Text(
                    text = stringResource(R.string.dialog_tracking_error_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(16.dp))

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(stringResource(R.string.button_confirm), color = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    }
}

@Preview(name = "Light Mode", showBackground = true)
@Preview(
    name = "Dark Mode",
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES
)
@Composable
private fun StudioIconButtonPreview() {
    LiveMotionTheme {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            StudioIconButton(
                emoji = "⬅️",
                text = "뒤로",
                onClick = {}
            )
            StudioIconButton(
                emoji = "😊",
                text = "감정",
                onClick = {},
                accentColor = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Preview(name = "Light Mode", showBackground = true)
@Preview(
    name = "Dark Mode",
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES
)
@Composable
private fun StudioToggleButtonPreview() {
    LiveMotionTheme {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            StudioToggleButton(
                text = "GPU",
                emoji = "🚀",
                checked = true,
                onCheckedChange = {},
                activeColor = MaterialTheme.colorScheme.primary
            )
            StudioToggleButton(
                text = "확대",
                emoji = "🔍",
                checked = false,
                onCheckedChange = {},
                activeColor = MaterialTheme.colorScheme.secondary
            )
        }
    }
}
