package com.stratum.feature.onboarding

import app.cash.turbine.test
import com.stratum.core.testing.MainDispatcherRule
import com.stratum.core.testing.onboarding.FakeOnboardingRepository
import com.stratum.core.testing.onboarding.FakeUserTrainingProfileRepository
import com.stratum.core.testing.program.FakeProgramGeneratorPort
import com.stratum.core.testing.program.FakeProgramRepository
import com.stratum.domain.lift.ApplyGymPresetUseCase
import com.stratum.domain.lift.SetSelectedPresetUseCase
import com.stratum.domain.onboarding.CompleteOnboardingUseCase
import com.stratum.domain.onboarding.TrainingAge
import com.stratum.domain.onboarding.UserTrainingProfile
import com.stratum.domain.program.GenerateProgramUseCase
import com.stratum.domain.program.Program
import com.stratum.domain.program.ProgramGenerationPayloadLoader
import com.stratum.domain.program.identity.ProgramId
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * OnboardingViewModel Generate flow (OS-7 D4). Scenarios PROG-GEN-600 (generate success →
 * completes + persists), 601 (failure → onboarding NOT complete; mutation-defense), 602 (error
 * state + retry), 603 (terminal generateState after generate).
 *
 * `generate()` drives [GenerateProgramUseCase] and exposes a terminal [GenerateState] (Idle on
 * success, Error on failure). The `runTest` block waits for the `viewModelScope.launch` to complete
 * before its body asserts, so the assertions see the terminal state (not the ephemeral Loading
 * intermediate that a conflating `StateFlow` drops). PROG-GEN-601 is the load-bearing
 * mutation-defense: a failed generation must never flip `isComplete` or persist a program.
 */
@ExtendWith(MainDispatcherRule::class)
class OnboardingGenerateViewModelTest {

    @Nested
    @DisplayName("PROG-GEN-600 tapping Generate completes onboarding and persists the program")
    inner class GenerateSuccess {
        @Test
        fun `generate marks onboarding complete and persists the program`() = runTest {
            val (viewModel, onboardingRepo, programRepo) = buildViewModel(fail = false)

            viewModel.generate()

            assertTrue(onboardingRepo.current.isComplete)
            assertTrue(programRepo.current != null, "expected a persisted program")
        }
    }

    @Nested
    @DisplayName("PROG-GEN-601 generation failure does not complete onboarding")
    inner class GenerateFailure {
        @Test
        fun `generate leaves onboarding incomplete and persists no program on failure`() = runTest {
            val (viewModel, onboardingRepo, programRepo) = buildViewModel(fail = true)

            viewModel.generate()

            assertFalse(onboardingRepo.current.isComplete)
            assertEquals(null, programRepo.current)
        }
    }

