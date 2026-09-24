package com.stratum.domain.workout.fatigue

import com.stratum.domain.lift.IncrementTier
import com.stratum.domain.lift.LoadType
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.WeightConversion
import com.stratum.domain.workout.BACKOFF_REP_FLOOR
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class LastSetFloorReduceTest {

    private val lb = Weight.WeightUnit.Lb
    private val tiers = listOf(IncrementTier(Weight(0.0, lb), null, Weight(5.0, lb)))
    private val lbMicro = listOf(Weight(2.5, lb))

    @Test
    @DisplayName("Given kg-denominated micro when reduced then matches lb-denominated micro")
    fun mixedUnitMicroMatchesStorageMicro() {
        // Numerically identical to lbMicro (2.5 lb) but denominated in kg. 96.5 is
        // off-lattice, forcing the micro stop (via the ceiling step-down) to decide
        // the result: 96.5 → 97.5 (adder) → step down → 92.5.
        val kgMicro =
            listOf(Weight(2.5 * WeightConversion.LB_TO_KG, Weight.WeightUnit.Kg))
        val alwaysClears = { _: Double -> BACKOFF_REP_FLOOR }

        val fromLb =
            reduceWeightForLastSetFloor(
                startFrom = Weight(96.5, lb),
                incrementTiers = tiers,
                loadType = LoadType.Weighted,
                capacityAtRir = alwaysClears,
                micro = lbMicro,
            )
        val fromKg =
            reduceWeightForLastSetFloor(
                startFrom = Weight(96.5, lb),
                incrementTiers = tiers,
                loadType = LoadType.Weighted,
                capacityAtRir = alwaysClears,
                micro = kgMicro,
            )

        assertEquals(92.5, fromLb.amount, 1e-9)
        assertEquals(fromLb.amount, fromKg.amount, 1e-9)
        assertEquals(fromLb.unit, fromKg.unit)
    }
}
