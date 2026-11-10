package com.stratum.application.program

import app.cash.turbine.test
import com.stratum.application.lift.ObserveLiftCatalogUseCase
import com.stratum.application.training.ObserveActiveSessionUseCase
import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.core.testing.program.FakeProgramRepository
import com.stratum.core.testing.training.FakeTrainingLogRepository
import com.stratum.core.testing.training.WorkoutLoggingFixtures
import com.stratum.core.testing.training.WorkoutLoggingFixtures.benchLift
import com.stratum.core.testing.training.WorkoutLoggingFixtures.inProgressSession
import com.stratum.core.testing.training.WorkoutLoggingFixtures.twoWorkoutProgram
import com.stratum.domain.program.Program
import com.stratum.domain.program.identity.ProgramId
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * [ObserveProgramLibraryUseCase] composition tests (STR-288): the library
 * merge/dedupe moved out of `ProgramLibraryViewModel` must preserve the VM's
 * prior behavior — active-first ordering, newest-first library inventory, and
 * catalog/session passthrough in one [ProgramLibrarySnapshot].
 */
class ObserveProgramLibraryUseCaseTest {

    @Test
    @DisplayName("Active program leads, library follows newest-first, session starts null")
    fun emitsMergedProgramsWithNullSession() = runTest {
        val programRepo = FakeProgramRepository(twoWorkoutProgram())
        programRepo.saveToLibrary(libraryProgram(id = "lib-old", createdAt = "2026-01-02T00:00:00Z"))
        programRepo.saveToLibrary(libraryProgram(id = "lib-new", createdAt = "2026-02-01T00:00:00Z"))
        val subject = buildUseCase(programRepo)

        subject().test {
            val snapshot = awaitItem()
            assertEquals(
                listOf(WorkoutLoggingFixtures.PROGRAM_ID, "lib-new", "lib-old"),
                snapshot.programs.map { it.id.value },
            )
            assertTrue(snapshot.programs.first().isActive)
            assertNull(snapshot.activeSession)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Active session passes through into the snapshot")
    fun activeSessionPassesThrough() = runTest {
        val programRepo = FakeProgramRepository(twoWorkoutProgram())
        val trainingLog = FakeTrainingLogRepository()
        val session = inProgressSession(id = "ts-live-1")
        trainingLog.seedSession(session)
        val subject = buildUseCase(programRepo, trainingLog)

        subject().test {
            val snapshot = awaitItem()
            assertEquals(session.id, snapshot.activeSession?.id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Lift catalog passes through into the snapshot")
    fun liftCatalogPassesThrough() = runTest {
        val programRepo = FakeProgramRepository(twoWorkoutProgram())
        val liftRepo = FakeLiftRepository(initialLifts = listOf(benchLift()))
        val subject = buildUseCase(programRepo, liftRepository = liftRepo)

        subject().test {
            val snapshot = awaitItem()
            assertEquals(listOf(benchLift().id), snapshot.lifts.map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("Empty inventory emits empty programs with null session")
    fun emptyInventoryEmitsEmpty() = runTest {
        val subject = buildUseCase(FakeProgramRepository())

        subject().test {
            val snapshot = awaitItem()
            assertTrue(snapshot.programs.isEmpty())
            assertNull(snapshot.activeSession)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun libraryProgram(id: String, createdAt: String): Program {
        val programId = ProgramId(id)
        return twoWorkoutProgram().copy(
            id = programId,
            name = id,
            createdAt = Instant.parse(createdAt),
            workouts = twoWorkoutProgram().workouts.map { it.copy(programId = programId) },
        )
    }

    private fun buildUseCase(
        programRepo: FakeProgramRepository,
        trainingLog: FakeTrainingLogRepository = FakeTrainingLogRepository(),
        liftRepository: FakeLiftRepository = FakeLiftRepository(),
    ): ObserveProgramLibraryUseCase =
        ObserveProgramLibraryUseCase(
            ObserveProgramUseCase(programRepo),
            ObserveLibraryProgramsUseCase(programRepo),
            ObserveLiftCatalogUseCase(liftRepository),
            ObserveActiveSessionUseCase(trainingLog),
        )
}
