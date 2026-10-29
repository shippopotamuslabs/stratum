package com.stratum.domain.response.modeling

import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.response.EpochSplitReason
import com.stratum.core.testing.response.ResponseFixtures
import com.stratum.domain.response.identity.EpochId
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import com.stratum.domain.response.Epoch
import com.stratum.domain.response.TrainingSignalCleaner

class LaggedDoseResponsePairerTest {
    @Test
    @DisplayName("LAG-001 pairs dose and forward response for rich chest history")
    fun pairsDoseAndResponse() {
        val input = ResponseFixtures.fixS5SingleEpoch()
        val epoch =
            com.stratum.domain.response.Epoch(
                id = EpochId("lag"),
                start = input.analysisWindowStart,
                end = input.analysisWindowEnd,
                anchorLiftIds = emptySet(),
                splitReason = EpochSplitReason.WindowEnd,
            )
        val cleanSets = com.stratum.domain.response.TrainingSignalCleaner.clean(input)
        val stimulus = MuscleWeeklyStimulusBuilder.build(epoch, cleanSets, input.lifts)
        val liftPerformance = LiftPerformancePanelBuilder.build(epoch, cleanSets)
        val indices = MuscleOutcomeIndexBuilder.build(MuscleGroup.Quad, liftPerformance, input.lifts)
        val pairing = LaggedDoseResponsePairer.pair(MuscleGroup.Quad, stimulus.filter { it.muscle == MuscleGroup.Quad }, indices)

        assertTrue(pairing.points.isNotEmpty())
        assertTrue(pairing.selectedLagWeeks in ResponseModelingConstants.CANDIDATE_LAG_WEEKS)
    }
}
