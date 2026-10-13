package com.stratum.domain.lift

import com.stratum.domain.lift.identity.LiftId

/**
 * Atomic persistence boundary for [MergeLiftsUseCase]. Implementations must apply all structural
 * mutations in a single transaction (history reassignment, program template re-point without
 * collapsing duplicate slots, tombstone seed sources, delete custom sources + sync tombstones).
 *
 * Personal-record recompute, active-session overlay remap, and derived weekly rebuild are
 * intentionally **outside** this port (post-commit).
 */
interface MergeLiftsPort {
    /**
     * @param targetLiftId survivor catalog id (unchanged metadata)
     * @param targetName snapshot name written onto reassigned sets
     * @param sourcesToTombstone seed / non-custom sources — tombstone the row
     * ([Lift.isDeleted] = true, STR-131), keep the row for history resolution;
     * `enabled` is left as-is. Seed sources are never merely disabled: a merged
     * lift is deleted, same as a Gym delete.
     * @param sourcesToDelete custom sources — hard-delete after re-point (FK RESTRICT-safe)
     */
    suspend fun applyMerge(
        targetLiftId: LiftId,
        targetName: String,
        sourcesToTombstone: Set<LiftId>,
        sourcesToDelete: Set<LiftId>,
    )
}
