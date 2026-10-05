package com.stratum.domain.lift

import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.lift.identity.LiftId
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * A lift that inherits [equipmentId]'s default increment tiers (no lift-scoped override).
 *
 * Disabled lifts are included — they still point at the equipment and pick up new defaults
 * when re-enabled (equipment-increments follow-ups Feature 2).
 */
data class AffectedLift(
    val id: LiftId,
    val name: String,
    val enabled: Boolean,
)

/**
 * Blast-radius snapshot for editing equipment default increments.
 *
 * [count] / [lifts] include disabled lifts that inherit defaults. Per-lift overrides were
 * removed (STR-82/84), so every lift linked to the equipment inherits its defaults.
 */
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
