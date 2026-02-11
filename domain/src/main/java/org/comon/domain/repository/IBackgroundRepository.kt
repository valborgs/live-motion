package org.comon.domain.repository

import org.comon.domain.common.Result
import org.comon.domain.model.ExternalBackground

interface IBackgroundRepository {
    suspend fun listAssetBackgrounds(): Result<List<String>>
    suspend fun listExternalBackgrounds(): Result<List<ExternalBackground>>
    suspend fun importBackground(
        fileUri: String,
        onProgress: (Float) -> Unit = {}
    ): Result<ExternalBackground>
    suspend fun deleteBackground(backgroundId: String): Result<Unit>
}
