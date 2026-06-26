package com.stratum.domain.response

import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.response.fixtures.ResponseFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class VolumeResponseAnalyzerTest {
    @Test
    @DisplayName("RP-S5-001 single epoch caps at Hypothesis")
    fun rpS5_001_singleEpochCapsAtHypothesis() {
        val input = ResponseFixtures.fixS5SingleEpoch()
        val epochs = EpochSplitter.split(input)
        val epoch = epochs.single()
        val deloadWeeks = EpochSplitter.markDeloadWeeks(epoch, input)
        val anchor = AnchorLiftSelector.select(MuscleGroup.Quad, epochs, input) ?: ResponseFixtures.liftSquat

        val observation =
            VolumeResponseAnalyzer.analyze(
                muscle = MuscleGroup.Quad,
                anchorLiftId = anchor,
                epoch = epoch,
                input = input,
                deloadWeeks = deloadWeeks,
            )

        assertNotNull(observation)
        assertEquals(EvidenceLevel.Hypothesis, observation!!.evidence)
        assertNotEquals(EvidenceLevel.Proven, observation.evidence)
    }

    @Test
    @DisplayName("RP-S5-002 dual epoch agreement")
    fun rpS5_002_dualEpochAgreement() {
        val input = ResponseFixtures.fixS5DualEpoch()
        val epochs = EpochSplitter.split(input)
        assertEquals(2, epochs.size)
        val deloadWeeksByEpoch = epochs.associateWith { EpochSplitter.markDeloadWeeks(it, input) }
        val anchor = AnchorLiftSelector.select(MuscleGroup.Quad, epochs, input) ?: ResponseFixtures.liftSquat
        val perEpoch =
            epochs.mapNotNull { epoch ->
                VolumeResponseAnalyzer.analyze(
                    muscle = MuscleGroup.Quad,
                    anchorLiftId = anchor,
                    epoch = epoch,
                    input = input,
                    deloadWeeks = deloadWeeksByEpoch.getValue(epoch),
                )
            }
        val mergedEvidence = ResponseEvidenceMerger.mergeVolumeEvidence(perEpoch)

        assertEquals(EvidenceLevel.Proven, mergedEvidence)
        assertTrue(perEpoch.all { it.confidenceScore >= 0.65 })
    }

    @Test
    @DisplayName("RP-S5-003 insufficient weekly points")
    fun rpS5_003_insufficientWeeklyPoints() {
        val input = ResponseFixtures.fixS5ThreeWeeklyPoints()
        val epoch =
            ResponseFixtures.epoch(
                id = "three-points",
                start = input.analysisWindowStart,
                end = input.analysisWindowEnd,
            )
        val result =
            VolumeResponseAnalyzer.analyze(
                muscle = MuscleGroup.Quad,
                anchorLiftId = ResponseFixtures.liftSquat,
                epoch = epoch,
                input = input,
                deloadWeeks = emptySet(),
            )

        assertNull(result)
    }

    @Test
    @DisplayName("RP-S5-004 short epoch rejected")
    fun rpS5_004_shortEpochRejected() {
        val input = ResponseFixtures.fixS5SevenWeekEpoch()
        val epoch =
            ResponseFixtures.epoch(
                id = "seven-weeks",
                start = input.analysisWindowStart,
                end = input.analysisWindowEnd,
            )
        val result =
            VolumeResponseAnalyzer.analyze(
                muscle = MuscleGroup.Quad,
                anchorLiftId = ResponseFixtures.liftSquat,
                epoch = epoch,
                input = input,
                deloadWeeks = emptySet(),
            )

        assertNull(result)
    }

    @Test
    @DisplayName("RP-S5-005 deload excluded from fit")
    fun rpS5_005_deloadExcludedFromFit() {
        val input = ResponseFixtures.fixS5SingleEpochWithDeload()
        val epoch =
            ResponseFixtures.epoch(
                id = "deload-fit",
                start = input.analysisWindowStart,
                end = input.analysisWindowEnd,
            )
        val deloadWeeks = EpochSplitter.markDeloadWeeks(epoch, input)
        val anchor = ResponseFixtures.liftSquat

        val observation =
            VolumeResponseAnalyzer.analyze(
                muscle = MuscleGroup.Quad,
                anchorLiftId = anchor,
                epoch = epoch,
                input = input,
                deloadWeeks = deloadWeeks,
            )

        assertNotNull(observation)
        assertEquals(5, observation!!.weeklyPointsUsed)
        assertEquals(10, observation.epochWeeksUsed)
    }

    @Test
    @DisplayName("RP-S5-M001 hardcoded Proven on single epoch fails")
    fun rpS5_M001_singleEpochNeverProven() {
        val input = ResponseFixtures.fixS5SingleEpoch()
        val epochs = EpochSplitter.split(input)
        val epoch = epochs.single()
        val observation =
            VolumeResponseAnalyzer.analyze(
                muscle = MuscleGroup.Quad,
                anchorLiftId = ResponseFixtures.liftSquat,
                epoch = epoch,
                input = input,
                deloadWeeks = emptySet(),
            )

        assertNotNull(observation)
        assertEquals(EvidenceLevel.Hypothesis, observation!!.evidence)
        assertNotEquals(EvidenceLevel.Proven, observation.evidence)
        assertEquals(5, observation.weeklyPointsUsed)
    }

}
