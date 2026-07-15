package com.stratum.domain.program

import com.stratum.domain.program.schedule.CycleSlot
import com.stratum.domain.program.schedule.resolvedCycleSlots

/**
 * Stable id for a [CycleSlot] while reordering. Training slots use workout id; rest slots use a
 * synthetic id scoped to [programId] + original slot index (valid only for one reorder session).
 */
fun cycleSlotReorderId(programId: String, slot: CycleSlot): String =
    when (slot) {
        is CycleSlot.Training -> slot.workout.id.value
        is CycleSlot.Rest -> "rest-${slot.slotIndex}-$programId"
    }

/** Projects cycle slots into reorder rows (train + rest names). */
fun Program.toReorderDayItems(restDayName: String = "Rest"): List<ReorderDayItem> {
    val programId = id.value
    return resolvedCycleSlots().map { slot ->
        when (slot) {
            is CycleSlot.Training ->
                ReorderDayItem(
                    dayId = cycleSlotReorderId(programId, slot),
                    name = slot.workout.name,
                )
            is CycleSlot.Rest ->
                ReorderDayItem(
                    dayId = cycleSlotReorderId(programId, slot),
                    name = restDayName,
                )
        }
    }
}

/**
 * Maps each pre-reorder slot index to its post-reorder index for [orderedDayIds].
 * Empty when [orderedDayIds] is not a permutation of the current slot set.
 *
 * Used to rewrite [com.stratum.domain.training.TrainingSession.cycleSlotIndexSnapshot] so rest
 * progress and unskip matching stay correct after a live program reorder.
 */
fun Program.slotIndexRemapAfterReorder(orderedDayIds: List<String>): Map<Int, Int> {
    val slots = resolvedCycleSlots()
    if (slots.isEmpty() || orderedDayIds.size != slots.size) return emptyMap()

    val programId = id.value
    val byId = slots.associateBy { slot -> cycleSlotReorderId(programId, slot) }
    if (orderedDayIds.any { id -> id !in byId }) return emptyMap()

    return buildMap {
        orderedDayIds.forEachIndexed { newIndex, id ->
            put(byId.getValue(id).slotIndex, newIndex)
        }
    }
}

/**
 * Rebuilds [Program] with cycle slots in [orderedDayIds] order.
 *
 * - [Program.cycleLength] stays the full train+rest count
 * - [Program.restSlotIndices] updates to new rest positions
 * - Training [Workout.position] is 0..T-1 among train slots only
 * - [Program.cyclePosition] is clamped only; callers that own the session ledger should
 *   reconcile via [com.stratum.domain.training.CycleProgressResolver.reconcileCyclePosition]
 *
 * Returns the original program when [orderedDayIds] does not match the current slot set.
 */
fun Program.reorderCycleDays(orderedDayIds: List<String>): Program {
    val slots = resolvedCycleSlots()
    if (slots.isEmpty() || orderedDayIds.size != slots.size) return this

    val programId = id.value
    val byId = slots.associateBy { slot -> cycleSlotReorderId(programId, slot) }
    if (orderedDayIds.any { id -> id !in byId }) return this

    val reordered = orderedDayIds.map { id -> byId.getValue(id) }
    val restSlotIndices = linkedSetOf<Int>()
    val workouts = mutableListOf<Workout>()

    reordered.forEachIndexed { slotIndex, slot ->
        when (slot) {
            is CycleSlot.Rest -> restSlotIndices += slotIndex
            is CycleSlot.Training ->
                workouts +=
                    slot.workout.copy(position = workouts.size)
        }
    }

    val cycleLength = reordered.size.coerceAtLeast(1)
    return copy(
        workouts = workouts,
        restSlotIndices = restSlotIndices,
        cycleLength = cycleLength,
        cyclePosition = cyclePosition.coerceIn(0, (cycleLength - 1).coerceAtLeast(0)),
        // Slot order is identity; clear weekday projection cache.
        trainingWeekdays = emptySet(),
    )
}
