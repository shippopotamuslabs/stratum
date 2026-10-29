package com.stratum.application.importing

import com.stratum.core.testing.importing.FakeLiftEmbeddingCatalog
import com.stratum.core.testing.importing.FakeLiftNameEmbeddingPort
import com.stratum.domain.lift.identity.LiftId
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import com.stratum.domain.lift.residual

class HybridLiftNameResolverTest {
    private val bm25 = Bm25LiftNameResolver()

    @Test
    fun `subset name matches longer catalog lift via equipment-aware alignment without embeddings`() = runTest {
        val catalog =
            mapOf(
                LiftId("bench") to "Barbell Bench Press",
            )
        val resolver =
            HybridLiftNameResolver(
                bm25 = bm25,
                catalogEmbeddings = FakeLiftEmbeddingCatalog(),
                nameEmbedding = FakeLiftNameEmbeddingPort(result = Result.failure(IllegalStateException("offline"))),
            )
        resolver.prepareBatch(listOf("bench press"))

        val evaluation = resolver.evaluate("bench press", catalog)
        assertTrue(evaluation.matched)
        assertEquals(LiftId("bench"), evaluation.matchedLiftId)
    }

    @Test
    fun `high confidence bm25 match skips embedding rerank`() = runTest {
        val catalog =
            mapOf(
                LiftId("bench") to "Barbell Bench Press",
            )
        val embeddingPort = FakeLiftNameEmbeddingPort()
        val resolver =
            HybridLiftNameResolver(
                bm25 = bm25,
                catalogEmbeddings = FakeLiftEmbeddingCatalog(),
                nameEmbedding = embeddingPort,
            )
        // Residual-only callers skip prepareBatch for high-conf names entirely.
        val evaluation = resolver.evaluate("barbell bench press", catalog)
        assertTrue(evaluation.matched)
        assertEquals(LiftId("bench"), evaluation.matchedLiftId)
        assertTrue(embeddingPort.lastRequestedNames.isEmpty())
    }

    @Test
    fun `exact short catalog name matches high confidence without embeddings`() = runTest {
        val catalog =
            mapOf(
                LiftId("crunch") to "Crunch",
            )
        val embeddingPort = FakeLiftNameEmbeddingPort()
        val resolver =
            HybridLiftNameResolver(
                bm25 = bm25,
                catalogEmbeddings = FakeLiftEmbeddingCatalog(),
                nameEmbedding = embeddingPort,
            )

        val evaluation = resolver.evaluate("Crunch", catalog)
        assertTrue(evaluation.matched)
        assertEquals(LiftId("crunch"), evaluation.matchedLiftId)
        assertTrue(embeddingPort.lastRequestedNames.isEmpty())
    }

    @Test
    fun `padded residual name still finds embedding keyed by trimmed batch name`() = runTest {
        val seatedId = LiftId("seated-leg")
        val ballId = LiftId("ball-leg")
        val catalog =
            mapOf(
                ballId to "Ball Leg Curl",
                seatedId to "Seated Leg Curl",
            )
        val query = floatArrayOf(1f, 0f)
        val resolver =
            HybridLiftNameResolver(
                bm25 = bm25,
                catalogEmbeddings =
                    FakeLiftEmbeddingCatalog(
                        vectorsByLiftId =
                            mapOf(
                                seatedId to floatArrayOf(0.95f, 0.31f),
                                ballId to floatArrayOf(0.2f, 0.98f),
                            ),
                        dimensions = 2,
                    ),
                nameEmbedding =
                    FakeLiftNameEmbeddingPort(
                        vectorsByName = mapOf("Hamstring Curl" to query),
                    ),
            )
        // Batch uses trimmed form (as cloud does); evaluate uses padded raw string.
        resolver.prepareBatch(listOf("  Hamstring Curl  "))

        val evaluation = resolver.evaluate("  Hamstring Curl  ", catalog)
        assertTrue(evaluation.matched)
        assertEquals(seatedId, evaluation.matchedLiftId)
    }

    @Test
    fun `prepareBatch empty clears prior embeddings so local pass cannot reuse them`() = runTest {
        val seatedId = LiftId("seated-leg")
        val ballId = LiftId("ball-leg")
        val catalog =
            mapOf(
                ballId to "Ball Leg Curl",
                seatedId to "Seated Leg Curl",
            )
        val resolver =
            HybridLiftNameResolver(
                bm25 = bm25,
                catalogEmbeddings =
                    FakeLiftEmbeddingCatalog(
                        vectorsByLiftId =
                            mapOf(
                                seatedId to floatArrayOf(0.95f, 0.31f),
                                ballId to floatArrayOf(0.2f, 0.98f),
                            ),
                        dimensions = 2,
                    ),
                nameEmbedding =
                    FakeLiftNameEmbeddingPort(
                        vectorsByName = mapOf("Hamstring Curl" to floatArrayOf(1f, 0f)),
                    ),
            )
        resolver.prepareBatch(listOf("Hamstring Curl"))
        assertTrue(resolver.evaluate("Hamstring Curl", catalog).matched)

        resolver.prepareBatch(emptyList())
        // Without vectors, gray-zone falls through (no high-conf BM25 to seated/ball).
        assertFalse(resolver.evaluate("Hamstring Curl", catalog).matched)
    }

