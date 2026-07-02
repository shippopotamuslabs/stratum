@file:Suppress("MagicNumber", "ReturnCount", "LoopWithTooManyJumpStatements", "MatchingDeclarationName", "ComplexCondition", "MaxLineLength")

package com.stratum.domain.program.generation.integration

import com.stratum.domain.program.generation.context.LiftContext
import com.stratum.domain.program.generation.context.PRIMARY_SET_CREDIT
import com.stratum.domain.program.generation.context.ProgramGenerationContext
import com.stratum.domain.program.generation.draft.ProgramDraft
import com.stratum.domain.program.generation.draft.ProgramLiftDraft
import com.stratum.domain.program.generation.draft.ProgramWorkoutDraft
import com.stratum.domain.program.generation.reference.MAJOR_COMPOUND_PATTERNS
import com.stratum.domain.program.generation.reference.MIN_SPECIALIZED_DIRECT_SETS
import com.stratum.domain.program.generation.reference.SESSION_MUSCLE_SET_CAP
import com.stratum.domain.program.generation.reference.SETS_PER_LIFT_CAP
import com.stratum.domain.program.generation.reference.calendarDayIndexGap
import com.stratum.domain.program.generation.reference.muscleRecoveryGapViolated
import com.stratum.domain.program.generation.reference.sessionHardSetCapForLength
import com.stratum.domain.program.generation.reference.trainingDayPairsForRecoveryCheck
import com.stratum.domain.program.generation.split.trainingWeekdayIndices
import com.stratum.domain.program.generation.validation.computeWorkoutMuscleSessionStress
import com.stratum.domain.program.generation.validation.computeWorkoutMuscleVolume
import com.stratum.domain.program.generation.validation.validateProgramMetrics
import com.stratum.domain.program.generation.volume.WeeklyVolumeTarget

private const val LOWER_ANCHOR_MIN_SETS = 2
private const val MIN_SETS_PER_LIFT = 2

private val LOWER_LEG_ANCHOR_PATTERNS = setOf("SquatPattern", "HipHinge")

private val ISOLATION_PATTERNS = setOf(
    "ElbowFlexion",
    "ElbowExtension",
    "KneeFlexion",
    "KneeExtension",
    "PlantarFlexion",
    "ShoulderAbduction",
    "ShoulderHorizontalAbduction",
)

fun repairWeeklyVolume(
    program: ProgramDraft,
    @Suppress("UNUSED_PARAMETER") weeklyTargets: Map<String, WeeklyVolumeTarget>,
    context: ProgramGenerationContext,
): ProgramDraft {
    // Volume is owned by the CP-SAT synthesizer; integration only enforces hard caps, duration,
    // and metadata-truth antagonist balance (the solver's pattern-credit accounting can diverge
    // from actual lift metadata when a lift tags a muscle the pattern table doesn't credit). The
    // final adjacent-recovery pass reconciles the same divergence against the recovery rule: a
    // selected lift can tag a muscle the pattern-average under-credited, pushing realized metadata
    // volume above the meaningful threshold on two adjacent days the solver never forbade.
    // Antagonist rebalance runs last so recovery trims cannot leave chest > back (or quad > ham).
    return rebalanceAntagonistPairs(
        enforceCalendarAdjacentRecovery(
            trimWorkoutsToSessionDuration(
                trimSessionMuscleCreditCaps(program, context),
                context,
            ),
            context,
        ),
        context,
    )
}

fun trimWorkoutsToSessionDuration(
    program: ProgramDraft,
    context: ProgramGenerationContext,
): ProgramDraft {
    val setCap = sessionHardSetCapForLength(context.sessionLengthMinutes) ?: return program
    return program.copy(
        workouts = program.workouts.map { workout ->
            trimWorkoutToSessionDuration(workout, setCap, context)
        },
    )
}

fun trimSessionMuscleCreditCaps(
    program: ProgramDraft,
    context: ProgramGenerationContext,
): ProgramDraft {
    return program.copy(
        workouts = program.workouts.map { workout ->
            trimWorkoutMuscleCredits(workout, context)
        },
    )
}

