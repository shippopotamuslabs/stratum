package com.stratum.domain.lift

import com.stratum.domain.lift.Weight.WeightUnit.Lb
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * STR-165: the tier and micro rules that used to live in the deleted validator use
 * cases now live on [IncrementLattice]. Every rejected shape is asserted twice:
 * the non-throwing [IncrementLattice.validateTiers] / [IncrementLattice.validateMicro]
 * result (for drafts) and the construction exception (invalid lattices are
 * unrepresentable).
 */
class IncrementLatticeValidationTest {

    @Nested
    @DisplayName("valid tier shapes")
    inner class ValidTierShapes {
        @Test
        @DisplayName("Given no tiers when validated then it is valid")
        fun emptyIsValid() {
            assertEquals(IncrementTiersValidationResult.Valid, IncrementLattice.validateTiers(emptyList()))
            assertEquals(IncrementLattice.EMPTY, IncrementLattice(emptyList()))
        }

        @Test
        @DisplayName("Given a single open-ended tier starting at 0 when validated then it is valid")
        fun singleOpenTierIsValid() {
            val tiers = listOf(tier(lower = 0.0, upper = null, inc = 5.0))
            assertEquals(IncrementTiersValidationResult.Valid, IncrementLattice.validateTiers(tiers))
            assertEquals(tiers, IncrementLattice(tiers).tiers)
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
            assertEquals(IncrementTiersValidationResult.Valid, IncrementLattice.validateTiers(tiers))
            assertEquals(tiers, IncrementLattice(tiers).tiers)
        }

        @Test
        @DisplayName("Given equal increments across bands when validated then it is valid")
        fun equalIncrementsAreValid() {
            val tiers = listOf(
                tier(lower = 0.0, upper = 50.0, inc = 5.0),
                tier(lower = 50.0, upper = null, inc = 5.0),
            )
            assertEquals(IncrementTiersValidationResult.Valid, IncrementLattice.validateTiers(tiers))
            IncrementLattice(tiers)
        }

        @Test
        @DisplayName("Given a single open band starting above 0 when validated then it is valid")
        fun singleShiftedBandIsValid() {
            val tiers = listOf(tier(lower = 7.5, upper = null, inc = 5.0))
            assertEquals(IncrementTiersValidationResult.Valid, IncrementLattice.validateTiers(tiers))
            IncrementLattice(tiers)
        }

        @Test
        @DisplayName("Given tiling bands starting above 0 when validated then it is valid")
        fun shiftedTilingBandsAreValid() {
            val tiers = listOf(
                tier(lower = 7.5, upper = 20.0, inc = 5.0),
                tier(lower = 20.0, upper = null, inc = 10.0),
            )
            assertEquals(IncrementTiersValidationResult.Valid, IncrementLattice.validateTiers(tiers))
            IncrementLattice(tiers)
        }
    }

    @Nested
    @DisplayName("invalid tier shapes")
    inner class InvalidTierShapes {
        @Test
        @DisplayName("Given a gap between bands when constructed then NotContiguous is thrown")
        fun gapIsInvalid() {
            val tiers = listOf(
                tier(lower = 0.0, upper = 40.0, inc = 2.5),
                tier(lower = 50.0, upper = null, inc = 5.0),
            )
            assertInvalidTiers(tiers, IncrementTiersValidationError.NotContiguous)
        }

        @Test
        @DisplayName("Given an intermediate tier missing an upper bound when constructed then it is invalid")
        fun intermediateTierMissingUpperBoundIsInvalid() {
            val tiers = listOf(
                tier(lower = 0.0, upper = null, inc = 2.5),
                tier(lower = 0.0, upper = null, inc = 5.0),
            )
            assertInvalidTiers(tiers, IncrementTiersValidationError.IntermediateTierMissingUpperBound)
        }

        @Test
        @DisplayName("Given a final tier with an upper bound when constructed then it is invalid")
        fun closedFinalTierIsInvalid() {
            val tiers = listOf(
                tier(lower = 0.0, upper = 100.0, inc = 5.0),
                tier(lower = 100.0, upper = 200.0, inc = 10.0),
            )
            assertInvalidTiers(tiers, IncrementTiersValidationError.FinalTierMustBeOpenEnded)
        }

        @Test
        @DisplayName("Given a zero-width band when constructed then ZeroWidthBand is thrown")
        fun zeroWidthBandIsInvalid() {
            val tiers = listOf(
                tier(lower = 0.0, upper = 0.0, inc = 5.0),
                tier(lower = 0.0, upper = null, inc = 5.0),
            )
            assertInvalidTiers(tiers, IncrementTiersValidationError.ZeroWidthBand)
        }

        @Test
        @DisplayName("Given mixed units when constructed then MixedUnits is thrown")
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
            assertInvalidTiers(tiers, IncrementTiersValidationError.MixedUnits)
        }

