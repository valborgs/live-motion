package org.comon.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Mint80,
    onPrimary = Neutral10,
    primaryContainer = Mint30,
    onPrimaryContainer = Mint90,
    secondary = Teal80,
    onSecondary = Neutral10,
    secondaryContainer = Teal40,
    onSecondaryContainer = Color.White,
    tertiary = Coral80,
    onTertiary = Neutral10,
    tertiaryContainer = Coral40,
    onTertiaryContainer = Color.White,
    background = Neutral10,
    onBackground = Neutral99,
    surface = Neutral10,
    onSurface = Neutral99,
    surfaceVariant = NeutralVariant30,
    onSurfaceVariant = NeutralVariant90
)

private val LightColorScheme = lightColorScheme(
    primary = Mint70,
    onPrimary = Color.White,
    primaryContainer = Mint90,
    onPrimaryContainer = Mint30,
    secondary = Teal40,
    onSecondary = Color.White,
    secondaryContainer = Teal80,
    onSecondaryContainer = Neutral10,
    tertiary = Coral40,
    onTertiary = Color.White,
    tertiaryContainer = Coral80,
    onTertiaryContainer = Neutral10,
    background = Neutral99,
    onBackground = Neutral10,
    surface = Neutral99,
    onSurface = Neutral10,
    surfaceVariant = NeutralVariant90,
    onSurfaceVariant = NeutralVariant30
)

@Composable
fun LiveMotionTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // 민트 테마 사용을 위해 기본값 false
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