private fun workoutHardSetCount(workout: ProgramWorkoutDraft): Int =
    workout.workoutLifts.orEmpty().sumOf { it.workoutLiftSets?.size ?: 0 }

private fun isProtectedLowerAnchorLift(
    @Suppress("UNUSED_PARAMETER") workout: ProgramWorkoutDraft,
    meta: LiftContext,
    setCount: Int,
): Boolean {
    if (meta.movementPattern !in LOWER_LEG_ANCHOR_PATTERNS) return false
    return setCount <= LOWER_ANCHOR_MIN_SETS
}

private fun trimWorkoutToSessionDuration(
    workout: ProgramWorkoutDraft,
    setCap: Int,
    context: ProgramGenerationContext,
): ProgramWorkoutDraft {
    var current = workout
    var guard = 0
    while (guard++ < 500 && workoutHardSetCount(current) > setCap) {
        val dropIndex = liftIndexToDropForSessionDuration(current, context, ISOLATION_PATTERNS) ?: break
        current = dropLastSetFromLiftAt(current, dropIndex)
    }
    return current
}

private fun liftIndexToDropForSessionDuration(
    workout: ProgramWorkoutDraft,
    context: ProgramGenerationContext,
    isolationPatterns: Set<String>,
): Int? {
    val specialized = context.specialization
        .filter { (_, level) -> level == "Specialize" }
        .keys

    data class Candidate(val index: Int, val score: Int)

    val candidates = buildList {
        workout.workoutLifts.orEmpty().forEachIndexed { index, lift ->
            val sets = lift.workoutLiftSets.orEmpty()
            if (sets.isEmpty()) return@forEachIndexed
            val meta = context.liftsById[lift.liftId.orEmpty()] ?: return@forEachIndexed
            if (isProtectedLowerAnchorLift(workout, meta, sets.size)) return@forEachIndexed

            val primarySpecialized = meta.primaryMuscles.any { it in specialized }
            val score = when {
                primarySpecialized && meta.movementPattern in isolationPatterns -> {
                    if (sets.size <= MIN_SPECIALIZED_DIRECT_SETS) return@forEachIndexed
                    4
                }
                meta.movementPattern in isolationPatterns -> 0
                meta.secondaryMuscles.any { it in specialized } -> 1
                meta.movementPattern in MAJOR_COMPOUND_PATTERNS -> if (sets.size > 1) 3 else 5
                else -> 2
            }
            add(Candidate(index, score))
        }
    }

    return candidates.minByOrNull { it.score }?.index
}

/**
 * Maintenance antagonist pairs to reconcile on metadata-truth weekly volume (same definition as
 * [com.stratum.domain.program.generation.selection.AntagonistMetadataBalance]). Joint-safer
 * direction: back ≥ chest, ham ≥ quad.
 */
private data class AntagonistPair(val weaker: String, val stronger: String)

private val METADATA_ANTAGONIST_PAIRS = listOf(
    AntagonistPair("Chest", "Back"),
    AntagonistPair("Quad", "Hamstring"),
)

private const val ANTAGONIST_BALANCE_TOLERANCE = 0.5

/**
 * Reconciles metadata-truth antagonist balance (quad/ham, chest/back) after lift selection and
 * per-session trims. The CP-SAT solver balances on pattern credits, but a selected lift can tag a
 * secondary muscle the pattern table doesn't credit (e.g. `barbell-sumo-deadlift` is HipHinge
 * pattern but tags Quad as secondary), so the realized metadata volume can diverge from the
 * solver's plan. This pass trims sets from lifts contributing to the wrong-way excess — preferring
 * lifts where the over-volume muscle is a secondary contributor — and redistributes the trimmed set
 * to an antagonist-muscle lift in the same workout when the session has capacity.
 */
