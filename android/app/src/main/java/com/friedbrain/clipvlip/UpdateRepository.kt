package com.friedbrain.clipvlip

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

enum class UpdateChannel { AUTOMATIC, DIRECT, GOOGLE_PLAY }
data class DirectRelease(val tag: String, val downloadUrl: String, val sha256: String)
data class MobileRemoteConfig(
    val minimumSupportedVersionCode: Int = 1,
    val maintenance: Boolean = false,
    val maintenanceMessage: String = "",
    val directUpdates: Boolean = true,
    val googlePlayUpdates: Boolean = true
)

class UpdateRepository(private val context: android.content.Context) {
    private val prefs = context.getSharedPreferences("clipvlip_updates", 0)
    private val http = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS).build()

    var lastStatus: String
        get() = prefs.getString("last_status", "Updates not checked") ?: ""
        private set(value) { prefs.edit().putString("last_status", value).apply() }

    fun selectedChannel(): UpdateChannel = runCatching {
        UpdateChannel.valueOf(prefs.getString("channel", "AUTOMATIC")!!)
    }.getOrDefault(UpdateChannel.AUTOMATIC)

    fun setChannel(channel: UpdateChannel) { prefs.edit().putString("channel", channel.name).apply() }

    fun fetchRemoteConfig(): MobileRemoteConfig {
        val request = Request.Builder().url(BuildConfig.MOBILE_CONFIG_URL).build()
        http.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Config HTTP ${response.code}" }
            val root = JSONObject(response.body?.string().orEmpty())
            val app = root.optJSONObject("app") ?: JSONObject()
            val features = root.optJSONObject("features") ?: JSONObject()
            prefs.edit().putString("remote_config", root.toString()).apply()
            return MobileRemoteConfig(
                app.optInt("minimum_supported_version_code", 1),
                app.optBoolean("maintenance", false),
                app.optString("maintenance_message", ""),
                features.optBoolean("direct_updates", true),
                features.optBoolean("google_play_updates", true)
            )
        }
    }

    fun checkDirectRelease(): DirectRelease? {
        val url = "https://api.github.com/repos/${BuildConfig.GITHUB_REPOSITORY}/releases/latest"
        val request = Request.Builder().url(url).header("Accept", "application/vnd.github+json").build()
        http.newCall(request).execute().use { response ->
            if (response.code == 404) { lastStatus = "No direct release published yet."; return null }
            check(response.isSuccessful) { "Release check HTTP ${response.code}" }
            val release = JSONObject(response.body?.string().orEmpty())
            val assets = release.optJSONArray("assets") ?: return null
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                if (asset.optString("name").endsWith(".apk", true)) {
                    val digest = asset.optString("digest").removePrefix("sha256:")
                    if (digest.matches(Regex("[a-fA-F0-9]{64}")))
                        return DirectRelease(release.optString("tag_name"), asset.getString("browser_download_url"), digest)
                }
            }
            lastStatus = "No SHA-256-verifiable APK in latest release."
            return null
        }
    }

    fun downloadAndInstall(activity: Activity, release: DirectRelease) {
        if (!activity.packageManager.canRequestPackageInstalls()) {
            activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${activity.packageName}")))
            lastStatus = "Allow installs from ClipVlip in Android settings, then retry."
            return
        }
        val part = File(context.cacheDir, "clipvlip-update.part")
        val apk = File(context.cacheDir, "clipvlip-update.apk")
        val request = Request.Builder().url(release.downloadUrl).build()
        http.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "APK download HTTP ${response.code}" }
            response.body?.byteStream()?.use { input -> part.outputStream().use(input::copyTo) }
                ?: error("APK response was empty")
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(part.readBytes())
            .joinToString("") { "%02x".format(it) }
        check(digest.equals(release.sha256, true)) { "APK checksum mismatch; installation cancelled." }
        check(!apk.exists() || apk.delete())
        check(part.renameTo(apk)) { "Could not stage update APK." }
        val uri = FileProvider.getUriForFile(context, "${activity.packageName}.updates", apk)
        activity.startActivity(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        })
        lastStatus = "APK verified. Android installer opened for approval."
    }

    fun startPlayUpdate(activity: Activity, requestCode: Int) {\n        val manager = AppUpdateManagerFactory.create(activity)\n        manager.appUpdateInfo.addOnSuccessListener { info ->\n            if (info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE && info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)) {\n                manager.startUpdateFlowForResult(info, AppUpdateType.FLEXIBLE, activity, requestCode)\n            } else { lastStatus = "No Play update is available for this installation." }\n        }.addOnFailureListener { lastStatus = "Play update unavailable: ${it.message}" }\n    }\n\n    fun checkPlayUpdate(activity: Activity, onResult: (Boolean) -> Unit) {
        AppUpdateManagerFactory.create(activity).appUpdateInfo
            .addOnSuccessListener { info ->
                val available = info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                    info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)
                lastStatus = if (available) "Google Play update available." else "No Play update available."
                onResult(available)
            }.addOnFailureListener { error ->
                lastStatus = "Play update check unavailable: ${error.message}"
                onResult(false)
            }
    }
}
