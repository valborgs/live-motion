package org.comon.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.comon.ui.theme.LiveMotionTheme

@Composable
fun IntroScreen(
    onTimeout: () -> Unit
) {
    LaunchedEffect(Unit) {
        delay(1000L)
        onTimeout()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.cubism_logo_orange),
            contentDescription = stringResource(R.string.intro_logo_description),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 48.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun IntroScreenPreview() {
    LiveMotionTheme {
        IntroScreen(onTimeout = {})
    }
}