private fun rebalanceAntagonistPairs(
    program: ProgramDraft,
    context: ProgramGenerationContext,
): ProgramDraft {
    var current = program
    var guard = 0
    while (guard++ < 200) {
        val weekly = metadataWeeklyVolume(current, context)
        val pair = METADATA_ANTAGONIST_PAIRS.firstOrNull { pair ->
            shouldRebalancePair(pair, context) &&
                (weekly[pair.weaker] ?: 0.0) - (weekly[pair.stronger] ?: 0.0) > ANTAGONIST_BALANCE_TOLERANCE
        } ?: return current

        val excess = (weekly[pair.weaker] ?: 0.0) - (weekly[pair.stronger] ?: 0.0)
        val result = trimAntagonistExcess(current, pair, excess, context) ?: return current
        current = result
    }
    return current
}

private fun shouldRebalancePair(pair: AntagonistPair, context: ProgramGenerationContext): Boolean {
    val weakerLevel = context.specialization[pair.weaker] ?: "Maintenance"
    val strongerLevel = context.specialization[pair.stronger] ?: "Maintenance"
    if (weakerLevel == "Omit" || strongerLevel == "Omit") return false
    return specializationTier(strongerLevel) >= specializationTier(weakerLevel)
}

private fun specializationTier(level: String): Int = when (level) {
    "Specialize" -> 2
    "Maintenance" -> 1
    "MinimumVolume" -> 0
    else -> 1
}

/**
 * Finds and trims the best candidate set to reduce [pair]'s wrong-way excess. Prefers a lift where
 * the weaker muscle (e.g. Quad) is a **secondary** contributor — trimming one set removes 0.5 weaker
 * volume while keeping the stronger muscle's primary contribution. Redistributes the set to a
 * stronger-muscle lift in the same workout if capacity allows.
 *
 * Returns null when no trimmable candidate exists (lower-body anchors ≤3 sets are protected).
 */
private fun trimAntagonistExcess(
    program: ProgramDraft,
    pair: AntagonistPair,
    @Suppress("UNUSED_PARAMETER") excess: Double,
    context: ProgramGenerationContext,
): ProgramDraft? {
    val sessionCap = sessionHardSetCapForLength(context.sessionLengthMinutes)
    val cap = SESSION_MUSCLE_SET_CAP

    data class Candidate(val workoutIndex: Int, val liftIndex: Int, val score: Int, val isSecondary: Boolean)

    val candidates = buildList {
        program.workouts.forEachIndexed { workoutIndex, workout ->
            workout.workoutLifts.orEmpty().forEachIndexed { liftIndex, lift ->
                val sets = lift.workoutLiftSets.orEmpty()
                if (sets.size <= MIN_SETS_PER_LIFT) return@forEachIndexed
                val meta = context.liftsById[lift.liftId.orEmpty()] ?: return@forEachIndexed
                val isPrimary = pair.weaker in meta.primaryMuscles
                val isSecondary = !isPrimary && pair.weaker in meta.secondaryMuscles
                if (!isPrimary && !isSecondary) return@forEachIndexed
                if (isProtectedLowerAnchorLift(workout, meta, sets.size)) return@forEachIndexed
                val score = when {
                    isSecondary -> 0
                    meta.movementPattern in ISOLATION_PATTERNS -> 1
                    meta.movementPattern in MAJOR_COMPOUND_PATTERNS -> 2
                    else -> 3
                }
                add(Candidate(workoutIndex, liftIndex, score, isSecondary))
            }
        }
    }

    val target = candidates.minByOrNull { it.score } ?: return null

    val workouts = program.workouts.toMutableList()
    val workout = workouts[target.workoutIndex]
    val lifts = workout.workoutLifts.orEmpty().toMutableList()
    val trimmedLift = lifts[target.liftIndex]
    val trimmedSets = trimmedLift.workoutLiftSets.orEmpty()
    if (trimmedSets.isEmpty()) return null

    // Try to redistribute the set to a stronger-muscle lift in the same workout.
    val redistributed = tryRedistributeSet(
        workout = workout,
        fromLiftIndex = target.liftIndex,
        pair = pair,
        sessionCap = sessionCap,
        muscleCap = cap,
        context = context,
    )

    lifts[target.liftIndex] = trimmedLift.copy(workoutLiftSets = trimmedSets.dropLast(1).toMutableList())
    if (redistributed != null) {
        lifts[redistributed] = lifts[redistributed].copy(
            workoutLiftSets = (lifts[redistributed].workoutLiftSets.orEmpty() +
                trimmedSets.last()).toMutableList(),
        )
    }
    workouts[target.workoutIndex] = workout.copy(workoutLifts = lifts)
    return program.copy(workouts = workouts)
}

