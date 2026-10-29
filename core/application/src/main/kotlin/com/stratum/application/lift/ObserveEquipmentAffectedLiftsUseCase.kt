package com.stratum.application.lift

import com.stratum.domain.lift.AffectedLift
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.identity.EquipmentId
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class EquipmentAffectedLifts(
    val equipmentId: EquipmentId,
    val count: Int,
    val lifts: List<AffectedLift>,
)

/**
 * Pure filter of the lift catalog for equipment increment blast radius.
 *
 * ```
 * affected(equipmentId) = lifts where equipmentId == this
 * ```
 *
 * Per-lift overrides were removed (STR-82/84): every linked lift inherits the equipment's
 * defaults. Includes disabled lifts. Sorted by name (case-insensitive) for stable UI previews.
 */

fun filterEquipmentAffectedLifts(
    equipmentId: EquipmentId,
    lifts: Collection<Lift>,
): EquipmentAffectedLifts {
    val affected =
        lifts
            .asSequence()
            .filter { it.equipmentId == equipmentId }
            .sortedBy { it.name.lowercase() }
            .map { lift ->
                AffectedLift(
                    id = lift.id,
                    name = lift.name,
                    enabled = lift.enabled,
                )
            }
            .toList()
    return EquipmentAffectedLifts(
        equipmentId = equipmentId,
        count = affected.size,
        lifts = affected,
    )
}

/**
 * Observes lifts that inherit defaults from [equipmentId] (including disabled).
 *
 * Hot stream over [LiftRepository.observeLifts] — no new Room columns.
 */

class ObserveEquipmentAffectedLiftsUseCase @Inject constructor(
    private val repository: LiftRepository,
) {
    operator fun invoke(equipmentId: EquipmentId): Flow<EquipmentAffectedLifts> =
        repository.observeLifts().map { lifts ->
            filterEquipmentAffectedLifts(equipmentId, lifts)
        }
}
