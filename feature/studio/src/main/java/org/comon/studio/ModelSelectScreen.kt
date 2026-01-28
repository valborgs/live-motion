package org.comon.studio

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSelectScreen(
    onModelSelected: (String) -> Unit,
    errorMessage: String? = null,
    onErrorConsumed: () -> Unit = {}
) {
    val context = LocalContext.current
    val modelList = remember {
        context.assets.list("")?.filter { name ->
            // 1. 개별 파일 및 알려진 비-모델 폴더 제외
            if (name.contains(".") || name == "Shaders" || name == "images" || name == "webkit" || name == "geoid_map") {
                return@filter false
            }

            // 2. 폴더 내부에 '{폴더이름}.model3.json' 파일이 있는지 검사하여 실제 라이브2D 모델 폴더인지 확인
            try {
                val subFiles = context.assets.list(name) ?: emptyArray()
                subFiles.any { it == "$name.model3.json" }
            } catch (_: Exception) {
                false
            }
        } ?: emptyList()
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // 에러 상세 다이얼로그 상태
    var showErrorDetailDialog by remember { mutableStateOf(false) }
    var currentErrorDetail by remember { mutableStateOf<String?>(null) }

    // 에러 메시지가 있으면 스낵바 표시
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("캐릭터 선택", fontWeight = FontWeight.Bold) }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(modelList) { modelId ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clickable { onModelSelected(modelId) },
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = modelId,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
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
