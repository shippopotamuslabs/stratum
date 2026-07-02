@file:Suppress("LoopWithTooManyJumpStatements")

package com.stratum.domain.program.generation.integration

import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.Weekday
import com.stratum.domain.program.Program
import com.stratum.domain.program.Workout
import com.stratum.domain.program.generation.context.LiftContext
import com.stratum.domain.program.generation.context.PRIMARY_SET_CREDIT
import com.stratum.domain.program.generation.context.ProgramGenerationContext
import com.stratum.domain.program.generation.context.SECONDARY_SET_CREDIT
import com.stratum.domain.program.generation.prescription.classifyLiftStressClass
import com.stratum.domain.program.generation.prescription.dominantStressClass
import com.stratum.domain.program.generation.reference.LiftStressClass
import com.stratum.domain.program.generation.reference.MuscleSessionStress
import com.stratum.domain.program.generation.reference.calendarDayIndexGap
import com.stratum.domain.program.generation.reference.muscleRecoveryGapViolated
import com.stratum.domain.program.generation.swap.alternativesForLift
import com.stratum.domain.program.generation.split.SessionTypeId
import com.stratum.domain.program.generation.split.inferSessionTypeFromWorkoutName

private val LOWER_FORBIDDEN_PRIMARY_PATTERNS = setOf(
    "VerticalPull",
    "HorizontalPull",
    "HorizontalPress",
    "VerticalPress",
    "InclinePress",
)

/**
 * Lift selection runs in generation cycle order, but the CP-SAT solver assigns weekdays afterward.
 * onto weekdays afterward. Reconcile adjacent calendar sessions so the same exercise does not
 * appear on back-to-back training days (e.g. lat pulldown on Upper Pull then Lower).
 *
 * Also repairs **metadata-meaningful adjacent same-muscle exposure** (G7b): a selected lift can
 * carry a recovery-relevant secondary muscle (e.g. Hip Thrust → Hamstring) that the solver's
 * pattern-credit accounting never saw, so two adjacent workouts can both meaningfully stress the
 * same muscle in the UI's eyes ([computeWorkoutMuscleVolume] truth) even when no liftId repeats.
 * This pass swaps one offending lift in the later workout for an alternative whose metadata does
 * not meaningfully stress the shared muscle. Set counts are preserved (liftId swap only).
 */
fun resolveAdjacentCalendarLiftCollisions(
    program: Program,
    context: ProgramGenerationContext,
): Program {
    val calendarOrder =
        program.workouts
            .filter { workout -> workout.weekday != null }
            .sortedBy { workout -> Weekday.entries.indexOf(workout.weekday) }
    if (calendarOrder.size < 2) return program

    val updatedById = program.workouts.associateBy { it.id }.toMutableMap()
    var previousLiftIds = emptySet<String>()
    var previousWorkout: Workout? = null
    var previousMuscleStress: Map<String, MuscleSessionStress> = emptyMap()

    for (workout in calendarOrder) {
        val current = updatedById.getValue(workout.id)
        val afterIdPass = resolveWorkoutCollisions(
            workout = current,
            previousLiftIds = previousLiftIds,
            context = context,
        )
        val afterMetadataPass = resolveMetadataMuscleExposure(
            workout = afterIdPass,
            previousWorkout = previousWorkout,
            previousMuscleStress = previousMuscleStress,
            context = context,
        )
        updatedById[workout.id] = afterMetadataPass
        previousLiftIds = afterMetadataPass.workoutLifts.map { lift -> lift.liftId.value }.toSet()
        previousWorkout = afterMetadataPass
        previousMuscleStress = workoutMetadataMuscleSessionStress(afterMetadataPass, context)
    }

    return program.copy(workouts = program.workouts.map { workout -> updatedById.getValue(workout.id) })
}

private fun resolveWorkoutCollisions(
    workout: Workout,
    previousLiftIds: Set<String>,
    context: ProgramGenerationContext,
): Workout {
    if (previousLiftIds.isEmpty()) return workout

    val lifts = workout.workoutLifts.toMutableList()
    val currentLiftIds = lifts.map { lift -> lift.liftId.value }.toMutableSet()

    for (index in lifts.indices) {
        val liftId = lifts[index].liftId.value
        if (liftId in previousLiftIds) {
            val replacement =
                alternativesForLift(context, liftId, emptyList())
                    .firstOrNull { candidate ->
                        candidate !in previousLiftIds &&
                            candidate !in currentLiftIds &&
                            isReplacementAppropriateForWorkout(workout, context.liftsById[candidate])
                    }
            if (replacement != null) {
                currentLiftIds.remove(liftId)
                currentLiftIds.add(replacement)
                lifts[index] = lifts[index].copy(liftId = LiftId(replacement))
            }
        }
    }

    return workout.copy(workoutLifts = lifts)
}

/**
 * For each muscle whose earlier-session recovery debt is violated by this workout's meaningful
 * exposure, swap one contributing lift for an alternative that does not meaningfully stress that
 * muscle. Only lifts in this workout are swap candidates.
 */
