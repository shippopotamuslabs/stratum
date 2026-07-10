package com.stratum.domain.program

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

        assertTrue(result.isSuccess)
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

        assertTrue(result.isFailure)
    }
}
