package org.comon.studio

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.comon.studio.components.BackgroundCard
import org.comon.studio.components.DeletingProgressDialog
import org.comon.studio.components.ImportProgressDialog
import org.comon.ui.snackbar.ErrorDetailDialog
import org.comon.ui.snackbar.SnackbarStateHolder

@Composable
internal fun BackgroundSelectContent(
    modifier: Modifier = Modifier,
    uiState: BackgroundSelectViewModel.UiState = BackgroundSelectViewModel.UiState(),
    snackbarState: SnackbarStateHolder? = null,
    onIntent: (BackgroundSelectUiIntent) -> Unit = {},
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
                items(uiState.backgrounds) { bg ->
                    BackgroundCard(
                        backgroundSource = bg,
                        isSelected = bg.id == uiState.selectedBackgroundId,
                        isDeleteMode = uiState.isDeleteMode,
                        isSelectedForDeletion = bg.id in uiState.selectedForDeletion,
                        onClick = {
                            if (uiState.isDeleteMode) {
                                if (bg.isExternal) {
                                    onIntent(BackgroundSelectUiIntent.ToggleBackgroundSelection(bg.id))
                                }
                            } else {
                                onIntent(BackgroundSelectUiIntent.SelectBackground(bg.id))
                            }
                        },
                        onLongClick = {
                            if (bg.isExternal && !uiState.isDeleteMode) {
                                onIntent(BackgroundSelectUiIntent.EnterDeleteMode(bg.id))
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
    if (snackbarState?.showErrorDialog == true) {
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
