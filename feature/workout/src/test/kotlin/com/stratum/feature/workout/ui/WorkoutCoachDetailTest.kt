package com.stratum.feature.workout.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** Up-next detail parts and the recommendation heading suffix (STR-325). */
class WorkoutCoachDetailTest {
    private fun set(
        recommendedWeightLabel: String = "",
        recommendedRepsLabel: String = "",
        repRangeLabel: String = "",
        recommendedReps: Int = 0,
        recommendedWeightAmount: Double = 0.0,
        repMin: Int = 0,
        repMax: Int = 0,
        showConcreteReps: Boolean = false,
        rirGoal: Int = 2,
    ): RenderedSetUi =
        RenderedSetUi(
            slotKey = "s",
            positionLabel = "1",
            setTypeLabel = "S",
            repRangeLabel = repRangeLabel,
            rirLabel = "",
            recommendedWeightLabel = recommendedWeightLabel,
            recommendedRepsLabel = recommendedRepsLabel,
            loggedWeightLabel = null,
            loggedRepsLabel = null,
            isComplete = false,
            rirGoal = rirGoal,
            repMin = repMin,
            repMax = repMax,
            recommendedWeightAmount = recommendedWeightAmount,
            recommendedReps = recommendedReps,
            showConcreteReps = showConcreteReps,
        )

    @Test
    fun upNextUsesConcreteRecommendation() {
        val detail =
            upNextDetail(
                set(
                    recommendedWeightLabel = "185 lb",
                    recommendedRepsLabel = "5",
                    repRangeLabel = "5",
                    showConcreteReps = true,
                ),
            )
        assertEquals("185 lb", detail.weightLabel)
        assertEquals("5", detail.repsLabel)
        assertEquals(2, detail.rirGoal)
    }

    @Test
    fun upNextColdStartBaselineIsNotConcrete() {
        // The snapshot baseline carries repMin as the rep count with no live
        // recommendation, no prior performance and no projection: the mapper
        // gates it out, and the card shows the range like the set table does.
        val detail =
            upNextDetail(
                set(
                    recommendedRepsLabel = "8-12",
                    repRangeLabel = "8-12",
                    recommendedReps = 8,
                    repMin = 8,
                    repMax = 12,
                    showConcreteReps = false,
                ),
            )
        assertNull(detail.weightLabel)
        assertEquals("8-12", detail.repsLabel)
    }

    @Test
    fun upNextBodyweightConcreteReps() {
        val detail =
            upNextDetail(
                set(recommendedRepsLabel = "10", repRangeLabel = "8-12", showConcreteReps = true),
            )
        assertNull(detail.weightLabel)
        assertEquals("10", detail.repsLabel)
    }

    @Test
    fun recommendationSuffixShowsPositiveDelta() {
        val suffix =
            recommendationHeadingSuffix(
                set(recommendedWeightLabel = "187.5 lb", recommendedWeightAmount = 187.5),
                topSetWeight = 185.0,
                unit = "lb",
            )
        assertEquals("+2.5 lb", suffix)
    }

    @Test
    fun recommendationSuffixShowsNegativeDelta() {
        val suffix =
            recommendationHeadingSuffix(
                set(recommendedWeightLabel = "180 lb", recommendedWeightAmount = 180.0),
                topSetWeight = 185.0,
                unit = "lb",
            )
        assertEquals("-5 lb", suffix)
    }

    @Test
    fun recommendationSuffixSubPrecisionDeltaFallsBackToWeight() {
        val suffix =
            recommendationHeadingSuffix(
                set(recommendedWeightLabel = "185 lb", recommendedWeightAmount = 185.04),
                topSetWeight = 185.0,
                unit = "lb",
            )
        assertEquals("185 lb", suffix)
    }

    @Test
    fun recommendationSuffixZeroDeltaFallsBackToWeight() {
        val suffix =
            recommendationHeadingSuffix(
                set(recommendedWeightLabel = "185 lb", recommendedWeightAmount = 185.0),
                topSetWeight = 185.0,
                unit = "lb",
            )
        assertEquals("185 lb", suffix)
    }

    @Test
    fun recommendationSuffixNoHistoryFallsBackToWeight() {
        val suffix =
            recommendationHeadingSuffix(
                set(recommendedWeightLabel = "185 lb", recommendedWeightAmount = 185.0),
                topSetWeight = null,
                unit = "lb",
            )
        assertEquals("185 lb", suffix)
    }

    @Test
    fun volumeLabelSpellsOutZero() {
        assertEquals("0 lb", volumeLabel(0.0, "lb"))
        assertEquals("925 lb", volumeLabel(925.0, "lb"))
    }
}
