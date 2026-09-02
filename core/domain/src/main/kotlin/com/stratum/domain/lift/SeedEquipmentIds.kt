package com.stratum.domain.lift

import com.stratum.domain.lift.identity.EquipmentId

/**
 * Stable seed [EquipmentId] values for loading-method lattices.
 *
 * Keep in sync with `core/data/src/main/assets/lifts/seed-equipment.json` and the Cloud Function
 * `SEED_LOADING_KINDS` list used by `inferCustomLifts`.
 */
object SeedEquipmentIds {
    /** Hidden system fallback: unspecified implement with a 2.5 lb open band. */
    val Default = EquipmentId("default")
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
            Default,
            Dumbbell,
            DoubleWeightPlates,
            SingleWeightPlate,
            FixedBarbell,
            PinLoadedMachine,
            Cable,
            Kettlebell,
            Bodyweight,
        )

    /**
     * Seed implements omitted from Manage Equipment and the equipment picker.
     * Bodyweight is assigned via the Details "Bodyweight only" action; Default is the
     * create-lift / unknown-loading fallback.
     */
    val hiddenFromCatalog: Set<EquipmentId> = setOf(Default, Bodyweight)

    fun fromWireId(value: String): EquipmentId? {
        val id = EquipmentId(value.trim())
        return id.takeIf { it in ALL }
    }
}
