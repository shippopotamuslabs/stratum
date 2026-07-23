package com.stratum.feature.onboarding

import com.stratum.core.testing.MainDispatcherRule
import com.stratum.core.testing.importing.FakeLiftNameResolverPort
import com.stratum.core.testing.importing.FakeProgramTemplateImportPort
import com.stratum.core.testing.logging.FakeLoggingPort
import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.core.testing.onboarding.FakeOnboardingRepository
import com.stratum.core.testing.onboarding.FakeSpecializationPreferenceRepository
import com.stratum.core.testing.onboarding.FakeUserTrainingProfileRepository
import com.stratum.core.testing.program.FakeProgramRepository
import com.stratum.core.testing.program.FixedProgramGenerationContextLoader
import com.stratum.core.testing.program.testProgramGenerationPipeline
import com.stratum.core.testing.training.NoOpProgramChangeEventRepository
import com.stratum.domain.importing.CancelHistoricalImportUseCase
import com.stratum.domain.importing.ExecuteHistoricalImportUseCase
import com.stratum.domain.importing.ImportFileInput
import com.stratum.domain.importing.LoadHistoricalImportSummaryUseCase
import com.stratum.domain.importing.ReconcileHistoricalImportUseCase
import com.stratum.domain.importing.RefreshPersistedImportStateUseCase
import com.stratum.domain.importing.RollbackHistoricalImportUseCase
import com.stratum.domain.importing.RunHistoricalImportUseCase
import com.stratum.domain.importing.ValidateImportFilesUseCase
import com.stratum.domain.lift.ApplyGymPresetUseCase
import com.stratum.domain.lift.ObserveLiftCatalogUseCase
import com.stratum.domain.lift.ObserveSelectedPresetUseCase
import com.stratum.domain.lift.ObserveUsedHistoricalImportUseCase
import com.stratum.domain.lift.SetSelectedPresetUseCase
import com.stratum.domain.lift.ValidateGymLiftsStepUseCase
import com.stratum.domain.onboarding.AdvanceOnboardingStepUseCase
import com.stratum.domain.onboarding.CompleteOnboardingUseCase
import com.stratum.domain.onboarding.GetOnboardingStateUseCase
import com.stratum.domain.onboarding.GetOnboardingStepCapabilitiesUseCase
import com.stratum.domain.onboarding.GetOnboardingWizardStateUseCase
import com.stratum.domain.onboarding.ObserveSpecializationUseCase
import com.stratum.domain.onboarding.SaveOnboardingProgressUseCase
import com.stratum.domain.onboarding.UpdateSpecializationUseCase
import com.stratum.domain.onboarding.UpdateUserTrainingProfileUseCase
import com.stratum.domain.onboarding.ValidateCurrentOnboardingStepUseCase
import com.stratum.domain.onboarding.ValidateMinimumVolumeStepUseCase
import com.stratum.domain.onboarding.ValidateProfileStepUseCase
import com.stratum.domain.onboarding.ValidateScheduleStepUseCase
import com.stratum.domain.onboarding.ValidateSpecializationStepUseCase
import com.stratum.domain.program.ActivateProgramUseCase
import com.stratum.domain.program.GenerateProgramUseCase
import com.stratum.domain.program.importing.ImportProgramTemplateUseCase
import com.stratum.domain.program.importing.ResolveProgramTemplateLiftsUseCase
import com.stratum.core.testing.importing.FakeHistoricalImportRunnerPort
import com.stratum.core.testing.importing.FakeImportAnalysisMetadataRepository
import com.stratum.core.testing.importing.FakeImportJobRepository
import com.stratum.core.testing.importing.FakeLiftTrainingInsightsRepository
import com.stratum.core.testing.importing.FakeMuscleVolumeBaselineRepository
import com.stratum.core.testing.importing.FakeSupersetAffinityRepository
import com.stratum.core.testing.response.FakeLiftEffectivenessObservationRepository
import com.stratum.core.testing.response.FakeMuscleVolumeResponseSnapshotRepository
import com.stratum.core.testing.response.FakeResponseProfilePersistencePort
import com.stratum.core.testing.response.FakeVolumeResponseObservationRepository
import com.stratum.core.testing.training.FakeTrainingLogRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherRule::class)
class ImportExportOnboardingRegressionTest {

    @Test
    @DisplayName("IE-S8-002 Onboarding program import still completes onboarding")
    fun ieS8_002_programImportCompletes() = runTest {
        val importPort = FakeProgramTemplateImportPort()
        val onboardingRepo = FakeOnboardingRepository()
        val programRepo = FakeProgramRepository()
        val profileRepo = FakeUserTrainingProfileRepository()
        val liftRepo = FakeLiftRepository()
        val viewModel =
            buildViewModel(
                importPort = importPort,
                onboardingRepo = onboardingRepo,
                programRepo = programRepo,
                profileRepo = profileRepo,
                liftRepo = liftRepo,
                testScheduler = testScheduler,
            )
        viewModel.importExistingProgram(ImportFileInput(byteArrayOf(1, 2, 3), "plan.csv"))
        advanceUntilIdle()
        assertTrue(onboardingRepo.current.isComplete)
        assertTrue(programRepo.current != null)
    }

