package org.comon.domain.usecase

import org.comon.domain.common.Result
import org.comon.domain.repository.IExternalModelRepository

/**
 * 외부 모델들을 삭제하는 UseCase
 */
class DeleteExternalModelsUseCase(
    private val externalModelRepository: IExternalModelRepository
) {
    /**
     * 여러 외부 모델을 삭제합니다.
     * @param modelIds 삭제할 모델 ID 목록
     * @return 성공 또는 에러 (첫 번째 발생한 에러)
     */
    suspend operator fun invoke(modelIds: List<String>): Result<Unit> {
        for (modelId in modelIds) {
            val result = externalModelRepository.deleteModel(modelId)
            if (result.isError) {
                return result
            }
        }
        return Result.success(Unit)
    }
}
