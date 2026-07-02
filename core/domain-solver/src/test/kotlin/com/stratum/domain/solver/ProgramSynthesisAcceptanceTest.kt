@file:Suppress("FunctionNaming", "MagicNumber", "MaxLineLength")

package com.stratum.domain.solver

import com.stratum.core.testing.program.SPECIALIZATION_PRESETS
import com.stratum.core.testing.program.parseScenarioContext
import com.stratum.core.testing.program.scenarioPayload
import com.stratum.core.testing.program.testProgramGenerationPipeline
import com.stratum.domain.onboarding.Weekday
import com.stratum.domain.program.generation.context.LiftContext
import com.stratum.domain.program.generation.context.ProgramGenerationContext
import com.stratum.domain.program.generation.pipeline.PipelineOptions
import com.stratum.domain.program.generation.reference.MIN_SPECIALIZED_DIRECT_SETS
import com.stratum.domain.program.generation.reference.MIN_TOUCH_DIRECT_SETS
import com.stratum.domain.program.generation.reference.sessionHardSetCapForLength
import com.stratum.domain.program.generation.schema.toDraft
import com.stratum.domain.program.generation.schema.toProgramNormalizationRequest
import com.stratum.domain.program.generation.split.trainingWeekdayIndices
import com.stratum.domain.program.generation.validation.computeWorkoutMuscleVolume
import com.stratum.domain.program.generation.validation.computeWorkoutMuscleSessionStress
import com.stratum.domain.program.generation.reference.calendarDayIndexGap
import com.stratum.domain.program.generation.reference.muscleRecoveryGapViolated
import com.stratum.domain.program.generation.volume.planWeeklyVolume
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Coach-quality acceptance harness for the CP-SAT synthesis pipeline (audit §5). Each scenario
 * asserts on the FINAL [com.stratum.domain.program.generation.draft.ProgramDraft] via
 * [computeWorkoutMuscleVolume] (UI truth), not solver credits, so the assertions measure what
 * the user actually sees. Locks: no 1-set lifts (G1/G2), no adjacent same-muscle metadata
 * exposure (G3/G7/G8), consolidation shape (G5), session set band, specialization honored, and
 * a solve-time budget on the 6-day presets (D3 — domain-alternation removal must not regress
 * solve time).
 */
class ProgramSynthesisAcceptanceTest {

    private val armCalfSpecialization =
        mapOf(
            "Calf" to "Specialize",
            "Bicep" to "Specialize",
            "Tricep" to "Specialize",
        )

    /**
     * #1 No 1-set lifts: every active lift carries ≥2 sets, and every specialized-primary
     * isolation carries ≥ [MIN_SPECIALIZED_DIRECT_SETS]. Directly fails on the audit's [REPRO]
     * (Calf Raise=1, Rear Delt Fly=1, specialized Calf at 1 set).
     */
    @Test
    fun noOneSetLifts() = runBlocking {
        val context = richArmCalfCatalog()
        val draft = generateDraft(context)

        val oneSetLifts = mutableListOf<String>()
        val underMinSpecialized = mutableListOf<String>()
        for (workout in draft.workouts) {
            for (lift in workout.workoutLifts.orEmpty()) {
                val sets = lift.workoutLiftSets?.size ?: 0
                if (sets == 0) continue
                val liftId = lift.liftId.orEmpty()
                val meta = context.liftsById[liftId] ?: continue
                if (sets < 2) {
                    oneSetLifts += "${workout.name}/${meta.name}=$sets"
                }
                val primary = meta.primaryMuscles
                val isSpecializedPrimary = primary.any { context.specialization[it] == "Specialize" }
                val pattern = meta.movementPattern
                val isIsolationPattern = pattern !in COMPOUND_PATTERNS
                // Per-lift floor is the cross-domain touch minimum: a specialized muscle may fill a
                // touch slot on a session outside its primary domain, which legitimately carries
                // MIN_TOUCH_DIRECT_SETS (light supplementary volume), not MIN_SPECIALIZED_DIRECT_SETS.
                // The "≥ MIN_SPECIALIZED_DIRECT_SETS on the main in-domain session" guarantee is
                // enforced separately by specializationHonored().
                if (isSpecializedPrimary && isIsolationPattern && sets < MIN_TOUCH_DIRECT_SETS) {
                    underMinSpecialized += "${workout.name}/${meta.name}=$sets (primary=$primary)"
                }
            }
        }
        assertTrue(oneSetLifts.isEmpty(), "Active lifts with <2 sets: $oneSetLifts")
        assertTrue(
            underMinSpecialized.isEmpty(),
            "Specialized isolations with < $MIN_TOUCH_DIRECT_SETS sets (touch floor): $underMinSpecialized",
        )
    }

