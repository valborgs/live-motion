package org.comon.storage

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class ExternalBackgroundMetadataStore(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    )

    companion object {
        private const val PREFS_NAME = "external_background_metadata"
        private const val KEY_BACKGROUNDS = "backgrounds"
    }

    data class BackgroundMetadata(
        val id: String,
        val name: String,
        val originalUri: String,
        val cachePath: String,
        val sizeBytes: Long,
        val cachedAt: Long,
    )

    suspend fun saveBackground(metadata: BackgroundMetadata) = withContext(Dispatchers.IO) {
        val backgrounds = getAllBackgrounds().toMutableList()
        backgrounds.removeAll { it.id == metadata.id }
        backgrounds.add(metadata)
        saveBackgrounds(backgrounds)
    }

    suspend fun getAllBackgrounds(): List<BackgroundMetadata> = withContext(Dispatchers.IO) {
        val json = prefs.getString(KEY_BACKGROUNDS, "[]") ?: "[]"
        val array = JSONArray(json)
        (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            BackgroundMetadata(
                id = obj.getString("id"),
                name = obj.getString("name"),
                originalUri = obj.getString("originalUri"),
                cachePath = obj.getString("cachePath"),
                sizeBytes = obj.getLong("sizeBytes"),
                cachedAt = obj.getLong("cachedAt"),
            )
        }
    }

    suspend fun deleteBackground(id: String) = withContext(Dispatchers.IO) {
        val backgrounds = getAllBackgrounds().filter { it.id != id }
        saveBackgrounds(backgrounds)
    }

    private fun saveBackgrounds(backgrounds: List<BackgroundMetadata>) {
        val array = JSONArray()
        backgrounds.forEach { bg ->
            array.put(JSONObject().apply {
                put("id", bg.id)
                put("name", bg.name)
                put("originalUri", bg.originalUri)
                put("cachePath", bg.cachePath)
                put("sizeBytes", bg.sizeBytes)
                put("cachedAt", bg.cachedAt)
            })
        }
        prefs.edit().putString(KEY_BACKGROUNDS, array.toString()).apply()
    }
}
