package org.comon.studio

import android.content.res.Configuration
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.comon.domain.model.BackgroundSource
import org.comon.domain.model.ModelSource
import org.comon.storage.SAFPermissionManager
import org.comon.studio.components.DeleteConfirmDialog
import org.comon.ui.snackbar.SnackbarStateHolder
import org.comon.ui.snackbar.rememberSnackbarStateHolder
import org.comon.ui.theme.LiveMotionTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrepareScreen(
    onNavigateBack: () -> Unit,
    onModelSelected: (ModelSource) -> Unit,
    errorMessage: String? = null,
    onErrorConsumed: () -> Unit = {},
    modelSelectViewModel: ModelSelectViewModel = hiltViewModel(),
    backgroundSelectViewModel: BackgroundSelectViewModel = hiltViewModel()
) {
    val context = LocalContext.current

    val modelSelectViewModelUiState by modelSelectViewModel.uiState.collectAsState()
    val backgroundSelectViewModelUiState by backgroundSelectViewModel.uiState.collectAsState()
    val snackbarState = rememberSnackbarStateHolder()

    // 탭 상태
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    // Model UI Effect 처리
    val snackbarAction = stringResource(R.string.snackbar_action_detail)
    LaunchedEffect(Unit) {
        modelSelectViewModel.uiEffect.collect { effect ->
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

    // Background UI Effect 처리
    LaunchedEffect(Unit) {
        backgroundSelectViewModel.uiEffect.collect { effect ->
            when (effect) {
                is BackgroundSelectUiEffect.ShowSnackbar -> {
                    snackbarState.showSnackbar(
                        message = effect.message,
                        actionLabel = effect.actionLabel,
                        duration = SnackbarDuration.Short
                    )
                }
                is BackgroundSelectUiEffect.ShowErrorWithDetail -> {
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

    // 모델 폴더 선택기 런처
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            safPermissionManager.persistPermission(it)
            modelSelectViewModel.onIntent(ModelSelectUiIntent.ImportModel(it.toString()))
        }
    }

    // 배경 이미지 선택기 런처
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            backgroundSelectViewModel.onIntent(BackgroundSelectUiIntent.ImportBackground(it.toString()))
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
        modelSelectViewModelUiState = modelSelectViewModelUiState,
        backgroundSelectViewModelUiState = backgroundSelectViewModelUiState,
        snackbarState = snackbarState,
        selectedTabIndex = selectedTabIndex,
        onTabSelected = { selectedTabIndex = it },
        onNavigateBack = onNavigateBack,
        onModelSelected = onModelSelected,
        onModelImportClick = { folderPickerLauncher.launch(null) },
        onBgImportClick = { imagePickerLauncher.launch(arrayOf("image/*")) },
        onModelIntent = modelSelectViewModel::onIntent,
        onBgIntent = backgroundSelectViewModel::onIntent,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrepareScreenContent(
    modelSelectViewModelUiState: ModelSelectViewModel.UiState,
    backgroundSelectViewModelUiState: BackgroundSelectViewModel.UiState,
    snackbarState: SnackbarStateHolder,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    onNavigateBack: () -> Unit,
    onModelSelected: (ModelSource) -> Unit,
    onModelImportClick: () -> Unit,
    onBgImportClick: () -> Unit,
    onModelIntent: (ModelSelectUiIntent) -> Unit,
    onBgIntent: (BackgroundSelectUiIntent) -> Unit,
) {
    val isLandscape =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    val tabTitles = listOf(
        stringResource(R.string.prepare_tab_character),
        stringResource(R.string.prepare_tab_background),
    )

    // 삭제 확인 다이얼로그 상태
    var showModelDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showBgDeleteConfirmDialog by remember { mutableStateOf(false) }

    // 현재 탭의 삭제 모드 여부
    val isAnyDeleteMode = (selectedTabIndex == 0 && modelSelectViewModelUiState.isDeleteMode) ||
        (selectedTabIndex == 1 && backgroundSelectViewModelUiState.isDeleteMode)

    // 삭제 모드에서 뒤로가기 처리
    BackHandler(enabled = isAnyDeleteMode) {
        when (selectedTabIndex) {
            0 -> onModelIntent(ModelSelectUiIntent.ExitDeleteMode)
            1 -> onBgIntent(BackgroundSelectUiIntent.ExitDeleteMode)
        }
    }

    Scaffold(
        topBar = {
            PrepareTopBar(
                selectedTabIndex = selectedTabIndex,
                tabTitles = tabTitles,
                isLandscape = isLandscape,
                modelUiState = modelSelectViewModelUiState,
                bgUiState = backgroundSelectViewModelUiState,
                onTabSelected = onTabSelected,
                onNavigateBack = onNavigateBack,
                onModelExitDeleteMode = { onModelIntent(ModelSelectUiIntent.ExitDeleteMode) },
                onBgExitDeleteMode = { onBgIntent(BackgroundSelectUiIntent.ExitDeleteMode) },
                onModelDeleteClick = { showModelDeleteConfirmDialog = true },
                onBgDeleteClick = { showBgDeleteConfirmDialog = true },
            )
        },
        snackbarHost = { SnackbarHost(snackbarState.snackbarHostState) },
        floatingActionButton = {
            // Portrait: FAB 표시 / Landscape: NavigationRail header로 이동
            if (!isLandscape && !isAnyDeleteMode) {
                when (selectedTabIndex) {
                    0 -> {
                        FloatingActionButton(
                            onClick = onModelImportClick,
                            containerColor = MaterialTheme.colorScheme.primary
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = stringResource(R.string.model_select_import)
                            )
                        }
                    }
                    1 -> {
                        FloatingActionButton(
                            onClick = onBgImportClick,
                            containerColor = MaterialTheme.colorScheme.primary
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = stringResource(R.string.background_select_import)
                            )
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        val gridColumns = if (isLandscape) 4 else 2

        if (isLandscape) {
            // === Landscape: NavigationRail + Content ===
            Row(modifier = Modifier.padding(paddingValues)) {
                NavigationRail(
                    header = if (!isAnyDeleteMode) {
                        {
                            FloatingActionButton(
                                onClick = when (selectedTabIndex) {
                                    0 -> onModelImportClick
                                    else -> onBgImportClick
                                },
                                containerColor = MaterialTheme.colorScheme.primary
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = when (selectedTabIndex) {
                                        0 -> stringResource(R.string.model_select_import)
                                        else -> stringResource(R.string.background_select_import)
                                    }
                                )
                            }
                        }
                    } else null
                ) {
                    NavigationRailItem(
                        selected = selectedTabIndex == 0,
                        onClick = { onTabSelected(0) },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = tabTitles[0]
                            )
                        },
                        label = { Text(tabTitles[0]) }
                    )
                    NavigationRailItem(
                        selected = selectedTabIndex == 1,
                        onClick = { onTabSelected(1) },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Home,
                                contentDescription = tabTitles[1]
                            )
                        },
                        label = { Text(tabTitles[1]) }
                    )
                }

                when (selectedTabIndex) {
                    0 -> ModelSelectContent(
                        gridColumns = gridColumns,
                        uiState = modelSelectViewModelUiState,
                        snackbarState = snackbarState,
                        onModelSelected = onModelSelected,
                        onIntent = onModelIntent,
                    )
                    1 -> BackgroundSelectContent(
                        gridColumns = gridColumns,
                        uiState = backgroundSelectViewModelUiState,
                        snackbarState = snackbarState,
                        onIntent = onBgIntent,
                    )
                }
            }
        } else {
            // === Portrait: 기존 레이아웃 ===
            when (selectedTabIndex) {
                0 -> ModelSelectContent(
                    modifier = Modifier.padding(paddingValues),
                    gridColumns = gridColumns,
                    uiState = modelSelectViewModelUiState,
                    snackbarState = snackbarState,
                    onModelSelected = onModelSelected,
                    onIntent = onModelIntent,
                )
                1 -> BackgroundSelectContent(
                    modifier = Modifier.padding(paddingValues),
                    gridColumns = gridColumns,
                    uiState = backgroundSelectViewModelUiState,
                    snackbarState = snackbarState,
                    onIntent = onBgIntent,
                )
            }
        }
    }

    // 모델 삭제 확인 다이얼로그
    if (showModelDeleteConfirmDialog) {
        DeleteConfirmDialog(
            count = modelSelectViewModelUiState.selectedModelIds.size,
            onConfirm = {
                showModelDeleteConfirmDialog = false
                onModelIntent(ModelSelectUiIntent.DeleteSelectedModels)
            },
            onDismiss = { showModelDeleteConfirmDialog = false }
        )
    }

    // 배경 삭제 확인 다이얼로그
    if (showBgDeleteConfirmDialog) {
        DeleteConfirmDialog(
            count = backgroundSelectViewModelUiState.selectedForDeletion.size,
            titleResId = R.string.dialog_bg_delete_title,
            messageResId = R.string.dialog_bg_delete_message,
            onConfirm = {
                showBgDeleteConfirmDialog = false
                onBgIntent(BackgroundSelectUiIntent.DeleteSelectedBackgrounds)
            },
            onDismiss = { showBgDeleteConfirmDialog = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrepareTopBar(
    selectedTabIndex: Int,
    tabTitles: List<String>,
    isLandscape: Boolean,
    modelUiState: ModelSelectViewModel.UiState,
    bgUiState: BackgroundSelectViewModel.UiState,
    onTabSelected: (Int) -> Unit,
    onNavigateBack: () -> Unit,
    onModelExitDeleteMode: () -> Unit,
    onBgExitDeleteMode: () -> Unit,
    onModelDeleteClick: () -> Unit,
    onBgDeleteClick: () -> Unit,
) {
    if (modelUiState.isDeleteMode && selectedTabIndex == 0) {
        // 모델 삭제 모드 앱바
        TopAppBar(
            title = {
                Text(
                    stringResource(R.string.model_select_selected_count, modelUiState.selectedModelIds.size),
                    fontWeight = FontWeight.Bold
                )
            },
            navigationIcon = {
                IconButton(onClick = onModelExitDeleteMode) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.model_select_delete_mode_exit)
                    )
                }
            },
            actions = {
                IconButton(
                    onClick = onModelDeleteClick,
                    enabled = modelUiState.selectedModelIds.isNotEmpty()
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.model_select_delete_selected),
                        tint = if (modelUiState.selectedModelIds.isNotEmpty()) {
                            Color(0xFFE53935)
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        }
                    )
                }
            }
        )
    } else if (bgUiState.isDeleteMode && selectedTabIndex == 1) {
        // 배경 삭제 모드 앱바
        TopAppBar(
            title = {
                Text(
                    stringResource(R.string.background_select_selected_count, bgUiState.selectedForDeletion.size),
                    fontWeight = FontWeight.Bold
                )
            },
            navigationIcon = {
                IconButton(onClick = onBgExitDeleteMode) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.background_select_delete_mode_exit)
                    )
                }
            },
            actions = {
                IconButton(
                    onClick = onBgDeleteClick,
                    enabled = bgUiState.selectedForDeletion.isNotEmpty()
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.background_select_delete_selected),
                        tint = if (bgUiState.selectedForDeletion.isNotEmpty()) {
                            Color(0xFFE53935)
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        }
                    )
                }
            }
        )
    } else if (isLandscape) {
        // Landscape 일반 모드: 탭 없는 TopAppBar만
        TopAppBar(
            title = { Text(stringResource(R.string.prepare_title), fontWeight = FontWeight.Bold) },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.studio_back)
                    )
                }
            }
        )
    } else {
        // Portrait 일반 모드: TopAppBar + TabRow
        Column {
            TopAppBar(
                title = { Text(stringResource(R.string.prepare_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.studio_back)
                        )
                    }
                }
            )
            PrimaryTabRow(selectedTabIndex = selectedTabIndex) {
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
}

@Preview
@Composable
private fun PrepareScreenPreview() {
    LiveMotionTheme {
        PrepareScreenContent(
            modelSelectViewModelUiState = ModelSelectViewModel.UiState(
                models = listOf(ModelSource.Asset("Haru"), ModelSource.Asset("Mark")),
            ),
            backgroundSelectViewModelUiState = BackgroundSelectViewModel.UiState(
                backgrounds = listOf(BackgroundSource.Default),
            ),
            snackbarState = rememberSnackbarStateHolder(),
            selectedTabIndex = 0,
            onTabSelected = {},
            onNavigateBack = {},
            onModelSelected = {},
            onModelImportClick = {},
            onBgImportClick = {},
            onModelIntent = {},
            onBgIntent = {},
        )
    }
}

@Preview
@Composable
private fun PrepareScreenBackgroundTabPreview() {
    LiveMotionTheme {
        PrepareScreenContent(
            modelSelectViewModelUiState = ModelSelectViewModel.UiState(),
            backgroundSelectViewModelUiState = BackgroundSelectViewModel.UiState(
                backgrounds = listOf(
                    BackgroundSource.Default,
                    BackgroundSource.Asset("sunset.png"),
                ),
                selectedBackgroundId = "default_white",
            ),
            snackbarState = rememberSnackbarStateHolder(),
            selectedTabIndex = 1,
            onTabSelected = {},
            onNavigateBack = {},
            onModelSelected = {},
            onModelImportClick = {},
            onBgImportClick = {},
            onModelIntent = {},
            onBgIntent = {},
        )
    }
}

@Preview(widthDp = 800, heightDp = 400)
@Composable
private fun PrepareScreenLandscapePreview() {
    LiveMotionTheme {
        PrepareScreenContent(
            modelSelectViewModelUiState = ModelSelectViewModel.UiState(
                models = listOf(
                    ModelSource.Asset("Haru"),
                    ModelSource.Asset("Mark"),
                    ModelSource.Asset("Natori"),
                    ModelSource.Asset("Rice"),
                ),
            ),
            backgroundSelectViewModelUiState = BackgroundSelectViewModel.UiState(
                backgrounds = listOf(BackgroundSource.Default),
            ),
            snackbarState = rememberSnackbarStateHolder(),
            selectedTabIndex = 0,
            onTabSelected = {},
            onNavigateBack = {},
            onModelSelected = {},
            onModelImportClick = {},
            onBgImportClick = {},
            onModelIntent = {},
            onBgIntent = {},
        )
    }
}
