package com.stratum.domain.training

import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.onboarding.MuscleGroup

/** Decode a movement-pattern bitmask into the matching [MovementPattern] set (by ordinal bit position). */
fun Int.toMovementPatternSet(): Set<MovementPattern> =
    MovementPattern.entries.filter { this and (1 shl it.ordinal) != 0 }.toSet()

/** Encode a [MovementPattern] set into a bitmask (`1 shl ordinal`). */
fun Set<MovementPattern>.toMovementPatternsBitmask(): Int =
    fold(0) { mask, pattern -> mask or (1 shl pattern.ordinal) }

/** Decode a muscle bitmask into the matching [MuscleGroup] set (by ordinal bit position). */
fun Int.toMuscleGroupSet(): Set<MuscleGroup> =
    MuscleGroup.entries.filter { this and (1 shl it.ordinal) != 0 }.toSet()

/** Encode a [MuscleGroup] set into a bitmask (`1 shl ordinal`). */
fun Set<MuscleGroup>.toMusclesBitmask(): Int =
    fold(0) { mask, muscle -> mask or (1 shl muscle.ordinal) }
