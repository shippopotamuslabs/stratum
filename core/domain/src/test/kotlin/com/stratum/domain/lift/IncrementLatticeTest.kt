package com.stratum.domain.lift

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class IncrementLatticeTest {

    private val lb = Weight.WeightUnit.Lb

    private fun openBand(step: Double): List<IncrementTier> =
        listOf(
            IncrementTier(
                lowerBound = Weight(0.0, lb),
                upperBound = null,
                increment = Weight(step, lb),
            ),
        )

    private fun lb(amount: Double) = Weight(amount, lb)

    @Nested
    @DisplayName("residual")
    inner class Residual {
        @Test
        @DisplayName("Given 60 on a 5-step lattice when residual then zero")
        fun onLatticeSixty() {
            val tiers = openBand(5.0)
            assertEquals(0.0, tiers.residual(lb(60.0)), 1e-9)
        }

        @Test
        @DisplayName("Given 62.5 on a 5-step lattice when residual then 2.5")
        fun offLatticeSixtyTwoFive() {
            val tiers = openBand(5.0)
            // rounds to 60 or 65 (half-away-from-zero → 65? 62.5/5=12.5 → 13*5=65)
            // residual = |62.5 - 65| = 2.5  OR |62.5-60|=2.5 depending on half-away
            val residual = tiers.residual(lb(62.5))
            assertEquals(2.5, residual, 1e-9)
        }

        @Test
        @DisplayName("Given empty tiers when residual then infinity")
        fun emptyTiersInfinity() {
            assertEquals(Double.POSITIVE_INFINITY, emptyList<IncrementTier>().residual(lb(60.0)))
        }

        @Test
        @DisplayName("Given bodyweight when residual then zero")
        fun bodyweightZero() {
            val tiers = openBand(5.0)
            assertEquals(0.0, tiers.residual(lb(0.0), LoadType.Bodyweight), 0.0)
        }

        @Test
        @DisplayName("Given assisted weight when residual then uses magnitude bands")
        fun assistedUsesMagnitude() {
            val tiers = openBand(5.0)
            // -47.3 rounds to -45; residual 2.3
            assertEquals(2.3, tiers.residual(lb(-47.3), LoadType.Assisted), 1e-9)
        }
    }

    @Nested
    @DisplayName("isOnLattice")
    inner class OnLattice {
        @Test
        @DisplayName("Given 60 on a 5-step lattice when checked then on-lattice")
        fun sixtyOnLattice() {
            assertTrue(openBand(5.0).isOnLattice(lb(60.0)))
        }

        @Test
        @DisplayName("Given 62.5 on a 5-step lattice when checked then off-lattice")
        fun sixtyTwoFiveOffLattice() {
            assertFalse(openBand(5.0).isOnLattice(lb(62.5)))
        }

        @Test
        @DisplayName("Given floating noise near a plate when checked then on-lattice")
        fun floatingNoiseNearPlate() {
            // 100/3 ≈ 33.333… not relevant; 60 + 1e-9 noise
            val noisy = lb(60.0 + 1e-9)
            assertTrue(openBand(5.0).isOnLattice(noisy))
            // Within ε=0.05 of 60
            assertTrue(openBand(5.0).isOnLattice(lb(60.04)))
            assertFalse(openBand(5.0).isOnLattice(lb(60.06)))
        }

        @Test
        @DisplayName("Given empty tiers when checked then not on-lattice")
        fun emptyNotOnLattice() {
            assertFalse(emptyList<IncrementTier>().isOnLattice(lb(60.0)))
        }

        @Test
        @DisplayName("Given bodyweight when checked then treated as on-lattice")
        fun bodyweightOnLattice() {
            assertTrue(openBand(5.0).isOnLattice(lb(0.0), LoadType.Bodyweight))
        }

        @Test
        @DisplayName("Given assisted on-lattice magnitude when checked then on-lattice")
        fun assistedOnLattice() {
            assertTrue(openBand(5.0).isOnLattice(lb(-45.0), LoadType.Assisted))
            assertFalse(openBand(5.0).isOnLattice(lb(-47.3), LoadType.Assisted))
        }

        @Test
        @DisplayName("Given weight on 2.5 lattice when checked then on-lattice")
        fun twoPointFiveLattice() {
            val tiers = openBand(2.5)
            assertTrue(tiers.isOnLattice(lb(62.5)))
            assertTrue(tiers.isOnLattice(lb(67.5)))
        }
    }
}
