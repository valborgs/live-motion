package org.comon.studio.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.comon.domain.model.ModelSource
import org.comon.studio.R
import org.comon.ui.theme.LiveMotionTheme

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ModelCard(
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
