package com.stratum.application.workout

import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.LiftSet
import com.stratum.domain.program.ProgressionScheme
import com.stratum.domain.program.SetType
import com.stratum.domain.program.Workout
import com.stratum.domain.program.WorkoutLift
import com.stratum.domain.program.identity.LiftSetId
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.importing.identity.LoggedSetId
import com.stratum.domain.training.LoggedLiftContext
import com.stratum.domain.training.LoggedSet
import com.stratum.domain.training.TrainingSession
import com.stratum.domain.training.TrainingSessionKind
import com.stratum.domain.training.TrainingSessionOrigin
import com.stratum.domain.training.TrainingSessionStatus
import com.stratum.domain.training.identity.TrainingSessionId
import com.stratum.domain.lift.FatigueProfile
import com.stratum.domain.lift.MuscleLengthBias
import com.stratum.core.testing.training.NoOpProgramChangeEventRepository
import com.stratum.core.testing.workout.FakeProgressionHistoryRepository
import com.stratum.core.testing.importing.FakeLiftTrainingInsightsRepository
import com.stratum.domain.onboarding.Weekday
import com.stratum.domain.workout.progression.DoubleProgressionEngine
import com.stratum.domain.workout.progression.LinearProgressionDefaults
import com.stratum.domain.workout.progression.LinearProgressionEngine
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import com.stratum.domain.importing.ImportHistoryTrainingMapper
import com.stratum.domain.workout.OverlaySetInstanceId
import com.stratum.domain.workout.ProgressionSnapshot
import com.stratum.domain.workout.SetSlot
import com.stratum.domain.workout.SetSlotId
import com.stratum.domain.workout.SlotHistoryEntry
import com.stratum.domain.workout.SlotHistoryRequest
import com.stratum.domain.workout.SlotRecommendation
import com.stratum.domain.workout.StructuralMutation
import com.stratum.domain.workout.WorkoutLiftProjection
import com.stratum.domain.workout.WorkoutOverlay
import com.stratum.domain.workout.WorkoutStructureProjection

/**
 * Slice 1 — Snapshot + merge skeleton. 9 scenarios (S1-*).
 */
@DisplayName("Slice 1: Snapshot + merge skeleton")
class Slice1SnapshotMergeTest {

    private val programId = ProgramId("p-1")
    private val workoutId = WorkoutId("w-1")
    private val workoutLiftId = WorkoutLiftId("wl-1")
    private val liftId = LiftId("L-1")
    private val liftSetId = LiftSetId("ls-1")
    private val slotId = SetSlotId.Template(liftSetId)

    private fun lb(amount: Double) = Weight(amount, Weight.WeightUnit.Lb)

    private fun oneLiftWorkout(): Workout = Workout(
        id = workoutId,
        programId = programId,
        name = "Workout 1",
        weekday = Weekday.Monday,
        position = 0,
        workoutLifts = listOf(
            WorkoutLift(
                id = workoutLiftId,
                workoutId = workoutId,
                liftId = liftId,
                progressionScheme = ProgressionScheme.Double,
                position = 0,
                sets = listOf(
                    LiftSet(
                        id = liftSetId,
                        workoutLiftId = workoutLiftId,
                        position = 0,
                        setType = SetType.Standard,
                        repMin = 4,
                        repMax = 6,
                        rirGoal = 2,
                        isTopSet = true,
                    ),
                ),
            ),
        ),
    )

    @Test
    @DisplayName("S1-structure-projection-empty-overlay")
    fun `S1-structure-projection-empty-overlay`() {
        val useCase = ProjectWorkoutStructureUseCase()
        val projection = useCase(oneLiftWorkout(), mapOf(liftId to "Squat"))

        assertEquals(1, projection.lifts.size)
        assertEquals(1, projection.lifts.first().slots.size)
        val slot = projection.lifts.first().slots.first()
        assertEquals(SetSlotId.Template(LiftSetId("ls-1")), slot.slotId)
        assertEquals(SetType.Standard, slot.setType)
        assertTrue(slot.isTopSet)
        assertEquals(4, slot.repMin)
        assertEquals(6, slot.repMax)
        assertEquals(2, slot.rirGoal)
        // SetSlot has no recommendedWeight field (structure projection carries no load).
        val hasNoRecommendedWeight = SetSlot::class.members.none { it.name == "recommendedWeight" }
        assertTrue(hasNoRecommendedWeight, "SetSlot must not carry recommendedWeight")
    }