    @Test
    fun `embedding rerank breaks bm25 tie toward closer semantic match`() = runTest {
        val seatedId = LiftId("seated-leg")
        val ballId = LiftId("ball-leg")
        val catalog =
            mapOf(
                ballId to "Ball Leg Curl",
                seatedId to "Seated Leg Curl",
            )
        val query = floatArrayOf(1f, 0f)
        val seatedVector = floatArrayOf(0.95f, 0.31f)
        val ballVector = floatArrayOf(0.2f, 0.98f)
        val resolver =
            HybridLiftNameResolver(
                bm25 = bm25,
                catalogEmbeddings =
                    FakeLiftEmbeddingCatalog(
                        vectorsByLiftId =
                            mapOf(
                                seatedId to seatedVector,
                                ballId to ballVector,
                            ),
                        dimensions = 2,
                    ),
                nameEmbedding =
                    FakeLiftNameEmbeddingPort(
                        vectorsByName = mapOf("Hamstring Curl" to query),
                    ),
            )
        resolver.prepareBatch(listOf("Hamstring Curl"))

        val evaluation = resolver.evaluate("Hamstring Curl", catalog)
        assertTrue(evaluation.matched)
        assertEquals(seatedId, evaluation.matchedLiftId)
    }

    @Test
    fun `low confidence bm25 without exact name falls back to custom lift path`() = runTest {
        val catalog =
            mapOf(
                LiftId("pullups") to "Pullups",
                LiftId("scapular") to "Scapular Pull-Up",
            )
        val resolver =
            HybridLiftNameResolver(
                bm25 = bm25,
                catalogEmbeddings = FakeLiftEmbeddingCatalog(),
                nameEmbedding = FakeLiftNameEmbeddingPort(result = Result.failure(IllegalStateException("offline"))),
            )
        resolver.prepareBatch(listOf("Pull-Up"))

        val evaluation = resolver.evaluate("Pull-Up", catalog)
        assertFalse(evaluation.matched)
    }

    @Test
    fun `scapular pullup without embeddings falls through to custom lift path`() = runTest {
        val catalog =
            mapOf(
                LiftId("pullups") to "Pullups",
                LiftId("scapular") to "Scapular Pull-Up",
            )
        val resolver =
            HybridLiftNameResolver(
                bm25 = bm25,
                catalogEmbeddings = FakeLiftEmbeddingCatalog(),
                nameEmbedding = FakeLiftNameEmbeddingPort(result = Result.failure(IllegalStateException("offline"))),
            )
        resolver.prepareBatch(listOf("Scapular Pullup"))

        val evaluation = resolver.evaluate("Scapular Pullup", catalog)
        assertFalse(evaluation.matched)
    }

    @Test
    fun `embedding rerank resolves scapular pullup to scapular catalog entry`() = runTest {
        val pullupsId = LiftId("pullups")
        val scapularId = LiftId("scapular")
        val catalog =
            mapOf(
                pullupsId to "Pullups",
                scapularId to "Scapular Pull-Up",
            )
        val query = floatArrayOf(0.2f, 0.98f)
        val pullupsVector = floatArrayOf(1f, 0f)
        val scapularVector = floatArrayOf(0.2f, 1f)
        val resolver =
            HybridLiftNameResolver(
                bm25 = bm25,
                catalogEmbeddings =
                    FakeLiftEmbeddingCatalog(
                        vectorsByLiftId =
                            mapOf(
                                pullupsId to pullupsVector,
                                scapularId to scapularVector,
                            ),
                        dimensions = 2,
                    ),
                nameEmbedding =
                    FakeLiftNameEmbeddingPort(
                        vectorsByName = mapOf("Scapular Pullup" to query),
                    ),
            )
        resolver.prepareBatch(listOf("Scapular Pullup"))

        val evaluation = resolver.evaluate("Scapular Pullup", catalog)
        assertTrue(evaluation.matched)
        assertEquals(scapularId, evaluation.matchedLiftId)
    }

    @Test
    fun `exact catalog name still matches when embeddings unavailable`() = runTest {
        val catalog =
            mapOf(
                LiftId("bench") to "Barbell Bench Press",
            )
        val resolver =
            HybridLiftNameResolver(
                bm25 = bm25,
                catalogEmbeddings = FakeLiftEmbeddingCatalog(),
                nameEmbedding = FakeLiftNameEmbeddingPort(result = Result.failure(IllegalStateException("offline"))),
            )
        resolver.prepareBatch(listOf("barbell bench press"))

        val evaluation = resolver.evaluate("barbell bench press", catalog)
        assertTrue(evaluation.matched)
        assertEquals(LiftId("bench"), evaluation.matchedLiftId)
    }

    @Test
    fun `implement veto blocks ball leg curl when query lacks implement token`() = runTest {
        val seatedId = LiftId("seated-leg")
        val ballId = LiftId("ball-leg")
        val catalog =
            mapOf(
                ballId to "Ball Leg Curl",
                seatedId to "Seated Leg Curl",
            )
        val query = floatArrayOf(0.1f, 1f)
        val seatedVector = floatArrayOf(0.1f, 0.9f)
        val ballVector = floatArrayOf(0.1f, 1f)
        val resolver =
            HybridLiftNameResolver(
                bm25 = bm25,
                catalogEmbeddings =
                    FakeLiftEmbeddingCatalog(
                        vectorsByLiftId =
                            mapOf(
                                seatedId to seatedVector,
                                ballId to ballVector,
                            ),
                        dimensions = 2,
                    ),
                nameEmbedding =
                    FakeLiftNameEmbeddingPort(
                        vectorsByName = mapOf("Hamstring Curl" to query),
                    ),
            )
        resolver.prepareBatch(listOf("Hamstring Curl"))

        val evaluation = resolver.evaluate("Hamstring Curl", catalog)
        assertTrue(evaluation.matched)
        assertEquals(seatedId, evaluation.matchedLiftId)
        assertFalse(evaluation.bestCatalogName?.contains("Ball") == true)
    }
}
