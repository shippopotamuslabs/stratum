package com.stratum.domain.lift

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("warnedIndexesForSamples")
class IncrementTierWarnedSamplesTest {

    private val lb = Weight.WeightUnit.Lb
    private val twoBand =
        listOf(
            IncrementTier(Weight(0.0, lb), Weight(40.0, lb), Weight(2.5, lb)),
            IncrementTier(Weight(40.0, lb), null, Weight(5.0, lb)),
        )

    @Test
    @DisplayName("Given on-lattice samples when checked then no bands warn")
    fun onLatticeSamplesWarnNothing() {
        assertTrue(twoBand.warnedIndexesForSamples(listOf(60.0, 100.0, 37.5)).isEmpty())
    }

    @Test
    @DisplayName("Given off-lattice sample when checked then only its band warns")
    fun offLatticeSampleWarnsItsBand() {
        assertEquals(setOf(1), twoBand.warnedIndexesForSamples(listOf(62.5)))
    }

    @Test
    @DisplayName("Given band-boundary sample when checked then no warning")
    fun boundarySampleWarnsNothing() {
        assertTrue(twoBand.warnedIndexesForSamples(listOf(40.0)).isEmpty())
    }

    @Test
    @DisplayName("Given empty samples or tiers when checked then no bands warn")
    fun emptyInputsWarnNothing() {
        assertTrue(twoBand.warnedIndexesForSamples(emptyList()).isEmpty())
        assertTrue(emptyList<IncrementTier>().warnedIndexesForSamples(listOf(62.5)).isEmpty())
    }

    @Test
    @DisplayName("Given non-positive samples when checked then they are ignored")
    fun nonPositiveSamplesIgnored() {
        assertTrue(twoBand.warnedIndexesForSamples(listOf(0.0, -5.0)).isEmpty())
    }
}
