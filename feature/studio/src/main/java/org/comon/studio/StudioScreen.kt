package org.comon.studio

import androidx.compose.foundation.Canvas
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import org.comon.domain.model.ModelSource
import org.comon.live2d.LAppMinimumLive2DManager
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
        onExpressionFileSelected = { fileName ->
            LAppMinimumLive2DManager.getInstance()
                .startExpression("${uiState.expressionsFolder}/$fileName")
        },
        onMotionFileSelected = { fileName ->
            LAppMinimumLive2DManager.getInstance()
                .startMotion("${uiState.motionsFolder}/$fileName")
        },
        onExpressionReset = {
            LAppMinimumLive2DManager.getInstance().clearExpression()
        },
        onMotionReset = {
            LAppMinimumLive2DManager.getInstance().clearMotion()
        },
        modelViewContent = {
            Live2DScreen(
                modifier = Modifier.fillMaxSize(),
                modelSource = modelSource,
                faceParams = faceParams,
                isZoomEnabled = uiState.isZoomEnabled,
                isMoveEnabled = uiState.isMoveEnabled,
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
    onExpressionFileSelected: (String) -> Unit,
    onMotionFileSelected: (String) -> Unit,
    onExpressionReset: () -> Unit = {},
    onMotionReset: () -> Unit = {},
    modelViewContent: @Composable () -> Unit = {},
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
        // 상단 모델 뷰 영역 (8)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.8f)
        ) {
            modelViewContent()

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
                            text = stringResource(R.string.studio_zoom),
                            emoji = "🔍",
                            checked = uiState.isZoomEnabled,
                            onCheckedChange = { onIntent(StudioUiIntent.ToggleZoom) },
                            activeColor = MaterialTheme.colorScheme.secondary
                        )
                        StudioToggleButton(
                            text = stringResource(R.string.studio_move),
                            emoji = "↕️",
                            checked = uiState.isMoveEnabled,
                            onCheckedChange = { onIntent(StudioUiIntent.ToggleMove) },
                            activeColor = MaterialTheme.colorScheme.primary
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
                    Box(
                        modifier = Modifier
                            .size(100.dp, 130.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black)
                    ) {
                        Canvas(
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
                        onExpressionReset()
                    } else {
                        onExpressionFileSelected(fileName)
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
                        onMotionReset()
                    } else {
                        onMotionFileSelected(fileName)
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
            onExpressionFileSelected = {},
            onMotionFileSelected = {},
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
