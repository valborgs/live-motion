package org.comon.navigation

import org.comon.domain.model.ModelSource

/**
 * 앱 내 네비게이션을 추상화하는 인터페이스.
 *
 * feature 모듈에서 app 모듈의 NavController를 직접 참조하지 않고
 * 네비게이션을 수행할 수 있도록 합니다.
 */
interface Navigator {
    /**
     * Studio 화면으로 이동합니다.
     *
     * @param modelSource 로드할 모델 소스 (Asset 또는 External)
     */
    fun navigateToStudio(modelSource: ModelSource)

    /**
     * 준비 화면으로 이동합니다.
     */
    fun navigateToPrepare()

    /**
     * 설정 화면으로 이동합니다.
     */
    fun navigateToSettings()

    /**
     * 이용약관 화면으로 이동합니다.
     */
    fun navigateToTermsOfService()

    /**
     * 타이틀 화면으로 이동합니다.
     */
    fun navigateToTitle()

    /**
     * 이전 화면으로 돌아갑니다.
     */
    fun back()

    /**
     * 모델 로드 에러를 이전 화면에 전달하고 뒤로 갑니다.
     *
     * @param errorMessage 에러 메시지
     */
    fun backWithError(errorMessage: String)
}
