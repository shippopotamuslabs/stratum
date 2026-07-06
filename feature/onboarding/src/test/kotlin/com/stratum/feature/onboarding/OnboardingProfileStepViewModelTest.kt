package com.stratum.feature.onboarding

import app.cash.turbine.test
import com.stratum.core.testing.MainDispatcherRule
import com.stratum.domain.onboarding.LastTrainedBucket
import com.stratum.domain.onboarding.OnboardingProgress
import com.stratum.domain.onboarding.OnboardingStep
import com.stratum.domain.onboarding.TrainingAge
import com.stratum.domain.onboarding.UserTrainingProfile
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MainDispatcherRule::class)
class OnboardingProfileStepViewModelTest {

    private lateinit var subject: OnboardingViewModel

    @BeforeEach
    fun seedProfileStep() {
        OnboardingViewModelTestFixtures.resetDefaults()
        OnboardingViewModelTestFixtures.fakeRepository.seed(
            OnboardingProgress(OnboardingStep.Profile, startedAt = 0L, completedAt = null),
        )
        subject = OnboardingViewModelTestFixtures.buildViewModel()
    }

    @Test
    @DisplayName("Given an empty profile when on Profile step then canAdvance is false")
    fun emptyProfileCannotAdvance() = runTest {
        subject.uiState.test {
            assertFalse(awaitItem().canAdvance)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given an invalid profile when next() then step does not advance and errors show")
    fun invalidProfileNextDoesNotAdvance() = runTest {
        subject.uiState.test {
            awaitItem()
            subject.next()
            val state = awaitItem()
            assertEquals("Profile", state.step.id)
            assertTrue(state.profileDraft.showErrors)
            assertTrue(state.profileDraft.hasTrainingAgeError)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given a complete profile when next() then step advances and draft is persisted")
    fun completeProfileNextAdvancesAndPersists() = runTest {
        subject.onTrainingAgeSelected("4–7 years")
        subject.onLastTrainedSelected("Within the last month")
        advanceUntilIdle()
        assertTrue(subject.uiState.value.canAdvance)

        subject.next()
        advanceUntilIdle()
        assertEquals("Schedule", subject.uiState.value.step.id)

        val saved = OnboardingViewModelTestFixtures.fakeProfileRepository.current
        assertEquals(TrainingAge.Intermediate, saved?.trainingAge)
        assertEquals(LastTrainedBucket.WithinLastThreeMonths, saved?.lastTrained)
    }

    @Test
    @DisplayName("Given a persisted profile draft when VM created then it seeds the UI")
    fun persistedProfileSeedsUi() = runTest {
        OnboardingViewModelTestFixtures.fakeProfileRepository.seed(
            UserTrainingProfile(
                trainingAge = TrainingAge.Advanced,
                lastTrained = LastTrainedBucket.MoreThanThreeMonthsAgo,
            ),
        )
        subject = OnboardingViewModelTestFixtures.buildViewModel()

        subject.uiState.test {
            val draft = awaitItem().profileDraft
            assertEquals("10+ years", draft.trainingAgeSelection)
            assertEquals("Over a year ago", draft.lastTrainedSelection)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
