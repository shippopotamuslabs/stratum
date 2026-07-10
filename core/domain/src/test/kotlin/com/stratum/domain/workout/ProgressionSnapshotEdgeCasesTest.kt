package com.stratum.domain.workout

import com.stratum.core.testing.importing.FakeLiftTrainingInsightsRepository
import com.stratum.core.testing.program.FakeProgramRepository
import com.stratum.core.testing.training.FakeProgramChangeEventRepository
import com.stratum.core.testing.workout.FakeProgressionHistoryRepository
import com.stratum.domain.importing.identity.LoggedSetId
import com.stratum.domain.lift.KinematicProfile
import com.stratum.domain.lift.MuscleLengthBias
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.Weekday
import com.stratum.domain.program.LiftSet
import com.stratum.domain.program.Program
import com.stratum.domain.program.ProgressionScheme
import com.stratum.domain.program.SetType
import com.stratum.domain.program.Workout
import com.stratum.domain.program.WorkoutLift
import com.stratum.domain.program.identity.LiftSetId
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.training.LoggedLiftContext
import com.stratum.domain.training.LoggedSet
import com.stratum.domain.training.ProgramChangeEventType
import com.stratum.domain.training.TrainingSession
import com.stratum.domain.training.TrainingSessionKind
import com.stratum.domain.training.TrainingSessionOrigin
import com.stratum.domain.training.TrainingSessionStatus
import com.stratum.domain.training.identity.TrainingSessionId
import com.stratum.domain.workout.progression.DoubleProgressionEngine
import com.stratum.domain.workout.progression.LinearProgressionDefaults
import com.stratum.domain.workout.progression.LinearProgressionEngine
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * End-to-end [GenerateProgressionSnapshotUseCase] scenarios that wire
 * [buildRecentSessionBlock] → engines: reversed within-session difficulty and mixed
 * session fill (sets 1–3 recent, 4–5 only on older sessions).
 */
@DisplayName("Progression snapshot edge cases (orchestrator E2E)")
class ProgressionSnapshotEdgeCasesTest {

    private val programId = ProgramId("p-1")
    private val workoutId = WorkoutId("w-1")
    private val workoutLiftId = WorkoutLiftId("wl-1")
    private val liftId = LiftId("L-1")

    private fun lb(amount: Double) = Weight(amount, Weight.WeightUnit.Lb)

    private fun fiveSlotLift(scheme: ProgressionScheme): WorkoutLift {
        val sets = (0 until 5).map { index ->
            LiftSet(
                id = LiftSetId("ls-$index"),
                workoutLiftId = workoutLiftId,
                position = index,
                setType = SetType.Standard,
                repMin = 8,
                repMax = 16,
                rirGoal = if (index == 0) 2 else 1,
                isTopSet = index == 0,
            )
        }
        return WorkoutLift(
            id = workoutLiftId,
            workoutId = workoutId,
            liftId = liftId,
            progressionScheme = scheme,
            position = 0,
            sets = sets,
        )
    }

    private fun session(id: String, startedAt: String) = TrainingSession(
        id = TrainingSessionId(id),
        programId = programId,
        workoutId = workoutId,
        status = TrainingSessionStatus.COMPLETED,
        kind = TrainingSessionKind.PROGRAM_WORKOUT,
        origin = TrainingSessionOrigin.IMPORT,
        cycleNumberSnapshot = 1,
        weekdaySnapshot = Weekday.Monday,
        startedAt = Instant.parse(startedAt),
        endedAt = Instant.parse(startedAt).plusSeconds(3600),
        sourceApp = null,
    )

    private fun logged(
        sessionId: TrainingSessionId,
        setIndex: Int,
        weight: Double,
        reps: Int,
        rir: Int?,
        completedAt: Instant,
    ) = LoggedSet(
        id = LoggedSetId("set-${sessionId.value}-$setIndex"),
        sessionId = sessionId,
        exerciseNameRaw = "Squat",
        liftId = liftId,
        setIndex = setIndex,
        weight = lb(weight),
        reps = reps,
        rir = rir,
        completedAt = completedAt,
        liftContext = LoggedLiftContext(
            liftName = "Squat",
            movementPatternsBitmask = 1,
            primaryMusclesBitmask = 1,
            secondaryMusclesBitmask = 0,
            kinematicProfile = KinematicProfile.Isolation,
            muscleLengthBias = MuscleLengthBias.MidRange,
        ),
    )

