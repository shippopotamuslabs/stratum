package com.stratum.application.program

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import com.stratum.domain.program.DurationSetInput
import com.stratum.domain.program.WorkoutLiftDurationInput

class EstimateWorkoutDurationMinutesUseCaseTest {
    private val subject = EstimateWorkoutDurationMinutesUseCase()

    @Test
    @DisplayName("Solo lift: setup + reps × 5s; rest only between sets")
    fun estimateDuration_soloUsesRepsAndBetweenSetRest() {
        val minutes =
            subject(
                workoutLifts =
                    listOf(
                        lift(
                            sets(rest = 120, repMin = 10, repMax = 10, count = 2),
                        ),
                    ),
            )

        // 2 × (15 + 50) + 120 = 250s -> 5 minutes
        assertEquals(5, minutes)
    }

    @Test
    @DisplayName("estimateLiftSeconds sums setup, rep work, and between-set rest")
    fun estimateLiftSeconds_betweenSetRestOnly() {
        val seconds =
            estimateLiftSeconds(
                lift(sets(rest = 90, repMin = 10, repMax = 10, count = 3)),
            )

        // 3 × (15+50) + 2 × 90 = 375
        assertEquals(375, seconds)
    }

    @Test
    @DisplayName("Empty set list yields zero duration")
    fun emptySets_zeroDuration() {
        assertEquals(0, subject(listOf(lift(emptyList()))))
        assertEquals(0, subject(emptyList()))
    }

    @Test
    @DisplayName("Configured rest of 0 is honored (not rewritten to 120)")
    fun zeroRest_honored() {
        val seconds =
            estimateLiftSeconds(
                lift(sets(rest = 0, repMin = 10, repMax = 10, count = 2)),
            )

        // 2 × (15+50) + 0 rest = 130
        assertEquals(130, seconds)
    }

    @Test
    @DisplayName("Negative rest is clamped to 0")
    fun negativeRest_clampedToZero() {
        val seconds =
            estimateLiftSeconds(
                lift(
                    listOf(
                        DurationSetInput(restSeconds = -10, repMin = 10, repMax = 10),
                        DurationSetInput(restSeconds = -10, repMin = 10, repMax = 10),
                    ),
                ),
            )

        assertEquals(130, seconds)
    }

    @Test
    @DisplayName("Missing positive rep band falls back to default 10 reps")
    fun missingReps_defaultTen() {
        val seconds =
            estimateLiftSeconds(
                lift(
                    listOf(
                        DurationSetInput(restSeconds = 120),
                        DurationSetInput(restSeconds = 120),
                    ),
                ),
            )

        // 2 × (15+50) + 120 = 250
        assertEquals(250, seconds)
    }

    @Test
    @DisplayName("Double 0/0 back-off inherits midpoint from first positive band")
    fun doubleBackOff_inheritsTopSetBand() {
        val lift =
            lift(
                listOf(
                    DurationSetInput(restSeconds = 120, repMin = 8, repMax = 12),
                    DurationSetInput(restSeconds = 120, repMin = 0, repMax = 0),
                ),
            )

        assertEquals(10, estimatedRepsForSet(lift, 0))
        assertEquals(10, estimatedRepsForSet(lift, 1))
    }

    @Test
    @DisplayName("midpointRepsOrNull returns null for 0/0 and midpoint otherwise")
    fun midpointReps_policy() {
        assertNull(midpointRepsOrNull(0, 0))
        assertEquals(10, midpointRepsOrNull(8, 12))
        assertEquals(5, midpointRepsOrNull(5, 0))
        assertEquals(8, midpointRepsOrNull(0, 8))
    }

    @Test
    @DisplayName("Superset with rest timer off uses 30s between lifts and terminal rest between rounds")
    fun supersetInterLiftDefault30s() {
        val minutes =
            subject(
                listOf(
                    lift(
                        sets(rest = 60, repMin = 10, repMax = 10, count = 2),
                        position = 0,
                        supersetGroupId = "sg-1",
                        useRestTimerBetweenLifts = false,
                    ),
                    lift(
                        sets(rest = 90, repMin = 10, repMax = 10, count = 2),
                        position = 1,
                        supersetGroupId = "sg-1",
                    ),
                ),
            )

        // Round 1: (15+50)+30+(15+50)+90 = 250
        // Round 2: (15+50)+30+(15+50) = 160
        // = 410s -> 7 minutes
        assertEquals(7, minutes)
    }

