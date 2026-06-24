package com.stratum.domain.lift.identity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class LiftIdTest {

    @Nested
    @DisplayName("construction")
    inner class Construction {
        @Test
        @DisplayName("Given a non-blank value when constructed then it holds the value")
        fun holdsValue() {
            val id = LiftId("leg-press")
            assertEquals("leg-press", id.value)
        }

        @Test
        @DisplayName("Given a blank value when constructed then it throws")
        fun blankThrows() {
            assertThrows(IllegalArgumentException::class.java) { LiftId("") }
        }

        @Test
        @DisplayName("Given a whitespace-only value when constructed then it throws")
        fun whitespaceThrows() {
            assertThrows(IllegalArgumentException::class.java) { LiftId("   ") }
        }
    }

    @Nested
    @DisplayName("equality")
    inner class Equality {
        @Test
        @DisplayName("Given two ids with the same value when compared then they are equal")
        fun sameValueEquals() {
            assertEquals(LiftId("bench-press"), LiftId("bench-press"))
        }

        @Test
        @DisplayName("Given an id when used as a map key then it round-trips by value")
        fun mapKeyRoundTrip() {
            val map = mapOf(LiftId("squat") to 1)
            assertEquals(1, map[LiftId("squat")])
        }
    }
}
