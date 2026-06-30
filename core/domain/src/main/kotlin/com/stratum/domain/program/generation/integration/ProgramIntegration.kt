@file:Suppress("MagicNumber", "ReturnCount", "LoopWithTooManyJumpStatements", "MatchingDeclarationName", "ComplexCondition", "MaxLineLength")

package com.stratum.domain.program.generation.integration

import com.stratum.domain.program.SetType
import com.stratum.domain.program.generation.context.LiftContext
import com.stratum.domain.program.generation.context.ProgramGenerationContext
import com.stratum.domain.program.generation.draft.LiftSetDraft
import com.stratum.domain.program.generation.draft.ProgramDraft
import com.stratum.domain.program.generation.draft.ProgramLiftDraft
import com.stratum.domain.program.generation.draft.ProgramWorkoutDraft
import com.stratum.domain.program.generation.reference.MAJOR_COMPOUND_PATTERNS
import com.stratum.domain.program.generation.reference.MIN_SPECIALIZED_DIRECT_SETS
import com.stratum.domain.program.generation.reference.SESSION_MUSCLE_SET_CAP
import com.stratum.domain.program.generation.reference.SETS_PER_LIFT_CAP
import com.stratum.domain.program.generation.reference.sessionHardSetCapForLength
import com.stratum.domain.program.generation.selection.liftCatalogQualityAdjustment
import com.stratum.domain.program.generation.validation.computeWorkoutMuscleVolume
import com.stratum.domain.program.generation.validation.validateProgramMetrics
import com.stratum.domain.program.generation.volume.WeeklyVolumeTarget

private const val LOWER_ANCHOR_MIN_SETS = 3

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
    weeklyTargets: Map<String, WeeklyVolumeTarget>,
    context: ProgramGenerationContext,
): ProgramDraft {
    var current = program
    val report = validateProgramMetrics(current, context)
    val muscleVolume = report.summary.weeklyMuscleVolume
    for ((muscle, target) in weeklyTargets) {
        val volume = muscleVolume[muscle] ?: 0.0
        if (volume >= target.min && volume <= target.max) continue
        if (volume < target.min) {
            current = addSetsForMuscle(
                current,
                muscle,
                kotlin.math.ceil(target.min - volume).toInt(),
                context,
            )
        }
    }
    current = trimSessionMuscleCreditCaps(current, context)
    return trimWorkoutsToSessionDuration(current, context)
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
    workout: ProgramWorkoutDraft,
    meta: LiftContext,
    setCount: Int,
): Boolean {
    if (!workout.name.orEmpty().contains("Lower", ignoreCase = true)) return false
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

private fun addSetsForMuscle(
    program: ProgramDraft,
    muscle: String,
    count: Int,
    context: ProgramGenerationContext,
): ProgramDraft {
    data class Entry(
        val workoutIndex: Int,
        val liftIndex: Int,
        val primary: Boolean,
    )

    val candidates = buildList {
        program.workouts.forEachIndexed { workoutIndex, workout ->
            workout.workoutLifts.orEmpty().forEachIndexed { liftIndex, lift ->
                val meta = context.liftsById[lift.liftId.orEmpty()] ?: return@forEachIndexed
                val primary = muscle in meta.primaryMuscles
                val secondary = muscle in meta.secondaryMuscles
                if (primary || secondary) {
                    add(Entry(workoutIndex, liftIndex, primary))
                }
            }
        }
    }.sortedWith { a, b ->
        val primaryDiff = b.primary.compareTo(a.primary)
        if (primaryDiff != 0) return@sortedWith primaryDiff
        val aSets = program.workouts[a.workoutIndex].workoutLifts.orEmpty()[a.liftIndex].workoutLiftSets?.size ?: 0
        val bSets = program.workouts[b.workoutIndex].workoutLifts.orEmpty()[b.liftIndex].workoutLiftSets?.size ?: 0
        if (aSets != bSets) return@sortedWith aSets - bSets
        val aMeta = context.liftsById[
            program.workouts[a.workoutIndex].workoutLifts.orEmpty()[a.liftIndex].liftId.orEmpty(),
        ]
        val bMeta = context.liftsById[
            program.workouts[b.workoutIndex].workoutLifts.orEmpty()[b.liftIndex].liftId.orEmpty(),
        ]
        val aQuality = aMeta?.let { liftCatalogQualityAdjustment(it) } ?: 0.0
        val bQuality = bMeta?.let { liftCatalogQualityAdjustment(it) } ?: 0.0
        bQuality.compareTo(aQuality)
    }

    var current = program
    var remaining = count
    var idx = 0
    val maxSetsPerLift = SETS_PER_LIFT_CAP
    while (remaining > 0 && candidates.isNotEmpty() && idx < candidates.size * 8) {
        val entry = candidates[idx % candidates.size]
        val workout = current.workouts[entry.workoutIndex]
        val lift = workout.workoutLifts.orEmpty()[entry.liftIndex]
        val sets = lift.workoutLiftSets.orEmpty()
        if (sets.size >= maxSetsPerLift) {
            idx++
            continue
        }
        val setCap = sessionHardSetCapForLength(context.sessionLengthMinutes)
        if (setCap != null && workoutHardSetCount(workout) >= setCap) {
            idx++
            continue
        }
        val newSet = LiftSetDraft(
            position = sets.size,
            setType = SetType.Standard,
            repMin = 8,
            repMax = 12,
            rirGoal = 2,
            isTopSet = false,
        )
        val updatedWorkout = updateLiftAt(workout, entry.liftIndex) { existing ->
            existing.copy(workoutLiftSets = (sets + newSet).toMutableList())
        }
        val trial = current.copy(
            workouts = current.workouts.toMutableList().also {
                it[entry.workoutIndex] = updatedWorkout
            },
        )
        if (workoutExceedsSessionMuscleCap(updatedWorkout, context)) {
            idx++
            continue
        }
        current = trial
        remaining--
        idx++
    }
    return current
}

private fun workoutExceedsSessionMuscleCap(
    workout: ProgramWorkoutDraft,
    context: ProgramGenerationContext,
): Boolean {
    val volume = computeWorkoutMuscleVolume(workout, context)
    return volume.values.any { credits -> credits > SESSION_MUSCLE_SET_CAP + 0.01 }
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
