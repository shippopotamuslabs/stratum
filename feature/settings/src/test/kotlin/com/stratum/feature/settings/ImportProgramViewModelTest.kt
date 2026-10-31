package com.stratum.feature.settings

import com.stratum.core.testing.MainDispatcherRule
import com.stratum.core.testing.auth.FakeAuthManager
import com.stratum.core.testing.logging.FakeLoggingPort
import com.stratum.core.testing.telemetry.FakeAnalyticsPort
import com.stratum.domain.logging.LogAnalyticsEventUseCase
import com.stratum.domain.logging.LogErrorUseCase
import com.stratum.domain.logging.PARAM_ACTION
import com.stratum.domain.logging.PARAM_SCREEN
import com.stratum.domain.logging.USER_VISIBLE_ERROR_EVENT
import com.stratum.core.testing.importing.FakeLiftNameResolverPort
import com.stratum.core.testing.importing.FakeProgramTemplateImportPort
import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.core.testing.onboarding.FakeOnboardingRepository
import com.stratum.core.testing.onboarding.FakeUserTrainingProfileRepository
import com.stratum.core.testing.program.FakeProgramRepository
import com.stratum.core.testing.training.FakeProgramChangeEventRepository
import com.stratum.domain.auth.AuthState
import com.stratum.domain.auth.UserProfile
import com.stratum.application.onboarding.CompleteOnboardingUseCase
import com.stratum.domain.program.identity.ProgramId
import com.stratum.application.program.importing.ImportProgramTemplatesUseCase
import com.stratum.application.program.importing.ProgramImportRollbackException
import com.stratum.domain.program.importing.ProgramImportActivation
import com.stratum.domain.program.importing.ProgramImportRollbackDiagnostic
import com.stratum.domain.program.importing.ProgramTemplateImportOptions
import com.stratum.application.program.importing.ProgramTemplateImportProgressCallback
import com.stratum.domain.program.importing.ProgramTemplateImportResult
import com.stratum.application.program.importing.ResolveProgramTemplateLiftsUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
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
class ImportProgramViewModelTest {

    private class RecordingImportProgramTemplatesUseCase(
        private val result: Result<ProgramTemplateImportResult> =
            Result.success(
                ProgramTemplateImportResult(
                    importedProgramIds = listOf(ProgramId("x")),
                    skippedProgramCount = 0,
                ),
            ),
    ) : ImportProgramTemplatesUseCase(
        importPort = FakeProgramTemplateImportPort(),
        resolver =
            ResolveProgramTemplateLiftsUseCase(
                FakeLiftRepository(),
                FakeLiftNameResolverPort(),
            ),
        repository = FakeProgramRepository(),
        completeOnboarding =
            CompleteOnboardingUseCase(
                FakeOnboardingRepository(),
                FakeUserTrainingProfileRepository(),
            ),
        profileRepository = FakeUserTrainingProfileRepository(),
        programChangeEventRepository = FakeProgramChangeEventRepository(),
        importDispatcher = StandardTestDispatcher(),
    ) {
        var callCount: Int = 0
        var lastOptions: ProgramTemplateImportOptions? = null

        override suspend fun invoke(
            bytes: ByteArray,
            fileName: String,
            options: ProgramTemplateImportOptions,
            onProgress: ProgramTemplateImportProgressCallback,
        ): Result<ProgramTemplateImportResult> {
            callCount++
            lastOptions = options
            return result
        }
    }

    @Test
    @DisplayName("IE-S7-005 Import program signed-out shows guidance")
    fun ieS7_005_signedOut() = runTest {
        val auth = FakeAuthManager(AuthState.Unauthenticated)
        val useCase = RecordingImportProgramTemplatesUseCase()
        val vm = ImportProgramViewModel(
            useCase,
            auth,
            LogAnalyticsEventUseCase(FakeAnalyticsPort()),
            LogErrorUseCase(FakeLoggingPort()),
        )
        vm.onFileSelected(byteArrayOf(1), "p.json")
        advanceUntilIdle()
        assertTrue(vm.uiState.value.signedOutGuidanceVisible)
        assertEquals(0, useCase.callCount)
    }

    @Test
    @DisplayName("IE-S7-006 Import program uses LibraryOnly options")
    fun ieS7_006_libraryOnlyOptions() = runTest {
        val auth =
            FakeAuthManager(AuthState.Authenticated(UserProfile("u1", "U", "u@e.com", null)))
        val useCase = RecordingImportProgramTemplatesUseCase()
        val vm = ImportProgramViewModel(
            useCase,
            auth,
            LogAnalyticsEventUseCase(FakeAnalyticsPort()),
            LogErrorUseCase(FakeLoggingPort()),
        )
        vm.onFileSelected(byteArrayOf(1), "p.json")
        advanceUntilIdle()
        val options = useCase.lastOptions!!
        assertEquals(ProgramImportActivation.LibraryOnly, options.activation)
        assertEquals(false, options.completeOnboarding)
        assertEquals(false, options.appendProgramRegeneratedEvent)
    }

