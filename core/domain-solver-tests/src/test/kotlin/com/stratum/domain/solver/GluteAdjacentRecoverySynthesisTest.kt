@file:Suppress("FunctionNaming")

package com.stratum.domain.solver

import com.stratum.core.testing.program.parseScenarioContext
import com.stratum.core.testing.program.scenarioPayload
import com.stratum.core.testing.program.testProgramGenerationPipeline
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.onboarding.Weekday
import com.stratum.domain.program.generation.context.ProgramGenerationContext
import com.stratum.domain.program.generation.pipeline.PipelineOptions
import com.stratum.domain.program.generation.prescription.FilledSlot
import com.stratum.domain.program.generation.slots.SlotRole
import com.stratum.domain.program.generation.reference.RpRepProfile
import com.stratum.domain.program.generation.schema.toDraft
import com.stratum.domain.program.generation.schema.toProgramNormalizationRequest
import com.stratum.domain.program.generation.volume.planWeeklyVolume
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Glute specialization must respect adjacent-day recovery when squats and hinges
 * contribute secondary glute credits that match lift metadata.
 */
class GluteAdjacentRecoverySynthesisTest {
    @Test
    fun squatPatternCreditsGluteLikeLiftMetadata() {
        val slot =
            FilledSlot(
                slotId = "s0_squat",
                role = SlotRole.Anchor,
                pattern = MovementPattern.SquatPattern,
                primaryMuscle = MuscleGroup.Quad,
                repProfile = RpRepProfile.LowerFreeWeightCompound,
                setCount = 4,
            )
        assertEquals(2.0, VolumeAllocationCoefficients.Default.coeff(slot, "Glute") * slot.setCount)
    }

    @Test
    fun fourDayGluteSpecAvoidsAdjacentMeaningfulExposureInSolverAndPipeline() = runBlocking {
        val context =
            parseScenarioContext(
                scenarioPayload(
                    days = 4,
                    specialization = mapOf("Glute" to "Specialize"),
                    weekdayLayout = "sequential",
                ),
            )
        val synthesizer = CpSatProgramSynthesizer()

        val synthesis = synthesizer.synthesize(planWeeklyVolume(context), context)
        val solverRepeats =
            recoveryGapViolationsFromPlans(
                plans = synthesis.plans,
                cycleSlotIndexByPosition = synthesis.cycleSlotIndexByPosition,
                context = context,
                muscleFilter = { muscle -> muscle == "Glute" },
            )
        assertTrue(
            solverRepeats.isEmpty(),
            "Solver must schedule glute recovery across squat/hinge secondaries: $solverRepeats",
        )

        val pipeline = testProgramGenerationPipeline(programSynthesisPort = synthesizer)
        val normalized =
            pipeline.generate(
                context = context,
                normalizationRequest = context.toProgramNormalizationRequest(),
                options = PipelineOptions(skipLiftSwap = true, skipProgramNaming = true),
            )
        val draft = normalized.toDraft()
        val pipelineRepeats =
            recoveryGapViolationsFromDraftWorkouts(
                workouts = draft.workouts.sortedBy { workout ->
                    workout.weekday?.let { day -> Weekday.entries.indexOfFirst { it.name == day } } ?: workout.position
                },
                context = context,
                muscleFilter = { muscle -> muscle == "Glute" },
            )
        assertTrue(
            pipelineRepeats.isEmpty(),
            "Pipeline glute exposure must avoid calendar-adjacent meaningful dose: $pipelineRepeats",
        )
    }

    @Test
    fun sean5GluteSpecRespectsSundayMondayWrapAroundAdjacency() = runBlocking {
        val context =
            parseScenarioContext(
                scenarioPayload(
                    days = 5,
                    specialization = mapOf("Glute" to "Specialize"),
                    weekdayLayout = "sean5",
                ),
            )
        val synthesizer = CpSatProgramSynthesizer()
        val synthesis = synthesizer.synthesize(planWeeklyVolume(context), context)
        val repeats =
            recoveryGapViolationsFromPlans(
                plans = synthesis.plans,
                cycleSlotIndexByPosition = synthesis.cycleSlotIndexByPosition,
                context = context,
                muscleFilter = { muscle -> muscle == "Glute" },
            )
        assertTrue(
            repeats.isEmpty(),
            "Sun→Mon wrap-around must forbid adjacent glute exposure: $repeats " +
                "weekdays=${synthesis.cycleSlotIndexByPosition}",
        )
    }
}
