package com.stratum.application.program

import com.stratum.core.testing.logging.FakeLoggingPort
import com.stratum.core.testing.program.FakeProgramRepository
import com.stratum.core.testing.training.NoOpProgramChangeEventRepository
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.Weekday
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.program.identity.WorkoutLiftId
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant
import com.stratum.domain.lift.Equipment
import com.stratum.domain.lift.WorkoutLiftNoteInput
import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.training.ProgramChangeEventPayload
import com.stratum.domain.training.ProgramChangeEventType
import com.stratum.domain.workout.LiftSwapped
import com.stratum.domain.program.Program
import com.stratum.domain.program.Workout
import com.stratum.domain.program.WorkoutLift
import com.stratum.domain.program.ProgressionScheme

class SwapWorkoutLiftUseCaseTest {

    @Test
    @DisplayName("Given active program, when swap is called with valid workout lift ID, then the database is updated with the new lift ID")
    fun swapSucceedsAndSaves() = runTest {
        val originalLiftId = LiftId("bench-press")
        val newLiftId = LiftId("incline-db-bench")
        val targetWorkoutLiftId = WorkoutLiftId("wl-1")

        val initialProgram = Program(
            id = ProgramId("p-1"),
            name = "Push Program",
            createdAt = Instant.now(),
            cycleLength = 1,
            trainingWeekdays = setOf(Weekday.Monday),
            workouts = listOf(
                Workout(
                    id = WorkoutId("w-1"),
                    programId = ProgramId("p-1"),
                    name = "Push Day",
                    weekday = Weekday.Monday,
                    position = 0,
                    workoutLifts = listOf(
                        WorkoutLift(
                            id = targetWorkoutLiftId,
                            workoutId = WorkoutId("w-1"),
                            liftId = originalLiftId,
                            progressionScheme = ProgressionScheme.Linear,
                            position = 0,
                            sets = emptyList()
                        )
                    )
                )
            )
        )

        val repository = FakeProgramRepository(initialProgram)
        val useCase = SwapWorkoutLiftUseCase(repository, NoOpProgramChangeEventRepository(), com.stratum.core.testing.onboarding.FakeEquipmentRepository(), FakeLoggingPort())

        val result = useCase(targetWorkoutLiftId, newLiftId)

        assertTrue(result.isSuccess)
        val updatedProgram = repository.getProgram()
        assertEquals(newLiftId, updatedProgram?.workouts?.first()?.workoutLifts?.first()?.liftId)
    }

    @Test
    @DisplayName("When swap is called and no active program exists, then it returns a failure result")
    fun swapFailsWhenNoProgram() = runTest {
        val logging = FakeLoggingPort()
        val repository = FakeProgramRepository(null)
        val useCase = SwapWorkoutLiftUseCase(repository, NoOpProgramChangeEventRepository(), com.stratum.core.testing.onboarding.FakeEquipmentRepository(), logging)

        val result = useCase(WorkoutLiftId("wl-1"), LiftId("incline-db-bench"))

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
        assertEquals(0, logging.errors.size)
        assertEquals(1, logging.warnings.size)
        assertTrue(logging.warnings.first().message.contains("no active program"))
    }

    @Test
    @DisplayName("When swap is called with a non-existent workout lift ID, then it returns a failure result and does not modify the program")
    fun swapFailsWhenWorkoutLiftNotFound() = runTest {
        val initialProgram = Program(
            id = ProgramId("p-1"),
            name = "Push Program",
            createdAt = Instant.now(),
            cycleLength = 1,
            trainingWeekdays = setOf(Weekday.Monday),
            workouts = listOf(
                Workout(
                    id = WorkoutId("w-1"),
                    programId = ProgramId("p-1"),
                    name = "Push Day",
                    weekday = Weekday.Monday,
                    position = 0,
                    workoutLifts = listOf(
                        WorkoutLift(
                            id = WorkoutLiftId("wl-1"),
                            workoutId = WorkoutId("w-1"),
                            liftId = LiftId("bench-press"),
                            progressionScheme = ProgressionScheme.Linear,
                            position = 0,
                            sets = emptyList()
                        )
                    )
                )
            )
        )

        val repository = FakeProgramRepository(initialProgram)
        val logging = FakeLoggingPort()
        val useCase = SwapWorkoutLiftUseCase(repository, NoOpProgramChangeEventRepository(), com.stratum.core.testing.onboarding.FakeEquipmentRepository(), logging)

        val result = useCase(WorkoutLiftId("non-existent"), LiftId("incline-db-bench"))

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        // Verify program was not modified
        val currentProgram = repository.getProgram()
        assertEquals(LiftId("bench-press"), currentProgram?.workouts?.first()?.workoutLifts?.first()?.liftId)
        assertEquals(0, logging.errors.size)
        assertEquals(1, logging.warnings.size)
        assertTrue(logging.warnings.first().message.contains("not found"))
    }

