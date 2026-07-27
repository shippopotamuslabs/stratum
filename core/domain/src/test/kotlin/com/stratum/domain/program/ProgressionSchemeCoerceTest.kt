package com.stratum.domain.program

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("ProgressionScheme.coerceForCompound")
class ProgressionSchemeCoerceTest {

    @Test
    fun `isolation always Double even when stored Linear`() {
        assertEquals(
            ProgressionScheme.Double,
            ProgressionScheme.Linear.coerceForCompound(isCompound = false),
        )
        assertEquals(
            ProgressionScheme.Double,
            ProgressionScheme.Double.coerceForCompound(isCompound = false),
        )
    }

    @Test
    fun `compound keeps stored scheme`() {
        assertEquals(
            ProgressionScheme.Linear,
            ProgressionScheme.Linear.coerceForCompound(isCompound = true),
        )
        assertEquals(
            ProgressionScheme.Double,
            ProgressionScheme.Double.coerceForCompound(isCompound = true),
        )
    }
}
