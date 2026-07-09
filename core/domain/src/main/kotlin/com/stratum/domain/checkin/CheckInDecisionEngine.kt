package com.stratum.domain.checkin

import com.stratum.domain.dashboard.CALLOUT_MIN_POINTS
import com.stratum.domain.dashboard.CALLOUT_MIN_R_SQUARED
import com.stratum.domain.dashboard.LIFT_CALLOUT_MIN_SESSIONS
import com.stratum.domain.dashboard.LIFT_CALLOUT_SLOPE_THRESHOLD
import com.stratum.domain.dashboard.TrendPoint
import com.stratum.domain.dashboard.olsFit
import kotlin.math.abs
import kotlin.math.roundToInt

/** Weeks on the same variation before a plateaued / soft lift is considered stale (swap path). */
const val STALE_LIFT_TENURE_WEEKS = 4

/** Working-set cut fraction for severe under-recovery / joint pain (~25%). */
const val VOLUME_CUT_FRACTION = 0.25

/**
 * Pure deterministic coach: hard performance is primary; subjective recovery is secondary.
 *
 * Trend classification uses the **same high bar as dashboard lift callouts** (slope, R², point
 * count, session count) so check-in does not fire on short noisy windows.
 *
 * Rule 1: Climbing → Maintain (even with mild soreness).
 * Rule 2: Flat **or** Dropping with good/mild recovery → swap if stale (≥4 weeks), else +1 set.
 * Rule 3: Severe under-recovery **or** joint pain → cut volume ~25% (recovery-gated only).
 * Insufficient trend evidence → Maintain (prefer silence over speculative edits).
 *
 * Swap **catalog** enrichment is owned by [EvaluateCheckInUseCase]; this engine only emits
 * [CheckInActionKind.SwapLift] without lift ids.
 */
object CheckInDecisionEngine {

    fun classifyPerformanceTrend(
        performanceSeries: List<TrendPoint>,
        sessionCount: Int = LIFT_CALLOUT_MIN_SESSIONS,
        slopeThreshold: Double = LIFT_CALLOUT_SLOPE_THRESHOLD,
        minPoints: Int = CALLOUT_MIN_POINTS,
        minRSquared: Double = CALLOUT_MIN_R_SQUARED,
        minSessions: Int = LIFT_CALLOUT_MIN_SESSIONS,
    ): PerformanceTrend {
        if (sessionCount < minSessions || performanceSeries.size < minPoints) {
            return PerformanceTrend.Insufficient
        }
        val fit = olsFit(performanceSeries)
        if (fit == null) return PerformanceTrend.Flat
        if (abs(fit.slope) < slopeThreshold) return PerformanceTrend.Flat
        if (fit.rSquared < minRSquared) return PerformanceTrend.Insufficient
        return if (fit.slope > 0) PerformanceTrend.Climbing else PerformanceTrend.Dropping
    }

    fun resolveRecoverySignal(
        overall: RecoverySignal,
        muscleSignals: Map<com.stratum.domain.onboarding.MuscleGroup, RecoverySignal>,
        primaryMuscles: Set<com.stratum.domain.onboarding.MuscleGroup>,
    ): RecoverySignal {
        val local =
            primaryMuscles
                .mapNotNull { muscleSignals[it] }
                .maxByOrNull { it.severity }
                ?: RecoverySignal.Good
        return listOf(overall, local).maxBy { it.severity }
    }

