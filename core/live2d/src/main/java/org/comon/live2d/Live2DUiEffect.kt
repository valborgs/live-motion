package org.comon.live2d

import android.view.Surface

/**
 * Live2D 렌더링 관련 일회성 이벤트를 정의합니다.
 *
 * ViewModel에서 Channel을 통해 전송하고,
 * Live2DScreen에서 수신하여 GL 스레드에서 처리합니다.
 */
sealed interface Live2DUiEffect {
    /** 표정 시작 */
    data class StartExpression(val path: String) : Live2DUiEffect

    /** 표정 초기화 */
    data object ClearExpression : Live2DUiEffect

    /** 모션 시작 */
    data class StartMotion(val path: String) : Live2DUiEffect

    /** 모션 초기화 */
    data object ClearMotion : Live2DUiEffect

    /** Transform(위치/스케일) 리셋 */
    data object ResetTransform : Live2DUiEffect

    /** 녹화용 Surface 설정 (null이면 녹화 중단) */
    data class SetRecordingSurface(val surface: Surface?) : Live2DUiEffect
}