    @Test
    @DisplayName("Superset with rest timer on uses configured rest between lifts")
    fun supersetInterLiftUsesConfiguredRestWhenEnabled() {
        val without =
            estimateWorkoutSeconds(
                listOf(
                    lift(
                        sets(rest = 120, repMin = 10, repMax = 10, count = 1),
                        position = 0,
                        supersetGroupId = "sg-1",
                        useRestTimerBetweenLifts = false,
                    ),
                    lift(
                        sets(rest = 90, repMin = 10, repMax = 10, count = 1),
                        position = 1,
                        supersetGroupId = "sg-1",
                    ),
                ),
            )
        val withTimer =
            estimateWorkoutSeconds(
                listOf(
                    lift(
                        sets(rest = 120, repMin = 10, repMax = 10, count = 1),
                        position = 0,
                        supersetGroupId = "sg-1",
                        useRestTimerBetweenLifts = true,
                    ),
                    lift(
                        sets(rest = 90, repMin = 10, repMax = 10, count = 1),
                        position = 1,
                        supersetGroupId = "sg-1",
                    ),
                ),
            )

        // without: (15+50)+30+(15+50) = 160
        // with:    (15+50)+120+(15+50) = 250
        assertEquals(160, without)
        assertEquals(250, withTimer)
        assertTrue(withTimer > without)
    }

    @Test
    @DisplayName("Superset block is shorter than the same lifts sequenced solo when inter-lift timer is off")
    fun supersetShorterThanSequential() {
        val solo =
            subject(
                listOf(
                    lift(sets(rest = 120, repMin = 10, repMax = 10, count = 3), position = 0),
                    lift(sets(rest = 120, repMin = 10, repMax = 10, count = 3), position = 1),
                ),
            )
        val superset =
            subject(
                listOf(
                    lift(
                        sets(rest = 120, repMin = 10, repMax = 10, count = 3),
                        position = 0,
                        supersetGroupId = "sg-1",
                    ),
                    lift(
                        sets(rest = 120, repMin = 10, repMax = 10, count = 3),
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
                    lift(
                        sets(rest = 120, repMin = 10, repMax = 10, count = 2),
                        position = 0,
                        supersetGroupId = "sg-lonely",
                    ),
                ),
            )

        assertEquals(5, minutes)
    }

    @Test
    @DisplayName("Uneven set counts skip missing rounds for shorter lift")
    fun unevenSetCounts_skipMissing() {
        val seconds =
            estimateWorkoutSeconds(
                listOf(
                    lift(
                        sets(rest = 120, repMin = 10, repMax = 10, count = 2),
                        position = 0,
                        supersetGroupId = "sg-1",
                    ),
                    lift(
                        listOf(DurationSetInput(restSeconds = 90, repMin = 8, repMax = 8)),
                        position = 1,
                        supersetGroupId = "sg-1",
                    ),
                ),
            )

        // Round 0: A(65)+30 + B(15+40=55)+90 = 240
        // Round 1: A(65) only, last round, no rest = 65
        // = 305
        assertEquals(305, seconds)
    }

    @Test
    @DisplayName("Low-rep sets still include fixed setup overhead per set")
    fun lowRep_includesSetupOverhead() {
        val seconds =
            estimateLiftSeconds(
                lift(
                    listOf(DurationSetInput(restSeconds = 180, repMin = 1, repMax = 1)),
                ),
            )

        // 15 setup + 5 rep work = 20 (not only 5)
        assertEquals(20, seconds)
    }

    private fun lift(
        sets: List<DurationSetInput>,
        position: Int = 0,
        supersetGroupId: String? = null,
        useRestTimerBetweenLifts: Boolean = false,
    ): WorkoutLiftDurationInput =
        WorkoutLiftDurationInput(
            sets = sets,
            position = position,
            supersetGroupId = supersetGroupId,
            useRestTimerBetweenLifts = useRestTimerBetweenLifts,
        )

    private fun sets(
        rest: Int,
        repMin: Int,
        repMax: Int,
        count: Int,
    ): List<DurationSetInput> =
        List(count) {
            DurationSetInput(restSeconds = rest, repMin = repMin, repMax = repMax)
        }
}
