package com.stratum.domain.workout

import com.stratum.domain.lift.Weight
import com.stratum.domain.program.identity.WorkoutId

/**
 * Snapshot-layer recommendation for a single [SetSlot] (D1, D3). Computed once per
 * session from history; immutable for the session life.
 */
data class SlotRecommendation(
    val slotId: SetSlotId,
    val recommendedWeight: Weight,
    val baselineExpectedReps: Int,
)

/**
 * The progression snapshot (D3): `SetSlot → recommended weight + baseline expected reps`,
 * computed once per session. Recomputed only on exercise swap. Immutable for the
 * session life — overlay mutations never write back here.
 */
data class ProgressionSnapshot(
    val workoutId: WorkoutId,
    val bySlot: Map<SetSlotId, SlotRecommendation>,
)
