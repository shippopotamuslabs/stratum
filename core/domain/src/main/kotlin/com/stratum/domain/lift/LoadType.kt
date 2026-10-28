package com.stratum.domain.lift

import com.stratum.domain.lift.identity.EquipmentId

/**
 * How external load relates to a [Lift]'s resistance profile.
 *
 * - [Weighted]: standard external load (barbell, dumbbell, cable, machine stack). The
 *   logged weight is the added resistance. Default for all lifts.
 * - [Bodyweight]: bodyweight-only (pull-ups, dips, push-ups). No external load is logged;
 *   the weight field is hidden in the UI and stored as `Weight(0.0, ...)`. Progression is
 *   rep-only — the engine never attempts to increment load.
 * - [Assisted]: machine-assisted or band-assisted variant (assisted pull-up, assisted dip).
 *   The logged weight is **negative** (e.g. `Weight(-50.0, Lb)` = 50 lb of assistance).
 *   Progression adds positive increments, reducing assistance toward zero. The UI displays
 *   the absolute value with a "−" prefix header.
 */
enum class LoadType {
    Weighted,
    Bodyweight,
    Assisted,
}

/**
 * Couples logging modality to the selected implement so bodyweight equipment cannot stay
 * Weighted (or the reverse) after a Details-tab assignment.
 *
 * Bodyweight equipment always becomes [Bodyweight]. Leaving bodyweight restores [Weighted]
 * only when the current value is [Bodyweight]; [Assisted] is left alone.
 */
fun LoadType.alignedWithEquipment(equipmentId: EquipmentId?): LoadType =
    when {
        equipmentId == null -> this
        equipmentId == SeedEquipmentIds.Bodyweight -> LoadType.Bodyweight
        this == LoadType.Bodyweight -> LoadType.Weighted
        else -> this
    }

/**
 * True when this load type can coexist with [equipmentId] — the pair is a fixed
 * point of [alignedWithEquipment]. Bodyweight equipment forces [LoadType.Bodyweight];
 * a [LoadType.Bodyweight] lift cannot sit on loaded equipment; [LoadType.Assisted]
 * survives any loaded equipment. Null equipment imposes no constraint.
 */
fun LoadType.isAlignedWithEquipment(equipmentId: EquipmentId?): Boolean =
    alignedWithEquipment(equipmentId) == this

/**
 * The lift-construction invariant behind [isAlignedWithEquipment] (STR-250): a lift
 * whose equipment is the bodyweight station must be [LoadType.Bodyweight], and a
 * bodyweight lift must not carry loaded equipment. Writers that assemble a lift from
 * two independent signals (the seed catalog pairs a declared loadType with equipment
 * tags) call this at construction so a contradictory pair is never persisted: which
 * of the two signals is wrong is unknowable at write time, so the write fails
 * instead of guessing. Throws [IllegalArgumentException] on violation.
 */
fun requireLoadTypeEquipmentAlignment(loadType: LoadType, equipmentId: EquipmentId?) {
    require(loadType.isAlignedWithEquipment(equipmentId)) {
        "LoadType $loadType contradicts equipment ${equipmentId?.value ?: "none"}: " +
            "bodyweight equipment requires LoadType.Bodyweight and a Bodyweight lift " +
            "cannot use loaded equipment"
    }
}
