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
        @DisplayName("Given Welcome is current when next() then state advances to Profile")
        fun nextAdvancesFromWelcomeToProfile() = runTest {
            subject.uiState.test {
                assertEquals("Welcome", awaitItem().step.id)
                subject.next()
                assertEquals("Profile", awaitItem().step.id)
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        @DisplayName("Given Profile is current when back() then state returns to Welcome")
        fun backReturnsFromProfileToWelcome() = runTest {
            OnboardingViewModelTestFixtures.fakeRepository.seed(
                OnboardingProgress(OnboardingStep.Profile, startedAt = 0L, completedAt = null),
            )
            subject = OnboardingViewModelTestFixtures.buildViewModel()

            subject.uiState.test {
                assertEquals("Profile", awaitItem().step.id)
                subject.back()
                assertEquals("Welcome", awaitItem().step.id)
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        @DisplayName("Given Welcome is current when next() then the step is persisted to the repository")
        fun nextPersistsStepToRepository() = runTest {
            subject.next()

            assertEquals(OnboardingStep.Profile, OnboardingViewModelTestFixtures.fakeRepository.current.currentStep)
        }

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
        @DisplayName("Given profile edits when back() then the draft is persisted before leaving")
        fun backPersistsProfileDraft() = runTest {
            OnboardingViewModelTestFixtures.fakeRepository.seed(
                OnboardingProgress(OnboardingStep.Profile, startedAt = 0L, completedAt = null),
            )
            subject = OnboardingViewModelTestFixtures.buildViewModel()
            subject.onTrainingAgeSelected("0–2 years")

            subject.back()

            val saved = OnboardingViewModelTestFixtures.fakeProfileRepository.current
            assertEquals(TrainingAge.Beginner, saved?.trainingAge)
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
        @DisplayName("Given the Review step when next() then it advances to Generate with no validation")
        fun nextAdvancesFromReviewToGenerate() = runTest {
            subject.uiState.test {
                assertEquals("Review", awaitItem().step.id)
                subject.next()
                assertEquals("Generate", awaitItem().step.id)
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(OnboardingStep.Generate, OnboardingViewModelTestFixtures.fakeRepository.current.currentStep)
        }
    }
}
