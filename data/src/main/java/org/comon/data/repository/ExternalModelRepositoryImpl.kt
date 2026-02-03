package org.comon.data.repository

import android.net.Uri
import org.comon.domain.common.DomainException
import org.comon.domain.common.Result
import org.comon.domain.model.ExternalModel
import org.comon.domain.model.Live2DModelInfo
import org.comon.domain.model.ModelValidationResult
import org.comon.domain.repository.IExternalModelRepository
import org.comon.storage.ExternalModelMetadataStore
import org.comon.storage.ModelCacheManager
import org.comon.storage.SAFPermissionManager

/**
 * 외부 모델 Repository 구현체
 */
class ExternalModelRepositoryImpl(
    private val cacheManager: ModelCacheManager,
    private val metadataStore: ExternalModelMetadataStore,
    private val safPermissionManager: SAFPermissionManager
) : IExternalModelRepository {

    override suspend fun listExternalModels(): Result<List<ExternalModel>> {
        return try {
            val models = metadataStore.getAllModels().mapNotNull { metadata ->
                // 캐시가 존재하는 경우에만 반환
                if (cacheManager.isCached(metadata.id)) {
                    ExternalModel(
                        id = metadata.id,
                        name = metadata.name,
                        originalUri = metadata.originalUri,
                        cachePath = cacheManager.getModelCacheDir(metadata.id).absolutePath,
                        modelJsonName = metadata.modelJsonName,
                        sizeBytes = metadata.sizeBytes,
                        cachedAt = metadata.cachedAt,
                        lastAccessedAt = metadata.lastAccessedAt
                    )
                } else {
                    // 고아 메타데이터 정리
                    metadataStore.deleteModel(metadata.id)
                    null
                }
            }
            Result.success(models)
        } catch (e: Exception) {
            Result.error(DomainException.AssetReadError("external models", e))
        }
    }

    override suspend fun validateModel(folderUri: String): Result<ModelValidationResult> {
        return try {
            val result = cacheManager.validateModelFolder(Uri.parse(folderUri))
            Result.success(result)
        } catch (e: Exception) {
            Result.error(
                DomainException.ExternalModelException.ModelValidationFailed(
                    e.message ?: "Validation failed"
                )
            )
        }
    }

    override suspend fun importModel(
        folderUri: String,
        onProgress: (Float) -> Unit
    ): Result<ExternalModel> {
        return try {
            val uri = Uri.parse(folderUri)
            val modelId = cacheManager.generateModelId(uri)

            // 먼저 검증
            val validation = cacheManager.validateModelFolder(uri)
            if (!validation.isValid) {
                return Result.error(
                    DomainException.ExternalModelException.InvalidModelFormat(
                        validation.errorMessage ?: "Invalid model"
                    )
                )
            }

            // modelJsonName을 로컬 변수로 캡처 (스마트 캐스트를 위해)
            val modelJsonName = validation.modelJsonName
                ?: return Result.error(
                    DomainException.ExternalModelException.InvalidModelFormat("model3.json not found")
                )

            // 캐시로 복사
            val sizeBytes = cacheManager.copyToCache(uri, modelId, onProgress)

            // model3.json 파일명에서 이름 추출
            val name = modelJsonName.removeSuffix(".model3.json")

            // 메타데이터 저장
            val now = System.currentTimeMillis()
            val metadata = ExternalModelMetadataStore.ModelMetadata(
                id = modelId,
                name = name,
                originalUri = folderUri,
                modelJsonName = modelJsonName,
                sizeBytes = sizeBytes,
                cachedAt = now,
                lastAccessedAt = now
            )
            metadataStore.saveModel(metadata)

            Result.success(
                ExternalModel(
                    id = modelId,
                    name = name,
                    originalUri = folderUri,
                    cachePath = cacheManager.getModelCacheDir(modelId).absolutePath,
                    modelJsonName = modelJsonName,
                    sizeBytes = sizeBytes,
                    cachedAt = now,
                    lastAccessedAt = now
                )
            )
        } catch (e: OutOfMemoryError) {
            Result.error(DomainException.ExternalModelException.OutOfMemory(e.message ?: "Out of memory"))
        } catch (e: Exception) {
            Result.error(
                DomainException.ExternalModelException.CacheOperationFailed(
                    e.message ?: "Import failed", e
                )
            )
        }
    }

    override suspend fun getModel(modelId: String): Result<ExternalModel> {
        val metadata = metadataStore.getModel(modelId)
            ?: return Result.error(DomainException.ModelNotFoundError(modelId))

        if (!cacheManager.isCached(modelId)) {
            metadataStore.deleteModel(modelId)
            return Result.error(DomainException.ModelNotFoundError(modelId))
        }

        return Result.success(
            ExternalModel(
                id = metadata.id,
                name = metadata.name,
                originalUri = metadata.originalUri,
                cachePath = cacheManager.getModelCacheDir(modelId).absolutePath,
                modelJsonName = metadata.modelJsonName,
                sizeBytes = metadata.sizeBytes,
                cachedAt = metadata.cachedAt,
                lastAccessedAt = metadata.lastAccessedAt
            )
        )
    }

    override suspend fun deleteModel(modelId: String): Result<Unit> {
        return try {
            // 삭제 전에 메타데이터에서 URI를 가져와서 SAF 권한 해제
            metadataStore.getModel(modelId)?.let { metadata ->
                safPermissionManager.releasePermission(Uri.parse(metadata.originalUri))
            }

            cacheManager.deleteCache(modelId)
            metadataStore.deleteModel(modelId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.error(
                DomainException.ExternalModelException.CacheOperationFailed(
                    "Failed to delete model", e
                )
            )
        }
    }

    override suspend fun getModelMetadata(modelId: String): Result<Live2DModelInfo> {
        if (!cacheManager.isCached(modelId)) {
            return Result.error(DomainException.ModelNotFoundError(modelId))
        }

        val cacheDir = cacheManager.getModelCacheDir(modelId)

        // expressions 폴더 찾기 (대소문자 무시)
        val expressionsFolder = cacheDir.listFiles()
            ?.firstOrNull { it.isDirectory && it.name.equals("expressions", ignoreCase = true) }

        // motions 폴더 찾기 (대소문자 무시)
        val motionsFolder = cacheDir.listFiles()
            ?.firstOrNull { it.isDirectory && it.name.equals("motions", ignoreCase = true) }

        // 파일 목록 조회
        val expressionFiles = expressionsFolder?.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".exp3.json", ignoreCase = true) }
            ?.map { it.name }
            ?: emptyList()

        val motionFiles = motionsFolder?.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".motion3.json", ignoreCase = true) }
            ?.map { it.name }
            ?: emptyList()

        return Result.success(
            Live2DModelInfo(
                modelId = modelId,
                expressionsFolder = expressionsFolder?.name,
                motionsFolder = motionsFolder?.name,
                expressionFiles = expressionFiles,
                motionFiles = motionFiles
            )
        )
    }

    override fun getCachePath(modelId: String): String? {
        return if (cacheManager.isCached(modelId)) {
            cacheManager.getModelCacheDir(modelId).absolutePath
        } else null
    }

    override suspend fun updateLastAccessed(modelId: String) {
        metadataStore.updateLastAccessed(modelId)
    }

    override suspend fun cleanupCache(maxCacheSizeBytes: Long, maxAgeMillis: Long): Result<Int> {
        return try {
            var deletedCount = 0
            val models = metadataStore.getAllModels()
                .sortedBy { it.lastAccessedAt } // LRU 순서

            val now = System.currentTimeMillis()
            var currentSize = cacheManager.getTotalCacheSize()

            for (model in models) {
                val shouldDelete = (now - model.lastAccessedAt > maxAgeMillis) ||
                    (currentSize > maxCacheSizeBytes)

                if (shouldDelete) {
                    cacheManager.deleteCache(model.id)
                    metadataStore.deleteModel(model.id)
                    currentSize -= model.sizeBytes
                    deletedCount++
                }

                if (currentSize <= maxCacheSizeBytes) break
            }

            Result.success(deletedCount)
        } catch (e: Exception) {
            Result.error(
                DomainException.ExternalModelException.CacheOperationFailed(
                    "Cleanup failed", e
                )
            )
        }
    }
}
