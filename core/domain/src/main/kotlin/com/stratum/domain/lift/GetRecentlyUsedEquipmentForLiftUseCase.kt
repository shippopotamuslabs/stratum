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
        // Sets arrive newest-first per the repository contract; resolve ids in order and
        // stop at the first [limit] live rows so tombstones skip without consuming slots.
        val live = ArrayList<Equipment>(limit)
        val orderedIds =
            trainingLogRepository.getSetsForLift(liftId).mapNotNull { it.equipmentId }.distinct()
        for (id in orderedIds) {
            if (live.size >= limit) break
            equipmentRepository.getEquipment(id)?.takeUnless { it.isDeleted }?.let(live::add)
        }
        return live
    }

    companion object {
        /** Sheet-sized recent list; full history stays available via search. */
        const val DEFAULT_RECENT_EQUIPMENT_LIMIT = 5
    }
}
