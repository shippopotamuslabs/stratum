package com.stratum.domain.checkin

import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.core.testing.program.FakeProgramRepository
import com.stratum.core.testing.response.FakeLiftWeeklyPerformanceRepository
import com.stratum.core.testing.training.FakeProgramChangeEventRepository
import com.stratum.domain.dashboard.CALLOUT_MIN_POINTS
import com.stratum.domain.lift.KinematicProfile
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.RankSwapCandidatesUseCase
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
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
import com.stratum.domain.response.TrainingSignalQualityFlag
import com.stratum.domain.response.modeling.LiftWeeklyPerformance
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Integration tests for [EvaluateCheckInUseCase]: trend classification → decide → swap
 * enrichment / no-candidate fallback, with fakes only (UNIT_TESTING.md).
 */
class EvaluateCheckInUseCaseTest {

    private val programId = ProgramId("p-1")
    private val now = Instant.parse("2026-07-01T12:00:00Z")

    @Test
    @DisplayName("no active program → null evaluation")
    fun noProgramReturnsNull() = runTest {
        val useCase = evaluateUseCase(program = null)
        assertNull(useCase(CheckInSubjectiveFeedback(), now))
    }

    @Test
    @DisplayName("stale flat lift with swap candidate → SwapLift action with suggested id")
    fun staleFlatEnrichesSwap() = runTest {
        val lifts = FakeLiftRepository()
        lifts.seedLifts(listOf(BENCH, INCLINE, LEG_CURL))
        val program = singleLiftProgram(
            liftId = BENCH.id,
            setCount = 3,
            createdAt = now.minus(10, ChronoUnit.DAYS).minus(6 * 7, ChronoUnit.DAYS),
        )
        val perf = FakeLiftWeeklyPerformanceRepository()
        perf.seed(flatSeries(BENCH.id, peak = 100.0))

        val useCase = evaluateUseCase(program, lifts, perf)
        val evaluation = useCase(CheckInSubjectiveFeedback(overallRecovery = RecoverySignal.Good), now)!!
        val rec = evaluation.recommendations.single()
        assertTrue(rec.action is CheckInAction.SwapLift, "expected swap, got ${rec.action}")
        val swap = rec.action as CheckInAction.SwapLift
        assertEquals(INCLINE.id, swap.suggestedLiftId)
        assertTrue(rec.rationale is CheckInRationale.SoftStaleSwap)
    }

    @Test
    @DisplayName("stale soft lift with no non-excluded candidate → +volume fallback")
    fun noSwapCandidateFallsBackToVolume() = runTest {
        // Catalog is only the programmed lift — no other enabled candidates.
        val lifts = FakeLiftRepository()
        lifts.seedLifts(listOf(BENCH))
        val program = singleLiftProgram(
            liftId = BENCH.id,
            setCount = 3,
            createdAt = now.minus(8 * 7, ChronoUnit.DAYS),
        )
        val perf = FakeLiftWeeklyPerformanceRepository()
        perf.seed(flatSeries(BENCH.id, peak = 100.0))

        val useCase = evaluateUseCase(program, lifts, perf)
        val rec = useCase(CheckInSubjectiveFeedback(), now)!!.recommendations.single()
        assertTrue(rec.action is CheckInAction.IncreaseVolume, "expected +vol fallback, got ${rec.action}")
        assertTrue(rec.rationale is CheckInRationale.SoftStaleIncreaseNoCandidate)
    }

    @Test
    @DisplayName("climbing performance with mild soreness → Maintain (Rule 1)")
    fun climbingMaintainsThroughEvaluate() = runTest {
        val lifts = FakeLiftRepository()
        lifts.seedLifts(listOf(BENCH, INCLINE))
        val program = singleLiftProgram(BENCH.id, setCount = 4, createdAt = now.minus(12 * 7, ChronoUnit.DAYS))
        val perf = FakeLiftWeeklyPerformanceRepository()
        perf.seed(climbingSeries(BENCH.id))

        val useCase = evaluateUseCase(program, lifts, perf)
        val rec = useCase(
            CheckInSubjectiveFeedback(overallRecovery = RecoverySignal.MildSoreness),
            now,
        )!!.recommendations.single()
        assertTrue(rec.action is CheckInAction.Maintain)
        assertEquals(CheckInRationale.ClimbingMaintain, rec.rationale)
    }

