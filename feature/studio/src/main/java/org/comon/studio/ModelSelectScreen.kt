package org.comon.studio

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSelectScreen(
    onModelSelected: (String) -> Unit
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
            } catch (e: Exception) {
                false
            }
        } ?: emptyList()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("캐릭터 선택", fontWeight = FontWeight.Bold) }
            )
        }
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
}
