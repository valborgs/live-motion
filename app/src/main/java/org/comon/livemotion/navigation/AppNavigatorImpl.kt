package org.comon.livemotion.navigation

import androidx.navigation.NavController
import org.comon.domain.model.ModelSource
import org.comon.navigation.NavKey
import org.comon.navigation.Navigator

/**
 * Navigator 인터페이스의 구현체.
 *
 * NavController를 캡슐화하여 feature 모듈에서 app 모듈의
 * Navigation 로직에 직접 의존하지 않도록 합니다.
 *
 * @property navController Jetpack Navigation의 NavController
 */
class AppNavigatorImpl(
    private val navController: NavController
) : Navigator {

    override fun navigateToStudio(modelSource: ModelSource) {
        when (modelSource) {
            is ModelSource.Asset -> {
                navController.navigate(
                    NavKey.Studio(modelId = modelSource.modelId)
                )
            }
            is ModelSource.External -> {
                navController.navigate(
                    NavKey.Studio(
                        modelId = modelSource.model.id,
                        isExternal = true,
                        cachePath = modelSource.model.cachePath,
                        modelJsonName = modelSource.model.modelJsonName
                    )
                )
            }
        }
    }

    override fun navigateToModelSelect() {
        navController.navigate(NavKey.ModelSelect)
    }

    override fun navigateToSettings() {
        navController.navigate(NavKey.Settings)
    }

    override fun navigateToTermsOfService() {
        navController.navigate(NavKey.TermsOfService()) {
            popUpTo(NavKey.Intro) { inclusive = true }
        }
    }

    fun navigateToTermsOfServiceViewOnly() {
        navController.navigate(NavKey.TermsOfService(viewOnly = true))
    }

    override fun navigateToTitle() {
        navController.navigate(NavKey.Title) {
            popUpTo(0) { inclusive = true }
        }
    }

    override fun back() {
        navController.popBackStack()
    }

    override fun backWithError(errorMessage: String) {
        // 이전 화면(ModelSelect)의 savedStateHandle에 에러 메시지 저장
        navController.previousBackStackEntry
            ?.savedStateHandle
            ?.set("model_load_error", errorMessage)
        navController.popBackStack()
    }
}
