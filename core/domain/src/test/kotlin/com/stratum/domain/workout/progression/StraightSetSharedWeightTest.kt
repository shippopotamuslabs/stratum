package com.stratum.domain.workout.progression

import com.stratum.domain.importing.identity.LoggedSetId
import com.stratum.domain.lift.IncrementTier
import com.stratum.domain.lift.FatigueProfile
import com.stratum.domain.lift.LoadType
import com.stratum.domain.lift.MuscleLengthBias
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.lift.isOnLattice
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
import com.stratum.domain.workout.fatigue.capacityAtRirWithProjectReps
import com.stratum.domain.workout.fatigue.reduceWeightForLastSetFloor
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import com.stratum.domain.lift.WeightConversion
import com.stratum.domain.workout.SlotHistoryEntry

/**
 * Pure unit coverage for [resolveStraightSetSharedWeight] and [reduceWeightForLastSetFloor]
 * (snapshot shared-weight look-ahead).
 */
@DisplayName("StraightSetSharedWeight: real-data first + partial look-ahead")
class StraightSetSharedWeightTest {

    private val liftId = LiftId("L-1")
    private val workoutLiftId = WorkoutLiftId("wl-1")

    private fun lb(amount: Double) = Weight(amount, Weight.WeightUnit.Lb)
    private fun kg(amount: Double) = Weight(amount, Weight.WeightUnit.Kg)

    private fun slot(
        position: Int,
        isTopSet: Boolean = position == 0,
        rirGoal: Int = if (position == 0) 2 else 1,
    ) = SetSlot(
        slotId = SetSlotId.Template(LiftSetId("ls-$position")),
        workoutLiftId = workoutLiftId,
        liftId = liftId,
        position = position,
        setType = SetType.Standard,
        isTopSet = isTopSet,
        repMin = 8,
        repMax = 16,
        rirGoal = rirGoal,
        restSeconds = 90,
    )

    private fun slots(n: Int) = (0 until n).map { slot(it) }

    private fun entry(
        position: Int,
        reps: Int,
        rir: Int?,
        weight: Weight,
        sessionId: String = "s-n1",
        startedAt: String = "2026-07-01T10:00:00Z",
    ): SlotHistoryEntry =
        SlotHistoryEntry(
            session = TrainingSession(
                id = TrainingSessionId(sessionId),
                programId = ProgramId("p-1"),
                workoutId = WorkoutId("w-1"),
                status = TrainingSessionStatus.COMPLETED,
                kind = TrainingSessionKind.PROGRAM_WORKOUT,
                origin = TrainingSessionOrigin.IMPORT,
                cycleNumberSnapshot = 1,
                weekdaySnapshot = null,
                startedAt = Instant.parse(startedAt),
                endedAt = Instant.parse(startedAt).plusSeconds(3600),
                sourceApp = null,
            ),
            loggedSet = LoggedSet(
                id = LoggedSetId("set-$sessionId-$position"),
                sessionId = TrainingSessionId(sessionId),
                exerciseNameRaw = "Curl",
                liftId = liftId,
                setIndex = position,
                weight = weight,
                reps = reps,
                rir = rir,
                completedAt = Instant.parse(startedAt),
                liftContext = LoggedLiftContext(
                    liftName = "Curl",
                    movementPatternsBitmask = 1,
                    primaryMusclesBitmask = 1,
                    secondaryMusclesBitmask = 0,
                    fatigueProfile = FatigueProfile.Low,
                    muscleLengthBias = MuscleLengthBias.MidRange,
                ),
            ),
        )

    private fun block(vararg sets: Triple<Int, Int, Int?>): RecentSessionBlock {
        // Triple: position, reps, rir — all at 15 lb same session
        val perSlot = sets.associate { (pos, reps, rir) ->
            pos to listOf(entry(pos, reps, rir, lb(15.0)))
        }
        return buildRecentSessionBlock(perSlot)!!
    }

    private fun blockAt(
        weight: Weight,
        vararg sets: Triple<Int, Int, Int?>,
    ): RecentSessionBlock {
        val perSlot = sets.associate { (pos, reps, rir) ->
            pos to listOf(entry(pos, reps, rir, weight))
        }
        return buildRecentSessionBlock(perSlot)!!
    }

