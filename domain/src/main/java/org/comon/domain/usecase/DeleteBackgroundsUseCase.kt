package org.comon.domain.usecase

import org.comon.domain.common.Result
import org.comon.domain.repository.IBackgroundRepository

class DeleteBackgroundsUseCase(
    private val backgroundRepository: IBackgroundRepository
) {
    suspend operator fun invoke(backgroundIds: Set<String>): Result<Unit> {
        backgroundIds.forEach { id ->
            val result = backgroundRepository.deleteBackground(id)
            if (result.isError) return result
        }
        return Result.success(Unit)
    }
}