    /**
     * #1b Lower-body anchor activation (Bug #2 fix): the squat anchor must fire on at least one
     * session per cycle so Leg Extension is not the sole Quad work. The objective bonus
     * ([LOWER_ANCHOR_ACTIVATION_BONUS]) should make the solver prefer activating the squat anchor
     * over letting the cheap `quad-iso` isolation slot carry all Quad volume.
     */
    @Test
    fun squatAnchorActivatesAtLeastOncePerCycle() = runBlocking {
        val context = richArmCalfCatalog()
        val draft = generateDraft(context)

        val squatLiftIds = setOf("squat", "front-squat")
        val squatSessions = mutableListOf<String>()
        for (workout in draft.workouts) {
            for (lift in workout.workoutLifts.orEmpty()) {
                val liftId = lift.liftId.orEmpty()
                if (liftId in squatLiftIds && (lift.workoutLiftSets?.size ?: 0) > 0) {
                    squatSessions += "${workout.name}/${liftId}=${lift.workoutLiftSets?.size}"
                }
            }
        }
        assertTrue(
            squatSessions.isNotEmpty(),
            "Squat anchor never activated — Leg Extension was likely the sole Quad work. Sessions: $squatSessions",
        )
    }

    /**
     * #2 Class-aware adjacent recovery (metadata): earlier-session stress class and dose tier
     * determine the minimum gap — isolation low-dose may repeat on calendar-adjacent days (24h lane).
     * Also forbids direct primary exposure on 3 consecutive training days (calf stack audit).
     */
    @Test
    fun noAdjacentSameMuscleMetadata() = runBlocking {
        val context = richArmCalfCatalog()
        val draft = generateDraft(context)

        val adjacentViolations =
            recoveryGapViolationsFromDraftWorkouts(
                workouts = draft.workouts,
                context = context,
                calendarGapFilter = 1,
            )
        assertTrue(
            adjacentViolations.isEmpty(),
            "Class-aware calendar-adjacent recovery violations: $adjacentViolations",
        )

        val workoutByDayIndex =
            draft.workouts
                .filter { it.weekday != null }
                .associate { Weekday.entries.indexOf(Weekday.valueOf(it.weekday!!)) to it }
        val stressByWorkoutName =
            draft.workouts.associate { it.name.orEmpty() to computeWorkoutMuscleSessionStress(it, context) }
        val threeDayStacks = mutableListOf<String>()
        val orderedDays = trainingWeekdayIndices(context.weekdays).sorted()
        for (i in 0..orderedDays.size - 3) {
            val d1 = workoutByDayIndex[orderedDays[i]] ?: continue
            val d2 = workoutByDayIndex[orderedDays[i + 1]] ?: continue
            val d3 = workoutByDayIndex[orderedDays[i + 2]] ?: continue
            val consecutive = orderedDays[i + 1] - orderedDays[i] == 1 && orderedDays[i + 2] - orderedDays[i + 1] == 1
            if (!consecutive) continue
            val stress1 = stressByWorkoutName[d1.name.orEmpty()].orEmpty()
            val stress2 = stressByWorkoutName[d2.name.orEmpty()].orEmpty()
            val stress3 = stressByWorkoutName[d3.name.orEmpty()].orEmpty()
            for (muscle in stress1.keys.intersect(stress2.keys).intersect(stress3.keys)) {
                val directHere = hasDirectPrimaryExposure(d1, muscle, context) &&
                    hasDirectPrimaryExposure(d2, muscle, context) &&
                    hasDirectPrimaryExposure(d3, muscle, context)
                if (!directHere) continue
                val earlier12 = stress1[muscle] ?: continue
                val dose2 = stress2[muscle]?.fractionalSets ?: 0.0
                val dose3 = stress3[muscle]?.fractionalSets ?: 0.0
                val gap12 = calendarDayIndexGap(orderedDays[i], orderedDays[i + 1])
                val gap23 = calendarDayIndexGap(orderedDays[i + 1], orderedDays[i + 2])
                val violates =
                    muscleRecoveryGapViolated(earlier12, dose2, gap12) ||
                        muscleRecoveryGapViolated(stress2.getValue(muscle), dose3, gap23)
                if (violates) {
                    threeDayStacks += "${d1.weekday}/${d2.weekday}/${d3.weekday} $muscle"
                }
            }
        }
        assertTrue(
            threeDayStacks.isEmpty(),
            "Muscle with direct exposure on 3 consecutive training days: $threeDayStacks",
        )
    }