/**
 * Finds a lift in [workout] whose primary muscle is the pair's stronger muscle (e.g. Hamstring)
 * that can accept an additional set without exceeding per-lift ([SETS_PER_LIFT_CAP]), per-muscle
 * ([SESSION_MUSCLE_SET_CAP]), or session-time caps. Returns its index, or null if none qualifies.
 */
private fun tryRedistributeSet(
    workout: ProgramWorkoutDraft,
    fromLiftIndex: Int,
    pair: AntagonistPair,
    sessionCap: Int?,
    muscleCap: Int,
    context: ProgramGenerationContext,
): Int? {
    val currentHardSets = workoutHardSetCount(workout)
    val sessionFull = sessionCap != null && currentHardSets >= sessionCap
    if (sessionFull) return null

    val volume = computeWorkoutMuscleVolume(workout, context)
    val lifts = workout.workoutLifts.orEmpty()
    for (index in lifts.indices) {
        if (index == fromLiftIndex) continue
        val lift = lifts[index]
        val sets = lift.workoutLiftSets.orEmpty()
        if (sets.isEmpty()) continue
        val meta = context.liftsById[lift.liftId.orEmpty()] ?: continue
        if (pair.stronger !in meta.primaryMuscles) continue
        if (sets.size >= SETS_PER_LIFT_CAP) continue
        val currentMuscleVolume = volume[pair.stronger] ?: 0.0
        if (currentMuscleVolume + PRIMARY_SET_CREDIT > muscleCap + 0.01) continue
        return index
    }
    return null
}

private fun metadataWeeklyVolume(
    program: ProgramDraft,
    context: ProgramGenerationContext,
): Map<String, Double> {
    val totals = mutableMapOf<String, Double>()
    for (workout in program.workouts) {
        for ((muscle, dose) in computeWorkoutMuscleVolume(workout, context)) {
            totals[muscle] = (totals[muscle] ?: 0.0) + dose
        }
    }
    return totals
}

private fun trimWorkoutMuscleCredits(
    workout: ProgramWorkoutDraft,
    context: ProgramGenerationContext,
): ProgramWorkoutDraft {
    var current = workout
    var guard = 0
    while (guard++ < 500) {
        val volume = computeWorkoutMuscleVolume(current, context)
        val overEntry = volume.entries.firstOrNull { (_, credits) -> credits > SESSION_MUSCLE_SET_CAP + 0.01 }
            ?: return current
        val muscle = overEntry.key
        val isSpecializedMuscle = (context.specialization[muscle] ?: "Maintenance") == "Specialize"

        data class TrimCandidate(val index: Int, val score: Int)

        val candidates = buildList {
            current.workoutLifts.orEmpty().forEachIndexed { index, lift ->
                val sets = lift.workoutLiftSets.orEmpty()
                if (sets.isEmpty()) return@forEachIndexed
                val meta = context.liftsById[lift.liftId.orEmpty()] ?: return@forEachIndexed
                val isPrimary = muscle in meta.primaryMuscles
                val isSecondary = !isPrimary && muscle in meta.secondaryMuscles
                if (!isPrimary && !isSecondary) return@forEachIndexed
                if (isProtectedLowerAnchorLift(workout, meta, sets.size)) return@forEachIndexed

                if (isSpecializedMuscle && isPrimary && meta.movementPattern in ISOLATION_PATTERNS) {
                    if (sets.size <= MIN_SPECIALIZED_DIRECT_SETS) return@forEachIndexed
                }

                val score = when {
                    isSpecializedMuscle -> when {
                        isSecondary -> 0
                        meta.movementPattern in ISOLATION_PATTERNS -> 3
                        meta.movementPattern in MAJOR_COMPOUND_PATTERNS -> 2
                        else -> 1
                    }
                    isSecondary -> 3
                    meta.movementPattern in ISOLATION_PATTERNS -> 0
                    meta.movementPattern in MAJOR_COMPOUND_PATTERNS -> 2
                    else -> 1
                }
                add(TrimCandidate(index, score))
            }
        }

        val targetIndex = candidates.minByOrNull { it.score }?.index ?: return current
        val targetSets = current.workoutLifts.orEmpty().getOrNull(targetIndex)?.workoutLiftSets
        if (targetSets.isNullOrEmpty()) return current
        current = dropLastSetFromLiftAt(current, targetIndex)
    }
    return current
}

