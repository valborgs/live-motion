package org.comon.studio

/**
 * ModelSelect 화면의 사용자 의도(Intent)를 정의합니다.
 *
 * MVI 패턴에서 사용자 액션을 표현하는 sealed interface입니다.
 */
sealed interface ModelSelectUiIntent {
    /** 모델 목록 새로고침 */
    data object LoadModels : ModelSelectUiIntent

    /** 외부 모델 가져오기 */
    data class ImportModel(val folderUri: String) : ModelSelectUiIntent

    /** 삭제 모드 진입 */
    data class EnterDeleteMode(val initialModelId: String) : ModelSelectUiIntent

    /** 삭제 모드 종료 */
    data object ExitDeleteMode : ModelSelectUiIntent

    /** 모델 선택 토글 (삭제용) */
    data class ToggleModelSelection(val modelId: String) : ModelSelectUiIntent

    /** 선택된 모델 삭제 */
    data object DeleteSelectedModels : ModelSelectUiIntent
}
