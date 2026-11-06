package com.stratum.application.onboarding

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
        val subject = ReopenOnboardingWizardUseCase(repository)

        subject()

        assertNull(repository.current.completedAt)
        assertEquals(OnboardingStep.ChoosePath, repository.current.currentStep)
    }

    @Test
    fun `reopen returns PersistenceFailed carrying the diagnostic when the repository write throws (STR-243)`() = runTest {
        val boom = RuntimeException("store unavailable")
        val throwing =
            object : OnboardingRepository by FakeOnboardingRepository() {
                override suspend fun reopenWizard() {
                    throw boom
                }
            }
        val subject = ReopenOnboardingWizardUseCase(throwing)

        val result = subject()
        val failed = result as ReopenOnboardingWizardResult.PersistenceFailed
        assertEquals(1, failed.diagnostics.size)
        assertEquals("ReopenOnboardingWizard", failed.diagnostics.single().tag)
        assertEquals("Failed to reopen onboarding wizard: store unavailable", failed.diagnostics.single().message)
        org.junit.jupiter.api.Assertions.assertSame(boom, failed.diagnostics.single().cause)
    }

    @Test
    fun `reopen propagates CancellationException instead of capturing it (STR-243)`() {
        val cancelling =
            object : OnboardingRepository by FakeOnboardingRepository() {
                override suspend fun reopenWizard() {
                    throw kotlin.coroutines.cancellation.CancellationException("cancelled")
                }
            }
        val subject = ReopenOnboardingWizardUseCase(cancelling)

        org.junit.jupiter.api.Assertions.assertThrows(
            kotlin.coroutines.cancellation.CancellationException::class.java,
        ) {
            kotlinx.coroutines.runBlocking { subject() }
        }
    }
}
