package com.stratum.application.workout

import com.stratum.core.testing.importing.FakeLiftTrainingInsightsRepository
import com.stratum.core.testing.program.FakeProgramRepository
import com.stratum.core.testing.training.FakeProgramChangeEventRepository
import com.stratum.core.testing.workout.FakeProgressionHistoryRepository
import com.stratum.domain.importing.identity.LoggedSetId
import com.stratum.domain.lift.FatigueProfile
import com.stratum.domain.lift.LoadType
import com.stratum.domain.lift.MuscleLengthBias
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.Weekday
import com.stratum.domain.program.LiftSet
import com.stratum.domain.program.ProgressionScheme
import com.stratum.domain.program.SetType
import com.stratum.domain.program.WorkoutLift
import com.stratum.domain.program.identity.LiftSetId
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
import com.stratum.domain.workout.progression.DoubleProgressionEngine
import com.stratum.domain.workout.progression.LinearProgressionDefaults
import com.stratum.domain.workout.progression.LinearProgressionEngine
import com.stratum.domain.workout.progression.ProgressionDiagnostic
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import com.stratum.domain.training.EstimatedOneRm
import com.stratum.domain.workout.ProgressionSnapshotOutcome
import com.stratum.domain.workout.SetSlotId
import com.stratum.domain.workout.SlotHistoryEntry
import com.stratum.domain.workout.SlotHistoryRequest

/**
 * STR-250 regression: a Weighted-typed lift whose history was performed at zero load
 * (the Ab Roller catalog bug — seed typed it Weighted, the user performs it
 * bodyweight, every logged set is 0 lb) derives an estimated 1RM of 0.0. Load math
 * fed that e1RM into `EstimatedOneRm.repsForWeight`, which throws
 * `IllegalArgumentException: estimatedOneRm must be positive` and killed the session
 * actor at workout start. Snapshot computation must now complete, leave the
 * candidate weight unchanged at each step that lost its baseline, and record a
 * [ProgressionDiagnostic.BaselineSkipped] per lost baseline.
 */
@DisplayName("Progression snapshot with zero-load history (STR-250)")
class ProgressionZeroLoadHistoryTest {

    private val programId = ProgramId("p-1")
    private val workoutId = WorkoutId("w-1")
    private val workoutLiftId = WorkoutLiftId("wl-1")
    private val liftId = LiftId("ab-roller")

