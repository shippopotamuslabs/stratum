package com.stratum.application.program.importing


import com.stratum.application.importing.Bm25LiftNameResolver
import com.stratum.application.importing.HybridLiftNameResolver
import com.stratum.application.importing.LiftNameResolverPort
import com.stratum.core.testing.importing.FakeLiftEmbeddingCatalog
import com.stratum.core.testing.importing.FakeLiftNameEmbeddingPort
import com.stratum.core.testing.importing.FakeLiftNameResolverPort
import com.stratum.core.testing.importing.FakeProgramTemplateImportPort
import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.core.testing.onboarding.FakeOnboardingRepository
import com.stratum.core.testing.onboarding.FakeUserTrainingProfileRepository
import com.stratum.core.testing.program.FakeProgramRepository
import com.stratum.core.testing.training.FakeProgramChangeEventRepository
import com.stratum.core.testing.export.ExportTestFixtures
import com.stratum.domain.onboarding.UserTrainingProfile
import com.stratum.domain.program.Program
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import com.stratum.domain.program.identity.ProgramId
import com.stratum.application.onboarding.CompleteOnboardingUseCase
import com.stratum.application.onboarding.OnboardingRepository
import com.stratum.application.onboarding.UserTrainingProfileRepository
import com.stratum.application.program.ProgramRepository
import com.stratum.domain.program.importing.ProgramImportActivation
import com.stratum.domain.program.importing.ProgramImportRollbackDiagnostic
import com.stratum.domain.program.importing.ProgramTemplateImportOptions
import com.stratum.domain.program.importing.ProgramTemplateImportJobId
import com.stratum.domain.program.importing.ProgramTemplateJobSnapshot

@OptIn(ExperimentalCoroutinesApi::class)
class ImportProgramTemplatesUseCaseTest {

    private val libraryOnly =
        ProgramTemplateImportOptions(
            activation = ProgramImportActivation.LibraryOnly,
            completeOnboarding = false,
            appendProgramRegeneratedEvent = false,
        )

    private fun multiProgramJson(ids: List<String>): String {
        val programs =
            ids.joinToString(",") { id ->
                """{"id":"$id","template":{"programName":"Prog $id","cycleLength":4,"workouts":[{"name":"Day A","weekday":null,"position":0,"lifts":[{"name":"Squat","position":0,"sets":[{"position":0,"setTypeLabel":null,"repMin":5,"repMax":5,"rirGoal":2,"restSeconds":120,"isTopSet":true}],"supersetGroupKey":null}]}]}}"""
            }
        return """{"programs":[$programs]}"""
    }

    private fun singleTemplateJson(name: String = "Imported A"): String =
        """{"programName":"$name","cycleLength":4,"workouts":[{"name":"Day A","weekday":null,"position":0,"lifts":[{"name":"Squat","position":0,"sets":[{"position":0,"setTypeLabel":null,"repMin":5,"repMax":5,"rirGoal":2,"restSeconds":120,"isTopSet":true}],"supersetGroupKey":null}]}]}"""

    private fun buildUseCase(
        programRepo: ProgramRepository,
        onboardingRepo: FakeOnboardingRepository,
        importPort: ProgramTemplateImportPort,
        testScheduler: TestCoroutineScheduler,
        completeOnboarding: CompleteOnboardingUseCase? = null,
        liftNameResolver: LiftNameResolverPort = FakeLiftNameResolverPort(),
    ): ImportProgramTemplatesUseCase {
        val profileRepo = FakeUserTrainingProfileRepository()
        val liftRepo = FakeLiftRepository()
        return ImportProgramTemplatesUseCase(
            importPort = importPort,
            resolver =
                ResolveProgramTemplateLiftsUseCase(
                    liftRepository = liftRepo,
                    liftNameResolver = liftNameResolver,
                ),
            repository = programRepo,
            completeOnboarding =
                completeOnboarding
                    ?: CompleteOnboardingUseCase(onboardingRepo, profileRepo),
            profileRepository = profileRepo,
            programChangeEventRepository = FakeProgramChangeEventRepository(),
            importDispatcher = UnconfinedTestDispatcher(testScheduler),
        )
    }

