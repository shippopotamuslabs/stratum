@file:Suppress("LoopWithTooManyJumpStatements")

package com.stratum.domain.program.generation.integration

import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.onboarding.MuscleGroup
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
import com.stratum.domain.program.generation.context.resolveTrainingTimeline
import com.stratum.domain.program.generation.reference.muscleRecoveryGapViolated
import com.stratum.domain.program.generation.swap.alternativesForLift
import com.stratum.domain.program.generation.volume.TrainingDomain
import com.stratum.domain.program.generation.volume.muscleTrainingDomain
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.program.schedule.CycleSlot
import com.stratum.domain.program.schedule.MaterializedCycleSpace
import com.stratum.domain.program.schedule.TrainingTimeline
import com.stratum.domain.program.schedule.resolvedCycleSlots

private val LOWER_FORBIDDEN_PRIMARY_PATTERNS = setOf(
    MovementPattern.VerticalPull,
    MovementPattern.HorizontalPull,
    MovementPattern.HorizontalPress,
    MovementPattern.VerticalPress,
    MovementPattern.InclinePress,
)

/**
 * Lift selection runs in generation cycle order; recovery adjacency uses **cycle slot indices**
 * from [Program.resolvedCycleSlots] (not weekday ordinals). Reconcile adjacent training sessions
 * so the same exercise does not appear on back-to-back training slots.
 *
 * Also repairs **metadata-meaningful adjacent same-muscle exposure** (G7b): a selected lift can
 * carry a recovery-relevant secondary muscle (e.g. Hip Thrust → Hamstring) that the solver's
 * pattern-credit accounting never saw. This pass swaps one offending lift in the later workout
 * for an alternative whose metadata does not meaningfully stress the shared muscle.
 */