    @Test
    @DisplayName("WL-035 SwapWorkoutLiftUseCase appends LIFT_SWAPPED event")
    fun wl035_swapAppendsLiftSwappedEvent() = runTest {
        val events = com.stratum.core.testing.training.FakeProgramChangeEventRepository()
        val program = Program(
            id = ProgramId("p-1"),
            name = "Push Program",
            createdAt = Instant.now(),
            cycleLength = 1,
            trainingWeekdays = setOf(Weekday.Monday),
            workouts = listOf(
                Workout(
                    id = WorkoutId("w-1"),
                    programId = ProgramId("p-1"),
                    name = "Push Day",
                    weekday = Weekday.Monday,
                    position = 0,
                    workoutLifts = listOf(
                        WorkoutLift(
                            id = WorkoutLiftId("wl-bench"),
                            workoutId = WorkoutId("w-1"),
                            liftId = LiftId("lift-bench"),
                            progressionScheme = ProgressionScheme.Linear,
                            position = 0,
                            sets = emptyList(),
                        ),
                    ),
                ),
            ),
        )
        val useCase = SwapWorkoutLiftUseCase(FakeProgramRepository(program), events, com.stratum.core.testing.onboarding.FakeEquipmentRepository(), FakeLoggingPort())

        useCase(WorkoutLiftId("wl-bench"), LiftId("lift-squat")).getOrThrow()

        val appended = events.getEventsForProgram(ProgramId("p-1"))
        assertEquals(1, appended.size)
        assertEquals(com.stratum.domain.training.ProgramChangeEventType.LIFT_SWAPPED, appended.single().eventType)
        val payload = appended.single().payload as com.stratum.domain.training.ProgramChangeEventPayload.LiftSwapped
        assertEquals(LiftId("lift-bench"), payload.previousLiftId)
        assertEquals(LiftId("lift-squat"), payload.newLiftId)
    }

    // ---- STR-127: pair-scoped equipment + pinned notes -----------------------

    @Test
    @DisplayName("Swap resets equipment and notes to the incoming pair instead of carrying the old lift's")
    fun swapRescopesEquipmentAndNotes() = runTest {
        val slot = WorkoutLiftId("wl-1")
        val repository = FakeProgramRepository(pairProgram())
        repository.setWorkoutLiftPinnedNotes(
            slot,
            listOf(com.stratum.domain.lift.WorkoutLiftNoteInput(body = "bench cue")),
        )
        val useCase =
            SwapWorkoutLiftUseCase(repository, NoOpProgramChangeEventRepository(), com.stratum.core.testing.onboarding.FakeEquipmentRepository(), FakeLoggingPort())

        useCase(slot, LiftId("lift-ohp")).getOrThrow()

        val swapped = repository.getProgram()!!.workouts.single().workoutLifts.single()
        assertEquals(LiftId("lift-ohp"), swapped.liftId)
        // Equipment assignment cleared -> the new lift inherits its catalog default.
        assertEquals(null, swapped.equipmentId)
        assertEquals(null, swapped.pinnedNote)
        assertEquals(emptyList<String>(), swapped.pinnedNotes)
        // The outgoing pair's notes stay stored under their own pair.
        assertEquals(
            listOf("bench cue"),
            repository.getWorkoutLiftPinnedNotes(slot, LiftId("lift-bench")).map { it.body },
        )
        assertEquals(
            emptyList<String>(),
            repository.getWorkoutLiftPinnedNotes(slot).map { it.body },
        )
    }

    @Test
    @DisplayName("Swapping a lift back restores its stashed pair notes")
    fun swapBackRestoresNotes() = runTest {
        val slot = WorkoutLiftId("wl-1")
        val repository = FakeProgramRepository(pairProgram())
        repository.setWorkoutLiftPinnedNotes(
            slot,
            listOf(com.stratum.domain.lift.WorkoutLiftNoteInput(body = "bench cue")),
        )
        val useCase =
            SwapWorkoutLiftUseCase(repository, NoOpProgramChangeEventRepository(), com.stratum.core.testing.onboarding.FakeEquipmentRepository(), FakeLoggingPort())

        useCase(slot, LiftId("lift-ohp")).getOrThrow()
        useCase(slot, LiftId("lift-bench")).getOrThrow()

        val restored = repository.getProgram()!!.workouts.single().workoutLifts.single()
        assertEquals(LiftId("lift-bench"), restored.liftId)
        assertEquals("bench cue", restored.pinnedNote)
        assertEquals(
            listOf("bench cue"),
            repository.getWorkoutLiftPinnedNotes(slot).map { it.body },
        )
    }

