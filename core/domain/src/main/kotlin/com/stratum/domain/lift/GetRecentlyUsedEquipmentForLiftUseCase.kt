package com.stratum.domain.lift

import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.training.TrainingLogRepository
import javax.inject.Inject

/**
 * Equipment recently used for [liftId], most-recent first, derived from set history —
 * the system learns the lift's choice set. Skips sets with no equipment and
 * tombstoned rows. Feeds the "recently used" section of the equipment swap sheet.
 */
class GetRecentlyUsedEquipmentForLiftUseCase @Inject constructor(
    private val trainingLogRepository: TrainingLogRepository,
    private val equipmentRepository: EquipmentRepository,
) {
    suspend operator fun invoke(
        liftId: LiftId,
        limit: Int = DEFAULT_RECENT_EQUIPMENT_LIMIT,
    ): List<Equipment> {
        require(limit > 0) { "limit must be positive" }
        return trainingLogRepository.getSetsForLift(liftId)
            .mapNotNull { it.equipmentId }
            .distinct()
            .take(limit)
            .mapNotNull { equipmentRepository.getEquipment(it)?.takeUnless { equipment -> equipment.isDeleted } }
    }

    companion object {
        /** Sheet-sized recent list; full history stays available via search. */
        const val DEFAULT_RECENT_EQUIPMENT_LIMIT = 5
    }
}
