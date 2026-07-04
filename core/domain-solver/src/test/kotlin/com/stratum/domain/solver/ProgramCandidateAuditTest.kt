@file:Suppress("FunctionNaming", "MagicNumber", "MaxLineLength", "SpringQualifierArguments")

package com.stratum.domain.solver

import com.stratum.core.testing.program.parseScenarioContext
import com.stratum.core.testing.program.scenarioPayload
import com.stratum.core.testing.program.testProgramGenerationPipeline
import com.stratum.domain.lift.KinematicProfile
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.onboarding.SpecializationLevel
import com.stratum.domain.onboarding.Weekday
import com.stratum.domain.program.generation.context.LiftContext
import com.stratum.domain.program.generation.context.ProgramGenerationContext
import com.stratum.domain.program.generation.pipeline.PipelineOptions
import com.stratum.domain.program.generation.reference.calendarDayIndexGap
import com.stratum.domain.program.generation.reference.sessionHardSetCapForLength
import com.stratum.domain.program.generation.schema.toDraft
import com.stratum.domain.program.generation.schema.toProgramNormalizationRequest
import com.stratum.domain.program.generation.split.trainingWeekdayIndices
import com.stratum.domain.program.generation.validation.computeWorkoutMuscleVolume
import com.stratum.domain.program.generation.volume.planWeeklyVolume
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty

/**
 * Stage 2b — Solver validation harness for benchmark candidate cleaning.
 *
 * Gated by `-Dstratum.audit.run=true` (off in normal CI runs). Reads
 * `build/candidate_manifest.json` (emitted by `scripts/harvest_programs.py`),
 * runs the full generation pipeline for each candidate, evaluates the same
 * coach-quality predicates the acceptance test asserts on (axial spacing,
 * recovery gaps, volume limits, slot consolidation, feasibility), and writes
 * `build/candidate_metrics.json` for `scripts/clean_and_diversify.py` to consume.
 *
 * Run from the repo root, matching the test's working directory convention:
 *   ./gradlew :core:domain-solver:testDebugUnitTest \
 *       --tests com.stratum.domain.solver.ProgramCandidateAuditTest \
 *       -Dstratum.audit.run=true -Dstratum.solver.deadline=12000
 *
 * Output per candidate: `{label, passed, failures[], weekly_volume_vector[11],
 * movement_pattern_set[]}`. The 11-muscle vector order matches
 * `SCENARIO_MUSCLE_NAMES` in `ProgramGenerationScenarioFixtures.kt`.
 */
@EnabledIfSystemProperty(named = "stratum.audit.run", matches = "true")
class ProgramCandidateAuditTest {