    /**
     * Delegates to [inner]; fails on the Nth [saveProgram]/[saveToLibrary] call
     * (1-based) and every save after it. Used to prove multi-program import rolls
     * back earlier saves.
     */
    private class FailOnNthSaveRepository(
        private val failOnSaveNumber: Int,
        val inner: FakeProgramRepository = FakeProgramRepository(),
    ) : ProgramRepository by inner {
        private var saveCount = 0

        /** When non-null, thrown instead of the check failure on the Nth save. */
        var saveThrowable: Throwable? = null

        /** Program ids whose rollback [deleteProgram] must fail. */
        var failDeleteIds: Set<ProgramId> = emptySet()

        /** Every id [deleteProgram] was attempted for, in order. */
        val deleteAttempts = mutableListOf<ProgramId>()

        /** Causes thrown from failing rollback deletes, in attempt order. */
        val deleteCauses = mutableListOf<Throwable>()

        private suspend fun beforeSave() {
            saveCount++
            if (saveCount >= failOnSaveNumber) {
                saveThrowable?.let { throw it }
                check(false) {
                    "Fake save #$saveCount failed (configured fail on #$failOnSaveNumber)"
                }
            }
        }

        override suspend fun saveProgram(program: Program) {
            beforeSave()
            inner.saveProgram(program)
        }

        override suspend fun saveToLibrary(program: Program) {
            beforeSave()
            inner.saveToLibrary(program)
        }

        override suspend fun deleteProgram(programId: ProgramId) {
            deleteAttempts += programId
            if (programId in failDeleteIds) {
                val cause = IllegalStateException("Fake delete of ${programId.value} failed")
                deleteCauses += cause
                throw cause
            }
            inner.deleteProgram(programId)
        }
    }

    /** Records invocations for mutation defense (IE-S5-M001). */
    private class RecordingCompleteOnboardingUseCase(
        onboardingRepository: OnboardingRepository,
        profileRepository: UserTrainingProfileRepository,
    ) : CompleteOnboardingUseCase(onboardingRepository, profileRepository) {
        var invocationCount: Int = 0
            private set

        override suspend fun invoke(finalProfile: UserTrainingProfile) {
            invocationCount++
            super.invoke(finalProfile)
        }
    }

    @Test
    @DisplayName("IE-S5-001 LibraryOnly never activates and never completes onboarding")
    fun ieS5_001_libraryOnly() = runTest {
        val programRepo = FakeProgramRepository()
        val onboardingRepo = FakeOnboardingRepository()
        val port = FakeProgramTemplateImportPort(templateJson = singleTemplateJson("Imported A"))
        val result =
            buildUseCase(programRepo, onboardingRepo, port, testScheduler)(
                bytes = byteArrayOf(1),
                fileName = "p.json",
                options = libraryOnly,
            )
        assertTrue(result is ProgramTemplateImportOutcome.Success)
        assertEquals(1, (result as ProgramTemplateImportOutcome.Success).result.importedProgramIds.size)
        val saved = programRepo.getAllUserPrograms().single()
        assertFalse(saved.isActive)
        assertFalse(onboardingRepo.current.isComplete)
    }

    @Test
    @DisplayName("IE-S5-002 Existing exported program id is skipped")
    fun ieS5_002_idSkip() = runTest {
        val programRepo = FakeProgramRepository()
        programRepo.saveToLibrary(ExportTestFixtures.minimalProgram("prog-a", "Existing", isActive = false))
        val onboardingRepo = FakeOnboardingRepository()
        val port =
            FakeProgramTemplateImportPort(
                templateJson =
                    """{"programs":[{"id":"prog-a","template":{"programName":"Dup","cycleLength":4,"workouts":[{"name":"Day A","weekday":null,"position":0,"lifts":[{"name":"Squat","position":0,"sets":[{"position":0,"setTypeLabel":null,"repMin":5,"repMax":5,"rirGoal":2,"restSeconds":120,"isTopSet":true}],"supersetGroupKey":null}]}]}}]}""",
            )
        val result =
            buildUseCase(programRepo, onboardingRepo, port, testScheduler)(
                byteArrayOf(1),
                "p.json",
                libraryOnly,
            )
        assertTrue(result is ProgramTemplateImportOutcome.Success)
        assertTrue((result as ProgramTemplateImportOutcome.Success).result.importedProgramIds.isEmpty())
        assertEquals(1, (result as ProgramTemplateImportOutcome.Success).result.skippedProgramCount)
    }

