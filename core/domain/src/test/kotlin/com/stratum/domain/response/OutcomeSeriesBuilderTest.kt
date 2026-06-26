package com.stratum.domain.response

import com.stratum.domain.importing.HistoricalSession
import com.stratum.domain.importing.identity.HistoricalSessionId
import com.stratum.domain.importing.identity.LoggedSetId
import com.stratum.domain.lift.Weight
import com.stratum.domain.response.fixtures.ResponseFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.temporal.ChronoUnit

class OutcomeSeriesBuilderTest {
    @Test
    @DisplayName("RP-S2-007 anchor outcome aggregates top sets")
    fun rpS2_007_anchorOutcomeAggregatesTopSets() {
        val weekStart = ResponseFixtures.weekStart(2025, 1, 6)
        val session =
            HistoricalSession(
                id = HistoricalSessionId("anchor-session"),
                startedAt = weekStart.plus(1, ChronoUnit.DAYS),
                endedAt = null,
                sourceApp = "test",
            )
        // Brzycki e1RM targets: 115.0, 110.0, 90.0 kg (top-two mean 112.5)
        val workingWeightsKg = listOf(99.44736330025296, 95.12356489589413, 77.82837127845883)
        val sets =
            workingWeightsKg.mapIndexed { index, weightKg ->
                ResponseFixtures.fixS1Working().sets.single().copy(
                    id = LoggedSetId("outcome-$index"),
                    sessionId = session.id,
                    weight = Weight(weightKg, Weight.WeightUnit.Kg),
                    reps = 5,
                    rir = 2,
                    completedAt = session.startedAt.plusSeconds((index * 60).toLong()),
                )
            }
        val input = ResponseFixtures.input(listOf(session), sets, listOf(ResponseFixtures.benchPressLift()))
        val cleanSets = TrainingSignalCleaner.clean(input)
        val epoch =
            ResponseFixtures.epoch(
                id = "outcome-epoch",
                start = weekStart,
                end = weekStart.plus(7, ChronoUnit.DAYS),
            )
        val outcomes =
            OutcomeSeriesBuilder.weeklyAnchorOutcomes(
                epoch = epoch,
                anchorLiftId = ResponseFixtures.liftBench,
                cleanSets = cleanSets,
            )
        val week = outcomes.first { it.weekStart == weekStart }

        assertEquals(115.0, week.peakEstimatedOneRm)
        assertEquals(112.5, week.smoothedEstimatedOneRm)
    }
}