    @Test
    @DisplayName("S1-snapshot-immutable")
    fun `S1-snapshot-immutable`() {
        val recommendation = SlotRecommendation(slotId, lb(135.0), 5)
        val snapshot = ProgressionSnapshot(workoutId, mapOf(slotId to recommendation))

        // ProgressionSnapshot is a data class: copy() must exist (structural-contract test
        // that legitimately passes at Red — the stub is already a data class).
        val copied = snapshot.copy()
        assertEquals(snapshot, copied)
        // bySlot is a Map<SetSlotId, SlotRecommendation> keyed by SetSlotId.
        val bySlot: Map<SetSlotId, SlotRecommendation> = snapshot.bySlot
        assertEquals(recommendation, bySlot[slotId])
        // SlotRecommendation carries recommendedWeight + baselineExpectedReps.
        assertEquals(lb(135.0), recommendation.recommendedWeight)
        assertEquals(5, recommendation.baselineExpectedReps)
    }

    @Test
    @DisplayName("S1-snapshot-compute-once")
    fun `S1-snapshot-compute-once`() = runTest {
        val history = FakeProgressionHistoryRepository()
        val session = TrainingSession(
            id = TrainingSessionId("s-n1"),
            programId = programId,
            workoutId = workoutId,
            status = TrainingSessionStatus.COMPLETED,
            kind = TrainingSessionKind.PROGRAM_WORKOUT,
            origin = TrainingSessionOrigin.IMPORT,
            cycleNumberSnapshot = 1,
            weekdaySnapshot = Weekday.Monday,
            startedAt = Instant.parse("2026-06-28T10:00:00Z"),
            endedAt = Instant.parse("2026-06-28T11:00:00Z"),
            sourceApp = null,
        )
        val loggedSet = LoggedSet(
            id = LoggedSetId("set-n1"),
            sessionId = session.id,
            exerciseNameRaw = "Squat",
            liftId = liftId,
            setIndex = 0,
            weight = lb(135.0),
            reps = 5,
            rir = 2,
            completedAt = Instant.parse("2026-06-28T10:30:00Z"),
            liftContext = LoggedLiftContext(
                liftName = "Squat",
                movementPatternsBitmask = 1,
                primaryMusclesBitmask = 1,
                secondaryMusclesBitmask = 0,
                fatigueProfile = FatigueProfile.Low,
                muscleLengthBias = MuscleLengthBias.MidRange,
            ),
        )
        history.seed(
            SlotHistoryRequest(programId, workoutId, workoutLiftId, liftId, 0),
            SlotHistoryEntry(session, loggedSet),
        )
        val useCase =
            GenerateProgressionSnapshotUseCase(
                progressionHistoryPort = history,
                doubleProgressionEngine = DoubleProgressionEngine(),
                linearProgressionEngine = LinearProgressionEngine(LinearProgressionDefaults.config),
                programChangeEventRepository = NoOpProgramChangeEventRepository(),
                liftTrainingInsightsRepository = FakeLiftTrainingInsightsRepository(),
                programRepository = com.stratum.core.testing.program.FakeProgramRepository(),
            )
        val request = SnapshotRequest(programId, workoutId, oneLiftWorkout().workoutLifts)

        val result = useCase(request)
        assertTrue(result.isSuccess)
        val snapshot = result.getOrThrow().snapshot
        assertEquals(lb(135.0), snapshot.bySlot[slotId]?.recommendedWeight)
        assertEquals(5, snapshot.bySlot[slotId]?.baselineExpectedReps)

        // Apply an overlay mutation (add set) and do NOT recompute — the held reference
        // must be unchanged.
        val heldBefore = snapshot.bySlot[slotId]?.recommendedWeight
        val overlay = WorkoutOverlay().apply(
            StructuralMutation.AddSet(
                afterSlotId = slotId,
                newSlot = SetSlot(
                    slotId = SetSlotId.Overlay(OverlaySetInstanceId("added-1")),
                    workoutLiftId = workoutLiftId,
                    liftId = liftId,
                    position = 1,
                    setType = SetType.Standard,
                    isTopSet = false,
                    repMin = 4,
                    repMax = 6,
                    rirGoal = 2,
                    restSeconds = 120,
                ),
            ),
        )
        // invoke was NOT called again — same reference, same values.
        assertEquals(heldBefore, snapshot.bySlot[slotId]?.recommendedWeight)
    }

