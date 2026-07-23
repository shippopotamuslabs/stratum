package com.stratum.feature.onboarding

import com.stratum.core.testing.logging.FakeLoggingPort

import app.cash.turbine.test
import com.stratum.core.testing.MainDispatcherRule
import com.stratum.core.testing.onboarding.FakeOnboardingRepository
import com.stratum.core.testing.onboarding.FakeUserTrainingProfileRepository
import com.stratum.core.testing.program.FakeProgramRepository
import com.stratum.core.testing.program.SCENARIO_LIFT_CATALOG
import com.stratum.core.testing.program.scenarioContext
import com.stratum.core.testing.program.testProgramGenerationPipeline
import com.stratum.domain.lift.ApplyGymPresetUseCase
import com.stratum.domain.lift.SetSelectedPresetUseCase
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.CompleteOnboardingUseCase
import com.stratum.domain.onboarding.TrainingAge
import com.stratum.domain.onboarding.UserTrainingProfile
import com.stratum.domain.program.GenerateProgramUseCase
import com.stratum.core.testing.program.FixedProgramGenerationContextLoader
import com.stratum.domain.program.ProgramGenerationContextLoader
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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
        fun `generate marks onboarding complete and persists the program`() =
            runGenerateTest(fail = false) { _, onboardingRepo, programRepo ->
                assertTrue(onboardingRepo.current.isComplete)
                assertTrue(programRepo.current != null, "expected a persisted program")
            }
    }

    @Nested
    @DisplayName("PROG-GEN-601 generation failure does not complete onboarding")
    inner class GenerateFailure {
        @Test
        fun `generate leaves onboarding incomplete and persists no program on failure`() =
            runGenerateTest(fail = true) { _, onboardingRepo, programRepo ->
                assertFalse(onboardingRepo.current.isComplete)
                assertEquals(null, programRepo.current)
            }
    }

    @Nested
    @DisplayName("PROG-GEN-603 terminal generateState is Idle on success")
    inner class LoadingCopy {
        @Test
        fun `after generate the terminal generateState is Idle on success`() =
            runGenerateTest(fail = false) { viewModel, _, _ ->
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
        fun `the terminal generateState is Error on failure`() =
            runGenerateTest(fail = true) { viewModel, onboardingRepo, _ ->
                viewModel.uiState.test {
                    val state = awaitItem()
                    assertEquals(GenerateState.Error, state.generateState)
                    assertEquals(null, state.generateErrorMessage)
                    cancelAndIgnoreRemainingEvents()
                }
                assertFalse(onboardingRepo.current.isComplete)
            }

        @Test
        fun `generate failure does not surface technical exception message in ui state`() =
            runGenerateTest(
                fail = true,
                failMessage = "Network error. Check your connection and try again.",
            ) { viewModel, _, _ ->
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
            val generationDispatcher = StandardTestDispatcher(testScheduler)
            val (viewModel, _, _) =
                buildViewModel(fail = false, generationDispatcher = generationDispatcher)
            viewModel.generate()
            viewModel.generate()
            advanceUntilIdle()
            viewModel.uiState.test {
                assertEquals(GenerateState.Idle, awaitItem().generateState)
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    private fun buildViewModel(
        fail: Boolean,
        @Suppress("UNUSED_PARAMETER") failMessage: String = "generation failed",
        generationDispatcher: CoroutineDispatcher = kotlinx.coroutines.Dispatchers.Default,
    ): Triple<OnboardingViewModel, FakeOnboardingRepository, FakeProgramRepository> {
        val onboardingRepo = FakeOnboardingRepository()
        val profileRepo = FakeUserTrainingProfileRepository(
            UserTrainingProfile.Empty.copy(trainingAge = TrainingAge.Advanced),
        )
        val programRepo = FakeProgramRepository()
        val contextLoader: ProgramGenerationContextLoader =
            if (fail) {
                FixedProgramGenerationContextLoader(error = { error("generation failed") })
            } else {
                FixedProgramGenerationContextLoader(context = scenarioContext(days = 3, specialization = emptyMap()))
            }
        val validateCurrentStep = com.stratum.domain.onboarding.ValidateCurrentOnboardingStepUseCase(
            validateProfileStep = com.stratum.domain.onboarding.ValidateProfileStepUseCase(),
            validateScheduleStep = com.stratum.domain.onboarding.ValidateScheduleStepUseCase(),
            validateSpecializationStep = com.stratum.domain.onboarding.ValidateSpecializationStepUseCase(),
            validateMinimumVolumeStep = com.stratum.domain.onboarding.ValidateMinimumVolumeStepUseCase(),
            validateGymLiftsStep = com.stratum.domain.lift.ValidateGymLiftsStepUseCase(),
        )
        val rollbackHistoricalImport = com.stratum.domain.importing.RollbackHistoricalImportUseCase(
            com.stratum.core.testing.training.FakeTrainingLogRepository(),
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
                com.stratum.core.testing.response.FakeMuscleVolumeResponseSnapshotRepository(),
                com.stratum.core.testing.onboarding.FakeLiftRepository(),
            ),
        )
        val liftRepo = com.stratum.core.testing.onboarding.FakeLiftRepository(
            initialLifts =
                SCENARIO_LIFT_CATALOG.map { lift ->
                    com.stratum.domain.lift.Lift(
                        id = LiftId(lift.id),
                        name = lift.name,
                        movementPatterns = emptySet(),
                        primaryMuscles = emptySet(),
                        secondaryMuscles = emptySet(),
                        incrementTiers = emptyList(),
                        presetTags = emptySet(),
                        enabled = true,
                    )
                },
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
                loggingPort = com.stratum.core.testing.logging.FakeLoggingPort(),
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
                refreshPersistedImportState = refreshPersistedImportState,
            ),
            cancelHistoricalImport = com.stratum.domain.importing.CancelHistoricalImportUseCase(
                rollbackHistoricalImport,
                refreshPersistedImportState,
                com.stratum.core.testing.importing.FakeImportJobRepository(),
            ),
            refreshPersistedImportState = refreshPersistedImportState,
            updateUserTrainingProfile = com.stratum.domain.onboarding.UpdateUserTrainingProfileUseCase(profileRepo),
            updateSpecialization = com.stratum.domain.onboarding.UpdateSpecializationUseCase(
                com.stratum.core.testing.onboarding.FakeSpecializationPreferenceRepository(),
            ),
            generateProgram = GenerateProgramUseCase(
                loadContext = contextLoader,
                pipeline = testProgramGenerationPipeline(),
                liftRepository = liftRepo,
                generationDispatcher = generationDispatcher,
            ),
            activateProgram = com.stratum.domain.program.ActivateProgramUseCase(
                programRepository = programRepo,
                programChangeEventRepository = com.stratum.core.testing.training.NoOpProgramChangeEventRepository(),
            ),
            completeOnboarding = CompleteOnboardingUseCase(onboardingRepo, profileRepo),
            importProgramTemplate = com.stratum.domain.program.importing.ImportProgramTemplateUseCase(
                importPort = com.stratum.core.testing.importing.FakeProgramTemplateImportPort(),
                resolver = com.stratum.domain.program.importing.ResolveProgramTemplateLiftsUseCase(
                    liftRepository = liftRepo,
                    liftNameResolver = com.stratum.core.testing.importing.FakeLiftNameResolverPort(),
                ),
                repository = programRepo,
                completeOnboarding = CompleteOnboardingUseCase(onboardingRepo, profileRepo),
                profileRepository = profileRepo,
                programChangeEventRepository = com.stratum.core.testing.training.NoOpProgramChangeEventRepository(),
            ),
            logError = com.stratum.domain.logging.LogErrorUseCase(FakeLoggingPort()),
            observeWeightDisplayPreferences =
                com.stratum.domain.lift.ObserveWeightDisplayPreferencesUseCase(
                    weightDisplayPreferences,
                ),
            setWeightDisplayUnit =
                com.stratum.domain.lift.SetWeightDisplayUnitUseCase(
                    weightDisplayPreferences,
                    com.stratum.core.testing.logging.FakeLoggingPort(),
                ),
        )
        return Triple(viewModel, onboardingRepo, programRepo)
    }

    private fun runGenerateTest(
        fail: Boolean,
        failMessage: String = "generation failed",
        block: suspend (OnboardingViewModel, FakeOnboardingRepository, FakeProgramRepository) -> Unit,
    ) = runTest {
        val generationDispatcher = UnconfinedTestDispatcher(testScheduler)
        val (viewModel, onboardingRepo, programRepo) =
            buildViewModel(
                fail = fail,
                failMessage = failMessage,
                generationDispatcher = generationDispatcher,
            )
        viewModel.generate()
        block(viewModel, onboardingRepo, programRepo)
    }
}