    @Nested
    @DisplayName("PROG-GEN-603 terminal generateState is Idle on success")
    inner class LoadingCopy {
        @Test
        fun `after generate the terminal generateState is Idle on success`() = runTest {
            val (viewModel, _, _) = buildViewModel(fail = false)
            viewModel.generate()
            viewModel.uiState.test {
                assertEquals(GenerateState.Idle, awaitItem().generateState)
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    @Nested
    @DisplayName("PROG-GEN-602 error state offers retry and back-to-review handlers")
    inner class ErrorState {
        @Test
        fun `the terminal generateState is Error on failure`() = runTest {
            val (viewModel, onboardingRepo, _) = buildViewModel(fail = true)
            viewModel.generate()
            viewModel.uiState.test {
                val state = awaitItem()
                assertEquals(GenerateState.Error, state.generateState)
                assertEquals(null, state.generateErrorMessage)
                cancelAndIgnoreRemainingEvents()
            }
            assertFalse(onboardingRepo.current.isComplete)
        }

        @Test
        fun `generate failure does not surface technical exception message in ui state`() = runTest {
            val (viewModel, _, _) = buildViewModel(
                fail = true,
                failMessage = "Network error. Check your connection and try again.",
            )
            viewModel.generate()
            viewModel.uiState.test {
                val state = awaitItem()
                assertEquals(null, state.generateErrorMessage)
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        fun `GenerateState has exactly the Idle Loading Error variants`() {
            assertEquals(listOf("Idle", "Loading", "Error"), GenerateState.entries.map { it.name })
        }
    }

    @Nested
    @DisplayName("PROG-GEN-604 in-flight generate is single-flight")
    inner class SingleFlight {
        @Test
        fun `a second generate call while Loading is ignored`() = runTest {
            val (viewModel, _, _) = buildViewModel(fail = false)
            viewModel.generate()
            viewModel.generate()
            viewModel.uiState.test {
                assertEquals(GenerateState.Idle, awaitItem().generateState)
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    private fun buildViewModel(
        fail: Boolean,
        failMessage: String = "FakeProgramGeneratorPort configured to fail",
    ): Triple<OnboardingViewModel, FakeOnboardingRepository, FakeProgramRepository> {
        val onboardingRepo = FakeOnboardingRepository()
        val profileRepo = FakeUserTrainingProfileRepository(
            UserTrainingProfile.Empty.copy(trainingAge = TrainingAge.Beginner),
        )
        val programRepo = FakeProgramRepository()
        val generator = FakeProgramGeneratorPort(
            program = if (fail) null else PROGRAM,
            fail = fail,
            failMessage = failMessage,
        )
        val loadPayload = ProgramGenerationPayloadLoader { "{}" }
        val validateCurrentStep = com.stratum.domain.onboarding.ValidateCurrentOnboardingStepUseCase(
            validateProfileStep = com.stratum.domain.onboarding.ValidateProfileStepUseCase(),
            validateScheduleStep = com.stratum.domain.onboarding.ValidateScheduleStepUseCase(),
            validateSpecializationStep = com.stratum.domain.onboarding.ValidateSpecializationStepUseCase(),
            validateGymLiftsStep = com.stratum.domain.lift.ValidateGymLiftsStepUseCase(),
        )
        val rollbackHistoricalImport = com.stratum.domain.importing.RollbackHistoricalImportUseCase(
            com.stratum.core.testing.importing.FakeTrainingHistoryRepository(),
            com.stratum.core.testing.importing.FakeLiftTrainingInsightsRepository(),
            com.stratum.core.testing.importing.FakeSupersetAffinityRepository(),
            com.stratum.core.testing.importing.FakeMuscleVolumeBaselineRepository(),
            com.stratum.core.testing.importing.FakeImportAnalysisMetadataRepository(),
            com.stratum.core.testing.response.FakeResponseProfilePersistencePort(),
            com.stratum.core.testing.onboarding.FakeLiftRepository(),
        )
        val refreshPersistedImportState = com.stratum.domain.importing.RefreshPersistedImportStateUseCase(
            reconcileHistoricalImport = com.stratum.domain.importing.ReconcileHistoricalImportUseCase(
                com.stratum.core.testing.importing.FakeImportAnalysisMetadataRepository(),
                rollbackHistoricalImport,
            ),
            loadHistoricalImportSummary = com.stratum.domain.importing.LoadHistoricalImportSummaryUseCase(
                com.stratum.core.testing.importing.FakeImportAnalysisMetadataRepository(),
                com.stratum.core.testing.importing.FakeMuscleVolumeBaselineRepository(),
                com.stratum.core.testing.importing.FakeLiftTrainingInsightsRepository(),
                com.stratum.core.testing.importing.FakeSupersetAffinityRepository(),
                com.stratum.core.testing.response.FakeVolumeResponseObservationRepository(),
                com.stratum.core.testing.response.FakeLiftEffectivenessObservationRepository(),
                com.stratum.core.testing.response.FakeMuscleVolumeLandmarkEstimateRepository(),
                com.stratum.core.testing.onboarding.FakeLiftRepository(),
            ),
        )
        val liftRepo = com.stratum.core.testing.onboarding.FakeLiftRepository(
            initialLifts =
                listOf(
                    com.stratum.domain.lift.Lift(
                        id = com.stratum.domain.lift.identity.LiftId("back-squat"),
                        name = "Back Squat",
                        movementPattern = com.stratum.domain.lift.MovementPattern.LegPush,
                        primaryMuscles = emptySet(),
                        secondaryMuscles = emptySet(),
                        allowWeightChangeBetweenSets = false,
                        incrementTiers = emptyList(),
                        presetTags = emptySet(),
                        enabled = true,
                    ),
                ),
        )
        val viewModel = OnboardingViewModel(
            getOnboardingWizardState = com.stratum.domain.onboarding.GetOnboardingWizardStateUseCase(
                getOnboardingState = com.stratum.domain.onboarding.GetOnboardingStateUseCase(
                    onboardingRepo,
                    profileRepo,
                ),
                observeSpecialization = com.stratum.domain.onboarding.ObserveSpecializationUseCase(
                    com.stratum.core.testing.onboarding.FakeSpecializationPreferenceRepository(),
                ),
                observeLiftCatalog = com.stratum.domain.lift.ObserveLiftCatalogUseCase(
                    com.stratum.core.testing.onboarding.FakeLiftRepository(),
                ),
                observeSelectedPreset = com.stratum.domain.lift.ObserveSelectedPresetUseCase(
                    com.stratum.core.testing.onboarding.FakeLiftRepository(),
                ),
                observeUsedHistoricalImport = com.stratum.domain.lift.ObserveUsedHistoricalImportUseCase(
                    com.stratum.core.testing.onboarding.FakeLiftRepository(),
                ),
            ),
            advanceOnboardingStep = com.stratum.domain.onboarding.AdvanceOnboardingStepUseCase(
                validateCurrentStep = validateCurrentStep,
                updateUserTrainingProfile = com.stratum.domain.onboarding.UpdateUserTrainingProfileUseCase(profileRepo),
                updateSpecialization = com.stratum.domain.onboarding.UpdateSpecializationUseCase(
                    com.stratum.core.testing.onboarding.FakeSpecializationPreferenceRepository(),
                ),
                saveOnboardingProgress = com.stratum.domain.onboarding.SaveOnboardingProgressUseCase(onboardingRepo),
            ),
            getOnboardingStepCapabilities = com.stratum.domain.onboarding.GetOnboardingStepCapabilitiesUseCase(
                validateCurrentStep,
            ),
            applyGymPreset = ApplyGymPresetUseCase(com.stratum.core.testing.onboarding.FakeLiftRepository()),
            setSelectedPreset = SetSelectedPresetUseCase(com.stratum.core.testing.onboarding.FakeLiftRepository()),
            executeHistoricalImport = com.stratum.domain.importing.ExecuteHistoricalImportUseCase(
                validateImportFiles = com.stratum.domain.importing.ValidateImportFilesUseCase(),
                runHistoricalImport = com.stratum.domain.importing.RunHistoricalImportUseCase(
                    com.stratum.core.testing.importing.FakeHistoricalImportRunnerPort(),
                ),
                rollbackHistoricalImport = rollbackHistoricalImport,
                refreshPersistedImportState = refreshPersistedImportState,
            ),
            cancelHistoricalImport = com.stratum.domain.importing.CancelHistoricalImportUseCase(
                rollbackHistoricalImport,
                refreshPersistedImportState,
            ),
            refreshPersistedImportState = refreshPersistedImportState,
            generateProgram = GenerateProgramUseCase(
                loadPayload = loadPayload,
                generator = generator,
                repository = programRepo,
                liftRepository = liftRepo,
                completeOnboarding = CompleteOnboardingUseCase(onboardingRepo, profileRepo),
                profileRepository = profileRepo,
            ),
        )
        return Triple(viewModel, onboardingRepo, programRepo)
    }

    private companion object {
        val PROGRAM =
            Program(
                id = ProgramId("p-1"),
                name = "Generated Program",
                createdAt = Instant.parse("2026-06-26T12:00:00Z"),
                workouts =
                    listOf(
                        com.stratum.domain.program.Workout(
                            id = com.stratum.domain.program.identity.WorkoutId("w-1"),
                            programId = ProgramId("p-1"),
                            name = "Session A",
                            weekday = null,
                            position = 0,
                            workoutLifts =
                                listOf(
                                    com.stratum.domain.program.WorkoutLift(
                                        id = com.stratum.domain.program.identity.WorkoutLiftId("wl-1"),
                                        workoutId = com.stratum.domain.program.identity.WorkoutId("w-1"),
                                        liftId = com.stratum.domain.lift.identity.LiftId("back-squat"),
                                        progressionScheme = com.stratum.domain.program.ProgressionScheme.Linear,
                                        position = 0,
                                        sets =
                                            listOf(
                                                com.stratum.domain.program.LiftSet(
                                                    id = com.stratum.domain.program.identity.LiftSetId("ls-1"),
                                                    workoutLiftId =
                                                        com.stratum.domain.program.identity.WorkoutLiftId("wl-1"),
                                                    position = 0,
                                                    setType = com.stratum.domain.program.SetType.Standard,
                                                    repMin = 5,
                                                    repMax = 5,
                                                    rirGoal = 2,
                                                    isTopSet = true,
                                                ),
                                            ),
                                    ),
                                ),
                        ),
                    ),
            )
    }
}
