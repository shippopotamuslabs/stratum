package com.stratum.feature.workout.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class WorkoutCoachBestsTest {
    @Test
    @DisplayName("bests come from the previous session's sets")
    fun bestsFromPreviousSets() {
        val bests =
            coachLiftBests(
                listOf(
                    set(previousWeightAmount = 17.5, previousReps = 15),
                    set(previousWeightAmount = 17.5, previousReps = 12),
                    set(previousWeightAmount = 15.0, previousReps = 11),
                ),
            )

        assertEquals(17.5, bests.topSetWeight)
        assertEquals(38, bests.totalReps)
        // Epley on the best set: 17.5 * (1 + 15/30) = 26.25
        assertEquals(26.25, bests.estimatedOneRepMax!!, 0.001)
    }

    @Test
    @DisplayName("sets without history do not count")
    fun noHistoryNoBests() {
        val bests =
            coachLiftBests(
                listOf(
                    set(previousWeightAmount = null, previousReps = null),
                    set(previousWeightAmount = 20.0, previousReps = null),
                ),
            )

        assertNull(bests.topSetWeight)
        assertEquals(0, bests.totalReps)
        assertNull(bests.estimatedOneRepMax)
    }

    @Test
    @DisplayName("bodyweight sets count reps with no weighted bests")
    fun bodyweightCountsRepsOnly() {
        val bests =
            coachLiftBests(
                listOf(
                    set(previousWeightAmount = null, previousReps = 12),
                    set(previousWeightAmount = null, previousReps = 10),
                ),
            )

        assertNull(bests.topSetWeight)
        assertEquals(22, bests.totalReps)
        assertNull(bests.estimatedOneRepMax)
    }

    private fun set(
        previousWeightAmount: Double?,
        previousReps: Int?,
    ): RenderedSetUi =
        RenderedSetUi(
            slotKey = "s",
            positionLabel = "1",
            setTypeLabel = "S",
            repRangeLabel = "10–12",
            rirLabel = "RIR 2",
            recommendedWeightLabel = "20",
            recommendedRepsLabel = "10",
            loggedWeightLabel = null,
            loggedRepsLabel = null,
            isComplete = false,
            rirGoal = 2,
            repMin = 10,
            repMax = 12,
            recommendedWeightAmount = 20.0,
            recommendedReps = 10,
            previousWeightAmount = previousWeightAmount,
            previousReps = previousReps,
        )
}
