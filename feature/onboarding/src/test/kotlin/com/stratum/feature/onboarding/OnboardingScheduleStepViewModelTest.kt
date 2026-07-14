package com.stratum.feature.onboarding

import app.cash.turbine.test
import com.stratum.core.testing.MainDispatcherRule
import com.stratum.core.ui.trainingsetup.ScheduleDraftUi
import com.stratum.domain.onboarding.OnboardingProgress
import com.stratum.domain.onboarding.OnboardingStep
import com.stratum.domain.onboarding.Weekday
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MainDispatcherRule::class)
class OnboardingScheduleStepViewModelTest {

    private lateinit var subject: OnboardingViewModel

    @BeforeEach
    fun seedScheduleStep() {
        OnboardingViewModelTestFixtures.resetDefaults()
        OnboardingViewModelTestFixtures.fakeRepository.seed(
            OnboardingProgress(OnboardingStep.Schedule, startedAt = 0L, completedAt = null),
        )
        subject = OnboardingViewModelTestFixtures.buildViewModel()
    }

    @Test
    @DisplayName("Given an empty schedule when on Schedule step then canAdvance is false")
    fun emptyScheduleCannotAdvance() = runTest {
        subject.uiState.test {
            assertFalse(awaitItem().canAdvance)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given a complete schedule when next() then step advances and persists")
    fun completeScheduleNextAdvancesAndPersists() = runTest {
        subject.onTrainingDayCountChanged(2)
        subject.onSessionPresetSelected("40-60")
        advanceUntilIdle()
        assertTrue(subject.uiState.value.canAdvance)

        subject.next()
        advanceUntilIdle()
        assertEquals("Specialization", subject.uiState.value.step.id)

        val saved = OnboardingViewModelTestFixtures.fakeProfileRepository.current
        // Schedule identity is cycle-space (ephemeral); profile.weekdays stay empty.
        assertEquals(emptySet<Weekday>(), saved?.weekdays)
        assertEquals(50, saved?.sessionLengthMinutes)
    }

    @Test
    @DisplayName("Given Custom selected when onSessionPresetSelected then custom mode is active")
    fun customSelectionShowsCustomMode() = runTest {
        subject.onSessionPresetSelected(ScheduleDraftUi.CUSTOM_KEY)

        subject.uiState.test {
            assertEquals(ScheduleDraftUi.CUSTOM_KEY, awaitItem().scheduleDraft.sessionLengthSelection)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given six training days when count changed then 8-slot defaults apply")
    fun sixDayDefaults() = runTest {
        subject.onTrainingDayCountChanged(6)
        advanceUntilIdle()
        val draft = subject.uiState.value.scheduleDraft
        assertEquals(6, draft.trainingDayCount)
        assertEquals(8, draft.cycleLength)
        assertEquals(setOf(3, 7), draft.restSlotIndices)
    }

    @Test
    @DisplayName("Given six training days when 7-day cycle selected then single rest")
    fun sixDaySevenToggle() = runTest {
        subject.onTrainingDayCountChanged(6)
        subject.onSixDayCycleLengthSelected(7)
        advanceUntilIdle()
        val draft = subject.uiState.value.scheduleDraft
        assertEquals(7, draft.cycleLength)
        assertEquals(1, draft.restSlotIndices.size)
    }

    @Test
    @DisplayName("Given three training days when defaults applied then Day 1/3/5 train slots")
    fun threeDayDefaults() = runTest {
        subject.onTrainingDayCountChanged(3)
        advanceUntilIdle()
        val draft = subject.uiState.value.scheduleDraft
        assertEquals(7, draft.cycleLength)
        assertEquals(setOf(0, 2, 4), draft.trainingSlotIndices)
        assertEquals(setOf(1, 3, 5, 6), draft.restSlotIndices)
    }

    @Test
    @DisplayName("Given six training days when a training slot is toggled then selection updates")
    fun trainingSlotToggle() = runTest {
        subject.onTrainingDayCountChanged(6)
        subject.onTrainingSlotToggled(0)
        advanceUntilIdle()
        val draft = subject.uiState.value.scheduleDraft
        assertEquals(2, draft.restSlotIndices.size)
        assertEquals(6, draft.trainingSlotIndices.size)
    }
}
