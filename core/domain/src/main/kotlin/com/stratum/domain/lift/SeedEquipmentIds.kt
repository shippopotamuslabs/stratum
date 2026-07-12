package com.stratum.domain.lift

import com.stratum.domain.lift.identity.EquipmentId

/**
 * Stable seed [EquipmentId] values for loading-method lattices.
 *
 * Keep in sync with `core/data/src/main/assets/lifts/seed-equipment.json` and the Cloud Function
 * `SEED_LOADING_KINDS` list used by `inferCustomLifts`.
 */
object SeedEquipmentIds {
    val Dumbbell = EquipmentId("dumbbell")
    val DoubleWeightPlates = EquipmentId("double-weight-plates")
    val SingleWeightPlate = EquipmentId("single-weight-plate")
    val FixedBarbell = EquipmentId("fixed-barbell")
    val PinLoadedMachine = EquipmentId("pin-loaded-machine")
    val Cable = EquipmentId("cable")
    val Kettlebell = EquipmentId("kettlebell")
    val Bodyweight = EquipmentId("bodyweight")

    /** All seed loading-method ids (not including LLM `"custom"`). */
    val ALL: Set<EquipmentId> =
        setOf(
            Dumbbell,
            DoubleWeightPlates,
            SingleWeightPlate,
            FixedBarbell,
            PinLoadedMachine,
            Cable,
            Kettlebell,
            Bodyweight,
        )

    fun fromWireId(value: String): EquipmentId? {
        val id = EquipmentId(value.trim())
        return id.takeIf { it in ALL }
    }
}