        @Test
        @DisplayName("Given a decreasing increment when constructed then IncrementDecreased is thrown")
        fun decreasingIncrementIsInvalid() {
            val tiers = listOf(
                tier(lower = 0.0, upper = 100.0, inc = 10.0),
                tier(lower = 100.0, upper = null, inc = 5.0),
            )
            assertInvalidTiers(tiers, IncrementTiersValidationError.IncrementDecreased)
        }

        @Test
        @DisplayName("Given a first band starting below 0 when constructed then NegativeLowerBound is thrown")
        fun negativeStartIsInvalid() {
            val tiers = listOf(tier(lower = -5.0, upper = null, inc = 5.0))
            assertInvalidTiers(tiers, IncrementTiersValidationError.NegativeLowerBound)
        }

        @Test
        @DisplayName("Given a gap after a shifted start when constructed then NotContiguous is thrown")
        fun gapAfterShiftedStartIsInvalid() {
            val tiers = listOf(
                tier(lower = 7.5, upper = 20.0, inc = 5.0),
                tier(lower = 25.0, upper = null, inc = 5.0),
            )
            assertInvalidTiers(tiers, IncrementTiersValidationError.NotContiguous)
        }
    }

    @Nested
    @DisplayName("micro stops")
    inner class MicroStops {
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
                IncrementLattice.validateMicro(emptyList(), emptyList()),
            )
            assertEquals(
                MicroIncrementsValidationResult.Valid,
                IncrementLattice.validateMicro(singleFifteen, emptyList()),
            )
            IncrementLattice(emptyList(), emptyList())
        }

        @Test
        @DisplayName("Given stops within the largest increment when validated then valid")
        fun stopsWithinLargestIncrement() {
            val micro = listOf(stop(5.0), stop(10.0))
            assertEquals(
                MicroIncrementsValidationResult.Valid,
                IncrementLattice.validateMicro(singleFifteen, micro),
            )
            assertEquals(micro, IncrementLattice(singleFifteen, micro).microIncrements)
        }

        @Test
        @DisplayName("Given stop equal to the largest increment when validated then valid")
        fun stopEqualToLargestIncrement() {
            assertEquals(
                MicroIncrementsValidationResult.Valid,
                IncrementLattice.validateMicro(fineThenCoarse, listOf(stop(10.0))),
            )
            IncrementLattice(fineThenCoarse, listOf(stop(10.0)))
        }

        @Test
        @DisplayName("Given stop above the largest increment when constructed then rejected")
        fun stopAboveLargestIncrement() {
            assertInvalidMicro(
                singleFifteen,
                listOf(stop(20.0)),
                setOf(MicroIncrementsValidationError.ExceedsLargestIncrement),
            )
        }

        @Test
        @DisplayName("Given unsorted stops when constructed then rejected")
        fun unsortedStops() {
            assertInvalidMicro(
                singleFifteen,
                listOf(stop(10.0), stop(5.0)),
                setOf(MicroIncrementsValidationError.UnsortedOrDuplicate),
            )
        }

        @Test
        @DisplayName("Given duplicate stops when constructed then rejected")
        fun duplicateStops() {
            assertInvalidMicro(
                singleFifteen,
                listOf(stop(5.0), stop(5.0)),
                setOf(MicroIncrementsValidationError.UnsortedOrDuplicate),
            )
        }

        @Test
        @DisplayName("Given negative stop when constructed then rejected")
        fun negativeStop() {
            val result = IncrementLattice.validateMicro(singleFifteen, listOf(stop(-5.0), stop(5.0)))
            assertTrue(result is MicroIncrementsValidationResult.Invalid)
            assertTrue(
                (result as MicroIncrementsValidationResult.Invalid).errors.contains(
                    MicroIncrementsValidationError.NegativeStop,
                ),
            )
            assertThrows<MicroIncrementsInvalidException> {
                IncrementLattice(singleFifteen, listOf(stop(-5.0), stop(5.0)))
            }
        }

        @Test
        @DisplayName("Given lone zero stop when constructed then rejected as meaningless")
        fun loneZeroMeaningless() {
            assertInvalidMicro(
                singleFifteen,
                listOf(stop(0.0)),
                setOf(MicroIncrementsValidationError.MeaninglessZero),
            )
        }

        @Test
        @DisplayName("Given zero alongside other stops when constructed then rejected as meaningless")
        fun zeroAmongStopsMeaningless() {
            assertInvalidMicro(
                singleFifteen,
                listOf(stop(0.0), stop(5.0)),
                setOf(MicroIncrementsValidationError.MeaninglessZero),
            )
        }

        @Test
        @DisplayName("Given trailing zero stop when constructed then rejected as meaningless")
        fun trailingZeroMeaningless() {
            val result = IncrementLattice.validateMicro(singleFifteen, listOf(stop(5.0), stop(0.0)))
            assertTrue(result is MicroIncrementsValidationResult.Invalid)
            assertTrue(
                (result as MicroIncrementsValidationResult.Invalid).errors.contains(
                    MicroIncrementsValidationError.MeaninglessZero,
                ),
            )
            assertThrows<MicroIncrementsInvalidException> {
                IncrementLattice(singleFifteen, listOf(stop(5.0), stop(0.0)))
            }
        }

        @Test
        @DisplayName("Given mixed units when constructed then rejected")
        fun mixedUnits() {
            assertInvalidMicro(
                singleFifteen,
                listOf(Weight(5.0, Weight.WeightUnit.Kg)),
                setOf(MicroIncrementsValidationError.MixedUnits),
            )
        }

        @Test
        @DisplayName("Given micro without tiers when constructed then rejected")
        fun microWithoutTiers() {
            assertInvalidMicro(
                emptyList(),
                listOf(stop(5.0)),
                setOf(MicroIncrementsValidationError.MicroWithoutTiers),
            )
        }
    }

    @Nested
    @DisplayName("canonical construction surfaces")
    inner class ConstructionSurfaces {
        @Test
        @DisplayName("Given valid tiers when resultOf is used then success carries the lattice")
        fun resultOfSuccess() {
            val tiers = listOf(tier(0.0, null, 15.0))
            val micro = listOf(stop(5.0))
            val result = IncrementLattice.resultOf(tiers, micro)
            assertTrue(result.isSuccess)
            assertEquals(IncrementLattice(tiers, micro), result.getOrThrow())
        }

        @Test
        @DisplayName("Given invalid tiers when resultOf is used then failure carries the tier exception")
        fun resultOfTierFailure() {
            val tiers = listOf(tier(0.0, 100.0, 5.0))
            val result = IncrementLattice.resultOf(tiers)
            assertTrue(result.exceptionOrNull() is IncrementTiersInvalidException)
        }

        @Test
        @DisplayName("Given invalid micro when resultOf is used then failure carries the micro exception")
        fun resultOfMicroFailure() {
            val result = IncrementLattice.resultOf(listOf(tier(0.0, null, 15.0)), listOf(stop(20.0)))
            assertTrue(result.exceptionOrNull() is MicroIncrementsInvalidException)
        }

        @Test
        @DisplayName("Given stored equipment when requireLattice is called then the lattice is returned")
        fun equipmentRequireLattice() {
            val tiers = listOf(tier(0.0, null, 15.0))
            val equipment =
                Equipment(
                    id = com.stratum.domain.lift.identity.EquipmentId("eq-1"),
                    name = "Stack",
                    incrementTiers = tiers,
                    microIncrements = listOf(stop(5.0)),
                )
            assertEquals(IncrementLattice(tiers, listOf(stop(5.0))), equipment.requireLattice())
        }

        @Test
        @DisplayName("Given stops carried against replacement tiers when requireLattice is called then it throws")
        fun equipmentRequireLatticeThrowsOnHandOffState() {
            val equipment =
                Equipment(
                    id = com.stratum.domain.lift.identity.EquipmentId("eq-2"),
                    name = "Stack copy",
                    incrementTiers = listOf(tier(0.0, null, 2.5)),
                    microIncrements = listOf(stop(5.0)),
                )
            assertThrows<MicroIncrementsInvalidException> { equipment.requireLattice() }
        }
    }

    private fun assertInvalidTiers(
        tiers: List<IncrementTier>,
        expected: IncrementTiersValidationError,
    ) {
        val result = IncrementLattice.validateTiers(tiers)
        assertTrue(result is IncrementTiersValidationResult.Invalid, "Expected Invalid, got $result")
        assertTrue(
            (result as IncrementTiersValidationResult.Invalid).errors.contains(expected),
            "Expected $expected in ${result.errors}",
        )
        val thrown = assertThrows<IncrementTiersInvalidException> { IncrementLattice(tiers) }
        assertTrue(thrown.errors.contains(expected), "Expected $expected in ${thrown.errors}")
    }

    private fun assertInvalidMicro(
        tiers: List<IncrementTier>,
        micro: List<Weight>,
        expectedErrors: Set<MicroIncrementsValidationError>,
    ) {
        val result = IncrementLattice.validateMicro(tiers, micro)
        assertTrue(result is MicroIncrementsValidationResult.Invalid)
        assertEquals(expectedErrors, (result as MicroIncrementsValidationResult.Invalid).errors)
        val thrown = assertThrows<MicroIncrementsInvalidException> { IncrementLattice(tiers, micro) }
        assertEquals(expectedErrors, thrown.errors)
    }

    private fun tier(lower: Double, upper: Double?, inc: Double): IncrementTier =
        IncrementTier(
            lowerBound = Weight(lower, Lb),
            upperBound = upper?.let { Weight(it, Lb) },
            increment = Weight(inc, Lb),
        )

    private fun stop(amount: Double): Weight = Weight(amount, Lb)
}
