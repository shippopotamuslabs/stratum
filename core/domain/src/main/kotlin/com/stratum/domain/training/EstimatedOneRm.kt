package com.stratum.domain.training

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt

/**
 * Mathematically continuous, C1 smooth-spliced Brzycki-Exponential 1-Rep Max (1RM) engine.
 * Matches scaled Brzycki for reps ≤ 10, and transitions seamlessly to an exponential decay
 * curve for higher reps to prevent anomalies (such as 11 reps projecting a lower 1RM than 10 reps).
 */
object EstimatedOneRm {
    private const val BRZYCKI_REP_THRESHOLD = 10
    private const val BRZYCKI_DENOMINATOR_BASE = 1.0278
    private const val BRZYCKI_DENOMINATOR_FACTOR = 0.0278
    private const val DECAY_RATE = 0.0278 / 0.75 // 0.03706666666666667
    private const val EXP_BASE = 0.75 / 1.0278 // 0.7297139521307647

    fun calculate(weight: Double, reps: Int): Double {
        require(reps > 0) { "reps must be positive" }
        return if (reps <= BRZYCKI_REP_THRESHOLD) {
            weight * (BRZYCKI_DENOMINATOR_BASE / (BRZYCKI_DENOMINATOR_BASE - BRZYCKI_DENOMINATOR_FACTOR * reps))
        } else {
            weight / (EXP_BASE * exp(-DECAY_RATE * (reps - BRZYCKI_REP_THRESHOLD)))
        }
    }

    /** Inverse — "what weight lets the lifter hit targetReps at the given e1RM". */
    fun weightForReps(estimatedOneRm: Double, targetReps: Int): Double {
        require(targetReps > 0) { "targetReps must be positive" }
        return if (targetReps <= BRZYCKI_REP_THRESHOLD) {
            estimatedOneRm *
                (BRZYCKI_DENOMINATOR_BASE - BRZYCKI_DENOMINATOR_FACTOR * targetReps) /
                BRZYCKI_DENOMINATOR_BASE
        } else {
            estimatedOneRm * EXP_BASE * exp(-DECAY_RATE * (targetReps - BRZYCKI_REP_THRESHOLD))
        }
    }

    /** Second inverse — "how many reps is the lifter good for at this weight at this e1RM". */
    fun repsForWeight(estimatedOneRm: Double, weight: Double): Int {
        require(weight > 0.0) { "weight must be positive" }
        require(estimatedOneRm > 0.0) { "estimatedOneRm must be positive" }
        val targetRatio = weight / estimatedOneRm
        return if (targetRatio >= EXP_BASE) {
            val r = BRZYCKI_DENOMINATOR_BASE * (1.0 - targetRatio) / BRZYCKI_DENOMINATOR_FACTOR
            r.roundToInt().coerceAtLeast(1)
        } else {
            val r = BRZYCKI_REP_THRESHOLD.toDouble() - ln(targetRatio / EXP_BASE) / DECAY_RATE
            r.roundToInt()
        }
    }
}
