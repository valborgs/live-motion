package org.comon.data.repository

import org.comon.common.asset.ModelAssetReader
import org.comon.domain.common.DomainException
import org.comon.domain.common.Result
import org.comon.domain.model.Live2DModelInfo
import org.comon.domain.repository.IModelRepository

/**
 * IModelRepository의 구현체
 * ModelAssetReader를 사용하여 실제 데이터에 접근합니다.
 */
class ModelRepositoryImpl(
    private val modelAssetReader: ModelAssetReader
) : IModelRepository {

    override fun listLive2DModels(): Result<List<String>> {
        return try {
            val models = modelAssetReader.listLive2DModels()
            Result.success(models)
        } catch (e: Exception) {
            Result.error(DomainException.AssetReadError("assets root", e))
        }
    }

    override fun getModelMetadata(modelId: String): Result<Live2DModelInfo> {
        return try {
            val expressionsFolder = modelAssetReader.findAssetFolder(modelId, "expressions")
            val motionsFolder = modelAssetReader.findAssetFolder(modelId, "motions")

            val expressionFiles = if (expressionsFolder != null) {
                modelAssetReader.listFiles("$modelId/$expressionsFolder")
            } else {
                emptyList()
            }

            val motionFiles = if (motionsFolder != null) {
                modelAssetReader.listFiles("$modelId/$motionsFolder")
            } else {
                emptyList()
            }

            Result.success(
                Live2DModelInfo(
                    modelId = modelId,
                    expressionsFolder = expressionsFolder,
                    motionsFolder = motionsFolder,
                    expressionFiles = expressionFiles,
                    motionFiles = motionFiles
                )
            )
        } catch (e: Exception) {
            Result.error(DomainException.AssetReadError(modelId, e))
        }
    }

    override fun modelExists(modelId: String): Boolean {
        return try {
            modelAssetReader.listLive2DModels().contains(modelId)
        } catch (e: Exception) {
            false
        }
    }
}
