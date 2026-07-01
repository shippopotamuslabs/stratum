package com.stratum.domain.program.generation.prescription

import com.stratum.core.testing.program.parseScenarioContext
import com.stratum.core.testing.program.scenarioPayload
import com.stratum.core.testing.program.testProgramGenerationPipeline
import com.stratum.domain.onboarding.Weekday
import com.stratum.domain.program.generation.context.ProgramGenerationContext
import com.stratum.domain.program.generation.integration.resolveAdjacentCalendarLiftCollisions
import com.stratum.domain.program.generation.draft.ProgramDraft
import com.stratum.domain.program.generation.pipeline.PipelineOptions
import com.stratum.domain.program.generation.schema.toDomainProgram
import com.stratum.domain.program.generation.schema.toDraft
import com.stratum.domain.program.generation.schema.toProgramNormalizationRequest
import com.stratum.domain.program.generation.validation.computeWorkoutMuscleVolume
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FiveDayArmCalfSpecDiagnosticTest {

    private val specialization =
        mapOf(
            "Calf" to "Specialize",
            "Bicep" to "Specialize",
            "Tricep" to "Specialize",
        )

    @Test
    fun fiveDayArmAndCalfSpecPrioritizesSpecializedMusclesOverMaintenanceUpper() = runBlocking {
        val context = richArmCalfCatalog()
        val pipeline = testProgramGenerationPipeline()
        val normalized =
            pipeline.generate(
                context = context,
                normalizationRequest = context.toProgramNormalizationRequest(),
                options = PipelineOptions(skipLiftSwap = true),
            )
        val draft = normalized.toDraft()
        val program = normalized.toDomainProgram()
        val exposuresByName =
            draft.workouts.associate { workout ->
                workout.name.orEmpty() to computeWorkoutMuscleVolume(workout, context)
            }
        val reconciled =
            resolveAdjacentCalendarLiftCollisions(
                program = program,
                context = context,
            )

        val weekly = weeklyMuscleVolume(draft, context)
        val maintenanceUpper =
            listOf("Chest", "Back", "LateralDeltoid", "AnteriorDeltoid", "PosteriorDeltoid")
                .associateWith { muscle -> weekly[muscle] ?: 0.0 }
        val adjacentDuplicates = adjacentCalendarLiftDuplicates(reconciled, context)
        val avgMaintenanceUpper =
            maintenanceUpper.values.filter { it > 0 }.average().takeIf { !it.isNaN() } ?: 0.0
        val specializedAvg =
            specialization.keys.map { muscle -> weekly[muscle] ?: 0.0 }.average()

        assertTrue(
            specializedAvg >= avgMaintenanceUpper + 2.0,
            "Specialized avg=$specializedAvg should exceed maintenance upper avg=$avgMaintenanceUpper; " +
                "weekly=$weekly adjacent=$adjacentDuplicates schedule=${scheduleSummary(reconciled)}",
        )
        assertTrue(
            adjacentDuplicates.size <= 10,
            "Adjacent calendar lift reuse should stay bounded: $adjacentDuplicates schedule=${scheduleSummary(reconciled)}",
        )
    }

    private fun weeklyMuscleVolume(
        draft: ProgramDraft,
        context: ProgramGenerationContext,
    ): Map<String, Double> {
        val totals = mutableMapOf<String, Double>()
        for (workout in draft.workouts) {
            for ((muscle, dose) in computeWorkoutMuscleVolume(workout, context)) {
                totals[muscle] = (totals[muscle] ?: 0.0) + dose
            }
        }
        return totals
    }

    private fun workoutSummary(
        draft: ProgramDraft,
        context: ProgramGenerationContext,
    ): String =
        draft.workouts.joinToString("; ") { workout ->
            val lifts =
                workout.workoutLifts.orEmpty().joinToString(", ") { lift ->
                    val name = context.liftsById[lift.liftId.orEmpty()]?.name ?: lift.liftId
                    "$name(${lift.workoutLiftSets?.size ?: 0})"
                }
            "${workout.name}: $lifts"
        }

    private fun scheduleSummary(
        program: com.stratum.domain.program.Program,
    ): String =
        program.workouts
            .sortedBy { it.weekday?.let { day -> Weekday.entries.indexOf(day) } ?: it.position }
            .joinToString(" → ") { "${it.weekday} ${it.name}" }

    private fun adjacentCalendarLiftDuplicates(
        program: com.stratum.domain.program.Program,
        context: ProgramGenerationContext,
    ): List<String> {
        val ordered =
            program.workouts.sortedBy { workout ->
                workout.weekday?.let { day -> Weekday.entries.indexOf(day) } ?: workout.position
            }
        val issues = mutableListOf<String>()
        for (i in 1 until ordered.size) {
            val prevIds = ordered[i - 1].workoutLifts.map { it.liftId.value }.toSet()
            val currIds = ordered[i].workoutLifts.map { it.liftId.value }.toSet()
            val overlap = prevIds.intersect(currIds)
            for (liftId in overlap) {
                val name = context.liftsById[liftId]?.name ?: liftId
                issues +=
                    "${ordered[i - 1].name}(${ordered[i - 1].weekday}) then " +
                    "${ordered[i].name}(${ordered[i].weekday}): $name"
            }
        }
        return issues
    }

    private fun richArmCalfCatalog(): ProgramGenerationContext {
        val base = parseScenarioContext(scenarioPayload(5, specialization, weekdayLayout = "mtwfs"))
        val extraLifts =
            listOf(
                lift("hammer-curl", "Hammer Curl", "ElbowFlexion", listOf("Bicep")),
                lift("preacher-curl", "Preacher Curl", "ElbowFlexion", listOf("Bicep")),
                lift("rope-tricep", "Rope Tricep Extension", "ElbowExtension", listOf("Tricep")),
                lift("skull-crusher", "Skull Crusher", "ElbowExtension", listOf("Tricep")),
                lift("standing-calf", "Standing Calf Raise", "PlantarFlexion", listOf("Calf")),
                lift("donkey-calf", "Donkey Calf Raise", "PlantarFlexion", listOf("Calf")),
                lift("lat-pulldown", "Cable Lat Pulldown", "VerticalPull", listOf("Back"), listOf("Bicep")),
                lift("pull-down-neutral", "Neutral Grip Lat Pulldown", "VerticalPull", listOf("Back"), listOf("Bicep")),
            )
        val liftsById = base.liftsById.toMutableMap()
        val enabled = base.enabledLiftIds.toMutableSet()
        for (extra in extraLifts) {
            liftsById[extra.id] = extra
            enabled += extra.id
        }
        return base.copy(
            liftsById = liftsById,
            enabledLiftIds = enabled,
            movementPatternByLiftId = liftsById.mapValues { (_, value) -> value.movementPattern },
        )
    }

    private fun lift(
        id: String,
        name: String,
        pattern: String,
        primary: List<String>,
        secondary: List<String> = emptyList(),
    ): com.stratum.domain.program.generation.context.LiftContext =
        com.stratum.domain.program.generation.context.LiftContext(
            id = id,
            name = name,
            movementPattern = pattern,
            movementPatterns = listOf(pattern),
            primaryMuscles = primary,
            secondaryMuscles = secondary,
            proven = true,
            mainstream = true,
        )
}
