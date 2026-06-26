package com.stratum.domain.response

import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.response.fixtures.ResponseFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class StimulusSeriesBuilderTest {
    @Test
    @DisplayName("RP-S2-003 hard sets exclude warmups")
    fun rpS2_003_hardSetsExcludeWarmups() {
        val input = ResponseFixtures.fixS1WarmupRir()
        val cleanSets = TrainingSignalCleaner.clean(input)
        val epoch =
            ResponseFixtures.epoch(
                id = "e1",
                start = input.analysisWindowStart,
                end = input.analysisWindowEnd,
            )
        val series =
            StimulusSeriesBuilder.weeklyHardSetsByMuscle(epoch, cleanSets, input.lifts)[MuscleGroup.Chest]
                ?: emptyList()

        val hardSets = series.sumOf { it.hardSets }
        assertEquals(1, hardSets)
        assertNotEquals(2, hardSets)
    }
}