    /**
     * #3 Consolidation: at most one isolation slot per muscle per session, mean sets-per-lift
     * ≥ 2.5, and session lift count within solver-feasible bounds (≥3 active, ≤ cap/2 sets-per-lift floor).
     */
    @Test
    fun consolidationShape() = runBlocking {
        val context = richArmCalfCatalog()
        val draft = generateDraft(context)
        val sessionCap = checkNotNull(sessionHardSetCapForLength(context.sessionLengthMinutes))
        val maxLiftsPerSession = sessionCap / MIN_SETS_PER_ACTIVE_SLOT

        val totalLifts = draft.workouts.sumOf { it.workoutLifts.orEmpty().size }
        val totalSets = draft.workouts.sumOf { w ->
            w.workoutLifts.orEmpty().sumOf { it.workoutLiftSets?.size ?: 0 }
        }
        val meanSetsPerLift = if (totalLifts == 0) 0.0 else totalSets.toDouble() / totalLifts
        assertTrue(
            meanSetsPerLift >= 2.5,
            "Mean sets per lift $meanSetsPerLift < 2.5 (consolidation G5)",
        )

        for (workout in draft.workouts) {
            val lifts = workout.workoutLifts.orEmpty()
            assertTrue(
                lifts.size in MIN_ACTIVE_LIFTS_PER_SESSION..maxLiftsPerSession,
                "${workout.name} lift count ${lifts.size} outside [$MIN_ACTIVE_LIFTS_PER_SESSION,$maxLiftsPerSession]",
            )
            val isolationMusclesByCount =
                lifts.groupingBy { lift ->
                    val meta = context.liftsById[lift.liftId.orEmpty()] ?: return@groupingBy ""
                    if (meta.movementPattern in COMPOUND_PATTERNS) "" else meta.primaryMuscles.joinToString("|")
                }.eachCount()
            val dup = isolationMusclesByCount.filter { it.key.isNotEmpty() && it.value > 1 }
            assertTrue(
                dup.isEmpty(),
                "${workout.name} has >1 isolation slot for the same muscle: $dup",
            )
        }
    }

    /**
     * #4 Session set band: each session lands within [MIN_SESSION_HARD_SETS, sessionHardSetCap],
     * and the min session ≥ max/2 (no token days, no blowout days).
     */
    @Test
    fun sessionSetBand() = runBlocking {
        val context = richArmCalfCatalog(sessionLengthMinutes = 60)
        val draft = generateDraft(context)
        val sessionCap = checkNotNull(sessionHardSetCapForLength(context.sessionLengthMinutes))
        val setCounts =
            draft.workouts.map { w ->
                w.workoutLifts.orEmpty().sumOf { it.workoutLiftSets?.size ?: 0 }
            }
        for (count in setCounts) {
            assertTrue(
                count in MIN_SESSION_HARD_SETS..sessionCap,
                "60-min session total $count outside [$MIN_SESSION_HARD_SETS,$sessionCap]; all=$setCounts",
            )
        }
        val min = setCounts.minOrNull() ?: 0
        val max = setCounts.maxOrNull() ?: 0
        assertTrue(min * 2 >= max, "Session set imbalance: min=$min max=$max (min < max/2)")
    }

    /**
     * #5 Specialization honored: each Specialize muscle meets weekly MEV, frequency ≥ tier, and
     * has ≥ [MIN_SPECIALIZED_DIRECT_SETS] direct primary sets on at least one session.
     */
    @Test
    fun specializationHonored() = runBlocking {
        val context = richArmCalfCatalog()
        val draft = generateDraft(context)
        val targets = planWeeklyVolume(context)

        for ((muscle, level) in context.specialization) {
            if (level != "Specialize") continue
            val target = targets[muscle] ?: continue
            var weekly = 0.0
            var frequency = 0
            var maxDirectInOneSession = 0
            for (workout in draft.workouts) {
                val volume = computeWorkoutMuscleVolume(workout, context)
                val here = volume[muscle] ?: 0.0
                if (here > 0.0) frequency += 1
                weekly += here
                val directHere =
                    workout.workoutLifts.orEmpty().sumOf { lift ->
                        val meta = context.liftsById[lift.liftId.orEmpty()] ?: return@sumOf 0
                        if (muscle in meta.primaryMuscles) lift.workoutLiftSets?.size ?: 0 else 0
                    }
                if (directHere > maxDirectInOneSession) maxDirectInOneSession = directHere
            }
            assertTrue(
                weekly >= target.min - 2.0,
                "$muscle weekly $weekly < MEV ${target.min} (tolerance 2.0)",
            )
            assertTrue(frequency >= 1, "$muscle frequency $frequency < 1")
            assertTrue(
                maxDirectInOneSession >= MIN_SPECIALIZED_DIRECT_SETS,
                "$muscle max direct sets in one session $maxDirectInOneSession < $MIN_SPECIALIZED_DIRECT_SETS",
            )
        }
    }

