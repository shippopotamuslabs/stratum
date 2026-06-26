package com.stratum.domain.response

import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.response.fixtures.ResponseFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class LiftEffectivenessAnalyzerTest {
    @Test
    @DisplayName("RP-S6-001 fewer than 6 sessions excluded")
    fun rpS6_001_fewerThanSixSessionsExcluded() {
        val input = ResponseFixtures.fixS6Pattern3()
        val epochs =
            listOf(
                ResponseFixtures.epoch(
                    id = "s6-gate",
                    start = input.analysisWindowStart,
                    end = input.analysisWindowEnd,
                ),
            )
        val observations =
            LiftEffectivenessAnalyzer.analyze(
                pattern = MovementPattern.HorizontalPush,
                epoch = epochs.single(),
                input = input,
                deloadWeeks = emptySet(),
            )
        val inclineObs = observations.filter { it.liftId == ResponseFixtures.liftIncline }

        assertEquals(0, inclineObs.size)
    }

    @Test
    @DisplayName("RP-S6-002 single peer no rank")
    fun rpS6_002_singlePeerNoRank() {
        val input = ResponseFixtures.fixS6SingleLift()
        val epochs =
            listOf(
                ResponseFixtures.epoch(
                    id = "s6-single",
                    start = input.analysisWindowStart,
                    end = input.analysisWindowEnd,
                ),
            )
        val observations =
            LiftEffectivenessAnalyzer.analyze(
                pattern = MovementPattern.HorizontalPush,
                epoch = epochs.single(),
                input = input,
                deloadWeeks = emptySet(),
            )

        assertEquals(1, observations.size)
        assertEquals(1, observations[0].rank)
        assertEquals(1, observations[0].peerLiftCount)
        assertEquals(EvidenceLevel.Hypothesis, observations[0].evidence)
    }

    @Test
    @DisplayName("RP-S6-003 rank margin enforced")
    fun rpS6_003_rankMarginEnforced() {
        val input = ResponseFixtures.fixS6Pattern3()
        val epochs =
            listOf(
                ResponseFixtures.epoch(
                    id = "s6-margin",
                    start = input.analysisWindowStart,
                    end = input.analysisWindowEnd,
                ),
            )
        val observations =
            LiftEffectivenessAnalyzer.analyze(
                pattern = MovementPattern.HorizontalPush,
                epoch = epochs.single(),
                input = input,
                deloadWeeks = emptySet(),
            )
        val rank1 = observations.first { it.rank == 1 }

        assertEquals(0.12, rank1.effectivenessScore, 0.001)
        assertEquals(EvidenceLevel.Hypothesis, rank1.evidence)
        assertTrue(observations.none { it.rank == 1 && it.evidence == EvidenceLevel.Proven })
    }

    @Test
    @DisplayName("RP-S6-004 cross-epoch rank stability")
    fun rpS6_004_crossEpochRankStability() {
        val input = ResponseFixtures.fixS5DualEpoch()
        val epochs = EpochSplitter.split(input)
        val perEpoch =
            epochs.flatMap { epoch ->
                LiftEffectivenessAnalyzer.analyze(
                    pattern = MovementPattern.HorizontalPush,
                    epoch = epoch,
                    input = input,
                    deloadWeeks = emptySet(),
                )
            }
        val benchObs = perEpoch.filter { it.liftId == ResponseFixtures.liftBench }
        val mergedEvidence = ResponseEvidenceMerger.mergeLiftEffectivenessEvidence(benchObs)

        assertEquals(EvidenceLevel.Proven, mergedEvidence)
        assertEquals(1, benchObs.last().rank)
    }

    @Test
    @DisplayName("RP-S6-005 patterns ranked independently")
    fun rpS6_005_patternsRankedIndependently() {
        val input = ResponseFixtures.fixS6WithVerticalPull()
        val epoch =
            ResponseFixtures.epoch(
                id = "s6-cross",
                start = input.analysisWindowStart,
                end = input.analysisWindowEnd,
            )
        val horizontal =
            LiftEffectivenessAnalyzer.analyze(
                pattern = MovementPattern.HorizontalPush,
                epoch = epoch,
                input = input,
                deloadWeeks = emptySet(),
            )
        val vertical =
            LiftEffectivenessAnalyzer.analyze(
                pattern = MovementPattern.VerticalPull,
                epoch = epoch,
                input = input,
                deloadWeeks = emptySet(),
            )
        val horizontalRank1 = horizontal.first { it.rank == 1 }
        val verticalRank1 = vertical.first { it.rank == 1 }

        assertNotEquals(horizontalRank1.liftId, verticalRank1.liftId)
        assertEquals(MovementPattern.HorizontalPush, horizontalRank1.pattern)
        assertEquals(MovementPattern.VerticalPull, verticalRank1.pattern)
    }
}