    fun decide(input: LiftCheckInInput): CheckInCoreDecision {
        val severeRecovery =
            input.recoverySignal == RecoverySignal.SevereUnderRecovery ||
                input.recoverySignal == RecoverySignal.JointPain

        if (severeRecovery) {
            val remove = setsToRemove(input.currentSetCount)
            return if (remove == 0) {
                CheckInCoreDecision(
                    kind = CheckInActionKind.CutVolume,
                    removeSets = 0,
                    rationale = CheckInRationale.AlreadyAtFloorSets,
                )
            } else {
                val rationale =
                    if (input.recoverySignal == RecoverySignal.JointPain) {
                        CheckInRationale.JointPainCut(remove)
                    } else {
                        CheckInRationale.SevereUnderRecoveryCut(remove)
                    }
                CheckInCoreDecision(
                    kind = CheckInActionKind.CutVolume,
                    removeSets = remove,
                    rationale = rationale,
                )
            }
        }

        if (input.performanceTrend == PerformanceTrend.Climbing) {
            return CheckInCoreDecision(
                kind = CheckInActionKind.Maintain,
                rationale = CheckInRationale.ClimbingMaintain,
            )
        }

        if (input.performanceTrend == PerformanceTrend.Insufficient) {
            return CheckInCoreDecision(
                kind = CheckInActionKind.Maintain,
                rationale = CheckInRationale.NoClearChange,
            )
        }

        if (isSoftPerformanceWithOkRecovery(input)) {
            val soft = input.performanceTrend.toSoftTrend()
            return if (input.rotationTenureWeeks >= STALE_LIFT_TENURE_WEEKS) {
                CheckInCoreDecision(
                    kind = CheckInActionKind.SwapLift,
                    rationale =
                        CheckInRationale.SoftStaleSwap(
                            softTrend = soft,
                            tenureWeeks = input.rotationTenureWeeks,
                        ),
                )
            } else {
                CheckInCoreDecision(
                    kind = CheckInActionKind.IncreaseVolume,
                    addSets = 1,
                    rationale =
                        CheckInRationale.SoftFreshIncrease(
                            softTrend = soft,
                            tenureWeeks = input.rotationTenureWeeks,
                        ),
                )
            }
        }

        return CheckInCoreDecision(
            kind = CheckInActionKind.Maintain,
            rationale = CheckInRationale.NoClearChange,
        )
    }

    fun setsToRemove(currentSetCount: Int): Int {
        if (currentSetCount <= 1) return 0
        val raw = (currentSetCount * VOLUME_CUT_FRACTION).roundToInt().coerceAtLeast(1)
        return raw.coerceAtMost(currentSetCount - 1)
    }

    fun isSoftPerformanceWithOkRecovery(input: LiftCheckInInput): Boolean {
        val soft =
            input.performanceTrend == PerformanceTrend.Flat ||
                input.performanceTrend == PerformanceTrend.Dropping
        val recoveryOk =
            input.recoverySignal == RecoverySignal.Good ||
                input.recoverySignal == RecoverySignal.MildSoreness
        return soft && recoveryOk
    }

    fun recommendationComparator(): Comparator<CheckInRecommendation> =
        compareBy<CheckInRecommendation> { it.action.priority }
            .thenByDescending { it.performanceTrend == PerformanceTrend.Dropping }
            .thenBy { it.liftName.lowercase() }

    private fun PerformanceTrend.toSoftTrend(): SoftPerformanceTrend =
        when (this) {
            PerformanceTrend.Dropping -> SoftPerformanceTrend.Dropping
            else -> SoftPerformanceTrend.Flat
        }

    private val CheckInAction.priority: Int
        get() =
            when (this) {
                is CheckInAction.CutVolume -> 0
                is CheckInAction.SwapLift -> 1
                is CheckInAction.IncreaseVolume -> 2
                is CheckInAction.Maintain -> 3
            }

    private val CheckInActionKind.priority: Int
        get() =
            when (this) {
                CheckInActionKind.CutVolume -> 0
                CheckInActionKind.SwapLift -> 1
                CheckInActionKind.IncreaseVolume -> 2
                CheckInActionKind.Maintain -> 3
            }

    private val RecoverySignal.severity: Int
        get() =
            when (this) {
                RecoverySignal.Good -> 0
                RecoverySignal.MildSoreness -> 1
                RecoverySignal.SevereUnderRecovery -> 2
                RecoverySignal.JointPain -> 3
            }
}
