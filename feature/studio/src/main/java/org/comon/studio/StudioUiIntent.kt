package org.comon.studio

/**
 * Studio 화면의 사용자 의도(Intent)를 정의합니다.
 *
 * MVI 패턴에서 사용자 액션을 표현하는 sealed interface입니다.
 */
sealed interface StudioUiIntent {
    /** 모델 제스처 모드 토글 (드래그 이동 + 핀치 확대/축소) */
    data object ToggleGesture : StudioUiIntent

    /** 배경 제스처 모드 토글 (배경 확대/이동) */
    data object ToggleBackgroundGesture : StudioUiIntent

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

    /** 모델 로딩 완료 */
    data object OnModelLoaded : StudioUiIntent

    /** 표정 시작 */
    data class StartExpression(val path: String) : StudioUiIntent

    /** 표정 초기화 */
    data object ClearExpression : StudioUiIntent

    /** 모션 시작 */
    data class StartMotion(val path: String) : StudioUiIntent

    /** 모션 초기화 */
    data object ClearMotion : StudioUiIntent

    /** 모델 Transform(위치/스케일) 리셋 */
    data object ResetTransform : StudioUiIntent

    // ━━━━━━ 녹화 관련 ━━━━━━

    /** 녹화 모드 토글 (설정 패널의 녹화 버튼) */
    data object ToggleRecordingMode : StudioUiIntent

    /** 녹화 시작 */
    data object StartRecording : StudioUiIntent

    /** 녹화 정지 */
    data object StopRecording : StudioUiIntent

    /** 녹화 일시정지/재개 토글 */
    data object TogglePauseRecording : StudioUiIntent

    /** RECORD_AUDIO 권한 결과 수신 */
    data class OnAudioPermissionResult(val granted: Boolean) : StudioUiIntent

    /** SAF 파일 저장 위치 선택 결과 */
    data class OnSaveLocationSelected(val uriString: String?) : StudioUiIntent

    /** GL Surface 크기 수신 */
    data class OnSurfaceSizeAvailable(val width: Int, val height: Int) : StudioUiIntent

    // ━━━━━━ 영상/음성 분리 관련 ━━━━━━

    /** 분리 확인 "예" */
    data object ConfirmSplit : StudioUiIntent

    /** 분리 확인 "아니오" */
    data object DeclineSplit : StudioUiIntent

    /** 분리 작업 취소 */
    data object CancelSplit : StudioUiIntent

    /** SAF 폴더 선택 결과 */
    data class OnSplitFolderSelected(val uriString: String?) : StudioUiIntent
}
