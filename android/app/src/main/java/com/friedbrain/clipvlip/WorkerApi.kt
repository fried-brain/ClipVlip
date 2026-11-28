package com.friedbrain.clipvlip

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

data class WorkerTask(
    val id: String,
    val status: String,
    val progress: Int,
    val message: String,
    val error: String? = null,
    val clips: JSONArray = JSONArray()
)

class WorkerApi(context: Context) {
    private val prefs = context.getSharedPreferences("clipvlip_worker", Context.MODE_PRIVATE)
    var baseUrl: String
        get() = prefs.getString("base_url", "") ?: ""
        set(value) { prefs.edit().putString("base_url", value.trim().trimEnd('/')).apply() }
    var accessKey: String
        get() = prefs.getString("access_key", "") ?: ""
        set(value) { prefs.edit().putString("access_key", value.trim()).apply() }

    fun isConfigured(): Boolean = baseUrl.startsWith("https://") && accessKey.isNotBlank()

    fun createTask(
        context: Context,
        sourceUri: Uri?,
        sourceUrl: String,
        clipCount: Int,
        minDuration: Int,
        maxDuration: Int,
        aspectRatio: String,
        captions: Boolean,
        captionStyle: String
    ): String {
        check(isConfigured()) { "Set the HTTPS worker URL and access key in Settings first." }
        require(sourceUri != null || sourceUrl.startsWith("https://")) { "Choose a video or enter an HTTPS video URL." }
        val boundary = "ClipVlip-${UUID.randomUUID()}"
        val connection = open("/api/tasks", "POST")
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        connection.doOutput = true
        connection.connectTimeout = 20000
        connection.readTimeout = 120000
        connection.outputStream.use { out ->
            fun field(name: String, value: String) {
                out.write("--$boundary\r\n".toByteArray())
                out.write("Content-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n".toByteArray())
            }
            field("clip_count", clipCount.toString())
            field("min_duration", minDuration.toString())
            field("max_duration", maxDuration.toString())
            field("aspect_ratio", aspectRatio)
            field("captions", captions.toString())
            field("caption_style", captionStyle)
            if (sourceUri != null) {
                val resolver = context.contentResolver
                val name = resolver.query(sourceUri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else "source.mp4" } ?: "source.mp4"
                val mime = resolver.getType(sourceUri) ?: "video/mp4"
                out.write("--$boundary\r\n".toByteArray())
                out.write("Content-Disposition: form-data; name=\"file\"; filename=\"$name\"\r\n".toByteArray())
                out.write("Content-Type: $mime\r\n\r\n".toByteArray())
                resolver.openInputStream(sourceUri)?.use { input -> input.copyTo(out) }
                    ?: error("Cannot read the selected video.")
                out.write("\r\n".toByteArray())
            } else field("source_url", sourceUrl)
            out.write("--$boundary--\r\n".toByteArray())
        }
        val code = connection.responseCode
        val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
            ?.bufferedReader()?.use { it.readText() }.orEmpty()
        connection.disconnect()
        if (code !in 200..299) throw IllegalStateException("Worker HTTP $code: ${runCatching { JSONObject(body).optString("detail") }.getOrDefault(body)}")
        return JSONObject(body).getString("id")
    }

    fun listTasks(): List<WorkerTask> {
        check(isConfigured()) { "Configure the worker in Settings first." }
        val connection = open("/api/tasks", "GET")
        val code = connection.responseCode
        val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
            ?.bufferedReader()?.use { it.readText() }.orEmpty()
        connection.disconnect()
        if (code !in 200..299) throw IllegalStateException("Worker HTTP $code: $body")
        val array = JSONArray(body)
        return (0 until array.length()).map { i ->
            val item = array.getJSONObject(i)
            WorkerTask(
                id = item.optString("id"),
                status = item.optString("status"),
                progress = item.optInt("progress"),
                message = item.optString("message"),
                error = item.optString("error").takeIf { it.isNotBlank() && it != "null" },
                clips = item.optJSONArray("clips") ?: JSONArray()
            )
        }
    }

    private fun open(path: String, method: String): HttpURLConnection {
        val connection = URL(baseUrl + path).openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("X-ClipVlip-Key", accessKey)
        connection.connectTimeout = 20000
        connection.readTimeout = 120000
        return connection
    }
}
