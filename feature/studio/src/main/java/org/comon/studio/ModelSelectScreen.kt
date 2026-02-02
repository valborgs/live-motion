package org.comon.studio

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import org.comon.ui.theme.LiveMotionTheme
import org.comon.domain.model.ModelSource
import org.comon.storage.SAFPermissionManager
import org.comon.ui.snackbar.ErrorDetailDialog
import org.comon.ui.snackbar.rememberSnackbarStateHolder

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

    // 삭제 확인 다이얼로그 상태
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

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

    // 삭제 모드에서 뒤로가기 처리
    BackHandler(enabled = uiState.isDeleteMode) {
        viewModel.onIntent(ModelSelectUiIntent.ExitDeleteMode)
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
                        IconButton(onClick = { viewModel.onIntent(ModelSelectUiIntent.ExitDeleteMode) }) {
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
                    onClick = { folderPickerLauncher.launch(null) },
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
                                        viewModel.onIntent(ModelSelectUiIntent.ToggleModelSelection(modelSource.id))
                                    }
                                } else {
                                    onModelSelected(modelSource)
                                }
                            },
                            onLongClick = {
                                // 외부 모델만 길게 눌러서 삭제 모드 진입 가능
                                if (modelSource is ModelSource.External && !uiState.isDeleteMode) {
                                    viewModel.onIntent(ModelSelectUiIntent.EnterDeleteMode(modelSource.id))
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
                viewModel.onIntent(ModelSelectUiIntent.DeleteSelectedModels)
            },
            onDismiss = { showDeleteConfirmDialog = false }
        )
    }

    // 삭제 진행 다이얼로그
    if (uiState.isDeleting) {
        DeletingProgressDialog()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ModelCard(
    modelSource: ModelSource,
    isDeleteMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val isExternal = modelSource is ModelSource.External

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = when {
            isDeleteMode && !isExternal -> {
                // 삭제 모드에서 Asset 모델은 비활성화 스타일
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            }
            else -> {
                // 모든 모델 카드는 연한 민트 배경
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            }
        }
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = modelSource.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    color = if (isDeleteMode && !isExternal) {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    } else {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    }
                )
                if (isExternal) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.model_select_external_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // 삭제 모드에서 외부 모델에만 체크박스 표시
            if (isDeleteMode && isExternal) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onClick() },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                    colors = CheckboxDefaults.colors(
                        checkedColor = Color(0xFFE53935)
                    )
                )
            }
        }
    }
}

@Composable
private fun ImportProgressDialog(progress: Float) {
    Dialog(onDismissRequest = { /* 취소 불가 */ }) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.dialog_import_progress_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}


@Composable
private fun DeleteConfirmDialog(
    count: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.dialog_delete_title),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(stringResource(R.string.dialog_delete_message, count))
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.button_cancel))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = Color(0xFFE53935)
                )
            ) {
                Text(stringResource(R.string.button_delete))
            }
        }
    )
}

@Composable
private fun DeletingProgressDialog() {
    Dialog(onDismissRequest = { /* 취소 불가 */ }) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier.padding(24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
                Text(
                    text = stringResource(R.string.dialog_deleting),
                    style = MaterialTheme.typography.bodyLarge
                )
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
private fun ModelCardPreview() {
    LiveMotionTheme {
        ModelCard(
            modelSource = ModelSource.Asset("Haru"),
            isDeleteMode = false,
            isSelected = false,
            onClick = {},
            onLongClick = {}
        )
    }
}

@Preview(name = "Delete Mode", showBackground = true)
@Composable
private fun ModelCardDeleteModePreview() {
    LiveMotionTheme {
        ModelCard(
            modelSource = ModelSource.Asset("Haru"),
            isDeleteMode = true,
            isSelected = false,
            onClick = {},
            onLongClick = {}
        )
    }
}
