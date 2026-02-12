package org.comon.studio

import android.content.res.Configuration
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import org.comon.domain.model.ModelSource
import org.comon.live2d.Live2DScreen
import org.comon.studio.components.FileListDialog
import org.comon.studio.components.StudioIconButton
import org.comon.studio.components.StudioToggleButton
import org.comon.ui.snackbar.ErrorDetailDialog
import org.comon.ui.snackbar.SnackbarStateHolder
import org.comon.ui.snackbar.rememberSnackbarStateHolder
import org.comon.ui.theme.LiveMotionTheme

@Composable
fun StudioScreen(
    modelSource: ModelSource,
    onBack: () -> Unit,
    onError: (String) -> Unit = {},
    viewModel: StudioViewModel = hiltViewModel()
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarState = rememberSnackbarStateHolder()

    // 초기화
    LaunchedEffect(modelSource) {
        viewModel.initialize(lifecycleOwner, modelSource)
    }

    // UI Effect 처리
    val snackbarAction = stringResource(R.string.snackbar_action_detail)
    LaunchedEffect(Unit) {
        viewModel.uiEffect.collect { effect ->
            when (effect) {
                is StudioUiEffect.ShowSnackbar -> {
                    snackbarState.showSnackbar(
                        message = effect.message,
                        actionLabel = effect.actionLabel,
                        duration = SnackbarDuration.Short
                    )
                }
                is StudioUiEffect.ShowErrorWithDetail -> {
                    snackbarState.showErrorWithDetail(
                        displayMessage = effect.displayMessage,
                        detailMessage = effect.detailMessage,
                        actionLabel = snackbarAction
                    )
                }
                is StudioUiEffect.NavigateBack -> onBack()
            }
        }
    }

    // 실시간 트래킹 데이터 (별도 collect)
    val facePose by viewModel.facePose.collectAsStateWithLifecycle()
    val landmarks by viewModel.faceLandmarks.collectAsStateWithLifecycle()

    // UI 상태 (단일 State)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // faceParams 계산
    val faceParams = remember(facePose, landmarks) {
        viewModel.mapFaceParams(facePose, landmarks.isNotEmpty())
    }

    StudioScreenContent(
        uiState = uiState,
        landmarks = landmarks,
        snackbarState = snackbarState,
        onBack = onBack,
        onIntent = viewModel::onIntent,
        modelViewContent = {
            Live2DScreen(
                modifier = Modifier.fillMaxSize(),
                modelSource = modelSource,
                faceParams = faceParams,
                isGestureEnabled = uiState.isGestureEnabled,
                backgroundPath = uiState.backgroundPath,
                effectFlow = viewModel.live2dEffect,
                onModelLoaded = { viewModel.onIntent(StudioUiIntent.OnModelLoaded) },
                onModelLoadError = onError,
            )
        },
    )
}

