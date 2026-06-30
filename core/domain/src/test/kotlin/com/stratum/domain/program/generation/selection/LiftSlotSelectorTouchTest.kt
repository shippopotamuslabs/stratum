package com.stratum.domain.program.generation.selection

import com.stratum.domain.program.generation.context.LiftContext
import com.stratum.domain.program.generation.context.ProgramGenerationContext
import com.stratum.domain.program.generation.reference.RpRepProfile
import com.stratum.domain.program.generation.split.SlotRole
import com.stratum.domain.program.generation.split.WorkoutSlotTemplate
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
                pattern = "ElbowFlexion",
                primaryMuscle = "Bicep",
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
                    movementPattern = "VerticalPull",
                    movementPatterns = listOf("VerticalPull", "ElbowFlexion"),
                    primaryMuscles = listOf("Back"),
                    secondaryMuscles = listOf("Bicep"),
                    proven = true,
                    mainstream = true,
                ),
                LiftContext(
                    id = "hammer-curl",
                    name = "Hammer Curl",
                    movementPattern = "ElbowFlexion",
                    movementPatterns = listOf("ElbowFlexion"),
                    primaryMuscles = listOf("Bicep"),
                    secondaryMuscles = emptyList(),
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
            specialization = mapOf("Bicep" to "Specialize"),
            liftsById = liftsById,
            volumeLandmarksByMuscle = emptyMap(),
            volumeResponsesByMuscle = emptyMap(),
            enabledLiftIds = liftsById.keys,
            movementPatternByLiftId = liftsById.mapValues { (_, lift) -> lift.movementPattern },
            liftEffectivenessByLiftId = emptyMap(),
        )
    }
}
