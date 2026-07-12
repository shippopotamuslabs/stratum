@file:Suppress("FunctionNaming", "MagicNumber")

package com.stratum.domain.solver

import com.stratum.core.testing.program.parseScenarioContext
import com.stratum.core.testing.program.scenarioPayload
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.program.generation.context.ProgramGenerationContext
import com.stratum.domain.program.generation.prescription.FilledSlot
import com.stratum.domain.program.generation.prescription.SessionSlotPlan
import com.stratum.domain.program.generation.reference.MUSCLE_RECOVERY_MIN_SCHEDULING_DOSE
import com.stratum.domain.program.generation.reference.MUSCLE_RECOVERY_MODERATE_DOSE
import com.stratum.domain.program.generation.reference.RpRepProfile
import com.stratum.domain.program.generation.reference.calendarGapBetweenDays
import com.stratum.domain.program.generation.reference.computePatternSecondaryCredits
import com.stratum.domain.program.generation.slots.SlotRole
import com.stratum.domain.program.generation.synthesis.ProgramSynthesisResult
import com.stratum.domain.program.generation.volume.planWeeklyVolume
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Heavy-axial hard spacing: moderate free-weight squat/hinge exposure forbids a gap-2 repeat.
 */
class HeavyAxialSpacingSynthesisTest {

    @Test
    fun moderateSquatDayForbidsGapTwoQuadRepeat() = runBlocking {
        val context =
            parseScenarioContext(
                scenarioPayload(
                    days = 4,
                    specialization = mapOf("Quad" to "Specialize"),
                    weekdayLayout = "sequential",
                ),
            )
        val synthesizer = CpSatProgramSynthesizer()
        val synthesis = synthesizer.synthesize(planWeeklyVolume(context), context)

        val violations =
            gapTwoHeavyAxialViolations(
                synthesis,
                context,
                "Quad",
                moderateThreshold = MUSCLE_RECOVERY_MODERATE_DOSE,
            )
        assertTrue(
            violations.isEmpty(),
            "Moderate heavy-axial quad work must not repeat on gap-2 days: $violations " +
                "weekdays=${synthesis.weekdayIndexByPosition}",
        )
    }

    @Test
    fun deadliftDayForbidsAdjacentHamstringMeaningfulWork() = runBlocking {
        val context =
            parseScenarioContext(
                scenarioPayload(
                    days = 4,
                    specialization = mapOf("Hamstring" to "Specialize"),
                    weekdayLayout = "sequential",
                ),
            )
        val synthesizer = CpSatProgramSynthesizer()
        val synthesis = synthesizer.synthesize(planWeeklyVolume(context), context)

        val adjacentViolations =
            recoveryGapViolationsFromPlans(
                plans = synthesis.plans,
                weekdayIndexByPosition = synthesis.weekdayIndexByPosition,
                context = context,
                muscleFilter = { muscle -> muscle == "Hamstring" },
                calendarGapFilter = 1,
            )
        assertTrue(
            adjacentViolations.isEmpty(),
            "Class-aware adjacent hamstring recovery must hold: $adjacentViolations",
        )
    }

    @Test
    fun heavyAxialExposureLiteralRequiresFreeWeightCompoundSlot() {
        val slot =
            FilledSlot(
                slotId = "s0_squat",
                role = SlotRole.Anchor,
                pattern = MovementPattern.SquatPattern,
                primaryMuscle = MuscleGroup.Quad,
                repProfile = RpRepProfile.LowerFreeWeightCompound,
                setCount = 4,
            )
        assertTrue(VolumeAllocationCoefficients.Default.isHeavyAxialSlot(slot))
        assertEquals(4.0, VolumeAllocationCoefficients.Default.coeff(slot, "Quad") * slot.setCount)
    }

    private fun gapTwoHeavyAxialViolations(
        synthesis: ProgramSynthesisResult,
        context: ProgramGenerationContext,
        muscle: String,
        moderateThreshold: Double,
    ): List<String> {
        val coefficients = VolumeAllocationCoefficients(computePatternSecondaryCredits(context))
        val ordered =
            synthesis.plans.sortedBy { plan -> synthesis.weekdayIndexByPosition[plan.position] }
        val issues = mutableListOf<String>()
        for (earlier in ordered) {
            for (later in ordered) {
                if (earlier.position == later.position) continue
                val earlierDay = synthesis.weekdayIndexByPosition[earlier.position]
                val laterDay = synthesis.weekdayIndexByPosition[later.position]
                val gap = calendarGapBetweenDays(earlierDay, laterDay)
                if (gap != 2) continue
                val earlierDose = planMuscleCredits(earlier, muscle, coefficients)
                val laterDose = planMuscleCredits(later, muscle, coefficients)
                if (
                    planHasHeavyAxialPrimaryExposure(earlier, muscle, coefficients) &&
                    earlierDose >= moderateThreshold &&
                    laterDose >= MUSCLE_RECOVERY_MIN_SCHEDULING_DOSE
                ) {
                    issues +=
                        "p${earlier.position}(d=$earlierDay)→p${later.position}(d=$laterDay): " +
                        "$muscle ($earlierDose, $laterDose)"
                }
            }
        }
        return issues
    }

    private fun planHasHeavyAxialPrimaryExposure(
        plan: SessionSlotPlan,
        muscle: String,
        coefficients: VolumeAllocationCoefficients,
    ): Boolean =
        plan.filledSlots.any { slot ->
            coefficients.isHeavyAxialSlot(slot) &&
                slot.primaryMuscle.name == muscle &&
                slot.setCount > 0 &&
                slot.setCount * coefficients.coeff(slot, muscle) >= 1.0
        }

    private fun planMuscleCredits(
        plan: SessionSlotPlan,
        muscle: String,
        coefficients: VolumeAllocationCoefficients,
    ): Double {
        var total = 0.0
        for (slot in plan.filledSlots) {
            if (slot.setCount <= 0) continue
            total += slot.setCount * coefficients.coeff(slot, muscle)
        }
        return total
    }
}
