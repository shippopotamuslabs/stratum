package com.stratum.feature.workout.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/** STR-327: Overview row status from the lift's completed sets. */
class WorkoutCoachOverviewStatusTest {
    @Test
    @DisplayName("no completed sets is planned")
    fun plannedWhenNothingLogged() {
        assertEquals(OverviewLiftStatus.Planned, overviewLiftStatus(lift(complete = 0, total = 3)))
    }

    @Test
    @DisplayName("some completed sets is in progress")
    fun inProgressWhenPartiallyLogged() {
        assertEquals(OverviewLiftStatus.InProgress, overviewLiftStatus(lift(complete = 1, total = 3)))
    }

    @Test
    @DisplayName("all sets completed is complete")
    fun completeWhenAllLogged() {
        assertEquals(OverviewLiftStatus.Complete, overviewLiftStatus(lift(complete = 3, total = 3)))
    }

    @Test
    @DisplayName("a lift with no sets is planned, never complete")
    fun emptyLiftIsPlanned() {
        assertEquals(OverviewLiftStatus.Planned, overviewLiftStatus(lift(complete = 0, total = 0)))
    }

    private fun lift(
        complete: Int,
        total: Int,
    ): RenderedLiftUi =
        RenderedLiftUi(
            workoutLiftId = "wl",
            liftId = "lift",
            liftName = "Lift",
            sets =
                (0 until total).map { index ->
                    RenderedSetUi(
                        slotKey = "s$index",
                        positionLabel = "${index + 1}",
                        setTypeLabel = "S",
                        repRangeLabel = "8–12",
                        rirLabel = "RIR 2",
                        recommendedWeightLabel = "",
                        recommendedRepsLabel = "",
                        loggedWeightLabel = null,
                        loggedRepsLabel = null,
                        isComplete = index < complete,
                        rirGoal = 2,
                        repMin = 8,
                        repMax = 12,
                        recommendedWeightAmount = 0.0,
                        recommendedReps = 0,
                    )
                },
        )
}
