package com.stratum.application.program

import com.stratum.core.testing.logging.FakeLoggingPort
import com.stratum.core.testing.program.FakeProgramRepository
import com.stratum.core.testing.training.NoOpProgramChangeEventRepository
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.Weekday
import com.stratum.domain.program.identity.LiftSetId
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.program.identity.WorkoutSupersetGroupId
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import com.stratum.domain.training.ProgramChangeEvent
import com.stratum.domain.training.ProgramChangeEventPayload
import com.stratum.domain.training.ProgramChangeEventType
import com.stratum.domain.training.identity.ProgramChangeEventId
import com.stratum.domain.workout.LiftSwapped
import com.stratum.domain.program.LiftSet
import com.stratum.domain.program.Program
import com.stratum.domain.program.SetType
import com.stratum.domain.program.Workout
import com.stratum.domain.program.WorkoutLift
import com.stratum.domain.program.ProgressionScheme

class SaveWorkoutUseCaseTest {
    @Test
    @DisplayName("Given active program when save workout then only target workout is replaced")
    fun saveWorkoutMergesSingleWorkout() = runTest {
        val repository = FakeProgramRepository(TWO_WORKOUT_PROGRAM)
        val useCase = SaveWorkoutUseCase(repository, NoOpProgramChangeEventRepository(), FakeLoggingPort())
        val updatedWorkout =
            TWO_WORKOUT_PROGRAM.workouts.first().copy(
                workoutLifts =
                    listOf(
                        WorkoutLift(
                            id = WorkoutLiftId("wl-1"),
                            workoutId = WorkoutId("w-1"),
                            liftId = LiftId("bench-press"),
                            progressionScheme = ProgressionScheme.Linear,
                            position = 0,
                            sets =
                                listOf(
                                    LiftSet(
                                        id = LiftSetId("ls-1"),
                                        workoutLiftId = WorkoutLiftId("wl-1"),
                                        position = 0,
                                        setType = SetType.Standard,
                                        repMin = 6,
                                        repMax = 8,
                                        rirGoal = 1,
                                        isTopSet = true,
                                        restSeconds = 90,
                                    ),
                                ),
                            supersetGroupId = WorkoutSupersetGroupId("sg-1"),
                            useRestTimerBetweenLifts = true,
                        ),
                    ),
            )

        val result = useCase(WorkoutId("w-1"), updatedWorkout)

        assertTrue(result.isSuccess)
        val saved = repository.getProgram()!!
        assertEquals(2, saved.workouts.size)
        assertEquals(1, saved.workouts[0].workoutLifts[0].sets.size)
        assertEquals(90, saved.workouts[0].workoutLifts[0].sets[0].restSeconds)
        assertEquals(WorkoutSupersetGroupId("sg-1"), saved.workouts[0].workoutLifts[0].supersetGroupId)
        assertEquals(2, saved.workouts[1].workoutLifts.size)
    }

    @Test
    @DisplayName("When no active program exists then save returns failure")
    fun saveFailsWhenNoProgram() = runTest {
        val repository = FakeProgramRepository(null)
        val useCase = SaveWorkoutUseCase(repository, NoOpProgramChangeEventRepository(), FakeLoggingPort())

        val result = useCase(WorkoutId("w-1"), sampleWorkout())

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }

    @Test
    @DisplayName("When workout id is missing then save returns failure")
    fun saveFailsWhenWorkoutNotFound() = runTest {
        val repository = FakeProgramRepository(TWO_WORKOUT_PROGRAM)
        val useCase = SaveWorkoutUseCase(repository, NoOpProgramChangeEventRepository(), FakeLoggingPort())

        val result = useCase(WorkoutId("missing"), sampleWorkout())

        assertTrue(result.isFailure)
        assertEquals(TWO_WORKOUT_PROGRAM, repository.getProgram())
    }

    @Test
    @DisplayName("When programId targets inactive library program then workout merges without activating")
    fun saveWorkoutInLibraryProgramPreservesInactive() = runTest {
        val active = TWO_WORKOUT_PROGRAM
        val library =
            TWO_WORKOUT_PROGRAM.copy(
                id = ProgramId("p-library"),
                isActive = false,
                name = "Library Split",
                workouts =
                    TWO_WORKOUT_PROGRAM.workouts.map { workout ->
                        val libraryWorkoutId = WorkoutId("lib-${workout.id.value}")
                        workout.copy(
                            id = libraryWorkoutId,
                            programId = ProgramId("p-library"),
                            workoutLifts =
                                workout.workoutLifts.map { lift ->
                                    lift.copy(workoutId = libraryWorkoutId)
                                },
                        )
                    },
            )
        val repository = FakeProgramRepository(active)
        repository.saveToLibrary(library)
        val useCase = SaveWorkoutUseCase(repository, NoOpProgramChangeEventRepository(), FakeLoggingPort())
        val targetWorkout = library.workouts.first()
        val updated =
            targetWorkout.copy(
                name = "Library Push Updated",
            )

        val result = useCase(targetWorkout.id, updated, programId = library.id)

        assertTrue(result.isSuccess)
        val savedLibrary = repository.getProgramById(library.id)!!
        assertEquals(false, savedLibrary.isActive)
        assertEquals("Library Push Updated", savedLibrary.workouts.first().name)
        assertEquals(active.id, repository.getProgram()?.id)
    }

