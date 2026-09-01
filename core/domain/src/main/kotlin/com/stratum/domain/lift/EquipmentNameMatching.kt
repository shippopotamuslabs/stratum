package com.stratum.domain.lift

import com.stratum.domain.lift.identity.EquipmentId

/**
 * Free-text equipment label matching for import / create paths.
 *
 * Seed display names ("Cables") often diverge from LLM / user shorthand ("cable"). Exact
 * case-insensitive equality is not enough — callers should [normalizeKey] both sides and/or
 * resolve [seedIdFromFreeText] before minting a custom equipment row.
 */
object EquipmentNameMatching {
    /**
     * Collapses punctuation/whitespace and strips a naive trailing plural `s` so that
     * `"cable"` and `"Cables"` share a key.
     */
    fun normalizeKey(raw: String): String {
        val compact =
            raw
                .trim()
                .lowercase()
                .replace(NON_ALNUM, "")
        return if (compact.length > 3 && compact.endsWith('s') && !compact.endsWith("ss")) {
            compact.dropLast(1)
        } else {
            compact
        }
    }

    /**
     * Maps common free-text labels onto bundled seed [EquipmentId]s. Returns null when the
     * string is not a known alias (caller may still match existing rows by [normalizeKey] or
     * create custom equipment).
     */
    fun seedIdFromFreeText(name: String): EquipmentId? {
        val key = normalizeKey(name)
        if (key.isEmpty()) return null
        return FREE_TEXT_ALIASES[key]
    }

    private val NON_ALNUM = Regex("[^a-z0-9]+")

    private val FREE_TEXT_ALIASES: Map<String, EquipmentId> =
        buildMap {
            fun alias(id: EquipmentId, vararg labels: String) {
                labels.forEach { put(normalizeKey(it), id) }
            }
            alias(
                EquipmentId("cable"),
                "cable",
                "cables",
                "cable machine",
                "cable stack",
                "cable station",
                "cable crossover",
            )
            alias(
                EquipmentId("dumbbell"),
                "dumbbell",
                "dumbbells",
                "db",
                "dbs",
            )
            alias(
                EquipmentId("kettlebell"),
                "kettlebell",
                "kettlebells",
                "kb",
            )
            alias(
                EquipmentId("double-weight-plates"),
                "barbell",
                "plates",
                "weight plates",
                "double weight plates",
                "olympic bar",
                "ez bar",
                "ezbar",
                "smith machine",
                // Generic / sled dual-horn leg press (not a pin stack).
                "leg press",
                "sled leg press",
            )
            alias(
                EquipmentId("single-weight-plate"),
                "single weight plate",
                "landmine",
                "t bar",
                "tbar",
                "t-bar",
            )
            alias(
                EquipmentId("fixed-barbell"),
                "fixed barbell",
                "fixed bar",
            )
            alias(
                EquipmentId("pin-loaded-machine"),
                "machine",
                "pin loaded",
                "pin-loaded machine",
                "selectorized",
                "stack machine",
                "machine leg press",
            )
            alias(
                EquipmentId("default"),
                "default",
            )
            alias(
                EquipmentId("bodyweight"),
                "bodyweight",
                "body weight",
                "bw",
                "band",
                "bands",
            )
        }
}
