package org.comon.domain.repository

import org.comon.domain.common.Result
import org.comon.domain.model.ExternalModel
import org.comon.domain.model.ModelValidationResult

/**
 * 외부 Live2D 모델 관리를 위한 Repository 인터페이스
 * SAF를 통해 선택한 외부 모델의 캐싱 및 관리를 담당합니다.
 */
interface IExternalModelRepository {

    /**
     * 캐시된 외부 모델 목록을 반환합니다.
     * @return 외부 모델 목록 또는 에러
     */
    suspend fun listExternalModels(): Result<List<ExternalModel>>

    /**
     * SAF URI의 모델 폴더를 검증합니다.
     * @param folderUri SAF document tree URI
     * @return 검증 결과
     */
    suspend fun validateModel(folderUri: String): Result<ModelValidationResult>

    /**
     * SAF URI에서 모델을 내부 캐시로 가져옵니다.
     * @param folderUri SAF document tree URI
     * @param onProgress 진행률 콜백 (0.0 ~ 1.0)
     * @return 가져온 모델 정보 또는 에러
     */
    suspend fun importModel(
        folderUri: String,
        onProgress: (Float) -> Unit = {}
    ): Result<ExternalModel>

    /**
     * ID로 외부 모델을 조회합니다.
     * @param modelId 모델 ID
     * @return 모델 정보 또는 에러
     */
    suspend fun getModel(modelId: String): Result<ExternalModel>

    /**
     * 캐시된 외부 모델을 삭제합니다.
     * @param modelId 모델 ID
     * @return 성공 또는 에러
     */
    suspend fun deleteModel(modelId: String): Result<Unit>

    /**
     * 외부 모델의 캐시 경로를 반환합니다 (Live2D 로딩용).
     * @param modelId 모델 ID
     * @return 캐시 경로 또는 null (캐시되지 않은 경우)
     */
    fun getCachePath(modelId: String): String?

    /**
     * 마지막 접근 시간을 업데이트합니다.
     * @param modelId 모델 ID
     */
    suspend fun updateLastAccessed(modelId: String)

    /**
     * LRU 정책에 따라 오래된 캐시를 정리합니다.
     * @param maxCacheSizeBytes 최대 캐시 크기 (바이트)
     * @param maxAgeMillis 최대 보관 기간 (밀리초)
     * @return 삭제된 모델 수 또는 에러
     */
    suspend fun cleanupCache(
        maxCacheSizeBytes: Long,
        maxAgeMillis: Long
    ): Result<Int>
}