fun resolveAdjacentCalendarLiftCollisions(
    program: Program,
    context: ProgramGenerationContext,
): Program {
    val slots = program.resolvedCycleSlots()
    val timeline =
        if (slots.isNotEmpty()) {
            TrainingTimeline.fromCycleSpace(
                MaterializedCycleSpace(
                    cycleLength = program.cycleLength,
                    restSlotIndices = program.restSlotIndices,
                ),
            )
        } else {
            context.resolveTrainingTimeline()
        }
    val trainingSlots =
        if (slots.isNotEmpty()) {
            slots.filterIsInstance<CycleSlot.Training>()
        } else {
            // Fallback: position-order when cycle-space is unavailable.
            program.workouts
                .sortedBy { workout -> workout.position }
                .mapIndexed { index, workout ->
                    CycleSlot.Training(slotIndex = index, workout = workout)
                }
        }
    if (trainingSlots.size < 2) return program

    val slotIndexByWorkoutId: Map<WorkoutId, Int> =
        trainingSlots.associate { training -> training.workout.id to training.slotIndex }

    val updatedById = program.workouts.associateBy { it.id }.toMutableMap()
    var previousLiftIds = emptySet<String>()
    var previousWorkout: Workout? = null
    var previousMuscleStress: Map<MuscleGroup, MuscleSessionStress> = emptyMap()

    fun repairAgainstPrevious(workout: Workout): Workout {
        val current = updatedById.getValue(workout.id)
        val afterIdPass =
            resolveWorkoutCollisions(
                workout = current,
                previousLiftIds = previousLiftIds,
                context = context,
            )
        return resolveMetadataMuscleExposure(
            workout = afterIdPass,
            previousWorkout = previousWorkout,
            previousMuscleStress = previousMuscleStress,
            context = context,
            timeline = timeline,
            slotIndexByWorkoutId = slotIndexByWorkoutId,
        )
    }

    for (training in trainingSlots) {
        val afterMetadataPass = repairAgainstPrevious(training.workout)
        updatedById[training.workout.id] = afterMetadataPass
        previousLiftIds = afterMetadataPass.workoutLifts.map { lift -> lift.liftId.value }.toSet()
        previousWorkout = afterMetadataPass
        previousMuscleStress = workoutMetadataMuscleSessionStress(afterMetadataPass, context)
    }

    // Cycle wrap: last training slot → first (same recovery geometry as mid-cycle pairs).
    if (trainingSlots.size >= 2) {
        val first = trainingSlots.first().workout
        val lastId = trainingSlots.last().workout.id
        val last = updatedById.getValue(lastId)
        previousLiftIds = last.workoutLifts.map { lift -> lift.liftId.value }.toSet()
        previousWorkout = last
        previousMuscleStress = workoutMetadataMuscleSessionStress(last, context)
        updatedById[first.id] = repairAgainstPrevious(first)
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
                            isReplacementAppropriateForWorkout(
                                workout,
                                context.liftsById[candidate],
                                context,
                            )
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
    previousMuscleStress: Map<MuscleGroup, MuscleSessionStress>,
    context: ProgramGenerationContext,
    timeline: TrainingTimeline,
    slotIndexByWorkoutId: Map<WorkoutId, Int>,
): Workout {
    val slotPair = recoverySlotPair(previousWorkout, workout, slotIndexByWorkoutId) ?: return workout
    if (previousMuscleStress.isEmpty()) return workout
    val calendarGap = timeline.gap(slotPair.first, slotPair.second)
    val currentStress = workoutMetadataMuscleSessionStress(workout, context)
    val conflictMuscles =
        if (calendarGap <= 0) {
            emptyList()
        } else {
            previousMuscleStress.keys.filter { muscle ->
                val earlierStress = previousMuscleStress[muscle] ?: return@filter false
                val laterDose = currentStress[muscle]?.fractionalSets ?: 0.0
                muscleRecoveryGapViolated(earlierStress, laterDose, calendarGap)
            }
        }
    if (conflictMuscles.isEmpty()) return workout

    val lifts = workout.workoutLifts.toMutableList()
    val currentLiftIds = lifts.map { lift -> lift.liftId.value }.toMutableSet()
    val resolvedMuscles = mutableSetOf<MuscleGroup>()

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
                        isReplacementAppropriateForWorkout(
                            workout,
                            context.liftsById[candidate],
                            context,
                        ) &&
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

/** Earlier/later cycle slot indices for adjacent training workouts on the cycle ring. */
private fun recoverySlotPair(
    previous: Workout?,
    current: Workout,
    slotIndexByWorkoutId: Map<WorkoutId, Int>,
): Pair<Int, Int>? {
    val earlier = previous?.let { workout -> slotIndexByWorkoutId[workout.id] } ?: return null
    val later = slotIndexByWorkoutId[current.id] ?: return null
    return earlier to later
}

private fun replacementStressesMuscle(lift: LiftContext?, muscle: MuscleGroup): Boolean {
    if (lift == null) return true
    return muscle in lift.primaryMuscles || muscle in lift.secondaryMuscles
}

/** Metadata muscle session stress for a domain [Workout] (mirrors [computeWorkoutMuscleSessionStress]). */
private fun workoutMetadataMuscleSessionStress(
    workout: Workout,
    context: ProgramGenerationContext,
): Map<MuscleGroup, MuscleSessionStress> {
    val doses = workoutMetadataMuscleVolume(workout, context)
    val classContributors = mutableMapOf<MuscleGroup, MutableList<Pair<LiftStressClass, Double>>>()
    // Length bias of the primary contributor for each muscle (drives the KneeFlexion
    // lengthened-state surcharge). Secondary contributors do not override the primary's bias.
    val lengthBiasByMuscle = mutableMapOf<MuscleGroup, String>()
    for (lift in workout.workoutLifts) {
        val setCount = lift.sets.size
        if (setCount == 0) continue
        val meta = context.liftsById[lift.liftId.value] ?: continue
        val stressClass = classifyLiftStressClass(meta)
        for (muscle in meta.primaryMuscles) {
            classContributors.getOrPut(muscle) { mutableListOf() }
                .add(stressClass to setCount * PRIMARY_SET_CREDIT)
            lengthBiasByMuscle.putIfAbsent(muscle, meta.muscleLengthBias?.name ?: "MidRange")
        }
        for (muscle in meta.secondaryMuscles) {
            if (muscle in meta.primaryMuscles) continue
            classContributors.getOrPut(muscle) { mutableListOf() }
                .add(stressClass to setCount * SECONDARY_SET_CREDIT)
        }
    }
    return doses.mapValues { (muscle, dose) ->
        val primaryOnly =
            workout.workoutLifts.sumOf { lift ->
                val setCount = lift.sets.size
                if (setCount == 0) return@sumOf 0.0
                val meta = context.liftsById[lift.liftId.value] ?: return@sumOf 0.0
                if (muscle in meta.primaryMuscles) setCount * PRIMARY_SET_CREDIT else 0.0
            }
        MuscleSessionStress(
            fractionalSets = dose,
            stressClass = dominantStressClass(classContributors[muscle].orEmpty()),
            muscle = muscle.name,
            muscleLengthBias = lengthBiasByMuscle[muscle],
            primaryFractionalSets = primaryOnly,
        )
    }
}

/** Metadata muscle volume for a domain [Workout] (mirrors [computeWorkoutMuscleVolume] truth). */
private fun workoutMetadataMuscleVolume(
    workout: Workout,
    context: ProgramGenerationContext,
): Map<MuscleGroup, Double> {
    val totals = mutableMapOf<MuscleGroup, Double>()
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

/**
 * Whether [lift] is an appropriate collision/exposure replacement for [workout].
 *
 * Uses **existing lift metadata** (primary-muscle training domain), not workout names.
 * Names are polish (LLM rename) and must not gate recovery/variety behavior.
 */
private fun isReplacementAppropriateForWorkout(
    workout: Workout,
    lift: LiftContext?,
    context: ProgramGenerationContext,
): Boolean {
    if (lift == null) return false
    if (!workoutIsLowerDominant(workout, context)) return true
    return lift.movementPattern !in LOWER_FORBIDDEN_PRIMARY_PATTERNS
}

/** True when primary-muscle credits on the workout skew lower (or exclusively lower). */
private fun workoutIsLowerDominant(
    workout: Workout,
    context: ProgramGenerationContext,
): Boolean {
    var lower = 0
    var upper = 0
    for (workoutLift in workout.workoutLifts) {
        val meta = context.liftsById[workoutLift.liftId.value] ?: continue
        for (muscle in meta.primaryMuscles) {
            when (muscleTrainingDomain(muscle.name)) {
                TrainingDomain.Lower -> lower++
                TrainingDomain.Upper -> upper++
            }
        }
    }
    if (lower == 0 && upper == 0) return false
    return lower >= upper
}
