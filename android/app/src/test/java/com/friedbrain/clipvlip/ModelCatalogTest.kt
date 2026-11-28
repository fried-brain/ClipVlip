package com.friedbrain.clipvlip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelCatalogTest {
    @Test
    fun catalogueContainsTheTwoDocumentedLocalLanguageModels() {
        assertEquals(
            setOf("qwen-2.5-1.5b", "deepseek-r1-qwen-1.5b"),
            ModelCatalog.models.map { it.id }.toSet()
        )
    }

    @Test
    fun everyModelHasAPinnedHttpsArtifactAndSha256Digest() {
        ModelCatalog.models.forEach { model ->
            assertTrue(model.downloadUrl.startsWith("https://huggingface.co/"))
            assertTrue(model.fileName.endsWith(".litertlm"))
            assertTrue(model.sha256.matches(Regex("[0-9a-f]{64}")))
            assertTrue(model.sizeBytes > 1_000_000_000L)
            assertEquals("LiteRT-LM 0.18.0", model.minimumRuntime)
        }
    }

    @Test
    fun modelLookupReturnsTheExpectedLicenseAndNullForUnknownIds() {
        assertEquals("Apache License 2.0", ModelCatalog.byId("qwen-2.5-1.5b")?.license)
        assertEquals("MIT License", ModelCatalog.byId("deepseek-r1-qwen-1.5b")?.license)
        assertNotNull(ModelCatalog.byId("qwen-2.5-1.5b"))
        assertEquals(null, ModelCatalog.byId("not-a-model"))
    }
}
