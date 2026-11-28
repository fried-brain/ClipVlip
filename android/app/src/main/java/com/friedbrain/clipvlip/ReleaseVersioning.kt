package com.friedbrain.clipvlip

/**
 * Compares a published release with the installed application.
 * Version code is authoritative when the release publishes it; semantic
 * version tags remain a backwards-compatible fallback for older releases.
 */
internal object ReleaseVersioning {
    /**
     * Encodes semantic versions in lexicographic order for Google Play.
     * Supported range: major 0..2099, minor/patch 0..999.
     */
    fun androidVersionCode(major: Int, minor: Int, patch: Int): Int {
        require(major in 0..2099) { "major version out of range" }
        require(minor in 0..999) { "minor version out of range" }
        require(patch in 0..999) { "patch version out of range" }
        return major * 1_000_000 + minor * 1_000 + patch + 1
    }

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
