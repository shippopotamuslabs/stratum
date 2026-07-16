package com.stratum.domain.checkin

import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.core.testing.program.FakeProgramRepository
import com.stratum.core.testing.response.FakeLiftWeeklyPerformanceRepository
import com.stratum.core.testing.response.FakeMuscleVolumeResponseSnapshotRepository
import com.stratum.core.testing.training.FakeProgramChangeEventRepository
import com.stratum.domain.dashboard.CALLOUT_MIN_POINTS
import com.stratum.domain.importing.GlobalVolumePriors
import com.stratum.domain.importing.LandmarkConfidenceInterval
import com.stratum.domain.importing.LandmarkConfidenceTier
import com.stratum.domain.lift.FatigueProfile
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.RankSwapCandidatesUseCase
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.onboarding.Weekday
import com.stratum.domain.program.ComputeProgramMuscleVolumeUseCase
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
import com.stratum.domain.response.modeling.MuscleVolumeResponseSnapshot
import com.stratum.domain.response.modeling.OutcomeModelType
import com.stratum.domain.response.modeling.VolumePosture
import com.stratum.domain.training.ProgramChangeEvent
import com.stratum.domain.training.ProgramChangeEventPayload
import com.stratum.domain.training.ProgramChangeEventType
import com.stratum.domain.training.identity.ProgramChangeEventId
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Integration tests for [EvaluateCheckInUseCase]: trend classification → decide → batch
 * selection → swap enrichment / no-candidate fallback, with fakes only (UNIT_TESTING.md).
 */
class EvaluateCheckInUseCaseTest {

    private val programId = ProgramId("p-1")
    private val now = Instant.parse("2026-07-01T12:00:00Z")
    private val literatureCeiling = GlobalVolumePriors.maintenanceCeiling()

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
        // Similar hamstring option in band (not a near-clone — max similarity excludes ~1.0 twins).
        val candidate =
            LEG_CURL.copy(
                id = LiftId("only-candidate"),
                name = "Only Candidate Curl",
                movementPatterns = setOf(MovementPattern.KneeFlexion, MovementPattern.HipExtension),
                muscleLengthBias = com.stratum.domain.lift.MuscleLengthBias.Lengthened,
            )

        val lifts = FakeLiftRepository()
        lifts.seedLifts(listOf(cutLift, swapA, swapB, candidate))

        val program = Program(
            id = programId,
            name = "Split",
            createdAt = now.minus(10 * 7, ChronoUnit.DAYS),
            cycleLength = 7,
            restSlotIndices = setOf(1, 2, 3, 4, 5, 6),
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
        // G2: both soft lifts share Hamstring — only one non-cut lever; loser demoted before reserve.
        assertEquals(1, swapActions.size, "actions=${evaluation.recommendations.map { it.liftName to it.action }}")
        assertEquals(candidate.id, swapActions.single().suggestedLiftId)
        assertTrue(
            evaluation.recommendations.any { it.rationale is CheckInRationale.MuscleLeverAlreadyTaken },
            "second hamstring soft lift demoted by one-lever rule",
        )
    }

    @Test
    @DisplayName("D1: flat fresh low volume → +1")
    fun flatFreshLowVolumeIncreases() = runTest {
        val lifts = FakeLiftRepository()
        lifts.seedLifts(listOf(BENCH, INCLINE))
        val program = singleLiftProgram(BENCH.id, setCount = 3, createdAt = now.minus(2 * 7, ChronoUnit.DAYS))
        val perf = FakeLiftWeeklyPerformanceRepository()
        perf.seed(flatSeries(BENCH.id, peak = 100.0))
        val rec = evaluateUseCase(program, lifts, perf)(CheckInSubjectiveFeedback(), now)!!.recommendations.single()
        assertTrue(rec.action is CheckInAction.IncreaseVolume)
        assertTrue(rec.rationale is CheckInRationale.SoftFreshIncrease)
    }

