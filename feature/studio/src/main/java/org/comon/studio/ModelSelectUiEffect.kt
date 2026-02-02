package org.comon.studio

/**
 * ModelSelect 화면의 일회성 이벤트(Effect)를 정의합니다.
 *
 * MVI 패턴에서 UI에 전달되는 일회성 이벤트를 표현합니다.
 * 스낵바 표시, 네비게이션 등의 side effect를 처리합니다.
 */
sealed class ModelSelectUiEffect {
    /** 간단한 스낵바 메시지 표시 (상세보기 없음) */
    data class ShowSnackbar(
        val message: String,
        val actionLabel: String? = null
    ) : ModelSelectUiEffect()

    /** 
     * 에러 스낵바 표시 (상세보기 액션 포함)
     * 사용자가 액션 버튼 클릭 시 상세 다이얼로그 표시
     */
    data class ShowErrorWithDetail(
        val displayMessage: String,
        val detailMessage: String
    ) : ModelSelectUiEffect()
}
