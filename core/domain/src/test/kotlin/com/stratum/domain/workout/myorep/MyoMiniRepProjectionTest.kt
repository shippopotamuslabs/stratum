package com.stratum.domain.workout.myorep

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("MyoMiniRepProjection: short-rest literature defaults")
class MyoMiniRepProjectionTest {

    @Nested
    @DisplayName("first mini after activation")
    inner class FirstMini {
        @Test
        @DisplayName("9-rep activation near fail projects well below 9 (not zero fatigue)")
        fun nineRepActivationDropsHard() {
            val reps = MyoMiniRepProjection.expectedReps(
                priorReps = 9,
                priorRir = 0,
                isFirstMini = true,
            )
            assertTrue(reps < 9, "expected aggressive short-rest drop from 9, got $reps")
            assertEquals(4, reps) // 9 * 0.45 → 4
        }

        @Test
        @DisplayName("12-rep activation maps into classic ~5 mini band")
        fun twelveRepActivation() {
            assertEquals(
                5,
                MyoMiniRepProjection.expectedReps(12, priorRir = 0, isFirstMini = true),
            )
        }

        @Test
        @DisplayName("RIR on activation counts toward failure capacity")
        fun rirIncluded() {
            // 9 + 1 RIR = 10 failure → 4.5 → 5
            assertEquals(
                5,
                MyoMiniRepProjection.expectedReps(9, priorRir = 1, isFirstMini = true),
            )
        }
    }

    @Nested
    @DisplayName("subsequent minis")
    inner class SubsequentMinis {
        @Test
        @DisplayName("drops 3 reps from last mini under short rest")
        fun dropsThreeFromLastMini() {
            assertEquals(
                6,
                MyoMiniRepProjection.expectedReps(9, priorRir = 0, isFirstMini = false),
            )
            assertEquals(
                2,
                MyoMiniRepProjection.expectedReps(5, priorRir = 0, isFirstMini = false),
            )
        }

        @Test
        @DisplayName("never recommends below 1 rep")
        fun floorAtOne() {
            assertEquals(
                1,
                MyoMiniRepProjection.expectedReps(2, priorRir = 0, isFirstMini = false),
            )
        }
    }
}