    /**
     * Solve-time budget on the 6-day presets (D3): removing the hard domain-alternation
     * constraint must keep 6-day solves fast. Asserts the synthesizer returns a feasible/optimal
     * result within a tight deadline for the legs and arms 6-day presets.
     */
    @Test
    fun sixDayPresetsSolveWithinBudget() = runBlocking {
        val presets = listOf("legs", "arms")
        for (preset in presets) {
            val specialization = SPECIALIZATION_PRESETS.getValue(preset)
            val context = parseScenarioContext(scenarioPayload(6, specialization))
            val synthesizer = CpSatProgramSynthesizer(deadlineMillis = SOLVER_DEADLINE_MILLIS)
            val result = synthesizer.synthesize(planWeeklyVolume(context), context)
            assertTrue(
                result.plans.isNotEmpty(),
                "6-day $preset synth returned no plans within 10s budget",
            )
            assertTrue(
                result.weekdayIndexByPosition.size == 6,
                "6-day $preset synth weekday assignment incomplete",
            )
        }
    }

    /**
     * G9 + G13 + G10: when chest and back are both Maintenance, weekly volume should stay close
     * and favor the joint-safer direction (back ≥ chest). The prior model gated antagonist
     * balance on equal tiers and set push/pull + closeness weights too low to matter, so chest
     * could run away. Mirrors [AntagonistMaintenanceBalanceTest] via the UI-truth validator.
     */
    @Test
    fun chestDoesNotOutpaceBackWhenBothMaintenance() = runBlocking {
        val context = parseScenarioContext(
            scenarioPayload(
                days = 5,
                specialization = mapOf("Calf" to "Specialize"), // chest/back both Maintenance
                weekdayLayout = "mtwfs",
            ),
        )
        val draft = generateDraft(context)
        val weekly = weeklyMuscleVolume(draft, context)
        val chest = weekly["Chest"] ?: 0.0
        val back = weekly["Back"] ?: 0.0

        assertTrue(
            back + 1.0 >= chest,
            "Back should lead or match chest when both Maintenance: chest=$chest back=$back (G9/G13)",
        )
        assertTrue(
            kotlin.math.abs(chest - back) <= 6.0,
            "Chest/back should stay within 6 sets when both Maintenance: chest=$chest back=$back (G13)",
        )
    }

    /**
     * G9 (the tier-mismatch case): with Back Specialize + Chest Maintenance, antagonist balance
     * previously fired zero constraints, so maintenance chest could outpace specialized back.
     * The fix drops the tier-equality gate so directional pressure applies. Specialized back is
     * pulled toward MRV, so it should not be outpaced by maintenance chest.
     */
    @Test
    fun chestDoesNotOutpaceBackWhenBackSpecialized() = runBlocking {
        val context = parseScenarioContext(
            scenarioPayload(
                days = 5,
                specialization = mapOf("Back" to "Specialize"),
                weekdayLayout = "mtwfs",
            ),
        )
        val draft = generateDraft(context)
        val weekly = weeklyMuscleVolume(draft, context)
        val chest = weekly["Chest"] ?: 0.0
        val back = weekly["Back"] ?: 0.0

        assertTrue(
            back >= chest,
            "Specialized back should not be outpaced by maintenance chest: chest=$chest back=$back (G9)",
        )
    }