private fun resolveMetadataMuscleExposure(
    workout: Workout,
    previousWorkout: Workout?,
    previousMuscleStress: Map<String, MuscleSessionStress>,
    context: ProgramGenerationContext,
): Workout {
    if (previousWorkout == null || previousMuscleStress.isEmpty()) return workout
    val previousDayIndex = previousWorkout.weekday?.ordinal ?: return workout
    val currentDayIndex = workout.weekday?.ordinal ?: return workout
    val calendarGap = calendarDayIndexGap(previousDayIndex, currentDayIndex)
    val currentStress = workoutMetadataMuscleSessionStress(workout, context)
    val conflictMuscles = previousMuscleStress.keys
        .filter { muscle ->
            val earlierStress = previousMuscleStress[muscle] ?: return@filter false
            val laterDose = currentStress[muscle]?.fractionalSets ?: 0.0
            muscleRecoveryGapViolated(earlierStress, laterDose, calendarGap)
        }
    if (conflictMuscles.isEmpty()) return workout

    val lifts = workout.workoutLifts.toMutableList()
    val currentLiftIds = lifts.map { lift -> lift.liftId.value }.toMutableSet()
    val resolvedMuscles = mutableSetOf<String>()

    for (index in lifts.indices) {
        if (resolvedMuscles.size == conflictMuscles.size) break
        val liftId = lifts[index].liftId.value
        val meta = context.liftsById[liftId] ?: continue
        val stressedMuscles = meta.primaryMuscles + meta.secondaryMuscles
        val muscleHere = conflictMuscles.firstOrNull { it in stressedMuscles } ?: continue
        // Swap for an alternative that does not stress the conflict muscle (primary or secondary).
        val replacement =
            alternativesForLift(context, liftId, emptyList())
                .firstOrNull { candidate ->
                    candidate !in currentLiftIds &&
                        isReplacementAppropriateForWorkout(workout, context.liftsById[candidate]) &&
                        !replacementStressesMuscle(context.liftsById[candidate], muscleHere)
                }
        if (replacement != null) {
            currentLiftIds.remove(liftId)
            currentLiftIds.add(replacement)
            lifts[index] = lifts[index].copy(liftId = LiftId(replacement))
            resolvedMuscles += muscleHere
        }
    }

    return workout.copy(workoutLifts = lifts)
}

private fun replacementStressesMuscle(lift: LiftContext?, muscle: String): Boolean {
    if (lift == null) return true
    return muscle in lift.primaryMuscles || muscle in lift.secondaryMuscles
}

/** Metadata muscle session stress for a domain [Workout] (mirrors [computeWorkoutMuscleSessionStress]). */
private fun workoutMetadataMuscleSessionStress(
    workout: Workout,
    context: ProgramGenerationContext,
): Map<String, MuscleSessionStress> {
    val doses = workoutMetadataMuscleVolume(workout, context)
    val classContributors = mutableMapOf<String, MutableList<Pair<LiftStressClass, Double>>>()
    for (lift in workout.workoutLifts) {
        val setCount = lift.sets.size
        if (setCount == 0) continue
        val meta = context.liftsById[lift.liftId.value] ?: continue
        val stressClass = classifyLiftStressClass(meta)
        for (muscle in meta.primaryMuscles) {
            classContributors.getOrPut(muscle) { mutableListOf() }
                .add(stressClass to setCount * PRIMARY_SET_CREDIT)
        }
        for (muscle in meta.secondaryMuscles) {
            if (muscle in meta.primaryMuscles) continue
            classContributors.getOrPut(muscle) { mutableListOf() }
                .add(stressClass to setCount * SECONDARY_SET_CREDIT)
        }
    }
    return doses.mapValues { (muscle, dose) ->
        MuscleSessionStress(dose, dominantStressClass(classContributors[muscle].orEmpty()))
    }
}

/** Metadata muscle volume for a domain [Workout] (mirrors [computeWorkoutMuscleVolume] truth). */
private fun workoutMetadataMuscleVolume(
    workout: Workout,
    context: ProgramGenerationContext,
): Map<String, Double> {
    val totals = mutableMapOf<String, Double>()
    for (lift in workout.workoutLifts) {
        val setCount = lift.sets.size
        if (setCount == 0) continue
        val meta = context.liftsById[lift.liftId.value] ?: continue
        for (muscle in meta.primaryMuscles) {
            totals[muscle] = (totals[muscle] ?: 0.0) + setCount * PRIMARY_SET_CREDIT
        }
        for (muscle in meta.secondaryMuscles) {
            if (muscle in meta.primaryMuscles) continue
            totals[muscle] = (totals[muscle] ?: 0.0) + setCount * SECONDARY_SET_CREDIT
        }
    }
    return totals
}

private fun isReplacementAppropriateForWorkout(
    workout: Workout,
    lift: LiftContext?,
): Boolean {
    if (lift == null) return false
    val sessionType = inferSessionTypeFromWorkoutName(workout.name) ?: return true
    if (sessionType != SessionTypeId.Lower) return true
    return lift.movementPattern !in LOWER_FORBIDDEN_PRIMARY_PATTERNS
}