    private fun lb(amount: Double) = Weight(amount, Weight.WeightUnit.Lb)

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
    ) = LoggedSet(
        id = LoggedSetId("set-${sessionId.value}-$setIndex"),
        sessionId = sessionId,
        exerciseNameRaw = "Ab Roller",
        liftId = liftId,
        setIndex = setIndex,
        weight = lb(weight),
        reps = reps,
        rir = rir,
        completedAt = Instant.parse("2026-07-01T10:30:00Z"),
        liftContext = LoggedLiftContext(
            liftName = "Ab Roller",
            movementPatternsBitmask = 1,
            primaryMusclesBitmask = 1,
            secondaryMusclesBitmask = 0,
            fatigueProfile = FatigueProfile.Low,
            muscleLengthBias = MuscleLengthBias.MidRange,
        ),
    )

    private fun useCase(history: FakeProgressionHistoryRepository) =
        GenerateProgressionSnapshotUseCase(
            progressionHistoryPort = history,
            doubleProgressionEngine = DoubleProgressionEngine(),
            linearProgressionEngine = LinearProgressionEngine(LinearProgressionDefaults.config),
            programChangeEventRepository = FakeProgramChangeEventRepository(),
            liftTrainingInsightsRepository = FakeLiftTrainingInsightsRepository(),
            programRepository = FakeProgramRepository(),
        )

    private fun liftWithSets(sets: List<LiftSet>) = WorkoutLift(
        id = workoutLiftId,
        workoutId = workoutId,
        liftId = liftId,
        progressionScheme = ProgressionScheme.Double,
        position = 0,
        sets = sets,
    )

    private fun liftSet(
        index: Int,
        setType: SetType,
        isTopSet: Boolean,
        rirGoal: Int,
    ) = LiftSet(
        id = LiftSetId("ls-$index"),
        workoutLiftId = workoutLiftId,
        position = index,
        setType = setType,
        repMin = 8,
        repMax = 12,
        rirGoal = rirGoal,
        isTopSet = isTopSet,
    )

    private fun seed(
        history: FakeProgressionHistoryRepository,
        session: TrainingSession,
        setIndex: Int,
        weight: Double,
        reps: Int,
        rir: Int?,
    ) {
        history.seed(
            SlotHistoryRequest(programId, workoutId, workoutLiftId, liftId, setIndex),
            SlotHistoryEntry(session, logged(session.id, setIndex, weight, reps, rir)),
        )
    }

    private fun skippedSites(
        outcome: ProgressionSnapshotOutcome,
    ): Set<Pair<ProgressionDiagnostic.BaselineSkipped.Site, Int>> =
        outcome.diagnostics
            .filterIsInstance<ProgressionDiagnostic.BaselineSkipped>()
            .map { it.site to it.slotPosition }
            .toSet()

    @Test
    @DisplayName("Ab Roller shape: zero-load block completes, weight untightened, skips recorded")
    fun zeroLoadBlockCompletesWithDiagnostics() = runTest {
        val history = FakeProgressionHistoryRepository()
        val s = session("s-1", "2026-07-01T10:00:00Z")
        // Performed bodyweight: both sets logged at 0 lb. Top set capacity 15+1
        // clears repMax 12 + rirGoal 2, so the Double gate advances 0 → 5 lb and
        // tightening/reprojection run against the poisoned baselines.
        seed(history, s, 0, 0.0, 15, 1)
        seed(history, s, 1, 0.0, 9, 1)

        val lift = liftWithSets(
            listOf(
                liftSet(0, SetType.Standard, isTopSet = true, rirGoal = 2),
                liftSet(1, SetType.Standard, isTopSet = false, rirGoal = 1),
            ),
        )
        val outcome = useCase(history)(
            SnapshotRequest(
                programId,
                workoutId,
                listOf(lift),
                loadTypesById = mapOf(liftId to LoadType.Weighted),
            ),
        ).getOrThrow()

        // Snapshot completes for both slots at the untightened candidate weight.
        assertEquals(2, outcome.snapshot.bySlot.size)
        outcome.snapshot.bySlot.values.forEach { rec ->
            assertEquals(5.0, rec.recommendedWeight.amount, 0.001)
        }
        // Every load-math step that lost its baseline recorded exactly one skip:
        // tightening for both covered slots, reprojection for the backoff slot.
        assertEquals(
            setOf(
                ProgressionDiagnostic.BaselineSkipped.Site.Tightening to 0,
                ProgressionDiagnostic.BaselineSkipped.Site.Tightening to 1,
                ProgressionDiagnostic.BaselineSkipped.Site.Reprojection to 1,
            ),
            skippedSites(outcome),
        )
        assertEquals(3, outcome.diagnostics.size)
        val first = outcome.diagnostics
            .filterIsInstance<ProgressionDiagnostic.BaselineSkipped>()
            .first { it.site == ProgressionDiagnostic.BaselineSkipped.Site.Tightening }
        assertEquals(liftId, first.liftId)
        assertEquals(0.0, first.historyWeight.amount, 0.001)
        assertEquals(16, first.historyFailureReps)
        assertTrue(outcome.diagnostics.all { it is ProgressionDiagnostic.BaselineSkipped })
    }

    @Test
    @DisplayName("partial tail: zero-load anchor leaves candidate unchanged and records the skip")
    fun zeroLoadPartialTailAnchorSkips() = runTest {
        val history = FakeProgressionHistoryRepository()
        val s = session("s-1", "2026-07-01T10:00:00Z")
        // Recent session covers only the two leading drop slots (excluded from the
        // covered-slot tightening gate), so the partial tail anchors on the last
        // covered performance — a zero-load set.
        seed(history, s, 0, 0.0, 20, 0)
        seed(history, s, 1, 0.0, 12, 0)

        val lift = liftWithSets(
            listOf(
                liftSet(0, SetType.DropSet, isTopSet = true, rirGoal = 0),
                liftSet(1, SetType.DropSet, isTopSet = false, rirGoal = 0),
                liftSet(2, SetType.Standard, isTopSet = false, rirGoal = 1),
                liftSet(3, SetType.Standard, isTopSet = false, rirGoal = 1),
            ),
        )
        val outcome = useCase(history)(
            SnapshotRequest(
                programId,
                workoutId,
                listOf(lift),
                loadTypesById = mapOf(liftId to LoadType.Weighted),
            ),
        ).getOrThrow()

        // Snapshot completes; the straight slots keep the candidate weight — the
        // unusable anchor must behave like the no-anchor path, not crush the load.
        assertEquals(4, outcome.snapshot.bySlot.size)
        val straightRecs = listOf("ls-2", "ls-3").map { id ->
            outcome.snapshot.bySlot.getValue(SetSlotId.Template(LiftSetId(id)))
        }
        straightRecs.forEach { rec ->
            assertEquals(5.0, rec.recommendedWeight.amount, 0.001)
        }
        val tailSkips = outcome.diagnostics
            .filterIsInstance<ProgressionDiagnostic.BaselineSkipped>()
            .filter { it.site == ProgressionDiagnostic.BaselineSkipped.Site.PartialTailAnchor }
        assertEquals(1, tailSkips.size)
        assertEquals(1, tailSkips.single().slotPosition)
        assertEquals(0.0, tailSkips.single().historyWeight.amount, 0.001)
    }
}
