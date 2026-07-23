package com.stratum.feature.settings

import com.stratum.core.testing.MainDispatcherRule
import com.stratum.core.testing.auth.FakeAuthManager
import com.stratum.core.testing.importing.FakeHistoricalImportRunnerPort
import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.core.testing.response.FakeLiftEffectivenessObservationRepository
import com.stratum.core.testing.response.FakeMuscleVolumeResponseSnapshotRepository
import com.stratum.core.testing.response.FakeResponseProfilePersistencePort
import com.stratum.core.testing.response.FakeVolumeResponseObservationRepository
import com.stratum.core.testing.training.FakeTrainingLogRepository
import com.stratum.domain.auth.AuthState
import com.stratum.domain.auth.UserProfile
import com.stratum.domain.importing.CancelHistoricalImportUseCase
import com.stratum.domain.importing.ExecuteHistoricalImportUseCase
import com.stratum.domain.importing.ExtractTrainingInsightsSummary
import com.stratum.domain.importing.HistoricalImportEvent
import com.stratum.domain.importing.ImportAnalysisMetadata
import com.stratum.domain.importing.ImportAnalysisMetadataRepository
import com.stratum.domain.importing.ImportHistoryPersistPolicy
import com.stratum.domain.importing.LiftTrainingInsights
import com.stratum.domain.importing.LiftTrainingInsightsRepository
import com.stratum.domain.importing.LoadHistoricalImportSummaryUseCase
import com.stratum.domain.importing.MergeImportStats
import com.stratum.domain.importing.MuscleVolumeBaseline
import com.stratum.domain.importing.MuscleVolumeBaselineRepository
import com.stratum.domain.importing.ReconcileHistoricalImportUseCase
import com.stratum.domain.importing.RefreshPersistedImportStateUseCase
import com.stratum.domain.importing.RollbackHistoricalImportUseCase
import com.stratum.domain.importing.RunHistoricalImportUseCase
import com.stratum.domain.importing.SupersetAffinity
import com.stratum.domain.importing.SupersetAffinityRepository
import com.stratum.domain.importing.ValidateImportFilesUseCase
import com.stratum.domain.lift.identity.LiftId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherRule::class)
class ImportLogsViewModelTest {

    private val emptyMetadata =
        object : ImportAnalysisMetadataRepository {
            override suspend fun save(metadata: ImportAnalysisMetadata) = Unit
            override suspend fun get(): ImportAnalysisMetadata? = null
            override suspend fun clear() = Unit
        }
    private val emptyInsights =
        object : LiftTrainingInsightsRepository {
            override suspend fun replaceAll(insights: List<LiftTrainingInsights>) = Unit
            override suspend fun getAll(): List<LiftTrainingInsights> = emptyList()
            override suspend fun getByLiftId(liftId: LiftId) = null
        }
    private val emptySuperset =
        object : SupersetAffinityRepository {
            override suspend fun replaceAll(affinities: List<SupersetAffinity>) = Unit
            override suspend fun getAll(): List<SupersetAffinity> = emptyList()
        }
    private val emptyVolume =
        object : MuscleVolumeBaselineRepository {
            override suspend fun replaceAll(baselines: List<MuscleVolumeBaseline>) = Unit
            override suspend fun getAll(): List<MuscleVolumeBaseline> = emptyList()
        }

