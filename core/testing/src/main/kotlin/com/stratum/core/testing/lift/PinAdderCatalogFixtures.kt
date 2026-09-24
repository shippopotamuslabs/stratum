package com.stratum.core.testing.lift

import com.stratum.domain.lift.IncrementTier
import com.stratum.domain.lift.Weight

/**
 * Shared pin-loaded stack catalog: 15 lb plates with a 0 / 5 / 10 adder knob.
 * Single source for lattice tests so tier/adder values cannot drift apart.
 */
object PinAdderCatalogFixtures {
    fun pinTiers(unit: Weight.WeightUnit = Weight.WeightUnit.Lb): List<IncrementTier> =
        listOf(IncrementTier(Weight(0.0, unit), null, Weight(15.0, unit)))

    fun pinMicro(unit: Weight.WeightUnit = Weight.WeightUnit.Lb): List<Weight> =
        listOf(Weight(5.0, unit), Weight(10.0, unit))
}
