package org.comon.domain.usecase

import org.comon.domain.common.Result
import org.comon.domain.model.Live2DModelInfo
import org.comon.domain.repository.IModelRepository

/**
 * 특정 Live2D 모델의 메타데이터를 조회하는 UseCase
 */
class GetModelMetadataUseCase(
    private val modelRepository: IModelRepository
) {
    /**
     * 모델 메타데이터를 조회합니다.
     * @param modelId 모델 ID
     * @return 모델 메타데이터 또는 에러
     */
    operator fun invoke(modelId: String): Result<Live2DModelInfo> {
        return modelRepository.getModelMetadata(modelId)
    }
}
