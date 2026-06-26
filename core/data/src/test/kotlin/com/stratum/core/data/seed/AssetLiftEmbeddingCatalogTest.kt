package com.stratum.core.data.seed

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
class AssetLiftEmbeddingCatalogTest {
    @Test
    fun `loads bundled catalog embeddings`() {
        val context = RuntimeEnvironment.getApplication()
        val catalog = AssetLiftEmbeddingCatalog(context)

        assertEquals(384, catalog.dimensions)
        assertTrue(catalog.allVectors().size >= 600)
        val anyVector = catalog.allVectors().values.first()
        assertEquals(384, anyVector.size)
    }
}
