package com.stratum.domain.lift

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class EquipmentIncrementInferenceTest {

    private val lb = Weight.WeightUnit.Lb
    private val kg = Weight.WeightUnit.Kg
    private val validate: (List<IncrementTier>) -> IncrementTiersValidationResult =
        { ValidateIncrementTiersUseCase()(it) }

    private fun openBand(step: Double, unit: Weight.WeightUnit = lb): List<IncrementTier> =
        listOf(
            IncrementTier(
                lowerBound = Weight(0.0, unit),
                upperBound = null,
                increment = Weight(step, unit),
            ),
        )

    private fun lb(amount: Double) = Weight(amount, lb)

    private fun kg(amount: Double) = Weight(amount, kg)

    @Nested
    @DisplayName("inferIncrementStep")
    inner class InferStep {
        @Test
        @DisplayName("Given 62.5 and 67.5 when inferred then step is 2.5")
        fun residualPairIsTwoPointFive() {
            val step =
                inferIncrementStep(
                    residualAmounts = listOf(62.5, 67.5),
                    allAmounts = listOf(62.5, 67.5),
                )
            assertEquals(2.5, step)
        }

        @Test
        @DisplayName("Given pairwise 5 lb jumps on multiples of 5 when inferred then step is 5")
        fun fivePoundJumps() {
            val step =
                inferIncrementStep(
                    residualAmounts = listOf(55.0, 60.0, 65.0),
                    allAmounts = listOf(55.0, 60.0, 65.0),
                )
            assertEquals(5.0, step)
        }

        @Test
        @DisplayName("Given single residual 62.5 when inferred then largest allowlist divisor wins")
        fun singleResidualLargestAllowlist() {
            // 12.5 divides 62.5; prefer largest covering residual
            val step = inferIncrementStep(residualAmounts = listOf(62.5))
            assertEquals(12.5, step)
        }

        @Test
        @DisplayName("Given empty residuals when inferred then null")
        fun emptyNull() {
            assertNull(inferIncrementStep(residualAmounts = emptyList()))
        }

        @Test
        @DisplayName("Given odd amount 33.33 when inferred then best-effort allowlist step")
        fun oddAmountBestEffort() {
            // 33.33 is not within ε of any allowlist lattice; fallback still picks a step.
            val step = inferIncrementStep(residualAmounts = listOf(33.33))
            assertNotNull(step)
            assertTrue(step!! in INCREMENT_STEP_ALLOWLIST_LB)
        }
    }

    @Nested
    @DisplayName("inferIncrementLattice")
    inner class InferLatticeOdd {
        @Test
        @DisplayName("Given 33.33 on 5-lb open band when inferred then non-null lattice")
        fun oddWeightOnFiveLbBand() {
            val result =
                inferIncrementLattice(
                    sampleWeights = listOf(lb(33.33)),
                    currentTiers = openBand(5.0),
                )
            assertNotNull(result)
            assertTrue(result!!.tiers.isNotEmpty())
        }
    }

    @Nested
    @DisplayName("buildProposedIncrementTiers")
    inner class BuildTiers {
        @Test
        @DisplayName("Given finer step than open band when built then multi-band fine/coarse")
        fun multiBandWhenUseful() {
            val current = openBand(5.0)
            val tiers =
                buildProposedIncrementTiers(
                    unit = lb,
                    step = 2.5,
                    currentTiers = current,
                    observedAmounts = listOf(62.5, 67.5),
                    validate = validate,
                )
            assertEquals(2, tiers.size)
            assertEquals(2.5, tiers[0].increment.amount)
            assertEquals(5.0, tiers[1].increment.amount)
            assertEquals(0.0, tiers[0].lowerBound.amount)
            assertNotNull(tiers[0].upperBound)
            assertNull(tiers[1].upperBound)
            // Exclusive upper strictly above max observed
            assertTrue(tiers[0].upperBound!!.amount > 67.5)
            assertEquals(IncrementTiersValidationResult.Valid, validate(tiers))
        }

        @Test
        @DisplayName("Given same step as current when built then single open band")
        fun singleBandWhenNoCoarser() {
            val tiers =
                buildProposedIncrementTiers(
                    unit = lb,
                    step = 5.0,
                    currentTiers = openBand(5.0),
                    observedAmounts = listOf(60.0, 65.0),
                    validate = validate,
                )
            assertEquals(1, tiers.size)
            assertNull(tiers[0].upperBound)
            assertEquals(5.0, tiers[0].increment.amount)
            assertEquals(IncrementTiersValidationResult.Valid, validate(tiers))
        }

        @Test
        @DisplayName("Given empty current tiers when built then single open band at step")
        fun emptyCurrentSingleBand() {
            val tiers =
                buildProposedIncrementTiers(
                    unit = lb,
                    step = 2.5,
                    currentTiers = emptyList(),
                    observedAmounts = listOf(62.5),
                    validate = validate,
                )
            assertEquals(1, tiers.size)
            assertEquals(2.5, tiers[0].increment.amount)
            assertEquals(IncrementTiersValidationResult.Valid, validate(tiers))
        }

        @Test
        @DisplayName("Given kg unit when built then tiers use kg")
        fun kgUnitPreserved() {
            val tiers =
                buildProposedIncrementTiers(
                    unit = kg,
                    step = 2.5,
                    currentTiers = openBand(5.0, kg),
                    observedAmounts = listOf(22.5, 25.0),
                    validate = validate,
                )
            assertTrue(tiers.all { it.increment.unit == kg })
            assertEquals(IncrementTiersValidationResult.Valid, validate(tiers))
        }

        @Test
        @DisplayName("Given multi-band candidate when validate fails then falls back to single open band")
        fun invalidMultiBandFallsBack() {
            val alwaysInvalid: (List<IncrementTier>) -> IncrementTiersValidationResult = {
                IncrementTiersValidationResult.Invalid(
                    setOf(IncrementTiersValidationError.NotContiguous),
                )
            }
            val tiers =
                buildProposedIncrementTiers(
                    unit = lb,
                    step = 2.5,
                    currentTiers = openBand(5.0),
                    observedAmounts = listOf(62.5, 67.5),
                    validate = alwaysInvalid,
                )
            assertEquals(1, tiers.size)
            assertNull(tiers[0].upperBound)
            assertEquals(2.5, tiers[0].increment.amount)
            assertEquals(IncrementTiersValidationResult.Valid, validate(tiers))
        }
    }

    @Nested
    @DisplayName("inferIncrementLattice")
    inner class InferLattice {
        @Test
        @DisplayName("Given 62.5/67.5 on 5-step seed when inferred then 2.5 step multi-band")
        fun cableStackCase() {
            val result =
                inferIncrementLattice(
                    sampleWeights = listOf(lb(62.5), lb(67.5)),
                    currentTiers = openBand(5.0),
                )
            assertNotNull(result)
            assertEquals(2.5, result!!.step)
            assertEquals(2, result.tiers.size)
            assertTrue(result.tiers.isOnLattice(lb(62.5)))
            assertTrue(result.tiers.isOnLattice(lb(67.5)))
            assertEquals(IncrementTiersValidationResult.Valid, validate(result.tiers))
        }

        @Test
        @DisplayName("Given mix of on- and off-lattice when inferred then residual drives step")
        fun mixedOnAndOff() {
            val result =
                inferIncrementLattice(
                    sampleWeights = listOf(lb(60.0), lb(62.5), lb(67.5)),
                    currentTiers = openBand(5.0),
                )
            assertNotNull(result)
            assertEquals(2.5, result!!.step)
            assertTrue(result.tiers.isOnLattice(lb(60.0)))
            assertTrue(result.tiers.isOnLattice(lb(62.5)))
        }

        @Test
        @DisplayName("Given all on-lattice when inferred then null")
        fun allOnLatticeNull() {
            assertNull(
                inferIncrementLattice(
                    sampleWeights = listOf(lb(60.0), lb(65.0)),
                    currentTiers = openBand(5.0),
                ),
            )
        }

        @Test
        @DisplayName("Given bodyweight load type when inferred then null")
        fun bodyweightNull() {
            assertNull(
                inferIncrementLattice(
                    sampleWeights = listOf(lb(0.0)),
                    currentTiers = openBand(5.0),
                    loadType = LoadType.Bodyweight,
                ),
            )
        }

        @Test
        @DisplayName("Given only zero weights when inferred then null")
        fun zeroWeightsNull() {
            assertNull(
                inferIncrementLattice(
                    sampleWeights = listOf(lb(0.0)),
                    currentTiers = openBand(5.0),
                ),
            )
        }

        @Test
        @DisplayName("Given empty current tiers and positive samples when inferred then builds lattice")
        fun emptyCurrentBuilds() {
            val result =
                inferIncrementLattice(
                    sampleWeights = listOf(lb(10.0), lb(20.0), lb(30.0)),
                    currentTiers = emptyList(),
                )
            assertNotNull(result)
            assertEquals(10.0, result!!.step)
            assertEquals(IncrementTiersValidationResult.Valid, validate(result.tiers))
        }

        @Test
        @DisplayName("Given mixed units among samples when inferred then null")
        fun mixedSampleUnitsNull() {
            assertNull(
                inferIncrementLattice(
                    sampleWeights = listOf(lb(62.5), kg(30.0)),
                    currentTiers = openBand(5.0),
                ),
            )
        }

        @Test
        @DisplayName("Given sample unit differing from currentTiers unit when inferred then null")
        fun sampleUnitDiffersFromTiersNull() {
            assertNull(
                inferIncrementLattice(
                    sampleWeights = listOf(lb(62.5), lb(67.5)),
                    currentTiers = openBand(5.0, kg),
                ),
            )
        }

        @Test
        @DisplayName("Given assisted negative off-lattice weights when inferred then builds lattice")
        fun assistedNegativeWeights() {
            val result =
                inferIncrementLattice(
                    sampleWeights = listOf(lb(-47.5), lb(-42.5)),
                    currentTiers = openBand(5.0),
                    loadType = LoadType.Assisted,
                )
            assertNotNull(result)
            assertEquals(2.5, result!!.step)
            assertTrue(result.tiers.isOnLattice(lb(-47.5), LoadType.Assisted))
            assertTrue(result.tiers.isOnLattice(lb(-42.5), LoadType.Assisted))
            assertEquals(IncrementTiersValidationResult.Valid, validate(result.tiers))
        }

        @Test
        @DisplayName("Given empty sampleWeights when inferred then null")
        fun emptySamplesNull() {
            assertNull(
                inferIncrementLattice(
                    sampleWeights = emptyList(),
                    currentTiers = openBand(5.0),
                ),
            )
        }
    }

    @Nested
    @DisplayName("inferIncrementLattice with micro")
    inner class InferLatticeAdder {
        private val pinTiers = openBand(15.0)
        private val pinMicro = listOf(lb(5.0), lb(10.0))

        @Test
        @DisplayName("Given adder-exact samples when inferred then null")
        fun adderExactSamplesNull() {
            assertNull(
                inferIncrementLattice(
                    sampleWeights = listOf(lb(65.0), lb(65.0), lb(70.0)),
                    currentTiers = pinTiers,
                    micro = pinMicro,
                ),
            )
        }

        @Test
        @DisplayName("Given adder-exact plus true residuals when inferred then residual step only")
        fun mixedSamplesInferResidualStep() {
            val result =
                inferIncrementLattice(
                    sampleWeights = listOf(lb(65.0), lb(62.5), lb(62.5)),
                    currentTiers = pinTiers,
                    micro = pinMicro,
                )
            assertNotNull(result)
            assertEquals(2.5, result!!.step)
        }

        @Test
        @DisplayName("Given true residuals with micro when inferred then same as without micro")
        fun trueResidualsUndistorted() {
            val samples = listOf(lb(62.5), lb(67.5))
            val withMicro =
                inferIncrementLattice(
                    sampleWeights = samples,
                    currentTiers = pinTiers,
                    micro = pinMicro,
                )
            val withoutMicro =
                inferIncrementLattice(
                    sampleWeights = samples,
                    currentTiers = pinTiers,
                )
            assertNotNull(withMicro)
            assertNotNull(withoutMicro)
            assertEquals(withoutMicro!!.step, withMicro!!.step)
            assertEquals(2.5, withMicro.step)
            assertEquals(IncrementTiersValidationResult.Valid, validate(withMicro.tiers))
        }
    }

    @Nested
    @DisplayName("tidyUpperBound / isNearMultiple / GCD helpers")
    inner class Helpers {
        @Test
        @DisplayName("Given 67.5 when tidy upper then 70")
        fun tidySixtySevenFive() {
            assertEquals(70.0, tidyUpperBound(67.5))
        }

        @Test
        @DisplayName("Given exact 70 when tidy upper then 75 exclusive")
        fun tidyExactSeventy() {
            assertEquals(75.0, tidyUpperBound(70.0))
        }

        @Test
        @DisplayName("Given amounts near 2.5 grid when isNearMultiple then true")
        fun nearMultiple() {
            assertTrue(isNearMultiple(62.5, 2.5))
            assertTrue(isNearMultiple(62.51, 2.5))
            assertTrue(!isNearMultiple(63.0, 2.5))
        }

        @Test
        @DisplayName("Given 62.5 and 67.5 when pairwise GCD then 5")
        fun pairwiseGcdFive() {
            // Diff only is 5; residual coverage is enforced by inferIncrementStep separately
            assertEquals(5.0, approximatePairwiseGcd(listOf(62.5, 67.5))!!, 1e-9)
        }

        @Test
        @DisplayName("Given 60/62.5/67.5 when pairwise GCD then 2.5")
        fun pairwiseGcdTwoPointFive() {
            assertEquals(2.5, approximatePairwiseGcd(listOf(60.0, 62.5, 67.5))!!, 1e-9)
        }
    }
}
