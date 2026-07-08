package com.stratum.domain.onboarding

import com.stratum.core.testing.onboarding.FakeOnboardingRepository
import com.stratum.domain.onboarding.OnboardingProgress
import com.stratum.domain.onboarding.OnboardingStep
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ReopenOnboardingWizardUseCaseTest {
    @Test
    fun `reopen clears completion and returns to Review`() = runTest {
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
        assertEquals(OnboardingStep.Review, repository.current.currentStep)
    }
}