    private fun buildExecute(runner: FakeHistoricalImportRunnerPort): ExecuteHistoricalImportUseCase {
        val trainingLogRepo = FakeTrainingLogRepository()
        val rollback =
            RollbackHistoricalImportUseCase(
                trainingLogRepository = trainingLogRepo,
                liftTrainingInsightsRepository = emptyInsights,
                supersetAffinityRepository = emptySuperset,
                muscleVolumeBaselineRepository = emptyVolume,
                importAnalysisMetadataRepository = emptyMetadata,
                responseProfilePersistence = FakeResponseProfilePersistencePort(),
                liftRepository = FakeLiftRepository(),
            )
        val refresh =
            RefreshPersistedImportStateUseCase(
                reconcileHistoricalImport = ReconcileHistoricalImportUseCase(emptyMetadata, rollback),
                loadHistoricalImportSummary =
                    LoadHistoricalImportSummaryUseCase(
                        emptyMetadata,
                        emptyVolume,
                        emptyInsights,
                        emptySuperset,
                        FakeVolumeResponseObservationRepository(),
                        FakeLiftEffectivenessObservationRepository(),
                        FakeMuscleVolumeResponseSnapshotRepository(),
                        FakeLiftRepository(),
                    ),
            )
        return ExecuteHistoricalImportUseCase(
            validateImportFiles = ValidateImportFilesUseCase(),
            runHistoricalImport = RunHistoricalImportUseCase(runner),
            refreshPersistedImportState = refresh,
        )
    }

    private fun buildVm(
        auth: FakeAuthManager,
        runner: FakeHistoricalImportRunnerPort,
    ): ImportLogsViewModel {
        val execute = buildExecute(runner)
        val cancel =
            CancelHistoricalImportUseCase(
                rollbackHistoricalImport =
                    RollbackHistoricalImportUseCase(
                        trainingLogRepository = FakeTrainingLogRepository(),
                        liftTrainingInsightsRepository = emptyInsights,
                        supersetAffinityRepository = emptySuperset,
                        muscleVolumeBaselineRepository = emptyVolume,
                        importAnalysisMetadataRepository = emptyMetadata,
                        responseProfilePersistence = FakeResponseProfilePersistencePort(),
                        liftRepository = FakeLiftRepository(),
                    ),
                refreshPersistedImportState =
                    RefreshPersistedImportStateUseCase(
                        reconcileHistoricalImport =
                            ReconcileHistoricalImportUseCase(
                                emptyMetadata,
                                RollbackHistoricalImportUseCase(
                                    FakeTrainingLogRepository(),
                                    emptyInsights,
                                    emptySuperset,
                                    emptyVolume,
                                    emptyMetadata,
                                    FakeResponseProfilePersistencePort(),
                                    FakeLiftRepository(),
                                ),
                            ),
                        loadHistoricalImportSummary =
                            LoadHistoricalImportSummaryUseCase(
                                emptyMetadata,
                                emptyVolume,
                                emptyInsights,
                                emptySuperset,
                                FakeVolumeResponseObservationRepository(),
                                FakeLiftEffectivenessObservationRepository(),
                                FakeMuscleVolumeResponseSnapshotRepository(),
                                FakeLiftRepository(),
                            ),
                    ),
                importJobRepository = com.stratum.core.testing.importing.FakeImportJobRepository(),
            )
        return ImportLogsViewModel(
            executeImport = execute,
            cancelImport = cancel,
            authManager = auth,
            observeWeightDisplayPreferences =
                com.stratum.domain.lift.ObserveWeightDisplayPreferencesUseCase(
                    com.stratum.core.testing.lift.FakeWeightDisplayPreferencesRepository(),
                ),
        )
    }

    @Test
    @DisplayName("IE-S7-001 Import logs signed-out shows guidance and skips execute")
    fun ieS7_001_signedOut() = runTest {
        val runner = FakeHistoricalImportRunnerPort()
        val auth = FakeAuthManager(AuthState.Unauthenticated)
        val vm = buildVm(auth, runner)
        vm.onFilesSelected(listOf("x".toByteArray() to "export.csv"))
        advanceUntilIdle()
        assertTrue(vm.uiState.value.signedOutGuidanceVisible)
        assertEquals(0, runner.invocations.size)
    }

    @Test
    @DisplayName("IE-S7-002 Import logs passes MergeSkipDuplicates")
    fun ieS7_002_mergePolicy() = runTest {
        val runner = FakeHistoricalImportRunnerPort()
        val auth =
            FakeAuthManager(AuthState.Authenticated(UserProfile("u1", "U", "u@e.com", null)))
        val vm = buildVm(auth, runner)
        vm.onFilesSelected(listOf("x".toByteArray() to "export.csv"))
        advanceUntilIdle()
        assertEquals(
            ImportHistoryPersistPolicy.MergeSkipDuplicates,
            runner.invocations.last().persistPolicy,
        )
    }