    private fun useCase(
        history: FakeProgressionHistoryRepository,
        events: FakeProgramChangeEventRepository = FakeProgramChangeEventRepository(),
        programRepo: FakeProgramRepository = FakeProgramRepository(),
    ) = GenerateProgressionSnapshotUseCase(
        progressionHistoryPort = history,
        doubleProgressionEngine = DoubleProgressionEngine(),
        linearProgressionEngine = LinearProgressionEngine(LinearProgressionDefaults.config),
        programChangeEventRepository = events,
        liftTrainingInsightsRepository = FakeLiftTrainingInsightsRepository(),
        programRepository = programRepo,
    )

    private fun programWithLift(lift: WorkoutLift): Program =
        Program(
            id = programId,
            name = "Test",
            createdAt = Instant.parse("2026-01-01T00:00:00Z"),
            cycleLength = 1,
            trainingWeekdays = setOf(Weekday.Monday),
            workouts = listOf(
                Workout(
                    id = workoutId,
                    programId = programId,
                    name = "Day 1",
                    weekday = Weekday.Monday,
                    position = 0,
                    workoutLifts = listOf(lift),
                ),
            ),
        )

    private fun seedSlot(
        history: FakeProgressionHistoryRepository,
        setIndex: Int,
        entry: SlotHistoryEntry,
    ) {
        history.seed(
            SlotHistoryRequest(programId, workoutId, workoutLiftId, liftId, setIndex),
            entry,
        )
    }

    @Test
    @DisplayName("reversed RIR/reps within one session → monotonic non-increasing recs (Double)")
    fun reversedDifficultyDoubleMonotonic() = runTest {
        // Positional: set0 12@1, set1 13@1, set2 15@0 at same weight — capacity-ordered block
        // should feed engines so recommended reps do not rise across slots.
        val history = FakeProgressionHistoryRepository()
        val s = session("s-n1", "2026-07-01T10:00:00Z")
        val t = Instant.parse("2026-07-01T10:30:00Z")
        seedSlot(history, 0, SlotHistoryEntry(s, logged(s.id, 0, 240.0, 12, 1, t)))
        seedSlot(history, 1, SlotHistoryEntry(s, logged(s.id, 1, 240.0, 13, 1, t)))
        seedSlot(history, 2, SlotHistoryEntry(s, logged(s.id, 2, 240.0, 15, 0, t)))

        val lift = fiveSlotLift(ProgressionScheme.Double).copy(
            sets = fiveSlotLift(ProgressionScheme.Double).sets.take(3),
        )
        val snapshot = useCase(history)(SnapshotRequest(programId, workoutId, listOf(lift))).getOrThrow()

        val r0 = snapshot.bySlot.getValue(SetSlotId.Template(LiftSetId("ls-0")))
        val r1 = snapshot.bySlot.getValue(SetSlotId.Template(LiftSetId("ls-1")))
        val r2 = snapshot.bySlot.getValue(SetSlotId.Template(LiftSetId("ls-2")))
        assertTrue(r0.baselineExpectedReps >= r1.baselineExpectedReps)
        assertTrue(r1.baselineExpectedReps >= r2.baselineExpectedReps)
        // UI previous* stays positional
        assertEquals(12, r0.previousReps)
        assertEquals(13, r1.previousReps)
        assertEquals(15, r2.previousReps)
    }