    @Test
    @DisplayName("S1-history-priority-n-minus-one-first")
    fun `S1-history-priority-n-minus-one-first`() = runTest {
        val history = FakeProgressionHistoryRepository()
        val sN2 = session("s-n2", "2026-06-21T10:00:00Z")
        val sN1 = session("s-n1", "2026-06-28T10:00:00Z")
        history.seed(req(0), SlotHistoryEntry(sN2, loggedSet(sN2.id, lb(130.0))))
        history.seed(req(0), SlotHistoryEntry(sN1, loggedSet(sN1.id, lb(135.0))))

        val entries = history.historyForSlot(req(0))
        assertEquals(2, entries.size)
        assertEquals(TrainingSessionId("s-n1"), entries.first().session.id)
        assertEquals(lb(135.0), entries.first().loggedSet.weight)
    }

    @Test
    @DisplayName("S1-history-priority-earlier-sessions")
    fun `S1-history-priority-earlier-sessions`() = runTest {
        val history = FakeProgressionHistoryRepository()
        val sN3 = session("s-n3", "2026-06-14T10:00:00Z")
        val sN2 = session("s-n2", "2026-06-21T10:00:00Z")
        val sN1 = session("s-n1", "2026-06-28T10:00:00Z")
        history.seed(req(0), SlotHistoryEntry(sN3, loggedSet(sN3.id, lb(120.0))))
        history.seed(req(0), SlotHistoryEntry(sN2, loggedSet(sN2.id, lb(125.0))))
        history.seed(req(0), SlotHistoryEntry(sN1, loggedSet(sN1.id, lb(130.0))))

        val entries = history.historyForSlot(req(0))
        val ids = entries.map { it.session.id.value }
        assertEquals(listOf("s-n1", "s-n2", "s-n3"), ids)
    }

    @Test
    @DisplayName("S1-history-priority-same-lift-other-workout")
    fun `S1-history-priority-same-lift-other-workout`() = runTest {
        val history = FakeProgressionHistoryRepository()
        val sameWorkout = session("s-same", "2026-06-28T10:00:00Z")
        val otherWorkout = session("s-other", "2026-06-29T10:00:00Z")
        history.seed(req(0), SlotHistoryEntry(sameWorkout, loggedSet(sameWorkout.id, lb(135.0))))
        // Same lift from a different workout (w-2).
        val otherReq = SlotHistoryRequest(programId, WorkoutId("w-2"), workoutLiftId, liftId, 0)
        history.seed(otherReq, SlotHistoryEntry(otherWorkout, loggedSet(otherWorkout.id, lb(140.0))))

        val entries = history.historyForSlot(req(0))
        // The same-workout entry must precede the other-workout entry.
        val sameIdx = entries.indexOfFirst { it.session.workoutId == workoutId }
        val otherIdx = entries.indexOfFirst { it.session.workoutId == WorkoutId("w-2") }
        assertTrue(sameIdx in entries.indices, "same-workout entry present")
        assertTrue(otherIdx in entries.indices, "other-workout entry present")
        assertTrue(sameIdx < otherIdx, "same-workout entry must precede other-workout entry")
    }

