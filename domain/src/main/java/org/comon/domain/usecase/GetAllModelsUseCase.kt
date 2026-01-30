package org.comon.domain.usecase

import org.comon.domain.common.Result
import org.comon.domain.model.ModelSource
import org.comon.domain.repository.IExternalModelRepository
import org.comon.domain.repository.IModelRepository

/**
 * 모든 사용 가능한 모델(Asset + External)을 조회하는 UseCase
 */
class GetAllModelsUseCase(
    private val modelRepository: IModelRepository,
    private val externalModelRepository: IExternalModelRepository
) {
    /**
     * Asset 모델과 외부 모델을 합쳐서 반환합니다.
     * @return 모든 모델 소스 목록
     */
    suspend operator fun invoke(): Result<List<ModelSource>> {
        val assetModels = modelRepository.listLive2DModels().getOrNull() ?: emptyList()
        val externalModels = externalModelRepository.listExternalModels().getOrNull() ?: emptyList()

        val combined = assetModels.map { ModelSource.Asset(it) } +
            externalModels.map { ModelSource.External(it) }

        return Result.success(combined)
    }
}
