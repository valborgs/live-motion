package org.comon.domain.model

/**
 * Live2D 파라미터를 담는 래퍼 클래스
 * FacePose를 Live2D SDK 파라미터로 변환한 결과를 캡슐화합니다.
 *
 * @property params Live2D 파라미터 맵 (e.g., "ParamAngleX" to 15.0f)
 */
data class Live2DParams(val params: Map<String, Float>) {
    companion object {
        /** 기본 상태의 파라미터 (얼굴 미감지 시 사용) */
        val DEFAULT = Live2DParams(
            mapOf(
                "ParamAngleX" to 0f,
                "ParamAngleY" to 0f,
                "ParamAngleZ" to 0f,
                "ParamEyeLOpen" to 1f,
                "ParamEyeROpen" to 1f,
                "ParamMouthOpenY" to 0f,
                "ParamMouthForm" to 0f,
                "ParamBodyAngleX" to 0f,
                "ParamEyeBallX" to 0f,
                "ParamEyeBallY" to 0f
            )
        )
    }
}