private fun dropLastSetFromLiftAt(workout: ProgramWorkoutDraft, liftIndex: Int): ProgramWorkoutDraft =
    updateLiftAt(workout, liftIndex) { lift ->
        lift.copy(workoutLiftSets = lift.workoutLiftSets.orEmpty().dropLast(1).toMutableList())
    }

private fun updateLiftAt(
    workout: ProgramWorkoutDraft,
    liftIndex: Int,
    transform: (ProgramLiftDraft) -> ProgramLiftDraft,
): ProgramWorkoutDraft {
    val lifts = workout.workoutLifts.orEmpty().mapIndexed { index, lift ->
        if (index == liftIndex) transform(lift) else lift
    }
    return workout.copy(workoutLifts = lifts.toMutableList())
}

/**
 * Enforces class-aware recovery spacing on **metadata truth** (the realized program), closing the
 * gap between the CP-SAT solver's pattern-credit approximation and the per-lift metadata volume the
 * user sees. Evaluates every training-day pair whose calendar gap is shorter than
 * [minimumRecoveryCalendarGap] for the earlier session's dose and dominant stress class.
 *
 * After lift selection and the per-session/antagonist repairs, this pass trims sets from the
 * violating muscle's contributors — preferring the *later* day (the day that violates the prior
 * day's recovery debt) and lifts where the muscle is a **secondary** contributor (trimming removes
 * 0.5/set while preserving the lift's primary contribution). Reuses the trim-candidate scoring
 * pattern from [trimAntagonistExcess] and [trimWorkoutMuscleCredits]. Pure trim — no redistribution,
 * since moving a set within the same workout could re-introduce the violation or push another muscle
 * over threshold.
 */
