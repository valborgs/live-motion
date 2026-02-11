package org.comon.storage

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

class BackgroundCacheManager(private val context: Context) {

    private val cacheDir: File
        get() = File(context.cacheDir, CACHE_DIR_NAME).apply { mkdirs() }

    companion object {
        private const val CACHE_DIR_NAME = "external_backgrounds"
        private const val MAX_FILE_SIZE = 50 * 1024 * 1024L // 50MB
    }

    fun generateBackgroundId(fileUri: Uri): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(fileUri.toString().toByteArray())
        return hash.take(16).joinToString("") { "%02x".format(it) }
    }

    fun getBackgroundCacheDir(backgroundId: String): File {
        return File(cacheDir, backgroundId)
    }

    fun isCached(backgroundId: String): Boolean {
        val bgDir = getBackgroundCacheDir(backgroundId)
        return bgDir.exists() && bgDir.listFiles()?.isNotEmpty() == true
    }

    suspend fun copyToCache(
        fileUri: Uri,
        backgroundId: String,
        fileName: String,
        onProgress: (Float) -> Unit
    ): Long = withContext(Dispatchers.IO) {
        val targetDir = getBackgroundCacheDir(backgroundId)
        if (targetDir.exists()) {
            targetDir.deleteRecursively()
        }
        targetDir.mkdirs()

        val targetFile = File(targetDir, fileName)

        val inputStream = context.contentResolver.openInputStream(Uri.parse(fileUri.toString()))
            ?: throw IllegalArgumentException("Cannot open file")

        val fileSize = inputStream.available().toLong().coerceAtLeast(1L)
        if (fileSize > MAX_FILE_SIZE) {
            inputStream.close()
            throw IllegalArgumentException("File size exceeds maximum ($MAX_FILE_SIZE bytes)")
        }

        var copiedSize = 0L
        inputStream.use { input ->
            FileOutputStream(targetFile).use { output ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    copiedSize += bytesRead
                    onProgress(copiedSize.toFloat() / fileSize)
                }
            }
        }

        return@withContext copiedSize
    }

    fun deleteCache(backgroundId: String): Boolean {
        return getBackgroundCacheDir(backgroundId).deleteRecursively()
    }

    fun getCachedImagePath(backgroundId: String): String? {
        val bgDir = getBackgroundCacheDir(backgroundId)
        return bgDir.listFiles()?.firstOrNull()?.absolutePath
    }
}