    @Test
    @DisplayName("Given pinned-notes read fails when swapped then succeeds carrying the bound diagnostic")
    fun notesReadFailureSurfacedAsDiagnostic() = runTest {
        val slot = WorkoutLiftId("wl-1")
        val repository = FakeProgramRepository(pairProgram())
        val failure = IllegalStateException("notes boom")
        repository.pinnedNotesReadError = failure
        val useCase =
            SwapWorkoutLiftUseCase(repository, NoOpProgramChangeEventRepository(), com.stratum.core.testing.onboarding.FakeEquipmentRepository(), FakeLoggingPort())

        val result = useCase(slot, LiftId("lift-ohp"))

        assertTrue(result.isSuccess)
        val outcome = result.getOrThrow()
        assertEquals(1, outcome.diagnostics.size)
        val diagnostic = outcome.diagnostics.single() as SwapWorkoutLiftDiagnostic.PinnedNotesUnreadable
        assertEquals(slot, diagnostic.workoutLiftId)
        assertEquals(LiftId("lift-ohp"), diagnostic.newLiftId)
        assertTrue(diagnostic.cause === failure)
        // The swap itself still applied, proceeding with empty notes.
        val swapped = repository.getProgram()!!.workouts.single().workoutLifts.single()
        assertEquals(LiftId("lift-ohp"), swapped.liftId)
        assertEquals(emptyList<String>(), swapped.pinnedNotes)
        assertEquals(null, swapped.equipmentId)
    }

    @Test
    @DisplayName("Given readable pinned notes when swapped then no diagnostic is recorded")
    fun readableNotesCarryNoDiagnostic() = runTest {
        val slot = WorkoutLiftId("wl-1")
        val repository = FakeProgramRepository(pairProgram())
        repository.setWorkoutLiftPinnedNotes(
            slot,
            listOf(com.stratum.domain.lift.WorkoutLiftNoteInput(body = "bench cue")),
        )
        val useCase =
            SwapWorkoutLiftUseCase(repository, NoOpProgramChangeEventRepository(), com.stratum.core.testing.onboarding.FakeEquipmentRepository(), FakeLoggingPort())

        val outcome = useCase(slot, LiftId("lift-ohp")).getOrThrow()

        assertTrue(outcome.diagnostics.isEmpty())
    }

    @Test
    @DisplayName("Given pinned-notes read is cancelled when swapped then cancellation propagates")
    fun notesReadCancellationPropagates() = runTest {
        val slot = WorkoutLiftId("wl-1")
        val repository = FakeProgramRepository(pairProgram())
        repository.pinnedNotesReadError = kotlinx.coroutines.CancellationException("cancelled")
        val useCase =
            SwapWorkoutLiftUseCase(repository, NoOpProgramChangeEventRepository(), com.stratum.core.testing.onboarding.FakeEquipmentRepository(), FakeLoggingPort())

        var propagated: kotlinx.coroutines.CancellationException? = null
        try {
            useCase(slot, LiftId("lift-ohp"))
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            propagated = cancelled
        }

        assertTrue(propagated != null)
        // The cancelled swap persisted nothing.
        val untouched = repository.getProgram()!!.workouts.single().workoutLifts.single()
        assertEquals(LiftId("lift-bench"), untouched.liftId)
    }

    private fun pairProgram(): Program =
        Program(
            id = ProgramId("p-1"),
            name = "Push Program",
            createdAt = Instant.now(),
            cycleLength = 1,
            trainingWeekdays = setOf(Weekday.Monday),
            workouts = listOf(
                Workout(
                    id = WorkoutId("w-1"),
                    programId = ProgramId("p-1"),
                    name = "Push Day",
                    weekday = Weekday.Monday,
                    position = 0,
                    workoutLifts = listOf(
                        WorkoutLift(
                            id = WorkoutLiftId("wl-1"),
                            workoutId = WorkoutId("w-1"),
                            liftId = LiftId("lift-bench"),
                            progressionScheme = ProgressionScheme.Double,
                            position = 0,
                            sets = emptyList(),
                            pinnedNote = "bench cue",
                            equipmentId =
                                com.stratum.domain.lift.identity.EquipmentId("eq-bench"),
                        ),
                    ),
                ),
            ),
        )
}
