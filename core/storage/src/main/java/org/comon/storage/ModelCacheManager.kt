package org.comon.storage

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.comon.domain.model.ModelValidationResult
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * 외부 Live2D 모델의 캐시를 관리하는 클래스
 *
 * 캐시 구조:
 * /data/data/org.comon.livemotion/cache/external_models/
 *   ├── {model_id}/           (복사된 모델 파일)
 *   │   ├── model.model3.json
 *   │   ├── model.moc3
 *   │   ├── textures/
 *   │   └── ...
 */
class ModelCacheManager(private val context: Context) {

    private val cacheDir: File
        get() = File(context.cacheDir, CACHE_DIR_NAME).apply { mkdirs() }

    companion object {
        private const val CACHE_DIR_NAME = "external_models"
        private const val MAX_SINGLE_FILE_SIZE = 50 * 1024 * 1024L // 50MB
        private const val MAX_MODEL_SIZE = 200 * 1024 * 1024L // 200MB
    }

    /**
     * URI에서 고유 ID를 생성합니다 (SHA-256 해시).
     */
    fun generateModelId(folderUri: Uri): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(folderUri.toString().toByteArray())
        return hash.take(16).joinToString("") { "%02x".format(it) }
    }

    /**
     * 모델의 캐시 디렉토리를 반환합니다.
     */
    fun getModelCacheDir(modelId: String): File {
        return File(cacheDir, modelId)
    }

    /**
     * 모델이 캐시되어 있는지 확인합니다.
     */
    fun isCached(modelId: String): Boolean {
        val modelDir = getModelCacheDir(modelId)
        return modelDir.exists() && modelDir.listFiles()?.isNotEmpty() == true
    }

    /**
     * 모델 폴더 구조를 검증합니다.
     */
    suspend fun validateModelFolder(folderUri: Uri): ModelValidationResult = withContext(Dispatchers.IO) {
        val documentFile = DocumentFile.fromTreeUri(context, folderUri)
            ?: return@withContext ModelValidationResult(
                isValid = false,
                modelJsonName = null,
                cubismVersion = null,
                errorMessage = "Cannot access folder"
            )

        val files = documentFile.listFiles()

        // model3.json 찾기
        val model3Json = files.find { it.name?.endsWith(".model3.json") == true }
            ?: return@withContext ModelValidationResult(
                isValid = false,
                modelJsonName = null,
                cubismVersion = null,
                errorMessage = "No .model3.json file found"
            )

        // model3.json 읽어서 버전 확인
        try {
            val inputStream = context.contentResolver.openInputStream(model3Json.uri)
            val jsonContent = inputStream?.bufferedReader()?.readText() ?: ""
            inputStream?.close()

            // 버전 추출 (간단한 정규식 파싱)
            val versionMatch = Regex("\"Version\"\\s*:\\s*(\\d+)").find(jsonContent)
            val version = versionMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0

            // Cubism SDK 4.x는 버전 3, 4 지원
            if (version < 3) {
                return@withContext ModelValidationResult(
                    isValid = false,
                    modelJsonName = model3Json.name,
                    cubismVersion = version.toString(),
                    errorMessage = "Unsupported Cubism model version: $version (requires 3+)"
                )
            }

            return@withContext ModelValidationResult(
                isValid = true,
                modelJsonName = model3Json.name,
                cubismVersion = version.toString(),
                errorMessage = null
            )
        } catch (e: Exception) {
            return@withContext ModelValidationResult(
                isValid = false,
                modelJsonName = model3Json.name,
                cubismVersion = null,
                errorMessage = "Failed to read model3.json: ${e.message}"
            )
        }
    }

    /**
     * SAF에서 내부 캐시로 모델 파일을 복사합니다.
     * @return 복사된 총 바이트 수
     */
    suspend fun copyToCache(
        folderUri: Uri,
        modelId: String,
        onProgress: (Float) -> Unit
    ): Long = withContext(Dispatchers.IO) {
        val documentFile = DocumentFile.fromTreeUri(context, folderUri)
            ?: throw IllegalArgumentException("Cannot access folder")

        val targetDir = getModelCacheDir(modelId)
        if (targetDir.exists()) {
            targetDir.deleteRecursively()
        }
        targetDir.mkdirs()

        // 총 크기 계산
        var totalSize = 0L
        val filesToCopy = mutableListOf<Pair<DocumentFile, String>>()
        collectFiles(documentFile, "", filesToCopy)

        filesToCopy.forEach { (file, _) ->
            totalSize += file.length()
        }

        if (totalSize > MAX_MODEL_SIZE) {
            throw OutOfMemoryError("Model size ($totalSize bytes) exceeds maximum ($MAX_MODEL_SIZE bytes)")
        }

        // 파일 복사
        var copiedSize = 0L
        filesToCopy.forEach { (sourceFile, relativePath) ->
            val targetFile = File(targetDir, relativePath)
            targetFile.parentFile?.mkdirs()

            if (sourceFile.length() > MAX_SINGLE_FILE_SIZE) {
                throw IllegalArgumentException("File ${sourceFile.name} exceeds maximum size")
            }

            context.contentResolver.openInputStream(sourceFile.uri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        copiedSize += bytesRead
                        onProgress(copiedSize.toFloat() / totalSize)
                    }
                }
            }
        }

        return@withContext copiedSize
    }

    private fun collectFiles(
        dir: DocumentFile,
        basePath: String,
        result: MutableList<Pair<DocumentFile, String>>
    ) {
        dir.listFiles().forEach { file ->
            val fileName = file.name ?: return@forEach
            val relativePath = if (basePath.isEmpty()) fileName else "$basePath/$fileName"
            if (file.isDirectory) {
                collectFiles(file, relativePath, result)
            } else {
                result.add(file to relativePath)
            }
        }
    }

    /**
     * 캐시된 모델을 삭제합니다.
     */
    fun deleteCache(modelId: String): Boolean {
        return getModelCacheDir(modelId).deleteRecursively()
    }

    /**
     * 전체 캐시 크기를 계산합니다.
     */
    fun getTotalCacheSize(): Long {
        return cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    /**
     * 모든 캐시를 삭제합니다.
     */
    fun clearAllCache() {
        cacheDir.deleteRecursively()
        cacheDir.mkdirs()
    }
}
