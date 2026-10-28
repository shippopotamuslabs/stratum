package com.stratum.domain.workout

import com.stratum.domain.lift.Weight
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.workout.progression.ProgressionDiagnostic

/**
 * Snapshot-layer recommendation for a single [SetSlot] (D1, D3). Computed once per
 * session from history; immutable for the session life.
 */
data class SlotRecommendation(
    val slotId: SetSlotId,
    val recommendedWeight: Weight,
    val baselineExpectedReps: Int,
    val previousWeight: Weight? = null,
    val previousReps: Int? = null,
    /** Prior session RIR for this slot position (UI prev column); null when unlogged. */
    val previousRir: Int? = null,
    /** Prior session partial reps for this slot position (UI prev column); null when none. */
    val previousPartialReps: Int? = null,
    val isProjected: Boolean = false,
)

/**
 * The progression snapshot (D3): `SetSlot → recommended weight + baseline expected reps`,
 * computed once per session. Recomputed on exercise and equipment swaps. Immutable for
 * the session life — overlay mutations never write back here.
 */
data class ProgressionSnapshot(
    val workoutId: WorkoutId,
    val bySlot: Map<SetSlotId, SlotRecommendation>,
)

/**
 * A computed [ProgressionSnapshot] plus the non-fatal [ProgressionDiagnostic]s
 * recorded while computing it (STR-250). Diagnostics are data: the domain never
 * logs, so the caller at the application boundary reports each one exactly once
 * through its logging port. They are never surfaced in the UI.
 */
data class ProgressionSnapshotOutcome(
    val snapshot: ProgressionSnapshot,
    val diagnostics: List<ProgressionDiagnostic> = emptyList(),
)
