package com.stratum.feature.onboarding

import app.cash.turbine.test
import com.stratum.core.testing.MainDispatcherRule
import com.stratum.domain.lift.GymPreset
import com.stratum.domain.onboarding.LastTrainedBucket
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.onboarding.OnboardingProgress
import com.stratum.domain.onboarding.OnboardingStep
import com.stratum.domain.onboarding.SpecializationLevel
import com.stratum.domain.onboarding.SpecializationPreference
import com.stratum.domain.onboarding.TrainingAge
import com.stratum.domain.onboarding.UserTrainingProfile
import com.stratum.domain.onboarding.Weekday
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MainDispatcherRule::class)
class OnboardingReviewStepViewModelTest {

    private lateinit var subject: OnboardingViewModel

    @BeforeEach
    fun seedReviewStep() {
        OnboardingViewModelTestFixtures.resetDefaults()
        OnboardingViewModelTestFixtures.fakeRepository.seed(
            OnboardingProgress(OnboardingStep.Review, startedAt = 0L, completedAt = null),
        )
        subject = OnboardingViewModelTestFixtures.buildViewModel()
    }

    @Test
    @DisplayName("Given all inputs when on Review step then reviewDraft aggregates them")
    fun reviewDraftAggregatesAllInputs() = runTest {
        OnboardingViewModelTestFixtures.fakeProfileRepository.seed(
            UserTrainingProfile(
                trainingAge = TrainingAge.Intermediate,
                lastTrained = LastTrainedBucket.WithinLastThreeMonths,
                weekdays = setOf(Weekday.Monday, Weekday.Wednesday, Weekday.Friday),
                sessionLengthMinutes = 60,
            ),
        )
        OnboardingViewModelTestFixtures.fakeSpecializationRepository.seed(
            SpecializationPreference(
                levels = mapOf(
                    MuscleGroup.Quad to SpecializationLevel.Specialize,
                    MuscleGroup.Chest to SpecializationLevel.Specialize,
                ),
            ),
        )
        OnboardingViewModelTestFixtures.fakeLiftRepository.seedLifts(
            listOf(
                OnboardingViewModelTestFixtures.aLift("squat", tags = setOf(GymPreset.CommercialGym), enabled = true),
                OnboardingViewModelTestFixtures.aLift("bench", tags = setOf(GymPreset.CommercialGym), enabled = true),
                OnboardingViewModelTestFixtures.aLift("curl", enabled = false),
            ),
        )
        OnboardingViewModelTestFixtures.fakeLiftRepository.seedPreset(GymPreset.CommercialGym)
        subject = OnboardingViewModelTestFixtures.buildViewModel()

        subject.uiState.test {
            val draft = awaitItem().reviewDraft
            assertEquals("4–7 years", draft.trainingAge)
            assertEquals("Within the last month", draft.lastTrained)
            assertEquals(listOf("Mon", "Wed", "Fri"), draft.weekdays)
            assertEquals(60, draft.sessionMinutes)
            assertEquals(listOf("Chest", "Quads"), draft.specialization)
            assertEquals("Commercial gym", draft.presetLabel)
            assertEquals(2, draft.enabledLiftCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Given no specialization when on Review step then reviewDraft reports a balanced program")
    fun reviewDraftShowsBalancedWhenSpecializationEmpty() = runTest {
        OnboardingViewModelTestFixtures.fakeProfileRepository.seed(
            UserTrainingProfile(
                trainingAge = TrainingAge.Beginner,
                lastTrained = LastTrainedBucket.MoreThanThreeMonthsAgo,
                weekdays = setOf(Weekday.Tuesday),
                sessionLengthMinutes = 45,
            ),
        )
        subject = OnboardingViewModelTestFixtures.buildViewModel()

        subject.uiState.test {
            val draft = awaitItem().reviewDraft
            assertTrue(draft.specialization.isEmpty())
            assertEquals("0–2 years", draft.trainingAge)
            assertEquals(listOf("Tue"), draft.weekdays)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
