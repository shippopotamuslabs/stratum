package com.stratum.feature.onboarding

import com.stratum.core.testing.program.FixedProgramGenerationContextLoader
import com.stratum.core.testing.logging.FakeLoggingPort

import com.stratum.core.testing.importing.FakeHistoricalImportRunnerPort
import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.core.testing.response.FakeResponseProfilePersistencePort
import com.stratum.core.testing.onboarding.FakeOnboardingRepository
import com.stratum.core.testing.onboarding.FakeSpecializationPreferenceRepository
import com.stratum.core.testing.onboarding.FakeUserTrainingProfileRepository
import com.stratum.domain.lift.ApplyGymPresetUseCase
import com.stratum.domain.lift.GymPreset
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.ObserveLiftCatalogUseCase
import com.stratum.domain.lift.ObserveSelectedPresetUseCase
import com.stratum.domain.importing.CancelHistoricalImportUseCase
import com.stratum.domain.importing.ExecuteHistoricalImportUseCase
import com.stratum.domain.onboarding.GetOnboardingStepCapabilitiesUseCase
import com.stratum.domain.importing.HistoricalImportEvent
import com.stratum.domain.importing.ImportProgress
import com.stratum.domain.importing.ImportProgressPhase
import com.stratum.domain.importing.LoadHistoricalImportSummaryUseCase
import com.stratum.domain.importing.ReconcileHistoricalImportUseCase
import com.stratum.core.testing.response.FakeLiftEffectivenessObservationRepository
import com.stratum.core.testing.response.FakeVolumeResponseObservationRepository
import com.stratum.domain.importing.RefreshPersistedImportStateUseCase
import com.stratum.domain.importing.RollbackHistoricalImportUseCase
import com.stratum.domain.importing.RunHistoricalImportUseCase
import com.stratum.core.testing.training.FakeTrainingLogRepository
import com.stratum.core.testing.training.WorkoutLoggingFixtures
import com.stratum.domain.onboarding.AdvanceOnboardingStepUseCase
import com.stratum.domain.onboarding.CompleteOnboardingUseCase
import com.stratum.domain.onboarding.GetOnboardingStateUseCase
import com.stratum.domain.onboarding.GetOnboardingWizardStateUseCase
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.onboarding.ObserveSpecializationUseCase
import com.stratum.domain.onboarding.OnboardingProgress
import com.stratum.domain.onboarding.OnboardingStep
import com.stratum.domain.onboarding.SaveOnboardingProgressUseCase
import com.stratum.domain.onboarding.SpecializationPreference
import com.stratum.domain.onboarding.UpdateSpecializationUseCase
import com.stratum.domain.onboarding.UpdateUserTrainingProfileUseCase
import com.stratum.domain.onboarding.ValidateCurrentOnboardingStepUseCase
import kotlinx.coroutines.runBlocking
import java.time.Instant
import com.stratum.core.testing.program.scenarioContext
import com.stratum.domain.onboarding.ValidateMinimumVolumeStepUseCase
import com.stratum.domain.onboarding.ValidateProfileStepUseCase
import com.stratum.domain.onboarding.ValidateScheduleStepUseCase
import com.stratum.domain.onboarding.ValidateSpecializationStepUseCase
import com.stratum.domain.importing.ImportAnalysisMetadata
import com.stratum.domain.importing.ImportAnalysisMetadataRepository
import com.stratum.domain.importing.LiftTrainingInsights
import com.stratum.domain.importing.LiftTrainingInsightsRepository
import com.stratum.domain.importing.MuscleVolumeBaseline
import com.stratum.domain.importing.MuscleVolumeBaselineRepository
import com.stratum.domain.importing.SupersetAffinity
import com.stratum.domain.importing.SupersetAffinityRepository
import com.stratum.core.testing.program.testProgramGenerationPipeline
import com.stratum.core.testing.program.FakeProgramRepository
import com.stratum.domain.lift.ObserveUsedHistoricalImportUseCase
import com.stratum.domain.lift.SetSelectedPresetUseCase
import com.stratum.domain.lift.ValidateGymLiftsStepUseCase
import com.stratum.domain.lift.identity.LiftId

internal object OnboardingViewModelTestFixtures {

    val fakeRepository = FakeOnboardingRepository()
    val fakeProfileRepository = FakeUserTrainingProfileRepository()
    val fakeSpecializationRepository = FakeSpecializationPreferenceRepository()
    val fakeLiftRepository = FakeLiftRepository()

    val fakeHistoricalImport = FakeHistoricalImportRunnerPort()
    private val fakeTrainingLogRepository = FakeTrainingLogRepository()
    private val fakeMetadataRepository = FakeImportAnalysisMetadataRepository()
    private val fakeLiftInsightsRepository = FakeLiftInsightsRepository()
    private val fakeSupersetRepository = FakeSupersetRepository()
    private val fakeVolumeRepository = FakeVolumeRepository()
    private val fakeVolumeResponseRepository = FakeVolumeResponseObservationRepository()
    private val fakeEffectivenessRepository = FakeLiftEffectivenessObservationRepository()
    val fakeProgramRepository = FakeProgramRepository()

