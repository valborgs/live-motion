package org.comon.common.di

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Compose에서 AppContainer에 접근하기 위한 CompositionLocal
 */
val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("AppContainer has not been provided. Wrap your content with CompositionLocalProvider.")
}