    private val fiveLbTiers = listOf(
        IncrementTier(lb(0.0), lb(40.0), lb(5.0)),
        IncrementTier(lb(40.0), null, lb(5.0)),
    )

    @Nested
    @DisplayName("full recent coverage (M >= N)")
    inner class FullCoverage {
        @Test
        @DisplayName("last set ≥ floor → keep candidate (no model second-guess)")
        fun lastSetClearsFloorKeepsCandidate() {
            val w = resolveStraightSetSharedWeight(
                candidateWeight = lb(15.0),
                slots = slots(5),
                recentBlock = block(
                    Triple(0, 15, 1),
                    Triple(1, 12, 1),
                    Triple(2, 10, 1),
                    Triple(3, 8, 1),
                    Triple(4, 6, 1),
                ),
                perLiftSlope = null,
                perLiftIntercept = null,
                incrementTiers = emptyList(),
                loadType = LoadType.Weighted,
            )
            assertEquals(15.0, w.amount, 0.01)
        }

        @Test
        @DisplayName("last set under floor → reduce shared load")
        fun lastSetUnderFloorReduces() {
            val w = resolveStraightSetSharedWeight(
                candidateWeight = lb(15.0),
                slots = slots(5),
                recentBlock = block(
                    Triple(0, 12, 0),
                    Triple(1, 10, 0),
                    Triple(2, 8, 0),
                    Triple(3, 6, 0),
                    Triple(4, 3, 0),
                ),
                perLiftSlope = null,
                perLiftIntercept = null,
                incrementTiers = emptyList(),
                loadType = LoadType.Weighted,
            )
            assertTrue(w.amount < 15.0, "must reduce, was ${w.amount}")
            assertTrue(w.amount > 0.0, "must stay positive")
        }

        @Test
        @DisplayName("missing RIR on last set treated as 0 capacity residual")
        fun missingRirOnLastSetTreatedAsZero() {
            // 4 reps @ null RIR → failure 4; at rirGoal 1 → 3 < floor → reduce
            val w = resolveStraightSetSharedWeight(
                candidateWeight = lb(15.0),
                slots = slots(3),
                recentBlock = block(
                    Triple(0, 12, 1),
                    Triple(1, 10, 1),
                    Triple(2, 4, null),
                ),
                perLiftSlope = null,
                perLiftIntercept = null,
                incrementTiers = emptyList(),
                loadType = LoadType.Weighted,
            )
            assertTrue(w.amount < 15.0, "null RIR last set under floor must reduce, was ${w.amount}")
        }

        @Test
        @DisplayName("advanced candidate above history reprojects last set at new load")
        fun advancedCandidateUsesE1rmAtNewLoad() {
            // Last set 6@1 at 15 is fine at 15, but candidate advanced to 20 may fail floor.
            val w = resolveStraightSetSharedWeight(
                candidateWeight = lb(20.0),
                slots = slots(3),
                recentBlock = block(
                    Triple(0, 16, 2),
                    Triple(1, 12, 1),
                    Triple(2, 6, 1),
                ),
                perLiftSlope = null,
                perLiftIntercept = null,
                incrementTiers = emptyList(),
                loadType = LoadType.Weighted,
            )
            // Either keep 20 if e1RM allows, or reduce — never raise above candidate
            assertTrue(w.amount <= 20.0)
            assertTrue(w.amount > 0.0)
            // With only 6@1 at 15, 20 is likely too heavy for last set
            val e1rm = EstimatedOneRm.calculate(15.0, 7)
            val at20 = EstimatedOneRm.repsForWeight(e1rm, 20.0) - 1
            if (at20 < BACKOFF_REP_FLOOR) {
                assertTrue(w.amount < 20.0, "must reduce when advanced load fails last-set floor")
            }
        }
    }

