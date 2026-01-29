package org.comon.domain.repository

import org.comon.domain.common.Result
import org.comon.domain.model.Live2DModelInfo

/**
 * Live2D 모델 데이터에 접근하기 위한 Repository 인터페이스
 * Clean Architecture에서 Domain Layer는 이 인터페이스만 알고,
 * 실제 구현은 Data Layer에서 제공합니다.
 */
interface IModelRepository {

    /**
     * 사용 가능한 Live2D 모델 목록을 반환합니다.
     * @return 모델 ID 목록 또는 에러
     */
    fun listLive2DModels(): Result<List<String>>

    /**
     * 특정 모델의 메타데이터를 반환합니다.
     * @param modelId 모델 ID
     * @return 모델 메타데이터 또는 에러
     */
    fun getModelMetadata(modelId: String): Result<Live2DModelInfo>

    /**
     * 모델이 존재하는지 확인합니다.
     * @param modelId 모델 ID
     * @return 존재 여부
     */
    fun modelExists(modelId: String): Boolean
}
