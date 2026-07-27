package com.monsteraltech.habitly.feature.aiassistant.data.source

import android.content.Context
import android.util.Log
import com.monsteraltech.habitly.feature.aiassistant.domain.model.AiModelConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.appendingSink
import okio.buffer
import okio.sink
import okio.source
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Non-retryable download exception (HTTP 4xx, checksum mismatch, insufficient disk space).
 */
class NonRetryableDownloadException(message: String) : IOException(message)

@Singleton
class LocalModelManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val tag = "LocalModelManager"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.MINUTES)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    private val modelDir: File
        get() = File(context.filesDir, "litertlm-models").also { it.mkdirs() }

    private val legacyModelDir: File
        get() = File(context.filesDir, "litert-models")

    private fun getModelFile(config: AiModelConfig): File {
        return File(modelDir, config.filename)
    }

    /**
     * Checks if a local model file exists and meets at least 95% of expected size.
     */
    private fun isValidModelFile(file: File, config: AiModelConfig): Boolean =
        file.exists() && file.length() >= config.sizeBytes / 100 * 95

    fun getModelPath(config: AiModelConfig): String? {
        val file = getModelFile(config)
        return if (isValidModelFile(file, config)) file.absolutePath else null
    }

    suspend fun isModelDownloaded(config: AiModelConfig): Boolean = withContext(Dispatchers.IO) {
        isValidModelFile(getModelFile(config), config)
    }

    suspend fun downloadModel(config: AiModelConfig, onProgress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        val modelFile = getModelFile(config)
        if (isValidModelFile(modelFile, config)) {
            Log.d(tag, "Model already downloaded: ${modelFile.absolutePath}")
            return@withContext
        }

        val tempFile = File(modelDir, "${config.filename}.tmp")
        modelFile.parentFile?.mkdirs()
        checkFreeSpace(config, tempFile)

        try {
            // Resumption support using HTTP Range requests.
            var startOffset = tempFile.length()
            var request = buildRequest(config.downloadUrl, startOffset)
            var response = executeCall(okHttpClient.newCall(request))

            var redirectCount = 0
            while (response.code in listOf(301, 302, 303, 307, 308)) {
                redirectCount++
                if (redirectCount > 5) throw IOException("Too many redirects")

                val location = response.header("Location")
                    ?: throw IOException("Redirect without Location header")
                response.close()

                // HTTPS-only redirect policy.
                if (!location.startsWith("https://")) {
                    throw NonRetryableDownloadException("Insecure redirect rejected (HTTPS required)")
                }
                request = buildRequest(location, startOffset)
                response = executeCall(okHttpClient.newCall(request))
            }

            if (response.code == 416) {
                response.close()
                tempFile.delete()
                throw IOException("Invalid range; restarting download")
            }
            if (!response.isSuccessful) {
                val code = response.code
                response.close()
                if (code in 400..499) {
                    throw NonRetryableDownloadException("HTTP error $code when downloading model")
                }
                throw IOException("HTTP error $code when downloading model")
            }

            // 206 = server accepts Range; 200 = full file download.
            val resuming = response.code == 206 && startOffset > 0
            if (!resuming) startOffset = 0L

            // Calculate SHA-256 in streaming mode during write.
            val digest = MessageDigest.getInstance("SHA-256")
            if (resuming) seedDigestWithExistingBytes(digest, tempFile)

            // Longitud anunciada del CUERPO (en 206 es solo el resto) y total estimado
            // para el progreso (el header puede faltar en respuestas chunked).
            val bodyLength = response.body?.contentLength() ?: -1L
            val expectedTotal = (if (bodyLength > 0) startOffset + bodyLength else config.sizeBytes)
                .coerceAtLeast(1L)

            var sessionRead = 0L
            response.body?.source()?.use { source ->
                val fileSink = if (resuming) tempFile.appendingSink() else tempFile.sink()
                fileSink.buffer().use { sink ->
                    val buffer = ByteArray(DOWNLOAD_BUFFER_BYTES)
                    var bytesRead: Int

                    while (source.read(buffer).also { bytesRead = it } != -1) {
                        ensureActive()
                        sink.write(buffer, 0, bytesRead)
                        digest.update(buffer, 0, bytesRead)
                        sessionRead += bytesRead
                        val progress = ((startOffset + sessionRead).toFloat() / expectedTotal)
                            .coerceIn(0f, 1f)
                        onProgress(progress)
                    }
                    sink.flush()
                }
            }
            response.close()

            if (bodyLength > 0 && sessionRead < bodyLength) {
                throw IOException("Incomplete download: $sessionRead of $bodyLength bytes")
            }
            if (!isValidModelFile(tempFile, config)) {
                throw NonRetryableDownloadException(
                    "Downloaded file size mismatch (${tempFile.length()} bytes)"
                )
            }
            verifyChecksum(config, digest)

            if (modelFile.exists()) modelFile.delete()
            if (!tempFile.renameTo(modelFile)) {
                throw NonRetryableDownloadException("Failed to move downloaded model file")
            }

            Log.d(tag, "Model downloaded successfully: ${modelFile.absolutePath} " +
                    "(${modelFile.length() / 1024 / 1024}MB)")
        } catch (e: CancellationException) {
            // Keep .tmp file on cancellation for future resumption.
            throw e
        } catch (e: NonRetryableDownloadException) {
            tempFile.delete()
            Log.e(tag, "Non-retryable model download error", e)
            throw e
        } catch (e: Exception) {
            Log.e(tag, "Transient model download error", e)
            throw e
        }
    }

    private fun buildRequest(url: String, startOffset: Long): Request {
        val builder = Request.Builder().url(url)
        if (startOffset > 0) builder.header("Range", "bytes=$startOffset-")
        return builder.build()
    }

    /** Checks available disk space before starting download. */
    private fun checkFreeSpace(config: AiModelConfig, tempFile: File) {
        val needed = (config.sizeBytes - tempFile.length()).coerceAtLeast(0L) + FREE_SPACE_MARGIN_BYTES
        val usable = modelDir.usableSpace
        if (usable in 1 until needed) {
            val missingMb = (needed - usable) / 1_000_000
            throw NonRetryableDownloadException(
                "Insufficient disk space for model: missing $missingMb MB"
            )
        }
    }

    /** Re-seeds SHA-256 digest with existing partial download bytes. */
    private fun seedDigestWithExistingBytes(digest: MessageDigest, file: File) {
        file.inputStream().use { input ->
            val buffer = ByteArray(DOWNLOAD_BUFFER_BYTES)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
    }

    /** Verifies SHA-256 checksum against catalog configuration. */
    private fun verifyChecksum(config: AiModelConfig, digest: MessageDigest) {
        val computed = digest.digest().joinToString("") { "%02x".format(it) }
        if (computed != config.sha256.lowercase()) {
            Log.e(tag, "SHA-256 mismatch for ${config.filename}: expected ${config.sha256}, got $computed")
            throw NonRetryableDownloadException("Downloaded model failed integrity verification")
        }
    }

    private suspend fun executeCall(call: Call): okhttp3.Response = suspendCancellableCoroutine { continuation ->
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) {
                    continuation.resumeWithException(e)
                }
            }

            override fun onResponse(call: Call, response: okhttp3.Response) {
                if (continuation.isActive) {
                    continuation.resume(response)
                } else {
                    response.close()
                }
            }
        })

        continuation.invokeOnCancellation {
            call.cancel()
        }
    }

    /** Deletes model file and any temporary download file. */
    fun deleteModel(config: AiModelConfig): Boolean {
        File(modelDir, "${config.filename}.tmp").delete()
        return getModelFile(config).delete()
    }

    private companion object {
        const val DOWNLOAD_BUFFER_BYTES = 64 * 1024
        const val FREE_SPACE_MARGIN_BYTES = 200_000_000L
    }

    fun cleanupLegacyModels() {
        if (legacyModelDir.exists()) {
            val deleted = legacyModelDir.deleteRecursively()
            Log.d(tag, "Legacy model cleanup: ${if (deleted) "success" else "failed"}")
        }
    }

    /**
     * Deletes orphaned model files not listed in [validModels].
     */
    fun cleanupOrphanedModels(validModels: List<AiModelConfig>) {
        val known = validModels.flatMap { listOf(it.filename, "${it.filename}.tmp") }.toSet()
        modelDir.listFiles()?.forEach { file ->
            if (file.isFile && file.name !in known) {
                val deleted = file.delete()
                Log.d(tag, "Orphaned model cleanup: ${file.name} -> ${if (deleted) "deleted" else "failed"}")
            }
        }
    }
}