    @Nested
    @DisplayName("partial recent coverage (M < N)")
    inner class PartialCoverage {
        @Test
        @DisplayName("strong 3-set block sustains 5 → keep candidate")
        fun strongPartialKeepsCandidate() {
            val w = resolveStraightSetSharedWeight(
                candidateWeight = lb(240.0),
                slots = slots(5),
                recentBlock = blockAt(
                    lb(240.0),
                    Triple(0, 15, 2),
                    Triple(1, 14, 2),
                    Triple(2, 13, 2),
                ),
                perLiftSlope = -2.0,
                perLiftIntercept = 0.0,
                incrementTiers = emptyList(),
                loadType = LoadType.Weighted,
            )
            assertEquals(240.0, w.amount, 0.01, "sustainable partial tail must not second-guess")
        }

        @Test
        @DisplayName("thin 3-set block cannot sustain 5 → reduce")
        fun thinPartialReduces() {
            val w = resolveStraightSetSharedWeight(
                candidateWeight = lb(15.0),
                slots = slots(5),
                recentBlock = block(
                    Triple(0, 15, 1),
                    Triple(1, 10, 1),
                    Triple(2, 6, 1),
                ),
                perLiftSlope = -2.0,
                perLiftIntercept = 0.0,
                incrementTiers = emptyList(),
                loadType = LoadType.Weighted,
            )
            assertTrue(w.amount < 15.0, "unsustainable tail must reduce, was ${w.amount}")
            assertTrue(w.amount > 0.0)
        }

        @Test
        @DisplayName("M=1 of N=5 projects long tail from single anchor")
        fun singleRecentSetProjectsLongTail() {
            val w = resolveStraightSetSharedWeight(
                candidateWeight = lb(15.0),
                slots = slots(5),
                recentBlock = block(Triple(0, 12, 1)),
                perLiftSlope = -2.0,
                perLiftIntercept = 0.0,
                incrementTiers = emptyList(),
                loadType = LoadType.Weighted,
            )
            // 4 missing sets of decay from a modest top set — expect reduce or keep if strong enough
            assertTrue(w.amount > 0.0)
            assertTrue(w.amount <= 15.0)
        }

        @Test
        @DisplayName("steep per-lift slope reduces when population slope would keep")
        fun steepPerLiftSlopeForcesReduce() {
            val strongBlock = blockAt(
                lb(100.0),
                Triple(0, 12, 2),
                Triple(1, 11, 2),
                Triple(2, 10, 2),
            )
            val keepPop = resolveStraightSetSharedWeight(
                candidateWeight = lb(100.0),
                slots = slots(5),
                recentBlock = strongBlock,
                perLiftSlope = -1.0,
                perLiftIntercept = 0.0,
                incrementTiers = emptyList(),
                loadType = LoadType.Weighted,
            )
            val steep = resolveStraightSetSharedWeight(
                candidateWeight = lb(100.0),
                slots = slots(5),
                recentBlock = strongBlock,
                perLiftSlope = -6.0,
                perLiftIntercept = 0.0,
                incrementTiers = emptyList(),
                loadType = LoadType.Weighted,
            )
            assertTrue(
                steep.amount <= keepPop.amount,
                "steeper slope must not prescribe heavier load (steep=${steep.amount}, pop=${keepPop.amount})",
            )
            // With -6/set over 2 missing sets, last set almost always under floor
            assertTrue(steep.amount < 100.0, "steep slope should force shared reduce, was ${steep.amount}")
        }
    }

