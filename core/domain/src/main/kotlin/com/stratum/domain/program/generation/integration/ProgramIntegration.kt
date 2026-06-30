@file:Suppress("MagicNumber", "ReturnCount", "LoopWithTooManyJumpStatements", "MatchingDeclarationName", "ComplexCondition", "MaxLineLength")

package com.stratum.domain.program.generation.integration

import com.stratum.domain.program.generation.context.LiftContext
import com.stratum.domain.program.generation.context.ProgramGenerationContext
import com.stratum.domain.program.generation.draft.ProgramDraft
import com.stratum.domain.program.generation.draft.ProgramLiftDraft
import com.stratum.domain.program.generation.draft.ProgramWorkoutDraft
import com.stratum.domain.program.generation.reference.MAJOR_COMPOUND_PATTERNS
import com.stratum.domain.program.generation.reference.MIN_SPECIALIZED_DIRECT_SETS
import com.stratum.domain.program.generation.reference.SESSION_MUSCLE_SET_CAP
import com.stratum.domain.program.generation.reference.sessionHardSetCapForLength
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
    @Suppress("UNUSED_PARAMETER") weeklyTargets: Map<String, WeeklyVolumeTarget>,
    context: ProgramGenerationContext,
): ProgramDraft {
    // Volume is owned by the CP-SAT synthesizer; integration only enforces hard caps and duration.
    return trimWorkoutsToSessionDuration(
        trimSessionMuscleCreditCaps(program, context),
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
