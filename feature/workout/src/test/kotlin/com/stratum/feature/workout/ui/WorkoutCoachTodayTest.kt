package com.stratum.feature.workout.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * STR-328: Coach fills in from today's completed sets when a lift has no
 * history, and the recommendation panel speaks for the next set.
 */
class WorkoutCoachTodayTest {
    @Test
    @DisplayName("today's bests come from completed sets only")
    fun todayBestsFromCompletedSets() {
        val bests =
            coachTodayBests(
                listOf(
                    set(isComplete = true, loggedWeightAmount = 50.0, loggedReps = 8),
                    set(isComplete = true, loggedWeightAmount = 50.0, loggedReps = 6),
                    set(isComplete = false, loggedWeightAmount = null, loggedReps = null),
                ),
            )

        assertEquals(50.0, bests.topSetWeight)
        assertEquals(14, bests.totalReps)
        // Epley on the best set: 50 * (1 + 8/30) = 63.33
        assertEquals(63.33, bests.estimatedOneRepMax!!, 0.01)
        assertTrue(bests.hasData())
    }

    @Test
    @DisplayName("nothing logged yet means no today data")
    fun todayBestsEmptyBeforeAnySet() {
        val bests =
            coachTodayBests(
                listOf(set(isComplete = false, loggedWeightAmount = null, loggedReps = null)),
            )

        assertNull(bests.topSetWeight)
        assertEquals(0, bests.totalReps)
        assertFalse(bests.hasData())
    }

    @Test
    @DisplayName("the recommendation set is the first incomplete set with a recommendation")
    fun recommendationTargetsNextSet() {
        val lift =
            lift(
                listOf(
                    set(isComplete = true, loggedWeightAmount = 50.0, loggedReps = 8, recommendedWeightLabel = "50"),
                    set(isComplete = false, loggedWeightAmount = null, loggedReps = null, recommendedWeightLabel = "50"),
                    set(isComplete = false, loggedWeightAmount = null, loggedReps = null, recommendedWeightLabel = ""),
                ),
            )

        assertEquals("2", coachRecommendationSet(lift)?.positionLabel)
    }

    @Test
    @DisplayName("no recommendation panel when every set is complete")
    fun recommendationSetNullWhenAllComplete() {
        val lift =
            lift(
                listOf(
                    set(isComplete = true, loggedWeightAmount = 50.0, loggedReps = 8, recommendedWeightLabel = "50"),
                ),
            )

        assertNull(coachRecommendationSet(lift))
    }

    private fun lift(sets: List<RenderedSetUi>): RenderedLiftUi =
        RenderedLiftUi(
            workoutLiftId = "wl",
            liftId = "lift",
            liftName = "Assisted Dip",
            sets = sets,
        )

    private fun set(
        isComplete: Boolean,
        loggedWeightAmount: Double?,
        loggedReps: Int?,
        recommendedWeightLabel: String = "50",
    ): RenderedSetUi =
        RenderedSetUi(
            slotKey = "s",
            positionLabel = (positionCounter++).toString(),
            setTypeLabel = "S",
            repRangeLabel = "8–12",
            rirLabel = "RIR 2",
            recommendedWeightLabel = recommendedWeightLabel,
            recommendedRepsLabel = "8",
            loggedWeightLabel = null,
            loggedRepsLabel = null,
            isComplete = isComplete,
            rirGoal = 2,
            repMin = 8,
            repMax = 12,
            recommendedWeightAmount = 50.0,
            recommendedReps = 8,
            loggedWeightAmount = loggedWeightAmount,
            loggedReps = loggedReps,
        )

    private var positionCounter = 1
}
