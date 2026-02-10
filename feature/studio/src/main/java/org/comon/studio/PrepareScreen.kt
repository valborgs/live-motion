package org.comon.studio

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.comon.domain.model.ModelSource
import org.comon.storage.SAFPermissionManager
import org.comon.studio.components.DeleteConfirmDialog
import org.comon.ui.snackbar.SnackbarStateHolder
import org.comon.ui.snackbar.rememberSnackbarStateHolder
import org.comon.ui.theme.LiveMotionTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrepareScreen(
    onModelSelected: (ModelSource) -> Unit,
    errorMessage: String? = null,
    onErrorConsumed: () -> Unit = {},
    viewModel: ModelSelectViewModel = hiltViewModel()
) {
    val context = LocalContext.current

    val uiState by viewModel.uiState.collectAsState()
    val snackbarState = rememberSnackbarStateHolder()

    // 탭 상태
    var selectedTabIndex by remember { mutableIntStateOf(0) }

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
            safPermissionManager.persistPermission(it)
            viewModel.onIntent(ModelSelectUiIntent.ImportModel(it.toString()))
        }
    }

    val snackbarMessage = stringResource(R.string.snackbar_model_load_failed)
    // 외부 에러 메시지 처리 (StudioScreen에서 전달된 에러)
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

    PrepareScreenContent(
        uiState = uiState,
        snackbarState = snackbarState,
        selectedTabIndex = selectedTabIndex,
        onTabSelected = { selectedTabIndex = it },
        onModelSelected = onModelSelected,
        onImportClick = { folderPickerLauncher.launch(null) },
        onIntent = viewModel::onIntent,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrepareScreenContent(
    uiState: ModelSelectViewModel.UiState,
    snackbarState: SnackbarStateHolder,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    onModelSelected: (ModelSource) -> Unit,
    onImportClick: () -> Unit,
    onIntent: (ModelSelectUiIntent) -> Unit,
) {
    val tabTitles = listOf(
        stringResource(R.string.prepare_tab_character),
        stringResource(R.string.prepare_tab_background),
    )

    // 삭제 확인 다이얼로그 상태
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    // 삭제 모드에서 뒤로가기 처리
    BackHandler(enabled = uiState.isDeleteMode) {
        onIntent(ModelSelectUiIntent.ExitDeleteMode)
    }

    Scaffold(
        topBar = {
            if (uiState.isDeleteMode && selectedTabIndex == 0) {
                // 삭제 모드 앱바 (캐릭터 탭에서만)
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
                // 일반 모드: TabRow
                Column {
                    TopAppBar(
                        title = { Text(stringResource(R.string.prepare_title), fontWeight = FontWeight.Bold) }
                    )
                    TabRow(selectedTabIndex = selectedTabIndex) {
                        tabTitles.forEachIndexed { index, title ->
                            Tab(
                                selected = selectedTabIndex == index,
                                onClick = { onTabSelected(index) },
                                text = { Text(title) }
                            )
                        }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarState.snackbarHostState) },
        floatingActionButton = {
            // 캐릭터 탭 + 일반 모드일 때만 FAB 표시
            if (selectedTabIndex == 0 && !uiState.isDeleteMode) {
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
        when (selectedTabIndex) {
            0 -> ModelSelectContent(
                modifier = Modifier.padding(paddingValues),
                uiState = uiState,
                snackbarState = snackbarState,
                onModelSelected = onModelSelected,
                onImportClick = onImportClick,
                onIntent = onIntent,
            )
            1 -> BackgroundSelectContent(
                modifier = Modifier.padding(paddingValues),
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
}

@Preview
@Composable
private fun PrepareScreenPreview() {
    LiveMotionTheme {
        PrepareScreenContent(
            uiState = ModelSelectViewModel.UiState(
                models = listOf(ModelSource.Asset("Haru"), ModelSource.Asset("Mark")),
            ),
            snackbarState = rememberSnackbarStateHolder(),
            selectedTabIndex = 0,
            onTabSelected = {},
            onModelSelected = {},
            onImportClick = {},
            onIntent = {},
        )
    }
}

@Preview
@Composable
private fun PrepareScreenBackgroundTabPreview() {
    LiveMotionTheme {
        PrepareScreenContent(
            uiState = ModelSelectViewModel.UiState(),
            snackbarState = rememberSnackbarStateHolder(),
            selectedTabIndex = 1,
            onTabSelected = {},
            onModelSelected = {},
            onImportClick = {},
            onIntent = {},
        )
    }
}
