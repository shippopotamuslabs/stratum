package com.stratum.core.inference

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class FirebaseLiftSwapAdapterTest {
    @Test
    @DisplayName("parseResponse maps swap rows from callable payload")
    fun `parseResponse extracts swaps`() {
        val data =
            mapOf(
                "swaps" to
                    listOf(
                        mapOf(
                            "workoutPosition" to 0,
                            "liftPosition" to 1,
                            "newLiftId" to "curl",
                        ),
                    ),
            )
        val result = parseLiftSwapResponse(data)
        assertEquals(1, result.swaps.size)
        assertEquals(0, result.swaps.first().workoutPosition)
        assertEquals(1, result.swaps.first().liftPosition)
        assertEquals("curl", result.swaps.first().newLiftId)
    }

    @Test
    @DisplayName("parseResponse returns empty swaps for malformed payload")
    fun `parseResponse tolerates missing swaps`() {
        val result = parseLiftSwapResponse(emptyMap<String, Any>())
        assertEquals(0, result.swaps.size)
    }
}