    @Test
    @DisplayName("S1-history-priority-empty-when-no-history")
    fun `S1-history-priority-empty-when-no-history`() = runTest {
        val history = FakeProgressionHistoryRepository()
        val entries = history.historyForSlot(req(0))
        assertTrue(entries.isEmpty())
    }

    @Test
    @DisplayName("S1-import-history-visible-to-live-program-session")
    fun `S1-import-history-visible-to-live-program-session`() = runTest {
        // Bug #3: imported sessions use sentinel programId "import-history" and null workoutId.
        // Live "Start next workout" must still see that history for previous + weight recs.
        val history = FakeProgressionHistoryRepository()
        val importProgramId = com.stratum.domain.importing.ImportHistoryTrainingMapper.programId
        val importSession = TrainingSession(
            id = TrainingSessionId("import-s1"),
            programId = importProgramId,
            workoutId = null,
            status = TrainingSessionStatus.COMPLETED,
            kind = TrainingSessionKind.AD_HOC,
            origin = TrainingSessionOrigin.IMPORT,
            cycleNumberSnapshot = 1,
            weekdaySnapshot = null,
            startedAt = Instant.parse("2026-06-20T10:00:00Z"),
            endedAt = Instant.parse("2026-06-20T11:00:00Z"),
            sourceApp = "hevy",
            programNameSnapshot = "Import History",
            workoutNameSnapshot = null,
        )
        val importSet = LoggedSet(
            id = LoggedSetId("import-set-1"),
            sessionId = importSession.id,
            exerciseNameRaw = "Squat",
            liftId = liftId,
            setIndex = 0,
            weight = lb(185.0),
            reps = 8,
            rir = 2,
            completedAt = Instant.parse("2026-06-20T10:30:00Z"),
            liftContext = LoggedLiftContext(
                liftName = "Squat",
                movementPatternsBitmask = 1,
                primaryMusclesBitmask = 1,
                secondaryMusclesBitmask = 0,
                fatigueProfile = FatigueProfile.Low,
                muscleLengthBias = MuscleLengthBias.MidRange,
            ),
        )
        // Seed under the import program id (what PersistImportedHistory writes).
        // workoutId on the request is ignored for IMPORT origin (session keeps null).
        history.seed(
            SlotHistoryRequest(importProgramId, workoutId, workoutLiftId, liftId, 0),
            SlotHistoryEntry(importSession, importSet),
        )

        // Live session queries with the *active* program id — must still resolve import rows.
        val entries = history.historyForSlot(req(0))
        assertEquals(1, entries.size)
        assertEquals(lb(185.0), entries.first().loggedSet.weight)
        assertEquals(8, entries.first().loggedSet.reps)

        val useCase =
            GenerateProgressionSnapshotUseCase(
                progressionHistoryPort = history,
                doubleProgressionEngine = DoubleProgressionEngine(),
                linearProgressionEngine = LinearProgressionEngine(LinearProgressionDefaults.config),
                programChangeEventRepository = NoOpProgramChangeEventRepository(),
                liftTrainingInsightsRepository = FakeLiftTrainingInsightsRepository(),
                programRepository = com.stratum.core.testing.program.FakeProgramRepository(),
            )
        val snapshot = useCase(SnapshotRequest(programId, workoutId, oneLiftWorkout().workoutLifts))
            .getOrThrow().snapshot
        val rec = snapshot.bySlot[slotId]
        assertNotNull(rec)
        assertEquals(lb(185.0), rec!!.previousWeight, "Previous column must show imported weight")
        assertEquals(8, rec.previousReps, "Previous column must show imported reps")
        assertEquals(2, rec.previousRir, "Previous column must show imported RIR")
        assertTrue(
            rec.recommendedWeight.amount > 0.0,
            "Weight recommendation must be derived from imported history",
        )
    }

