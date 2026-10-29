package com.stratum.application.workout

import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.core.testing.program.FakeProgramRepository
import com.stratum.domain.lift.CatalogNoteInput
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.WorkoutLiftNoteInput
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.onboarding.Weekday
import com.stratum.domain.program.Program
import com.stratum.domain.program.ProgressionScheme
import com.stratum.domain.program.Workout
import com.stratum.domain.program.WorkoutLift
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.program.identity.WorkoutLiftId
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("GetSessionPinnedNoteListsUseCase")
class GetSessionPinnedNoteListsUseCaseTest {

    private val workoutLiftId = WorkoutLiftId("wl-1")
    private val liftId = LiftId("squat")

    @Test
    @DisplayName("empty storage yields empty bodies per slot and lift")
    fun emptyBodies() = runTest {
        val useCase =
            GetSessionPinnedNoteListsUseCase(
                FakeProgramRepository(sampleProgram()),
                FakeLiftRepository(listOf(sampleLift())),
            )

        val lists = useCase(sampleProgram().workouts.single())

        assertEquals(listOf(workoutLiftId), lists.byWorkoutLiftId.keys.toList())
        assertEquals(listOf(liftId), lists.byLiftId.keys.toList())
        assertEquals(emptyList<String>(), lists.byWorkoutLiftId.getValue(workoutLiftId))
        assertEquals(emptyList<String>(), lists.byLiftId.getValue(liftId))
    }

    @Test
    @DisplayName("stored multi-note lists load per slot and per lift")
    fun loadsAllPins() = runTest {
        val programs = FakeProgramRepository(sampleProgram())
        programs.setWorkoutLiftPinnedNotes(
            workoutLiftId,
            listOf(WorkoutLiftNoteInput(body = "tuck"), WorkoutLiftNoteInput(body = "chest up")),
        )
        val lifts = FakeLiftRepository(listOf(sampleLift()))
        lifts.setPinnedNotes(
            liftId,
            listOf(CatalogNoteInput(body = "belt on"), CatalogNoteInput(body = "knees out")),
        )
        val useCase = GetSessionPinnedNoteListsUseCase(programs, lifts)

        val lists = useCase(sampleProgram().workouts.single())

        assertEquals(listOf("tuck", "chest up"), lists.byWorkoutLiftId[workoutLiftId])
        assertEquals(listOf("belt on", "knees out"), lists.byLiftId[liftId])
    }

    private fun sampleLift(): Lift =
        Lift(
            id = liftId,
            name = "Squat",
            movementPatterns = setOf(MovementPattern.SquatPattern),
            primaryMuscles = setOf(MuscleGroup.Quad),
            secondaryMuscles = emptySet(),
            incrementTiers = emptyList(),
            presetTags = emptySet(),
            enabled = true,
        )

    private fun sampleProgram(): Program =
        Program(
            id = ProgramId("p-1"),
            name = "P",
            createdAt = Instant.EPOCH,
            cycleLength = 1,
            trainingWeekdays = setOf(Weekday.Monday),
            workouts =
                listOf(
                    Workout(
                        id = WorkoutId("w-1"),
                        programId = ProgramId("p-1"),
                        name = "Day",
                        weekday = Weekday.Monday,
                        position = 0,
                        workoutLifts =
                            listOf(
                                WorkoutLift(
                                    id = workoutLiftId,
                                    workoutId = WorkoutId("w-1"),
                                    liftId = liftId,
                                    progressionScheme = ProgressionScheme.Double,
                                    position = 0,
                                    sets = emptyList(),
                                ),
                            ),
                    ),
                ),
        )
}