    @Test
    @DisplayName("D2: flat fresh high program volume → Maintain at ceiling")
    fun flatFreshHighVolumeHolds() = runTest {
        val lifts = FakeLiftRepository()
        lifts.seedLifts(listOf(BENCH, INCLINE))
        val sets = (literatureCeiling + 1).toInt().coerceAtLeast(17)
        val program = singleLiftProgram(BENCH.id, setCount = sets, createdAt = now.minus(2 * 7, ChronoUnit.DAYS))
        val perf = FakeLiftWeeklyPerformanceRepository()
        perf.seed(flatSeries(BENCH.id, peak = 100.0))
        val rec = evaluateUseCase(program, lifts, perf)(CheckInSubjectiveFeedback(), now)!!.recommendations.single()
        assertTrue(rec.action is CheckInAction.Maintain, "got ${rec.action}")
        assertEquals(CheckInRationale.SoftFreshAtVolumeCeiling, rec.rationale)
    }

    @Test
    @DisplayName("D3: Saturated snapshot + enough volume (≥50% ceiling) → Maintain")
    fun saturatedSnapshotHolds() = runTest {
        val lifts = FakeLiftRepository()
        lifts.seedLifts(listOf(BENCH, INCLINE))
        // Saturated only freezes +1 when weekly volume is at least half the maintenance ceiling.
        val program =
            singleLiftProgram(
                BENCH.id,
                setCount = 10,
                createdAt = now.minus(2 * 7, ChronoUnit.DAYS),
            )
        val perf = FakeLiftWeeklyPerformanceRepository()
        perf.seed(flatSeries(BENCH.id, peak = 100.0))
        val snapshots = FakeMuscleVolumeResponseSnapshotRepository()
        snapshots.seed(listOf(chestSnapshot(posture = VolumePosture.Saturated)))
        val rec =
            evaluateUseCase(program, lifts, perf, snapshots = snapshots)(CheckInSubjectiveFeedback(), now)!!
                .recommendations.single()
        assertTrue(rec.action is CheckInAction.Maintain, "got ${rec.action} / ${rec.rationale}")
        assertEquals(CheckInRationale.SoftFreshAtVolumeCeiling, rec.rationale)
    }

    @Test
    @DisplayName("D4: two soft-fresh same muscle → single +1 on lighter slot")
    fun twoSoftFreshOneIncrease() = runTest {
        val heavy = BENCH.copy(id = LiftId("heavy-bench"), name = "Heavy Bench")
        val light = BENCH.copy(id = LiftId("light-fly"), name = "Light Fly", fatigueProfile = FatigueProfile.Low)
        val lifts = FakeLiftRepository()
        lifts.seedLifts(listOf(heavy, light, INCLINE))
        val program =
            Program(
                id = programId,
                name = "Split",
                createdAt = now.minus(2 * 7, ChronoUnit.DAYS),
                cycleLength = 7,
                restSlotIndices = setOf(1, 2, 3, 4, 5, 6),
                trainingWeekdays = setOf(Weekday.Monday),
                workouts =
                    listOf(
                        Workout(
                            id = WorkoutId("w-1"),
                            programId = programId,
                            name = "Push",
                            weekday = Weekday.Monday,
                            position = 0,
                            workoutLifts =
                                listOf(
                                    workoutLift(WorkoutLiftId("wl-h"), heavy.id, setCount = 5),
                                    workoutLift(WorkoutLiftId("wl-l"), light.id, setCount = 2),
                                ),
                        ),
                    ),
            )
        val perf = FakeLiftWeeklyPerformanceRepository()
        perf.seed(flatSeries(heavy.id, 100.0) + flatSeries(light.id, 50.0))
        val evaluation = evaluateUseCase(program, lifts, perf)(CheckInSubjectiveFeedback(), now)!!
        val increases = evaluation.recommendations.filter { it.action is CheckInAction.IncreaseVolume }
        assertEquals(1, increases.size)
        assertEquals("Light Fly", increases.single().liftName)
        assertTrue(evaluation.recommendations.any { it.rationale is CheckInRationale.MuscleLeverAlreadyTaken })
    }

