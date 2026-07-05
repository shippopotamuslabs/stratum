package com.stratum.domain.program

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
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

    @Test
    @DisplayName("Superset with inter-lift rest off skips non-terminal rest between lifts")
    fun supersetWithoutInterLiftRestIsShorter() {
        val withRest =
            subject(
                listOf(
                    WorkoutLiftDurationInput(
                        restSecondsBySet = listOf(120),
                        position = 0,
                        supersetGroupId = "sg-1",
                        useRestTimerBetweenLifts = true,
                    ),
                    WorkoutLiftDurationInput(
                        restSecondsBySet = listOf(120),
                        position = 1,
                        supersetGroupId = "sg-1",
                        useRestTimerBetweenLifts = false,
                    ),
                ),
            )
        val withoutInterLiftRest =
            subject(
                listOf(
                    WorkoutLiftDurationInput(
                        restSecondsBySet = listOf(120),
                        position = 0,
                        supersetGroupId = "sg-1",
                        useRestTimerBetweenLifts = false,
                    ),
                    WorkoutLiftDurationInput(
                        restSecondsBySet = listOf(120),
                        position = 1,
                        supersetGroupId = "sg-1",
                        useRestTimerBetweenLifts = false,
                    ),
                ),
            )

        assertTrue(withRest > withoutInterLiftRest)
    }

    @Test
    @DisplayName("Terminal lift in superset counts rest between rounds")
    fun supersetTerminalLiftCountsRoundRest() {
        val minutes =
            subject(
                listOf(
                    WorkoutLiftDurationInput(
                        restSecondsBySet = listOf(60, 60),
                        position = 0,
                        supersetGroupId = "sg-1",
                    ),
                    WorkoutLiftDurationInput(
                        restSecondsBySet = listOf(90, 90),
                        position = 1,
                        supersetGroupId = "sg-1",
                    ),
                ),
            )

        // Round 1: 60 active + 90 active + 90 terminal rest; Round 2: 60 active + 90 active = 390s -> 7m
        assertEquals(7, minutes)
    }
}
