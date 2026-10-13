package com.stratum.domain.lift

import com.stratum.domain.lift.identity.LiftId

/**
 * Atomic persistence boundary for [DeleteLiftUseCase] (STR-131). Implementations
 * must apply the whole delete in a single transaction:
 *
 * - delete every template slot (`workout_lifts`) pointing at the lift across
 *   all programs (template sets cascade; workouts survive, possibly empty),
 *   enqueueing sync delete records for the removed rows;
 * - delete every `workout_lift_notes` row scoped to the lift by its pair
 *   `lift_id`, regardless of which slot currently occupies it (the STR-127
 *   stash sweep), enqueueing their sync delete records;
 * - tombstone the lift row itself ([Lift.isDeleted]) with a pending upsert —
 *   the deletion propagates as an `is_deleted` upsert, never as a sync delete
 *   record for the lift row.
 *
 * Logged sets are never touched: they keep pointing at the tombstoned row
 * byte-identical (history is not a live pointer). Catalog notes, the legacy
 * pinned note, and session notes stay on / with the retained row. Derived
 * rebuild is intentionally **outside** this port (post-commit).
 *
 * Returns true when a live row was tombstoned; false when the lift is
 * missing or already tombstoned (re-delete is a no-op success upstream).
 */
interface DeleteLiftPort {
    suspend fun deleteLift(liftId: LiftId): Boolean
}
