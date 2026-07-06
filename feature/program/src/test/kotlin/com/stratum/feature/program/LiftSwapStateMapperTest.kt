package com.stratum.feature.program

import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.ScoredLift
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.feature.program.ui.PLACEHOLDER_LIFT_NAME
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class LiftSwapStateMapperTest {

    @Test
    @DisplayName("Given PLACEHOLDER_LIFT_NAME target when buildLiftSwapSheetState then isAddMode is true")
    fun placeholderLiftTargetNameSetsAddModeToTrue() {
        val state = buildLiftSwapSheetState(
            targetLiftName = PLACEHOLDER_LIFT_NAME,
            ranked = listOf(
                ScoredLift(catalogLift(LiftId("squat"), "Squat"), 0.0)
            ),
            filters = LiftSwapFilters()
        )

        assertTrue(state.isAddMode)
    }

    @Test
    @DisplayName("Given regular lift target when buildLiftSwapSheetState then isAddMode is false")
    fun regularLiftTargetNameSetsAddModeToFalse() {
        val state = buildLiftSwapSheetState(
            targetLiftName = "Bench Press",
            ranked = listOf(
                ScoredLift(catalogLift(LiftId("squat"), "Squat"), 0.0)
            ),
            filters = LiftSwapFilters()
        )

        assertFalse(state.isAddMode)
    }

    private fun catalogLift(id: LiftId, name: String): Lift =
        Lift(
            id = id,
            name = name,
            movementPatterns = setOf(MovementPattern.HorizontalPress),
            primaryMuscles = setOf(MuscleGroup.Chest),
            secondaryMuscles = emptySet(),
            allowWeightChangeBetweenSets = false,
            incrementTiers = emptyList(),
            presetTags = emptySet(),
            enabled = true,
        )
}
