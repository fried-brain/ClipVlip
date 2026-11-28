package com.friedbrain.clipvlip

/**
 * Compares a published release with the installed application.
 * Version code is authoritative when the release publishes it; semantic
 * version tags remain a backwards-compatible fallback for older releases.
 */
internal object ReleaseVersioning {
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

    private data class SemVer(
        val major: Int,
        val minor: Int,
        val patch: Int,
        val prerelease: List<String>?
    )

    private fun parse(value: String): SemVer? {
        val normalized = value.removePrefix("v")
        val match = Regex("^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)(?:-([0-9A-Za-z.-]+))?(?:\\+[0-9A-Za-z.-]+)?$")
            .matchEntire(normalized) ?: return null
        val pre = match.groupValues[4].takeIf { it.isNotEmpty() }?.split(".")
        if (pre?.any { it.isEmpty() || (it.all(Char::isDigit) && it.length > 1 && it.startsWith("0")) } == true) return null
        return SemVer(
            match.groupValues[1].toIntOrNull() ?: return null,
            match.groupValues[2].toIntOrNull() ?: return null,
            match.groupValues[3].toIntOrNull() ?: return null,
            pre
        )
    }

    private fun compareVersions(left: String, right: String): Int {
        val a = parse(left) ?: return 0
        val b = parse(right) ?: return 0
        compareValues(a.major, b.major).takeIf { it != 0 }?.let { return it }
        compareValues(a.minor, b.minor).takeIf { it != 0 }?.let { return it }
        compareValues(a.patch, b.patch).takeIf { it != 0 }?.let { return it }
        if (a.prerelease == null && b.prerelease == null) return 0
        if (a.prerelease == null) return 1
        if (b.prerelease == null) return -1
        for (index in 0 until minOf(a.prerelease.size, b.prerelease.size)) {
            val x = a.prerelease[index]
            val y = b.prerelease[index]
            val xNumeric = x.all(Char::isDigit)
            val yNumeric = y.all(Char::isDigit)
            val comparison = when {
                xNumeric && yNumeric -> {
                    // SemVer numeric identifiers are unbounded; length then lexical
                    // comparison gives exact numeric ordering without integer overflow.
                    x.length.compareTo(y.length).takeIf { it != 0 } ?: x.compareTo(y)
                }
                xNumeric -> -1
                yNumeric -> 1
                else -> x.compareTo(y)
            }
            if (comparison != 0) return comparison
        }
        return a.prerelease.size.compareTo(b.prerelease.size)
    }
}
