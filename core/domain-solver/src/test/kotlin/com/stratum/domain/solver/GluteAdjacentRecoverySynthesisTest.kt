@file:Suppress("FunctionNaming")

package com.stratum.domain.solver

import com.stratum.core.testing.program.parseScenarioContext
import com.stratum.core.testing.program.scenarioPayload
import com.stratum.core.testing.program.testProgramGenerationPipeline
import com.stratum.domain.onboarding.Weekday
import com.stratum.domain.program.Workout
import com.stratum.domain.program.generation.context.ProgramGenerationContext
import com.stratum.domain.program.generation.pipeline.PipelineOptions
import com.stratum.domain.program.generation.prescription.FilledSlot
import com.stratum.domain.program.generation.prescription.SessionSlotPlan
import com.stratum.domain.program.generation.split.SlotRole
import com.stratum.domain.program.generation.reference.MUSCLE_RECOVERY_MIN_SCHEDULING_DOSE
import com.stratum.domain.program.generation.reference.RpRepProfile
import com.stratum.domain.program.generation.schema.toDraft
import com.stratum.domain.program.generation.schema.toDomainProgram
import com.stratum.domain.program.generation.schema.toProgramNormalizationRequest
import com.stratum.domain.program.generation.validation.computeWorkoutMuscleVolume
import com.stratum.domain.program.generation.volume.planWeeklyVolume
import kotlinx.coroutines.test.runTest
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
                pattern = "SquatPattern",
                primaryMuscle = "Quad",
                repProfile = RpRepProfile.LowerFreeWeightCompound,
                setCount = 4,
            )
        assertEquals(2.0, VolumeAllocationCoefficients.coeff(slot, "Glute") * slot.setCount)
    }

    @Test
    fun fourDayGluteSpecAvoidsAdjacentMeaningfulExposureInSolverAndPipeline() = runTest {
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
            adjacentMeaningfulMuscleRepeats(
                plans = synthesis.plans,
                weekdayIndexByPosition = synthesis.weekdayIndexByPosition,
                muscle = "Glute",
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
        val program = normalized.toDomainProgram()
        val exposuresByWorkout =
            draft.workouts.associate { workout ->
                workout.name.orEmpty() to computeWorkoutMuscleVolume(workout, context)
            }
        val workouts =
            program.workouts.sortedBy { workout ->
                workout.weekday?.let { day -> Weekday.entries.indexOf(day) } ?: workout.position
            }
        val pipelineRepeats = adjacentMeaningfulMuscleRepeats(workouts, exposuresByWorkout, "Glute")
        assertTrue(
            pipelineRepeats.isEmpty(),
            "Pipeline glute exposure must avoid calendar-adjacent meaningful dose: $pipelineRepeats",
        )
    }

    @Test
    fun sean5GluteSpecRespectsSundayMondayWrapAroundAdjacency() = runTest {
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
            adjacentMeaningfulMuscleRepeats(
                plans = synthesis.plans,
                weekdayIndexByPosition = synthesis.weekdayIndexByPosition,
                muscle = "Glute",
            )
        assertTrue(
            repeats.isEmpty(),
            "Sun→Mon wrap-around must forbid adjacent glute exposure: $repeats " +
                "weekdays=${synthesis.weekdayIndexByPosition}",
        )
    }

    private fun adjacentMeaningfulMuscleRepeats(
        plans: List<SessionSlotPlan>,
        weekdayIndexByPosition: List<Int>,
        muscle: String,
    ): List<String> {
        val ordered =
            plans.sortedBy { plan -> weekdayIndexByPosition[plan.position] }
        val issues = mutableListOf<String>()
        for (index in 1 until ordered.size) {
            val prevDay = Weekday.entries[weekdayIndexByPosition[ordered[index - 1].position]]
            val currDay = Weekday.entries[weekdayIndexByPosition[ordered[index].position]]
            if (currDay.ordinal - prevDay.ordinal != 1) continue
            val prevDose = planMuscleCredits(ordered[index - 1], muscle)
            val currDose = planMuscleCredits(ordered[index], muscle)
            if (
                prevDose >= MUSCLE_RECOVERY_MIN_SCHEDULING_DOSE &&
                currDose >= MUSCLE_RECOVERY_MIN_SCHEDULING_DOSE
            ) {
                issues +=
                    "p${ordered[index - 1].position}→p${ordered[index].position}: " +
                    "$muscle ($prevDose, $currDose)"
            }
        }
        val firstDay = Weekday.entries[weekdayIndexByPosition[ordered.first().position]]
        val lastDay = Weekday.entries[weekdayIndexByPosition[ordered.last().position]]
        if (firstDay == Weekday.Monday && lastDay == Weekday.Sunday) {
            val prevDose = planMuscleCredits(ordered.last(), muscle)
            val currDose = planMuscleCredits(ordered.first(), muscle)
            if (
                prevDose >= MUSCLE_RECOVERY_MIN_SCHEDULING_DOSE &&
                currDose >= MUSCLE_RECOVERY_MIN_SCHEDULING_DOSE
            ) {
                issues += "p${ordered.last().position}→p${ordered.first().position}: $muscle ($prevDose, $currDose)"
            }
        }
        return issues
    }

    private fun adjacentMeaningfulMuscleRepeats(
        workouts: List<Workout>,
        exposuresByWorkout: Map<String, Map<String, Double>>,
        muscle: String,
    ): List<String> {
        val issues = mutableListOf<String>()
        for (index in 1 until workouts.size) {
            val prevDay = workouts[index - 1].weekday ?: continue
            val currDay = workouts[index].weekday ?: continue
            if (Weekday.entries.indexOf(currDay) - Weekday.entries.indexOf(prevDay) != 1) continue
            val prevDose = exposuresByWorkout[workouts[index - 1].name].orEmpty()[muscle] ?: 0.0
            val currDose = exposuresByWorkout[workouts[index].name].orEmpty()[muscle] ?: 0.0
            if (
                prevDose >= MUSCLE_RECOVERY_MIN_SCHEDULING_DOSE &&
                currDose >= MUSCLE_RECOVERY_MIN_SCHEDULING_DOSE
            ) {
                issues += "${workouts[index - 1].name}→${workouts[index].name}: $muscle ($prevDose, $currDose)"
            }
        }
        val first = workouts.firstOrNull()?.weekday
        val last = workouts.lastOrNull()?.weekday
        if (first == Weekday.Monday && last == Weekday.Sunday) {
            val prevDose = exposuresByWorkout[workouts.last().name].orEmpty()[muscle] ?: 0.0
            val currDose = exposuresByWorkout[workouts.first().name].orEmpty()[muscle] ?: 0.0
            if (
                prevDose >= MUSCLE_RECOVERY_MIN_SCHEDULING_DOSE &&
                currDose >= MUSCLE_RECOVERY_MIN_SCHEDULING_DOSE
            ) {
                issues += "${workouts.last().name}→${workouts.first().name}: $muscle ($prevDose, $currDose)"
            }
        }
        return issues
    }

    private fun planMuscleCredits(plan: SessionSlotPlan, muscle: String): Double {
        var total = 0.0
        for (slot in plan.filledSlots) {
            if (slot.setCount <= 0) continue
            total += slot.setCount * VolumeAllocationCoefficients.coeff(slot, muscle)
        }
        return total
    }
}
