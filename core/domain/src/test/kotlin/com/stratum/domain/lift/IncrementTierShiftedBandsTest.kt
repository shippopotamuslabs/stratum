package com.stratum.domain.lift

import com.stratum.domain.lift.Weight.WeightUnit.Lb
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class IncrementTierShiftedBandsTest {

    private val shifted = listOf(tier(lower = 7.5, upper = null, inc = 5.0))

    @Nested
    @DisplayName("incrementForAmount")
    inner class IncrementForAmount {
        @Test
        @DisplayName("Given an amount below the lowest band when resolved then the first tier step is used")
        fun belowLowestBandFallsBackToFirstTier() {
            assertEquals(5.0, shifted.incrementForAmount(2.0))
        }

        @Test
        @DisplayName("Given an amount inside the band when resolved then that band step is used")
        fun insideBandUsesBandStep() {
            assertEquals(5.0, shifted.incrementForAmount(30.0))
        }
    }

    @Nested
    @DisplayName("roundToIncrement")
    inner class RoundToIncrement {
        @Test
        @DisplayName("Given a load near a shifted lattice point when rounded then it snaps to that point")
        fun snapsToShiftedLattice() {
            assertEquals(Weight(7.5, Lb), shifted.roundToIncrement(Weight(9.0, Lb)))
            assertEquals(Weight(12.5, Lb), shifted.roundToIncrement(Weight(11.0, Lb)))
        }

        @Test
        @DisplayName("Given a load on a shifted lattice point when rounded then it is unchanged")
        fun latticePointIsStable() {
            assertEquals(Weight(7.5, Lb), shifted.roundToIncrement(Weight(7.5, Lb)))
        }
    }

    private fun tier(lower: Double, upper: Double?, inc: Double): IncrementTier =
        IncrementTier(
            lowerBound = Weight(lower, Lb),
            upperBound = upper?.let { Weight(it, Lb) },
            increment = Weight(inc, Lb),
        )
}
