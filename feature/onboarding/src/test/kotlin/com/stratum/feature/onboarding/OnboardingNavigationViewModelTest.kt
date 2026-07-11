package com.stratum.feature.onboarding

import app.cash.turbine.test
import com.stratum.core.testing.MainDispatcherRule
import com.stratum.domain.onboarding.OnboardingProgress
import com.stratum.domain.onboarding.OnboardingStep
import com.stratum.domain.onboarding.TrainingAge
import com.stratum.domain.onboarding.UserTrainingProfile
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
class OnboardingNavigationViewModelTest {

    private lateinit var subject: OnboardingViewModel

    @BeforeEach
    fun setup() {
        OnboardingViewModelTestFixtures.resetDefaults()
        subject = OnboardingViewModelTestFixtures.buildViewModel()
    }

    @Nested
    @DisplayName("OS-1 step navigation (regression)")
    inner class StepNavigation {
        @Test
        @DisplayName("Given Profile is current when next() then state advances to GymAndLifts")
        fun nextAdvancesFromProfileToGymAndLifts() = runTest {
            OnboardingViewModelTestFixtures.fakeProfileRepository.seed(validProfile())
            subject = OnboardingViewModelTestFixtures.buildViewModel()

            subject.uiState.test {
                assertEquals("Profile", awaitItem().step.id)
                subject.next()
                assertEquals("GymAndLifts", awaitItem().step.id)
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        @DisplayName("Given Profile is current when back() then state stays on Profile (first step)")
        fun backOnFirstStepIsNoOp() = runTest {
            subject.uiState.test {
                assertEquals("Profile", awaitItem().step.id)
                subject.back()
                // No step change — still Profile (NoOp).
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        @DisplayName("Given Profile is current when next() then the step is persisted to the repository")
        fun nextPersistsStepToRepository() = runTest {
            OnboardingViewModelTestFixtures.fakeProfileRepository.seed(validProfile())
            subject = OnboardingViewModelTestFixtures.buildViewModel()
            subject.next()

            assertEquals(OnboardingStep.GymAndLifts, OnboardingViewModelTestFixtures.fakeRepository.current.currentStep)
        }

        private fun validProfile() =
            UserTrainingProfile(
                trainingAge = TrainingAge.Beginner,
                lastTrained = com.stratum.domain.onboarding.LastTrainedBucket.WithinLastThreeMonths,
            )

        @Test
        @DisplayName("Given onboarding is incomplete then uiState reports isComplete false")
        fun incompleteOnboardingReportsNotComplete() = runTest {
            subject.uiState.test {
                assertEquals(false, awaitItem().isComplete)
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    @Nested
    @DisplayName("OS-2 back-navigation preserves edits")
    inner class BackPreserves {
        @Test
        @DisplayName("Given profile edits when next() then the draft is persisted before leaving")
        fun nextPersistsProfileDraft() = runTest {
            OnboardingViewModelTestFixtures.fakeRepository.seed(
                OnboardingProgress(OnboardingStep.Profile, startedAt = 0L, completedAt = null),
            )
            subject = OnboardingViewModelTestFixtures.buildViewModel()
            subject.onTrainingAgeSelected("0–2 years")
            subject.onLastTrainedSelected("Within the last 3 months")

            subject.next()

            val saved = OnboardingViewModelTestFixtures.fakeProfileRepository.current
            assertEquals(TrainingAge.Beginner, saved?.trainingAge)
            assertEquals(OnboardingStep.GymAndLifts, OnboardingViewModelTestFixtures.fakeRepository.current.currentStep)
        }
    }

    @Nested
    @DisplayName("OS-5 review step navigation")
    inner class ReviewNavigation {
        @BeforeEach
        fun seedReviewStep() {
            OnboardingViewModelTestFixtures.fakeRepository.seed(
                OnboardingProgress(OnboardingStep.Review, startedAt = 0L, completedAt = null),
            )
            subject = OnboardingViewModelTestFixtures.buildViewModel()
        }

        @Test
        @DisplayName("Given the Review step when next() then it stays on Review (terminal step)")
        fun reviewIsTerminal() = runTest {
            assertEquals(OnboardingStep.Review, OnboardingViewModelTestFixtures.fakeRepository.current.currentStep)
            subject.next()
            // Review is the terminal step — next() is a NoOp, no advance.
            assertEquals(OnboardingStep.Review, OnboardingViewModelTestFixtures.fakeRepository.current.currentStep)
        }
    }
}
