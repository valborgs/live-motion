package org.comon.domain.model

/**
 * 외부 저장소에서 로드한 Live2D 모델 정보
 *
 * @property id 고유 식별자 (원본 URI의 SHA-256 해시)
 * @property name 표시 이름
 * @property originalUri SAF URI
 * @property cachePath 내부 저장소 캐시 경로
 * @property modelJsonName model3.json 파일명
 * @property sizeBytes 모델 파일 총 크기
 * @property cachedAt 캐시된 시간 (밀리초)
 * @property lastAccessedAt 마지막 접근 시간 (밀리초)
 */
data class ExternalModel(
    val id: String,
    val name: String,
    val originalUri: String,
    val cachePath: String,
    val modelJsonName: String,
    val sizeBytes: Long,
    val cachedAt: Long,
    val lastAccessedAt: Long
)

/**
 * 모델 유효성 검사 결과
 */
data class ModelValidationResult(
    val isValid: Boolean,
    val modelJsonName: String?,
    val cubismVersion: String?,
    val errorMessage: String?
)

/**
 * 모델 소스 타입 (Asset 또는 External)
 */
sealed class ModelSource {
    /**
     * Assets 폴더에 포함된 내장 모델
     */
    data class Asset(val modelId: String) : ModelSource()

    /**
     * 외부 저장소에서 가져온 모델
     */
    data class External(val model: ExternalModel) : ModelSource()

    /**
     * 모델 ID 반환 (Asset은 modelId, External은 model.id)
     */
    val id: String
        get() = when (this) {
            is Asset -> modelId
            is External -> model.id
        }

    /**
     * 표시 이름 반환
     */
    val displayName: String
        get() = when (this) {
            is Asset -> modelId
            is External -> model.name
        }
}
