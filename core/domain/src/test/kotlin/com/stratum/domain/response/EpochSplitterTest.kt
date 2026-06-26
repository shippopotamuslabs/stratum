package com.stratum.domain.response

import com.stratum.domain.response.fixtures.ResponseFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.temporal.ChronoUnit

class EpochSplitterTest {
    @Test
    @DisplayName("RP-S3-001 15 day gap yields 2 epochs")
    fun rpS3_001_gap15DaysYieldsTwoEpochs() {
        val input = ResponseFixtures.fixS3Gap15d()
        val epochs = EpochSplitter.split(input)

        assertEquals(2, epochs.size)
        assertEquals(EpochSplitReason.SessionGap, epochs[1].splitReason)
        assertTrue(epochs[0].end.isBefore(epochs[1].start))
    }

    @Test
    @DisplayName("RP-S3-002 Jaccard above 0.5 splits")
    fun rpS3_002_jaccardAboveHalfSplits() {
        val input = ResponseFixtures.fixS3Jaccard()
        val epochs = EpochSplitter.split(input)

        assertEquals(2, epochs.size)
        assertTrue(epochs.any { it.splitReason == EpochSplitReason.PrimaryLiftChange })
    }

    @Test
    @DisplayName("RP-S3-003 persistent lift churn splits epoch")
    fun rpS3_003_liftChurnSplits() {
        val input = ResponseFixtures.fixS3LiftChurn()
        val epochs = EpochSplitter.split(input)

        assertTrue(epochs.size >= 2)
        assertTrue(epochs.any { it.splitReason == EpochSplitReason.LiftChurn })
    }

    @Test
    @DisplayName("RP-S3-004 equipment shift boundary")
    fun rpS3_004_equipmentShiftBoundary() {
        val weekStart = ResponseFixtures.weekStart(2025, 1, 6)
        val sessionKg =
            ResponseFixtures.input(
                sessions =
                    listOf(
                        com.stratum.domain.importing.HistoricalSession(
                            id = com.stratum.domain.importing.identity.HistoricalSessionId("eq-kg"),
                            startedAt = weekStart,
                            endedAt = null,
                            sourceApp = "test",
                        ),
                    ),
                sets = emptyList(),
                lifts = listOf(ResponseFixtures.benchPressLift()),
            ).sessions.single()
        val sessionLb =
            com.stratum.domain.importing.HistoricalSession(
                id = com.stratum.domain.importing.identity.HistoricalSessionId("eq-lb"),
                startedAt = weekStart.plus(7, ChronoUnit.DAYS),
                endedAt = null,
                sourceApp = "test",
            )
        val sets =
            listOf(
                ResponseFixtures.fixS1Working().sets.single().copy(
                    id = com.stratum.domain.importing.identity.LoggedSetId("eq-1"),
                    sessionId = sessionKg.id,
                    weight = com.stratum.domain.lift.Weight(100.0, com.stratum.domain.lift.Weight.WeightUnit.Kg),
                    exerciseNameRaw = "Bench Press",
                    completedAt = sessionKg.startedAt,
                ),
                ResponseFixtures.fixS1Working().sets.single().copy(
                    id = com.stratum.domain.importing.identity.LoggedSetId("eq-2"),
                    sessionId = sessionLb.id,
                    weight = com.stratum.domain.lift.Weight(118.0, com.stratum.domain.lift.Weight.WeightUnit.Lb),
                    exerciseNameRaw = "Bench Press",
                    completedAt = sessionLb.startedAt,
                ),
            )
        val input = ResponseFixtures.input(listOf(sessionKg, sessionLb), sets, listOf(ResponseFixtures.benchPressLift()))
        val epochs = EpochSplitter.split(input)

        assertTrue(epochs.any { it.splitReason == EpochSplitReason.EquipmentShift })
    }

    @Test
    @DisplayName("RP-S3-006 volume deload week excluded from fit")
    fun rpS3_006_volumeDeloadMarkedNotSplit() {
        val input = ResponseFixtures.fixS3DeloadVol()
        val epochs = EpochSplitter.split(input)
        val epoch = epochs.single()
        val deloadWeeks = EpochSplitter.markDeloadWeeks(epoch, input)

        assertEquals(1, deloadWeeks.size)
        assertEquals(1, epochs.size)
    }

    @Test
    @DisplayName("RP-S3-007 weight deload with steady volume")
    fun rpS3_007_intensityDeloadMarkedNotSplit() {
        val input = ResponseFixtures.fixS3DeloadWgt()
        val epochs = EpochSplitter.split(input)
        val epoch = epochs.single()
        val deloadWeeks = EpochSplitter.markDeloadWeeks(epoch, input)

        assertEquals(1, deloadWeeks.size)
        assertEquals(1, epochs.size)
    }

    @Test
    @DisplayName("RP-S3-M001 gap exactly 14 days does not split")
    fun rpS3_M001_gap14DaysDoesNotSplit() {
        val input = ResponseFixtures.fixS3Gap14d()
        val epochs = EpochSplitter.split(input)

        assertEquals(1, epochs.size)
        assertEquals(EpochSplitReason.WindowEnd, epochs[0].splitReason)
    }
}