    @Test
    @DisplayName("IE-S5-003 Multi-program file imports all templates to library")
    fun ieS5_003_multiAllLibrary() = runTest {
        val programRepo = FakeProgramRepository()
        val onboardingRepo = FakeOnboardingRepository()
        val port = FakeProgramTemplateImportPort(templateJson = multiProgramJson(listOf("prog-a", "prog-b")))
        val result =
            buildUseCase(programRepo, onboardingRepo, port, testScheduler)(
                byteArrayOf(1),
                "p.json",
                libraryOnly,
            )
        assertTrue(result is ProgramTemplateImportOutcome.Success)
        assertEquals(2, (result as ProgramTemplateImportOutcome.Success).result.importedProgramIds.size)
        assertEquals(0, (result as ProgramTemplateImportOutcome.Success).result.skippedProgramCount)
        assertTrue(programRepo.getAllUserPrograms().all { !it.isActive })
    }

    @Test
    @DisplayName("IE-S5-004 Multi-program skips existing id and imports the other")
    fun ieS5_004_multiPartialSkip() = runTest {
        val programRepo = FakeProgramRepository()
        programRepo.saveToLibrary(ExportTestFixtures.minimalProgram("prog-a", "A", isActive = false))
        val onboardingRepo = FakeOnboardingRepository()
        val port = FakeProgramTemplateImportPort(templateJson = multiProgramJson(listOf("prog-a", "prog-b")))
        val result =
            buildUseCase(programRepo, onboardingRepo, port, testScheduler)(
                byteArrayOf(1),
                "p.json",
                libraryOnly,
            )
        assertTrue(result is ProgramTemplateImportOutcome.Success)
        val imported = (result as ProgramTemplateImportOutcome.Success).result.importedProgramIds.map { it.value }
        assertEquals(listOf("prog-b"), imported)
        assertEquals(1, (result as ProgramTemplateImportOutcome.Success).result.skippedProgramCount)
    }

    @Test
    @DisplayName("IE-S5-006 Onboarding wrapper ActivateAsActive completes onboarding")
    fun ieS5_006_wrapperActivates() = runTest {
        val programRepo = FakeProgramRepository()
        val onboardingRepo = FakeOnboardingRepository()
        val profileRepo = FakeUserTrainingProfileRepository()
        val port = FakeProgramTemplateImportPort()
        // SUT is the thin-API onboarding wrapper (test_spec Gherkin), not core options.
        val wrapper =
            ImportProgramTemplateUseCase(
                importPort = port,
                resolver =
                    ResolveProgramTemplateLiftsUseCase(
                        liftRepository = FakeLiftRepository(),
                        liftNameResolver = FakeLiftNameResolverPort(),
                    ),
                repository = programRepo,
                completeOnboarding = CompleteOnboardingUseCase(onboardingRepo, profileRepo),
                profileRepository = profileRepo,
                programChangeEventRepository = FakeProgramChangeEventRepository(),
                importDispatcher = UnconfinedTestDispatcher(testScheduler),
            )
        val result = wrapper(byteArrayOf(1), "plan.csv")
        assertTrue(result is ProgramTemplateImportOutcome.Success)
        assertTrue(programRepo.current != null && programRepo.current!!.isActive)
        assertTrue(onboardingRepo.current.isComplete)
    }

    @Test
    @DisplayName("IE-S5-M001 Mutation defense LibraryOnly must not complete onboarding")
    fun ieS5_m001_noCompleteOnLibraryOnly() = runTest {
        val programRepo = FakeProgramRepository()
        val onboardingRepo = FakeOnboardingRepository()
        val profileRepo = FakeUserTrainingProfileRepository()
        val recordingComplete =
            RecordingCompleteOnboardingUseCase(onboardingRepo, profileRepo)
        val port = FakeProgramTemplateImportPort(templateJson = singleTemplateJson())
        val core =
            buildUseCase(
                programRepo = programRepo,
                onboardingRepo = onboardingRepo,
                importPort = port,
                testScheduler = testScheduler,
                completeOnboarding = recordingComplete,
            )
        core(byteArrayOf(1), "p.json", libraryOnly)
        assertEquals(0, recordingComplete.invocationCount)
        assertFalse(onboardingRepo.current.isComplete)
    }

