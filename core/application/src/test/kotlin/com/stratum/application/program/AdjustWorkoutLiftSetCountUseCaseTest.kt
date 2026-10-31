package com.stratum.application.program

import com.stratum.core.testing.program.FakeProgramRepository
import com.stratum.core.testing.training.FakeProgramChangeEventRepository
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.Weekday
import com.stratum.domain.program.identity.LiftSetId
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.training.ProgramChangeEventType
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import com.stratum.domain.program.LiftSet
import com.stratum.domain.program.Program
import com.stratum.domain.program.SetType
import com.stratum.domain.program.Workout
import com.stratum.domain.program.WorkoutLift
import com.stratum.domain.program.ProgressionScheme

class AdjustWorkoutLiftSetCountUseCaseTest {

    private val workoutLiftId = WorkoutLiftId("wl-1")

    @Test
    @DisplayName("positive delta appends non-top sets")
    fun addSets() = runTest {
        val repository = FakeProgramRepository(programWithSets(3))
        val events = FakeProgramChangeEventRepository()
        val useCase = AdjustWorkoutLiftSetCountUseCase(repository, events)

        val result = useCase(workoutLiftId, delta = 1)

        assertTrue(result.isSuccess)
        val sets = repository.getProgram()!!.workouts.first().workoutLifts.first().sets
        assertEquals(4, sets.size)
        assertEquals(false, sets.last().isTopSet)
        assertEquals(
            1,
            events.getEventsForProgram(ProgramId("p-1")).events.count {
                it.eventType == ProgramChangeEventType.WORKOUT_EDITED
            },
        )
    }

    @Test
    @DisplayName("negative delta drops trailing non-top sets and never below 1")
    fun removeSetsKeepsFloor() = runTest {
        val repository = FakeProgramRepository(programWithSets(4))
        val useCase = AdjustWorkoutLiftSetCountUseCase(repository, FakeProgramChangeEventRepository())

        useCase(workoutLiftId, delta = -2).getOrThrow()
        assertEquals(2, repository.getProgram()!!.workouts.first().workoutLifts.first().sets.size)

        useCase(workoutLiftId, delta = -10).getOrThrow()
        assertEquals(1, repository.getProgram()!!.workouts.first().workoutLifts.first().sets.size)
    }

    private fun programWithSets(setCount: Int): Program {
        val sets =
            (0 until setCount).map { index ->
                LiftSet(
                    id = LiftSetId("ls-$index"),
                    workoutLiftId = workoutLiftId,
                    position = index,
                    setType = SetType.Standard,
                    repMin = 8,
                    repMax = 12,
                    rirGoal = 2,
                    isTopSet = index == 0,
                )
            }
        return Program(
            id = ProgramId("p-1"),
            name = "Split",
            createdAt = Instant.parse("2026-01-01T00:00:00Z"),
            cycleLength = 1,
            trainingWeekdays = setOf(Weekday.Monday),
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
                                    id = workoutLiftId,
                                    workoutId = WorkoutId("w-1"),
                                    liftId = LiftId("bench-press"),
                                    progressionScheme = ProgressionScheme.Double,
                                    position = 0,
                                    sets = sets,
                                ),
                            ),
                    ),
                ),
        )
    }
}