    private fun sampleWorkout(): Workout =
        Workout(
            id = WorkoutId("missing"),
            programId = ProgramId("p-1"),
            name = "Missing",
            weekday = Weekday.Monday,
            position = 0,
            workoutLifts = emptyList(),
        )

    private companion object {
        val TWO_WORKOUT_PROGRAM: Program =
            Program(
                id = ProgramId("p-1"),
                name = "Split",
                createdAt = Instant.parse("2026-06-26T12:00:00Z"),
                cycleLength = 2,
                trainingWeekdays = setOf(Weekday.Monday, Weekday.Wednesday),
                workouts =
                    listOf(
                        Workout(
                            id = WorkoutId("w-1"),
                            programId = ProgramId("p-1"),
                            name = "Push",
                            weekday = Weekday.Monday,
                            position = 0,
                            workoutLifts =
                                listOf(
                                    WorkoutLift(
                                        id = WorkoutLiftId("wl-1"),
                                        workoutId = WorkoutId("w-1"),
                                        liftId = LiftId("bench-press"),
                                        progressionScheme = ProgressionScheme.Linear,
                                        position = 0,
                                        sets =
                                            listOf(
                                                LiftSet(
                                                    LiftSetId("ls-1"),
                                                    WorkoutLiftId("wl-1"),
                                                    0,
                                                    SetType.Standard,
                                                    8,
                                                    12,
                                                    2,
                                                    isTopSet = true,
                                                ),
                                            ),
                                    ),
                                ),
                        ),
                        Workout(
                            id = WorkoutId("w-2"),
                            programId = ProgramId("p-1"),
                            name = "Pull",
                            weekday = Weekday.Wednesday,
                            position = 1,
                            workoutLifts =
                                listOf(
                                    WorkoutLift(
                                        id = WorkoutLiftId("wl-2"),
                                        workoutId = WorkoutId("w-2"),
                                        liftId = LiftId("back-squat"),
                                        progressionScheme = ProgressionScheme.Double,
                                        position = 0,
                                        sets = emptyList(),
                                    ),
                                    WorkoutLift(
                                        id = WorkoutLiftId("wl-3"),
                                        workoutId = WorkoutId("w-2"),
                                        liftId = LiftId("bench-press"),
                                        progressionScheme = ProgressionScheme.Linear,
                                        position = 1,
                                        sets = emptyList(),
                                    ),
                                ),
                        ),
                    ),
            )
    }

    @Test
    @DisplayName("WL-036 SaveWorkoutUseCase appends WORKOUT_EDITED event")
    fun wl036_saveWorkoutAppendsWorkoutEditedEvent() = runTest {
        val events = com.stratum.core.testing.training.FakeProgramChangeEventRepository()
        val program = com.stratum.core.testing.training.WorkoutLoggingFixtures.twoWorkoutProgram()
        val useCase = SaveWorkoutUseCase(FakeProgramRepository(program), events, FakeLoggingPort())

        useCase(com.stratum.core.testing.training.WorkoutLoggingFixtures.workoutAId, program.workouts.first()).getOrThrow()

        val appended = events.getEventsForProgram(com.stratum.core.testing.training.WorkoutLoggingFixtures.programId)
        assertEquals(1, appended.size)
        assertEquals(com.stratum.domain.training.ProgramChangeEventType.WORKOUT_EDITED, appended.single().eventType)
        val payload = appended.single().payload as com.stratum.domain.training.ProgramChangeEventPayload.WorkoutEdited
        assertEquals(com.stratum.core.testing.training.WorkoutLoggingFixtures.workoutAId, payload.workoutId)
    }

    @Test
    @DisplayName("WL-040 Program change events are append-only")
    fun wl040_eventsAreAppendOnly() = runTest {
        val events = com.stratum.core.testing.training.FakeProgramChangeEventRepository()
        events.seed(
            com.stratum.domain.training.ProgramChangeEvent(
                id = com.stratum.domain.training.identity.ProgramChangeEventId("evt-1"),
                programId = com.stratum.core.testing.training.WorkoutLoggingFixtures.programId,
                eventType = com.stratum.domain.training.ProgramChangeEventType.LIFT_SWAPPED,
                occurredAt = java.time.Instant.parse("2025-01-01T00:00:00Z"),
                payload = com.stratum.domain.training.ProgramChangeEventPayload.LiftSwapped(
                    workoutLiftId = WorkoutLiftId("wl-1"),
                    previousLiftId = LiftId("lift-bench"),
                    newLiftId = LiftId("lift-squat"),
                ),
            ),
        )
        val useCase = SaveWorkoutUseCase(
            FakeProgramRepository(com.stratum.core.testing.training.WorkoutLoggingFixtures.twoWorkoutProgram()),
            events,
            FakeLoggingPort(),
        )

        useCase(
            com.stratum.core.testing.training.WorkoutLoggingFixtures.workoutAId,
            com.stratum.core.testing.training.WorkoutLoggingFixtures.twoWorkoutProgram().workouts.first(),
        ).getOrThrow()

        val all = events.getEventsForProgram(com.stratum.core.testing.training.WorkoutLoggingFixtures.programId)
        assertEquals(2, all.size)
        assertEquals(com.stratum.domain.training.identity.ProgramChangeEventId("evt-1"), all.first().id)
    }
}
