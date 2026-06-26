package com.stratum.domain.response.modeling

import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.response.fixtures.ResponseFixtures
import java.time.temporal.ChronoUnit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class MuscleOutcomeIndexBuilderTest {
    @Test
    @DisplayName("MOI-001 composite index rises when fly progresses faster than bench")
    fun compositeIndexTracksContributingLifts() {
        val input = ResponseFixtures.fixS4RichChest()
        val epoch =
            ResponseFixtures.epoch(
                id = "moi",
                start = input.analysisWindowStart,
                end = input.analysisWindowEnd,
            )
        val cleanSets = com.stratum.domain.response.TrainingSignalCleaner.clean(input)
        val liftPerformance = LiftPerformancePanelBuilder.build(epoch, cleanSets)
        val indices = MuscleOutcomeIndexBuilder.build(MuscleGroup.Chest, liftPerformance, input.lifts)

        assertTrue(indices.size >= 6)
        assertTrue(indices.all { it.contributingLiftCount >= 1 })
        assertTrue(indices.any { it.effectiveLiftCount >= 2 })
    }

    @Test
    @DisplayName("MOI-002 credits only lifts that contribute to the muscle")
    fun creditsFilterToMuscle() {
        val input = ResponseFixtures.fixS4RichChest()
        val credits = MuscleOutcomeIndexBuilder.creditsForMuscle(MuscleGroup.Chest, input.lifts)
        assertTrue(credits.any { it.liftId == ResponseFixtures.liftBench })
        assertTrue(credits.any { it.liftId == ResponseFixtures.liftFly })
    }
}
