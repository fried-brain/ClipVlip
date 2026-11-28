package com.friedbrain.clipvlip

/**
 * Compares a published release with the installed application.
 * Version code is authoritative when the release publishes it; semantic
 * version tags remain a backwards-compatible fallback for older releases.
 */
internal object ReleaseVersioning {
    fun isNewer(
        publishedVersionCode: Int?,
        installedVersionCode: Int,
        publishedTag: String,
        installedVersionName: String
    ): Boolean {
        return publishedVersionCode?.let { it > installedVersionCode }
            ?: (compareVersions(publishedTag, installedVersionName) > 0)
    }

    private fun compareVersions(left: String, right: String): Int {
        fun parts(value: String) = value.removePrefix("v").substringBefore("-")
            .split(".").map { it.toIntOrNull() ?: 0 }
        val a = parts(left)
        val b = parts(right)
        for (index in 0 until maxOf(a.size, b.size)) {
            val comparison = (a.getOrElse(index) { 0 }).compareTo(b.getOrElse(index) { 0 })
            if (comparison != 0) return comparison
        }
        return 0
    }
}
