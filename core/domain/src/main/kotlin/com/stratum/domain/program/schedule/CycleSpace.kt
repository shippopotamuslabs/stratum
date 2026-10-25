package com.stratum.domain.program.schedule

import com.stratum.domain.onboarding.Weekday
import com.stratum.domain.program.Workout

/**
 * Pure description of one cycle: [cycleLength] slots, of which [restSlotIndices] are rest and the
 * rest are filled by [workouts] in position order.
 *
 * Invariant: `workouts.size + restSlotIndices.size == cycleLength` and every rest index is in
 * `[0, cycleLength)`.
 */
/**
 * Shared cycle-geometry rule (STR-167): the one definition of a valid cycle shape,
 * enforced by `Program`, [CycleSpace], `MaterializedCycleSpace`, and
 * `GenerationCycleHints` alike — a positive [cycleLength] with every rest index
 * inside the cycle.
 */
internal fun requireCycleGeometry(cycleLength: Int, restSlotIndices: Set<Int>) {
    require(cycleLength > 0) { "cycleLength must be positive, was $cycleLength" }
    require(restSlotIndices.all { index -> index in 0 until cycleLength }) {
        "restSlotIndices $restSlotIndices must all lie in [0, $cycleLength)"
    }
}

data class CycleSpace(
    val cycleLength: Int,
    val restSlotIndices: Set<Int>,
    val workouts: List<Workout>,
) {
    init {
        requireCycleGeometry(cycleLength, restSlotIndices)
        val trainingSlots = cycleLength - restSlotIndices.size
        require(workouts.size == trainingSlots) {
            "workout count ${workouts.size} must equal training slots $trainingSlots " +
                "(cycleLength=$cycleLength, rests=${restSlotIndices.size})"
        }
    }

    val trainingSlotCount: Int get() = workouts.size

    fun slots(): List<CycleSlot> = buildCycleSlots(cycleLength, restSlotIndices, workouts)
}

/**
 * Builds ordered [CycleSlot]s: walk `0 until cycleLength`, emit Rest when the index is in
 * [restSlotIndices], otherwise the next workout by [Workout.position].
 */
fun buildCycleSlots(
    cycleLength: Int,
    restSlotIndices: Set<Int>,
    workouts: List<Workout>,
): List<CycleSlot> {
    val space = CycleSpace(cycleLength, restSlotIndices, workouts)
    val sortedWorkouts = space.workouts.sortedBy { workout -> workout.position }
    var workoutCursor = 0
    return List(cycleLength) { slotIndex ->
        if (slotIndex in restSlotIndices) {
            CycleSlot.Rest(slotIndex)
        } else {
            val workout = sortedWorkouts[workoutCursor]
            workoutCursor += 1
            CycleSlot.Training(slotIndex = slotIndex, workout = workout)
        }
    }
}

/**
 * Rest slot indices for a classic Mon-start 7-day week: every [Weekday] not in
 * [trainingWeekdays] is rest. Training ordinals use [Weekday.ordinal] (Mon=0 … Sun=6).
 */
fun restSlotIndicesForSevenDayWeek(trainingWeekdays: Set<Weekday>): Set<Int> {
    require(trainingWeekdays.isNotEmpty()) { "trainingWeekdays must not be empty" }
    require(trainingWeekdays.size <= 7) { "trainingWeekdays size ${trainingWeekdays.size} > 7" }
    val trainingOrdinals = trainingWeekdays.map { weekday -> weekday.ordinal }.toSet()
    return (0 until 7).filterNot { ordinal -> ordinal in trainingOrdinals }.toSet()
}

/** Convenience: 7-slot cycle with rests = complement of [trainingWeekdays]. */
fun cycleSpaceForSevenDayWeek(
    trainingWeekdays: Set<Weekday>,
    workouts: List<Workout>,
): CycleSpace {
    val rests = restSlotIndicesForSevenDayWeek(trainingWeekdays)
    return CycleSpace(
        cycleLength = 7,
        restSlotIndices = rests,
        workouts = workouts,
    )
}
