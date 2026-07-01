@file:Suppress("FunctionNaming", "MagicNumber")

package com.stratum.domain.solver

import com.stratum.core.testing.program.parseScenarioContext
import com.stratum.core.testing.program.scenarioPayload
import com.stratum.core.testing.program.testProgramGenerationPipeline
import com.stratum.domain.program.generation.context.ProgramGenerationContext
import com.stratum.domain.program.generation.draft.ProgramDraft
import com.stratum.domain.program.generation.pipeline.PipelineOptions
import com.stratum.domain.program.generation.schema.toDraft
import com.stratum.domain.program.generation.schema.toProgramNormalizationRequest
import com.stratum.domain.program.generation.validation.computeWorkoutMuscleVolume
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * When chest/back and quad/ham share the same maintenance tier, weekly volume should stay
 * close and favor the joint-safer direction (back ≥ chest, ham ≥ quad) when imbalanced.
 */
class AntagonistMaintenanceBalanceTest {
    @Test
    fun fiveDayArmCalfSpecBalancesMaintenanceAntagonistPairs() = runBlocking {
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
                ),
            )
        val pipeline = testProgramGenerationPipeline(programSynthesisPort = CpSatProgramSynthesizer())
        val normalized =
            pipeline.generate(
                context = context,
                normalizationRequest = context.toProgramNormalizationRequest(),
                options = PipelineOptions(skipLiftSwap = true, skipProgramNaming = true),
            )
        val draft = normalized.toDraft()
        val weekly = weeklyMuscleVolume(draft, context)
        val chest = weekly["Chest"] ?: 0.0
        val back = weekly["Back"] ?: 0.0
        val quad = weekly["Quad"] ?: 0.0
        val ham = weekly["Hamstring"] ?: 0.0

        assertTrue(
            kotlin.math.abs(chest - back) <= 6.0,
            "Chest/back should stay within 6 sets when both are maintenance: chest=$chest back=$back",
        )
        assertTrue(
            back + 0.5 >= chest,
            "Prefer back ≥ chest when imbalanced (joint safety): chest=$chest back=$back",
        )
        assertTrue(
            kotlin.math.abs(quad - ham) <= 6.0,
            "Quad/ham should stay within 6 sets when both are maintenance: quad=$quad ham=$ham",
        )
        assertTrue(
            ham + 0.5 >= quad,
            "Prefer ham ≥ quad when imbalanced (joint safety): quad=$quad ham=$ham",
        )
    }

    @Test
    fun fourDayMaintenanceExcludesDirectAnteriorAndPrioritizesArmsOverRearDelt() = runBlocking {
        val context =
            parseScenarioContext(
                scenarioPayload(
                    days = 4,
                    specialization = emptyMap(),
                ),
            )
        val pipeline = testProgramGenerationPipeline(programSynthesisPort = CpSatProgramSynthesizer())
        val normalized =
            pipeline.generate(
                context = context,
                normalizationRequest = context.toProgramNormalizationRequest(),
                options = PipelineOptions(skipLiftSwap = true, skipProgramNaming = true),
            )
        val draft = normalized.toDraft()

        // G14a: No lift with primaryMuscle == AnteriorDeltoid should appear — anterior volume
        // comes entirely from press secondaries when anterior is not Specialize.
        val anteriorDirectLifts = directLiftsForMuscle(draft, context, "AnteriorDeltoid")
        assertTrue(
            anteriorDirectLifts.isEmpty(),
            "Expected zero direct anterior lifts when AnteriorDeltoid is Maintenance, " +
                "but found: ${anteriorDirectLifts.map { it.first }}",
        )

        // G14b: Rear-delt direct sets should not exceed bicep/tricep/lateral-delt direct sets —
        // arms and lateral delts are direct-priority muscles; posterior gets partial coverage
        // from row secondaries (0.5/set) and is deprioritized via the activation penalty.
        val directSets = directSetsByMuscle(draft, context)
        val rearDeltDirect = directSets["PosteriorDeltoid"] ?: 0
        val bicepDirect = directSets["Bicep"] ?: 0
        val tricepDirect = directSets["Tricep"] ?: 0
        val lateralDirect = directSets["LateralDeltoid"] ?: 0

        assertTrue(
            rearDeltDirect <= bicepDirect,
            "Bicep direct sets ($bicepDirect) should be ≥ rear-delt direct sets ($rearDeltDirect)",
        )
        assertTrue(
            rearDeltDirect <= tricepDirect,
            "Tricep direct sets ($tricepDirect) should be ≥ rear-delt direct sets ($rearDeltDirect)",
        )
        assertTrue(
            rearDeltDirect <= lateralDirect,
            "Lateral-delt direct sets ($lateralDirect) should be ≥ rear-delt direct sets ($rearDeltDirect)",
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

    /** Returns (liftId, setCount) pairs where the lift's primaryMuscles include [muscle]. */
    private fun directLiftsForMuscle(
        draft: ProgramDraft,
        context: ProgramGenerationContext,
        muscle: String,
    ): List<Pair<String, Int>> {
        val result = mutableListOf<Pair<String, Int>>()
        for (workout in draft.workouts) {
            for (lift in workout.workoutLifts.orEmpty()) {
                val liftId = lift.liftId.orEmpty()
                val meta = context.liftsById[liftId] ?: continue
                val setCount = lift.workoutLiftSets?.size ?: 0
                if (setCount == 0) continue
                if (muscle in meta.primaryMuscles) {
                    result += liftId to setCount
                }
            }
        }
        return result
    }

    /** Total direct (primary-muscle) sets per muscle across all workouts. */
    private fun directSetsByMuscle(
        draft: ProgramDraft,
        context: ProgramGenerationContext,
    ): Map<String, Int> {
        val totals = mutableMapOf<String, Int>()
        for (workout in draft.workouts) {
            for (lift in workout.workoutLifts.orEmpty()) {
                val liftId = lift.liftId.orEmpty()
                val meta = context.liftsById[liftId] ?: continue
                val setCount = lift.workoutLiftSets?.size ?: 0
                if (setCount == 0) continue
                for (muscle in meta.primaryMuscles) {
                    totals[muscle] = (totals[muscle] ?: 0) + setCount
                }
            }
        }
        return totals
    }
}