    fun resetDefaults() {
        fakeRepository.seed(
            OnboardingProgress(OnboardingStep.Profile, startedAt = 0L, completedAt = null),
        )
        fakeProfileRepository.seed(null)
        fakeSpecializationRepository.seed(SpecializationPreference.Empty)
        fakeLiftRepository.seedLifts(emptyList())
        fakeLiftRepository.seedPreset(null)
        fakeLiftRepository.seedUsedHistoricalImport(false)
        runBlocking { fakeTrainingLogRepository.replaceImportHistory(emptyList(), emptyList()) }
        fakeMetadataRepository.reset()
        fakeVolumeRepository.reset()
        fakeVolumeResponseRepository.seed(emptyList())
        fakeEffectivenessRepository.seed(emptyList())
        fakeHistoricalImport.setEvents(
            listOf(
                HistoricalImportEvent.Progress(
                    ImportProgress(ImportProgressPhase.Uploading),
                ),
                HistoricalImportEvent.Completed(
                    com.stratum.domain.importing.ExtractTrainingInsightsSummary(
                        liftsWithInsights = 1,
                        supersetPairs = 0,
                        musclesWithBaselines = 1,
                    ),
                ),
            ),
        )
        fakeHistoricalImport.resetInvocations()
    }

    fun seedOrphanImportHistory() {
        runBlocking {
            fakeTrainingLogRepository.replaceImportHistory(
                sessions =
                    listOf(
                        WorkoutLoggingFixtures.importTrainingSession(
                            id = "s1",
                            startedAt = Instant.parse("2025-01-01T00:00:00Z"),
                            endedAt = null,
                            sourceApp = null,
                        ),
                    ),
                sets =
                    listOf(
                        WorkoutLoggingFixtures.importLoggedSet(
                            id = "set1",
                            sessionId = "s1",
                            exerciseNameRaw = "Squat",
                            liftId = null,
                            completedAt = Instant.parse("2025-01-01T01:00:00Z"),
                        ),
                    ),
            )
        }
    }

    fun buildViewModel(): OnboardingViewModel {
        val validateCurrentStep = ValidateCurrentOnboardingStepUseCase(
            validateProfileStep = ValidateProfileStepUseCase(),
            validateScheduleStep = ValidateScheduleStepUseCase(),
            validateSpecializationStep = ValidateSpecializationStepUseCase(),
            validateMinimumVolumeStep = ValidateMinimumVolumeStepUseCase(),
            validateGymLiftsStep = ValidateGymLiftsStepUseCase(),
        )
        val rollbackHistoricalImport = buildRollbackHistoricalImport()
        val refreshPersistedImportState = RefreshPersistedImportStateUseCase(
            reconcileHistoricalImport = ReconcileHistoricalImportUseCase(
                fakeMetadataRepository,
                rollbackHistoricalImport,
            ),
            loadHistoricalImportSummary = LoadHistoricalImportSummaryUseCase(
                fakeMetadataRepository,
                fakeVolumeRepository,
                fakeLiftInsightsRepository,
                fakeSupersetRepository,
                fakeVolumeResponseRepository,
                fakeEffectivenessRepository,
                com.stratum.core.testing.response.FakeMuscleVolumeResponseSnapshotRepository(),
                fakeLiftRepository,
            ),
        )
        return OnboardingViewModel(
            getOnboardingWizardState = GetOnboardingWizardStateUseCase(
                getOnboardingState = GetOnboardingStateUseCase(fakeRepository, fakeProfileRepository),
                observeSpecialization = ObserveSpecializationUseCase(fakeSpecializationRepository),
                observeLiftCatalog = ObserveLiftCatalogUseCase(fakeLiftRepository),
                observeSelectedPreset = ObserveSelectedPresetUseCase(fakeLiftRepository),
                observeUsedHistoricalImport = ObserveUsedHistoricalImportUseCase(fakeLiftRepository),
            ),
            advanceOnboardingStep = AdvanceOnboardingStepUseCase(
                validateCurrentStep = validateCurrentStep,
                updateUserTrainingProfile = UpdateUserTrainingProfileUseCase(fakeProfileRepository),
                updateSpecialization = UpdateSpecializationUseCase(fakeSpecializationRepository),
                saveOnboardingProgress = SaveOnboardingProgressUseCase(fakeRepository),
                loggingPort = FakeLoggingPort(),
            ),
            getOnboardingStepCapabilities = GetOnboardingStepCapabilitiesUseCase(validateCurrentStep),
            applyGymPreset = ApplyGymPresetUseCase(fakeLiftRepository),
            setSelectedPreset = SetSelectedPresetUseCase(fakeLiftRepository),
            executeHistoricalImport = ExecuteHistoricalImportUseCase(
                validateImportFiles = com.stratum.domain.importing.ValidateImportFilesUseCase(),
                runHistoricalImport = RunHistoricalImportUseCase(fakeHistoricalImport),
                refreshPersistedImportState = refreshPersistedImportState,
            ),
            cancelHistoricalImport = CancelHistoricalImportUseCase(
                rollbackHistoricalImport,
                refreshPersistedImportState,
                com.stratum.core.testing.importing.FakeImportJobRepository(),
            ),
            refreshPersistedImportState = refreshPersistedImportState,
            updateUserTrainingProfile = UpdateUserTrainingProfileUseCase(fakeProfileRepository),
            updateSpecialization = UpdateSpecializationUseCase(fakeSpecializationRepository),
            generateProgram = com.stratum.domain.program.GenerateProgramUseCase(
                loadContext =
                    FixedProgramGenerationContextLoader(
                        context = scenarioContext(days = 3, specialization = emptyMap()),
                    ),
                pipeline = testProgramGenerationPipeline(),
                liftRepository = fakeLiftRepository,
            ),
            activateProgram = com.stratum.domain.program.ActivateProgramUseCase(
                programRepository = fakeProgramRepository,
                programChangeEventRepository = com.stratum.core.testing.training.NoOpProgramChangeEventRepository(),
            ),
            completeOnboarding = CompleteOnboardingUseCase(fakeRepository, fakeProfileRepository),
            importProgramTemplate = com.stratum.domain.program.importing.ImportProgramTemplateUseCase(
                importPort = com.stratum.core.testing.importing.FakeProgramTemplateImportPort(),
                resolver = com.stratum.domain.program.importing.ResolveProgramTemplateLiftsUseCase(
                    liftRepository = fakeLiftRepository,
                    liftNameResolver = com.stratum.core.testing.importing.FakeLiftNameResolverPort(),
                ),
                repository = fakeProgramRepository,
                completeOnboarding = CompleteOnboardingUseCase(fakeRepository, fakeProfileRepository),
                profileRepository = fakeProfileRepository,
                programChangeEventRepository = com.stratum.core.testing.training.NoOpProgramChangeEventRepository(),
            ),
            logError = com.stratum.domain.logging.LogErrorUseCase(FakeLoggingPort()),
        )
    }

