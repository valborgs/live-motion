package org.comon.studio

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
            container.importExternalModelUseCase
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("캐릭터 선택", fontWeight = FontWeight.Bold) }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
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
                            onClick = { onModelSelected(modelSource) }
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
}

@Composable
private fun ModelCard(
    modelSource: ModelSource,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = modelSource.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
                if (modelSource is ModelSource.External) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "외부 모델",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
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