@Composable
private fun StudioScreenContent(
    uiState: StudioViewModel.StudioUiState,
    landmarks: List<NormalizedLandmark>,
    snackbarState: SnackbarStateHolder,
    onBack: () -> Unit,
    onIntent: (StudioUiIntent) -> Unit,
    modelViewContent: @Composable () -> Unit = {},
) {
    val isLandscape =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    Box(modifier = Modifier.fillMaxSize()) {
        if (isLandscape) {
            // === Landscape 레이아웃: 70/30 수평 분할 ===
            Row(modifier = Modifier.fillMaxSize()) {
                // 왼쪽: 모델 뷰 영역 (70%)
                Box(
                    modifier = Modifier
                        .weight(0.7f)
                        .fillMaxHeight()
                ) {
                    modelViewContent()
                    ModelLoadingOverlay(uiState.isModelLoading)
                    CalibrationOverlay(
                        visible = !uiState.isModelLoading && uiState.isCalibrating
                    )
                }

                // 오른쪽: 설정 패널 (30%)
                Column(
                    modifier = Modifier
                        .weight(0.3f)
                        .fillMaxHeight()
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.95f),
                                    MaterialTheme.colorScheme.surfaceContainer
                                )
                            )
                        )
                        .navigationBarsPadding()
                        .systemBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // 버튼 영역: 2열 배치
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // 왼쪽 열: 뒤로가기, 감정, 모션
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            StudioIconButton(
                                emoji = "⬅️",
                                text = stringResource(R.string.studio_back),
                                onClick = onBack,
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (uiState.expressionsFolder != null) {
                                StudioIconButton(
                                    emoji = "😊",
                                    text = stringResource(R.string.studio_expression),
                                    onClick = { onIntent(StudioUiIntent.ShowExpressionDialog) },
                                    modifier = Modifier.fillMaxWidth(),
                                    accentColor = MaterialTheme.colorScheme.primary
                                )
                            }
                            if (uiState.motionsFolder != null) {
                                StudioIconButton(
                                    emoji = "🎬",
                                    text = stringResource(R.string.studio_motion),
                                    onClick = { onIntent(StudioUiIntent.ShowMotionDialog) },
                                    modifier = Modifier.fillMaxWidth(),
                                    accentColor = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }

                        // 오른쪽 열: GPU/CPU, 제스처, 리셋, 프리뷰
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            StudioToggleButton(
                                text = if (uiState.isGpuEnabled) stringResource(R.string.studio_gpu) else stringResource(R.string.studio_cpu),
                                emoji = if (uiState.isGpuEnabled) "🚀" else "💻",
                                checked = uiState.isGpuEnabled,
                                activeColor = MaterialTheme.colorScheme.primary,
                                onCheckedChange = { onIntent(StudioUiIntent.SetGpuEnabled(it)) },
                                modifier = Modifier.fillMaxWidth()
                            )
                            StudioToggleButton(
                                text = stringResource(R.string.studio_gesture),
                                emoji = "✋",
                                checked = uiState.isGestureEnabled,
                                activeColor = MaterialTheme.colorScheme.secondary,
                                onCheckedChange = { onIntent(StudioUiIntent.ToggleGesture) },
                                modifier = Modifier.fillMaxWidth()
                            )
                            StudioIconButton(
                                text = stringResource(R.string.studio_reset),
                                emoji = "🔄",
                                onClick = { onIntent(StudioUiIntent.ResetTransform) },
                                modifier = Modifier.fillMaxWidth()
                            )
                            StudioToggleButton(
                                text = stringResource(R.string.studio_preview),
                                emoji = "📷",
                                checked = uiState.isPreviewVisible,
                                activeColor = MaterialTheme.colorScheme.tertiary,
                                onCheckedChange = { onIntent(StudioUiIntent.TogglePreview) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // 랜드마크 프리뷰 (하단 중앙)
                    if (uiState.isPreviewVisible) {
                        Spacer(modifier = Modifier.height(8.dp))
                        LandmarkPreviewCanvas(
                            landmarks = landmarks,
                            modifier = Modifier.size(100.dp, 130.dp)
                        )
                    }
                }
            }
        } else {
            // === Portrait 레이아웃: 기존 80/20 수직 분할 ===
            Column(modifier = Modifier.fillMaxSize()) {
                // 상단 모델 뷰 영역 (80%)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.8f)
                ) {
                    modelViewContent()
                    ModelLoadingOverlay(uiState.isModelLoading)
                    CalibrationOverlay(
                        visible = !uiState.isModelLoading && uiState.isCalibrating
                    )
                }

                // 하단 설정 영역 (20%)
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
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            verticalArrangement = Arrangement.spacedBy(
                                8.dp,
                                Alignment.CenterVertically
                            )
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
                                        onClick = { onIntent(StudioUiIntent.ShowExpressionDialog) },
                                        accentColor = MaterialTheme.colorScheme.primary
                                    )
                                }
                                if (uiState.motionsFolder != null) {
                                    StudioIconButton(
                                        emoji = "🎬",
                                        text = stringResource(R.string.studio_motion),
                                        onClick = { onIntent(StudioUiIntent.ShowMotionDialog) },
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
                                    onCheckedChange = { onIntent(StudioUiIntent.SetGpuEnabled(it)) },
                                    activeColor = MaterialTheme.colorScheme.primary
                                )
                                StudioToggleButton(
                                    text = stringResource(R.string.studio_gesture),
                                    emoji = "✋",
                                    checked = uiState.isGestureEnabled,
                                    onCheckedChange = { onIntent(StudioUiIntent.ToggleGesture) },
                                    activeColor = MaterialTheme.colorScheme.secondary
                                )
                                StudioIconButton(
                                    emoji = "🔄",
                                    text = stringResource(R.string.studio_reset),
                                    onClick = { onIntent(StudioUiIntent.ResetTransform) }
                                )
                                StudioToggleButton(
                                    text = stringResource(R.string.studio_preview),
                                    emoji = "📷",
                                    checked = uiState.isPreviewVisible,
                                    onCheckedChange = { onIntent(StudioUiIntent.TogglePreview) },
                                    activeColor = MaterialTheme.colorScheme.tertiary
                                )
                            }
                        }

                        // 프리뷰 영역 (고정 크기)
                        if (uiState.isPreviewVisible) {
                            Spacer(modifier = Modifier.width(12.dp))
                            LandmarkPreviewCanvas(
                                landmarks = landmarks,
                                modifier = Modifier.size(100.dp, 130.dp)
                            )
                        }
                    }
                }
            }
        }

        // 스낵바 호스트
        SnackbarHost(
            hostState = snackbarState.snackbarHostState,
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
                onDismiss = { onIntent(StudioUiIntent.DismissDialog) },
                onFileSelected = { fileName ->
                    if (fileName == resetLabel) {
                        onIntent(StudioUiIntent.ClearExpression)
                    } else {
                        onIntent(StudioUiIntent.StartExpression("${uiState.expressionsFolder}/$fileName"))
                    }
                    onIntent(StudioUiIntent.DismissDialog)
                }
            )
        }
        is StudioViewModel.DialogState.Motion -> {
            FileListDialog(
                title = stringResource(R.string.dialog_motion_title),
                files = listOf(resetLabel) + uiState.motionFiles,
                onDismiss = { onIntent(StudioUiIntent.DismissDialog) },
                onFileSelected = { fileName ->
                    if (fileName == resetLabel) {
                        onIntent(StudioUiIntent.ClearMotion)
                    } else {
                        onIntent(StudioUiIntent.StartMotion("${uiState.motionsFolder}/$fileName"))
                    }
                    onIntent(StudioUiIntent.DismissDialog)
                }
            )
        }
        StudioViewModel.DialogState.None -> { /* No dialog */ }
    }

    // 트래킹 에러 상세 다이얼로그
    if (snackbarState.showErrorDialog) {
        snackbarState.currentErrorDetail?.let {
            ErrorDetailDialog(
                title = stringResource(R.string.dialog_tracking_error_title),
                errorMessage = it,
                confirmButtonText = stringResource(R.string.button_confirm),
                onDismiss = { snackbarState.dismissErrorDialog() }
            )
        }
    }
}

