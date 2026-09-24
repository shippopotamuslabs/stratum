package com.stratum.domain.lift

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class IncrementLatticeAdderTest {

    private val lb = Weight.WeightUnit.Lb

    /** Pin-loaded stack: 15 lb plates with a 0 / 5 / 10 adder knob. */
    private val pinTiers = listOf(IncrementTier(Weight(0.0, lb), null, Weight(15.0, lb)))
    private val pinMicro = listOf(Weight(5.0, lb), Weight(10.0, lb))

    @Test
    @DisplayName("Given base plus adder when checked then on lattice with zero residual")
    fun adderExactIsOnLattice() {
        val weight = Weight(65.0, lb)

        assertTrue(pinTiers.isOnLattice(weight, micro = pinMicro))
        assertEquals(0.0, pinTiers.residual(weight, micro = pinMicro), 0.0)
        assertEquals(weight, pinTiers.roundToIncrement(weight, micro = pinMicro))
    }

    @Test
    @DisplayName("Given bare stack load when checked then on lattice via knob-off")
    fun bareStackIsOnLattice() {
        assertTrue(pinTiers.isOnLattice(Weight(60.0, lb), micro = pinMicro))
    }

    @Test
    @DisplayName("Given second adder stop when rounded then lands on base plus that stop")
    fun secondStopRounding() {
        assertEquals(
            Weight(70.0, lb),
            pinTiers.roundToIncrement(Weight(70.0, lb), micro = pinMicro),
        )
    }

    @Test
    @DisplayName("Given truly off weight when checked then off lattice and rounds to bare stack")
    fun trulyOffStillDetected() {
        val weight = Weight(62.0, lb)

        assertFalse(pinTiers.isOnLattice(weight, micro = pinMicro))
        assertEquals(
            Weight(60.0, lb),
            pinTiers.roundToIncrement(weight, micro = pinMicro),
        )
    }

    @Test
    @DisplayName("Given empty micro when checked then plain major-lattice behavior")
    fun emptyMicroPassthrough() {
        val fiveStep = listOf(IncrementTier(Weight(0.0, lb), null, Weight(5.0, lb)))

        assertFalse(fiveStep.isOnLattice(Weight(62.5, lb)))
        assertEquals(
            Weight(65.0, lb),
            fiveStep.roundToIncrement(Weight(62.5, lb)),
        )
    }

    @Test
    @DisplayName("Given tie between stops when rounded then half away from zero wins")
    fun tiePrefersLarger() {
        val tenStep = listOf(IncrementTier(Weight(0.0, lb), null, Weight(10.0, lb)))
        val micro = listOf(Weight(5.0, lb))

        assertEquals(
            Weight(10.0, lb),
            tenStep.roundToIncrement(Weight(7.5, lb), micro = micro),
        )
    }

    @Test
    @DisplayName("Given multi-band tiers when rounded then adder applies past the split")
    fun multiBandAdder() {
        val bands =
            listOf(
                IncrementTier(Weight(0.0, lb), Weight(40.0, lb), Weight(2.5, lb)),
                IncrementTier(Weight(40.0, lb), null, Weight(5.0, lb)),
            )
        val micro = listOf(Weight(2.5, lb))

        assertEquals(
            Weight(42.5, lb),
            bands.roundToIncrement(Weight(42.5, lb), micro = micro),
        )
        assertTrue(bands.isOnLattice(Weight(42.5, lb), micro = micro))
    }

    @Test
    @DisplayName("Given assisted adder-exact load when rounded then sign preserved")
    fun assistedSignPreserved() {
        assertEquals(
            Weight(-65.0, lb),
            pinTiers.roundToIncrement(Weight(-65.0, lb), LoadType.Assisted, pinMicro),
        )
        assertTrue(
            pinTiers.isOnLattice(Weight(-65.0, lb), LoadType.Assisted, micro = pinMicro),
        )
    }

    @Test
    @DisplayName("Given bodyweight when checked then lattice is bypassed even with micro")
    fun bodyweightBypassed() {
        val weight = Weight(0.0, lb)

        assertTrue(pinTiers.isOnLattice(weight, LoadType.Bodyweight, micro = pinMicro))
        assertEquals(0.0, pinTiers.residual(weight, LoadType.Bodyweight, pinMicro), 0.0)
    }

    @Test
    @DisplayName("Given mixed-unit micro stops when rounded then fails fast")
    fun mixedUnitMicroFailsFast() {
        val micro = listOf(Weight(5.0, Weight.WeightUnit.Kg))

        assertThrows(IllegalArgumentException::class.java) {
            pinTiers.roundToIncrement(Weight(65.0, lb), micro = micro)
        }
    }

    @Test
    @DisplayName("Given duplicate micro stops when rounded then adder-exact still holds")
    fun duplicateStopsNormalized() {
        val duplicated = listOf(Weight(5.0, lb), Weight(5.0, lb), Weight(10.0, lb))

        assertEquals(
            Weight(65.0, lb),
            pinTiers.roundToIncrement(Weight(65.0, lb), micro = duplicated),
        )
        assertTrue(pinTiers.isOnLattice(Weight(65.0, lb), micro = duplicated))
    }

    @Test
    @DisplayName("Given empty tiers when checked then no lattice even with micro")
    fun emptyTiersNoLattice() {
        val weight = Weight(65.0, lb)

        assertFalse(emptyList<IncrementTier>().isOnLattice(weight, micro = pinMicro))
        assertEquals(
            weight,
            emptyList<IncrementTier>().roundToIncrement(weight, micro = pinMicro),
        )
    }
}
