package com.friedbrain.clipvlip

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseVersioningTest {
    @Test
    fun semanticVersionCodesIncreaseInSemanticOrder() {
        val v151 = ReleaseVersioning.androidVersionCode(1, 5, 1)
        val v200 = ReleaseVersioning.androidVersionCode(2, 0, 0)
        assertTrue(v200 > v151)
        assertTrue(ReleaseVersioning.androidVersionCode(2, 0, 1) >
            ReleaseVersioning.androidVersionCode(2, 0, 0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsOutOfRangePatchVersion() {
        ReleaseVersioning.androidVersionCode(1, 0, 1000)
    }

    @Test
    fun rejectsReleaseWithVersionCodeAlreadyInstalled() {
        assertFalse(ReleaseVersioning.isNewer(42, 42, "v9.9.9", "0.1.0"))
    }

    @Test
    fun acceptsReleaseWithHigherVersionCodeEvenIfTagParsingIsNotUseful() {
        assertTrue(ReleaseVersioning.isNewer(43, 42, "nightly-build", "0.1.0"))
    }

    @Test
    fun fallsBackToSemanticVersionWhenReleaseHasNoVersionCode() {
        assertTrue(ReleaseVersioning.isNewer(null, 42, "v0.2.0", "0.1.0"))
        assertFalse(ReleaseVersioning.isNewer(null, 42, "v0.1.0", "0.1.0"))
    }
}
