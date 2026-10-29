package com.stratum.application.onboarding

import com.stratum.core.testing.logging.FakeLoggingPort
import com.stratum.core.testing.onboarding.FakeOnboardingRepository
import com.stratum.core.testing.onboarding.FakeSpecializationPreferenceRepository
import com.stratum.core.testing.onboarding.FakeUserTrainingProfileRepository
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.identity.LiftId
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import com.stratum.application.lift.ValidateGymLiftsStepUseCase
import com.stratum.domain.onboarding.LastTrainedBucket
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.onboarding.OnboardingPath
import com.stratum.domain.onboarding.OnboardingProgress
import com.stratum.domain.onboarding.OnboardingStep
import com.stratum.domain.onboarding.OnboardingWizardState
import com.stratum.domain.onboarding.SpecializationPreference
import com.stratum.domain.onboarding.TrainingAge
import com.stratum.domain.onboarding.UserTrainingProfile
import com.stratum.domain.onboarding.Weekday

class AdvanceOnboardingStepUseCaseTest {

    private val fakeOnboarding = FakeOnboardingRepository()
    private val fakeProfile = FakeUserTrainingProfileRepository()
    private val fakeSpecialization = FakeSpecializationPreferenceRepository()
    private lateinit var subject: AdvanceOnboardingStepUseCase

    @BeforeEach
    fun setup() {
        fakeOnboarding.seed(
            OnboardingProgress(OnboardingStep.Profile, startedAt = 0L, completedAt = null),
        )
        fakeProfile.seed(null)
        fakeSpecialization.seed(SpecializationPreference.Empty)
        subject = AdvanceOnboardingStepUseCase(
            validateCurrentStep = ValidateCurrentOnboardingStepUseCase(
                validateProfileStep = ValidateProfileStepUseCase(),
                validateScheduleStep = ValidateScheduleStepUseCase(),
                validateSpecializationStep = ValidateSpecializationStepUseCase(),
                validateMinimumVolumeStep = ValidateMinimumVolumeStepUseCase(),
                validateGymLiftsStep = ValidateGymLiftsStepUseCase(),
            ),
            updateUserTrainingProfile = UpdateUserTrainingProfileUseCase(fakeProfile),
            updateSpecialization = UpdateSpecializationUseCase(fakeSpecialization),
            saveOnboardingProgress = SaveOnboardingProgressUseCase(fakeOnboarding),
            loggingPort = FakeLoggingPort(),
        )
    }

    @Test
    @DisplayName("Given invalid profile when advancing forward then validation fails and step unchanged")
    fun forwardValidationFailed() = runTest {
        val result = subject(
            currentStep = OnboardingStep.Profile,
            direction = OnboardingStepDirection.Forward,
            profile = UserTrainingProfile.Empty,
            specialization = SpecializationPreference.Empty,
            lifts = emptyList(),
            selectedPreset = null,
        )

        assertEquals(AdvanceOnboardingStepResult.ValidationFailed, result)
        assertEquals(OnboardingStep.Profile, fakeOnboarding.current.currentStep)
    }

    @Test
    @DisplayName("Given valid profile when advancing forward then step advances and data persists")
    fun forwardSuccess() = runTest {
        val profile = UserTrainingProfile(
            trainingAge = TrainingAge.Intermediate,
            lastTrained = LastTrainedBucket.WithinLastThreeMonths,
        )

        val result = subject(
            currentStep = OnboardingStep.Profile,
            direction = OnboardingStepDirection.Forward,
            profile = profile,
            specialization = SpecializationPreference.Empty,
            lifts = emptyList(),
            selectedPreset = null,
        )

        assertTrue(result is AdvanceOnboardingStepResult.Success)
        assertEquals(OnboardingStep.GymAndLifts, (result as AdvanceOnboardingStepResult.Success).newStep)
        assertEquals(profile, fakeProfile.current)
        assertEquals(OnboardingStep.GymAndLifts, fakeOnboarding.current.currentStep)
    }

    @Test
    @DisplayName("Given any state when going back then step retreats without validation")
    fun backwardNoValidation() = runTest {
        val profile = UserTrainingProfile(trainingAge = TrainingAge.Beginner)

        val result = subject(
            currentStep = OnboardingStep.GymAndLifts,
            direction = OnboardingStepDirection.Backward,
            profile = profile,
            specialization = SpecializationPreference.Empty,
            lifts = emptyList(),
            selectedPreset = null,
        )

        assertTrue(result is AdvanceOnboardingStepResult.Success)
        assertEquals(OnboardingStep.Profile, (result as AdvanceOnboardingStepResult.Success).newStep)
        assertEquals(TrainingAge.Beginner, fakeProfile.current?.trainingAge)
    }

    @Test
    @DisplayName("Given Profile step when going back then returns NoOp")
    fun backwardFromFirstIsNoOp() = runTest {
        fakeOnboarding.seed(
            OnboardingProgress(OnboardingStep.Profile, startedAt = 0L, completedAt = null),
        )

        val result = subject(
            currentStep = OnboardingStep.Profile,
            direction = OnboardingStepDirection.Backward,
            profile = UserTrainingProfile.Empty,
            specialization = SpecializationPreference.Empty,
            lifts = emptyList(),
            selectedPreset = null,
        )

        assertEquals(AdvanceOnboardingStepResult.NoOp, result)
    }

