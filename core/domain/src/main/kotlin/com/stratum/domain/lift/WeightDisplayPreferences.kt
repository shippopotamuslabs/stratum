package com.stratum.domain.lift

/**
 * User-facing weight unit for **display and typed input only**.
 *
 * Defaults to [Weight.WeightUnit.Lb]. Training loads (logged sets, increment tiers, PR e1RM
 * magnitudes used in compares) are stored in **Lb** — convert at the UI boundary via
 * [WeightConversion] / [Weight.convertTo]. Flipping this preference never rewrites Room rows.
 */
data class WeightDisplayPreferences(
    val displayUnit: Weight.WeightUnit = Weight.WeightUnit.Lb,
)
