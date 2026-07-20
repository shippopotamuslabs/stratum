package com.stratum.domain.program

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class EstimateWorkoutDurationMinutesUseCaseTest {
    private val subject = EstimateWorkoutDurationMinutesUseCase()

    @Test
    @DisplayName("Solo lift: active work is reps × 5s; rest only between sets")
    fun estimateDuration_soloUsesRepsAndBetweenSetRest() {
        val minutes =
            subject(
                workoutLifts =
                    listOf(
                        WorkoutLiftDurationInput(
                            restSecondsBySet = listOf(120, 120),
                            repsBySet = listOf(10, 10),
                        ),
                    ),
            )

        // 50s work + 120s rest + 50s work = 220s -> 4 minutes
        assertEquals(4, minutes)
    }

    @Test
    @DisplayName("estimateLiftSeconds sums rep work and between-set rest")
    fun estimateLiftSeconds_betweenSetRestOnly() {
        val seconds =
            estimateLiftSeconds(
                WorkoutLiftDurationInput(
                    restSecondsBySet = listOf(90, 90, 90),
                    repsBySet = listOf(10, 10, 10),
                ),
            )

        // 50 + 90 + 50 + 90 + 50
        assertEquals(330, seconds)
    }

    @Test
    @DisplayName("Empty set list yields zero duration")
    fun emptySets_zeroDuration() {
        assertEquals(0, subject(listOf(WorkoutLiftDurationInput(restSecondsBySet = emptyList()))))
        assertEquals(0, subject(emptyList()))
    }

    @Test
    @DisplayName("Non-positive rest falls back to 120s between sets")
    fun nonPositiveRest_usesDefaultBetweenSets() {
        val seconds =
            estimateLiftSeconds(
                WorkoutLiftDurationInput(
                    restSecondsBySet = listOf(0, 0),
                    repsBySet = listOf(10, 10),
                ),
            )

        // 50 + 120 + 50
        assertEquals(220, seconds)
    }

    @Test
    @DisplayName("Missing repsBySet falls back to default 10 reps per set")
    fun missingReps_defaultTen() {
        val seconds =
            estimateLiftSeconds(
                WorkoutLiftDurationInput(restSecondsBySet = listOf(120, 120)),
            )

        // 50 + 120 + 50
        assertEquals(220, seconds)
    }

    @Test
    @DisplayName("Superset uses 30s between lifts and terminal rest between rounds only")
    fun supersetInterLiftAndRoundRest() {
        val minutes =
            subject(
                listOf(
                    WorkoutLiftDurationInput(
                        restSecondsBySet = listOf(60, 60),
                        repsBySet = listOf(10, 10),
                        position = 0,
                        supersetGroupId = "sg-1",
                    ),
                    WorkoutLiftDurationInput(
                        restSecondsBySet = listOf(90, 90),
                        repsBySet = listOf(10, 10),
                        position = 1,
                        supersetGroupId = "sg-1",
                    ),
                ),
            )

        // Round 1: 50 + 30 + 50 + 90 terminal rest
        // Round 2: 50 + 30 + 50 (no final rest)
        // = 350s -> 6 minutes
        assertEquals(6, minutes)
    }

    @Test
    @DisplayName("Superset block is shorter than the same lifts sequenced solo")
    fun supersetShorterThanSequential() {
        val solo =
            subject(
                listOf(
                    WorkoutLiftDurationInput(
                        restSecondsBySet = listOf(120, 120, 120),
                        repsBySet = listOf(10, 10, 10),
                        position = 0,
                    ),
                    WorkoutLiftDurationInput(
                        restSecondsBySet = listOf(120, 120, 120),
                        repsBySet = listOf(10, 10, 10),
                        position = 1,
                    ),
                ),
            )
        val superset =
            subject(
                listOf(
                    WorkoutLiftDurationInput(
                        restSecondsBySet = listOf(120, 120, 120),
                        repsBySet = listOf(10, 10, 10),
                        position = 0,
                        supersetGroupId = "sg-1",
                    ),
                    WorkoutLiftDurationInput(
                        restSecondsBySet = listOf(120, 120, 120),
                        repsBySet = listOf(10, 10, 10),
                        position = 1,
                        supersetGroupId = "sg-1",
                    ),
                ),
            )

        assertTrue(superset < solo)
    }

    @Test
    @DisplayName("Single-member superset group behaves as solo lift")
    fun singleMemberGroup_behavesAsSolo() {
        val minutes =
            subject(
                listOf(
                    WorkoutLiftDurationInput(
                        restSecondsBySet = listOf(120, 120),
                        repsBySet = listOf(10, 10),
                        position = 0,
                        supersetGroupId = "sg-lonely",
                    ),
                ),
            )

        assertEquals(4, minutes)
    }

    @Test
    @DisplayName("Uneven set counts skip missing rounds for shorter lift")
    fun unevenSetCounts_skipMissing() {
        val seconds =
            estimateWorkoutSeconds(
                listOf(
                    WorkoutLiftDurationInput(
                        restSecondsBySet = listOf(120, 120),
                        repsBySet = listOf(10, 10),
                        position = 0,
                        supersetGroupId = "sg-1",
                    ),
                    WorkoutLiftDurationInput(
                        restSecondsBySet = listOf(90),
                        repsBySet = listOf(8),
                        position = 1,
                        supersetGroupId = "sg-1",
                    ),
                ),
            )

        // Round 0: A(50)+30 + B(40)+90 terminal = 210
        // Round 1: A(50) only (B missing); A is sole participant so no inter-lift; last round -> no rest
        // = 260
        assertEquals(260, seconds)
    }
}
