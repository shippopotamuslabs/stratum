@file:Suppress("MagicNumber")

package com.stratum.domain.program.generation.reference

import com.stratum.domain.program.Workout
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.program.generation.context.LiftContext
import com.stratum.domain.program.generation.context.PRIMARY_SET_CREDIT
import com.stratum.domain.program.generation.context.ProgramGenerationContext
import com.stratum.domain.program.generation.context.SECONDARY_SET_CREDIT
import com.stratum.domain.program.generation.draft.ProgramWorkoutDraft
import com.stratum.domain.program.generation.prescription.classifyLiftStressClass
import com.stratum.domain.program.generation.prescription.dominantStressClass
import com.stratum.domain.program.generation.prescription.recoveryStressClassForContributor
import com.stratum.domain.program.schedule.TrainingTimeline

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
    earlierPrimaryDose: Double = earlierDose,
): Int {
    if (earlierDose < MUSCLE_RECOVERY_MIN_SCHEDULING_DOSE ||
        laterDose < MUSCLE_RECOVERY_MIN_SCHEDULING_DOSE
    ) {
        return 0
    }
    return minimumRecoveryCalendarGap(
        MuscleSessionStress(
            fractionalSets = earlierDose,
            stressClass = earlierStressClass,
            muscle = muscle,
            muscleLengthBias = earlierMuscleLengthBias,
            primaryFractionalSets = earlierPrimaryDose,
        ),
    )
}

/**
 * Wrap-around gap between slot indices on a cycle ring.
 * Prefer [com.stratum.domain.program.schedule.TrainingTimeline.gap] when available.
 *
 * @param cycleLength full ring length (default 7 for classic Mon–Sun layouts)
 */
fun calendarGapBetweenDays(earlierDay: Int, laterDay: Int, cycleLength: Int = 7): Int {
    require(cycleLength > 0) { "cycleLength must be positive, was $cycleLength" }
    return (laterDay - earlierDay + cycleLength) % cycleLength
}

/** @see [calendarGapBetweenDays] */
fun calendarDayIndexGap(
    earlierDayIndex: Int,
    laterDayIndex: Int,
    cycleLength: Int = 7,
): Int = calendarGapBetweenDays(earlierDayIndex, laterDayIndex, cycleLength)

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
        earlierPrimaryDose = earlier.primaryFractionalSets,
    )
    return required > 0 && calendarGap < required
}

/**
 * Ordered (earlier, later) training-slot pairs to evaluate recovery spacing, including cycle wrap
 * last → first when wrap gap is in `1 until cycleLength`.
 *
 * Prefer [com.stratum.domain.program.schedule.TrainingTimeline.pairsForRecoveryCheck].
 */
fun trainingDayPairsForRecoveryCheck(
    trainingDayIndices: List<Int>,
    cycleLength: Int = 7,
): List<Pair<Int, Int>> =
    com.stratum.domain.program.schedule.TrainingTimeline(
        cycleLength = cycleLength,
        trainingSlotIndices = trainingDayIndices.sorted(),
    ).pairsForRecoveryCheck()

/**
 * Ordered training-slot pairs with gap in `1..maxGap` (inclusive), including cycle wrap.
 * Used for gap-1 adjacent forbids. Gap-2 heavy-axial hard spacing applies to primary movers only.
 *
 * Prefer [com.stratum.domain.program.schedule.TrainingTimeline.pairsWithMaxGap].
 */
fun calendarDayPairsWithMaxGap(
    trainingDayIndices: List<Int>,
    maxGap: Int,
    cycleLength: Int = 7,
): List<Pair<Int, Int>> =
    com.stratum.domain.program.schedule.TrainingTimeline(
        cycleLength = cycleLength,
        trainingSlotIndices = trainingDayIndices.sorted(),
    ).pairsWithMaxGap(maxGap)

private fun contributorsForMuscle(
    setCount: Int,
    meta: LiftContext?,
    muscle: String,
): List<Pair<LiftStressClass, Double>> {
    if (setCount == 0 || meta == null) return emptyList()
    val stressClass = classifyLiftStressClass(meta)
    val contributors = mutableListOf<Pair<LiftStressClass, Double>>()
    val muscleGroup = runCatching { MuscleGroup.valueOf(muscle) }.getOrNull()
    if (muscleGroup != null && muscleGroup in meta.primaryMuscles) {
        contributors +=
            recoveryStressClassForContributor(stressClass, isPrimaryContributor = true) to
                setCount * PRIMARY_SET_CREDIT
    }
    if (muscleGroup != null && muscleGroup in meta.secondaryMuscles && muscleGroup !in meta.primaryMuscles) {
        contributors +=
            recoveryStressClassForContributor(stressClass, isPrimaryContributor = false) to
                setCount * SECONDARY_SET_CREDIT
    }
    return contributors
}
