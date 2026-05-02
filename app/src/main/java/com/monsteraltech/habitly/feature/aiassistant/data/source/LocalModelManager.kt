package com.monsteraltech.habitly.feature.aiassistant.data.source

import android.content.Context
import android.util.Log
import com.monsteraltech.habitly.feature.aiassistant.domain.model.AiModelConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalModelManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val tag = "LocalModelManager"

    private val modelDir: File
        get() = File(context.filesDir, "litertlm-models").also { it.mkdirs() }

    /** Legacy directory used by the old MediaPipe implementation. */
    private val legacyModelDir: File
        get() = File(context.filesDir, "litert-models")

    private fun getModelFile(config: AiModelConfig): File {
        return File(modelDir, config.filename)
    }

    fun getModelPath(config: AiModelConfig): String? {
        val file = getModelFile(config)
        return if (file.exists() && file.length() > 0) {
            file.absolutePath
        } else null
    }

    suspend fun isModelDownloaded(config: AiModelConfig): Boolean = withContext(Dispatchers.IO) {
        val file = getModelFile(config)
        file.exists() && file.length() > 0
    }

    suspend fun downloadModel(config: AiModelConfig, onProgress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        val modelFile = getModelFile(config)
        if (modelFile.exists() && modelFile.length() > 0) {
            Log.d(tag, "Model already downloaded: ${modelFile.absolutePath}")
            return@withContext
        }

        val tempFile = File(modelDir, "${config.filename}.tmp")
        modelFile.parentFile?.mkdirs()

        try {
            var url = URL(config.downloadUrl)
            var connection = url.openConnection() as java.net.HttpURLConnection
            var redirectCount = 0

            // Follow cross-domain redirects (Hugging Face to CDN LFS)
            while (true) {
                connection.instanceFollowRedirects = false
                val status = connection.responseCode
                if (status != java.net.HttpURLConnection.HTTP_MOVED_TEMP
                    && status != java.net.HttpURLConnection.HTTP_MOVED_PERM
                    && status != java.net.HttpURLConnection.HTTP_SEE_OTHER) {
                    break
                }
                redirectCount++
                if (redirectCount > 5) throw java.io.IOException("Demasiados redireccionamientos")
                
                val newUrl = connection.getHeaderField("Location")
                connection.disconnect()
                url = URL(newUrl)
                connection = url.openConnection() as java.net.HttpURLConnection
            }

            val contentLength = connection.contentLengthLong.coerceAtLeast(config.sizeBytes)

            connection.inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalRead = 0L
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        val progress = (totalRead.toFloat() / contentLength).coerceIn(0f, 1f)
                        onProgress(progress)
                    }
                }
            }

            tempFile.renameTo(modelFile)

            Log.d(tag, "Model downloaded successfully: ${modelFile.absolutePath} " +
                    "(${modelFile.length() / 1024 / 1024}MB)")
        } catch (e: Exception) {
            if (tempFile.exists()) tempFile.delete()
            if (modelFile.exists()) modelFile.delete()
            Log.e(tag, "Error downloading model", e)
            throw e
        }
    }

    fun deleteModel(config: AiModelConfig): Boolean {
        return getModelFile(config).delete()
    }

    /**
     * Removes old .task files from the legacy MediaPipe directory.
     * Called once during migration to free up storage.
     */
    fun cleanupLegacyModels() {
        if (legacyModelDir.exists()) {
            val deleted = legacyModelDir.deleteRecursively()
            Log.d(tag, "Legacy model cleanup: ${if (deleted) "success" else "failed"}")
        }
    }
}
