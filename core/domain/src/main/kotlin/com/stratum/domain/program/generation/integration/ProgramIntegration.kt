@file:Suppress("MagicNumber", "ReturnCount", "LoopWithTooManyJumpStatements", "MatchingDeclarationName", "ComplexCondition", "MaxLineLength")

package com.stratum.domain.program.generation.integration

import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.onboarding.SpecializationLevel
import com.stratum.domain.program.generation.context.LiftContext
import com.stratum.domain.program.generation.context.ProgramGenerationContext
import com.stratum.domain.program.generation.draft.ProgramDraft
import com.stratum.domain.program.generation.draft.ProgramWorkoutDraft
import com.stratum.domain.lift.MAJOR_COMPOUND_PATTERNS
import com.stratum.domain.program.generation.reference.MIN_SPECIALIZED_DIRECT_SETS
import com.stratum.domain.program.generation.reference.sessionTrimHardSetsForLength
import com.stratum.domain.program.generation.volume.WeeklyVolumeTarget

private const val LOWER_ANCHOR_MIN_SETS = 2

private val LOWER_LEG_ANCHOR_PATTERNS = setOf(MovementPattern.SquatPattern, MovementPattern.HipHinge)

private val ISOLATION_PATTERNS = setOf(
    MovementPattern.ElbowFlexion,
    MovementPattern.ElbowExtension,
    MovementPattern.KneeFlexion,
    MovementPattern.KneeExtension,
    MovementPattern.PlantarFlexion,
    MovementPattern.ShoulderAbduction,
    MovementPattern.ShoulderHorizontalAbduction,
)

/**
 * Post-solver polish: **duration trim only**.
 *
 * Weekly volume, recovery spacing, and antagonist balance are owned by CP-SAT. Integration must
 * not re-litigate those. Intra-workout lift order (antagonist interleave) is handled at assemble
 * time in [com.stratum.domain.program.generation.build.assembleWorkouts] and does not change sets.
 *
 * Duration: trim only when a workout exceeds band max + grace (see [sessionTrimHardSetsForLength]).
 */
fun repairWeeklyVolume(
    program: ProgramDraft,
    @Suppress("UNUSED_PARAMETER") weeklyTargets: Map<String, WeeklyVolumeTarget>,
    context: ProgramGenerationContext,
): ProgramDraft = trimWorkoutsToSessionDuration(program, context)

fun trimWorkoutsToSessionDuration(
    program: ProgramDraft,
    context: ProgramGenerationContext,
): ProgramDraft {
    val setCap = sessionTrimHardSetsForLength(context.sessionLengthMinutes) ?: return program
    return program.copy(
        workouts = program.workouts.map { workout ->
            trimWorkoutToSessionDuration(workout, setCap, context)
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
    isolationPatterns: Set<MovementPattern>,
): Int? {
    val specialized = context.specialization
        .filter { (_, level) -> level == SpecializationLevel.Specialize }
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

private fun dropLastSetFromLiftAt(workout: ProgramWorkoutDraft, liftIndex: Int): ProgramWorkoutDraft =
    updateLiftAt(workout, liftIndex) { lift ->
        lift.copy(workoutLiftSets = lift.workoutLiftSets.orEmpty().dropLast(1).toMutableList())
    }

private fun updateLiftAt(
    workout: ProgramWorkoutDraft,
    liftIndex: Int,
    transform: (com.stratum.domain.program.generation.draft.ProgramLiftDraft) ->
    com.stratum.domain.program.generation.draft.ProgramLiftDraft,
): ProgramWorkoutDraft {
    val lifts = workout.workoutLifts.orEmpty().toMutableList()
    if (liftIndex !in lifts.indices) return workout
    lifts[liftIndex] = transform(lifts[liftIndex])
    return workout.copy(workoutLifts = lifts)
}
