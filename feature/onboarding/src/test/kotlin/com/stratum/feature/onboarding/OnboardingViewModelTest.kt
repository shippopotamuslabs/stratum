package com.stratum.feature.onboarding

import app.cash.turbine.test
import com.stratum.core.testing.MainDispatcherRule
import com.stratum.core.testing.onboarding.FakeOnboardingRepository
import com.stratum.core.testing.onboarding.FakeSpecializationPreferenceRepository
import com.stratum.core.testing.onboarding.FakeUserTrainingProfileRepository
import com.stratum.domain.onboarding.CompleteOnboardingUseCase
import com.stratum.domain.onboarding.GetOnboardingStateUseCase
import com.stratum.domain.onboarding.LastTrainedBucket
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.onboarding.ObserveSpecializationUseCase
import com.stratum.domain.onboarding.OnboardingProgress
import com.stratum.domain.onboarding.OnboardingStep
import com.stratum.domain.onboarding.SaveOnboardingProgressUseCase
import com.stratum.domain.onboarding.SpecializationLevel
import com.stratum.domain.onboarding.SpecializationPreference
import com.stratum.domain.onboarding.TrainingAge
import com.stratum.domain.onboarding.UpdateSpecializationUseCase
import com.stratum.domain.onboarding.UpdateUserTrainingProfileUseCase
import com.stratum.domain.onboarding.UserTrainingProfile
import com.stratum.domain.onboarding.ValidateProfileStepUseCase
import com.stratum.domain.onboarding.ValidateScheduleStepUseCase
import com.stratum.domain.onboarding.ValidateSpecializationStepUseCase
import com.stratum.domain.onboarding.Weekday
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
class OnboardingViewModelTest {

    private val fakeRepository = FakeOnboardingRepository()
    private val fakeProfileRepository = FakeUserTrainingProfileRepository()
    private val fakeSpecializationRepository = FakeSpecializationPreferenceRepository()
    private lateinit var subject: OnboardingViewModel

    @BeforeEach
    fun setup() {
        fakeRepository.seed(
            OnboardingProgress(OnboardingStep.Welcome, startedAt = 0L, completedAt = null),
        )
        fakeProfileRepository.seed(null)
        fakeSpecializationRepository.seed(SpecializationPreference.Empty)
        subject = buildViewModel()
    }

    // Wires the VM over fakes by constructing the real use cases (they are thin, pure
    // wrappers over the ports — no behavior to fake, and using the real ones exercises
    // the production composition path end-to-end).
    private fun buildViewModel(): OnboardingViewModel = OnboardingViewModel(
        getOnboardingState = GetOnboardingStateUseCase(fakeRepository, fakeProfileRepository),
        saveOnboardingProgress = SaveOnboardingProgressUseCase(fakeRepository),
        updateUserTrainingProfile = UpdateUserTrainingProfileUseCase(fakeProfileRepository),
        observeSpecialization = ObserveSpecializationUseCase(fakeSpecializationRepository),
        updateSpecialization = UpdateSpecializationUseCase(fakeSpecializationRepository),
        completeOnboarding = CompleteOnboardingUseCase(fakeRepository, fakeProfileRepository),
        validateProfileStep = ValidateProfileStepUseCase(),
        validateScheduleStep = ValidateScheduleStepUseCase(),
        validateSpecializationStep = ValidateSpecializationStepUseCase(),
    )