    @Test
    @DisplayName("IE-S5-008 Exported program id rebinds workout.programId for Room FKs")
    fun ieS5_008_exportIdRebindsWorkoutProgramId() = runTest {
        val programRepo = FakeProgramRepository()
        val onboardingRepo = FakeOnboardingRepository()
        val port =
            FakeProgramTemplateImportPort(
                templateJson =
                    """{"programs":[{"id":"exported-prog-1","template":{"programName":"Strength","cycleLength":4,"workouts":[{"name":"Day A","weekday":null,"position":0,"lifts":[{"name":"Squat","position":0,"sets":[{"position":0,"setTypeLabel":null,"repMin":5,"repMax":5,"rirGoal":2,"restSeconds":120,"isTopSet":true}],"supersetGroupKey":null}]}]}}]}""",
            )
        val result =
            buildUseCase(programRepo, onboardingRepo, port, testScheduler)(
                bytes = byteArrayOf(1),
                fileName = "stratum-programs.json",
                options = libraryOnly,
            )
        assertTrue(result is ProgramTemplateImportOutcome.Success)
        val saved = programRepo.getProgramById(com.stratum.domain.program.identity.ProgramId("exported-prog-1"))
        requireNotNull(saved)
        assertEquals("exported-prog-1", saved.id.value)
        assertTrue(
            saved.workouts.isNotEmpty() && saved.workouts.all { it.programId.value == "exported-prog-1" },
            "workouts must reference the exported program id, not the resolver's random id",
        )
        // Re-import after delete: must succeed (not FK) and restore the program.
        programRepo.deleteProgram(saved.id)
        val reimport =
            buildUseCase(programRepo, onboardingRepo, port, testScheduler)(
                bytes = byteArrayOf(1),
                fileName = "stratum-programs.json",
                options = libraryOnly,
            )
        assertTrue(reimport is ProgramTemplateImportOutcome.Success, "$reimport")
        assertEquals(1, (reimport as ProgramTemplateImportOutcome.Success).result.importedProgramIds.size)
        assertEquals(0, (reimport as ProgramTemplateImportOutcome.Success).result.skippedProgramCount)
        val restored = programRepo.getProgramById(com.stratum.domain.program.identity.ProgramId("exported-prog-1"))
        requireNotNull(restored)
        assertTrue(restored.workouts.all { it.programId == restored.id })
    }

    @Test
    @DisplayName("rebindProgramId rewrites nested workout.programId")
    fun rebindProgramId_rewritesWorkouts() {
        val original = ExportTestFixtures.minimalProgram("random-id", "P", isActive = false)
        assertEquals("random-id", original.workouts.first().programId.value)
        val rebound = rebindProgramId(original, com.stratum.domain.program.identity.ProgramId("export-id"))
        assertEquals("export-id", rebound.id.value)
        assertTrue(rebound.workouts.all { it.programId.value == "export-id" })
        assertFalse(rebound.isActive)
    }

    @Test
    @DisplayName("IE-S5-007 Multi-program second-save failure rolls back first program")
    fun ieS5_007_multiSaveRollback() = runTest {
        val failingRepo = FailOnNthSaveRepository(failOnSaveNumber = 2)
        val onboardingRepo = FakeOnboardingRepository()
        val port =
            FakeProgramTemplateImportPort(
                templateJson = multiProgramJson(listOf("prog-a", "prog-b")),
            )
        val result =
            buildUseCase(failingRepo, onboardingRepo, port, testScheduler)(
                bytes = byteArrayOf(1),
                fileName = "p.json",
                options = libraryOnly,
            )
        assertTrue(
            failingRepo.inner.getAllUserPrograms().isEmpty(),
            "First program must not remain after batch rollback",
        )
        assertFalse(onboardingRepo.current.isComplete)
        // Clean rollback: the PersistFailed branch carries the primary's message
        // and no diagnostics (no wrapper evidence when every delete succeeded).
        assertEquals(
            ProgramTemplateImportOutcome.PersistFailed(
                message = "Fake save #2 failed (configured fail on #2)",
                diagnostics = emptyList(),
            ),
            result,
        )
    }

