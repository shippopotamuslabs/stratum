package com.stratum.domain.workout.progression

import com.stratum.domain.importing.identity.LoggedSetId
import com.stratum.domain.lift.KinematicProfile
import com.stratum.domain.lift.LoadType
import com.stratum.domain.lift.MuscleLengthBias
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.SetType
import com.stratum.domain.program.identity.LiftSetId
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.training.EstimatedOneRm
import com.stratum.domain.training.LoggedLiftContext
import com.stratum.domain.training.LoggedSet
import com.stratum.domain.training.TrainingSession
import com.stratum.domain.training.TrainingSessionKind
import com.stratum.domain.training.TrainingSessionOrigin
import com.stratum.domain.training.TrainingSessionStatus
import com.stratum.domain.training.identity.TrainingSessionId
import com.stratum.domain.workout.BACKOFF_REP_FLOOR
import com.stratum.domain.workout.SetSlot
import com.stratum.domain.workout.SetSlotId
import com.stratum.domain.workout.SlotHistoryEntry
import com.stratum.domain.workout.SlotRecommendation
import com.stratum.domain.workout.clampRepsMonotonicNonIncreasing
import com.stratum.domain.workout.fatigue.capacityAtRirWithProjectReps
import com.stratum.domain.workout.fatigue.reduceWeightForLastSetFloor
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Regression coverage for thermos findings on snapshot progression:
 * 1. FromRecent @ new weight reprojects baseline (not prior fatigue)
 * 2. Strictest multi-slot shared floor gate
 * 3. Monotonic rep clamp for rirGoal asymmetry
 */
@DisplayName("Thermos regression: snapshot progression load-change paths")
class ThermosRegressionProgressionTest {

    private val liftId = LiftId("L-1")
    private val workoutLiftId = WorkoutLiftId("wl-1")

    private fun lb(amount: Double) = Weight(amount, Weight.WeightUnit.Lb)

    private fun slot(
        position: Int,
        isTopSet: Boolean = position == 0,
        rirGoal: Int = if (position == 0) 2 else 1,
        repMin: Int = 8,
        repMax: Int = 16,
    ) = SetSlot(
        slotId = SetSlotId.Template(LiftSetId("ls-$position")),
        workoutLiftId = workoutLiftId,
        liftId = liftId,
        position = position,
        setType = SetType.Standard,
        isTopSet = isTopSet,
        repMin = repMin,
        repMax = repMax,
        rirGoal = rirGoal,
        restSeconds = 90,
    )

    private fun historyEntry(reps: Int, rir: Int?, weight: Double, position: Int = 0): SlotHistoryEntry =
        SlotHistoryEntry(
            session = TrainingSession(
                id = TrainingSessionId("s1"),
                programId = ProgramId("p-1"),
                workoutId = WorkoutId("w-1"),
                status = TrainingSessionStatus.COMPLETED,
                kind = TrainingSessionKind.PROGRAM_WORKOUT,
                origin = TrainingSessionOrigin.IMPORT,
                cycleNumberSnapshot = 1,
                weekdaySnapshot = null,
                startedAt = Instant.parse("2026-07-01T10:00:00Z"),
                endedAt = Instant.parse("2026-07-01T11:00:00Z"),
                sourceApp = null,
            ),
            loggedSet = LoggedSet(
                id = LoggedSetId("set-$position"),
                sessionId = TrainingSessionId("s1"),
                exerciseNameRaw = "Curl",
                liftId = liftId,
                setIndex = position,
                weight = lb(weight),
                reps = reps,
                rir = rir,
                completedAt = Instant.parse("2026-07-01T10:30:00Z"),
                liftContext = LoggedLiftContext(
                    liftName = "Curl",
                    movementPatternsBitmask = 1,
                    primaryMusclesBitmask = 1,
                    secondaryMusclesBitmask = 0,
                    kinematicProfile = KinematicProfile.Isolation,
                    muscleLengthBias = MuscleLengthBias.MidRange,
                ),
            ),
        )

    private fun request(
        slot: SetSlot,
        history: List<SlotHistoryEntry>,
        currentTopSetWeight: Weight? = null,
        priorSlotRecommendation: SlotRecommendation? = null,
        priorSlot: SetSlot? = null,
        baseline: ProgressionBaseline? = history.firstOrNull()?.let { ProgressionBaseline.FromRecent(it) },
    ) = SlotProgressionRequest(
        slot = slot,
        history = history,
        fallbackOneRm = null,
        loadType = LoadType.Weighted,
        currentTopSetWeight = currentTopSetWeight,
        priorSlotRecommendation = priorSlotRecommendation,
        priorSlot = priorSlot,
        progressionBaseline = baseline,
    )

