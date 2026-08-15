package com.stratum.feature.onboarding

import com.stratum.core.testing.program.FixedProgramGenerationContextLoader
import com.stratum.core.testing.logging.FakeLoggingPort

import app.cash.turbine.test
import com.stratum.core.testing.MainDispatcherRule
import com.stratum.core.testing.importing.FakeLiftNameResolverPort
import com.stratum.core.testing.importing.FakeProgramTemplateImportPort
import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.core.testing.onboarding.FakeOnboardingRepository
import com.stratum.core.testing.onboarding.FakeUserTrainingProfileRepository
import com.stratum.core.testing.program.FakeProgramRepository
import com.stratum.domain.importing.ImportFileInput
import com.stratum.domain.onboarding.UserTrainingProfile
import com.stratum.domain.program.importing.ImportProgramTemplateUseCase
import com.stratum.domain.program.importing.ProgramTemplateImportPhase
import com.stratum.domain.program.importing.ProgramTemplateJobSnapshot
import com.stratum.domain.program.importing.ProgramTemplateRejectedException
import com.stratum.domain.program.importing.RemoteProgramTemplateJobStatus
import com.stratum.domain.program.importing.ResolveProgramTemplateLiftsUseCase
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * OnboardingViewModel "Import Existing" program flow. Mirrors [OnboardingGenerateViewModelTest]:
 * success completes onboarding + persists a program; failure surfaces a terminal error state
 * without completing; rejection surfaces a distinct Rejected state.
 *
 * Uses the shared [OnboardingViewModelFactory] (the test-scoped factory in this package) to build
 * the ViewModel with a swapped import use case, keeping the wiring DRY.
 */
@ExtendWith(MainDispatcherRule::class)
class OnboardingImportProgramViewModelTest {

    @Test
    fun `import success completes onboarding and persists the program`() = runTest {
        val importPort = FakeProgramTemplateImportPort()
        val (viewModel, onboardingRepo, programRepo) = buildViewModel(importPort, testScheduler)

        viewModel.importExistingProgram(ImportFileInput(byteArrayOf(1, 2, 3), "plan.csv"))
        advanceUntilIdle()

        assertTrue(onboardingRepo.current.isComplete)
        assertTrue(programRepo.current != null, "expected a persisted program")
    }

