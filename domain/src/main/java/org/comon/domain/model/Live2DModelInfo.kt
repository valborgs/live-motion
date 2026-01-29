package org.comon.domain.model

/**
 * Live2D 모델의 메타데이터를 담는 데이터 클래스
 *
 * @property modelId 모델 ID (assets 내 폴더 이름)
 * @property expressionsFolder expressions 폴더 이름 (대소문자 원본, null이면 없음)
 * @property motionsFolder motions 폴더 이름 (대소문자 원본, null이면 없음)
 * @property expressionFiles 표정 파일 목록
 * @property motionFiles 모션 파일 목록
 */
data class Live2DModelInfo(
    val modelId: String,
    val expressionsFolder: String?,
    val motionsFolder: String?,
    val expressionFiles: List<String>,
    val motionFiles: List<String>
)
