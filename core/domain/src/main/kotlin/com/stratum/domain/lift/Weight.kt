package com.stratum.domain.lift

/**
 * A weight magnitude together with its unit. Introduced in OS-4 as the unit for increment
 * tiers (§2.2 mandates a domain `Weight` type).
 *
 * **Canonical storage for training loads is [WeightUnit.Lb]** (logged sets after import/live
 * log, increment tier amounts in Room). [Kg] is used for display conversion and for
 * analytics series that normalize to kg. User preference is [WeightDisplayPreferences].
 *
 * Pure value type — lattice/compare math should convert via [WeightConversion] / [toStorageLb]
 * when units may differ.
 */
data class Weight(
    val amount: Double,
    val unit: WeightUnit,
) {
    enum class WeightUnit {
        Lb,
        Kg,
        ;

        companion object {
            fun parseOrNull(raw: String): WeightUnit? {
                val trimmed = raw.trim()
                val normalized =
                    trimmed
                        .lowercase()
                        .removeSuffix(".")
                        .removeSuffix("s")
                return when (normalized) {
                    "lb", "pound", "pounds" -> Lb
                    "kg", "kilogram", "kilograms", "kilo" -> Kg
                    else -> entries.find { it.name.equals(trimmed, ignoreCase = true) }
                }
            }

            fun parse(raw: String): WeightUnit =
                parseOrNull(raw) ?: throw IllegalArgumentException("Unsupported weight unit: $raw")
        }
    }
}