    @Test
    fun `import failure does not complete onboarding and surfaces Error state`() = runTest {
        val importPort = FakeProgramTemplateImportPort(
            snapshotSequence = listOf(
                ProgramTemplateJobSnapshot(
                    status = RemoteProgramTemplateJobStatus.Failed,
                    progressPhase = ProgramTemplateImportPhase.Failed,
                    progressMessage = null,
                    programTemplateStoragePath = null,
                    errorMessage = "Conversion failed",
                ),
            ),
        )
        val (viewModel, onboardingRepo, _) = buildViewModel(importPort, testScheduler)

        viewModel.importExistingProgram(ImportFileInput(byteArrayOf(1), "bad.csv"))
        advanceUntilIdle()

        assertFalse(onboardingRepo.current.isComplete)
        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state.importProgramState is ImportProgramState.Error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `import rejection surfaces Rejected state`() = runTest {
        val importPort = FakeProgramTemplateImportPort(
            snapshotSequence = listOf(
                ProgramTemplateJobSnapshot(
                    status = RemoteProgramTemplateJobStatus.Rejected,
                    progressPhase = ProgramTemplateImportPhase.Failed,
                    progressMessage = null,
                    programTemplateStoragePath = null,
                    errorMessage = "not a program",
                ),
            ),
        )
        val (viewModel, onboardingRepo, _) = buildViewModel(importPort, testScheduler)

        viewModel.importExistingProgram(ImportFileInput(byteArrayOf(1), "groceries.csv"))
        advanceUntilIdle()

        assertFalse(onboardingRepo.current.isComplete)
        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state.importProgramState is ImportProgramState.Rejected)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a second import call while Loading is ignored`() = runTest {
        // Two-poll sequence: first poll non-terminal, second poll Ready. Lets us observe the
        // Loading state and assert single-flight on re-entry.
        val importPort = FakeProgramTemplateImportPort(
            snapshotSequence = listOf(
                ProgramTemplateJobSnapshot(
                    status = RemoteProgramTemplateJobStatus.Running,
                    progressPhase = ProgramTemplateImportPhase.Parsing,
                    progressMessage = "working",
                    programTemplateStoragePath = null,
                    errorMessage = null,
                ),
                ProgramTemplateJobSnapshot(
                    status = RemoteProgramTemplateJobStatus.Ready,
                    progressPhase = ProgramTemplateImportPhase.Parsing,
                    progressMessage = "done",
                    programTemplateStoragePath = "programImports/t/1/template.json",
                    errorMessage = null,
                ),
            ),
        )
        val (viewModel, _, _) = buildViewModel(importPort, testScheduler)

        viewModel.importExistingProgram(ImportFileInput(byteArrayOf(1), "plan.csv"))
        viewModel.importExistingProgram(ImportFileInput(byteArrayOf(2), "plan2.csv"))
        advanceUntilIdle()

        // Only the first upload is recorded (second call was ignored while Loading).
        assertEquals(1, importPort.uploads.size)
    }

    @Test
    fun `ImportProgramState has Idle Loading Error Rejected variants`() {
        // Mutation-defense: the sealed surface stays closed.
        assertTrue(ImportProgramState.Idle is ImportProgramState.Idle)
        assertTrue(
            ImportProgramState.Loading(ImportProgramPhaseUi.Uploading) is ImportProgramState.Loading,
        )
        assertTrue(ImportProgramState.Error("x") is ImportProgramState.Error)
        assertTrue(ImportProgramState.Rejected("y") is ImportProgramState.Rejected)
    }

    @Test
    fun `ProgramTemplateRejectedException carries the server message`() {
        val ex = ProgramTemplateRejectedException("not a program")
        assertEquals("not a program", ex.message)
    }

    @Test
    fun `showImportProgramError keeps user-facing message for UI`() = runTest {
        val importPort = FakeProgramTemplateImportPort()
        val (viewModel, _, _) = buildViewModel(importPort, testScheduler)

        viewModel.showImportProgramError(userFacingMessage = "That file is too large.")
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            val error = state.importProgramState as ImportProgramState.Error
            assertEquals("That file is too large.", error.message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `showImportProgramError with only logDetail shows Error without message`() = runTest {
        val importPort = FakeProgramTemplateImportPort()
        val (viewModel, _, _) = buildViewModel(importPort, testScheduler)

        viewModel.showImportProgramError(logDetail = "Permission denied")
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            val error = state.importProgramState as ImportProgramState.Error
            assertEquals(null, error.message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun buildViewModel(
        importPort: FakeProgramTemplateImportPort,
        testScheduler: TestCoroutineScheduler,
    ): Triple<OnboardingViewModel, FakeOnboardingRepository, FakeProgramRepository> {
        val onboardingRepo = FakeOnboardingRepository()
        val profileRepo = FakeUserTrainingProfileRepository(UserTrainingProfile.Empty)
        val programRepo = FakeProgramRepository()
        val liftRepo = FakeLiftRepository()
        val completeOnboarding = com.stratum.domain.onboarding.CompleteOnboardingUseCase(onboardingRepo, profileRepo)
        val importUseCase = ImportProgramTemplateUseCase(
            importPort = importPort,
            resolver = ResolveProgramTemplateLiftsUseCase(liftRepo, FakeLiftNameResolverPort()),
            repository = programRepo,
            completeOnboarding = completeOnboarding,
            profileRepository = profileRepo,
            programChangeEventRepository = com.stratum.core.testing.training.NoOpProgramChangeEventRepository(),
            importDispatcher = UnconfinedTestDispatcher(testScheduler),
        )
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
        val weightDisplayPreferences = com.stratum.core.testing.lift.FakeWeightDisplayPreferencesRepository()
        val viewModel = OnboardingViewModel(
            getOnboardingWizardState = com.stratum.domain.onboarding.GetOnboardingWizardStateUseCase(
                getOnboardingState = com.stratum.domain.onboarding.GetOnboardingStateUseCase(onboardingRepo, profileRepo),
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
            applyGymPreset = com.stratum.domain.lift.ApplyGymPresetUseCase(
                com.stratum.core.testing.onboarding.FakeLiftRepository(),
            ),
            setSelectedPreset = com.stratum.domain.lift.SetSelectedPresetUseCase(
                com.stratum.core.testing.onboarding.FakeLiftRepository(),
            ),
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
            generateProgram = com.stratum.domain.program.GenerateProgramUseCase(
                loadContext = FixedProgramGenerationContextLoader(error = { error("not used") }),
                pipeline = com.stratum.core.testing.program.testProgramGenerationPipeline(),
                liftRepository = liftRepo,
            ),
            activateProgram = com.stratum.domain.program.ActivateProgramUseCase(
                programRepository = programRepo,
                programChangeEventRepository = com.stratum.core.testing.training.NoOpProgramChangeEventRepository(),
            ),
            completeOnboarding = completeOnboarding,
            importProgramTemplate = importUseCase,
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
}
