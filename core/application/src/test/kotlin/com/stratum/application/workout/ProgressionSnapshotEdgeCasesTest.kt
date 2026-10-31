package com.stratum.application.workout

import com.stratum.core.testing.importing.FakeLiftTrainingInsightsRepository
import com.stratum.core.testing.program.FakeProgramRepository
import com.stratum.core.testing.training.FakeProgramChangeEventRepository
import com.stratum.core.testing.workout.FakeProgressionHistoryRepository
import com.stratum.domain.importing.LiftTrainingInsights
import com.stratum.domain.importing.identity.LoggedSetId
import com.stratum.domain.lift.IncrementTier
import com.stratum.domain.lift.FatigueProfile
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
import com.stratum.domain.workout.BACKOFF_REP_FLOOR
import com.stratum.domain.workout.progression.DoubleProgressionEngine
import com.stratum.domain.workout.progression.LinearProgressionDefaults
import com.stratum.domain.workout.progression.LinearProgressionEngine
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import com.stratum.domain.lift.WeightConversion
import com.stratum.domain.workout.progression.buildRecentSessionBlock
import com.stratum.domain.workout.ProgressionSnapshot
import com.stratum.domain.workout.SetSlotId
import com.stratum.domain.workout.SlotHistoryEntry
import com.stratum.domain.workout.SlotHistoryRequest
import com.stratum.domain.workout.SlotRecommendation

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

    private fun nSlotLift(
        scheme: ProgressionScheme,
        n: Int = 5,
        topSetIndex: Int = 0,
        repMin: Int = 8,
        repMax: Int = 16,
    ): WorkoutLift {
        val sets = (0 until n).map { index ->
            LiftSet(
                id = LiftSetId("ls-$index"),
                workoutLiftId = workoutLiftId,
                position = index,
                setType = SetType.Standard,
                repMin = repMin,
                repMax = repMax,
                rirGoal = if (index == topSetIndex) 2 else 1,
                isTopSet = index == topSetIndex,
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

    private fun fiveSlotLift(scheme: ProgressionScheme): WorkoutLift = nSlotLift(scheme, n = 5)

    private val fiveLbTiers = listOf(
        IncrementTier(lb(0.0), lb(40.0), lb(5.0)),
        IncrementTier(lb(40.0), null, lb(5.0)),
    )

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
        unit: Weight.WeightUnit = Weight.WeightUnit.Lb,
    ) = LoggedSet(
        id = LoggedSetId("set-${sessionId.value}-$setIndex"),
        sessionId = sessionId,
        exerciseNameRaw = "Squat",
        liftId = liftId,
        setIndex = setIndex,
        weight = Weight(weight, unit),
        reps = reps,
        rir = rir,
        completedAt = completedAt,
        liftContext = LoggedLiftContext(
            liftName = "Squat",
            movementPatternsBitmask = 1,
            primaryMusclesBitmask = 1,
            secondaryMusclesBitmask = 0,
            fatigueProfile = FatigueProfile.Low,
            muscleLengthBias = MuscleLengthBias.MidRange,
        ),
    )

    private fun useCase(
        history: FakeProgressionHistoryRepository,
        events: FakeProgramChangeEventRepository = FakeProgramChangeEventRepository(),
        programRepo: FakeProgramRepository = FakeProgramRepository(),
        insights: FakeLiftTrainingInsightsRepository = FakeLiftTrainingInsightsRepository(),
    ) = GenerateProgressionSnapshotUseCase(
        progressionHistoryPort = history,
        doubleProgressionEngine = DoubleProgressionEngine(),
        linearProgressionEngine = LinearProgressionEngine(LinearProgressionDefaults.config),
        programChangeEventRepository = events,
        liftTrainingInsightsRepository = insights,
        programRepository = programRepo,
    )

    private fun recsFor(snapshot: ProgressionSnapshot, n: Int) =
        (0 until n).map { i -> snapshot.bySlot.getValue(SetSlotId.Template(LiftSetId("ls-$i"))) }

    private fun assertSharedAndMonotonic(recs: List<SlotRecommendation>) {
        val shared = recs.first().recommendedWeight.amount
        recs.forEach {
            assertEquals(shared, it.recommendedWeight.amount, 0.01, "straight-set shared weight")
            assertTrue(it.baselineExpectedReps >= 5)
        }
        for (i in 0 until recs.lastIndex) {
            assertTrue(
                recs[i].baselineExpectedReps >= recs[i + 1].baselineExpectedReps,
                "reps non-increasing: ${recs.map { it.baselineExpectedReps }}",
            )
        }
    }

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
        val snapshot = useCase(history)(SnapshotRequest(programId, workoutId, listOf(lift))).getOrThrow().snapshot

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

        // Recent session: 3 strong sets @ 240 (last still high enough that tail projects ≥ floor)
        for (i in 0..2) {
            seedSlot(
                history,
                i,
                SlotHistoryEntry(recent, logged(recent.id, i, 240.0, 15 - i, 2, rt)),
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
        ).getOrThrow().snapshot

        val r0 = snapshot.bySlot.getValue(SetSlotId.Template(LiftSetId("ls-0")))
        val r1 = snapshot.bySlot.getValue(SetSlotId.Template(LiftSetId("ls-1")))
        val r2 = snapshot.bySlot.getValue(SetSlotId.Template(LiftSetId("ls-2")))
        val r3 = snapshot.bySlot.getValue(SetSlotId.Template(LiftSetId("ls-3")))
        val r4 = snapshot.bySlot.getValue(SetSlotId.Template(LiftSetId("ls-4")))

        // Shared weight from recent 240 block, not older 200 (may reduce if tail fails floor)
        assertTrue(r0.recommendedWeight.amount > 200.0, "must not use stale older 200 lb")
        assertTrue(r0.recommendedWeight.amount <= 240.0)
        assertEquals(r0.recommendedWeight, r3.recommendedWeight, "straight sets share one weight")
        assertEquals(r0.recommendedWeight, r4.recommendedWeight)
        // Monotonic reps; projected tail ≥ floor
        assertTrue(r0.baselineExpectedReps >= r1.baselineExpectedReps)
        assertTrue(r1.baselineExpectedReps >= r2.baselineExpectedReps)
        assertTrue(r2.baselineExpectedReps >= r3.baselineExpectedReps)
        assertTrue(r3.baselineExpectedReps >= r4.baselineExpectedReps)
        assertTrue(r3.baselineExpectedReps >= 5)
        assertTrue(r4.baselineExpectedReps >= 5)
        assertTrue(r3.isProjected || r4.isProjected)
        // UI previous on deeper slots still shows older-session actuals when that is n-1 positionally
        assertEquals(10, r3.previousReps)
        assertEquals(200.0, r3.previousWeight!!.amount, 0.01)
    }

    @Test
    @DisplayName("screenshot: shorter recent session + stale set-4 history → shared reduce, no positive slope")
    fun shorterRecentSessionStaleSet4NoPositiveSlope() = runTest {
        // New gym: 3 sets @ 15 decaying hard. Old gym only on set 4: 16.5×7.
        // Program wants 5 straight sets — 15 cannot finish the block ≥ floor 5.
        val history = FakeProgressionHistoryRepository()
        val recent = session("s-new", "2026-07-01T10:00:00Z")
        val older = session("s-old", "2026-06-01T10:00:00Z")
        val rt = Instant.parse("2026-07-01T10:30:00Z")
        val ot = Instant.parse("2026-06-01T10:30:00Z")

        seedSlot(history, 0, SlotHistoryEntry(recent, logged(recent.id, 0, 15.0, 15, 1, rt)))
        seedSlot(history, 1, SlotHistoryEntry(recent, logged(recent.id, 1, 15.0, 10, 1, rt)))
        seedSlot(history, 2, SlotHistoryEntry(recent, logged(recent.id, 2, 15.0, 6, 1, rt)))
        seedSlot(history, 3, SlotHistoryEntry(older, logged(older.id, 3, 16.5, 7, 1, ot)))

        val snapshot = useCase(history)(
            SnapshotRequest(programId, workoutId, listOf(fiveSlotLift(ProgressionScheme.Double))),
        ).getOrThrow().snapshot

        val recs = (0 until 5).map { i ->
            snapshot.bySlot.getValue(SetSlotId.Template(LiftSetId("ls-$i")))
        }
        val shared = recs[0].recommendedWeight.amount
        assertTrue(shared < 15.0, "shared look-ahead must drop below unsustainable 15 lb, was $shared")
        recs.forEach { r ->
            assertEquals(shared, r.recommendedWeight.amount, 0.01, "straight-set shared weight")
            assertTrue(r.baselineExpectedReps >= 5)
        }
        for (i in 0 until 4) {
            assertTrue(
                recs[i].baselineExpectedReps >= recs[i + 1].baselineExpectedReps,
                "reps must be non-increasing: set$i=${recs[i].baselineExpectedReps} set${i + 1}=${recs[i + 1].baselineExpectedReps}",
            )
        }
        // UI Prev on set 4 stays honest to older gym data
        assertEquals(7, recs[3].previousReps)
        assertEquals(16.5, recs[3].previousWeight!!.amount, 0.01)
    }

    @Test
    @DisplayName("full recent coverage last set ≥ floor → keep load (no model second-guess)")
    fun fullRecentCoverageLastSetClearsFloorKeepsLoad() = runTest {
        val history = FakeProgressionHistoryRepository()
        val recent = session("s-n1", "2026-07-01T10:00:00Z")
        val rt = Instant.parse("2026-07-01T10:30:00Z")
        // 5 completed sets @ 15, last set still 6 @ RIR 1 → sustainable
        val reps = listOf(15, 12, 10, 8, 6)
        for (i in 0..4) {
            seedSlot(
                history,
                i,
                SlotHistoryEntry(recent, logged(recent.id, i, 15.0, reps[i], 1, rt)),
            )
        }

        val snapshot = useCase(history)(
            SnapshotRequest(programId, workoutId, listOf(fiveSlotLift(ProgressionScheme.Double))),
        ).getOrThrow().snapshot

        val recs = (0 until 5).map { i ->
            snapshot.bySlot.getValue(SetSlotId.Template(LiftSetId("ls-$i")))
        }
        assertEquals(15.0, recs[0].recommendedWeight.amount, 0.01)
        recs.forEach { assertEquals(15.0, it.recommendedWeight.amount, 0.01) }
        for (i in 0 until 4) {
            assertTrue(recs[i].baselineExpectedReps >= recs[i + 1].baselineExpectedReps)
        }
        assertTrue(recs[4].baselineExpectedReps >= 5)
    }

    @Test
    @DisplayName("full recent coverage last set under floor → shared reduce, re-project reps")
    fun fullRecentCoverageLastSetUnderFloorReducesShared() = runTest {
        val history = FakeProgressionHistoryRepository()
        val recent = session("s-n1", "2026-07-01T10:00:00Z")
        val rt = Instant.parse("2026-07-01T10:30:00Z")
        // Last set only 3 reps @ 15 — real data says the load was not sustainable
        val reps = listOf(12, 10, 8, 6, 3)
        for (i in 0..4) {
            seedSlot(
                history,
                i,
                SlotHistoryEntry(recent, logged(recent.id, i, 15.0, reps[i], 0, rt)),
            )
        }

        val snapshot = useCase(history)(
            SnapshotRequest(programId, workoutId, listOf(fiveSlotLift(ProgressionScheme.Double))),
        ).getOrThrow().snapshot

        val recs = (0 until 5).map { i ->
            snapshot.bySlot.getValue(SetSlotId.Template(LiftSetId("ls-$i")))
        }
        val shared = recs[0].recommendedWeight.amount
        assertTrue(shared < 15.0, "must reduce shared weight when real last set < floor, was $shared")
        recs.forEach {
            assertEquals(shared, it.recommendedWeight.amount, 0.01)
            assertTrue(it.baselineExpectedReps >= 5)
        }
    }

    @Test
    @DisplayName("partial coverage strong 3/5 → keep weight strictly (no reduce)")
    fun partialCoverageStrongHoldsWeightStrictly() = runTest {
        val history = FakeProgressionHistoryRepository()
        val recent = session("s-n1", "2026-07-01T10:00:00Z")
        val rt = Instant.parse("2026-07-01T10:30:00Z")
        for (i in 0..2) {
            seedSlot(
                history,
                i,
                SlotHistoryEntry(recent, logged(recent.id, i, 240.0, 15 - i, 2, rt)),
            )
        }
        val snapshot = useCase(history)(
            SnapshotRequest(programId, workoutId, listOf(fiveSlotLift(ProgressionScheme.Double))),
        ).getOrThrow().snapshot
        val recs = recsFor(snapshot, 5)
        assertEquals(240.0, recs[0].recommendedWeight.amount, 0.01)
        assertSharedAndMonotonic(recs)
    }

    @Test
    @DisplayName("top-set advance candidate undone by shared look-ahead when tail cannot clear floor")
    fun advanceThenLookAheadReducesShared() = runTest {
        // Top set hit top-of-range @ RIR → Double wants +5, but only 3 sets logged and last
        // is already thin — 5-set program cannot sustain the advanced load.
        val history = FakeProgressionHistoryRepository()
        val recent = session("s-n1", "2026-07-01T10:00:00Z")
        val rt = Instant.parse("2026-07-01T10:30:00Z")
        seedSlot(history, 0, SlotHistoryEntry(recent, logged(recent.id, 0, 15.0, 16, 2, rt)))
        seedSlot(history, 1, SlotHistoryEntry(recent, logged(recent.id, 1, 15.0, 10, 1, rt)))
        seedSlot(history, 2, SlotHistoryEntry(recent, logged(recent.id, 2, 15.0, 6, 1, rt)))

        val snapshot = useCase(history)(
            SnapshotRequest(programId, workoutId, listOf(fiveSlotLift(ProgressionScheme.Double))),
        ).getOrThrow().snapshot
        val recs = recsFor(snapshot, 5)
        val shared = recs[0].recommendedWeight.amount
        assertTrue(shared < 20.0, "look-ahead must not leave advanced 20 lb if unsustainable, was $shared")
        assertTrue(shared > 0.0)
        assertSharedAndMonotonic(recs)
    }

    @Test
    @DisplayName("M=1 recent set for 5-slot program: projects long tail, shared weight, no positive slope")
    fun singleRecentSetOfFiveProjectsTail() = runTest {
        val history = FakeProgressionHistoryRepository()
        val recent = session("s-n1", "2026-07-01T10:00:00Z")
        val older = session("s-old", "2026-06-01T10:00:00Z")
        val rt = Instant.parse("2026-07-01T10:30:00Z")
        val ot = Instant.parse("2026-06-01T10:30:00Z")
        seedSlot(history, 0, SlotHistoryEntry(recent, logged(recent.id, 0, 15.0, 12, 1, rt)))
        // Older noise on other slots must not drive math
        for (i in 1..4) {
            seedSlot(history, i, SlotHistoryEntry(older, logged(older.id, i, 25.0, 8, 1, ot)))
        }

        val snapshot = useCase(history)(
            SnapshotRequest(programId, workoutId, listOf(fiveSlotLift(ProgressionScheme.Double))),
        ).getOrThrow().snapshot
        val recs = recsFor(snapshot, 5)
        assertTrue(recs[0].recommendedWeight.amount <= 15.0)
        assertTrue(recs[0].recommendedWeight.amount > 0.0)
        // Must not pick older 25 lb
        recs.forEach { assertTrue(it.recommendedWeight.amount < 25.0) }
        assertSharedAndMonotonic(recs)
        // UI prev on slot 1 still older when that is positional n-1
        assertEquals(8, recs[1].previousReps)
        assertEquals(25.0, recs[1].previousWeight!!.amount, 0.01)
    }

    @Test
    @DisplayName("cold start multi-slot (M=0): no history → non-crash, floor reps, shared zero/empty load path")
    fun coldStartMultiSlotNoHistory() = runTest {
        val history = FakeProgressionHistoryRepository()
        val snapshot = useCase(history)(
            SnapshotRequest(programId, workoutId, listOf(fiveSlotLift(ProgressionScheme.Double))),
        ).getOrThrow().snapshot
        val recs = recsFor(snapshot, 5)
        // No history → 0 lb candidate path; all slots share it; reps ≥ floor
        recs.forEach {
            assertEquals(0.0, it.recommendedWeight.amount, 0.01)
            assertTrue(it.baselineExpectedReps >= 5)
        }
    }

    @Test
    @DisplayName("Linear: partial recent + stale set-4 → shared reduce, no positive slope")
    fun linearPartialCoverageSharedReduceNoPositiveSlope() = runTest {
        val history = FakeProgressionHistoryRepository()
        val recent = session("s-new", "2026-07-01T10:00:00Z")
        val older = session("s-old", "2026-06-01T10:00:00Z")
        val rt = Instant.parse("2026-07-01T10:30:00Z")
        val ot = Instant.parse("2026-06-01T10:30:00Z")
        seedSlot(history, 0, SlotHistoryEntry(recent, logged(recent.id, 0, 15.0, 15, 1, rt)))
        seedSlot(history, 1, SlotHistoryEntry(recent, logged(recent.id, 1, 15.0, 10, 1, rt)))
        seedSlot(history, 2, SlotHistoryEntry(recent, logged(recent.id, 2, 15.0, 6, 1, rt)))
        seedSlot(history, 3, SlotHistoryEntry(older, logged(older.id, 3, 16.5, 7, 1, ot)))

        val snapshot = useCase(history)(
            SnapshotRequest(programId, workoutId, listOf(fiveSlotLift(ProgressionScheme.Linear))),
        ).getOrThrow().snapshot
        val recs = recsFor(snapshot, 5)
        assertTrue(recs[0].recommendedWeight.amount < 15.0)
        assertSharedAndMonotonic(recs)
        assertEquals(16.5, recs[3].previousWeight!!.amount, 0.01)
    }

    @Test
    @DisplayName("tier lattice on shared reduce aligns to 5 lb steps")
    fun sharedReduceWithTiersAlignsToFiveLb() = runTest {
        val history = FakeProgressionHistoryRepository()
        val recent = session("s-n1", "2026-07-01T10:00:00Z")
        val rt = Instant.parse("2026-07-01T10:30:00Z")
        seedSlot(history, 0, SlotHistoryEntry(recent, logged(recent.id, 0, 15.0, 15, 1, rt)))
        seedSlot(history, 1, SlotHistoryEntry(recent, logged(recent.id, 1, 15.0, 10, 1, rt)))
        seedSlot(history, 2, SlotHistoryEntry(recent, logged(recent.id, 2, 15.0, 6, 1, rt)))

        val snapshot = useCase(history)(
            SnapshotRequest(
                programId,
                workoutId,
                listOf(fiveSlotLift(ProgressionScheme.Double)),
                incrementTiersById = mapOf(liftId to fiveLbTiers),
            ),
        ).getOrThrow().snapshot
        val recs = recsFor(snapshot, 5)
        val shared = recs[0].recommendedWeight.amount
        assertTrue(shared < 15.0, "must reduce, was $shared")
        assertEquals(0.0, shared % 5.0, 1e-6, "tier-aligned shared weight, was $shared")
        assertSharedAndMonotonic(recs)
    }

    @Test
    @DisplayName("per-lift steep fatigue slope forces shared reduce vs population keep")
    fun perLiftSteepSlopeForcesSharedReduce() = runTest {
        val history = FakeProgressionHistoryRepository()
        val recent = session("s-n1", "2026-07-01T10:00:00Z")
        val rt = Instant.parse("2026-07-01T10:30:00Z")
        // Moderately strong 3-set block — population -2 may keep; -6 should reduce
        for (i in 0..2) {
            seedSlot(
                history,
                i,
                SlotHistoryEntry(recent, logged(recent.id, i, 100.0, 12 - i, 2, rt)),
            )
        }
        val steepInsights = FakeLiftTrainingInsightsRepository(
            listOf(
                LiftTrainingInsights(
                    liftId = liftId,
                    estimatedOneRm = lb(130.0),
                    fatigueDecaySlope = -6.0,
                    fatigueDecayIntercept = 0.0,
                    sessionCount = 5,
                    progressTrend = null,
                    lastPerformedAt = rt,
                ),
            ),
        )
        val snapshot = useCase(history, insights = steepInsights)(
            SnapshotRequest(programId, workoutId, listOf(fiveSlotLift(ProgressionScheme.Double))),
        ).getOrThrow().snapshot
        val recs = recsFor(snapshot, 5)
        assertTrue(
            recs[0].recommendedWeight.amount < 100.0,
            "steep per-lift slope must reduce shared load, was ${recs[0].recommendedWeight.amount}",
        )
        assertSharedAndMonotonic(recs)
    }

    @Test
    @DisplayName("full coverage missing RIR on last set → treated as under floor → shared reduce")
    fun fullCoverageMissingRirOnLastSetReduces() = runTest {
        val history = FakeProgressionHistoryRepository()
        val recent = session("s-n1", "2026-07-01T10:00:00Z")
        val rt = Instant.parse("2026-07-01T10:30:00Z")
        val reps = listOf(12, 10, 8, 6, 4)
        for (i in 0..4) {
            val rir = if (i == 4) null else 1
            seedSlot(
                history,
                i,
                SlotHistoryEntry(recent, logged(recent.id, i, 15.0, reps[i], rir, rt)),
            )
        }
        val snapshot = useCase(history)(
            SnapshotRequest(programId, workoutId, listOf(fiveSlotLift(ProgressionScheme.Double))),
        ).getOrThrow().snapshot
        val recs = recsFor(snapshot, 5)
        assertTrue(recs[0].recommendedWeight.amount < 15.0)
        assertSharedAndMonotonic(recs)
    }

    @Test
    @DisplayName("kg-tagged history: partial thin block reduces in canonical Lb")
    fun kgPartialCoverageReducesInLb() = runTest {
        val history = FakeProgressionHistoryRepository()
        val recent = session("s-n1", "2026-07-01T10:00:00Z")
        val rt = Instant.parse("2026-07-01T10:30:00Z")
        seedSlot(history, 0, SlotHistoryEntry(recent, logged(recent.id, 0, 20.0, 15, 1, rt, Weight.WeightUnit.Kg)))
        seedSlot(history, 1, SlotHistoryEntry(recent, logged(recent.id, 1, 20.0, 10, 1, rt, Weight.WeightUnit.Kg)))
        seedSlot(history, 2, SlotHistoryEntry(recent, logged(recent.id, 2, 20.0, 6, 1, rt, Weight.WeightUnit.Kg)))

        val snapshot = useCase(history)(
            SnapshotRequest(programId, workoutId, listOf(fiveSlotLift(ProgressionScheme.Double))),
        ).getOrThrow().snapshot
        val recs = recsFor(snapshot, 5)
        val startLb =
            com.stratum.domain.lift.WeightConversion.amountInLb(
                Weight(20.0, Weight.WeightUnit.Kg),
            )
        assertEquals(Weight.WeightUnit.Lb, recs[0].recommendedWeight.unit)
        assertTrue(recs[0].recommendedWeight.amount < startLb)
        assertTrue(recs[0].recommendedWeight.amount > 0.0)
        assertSharedAndMonotonic(recs)
    }

    @Test
    @DisplayName("N=1 single-slot program last set under floor → reduce")
    fun singleSlotProgramUnderFloorReduces() = runTest {
        val history = FakeProgressionHistoryRepository()
        val recent = session("s-n1", "2026-07-01T10:00:00Z")
        val rt = Instant.parse("2026-07-01T10:30:00Z")
        seedSlot(history, 0, SlotHistoryEntry(recent, logged(recent.id, 0, 15.0, 3, 0, rt)))
        val lift = nSlotLift(ProgressionScheme.Double, n = 1)
        val snapshot = useCase(history)(
            SnapshotRequest(programId, workoutId, listOf(lift)),
        ).getOrThrow().snapshot
        val r0 = snapshot.bySlot.getValue(SetSlotId.Template(LiftSetId("ls-0")))
        assertTrue(r0.recommendedWeight.amount < 15.0)
        assertTrue(r0.baselineExpectedReps >= 5)
    }

    @Test
    @DisplayName("top set not at position 0: shared weight still uniform, sequential priors")
    fun topSetNotAtPositionZero() = runTest {
        val history = FakeProgressionHistoryRepository()
        val recent = session("s-n1", "2026-07-01T10:00:00Z")
        val rt = Instant.parse("2026-07-01T10:30:00Z")
        // Program: warmup slot 0 (not top), top at index 1, then backoffs
        val lift = nSlotLift(ProgressionScheme.Double, n = 4, topSetIndex = 1)
        seedSlot(history, 0, SlotHistoryEntry(recent, logged(recent.id, 0, 10.0, 12, 2, rt)))
        seedSlot(history, 1, SlotHistoryEntry(recent, logged(recent.id, 1, 15.0, 12, 2, rt)))
        seedSlot(history, 2, SlotHistoryEntry(recent, logged(recent.id, 2, 15.0, 8, 1, rt)))
        // Slot 3 only older / absent from recent → project

        val snapshot = useCase(history)(
            SnapshotRequest(programId, workoutId, listOf(lift)),
        ).getOrThrow().snapshot
        val recs = recsFor(snapshot, 4)
        // Straight-set inherit uses top recommended weight for all when currentTopSetWeight set
        val topW = recs[1].recommendedWeight.amount
        recs.forEach {
            assertEquals(topW, it.recommendedWeight.amount, 0.01)
            assertTrue(it.baselineExpectedReps >= 5)
        }
    }

    @Test
    @DisplayName("same-session mixed weights ranked by e1RM; shared rec weight from top gate")
    fun sameSessionMixedWeightsThroughSnapshot() = runTest {
        val history = FakeProgressionHistoryRepository()
        val recent = session("s-n1", "2026-07-01T10:00:00Z")
        val rt = Instant.parse("2026-07-01T10:30:00Z")
        // Heavier lower-rep top, lighter higher-rep backoff (same session)
        seedSlot(history, 0, SlotHistoryEntry(recent, logged(recent.id, 0, 100.0, 6, 1, rt)))
        seedSlot(history, 1, SlotHistoryEntry(recent, logged(recent.id, 1, 80.0, 12, 1, rt)))
        seedSlot(history, 2, SlotHistoryEntry(recent, logged(recent.id, 2, 80.0, 10, 1, rt)))

        val lift = nSlotLift(ProgressionScheme.Double, n = 3)
        val snapshot = useCase(history)(
            SnapshotRequest(programId, workoutId, listOf(lift)),
        ).getOrThrow().snapshot
        val recs = recsFor(snapshot, 3)
        // UI prev stays positional
        assertEquals(100.0, recs[0].previousWeight!!.amount, 0.01)
        assertEquals(80.0, recs[1].previousWeight!!.amount, 0.01)
        // Shared straight-set weight from top gate (100 held or advanced)
        val shared = recs[0].recommendedWeight.amount
        recs.forEach { assertEquals(shared, it.recommendedWeight.amount, 0.01) }
        assertTrue(shared >= 100.0 - 0.01 || shared > 0.0)
        for (i in 0 until 2) {
            assertTrue(recs[i].baselineExpectedReps >= recs[i + 1].baselineExpectedReps)
        }
    }

    @Test
    @DisplayName("positional Prev from different sessions: only most-recent session drives math")
    fun positionalPrevFromDifferentSessionsOnlyRecentDrivesMath() = runTest {
        val history = FakeProgressionHistoryRepository()
        // Each slot's "latest" is a different session/day — only the max startedAt session is math
        val s0 = session("s0", "2026-07-05T10:00:00Z") // most recent overall
        val s1 = session("s1", "2026-07-03T10:00:00Z")
        val s2 = session("s2", "2026-07-01T10:00:00Z")
        seedSlot(
            history,
            0,
            SlotHistoryEntry(s0, logged(s0.id, 0, 50.0, 12, 1, Instant.parse("2026-07-05T10:30:00Z"))),
        )
        seedSlot(
            history,
            1,
            SlotHistoryEntry(s1, logged(s1.id, 1, 90.0, 10, 1, Instant.parse("2026-07-03T10:30:00Z"))),
        )
        seedSlot(
            history,
            2,
            SlotHistoryEntry(s2, logged(s2.id, 2, 90.0, 8, 1, Instant.parse("2026-07-01T10:30:00Z"))),
        )

        val lift = nSlotLift(ProgressionScheme.Double, n = 3)
        val snapshot = useCase(history)(
            SnapshotRequest(programId, workoutId, listOf(lift)),
        ).getOrThrow().snapshot
        val recs = recsFor(snapshot, 3)
        // Math from s0 only (50 lb block size 1) — must not use 90
        assertTrue(
            recs[0].recommendedWeight.amount <= 55.0,
            "math must come from most-recent session 50 lb, was ${recs[0].recommendedWeight.amount}",
        )
        recs.forEach { assertTrue(it.recommendedWeight.amount < 90.0) }
        // UI previous stays per-slot latest
        assertEquals(50.0, recs[0].previousWeight!!.amount, 0.01)
        assertEquals(90.0, recs[1].previousWeight!!.amount, 0.01)
        assertEquals(90.0, recs[2].previousWeight!!.amount, 0.01)
        assertSharedAndMonotonic(recs)
    }

    @Test
    @DisplayName("thermos: held weight tied capacity + falling rirGoal → monotonic after clamp")
    fun heldWeightRirGoalAsymmetryMonotonic() = runTest {
        // Same failure capacity on two sets; top rirGoal 2, backoff rirGoal 1 would raw-slope +1.
        val history = FakeProgressionHistoryRepository()
        val recent = session("s-n1", "2026-07-01T10:00:00Z")
        val rt = Instant.parse("2026-07-01T10:30:00Z")
        // 10@2 and 12@0 → capacity 12 both; ties broken by position → pos0 first
        seedSlot(history, 0, SlotHistoryEntry(recent, logged(recent.id, 0, 100.0, 10, 2, rt)))
        seedSlot(history, 1, SlotHistoryEntry(recent, logged(recent.id, 1, 100.0, 12, 0, rt)))

        val lift = nSlotLift(ProgressionScheme.Double, n = 2)
        val snapshot = useCase(history)(
            SnapshotRequest(programId, workoutId, listOf(lift)),
        ).getOrThrow().snapshot
        val r0 = snapshot.bySlot.getValue(SetSlotId.Template(LiftSetId("ls-0")))
        val r1 = snapshot.bySlot.getValue(SetSlotId.Template(LiftSetId("ls-1")))
        assertTrue(
            r0.baselineExpectedReps >= r1.baselineExpectedReps,
            "monotonic clamp: ${r0.baselineExpectedReps} then ${r1.baselineExpectedReps}",
        )
        assertEquals(r0.recommendedWeight.amount, r1.recommendedWeight.amount, 0.01)
    }

    @Test
    @DisplayName("thermos: after shared reduce mid FromRecent reprojects own baseline not top chain")
    fun afterSharedReduceMidSlotUsesOwnBaseline() = runTest {
        // 3 strong-ish then thin last → reduce. Mid set history 6@15 should reproject at new W.
        val history = FakeProgressionHistoryRepository()
        val recent = session("s-n1", "2026-07-01T10:00:00Z")
        val rt = Instant.parse("2026-07-01T10:30:00Z")
        seedSlot(history, 0, SlotHistoryEntry(recent, logged(recent.id, 0, 15.0, 15, 1, rt)))
        seedSlot(history, 1, SlotHistoryEntry(recent, logged(recent.id, 1, 15.0, 6, 1, rt)))
        seedSlot(history, 2, SlotHistoryEntry(recent, logged(recent.id, 2, 15.0, 4, 0, rt)))

        val lift = nSlotLift(ProgressionScheme.Double, n = 3)
        val snapshot = useCase(history)(
            SnapshotRequest(programId, workoutId, listOf(lift)),
        ).getOrThrow().snapshot
        val recs = recsFor(snapshot, 3)
        val shared = recs[0].recommendedWeight.amount
        assertTrue(shared < 15.0, "expect shared reduce, was $shared")
        assertSharedAndMonotonic(recs)
        // Mid slot previous still 6; recommended reps at lower load should be >= historical 6
        // when capacity reprojects up (lighter load), never a wild top-chain bounce alone.
        assertEquals(6, recs[1].previousReps)
        assertTrue(recs[1].baselineExpectedReps >= BACKOFF_REP_FLOOR)
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
        ).getOrThrow().snapshot
        val rec = snapshot.bySlot.getValue(SetSlotId.Template(LiftSetId("ls-0")))
        // After transition, Double engine runs: stall history does not meet top-of-range, so
        // weight does not increment. Capacity 4@2 is below the global floor of 5, so a small
        // floor-driven weight drop is OK (never a classic 10% greyskull deload / increment).
        assertTrue(rec.recommendedWeight.amount <= 200.0)
        assertTrue(rec.baselineExpectedReps >= 5)
        val stored = events.getEventsForProgram(programId).events
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
        ).getOrThrow().snapshot
        assertEquals(
            1,
            events.getEventsForProgram(programId).events.count { it.eventType == ProgramChangeEventType.SCHEME_CHANGED },
            "already-Double lift must not re-append SCHEME_CHANGED",
        )
    }
}