    @Test
    @DisplayName("Given ChoosePath when advancing with Import path then jumps to Review and clears schedule data")
    fun choosePathImportJumpsToReviewAndClears() = runTest {
        val profileWithSchedule = UserTrainingProfile(
            trainingAge = TrainingAge.Intermediate,
            sessionLengthMinutes = 60,
            generationTrainingDayCount = 4,
            generationCycleLength = 7,
            generationRestSlotIndices = setOf(3, 6),
        )
        val specialization = SpecializationPreference.Empty

        val result = subject(
            currentStep = OnboardingStep.ChoosePath,
            direction = OnboardingStepDirection.Forward,
            profile = profileWithSchedule,
            specialization = specialization,
            lifts = emptyList(),
            selectedPreset = null,
            path = OnboardingPath.Import,
        )

        assertTrue(result is AdvanceOnboardingStepResult.Success)
        assertEquals(OnboardingStep.Review, (result as AdvanceOnboardingStepResult.Success).newStep)
        // Schedule, session length, and generate cycle-space cleared; specialization reset.
        assertTrue(fakeProfile.current?.weekdays.isNullOrEmpty())
        assertNull(fakeProfile.current?.sessionLengthMinutes)
        assertNull(fakeProfile.current?.generationTrainingDayCount)
        assertNull(fakeProfile.current?.generationCycleLength)
        assertTrue(fakeProfile.current?.generationRestSlotIndices.isNullOrEmpty())
        assertEquals(OnboardingPath.Import, fakeOnboarding.current.selectedPath)
        assertEquals(SpecializationPreference.Empty, fakeSpecialization.current)
    }

    @Test
    @DisplayName("Given ChoosePath when advancing with Generate path then goes to Schedule and keeps data")
    fun choosePathGenerateGoesToSchedule() = runTest {
        val profile = UserTrainingProfile(trainingAge = TrainingAge.Intermediate)

        val result = subject(
            currentStep = OnboardingStep.ChoosePath,
            direction = OnboardingStepDirection.Forward,
            profile = profile,
            specialization = SpecializationPreference.Empty,
            lifts = emptyList(),
            selectedPreset = null,
            path = OnboardingPath.Generate,
        )

        assertTrue(result is AdvanceOnboardingStepResult.Success)
        assertEquals(OnboardingStep.Schedule, (result as AdvanceOnboardingStepResult.Success).newStep)
        assertEquals(profile, fakeProfile.current)
        assertEquals(OnboardingPath.Generate, fakeOnboarding.current.selectedPath)
    }

    @Test
    @DisplayName("Leaving Schedule on Generate persists cycle-space and clears weekdays")
    fun scheduleFlushPersistsCycleSpace() = runTest {
        fakeOnboarding.seed(
            OnboardingProgress(
                currentStep = OnboardingStep.Schedule,
                startedAt = 0L,
                completedAt = null,
                selectedPath = OnboardingPath.Generate,
            ),
        )
        val profile =
            UserTrainingProfile(
                trainingAge = TrainingAge.Intermediate,
                lastTrained = LastTrainedBucket.WithinLastThreeMonths,
                weekdays = setOf(Weekday.Monday),
                sessionLengthMinutes = 60,
            )

        val result = subject(
            currentStep = OnboardingStep.Schedule,
            direction = OnboardingStepDirection.Forward,
            profile = profile,
            specialization = SpecializationPreference.Empty,
            lifts = emptyList(),
            selectedPreset = null,
            path = OnboardingPath.Generate,
            trainingDayCount = 4,
            cycleLength = 7,
            restSlotIndices = setOf(1, 3, 5),
        )

        assertTrue(result is AdvanceOnboardingStepResult.Success)
        assertEquals(OnboardingStep.Specialization, (result as AdvanceOnboardingStepResult.Success).newStep)
        assertTrue(fakeProfile.current?.weekdays.isNullOrEmpty())
        assertEquals(4, fakeProfile.current?.generationTrainingDayCount)
        assertEquals(7, fakeProfile.current?.generationCycleLength)
        assertEquals(setOf(1, 3, 5), fakeProfile.current?.generationRestSlotIndices)
        assertEquals(OnboardingPath.Generate, fakeOnboarding.current.selectedPath)
    }

    @Test
    @DisplayName("Review with selectedPath=Generate and empty weekdays still resolves Generate for back")
    fun reviewGeneratePathWithEmptyWeekdays() {
        val state =
            OnboardingWizardState(
                progress =
                    OnboardingProgress(
                        currentStep = OnboardingStep.Review,
                        startedAt = 0L,
                        completedAt = null,
                        selectedPath = OnboardingPath.Generate,
                    ),
                profile = UserTrainingProfile(weekdays = emptySet(), sessionLengthMinutes = 60),
                specialization = SpecializationPreference.Empty,
                lifts = emptyList(),
                selectedPreset = null,
            )
        assertEquals(OnboardingPath.Generate, state.onboardingPath)
    }

    private fun enabledLift(): Lift = Lift(
        id = LiftId("squat"),
        name = "Squat",
        movementPatterns = setOf(MovementPattern.SquatPattern),
        primaryMuscles = setOf(MuscleGroup.Quad),
        secondaryMuscles = emptySet(),
        incrementTiers = emptyList(),
        presetTags = emptySet(),
        enabled = true,
    )
}
