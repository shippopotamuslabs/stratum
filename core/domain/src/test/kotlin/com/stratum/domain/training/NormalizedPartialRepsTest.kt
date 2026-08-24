package com.stratum.domain.training

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("normalizedPartialReps")
class NormalizedPartialRepsTest {

    @Test
    @DisplayName("null and non-positive become null")
    fun nullAndNonPositiveAreNone() {
        assertNull(null.normalizedPartialReps())
        assertNull(0.normalizedPartialReps())
        assertNull((-1).normalizedPartialReps())
    }

    @Test
    @DisplayName("positive values are kept")
    fun positiveKept() {
        assertEquals(2, 2.normalizedPartialReps())
    }
}
