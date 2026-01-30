package org.comon.domain.usecase

import org.comon.domain.common.DomainException
import org.comon.domain.common.Result
import org.comon.domain.model.ExternalModel
import org.comon.domain.repository.IExternalModelRepository

/**
 * 외부 모델을 검증하고 캐시로 가져오는 UseCase
 */
class ImportExternalModelUseCase(
    private val externalModelRepository: IExternalModelRepository
) {
    /**
     * 외부 모델을 검증 후 가져옵니다.
     * @param folderUri SAF document tree URI
     * @param onProgress 진행률 콜백 (0.0 ~ 1.0)
     * @return 가져온 모델 정보 또는 에러
     */
    suspend operator fun invoke(
        folderUri: String,
        onProgress: (Float) -> Unit = {}
    ): Result<ExternalModel> {
        // 먼저 검증
        val validationResult = externalModelRepository.validateModel(folderUri)
        if (validationResult.isError) {
            return Result.error(validationResult.exceptionOrNull()!!)
        }

        val validation = validationResult.getOrNull()!!
        if (!validation.isValid) {
            return Result.error(
                DomainException.ExternalModelException.InvalidModelFormat(
                    validation.errorMessage ?: "Invalid model format"
                )
            )
        }

        // 검증 통과 후 가져오기
        return externalModelRepository.importModel(folderUri, onProgress)
    }
}
