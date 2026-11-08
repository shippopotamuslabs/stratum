package com.stratum.application.lift

import com.stratum.domain.lift.Equipment
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.EquipmentId
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

/**
 * Application-layer read model for the equipment increment editor (STR-284).
 *
 * The selected equipment resolved from the catalog, the lifts inheriting its
 * defaults, and the display unit, so the ViewModel can map presentation state
 * from a single durable source per §12.4. [equipment] is null until the
 * catalog contains the selected id; [affected] is empty for a blank id.
 */
data class EquipmentEditorSnapshot(
    val equipmentId: String?,
    val equipment: Equipment?,
    val affected: EquipmentAffectedLifts,
    val displayUnit: Weight.WeightUnit = Weight.WeightUnit.Lb,
)

/**
 * Owns the equipment-editor multi-source composition (STR-284).
 *
 * Combines the selected equipment id, the equipment catalog, the affected
 * lifts, and the weight display preferences into one [EquipmentEditorSnapshot]
 * stream. Resolution semantics are unchanged from the former ViewModel
 * pipeline: first catalog equipment whose id matches, empty affected set for
 * a blank id, display unit from prefs.
 */
class ObserveEquipmentEditorStateUseCase @Inject constructor(
    private val observeEquipmentCatalog: ObserveEquipmentCatalogUseCase,
    private val observeEquipmentAffectedLifts: ObserveEquipmentAffectedLiftsUseCase,
    private val observeWeightDisplayPreferences: ObserveWeightDisplayPreferencesUseCase,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(equipmentId: Flow<String?>): Flow<EquipmentEditorSnapshot> {
        val catalog = observeEquipmentCatalog()
        val prefs = observeWeightDisplayPreferences()
        return equipmentId.flatMapLatest { id ->
            val affected =
                if (id.isNullOrBlank()) {
                    flowOf(emptyAffected())
                } else {
                    observeEquipmentAffectedLifts(EquipmentId(id))
                }
            combine(catalog, prefs, affected) { equipment, weightPrefs, lifts ->
                EquipmentEditorSnapshot(
                    equipmentId = id,
                    equipment = equipment.firstOrNull { it.id.value == id },
                    affected = lifts,
                    displayUnit = weightPrefs.displayUnit,
                )
            }
        }
    }

    // Blank means "no equipment selected": the affected set is hardcoded empty,
    // so the stamped id is cosmetic. (The former ViewModel built
    // EquipmentId("") here, which throws when collected — this path never
    // survived collection before.)
    private fun emptyAffected(): EquipmentAffectedLifts =
        EquipmentAffectedLifts(
            equipmentId = EquipmentId("unselected"),
            count = 0,
            lifts = emptyList(),
        )
}
