package com.stratum.feature.onboarding

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
import com.stratum.domain.importing.HistoricalImportEvent
import com.stratum.domain.importing.ImportProgress
import com.stratum.domain.importing.ImportProgressPhase
import com.stratum.domain.importing.LoadHistoricalImportSummaryUseCase
import com.stratum.domain.importing.ReconcileHistoricalImportUseCase
import com.stratum.core.testing.response.FakeLiftEffectivenessObservationRepository
import com.stratum.core.testing.response.FakeVolumeResponseObservationRepository
import com.stratum.domain.importing.RollbackHistoricalImportUseCase
import com.stratum.domain.importing.TrainingHistoryRepository
import com.stratum.domain.lift.ObserveUsedHistoricalImportUseCase
import com.stratum.domain.lift.SetSelectedPresetUseCase
import com.stratum.domain.lift.ValidateGymLiftsStepUseCase
import com.stratum.domain.importing.HistoricalSession
import com.stratum.domain.importing.LoggedSet
import com.stratum.domain.importing.identity.HistoricalSessionId
import com.stratum.domain.importing.identity.LoggedSetId
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.lift.Weight
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
import java.time.Instant

internal object OnboardingViewModelTestFixtures {

    val fakeRepository = FakeOnboardingRepository()
    val fakeProfileRepository = FakeUserTrainingProfileRepository()
    val fakeSpecializationRepository = FakeSpecializationPreferenceRepository()
    val fakeLiftRepository = FakeLiftRepository()

    val fakeHistoricalImport = FakeHistoricalImportRunnerPort()
    private val fakeTrainingHistoryRepository = FakeTrainingHistoryRepository()
    private val fakeMetadataRepository = FakeImportAnalysisMetadataRepository()
    private val fakeLiftInsightsRepository = FakeLiftInsightsRepository()
    private val fakeSupersetRepository = FakeSupersetRepository()
    private val fakeVolumeRepository = FakeVolumeRepository()
    private val fakeVolumeResponseRepository = FakeVolumeResponseObservationRepository()
    private val fakeEffectivenessRepository = FakeLiftEffectivenessObservationRepository()

    fun resetDefaults() {
        fakeRepository.seed(
            OnboardingProgress(OnboardingStep.Welcome, startedAt = 0L, completedAt = null),
        )
        fakeProfileRepository.seed(null)
        fakeSpecializationRepository.seed(SpecializationPreference.Empty)
        fakeLiftRepository.seedLifts(emptyList())
        fakeLiftRepository.seedPreset(null)
        fakeLiftRepository.seedUsedHistoricalImport(false)
        fakeTrainingHistoryRepository.clear()
        fakeMetadataRepository.reset()
        fakeVolumeRepository.reset()
        fakeVolumeResponseRepository.seed(emptyList())
        fakeEffectivenessRepository.seed(emptyList())
        fakeHistoricalImport.setEvents(
            listOf(
                com.stratum.domain.importing.HistoricalImportEvent.Progress(
                    com.stratum.domain.importing.ImportProgress(ImportProgressPhase.Uploading),
                ),
                com.stratum.domain.importing.HistoricalImportEvent.Completed(
                    com.stratum.domain.importing.ExtractTrainingInsightsSummary(
                        liftsWithInsights = 1,
                        supersetPairs = 0,
                        musclesWithBaselines = 1,
                    ),
                ),
            ),
        )
    }

    fun seedOrphanImportHistory() {
        fakeTrainingHistoryRepository.seedOrphanHistory()
    }