    private fun buildRollbackHistoricalImport(): RollbackHistoricalImportUseCase =
        RollbackHistoricalImportUseCase(
            fakeTrainingLogRepository,
            fakeLiftInsightsRepository,
            fakeSupersetRepository,
            fakeVolumeRepository,
            fakeMetadataRepository,
            FakeResponseProfilePersistencePort(),
            fakeLiftRepository,
        )

    private class FakeImportAnalysisMetadataRepository : ImportAnalysisMetadataRepository {
        private var stored: ImportAnalysisMetadata? = null

        fun reset() {
            stored = null
        }

        override suspend fun save(metadata: ImportAnalysisMetadata) {
            stored = metadata
        }

        override suspend fun get(): ImportAnalysisMetadata? = stored

        override suspend fun clear() {
            stored = null
        }
    }

    private class FakeLiftInsightsRepository : LiftTrainingInsightsRepository {
        override suspend fun replaceAll(insights: List<LiftTrainingInsights>) = Unit

        override suspend fun getAll(): List<LiftTrainingInsights> = emptyList()

        override suspend fun getByLiftId(liftId: LiftId): LiftTrainingInsights? = null
    }

    private class FakeSupersetRepository : SupersetAffinityRepository {
        override suspend fun replaceAll(affinities: List<SupersetAffinity>) = Unit

        override suspend fun getAll(): List<SupersetAffinity> = emptyList()
    }

    private class FakeVolumeRepository : MuscleVolumeBaselineRepository {
        private var baselines: List<MuscleVolumeBaseline> = emptyList()

        fun reset() {
            baselines = emptyList()
        }

        fun seed(baselines: List<MuscleVolumeBaseline>) {
            this.baselines = baselines
        }

        override suspend fun replaceAll(baselines: List<MuscleVolumeBaseline>) {
            this.baselines = baselines
        }

        override suspend fun getAll(): List<MuscleVolumeBaseline> = baselines
    }

    fun aLift(
        slug: String,
        tags: Set<GymPreset> = emptySet(),
        enabled: Boolean = false,
    ): Lift = Lift(
        id = LiftId(slug),
        name = slug.replace('-', ' ').replaceFirstChar { it.uppercase() },
        movementPatterns = setOf(MovementPattern.SquatPattern),
        primaryMuscles = setOf(MuscleGroup.Quad),
        secondaryMuscles = emptySet(),
        incrementTiers = emptyList(),
        presetTags = tags,
        enabled = enabled,
    )

    fun stepId(step: OnboardingStep): String = step.name
}