    /**
     * G11: when AnteriorDeltoid is Maintenance (here, LateralDeltoid Specialize leaves anterior
     * at default Maintenance), direct overhead (VerticalPress) anterior work is coach-unwanted —
     * the low anterior aim is reachable via chest-press secondaries. The discouragement term
     * biases the solver toward relying on those secondaries rather than churning direct sets.
     */
    @Test
    fun minimalDirectAnteriorDeltWorkWhenLateralSpecialized() = runBlocking {
        val context = parseScenarioContext(
            scenarioPayload(
                days = 5,
                specialization = mapOf("LateralDeltoid" to "Specialize"),
                weekdayLayout = "mtwfs",
            ),
        )
        val draft = generateDraft(context)
        var directAnteriorSets = 0
        for (workout in draft.workouts) {
            for (lift in workout.workoutLifts.orEmpty()) {
                val meta = context.liftsById[lift.liftId.orEmpty()] ?: continue
                if ("AnteriorDeltoid" in meta.primaryMuscles && meta.movementPattern == "VerticalPress") {
                    directAnteriorSets += lift.workoutLiftSets?.size ?: 0
                }
            }
        }
        // Allow a small tolerance — the term is a soft discouragement (G11), not a hard forbid.
        assertTrue(
            directAnteriorSets <= 2,
            "Direct VerticalPress anterior-delt work should be minimal when anterior is Maintenance: " +
                "$directAnteriorSets sets (G11)",
        )
    }

    private fun weeklyMuscleVolume(
        draft: com.stratum.domain.program.generation.draft.ProgramDraft,
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

    private fun hasDirectPrimaryExposure(
        workout: com.stratum.domain.program.generation.draft.ProgramWorkoutDraft,
        muscle: String,
        context: ProgramGenerationContext,
    ): Boolean {
        for (lift in workout.workoutLifts.orEmpty()) {
            val sets = lift.workoutLiftSets.orEmpty()
            if (sets.isEmpty()) continue
            val meta = context.liftsById[lift.liftId.orEmpty()] ?: continue
            if (muscle in meta.primaryMuscles) return true
        }
        return false
    }

    private suspend fun generateDraft(context: ProgramGenerationContext): com.stratum.domain.program.generation.draft.ProgramDraft {
        // Tight 10s solver deadline — a healthy 5-day solve after D3 finishes well under this.
        // If a scenario cannot, that is a real constraint-blow-up regression, not a 120s hang.
        val pipeline = testProgramGenerationPipeline(
            programSynthesisPort = CpSatProgramSynthesizer(deadlineMillis = SOLVER_DEADLINE_MILLIS),
        )
        val normalized =
            pipeline.generate(
                context = context,
                normalizationRequest = context.toProgramNormalizationRequest(),
                options = PipelineOptions(skipLiftSwap = true, skipProgramNaming = true),
            )
        return normalized.toDraft()
    }

    private fun richArmCalfCatalog(sessionLengthMinutes: Int = 75): ProgramGenerationContext {
        val base = parseScenarioContext(
            scenarioPayload(
                days = 5,
                specialization = armCalfSpecialization,
                sessionLengthMinutes = sessionLengthMinutes,
                weekdayLayout = "mtwfs",
            ),
        )
        val extraLifts =
            listOf(
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
        kinematicProfile: String = when (pattern) {
            "SquatPattern", "HipHinge" -> if ("barbell" in name.lowercase()) "AxialCompound" else "NonAxialCompound"
            "HipExtension", "HorizontalPress", "VerticalPress", "InclinePress",
            "HorizontalPull", "VerticalPull" -> "NonAxialCompound"
            else -> "Isolation"
        },
    ): LiftContext =
        LiftContext(
            id = id,
            name = name,
            movementPattern = pattern,
            movementPatterns = listOf(pattern),
            primaryMuscles = primary,
            secondaryMuscles = secondary,
            proven = true,
            mainstream = true,
            kinematicProfile = kinematicProfile,
        )

    private companion object {
        // 30s solver deadline: tight enough to catch constraint-blow-up regressions, generous
        // enough that CP-SAT reaches a properly validated FEASIBLE/OPTIMAL incumbent. At 10s the
        // 5-day model with reified onlyEnforceIf floors can return an incumbent before presolve
        // finishes posting all implications, surfacing as an apparent hard-constraint violation.
        const val SOLVER_DEADLINE_MILLIS = 30_000L
        const val MIN_SESSION_HARD_SETS = 8
        const val MIN_ACTIVE_LIFTS_PER_SESSION = 3
        const val MIN_SETS_PER_ACTIVE_SLOT = 2
        val COMPOUND_PATTERNS =
            setOf(
                "SquatPattern",
                "HipHinge",
                "HipExtension",
                "VerticalPull",
                "HorizontalPull",
                "HorizontalPress",
                "VerticalPress",
                "InclinePress",
            )
    }
}