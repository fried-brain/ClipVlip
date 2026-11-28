package com.friedbrain.clipvlip

import android.content.Context
import android.os.StatFs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ModelDownloadRepository(context: Context) {
    private val modelDirectory = File(context.filesDir, "models").apply { mkdirs() }
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.MINUTES)
        .retryOnConnectionFailure(true)
        .build()

    fun installedFile(model: ModelArtifact): File = File(modelDirectory, model.fileName)

    fun isInstalled(model: ModelArtifact): Boolean {
        val file = installedFile(model)
        return file.exists() && file.length() == model.sizeBytes
    }

    suspend fun download(
        model: ModelArtifact,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val destination = installedFile(model)
        if (isInstalled(model)) return@withContext destination

        val partial = File(modelDirectory, "${model.fileName}.part")
        if (partial.exists() && !partial.delete()) {
            throw IllegalStateException("Could not remove an incomplete previous download. Free storage manually and try again.")
        }

        val availableBytes = StatFs(modelDirectory.absolutePath).availableBytes
        if (availableBytes < model.sizeBytes + 100_000_000L) {
            throw IllegalStateException("Not enough free storage. Free at least ${model.sizeLabel} plus temporary download space.")
        }

        val request = Request.Builder()
            .url(model.downloadUrl)
            .header("User-Agent", "ClipVlip-Android/0.1")
            .build()

        val coroutineContext = currentCoroutineContext()
        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            var completed = false
            try {
                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IllegalStateException("Model download failed: HTTP ${response.code}")
                    }
                    val body = response.body ?: throw IllegalStateException("The model server returned an empty response.")
                    val totalBytes = body.contentLength().takeIf { it > 0 } ?: model.sizeBytes
                    var downloaded = 0L
                    val digest = MessageDigest.getInstance("SHA-256")

                    body.byteStream().use { input ->
                        FileOutputStream(partial).use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 16)
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val count = input.read(buffer)
                                if (count < 0) break
                                output.write(buffer, 0, count)
                                digest.update(buffer, 0, count)
                                downloaded += count
                                onProgress(downloaded, totalBytes)
                            }
                            output.fd.sync()
                        }
                    }

                    if (downloaded != model.sizeBytes) {
                        throw IllegalStateException("Downloaded size does not match the verified model package.")
                    }
                    val actualHash = digest.digest().joinToString("") { "%02x".format(it) }
                    if (!actualHash.equals(model.sha256, ignoreCase = true)) {
                        throw SecurityException("Model integrity check failed. The incomplete file has been rejected.")
                    }
                    Files.move(partial.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    completed = true
                    continuation.resume(destination)
                }
            } catch (error: Throwable) {
                if (continuation.isActive) continuation.resumeWithException(error)
            } finally {
                if (!completed) partial.delete()
            }
        }
    }

    fun delete(model: ModelArtifact): Boolean = installedFile(model).delete()
}
