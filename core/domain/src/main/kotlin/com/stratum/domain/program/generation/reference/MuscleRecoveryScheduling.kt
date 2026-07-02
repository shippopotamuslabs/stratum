@file:Suppress("MagicNumber")

package com.stratum.domain.program.generation.reference

import com.stratum.domain.program.Workout
import com.stratum.domain.program.generation.context.LiftContext
import com.stratum.domain.program.generation.context.PRIMARY_SET_CREDIT
import com.stratum.domain.program.generation.context.ProgramGenerationContext
import com.stratum.domain.program.generation.context.SECONDARY_SET_CREDIT
import com.stratum.domain.program.generation.draft.ProgramWorkoutDraft
import com.stratum.domain.program.generation.prescription.classifyLiftStressClass
import com.stratum.domain.program.generation.prescription.dominantStressClass
import com.stratum.domain.program.generation.prescription.recoveryStressClassForContributor

/**
 * Dominant [LiftStressClass] for [muscle] in a session using lift metadata truth. Contributors
 * crediting ≥ 1.0 fractional sets may escalate the class; sub-1.0 secondaries do not.
 */
fun dominantStressClassForWorkout(
    workout: ProgramWorkoutDraft,
    muscle: String,
    context: ProgramGenerationContext,
): LiftStressClass =
    dominantStressClass(
        workout.workoutLifts.orEmpty().flatMap { lift ->
            contributorsForMuscle(lift.workoutLiftSets?.size ?: 0, context.liftsById[lift.liftId.orEmpty()], muscle)
        },
    )

/** Domain [Workout] overload for post-normalization lift-swap repair. */
fun dominantStressClassForWorkout(
    workout: Workout,
    muscle: String,
    context: ProgramGenerationContext,
): LiftStressClass =
    dominantStressClass(
        workout.workoutLifts.flatMap { lift ->
            contributorsForMuscle(lift.sets.size, context.liftsById[lift.liftId.value], muscle)
        },
    )

/**
 * Minimum calendar day-index gap between two meaningful sessions. Gates on both doses ≥
 * [MUSCLE_RECOVERY_MIN_SCHEDULING_DOSE]; debt is priced from the earlier session's dose and class.
 *
 * @param muscle the muscle receiving the dose; passed through to the muscle-mass penalty so
 *  isolation lifts on large compound muscles (Quad/Hamstring/Glute/Chest/Back) get the +12h
 *  Low-tier surcharge. `null` keeps the pre-penalty baseline.
 * @param earlierMuscleLengthBias length bias of the earlier session's contributing lift; drives
 *  the lengthened-state surcharge on `KneeFlexion` (seated curl). `null` = no surcharge.
 */
fun minimumRecoveryGapBetweenSessions(
    earlierDose: Double,
    laterDose: Double,
    earlierStressClass: LiftStressClass,
    muscle: String? = null,
    earlierMuscleLengthBias: String? = null,
): Int {
    if (earlierDose < MUSCLE_RECOVERY_MIN_SCHEDULING_DOSE ||
        laterDose < MUSCLE_RECOVERY_MIN_SCHEDULING_DOSE
    ) {
        return 0
    }
    return minimumRecoveryCalendarGap(
        MuscleSessionStress(earlierDose, earlierStressClass, muscle, earlierMuscleLengthBias),
    )
}

/** Wrap-around calendar gap between weekday indices (Mon=0 … Sun=6). */
fun calendarGapBetweenDays(earlierDay: Int, laterDay: Int): Int =
    (laterDay - earlierDay + 7) % 7

/** @see [calendarGapBetweenDays] */
fun calendarDayIndexGap(earlierDayIndex: Int, laterDayIndex: Int): Int =
    calendarGapBetweenDays(earlierDayIndex, laterDayIndex)

/**
 * True when both sessions meaningfully expose the muscle and [calendarGap] is shorter than the
 * recovery debt created by [earlier] (earlier-session semantics per literature alignment G8).
 */
fun muscleRecoveryGapViolated(
    earlier: MuscleSessionStress,
    laterDose: Double,
    calendarGap: Int,
): Boolean {
    val required = minimumRecoveryGapBetweenSessions(
        earlier.fractionalSets,
        laterDose,
        earlier.stressClass,
        muscle = earlier.muscle,
        earlierMuscleLengthBias = earlier.muscleLengthBias,
    )
    return required > 0 && calendarGap < required
}

/**
 * Ordered (earlier, later) training-day index pairs to evaluate recovery spacing, including the
 * Sun→Mon wrap when the user's layout trains on consecutive week-boundary days.
 */
fun trainingDayPairsForRecoveryCheck(trainingDayIndices: List<Int>): List<Pair<Int, Int>> {
    val sorted = trainingDayIndices.sorted()
    if (sorted.size < 2) return emptyList()
    val pairs = mutableListOf<Pair<Int, Int>>()
    for (earlierIndex in sorted.indices) {
        for (laterIndex in earlierIndex + 1 until sorted.size) {
            pairs += sorted[earlierIndex] to sorted[laterIndex]
        }
    }
    val wrapEarlier = sorted.last()
    val wrapLater = sorted.first()
    val wrapGap = calendarGapBetweenDays(wrapEarlier, wrapLater)
    if (wrapGap in 1 until 7) {
        pairs += wrapEarlier to wrapLater
    }
    return pairs
}

/**
 * Ordered training-day pairs with calendar gap in `1..maxGap` (inclusive), including week wrap.
 * Used for gap-1 adjacent forbids. Gap-2 heavy-axial hard spacing applies to primary movers only.
 */
fun calendarDayPairsWithMaxGap(trainingDayIndices: List<Int>, maxGap: Int): List<Pair<Int, Int>> {
    val sorted = trainingDayIndices.sorted()
    val pairs = mutableListOf<Pair<Int, Int>>()
    for (earlier in sorted) {
        for (later in sorted) {
            if (earlier == later) continue
            val gap = calendarGapBetweenDays(earlier, later)
            if (gap in 1..maxGap) pairs += earlier to later
        }
    }
    return pairs
}

private fun contributorsForMuscle(
    setCount: Int,
    meta: LiftContext?,
    muscle: String,
): List<Pair<LiftStressClass, Double>> {
    if (setCount == 0 || meta == null) return emptyList()
    val stressClass = classifyLiftStressClass(meta)
    val contributors = mutableListOf<Pair<LiftStressClass, Double>>()
    if (muscle in meta.primaryMuscles) {
        contributors +=
            recoveryStressClassForContributor(stressClass, isPrimaryContributor = true) to
                setCount * PRIMARY_SET_CREDIT
    }
    if (muscle in meta.secondaryMuscles && muscle !in meta.primaryMuscles) {
        contributors +=
            recoveryStressClassForContributor(stressClass, isPrimaryContributor = false) to
                setCount * SECONDARY_SET_CREDIT
    }
    return contributors
}
