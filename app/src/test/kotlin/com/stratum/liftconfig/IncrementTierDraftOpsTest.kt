package com.stratum.liftconfig

import com.stratum.domain.lift.MicroIncrementsValidationError
import com.stratum.domain.lift.Weight
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Pure unit tests for the proposal sample-weight band check: warns exactly the bands
 * whose steps miss a logged weight, never blocks saving (UNIT_TESTING.md §1).
 */
@DisplayName("IncrementTierDraftOps.warnedIndexesForSamples")
class IncrementTierDraftOpsTest {

    private val lb = Weight.WeightUnit.Lb
    private val twoBand =
        listOf(
            IncrementTierDraft("0", "40", "2.5", openEnded = false),
            IncrementTierDraft("40", null, "5", openEnded = true),
        )

    @Test
    @DisplayName("Given on-lattice samples when checked then no bands warn")
    fun onLatticeSamplesWarnNothing() {
        assertTrue(
            IncrementTierDraftOps.warnedIndexesForSamples(
                drafts = twoBand,
                displayUnit = lb,
                samplesLb = listOf(60.0, 100.0, 37.5),
            ).isEmpty(),
        )
    }

    @Test
    @DisplayName("Given off-lattice sample when checked then only its band warns")
    fun offLatticeSampleWarnsItsBand() {
        assertEquals(
            setOf(1),
            IncrementTierDraftOps.warnedIndexesForSamples(
                drafts = twoBand,
                displayUnit = lb,
                samplesLb = listOf(62.5),
            ),
        )
    }

    @Test
    @DisplayName("Given band-boundary sample when checked then no warning")
    fun boundarySampleWarnsNothing() {
        assertTrue(
            IncrementTierDraftOps.warnedIndexesForSamples(
                drafts = twoBand,
                displayUnit = lb,
                samplesLb = listOf(40.0),
            ).isEmpty(),
        )
    }

    @Test
    @DisplayName("Given empty samples when checked then no bands warn")
    fun emptySamplesWarnNothing() {
        assertTrue(
            IncrementTierDraftOps.warnedIndexesForSamples(
                drafts = twoBand,
                displayUnit = lb,
                samplesLb = emptyList(),
            ).isEmpty(),
        )
    }

    @Test
    @DisplayName("Given unparseable drafts when checked then no bands warn")
    fun unparseableDraftsWarnNothing() {
        assertTrue(
            IncrementTierDraftOps.warnedIndexesForSamples(
                drafts = listOf(IncrementTierDraft("", null, "5", openEnded = true)),
                displayUnit = lb,
                samplesLb = listOf(62.5),
            ).isEmpty(),
        )
    }

    @Test
    @DisplayName("Given non-positive samples when checked then they are ignored")
    fun nonPositiveSamplesIgnored() {
        assertTrue(
            IncrementTierDraftOps.warnedIndexesForSamples(
                drafts = twoBand,
                displayUnit = lb,
                samplesLb = listOf(0.0, -5.0),
            ).isEmpty(),
        )
    }

    @Test
    @DisplayName("Given stops when drafted then display strings match the unit")
    fun microDraftsMatchUnit() {
        val stops =
            listOf(Weight(5.0, lb), Weight(10.0, lb))

        assertEquals(
            listOf("5", "10"),
            IncrementTierDraftOps.toMicroDrafts(stops, lb),
        )
    }

    @Test
    @DisplayName("Given kg display when drafted and parsed then stops round-trip")
    fun microDraftsRoundTripKg() {
        val stops = listOf(Weight(5.0, lb), Weight(10.0, lb))
        val drafts =
            IncrementTierDraftOps.toMicroDrafts(stops, Weight.WeightUnit.Kg)

        // Display strings are converted (not lb identity) and lossy to display
        // precision, like band drafts.
        assertTrue(drafts != listOf("5", "10"))
        val back =
            IncrementTierDraftOps.microToDomainOrNull(drafts, Weight.WeightUnit.Kg)!!

        assertEquals(5.0, back[0].amount, 0.1)
        assertEquals(10.0, back[1].amount, 0.1)
        assertTrue(back.all { it.unit == lb })
    }

    @Test
    @DisplayName("Given unparseable stops when parsed then null")
    fun microUnparseableIsNull() {
        assertNull(
            IncrementTierDraftOps.microToDomainOrNull(listOf("5", "abc"), lb),
        )
    }

    @Test
    @DisplayName("Given empty stops when parsed then empty")
    fun microEmptyParsesEmpty() {
        assertEquals(
            emptyList<Weight>(),
            IncrementTierDraftOps.microToDomainOrNull(emptyList(), lb),
        )
    }

    @Test
    @DisplayName("Given micro errors when messaged then each maps to guidance")
    fun microMessagesCoverErrors() {
        val message =
            IncrementTierDraftOps.toMicroUserMessage(
                setOf(
                    MicroIncrementsValidationError.MixedUnits,
                    MicroIncrementsValidationError.NegativeStop,
                    MicroIncrementsValidationError.UnsortedOrDuplicate,
                    MicroIncrementsValidationError.MeaninglessZero,
                    MicroIncrementsValidationError.ExceedsLargestIncrement,
                    MicroIncrementsValidationError.MicroWithoutTiers,
                ),
            )

        assertTrue(message.isNotBlank())
        assertTrue(message.contains("unit"))
        assertTrue(message.contains("negative", ignoreCase = true))
        assertTrue(message.contains("largest band step"))
    }

    @Test
    @DisplayName("Given adder-exact samples with micro when checked then no bands warn")
    fun adderExactSamplesWarnNothingWithMicro() {
        val pinBand =
            listOf(IncrementTierDraft("0", null, "15", openEnded = true))

        assertTrue(
            IncrementTierDraftOps.warnedIndexesForSamples(
                drafts = pinBand,
                displayUnit = lb,
                samplesLb = listOf(65.0, 70.0),
                microLb = listOf(5.0, 10.0),
            ).isEmpty(),
        )
    }

    @Test
    @DisplayName("Given true residuals with micro when checked then the band still warns")
    fun trueResidualsWarnWithMicro() {
        val pinBand =
            listOf(IncrementTierDraft("0", null, "15", openEnded = true))

        assertEquals(
            setOf(0),
            IncrementTierDraftOps.warnedIndexesForSamples(
                drafts = pinBand,
                displayUnit = lb,
                samplesLb = listOf(65.0, 62.5),
                microLb = listOf(5.0, 10.0),
            ),
        )
    }
}