private fun enforceCalendarAdjacentRecovery(
    program: ProgramDraft,
    context: ProgramGenerationContext,
): ProgramDraft {
    val trainingDayIndices = trainingWeekdayIndices(context.weekdays)
    val recoveryPairs = trainingDayPairsForRecoveryCheck(trainingDayIndices)

    var current = program
    var guard = 0
    while (guard++ < 200) {
        val dayIndexToWorkoutIndex = mutableMapOf<Int, Int>()
        current.workouts.forEachIndexed { workoutIndex, workout ->
            val weekday = workout.weekday ?: return@forEachIndexed
            val dayIndex = trainingWeekdayIndices(listOf(weekday)).singleOrNull() ?: return@forEachIndexed
            dayIndexToWorkoutIndex[dayIndex] = workoutIndex
        }
        val stressByWorkoutIndex = current.workouts.mapIndexed { index, workout ->
            index to computeWorkoutMuscleSessionStress(workout, context)
        }.toMap()

        val violation = recoveryPairs.firstNotNullOfOrNull { (earlierDay, laterDay) ->
            val earlierIndex = dayIndexToWorkoutIndex[earlierDay] ?: return@firstNotNullOfOrNull null
            val laterIndex = dayIndexToWorkoutIndex[laterDay] ?: return@firstNotNullOfOrNull null
            val earlierStressByMuscle = stressByWorkoutIndex[earlierIndex].orEmpty()
            val laterStressByMuscle = stressByWorkoutIndex[laterIndex].orEmpty()
            val calendarGap = calendarDayIndexGap(earlierDay, laterDay)
            val muscle = (earlierStressByMuscle.keys + laterStressByMuscle.keys).firstOrNull { muscle ->
                val earlierStress = earlierStressByMuscle[muscle] ?: return@firstOrNull false
                val laterDose = laterStressByMuscle[muscle]?.fractionalSets ?: 0.0
                muscleRecoveryGapViolated(earlierStress, laterDose, calendarGap)
            } ?: return@firstNotNullOfOrNull null
            AdjacentRecoveryViolation(earlierIndex, laterIndex, muscle)
        } ?: return current

        val laterTarget = adjacentRecoveryTrimIndex(current.workouts[violation.laterIndex], violation.muscle, context)
        val target = if (laterTarget != null) {
            violation.laterIndex to laterTarget
        } else {
            val earlierTarget =
                adjacentRecoveryTrimIndex(current.workouts[violation.earlierIndex], violation.muscle, context)
            if (earlierTarget == null) return current
            violation.earlierIndex to earlierTarget
        }
        val (workoutIndex, liftIndex) = target
        current = current.copy(
            workouts = current.workouts.toMutableList().also { workouts ->
                workouts[workoutIndex] = dropLastSetFromLiftAt(workouts[workoutIndex], liftIndex)
            },
        )
    }
    return current
}

private data class AdjacentRecoveryViolation(
    val earlierIndex: Int,
    val laterIndex: Int,
    val muscle: String,
)

/**
 * Selects the lift index in [workout] whose last set is the best candidate to trim in order to reduce
 * [muscle]'s metadata volume below the meaningful threshold. Prefers lifts where the muscle is a
 * **secondary** contributor (a single set costs 0.5 muscle credit while preserving the lift's primary
 * contribution). Follows the scoring tiers of [trimAntagonistExcess] / [trimWorkoutMuscleCredits]:
 * secondary first, then isolation primary, then compound primary, then everything else.
 *
 * The recovery rule is harder than the antagonist/credit trims but must still honour the project's
 * no-one-set-lift invariant (≥2 sets per active lift), so this pass reuses [MIN_SETS_PER_LIFT] as its
 * floor: a violation is cleared by trimming across multiple contributors on the same day rather
 * than grinding a single lift to 1 set or removing it (0-set lifts are dropped on normalization,
 * which can violate the solver's session lift-count band). When no trimmable candidate remains on
 * either adjacent day, the violation is left for advisory validation. Lower-body anchors remain
 * protected (squat/hinge at ≤3 sets).
 */
private fun adjacentRecoveryTrimIndex(
    workout: ProgramWorkoutDraft,
    muscle: String,
    context: ProgramGenerationContext,
): Int? {
    data class Candidate(val index: Int, val score: Int)

    val candidates = buildList {
        workout.workoutLifts.orEmpty().forEachIndexed { index, lift ->
            val sets = lift.workoutLiftSets.orEmpty()
            if (sets.size <= MIN_SETS_PER_LIFT) return@forEachIndexed
            val meta = context.liftsById[lift.liftId.orEmpty()] ?: return@forEachIndexed
            val isPrimary = muscle in meta.primaryMuscles
            val isSecondary = !isPrimary && muscle in meta.secondaryMuscles
            if (!isPrimary && !isSecondary) return@forEachIndexed
            if (isProtectedLowerAnchorLift(workout, meta, sets.size)) return@forEachIndexed
            val score = when {
                isSecondary -> 0
                meta.movementPattern in ISOLATION_PATTERNS -> 1
                meta.movementPattern in MAJOR_COMPOUND_PATTERNS -> 2
                else -> 3
            }
            add(Candidate(index, score))
        }
    }
    return candidates.minByOrNull { it.score }?.index
}