    @Test
    @DisplayName("IE-S7-007 Import program success surfaces counts and Complete phase")
    fun ieS7_007_successCounts() = runTest {
        val auth =
            FakeAuthManager(AuthState.Authenticated(UserProfile("u1", "U", "u@e.com", null)))
        val useCase = RecordingImportProgramTemplatesUseCase()
        val vm = ImportProgramViewModel(
            useCase,
            auth,
            LogAnalyticsEventUseCase(FakeAnalyticsPort()),
            LogErrorUseCase(FakeLoggingPort()),
        )
        vm.onFileSelected(byteArrayOf(1), "p.json")
        advanceUntilIdle()
        assertEquals(ImportProgramPhaseUi.Complete, vm.uiState.value.phase)
        assertEquals(1, vm.uiState.value.importedCount)
        assertEquals(0, vm.uiState.value.skippedCount)
        assertEquals(false, vm.uiState.value.isImporting)
    }

    @Test
    @DisplayName("IE-S7-008 Read file failed surfaces error without starting import")
    fun ieS7_008_readFilesFailed() = runTest {
        val auth =
            FakeAuthManager(AuthState.Authenticated(UserProfile("u1", "U", "u@e.com", null)))
        val useCase = RecordingImportProgramTemplatesUseCase()
        val analytics = FakeAnalyticsPort()
        val vm = ImportProgramViewModel(
            useCase,
            auth,
            LogAnalyticsEventUseCase(analytics),
            LogErrorUseCase(FakeLoggingPort()),
        )
        vm.onReadFilesFailed("Could not open document")
        advanceUntilIdle()
        assertEquals("Could not open document", vm.uiState.value.errorMessage)
        assertEquals(0, useCase.callCount)
        assertEquals(false, vm.uiState.value.isImporting)
        val event = analytics.events.single()
        assertEquals(USER_VISIBLE_ERROR_EVENT, event.name)
        assertEquals("import_program", event.params[PARAM_SCREEN])
        assertEquals("read_files", event.params[PARAM_ACTION])
    }

    @Test
    @DisplayName("IE-S7-009 Import failure records analytics and surfaces error")
    fun ieS7_009_importFailureRecordsAnalytics() = runTest {
        val auth =
            FakeAuthManager(AuthState.Authenticated(UserProfile("u1", "U", "u@e.com", null)))
        val useCase =
            RecordingImportProgramTemplatesUseCase(
                result = Result.failure(RuntimeException("boom")),
            )
        val analytics = FakeAnalyticsPort()
        val vm = ImportProgramViewModel(
            useCase,
            auth,
            LogAnalyticsEventUseCase(analytics),
            LogErrorUseCase(FakeLoggingPort()),
        )
        vm.onFileSelected(byteArrayOf(1), "p.json")
        advanceUntilIdle()
        assertEquals(false, vm.uiState.value.isImporting)
        val event = analytics.events.single()
        assertEquals(USER_VISIBLE_ERROR_EVENT, event.name)
        assertEquals("import_program", event.params[PARAM_SCREEN])
        assertEquals("import_templates", event.params[PARAM_ACTION])
    }

    @Test
    @DisplayName("STR-256 Rollback failure logs each orphan delete once, UI copy uses primary message")
    fun str256_rollbackFailureDrainsDiagnosticsOnce() = runTest {
        val auth =
            FakeAuthManager(AuthState.Authenticated(UserProfile("u1", "U", "u@e.com", null)))
        val deleteCause = IllegalStateException("delete failed")
        val primary = IllegalStateException("save failed")
        val wrapper =
            ProgramImportRollbackException(
                cause = primary,
                diagnostics =
                    listOf(
                        ProgramImportRollbackDiagnostic.RollbackDeleteFailed(
                            programId = ProgramId("prog-a"),
                            cause = deleteCause,
                        ),
                    ),
            )
        val useCase = RecordingImportProgramTemplatesUseCase(result = Result.failure(wrapper))
        val analytics = FakeAnalyticsPort()
        val logging = FakeLoggingPort()
        val vm =
            ImportProgramViewModel(
                useCase,
                auth,
                LogAnalyticsEventUseCase(analytics),
                LogErrorUseCase(logging),
            )
        vm.onFileSelected(byteArrayOf(1), "p.json")
        advanceUntilIdle()

        assertEquals(false, vm.uiState.value.isImporting)
        // User-facing copy derives from the primary message, diagnostics never reach the UI.
        assertEquals("save failed", vm.uiState.value.errorMessage)
        // Each orphan delete emitted exactly once.
        assertEquals(1, logging.errors.size)
        assertEquals(deleteCause, logging.errors.single().throwable)
        assertTrue(logging.errors.single().message.contains("prog-a"))
        // Analytics breadcrumb still recorded once.
        assertEquals(1, analytics.events.size)
    }

    @Test
    @DisplayName("userFacingImportError hides SQLite foreign key messages")
    fun userFacingImportError_hidesSqlite() {
        val friendly =
            userFacingImportError(
                Exception("FOREIGN KEY constraint failed (code 787 SQLITE_CONSTRAINT_FOREIGNKEY)"),
            )
        assertEquals(
            "Couldn't save this program. Try again or pick a different file.",
            friendly,
        )
        assertFalse(friendly.contains("FOREIGN KEY", ignoreCase = true))
        assertFalse(friendly.contains("SQLITE", ignoreCase = true))
    }
}
