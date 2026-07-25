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
 * Error de descarga que no merece reintento automático (HTTP 4xx, validación de integridad,
 * falta de espacio): el worker lo convierte en fallo definitivo en vez de en `Result.retry()`.
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
     * Un fichero de modelo solo es válido si pesa al menos el 95% del tamaño esperado del
     * catálogo: descarta páginas de error o ficheros truncados guardados por descargas
     * antiguas sin validación, sin exigir el byte exacto (el tamaño del catálogo es
     * aproximado). Un fichero inválido cuenta como "no descargado" y se puede redescargar.
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
            // Reanudación: si quedó un .tmp de un intento anterior, se pide solo el resto
            // con Range. La verificación de integridad del final valida el conjunto.
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

                // Solo se siguen redirecciones HTTPS: al seguirlas a mano, OkHttp ya no
                // puede impedir por sí mismo una degradación a HTTP plano.
                if (!location.startsWith("https://")) {
                    throw NonRetryableDownloadException("Redirección insegura rechazada (solo HTTPS)")
                }
                request = buildRequest(location, startOffset)
                response = executeCall(okHttpClient.newCall(request))
            }

            if (response.code == 416) {
                // El rango ya no cuadra con el fichero remoto: se descarta lo parcial y el
                // reintento (este error es transitorio) empieza de cero.
                response.close()
                tempFile.delete()
                throw IOException("Rango de reanudación no válido; se reinicia la descarga")
            }
            if (!response.isSuccessful) {
                val code = response.code
                response.close()
                if (code in 400..499) {
                    // Un 4xx (sin permisos, modelo retirado…) no se arregla reintentando.
                    throw NonRetryableDownloadException("Error HTTP $code al descargar el modelo")
                }
                throw IOException("Error HTTP $code al descargar el modelo")
            }

            // 206 = el servidor acepta reanudar; 200 con Range = manda el fichero entero.
            val resuming = response.code == 206 && startOffset > 0
            if (!resuming) startOffset = 0L

            // SHA-256 en streaming (coste cero de pasada extra). Al reanudar hay que rehacer
            // primero el hash de lo ya escrito: un digest no se puede retomar a mitad.
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

            // Un cierre "limpio" del servidor a mitad de fichero no lanza excepción: sin estas
            // comprobaciones, un modelo truncado (o una página de error diminuta) se daría por
            // bueno y el engine fallaría después con un error críptico e irrecuperable.
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
            // Cancelación (usuario o sistema): se conserva el .tmp para reanudar después.
            throw e
        } catch (e: NonRetryableDownloadException) {
            // El contenido no vale (validación, 4xx…): fuera el .tmp, el siguiente intento es limpio.
            tempFile.delete()
            Log.e(tag, "Error downloading model (no recuperable)", e)
            throw e
        } catch (e: Exception) {
            // Error transitorio (red): se conserva el .tmp y el worker reintenta con Range.
            Log.e(tag, "Error downloading model", e)
            throw e
        }
    }

    private fun buildRequest(url: String, startOffset: Long): Request {
        val builder = Request.Builder().url(url)
        if (startOffset > 0) builder.header("Range", "bytes=$startOffset-")
        return builder.build()
    }

    /** El margen evita apurar el disco: Android degrada mucho con el almacenamiento al límite. */
    private fun checkFreeSpace(config: AiModelConfig, tempFile: File) {
        val needed = (config.sizeBytes - tempFile.length()).coerceAtLeast(0L) + FREE_SPACE_MARGIN_BYTES
        // usableSpace devuelve 0 cuando el sistema no sabe responder: ahí no bloqueamos.
        val usable = modelDir.usableSpace
        if (usable in 1 until needed) {
            val missingMb = (needed - usable) / 1_000_000
            throw NonRetryableDownloadException(
                "No hay espacio suficiente para el modelo: faltan $missingMb MB libres"
            )
        }
    }

    /** Rehace el hash de lo ya descargado antes de reanudar. */
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
     * Compara el SHA-256 calculado durante la escritura con el fijado en el catálogo. Un fallo
     * aquí no es recuperable reintentando: o el artefacto remoto ha cambiado, o alguien está
     * sirviendo otro fichero. En ambos casos se descarta la descarga y NO se ejecuta.
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

    /** Borra el modelo y, si la hay, su descarga parcial (.tmp). */
    fun deleteModel(config: AiModelConfig): Boolean {
        File(modelDir, "${config.filename}.tmp").delete()
        return getModelFile(config).delete()
    }

    private companion object {
        /** Búfer de lectura de la descarga (ficheros de GB: mejor 64 KB que 8 KB). */
        const val DOWNLOAD_BUFFER_BYTES = 64 * 1024

        /** Espacio libre extra exigido además del propio modelo. */
        const val FREE_SPACE_MARGIN_BYTES = 200_000_000L
    }

    fun cleanupLegacyModels() {
        if (legacyModelDir.exists()) {
            val deleted = legacyModelDir.deleteRecursively()
            Log.d(tag, "Legacy model cleanup: ${if (deleted) "success" else "failed"}")
        }
    }

    /**
     * Elimina de [modelDir] cualquier fichero que no corresponda a un modelo
     * vigente en [validModels]. Así, cuando se retira o renombra un modelo del
     * catálogo, su descarga deja de ocupar espacio sin necesidad de código de
     * limpieza específico por modelo. Se conservan los modelos vigentes y sus
     * descargas en curso (.tmp) para no interrumpir una descarga activa.
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
