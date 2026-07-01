@file:Suppress("FunctionNaming", "MagicNumber")

package com.stratum.domain.solver

import com.stratum.core.testing.program.parseScenarioContext
import com.stratum.core.testing.program.scenarioPayload
import com.stratum.core.testing.program.testProgramGenerationPipeline
import com.stratum.domain.program.generation.context.LiftContext
import com.stratum.domain.program.generation.context.ProgramGenerationContext
import com.stratum.domain.program.generation.context.VolumeLandmarkContext
import com.stratum.domain.program.generation.context.VolumeLandmarkSource
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

    /**
     * Regression: `barbell-sumo-deadlift` (HipHinge, Hamstring primary) tags Quad as a secondary
     * muscle (0.5/set). The static `PATTERN_SECONDARY_MUSCLE_CREDITS["HipHinge"]` table credits 0
     * Quad, so the solver used to allocate HipHinge sets thinking they cost 0 Quad — then the
     * Programs UI (metadata truth) showed Quad inflated by the secondary credit the solver never
     * saw. With catalog-derived credits (`computePatternSecondaryCredits`), the solver's HipHinge
     * Quad credit is the catalog average (non-zero when any HipHinge lift tags Quad secondary),
     * so quad/ham stays balanced in metadata truth.
     */
    @Test
    fun fourDayMaintenanceQuadHamBalanceWithSumoDeadliftQuadSecondary() = runBlocking {
        val context = quadHamDivergenceCatalog()
        val pipeline = testProgramGenerationPipeline(programSynthesisPort = CpSatProgramSynthesizer())
        val normalized =
            pipeline.generate(
                context = context,
                normalizationRequest = context.toProgramNormalizationRequest(),
                options = PipelineOptions(skipLiftSwap = true, skipProgramNaming = true),
            )
        val draft = normalized.toDraft()
        val weekly = weeklyMuscleVolume(draft, context)
        val quad = weekly["Quad"] ?: 0.0
        val ham = weekly["Hamstring"] ?: 0.0

        assertTrue(
            ham + 0.5 >= quad,
            "Quad should not exceed Hamstring by more than 0.5 when both are Maintenance, " +
                "even when a HipHinge lift (sumo deadlift) tags Quad as secondary: quad=$quad ham=$ham",
        )
    }

    /**
     * Catalog where barbell-sumo-deadlift (HipHinge, Hamstring primary, Quad secondary) is the
     * dominant HipHinge lift. With the old static credits, the solver's HipHinge Quad credit was
     * 0 — now it is the catalog average and must be non-zero.
     */
    private fun quadHamDivergenceCatalog(): ProgramGenerationContext {
        fun lift(id: String, name: String, pattern: String, primary: List<String>, secondary: List<String> = emptyList()) =
            LiftContext(
                id = id,
                name = name,
                movementPattern = pattern,
                movementPatterns = listOf(pattern),
                primaryMuscles = primary,
                secondaryMuscles = secondary,
                proven = true,
            )
        val lifts = listOf(
            lift("bench", "Bench Press", "HorizontalPress", listOf("Chest"), listOf("Tricep", "AnteriorDeltoid")),
            lift("inc-press", "Incline Press", "InclinePress", listOf("Chest"), listOf("AnteriorDeltoid", "Tricep")),
            lift("row", "Barbell Row", "HorizontalPull", listOf("Back"), listOf("Bicep", "PosteriorDeltoid")),
            lift("pullup", "Pullup", "VerticalPull", listOf("Back"), listOf("Bicep")),
            lift("back-squat", "Barbell Back Squat", "SquatPattern", listOf("Quad"), listOf("Glute", "LowerBack")),
            lift("front-squat", "Barbell Front Squat", "SquatPattern", listOf("Quad"), listOf("Glute")),
            lift("leg-ext", "Leg Extension", "KneeExtension", listOf("Quad")),
            lift("rdl", "Romanian Deadlift", "HipHinge", listOf("Hamstring"), listOf("Glute", "LowerBack")),
            // Sumo deadlift: HipHinge pattern, Hamstring primary, but Quad in secondary.
            // This is the only HipHinge lift tagging Quad, so the catalog average credit for
            // HipHinge→Quad is non-zero, making the solver aware of the divergence.
            lift("sumo-dl", "Barbell Sumo Deadlift", "HipHinge", listOf("Hamstring"), listOf("Glute", "LowerBack", "Quad")),
            lift("leg-curl", "Leg Curl", "KneeFlexion", listOf("Hamstring")),
            lift("hip-thrust", "Hip Thrust", "HipExtension", listOf("Glute"), listOf("Hamstring")),
            lift("calf-raise", "Calf Raise", "PlantarFlexion", listOf("Calf")),
            lift("lat-raise", "Lateral Raise", "ShoulderAbduction", listOf("LateralDeltoid")),
            lift("ohp", "Overhead Press", "VerticalPress", listOf("AnteriorDeltoid"), listOf("Tricep")),
            lift("curl", "Curl", "ElbowFlexion", listOf("Bicep")),
            lift("tricep-ext", "Tricep Extension", "ElbowExtension", listOf("Tricep")),
        )
        val landmarks = listOf(
            vlc("Chest", 10.0, 14.0, 20.0, 11.0),
            vlc("Back", 11.0, 14.0, 20.0, 12.8),
            vlc("Quad", 11.0, 14.0, 20.0, 12.8),
            vlc("Hamstring", 6.0, 10.0, 16.0, 8.4),
            vlc("Glute", 6.0, 10.0, 16.0, 8.4),
            vlc("Calf", 8.0, 12.0, 22.0, 9.6),
            vlc("Tricep", 6.0, 11.0, 18.0, 9.0),
            vlc("Bicep", 6.0, 10.0, 22.0, 8.4),
            vlc("LateralDeltoid", 6.0, 11.0, 18.0, 9.0),
            vlc("PosteriorDeltoid", 6.0, 11.0, 18.0, 9.0),
            vlc("AnteriorDeltoid", 6.0, 11.0, 18.0, 9.0),
            vlc("LowerBack", 0.0, 8.0, 14.0, 4.0),
        )
        val liftsById = linkedMapOf<String, LiftContext>()
        val enabledIds = mutableSetOf<String>()
        val patternById = linkedMapOf<String, String>()
        for (l in lifts) {
            liftsById[l.id] = l
            enabledIds += l.id
            patternById[l.id] = l.movementPattern
        }
        return ProgramGenerationContext(
            trainingAge = "Advanced",
            lastTrained = null,
            sessionLengthMinutes = 60,
            trainingDaysPerWeek = 4,
            weekdays = listOf("Monday", "Tuesday", "Thursday", "Friday"),
            specialization = emptyMap(),
            liftsById = liftsById,
            volumeLandmarksByMuscle = landmarks.associateBy { it.muscle },
            volumeResponsesByMuscle = emptyMap(),
            enabledLiftIds = enabledIds,
            movementPatternByLiftId = patternById,
            liftEffectivenessByLiftId = emptyMap(),
        )
    }

    private fun vlc(muscle: String, mev: Double, mav: Double, mrv: Double, pv: Double) =
        VolumeLandmarkContext(
            muscle = muscle,
            estimatedMev = mev,
            estimatedMav = mav,
            estimatedMrv = mrv,
            estimatedPv = pv,
            source = VolumeLandmarkSource.Literature,
        )

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