    @Nested
    @DisplayName("FromRecent at new weight reprojects this baseline (not prior fatigue)")
    inner class BaselineReproject {
        @Test
        @DisplayName("mid-slot after shared reduce uses own history e1RM, not top rec chain")
        fun midSlotAfterReduceUsesOwnBaseline() {
            val engine = DoubleProgressionEngine()
            val top = slot(0, isTopSet = true, rirGoal = 2)
            val mid = slot(1, isTopSet = false, rirGoal = 1)
            val histTop = listOf(historyEntry(reps = 15, rir = 1, weight = 15.0, position = 0))
            // Mid set was much weaker historically — reproject must reflect that, not
            // one fatigue step from a strong top rec at 10 lb.
            val histMid = listOf(historyEntry(reps = 6, rir = 1, weight = 15.0, position = 1))

            val topRec = engine.recommend(
                request(
                    slot = top,
                    history = histTop,
                    currentTopSetWeight = lb(10.0),
                ),
            )
            val midFromPriorChain = engine.recommend(
                request(
                    slot = mid,
                    history = histMid,
                    currentTopSetWeight = lb(10.0),
                    priorSlotRecommendation = topRec,
                    priorSlot = top,
                    // Force the old bug path would use prior; baseline is FromRecent
                    baseline = ProgressionBaseline.FromRecent(histMid.first()),
                ),
            )

            val expectedFromBaseline = projectBaselineAtTargetWeight(
                baselineEntry = histMid.first(),
                targetWeight = lb(10.0),
                slot = mid,
                incrementTiers = emptyList(),
                loadType = LoadType.Weighted,
            ).second

            assertEquals(
                expectedFromBaseline,
                midFromPriorChain.baselineExpectedReps,
                "FromRecent @ reduced load must reproject this slot's baseline, not prior fatigue",
            )
            // Sanity: own baseline (weak) should differ from pure top-chain in typical cases
            val purePrior = engine.recommend(
                request(
                    slot = mid,
                    history = emptyList(),
                    currentTopSetWeight = lb(10.0),
                    priorSlotRecommendation = topRec,
                    priorSlot = top,
                    baseline = ProgressionBaseline.ProjectFromPrior,
                ),
            )
            // If they happen to match numerically, baseline path is still correct via equality above.
            assertTrue(purePrior.baselineExpectedReps >= BACKOFF_REP_FLOOR)
        }

        @Test
        @DisplayName("ProjectFromPrior still uses sequential prior fatigue")
        fun projectFromPriorStillUsesPrior() {
            val engine = DoubleProgressionEngine()
            val s0 = slot(0)
            val s1 = slot(1)
            val hist0 = listOf(historyEntry(reps = 12, rir = 1, weight = 15.0))
            val rec0 = engine.recommend(request(s0, hist0, currentTopSetWeight = lb(15.0)))
            val rec1 = engine.recommend(
                request(
                    slot = s1,
                    history = emptyList(),
                    currentTopSetWeight = lb(15.0),
                    priorSlotRecommendation = rec0,
                    priorSlot = s0,
                    baseline = ProgressionBaseline.ProjectFromPrior,
                ),
            )
            assertTrue(rec1.isProjected)
            assertTrue(rec0.baselineExpectedReps >= rec1.baselineExpectedReps)
            assertTrue(rec1.baselineExpectedReps >= BACKOFF_REP_FLOOR)
        }
    }

