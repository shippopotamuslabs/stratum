package com.stratum.domain.lift

import com.stratum.core.testing.lift.FakeLiftNotesRepository
import com.stratum.core.testing.logging.FakeLoggingPort
import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.core.testing.program.FakeProgramRepository
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.lift.identity.LiftSessionNoteId
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.onboarding.Weekday
import com.stratum.domain.program.Program
import com.stratum.domain.program.Workout
import com.stratum.domain.program.WorkoutLift
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.training.identity.TrainingSessionId
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class SaveLiftNotesUseCaseTest {

    private val sessionId = TrainingSessionId("ts-1")
    private val workoutLiftId = WorkoutLiftId("wl-1")
    private val liftId = LiftId("lift-1")

    @Test
    @DisplayName("dirty-only tabs persist; untouched tabs stay unchanged")
    fun dirtyOnly() = runTest {
        val notes = FakeLiftNotesRepository()
        val lifts = FakeLiftRepository(listOf(sampleLift()))
        val programs = FakeProgramRepository(sampleProgram())
        val useCase = SaveLiftNotesUseCase(notes, lifts, programs, FakeLoggingPort())

        val result =
            useCase(
                SaveLiftNotesCommand(
                    sessionId = sessionId,
                    workoutLiftId = workoutLiftId,
                    liftId = liftId,
                    sessionNote = OptionalNoteEdit("session cue"),
                    liftNote = OptionalNoteEdit("catalog pin"),
                ),
            )

        assertTrue(result.isSuccess)
        assertFalse(result.getOrThrow().workoutNoteNeedsOverlay)
        assertEquals("session cue", notes.getSessionNote(sessionId, workoutLiftId)?.body)
        assertEquals(
            LiftSessionNoteId.forSlot(sessionId, workoutLiftId),
            notes.getSessionNote(sessionId, workoutLiftId)?.id,
        )
        assertEquals("catalog pin", lifts.currentLifts.single().pinnedNote)
        assertNull(programs.current!!.workouts.single().workoutLifts.single().pinnedNote)
    }

    @Test
    @DisplayName("blank dirty session note deletes the row")
    fun blankDeletesSessionNote() = runTest {
        val notes = FakeLiftNotesRepository()
        notes.upsertSessionNote(
            LiftSessionNote(
                id = LiftSessionNoteId("n-1"),
                sessionId = sessionId,
                liftId = liftId,
                workoutLiftId = workoutLiftId,
                body = "old",
                updatedAt = Instant.EPOCH,
            ),
        )
        val useCase =
            SaveLiftNotesUseCase(
                notes,
                FakeLiftRepository(listOf(sampleLift())),
                FakeProgramRepository(sampleProgram()),
                FakeLoggingPort(),
            )

        useCase(
            SaveLiftNotesCommand(
                sessionId = sessionId,
                workoutLiftId = workoutLiftId,
                liftId = liftId,
                sessionNote = OptionalNoteEdit("  "),
            ),
        )

        assertNull(notes.getSessionNote(sessionId, workoutLiftId))
    }

    @Test
    @DisplayName("session note list replaces the slot in display order")
    fun sessionNoteListReplace() = runTest {
        val notes = FakeLiftNotesRepository()
        val useCase =
            SaveLiftNotesUseCase(
                notes,
                FakeLiftRepository(listOf(sampleLift())),
                FakeProgramRepository(sampleProgram()),
                FakeLoggingPort(),
            )

        useCase(
            SaveLiftNotesCommand(
                sessionId = sessionId,
                workoutLiftId = workoutLiftId,
                liftId = liftId,
                sessionNotes = listOf(
                    SessionNoteInput(body = "first cue"),
                    SessionNoteInput(body = "second cue"),
                ),
            ),
        )

        val stored = notes.getSessionNotes(sessionId, workoutLiftId)
        assertEquals(listOf("first cue", "second cue"), stored.map { it.body })
        assertEquals(listOf(0, 1), stored.map { it.position })
    }

    @Test
    @DisplayName("session note list keeps stable ids across re-saves and drops blanks")
    fun sessionNoteListStableIds() = runTest {
        val notes = FakeLiftNotesRepository()
        val useCase =
            SaveLiftNotesUseCase(
                notes,
                FakeLiftRepository(listOf(sampleLift())),
                FakeProgramRepository(sampleProgram()),
                FakeLoggingPort(),
            )
        suspend fun stored() = notes.getSessionNotes(sessionId, workoutLiftId)

        useCase(
            SaveLiftNotesCommand(
                sessionId = sessionId,
                workoutLiftId = workoutLiftId,
                liftId = liftId,
                sessionNotes = listOf(
                    SessionNoteInput(body = "keep me"),
                    SessionNoteInput(body = "drop me"),
                ),
            ),
        )
        val firstId = stored().first().id

        useCase(
            SaveLiftNotesCommand(
                sessionId = sessionId,
                workoutLiftId = workoutLiftId,
                liftId = liftId,
                sessionNotes = listOf(
                    SessionNoteInput(id = firstId, body = "keep me edited"),
                    SessionNoteInput(body = "  "),
                    SessionNoteInput(body = "appended"),
                ),
            ),
        )

        val next = stored()
        assertEquals(listOf("keep me edited", "appended"), next.map { it.body })
        assertEquals(firstId, next.first().id)
    }

    @Test
    @DisplayName("empty session note list deletes the slot")
    fun sessionNoteListEmptyDeletes() = runTest {
        val notes = FakeLiftNotesRepository()
        notes.upsertSessionNote(
            LiftSessionNote(
                id = LiftSessionNoteId("n-1"),
                sessionId = sessionId,
                liftId = liftId,
                workoutLiftId = workoutLiftId,
                body = "old",
                updatedAt = Instant.EPOCH,
            ),
        )
        val useCase =
            SaveLiftNotesUseCase(
                notes,
                FakeLiftRepository(listOf(sampleLift())),
                FakeProgramRepository(sampleProgram()),
                FakeLoggingPort(),
            )

        useCase(
            SaveLiftNotesCommand(
                sessionId = sessionId,
                workoutLiftId = workoutLiftId,
                liftId = liftId,
                sessionNotes = emptyList(),
            ),
        )

        assertTrue(notes.getSessionNotes(sessionId, workoutLiftId).isEmpty())
    }

    @Test
    @DisplayName("catalog note list replaces pins and drops blanks")
    fun catalogNoteListReplace() = runTest {
        val lifts = FakeLiftRepository(listOf(sampleLift()))
        val useCase =
            SaveLiftNotesUseCase(
                FakeLiftNotesRepository(),
                lifts,
                FakeProgramRepository(sampleProgram()),
                FakeLoggingPort(),
            )

        useCase(
            SaveLiftNotesCommand(
                sessionId = sessionId,
                workoutLiftId = workoutLiftId,
                liftId = liftId,
                liftNotes = listOf(
                    CatalogNoteInput(body = "belt on"),
                    CatalogNoteInput(body = "   "),
                    CatalogNoteInput(body = "knees out"),
                ),
            ),
        )

        val stored = lifts.getPinnedNotes(liftId)
        assertEquals(listOf("belt on", "knees out"), stored.map { it.body })
        // Legacy single-note read mirrors the first entry until PR2 removes the column.
        assertEquals("belt on", lifts.currentLifts.single().pinnedNote)
    }

    @Test
    @DisplayName("workout note list on a missing template slot needs overlay")
    fun overlayOnlyWorkoutNoteList() = runTest {
        val programs = FakeProgramRepository(sampleProgram())
        val useCase =
            SaveLiftNotesUseCase(
                FakeLiftNotesRepository(),
                FakeLiftRepository(listOf(sampleLift())),
                programs,
                FakeLoggingPort(),
            )

        val result =
            useCase(
                SaveLiftNotesCommand(
                    sessionId = sessionId,
                    workoutLiftId = WorkoutLiftId("wl-overlay"),
                    liftId = liftId,
                    workoutNotes = listOf(WorkoutLiftNoteInput(body = "overlay pin")),
                ),
            )

        assertTrue(result.getOrThrow().workoutNoteNeedsOverlay)
    }

    @Test
    @DisplayName("workout pin on a missing template slot needs overlay")
    fun overlayOnlyWorkoutPin() = runTest {
        val useCase =
            SaveLiftNotesUseCase(
                FakeLiftNotesRepository(),
                FakeLiftRepository(listOf(sampleLift())),
                FakeProgramRepository(sampleProgram()),
                FakeLoggingPort(),
            )

        val result =
            useCase(
                SaveLiftNotesCommand(
                    sessionId = sessionId,
                    workoutLiftId = WorkoutLiftId("wl-overlay"),
                    liftId = liftId,
                    workoutNote = OptionalNoteEdit("overlay pin"),
                ),
            )

        assertTrue(result.getOrThrow().workoutNoteNeedsOverlay)
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
                                    progressionScheme = com.stratum.domain.program.ProgressionScheme.Double,
                                    position = 0,
                                    sets = emptyList(),
                                ),
                            ),
                    ),
                ),
        )
}