    @Test
    @DisplayName("IE-S7-003 Import logs success surfaces merge stats")
    fun ieS7_003_mergeStats() = runTest {
        val stats =
            MergeImportStats(
                sessionsInserted = 1,
                setsInserted = 2,
                sessionsSkipped = 0,
                setsSkipped = 0,
            )
        val runner =
            FakeHistoricalImportRunnerPort(
                events =
                    listOf(
                        HistoricalImportEvent.Completed(
                            summary =
                                ExtractTrainingInsightsSummary(
                                    liftsWithInsights = 1,
                                    supersetPairs = 0,
                                    musclesWithBaselines = 1,
                                ),
                            mergeStats = stats,
                        ),
                    ),
            )
        val auth =
            FakeAuthManager(AuthState.Authenticated(UserProfile("u1", "U", "u@e.com", null)))
        val vm = buildVm(auth, runner)
        vm.onFilesSelected(listOf("x".toByteArray() to "export.csv"))
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.mergeStats!!.sessionsInserted)
        assertEquals(2, vm.uiState.value.mergeStats!!.setsInserted)
    }

    @Test
    @DisplayName("IE-S7-004 Cancel import does not throw")
    fun ieS7_004_cancel() = runTest {
        val runner = FakeHistoricalImportRunnerPort()
        val auth =
            FakeAuthManager(AuthState.Authenticated(UserProfile("u1", "U", "u@e.com", null)))
        val vm = buildVm(auth, runner)
        vm.onCancelImport()
        advanceUntilIdle()
        // no exception; errorMessage null or non-crash terminal
        assertTrue(
            vm.uiState.value.errorMessage == null ||
                vm.uiState.value.errorMessage!!.isNotBlank(),
        )
    }

    @Test
    @DisplayName("IE-S7-008 Read files failed surfaces error without starting import")
    fun ieS7_008_readFilesFailed() = runTest {
        val runner = FakeHistoricalImportRunnerPort()
        val auth =
            FakeAuthManager(AuthState.Authenticated(UserProfile("u1", "U", "u@e.com", null)))
        val vm = buildVm(auth, runner)
        vm.onReadFilesFailed("Could not open document")
        advanceUntilIdle()
        assertEquals("Could not open document", vm.uiState.value.errorMessage)
        assertEquals(0, runner.invocations.size)
        assertEquals(false, vm.uiState.value.isImporting)
    }

    @Test
    @DisplayName("IE-S7-M001 Mutation defense Settings never passes ReplaceImportOrigin")
    fun ieS7_m001_neverReplace() = runTest {
        val runner = FakeHistoricalImportRunnerPort()
        val auth =
            FakeAuthManager(AuthState.Authenticated(UserProfile("u1", "U", "u@e.com", null)))
        val vm = buildVm(auth, runner)
        vm.onFilesSelected(listOf("x".toByteArray() to "export.csv"))
        advanceUntilIdle()
        val policy = runner.invocations.last().persistPolicy
        assertEquals(ImportHistoryPersistPolicy.MergeSkipDuplicates, policy)
        assertTrue(policy != ImportHistoryPersistPolicy.ReplaceImportOrigin)
    }

    @Test
    @DisplayName("IE-S7-009 Validation failed maps to readable message")
    fun ieS7_009_validationMessage() = runTest {
        val runner = FakeHistoricalImportRunnerPort()
        val auth =
            FakeAuthManager(AuthState.Authenticated(UserProfile("u1", "U", "u@e.com", null)))
        val vm = buildVm(auth, runner)
        // Empty selection → ValidateImportFilesUseCase returns NoFiles
        vm.onFilesSelected(emptyList())
        advanceUntilIdle()
        assertEquals("No files selected.", vm.uiState.value.errorMessage)
        assertEquals(0, runner.invocations.size)
    }
}
