package com.monsteraltech.habitly.feature.aiassistant.data.source

import android.content.Context
import android.os.storage.StorageManager
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
 * A download error not worth retrying automatically (HTTP 4xx, integrity validation, no space):
 * the worker turns it into a definitive failure instead of a `Result.retry()`.
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
     * A model file is only valid if it weighs at least 95% of the size the catalog expects. That
     * discards error pages and truncated files left by older unvalidated downloads without
     * demanding an exact byte count, since the catalog size is approximate. An invalid file counts
     * as "not downloaded" and can be fetched again.
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
            // Resume: if a .tmp survived a previous attempt, only the remainder is requested with
            // Range. The integrity check at the end validates the whole file.
            var startOffset = tempFile.length()
            var request = buildRequest(config.downloadUrl, startOffset)
            var response = executeCall(okHttpClient.newCall(request))

            var redirectCount = 0
            while (response.code in listOf(301, 302, 303, 307, 308)) {
                redirectCount++
                if (redirectCount > 5) throw IOException("Demasiados redireccionamientos")

                val location = response.header("Location")
                    ?: throw IOException("Redirect sin Location")
                response.close()

                // Only HTTPS redirects are followed: since they are followed by hand, OkHttp can no
                // longer prevent a downgrade to plain HTTP on its own.
                if (!location.startsWith("https://")) {
                    throw NonRetryableDownloadException("Redirección insegura rechazada (solo HTTPS)")
                }
                request = buildRequest(location, startOffset)
                response = executeCall(okHttpClient.newCall(request))
            }

            if (response.code == 416) {
                // The range no longer matches the remote file: the partial download is discarded
                // and the retry — this error is transient — starts from scratch.
                response.close()
                tempFile.delete()
                throw IOException("Rango de reanudación no válido; se reinicia la descarga")
            }
            if (!response.isSuccessful) {
                val code = response.code
                response.close()
                if (code in 400..499) {
                    // A 4xx (no permission, model withdrawn) is not fixed by retrying.
                    throw NonRetryableDownloadException("Error HTTP $code al descargar el modelo")
                }
                throw IOException("Error HTTP $code al descargar el modelo")
            }

            // 206 means the server accepts resuming; 200 with a Range means it sends the whole file.
            val resuming = response.code == 206 && startOffset > 0
            if (!resuming) startOffset = 0L

            // SHA-256 computed while streaming, so no extra pass. On resume the hash of what was
            // already written must be replayed first: a digest cannot be picked up mid-way.
            val digest = MessageDigest.getInstance("SHA-256")
            if (resuming) seedDigestWithExistingBytes(digest, tempFile)

            // Announced **body** length — on a 206 that is only the remainder — plus an estimated
            // total for the progress bar, since the header can be missing on chunked responses.
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

            // A "clean" server close half-way through the file raises no exception. Without these
            // checks a truncated model, or a tiny error page, would be accepted and the engine
            // would fail later with a cryptic, unrecoverable error.
            if (bodyLength > 0 && sessionRead < bodyLength) {
                throw IOException("Descarga incompleta: $sessionRead de $bodyLength bytes")
            }
            if (!isValidModelFile(tempFile, config)) {
                throw NonRetryableDownloadException(
                    "El fichero descargado no tiene el tamaño esperado (${tempFile.length()} bytes)"
                )
            }
            verifyChecksum(config, digest)

            if (modelFile.exists()) modelFile.delete()
            if (!tempFile.renameTo(modelFile)) {
                throw NonRetryableDownloadException("No se pudo mover el modelo descargado a su destino")
            }

            Log.d(tag, "Model downloaded successfully: ${modelFile.absolutePath} " +
                    "(${modelFile.length() / 1024 / 1024}MB)")
        } catch (e: CancellationException) {
            // Cancellation (user or system): the .tmp is kept so it can resume later.
            throw e
        } catch (e: NonRetryableDownloadException) {
            // The content is no good (validation, 4xx): drop the .tmp so the next attempt is clean.
            tempFile.delete()
            Log.e(tag, "Error downloading model (no recuperable)", e)
            throw e
        } catch (e: Exception) {
            // Transient error (network): the .tmp is kept and the worker retries with Range.
            Log.e(tag, "Error downloading model", e)
            throw e
        }
    }

    private fun buildRequest(url: String, startOffset: Long): Request {
        val builder = Request.Builder().url(url)
        if (startOffset > 0) builder.header("Range", "bytes=$startOffset-")
        return builder.build()
    }

    /** The margin avoids filling the disk: Android degrades badly with storage at the limit. */
    private fun checkFreeSpace(config: AiModelConfig, tempFile: File) {
        val needed = (config.sizeBytes - tempFile.length()).coerceAtLeast(0L) + FREE_SPACE_MARGIN_BYTES
        val storageManager = context.getSystemService(StorageManager::class.java)
        val storageUuid = storageManager.getUuidForPath(modelDir)
        val allocatable = storageManager.getAllocatableBytes(storageUuid)
        if (allocatable < needed) {
            val missingMb = (needed - allocatable) / 1_000_000
            throw NonRetryableDownloadException(
                "No hay espacio suficiente para el modelo: faltan $missingMb MB libres"
            )
        }
        // Makes clearable cache space actually available before starting a multi-gigabyte file.
        storageManager.allocateBytes(storageUuid, needed)
    }

    /** Replays the hash over what was already downloaded, before resuming. */
    private fun seedDigestWithExistingBytes(digest: MessageDigest, file: File) {
        file.inputStream().use { input ->
            val buffer = ByteArray(DOWNLOAD_BUFFER_BYTES)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
    }

    /**
     * Compares the SHA-256 computed while writing against the one pinned in the catalog. A failure
     * here is not recoverable by retrying: either the remote artifact changed, or someone is
     * serving a different file. Either way the download is discarded and **never** executed.
     */
    private fun verifyChecksum(config: AiModelConfig, digest: MessageDigest) {
        val computed = digest.digest().joinToString("") { "%02x".format(it) }
        if (computed != config.sha256.lowercase()) {
            Log.e(tag, "SHA-256 de ${config.filename}: esperado ${config.sha256}, calculado $computed")
            throw NonRetryableDownloadException("El modelo descargado no supera la verificación de integridad")
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

    /** Deletes the model and, if present, its partial download (.tmp). */
    fun deleteModel(config: AiModelConfig): Boolean {
        File(modelDir, "${config.filename}.tmp").delete()
        return getModelFile(config).delete()
    }

    private companion object {
        /** Download read buffer — for gigabyte files 64 KB beats 8 KB. */
        const val DOWNLOAD_BUFFER_BYTES = 64 * 1024

        /** Extra free space required on top of the model itself. */
        const val FREE_SPACE_MARGIN_BYTES = 200_000_000L
    }

    fun cleanupLegacyModels() {
        if (legacyModelDir.exists()) {
            val deleted = legacyModelDir.deleteRecursively()
            Log.d(tag, "Legacy model cleanup: ${if (deleted) "success" else "failed"}")
        }
    }

    /**
     * Removes from [modelDir] any file that does not belong to a model still listed in
     * [validModels]. When a model is withdrawn or renamed in the catalog, its download stops taking
     * up space without any per-model cleanup code. Current models and their in-progress downloads
     * (.tmp) are preserved so an active download is not interrupted.
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
