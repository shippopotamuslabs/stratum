package com.stratum.domain.workout.myorep

import kotlin.math.roundToInt

/**
 * Literature-based default rep projection for myo-rep mini-sets under short rest
 * (~10–20s, [RestDefaultForTransition.MYO_MINI_DEFAULT_REST_SECONDS]).
 *
 * The population fatigue slope (−2 reps/set) is calibrated for full inter-set rest
 * (~2–3 min). Rest-pause / myo-rep practice (Fagerli and related cluster/rest-pause
 * literature) shows a much steeper acute drop: an activation near failure is typically
 * followed by mini-sets in the ~3–5 rep band, then further 1–3 rep drops between minis.
 *
 * Per-lift [com.stratum.domain.importing.FatigueDecay] is measured on normal-rest multi-set
 * sessions, not mini-set chains — so it is **not** used here. When mini-set history is
 * available later, plug it in via [MyoMiniRepProjection] without changing call sites.
 */
object MyoMiniRepProjection {
    /**
     * First mini after activation: fraction of the activation's failure-rep capacity
     * (reps + RIR). ~0.45 maps a 10–12 rep activation onto the classic 4–5 mini band.
     */
    const val FIRST_MINI_CAPACITY_FRACTION: Double = 0.45

    /**
     * Subsequent minis: absolute rep drop from the last logged mini under ~15s rest.
     * Aggressive residual-fatigue default until per-lifter mini history exists.
     */
    const val SUBSEQUENT_MINI_REP_DROP: Int = 3

    const val MIN_REPS: Int = 1

    /**
     * @param priorReps last logged reps on the prior set in the myo chain
     * @param priorRir RIR on that set (null → treat as 0, common for minis)
     * @param isFirstMini true when projecting the first mini after activation
     */
    fun expectedReps(
        priorReps: Int,
        priorRir: Int?,
        isFirstMini: Boolean,
    ): Int {
        val effectiveRir = priorRir ?: 0
        val failureReps = (priorReps + effectiveRir).coerceAtLeast(1)
        return if (isFirstMini) {
            (failureReps * FIRST_MINI_CAPACITY_FRACTION)
                .roundToInt()
                .coerceAtLeast(MIN_REPS)
        } else {
            // Between minis, drop from performed reps (minis are near-fail; RIR rarely high).
            (priorReps - SUBSEQUENT_MINI_REP_DROP).coerceAtLeast(MIN_REPS)
        }
    }
}
