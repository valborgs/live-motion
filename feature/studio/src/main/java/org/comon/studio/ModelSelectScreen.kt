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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.comon.common.di.LocalAppContainer
import org.comon.domain.model.ModelSource
import org.comon.storage.SAFPermissionManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSelectScreen(
    onModelSelected: (ModelSource) -> Unit,
    errorMessage: String? = null,
    onErrorConsumed: () -> Unit = {}
) {
    val container = LocalAppContainer.current
    val context = LocalContext.current

    val viewModel: ModelSelectViewModel = viewModel(
        factory = ModelSelectViewModel.Factory(
            container.getAllModelsUseCase,
            container.importExternalModelUseCase,
            container.deleteExternalModelsUseCase
        )
    )

    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // SAF 권한 관리자
    val safPermissionManager = remember { SAFPermissionManager(context) }

    // 에러 상세 다이얼로그 상태
    var showErrorDetailDialog by remember { mutableStateOf(false) }
    var currentErrorDetail by remember { mutableStateOf<String?>(null) }

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
            viewModel.importModel(it.toString())
        }
    }

    // 외부 에러 메시지 처리
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            currentErrorDetail = errorMessage
            scope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = "모델 로딩에 실패했습니다",
                    actionLabel = "자세히",
                    duration = SnackbarDuration.Long
                )
                if (result == SnackbarResult.ActionPerformed) {
                    showErrorDetailDialog = true
                }
            }
            onErrorConsumed()
        }
    }

    // ViewModel 에러 처리
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            currentErrorDetail = error
            scope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = "오류가 발생했습니다",
                    actionLabel = "자세히",
                    duration = SnackbarDuration.Long
                )
                if (result == SnackbarResult.ActionPerformed) {
                    showErrorDetailDialog = true
                }
            }
            viewModel.clearError()
        }
    }

    // 삭제 모드에서 뒤로가기 처리
    BackHandler(enabled = uiState.isDeleteMode) {
        viewModel.exitDeleteMode()
    }

    Scaffold(
        topBar = {
            if (uiState.isDeleteMode) {
                // 삭제 모드 앱바
                TopAppBar(
                    title = {
                        Text(
                            "${uiState.selectedModelIds.size}개 선택됨",
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.exitDeleteMode() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "삭제 모드 종료"
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
                                contentDescription = "선택한 모델 삭제",
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
                    title = { Text("캐릭터 선택", fontWeight = FontWeight.Bold) }
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            // 삭제 모드가 아닐 때만 FAB 표시
            if (!uiState.isDeleteMode) {
                FloatingActionButton(
                    onClick = { folderPickerLauncher.launch(null) },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "모델 가져오기"
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
                                        viewModel.toggleModelSelection(modelSource.id)
                                    }
                                } else {
                                    onModelSelected(modelSource)
                                }
                            },
                            onLongClick = {
                                // 외부 모델만 길게 눌러서 삭제 모드 진입 가능
                                if (modelSource is ModelSource.External && !uiState.isDeleteMode) {
                                    viewModel.enterDeleteMode(modelSource.id)
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
    if (showErrorDetailDialog && currentErrorDetail != null) {
        ErrorDetailDialog(
            errorMessage = currentErrorDetail!!,
            onDismiss = { showErrorDetailDialog = false }
        )
    }

    // 삭제 확인 다이얼로그
    if (showDeleteConfirmDialog) {
        DeleteConfirmDialog(
            count = uiState.selectedModelIds.size,
            onConfirm = {
                showDeleteConfirmDialog = false
                viewModel.deleteSelectedModels()
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
                        text = "외부 모델",
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
                    text = "모델 가져오는 중...",
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
private fun ErrorDetailDialog(
    errorMessage: String,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Text(
                    text = "에러 상세",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
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
                        modifier = Modifier.padding(12.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("확인")
                }
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
                text = "모델 삭제",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text("${count}개의 모델을 삭제하시겠습니까?")
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = Color(0xFFE53935)
                )
            ) {
                Text("삭제")
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
                    text = "삭제 중...",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}
