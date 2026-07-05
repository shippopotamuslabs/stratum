package com.stratum.domain.program

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class EstimateWorkoutDurationMinutesUseCaseTest {
    private val subject = EstimateWorkoutDurationMinutesUseCase()

    @Test
    @DisplayName("EstimateWorkoutDurationMinutesUseCase excludes rest after the final set")
    fun estimateDuration_excludesFinalRest() {
        val minutes =
            subject(
                workoutLifts =
                    listOf(
                        WorkoutLiftDurationInput(restSecondsBySet = listOf(120, 120)),
                    ),
            )

        // 60s work + 120s rest + 60s work = 240s -> 4 minutes
        assertEquals(4, minutes)
    }

    @Test
    @DisplayName("estimateLiftSeconds sums active work and between-set rest")
    fun estimateLiftSeconds_betweenSetRestOnly() {
        val seconds =
            estimateLiftSeconds(
                WorkoutLiftDurationInput(restSecondsBySet = listOf(90, 90, 90)),
            )

        assertEquals(60 + 90 + 60 + 90 + 60, seconds)
    }
}
