package org.comon.storage

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * SharedPreferences를 사용하여 외부 모델 메타데이터를 저장합니다.
 */
class ExternalModelMetadataStore(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    )

    companion object {
        private const val PREFS_NAME = "external_model_metadata"
        private const val KEY_MODELS = "models"
    }

    /**
     * 외부 모델 메타데이터
     */
    data class ModelMetadata(
        val id: String,
        val name: String,
        val originalUri: String,
        val modelJsonName: String,
        val sizeBytes: Long,
        val cachedAt: Long,
        val lastAccessedAt: Long
    )

    /**
     * 모델 메타데이터를 저장합니다.
     */
    suspend fun saveModel(metadata: ModelMetadata) = withContext(Dispatchers.IO) {
        val models = getAllModels().toMutableList()
        models.removeAll { it.id == metadata.id }
        models.add(metadata)
        saveModels(models)
    }

    /**
     * 모든 모델 메타데이터를 조회합니다.
     */
    suspend fun getAllModels(): List<ModelMetadata> = withContext(Dispatchers.IO) {
        val json = prefs.getString(KEY_MODELS, "[]") ?: "[]"
        val array = JSONArray(json)
        (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            ModelMetadata(
                id = obj.getString("id"),
                name = obj.getString("name"),
                originalUri = obj.getString("originalUri"),
                modelJsonName = obj.getString("modelJsonName"),
                sizeBytes = obj.getLong("sizeBytes"),
                cachedAt = obj.getLong("cachedAt"),
                lastAccessedAt = obj.getLong("lastAccessedAt")
            )
        }
    }

    /**
     * ID로 모델 메타데이터를 조회합니다.
     */
    suspend fun getModel(id: String): ModelMetadata? = withContext(Dispatchers.IO) {
        getAllModels().find { it.id == id }
    }

    /**
     * 모델 메타데이터를 삭제합니다.
     */
    suspend fun deleteModel(id: String) = withContext(Dispatchers.IO) {
        val models = getAllModels().filter { it.id != id }
        saveModels(models)
    }

    /**
     * 마지막 접근 시간을 업데이트합니다.
     */
    suspend fun updateLastAccessed(id: String) = withContext(Dispatchers.IO) {
        val models = getAllModels().map {
            if (it.id == id) it.copy(lastAccessedAt = System.currentTimeMillis())
            else it
        }
        saveModels(models)
    }

    private fun saveModels(models: List<ModelMetadata>) {
        val array = JSONArray()
        models.forEach { model ->
            array.put(JSONObject().apply {
                put("id", model.id)
                put("name", model.name)
                put("originalUri", model.originalUri)
                put("modelJsonName", model.modelJsonName)
                put("sizeBytes", model.sizeBytes)
                put("cachedAt", model.cachedAt)
                put("lastAccessedAt", model.lastAccessedAt)
            })
        }
        prefs.edit().putString(KEY_MODELS, array.toString()).apply()
    }
}
