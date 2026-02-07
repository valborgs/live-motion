package org.comon.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.comon.domain.model.ThemeMode
import org.comon.ui.theme.LiveMotionTheme

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    SettingsScreenContent(
        uiState = uiState,
        onIntent = viewModel::onIntent,
        onBack = onBack
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreenContent(
    uiState: SettingsViewModel.SettingsUiState,
    onIntent: (SettingsUiIntent) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            // 테마 설정
            // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.settings_theme),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(12.dp))

            ThemeModeSelector(
                selected = uiState.themeMode,
                onSelect = { onIntent(SettingsUiIntent.UpdateThemeMode(it)) }
            )

            // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            // 트래킹 감도
            // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.settings_tracking_sensitivity),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(16.dp))

            SensitivitySlider(
                label = stringResource(R.string.settings_yaw),
                value = uiState.yaw,
                onValueChange = { onIntent(SettingsUiIntent.UpdateYaw(it)) }
            )

            SensitivitySlider(
                label = stringResource(R.string.settings_pitch),
                value = uiState.pitch,
                onValueChange = { onIntent(SettingsUiIntent.UpdatePitch(it)) }
            )

            SensitivitySlider(
                label = stringResource(R.string.settings_roll),
                value = uiState.roll,
                onValueChange = { onIntent(SettingsUiIntent.UpdateRoll(it)) }
            )

            // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            // 스무딩 강도
            // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.settings_smoothing),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(16.dp))

            SmoothingSlider(
                value = uiState.smoothing,
                onValueChange = { onIntent(SettingsUiIntent.UpdateSmoothing(it)) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = { onIntent(SettingsUiIntent.ResetToDefault) },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(stringResource(R.string.settings_reset_to_default))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeModeSelector(
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit
) {
    val options = listOf(
        ThemeMode.SYSTEM to stringResource(R.string.settings_theme_system),
        ThemeMode.LIGHT to stringResource(R.string.settings_theme_light),
        ThemeMode.DARK to stringResource(R.string.settings_theme_dark)
    )

    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (mode, label) ->
            SegmentedButton(
                selected = selected == mode,
                onClick = { onSelect(mode) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size)
            ) {
                Text(label)
            }
        }
    }
}

@Composable
private fun SensitivitySlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = String.format("%.1fx", value),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0.5f..2.0f,
            steps = 5,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SmoothingSlider(
    value: Float,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.settings_smoothing_label),
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = String.format("%.1f", value),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.settings_smoothing_smooth),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.settings_smoothing_responsive),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0.1f..0.8f,
            steps = 6,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Preview(name = "Light Mode", showBackground = true)
@Preview(
    name = "Dark Mode",
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES
)
@Composable
private fun SettingsScreenPreview() {
    LiveMotionTheme {
        SettingsScreenContent(
            uiState = SettingsViewModel.SettingsUiState(),
            onIntent = {},
            onBack = {}
        )
    }
}