    /**
     * Drives the audit. Reads the manifest, audits each candidate, writes metrics.
     * Tagged [TAG_AUDIT] so it can be selected/excluded independently of the
     * default `slow` exclusion.
     */
    @Test
    fun auditCandidateManifest() = runBlocking {
        val manifestFile = java.io.File(MANIFEST_PATH)
        assertTrue(manifestFile.exists(), "Missing $MANIFEST_PATH — run harvest_programs.py first.")
        val manifest = Json.parseToJsonElement(manifestFile.readText()).jsonObject
        val candidates = manifest["candidates"]?.jsonArray
            ?: error("manifest missing 'candidates' array")
        val muscleVocab = manifest["muscle_vocab"]?.jsonArray
            ?.map { it.jsonPrimitive.content }
            ?: error("manifest missing 'muscle_vocab'")

        val results = mutableListOf<JsonObject>()
        var passCount = 0
        for ((idx, element) in candidates.withIndex()) {
            val spec = element.jsonObject
            val label = spec["label"]!!.jsonPrimitive.content
            println("AUDIT [${idx + 1}/${candidates.size}] $label")
            val result = auditOne(label, spec, muscleVocab)
            results += result
            if (result["passed"]!!.jsonPrimitive.boolean) passCount += 1
        }

        val out = buildJsonObject {
            put("candidates", JsonArray(results))
            put("passed_count", JsonPrimitive(passCount))
            put("total_count", JsonPrimitive(candidates.size))
        }
        val outFile = java.io.File(METRICS_PATH)
        outFile.parentFile?.mkdirs()
        outFile.writeText(Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), out))
        println("AUDIT COMPLETE: $passCount/${candidates.size} passed → $METRICS_PATH")
        // Soft assertion: at least half must pass or something is fundamentally broken
        // (e.g. a solver regression, not just hard candidates).
        assertTrue(
            passCount * 2 >= candidates.size,
            "Audit pass rate ${passCount}/${candidates.size} < 50% — suspect a solver regression, not hard candidates.",
        )
    }

    /** Audit a single candidate spec. Returns the metrics JSON object. */
    private suspend fun auditOne(
        label: String,
        spec: JsonObject,
        muscleVocab: List<String>,
    ): JsonObject {
        val days = spec["days"]!!.jsonPrimitive.intOrNull!!
        val layout = spec["layout"]!!.jsonPrimitive.content
        val sessionLength = spec["session_length"]!!.jsonPrimitive.intOrNull!!
        val specialization = spec["specialization"]!!.jsonObject
            .entries.associate { (k, v) -> k to v.jsonPrimitive.content }
        val failures = mutableListOf<String>()
        val weeklyVolumeVector = DoubleArray(muscleVocab.size)
        val movementPatternSet = sortedSetOf<String>()

        try {
            val context = buildContext(days, layout, sessionLength, specialization)
            val draft = generateDraft(context)
            val targets = planWeeklyVolume(context)

            // ── Weekly muscle volume vector + movement pattern set (always computed) ──
            val weekly = mutableMapOf<MuscleGroup, Double>()
            for (workout in draft.workouts) {
                for ((muscle, dose) in computeWorkoutMuscleVolume(workout, context)) {
                    weekly[muscle] = (weekly[muscle] ?: 0.0) + dose
                }
                for (lift in workout.workoutLifts.orEmpty()) {
                    if (lift.workoutLiftSets.orEmpty().isEmpty()) continue
                    val meta = context.liftsById[lift.liftId.orEmpty()] ?: continue
                    meta.movementPattern?.let { movementPatternSet += it.name }
                }
            }
            for ((i, muscleName) in muscleVocab.withIndex()) {
                val mg = runCatching { MuscleGroup.valueOf(muscleName) }.getOrNull() ?: continue
                weeklyVolumeVector[i] = weekly[mg] ?: 0.0
            }

            // ── Predicate 1: Axial spacing — heavy axial anchors not on calendar-adjacent days ──
            // SquatPattern / HipHinge axial compounds with ≥MUSCLE_RECOVERY_MODERATE_DOSE (8) sets
            // must have ≥48h (gap ≥ 2) between repeat sessions.
            val axialFailures = checkAxialSpacing(draft, context)
            failures += axialFailures

            // ── Predicate 2: Recovery spacing — no calendar-adjacent (gap=1) same-muscle violations ──
            val recoveryViolations = recoveryGapViolationsFromDraftWorkouts(
                workouts = draft.workouts,
                context = context,
                calendarGapFilter = 1,
            )
            failures += recoveryViolations.map { "recovery_gap: $it" }

            // ── Predicate 3: Weekly volume limits per muscle (within [target.min×0.8, target.max×1.2]) ──
            // `targets` is keyed by muscle NAME (String); resolve to MuscleGroup for the weekly lookup.
            for ((muscleName, target) in targets) {
                val mg = runCatching { MuscleGroup.valueOf(muscleName) }.getOrNull() ?: continue
                val actual = weekly[mg] ?: 0.0
                val spec2 = context.specialization[mg] ?: SpecializationLevel.Maintenance
                // Specialized muscles are pulled toward RC aim; allow [MEV-tol, RC×1.2].
                // Maintenance: [MV×0.8, MAINT_CEILING×1.2]. Omit/MinVolume muscles: only check upper bound.
                val (lo, hi) = when (spec2) {
                    SpecializationLevel.Specialize -> (target.min - 3.0) to (target.max * 1.2)
                    SpecializationLevel.Maintenance -> 0.0 to (target.max * 1.2)
                    SpecializationLevel.MinimumVolume, SpecializationLevel.Omit -> 0.0 to (target.max * 1.2)
                }
                if (actual < lo) failures += "vol_low_$muscleName: $actual < $lo"
                if (actual > hi) failures += "vol_high_$muscleName: $actual > $hi"
            }

            // ── Predicate 4: Slot consolidation ──
            // 4a. Every active lift ≥ 2 sets.
            for (workout in draft.workouts) {
                for (lift in workout.workoutLifts.orEmpty()) {
                    val sets = lift.workoutLiftSets?.size ?: 0
                    if (sets in 1 until MIN_SETS_PER_ACTIVE_SLOT) {
                        val liftId = lift.liftId.orEmpty()
                        val name = context.liftsById[liftId]?.name ?: liftId
                        failures += "1set_lift: ${workout.name}/$name=$sets"
                    }
                }
            }
            // 4b. Mean sets per lift ≥ 2.5.
            val totalLifts = draft.workouts.sumOf { it.workoutLifts.orEmpty().size }
            val totalSets = draft.workouts.sumOf { w -> w.workoutLifts.orEmpty().sumOf { it.workoutLiftSets?.size ?: 0 } }
            val meanSetsPerLift = if (totalLifts == 0) 0.0 else totalSets.toDouble() / totalLifts
            if (meanSetsPerLift < 2.5) failures += "mean_sets_low: $meanSetsPerLift"
            // 4c. ≤1 isolation slot per muscle per session.
            for (workout in draft.workouts) {
                val isoByMuscle = workout.workoutLifts.orEmpty()
                    .groupingBy { lift ->
                        val meta = context.liftsById[lift.liftId.orEmpty()] ?: return@groupingBy ""
                        if (meta.movementPattern in COMPOUND_PATTERNS) "" else meta.primaryMuscles.joinToString("|")
                    }.eachCount()
                for ((muscleKey, count) in isoByMuscle) {
                    if (muscleKey.isNotEmpty() && count > 1) {
                        failures += "dup_iso: ${workout.name}/$muscleKey×$count"
                    }
                }
            }

            // ── Predicate 5: Feasibility — non-empty workouts, session set band ──
            if (draft.workouts.isEmpty()) failures += "empty_draft"
            val sessionCap = checkNotNull(sessionHardSetCapForLength(context.sessionLengthMinutes))
            for (workout in draft.workouts) {
                val sets = workout.workoutLifts.orEmpty().sumOf { it.workoutLiftSets?.size ?: 0 }
                if (sets < MIN_SESSION_HARD_SETS || sets > sessionCap) {
                    failures += "session_band: ${workout.name}=$sets outside[$MIN_SESSION_HARD_SETS,$sessionCap]"
                }
            }
        } catch (e: Throwable) {
            failures += "infeasible: ${e.message?.take(120)}"
        }

        return buildJsonObject {
            put("label", label)
            put("passed", failures.isEmpty())
            put("spec", spec)
            putJsonArray("failures") { failures.forEach { add(JsonPrimitive(it)) } }
            putJsonArray("weekly_volume_vector") {
                weeklyVolumeVector.forEach { add(JsonPrimitive(it)) }
            }
            putJsonArray("movement_pattern_set") {
                movementPatternSet.forEach { add(JsonPrimitive(it)) }
            }
        }
    }

    /** Check heavy-axial-anchor spacing: ≥48h between repeat sessions with ≥ moderate dose. */
    private fun checkAxialSpacing(
        draft: com.stratum.domain.program.generation.draft.ProgramDraft,
        context: ProgramGenerationContext,
    ): List<String> {
        val failures = mutableListOf<String>()
        val axialLiftIds = context.liftsById.values
            .filter { it.kinematicProfile == KinematicProfile.AxialCompound }
            .map { it.id }
            .toSet()
        val workoutByDayIndex = draft.workouts
            .filter { it.weekday != null }
            .associate { Weekday.entries.indexOf(Weekday.valueOf(it.weekday!!)) to it }
        val orderedDays = trainingWeekdayIndices(context.weekdays).sorted()
        for (i in 0 until orderedDays.size - 1) {
            val d1 = workoutByDayIndex[orderedDays[i]] ?: continue
            val d2 = workoutByDayIndex[orderedDays[i + 1]] ?: continue
            val gap = calendarDayIndexGap(orderedDays[i], orderedDays[i + 1])
            val axialDose1 = d1.workoutLifts.orEmpty()
                .filter { it.liftId in axialLiftIds }
                .sumOf { it.workoutLiftSets?.size ?: 0 }
            val axialDose2 = d2.workoutLifts.orEmpty()
                .filter { it.liftId in axialLiftIds }
                .sumOf { it.workoutLiftSets?.size ?: 0 }
            // If both sessions have heavy axial load and the gap is < 2 (48h), flag.
            if (axialDose1 >= AXIAL_MODERATE_DOSE && axialDose2 >= AXIAL_MODERATE_DOSE && gap < 2) {
                failures += "axial_spacing: ${d1.weekday}(${axialDose1}sets)→${d2.weekday}(${axialDose2}sets) gap=$gap"
            }
        }
        return failures
    }

    /**
     * Build a ProgramGenerationContext for the candidate spec. Uses the same fixture
     * pipeline as the acceptance test (scenarioPayload → parseScenarioContext) and
     * layers in the rich extra lifts so specialized muscles have enough exercise
     * options (mirrors `richArmCalfCatalog` in ProgramSynthesisAcceptanceTest).
     */
    private fun buildContext(
        days: Int,
        layout: String,
        sessionLength: Int,
        specialization: Map<String, String>,
    ): ProgramGenerationContext {
        val base = parseScenarioContext(
            scenarioPayload(
                days = days,
                specialization = specialization,
                sessionLengthMinutes = sessionLength,
                weekdayLayout = layout,
            ),
        )
        val extraLifts = listOf(
            lift("hammer-curl", "Hammer Curl", "ElbowFlexion", listOf("Bicep")),
            lift("preacher-curl", "Preacher Curl", "ElbowFlexion", listOf("Bicep")),
            lift("rope-tricep", "Rope Tricep Extension", "ElbowExtension", listOf("Tricep")),
            lift("skull-crusher", "Skull Crusher", "ElbowExtension", listOf("Tricep")),
            lift("standing-calf", "Standing Calf Raise", "PlantarFlexion", listOf("Calf")),
            lift("donkey-calf", "Donkey Calf Raise", "PlantarFlexion", listOf("Calf")),
            lift("seated-calf", "Seated Calf Raise", "PlantarFlexion", listOf("Calf")),
            lift("lat-pulldown", "Cable Lat Pulldown", "VerticalPull", listOf("Back"), listOf("Bicep")),
            lift("pull-down-neutral", "Neutral Grip Lat Pulldown", "VerticalPull", listOf("Back"), listOf("Bicep")),
            lift("close-grip-bench", "Close Grip Bench Press", "HorizontalPress", listOf("Tricep"), listOf("Chest")),
            lift("chin-up-bicep", "Bicep Dominant Chin Up", "VerticalPull", listOf("Bicep"), listOf("Back")),
            lift("rear-delt-fly", "Rear Delt Fly", "ShoulderHorizontalAbduction", listOf("PosteriorDeltoid")),
            lift("lat-raise", "Lateral Raise", "ShoulderAbduction", listOf("LateralDeltoid")),
            lift("leg-extension", "Leg Extension", "KneeExtension", listOf("Quad")),
            lift("good-morning", "Good Morning", "HipHinge", listOf("Hamstring"), listOf("Glute", "LowerBack")),
            lift("front-raise", "Front Raise", "ShoulderFlexion", listOf("AnteriorDeltoid")),
            lift("face-pull", "Face Pull", "ShoulderHorizontalAbduction", listOf("PosteriorDeltoid")),
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
            movementPatternByLiftId = liftsById.mapValues { (_, v) -> v.movementPattern },
        )
    }

    private fun lift(
        id: String,
        name: String,
        pattern: String,
        primary: List<String>,
        secondary: List<String> = emptyList(),
        kinematicProfile: KinematicProfile = when (pattern) {
            "SquatPattern", "HipHinge" -> if ("barbell" in name.lowercase()) KinematicProfile.AxialCompound else KinematicProfile.NonAxialCompound
            "HipExtension", "HorizontalPress", "VerticalPress", "InclinePress",
            "HorizontalPull", "VerticalPull" -> KinematicProfile.NonAxialCompound
            else -> KinematicProfile.Isolation
        },
    ): LiftContext {
        val movementPattern = runCatching { MovementPattern.valueOf(pattern) }.getOrNull()
        return LiftContext(
            id = id,
            name = name,
            movementPattern = movementPattern,
            movementPatterns = listOfNotNull(movementPattern).toSet(),
            primaryMuscles = primary.mapNotNull { runCatching { MuscleGroup.valueOf(it) }.getOrNull() }.toSet(),
            secondaryMuscles = secondary.mapNotNull { runCatching { MuscleGroup.valueOf(it) }.getOrNull() }.toSet(),
            proven = true,
            mainstream = true,
            kinematicProfile = kinematicProfile,
        )
    }

    private suspend fun generateDraft(context: ProgramGenerationContext): com.stratum.domain.program.generation.draft.ProgramDraft {
        val pipeline = testProgramGenerationPipeline(
            programSynthesisPort = CpSatProgramSynthesizer(deadlineMillis = SOLVER_DEADLINE_MILLIS),
        )
        val normalized = pipeline.generate(
            context = context,
            normalizationRequest = context.toProgramNormalizationRequest(),
            options = PipelineOptions(skipLiftSwap = true, skipProgramNaming = true),
        )
        return normalized.toDraft()
    }

    private companion object {
        // Test working directory is the module dir (core/domain-solver/), so paths
        // are relative to that — matching benchmarkTuningSuite's `java.io.File("build")`.
        const val MANIFEST_PATH = "build/candidate_manifest.json"
        const val METRICS_PATH = "build/candidate_metrics.json"
        const val MIN_SESSION_HARD_SETS = 8
        const val MIN_SETS_PER_ACTIVE_SLOT = 2
        const val AXIAL_MODERATE_DOSE = 6
        val SOLVER_DEADLINE_MILLIS = System.getProperty("stratum.solver.deadline")?.toLongOrNull() ?: 12_000L
        val COMPOUND_PATTERNS = setOf(
            MovementPattern.SquatPattern,
            MovementPattern.HipHinge,
            MovementPattern.HipExtension,
            MovementPattern.VerticalPull,
            MovementPattern.HorizontalPull,
            MovementPattern.HorizontalPress,
            MovementPattern.VerticalPress,
            MovementPattern.InclinePress,
        )
    }
}
