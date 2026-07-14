package com.stratum.domain.onboarding

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class OnboardingPathResolverTest {
    @Test
    @DisplayName("common prefix steps resolve to null path")
    fun commonPrefix_null() {
        assertNull(
            OnboardingPathResolver.resolve(
                step = OnboardingStep.ChoosePath,
                selectedPath = null,
                profile = UserTrainingProfile.Empty,
            ),
        )
    }

    @Test
    @DisplayName("Schedule / Specialization / MinimumVolume always Generate")
    fun generateOnlySteps() {
        for (step in
            listOf(
                OnboardingStep.Schedule,
                OnboardingStep.Specialization,
                OnboardingStep.MinimumVolume,
            )
        ) {
            assertEquals(
                OnboardingPath.Generate,
                OnboardingPathResolver.resolve(
                    step = step,
                    selectedPath = null,
                    profile = UserTrainingProfile.Empty,
                ),
            )
        }
    }

    @Test
    @DisplayName("Review with empty weekdays and no path is Import (not Generate)")
    fun review_emptyWeekdays_import() {
        assertEquals(
            OnboardingPath.Import,
            OnboardingPathResolver.resolve(
                step = OnboardingStep.Review,
                selectedPath = null,
                profile = UserTrainingProfile(weekdays = emptySet(), sessionLengthMinutes = 60),
            ),
        )
    }

    @Test
    @DisplayName("Review uses durable selectedPath=Generate even when weekdays empty")
    fun review_selectedPathGenerate() {
        assertEquals(
            OnboardingPath.Generate,
            OnboardingPathResolver.resolve(
                step = OnboardingStep.Review,
                selectedPath = OnboardingPath.Generate,
                profile = UserTrainingProfile(weekdays = emptySet()),
            ),
        )
    }

    @Test
    @DisplayName("Review uses durable selectedPath=Import even if profile has generation cycle-space")
    fun review_selectedPathImportWins() {
        assertEquals(
            OnboardingPath.Import,
            OnboardingPathResolver.resolve(
                step = OnboardingStep.Review,
                selectedPath = OnboardingPath.Import,
                profile =
                    UserTrainingProfile(
                        generationTrainingDayCount = 4,
                        generationCycleLength = 7,
                        generationRestSlotIndices = setOf(3, 6),
                    ),
            ),
        )
    }

    @Test
    @DisplayName("Review falls back to persisted generate cycle-space when selectedPath null")
    fun review_persistedCycleSpace_generate() {
        assertEquals(
            OnboardingPath.Generate,
            OnboardingPathResolver.resolve(
                step = OnboardingStep.Review,
                selectedPath = null,
                profile =
                    UserTrainingProfile(
                        weekdays = emptySet(),
                        generationTrainingDayCount = 5,
                        generationCycleLength = 7,
                        generationRestSlotIndices = setOf(2, 5),
                    ),
            ),
        )
    }

    @Test
    @DisplayName("default train count alone is not Generate identity")
    fun defaultTrainCount_notIdentity() {
        assertFalse(
            OnboardingPathResolver.hasGenerateScheduleIdentity(
                profile = UserTrainingProfile.Empty,
                trainingDayCount = 4,
                restSlotIndices = null,
                cycleLength = null,
            ),
        )
        assertTrue(
            OnboardingPathResolver.hasGenerateScheduleIdentity(
                profile = UserTrainingProfile.Empty,
                trainingDayCount = 4,
                restSlotIndices = setOf(3, 6),
                cycleLength = 7,
            ),
        )
    }
}
