package com.stratum.domain.lift

import com.stratum.domain.lift.identity.LiftId

/**
 * Atomic persistence boundary for [MergeLiftsUseCase]. Implementations must apply all structural
 * mutations in a single transaction (history reassignment, program template re-point, hide seed
 * sources, delete custom sources + sync tombstones).
 *
 * Personal-record recompute and derived weekly rebuild are intentionally **outside** this port
 * (post-commit).
 */
interface MergeLiftsPort {
    /**
     * @param targetLiftId survivor catalog id (unchanged metadata)
     * @param targetName snapshot name written onto reassigned sets
     * @param sourcesToHide seed / non-custom sources — set [Lift.enabled] = false, keep rows
     * @param sourcesToDelete custom sources — hard-delete after re-point (FK RESTRICT-safe)
     */
    suspend fun applyMerge(
        targetLiftId: LiftId,
        targetName: String,
        sourcesToHide: Set<LiftId>,
        sourcesToDelete: Set<LiftId>,
    )
}
