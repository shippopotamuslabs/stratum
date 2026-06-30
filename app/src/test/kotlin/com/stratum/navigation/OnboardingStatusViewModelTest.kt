package com.stratum.navigation

import app.cash.turbine.test
import com.stratum.core.testing.MainDispatcherRule
import com.stratum.core.testing.onboarding.FakeOnboardingRepository
import com.stratum.domain.onboarding.ObserveOnboardingCompleteUseCase
import com.stratum.domain.onboarding.OnboardingProgress
import com.stratum.domain.onboarding.OnboardingStep
import com.stratum.domain.onboarding.ReopenOnboardingWizardUseCase
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MainDispatcherRule::class)
class OnboardingStatusViewModelTest {

    private val fakeRepository = FakeOnboardingRepository()
    private lateinit var subject: OnboardingStatusViewModel

    @BeforeEach
    fun setup() {
        fakeRepository.seed(
            OnboardingProgress(
                currentStep = OnboardingStep.Welcome,
                startedAt = FakeOnboardingRepository.STARTED_AT,
                completedAt = null,
            ),
        )
        subject = buildViewModel()
    }

    private fun buildViewModel(): OnboardingStatusViewModel =
        OnboardingStatusViewModel(
            observeComplete = ObserveOnboardingCompleteUseCase(fakeRepository),
            reopenOnboardingWizard = ReopenOnboardingWizardUseCase(fakeRepository),
        )

    @Test
    @DisplayName("Given incomplete onboarding when observed then emits false and never true")
    fun incompleteOnboardingEmitsFalse() = runTest {
        subject.isComplete.test {
            while (true) {
                when (awaitItem()) {
                    null -> Unit
                    false -> {
                        cancelAndIgnoreRemainingEvents()
                        return@test
                    }
                    true -> error("Incomplete onboarding must not emit true")
                }
            }
        }
    }

    @Test
    @DisplayName("Given completed onboarding when observed then emits true without false flash")
    fun completedOnboardingEmitsTrueWithoutFalseFlash() = runTest {
        fakeRepository.seed(
            OnboardingProgress(
                currentStep = OnboardingStep.Welcome,
                startedAt = FakeOnboardingRepository.STARTED_AT,
                completedAt = FakeOnboardingRepository.COMPLETED_AT,
            ),
        )
        subject = buildViewModel()

        subject.isComplete.test {
            var sawFalse = false
            while (true) {
                when (val value = awaitItem()) {
                    null -> Unit
                    false -> sawFalse = true
                    true -> {
                        assertFalse(sawFalse, "Returning users must not briefly see onboarding (false)")
                        cancelAndIgnoreRemainingEvents()
                        return@test
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("Given onboarding completes later when observed then transitions to true")
    fun onboardingCompletionUpdatesStream() = runTest {
        subject.isComplete.test {
            assertEquals(false, awaitItem())

            fakeRepository.markComplete()

            assertEquals(true, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given completed onboarding when reopenOnboarding is called then resets isComplete to false")
    fun reopenOnboardingResetsIsComplete() = runTest {
        fakeRepository.seed(
            OnboardingProgress(
                currentStep = OnboardingStep.Welcome,
                startedAt = FakeOnboardingRepository.STARTED_AT,
                completedAt = FakeOnboardingRepository.COMPLETED_AT,
            ),
        )
        subject = buildViewModel()

        subject.isComplete.test {
            assertEquals(true, awaitItem())

            subject.reopenOnboarding()

            assertEquals(false, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