    @Test
    @DisplayName("D10: dropping + mild soreness end-to-end → hold")
    fun droppingMildHoldsThroughEvaluate() = runTest {
        val lifts = FakeLiftRepository()
        lifts.seedLifts(listOf(BENCH, INCLINE))
        val program = singleLiftProgram(BENCH.id, setCount = 3, createdAt = now.minus(2 * 7, ChronoUnit.DAYS))
        val perf = FakeLiftWeeklyPerformanceRepository()
        perf.seed(droppingSeries(BENCH.id))
        val rec =
            evaluateUseCase(program, lifts, perf)(
                CheckInSubjectiveFeedback(overallRecovery = RecoverySignal.MildSoreness),
                now,
            )!!.recommendations.single()
        assertTrue(rec.action is CheckInAction.Maintain)
        assertEquals(CheckInRationale.DroppingMildRecoveryHold, rec.rationale)
    }

    @Test
    @DisplayName("P0: failed multi-primary swap re-opens muscle — peer isolation gets +1")
    fun failedSwapReopensMuscleForPeerIncrease() = runTest {
        // Heavy stale compound wants swap but catalog has no in-band peer → becomes +1.
        // Light fresh isolation also wants +1. After enrich-then-batch, isolation wins G4.
        val heavy =
            BENCH.copy(
                id = LiftId("heavy-bench"),
                name = "Heavy Bench",
                primaryMuscles = setOf(MuscleGroup.Chest, MuscleGroup.Tricep),
                fatigueProfile = FatigueProfile.Medium,
            )
        val light =
            BENCH.copy(
                id = LiftId("light-fly"),
                name = "Light Fly",
                primaryMuscles = setOf(MuscleGroup.Chest),
                secondaryMuscles = emptySet(),
                fatigueProfile = FatigueProfile.Low,
                movementPatterns = setOf(MovementPattern.HorizontalPress),
            )
        val lifts = FakeLiftRepository()
        lifts.seedLifts(listOf(heavy, light))
        val program =
            Program(
                id = programId,
                name = "Split",
                createdAt = now.minus(8 * 7, ChronoUnit.DAYS),
                cycleLength = 7,
                restSlotIndices = setOf(1, 2, 3, 4, 5, 6),
                trainingWeekdays = setOf(Weekday.Monday),
                workouts =
                    listOf(
                        Workout(
                            id = WorkoutId("w-1"),
                            programId = programId,
                            name = "Push",
                            weekday = Weekday.Monday,
                            position = 0,
                            workoutLifts =
                                listOf(
                                    workoutLift(WorkoutLiftId("wl-h"), heavy.id, setCount = 4),
                                    // Light is "fresh": program-wide tenure from createdAt is long;
                                    // inject a recent swap onto light so tenure < 4.
                                    workoutLift(WorkoutLiftId("wl-l"), light.id, setCount = 2),
                                ),
                        ),
                    ),
            )
        val perf = FakeLiftWeeklyPerformanceRepository()
        perf.seed(flatSeries(heavy.id, 100.0) + flatSeries(light.id, 50.0))
        val events = FakeProgramChangeEventRepository()
        // Make light slot fresh via recent LIFT_SWAPPED onto current lift id.
        events.seed(
            recentSwapOnto(
                workoutLiftId = WorkoutLiftId("wl-l"),
                newLiftId = light.id,
                weeksAgo = 1,
            ),
        )
        val evaluation =
            evaluateUseCase(program, lifts, perf, events = events)(CheckInSubjectiveFeedback(), now)!!
        val increases =
            evaluation.recommendations.filter { it.action is CheckInAction.IncreaseVolume }
        assertEquals(
            1,
            increases.size,
            "actions=${evaluation.recommendations.map { it.liftName to it.action to it.rationale }}",
        )
        assertEquals("Light Fly", increases.single().liftName)
        val heavyRec = evaluation.recommendations.single { it.liftName == "Heavy Bench" }
        assertTrue(heavyRec.action is CheckInAction.Maintain)
        assertEquals(CheckInRationale.MuscleLeverAlreadyTaken, heavyRec.rationale)
    }

