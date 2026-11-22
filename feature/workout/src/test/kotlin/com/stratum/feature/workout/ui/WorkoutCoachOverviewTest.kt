package com.stratum.feature.workout.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class WorkoutCoachOverviewTest {
    @Test
    @DisplayName("remaining estimate counts work for every set left and rests except after the last")
    fun estimateRemaining() {
        val lifts =
            listOf(
                lift(
                    "a",
                    set(isComplete = true, restSeconds = 90),
                    set(isComplete = false, restSeconds = 90),
                ),
                lift(
                    "b",
                    set(isComplete = false, restSeconds = 120),
                    set(isComplete = false, restSeconds = 60),
                ),
            )

        // 3 sets of work (3 * 40) + rests after the first two of them (90 + 120).
        assertEquals(3 * ESTIMATED_SET_SECONDS + 90 + 120, estimateRemainingSeconds(lifts))
    }

    @Test
    @DisplayName("remaining estimate is zero when everything is logged")
    fun estimateRemainingZero() {
        val lifts = listOf(lift("a", set(isComplete = true, restSeconds = 90)))
        assertEquals(0, estimateRemainingSeconds(lifts))
    }

    @Test
    @DisplayName("session volume sums logged weight times reps")
    fun volumeSumsLoggedSets() {
        val lifts =
            listOf(
                lift(
                    "a",
                    set(isComplete = true, loggedWeightAmount = 20.0, loggedReps = 10),
                    set(isComplete = true, loggedWeightAmount = 20.0, loggedReps = 8),
                    set(isComplete = false, loggedWeightAmount = null, loggedReps = null),
                ),
            )

        assertEquals(360.0, sessionVolume(lifts), 0.001)
    }

    @Test
    @DisplayName("up next starts at the primary lift and walks forward")
    fun upNextWalksForward() {
        val done = set(isComplete = true)
        val open1 = set(isComplete = false)
        val open2 = set(isComplete = false)
        val lifts =
            listOf(
                lift("a", open1),
                lift("b", done, open2),
                lift("c", set(isComplete = false)),
            )

        val next = upNextSets(lifts, primaryLiftId = "b")

        assertEquals(listOf("b" to open2), next.take(1).map { (lift, set) -> lift.workoutLiftId to set })
        assertEquals(2, next.size)
        assertEquals("c", next[1].first.workoutLiftId)
    }

    private fun lift(
        id: String,
        vararg sets: RenderedSetUi,
    ): RenderedLiftUi =
        RenderedLiftUi(
            workoutLiftId = id,
            liftId = "L-$id",
            liftName = "Lift $id",
            sets = sets.toList(),
        )

    private fun set(
        isComplete: Boolean,
        restSeconds: Int = 0,
        loggedWeightAmount: Double? = null,
        loggedReps: Int? = null,
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
            loggedWeightAmount = loggedWeightAmount,
            loggedReps = loggedReps,
            isComplete = isComplete,
            rirGoal = 2,
            repMin = 10,
            repMax = 12,
            recommendedWeightAmount = 20.0,
            recommendedReps = 10,
            restSeconds = restSeconds,
        )
}
