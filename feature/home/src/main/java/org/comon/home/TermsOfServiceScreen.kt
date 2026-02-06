package org.comon.home

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.comon.ui.theme.LiveMotionTheme

@Composable
fun TermsOfServiceScreen(
    viewOnly: Boolean = false,
    onAgreed: () -> Unit = {},
    onBack: () -> Unit = {},
    viewModel: TermsOfServiceViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    if (!viewOnly) {
        LaunchedEffect(Unit) {
            viewModel.uiEffect.collect { effect ->
                when (effect) {
                    is TermsOfServiceUiEffect.NavigateToTitle -> onAgreed()
                }
            }
        }
    }

    TermsOfServiceScreenContent(
        uiState = uiState,
        viewOnly = viewOnly,
        onIntent = viewModel::onIntent,
        onBack = onBack
    )
}

@Composable
private fun TermsOfServiceScreenContent(
    uiState: TermsOfServiceViewModel.UiState,
    viewOnly: Boolean,
    onIntent: (TermsOfServiceUiIntent) -> Unit,
    onBack: () -> Unit
) {
    val scrollState = rememberScrollState()
    val reachedBottom by remember {
        derivedStateOf {
            val maxScroll = scrollState.maxValue
            maxScroll == 0 || scrollState.value >= maxScroll - 50
        }
    }

    if (!viewOnly) {
        LaunchedEffect(reachedBottom) {
            if (reachedBottom) {
                onIntent(TermsOfServiceUiIntent.ScrolledToBottom)
            }
        }
    }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.tos_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.tos_content),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.verticalScroll(scrollState)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (viewOnly) {
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.tos_back_button),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            } else {
                Button(
                    onClick = { onIntent(TermsOfServiceUiIntent.Agree) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    enabled = uiState.scrolledToBottom && !uiState.isLoading,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.tos_agree_button),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Preview(name = "Agree Mode", showBackground = true)
@Composable
private fun TermsOfServiceAgreePreview() {
    LiveMotionTheme {
        TermsOfServiceScreenContent(
            uiState = TermsOfServiceViewModel.UiState(scrolledToBottom = true),
            viewOnly = false,
            onIntent = {},
            onBack = {}
        )
    }
}

@Preview(name = "View Only", showBackground = true)
@Preview(
    name = "View Only Dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
private fun TermsOfServiceViewOnlyPreview() {
    LiveMotionTheme {
        TermsOfServiceScreenContent(
            uiState = TermsOfServiceViewModel.UiState(),
            viewOnly = true,
            onIntent = {},
            onBack = {}
        )
    }
}
