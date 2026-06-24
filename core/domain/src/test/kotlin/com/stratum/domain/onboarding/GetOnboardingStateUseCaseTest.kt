package com.stratum.domain.onboarding

import app.cash.turbine.test
import com.stratum.core.testing.onboarding.FakeOnboardingRepository
import com.stratum.core.testing.onboarding.FakeUserTrainingProfileRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class GetOnboardingStateUseCaseTest {

    private val fakeOnboarding = FakeOnboardingRepository()
    private val fakeProfile = FakeUserTrainingProfileRepository()
    private lateinit var subject: GetOnboardingStateUseCase

    @BeforeEach
    fun setup() {
        subject = GetOnboardingStateUseCase(fakeOnboarding, fakeProfile)
    }

    @Test
    @DisplayName("Given both repos empty when invoked then state emits null progress and profile")
    fun bothEmptyEmitsNulls() = runTest {
        fakeOnboarding.seed(OnboardingProgress(OnboardingStep.Welcome, startedAt = 0L, completedAt = null))
        fakeProfile.seed(null)

        subject().test {
            val state = awaitItem()
            assertEquals(OnboardingStep.Welcome, state.currentStep)
            assertNull(state.profile)
            assertEquals(false, state.isComplete)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given progress and profile when invoked then state composes both")
    fun composesBothStreams() = runTest {
        val progress = OnboardingProgress(OnboardingStep.Schedule, startedAt = 1L, completedAt = null)
        val profile = UserTrainingProfile(
            trainingAge = TrainingAge.Intermediate,
            lastTrained = LastTrainedBucket.WithinLastMonth,
        )
        fakeOnboarding.seed(progress)
        fakeProfile.seed(profile)

        subject().test {
            val state = awaitItem()
            assertEquals(OnboardingStep.Schedule, state.currentStep)
            assertEquals(profile, state.profile)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given a new profile is saved when observing then state re-emits with the update")
    fun reEmitsOnProfileChange() = runTest {
        fakeOnboarding.seed(OnboardingProgress(OnboardingStep.Profile, startedAt = 0L, completedAt = null))
        fakeProfile.seed(null)

        subject().test {
            assertNull(awaitItem().profile)
            subject // hold; the use case is the subject, mutate via repo
            val updatedProfile = UserTrainingProfile(trainingAge = TrainingAge.Advanced)
            fakeProfile.saveProfile(updatedProfile)
            val updated = awaitItem()
            assertEquals(updatedProfile, updated.profile)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
