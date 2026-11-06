package com.stratum.application.program

import com.stratum.core.testing.logging.FakeLoggingPort
import com.stratum.core.testing.program.FakeProgramRepository
import com.stratum.core.testing.training.WorkoutLoggingFixtures
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.identity.WorkoutLiftId
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.program.WorkoutLift
import com.stratum.domain.program.ProgressionScheme

class ActivateStoredProgramUseCaseTest {
    @Test
    @DisplayName("Activates a library program and demotes prior active")
    fun activatesLibraryProgram() = runTest {
        val active =
            WorkoutLoggingFixtures.twoWorkoutProgram().copy(
                id = com.stratum.domain.program.identity.ProgramId("active"),
                workouts =
                    WorkoutLoggingFixtures.defaultWorkouts().map { workout ->
                        workout.copy(
                            programId = com.stratum.domain.program.identity.ProgramId("active"),
                            workoutLifts =
                                listOf(
                                    WorkoutLift(
                                        id = WorkoutLiftId("wl-a"),
                                        workoutId = workout.id,
                                        liftId = LiftId("bench"),
                                        progressionScheme = ProgressionScheme.Linear,
                                        position = 0,
                                        sets = emptyList(),
                                    ),
                                ),
                        )
                    },
            )
        val library =
            WorkoutLoggingFixtures.twoWorkoutProgram().copy(
                id = com.stratum.domain.program.identity.ProgramId("library"),
                isActive = false,
                workouts =
                    WorkoutLoggingFixtures.defaultWorkouts().map { workout ->
                        workout.copy(
                            programId = com.stratum.domain.program.identity.ProgramId("library"),
                            id =
                                com.stratum.domain.program.identity.WorkoutId(
                                    "lib-${workout.id.value}",
                                ),
                            workoutLifts =
                                listOf(
                                    WorkoutLift(
                                        id = WorkoutLiftId("wl-l"),
                                        workoutId =
                                            com.stratum.domain.program.identity.WorkoutId(
                                                "lib-${workout.id.value}",
                                            ),
                                        liftId = LiftId("squat"),
                                        progressionScheme = ProgressionScheme.Linear,
                                        position = 0,
                                        sets = emptyList(),
                                    ),
                                ),
                        )
                    },
            )
        val repository = FakeProgramRepository(active)
        repository.saveToLibrary(library)
        val useCase = ActivateStoredProgramUseCase(repository, FakeLoggingPort())

        val result = useCase(library.id)

        assertEquals(ActivateStoredProgramResult.Success, result)
        assertEquals(library.id, repository.getProgram()?.id)
        assertFalse(repository.getProgramById(active.id)!!.isActive)
        assertTrue(repository.getProgramById(library.id)!!.isActive)
    }

    @Test
    @DisplayName("Fails when program is unknown")
    fun failsWhenUnknown() = runTest {
        val repository = FakeProgramRepository(WorkoutLoggingFixtures.twoWorkoutProgram())
        val useCase = ActivateStoredProgramUseCase(repository, FakeLoggingPort())

        val result = useCase(com.stratum.domain.program.identity.ProgramId("missing"))

        assertEquals(
            ActivateStoredProgramResult.ProgramNotFound(
                com.stratum.domain.program.identity.ProgramId("missing"),
            ),
            result,
        )
    }

    @Test
    @DisplayName("Refuses to activate import-history")
    fun refusesImportHistory() = runTest {
        val repository = FakeProgramRepository(WorkoutLoggingFixtures.twoWorkoutProgram())
        val useCase = ActivateStoredProgramUseCase(repository, FakeLoggingPort())

        val result = useCase(com.stratum.domain.program.identity.ProgramId.ImportHistory)

        assertEquals(
            ActivateStoredProgramResult.ImportHistoryProgram(
                com.stratum.domain.program.identity.ProgramId.ImportHistory,
            ),
            result,
        )
    }

    @Test
    @DisplayName("Repository save failure returns PersistenceFailed with one error log")
    fun persistenceFailureOnSaveError() = runTest {
        val library =
            WorkoutLoggingFixtures.twoWorkoutProgram().copy(
                id = com.stratum.domain.program.identity.ProgramId("library"),
                isActive = false,
                workouts =
                    WorkoutLoggingFixtures.defaultWorkouts().map { workout ->
                        workout.copy(
                            programId = com.stratum.domain.program.identity.ProgramId("library"),
                            workoutLifts =
                                listOf(
                                    WorkoutLift(
                                        id = WorkoutLiftId("wl-l"),
                                        workoutId = workout.id,
                                        liftId = LiftId("squat"),
                                        progressionScheme = ProgressionScheme.Linear,
                                        position = 0,
                                        sets = emptyList(),
                                    ),
                                ),
                        )
                    },
            )
        val repository = FakeProgramRepository(library, failOnSave = true)
        val logging = FakeLoggingPort()
        val useCase = ActivateStoredProgramUseCase(repository, logging)

        val result = useCase(library.id)

        assertEquals(ActivateStoredProgramResult.PersistenceFailed, result)
        assertEquals(1, logging.errors.size)
    }
}