    @Nested
    @DisplayName("OS-1 step navigation (regression)")
    inner class StepNavigation {
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
            subject = buildViewModel()

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

    @Nested
    @DisplayName("OS-2 profile step")
    inner class ProfileStep {
        @BeforeEach
        fun seedProfileStep() {
            fakeRepository.seed(
                OnboardingProgress(OnboardingStep.Profile, startedAt = 0L, completedAt = null),
            )
            subject = buildViewModel()
        }

        @Test
        @DisplayName("Given an empty profile when on Profile step then canAdvance is false")
        fun emptyProfileCannotAdvance() = runTest {
            subject.uiState.test {
                assertFalse(awaitItem().canAdvance)
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        @DisplayName("Given an invalid profile when next() then step does not advance and errors show")
        fun invalidProfileNextDoesNotAdvance() = runTest {
            subject.uiState.test {
                awaitItem()
                subject.next()
                val state = awaitItem()
                assertEquals(OnboardingStep.Profile, state.step)
                assertTrue(state.profileDraft.showErrors)
                assertEquals("Select your training age.", state.profileDraft.trainingAgeError)
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        @DisplayName("Given a complete profile when next() then step advances and draft is persisted")
        fun completeProfileNextAdvancesAndPersists() = runTest {
            subject.onTrainingAgeSelected("4–7 years")
            subject.onLastTrainedSelected("Within the last month")

            subject.uiState.test {
                assertTrue(awaitItem().canAdvance)
                subject.next()
                assertEquals(OnboardingStep.Schedule, awaitItem().step)
                cancelAndIgnoreRemainingEvents()
            }

            val saved = fakeProfileRepository.current
            assertEquals(TrainingAge.Intermediate, saved?.trainingAge)
            assertEquals(LastTrainedBucket.WithinLastMonth, saved?.lastTrained)
        }

        @Test
        @DisplayName("Given a persisted profile draft when VM created then it seeds the UI")
        fun persistedProfileSeedsUi() = runTest {
            fakeProfileRepository.seed(
                UserTrainingProfile(
                    trainingAge = TrainingAge.Advanced,
                    lastTrained = LastTrainedBucket.MoreThanAYearAgo,
                ),
            )
            subject = buildViewModel()

            subject.uiState.test {
                val draft = awaitItem().profileDraft
                assertEquals("10+ years", draft.trainingAgeSelection)
                assertEquals("Over a year ago", draft.lastTrainedSelection)
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    @Nested
    @DisplayName("OS-2 schedule step")
    inner class ScheduleStep {
        @BeforeEach
        fun seedScheduleStep() {
            fakeRepository.seed(
                OnboardingProgress(OnboardingStep.Schedule, startedAt = 0L, completedAt = null),
            )
            subject = buildViewModel()
        }

        @Test
        @DisplayName("Given an empty schedule when on Schedule step then canAdvance is false")
        fun emptyScheduleCannotAdvance() = runTest {
            subject.uiState.test {
                assertFalse(awaitItem().canAdvance)
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        @DisplayName("Given weekday toggle when onWeekdayToggled then selection updates")
        fun weekdayToggleUpdatesSelection() = runTest {
            subject.onWeekdayToggled("Mon")

            subject.uiState.test {
                assertTrue(awaitItem().scheduleDraft.selectedWeekdays.contains("Mon"))
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        @DisplayName("Given a weekday already selected when toggled again then it is removed")
        fun weekdayToggleRemovesWhenAlreadySelected() = runTest {
            subject.onWeekdayToggled("Mon")
            subject.onWeekdayToggled("Mon")

            subject.uiState.test {
                assertFalse(awaitItem().scheduleDraft.selectedWeekdays.contains("Mon"))
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        @DisplayName("Given a weekday toggled ON, then OFF, then ON again then it is selected")
        fun weekdayToggleCycleWorks() = runTest {
            subject.onWeekdayToggled("Mon")
            subject.onWeekdayToggled("Mon")
            subject.onWeekdayToggled("Mon")

            subject.uiState.test {
                assertTrue(awaitItem().scheduleDraft.selectedWeekdays.contains("Mon"))
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        @DisplayName("Given a complete schedule when next() then step advances and persists")
        fun completeScheduleNextAdvancesAndPersists() = runTest {
            subject.onWeekdayToggled("Mon")
            subject.onWeekdayToggled("Wed")
            subject.onSessionPresetSelected("60")

            subject.uiState.test {
                assertTrue(awaitItem().canAdvance)
                subject.next()
                assertEquals(OnboardingStep.Specialization, awaitItem().step)
                cancelAndIgnoreRemainingEvents()
            }

            val saved = fakeProfileRepository.current
            assertEquals(setOf(Weekday.Monday, Weekday.Wednesday), saved?.weekdays)
            assertEquals(60, saved?.sessionLengthMinutes)
        }

        @Test
        @DisplayName("Given a custom session length in range when entered then it is accepted")
        fun customSessionLengthInRange() = runTest {
            subject.onWeekdayToggled("Mon")
            subject.onSessionPresetSelected(ScheduleDraftUi.CUSTOM_KEY)
            subject.onCustomSessionChange("90")

            subject.uiState.test {
                assertTrue(awaitItem().canAdvance)
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
            fakeRepository.seed(
                OnboardingProgress(OnboardingStep.Profile, startedAt = 0L, completedAt = null),
            )
            subject = buildViewModel()
            subject.onTrainingAgeSelected("0–2 years")

            subject.back()

            val saved = fakeProfileRepository.current
            assertEquals(TrainingAge.Beginner, saved?.trainingAge)
        }
    }

    @Nested
    @DisplayName("OS-3 specialization step")
    inner class SpecializationStep {
        @BeforeEach
        fun seedSpecializationStep() {
            fakeRepository.seed(
                OnboardingProgress(OnboardingStep.Specialization, startedAt = 0L, completedAt = null),
            )
            fakeSpecializationRepository.seed(SpecializationPreference.Empty)
            subject = buildViewModel()
        }

        @Test
        @DisplayName("Given an empty specialization when on Specialization step then canAdvance is true (skip is valid)")
        fun emptySpecializationCanAdvance() = runTest {
            subject.uiState.test {
                assertTrue(awaitItem().canAdvance)
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        @DisplayName("Given a muscle toggle when onMuscleToggled then the selection updates")
        fun muscleToggleUpdatesSelection() = runTest {
            subject.onMuscleToggled("Chest")

            subject.uiState.test {
                assertTrue(awaitItem().specializationDraft.selectedMuscleLabels.contains("Chest"))
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        @DisplayName("Given a selected muscle when toggled again then it is removed")
        fun muscleToggleRemovesWhenAlreadySelected() = runTest {
            subject.onMuscleToggled("Chest")
            subject.onMuscleToggled("Chest")

            subject.uiState.test {
                assertFalse(awaitItem().specializationDraft.selectedMuscleLabels.contains("Chest"))
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        @DisplayName("Given a muscle toggled ON, then OFF, then ON again then it is selected")
        fun muscleToggleCycleWorks() = runTest {
            subject.onMuscleToggled("Chest")
            subject.onMuscleToggled("Chest")
            subject.onMuscleToggled("Chest")

            subject.uiState.test {
                assertTrue(awaitItem().specializationDraft.selectedMuscleLabels.contains("Chest"))
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        @DisplayName("Given three muscles selected when checked then the draft is at cap")
        fun threeMusclesIsAtCap() = runTest {
            subject.onMuscleToggled("Chest")
            subject.onMuscleToggled("Back")
            subject.onMuscleToggled("Quads")

            subject.uiState.test {
                val draft = awaitItem().specializationDraft
                assertEquals(3, draft.specializedCount)
                assertTrue(draft.atCap)
                assertTrue(draft.isValid)
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        @DisplayName("Given a skipped specialization when next() then step advances and Empty is persisted")
        fun skippedSpecializationAdvancesAndPersistsEmpty() = runTest {
            subject.uiState.test {
                awaitItem()
                subject.next()
                assertEquals(OnboardingStep.GymAndLifts, awaitItem().step)
                cancelAndIgnoreRemainingEvents()
            }

            assertEquals(SpecializationPreference.Empty, fakeSpecializationRepository.current)
        }

        @Test
        @DisplayName("Given a specialization selection when next() then it is persisted and step advances")
        fun selectionAdvancesAndPersists() = runTest {
            subject.onMuscleToggled("Chest")
            subject.onMuscleToggled("Back")

            subject.uiState.test {
                awaitItem()
                subject.next()
                assertEquals(OnboardingStep.GymAndLifts, awaitItem().step)
                cancelAndIgnoreRemainingEvents()
            }

            val saved = fakeSpecializationRepository.current
            assertEquals(setOf(MuscleGroup.Chest, MuscleGroup.Back), saved.specializedMuscles)
        }

        @Test
        @DisplayName("Given a persisted specialization when VM created then it seeds the UI")
        fun persistedSpecializationSeedsUi() = runTest {
            fakeSpecializationRepository.seed(
                SpecializationPreference(
                    levels = mapOf(MuscleGroup.Quad to SpecializationLevel.Specialize),
                ),
            )
            subject = buildViewModel()

            subject.uiState.test {
                assertTrue(awaitItem().specializationDraft.selectedMuscleLabels.contains("Quads"))
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        @DisplayName("Given a specialization edit when back() then it is persisted before leaving")
        fun backPersistsSpecialization() = runTest {
            subject.onMuscleToggled("Chest")

            subject.back()

            val saved = fakeSpecializationRepository.current
            assertEquals(setOf(MuscleGroup.Chest), saved.specializedMuscles)
            assertEquals(OnboardingStep.Schedule, fakeRepository.current.currentStep)
        }
    }
}
