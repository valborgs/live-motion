package org.comon.studio.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.comon.domain.model.BackgroundSource
import org.comon.studio.R
import org.comon.ui.theme.LiveMotionTheme
import java.io.File

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun BackgroundCard(
    backgroundSource: BackgroundSource,
    isSelected: Boolean,
    isDeleteMode: Boolean,
    isSelectedForDeletion: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
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
            isDeleteMode && !backgroundSource.isExternal -> {
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            }
            else -> CardDefaults.cardColors()
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 배경 썸네일
            when (backgroundSource) {
                is BackgroundSource.Default -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.White)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = backgroundSource.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            color = Color.DarkGray
                        )
                    }
                }
                is BackgroundSource.Asset -> {
                    val context = LocalContext.current
                    val bitmap = remember(backgroundSource.name) {
                        try {
                            val stream = context.assets.open("backgrounds/${backgroundSource.name}")
                            BitmapFactory.decodeStream(stream)?.asImageBitmap()
                        } catch (_: Exception) {
                            null
                        }
                    }
                    BackgroundThumbnail(
                        bitmap = bitmap,
                        displayName = backgroundSource.displayName,
                        isDisabled = isDeleteMode
                    )
                }
                is BackgroundSource.External -> {
                    val bitmap = remember(backgroundSource.background.cachePath) {
                        try {
                            val file = File(backgroundSource.background.cachePath)
                            if (file.exists()) {
                                BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
                            } else null
                        } catch (_: Exception) {
                            null
                        }
                    }
                    BackgroundThumbnail(
                        bitmap = bitmap,
                        displayName = backgroundSource.displayName,
                        isDisabled = false
                    )
                }
            }

            // 이름 라벨 (하단)
            if (backgroundSource !is BackgroundSource.Default) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = backgroundSource.displayName,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (backgroundSource.isExternal) {
                        Text(
                            text = stringResource(R.string.background_select_external_label),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.align(Alignment.CenterEnd)
                        )
                    }
                }
            }

            // 선택 체크마크 (삭제 모드가 아닐 때)
            if (!isDeleteMode && isSelected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(24.dp)
                        .background(
                            color = Color(0xFF4CAF50),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // 삭제 모드 체크박스 (외부 배경만)
            if (isDeleteMode && backgroundSource.isExternal) {
                Checkbox(
                    checked = isSelectedForDeletion,
                    onCheckedChange = { onClick() },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp),
                    colors = CheckboxDefaults.colors(
                        checkedColor = Color(0xFFE53935)
                    )
                )
            }
        }
    }
}

@Composable
private fun BackgroundThumbnail(
    bitmap: ImageBitmap?,
    displayName: String,
    isDisabled: Boolean,
) {
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = displayName,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alpha = if (isDisabled) 0.5f else 1f
        )
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = displayName,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Preview(name = "Default Background")
@Composable
private fun BackgroundCardDefaultPreview() {
    LiveMotionTheme {
        BackgroundCard(
            backgroundSource = BackgroundSource.Default,
            isSelected = true,
            isDeleteMode = false,
            isSelectedForDeletion = false,
            onClick = {},
            onLongClick = {}
        )
    }
}

@Preview(name = "Asset Background")
@Composable
private fun BackgroundCardAssetPreview() {
    LiveMotionTheme {
        BackgroundCard(
            backgroundSource = BackgroundSource.Asset("sample.png"),
            isSelected = false,
            isDeleteMode = false,
            isSelectedForDeletion = false,
            onClick = {},
            onLongClick = {}
        )
    }
}
