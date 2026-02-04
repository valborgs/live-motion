package org.comon.studio

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.comon.domain.model.ModelSource
import org.comon.storage.SAFPermissionManager
import org.comon.studio.components.DeleteConfirmDialog
import org.comon.studio.components.DeletingProgressDialog
import org.comon.studio.components.ImportProgressDialog
import org.comon.studio.components.ModelCard
import org.comon.ui.snackbar.ErrorDetailDialog
import org.comon.ui.snackbar.SnackbarStateHolder
import org.comon.ui.snackbar.rememberSnackbarStateHolder
import org.comon.ui.theme.LiveMotionTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSelectScreen(
    onModelSelected: (ModelSource) -> Unit,
    errorMessage: String? = null,
    onErrorConsumed: () -> Unit = {},
    viewModel: ModelSelectViewModel = hiltViewModel()
) {
    val context = LocalContext.current

    val uiState by viewModel.uiState.collectAsState()
    val snackbarState = rememberSnackbarStateHolder()

    // UI Effect 처리
    val snackbarAction = stringResource(R.string.snackbar_action_detail)
    LaunchedEffect(Unit) {
        viewModel.uiEffect.collect { effect ->
            when (effect) {
                is ModelSelectUiEffect.ShowSnackbar -> {
                    snackbarState.showSnackbar(
                        message = effect.message,
                        actionLabel = effect.actionLabel,
                        duration = SnackbarDuration.Short
                    )
                }
                is ModelSelectUiEffect.ShowErrorWithDetail -> {
                    snackbarState.showErrorWithDetail(
                        displayMessage = effect.displayMessage,
                        detailMessage = effect.detailMessage,
                        actionLabel = snackbarAction
                    )
                }
            }
        }
    }

    // SAF 권한 관리자
    val safPermissionManager = remember { SAFPermissionManager(context) }

    // 폴더 선택기 런처
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            // 권한 영구 저장
            safPermissionManager.persistPermission(it)
            // 모델 가져오기
            viewModel.onIntent(ModelSelectUiIntent.ImportModel(it.toString()))
        }
    }

    val snackbarMessage = stringResource(R.string.snackbar_model_load_failed)
    // 외부 에러 메시지 처리 (MainActivity로부터 전달된 에러)
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            snackbarState.showErrorWithDetail(
                displayMessage = snackbarMessage,
                detailMessage = errorMessage,
                actionLabel = snackbarAction
            )
            onErrorConsumed()
        }
    }

    ModelSelectScreenContent(
        uiState = uiState,
        snackbarState = snackbarState,
        onModelSelected = onModelSelected,
        onImportClick = { folderPickerLauncher.launch(null) },
        onIntent = viewModel::onIntent,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelSelectScreenContent(
    uiState: ModelSelectViewModel.UiState,
    snackbarState: SnackbarStateHolder,
    onModelSelected: (ModelSource) -> Unit,
    onImportClick: () -> Unit,
    onIntent: (ModelSelectUiIntent) -> Unit,
) {
    // 삭제 확인 다이얼로그 상태
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    // 삭제 모드에서 뒤로가기 처리
    BackHandler(enabled = uiState.isDeleteMode) {
        onIntent(ModelSelectUiIntent.ExitDeleteMode)
    }

    Scaffold(
        topBar = {
            if (uiState.isDeleteMode) {
                // 삭제 모드 앱바
                TopAppBar(
                    title = {
                        Text(
                            stringResource(R.string.model_select_selected_count, uiState.selectedModelIds.size),
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { onIntent(ModelSelectUiIntent.ExitDeleteMode) }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.model_select_delete_mode_exit)
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { showDeleteConfirmDialog = true },
                            enabled = uiState.selectedModelIds.isNotEmpty()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(R.string.model_select_delete_selected),
                                tint = if (uiState.selectedModelIds.isNotEmpty()) {
                                    Color(0xFFE53935)
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                }
                            )
                        }
                    }
                )
            } else {
                // 일반 모드 앱바
                TopAppBar(
                    title = { Text(stringResource(R.string.model_select_title), fontWeight = FontWeight.Bold) }
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarState.snackbarHostState) },
        floatingActionButton = {
            // 삭제 모드가 아닐 때만 FAB 표시
            if (!uiState.isDeleteMode) {
                FloatingActionButton(
                    onClick = onImportClick,
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.model_select_import)
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentPadding = PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(uiState.models) { modelSource ->
                        ModelCard(
                            modelSource = modelSource,
                            isDeleteMode = uiState.isDeleteMode,
                            isSelected = modelSource.id in uiState.selectedModelIds,
                            onClick = {
                                if (uiState.isDeleteMode) {
                                    // 삭제 모드에서 외부 모델만 선택 가능
                                    if (modelSource is ModelSource.External) {
                                        onIntent(ModelSelectUiIntent.ToggleModelSelection(modelSource.id))
                                    }
                                } else {
                                    onModelSelected(modelSource)
                                }
                            },
                            onLongClick = {
                                // 외부 모델만 길게 눌러서 삭제 모드 진입 가능
                                if (modelSource is ModelSource.External && !uiState.isDeleteMode) {
                                    onIntent(ModelSelectUiIntent.EnterDeleteMode(modelSource.id))
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // 가져오기 진행률 다이얼로그
    uiState.importProgress?.let { progress ->
        ImportProgressDialog(progress = progress)
    }

    // 에러 상세 다이얼로그
    if (snackbarState.showErrorDialog) {
        snackbarState.currentErrorDetail?.let {
            ErrorDetailDialog(
                title = stringResource(R.string.dialog_error_detail_title),
                errorMessage = it,
                confirmButtonText = stringResource(R.string.button_confirm),
                onDismiss = { snackbarState.dismissErrorDialog() }
            )
        }
    }

    // 삭제 확인 다이얼로그
    if (showDeleteConfirmDialog) {
        DeleteConfirmDialog(
            count = uiState.selectedModelIds.size,
            onConfirm = {
                showDeleteConfirmDialog = false
                onIntent(ModelSelectUiIntent.DeleteSelectedModels)
            },
            onDismiss = { showDeleteConfirmDialog = false }
        )
    }

    // 삭제 진행 다이얼로그
    if (uiState.isDeleting) {
        DeletingProgressDialog()
    }
}

@Preview
@Composable
private fun ModelSelectScreenPreview() {
    LiveMotionTheme {
        ModelSelectScreenContent(
            uiState = ModelSelectViewModel.UiState(
                models = listOf(ModelSource.Asset("Haru"), ModelSource.Asset("Mark")),
            ),
            snackbarState = rememberSnackbarStateHolder(),
            onModelSelected = {},
            onImportClick = {},
            onIntent = {},
        )
    }
}
