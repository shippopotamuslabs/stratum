package com.stratum.feature.workout

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class WorkoutCoachPostureTest {
    @Test
    @DisplayName("flat fold expands at any width")
    fun flatFoldExpands() {
        val posture =
            decideWorkoutPosture(
                widthDp = 500,
                fold = snapshot(state = WorkoutFoldState.Flat),
            )
        assertEquals(WorkoutPosture.TwoPane(hinge = null), posture)
    }

    @Test
    @DisplayName("half-opened fold never expands")
    fun halfOpenedStaysSingle() {
        val posture =
            decideWorkoutPosture(
                widthDp = 1200,
                fold = snapshot(state = WorkoutFoldState.HalfOpened),
            )
        assertEquals(WorkoutPosture.SinglePane, posture)
    }

    @Test
    @DisplayName("no fold expands at the expanded width and stays single below it")
    fun widthDecidesWithoutFold() {
        assertEquals(
            WorkoutPosture.TwoPane(hinge = null),
            decideWorkoutPosture(widthDp = WORKOUT_EXPANDED_WIDTH_DP, fold = null),
        )
        assertEquals(
            WorkoutPosture.SinglePane,
            decideWorkoutPosture(widthDp = WORKOUT_EXPANDED_WIDTH_DP - 1, fold = null),
        )
    }

    @Test
    @DisplayName("a vertical hinge band becomes fractional gap bounds")
    fun verticalHingeGap() {
        val posture = workoutFlatHingeFixture()
        val hinge = (posture as WorkoutPosture.TwoPane).hinge
        assertEquals(0.48f, hinge?.start)
        assertEquals(0.52f, hinge?.end)
    }

    @Test
    @DisplayName("a horizontal hinge yields no gap")
    fun horizontalHingeHasNoGap() {
        val posture =
            decideWorkoutPosture(
                widthDp = 1000,
                fold =
                    snapshot(
                        state = WorkoutFoldState.Flat,
                        axis = WorkoutHingeAxis.Horizontal,
                        hingeLeftPx = 480,
                        hingeRightPx = 520,
                    ),
            )
        assertNull((posture as WorkoutPosture.TwoPane).hinge)
    }

    private fun snapshot(
        state: WorkoutFoldState,
        axis: WorkoutHingeAxis = WorkoutHingeAxis.Vertical,
        hingeLeftPx: Int = 0,
        hingeRightPx: Int = 0,
    ): WorkoutFoldSnapshot =
        WorkoutFoldSnapshot(
            state = state,
            axis = axis,
            hingeLeftPx = hingeLeftPx,
            hingeRightPx = hingeRightPx,
            windowWidthPx = 1000,
        )
}
