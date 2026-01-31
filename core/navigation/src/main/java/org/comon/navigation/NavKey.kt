package org.comon.navigation

import kotlinx.serialization.Serializable

@Serializable
sealed interface NavKey {
    @Serializable
    data object Intro : NavKey

    @Serializable
    data object ModelSelect : NavKey

    /**
     * 스튜디오 화면 네비게이션 키
     * @param modelId 모델 ID (Asset 또는 External 모델 모두 사용)
     * @param isExternal 외부 모델 여부
     * @param cachePath 외부 모델의 캐시 경로 (isExternal=true일 때만 사용)
     * @param modelJsonName 외부 모델의 model3.json 파일명 (isExternal=true일 때만 사용)
     */
    @Serializable
    data class Studio(
        val modelId: String,
        val isExternal: Boolean = false,
        val cachePath: String? = null,
        val modelJsonName: String? = null
    ) : NavKey
}
