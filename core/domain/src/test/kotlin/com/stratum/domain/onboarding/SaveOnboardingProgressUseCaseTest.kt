package com.stratum.domain.onboarding

import app.cash.turbine.test
import com.stratum.core.testing.onboarding.FakeOnboardingRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class SaveOnboardingProgressUseCaseTest {

    private val fakeRepository = FakeOnboardingRepository()
    private lateinit var subject: SaveOnboardingProgressUseCase

    @BeforeEach
    fun setup() {
        // Seed to null so the first save establishes the row, matching a fresh install.
        fakeRepository.seed(OnboardingProgress(OnboardingStep.Welcome, startedAt = 0L, completedAt = null))
        subject = SaveOnboardingProgressUseCase(fakeRepository)
    }

    @Test
    @DisplayName("Given a step when invoked then the step is persisted to the repository")
    fun saveStepPersistsToRepository() = runTest {
        subject.invoke(OnboardingStep.Profile)

        fakeRepository.observeProgress().test {
            val emitted = awaitItem()
            assertEquals(OnboardingStep.Profile, emitted?.currentStep)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given a later step when invoked then it overwrites the current step")
    fun saveStepOverwritesPreviousStep() = runTest {
        subject.invoke(OnboardingStep.Profile)
        subject.invoke(OnboardingStep.Schedule)

        assertEquals(OnboardingStep.Schedule, fakeRepository.current.currentStep)
    }
}