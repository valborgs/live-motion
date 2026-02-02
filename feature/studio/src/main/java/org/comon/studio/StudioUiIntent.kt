package org.comon.studio

/**
 * Studio 화면의 사용자 의도(Intent)를 정의합니다.
 *
 * MVI 패턴에서 사용자 액션을 표현하는 sealed interface입니다.
 */
sealed interface StudioUiIntent {
    /** 확대/축소 모드 토글 */
    data object ToggleZoom : StudioUiIntent

    /** 이동 모드 토글 */
    data object ToggleMove : StudioUiIntent

    /** 카메라 프리뷰 표시 토글 */
    data object TogglePreview : StudioUiIntent

    /** GPU 사용 여부 변경 */
    data class SetGpuEnabled(val enabled: Boolean) : StudioUiIntent

    /** 감정 다이얼로그 표시 */
    data object ShowExpressionDialog : StudioUiIntent

    /** 모션 다이얼로그 표시 */
    data object ShowMotionDialog : StudioUiIntent

    /** 다이얼로그 닫기 */
    data object DismissDialog : StudioUiIntent

    /** 트래킹 에러 초기화 */
    data object ClearTrackingError : StudioUiIntent

    /** 도메인 에러 초기화 */
    data object ClearDomainError : StudioUiIntent

    /** 모델 로딩 완료 */
    data object OnModelLoaded : StudioUiIntent
}
