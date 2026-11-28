package com.friedbrain.clipvlip

import android.content.Context
import android.os.StatFs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class ModelDownloadRepository(context: Context) {
    private val modelDirectory = File(context.filesDir, "models").apply { mkdirs() }
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
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

        val availableBytes = StatFs(modelDirectory.absolutePath).availableBytes
        if (availableBytes < model.sizeBytes + 100_000_000L) {
            throw IllegalStateException("Not enough free storage. Free at least ${model.sizeLabel} plus temporary download space.")
        }

        val partial = File(modelDirectory, "${model.fileName}.part")
        partial.delete()
        val request = Request.Builder()
            .url(model.downloadUrl)
            .header("User-Agent", "ClipVlip-Android/0.1")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Model download failed: HTTP ${response.code}")
            }
            val body = response.body ?: throw IllegalStateException("The model server returned an empty response.")
            val totalBytes = body.contentLength().takeIf { it > 0 } ?: model.sizeBytes
            val coroutineContext = currentCoroutineContext()
            var downloaded = 0L
            val digest = MessageDigest.getInstance("SHA-256")

            try {
                body.byteStream().use { input ->
                    FileOutputStream(partial).use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 16)
                        while (true) {
                            coroutineContext.ensureActive()
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
                destination
            } catch (error: Throwable) {
                partial.delete()
                throw error
            }
        }
    }

    fun delete(model: ModelArtifact): Boolean = installedFile(model).delete()
}