    fun buildViewModel(): OnboardingViewModel = OnboardingViewModel(
        getOnboardingWizardState = GetOnboardingWizardStateUseCase(
            getOnboardingState = GetOnboardingStateUseCase(fakeRepository, fakeProfileRepository),
            observeSpecialization = ObserveSpecializationUseCase(fakeSpecializationRepository),
            observeLiftCatalog = ObserveLiftCatalogUseCase(fakeLiftRepository),
            observeSelectedPreset = ObserveSelectedPresetUseCase(fakeLiftRepository),
            observeUsedHistoricalImport = ObserveUsedHistoricalImportUseCase(fakeLiftRepository),
        ),
        advanceOnboardingStep = AdvanceOnboardingStepUseCase(
            validateCurrentStep = ValidateCurrentOnboardingStepUseCase(
                validateProfileStep = ValidateProfileStepUseCase(),
                validateScheduleStep = ValidateScheduleStepUseCase(),
                validateSpecializationStep = ValidateSpecializationStepUseCase(),
                validateGymLiftsStep = ValidateGymLiftsStepUseCase(),
            ),
            updateUserTrainingProfile = UpdateUserTrainingProfileUseCase(fakeProfileRepository),
            updateSpecialization = UpdateSpecializationUseCase(fakeSpecializationRepository),
            saveOnboardingProgress = SaveOnboardingProgressUseCase(fakeRepository),
        ),
        validateCurrentStep = ValidateCurrentOnboardingStepUseCase(
            validateProfileStep = ValidateProfileStepUseCase(),
            validateScheduleStep = ValidateScheduleStepUseCase(),
            validateSpecializationStep = ValidateSpecializationStepUseCase(),
            validateGymLiftsStep = ValidateGymLiftsStepUseCase(),
        ),
        applyGymPreset = ApplyGymPresetUseCase(fakeLiftRepository),
        setSelectedPreset = SetSelectedPresetUseCase(fakeLiftRepository),
        runFullHistoricalImport = fakeHistoricalImport,
        rollbackHistoricalImport = buildRollbackHistoricalImport(),
        reconcileHistoricalImport = ReconcileHistoricalImportUseCase(
            fakeMetadataRepository,
            buildRollbackHistoricalImport(),
        ),
        loadHistoricalImportSummary = LoadHistoricalImportSummaryUseCase(
            fakeMetadataRepository,
            fakeVolumeRepository,
            fakeLiftInsightsRepository,
            fakeSupersetRepository,
            fakeVolumeResponseRepository,
            fakeEffectivenessRepository,
            fakeTrainingHistoryRepository,
            fakeLiftRepository,
        ),
        completeOnboarding = CompleteOnboardingUseCase(fakeRepository, fakeProfileRepository),
    )

    private fun buildRollbackHistoricalImport(): RollbackHistoricalImportUseCase =
        RollbackHistoricalImportUseCase(
            fakeTrainingHistoryRepository,
            fakeLiftInsightsRepository,
            fakeSupersetRepository,
            fakeVolumeRepository,
            fakeMetadataRepository,
            FakeResponseProfilePersistencePort(),
            fakeLiftRepository,
        )

    private class FakeTrainingHistoryRepository : TrainingHistoryRepository {
        private var sessions: List<HistoricalSession> = emptyList()
        private var sets: List<LoggedSet> = emptyList()

        fun clear() {
            sessions = emptyList()
            sets = emptyList()
        }

        fun seedOrphanHistory() {
            sessions =
                listOf(
                    HistoricalSession(
                        id = HistoricalSessionId("s1"),
                        startedAt = Instant.parse("2025-01-01T00:00:00Z"),
                        endedAt = null,
                        sourceApp = null,
                    ),
                )
            sets =
                listOf(
                    LoggedSet(
                        id = LoggedSetId("set1"),
                        sessionId = HistoricalSessionId("s1"),
                        exerciseNameRaw = "Squat",
                        liftId = null,
                        setIndex = 0,
                        weight = Weight(100.0, Weight.WeightUnit.Lb),
                        reps = 5,
                        rir = null,
                        completedAt = Instant.parse("2025-01-01T01:00:00Z"),
                    ),
                )
        }

        override suspend fun replaceAll(
            sessions: List<HistoricalSession>,
            sets: List<LoggedSet>,
        ) {
            this.sessions = sessions
            this.sets = sets
        }

        override suspend fun getAllSessions(): List<HistoricalSession> = sessions

        override suspend fun getAllSets(): List<LoggedSet> = sets

        override suspend fun getSetsInWindow(windowStart: Instant, windowEnd: Instant): List<LoggedSet> =
            emptyList()
    }

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
        movementPattern = MovementPattern.LegPush,
        primaryMuscles = setOf(MuscleGroup.Quad),
        secondaryMuscles = emptySet(),
        allowWeightChangeBetweenSets = false,
        incrementTiers = emptyList(),
        presetTags = tags,
        enabled = enabled,
    )

    fun stepId(step: OnboardingStep): String = step.name
}
