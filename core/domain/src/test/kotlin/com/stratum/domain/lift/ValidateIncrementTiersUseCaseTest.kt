package com.stratum.domain.lift

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import com.stratum.domain.lift.Weight.WeightUnit.Lb

class ValidateIncrementTiersUseCaseTest {

    private lateinit var subject: ValidateIncrementTiersUseCase

    @BeforeEach
    fun setup() {
        subject = ValidateIncrementTiersUseCase()
    }

    @Nested
    @DisplayName("valid shapes")
    inner class ValidShapes {
        @Test
        @DisplayName("Given no tiers when validated then it is valid")
        fun emptyIsValid() {
            assertEquals(IncrementTiersValidationResult.Valid, subject.invoke(emptyList()))
        }

        @Test
        @DisplayName("Given a single open-ended tier starting at 0 when validated then it is valid")
        fun singleOpenTierIsValid() {
            val tiers = listOf(tier(lower = 0.0, upper = null, inc = 5.0))
            assertEquals(IncrementTiersValidationResult.Valid, subject.invoke(tiers))
        }

        @Test
        @DisplayName("Given contiguous tiling bands with an open final tier when validated then it is valid")
        fun tilingBandsAreValid() {
            val tiers = listOf(
                tier(lower = 0.0, upper = 40.0, inc = 2.5),
                tier(lower = 40.0, upper = 100.0, inc = 5.0),
                tier(lower = 100.0, upper = 300.0, inc = 10.0),
                tier(lower = 300.0, upper = null, inc = 25.0),
            )
            assertEquals(IncrementTiersValidationResult.Valid, subject.invoke(tiers))
        }

        @Test
        @DisplayName("Given equal increments across bands when validated then it is valid")
        fun equalIncrementsAreValid() {
            val tiers = listOf(
                tier(lower = 0.0, upper = 50.0, inc = 5.0),
                tier(lower = 50.0, upper = null, inc = 5.0),
            )
            assertEquals(IncrementTiersValidationResult.Valid, subject.invoke(tiers))
        }

        @Test
        @DisplayName("Given a single open band starting above 0 when validated then it is valid")
        fun singleShiftedBandIsValid() {
            val tiers = listOf(tier(lower = 7.5, upper = null, inc = 5.0))
            assertEquals(IncrementTiersValidationResult.Valid, subject.invoke(tiers))
        }

        @Test
        @DisplayName("Given tiling bands starting above 0 when validated then it is valid")
        fun shiftedTilingBandsAreValid() {
            val tiers = listOf(
                tier(lower = 7.5, upper = 20.0, inc = 5.0),
                tier(lower = 20.0, upper = null, inc = 10.0),
            )
            assertEquals(IncrementTiersValidationResult.Valid, subject.invoke(tiers))
        }
    }

    @Nested
    @DisplayName("invalid shapes")
    inner class InvalidShapes {
        @Test
        @DisplayName("Given a gap between bands when validated then NotContiguous is returned")
        fun gapIsInvalid() {
            val tiers = listOf(
                tier(lower = 0.0, upper = 40.0, inc = 2.5),
                tier(lower = 50.0, upper = null, inc = 5.0),
            )
            assertInvalid(subject.invoke(tiers), IncrementTiersValidationError.NotContiguous)
        }

        @Test
        @DisplayName("Given an intermediate tier missing an upper bound when validated then it is invalid")
        fun intermediateTierMissingUpperBoundIsInvalid() {
            val tiers = listOf(
                tier(lower = 0.0, upper = null, inc = 2.5),
                tier(lower = 0.0, upper = null, inc = 5.0),
            )
            assertInvalid(subject.invoke(tiers), IncrementTiersValidationError.IntermediateTierMissingUpperBound)
        }

        @Test
        @DisplayName("Given a final tier with an upper bound when validated then it is invalid")
        fun closedFinalTierIsInvalid() {
            val tiers = listOf(
                tier(lower = 0.0, upper = 100.0, inc = 5.0),
                tier(lower = 100.0, upper = 200.0, inc = 10.0),
            )
            assertInvalid(subject.invoke(tiers), IncrementTiersValidationError.FinalTierMustBeOpenEnded)
        }

        @Test
        @DisplayName("Given a zero-width band when validated then ZeroWidthBand is returned")
        fun zeroWidthBandIsInvalid() {
            val tiers = listOf(
                tier(lower = 0.0, upper = 0.0, inc = 5.0),
                tier(lower = 0.0, upper = null, inc = 5.0),
            )
            assertInvalid(subject.invoke(tiers), IncrementTiersValidationError.ZeroWidthBand)
        }

        @Test
        @DisplayName("Given mixed units when validated then MixedUnits is returned")
        fun mixedUnitsIsInvalid() {
            val tiers = listOf(
                IncrementTier(
                    lowerBound = Weight(0.0, Lb),
                    upperBound = Weight(40.0, Weight.WeightUnit.Kg),
                    increment = Weight(2.5, Lb),
                ),
                IncrementTier(
                    lowerBound = Weight(40.0, Weight.WeightUnit.Kg),
                    upperBound = null,
                    increment = Weight(5.0, Lb),
                ),
            )
            assertInvalid(subject.invoke(tiers), IncrementTiersValidationError.MixedUnits)
        }

        @Test
        @DisplayName("Given a decreasing increment when validated then IncrementDecreased is returned")
        fun decreasingIncrementIsInvalid() {
            val tiers = listOf(
                tier(lower = 0.0, upper = 100.0, inc = 10.0),
                tier(lower = 100.0, upper = null, inc = 5.0),
            )
            assertInvalid(subject.invoke(tiers), IncrementTiersValidationError.IncrementDecreased)
        }

        @Test
        @DisplayName("Given a first band starting below 0 when validated then NegativeLowerBound is returned")
        fun negativeStartIsInvalid() {
            val tiers = listOf(tier(lower = -5.0, upper = null, inc = 5.0))
            assertInvalid(subject.invoke(tiers), IncrementTiersValidationError.NegativeLowerBound)
        }

        @Test
        @DisplayName("Given a gap after a shifted start when validated then NotContiguous is returned")
        fun gapAfterShiftedStartIsInvalid() {
            val tiers = listOf(
                tier(lower = 7.5, upper = 20.0, inc = 5.0),
                tier(lower = 25.0, upper = null, inc = 5.0),
            )
            assertInvalid(subject.invoke(tiers), IncrementTiersValidationError.NotContiguous)
        }
    }

    private fun tier(lower: Double, upper: Double?, inc: Double): IncrementTier =
        IncrementTier(
            lowerBound = Weight(lower, Lb),
            upperBound = upper?.let { Weight(it, Lb) },
            increment = Weight(inc, Lb),
        )

    private fun assertInvalid(
        result: IncrementTiersValidationResult,
        expected: IncrementTiersValidationError,
    ) {
        assertTrue(result is IncrementTiersValidationResult.Invalid, "Expected Invalid, got $result")
        assertTrue(
            (result as IncrementTiersValidationResult.Invalid).errors.contains(expected),
            "Expected $expected in ${result.errors}",
        )
    }
}
