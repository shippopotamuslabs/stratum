package com.stratum.core.data.weightdisplay

import kotlinx.serialization.Serializable

/**
 * Typed DataStore root for [com.stratum.domain.lift.WeightDisplayPreferences].
 * Unit names match [com.stratum.domain.lift.Weight.WeightUnit] enum names.
 */
@Serializable
data class WeightDisplayPreferencesRecord(
    val displayUnit: String = WeightUnitNames.LB,
)

internal object WeightUnitNames {
    const val LB = "Lb"
    const val KG = "Kg"
}
