package org.comon.data.repository

import android.net.Uri
import org.comon.common.asset.ModelAssetReader
import org.comon.domain.common.DomainException
import org.comon.domain.common.Result
import org.comon.domain.model.ExternalBackground
import org.comon.domain.repository.IBackgroundRepository
import org.comon.storage.BackgroundCacheManager
import org.comon.storage.ExternalBackgroundMetadataStore

class BackgroundRepositoryImpl(
    private val modelAssetReader: ModelAssetReader,
    private val cacheManager: BackgroundCacheManager,
    private val metadataStore: ExternalBackgroundMetadataStore,
) : IBackgroundRepository {

    override suspend fun listAssetBackgrounds(): Result<List<String>> {
        return try {
            Result.success(modelAssetReader.listBackgrounds())
        } catch (e: Exception) {
            Result.error(DomainException.AssetReadError("backgrounds", e))
        }
    }

    override suspend fun listExternalBackgrounds(): Result<List<ExternalBackground>> {
        return try {
            val backgrounds = metadataStore.getAllBackgrounds().mapNotNull { metadata ->
                if (cacheManager.isCached(metadata.id)) {
                    ExternalBackground(
                        id = metadata.id,
                        name = metadata.name,
                        originalUri = metadata.originalUri,
                        cachePath = cacheManager.getCachedImagePath(metadata.id) ?: metadata.cachePath,
                        sizeBytes = metadata.sizeBytes,
                        cachedAt = metadata.cachedAt,
                    )
                } else {
                    metadataStore.deleteBackground(metadata.id)
                    null
                }
            }
            Result.success(backgrounds)
        } catch (e: Exception) {
            Result.error(DomainException.AssetReadError("external backgrounds", e))
        }
    }

    override suspend fun importBackground(
        fileUri: String,
        onProgress: (Float) -> Unit
    ): Result<ExternalBackground> {
        return try {
            val uri = Uri.parse(fileUri)
            val id = cacheManager.generateBackgroundId(uri)

            // 파일명 추출
            val fileName = uri.lastPathSegment
                ?.substringAfterLast('/')
                ?.substringAfterLast(':')
                ?: "background.png"

            val sizeBytes = cacheManager.copyToCache(uri, id, fileName, onProgress)
            val cachePath = cacheManager.getCachedImagePath(id)
                ?: throw IllegalStateException("Cache file not found after copy")

            val metadata = ExternalBackgroundMetadataStore.BackgroundMetadata(
                id = id,
                name = fileName,
                originalUri = fileUri,
                cachePath = cachePath,
                sizeBytes = sizeBytes,
                cachedAt = System.currentTimeMillis(),
            )
            metadataStore.saveBackground(metadata)

            val background = ExternalBackground(
                id = id,
                name = fileName,
                originalUri = fileUri,
                cachePath = cachePath,
                sizeBytes = sizeBytes,
                cachedAt = metadata.cachedAt,
            )
            Result.success(background)
        } catch (e: OutOfMemoryError) {
            Result.error(DomainException.ExternalModelException.OutOfMemory(e.message ?: "Out of memory"))
        } catch (e: Exception) {
            Result.error(
                DomainException.ExternalModelException.CacheOperationFailed(
                    e.message ?: "Failed to import background", e
                )
            )
        }
    }

    override suspend fun deleteBackground(backgroundId: String): Result<Unit> {
        return try {
            cacheManager.deleteCache(backgroundId)
            metadataStore.deleteBackground(backgroundId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.error(
                DomainException.ExternalModelException.CacheOperationFailed(
                    e.message ?: "Failed to delete background", e
                )
            )
        }
    }
}
