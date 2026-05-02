package com.monsteraltech.habitly.feature.aiassistant.data.source

import android.content.Context
import android.util.Log
import com.monsteraltech.habitly.feature.aiassistant.domain.model.AiModelConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import okio.sink
import okio.source
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

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
            var request = Request.Builder().url(config.downloadUrl).build()
            var response = executeCall(okHttpClient.newCall(request))

            var redirectCount = 0
            while (response.code in listOf(301, 302, 303, 307, 308)) {
                redirectCount++
                if (redirectCount > 5) throw IOException("Demasiados redireccionamientos")

                val location = response.header("Location")
                    ?: throw IOException("Redirect sin Location")
                response.close()

                request = Request.Builder().url(location).build()
                response = executeCall(okHttpClient.newCall(request))
            }

            val contentLength = (response.body?.contentLength() ?: 0L).coerceAtLeast(config.sizeBytes)

            response.body?.source()?.use { source ->
                tempFile.sink().buffer().use { sink ->
                    val buffer = ByteArray(8192)
                    var totalRead = 0L
                    var bytesRead: Int

                    while (source.read(buffer).also { bytesRead = it } != -1) {
                        ensureActive()
                        sink.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        val progress = (totalRead.toFloat() / contentLength).coerceIn(0f, 1f)
                        onProgress(progress)
                    }
                    sink.flush()
                }
            }

            response.close()
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

    fun deleteModel(config: AiModelConfig): Boolean {
        return getModelFile(config).delete()
    }

    fun cleanupLegacyModels() {
        if (legacyModelDir.exists()) {
            val deleted = legacyModelDir.deleteRecursively()
            Log.d(tag, "Legacy model cleanup: ${if (deleted) "success" else "failed"}")
        }
    }
}
