package com.stratum.application.onboarding

import com.stratum.core.testing.logging.FakeLoggingPort
import com.stratum.core.testing.onboarding.FakeOnboardingRepository
import com.stratum.domain.onboarding.OnboardingProgress
import com.stratum.domain.onboarding.OnboardingStep
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ReopenOnboardingWizardUseCaseTest {
    @Test
    fun `reopen clears completion and returns to ChoosePath`() = runTest {
        val repository =
            FakeOnboardingRepository(
                initial =
                    OnboardingProgress(
                        currentStep = OnboardingStep.Review,
                        startedAt = FakeOnboardingRepository.STARTED_AT,
                        completedAt = FakeOnboardingRepository.COMPLETED_AT,
                    ),
            )
        val subject = ReopenOnboardingWizardUseCase(repository, FakeLoggingPort())

        subject()

        assertNull(repository.current.completedAt)
        assertEquals(OnboardingStep.ChoosePath, repository.current.currentStep)
    }

    @Test
    fun `reopen returns PersistenceFailed when the repository write throws (STR-243)`() = runTest {
        val throwing =
            object : OnboardingRepository by FakeOnboardingRepository() {
                override suspend fun reopenWizard() {
                    throw RuntimeException("store unavailable")
                }
            }
        val subject = ReopenOnboardingWizardUseCase(throwing, FakeLoggingPort())

        assertEquals(ReopenOnboardingWizardResult.PersistenceFailed, subject())
    }

    @Test
    fun `reopen propagates CancellationException instead of capturing it (STR-243)`() {
        val cancelling =
            object : OnboardingRepository by FakeOnboardingRepository() {
                override suspend fun reopenWizard() {
                    throw kotlin.coroutines.cancellation.CancellationException("cancelled")
                }
            }
        val subject = ReopenOnboardingWizardUseCase(cancelling, FakeLoggingPort())

        org.junit.jupiter.api.Assertions.assertThrows(
            kotlin.coroutines.cancellation.CancellationException::class.java,
        ) {
            kotlinx.coroutines.runBlocking { subject() }
        }
    }
}
