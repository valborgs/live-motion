package org.comon.domain.usecase

import org.comon.domain.common.Result
import org.comon.domain.model.ExternalBackground
import org.comon.domain.repository.IBackgroundRepository

class ImportBackgroundUseCase(
    private val backgroundRepository: IBackgroundRepository
) {
    suspend operator fun invoke(
        fileUri: String,
        onProgress: (Float) -> Unit = {}
    ): Result<ExternalBackground> {
        return backgroundRepository.importBackground(fileUri, onProgress)
    }
}
