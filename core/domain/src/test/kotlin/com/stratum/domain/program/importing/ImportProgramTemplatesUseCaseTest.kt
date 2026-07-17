package com.stratum.domain.program.importing

import com.stratum.core.testing.importing.FakeLiftNameResolverPort
import com.stratum.core.testing.importing.FakeProgramTemplateImportPort
import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.core.testing.onboarding.FakeOnboardingRepository
import com.stratum.core.testing.onboarding.FakeUserTrainingProfileRepository
import com.stratum.core.testing.program.FakeProgramRepository
import com.stratum.core.testing.training.FakeProgramChangeEventRepository
import com.stratum.domain.export.ExportTestFixtures
import com.stratum.domain.onboarding.CompleteOnboardingUseCase
import com.stratum.domain.onboarding.OnboardingRepository
import com.stratum.domain.onboarding.UserTrainingProfile
import com.stratum.domain.onboarding.UserTrainingProfileRepository
import com.stratum.domain.program.Program
import com.stratum.domain.program.ProgramRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

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
        importPort: FakeProgramTemplateImportPort,
        testScheduler: TestCoroutineScheduler,
        completeOnboarding: CompleteOnboardingUseCase? = null,
    ): ImportProgramTemplatesUseCase {
        val profileRepo = FakeUserTrainingProfileRepository()
        val liftRepo = FakeLiftRepository()
        return ImportProgramTemplatesUseCase(
            importPort = importPort,
            resolver =
                ResolveProgramTemplateLiftsUseCase(
                    liftRepository = liftRepo,
                    liftNameResolver = FakeLiftNameResolverPort(),
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
     * (1-based). Used to prove multi-program import rolls back earlier saves.
     */
    private class FailOnNthSaveRepository(
        private val failOnSaveNumber: Int,
        val inner: FakeProgramRepository = FakeProgramRepository(),
    ) : ProgramRepository by inner {
        private var saveCount = 0

        override suspend fun saveProgram(program: Program) {
            saveCount++
            check(saveCount < failOnSaveNumber) {
                "Fake save #$saveCount failed (configured fail on #$failOnSaveNumber)"
            }
            inner.saveProgram(program)
        }

        override suspend fun saveToLibrary(program: Program) {
            saveCount++
            check(saveCount < failOnSaveNumber) {
                "Fake save #$saveCount failed (configured fail on #$failOnSaveNumber)"
            }
            inner.saveToLibrary(program)
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
        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrThrow().importedProgramIds.size)
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
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().importedProgramIds.isEmpty())
        assertEquals(1, result.getOrThrow().skippedProgramCount)
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
        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrThrow().importedProgramIds.size)
        assertEquals(0, result.getOrThrow().skippedProgramCount)
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
        assertTrue(result.isSuccess)
        val imported = result.getOrThrow().importedProgramIds.map { it.value }
        assertEquals(listOf("prog-b"), imported)
        assertEquals(1, result.getOrThrow().skippedProgramCount)
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
        assertTrue(result.isSuccess)
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
        assertTrue(result.isSuccess)
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
        assertTrue(reimport.isSuccess, reimport.exceptionOrNull()?.message)
        assertEquals(1, reimport.getOrThrow().importedProgramIds.size)
        assertEquals(0, reimport.getOrThrow().skippedProgramCount)
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
        assertTrue(result.isFailure)
        assertTrue(
            failingRepo.inner.getAllUserPrograms().isEmpty(),
            "First program must not remain after batch rollback",
        )
        assertFalse(onboardingRepo.current.isComplete)
    }
}
