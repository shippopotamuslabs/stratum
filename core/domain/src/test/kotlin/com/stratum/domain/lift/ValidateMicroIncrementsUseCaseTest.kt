package com.stratum.domain.lift

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ValidateMicroIncrementsUseCaseTest {

    private val lb = Weight.WeightUnit.Lb
    private val kg = Weight.WeightUnit.Kg
    private val validate = ValidateMicroIncrementsUseCase()

    private val singleFifteen = listOf(tier(0.0, null, 15.0))
    private val fineThenCoarse =
        listOf(
            tier(0.0, 40.0, 2.5),
            tier(40.0, null, 10.0),
        )

    @Test
    @DisplayName("Given empty micro when validated then valid even with empty tiers")
    fun emptyMicroAlwaysValid() {
        assertEquals(
            MicroIncrementsValidationResult.Valid,
            validate(emptyList(), emptyList()),
        )
        assertEquals(
            MicroIncrementsValidationResult.Valid,
            validate(singleFifteen, emptyList()),
        )
    }

    @Test
    @DisplayName("Given stops within the largest increment when validated then valid")
    fun stopsWithinLargestIncrement() {
        assertEquals(
            MicroIncrementsValidationResult.Valid,
            validate(singleFifteen, listOf(stop(5.0), stop(10.0))),
        )
    }

    @Test
    @DisplayName("Given stop equal to the largest increment when validated then valid")
    fun stopEqualToLargestIncrement() {
        assertEquals(
            MicroIncrementsValidationResult.Valid,
            validate(fineThenCoarse, listOf(stop(10.0))),
        )
    }

    @Test
    @DisplayName("Given stop above the largest increment when validated then rejected")
    fun stopAboveLargestIncrement() {
        val result = validate(singleFifteen, listOf(stop(20.0)))

        assertTrue(result is MicroIncrementsValidationResult.Invalid)
        assertEquals(
            setOf(MicroIncrementsValidationError.ExceedsLargestIncrement),
            (result as MicroIncrementsValidationResult.Invalid).errors,
        )
    }

    @Test
    @DisplayName("Given unsorted stops when validated then rejected")
    fun unsortedStops() {
        val result = validate(singleFifteen, listOf(stop(10.0), stop(5.0)))

        assertTrue(result is MicroIncrementsValidationResult.Invalid)
        assertEquals(
            setOf(MicroIncrementsValidationError.UnsortedOrDuplicate),
            (result as MicroIncrementsValidationResult.Invalid).errors,
        )
    }

    @Test
    @DisplayName("Given duplicate stops when validated then rejected")
    fun duplicateStops() {
        val result = validate(singleFifteen, listOf(stop(5.0), stop(5.0)))

        assertTrue(result is MicroIncrementsValidationResult.Invalid)
        assertEquals(
            setOf(MicroIncrementsValidationError.UnsortedOrDuplicate),
            (result as MicroIncrementsValidationResult.Invalid).errors,
        )
    }

    @Test
    @DisplayName("Given negative stop when validated then rejected")
    fun negativeStop() {
        val result = validate(singleFifteen, listOf(stop(-5.0), stop(5.0)))

        assertTrue(result is MicroIncrementsValidationResult.Invalid)
        assertTrue(
            (result as MicroIncrementsValidationResult.Invalid).errors.contains(
                MicroIncrementsValidationError.NegativeStop,
            ),
        )
    }

    @Test
    @DisplayName("Given lone zero stop when validated then rejected as meaningless")
    fun loneZeroMeaningless() {
        val result = validate(singleFifteen, listOf(stop(0.0)))

        assertTrue(result is MicroIncrementsValidationResult.Invalid)
        assertEquals(
            setOf(MicroIncrementsValidationError.MeaninglessZero),
            (result as MicroIncrementsValidationResult.Invalid).errors,
        )
    }

    @Test
    @DisplayName("Given zero alongside other stops when validated then rejected as meaningless")
    fun zeroAmongStopsMeaningless() {
        val result = validate(singleFifteen, listOf(stop(0.0), stop(5.0)))

        assertTrue(result is MicroIncrementsValidationResult.Invalid)
        assertEquals(
            setOf(MicroIncrementsValidationError.MeaninglessZero),
            (result as MicroIncrementsValidationResult.Invalid).errors,
        )
    }

    @Test
    @DisplayName("Given trailing zero stop when validated then rejected as meaningless")
    fun trailingZeroMeaningless() {
        val result = validate(singleFifteen, listOf(stop(5.0), stop(0.0)))

        assertTrue(result is MicroIncrementsValidationResult.Invalid)
        assertTrue(
            (result as MicroIncrementsValidationResult.Invalid).errors.contains(
                MicroIncrementsValidationError.MeaninglessZero,
            ),
        )
    }

    @Test
    @DisplayName("Given mixed units when validated then rejected")
    fun mixedUnits() {
        val result =
            validate(
                singleFifteen,
                listOf(Weight(5.0, kg)),
            )

        assertTrue(result is MicroIncrementsValidationResult.Invalid)
        assertEquals(
            setOf(MicroIncrementsValidationError.MixedUnits),
            (result as MicroIncrementsValidationResult.Invalid).errors,
        )
    }

    @Test
    @DisplayName("Given micro without tiers when validated then rejected")
    fun microWithoutTiers() {
        val result = validate(emptyList(), listOf(stop(5.0)))

        assertTrue(result is MicroIncrementsValidationResult.Invalid)
        assertEquals(
            setOf(MicroIncrementsValidationError.MicroWithoutTiers),
            (result as MicroIncrementsValidationResult.Invalid).errors,
        )
    }

    private fun tier(lower: Double, upper: Double?, inc: Double): IncrementTier =
        IncrementTier(
            lowerBound = Weight(lower, lb),
            upperBound = upper?.let { Weight(it, lb) },
            increment = Weight(inc, lb),
        )

    private fun stop(amount: Double): Weight = Weight(amount, lb)
}
