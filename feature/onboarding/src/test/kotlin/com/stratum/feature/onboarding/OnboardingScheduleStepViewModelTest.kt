package com.stratum.feature.onboarding

import app.cash.turbine.test
import com.stratum.core.testing.MainDispatcherRule
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
    @DisplayName("Given weekday toggle when onWeekdayToggled then selection updates")
    fun weekdayToggleUpdatesSelection() = runTest {
        subject.onWeekdayToggled("Mon")

        subject.uiState.test {
            assertTrue(awaitItem().scheduleDraft.selectedWeekdays.contains("Mon"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given a weekday already selected when toggled again then it is removed")
    fun weekdayToggleRemovesWhenAlreadySelected() = runTest {
        subject.onWeekdayToggled("Mon")
        subject.onWeekdayToggled("Mon")

        subject.uiState.test {
            assertFalse(awaitItem().scheduleDraft.selectedWeekdays.contains("Mon"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given a weekday toggled ON, then OFF, then ON again then it is selected")
    fun weekdayToggleCycleWorks() = runTest {
        subject.onWeekdayToggled("Mon")
        subject.onWeekdayToggled("Mon")
        subject.onWeekdayToggled("Mon")

        subject.uiState.test {
            assertTrue(awaitItem().scheduleDraft.selectedWeekdays.contains("Mon"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given a complete schedule when next() then step advances and persists")
    fun completeScheduleNextAdvancesAndPersists() = runTest {
        subject.onWeekdayToggled("Mon")
        subject.onWeekdayToggled("Wed")
        subject.onSessionPresetSelected("40-60")
        advanceUntilIdle()
        assertTrue(subject.uiState.value.canAdvance)

        subject.next()
        advanceUntilIdle()
        assertEquals("Specialization", subject.uiState.value.step.id)

        val saved = OnboardingViewModelTestFixtures.fakeProfileRepository.current
        assertEquals(setOf(Weekday.Monday, Weekday.Wednesday), saved?.weekdays)
        // Soft aim midpoint for the 40–60 band
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
    @DisplayName("Given a custom session length in range when adjusted then it is accepted")
    fun customSessionLengthInRange() = runTest {
        subject.onWeekdayToggled("Mon")
        subject.onSessionPresetSelected(ScheduleDraftUi.CUSTOM_KEY)
        subject.onCustomSessionMinutesChange(90)

        subject.uiState.test {
            assertTrue(awaitItem().canAdvance)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given a custom session length below the minimum then canAdvance is false")
    fun customSessionLengthBelowMinimumBlocksAdvance() = runTest {
        subject.onWeekdayToggled("Mon")
        subject.onSessionPresetSelected(ScheduleDraftUi.CUSTOM_KEY)
        // The stepper's tap-to-type path can hand the ViewModel any int; the ViewModel
        // stores it as-is and relies on the domain validation layer to reject it.
        subject.onCustomSessionMinutesChange(5)

        subject.uiState.test {
            assertFalse(awaitItem().canAdvance)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given a custom session length above the maximum then canAdvance is false")
    fun customSessionLengthAboveMaximumBlocksAdvance() = runTest {
        subject.onWeekdayToggled("Mon")
        subject.onSessionPresetSelected(ScheduleDraftUi.CUSTOM_KEY)
        subject.onCustomSessionMinutesChange(300)

        subject.uiState.test {
            assertFalse(awaitItem().canAdvance)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
