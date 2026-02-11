package org.comon.domain.usecase

import org.comon.domain.common.Result
import org.comon.domain.model.BackgroundSource
import org.comon.domain.repository.IBackgroundRepository

class GetAllBackgroundsUseCase(
    private val backgroundRepository: IBackgroundRepository
) {
    suspend operator fun invoke(): Result<List<BackgroundSource>> {
        val assetBackgrounds = backgroundRepository.listAssetBackgrounds().getOrNull() ?: emptyList()
        val externalBackgrounds = backgroundRepository.listExternalBackgrounds().getOrNull() ?: emptyList()

        val combined = listOf(BackgroundSource.Default) +
            assetBackgrounds.map { BackgroundSource.Asset(it) } +
            externalBackgrounds.map { BackgroundSource.External(it) }

        return Result.success(combined)
    }
}
