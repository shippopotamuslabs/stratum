package com.stratum.domain.program.generation

import com.stratum.core.testing.program.parseScenarioContext
import com.stratum.core.testing.program.scenarioPayload
import com.stratum.core.testing.program.testProgramGenerationPipeline
import com.stratum.domain.program.ScheduleDayItem
import com.stratum.domain.program.buildWeeklySchedule
import com.stratum.domain.program.generation.pipeline.PipelineOptions
import com.stratum.domain.program.generation.schema.toDomainProgram
import com.stratum.domain.program.generation.schema.toDraft
import com.stratum.domain.program.generation.schema.toProgramNormalizationRequest
import com.stratum.domain.program.generation.validation.computeWorkoutMuscleVolume
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import com.stratum.domain.solver.CpSatProgramSynthesizer

/**
 * Reference comparison against Sean's manual 5-day U/L specialization workbook
 * (`5 Day U_L - Specialization.xlsx`): Sun/Mon/Wed/Thu/Fri with Tuesday rest.
 */
class SeanWorkbookSynthesisTest {

    private val seanSpecialization =
        mapOf(
            "Chest" to "Specialize",
            "Bicep" to "Specialize",
            "Tricep" to "Specialize",
            "Calf" to "Specialize",
            "Back" to "MinimumVolume",
            "Quad" to "MinimumVolume",
        )

    @Test
    fun `unified synthesis matches Sean workbook shape and volume priorities`() = runBlocking {
        val context = seanContext()
        val pipeline = testProgramGenerationPipeline(programSynthesisPort = CpSatProgramSynthesizer())
        val normalized =
            pipeline.generate(
                context = context,
                normalizationRequest = context.toProgramNormalizationRequest(),
            )
        val draft = normalized.toDraft()
        val program = normalized.toDomainProgram()
        val weekly = weeklyMuscleVolume(draft, context)

        val schedule =
            buildWeeklySchedule(program)
                .filterIsInstance<ScheduleDayItem.WorkoutDay>()
                .joinToString { "${it.weekday.name.take(3)}: ${it.workout.name}" }

        val chest = weekly["Chest"] ?: 0.0
        val back = weekly["Back"] ?: 0.0
        val bicep = weekly["Bicep"] ?: 0.0
        val tricep = weekly["Tricep"] ?: 0.0
        val calf = weekly["Calf"] ?: 0.0
        val quad = weekly["Quad"] ?: 0.0

        assertTrue(
            schedule.contains("Sun:") && schedule.contains("Mon:"),
            "Sean calendar should include Sunday and Monday sessions: $schedule",
        )
        assertTrue(chest >= back + 4.0, "Chest ($chest) should exceed backburner back ($back)")
        assertTrue(bicep + tricep >= back + 10.0, "Arm volume should dominate back")
        assertTrue(calf >= 12.0, "Calf specialization should land near Sean 15: $calf")
        assertTrue(quad <= back + 4.0, "Quad backburner ($quad) should stay near back ($back)")
    }

    private fun seanContext() =
        parseScenarioContext(
            scenarioPayload(
                days = 5,
                specialization = seanSpecialization,
                weekdayLayout = "sean5",
            ),
        )

    private fun weeklyMuscleVolume(
        draft: com.stratum.domain.program.generation.draft.ProgramDraft,
        context: com.stratum.domain.program.generation.context.ProgramGenerationContext,
    ): Map<String, Double> {
        val totals = mutableMapOf<String, Double>()
        for (workout in draft.workouts) {
            for ((muscle, dose) in computeWorkoutMuscleVolume(workout, context)) {
                totals[muscle.name] = (totals[muscle.name] ?: 0.0) + dose
            }
        }
        return totals
    }
}