    @Test
    @DisplayName("STR-256 Rollback delete failure surfaces typed diagnostics on the failure")
    fun str256_rollbackDeleteFailureExposesDiagnostics() = runTest {
        val failingRepo = FailOnNthSaveRepository(failOnSaveNumber = 2).apply {
            failDeleteIds = setOf(ProgramId("prog-a"))
        }
        val onboardingRepo = FakeOnboardingRepository()
        val port =
            FakeProgramTemplateImportPort(
                templateJson = multiProgramJson(listOf("prog-a", "prog-b")),
            )
        val result =
            buildUseCase(failingRepo, onboardingRepo, port, testScheduler)(
                bytes = byteArrayOf(1),
                fileName = "p.json",
                options = libraryOnly,
            )
        assertTrue(result is ProgramTemplateImportOutcome.PersistFailed)
        val failure = result as ProgramTemplateImportOutcome.PersistFailed
        // Every persisted id still got its delete attempt.
        assertEquals(listOf(ProgramId("prog-a")), failingRepo.deleteAttempts)
        // Diagnostics carry the program id and the delete cause as values.
        val diagnostics = failure.diagnostics
        assertEquals(1, diagnostics.size)
        val orphan = diagnostics.single()
        assertTrue(orphan is ProgramImportRollbackDiagnostic.RollbackDeleteFailed)
        orphan as ProgramImportRollbackDiagnostic.RollbackDeleteFailed
        assertEquals(ProgramId("prog-a"), orphan.programId)
        assertTrue(orphan.cause is IllegalStateException)
        assertSame(failingRepo.deleteCauses.single(), orphan.cause)
        // User-facing copy is unchanged: the branch message is the primary persist failure's.
        assertEquals("Fake save #2 failed (configured fail on #2)", failure.message)
        assertFalse(onboardingRepo.current.isComplete)
    }

    @Test
    @DisplayName("STR-256 Cancellation primary is rethrown as-is with rollback causes suppressed")
    fun str256_cancellationPrimaryRethrownAsIs() = runTest {
        val cancellation = CancellationException("save cancelled")
        val failingRepo = FailOnNthSaveRepository(failOnSaveNumber = 2).apply {
            saveThrowable = cancellation
            failDeleteIds = setOf(ProgramId("prog-a"))
        }
        val onboardingRepo = FakeOnboardingRepository()
        val port =
            FakeProgramTemplateImportPort(
                templateJson = multiProgramJson(listOf("prog-a", "prog-b")),
            )
        // Cancellation must propagate as cancellation, never as Result.failure:
        // invoke rethrows the CancellationException instead of returning it.
        var thrown: Throwable? = null
        try {
            buildUseCase(failingRepo, onboardingRepo, port, testScheduler)(
                bytes = byteArrayOf(1),
                fileName = "p.json",
                options = libraryOnly,
            )
        } catch (cancelled: CancellationException) {
            thrown = cancelled
        }
        // Never wrapped in a non-cancellation failure: invoke throws a
        // CancellationException carrying the primary's message. Coroutine
        // machinery may normalize the rethrown instance (message-only copy) once the
        // block has suspended, so identity is not asserted — the use case rethrows
        // the caught instance as-is and re-attaches the rollback causes to whatever
        // instance surfaces.
        val actual = requireNotNull(thrown) { "invoke must throw CancellationException" }
        assertFalse(actual is ProgramImportRollbackException)
        assertTrue(actual is CancellationException)
        assertEquals("save cancelled", actual.message)
        assertEquals(1, actual.suppressed.size)
        assertSame(failingRepo.deleteCauses.single(), actual.suppressed.single())
        assertFalse(onboardingRepo.current.isComplete)
    }

