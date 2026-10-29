package com.stratum.domain.response

import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.core.testing.response.ResponseFixtures
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import com.stratum.domain.lift.Lift
import com.stratum.domain.training.LoggedLiftContext
import com.stratum.domain.training.identity.TrainingSessionId

class StimulusSeriesBuilderTest {
    @Test
    @DisplayName("RP-S2-003 hard sets exclude warmups")
    fun rpS2_003_hardSetsExcludeWarmups() {
        val input = ResponseFixtures.fixS1Working()
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
        assertEquals(1.0, hardSets, 0.001)
        assertNotEquals(2.0, hardSets, 0.001)
    }

    @Test
    @DisplayName("RP-S2-005 secondary muscles credit at half a set per working set")
    fun rpS2_005_secondaryMusclesCreditAtHalfSet() {
        val input =
            ResponseFixtures.fixS1Working().copy(
                lifts =
                    listOf(
                        ResponseFixtures.benchPressLift().copy(
                            secondaryMuscles = setOf(MuscleGroup.Tricep),
                        ),
                    ),
            )
        val cleanSets = TrainingSignalCleaner.clean(input)
        val epoch =
            ResponseFixtures.epoch(
                id = "e1",
                start = input.analysisWindowStart,
                end = input.analysisWindowEnd,
            )
        val byMuscle = StimulusSeriesBuilder.weeklyHardSetsByMuscle(epoch, cleanSets, input.lifts)
        val chest = byMuscle[MuscleGroup.Chest]?.single()?.hardSets
        val triceps = byMuscle[MuscleGroup.Tricep]?.single()?.hardSets

        assertEquals(1.0, requireNotNull(chest), 0.001)
        assertEquals(MuscleVolumeCredit.SECONDARY_SET_CREDIT, requireNotNull(triceps), 0.001)
    }

    @Test
    @DisplayName("RP-S2-006 primary muscle is not double-counted when also listed as secondary")
    fun rpS2_006_primaryNotDoubleCountedAsSecondary() {
        val input =
            ResponseFixtures.fixS1Working().copy(
                lifts =
                    listOf(
                        ResponseFixtures.benchPressLift().copy(
                            secondaryMuscles = setOf(MuscleGroup.Chest),
                        ),
                    ),
            )
        val cleanSets = TrainingSignalCleaner.clean(input)
        val epoch =
            ResponseFixtures.epoch(
                id = "e1",
                start = input.analysisWindowStart,
                end = input.analysisWindowEnd,
            )
        val chest =
            StimulusSeriesBuilder
                .weeklyHardSetsByMuscle(epoch, cleanSets, input.lifts)[MuscleGroup.Chest]
                ?.single()
                ?.hardSets

        assertEquals(1.0, requireNotNull(chest), 0.001)
    }

    @Test
    @DisplayName("WL-041 StimulusSeriesBuilder credits volume from LoggedLiftContext without Lift join")
    fun wl041_stimulusFromSnapshot() {
        val epoch = ResponseFixtures.epoch(
            id = "e1",
            start = Instant.parse("2025-01-01T00:00:00Z"),
            end = Instant.parse("2025-01-31T00:00:00Z"),
        )
        val sets = listOf(
            com.stratum.core.testing.training.WorkoutLoggingFixtures.loggedSet(
                id = "set-1",
                sessionId = com.stratum.domain.training.identity.TrainingSessionId("ts-1"),
            ),
        )

        val credits = StimulusSeriesBuilder.weeklyHardSetsFromSnapshot(epoch, sets)

        assertEquals(1.0, credits[MuscleGroup.Chest] ?: 0.0, 0.001)
    }
}
