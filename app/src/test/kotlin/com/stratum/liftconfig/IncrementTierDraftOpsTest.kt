package com.stratum.liftconfig

import com.stratum.domain.lift.Weight
import org.junit.jupiter.api.Assertions.assertEquals
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
}
