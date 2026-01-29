package org.comon.domain.usecase

import org.comon.domain.common.Result
import org.comon.domain.repository.IModelRepository

/**
 * 사용 가능한 Live2D 모델 목록을 조회하는 UseCase
 */
class GetLive2DModelsUseCase(
    private val modelRepository: IModelRepository
) {
    /**
     * 모델 목록을 조회합니다.
     * @return 모델 ID 목록 또는 에러
     */
    operator fun invoke(): Result<List<String>> {
        return modelRepository.listLive2DModels()
    }
}
