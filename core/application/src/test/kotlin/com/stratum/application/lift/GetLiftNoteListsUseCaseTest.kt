package com.stratum.application.lift

import com.stratum.core.testing.lift.FakeLiftNotesRepository
import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.core.testing.program.FakeProgramRepository
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.lift.identity.LiftSessionNoteId
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.onboarding.Weekday
import com.stratum.domain.program.Program
import com.stratum.domain.program.ProgressionScheme
import com.stratum.domain.program.Workout
import com.stratum.domain.program.WorkoutLift
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.training.identity.TrainingSessionId
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import com.stratum.domain.lift.CatalogNoteInput
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.LiftSessionNote
import com.stratum.domain.lift.MAX_LIFT_NOTE_LENGTH
import com.stratum.domain.lift.WorkoutLiftNoteInput

@DisplayName("GetLiftNoteListsUseCase")
class GetLiftNoteListsUseCaseTest {

    private val sessionId = TrainingSessionId("ts-1")
    private val workoutLiftId = WorkoutLiftId("wl-1")
    private val liftId = LiftId("squat")

    @Test
    @DisplayName("empty storage yields empty lists")
    fun emptyLists() = runTest {
        val useCase = useCase(FakeLiftNotesRepository())

        assertEquals(LiftNoteLists(), useCase(sessionId, workoutLiftId, liftId))
    }

    @Test
    @DisplayName("each tab loads its ordered list")
    fun loadsAllTabs() = runTest {
        val notes = FakeLiftNotesRepository()
        notes.upsertSessionNote(note("n-2", "second"))
        notes.upsertSessionNote(note("n-1", "first"))
        val lifts = FakeLiftRepository(listOf(sampleLift()))
        lifts.setPinnedNotes(liftId, listOf(CatalogNoteInput(body = "belt on")))
        val programs = FakeProgramRepository(sampleProgram())
        programs.setWorkoutLiftPinnedNotes(
            workoutLiftId,
            listOf(WorkoutLiftNoteInput(body = "tuck")),
        )
        val useCase = useCase(notes, lifts, programs)

        val lists = useCase(sessionId, workoutLiftId, liftId)

        // Fake session upserts append; order here follows insertion.
        assertEquals(listOf("second", "first"), lists.session.map { it.body })
        assertEquals(listOf("tuck"), lists.workout.map { it.body })
        assertEquals(listOf("belt on"), lists.lift.map { it.body })
    }

    @Test
    @DisplayName("explicit overlay workout notes win over the repository list")
    fun overlayWorkoutNotesPreferred() = runTest {
        val programs = FakeProgramRepository(sampleProgram())
        programs.setWorkoutLiftPinnedNotes(
            workoutLiftId,
            listOf(WorkoutLiftNoteInput(body = "repo pin")),
        )
        val useCase = useCase(programs = programs)

        val lists =
            useCase(
                sessionId,
                workoutLiftId,
                liftId,
                overlayWorkoutNotes = listOf("overlay one", "overlay two"),
            )

        assertEquals(listOf("overlay one", "overlay two"), lists.workout.map { it.body })
        assertEquals(listOf(0, 1), lists.workout.map { it.position })
        assertTrue(lists.workout.all { it.id.value.startsWith("${workoutLiftId.value}::overlay::") })
    }

    @Test
    @DisplayName("null overlay notes fall back to the repository list")
    fun nullOverlayFallsBackToRepository() = runTest {
        val programs = FakeProgramRepository(sampleProgram())
        programs.setWorkoutLiftPinnedNotes(
            workoutLiftId,
            listOf(WorkoutLiftNoteInput(body = "repo pin")),
        )
        val useCase = useCase(programs = programs)

        val lists = useCase(sessionId, workoutLiftId, liftId, overlayWorkoutNotes = null)

        assertEquals(listOf("repo pin"), lists.workout.map { it.body })
    }

    @Test
    @DisplayName("legacy scalar pins migrate to rows on first load with stable ids")
    fun legacyPinsMigrateOnLoad() = runTest {
        val programs = FakeProgramRepository(sampleProgram(slotPin = "slot legacy"))
        val lifts = FakeLiftRepository(listOf(sampleLift().copy(pinnedNote = "catalog legacy")))
        val useCase = useCase(lifts = lifts, programs = programs)

        val first = useCase(sessionId, workoutLiftId, liftId)
        assertEquals(listOf("slot legacy"), first.workout.map { it.body })
        assertEquals(listOf("catalog legacy"), first.lift.map { it.body })

        val second = useCase(sessionId, workoutLiftId, liftId)
        assertEquals(first.workout.single().id, second.workout.single().id)
        assertEquals(first.lift.single().id, second.lift.single().id)
    }

    @Test
    @DisplayName("legacy pins migrate sanitized (trimmed and length-capped)")
    fun legacyPinsMigrateSanitized() = runTest {
        val programs = FakeProgramRepository(sampleProgram(slotPin = "  padded  "))
        val lifts = FakeLiftRepository(listOf(sampleLift().copy(pinnedNote = "x".repeat(2500))))
        val useCase = useCase(lifts = lifts, programs = programs)

        val lists = useCase(sessionId, workoutLiftId, liftId)

        assertEquals(listOf("padded"), lists.workout.map { it.body })
        assertEquals(listOf(MAX_LIFT_NOTE_LENGTH), lists.lift.map { it.body.length })
    }

    @Test
    @DisplayName("concurrent first reads converge on one migrated row")
    fun concurrentMigrateReadsConverge() = runTest {
        val programs = FakeProgramRepository(sampleProgram(slotPin = "slot legacy"))
        val useCase = useCase(programs = programs)

        val ids = withContext(Dispatchers.Default) {
            (1..16).map { async { useCase(sessionId, workoutLiftId, liftId) } }
                .awaitAll()
                .map { it.workout.single().id }
        }

        assertEquals(1, ids.toSet().size)
    }

    private fun useCase(
        notes: FakeLiftNotesRepository = FakeLiftNotesRepository(),
        lifts: FakeLiftRepository = FakeLiftRepository(listOf(sampleLift())),
        programs: FakeProgramRepository = FakeProgramRepository(sampleProgram()),
    ): GetLiftNoteListsUseCase = GetLiftNoteListsUseCase(notes, lifts, programs)

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

    private fun sampleProgram(slotPin: String? = null): Program =
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
                                    pinnedNote = slotPin,
                                ),
                            ),
                    ),
                ),
        )

    private fun note(id: String, body: String): LiftSessionNote =
        LiftSessionNote(
            id = LiftSessionNoteId(id),
            sessionId = sessionId,
            liftId = liftId,
            workoutLiftId = workoutLiftId,
            body = body,
            updatedAt = Instant.EPOCH,
        )
}