    @Test
    @DisplayName("joint pain cuts even when performance is climbing (severity over Rule 1)")
    fun jointPainCutsDespiteClimbingSeries() = runTest {
        val lifts = FakeLiftRepository()
        lifts.seedLifts(listOf(BENCH, INCLINE))
        val program = singleLiftProgram(BENCH.id, setCount = 4, createdAt = now.minus(4 * 7, ChronoUnit.DAYS))
        val perf = FakeLiftWeeklyPerformanceRepository()
        perf.seed(climbingSeries(BENCH.id))

        val useCase = evaluateUseCase(program, lifts, perf)
        val rec = useCase(
            CheckInSubjectiveFeedback(overallRecovery = RecoverySignal.JointPain),
            now,
        )!!.recommendations.single()
        assertTrue(rec.action is CheckInAction.CutVolume)
        assertEquals(1, (rec.action as CheckInAction.CutVolume).removeSets)
    }

    @Test
    @DisplayName("multi-lift evaluation orders Cut before Swap; reserves swap ids across soft lifts")
    fun multiLiftOrderingAndSwapReservation() = runTest {
        val wlCut = WorkoutLiftId("wl-cut")
        val wlSwapA = WorkoutLiftId("wl-swap-a")
        val wlSwapB = WorkoutLiftId("wl-swap-b")
        // Chest + joint pain local → Cut. Hamstring soft lifts → Swap path (no local joint pain).
        val cutLift = BENCH.copy(id = LiftId("cut-lift"), name = "Cut Lift")
        val swapA = LEG_CURL.copy(id = LiftId("swap-a"), name = "Swap A Curl")
        val swapB = LEG_CURL.copy(id = LiftId("swap-b"), name = "Swap B Curl")
        // Single hamstring candidate — second swap falls back to volume after reservation.
        val candidate = LEG_CURL.copy(id = LiftId("only-candidate"), name = "Only Candidate Curl")

        val lifts = FakeLiftRepository()
        lifts.seedLifts(listOf(cutLift, swapA, swapB, candidate))

        val program = Program(
            id = programId,
            name = "Split",
            createdAt = now.minus(10 * 7, ChronoUnit.DAYS),
            cycleLength = 1,
            trainingWeekdays = setOf(Weekday.Monday),
            workouts = listOf(
                Workout(
                    id = WorkoutId("w-1"),
                    programId = programId,
                    name = "Full",
                    weekday = Weekday.Monday,
                    position = 0,
                    workoutLifts = listOf(
                        workoutLift(wlSwapB, swapB.id, setCount = 3),
                        workoutLift(wlCut, cutLift.id, setCount = 4),
                        workoutLift(wlSwapA, swapA.id, setCount = 3),
                    ),
                ),
            ),
        )
        val perf = FakeLiftWeeklyPerformanceRepository()
        perf.seed(
            flatSeries(cutLift.id, peak = 100.0) +
                flatSeries(swapA.id, peak = 110.0) +
                flatSeries(swapB.id, peak = 120.0),
        )

        val useCase = evaluateUseCase(program, lifts, perf)
        val evaluation = useCase(
            CheckInSubjectiveFeedback(
                overallRecovery = RecoverySignal.Good,
                muscleSignals = mapOf(MuscleGroup.Chest to RecoverySignal.JointPain),
            ),
            now,
        )!!

        // Cut (joint pain on chest primary only) first by sort priority.
        assertTrue(
            evaluation.recommendations.first().action is CheckInAction.CutVolume,
            "first action was ${evaluation.recommendations.first().action}",
        )

        val swapActions = evaluation.recommendations.map { it.action }.filterIsInstance<CheckInAction.SwapLift>()
        val increaseActions = evaluation.recommendations.map { it.action }.filterIsInstance<CheckInAction.IncreaseVolume>()
        // One swap claims the only candidate; the other soft-stale lift falls back to +volume.
        assertEquals(1, swapActions.size, "actions=${evaluation.recommendations.map { it.liftName to it.action }}")
        assertEquals(candidate.id, swapActions.single().suggestedLiftId)
        assertTrue(increaseActions.isNotEmpty(), "second stale lift must fall back after reservation")
    }

    private fun evaluateUseCase(
        program: Program?,
        lifts: FakeLiftRepository = FakeLiftRepository(),
        perf: FakeLiftWeeklyPerformanceRepository = FakeLiftWeeklyPerformanceRepository(),
        events: FakeProgramChangeEventRepository = FakeProgramChangeEventRepository(),
    ): EvaluateCheckInUseCase =
        EvaluateCheckInUseCase(
            programRepository = FakeProgramRepository(program),
            liftRepository = lifts,
            liftWeeklyPerformanceRepository = perf,
            programChangeEventRepository = events,
            rankSwapCandidates = RankSwapCandidatesUseCase(lifts),
        )