    @Nested
    @DisplayName("reduce oracle invariants")
    inner class ReduceInvariants {
        @Test
        @DisplayName("never returns 0 lb when positive continuous solution exists")
        fun neverZeroWhenSolutionExists() {
            val e1rm = EstimatedOneRm.calculate(15.0, 6)
            val reduced = reduceWeightForLastSetFloor(
                e1rm = e1rm,
                startFrom = lb(15.0),
                lastSetIndex = 3,
                rirGoal = 1,
                perLiftSlope = -2.0,
                perLiftIntercept = 0.0,
                incrementTiers = emptyList(),
                loadType = LoadType.Weighted,
            )
            assertTrue(reduced.amount > 0.0, "must stay positive, was ${reduced.amount}")
            val cap = capacityAtRirWithProjectReps(
                e1rm = e1rm,
                weight = reduced,
                setIndex = 3,
                rirGoal = 1,
                perLiftSlope = -2.0,
                perLiftIntercept = 0.0,
            )
            assertTrue(cap >= BACKOFF_REP_FLOOR, "reduced load must clear floor, cap=$cap")
        }

        @Test
        @DisplayName("micro lattice aligns reduce to base plus adder points")
        fun microLatticeAlignsToAdderPoints() {
            val pinTiers =
                listOf(
                    IncrementTier(lb(0.0), null, lb(15.0)),
                )
            val pinMicro = listOf(lb(5.0), lb(10.0))
            val e1rm = EstimatedOneRm.calculate(70.0, 6)
            val reduced = reduceWeightForLastSetFloor(
                e1rm = e1rm,
                startFrom = lb(70.0),
                lastSetIndex = 3,
                rirGoal = 1,
                perLiftSlope = -2.0,
                perLiftIntercept = 0.0,
                incrementTiers = pinTiers,
                loadType = LoadType.Weighted,
                micro = pinMicro,
            )
            assertTrue(reduced.amount > 0.0)
            assertTrue(reduced.amount <= 70.0)
            assertTrue(
                pinTiers.isOnLattice(reduced, micro = pinMicro),
                "reduce must agree with adder lattice, was ${reduced.amount}",
            )
        }

        @Test
        @DisplayName("tier lattice aligns reduce to 5 lb steps")
        fun tierLatticeAlignsToFiveLbSteps() {
            val e1rm = EstimatedOneRm.calculate(15.0, 6)
            val reduced = reduceWeightForLastSetFloor(
                e1rm = e1rm,
                startFrom = lb(15.0),
                lastSetIndex = 3,
                rirGoal = 1,
                perLiftSlope = -2.0,
                perLiftIntercept = 0.0,
                incrementTiers = fiveLbTiers,
                loadType = LoadType.Weighted,
            )
            assertTrue(reduced.amount > 0.0)
            assertEquals(
                0.0,
                reduced.amount % 5.0,
                1e-6,
                "tier-aligned reduce must land on 5 lb multiples, was ${reduced.amount}",
            )
            assertTrue(reduced.amount <= 15.0)
        }

        @Test
        @DisplayName("kg candidate reduces in canonical Lb lattice")
        fun kgCandidateReducesInLb() {
            val start = kg(20.0)
            val e1rm = EstimatedOneRm.calculate(
                com.stratum.domain.lift.WeightConversion.loadMagnitudeLb(start),
                6,
            )
            val reduced = reduceWeightForLastSetFloor(
                e1rm = e1rm,
                startFrom = start,
                lastSetIndex = 3,
                rirGoal = 1,
                perLiftSlope = -2.0,
                perLiftIntercept = 0.0,
                incrementTiers = emptyList(),
                loadType = LoadType.Weighted,
            )
            assertEquals(Weight.WeightUnit.Lb, reduced.unit)
            assertTrue(reduced.amount > 0.0)
            assertTrue(
                reduced.amount <=
                    com.stratum.domain.lift.WeightConversion.loadMagnitudeLb(start) + 1e-6,
            )
        }

        @Test
        @DisplayName("bodyweight load type is a passthrough on resolve")
        fun bodyweightPassthrough() {
            val w = resolveStraightSetSharedWeight(
                candidateWeight = lb(0.0),
                slots = slots(3),
                recentBlock = null,
                perLiftSlope = null,
                perLiftIntercept = null,
                incrementTiers = emptyList(),
                loadType = LoadType.Bodyweight,
            )
            assertEquals(0.0, w.amount, 0.01)
        }

        @Test
        @DisplayName("N=1 single-slot program with full coverage under floor still reduces")
        fun singleSlotUnderFloorReduces() {
            val w = resolveStraightSetSharedWeight(
                candidateWeight = lb(15.0),
                slots = listOf(slot(0, isTopSet = true, rirGoal = 2)),
                recentBlock = block(Triple(0, 3, 0)),
                perLiftSlope = null,
                perLiftIntercept = null,
                incrementTiers = emptyList(),
                loadType = LoadType.Weighted,
            )
            assertTrue(w.amount < 15.0)
            assertTrue(w.amount > 0.0)
        }

        @Test
        @DisplayName("N=1 single-slot last set clears floor → keep")
        fun singleSlotClearsFloorKeeps() {
            val w = resolveStraightSetSharedWeight(
                candidateWeight = lb(15.0),
                slots = listOf(slot(0, isTopSet = true, rirGoal = 2)),
                recentBlock = block(Triple(0, 10, 2)),
                perLiftSlope = null,
                perLiftIntercept = null,
                incrementTiers = emptyList(),
                loadType = LoadType.Weighted,
            )
            assertEquals(15.0, w.amount, 0.01)
        }
    }
}
