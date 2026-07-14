package com.stratum.domain.checkin

import com.stratum.domain.dashboard.CALLOUT_MIN_POINTS
import com.stratum.domain.dashboard.TrendPoint
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.program.identity.WorkoutLiftId
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class CheckInDecisionEngineTest {

    @Test
    @DisplayName("Rule 1: climbing performance maintains even with mild soreness")
    fun climbingMaintainsDespiteMildSoreness() {
        val decision =
            CheckInDecisionEngine.decide(
                baseInput(
                    trend = PerformanceTrend.Climbing,
                    recovery = RecoverySignal.MildSoreness,
                    tenureWeeks = 8,
                    setCount = 4,
                ),
            )
        assertEquals(CheckInActionKind.Maintain, decision.kind)
        assertEquals(CheckInRationale.ClimbingMaintain, decision.rationale)
    }

    @Test
    @DisplayName("Rule 2: flat + good recovery + stale lift → swap kind")
    fun flatStaleSwaps() {
        val decision =
            CheckInDecisionEngine.decide(
                baseInput(
                    trend = PerformanceTrend.Flat,
                    recovery = RecoverySignal.Good,
                    tenureWeeks = 5,
                    setCount = 3,
                ),
            )
        assertEquals(CheckInActionKind.SwapLift, decision.kind)
        assertTrue(decision.rationale is CheckInRationale.SoftStaleSwap)
    }

    @Test
    @DisplayName("Rule 2: flat + good recovery + fresh lift → +1 set")
    fun flatFreshIncreasesVolume() {
        val decision =
            CheckInDecisionEngine.decide(
                baseInput(
                    trend = PerformanceTrend.Flat,
                    recovery = RecoverySignal.Good,
                    tenureWeeks = 2,
                    setCount = 3,
                ),
            )
        assertEquals(CheckInActionKind.IncreaseVolume, decision.kind)
        assertEquals(1, decision.addSets)
    }

    @Test
    @DisplayName("Rule 2: dropping e1RM with good recovery + fresh → +volume (not cut)")
    fun droppingFreshIncreasesVolumeNotCut() {
        val decision =
            CheckInDecisionEngine.decide(
                baseInput(
                    trend = PerformanceTrend.Dropping,
                    recovery = RecoverySignal.Good,
                    tenureWeeks = 3,
                    setCount = 4,
                ),
            )
        assertEquals(CheckInActionKind.IncreaseVolume, decision.kind)
    }

    @Test
    @DisplayName("Rule 2: dropping e1RM with good recovery + stale → swap kind")
    fun droppingStaleSwapsNotCut() {
        val decision =
            CheckInDecisionEngine.decide(
                baseInput(
                    trend = PerformanceTrend.Dropping,
                    recovery = RecoverySignal.Good,
                    tenureWeeks = 6,
                    setCount = 4,
                ),
            )
        assertEquals(CheckInActionKind.SwapLift, decision.kind)
    }

    @Test
    @DisplayName("Rule 3: joint pain cuts volume even when performance is flat")
    fun jointPainCutsDespiteFlat() {
        val decision =
            CheckInDecisionEngine.decide(
                baseInput(
                    trend = PerformanceTrend.Flat,
                    recovery = RecoverySignal.JointPain,
                    tenureWeeks = 2,
                    setCount = 4,
                ),
            )
        assertEquals(CheckInActionKind.CutVolume, decision.kind)
        assertEquals(1, decision.removeSets)
        assertTrue(decision.rationale is CheckInRationale.JointPainCut)
    }

    @Test
    @DisplayName("Rule 3: single-set lift is AlreadyAtFloorSets (no fake deload)")
    fun singleSetFloorRationale() {
        val decision =
            CheckInDecisionEngine.decide(
                baseInput(
                    trend = PerformanceTrend.Dropping,
                    recovery = RecoverySignal.SevereUnderRecovery,
                    tenureWeeks = 1,
                    setCount = 1,
                ),
            )
        assertEquals(CheckInActionKind.CutVolume, decision.kind)
        assertEquals(0, decision.removeSets)
        assertEquals(CheckInRationale.AlreadyAtFloorSets, decision.rationale)
    }

    @Test
    @DisplayName("Insufficient hard data without severe recovery → maintain")
    fun insufficientMaintains() {
        val decision =
            CheckInDecisionEngine.decide(
                baseInput(
                    trend = PerformanceTrend.Insufficient,
                    recovery = RecoverySignal.MildSoreness,
                    tenureWeeks = 6,
                    setCount = 3,
                ),
            )
        assertEquals(CheckInActionKind.Maintain, decision.kind)
    }

    @Test
    @DisplayName("classifyPerformanceTrend: short noisy window is Insufficient")
    fun shortNoisyWindowInsufficient() {
        val noisyDrop =
            listOf(
                point(110.0),
                point(108.0),
                point(105.0),
            )
        assertEquals(
            PerformanceTrend.Insufficient,
            CheckInDecisionEngine.classifyPerformanceTrend(noisyDrop, sessionCount = 10),
        )
    }

    @Test
    @DisplayName("classifyPerformanceTrend: strong multi-week climb / drop / flat with R²")
    fun classifyTrendsWithCalloutGates() {
        val climbing = (0 until CALLOUT_MIN_POINTS).map { i -> point(100.0 + i * 2.0) }
        val flat = (0 until CALLOUT_MIN_POINTS).map { i -> point(100.0 + (i % 2) * 0.1) }
        val dropping = (0 until CALLOUT_MIN_POINTS).map { i -> point(120.0 - i * 2.0) }

        assertEquals(
            PerformanceTrend.Climbing,
            CheckInDecisionEngine.classifyPerformanceTrend(climbing, sessionCount = 10),
        )
        assertEquals(
            PerformanceTrend.Flat,
            CheckInDecisionEngine.classifyPerformanceTrend(flat, sessionCount = 10),
        )
        assertEquals(
            PerformanceTrend.Dropping,
            CheckInDecisionEngine.classifyPerformanceTrend(dropping, sessionCount = 10),
        )
        assertEquals(
            PerformanceTrend.Insufficient,
            CheckInDecisionEngine.classifyPerformanceTrend(climbing, sessionCount = 2),
        )
    }

    @Test
    @DisplayName("resolveRecoverySignal: worst of overall and local muscles wins")
    fun recoveryWorstWins() {
        val signal =
            CheckInDecisionEngine.resolveRecoverySignal(
                overall = RecoverySignal.Good,
                muscleSignals =
                    mapOf(
                        MuscleGroup.Chest to RecoverySignal.MildSoreness,
                        MuscleGroup.Tricep to RecoverySignal.JointPain,
                    ),
                primaryMuscles = setOf(MuscleGroup.Chest, MuscleGroup.Tricep),
            )
        assertEquals(RecoverySignal.JointPain, signal)
    }

    @Test
    @DisplayName("setsToRemove: ~25% with floor of one set remaining")
    fun setsToRemoveMath() {
        assertEquals(0, CheckInDecisionEngine.setsToRemove(1))
        assertEquals(1, CheckInDecisionEngine.setsToRemove(3))
        assertEquals(1, CheckInDecisionEngine.setsToRemove(4))
        assertEquals(2, CheckInDecisionEngine.setsToRemove(8))
    }

    @Test
    @DisplayName("Rule 3 wins over Rule 1: climbing + joint pain still cuts volume")
    fun climbingWithJointPainCuts() {
        val decision =
            CheckInDecisionEngine.decide(
                baseInput(
                    trend = PerformanceTrend.Climbing,
                    recovery = RecoverySignal.JointPain,
                    tenureWeeks = 2,
                    setCount = 4,
                ),
            )
        assertEquals(CheckInActionKind.CutVolume, decision.kind)
        assertEquals(1, decision.removeSets)
        assertTrue(decision.rationale is CheckInRationale.JointPainCut)
    }

    @Test
    @DisplayName("Rule 3 wins over Rule 1: climbing + severe under-recovery cuts volume")
    fun climbingWithSevereCuts() {
        val decision =
            CheckInDecisionEngine.decide(
                baseInput(
                    trend = PerformanceTrend.Climbing,
                    recovery = RecoverySignal.SevereUnderRecovery,
                    tenureWeeks = 8,
                    setCount = 4,
                ),
            )
        assertEquals(CheckInActionKind.CutVolume, decision.kind)
        assertTrue(decision.rationale is CheckInRationale.SevereUnderRecoveryCut)
    }

    @Test
    @DisplayName("Rule 2: mild soreness + flat + fresh still increases volume (not cut)")
    fun mildSorenessFlatFreshIncreases() {
        val decision =
            CheckInDecisionEngine.decide(
                baseInput(
                    trend = PerformanceTrend.Flat,
                    recovery = RecoverySignal.MildSoreness,
                    tenureWeeks = 2,
                    setCount = 3,
                ),
            )
        assertEquals(CheckInActionKind.IncreaseVolume, decision.kind)
        assertEquals(1, decision.addSets)
    }

    @Test
    @DisplayName("G3: mild soreness + dropping → hold (not swap, even if stale)")
    fun mildSorenessDroppingHolds() {
        val decision =
            CheckInDecisionEngine.decide(
                baseInput(
                    trend = PerformanceTrend.Dropping,
                    recovery = RecoverySignal.MildSoreness,
                    tenureWeeks = 6,
                    setCount = 3,
                ),
            )
        assertEquals(CheckInActionKind.Maintain, decision.kind)
        assertEquals(CheckInRationale.DroppingMildRecoveryHold, decision.rationale)
    }

    @Test
    @DisplayName("G1: flat + good + fresh + at ceiling → Maintain")
    fun flatFreshAtCeilingMaintains() {
        val decision =
            CheckInDecisionEngine.decide(
                baseInput(
                    trend = PerformanceTrend.Flat,
                    recovery = RecoverySignal.Good,
                    tenureWeeks = 2,
                    setCount = 3,
                    atCeiling = true,
                ),
            )
        assertEquals(CheckInActionKind.Maintain, decision.kind)
        assertEquals(CheckInRationale.SoftFreshAtVolumeCeiling, decision.rationale)
    }

    @Test
    @DisplayName("G1: flat + good + stale + at ceiling still swaps (variation, not volume)")
    fun flatStaleAtCeilingStillSwaps() {
        val decision =
            CheckInDecisionEngine.decide(
                baseInput(
                    trend = PerformanceTrend.Flat,
                    recovery = RecoverySignal.Good,
                    tenureWeeks = 5,
                    setCount = 3,
                    atCeiling = true,
                ),
            )
        assertEquals(CheckInActionKind.SwapLift, decision.kind)
    }

    @Test
    @DisplayName("G3: dropping + good + fresh + under ceiling → +1")
    fun droppingGoodFreshIncreases() {
        val decision =
            CheckInDecisionEngine.decide(
                baseInput(
                    trend = PerformanceTrend.Dropping,
                    recovery = RecoverySignal.Good,
                    tenureWeeks = 2,
                    setCount = 3,
                ),
            )
        assertEquals(CheckInActionKind.IncreaseVolume, decision.kind)
    }

    @Test
    @DisplayName("G3: dropping + good + fresh + at ceiling → Maintain")
    fun droppingGoodFreshAtCeiling() {
        val decision =
            CheckInDecisionEngine.decide(
                baseInput(
                    trend = PerformanceTrend.Dropping,
                    recovery = RecoverySignal.Good,
                    tenureWeeks = 2,
                    setCount = 3,
                    atCeiling = true,
                ),
            )
        assertEquals(CheckInActionKind.Maintain, decision.kind)
        assertEquals(CheckInRationale.SoftFreshAtVolumeCeiling, decision.rationale)
    }

    @Test
    @DisplayName("empty primary muscles on soft path → Maintain (no soft lever)")
    fun emptyPrimaryNoSoftLever() {
        val decision =
            CheckInDecisionEngine.decide(
                LiftCheckInInput(
                    workoutLiftId = WorkoutLiftId("wl-1"),
                    liftId = LiftId("mystery"),
                    liftName = "Mystery",
                    primaryMuscles = emptySet(),
                    performanceTrend = PerformanceTrend.Flat,
                    recoverySignal = RecoverySignal.Good,
                    rotationTenureWeeks = 2,
                    currentSetCount = 3,
                ),
            )
        assertEquals(CheckInActionKind.Maintain, decision.kind)
        assertEquals(CheckInRationale.NoClearChange, decision.rationale)
    }

    @Test
    @DisplayName("G3: dropping + mild + fresh → hold")
    fun droppingMildFreshHolds() {
        val decision =
            CheckInDecisionEngine.decide(
                baseInput(
                    trend = PerformanceTrend.Dropping,
                    recovery = RecoverySignal.MildSoreness,
                    tenureWeeks = 1,
                    setCount = 3,
                ),
            )
        assertEquals(CheckInActionKind.Maintain, decision.kind)
        assertEquals(CheckInRationale.DroppingMildRecoveryHold, decision.rationale)
    }

    @Test
    @DisplayName("Rule 2: tenure exactly STALE_LIFT_TENURE_WEEKS (4) → swap")
    fun tenureExactlyFourWeeksSwaps() {
        assertEquals(4, STALE_LIFT_TENURE_WEEKS)
        val decision =
            CheckInDecisionEngine.decide(
                baseInput(
                    trend = PerformanceTrend.Flat,
                    recovery = RecoverySignal.Good,
                    tenureWeeks = STALE_LIFT_TENURE_WEEKS,
                    setCount = 3,
                ),
            )
        assertEquals(CheckInActionKind.SwapLift, decision.kind)
        assertTrue(decision.rationale is CheckInRationale.SoftStaleSwap)
    }

    @Test
    @DisplayName("Rule 2: tenure just under stale boundary → +volume")
    fun tenureThreeWeeksIncreases() {
        val decision =
            CheckInDecisionEngine.decide(
                baseInput(
                    trend = PerformanceTrend.Flat,
                    recovery = RecoverySignal.Good,
                    tenureWeeks = STALE_LIFT_TENURE_WEEKS - 1,
                    setCount = 3,
                ),
            )
        assertEquals(CheckInActionKind.IncreaseVolume, decision.kind)
    }

    @Test
    @DisplayName("recommendationComparator: Cut before Swap before Increase before Maintain; Dropping first")
    fun recommendationComparatorOrdering() {
        fun rec(
            name: String,
            action: CheckInAction,
            trend: PerformanceTrend = PerformanceTrend.Flat,
        ) = CheckInRecommendation(
            workoutLiftId = WorkoutLiftId("wl-$name"),
            liftId = LiftId(name),
            liftName = name,
            primaryMuscles = setOf(MuscleGroup.Chest),
            performanceTrend = trend,
            recoverySignal = RecoverySignal.Good,
            rotationTenureWeeks = 4,
            currentSetCount = 3,
            action = action,
            rationale = CheckInRationale.NoClearChange,
        )

        val unordered =
            listOf(
                rec("zebra", CheckInAction.Maintain),
                rec("alpha", CheckInAction.IncreaseVolume(1)),
                rec("beta", CheckInAction.SwapLift(LiftId("x"), "X")),
                rec("gamma", CheckInAction.CutVolume(1)),
                rec("delta", CheckInAction.IncreaseVolume(1), trend = PerformanceTrend.Dropping),
            )
        val ordered = unordered.sortedWith(CheckInDecisionEngine.recommendationComparator())
        assertEquals(CheckInAction.CutVolume(1), ordered[0].action)
        assertTrue(ordered[1].action is CheckInAction.SwapLift)
        // Among IncreaseVolume, Dropping ranks above Flat
        assertEquals("delta", ordered[2].liftName)
        assertEquals("alpha", ordered[3].liftName)
        assertTrue(ordered[4].action is CheckInAction.Maintain)
    }

    private fun baseInput(
        trend: PerformanceTrend,
        recovery: RecoverySignal,
        tenureWeeks: Int,
        setCount: Int,
        atCeiling: Boolean = false,
    ): LiftCheckInInput =
        LiftCheckInInput(
            workoutLiftId = WorkoutLiftId("wl-1"),
            liftId = LiftId("bench-press"),
            liftName = "Bench Press",
            primaryMuscles = setOf(MuscleGroup.Chest),
            performanceTrend = trend,
            recoverySignal = recovery,
            rotationTenureWeeks = tenureWeeks,
            currentSetCount = setCount,
            atVolumeCeiling = atCeiling,
        )

    private var weekCounter = 0L

    private fun point(value: Double): TrendPoint {
        weekCounter += 1
        return TrendPoint(
            weekStart = Instant.parse("2026-01-01T00:00:00Z").plusSeconds(weekCounter * 7 * 24 * 3600),
            value = value,
        )
    }
}