    @Test
    @DisplayName("5-slot program after 3-set recent session: slots 3–4 project, ignore stale older sets")
    fun fiveToThreeToFiveProjectsNotStale() = runTest {
        val history = FakeProgressionHistoryRepository()
        val recent = session("s-n1", "2026-07-01T10:00:00Z")
        val older = session("s-n2", "2026-06-01T10:00:00Z")
        val rt = Instant.parse("2026-07-01T10:30:00Z")
        val ot = Instant.parse("2026-06-01T10:30:00Z")

        // Recent session: 3 sets @ 240
        for (i in 0..2) {
            seedSlot(
                history,
                i,
                SlotHistoryEntry(recent, logged(recent.id, i, 240.0, 12 - i, 1, rt)),
            )
        }
        // Older session: all 5 sets @ 200 — must not leak into slots 3/4 math as base weight
        for (i in 0..4) {
            seedSlot(
                history,
                i,
                SlotHistoryEntry(older, logged(older.id, i, 200.0, 10, 2, ot)),
            )
        }

        val snapshot = useCase(history)(
            SnapshotRequest(programId, workoutId, listOf(fiveSlotLift(ProgressionScheme.Double))),
        ).getOrThrow()

        val r0 = snapshot.bySlot.getValue(SetSlotId.Template(LiftSetId("ls-0")))
        val r3 = snapshot.bySlot.getValue(SetSlotId.Template(LiftSetId("ls-3")))
        val r4 = snapshot.bySlot.getValue(SetSlotId.Template(LiftSetId("ls-4")))

        // Top weight from recent 240 block, not older 200
        assertEquals(240.0, r0.recommendedWeight.amount, 0.01)
        assertEquals(r0.recommendedWeight, r3.recommendedWeight, "straight sets inherit top weight")
        assertEquals(r0.recommendedWeight, r4.recommendedWeight)
        // Projected slots still produce positive rep targets (ProjectFromPrior path)
        assertTrue(r3.baselineExpectedReps >= 5)
        assertTrue(r4.baselineExpectedReps >= 5)
        assertTrue(r3.isProjected || r4.isProjected)
    }

    @Test
    @DisplayName("Linear: two consecutive top-set stalls → SCHEME_CHANGED + persisted Double")
    fun linearTwoStallsEmitsSchemeTransition() = runTest {
        val history = FakeProgressionHistoryRepository()
        val events = FakeProgramChangeEventRepository()
        val s1 = session("s-n1", "2026-07-01T10:00:00Z")
        val s2 = session("s-n2", "2026-06-24T10:00:00Z")
        seedSlot(
            history,
            0,
            SlotHistoryEntry(
                s1,
                logged(s1.id, 0, 200.0, 4, 2, Instant.parse("2026-07-01T10:30:00Z")),
            ),
        )
        seedSlot(
            history,
            0,
            SlotHistoryEntry(
                s2,
                logged(s2.id, 0, 200.0, 4, 2, Instant.parse("2026-06-24T10:30:00Z")),
            ),
        )

        val lift = WorkoutLift(
            id = workoutLiftId,
            workoutId = workoutId,
            liftId = liftId,
            progressionScheme = ProgressionScheme.Linear,
            position = 0,
            sets = listOf(
                LiftSet(
                    id = LiftSetId("ls-0"),
                    workoutLiftId = workoutLiftId,
                    position = 0,
                    setType = SetType.Standard,
                    repMin = 5,
                    repMax = 8,
                    rirGoal = 2,
                    isTopSet = true,
                ),
            ),
        )
        val programRepo = FakeProgramRepository(programWithLift(lift))
        val snapshot = useCase(history, events, programRepo)(
            SnapshotRequest(programId, workoutId, listOf(lift)),
        ).getOrThrow()
        val rec = snapshot.bySlot.getValue(SetSlotId.Template(LiftSetId("ls-0")))
        // After transition, Double engine runs: stall history does not meet top-of-range, so
        // weight holds at 200 (no classic 10% deload).
        assertEquals(200.0, rec.recommendedWeight.amount, 0.01)
        val stored = events.getEventsForProgram(programId)
        assertTrue(
            stored.any { it.eventType == ProgramChangeEventType.SCHEME_CHANGED },
            "two consecutive stalls must append SCHEME_CHANGED (Linear → Double)",
        )
        val persistedScheme = programRepo.getProgram()!!
            .workouts.first().workoutLifts.first().progressionScheme
        assertEquals(ProgressionScheme.Double, persistedScheme, "scheme must persist so transition does not re-fire")

        // Second snapshot with the same Linear request but program already Double should
        // not append another SCHEME_CHANGED when caller passes updated lifts — simulate
        // re-entry with persisted scheme.
        val doubleLift = lift.copy(progressionScheme = ProgressionScheme.Double)
        useCase(history, events, programRepo)(
            SnapshotRequest(programId, workoutId, listOf(doubleLift)),
        ).getOrThrow()
        assertEquals(
            1,
            events.getEventsForProgram(programId).count { it.eventType == ProgramChangeEventType.SCHEME_CHANGED },
            "already-Double lift must not re-append SCHEME_CHANGED",
        )
    }
}
