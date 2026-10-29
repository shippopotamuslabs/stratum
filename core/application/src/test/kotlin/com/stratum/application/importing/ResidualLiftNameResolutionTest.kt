package com.stratum.application.importing

import com.stratum.core.testing.importing.FakeLiftEmbeddingCatalog
import com.stratum.core.testing.importing.FakeLiftNameEmbeddingPort
import com.stratum.domain.lift.identity.LiftId
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import com.stratum.domain.lift.residual

class ResidualLiftNameResolutionTest {
    @Test
    fun `clears singleton embeddings before local pass then residual rescue embeds only unmatched`() =
        runTest {
            val seatedId = LiftId("seated-leg")
            val ballId = LiftId("ball-leg")
            val squatId = LiftId("squat")
            val catalog =
                mapOf(
                    squatId to "Back Squat",
                    ballId to "Ball Leg Curl",
                    seatedId to "Seated Leg Curl",
                )
            val embeddingPort =
                FakeLiftNameEmbeddingPort(
                    vectorsByName = mapOf("Hamstring Curl" to floatArrayOf(1f, 0f)),
                )
            val resolver =
                HybridLiftNameResolver(
                    bm25 = Bm25LiftNameResolver(),
                    catalogEmbeddings =
                        FakeLiftEmbeddingCatalog(
                            vectorsByLiftId =
                                mapOf(
                                    seatedId to floatArrayOf(0.95f, 0.31f),
                                    ballId to floatArrayOf(0.2f, 0.98f),
                                ),
                            dimensions = 2,
                        ),
                    nameEmbedding = embeddingPort,
                )
            // Poison singleton with a prior residual batch (simulates previous import).
            resolver.prepareBatch(listOf("Hamstring Curl"))

            val resolved =
                ResidualLiftNameResolution.resolveNamesLocalThenResidual(
                    resolver = resolver,
                    distinctNames = listOf("Back Squat", "Hamstring Curl"),
                    catalogNames = catalog,
                )

            assertEquals(squatId, resolved["Back Squat"])
            assertEquals(seatedId, resolved["Hamstring Curl"])
            // Residual-only embed — exact catalog name never requested.
            assertEquals(listOf("Hamstring Curl"), embeddingPort.lastRequestedNames)
        }

    @Test
    fun `all exact matches never call embedding port`() = runTest {
        val squatId = LiftId("squat")
        val catalog = mapOf(squatId to "Back Squat")
        val embeddingPort = FakeLiftNameEmbeddingPort()
        val resolver =
            HybridLiftNameResolver(
                bm25 = Bm25LiftNameResolver(),
                catalogEmbeddings = FakeLiftEmbeddingCatalog(),
                nameEmbedding = embeddingPort,
            )

        val resolved =
            ResidualLiftNameResolution.resolveNamesLocalThenResidual(
                resolver = resolver,
                distinctNames = listOf("Back Squat"),
                catalogNames = catalog,
            )

        assertEquals(squatId, resolved["Back Squat"])
        assertTrue(embeddingPort.lastRequestedNames.isEmpty())
    }

    @Test
    fun `unmatched residual without usable embeddings stays null`() = runTest {
        val catalog = mapOf(LiftId("squat") to "Back Squat")
        val resolver =
            HybridLiftNameResolver(
                bm25 = Bm25LiftNameResolver(),
                catalogEmbeddings = FakeLiftEmbeddingCatalog(),
                nameEmbedding =
                    FakeLiftNameEmbeddingPort(
                        result = Result.failure(IllegalStateException("offline")),
                    ),
            )

        val resolved =
            ResidualLiftNameResolution.resolveNamesLocalThenResidual(
                resolver = resolver,
                distinctNames = listOf("Mystery Gizmo"),
                catalogNames = catalog,
            )

        assertNull(resolved["Mystery Gizmo"])
    }
}
