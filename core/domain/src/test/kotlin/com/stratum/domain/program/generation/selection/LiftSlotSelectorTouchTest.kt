package com.stratum.domain.program.generation.selection

import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.onboarding.SpecializationLevel
import com.stratum.domain.program.generation.context.LiftContext
import com.stratum.domain.program.generation.context.ProgramGenerationContext
import com.stratum.domain.program.generation.reference.RpRepProfile
import com.stratum.domain.program.generation.slots.SlotRole
import com.stratum.domain.program.generation.slots.WorkoutSlotTemplate
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LiftSlotSelectorTouchTest {

    @Test
    fun crossDomainBicepTouchDoesNotSelectChinUp() {
        val context = contextWithChinUpAndCurls()
        val touchSlot =
            WorkoutSlotTemplate(
                slotId = "touch-bicep-lower",
                role = SlotRole.Isolation,
                pattern = MovementPattern.ElbowFlexion,
                primaryMuscle = MuscleGroup.Bicep,
                repProfile = RpRepProfile.Isolation,
                isCrossDomainTouch = true,
            )
        val candidates = candidatesForSlot(touchSlot, context)
        assertFalse(candidates.any { it.id == "chin-up" })
        assertTrue(candidates.any { it.id == "hammer-curl" })
    }

    private fun contextWithChinUpAndCurls(): ProgramGenerationContext {
        val lifts =
            listOf(
                LiftContext(
                    id = "chin-up",
                    name = "Chin-Up",
                    movementPattern = MovementPattern.VerticalPull,
                    movementPatterns = setOf(MovementPattern.VerticalPull, MovementPattern.ElbowFlexion),
                    primaryMuscles = setOf(MuscleGroup.Back),
                    secondaryMuscles = setOf(MuscleGroup.Bicep),
                    proven = true,
                    mainstream = true,
                ),
                LiftContext(
                    id = "hammer-curl",
                    name = "Hammer Curl",
                    movementPattern = MovementPattern.ElbowFlexion,
                    movementPatterns = setOf(MovementPattern.ElbowFlexion),
                    primaryMuscles = setOf(MuscleGroup.Bicep),
                    secondaryMuscles = emptySet(),
                    proven = true,
                ),
            )
        val liftsById = lifts.associateBy { it.id }
        return ProgramGenerationContext(
            trainingAge = "Advanced",
            lastTrained = null,
            sessionLengthMinutes = 75,
            trainingDaysPerWeek = 5,
            weekdays = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday"),
            specialization = mapOf(MuscleGroup.Bicep to SpecializationLevel.Specialize),
            liftsById = liftsById,
            muscleVolumeSnapshotsByMuscle = emptyMap(),
            volumeCorrelationsByMuscle = emptyMap(),
            enabledLiftIds = liftsById.keys,
            movementPatternByLiftId = liftsById.mapValues { (_, lift) -> lift.movementPattern },
            liftEffectivenessByLiftId = emptyMap(),
        )
    }
}
