package com.stratum.core.data.seed

import com.stratum.domain.importing.GymEquipment
import com.stratum.domain.lift.EquipmentNameMatching
import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.lift.identity.LiftId

/**
 * Maps import-only [GymEquipment] tags (from `lift-equipment-tags.json`) to seed
 * [EquipmentId] rows for default increments.
 *
 * Load-bearing equipment only — benches/racks are accessories and never become the lift's
 * increment equipment.
 *
 * - Barbell compounds (squat/bench/deadlift) → double weight plates (+5)
 * - Fixed rubber bars / most free-weight rows → fixed barbell (+10)
 * - T-bar / single-end loading → single weight plate (+2.5)
 * - EZ-bar stays plate-loaded both ends → double weight plates
 * - Generic / sled leg press → double weight plates (dual-horn plate loading)
 * - Named "machine … leg press" catalog variants → pin-loaded stack (+15)
 */
object GymEquipmentToEquipmentId {
    private val LOAD_BEARING_PRIORITY: List<GymEquipment> = listOf(
        GymEquipment.Dumbbell,
        GymEquipment.Kettlebell,
        // Station / specialty before generic Barbell so dual-tagged T-bar rows don't inherit
        // double-plate increments.
        GymEquipment.TBarRowStation,
        GymEquipment.FixedBarbell,
        GymEquipment.Barbell,
        GymEquipment.EzBar,
        GymEquipment.SmithMachine,
        GymEquipment.CableStation,
        GymEquipment.CableCrossover,
        GymEquipment.SeatedCableRowStation,
        GymEquipment.LatPulldownMachine,
        GymEquipment.LegPress,
        GymEquipment.HackSquat,
        GymEquipment.LegExtension,
        GymEquipment.LegCurl,
        GymEquipment.CalfRaiseMachine,
        GymEquipment.AbCrunchMachine,
        GymEquipment.PecDeck,
        GymEquipment.PreacherCurlMachine,
        GymEquipment.HipThrustMachine,
        GymEquipment.Ghd,
        GymEquipment.LegRaiseCaptainChair,
        GymEquipment.Band,
        GymEquipment.Bodyweight,
    )

    /**
     * Per-lift overrides when tags alone are ambiguous (e.g. landmine / long-bar rows that
     * load one sleeve).
     */
    private val LIFT_ID_OVERRIDES: Map<String, EquipmentId> = mapOf(
        "one-arm-long-bar-row" to EquipmentId("single-weight-plate"),
    )

    fun equipmentIdFor(liftId: LiftId, tags: Set<GymEquipment>): EquipmentId? {
        LIFT_ID_OVERRIDES[liftId.value]?.let { return it }
        // Selectorized "Machine … Leg Press" catalog rows — not dual-horn sled loading.
        if (isMachineLegPressVariant(liftId.value)) {
            return EquipmentId("pin-loaded-machine")
        }
        return equipmentIdFor(tags)
    }

    fun equipmentIdFor(tags: Set<GymEquipment>): EquipmentId? {
        if (tags.isEmpty()) return null
        val primary = LOAD_BEARING_PRIORITY.firstOrNull { it in tags } ?: return null
        return map(primary)
    }

    fun map(equipment: GymEquipment): EquipmentId? =
        when (equipment) {
            GymEquipment.Dumbbell -> EquipmentId("dumbbell")
            GymEquipment.Kettlebell -> EquipmentId("kettlebell")
            GymEquipment.FixedBarbell -> EquipmentId("fixed-barbell")
            GymEquipment.Barbell,
            GymEquipment.EzBar,
            GymEquipment.SmithMachine,
            // Sled / dual-horn plate leg press is the commercial default; pin-stack variants
            // are called out by catalog id via [isMachineLegPressVariant].
            GymEquipment.LegPress,
            -> EquipmentId("double-weight-plates")
            GymEquipment.TBarRowStation -> EquipmentId("single-weight-plate")
            GymEquipment.CableStation,
            GymEquipment.CableCrossover,
            GymEquipment.SeatedCableRowStation,
            GymEquipment.LatPulldownMachine,
            -> EquipmentId("cable")
            GymEquipment.HackSquat,
            GymEquipment.LegExtension,
            GymEquipment.LegCurl,
            GymEquipment.CalfRaiseMachine,
            GymEquipment.AbCrunchMachine,
            GymEquipment.PecDeck,
            GymEquipment.PreacherCurlMachine,
            GymEquipment.HipThrustMachine,
            GymEquipment.Ghd,
            GymEquipment.LegRaiseCaptainChair,
            -> EquipmentId("pin-loaded-machine")
            GymEquipment.Bodyweight,
            GymEquipment.Band,
            -> EquipmentId("bodyweight")
            GymEquipment.FlatBench,
            GymEquipment.AdjustableBench,
            GymEquipment.PowerRack,
            -> null
        }

    /**
     * Catalog slugs like `machine-single-leg-press` name the selectorized stack machine.
     * Generic / sled / plate-loaded leg-press ids do not match.
     */
    internal fun isMachineLegPressVariant(liftId: String): Boolean {
        val key = liftId.lowercase()
        return key.contains("leg-press") && key.contains("machine")
    }

    /**
     * Resolves free-text equipment labels (LLM import, create-lift shorthand) onto seed
     * [EquipmentId]s. Tries common aliases first, then [GymEquipment] enum names
     * (e.g. `"CableStation"`).
     *
     * Returns null when no seed alias applies — callers should still match existing rows by
     * normalized display name before creating custom equipment.
     */
    fun seedIdFromFreeText(name: String): EquipmentId? {
        EquipmentNameMatching.seedIdFromFreeText(name)?.let { return it }
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null
        val key = EquipmentNameMatching.normalizeKey(trimmed)
        val enumHit =
            GymEquipment.entries.firstOrNull { entry ->
                entry.name.equals(trimmed, ignoreCase = true) ||
                    EquipmentNameMatching.normalizeKey(entry.name) == key
            }
        return enumHit?.let { map(it) }
    }
}
