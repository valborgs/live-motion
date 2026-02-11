package org.comon.studio

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.comon.domain.model.ModelSource
import org.comon.studio.components.DeletingProgressDialog
import org.comon.studio.components.ImportProgressDialog
import org.comon.studio.components.ModelCard
import org.comon.ui.snackbar.ErrorDetailDialog
import org.comon.ui.snackbar.SnackbarStateHolder

/**
 * ModelSelect의 본문 영역 (Scaffold 없이).
 *
 * 그리드, 다이얼로그를 포함하며 PrepareScreen에서 탭 콘텐츠로 재사용됩니다.
 */
@Composable
internal fun ModelSelectContent(
    modifier: Modifier = Modifier,
    uiState: ModelSelectViewModel.UiState,
    snackbarState: SnackbarStateHolder,
    onModelSelected: (ModelSource) -> Unit,
    onIntent: (ModelSelectUiIntent) -> Unit,
) {
    Box(
        modifier = modifier.fillMaxSize()
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
                                if (modelSource is ModelSource.External) {
                                    onIntent(ModelSelectUiIntent.ToggleModelSelection(modelSource.id))
                                }
                            } else {
                                onModelSelected(modelSource)
                            }
                        },
                        onLongClick = {
                            if (modelSource is ModelSource.External && !uiState.isDeleteMode) {
                                onIntent(ModelSelectUiIntent.EnterDeleteMode(modelSource.id))
                            }
                        }
                    )
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

    // 삭제 진행 다이얼로그
    if (uiState.isDeleting) {
        DeletingProgressDialog()
    }
}