// --- 추출된 private composable ---

@Composable
private fun ModelLoadingOverlay(visible: Boolean) {
    if (!visible) return
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

@Composable
private fun CalibrationOverlay(visible: Boolean) {
    if (!visible) return
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

@Composable
private fun LandmarkPreviewCanvas(
    landmarks: List<NormalizedLandmark>,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
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

@Preview
@Composable
private fun StudioScreenPreview() {
    LiveMotionTheme {
        StudioScreenContent(
            uiState = StudioViewModel.StudioUiState(isModelLoading = false),
            landmarks = emptyList(),
            snackbarState = rememberSnackbarStateHolder(),
            onBack = {},
            onIntent = {},
            modelViewContent = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.DarkGray),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Live2D Preview", color = Color.White)
                }
            },
        )
    }
}

@Preview(widthDp = 800, heightDp = 400)
@Composable
private fun StudioScreenLandscapePreview() {
    LiveMotionTheme {
        StudioScreenContent(
            uiState = StudioViewModel.StudioUiState(
                isModelLoading = false,
                expressionsFolder = "expressions",
                motionsFolder = "motions",
            ),
            landmarks = emptyList(),
            snackbarState = rememberSnackbarStateHolder(),
            onBack = {},
            onIntent = {},
            modelViewContent = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.DarkGray),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Live2D Preview", color = Color.White)
                }
            },
        )
    }
}