    @Test
    @DisplayName("S1-merge-empty-overlay-renders-snapshot-baseline")
    fun `S1-merge-empty-overlay-renders-snapshot-baseline`() {
        val structure = WorkoutStructureProjection(
            workoutId = workoutId,
            workoutName = "Workout 1",
            lifts = listOf(
                WorkoutLiftProjection(
                    workoutLiftId = workoutLiftId,
                    liftId = liftId,
                    liftName = "Squat",
                    progressionScheme = ProgressionScheme.Double,
                    slots = listOf(
                        SetSlot(
                            slotId = slotId,
                            workoutLiftId = workoutLiftId,
                            liftId = liftId,
                            position = 0,
                            setType = SetType.Standard,
                            isTopSet = true,
                            repMin = 4,
                            repMax = 6,
                            rirGoal = 2,
                            restSeconds = 120,
                        ),
                    ),
                ),
            ),
        )
        val snapshot = ProgressionSnapshot(
            workoutId = workoutId,
            bySlot = mapOf(slotId to SlotRecommendation(slotId, lb(135.0), 5)),
        )
        val overlay = WorkoutOverlay()

        val rendered = MergeRenderedWorkoutUseCase()(structure, snapshot, overlay)
        assertEquals(1, rendered.lifts.size)
        val set = rendered.lifts.first().sets.first()
        assertEquals(lb(135.0), set.recommendedWeight)
        assertEquals(5, set.recommendedReps)
        assertNull(set.loggedValue)
    }

    @Test
    @DisplayName("S1-mutation-defense-recompute-on-add-set")
    fun `S1-mutation-defense-recompute-on-add-set`() {
        val snapshot = ProgressionSnapshot(
            workoutId = workoutId,
            bySlot = mapOf(slotId to SlotRecommendation(slotId, lb(135.0), 5)),
        )
        val overlay = WorkoutOverlay().apply(
            StructuralMutation.AddSet(
                afterSlotId = slotId,
                newSlot = SetSlot(
                    slotId = SetSlotId.Overlay(OverlaySetInstanceId("added-1")),
                    workoutLiftId = workoutLiftId,
                    liftId = liftId,
                    position = 1,
                    setType = SetType.Standard,
                    isTopSet = false,
                    repMin = 4,
                    repMax = 6,
                    rirGoal = 2,
                    restSeconds = 120,
                ),
            ),
        )
        // The snapshot must be untouched by the overlay mutation.
        assertEquals(1, snapshot.bySlot.size)
        assertEquals(lb(135.0), snapshot.bySlot[slotId]?.recommendedWeight)
    }

    private fun req(setIndex: Int) = SlotHistoryRequest(programId, workoutId, workoutLiftId, liftId, setIndex)

    private fun session(id: String, completedAt: String) = TrainingSession(
        id = TrainingSessionId(id),
        programId = programId,
        workoutId = workoutId,
        status = TrainingSessionStatus.COMPLETED,
        kind = TrainingSessionKind.PROGRAM_WORKOUT,
        origin = TrainingSessionOrigin.IMPORT,
        cycleNumberSnapshot = 1,
        weekdaySnapshot = Weekday.Monday,
        startedAt = Instant.parse(completedAt),
        endedAt = Instant.parse(completedAt),
        sourceApp = null,
    )

    private fun loggedSet(sessionId: TrainingSessionId, weight: Weight) = LoggedSet(
        id = LoggedSetId("set-$sessionId"),
        sessionId = sessionId,
        exerciseNameRaw = "Squat",
        liftId = liftId,
        setIndex = 0,
        weight = weight,
        reps = 5,
        rir = 2,
        completedAt = Instant.parse("2026-06-28T10:30:00Z"),
        liftContext = LoggedLiftContext(
            liftName = "Squat",
            movementPatternsBitmask = 1,
            primaryMusclesBitmask = 1,
            secondaryMusclesBitmask = 0,
            fatigueProfile = FatigueProfile.Low,
            muscleLengthBias = MuscleLengthBias.MidRange,
        ),
    )
}