    private fun buildViewModel(
        importPort: FakeProgramTemplateImportPort,
        onboardingRepo: FakeOnboardingRepository,
        programRepo: FakeProgramRepository,
        profileRepo: FakeUserTrainingProfileRepository,
        liftRepo: FakeLiftRepository,
        testScheduler: TestCoroutineScheduler,
    ): OnboardingViewModel {
        val completeOnboarding = CompleteOnboardingUseCase(onboardingRepo, profileRepo)
        val importUseCase =
            ImportProgramTemplateUseCase(
                importPort = importPort,
                resolver =
                    ResolveProgramTemplateLiftsUseCase(
                        liftRepository = liftRepo,
                        liftNameResolver = FakeLiftNameResolverPort(),
                    ),
                repository = programRepo,
                completeOnboarding = completeOnboarding,
                profileRepository = profileRepo,
                programChangeEventRepository = NoOpProgramChangeEventRepository(),
                importDispatcher = UnconfinedTestDispatcher(testScheduler),
            )
        val validateCurrentStep =
            ValidateCurrentOnboardingStepUseCase(
                validateProfileStep = ValidateProfileStepUseCase(),
                validateScheduleStep = ValidateScheduleStepUseCase(),
                validateSpecializationStep = ValidateSpecializationStepUseCase(),
                validateMinimumVolumeStep = ValidateMinimumVolumeStepUseCase(),
                validateGymLiftsStep = ValidateGymLiftsStepUseCase(),
            )
        val rollbackHistoricalImport =
            RollbackHistoricalImportUseCase(
                FakeTrainingLogRepository(),
                FakeLiftTrainingInsightsRepository(),
                FakeSupersetAffinityRepository(),
                FakeMuscleVolumeBaselineRepository(),
                FakeImportAnalysisMetadataRepository(),
                FakeResponseProfilePersistencePort(),
                FakeLiftRepository(),
            )
        val refreshPersistedImportState =
            RefreshPersistedImportStateUseCase(
                reconcileHistoricalImport =
                    ReconcileHistoricalImportUseCase(
                        FakeImportAnalysisMetadataRepository(),
                        rollbackHistoricalImport,
                    ),
                loadHistoricalImportSummary =
                    LoadHistoricalImportSummaryUseCase(
                        FakeImportAnalysisMetadataRepository(),
                        FakeMuscleVolumeBaselineRepository(),
                        FakeLiftTrainingInsightsRepository(),
                        FakeSupersetAffinityRepository(),
                        FakeVolumeResponseObservationRepository(),
                        FakeLiftEffectivenessObservationRepository(),
                        FakeMuscleVolumeResponseSnapshotRepository(),
                        FakeLiftRepository(),
                    ),
            )
        val weightDisplayPreferences = com.stratum.core.testing.lift.FakeWeightDisplayPreferencesRepository()
        return OnboardingViewModel(
            getOnboardingWizardState =
                GetOnboardingWizardStateUseCase(
                    getOnboardingState = GetOnboardingStateUseCase(onboardingRepo, profileRepo),
                    observeSpecialization =
                        ObserveSpecializationUseCase(FakeSpecializationPreferenceRepository()),
                    observeLiftCatalog = ObserveLiftCatalogUseCase(liftRepo),
                    observeSelectedPreset = ObserveSelectedPresetUseCase(liftRepo),
                    observeUsedHistoricalImport = ObserveUsedHistoricalImportUseCase(liftRepo),
                ),
            advanceOnboardingStep =
                AdvanceOnboardingStepUseCase(
                    validateCurrentStep = validateCurrentStep,
                    updateUserTrainingProfile = UpdateUserTrainingProfileUseCase(profileRepo),
                    updateSpecialization =
                        UpdateSpecializationUseCase(FakeSpecializationPreferenceRepository()),
                    saveOnboardingProgress = SaveOnboardingProgressUseCase(onboardingRepo),
                    loggingPort = FakeLoggingPort(),
                ),
            getOnboardingStepCapabilities = GetOnboardingStepCapabilitiesUseCase(validateCurrentStep),
            applyGymPreset = ApplyGymPresetUseCase(liftRepo),
            setSelectedPreset = SetSelectedPresetUseCase(liftRepo),
            executeHistoricalImport =
                ExecuteHistoricalImportUseCase(
                    validateImportFiles = ValidateImportFilesUseCase(),
                    runHistoricalImport =
                        RunHistoricalImportUseCase(FakeHistoricalImportRunnerPort()),
                    refreshPersistedImportState = refreshPersistedImportState,
                ),
            cancelHistoricalImport =
                CancelHistoricalImportUseCase(
                    rollbackHistoricalImport,
                    refreshPersistedImportState,
                    FakeImportJobRepository(),
                ),
            refreshPersistedImportState = refreshPersistedImportState,
            updateUserTrainingProfile = UpdateUserTrainingProfileUseCase(profileRepo),
            updateSpecialization =
                UpdateSpecializationUseCase(FakeSpecializationPreferenceRepository()),
            generateProgram =
                GenerateProgramUseCase(
                    loadContext = FixedProgramGenerationContextLoader(error = { error("not used") }),
                    pipeline = testProgramGenerationPipeline(),
                    liftRepository = liftRepo,
                ),
            activateProgram =
                ActivateProgramUseCase(
                    programRepository = programRepo,
                    programChangeEventRepository = NoOpProgramChangeEventRepository(),
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
    }
}
