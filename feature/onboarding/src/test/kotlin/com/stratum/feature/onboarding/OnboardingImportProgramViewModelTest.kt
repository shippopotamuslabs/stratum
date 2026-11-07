package com.stratum.feature.onboarding

import com.stratum.core.infrastructure.telemetry.ProgramGenerationTelemetryLogger
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
import com.stratum.application.program.importing.ImportProgramTemplateUseCase
import com.stratum.domain.program.importing.ProgramTemplateImportPhase
import com.stratum.domain.program.importing.ProgramTemplateJobSnapshot
import com.stratum.application.program.importing.ProgramTemplateRejectedException
import com.stratum.domain.program.importing.RemoteProgramTemplateJobStatus
import com.stratum.application.program.importing.ResolveProgramTemplateLiftsUseCase
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

    /** Delegates to [inner]; every save throws the exact [failure] instance (persist-failure driver). */
    private class FailingSaveProgramRepository(
        private val inner: FakeProgramRepository,
        private val failure: Throwable,
    ) : com.stratum.application.program.ProgramRepository by inner {
        override suspend fun saveProgram(program: com.stratum.domain.program.Program) {
            throw failure
        }

        override suspend fun saveToLibrary(program: com.stratum.domain.program.Program) {
            throw failure
        }
    }

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
    fun `embedding failure on import success drains exactly once at the onboarding boundary`() = runTest {
        // Fix 7 (the missing 8b onboarding pin): the fixture resolver gains an
        // embedding-failure mode (mirroring the settings fixture's failing
        // resolver); the degraded-but-successful import still completes
        // onboarding, and its embedding diagnostic drains exactly once here.
        val failure = IllegalStateException("embedding down")
        val diagnosticLogging = FakeLoggingPort()
        val importPort = FakeProgramTemplateImportPort()
        val (viewModel, onboardingRepo, programRepo) = buildViewModel(
            importPort,
            testScheduler,
            liftNameResolver = FakeLiftNameResolverPort(embeddingFailure = failure),
            diagnosticLogging = diagnosticLogging,
        )

        viewModel.importExistingProgram(ImportFileInput(byteArrayOf(1, 2, 3), "plan.csv"))
        advanceUntilIdle()

        assertTrue(onboardingRepo.current.isComplete)
        assertTrue(programRepo.current != null, "expected a persisted program")
        assertEquals(1, diagnosticLogging.errors.size)
        assertEquals("ProgramTemplateImport", diagnosticLogging.errors.single().tag)
        assertEquals(
            "Import lift name embedding failed — gray-zone matches may be unresolved. " +
                "Error: embedding down",
            diagnosticLogging.errors.single().message,
        )
        org.junit.jupiter.api.Assertions.assertSame(
            failure,
            diagnosticLogging.errors.single().throwable,
        )
    }

    @Test
    fun `embedding failure on operational failure drains exactly once at the onboarding boundary`() = runTest {
        // Thermos round-2 edge: persist succeeds with a degraded resolve, then
        // the use case's completeOnboarding step throws, so the outcome is
        // OperationalFailed carrying BOTH the primary operational diagnostic
        // and the embedding diagnostic. Each drains exactly once here, and
        // OperationalFailed has no importFailureLogThrowable record, so the
        // drain holds exactly those two records with distinct causes.
        val embeddingFailure = IllegalStateException("embedding down")
        val onboardingFailure = IllegalStateException("complete onboarding down")
        val diagnosticLogging = FakeLoggingPort()
        val importPort = FakeProgramTemplateImportPort()
        val (viewModel, onboardingRepo, programRepo) = buildViewModel(
            importPort,
            testScheduler,
            liftNameResolver = FakeLiftNameResolverPort(embeddingFailure = embeddingFailure),
            diagnosticLogging = diagnosticLogging,
            completeOnboardingFailure = onboardingFailure,
        )

        viewModel.importExistingProgram(ImportFileInput(byteArrayOf(1, 2, 3), "plan.csv"))
        advanceUntilIdle()

        assertFalse(onboardingRepo.current.isComplete)
        assertTrue(programRepo.current != null, "expected the persisted program to remain")
        assertEquals(2, diagnosticLogging.errors.size)
        // The primary record's cause crossed the use case's withContext as a
        // throw, so it is matched by message (coroutine machinery may
        // normalize a thrown instance); the embedding cause is carried as
        // data and is identity-matched below.
        assertEquals(
            1,
            diagnosticLogging.errors.count { it.throwable?.message == onboardingFailure.message },
        )
        val embeddingRecords = diagnosticLogging.errors.filter { it.throwable === embeddingFailure }
        assertEquals(1, embeddingRecords.size)
        assertEquals("ProgramTemplateImport", embeddingRecords.single().tag)
        assertEquals(
            "Import lift name embedding failed — gray-zone matches may be unresolved. " +
                "Error: embedding down",
            embeddingRecords.single().message,
        )
    }

    @Test
    fun `embedding failure on persist failure drains exactly once at the onboarding boundary`() = runTest {
        // Round-2 coverage pin (production code already correct since Fix 7):
        // a degraded resolve followed by a persist failure yields PersistFailed
        // carrying the embedding diagnostic. It drains exactly once here; the
        // PRIMARY persist failure is recorded separately via
        // importFailureLogThrowable as a distinct record (constructed
        // IllegalStateException carrying the persist message, not the
        // embedding cause).
        val embeddingFailure = IllegalStateException("embedding down")
        val saveFailure = IllegalStateException("save failed")
        val diagnosticLogging = FakeLoggingPort()
        val importPort = FakeProgramTemplateImportPort()
        val (viewModel, onboardingRepo, programRepo) = buildViewModel(
            importPort,
            testScheduler,
            liftNameResolver = FakeLiftNameResolverPort(embeddingFailure = embeddingFailure),
            diagnosticLogging = diagnosticLogging,
            programSaveFailure = saveFailure,
        )

        viewModel.importExistingProgram(ImportFileInput(byteArrayOf(1, 2, 3), "plan.csv"))
        advanceUntilIdle()

        assertFalse(onboardingRepo.current.isComplete)
        assertTrue(programRepo.current == null, "expected no persisted program")
        assertEquals(2, diagnosticLogging.errors.size)
        val embeddingRecords = diagnosticLogging.errors.filter { it.throwable === embeddingFailure }
        assertEquals(1, embeddingRecords.size)
        assertEquals("ProgramTemplateImport", embeddingRecords.single().tag)
        assertEquals(
            "Import lift name embedding failed — gray-zone matches may be unresolved. " +
                "Error: embedding down",
            embeddingRecords.single().message,
        )
        val primaryRecords = diagnosticLogging.errors.filter { it.throwable !== embeddingFailure }
        assertEquals(1, primaryRecords.size)
        assertEquals("Program import failed", primaryRecords.single().message)
        assertEquals("save failed", primaryRecords.single().throwable?.message)
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
        val analytics = com.stratum.core.testing.telemetry.FakeAnalyticsPort()
        val (viewModel, onboardingRepo, _) = buildViewModel(importPort, testScheduler, analytics)

        viewModel.importExistingProgram(ImportFileInput(byteArrayOf(1), "bad.csv"))
        advanceUntilIdle()

        assertFalse(onboardingRepo.current.isComplete)
        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state.importProgramState is ImportProgramState.Error)
            cancelAndIgnoreRemainingEvents()
        }
        val event = analytics.events.single()
        assertEquals(
            com.stratum.core.infrastructure.telemetry.USER_VISIBLE_ERROR_EVENT,
            event.name,
        )
        assertEquals("onboarding", event.params[com.stratum.core.infrastructure.telemetry.PARAM_SCREEN])
        assertEquals("import_program", event.params[com.stratum.core.infrastructure.telemetry.PARAM_ACTION])
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
        analytics: com.stratum.core.testing.telemetry.FakeAnalyticsPort =
            com.stratum.core.testing.telemetry.FakeAnalyticsPort(),
        liftNameResolver: FakeLiftNameResolverPort = FakeLiftNameResolverPort(),
        diagnosticLogging: FakeLoggingPort = FakeLoggingPort(),
        completeOnboardingFailure: Throwable? = null,
        programSaveFailure: Throwable? = null,
    ): Triple<OnboardingViewModel, FakeOnboardingRepository, FakeProgramRepository> {
        val onboardingRepo = FakeOnboardingRepository()
        val profileRepo = FakeUserTrainingProfileRepository(UserTrainingProfile.Empty)
        val innerProgramRepo = FakeProgramRepository()
        val programRepo: com.stratum.application.program.ProgramRepository =
            if (programSaveFailure != null) {
                FailingSaveProgramRepository(innerProgramRepo, programSaveFailure)
            } else {
                innerProgramRepo
            }
        val liftRepo = FakeLiftRepository()
        val completeOnboarding = com.stratum.application.onboarding.CompleteOnboardingUseCase(onboardingRepo, profileRepo)
        // The import use case's own completeOnboarding step: optionally a
        // throwing variant driving the post-persist operational-failure path.
        val importCompleteOnboarding =
            if (completeOnboardingFailure != null) {
                object : com.stratum.application.onboarding.CompleteOnboardingUseCase(
                    onboardingRepo,
                    profileRepo,
                ) {
                    override suspend fun invoke(finalProfile: UserTrainingProfile) {
                        throw completeOnboardingFailure
                    }
                }
            } else {
                completeOnboarding
            }
        val importUseCase = ImportProgramTemplateUseCase(
            importPort = importPort,
            resolver = ResolveProgramTemplateLiftsUseCase(liftRepo, liftNameResolver),
            repository = programRepo,
            completeOnboarding = importCompleteOnboarding,
            profileRepository = profileRepo,
            programChangeEventRepository = com.stratum.core.testing.training.NoOpProgramChangeEventRepository(),
            importDispatcher = UnconfinedTestDispatcher(testScheduler),
        )
        val validateCurrentStep = com.stratum.application.onboarding.ValidateCurrentOnboardingStepUseCase(
            validateProfileStep = com.stratum.application.onboarding.ValidateProfileStepUseCase(),
            validateScheduleStep = com.stratum.application.onboarding.ValidateScheduleStepUseCase(),
            validateSpecializationStep = com.stratum.application.onboarding.ValidateSpecializationStepUseCase(),
            validateMinimumVolumeStep = com.stratum.application.onboarding.ValidateMinimumVolumeStepUseCase(),
            validateGymLiftsStep = com.stratum.application.lift.ValidateGymLiftsStepUseCase(),
        )
        val rollbackHistoricalImport = com.stratum.application.importing.RollbackHistoricalImportUseCase(
            com.stratum.core.testing.training.FakeTrainingLogRepository(),
            com.stratum.core.testing.importing.FakeLiftTrainingInsightsRepository(),
            com.stratum.core.testing.importing.FakeSupersetAffinityRepository(),
            com.stratum.core.testing.importing.FakeMuscleVolumeBaselineRepository(),
            com.stratum.core.testing.importing.FakeImportAnalysisMetadataRepository(),
            com.stratum.core.testing.response.FakeResponseProfilePersistencePort(),
            com.stratum.core.testing.onboarding.FakeLiftRepository(),
        )
        val refreshPersistedImportState = com.stratum.application.importing.RefreshPersistedImportStateUseCase(
            reconcileHistoricalImport = com.stratum.application.importing.ReconcileHistoricalImportUseCase(
                com.stratum.core.testing.importing.FakeImportAnalysisMetadataRepository(),
                rollbackHistoricalImport,
            ),
            loadHistoricalImportSummary = com.stratum.application.importing.LoadHistoricalImportSummaryUseCase(
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
            getOnboardingWizardState = com.stratum.application.onboarding.GetOnboardingWizardStateUseCase(
                getOnboardingState = com.stratum.application.onboarding.GetOnboardingStateUseCase(onboardingRepo, profileRepo),
                observeSpecialization = com.stratum.application.onboarding.ObserveSpecializationUseCase(
                    com.stratum.core.testing.onboarding.FakeSpecializationPreferenceRepository(),
                ),
                observeLiftCatalog = com.stratum.application.lift.ObserveLiftCatalogUseCase(
                    com.stratum.core.testing.onboarding.FakeLiftRepository(),
                ),
                observeSelectedPreset = com.stratum.application.lift.ObserveSelectedPresetUseCase(
                    com.stratum.core.testing.onboarding.FakeLiftRepository(),
                ),
                observeUsedHistoricalImport = com.stratum.application.lift.ObserveUsedHistoricalImportUseCase(
                    com.stratum.core.testing.onboarding.FakeLiftRepository(),
                ),
            ),
            advanceOnboardingStep = com.stratum.application.onboarding.AdvanceOnboardingStepUseCase(
                validateCurrentStep = validateCurrentStep,
                updateUserTrainingProfile = com.stratum.application.onboarding.UpdateUserTrainingProfileUseCase(profileRepo),
                updateSpecialization = com.stratum.application.onboarding.UpdateSpecializationUseCase(
                    com.stratum.core.testing.onboarding.FakeSpecializationPreferenceRepository(),
                ),
                saveOnboardingProgress = com.stratum.application.onboarding.SaveOnboardingProgressUseCase(onboardingRepo),
            ),
            getOnboardingStepCapabilities = com.stratum.application.onboarding.GetOnboardingStepCapabilitiesUseCase(
                validateCurrentStep,
            ),
            applyGymPreset = com.stratum.application.lift.ApplyGymPresetUseCase(
                com.stratum.core.testing.onboarding.FakeLiftRepository(),
            ),
            setSelectedPreset = com.stratum.application.lift.SetSelectedPresetUseCase(
                com.stratum.core.testing.onboarding.FakeLiftRepository(),
            ),
            executeHistoricalImport = com.stratum.application.importing.ExecuteHistoricalImportUseCase(
                validateImportFiles = com.stratum.application.importing.ValidateImportFilesUseCase(),
                runHistoricalImport = com.stratum.application.importing.RunHistoricalImportUseCase(
                    com.stratum.core.testing.importing.FakeHistoricalImportRunnerPort(),
                ),
                refreshPersistedImportState = refreshPersistedImportState,
            ),
            cancelHistoricalImport = com.stratum.application.importing.CancelHistoricalImportUseCase(
                rollbackHistoricalImport,
                refreshPersistedImportState,
                com.stratum.core.testing.importing.FakeImportJobRepository(),
            ),
            refreshPersistedImportState = refreshPersistedImportState,
            updateUserTrainingProfile = com.stratum.application.onboarding.UpdateUserTrainingProfileUseCase(profileRepo),
            updateSpecialization = com.stratum.application.onboarding.UpdateSpecializationUseCase(
                com.stratum.core.testing.onboarding.FakeSpecializationPreferenceRepository(),
            ),
            generateProgram = com.stratum.application.program.GenerateProgramUseCase(
                loadContext = FixedProgramGenerationContextLoader(error = { error("not used") }),
                pipeline = com.stratum.core.testing.program.testProgramGenerationPipeline(),
                liftRepository = liftRepo,
            ),
            activateProgram = com.stratum.application.program.ActivateProgramUseCase(
                programRepository = programRepo,
                programChangeEventRepository = com.stratum.core.testing.training.NoOpProgramChangeEventRepository(),
            ),
            completeOnboarding = completeOnboarding,
            importProgramTemplate = importUseCase,
            diagnosticDrain = DiagnosticDrain(diagnosticLogging),
            generationLogger = ProgramGenerationTelemetryLogger(FakeLoggingPort()),
            logAnalytics =
                AnalyticsRecorder(
                    analytics,
                ),
            observeWeightDisplayPreferences =
                com.stratum.application.lift.ObserveWeightDisplayPreferencesUseCase(
                    weightDisplayPreferences,
                ),
            setWeightDisplayUnit =
                com.stratum.application.lift.SetWeightDisplayUnitUseCase(
                    weightDisplayPreferences,
                ),
        )
        return Triple(viewModel, onboardingRepo, innerProgramRepo)
    }
}