    private fun singleLiftProgram(
        liftId: LiftId,
        setCount: Int,
        createdAt: Instant,
    ): Program =
        Program(
            id = programId,
            name = "Split",
            createdAt = createdAt,
            cycleLength = 1,
            trainingWeekdays = setOf(Weekday.Monday),
            workouts = listOf(
                Workout(
                    id = WorkoutId("w-1"),
                    programId = programId,
                    name = "Push",
                    weekday = Weekday.Monday,
                    position = 0,
                    workoutLifts = listOf(workoutLift(WorkoutLiftId("wl-1"), liftId, setCount)),
                ),
            ),
        )

    private fun workoutLift(id: WorkoutLiftId, liftId: LiftId, setCount: Int): WorkoutLift {
        val sets = (0 until setCount).map { index ->
            LiftSet(
                id = LiftSetId("${id.value}-ls-$index"),
                workoutLiftId = id,
                position = index,
                setType = SetType.Standard,
                repMin = 8,
                repMax = 12,
                rirGoal = 2,
                isTopSet = index == 0,
            )
        }
        return WorkoutLift(
            id = id,
            workoutId = WorkoutId("w-1"),
            liftId = liftId,
            progressionScheme = ProgressionScheme.Double,
            position = 0,
            sets = sets,
        )
    }

    /** Flat e1RM series with enough points/sessions to clear callout gates as Flat. */
    private fun flatSeries(liftId: LiftId, peak: Double): List<LiftWeeklyPerformance> =
        (0 until CALLOUT_MIN_POINTS).map { i ->
            weekly(
                liftId = liftId,
                weekOffset = i,
                peakE1rm = peak + (i % 2) * 0.1,
                sessions = 2,
            )
        }

    private fun climbingSeries(liftId: LiftId): List<LiftWeeklyPerformance> =
        (0 until CALLOUT_MIN_POINTS).map { i ->
            weekly(
                liftId = liftId,
                weekOffset = i,
                peakE1rm = 100.0 + i * 2.0,
                sessions = 2,
            )
        }

    private fun weekly(
        liftId: LiftId,
        weekOffset: Int,
        peakE1rm: Double,
        sessions: Int,
    ): LiftWeeklyPerformance =
        LiftWeeklyPerformance(
            liftId = liftId,
            weekStart = now.minus((CALLOUT_MIN_POINTS - 1 - weekOffset).toLong() * 7, ChronoUnit.DAYS),
            peakE1rm = peakE1rm,
            smoothedE1rm = peakE1rm,
            logE1rm = kotlin.math.ln(peakE1rm.coerceAtLeast(1.0)),
            hardSets = 8.0,
            sessions = sessions,
            meanRir = 2.0,
            reliability = 1.0,
            qualityFlags = emptySet<TrainingSignalQualityFlag>(),
        )

    private companion object {
        val BENCH = Lift(
            id = LiftId("bench-press"),
            name = "Bench Press",
            movementPatterns = setOf(MovementPattern.HorizontalPress, MovementPattern.ElbowExtension),
            primaryMuscles = setOf(MuscleGroup.Chest),
            secondaryMuscles = setOf(MuscleGroup.Tricep, MuscleGroup.AnteriorDeltoid),
            incrementTiers = emptyList(),
            presetTags = emptySet(),
            enabled = true,
            kinematicProfile = KinematicProfile.NonAxialCompound,
        )
        val INCLINE = Lift(
            id = LiftId("incline-press"),
            name = "Incline Dumbbell Press",
            movementPatterns = setOf(MovementPattern.InclinePress, MovementPattern.ElbowExtension),
            primaryMuscles = setOf(MuscleGroup.Chest),
            secondaryMuscles = setOf(MuscleGroup.AnteriorDeltoid),
            incrementTiers = emptyList(),
            presetTags = emptySet(),
            enabled = true,
            kinematicProfile = KinematicProfile.NonAxialCompound,
        )
        val LEG_CURL = Lift(
            id = LiftId("leg-curl"),
            name = "Leg Curl",
            movementPatterns = setOf(MovementPattern.KneeFlexion),
            primaryMuscles = setOf(MuscleGroup.Hamstring),
            secondaryMuscles = emptySet(),
            incrementTiers = emptyList(),
            presetTags = emptySet(),
            enabled = true,
            kinematicProfile = KinematicProfile.Isolation,
        )
    }
}
