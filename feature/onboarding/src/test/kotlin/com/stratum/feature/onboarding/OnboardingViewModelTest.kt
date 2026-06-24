package com.stratum.feature.onboarding

import app.cash.turbine.test
import com.stratum.core.testing.MainDispatcherRule
import com.stratum.core.testing.onboarding.FakeOnboardingRepository
import com.stratum.domain.onboarding.OnboardingProgress
import com.stratum.domain.onboarding.OnboardingStep
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MainDispatcherRule::class)
class OnboardingViewModelTest {

    private val fakeRepository = FakeOnboardingRepository()
    private lateinit var subject: OnboardingViewModel

    @BeforeEach
    fun setup() {
        fakeRepository.seed(
            OnboardingProgress(OnboardingStep.Welcome, startedAt = 0L, completedAt = null),
        )
        subject = OnboardingViewModel(fakeRepository)
    }

    @Test
    @DisplayName("Given Welcome is current when next() then state advances to Profile")
    fun nextAdvancesFromWelcomeToProfile() = runTest {
        subject.uiState.test {
            assertEquals(OnboardingStep.Welcome, awaitItem().step)
            subject.next()
            assertEquals(OnboardingStep.Profile, awaitItem().step)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given Profile is current when back() then state returns to Welcome")
    fun backReturnsFromProfileToWelcome() = runTest {
        fakeRepository.seed(
            OnboardingProgress(OnboardingStep.Profile, startedAt = 0L, completedAt = null),
        )
        subject = OnboardingViewModel(fakeRepository)

        subject.uiState.test {
            assertEquals(OnboardingStep.Profile, awaitItem().step)
            subject.back()
            assertEquals(OnboardingStep.Welcome, awaitItem().step)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given Welcome is current when next() then the step is persisted to the repository")
    fun nextPersistsStepToRepository() = runTest {
        subject.next()

        assertEquals(OnboardingStep.Profile, fakeRepository.current.currentStep)
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