    @Test
    @DisplayName("CancellationException carried as an upload Result failure value is rethrown")
    fun valueCarriedCancellationFromUploadIsRethrown() = runTest {
        val programRepo = FakeProgramRepository()
        val onboardingRepo = FakeOnboardingRepository()
        val port =
            object : ProgramTemplateImportPort {
                override suspend fun uploadAndStart(
                    jobId: ProgramTemplateImportJobId,
                    bytes: ByteArray,
                    fileName: String,
                ): Result<Unit> = Result.failure(CancellationException("upload cancelled"))

                override suspend fun pollJobStatus(
                    jobId: ProgramTemplateImportJobId,
                ): Result<ProgramTemplateJobSnapshot> = error("poll must not run")

                override suspend fun downloadProgramTemplate(storagePath: String): Result<String> =
                    error("download must not run")
            }
        var thrown: Throwable? = null
        try {
            buildUseCase(programRepo, onboardingRepo, port, testScheduler)(
                bytes = byteArrayOf(1),
                fileName = "p.json",
                options = libraryOnly,
            )
        } catch (cancelled: CancellationException) {
            thrown = cancelled
        }
        val actual = requireNotNull(thrown) { "invoke must throw CancellationException" }
        assertEquals("upload cancelled", actual.message)
        assertFalse(onboardingRepo.current.isComplete)
    }

    private fun terminalPort(
        status: com.stratum.domain.program.importing.RemoteProgramTemplateJobStatus,
        errorMessage: String?,
        storagePath: String? = "programImports/test/job/template.json",
    ): FakeProgramTemplateImportPort =
        FakeProgramTemplateImportPort(
            snapshotSequence =
                listOf(
                    ProgramTemplateJobSnapshot(
                        status = status,
                        progressPhase = com.stratum.domain.program.importing.ProgramTemplateImportPhase.Failed,
                        progressMessage = null,
                        programTemplateStoragePath = storagePath,
                        errorMessage = errorMessage,
                    ),
                ),
        )

    @Test
    @DisplayName("Rejected terminal status returns the Rejected branch with the server message")
    fun rejectedTerminalStatus() = runTest {
        val port = terminalPort(
            status = com.stratum.domain.program.importing.RemoteProgramTemplateJobStatus.Rejected,
            errorMessage = "not a program",
        )
        val result =
            buildUseCase(FakeProgramRepository(), FakeOnboardingRepository(), port, testScheduler)(
                bytes = byteArrayOf(1),
                fileName = "p.json",
                options = libraryOnly,
            )
        assertEquals(ProgramTemplateImportOutcome.Rejected("not a program"), result)
    }

    @Test
    @DisplayName("Rejected terminal status without a server message uses the default copy")
    fun rejectedTerminalStatusDefaultMessage() = runTest {
        val port = terminalPort(
            status = com.stratum.domain.program.importing.RemoteProgramTemplateJobStatus.Rejected,
            errorMessage = null,
        )
        val result =
            buildUseCase(FakeProgramRepository(), FakeOnboardingRepository(), port, testScheduler)(
                bytes = byteArrayOf(1),
                fileName = "p.json",
                options = libraryOnly,
            )
        assertEquals(
            ProgramTemplateImportOutcome.Rejected("This file does not look like a workout program."),
            result,
        )
    }

    @Test
    @DisplayName("Failed terminal status returns the Failed branch with the server message")
    fun failedTerminalStatus() = runTest {
        val port = terminalPort(
            status = com.stratum.domain.program.importing.RemoteProgramTemplateJobStatus.Failed,
            errorMessage = "Conversion failed",
        )
        val result =
            buildUseCase(FakeProgramRepository(), FakeOnboardingRepository(), port, testScheduler)(
                bytes = byteArrayOf(1),
                fileName = "p.json",
                options = libraryOnly,
            )
        assertEquals(ProgramTemplateImportOutcome.Failed("Conversion failed"), result)
    }

    @Test
    @DisplayName("Ready terminal status without a template path returns ReadyWithoutPath")
    fun readyWithoutPath() = runTest {
        val port = terminalPort(
            status = com.stratum.domain.program.importing.RemoteProgramTemplateJobStatus.Ready,
            errorMessage = null,
            storagePath = null,
        )
        val result =
            buildUseCase(FakeProgramRepository(), FakeOnboardingRepository(), port, testScheduler)(
                bytes = byteArrayOf(1),
                fileName = "p.json",
                options = libraryOnly,
            )
        assertEquals(ProgramTemplateImportOutcome.ReadyWithoutPath, result)
    }

