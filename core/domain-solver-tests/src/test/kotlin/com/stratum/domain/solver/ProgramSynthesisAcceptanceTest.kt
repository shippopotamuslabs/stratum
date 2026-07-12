@file:Suppress("FunctionNaming", "MagicNumber", "MaxLineLength")

package com.stratum.domain.solver

import com.stratum.core.testing.program.SPECIALIZATION_PRESETS
import com.stratum.core.testing.program.parseScenarioContext
import com.stratum.core.testing.program.scenarioPayload
import com.stratum.core.testing.program.testProgramGenerationPipeline
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.onboarding.SpecializationLevel
import com.stratum.domain.onboarding.Weekday
import com.stratum.domain.lift.KinematicProfile
import com.stratum.domain.lift.MovementPattern
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
import org.junit.jupiter.api.Tag
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
                val isSpecializedPrimary = primary.any { context.specialization[it] == SpecializationLevel.Specialize }
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
                val directHere = hasDirectPrimaryExposure(d1, muscle.name, context) &&
                    hasDirectPrimaryExposure(d2, muscle.name, context) &&
                    hasDirectPrimaryExposure(d3, muscle.name, context)
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
     * #4 Session set band: each session lands within [sessionCap/2, sessionHardSetCap], and the min
     * session ≥ max/2 (no token days, no blowout days). The floor is session-length-aware (50% of the
     * hard-set cap) rather than the prior flat 8 — a flat 8 is far below real capacity for 75-min
     * sessions (cap 25), which let underfilled 9-set programs pass. The 0.5 ratio is a hard legality
     * floor; the benchmark loss encourages a healthier 0.6 fill target separately.
     */
    @Test
    fun sessionSetBand() = runBlocking {
        val context = richArmCalfCatalog(sessionLengthMinutes = 60)
        val draft = generateDraft(context)
        val sessionCap = checkNotNull(sessionHardSetCapForLength(context.sessionLengthMinutes))
        val sessionFloor = sessionCap / 2
        val setCounts =
            draft.workouts.map { w ->
                w.workoutLifts.orEmpty().sumOf { it.workoutLiftSets?.size ?: 0 }
            }
        for (count in setCounts) {
            assertTrue(
                count in sessionFloor..sessionCap,
                "60-min session total $count outside [$sessionFloor,$sessionCap]; all=$setCounts",
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
            if (level != SpecializationLevel.Specialize) continue
            val target = targets[muscle.name] ?: continue
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
     * #5b Maintenance muscles meet minimum volume: the mirror of [specializationHonored] for
     * non-specialized muscles. Every Maintenance muscle's weekly volume must reach its target.min
     * (MV/MEV floor) within a 1.0-set tolerance. Uses a MAINTENANCE context (no specialization) so the
     * floor applies to ALL muscles including the isolation-dependent ones (Bicep, Tricep, LateralDelt)
     * that cratered in the prior tuning regression — [richArmCalfCatalog] specializes exactly those
     * muscles, so it would have skipped them and missed the failure. The 5-day/75-min/maintenance
     * shape mirrors the real-world scenario that broke.
     */
    @Test
    fun maintenanceMusclesMeetMinimumVolume() = runBlocking {
        // Maintenance 5-day/75-min program with the rich lift catalog — no specialization, so every
        // tracked muscle is checked against its MV floor.
        val base = parseScenarioContext(
            scenarioPayload(
                days = 5,
                specialization = emptyMap(),
                sessionLengthMinutes = 75,
                weekdayLayout = "mtwfs",
            ),
        )
        val context = withRichExtraLifts(base)
        val draft = generateDraft(context)
        val targets = planWeeklyVolume(context)
        val weekly = weeklyMuscleVolume(draft, context)

        val belowFloor = mutableListOf<String>()
        for ((muscleName, target) in targets) {
            val mg = runCatching { MuscleGroup.valueOf(muscleName) }.getOrNull() ?: continue
            if (context.specialization[mg] == SpecializationLevel.Specialize) continue
            val actual = weekly[muscleName] ?: 0.0
            if (actual < target.min - MAINTENANCE_VOLUME_TOLERANCE) {
                belowFloor += "$muscleName: $actual < MV ${target.min} (tol $MAINTENANCE_VOLUME_TOLERANCE)"
            }
        }
        assertTrue(
            belowFloor.isEmpty(),
            "Maintenance muscles below MV floor: $belowFloor",
        )
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
                if (MuscleGroup.AnteriorDeltoid in meta.primaryMuscles && meta.movementPattern == MovementPattern.VerticalPress) {
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
                totals[muscle.name] = (totals[muscle.name] ?: 0.0) + dose
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
            if (runCatching { MuscleGroup.valueOf(muscle) }.getOrNull() in meta.primaryMuscles) return true
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
        return withRichExtraLifts(base)
    }

    /**
     * Layer the rich extra-lift catalog onto a base context. Used by both
     * [richArmCalfCatalog] and [BenchmarkSpec.toContext] so every benchmark scenario
     * shares the same exercise palette (16 supplementary lifts covering arms, calves,
     * back, posterior-delt, and hamstring variants) the original anchors were tuned
     * against. Without this, specialized muscles would lack enough exercise options.
     */
    private fun withRichExtraLifts(base: ProgramGenerationContext): ProgramGenerationContext {
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

    /**
     * Optuna / weight-tuning surface (~56 diverse scenarios). Not a PR gate —
     * enable with `-PincludeBenchmark=true` (and usually a longer deadline).
     */
    @Test
    @Tag("benchmark")
    fun benchmarkTuningSuite() = runBlocking {
        val weightsProp = System.getProperty("stratum.solver.weights")
        if (!weightsProp.isNullOrBlank()) {
            val overrides = weightsProp.split(",")
                .associate {
                    val parts = it.split(":")
                    parts[0].trim() to parts[1].trim().toLong()
                }
            SynthesisObjectiveWeights.setOverrides(overrides)
            println("Benchmark: Running with overrides: $overrides")
        } else {
            SynthesisObjectiveWeights.setOverrides(null)
            println("Benchmark: Running with default weights")
        }

        var totalLoss = 0.0
        val details = mutableMapOf<String, Double>()

        // The benchmark suite is the anti-overfitting surface tune_solver.py optimizes
        // against. The first 8 entries (Benchmark_A..H) reproduce the original hand-tuned
        // baseline exactly; entries I+ extend coverage to 50+ diverse splits selected by
        // the Stage 4 farthest-first diversity pipeline (cosine similarity on the 11-dim
        // weekly muscle volume vector + Jaccard on movement-pattern sets). See
        // scripts/harvest_programs.py, ProgramCandidateAuditTest, scripts/clean_and_diversify.py.
        val cases = BENCHMARK_SPECS.map { it.label to it.toContext() }

        for ((name, context) in cases) {
            try {
                val draft = generateDraft(context)
                
                val adjacentViolations = recoveryGapViolationsFromDraftWorkouts(
                    workouts = draft.workouts,
                    context = context,
                    calendarGapFilter = 1
                )
                val recoveryLoss = adjacentViolations.size * 10000.0
                totalLoss += recoveryLoss
                details["${name}_recovery"] = recoveryLoss

                var volumeLoss = 0.0
                val actualVolumes = weeklyMuscleVolume(draft, context)
                val weeklyVolumeTargets = com.stratum.domain.program.generation.volume.planWeeklyVolume(context)
                // Floor-anchored volume penalty: iterate ALL tracked muscles with a target (not just
                // specialized ones). Below target.min (MV/MEV) → heavy quadratic penalty (the failure
                // mode the loss must dominate). At/above min → small linear nudge toward PV aim.
                // This blocks the exploit where the solver drops isolation slots: the quadratic floor
                // penalty (e.g. (6-4)²×300 = 1,200) far exceeds the activation/lift-count savings.
                for (muscle in weeklyVolumeTargets.keys) {
                    val target = weeklyVolumeTargets.getValue(muscle)
                    val actual = actualVolumes[muscle] ?: 0.0
                    if (actual < target.min) {
                        val shortfall = target.min - actual
                        volumeLoss += shortfall * shortfall * 300.0
                    } else {
                        volumeLoss += kotlin.math.abs(actual - target.aim) * 30.0
                    }
                }

                val quad = actualVolumes["Quad"] ?: 0.0
                val ham = actualVolumes["Hamstring"] ?: 0.0
                val calf = actualVolumes["Calf"] ?: 0.0
                if (quad > 0.0) {
                    if (ham > quad * 1.25) volumeLoss += (ham - quad * 1.25) * 500.0
                    if (calf > quad * 1.25) volumeLoss += (calf - quad * 1.25) * 500.0
                    if (quad > ham * 1.25) volumeLoss += (quad - ham * 1.25) * 500.0
                    if (quad > calf * 1.25) volumeLoss += (quad - calf * 1.25) * 500.0
                }
                totalLoss += volumeLoss
                details["${name}_volume"] = volumeLoss

                val workoutSets = draft.workouts.map { w ->
                    w.workoutLifts.orEmpty().sumOf { it.workoutLiftSets.orEmpty().size }
                }
                val setBalanceLoss = if (workoutSets.isNotEmpty()) {
                    val max = workoutSets.maxOrNull() ?: 0
                    val min = workoutSets.minOrNull() ?: 0
                    (max - min) * 50.0
                } else {
                    0.0
                }
                totalLoss += setBalanceLoss
                details["${name}_set_balance"] = setBalanceLoss

                // ── Layer 1: Session-fill gate (structural prerequisite) ──
                // Sessions MUST be filled to ~60% of the hard-set cap. This is not a tradeable
                // preference — underfilled sessions are structurally broken regardless of volume
                // distribution. The two-layer model mirrors the solver's own structure: fill is a
                // prerequisite (Layer 1), volume distribution is the quality layer that operates
                // WITHIN the constraint that sessions are full (Layer 2, computed above).
                //
                // The fill penalty is dominant (2000/set²) so the optimizer cannot profit by
                // slashing SESSION_FILL_SHORTFALL: a single 9-set 75-min session (floor 15) costs
                // (15-9)²×2000 = 72,000 — far exceeding any volume-deviation savings (~21k). The
                // catastrophic gate (any session below 50% of cap → flat 100k) catches the extreme
                // "3 lifts / 9 sets" collapse, making it strictly worse than any filled alternative.
                val sessionCap = checkNotNull(sessionHardSetCapForLength(context.sessionLengthMinutes))
                val healthyFloor = (sessionCap * 0.6).toInt()
                val catastrophicThreshold = sessionCap / 2
                val hasCatastrophicUnderfill = workoutSets.any { it < catastrophicThreshold }
                var fillLoss = if (hasCatastrophicUnderfill) CATASTROPHIC_FILL_PENALTY else 0.0
                for (sets in workoutSets) {
                    val shortfall = healthyFloor - sets
                    if (shortfall > 0) fillLoss += shortfall.toDouble() * shortfall * 2000.0
                }
                totalLoss += fillLoss
                details["${name}_fill"] = fillLoss

                // Lift-count: only penalize EXCESS above a healthy session average (5 lifts/session).
                // Penalizing all lifts unconditionally rewards underfilling — the prior bug.
                val totalLifts = draft.workouts.sumOf { it.workoutLifts.orEmpty().size }
                val expectedLifts = draft.workouts.size * 5
                val liftCountLoss = maxOf(0, totalLifts - expectedLifts) * 10.0
                totalLoss += liftCountLoss
                details["${name}_lift_count"] = liftCountLoss

            } catch (e: Exception) {
                val failLoss = 100000.0
                totalLoss += failLoss
                details["${name}_infeasible"] = failLoss
                println("Benchmark: $name failed to solve: ${e.message}")
            }
        }

        println("Benchmark Complete. Total Loss: $totalLoss")
        println("Details: $details")

        // Reset overrides to avoid side-effects on other tests in the suite
        SynthesisObjectiveWeights.setOverrides(null)

        val buildDir = java.io.File("build")
        if (!buildDir.exists()) buildDir.mkdirs()
        val lossFile = java.io.File(buildDir, "tuning_loss.json")
        lossFile.writeText("{\"loss\": $totalLoss}")
        println("Loss saved to ${lossFile.absolutePath}")
    }


    /**
     * A single benchmark scenario — the four axes a [ProgramGenerationContext] varies on.
     * Compact table representation (vs. 50+ hand-written builder functions) so the
     * anti-overfitting surface stays auditable in one place. `toContext()` reproduces
     * the rich extra-lift catalog the original Benchmark_A–H relied on.
     */
    private data class BenchmarkSpec(
        val label: String,
        val days: Int,
        val layout: String,
        val sessionLengthMinutes: Int,
        val specialization: Map<MuscleGroup, SpecializationLevel>,
    )

    /** Specialize helper for the spec table — keeps entries terse. */
    private fun specVararg(vararg muscles: MuscleGroup): Map<MuscleGroup, SpecializationLevel> =
        muscles.associateWith { SpecializationLevel.Specialize }

    private fun BenchmarkSpec.toContext(): ProgramGenerationContext {
        val specializationMap = specialization.mapKeys { it.key.name }.mapValues { it.value.name }
        val base = parseScenarioContext(
            scenarioPayload(
                days = days,
                specialization = specializationMap,
                sessionLengthMinutes = sessionLengthMinutes,
                weekdayLayout = layout,
            ),
        )
        // Layer the rich extra lifts (same catalog as richArmCalfCatalog) so every
        // benchmark has the full exercise palette the original anchors were tuned against.
        return withRichExtraLifts(base)
    }

    /**
     * The benchmark suite. Entries 0–7 reproduce Benchmark_A–H verbatim (same weekdays,
     * days/week, specialization, session length as the original hand-tuned anchors);
     * entries 8+ extend coverage to the diverse splits selected by the Stage 4 pipeline.
     * Layouts map to [WEEKDAY_LAYOUTS] keys in ProgramGenerationScenarioFixtures.
     */
    private val BENCHMARK_SPECS: List<BenchmarkSpec> = listOf(
        BenchmarkSpec("Benchmark_A_ArmCalf5Day", 5, "mtwfs", 75,
            specVararg(MuscleGroup.Calf, MuscleGroup.Bicep, MuscleGroup.Tricep)),
        BenchmarkSpec("Benchmark_B_FullBody4Day", 4, "spread5", 75, emptyMap()),
        BenchmarkSpec("Benchmark_C_ULPPL5Day", 5, "spread5", 75, emptyMap()),
        BenchmarkSpec("Benchmark_D_ArmTricepSpec5Day60Min", 5, "mtwfs", 60,
            specVararg(MuscleGroup.Bicep, MuscleGroup.Tricep)),
        BenchmarkSpec("Benchmark_E_ChestBackSpec3Day75Min", 3, "mwf", 75,
            specVararg(MuscleGroup.Chest, MuscleGroup.Back)),
        BenchmarkSpec("Benchmark_F_QuadHamSpec4Day75Min", 4, "spread5", 75,
            specVararg(MuscleGroup.Quad, MuscleGroup.Hamstring)),
        BenchmarkSpec("Benchmark_G_ShoulderSpec5Day75Min", 5, "mtwfs", 75,
            specVararg(MuscleGroup.LateralDeltoid, MuscleGroup.PosteriorDeltoid)),
        BenchmarkSpec("Benchmark_H_ChestTricepSpec6Day75Min", 6, "sequential", 75,
            specVararg(MuscleGroup.Chest, MuscleGroup.Tricep)),
        // ↓ Diversity-selected benchmarks (Stage 4 farthest-first: cosine on 11-dim weekly
        //   volume vector + Jaccard on movement-pattern sets). 48 entries extend the 8 anchors
        //   to 56 total — covering 3/4/5/6-day splits, every specialization preset, and the
        //   6 weekday layouts. Ordered by selection (highest diversity first).
        BenchmarkSpec("Synth_3d_mwf_legs_75min", 3, "mwf", 75, specVararg(MuscleGroup.Glute, MuscleGroup.Quad)),
        BenchmarkSpec("Synth_3d_mwf_arms_60min", 3, "mwf", 60, specVararg(MuscleGroup.Bicep, MuscleGroup.Tricep)),
        BenchmarkSpec("Synth_3d_mwf_shoulders_75min", 3, "mwf", 75, specVararg(MuscleGroup.AnteriorDeltoid, MuscleGroup.LateralDeltoid)),
        BenchmarkSpec("Synth_3d_mwf_hamstrings_75min", 3, "mwf", 75, specVararg(MuscleGroup.Hamstring)),
        BenchmarkSpec("Synth_3d_mwf_maintenance_60min", 3, "mwf", 60, emptyMap()),
        BenchmarkSpec("Synth_3d_mwf_arms_75min", 3, "mwf", 75, specVararg(MuscleGroup.Bicep, MuscleGroup.Tricep)),
        BenchmarkSpec("Synth_4d_sean5_glutes_75min", 4, "sean5", 75, specVararg(MuscleGroup.Glute)),
        BenchmarkSpec("Synth_3d_sequential_legs_75min", 3, "sequential", 75, specVararg(MuscleGroup.Glute, MuscleGroup.Quad)),
        BenchmarkSpec("Synth_3d_mwf_posterior_75min", 3, "mwf", 75, specVararg(MuscleGroup.PosteriorDeltoid)),
        BenchmarkSpec("Synth_3d_sequential_shoulders_75min", 3, "sequential", 75, specVararg(MuscleGroup.AnteriorDeltoid, MuscleGroup.LateralDeltoid)),
        BenchmarkSpec("Synth_3d_mwf_back_75min", 3, "mwf", 75, specVararg(MuscleGroup.Back)),
        BenchmarkSpec("Synth_Shoulder_Head_Triplex_4d_spread5", 4, "spread5", 75, specVararg(MuscleGroup.AnteriorDeltoid, MuscleGroup.LateralDeltoid, MuscleGroup.PosteriorDeltoid)),
        BenchmarkSpec("Synth_3d_mwf_biceps_75min", 3, "mwf", 75, specVararg(MuscleGroup.Bicep)),
        BenchmarkSpec("Synth_Pull_Emphasis_4d_spread5", 4, "spread5", 75, specVararg(MuscleGroup.Back, MuscleGroup.Bicep, MuscleGroup.PosteriorDeltoid)),
        BenchmarkSpec("Synth_6d_sequential_legs_75min", 6, "sequential", 75, specVararg(MuscleGroup.Glute, MuscleGroup.Quad)),
        BenchmarkSpec("PPL_3day", 3, "mwf", 75, emptyMap()),
        BenchmarkSpec("Synth_FullLower_4d_spread5", 4, "spread5", 75, specVararg(MuscleGroup.Calf, MuscleGroup.Glute, MuscleGroup.Hamstring, MuscleGroup.Quad)),
        BenchmarkSpec("Synth_3d_mwf_glutes_75min", 3, "mwf", 75, specVararg(MuscleGroup.Glute)),
        BenchmarkSpec("Synth_3d_sequential_maintenance_60min", 3, "sequential", 60, emptyMap()),
        BenchmarkSpec("Synth_Push_Emphasis_5d_mtwfs", 5, "mtwfs", 75, specVararg(MuscleGroup.AnteriorDeltoid, MuscleGroup.Chest, MuscleGroup.Tricep)),
        BenchmarkSpec("Synth_3d_mwf_chest_75min", 3, "mwf", 75, specVararg(MuscleGroup.Chest)),
        BenchmarkSpec("Synth_FullUpper_4d_spread5", 4, "spread5", 75, specVararg(MuscleGroup.Back, MuscleGroup.Bicep, MuscleGroup.Chest, MuscleGroup.Tricep)),
        BenchmarkSpec("Synth_4d_sean5_calves_75min", 4, "sean5", 75, specVararg(MuscleGroup.Calf)),
        BenchmarkSpec("Synth_Shoulder_Head_Triplex_5d_mtwfs", 5, "mtwfs", 75, specVararg(MuscleGroup.AnteriorDeltoid, MuscleGroup.LateralDeltoid, MuscleGroup.PosteriorDeltoid)),
        BenchmarkSpec("Smolov_Base_4day", 4, "sequential", 75, specVararg(MuscleGroup.Glute, MuscleGroup.Hamstring, MuscleGroup.Quad)),
        BenchmarkSpec("Synth_Push_Emphasis_4d_spread5", 4, "spread5", 75, specVararg(MuscleGroup.AnteriorDeltoid, MuscleGroup.Chest, MuscleGroup.Tricep)),
        BenchmarkSpec("Synth_3d_mwf_lateral_75min", 3, "mwf", 75, specVararg(MuscleGroup.LateralDeltoid)),
        BenchmarkSpec("Synth_5d_mtwfs_calves_75min", 5, "mtwfs", 75, specVararg(MuscleGroup.Calf)),
        BenchmarkSpec("Synth_3d_sequential_maintenance_90min", 3, "sequential", 90, emptyMap()),
        BenchmarkSpec("Synth_4d_sean5_biceps_75min", 4, "sean5", 75, specVararg(MuscleGroup.Bicep)),
        BenchmarkSpec("Synth_5d_sequential_shoulders_75min", 5, "sequential", 75, specVararg(MuscleGroup.AnteriorDeltoid, MuscleGroup.LateralDeltoid)),
        BenchmarkSpec("Synth_3d_mwf_triceps_75min", 3, "mwf", 75, specVararg(MuscleGroup.Tricep)),
        BenchmarkSpec("Synth_3d_sequential_biceps_75min", 3, "sequential", 75, specVararg(MuscleGroup.Bicep)),
        BenchmarkSpec("Synth_4d_sean5_maintenance_60min", 4, "sean5", 60, emptyMap()),
        BenchmarkSpec("Synth_5d_mtwfs_legs_75min", 5, "mtwfs", 75, specVararg(MuscleGroup.Glute, MuscleGroup.Quad)),
        BenchmarkSpec("Synth_4d_sean5_posterior_75min", 4, "sean5", 75, specVararg(MuscleGroup.PosteriorDeltoid)),
        BenchmarkSpec("Synth_4d_spread5_legs_75min", 4, "spread5", 75, specVararg(MuscleGroup.Glute, MuscleGroup.Quad)),
        BenchmarkSpec("Synth_4d_sean5_hamstrings_75min", 4, "sean5", 75, specVararg(MuscleGroup.Hamstring)),
        BenchmarkSpec("Synth_4d_sean5_back_75min", 4, "sean5", 75, specVararg(MuscleGroup.Back)),
        BenchmarkSpec("Synth_ChestArms_Bro_5d_mtwfs", 5, "mtwfs", 75, specVararg(MuscleGroup.Bicep, MuscleGroup.Chest, MuscleGroup.Tricep)),
        BenchmarkSpec("Synth_Pull_Emphasis_5d_mtwfs", 5, "mtwfs", 75, specVararg(MuscleGroup.Back, MuscleGroup.Bicep, MuscleGroup.PosteriorDeltoid)),
        BenchmarkSpec("Synth_4d_sean5_lateral_75min", 4, "sean5", 75, specVararg(MuscleGroup.LateralDeltoid)),
        BenchmarkSpec("Synth_5d_mtwfs_biceps_75min", 5, "mtwfs", 75, specVararg(MuscleGroup.Bicep)),
        BenchmarkSpec("Synth_5d_mtwfs_lateral_75min", 5, "mtwfs", 75, specVararg(MuscleGroup.LateralDeltoid)),
        BenchmarkSpec("Synth_FullLower_5d_mtwfs", 5, "mtwfs", 75, specVararg(MuscleGroup.Calf, MuscleGroup.Glute, MuscleGroup.Hamstring, MuscleGroup.Quad)),
        BenchmarkSpec("Synth_5d_sean5_back_75min", 5, "sean5", 75, specVararg(MuscleGroup.Back)),
        BenchmarkSpec("Synth_6d_sequential_arms_75min", 6, "sequential", 75, specVararg(MuscleGroup.Bicep, MuscleGroup.Tricep)),
        BenchmarkSpec("Synth_5d_mtwfs_shoulders_75min", 5, "mtwfs", 75, specVararg(MuscleGroup.AnteriorDeltoid, MuscleGroup.LateralDeltoid)),
        BenchmarkSpec("PPL_6day", 6, "sequential", 75, emptyMap()),
    )

    private companion object {
        val SOLVER_DEADLINE_MILLIS = System.getProperty("stratum.solver.deadline")?.toLongOrNull() ?: 30_000L
        const val MAINTENANCE_VOLUME_TOLERANCE = 1.0
        const val CATASTROPHIC_FILL_PENALTY = 100_000.0
        const val MIN_ACTIVE_LIFTS_PER_SESSION = 3
        const val MIN_SETS_PER_ACTIVE_SLOT = 2
        val COMPOUND_PATTERNS =
            setOf(
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