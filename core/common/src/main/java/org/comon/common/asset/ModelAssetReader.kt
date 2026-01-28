package org.comon.common.asset

import android.content.res.AssetManager
import java.io.IOException

/**
 * Asset 읽기를 담당하는 클래스
 * ViewModel/Screen이 Context를 직접 참조하지 않도록 합니다.
 */
class ModelAssetReader(private val assets: AssetManager) {

    /**
     * 대소문자 무시하고 특정 폴더를 찾습니다.
     * @param modelId 모델 ID (assets 내 폴더 이름)
     * @param targetFolder 찾을 폴더 이름 (e.g., "expressions", "motions")
     * @return 실제 폴더 이름 (대소문자 원본) 또는 null
     */
    fun findAssetFolder(modelId: String, targetFolder: String): String? {
        return try {
            val files = assets.list(modelId) ?: return null
            files.firstOrNull { it.equals(targetFolder, ignoreCase = true) }
        } catch (_: IOException) {
            null
        }
    }

    /**
     * 특정 경로의 파일 목록을 반환합니다.
     * @param path assets 내 경로
     * @return 파일 이름 목록
     */
    fun listFiles(path: String): List<String> {
        return try {
            assets.list(path)?.toList() ?: emptyList()
        } catch (_: IOException) {
            emptyList()
        }
    }

    /**
     * assets 루트의 Live2D 모델 폴더 목록을 반환합니다.
     * Live2D 모델 폴더는 내부에 '{폴더이름}.model3.json' 파일을 포함합니다.
     */
    fun listLive2DModels(): List<String> {
        return try {
            assets.list("")?.filter { name ->
                // 개별 파일 및 알려진 비-모델 폴더 제외
                if (name.contains(".") || name == "Shaders" || name == "images" || name == "webkit" || name == "geoid_map") {
                    return@filter false
                }

                // 폴더 내부에 '{폴더이름}.model3.json' 파일이 있는지 검사
                try {
                    val subFiles = assets.list(name) ?: emptyArray()
                    subFiles.any { it == "$name.model3.json" }
                } catch (_: Exception) {
                    false
                }
            } ?: emptyList()
        } catch (_: IOException) {
            emptyList()
        }
    }
}
