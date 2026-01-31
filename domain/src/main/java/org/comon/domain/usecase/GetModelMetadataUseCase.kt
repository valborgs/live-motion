package org.comon.domain.usecase

import org.comon.domain.common.Result
import org.comon.domain.model.Live2DModelInfo
import org.comon.domain.model.ModelSource
import org.comon.domain.repository.IExternalModelRepository
import org.comon.domain.repository.IModelRepository

/**
 * Live2D 모델의 메타데이터를 조회하는 UseCase
 * Asset 모델과 External 모델 모두 지원합니다.
 */
class GetModelMetadataUseCase(
    private val modelRepository: IModelRepository,
    private val externalModelRepository: IExternalModelRepository
) {
    /**
     * 모델 메타데이터를 조회합니다.
     * @param modelSource 모델 소스 (Asset 또는 External)
     * @return 모델 메타데이터 또는 에러
     */
    suspend operator fun invoke(modelSource: ModelSource): Result<Live2DModelInfo> {
        return when (modelSource) {
            is ModelSource.Asset -> modelRepository.getModelMetadata(modelSource.modelId)
            is ModelSource.External -> externalModelRepository.getModelMetadata(modelSource.model.id)
        }
    }
}
