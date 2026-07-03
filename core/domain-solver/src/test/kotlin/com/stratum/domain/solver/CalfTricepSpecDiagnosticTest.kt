package com.stratum.domain.solver

import com.stratum.core.testing.program.parseScenarioContext
import com.stratum.core.testing.program.scenarioPayload
import com.stratum.core.testing.program.testProgramGenerationPipeline
import com.stratum.domain.program.generation.draft.ProgramDraft
import com.stratum.domain.program.generation.pipeline.PipelineOptions
import com.stratum.domain.program.generation.schema.toDraft
import com.stratum.domain.program.generation.schema.toProgramNormalizationRequest
import com.stratum.domain.program.generation.validation.computeWorkoutMuscleVolume
import com.stratum.domain.program.generation.volume.planWeeklyVolume
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Locks calf + tricep 5-day specialization: PV maintenance delivery and session fill on 60 min days. */
class CalfTricepSpecDiagnosticTest {
    @Test
    fun calfTricepFiveDaySixtyMinuteDeliversPvMaintenanceAndFillsSessions() = runBlocking {
        val specialization = mapOf("Calf" to "Specialize", "Tricep" to "Specialize")
        val context =
            parseScenarioContext(
                scenarioPayload(
                    days = 5,
                    specialization = specialization,
                    weekdayLayout = "mtwfs",
                    sessionLengthMinutes = 60,
                ),
            )
        val targets = planWeeklyVolume(context)
        val pipeline = testProgramGenerationPipeline(programSynthesisPort = CpSatProgramSynthesizer())
        val normalized =
            pipeline.generate(
                context = context,
                normalizationRequest = context.toProgramNormalizationRequest(),
                options = PipelineOptions(skipLiftSwap = true, skipProgramNaming = true),
            )
        val draft = normalized.toDraft()
        val weekly = weeklyVolume(draft, context)
        val setCounts = sessionSetCounts(draft)
        val chestAim = targets.getValue("Chest").aim
        val backAim = targets.getValue("Back").aim
        val chestActual = weekly["Chest"] ?: 0.0
        val backActual = weekly["Back"] ?: 0.0
        val minSets = setCounts.minOrNull() ?: 0

        assertTrue(
            chestAim >= 13.0,
            "Weekly planner should keep chest at PV: aim=$chestAim",
        )
        assertTrue(
            backAim >= 13.0,
            "Weekly planner should keep back at PV: aim=$backAim",
        )
        assertTrue(
            chestActual >= chestAim - 3.0,
            "Chest should track weekly PV aim: aim=$chestAim actual=$chestActual weekly=$weekly",
        )
        assertTrue(
            backActual >= backAim - 1.5,
            "Back should track weekly PV aim: aim=$backAim actual=$backActual weekly=$weekly",
        )
        assertTrue(
            minSets >= 13,
            "60 min sessions should fill above token days; setCounts=$setCounts",
        )
    }

    @Test
    fun armCalfTricepFiveDaySixtyMinuteKeepsMaintenanceNearPv() = runBlocking {
        val specialization =
            mapOf(
                "Calf" to "Specialize",
                "Bicep" to "Specialize",
                "Tricep" to "Specialize",
            )
        val context =
            parseScenarioContext(
                scenarioPayload(
                    days = 5,
                    specialization = specialization,
                    weekdayLayout = "mtwfs",
                    sessionLengthMinutes = 60,
                ),
            )
        val targets = planWeeklyVolume(context)
        val pipeline = testProgramGenerationPipeline(programSynthesisPort = CpSatProgramSynthesizer())
        val normalized =
            pipeline.generate(
                context = context,
                normalizationRequest = context.toProgramNormalizationRequest(),
                options = PipelineOptions(skipLiftSwap = true, skipProgramNaming = true),
            )
        val draft = normalized.toDraft()
        val weekly = weeklyVolume(draft, context)
        val chestAim = targets.getValue("Chest").aim
        val backAim = targets.getValue("Back").aim
        val chestActual = weekly["Chest"] ?: 0.0
        val backActual = weekly["Back"] ?: 0.0

        assertTrue(
            chestActual >= chestAim - 5.0,
            "chest aim=$chestAim actual=$chestActual weekly=$weekly",
        )
        assertTrue(
            backActual >= backAim - 5.0,
            "back aim=$backAim actual=$backActual weekly=$weekly",
        )
        assertTrue(
            (sessionSetCounts(draft).minOrNull() ?: 0) >= 14,
            "setCounts=${sessionSetCounts(draft)}",
        )
    }

    private fun weeklyVolume(
        draft: ProgramDraft,
        context: com.stratum.domain.program.generation.context.ProgramGenerationContext,
    ): Map<String, Double> {
        val totals = mutableMapOf<String, Double>()
        for (workout in draft.workouts) {
            for ((muscle, dose) in computeWorkoutMuscleVolume(workout, context)) {
                totals[muscle] = (totals[muscle] ?: 0.0) + dose
            }
        }
        return totals
    }

    private fun sessionSetCounts(draft: ProgramDraft): List<Int> =
        draft.workouts.map { workout ->
            workout.workoutLifts.orEmpty().sumOf { lift -> lift.workoutLiftSets?.size ?: 0 }
        }
}
