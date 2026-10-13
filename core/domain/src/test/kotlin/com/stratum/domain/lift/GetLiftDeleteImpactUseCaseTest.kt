package com.stratum.domain.lift

import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.core.testing.program.FakeProgramRepository
import com.stratum.core.testing.training.FakeTrainingLogRepository
import com.stratum.core.testing.workout.FakeWorkoutOverlayStore
import com.stratum.domain.importing.identity.LoggedSetId
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.program.Program
import com.stratum.domain.program.ProgressionScheme
import com.stratum.domain.program.Workout
import com.stratum.domain.program.WorkoutLift
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.training.LoggedLiftContext
import com.stratum.domain.training.LoggedSet
import com.stratum.domain.training.TrainingSession
import com.stratum.domain.training.TrainingSessionKind
import com.stratum.domain.training.TrainingSessionOrigin
import com.stratum.domain.training.TrainingSessionStatus
import com.stratum.domain.training.identity.TrainingSessionId
import com.stratum.domain.workout.OverlayAddedLift
import com.stratum.domain.workout.WorkoutOverlay
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class GetLiftDeleteImpactUseCaseTest {

    private val liftId = LiftId("lift-a")
    private val otherId = LiftId("lift-b")
    private val sessionId = TrainingSessionId("session-1")

    private lateinit var liftRepo: FakeLiftRepository
    private lateinit var logRepo: FakeTrainingLogRepository
    private lateinit var programRepo: FakeProgramRepository
    private lateinit var overlayStore: FakeWorkoutOverlayStore
    private lateinit var subject: GetLiftDeleteImpactUseCase

    @BeforeEach
    fun setup() {
        liftRepo =
            FakeLiftRepository(
                listOf(
                    lift(liftId, "Alpha Press"),
                    lift(otherId, "Beta Row"),
                ),
            )
        logRepo = FakeTrainingLogRepository()
        programRepo = FakeProgramRepository()
        overlayStore = FakeWorkoutOverlayStore()
        subject = GetLiftDeleteImpactUseCase(liftRepo, programRepo, logRepo, overlayStore)
    }

    @Test
    @DisplayName("Impact counts duplicate slots across all programs and logged sets DB-wide")
    fun countsAcrossFullDatabase() = runTest {
        // Filtered/searched UI state must not matter: two slots in one workout
        // (duplicates), one more in a library program.
        programRepo.saveProgram(
            program("prog-1", "w-1", listOf("wl-1" to liftId, "wl-2" to liftId, "wl-3" to otherId)),
        )
        programRepo.saveToLibrary(program("prog-2", "w-2", listOf("wl-4" to liftId)))
        logRepo.seedSession(session(TrainingSessionStatus.COMPLETED, workoutId = null))
        logRepo.seedSet(loggedSet("s-1", liftId))
        logRepo.seedSet(loggedSet("s-2", liftId))
        logRepo.seedSet(loggedSet("s-3", otherId))

        val impact = subject(liftId)!!

        assertEquals("Alpha Press", impact.liftName)
        assertEquals(3, impact.totalSlotCount)
        assertEquals(2, impact.affectedWorkouts.size)
        assertEquals(2, impact.loggedSetCount)
        assertFalse(impact.inActiveSession)
    }

    @Test
    @DisplayName("Lift in the active session's template flags the grandfather note")
    fun flagsActiveSessionTemplate() = runTest {
        programRepo.saveProgram(program("prog-1", "w-1", listOf("wl-1" to liftId)))
        logRepo.seedSession(session(TrainingSessionStatus.IN_PROGRESS, workoutId = WorkoutId("w-1")))

        val impact = subject(liftId)!!

        assertTrue(impact.inActiveSession)
    }

    @Test
    @DisplayName("Lift only logged in the active session (swapped in) still flags the note")
    fun flagsActiveSessionLoggedSet() = runTest {
        logRepo.seedSession(session(TrainingSessionStatus.IN_PROGRESS, workoutId = null))
        logRepo.seedSet(loggedSet("s-1", liftId))

        val impact = subject(liftId)!!

        assertTrue(impact.inActiveSession)
    }

    @Test
    @DisplayName("Lift swapped into the active session via overlay (no sets logged) flags the note")
    fun flagsActiveSessionOverlaySwap() = runTest {
        logRepo.seedSession(session(TrainingSessionStatus.IN_PROGRESS, workoutId = null))
        overlayStore.seed(
            sessionId,
            WorkoutOverlay(swappedLifts = mapOf(WorkoutLiftId("wl-9") to liftId)),
        )

        val impact = subject(liftId)!!

        assertTrue(impact.inActiveSession)
    }

    @Test
    @DisplayName("Lift added to the active session via overlay (no sets logged) flags the note")
    fun flagsActiveSessionOverlayAdd() = runTest {
        logRepo.seedSession(session(TrainingSessionStatus.IN_PROGRESS, workoutId = null))
        overlayStore.seed(
            sessionId,
            WorkoutOverlay(
                addedLifts = listOf(OverlayAddedLift(WorkoutLiftId("wl-9"), liftId)),
            ),
        )

        val impact = subject(liftId)!!

        assertTrue(impact.inActiveSession)
    }

    @Test
    @DisplayName("Missing or already-tombstoned lifts have no impact to show")
    fun missingOrTombstonedReturnsNull() = runTest {
        assertNull(subject(LiftId("nope")))
        liftRepo.tombstoneLift(liftId)
        assertNull(subject(liftId))
    }

    private fun lift(id: LiftId, name: String): Lift =
        Lift(
            id = id,
            name = name,
            movementPatterns = emptySet(),
            primaryMuscles = setOf(MuscleGroup.Chest),
            secondaryMuscles = emptySet(),
            incrementTiers = emptyList(),
            presetTags = emptySet(),
            enabled = true,
            isCustom = false,
        )

    private fun program(
        id: String,
        workoutId: String,
        slots: List<Pair<String, LiftId>>,
    ): Program =
        Program(
            id = ProgramId(id),
            name = "Program $id",
            createdAt = Instant.parse("2026-01-01T00:00:00Z"),
            cycleLength = 1,
            isActive = id == "prog-1",
            workouts =
                listOf(
                    Workout(
                        id = WorkoutId(workoutId),
                        programId = ProgramId(id),
                        name = "Day $workoutId",
                        weekday = null,
                        position = 0,
                        workoutLifts =
                            slots.mapIndexed { index, (slotId, slotLiftId) ->
                                WorkoutLift(
                                    id = WorkoutLiftId(slotId),
                                    workoutId = WorkoutId(workoutId),
                                    liftId = slotLiftId,
                                    progressionScheme = ProgressionScheme.Linear,
                                    position = index,
                                    sets = emptyList(),
                                )
                            },
                    ),
                ),
        )

    private fun session(
        status: TrainingSessionStatus,
        workoutId: WorkoutId?,
    ): TrainingSession =
        TrainingSession(
            id = sessionId,
            programId = ProgramId("prog-1"),
            workoutId = workoutId,
            status = status,
            kind = TrainingSessionKind.PROGRAM_WORKOUT,
            origin = TrainingSessionOrigin.LIVE,
            cycleNumberSnapshot = 1,
            weekdaySnapshot = null,
            startedAt = Instant.parse("2026-03-01T10:00:00Z"),
            endedAt = if (status == TrainingSessionStatus.COMPLETED) Instant.parse("2026-03-01T11:00:00Z") else null,
            sourceApp = null,
        )

    private fun loggedSet(id: String, setLiftId: LiftId): LoggedSet =
        LoggedSet(
            id = LoggedSetId(id),
            sessionId = sessionId,
            exerciseNameRaw = "Alpha Press",
            liftId = setLiftId,
            setIndex = 0,
            weight = Weight(100.0, Weight.WeightUnit.Lb),
            reps = 5,
            rir = 1,
            completedAt = Instant.parse("2026-03-01T10:30:00Z"),
            liftContext = LoggedLiftContext(liftName = "Alpha Press"),
            isPersonalRecord = false,
            estimatedOneRm = 115.0,
        )
}
