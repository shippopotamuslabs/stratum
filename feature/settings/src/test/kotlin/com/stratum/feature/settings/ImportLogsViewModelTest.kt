package com.stratum.feature.settings

import com.stratum.core.testing.MainDispatcherRule
import com.stratum.core.testing.auth.FakeAuthManager
import com.stratum.core.testing.importing.FakeHistoricalImportRunnerPort
import com.stratum.core.testing.logging.FakeLoggingPort
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
import com.stratum.domain.importing.ImportFailureKind
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
import com.stratum.core.testing.telemetry.FakeAnalyticsPort
import com.stratum.domain.logging.LogAnalyticsEventUseCase
import com.stratum.domain.logging.LogErrorUseCase
import com.stratum.domain.logging.PARAM_ACTION
import com.stratum.domain.logging.PARAM_SCREEN
import com.stratum.domain.logging.USER_VISIBLE_ERROR_EVENT
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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

    private val loggingPort = FakeLoggingPort()

    private fun buildVm(
        auth: FakeAuthManager,
        runner: FakeHistoricalImportRunnerPort,
        analytics: FakeAnalyticsPort = FakeAnalyticsPort(),
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
            logError = LogErrorUseCase(loggingPort),
            logAnalytics = LogAnalyticsEventUseCase(analytics),
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
    @DisplayName("IE-S7-008 Read files failed surfaces generic error and logs detail")
    fun ieS7_008_readFilesFailed() = runTest {
        val runner = FakeHistoricalImportRunnerPort()
        val auth =
            FakeAuthManager(AuthState.Authenticated(UserProfile("u1", "U", "u@e.com", null)))
        val analytics = FakeAnalyticsPort()
        val vm = buildVm(auth, runner, analytics)
        vm.onReadFilesFailed("Could not open document")
        advanceUntilIdle()
        assertEquals(IMPORT_LOGS_READ_FILES_ERROR, vm.uiState.value.errorMessage)
        assertEquals(0, runner.invocations.size)
        assertEquals(false, vm.uiState.value.isImporting)
        assertTrue(loggingPort.errors.any { it.message.contains("Could not open document") })
        val event = analytics.events.single()
        assertEquals(USER_VISIBLE_ERROR_EVENT, event.name)
        assertEquals("import_logs", event.params[PARAM_SCREEN])
        assertEquals("read_files", event.params[PARAM_ACTION])
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

    @Test
    @DisplayName("IE-S7-010 Failed import hides technical provider message; logs detail")
    fun ieS7_010_failedHidesTechnicalMessage() = runTest {
        val technical =
            "Your request was for 57300 tokens but you only have enough credits " +
                "for 38042. Please refresh them at the OpenRouter console."
        val runner =
            FakeHistoricalImportRunnerPort(
                events =
                    listOf(
                        HistoricalImportEvent.Failed(
                            message = technical,
                            kind = ImportFailureKind.Generic,
                        ),
                    ),
            )
        val auth =
            FakeAuthManager(AuthState.Authenticated(UserProfile("u1", "U", "u@e.com", null)))
        val analytics = FakeAnalyticsPort()
        val vm = buildVm(auth, runner, analytics)
        vm.onFilesSelected(listOf("x".toByteArray() to "export.csv"))
        advanceUntilIdle()
        assertEquals(IMPORT_LOGS_GENERIC_ERROR, vm.uiState.value.errorMessage)
        assertFalse(vm.uiState.value.errorMessage!!.contains("OpenRouter"))
        assertFalse(vm.uiState.value.errorMessage!!.contains("tokens"))
        assertTrue(loggingPort.errors.any { it.message.contains(technical) })
        assertEquals(false, vm.uiState.value.isImporting)
        val event = analytics.events.single()
        assertEquals(USER_VISIBLE_ERROR_EVENT, event.name)
        assertEquals("import_logs", event.params[PARAM_SCREEN])
        assertEquals("import_history", event.params[PARAM_ACTION])
    }

    @Test
    @DisplayName("IE-S7-011 Unsupported file uses dedicated user-safe copy")
    fun ieS7_011_unsupportedFileMessage() = runTest {
        val technical = "agent rejected: schema mismatch sessionId"
        val runner =
            FakeHistoricalImportRunnerPort(
                events =
                    listOf(
                        HistoricalImportEvent.Failed(
                            message = technical,
                            kind = ImportFailureKind.UnsupportedFile,
                        ),
                    ),
            )
        val auth =
            FakeAuthManager(AuthState.Authenticated(UserProfile("u1", "U", "u@e.com", null)))
        val vm = buildVm(auth, runner)
        vm.onFilesSelected(listOf("x".toByteArray() to "export.csv"))
        advanceUntilIdle()
        assertEquals(IMPORT_LOGS_UNSUPPORTED_FILE_ERROR, vm.uiState.value.errorMessage)
        assertTrue(loggingPort.errors.any { it.message.contains(technical) })
    }
}