    @Nested
    @DisplayName("Strictest multi-slot shared floor gate")
    inner class StrictestGate {
        @Test
        @DisplayName("top slot fails floor at advanced load even if last slot clears → reduce")
        fun topFailsLastClearsStillReduces() {
            // performances capacity-desc: slot0 gets strongest, slot1 weakest.
            // After assign: top history strong at 15, but candidate 25 is too heavy for top rirGoal=2.
            // Last set at 15 with rirGoal=1 may still clear floor at 25 via e1RM of a weak-but-ok set...
            // Construct: both did well enough at 15 that last clears at 20, but top rirGoal=2 needs more.
            val perSlot = mapOf(
                0 to listOf(historyEntry(reps = 8, rir = 0, weight = 100.0, position = 0)),
                1 to listOf(historyEntry(reps = 10, rir = 2, weight = 100.0, position = 1)),
            )
            // capacities: set0 = 8, set1 = 12 → order: pos1 then pos0
            // performances[0]=pos1 (10@2), performances[1]=pos0 (8@0)
            val block = buildRecentSessionBlock(perSlot)!!
            val slots = listOf(
                slot(0, isTopSet = true, rirGoal = 2, repMin = 5, repMax = 12),
                slot(1, isTopSet = false, rirGoal = 0, repMin = 5, repMax = 12),
            )
            // Candidate advanced well above history
            val shared = resolveStraightSetSharedWeight(
                candidateWeight = lb(130.0),
                slots = slots,
                recentBlock = block,
                perLiftSlope = null,
                perLiftIntercept = null,
                incrementTiers = emptyList(),
                loadType = LoadType.Weighted,
            )
            assertTrue(
                shared.amount < 130.0,
                "strictest gate must drop when any covered slot fails floor, was ${shared.amount}",
            )
            assertTrue(shared.amount > 0.0)
        }

        @Test
        @DisplayName("all covered slots clear floor → keep candidate")
        fun allCoveredClearKeeps() {
            val perSlot = mapOf(
                0 to listOf(historyEntry(reps = 12, rir = 2, weight = 100.0, position = 0)),
                1 to listOf(historyEntry(reps = 10, rir = 1, weight = 100.0, position = 1)),
            )
            val block = buildRecentSessionBlock(perSlot)!!
            val slots = listOf(slot(0, rirGoal = 2), slot(1, rirGoal = 1))
            val shared = resolveStraightSetSharedWeight(
                candidateWeight = lb(100.0),
                slots = slots,
                recentBlock = block,
                perLiftSlope = null,
                perLiftIntercept = null,
                incrementTiers = emptyList(),
                loadType = LoadType.Weighted,
            )
            assertEquals(100.0, shared.amount, 0.01)
        }
    }

    @Nested
    @DisplayName("Monotonic rep clamp")
    inner class MonotonicClamp {
        @Test
        @DisplayName("clampRepsMonotonicNonIncreasing flattens positive slope")
        fun clampFlattensPositiveSlope() {
            val recs = listOf(
                SlotRecommendation(
                    slotId = SetSlotId.Template(LiftSetId("a")),
                    recommendedWeight = lb(100.0),
                    baselineExpectedReps = 10,
                ),
                SlotRecommendation(
                    slotId = SetSlotId.Template(LiftSetId("b")),
                    recommendedWeight = lb(100.0),
                    baselineExpectedReps = 11, // +1 slope
                ),
                SlotRecommendation(
                    slotId = SetSlotId.Template(LiftSetId("c")),
                    recommendedWeight = lb(100.0),
                    baselineExpectedReps = 9,
                ),
            )
            val clamped = clampRepsMonotonicNonIncreasing(recs)
            assertEquals(listOf(10, 10, 9), clamped.map { it.baselineExpectedReps })
            assertEquals(100.0, clamped[1].recommendedWeight.amount, 0.01)
        }

        @Test
        @DisplayName("already monotonic stays put")
        fun alreadyMonotonicUnchanged() {
            val recs = listOf(
                SlotRecommendation(
                    slotId = SetSlotId.Template(LiftSetId("a")),
                    recommendedWeight = lb(100.0),
                    baselineExpectedReps = 12,
                ),
                SlotRecommendation(
                    slotId = SetSlotId.Template(LiftSetId("b")),
                    recommendedWeight = lb(100.0),
                    baselineExpectedReps = 10,
                ),
            )
            val clamped = clampRepsMonotonicNonIncreasing(recs)
            assertEquals(listOf(12, 10), clamped.map { it.baselineExpectedReps })
            assertTrue(clamped[0] === recs[0])
        }
    }

    @Nested
    @DisplayName("Shared reduce oracle lives in fatigue package")
    inner class SharedOracle {
        @Test
        @DisplayName("reduceWeightForLastSetFloor clears floor and stays positive")
        fun reduceClearsFloor() {
            val e1rm = EstimatedOneRm.calculate(100.0, 6)
            val reduced = reduceWeightForLastSetFloor(
                e1rm = e1rm,
                startFrom = lb(100.0),
                lastSetIndex = 3,
                rirGoal = 2,
                perLiftSlope = -2.0,
                perLiftIntercept = 0.0,
                incrementTiers = emptyList(),
                loadType = LoadType.Weighted,
            )
            assertTrue(reduced.amount > 0.0)
            assertTrue(reduced.amount <= 100.0)
            val cap = capacityAtRirWithProjectReps(
                e1rm = e1rm,
                weight = reduced,
                setIndex = 3,
                rirGoal = 2,
                perLiftSlope = -2.0,
                perLiftIntercept = 0.0,
            )
            assertTrue(cap >= BACKOFF_REP_FLOOR, "cap=$cap")
        }
    }
}
