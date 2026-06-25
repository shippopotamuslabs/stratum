package com.stratum.domain.onboarding

import com.stratum.core.testing.onboarding.FakeOnboardingRepository
import com.stratum.core.testing.onboarding.FakeUserTrainingProfileRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class CompleteOnboardingUseCaseTest {

    private val fakeOnboarding = FakeOnboardingRepository()
    private val fakeProfile = FakeUserTrainingProfileRepository()
    private lateinit var subject: CompleteOnboardingUseCase

    @BeforeEach
    fun setup() {
        fakeOnboarding.seed(
            OnboardingProgress(OnboardingStep.Review, startedAt = 0L, completedAt = null),
        )
        subject = CompleteOnboardingUseCase(fakeOnboarding, fakeProfile)
    }

    @Test
    @DisplayName("Given a final profile when invoked then profile is saved and onboarding marked complete")
    fun savesProfileAndMarksComplete() = runTest {
        val profile = UserTrainingProfile(
            trainingAge = TrainingAge.Intermediate,
            lastTrained = LastTrainedBucket.WithinLastMonth,
        )

        subject(profile)

        assertEquals(profile, fakeProfile.current)
        assertNotNull(fakeOnboarding.current.completedAt)
    }

    @Test
    @DisplayName("Given onboarding already in progress when invoked then completedAt is set")
    fun setsCompletedAt() = runTest {
        subject(UserTrainingProfile.Empty)

        assertNotNull(fakeOnboarding.current.completedAt)
    }

    @Test
    @DisplayName("Given no prior profile when invoked then profile is persisted")
    fun persistsEmptyProfile() = runTest {
        fakeProfile.seed(null)

        subject(UserTrainingProfile(trainingAge = TrainingAge.Beginner))

        assertEquals(TrainingAge.Beginner, fakeProfile.current?.trainingAge)
    }
}
