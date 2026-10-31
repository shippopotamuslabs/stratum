package com.stratum.core.infrastructure.inference

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FirebaseLiftNameEmbeddingAdapterTest {
    @Test
    fun `parseResponse maps embedding lists to float arrays`() {
        val parsed =
            FirebaseLiftNameEmbeddingAdapter.parseResponse(
                mapOf(
                    "embeddings" to
                        mapOf(
                            "Bench Press" to listOf(0.1, 0.2, 0.3),
                            "Squat" to listOf(1.0, 0.0),
                        ),
                    "dimensions" to 3,
                ),
            )

        assertEquals(2, parsed.size)
        assertEquals(floatArrayOf(0.1f, 0.2f, 0.3f).toList(), parsed["Bench Press"]!!.toList())
        assertEquals(floatArrayOf(1.0f, 0.0f).toList(), parsed["Squat"]!!.toList())
    }
}
