package com.stratum.core.testing.onboarding

import com.stratum.core.testing.program.FakeProgramRepository
import com.stratum.domain.lift.DeleteLiftPort
import com.stratum.domain.lift.identity.LiftId

/**
 * In-memory [DeleteLiftPort] for domain / ViewModel tests. Mirrors production
 * semantics without Room: sweep the lift's template slots + pair-note stashes
 * across all programs, then tombstone the row. Logged sets are never touched.
 * Returns false for missing or already-tombstoned lifts (no-op).
 */
class FakeDeleteLiftPort(
    private val liftRepository: FakeLiftRepository,
    private val programRepository: FakeProgramRepository,
    private val failOnDelete: Boolean = false,
) : DeleteLiftPort {

    var deleteCallCount: Int = 0
        private set

    override suspend fun deleteLift(liftId: LiftId): Boolean {
        deleteCallCount++
        check(!failOnDelete) { "Fake delete failure" }
        val lift = liftRepository.getLiftsByIds(listOf(liftId)).firstOrNull() ?: return false
        if (lift.isDeleted) return false
        programRepository.removeSlotsForLift(liftId)
        liftRepository.tombstoneLift(liftId)
        return true
    }
}