    @Test
    @DisplayName("D15: stale + no candidate + high volume → Maintain at ceiling (not +1)")
    fun staleNoCandidateAtCeiling() = runTest {
        val lifts = FakeLiftRepository()
        lifts.seedLifts(listOf(BENCH))
        val sets = (literatureCeiling + 1).toInt().coerceAtLeast(17)
        val program = singleLiftProgram(BENCH.id, setCount = sets, createdAt = now.minus(8 * 7, ChronoUnit.DAYS))
        val perf = FakeLiftWeeklyPerformanceRepository()
        perf.seed(flatSeries(BENCH.id, peak = 100.0))
        val rec = evaluateUseCase(program, lifts, perf)(CheckInSubjectiveFeedback(), now)!!.recommendations.single()
        assertTrue(rec.action is CheckInAction.Maintain, "got ${rec.action}")
        assertEquals(CheckInRationale.SoftStaleNoCandidateAtCeiling, rec.rationale)
    }

    private fun evaluateUseCase(
        program: Program?,
        lifts: FakeLiftRepository = FakeLiftRepository(),
        perf: FakeLiftWeeklyPerformanceRepository = FakeLiftWeeklyPerformanceRepository(),
        events: FakeProgramChangeEventRepository = FakeProgramChangeEventRepository(),
        snapshots: FakeMuscleVolumeResponseSnapshotRepository = FakeMuscleVolumeResponseSnapshotRepository(),
    ): EvaluateCheckInUseCase =
        EvaluateCheckInUseCase(
            programRepository = FakeProgramRepository(program),
            liftRepository = lifts,
            liftWeeklyPerformanceRepository = perf,
            programChangeEventRepository = events,
            rankSwapCandidates = RankSwapCandidatesUseCase(lifts),
            computeProgramMuscleVolume = ComputeProgramMuscleVolumeUseCase(),
            muscleVolumeResponseSnapshotRepository = snapshots,
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
            cycleLength = 7,
            restSlotIndices = setOf(1, 2, 3, 4, 5, 6),
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

    private fun droppingSeries(liftId: LiftId): List<LiftWeeklyPerformance> =
        (0 until CALLOUT_MIN_POINTS).map { i ->
            weekly(
                liftId = liftId,
                weekOffset = i,
                peakE1rm = 120.0 - i * 2.0,
                sessions = 2,
            )
        }

    private fun chestSnapshot(posture: VolumePosture): MuscleVolumeResponseSnapshot =
        MuscleVolumeResponseSnapshot(
            muscle = MuscleGroup.Chest,
            retentionFloorMv = 4.0,
            paretoVolumePv = 12.0,
            recoverabilityCeilingRc = 18.0,
            growthEntryFloor = 6.0,
            observedDoseMin = 4.0,
            observedDoseMax = 16.0,
            observedDoseMedian = 10.0,
            productiveBandMin = 8.0,
            productiveBandMax = 14.0,
            posture = posture,
            pvInterval = LandmarkConfidenceInterval.Unavailable,
            confidenceTier = LandmarkConfidenceTier.Medium,
            outcomeModelType = OutcomeModelType.MuscleIndex,
            diagnosticAnchorLiftId = LiftId("anchor"),
        )

    private fun recentSwapOnto(
        workoutLiftId: WorkoutLiftId,
        newLiftId: LiftId,
        weeksAgo: Long,
    ): ProgramChangeEvent =
        ProgramChangeEvent(
            id = ProgramChangeEventId("swap-$weeksAgo"),
            programId = programId,
            eventType = ProgramChangeEventType.LIFT_SWAPPED,
            occurredAt = now.minus(weeksAgo * 7, ChronoUnit.DAYS),
            payload =
                ProgramChangeEventPayload.LiftSwapped(
                    workoutLiftId = workoutLiftId,
                    previousLiftId = LiftId("previous"),
                    newLiftId = newLiftId,
                ),
        )

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
            fatigueProfile = FatigueProfile.Medium,
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
            fatigueProfile = FatigueProfile.Medium,
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
            fatigueProfile = FatigueProfile.Low,
        )
    }
}
