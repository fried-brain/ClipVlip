package com.friedbrain.clipvlip

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseVersioningTest {
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
