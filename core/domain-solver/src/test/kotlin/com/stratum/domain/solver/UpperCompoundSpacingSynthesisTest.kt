@file:Suppress("FunctionNaming", "MagicNumber")

package com.stratum.domain.solver

import com.stratum.core.testing.program.parseScenarioContext
import com.stratum.core.testing.program.scenarioPayload
import com.stratum.domain.program.generation.context.ProgramGenerationContext
import com.stratum.domain.program.generation.prescription.SessionSlotPlan
import com.stratum.domain.program.generation.reference.MUSCLE_RECOVERY_MIN_SCHEDULING_DOSE
import com.stratum.domain.program.generation.reference.RpRepProfile
import com.stratum.domain.program.generation.reference.calendarGapBetweenDays
import com.stratum.domain.program.generation.reference.computePatternSecondaryCredits
import com.stratum.domain.program.generation.synthesis.ProgramSynthesisResult
import com.stratum.domain.program.generation.volume.planWeeklyVolume
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Upper-compound muscles (bench/row/OHP) are not subject to the gap-2 heavy-axial hard forbid;
 * Mon/Wed chest work at moderate dose remains feasible.
 */
class UpperCompoundSpacingSynthesisTest {

    @Test
    fun benchTwiceWeeklyAllowsGapTwoChestExposure() = runBlocking {
        val context =
            parseScenarioContext(
                scenarioPayload(
                    days = 3,
                    specialization = mapOf("Chest" to "Specialize"),
                    weekdayLayout = "sequential",
                ),
            )
        val synthesizer = CpSatProgramSynthesizer()
        val synthesis = synthesizer.synthesize(planWeeklyVolume(context), context)

        assertTrue(synthesis.plans.isNotEmpty(), "Synthesis must produce a feasible program")
        val gapTwoChest =
            gapTwoMeaningfulExposure(
                synthesis = synthesis,
                context = context,
                muscle = "Chest",
                minDose = MUSCLE_RECOVERY_MIN_SCHEDULING_DOSE,
            )
        assertTrue(
            gapTwoChest.isNotEmpty(),
            "Upper-compound chest should be schedulable on gap-2 days (not heavy-axial forbidden): " +
                "weekdays=${synthesis.weekdayIndexByPosition}",
        )
        val heavyAxialOnly =
            gapTwoChest.all { (earlier, _) ->
                !planHasHeavyAxialPrimaryExposure(earlier, "Chest", context)
            }
        assertTrue(
            heavyAxialOnly,
            "Gap-2 chest pairs must come from upper-compound slots, not heavy-axial forbids: $gapTwoChest",
        )
    }

    private fun gapTwoMeaningfulExposure(
        synthesis: ProgramSynthesisResult,
        context: ProgramGenerationContext,
        muscle: String,
        minDose: Double,
    ): List<Pair<SessionSlotPlan, SessionSlotPlan>> {
        val coefficients = VolumeAllocationCoefficients(computePatternSecondaryCredits(context))
        val ordered =
            synthesis.plans.sortedBy { plan -> synthesis.weekdayIndexByPosition[plan.position] }
        val pairs = mutableListOf<Pair<SessionSlotPlan, SessionSlotPlan>>()
        for (earlier in ordered) {
            for (later in ordered) {
                if (earlier.position == later.position) continue
                val gap =
                    calendarGapBetweenDays(
                        synthesis.weekdayIndexByPosition[earlier.position],
                        synthesis.weekdayIndexByPosition[later.position],
                    )
                if (gap != 2) continue
                val earlierDose = planMuscleCredits(earlier, muscle, coefficients)
                val laterDose = planMuscleCredits(later, muscle, coefficients)
                if (earlierDose >= minDose && laterDose >= minDose) {
                    pairs += earlier to later
                }
            }
        }
        return pairs
    }

    private fun planHasHeavyAxialPrimaryExposure(
        plan: SessionSlotPlan,
        muscle: String,
        context: ProgramGenerationContext,
    ): Boolean {
        val coefficients = VolumeAllocationCoefficients(computePatternSecondaryCredits(context))
        return plan.filledSlots.any { slot ->
            slot.primaryMuscle == muscle &&
                slot.repProfile == RpRepProfile.LowerFreeWeightCompound &&
                coefficients.isHeavyAxialSlot(slot) &&
                slot.setCount > 0
        }
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
