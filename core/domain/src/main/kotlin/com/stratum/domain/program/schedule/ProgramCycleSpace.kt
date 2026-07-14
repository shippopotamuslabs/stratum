package com.stratum.domain.program.schedule

import com.stratum.domain.program.Program

/**
 * Builds the [CycleSpace] for a [Program]. Uses [Program.cycleLength], [Program.restSlotIndices],
 * and [Program.workouts].
 *
 * When [Program.restSlotIndices] is empty and `cycleLength == workouts.size`, the cycle is
 * train-only (no rest slots). Callers that still write weekday-bound programs with
 * `cycleLength == workouts.size` remain valid until materialize/gen updates lengths.
 */
fun Program.toCycleSpace(): CycleSpace =
    CycleSpace(
        cycleLength = cycleLength,
        restSlotIndices = restSlotIndices,
        workouts = workouts,
    )

fun Program.cycleSlots(): List<CycleSlot> = toCycleSpace().slots()

/**
 * Like [cycleSlots], but returns empty when cycle-space invariants fail instead of inventing a
 * train-only layout (which hid corrupt `cycleLength` / `restSlotIndices` and made rests vanish
 * from the Programs strip). Prefer fixing the program via materialize / re-gen over silent repair.
 */
fun Program.resolvedCycleSlots(): List<CycleSlot> =
    runCatching { cycleSlots() }.getOrDefault(emptyList())