    @Test
    @DisplayName("Non-terminal job past the poll deadline returns TimedOut")
    fun pollTimeout() = runTest {
        val port = terminalPort(
            status = com.stratum.domain.program.importing.RemoteProgramTemplateJobStatus.Running,
            errorMessage = null,
            storagePath = null,
        )
        val result =
            buildUseCase(FakeProgramRepository(), FakeOnboardingRepository(), port, testScheduler)(
                bytes = byteArrayOf(1),
                fileName = "p.json",
                options = libraryOnly,
            )
        assertEquals(ProgramTemplateImportOutcome.TimedOut, result)
    }

    @Test
    @DisplayName("Embedding failure during template resolve rides Success as a diagnostic with the real cause")
    fun embeddingFailureRidesSuccessAsDiagnostic() = runTest {
        // STR-240 chunk 8b: ResolveProgramTemplateLiftsUseCase is the second
        // consumer of HybridLiftNameResolver.prepareBatch. Its embedding failure
        // degraded resolution to pure BM25 (the import still succeeds), and the
        // real cause rides the Success outcome in this flow's OperationalDiagnostic
        // vocabulary for the boundary to record exactly once — before this chunk
        // the template flow recorded it zero times.
        val failure = IllegalStateException("embedding down")
        val hybrid =
            HybridLiftNameResolver(
                bm25 = Bm25LiftNameResolver(),
                catalogEmbeddings = FakeLiftEmbeddingCatalog(),
                nameEmbedding = FakeLiftNameEmbeddingPort(result = Result.failure(failure)),
            )
        val port = FakeProgramTemplateImportPort(templateJson = singleTemplateJson("Imported A"))
        val result =
            buildUseCase(
                FakeProgramRepository(),
                FakeOnboardingRepository(),
                port,
                testScheduler,
                liftNameResolver = hybrid,
            )(
                bytes = byteArrayOf(1),
                fileName = "p.json",
                options = libraryOnly,
            )
        assertTrue(result is ProgramTemplateImportOutcome.Success)
        val success = result as ProgramTemplateImportOutcome.Success
        // Degraded-success behavior is unchanged: the program still imports.
        assertEquals(1, success.result.importedProgramIds.size)
        assertEquals(1, success.diagnostics.size)
        assertEquals("ProgramTemplateImport", success.diagnostics.single().tag)
        assertEquals(
            "Import lift name embedding failed — gray-zone matches may be unresolved. " +
                "Error: embedding down",
            success.diagnostics.single().message,
        )
        assertSame(failure, success.diagnostics.single().cause)
    }

    @Test
    @DisplayName("Download failure value returns OperationalFailed carrying the failure diagnostic")
    fun downloadFailureIsOperational() = runTest {
        val failure = IllegalStateException("storage down")
        val port =
            object : ProgramTemplateImportPort {
                override suspend fun uploadAndStart(
                    jobId: ProgramTemplateImportJobId,
                    bytes: ByteArray,
                    fileName: String,
                ): Result<Unit> = Result.success(Unit)

                override suspend fun pollJobStatus(
                    jobId: ProgramTemplateImportJobId,
                ): Result<ProgramTemplateJobSnapshot> =
                    Result.success(
                        ProgramTemplateJobSnapshot(
                            status = com.stratum.domain.program.importing.RemoteProgramTemplateJobStatus.Ready,
                            progressPhase = com.stratum.domain.program.importing.ProgramTemplateImportPhase.Parsing,
                            progressMessage = null,
                            programTemplateStoragePath = "programImports/test/job/template.json",
                            errorMessage = null,
                        ),
                    )

                override suspend fun downloadProgramTemplate(storagePath: String): Result<String> =
                    Result.failure(failure)
            }
        val result =
            buildUseCase(
                FakeProgramRepository(),
                FakeOnboardingRepository(),
                port,
                testScheduler,
            )(
                bytes = byteArrayOf(1),
                fileName = "p.json",
                options = libraryOnly,
            )
        // STR-237: the record rides the branch as a diagnostic (legacy tag +
        // rendered message, real throwable as cause); the boundary drains it
        // exactly once.
        val failed = result as ProgramTemplateImportOutcome.OperationalFailed
        assertEquals(1, failed.diagnostics.size)
        assertEquals("ProgramTemplateImport", failed.diagnostics.single().tag)
        assertEquals("Program import failed: storage down", failed.diagnostics.single().message)
        assertSame(failure, failed.diagnostics.single().cause)
    }
}
