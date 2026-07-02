@file:Suppress("MagicNumber", "MaxLineLength", "MatchingDeclarationName")

package com.stratum.domain.program.generation.reference

/**
 * Literature-informed minimum **calendar day-index gaps** between sessions that
 * meaningfully stress the same muscle.
 *
 * Weekday indices are Mon=0 … Sun=6; a gap of 2 (e.g. Mon → Wed) is ~48 hours
 * with one full rest day — the minimum supported for replication performance in
 * recreationally trained lifters (McLester et al. 2018, J Strength Cond Res;
 * Korak et al. 2015, Int J Exerc Sci). Twenty-four hours is consistently
 * insufficient for the same muscle group (McLester 2018; Jones et al. 2014).
 *
 * The recovery driver is *movement type* (heavy barbell multi-joint + eccentric
 * load in a lengthened position), not raw muscle size. In Korak et al. (2015),
 * the military dumbbell press (an anterior-delt compound) recovered at 90% at
 * 48h — identical to side raises and triceps extension, and far ahead of bench
 * press (70%) and deadlift (60%). The laggards are specifically heavy barbell
 * multi-joint lifts, not "large muscles" generically; NSCA's old upper-vs-lower
 * split was not supported. Sousa, Zourdos, Storey & Helms (2024, J Human
 * Kinetics) reaches the same conclusion: isolation and less-musculature
 * movements recover fastest, and upper-body may recover in 24h or less for
 * submaximal non-failure protocols. Single-joint / small muscles and the
 * anterior delt therefore share one recovery tier; heavy barbell multi-joint
 * movers (chest, back, quads, hams, glutes) warrant longer gaps when weekly
 * set dose is moderate or high (Korak et al. 2015; NSCA Essentials 4th ed.,
 * ch. 15 recovery guidelines).
 */
enum class MuscleRecoveryClass {
    /** Biceps, triceps, calves, rear/lateral/anterior delts, and unmapped muscles. */
    SmallIsolation,

    /** Chest, back, quads, hams, glutes — large multi-joint movers. */
    LargeCompound,
}

/**
 * Fractional hard-set credits (primary 1.0, secondary 0.5) at or below which a session's exposure
 * counts as light "touch" volume — incidental cross-domain work (e.g. a few triceps sets from
 * pulldowns on a lower day) that does not by itself lock in the longer recovery window.
 *
 * Per Sousa et al. (2024), even 1–3 hard sets is a meaningful stimulus, but the recovery timeline
 * scales with dose: low volume (1–3 sets) recovers in 24–48h, moderate (4–7) in 48–72h, high (8+)
 * in 72–96h+. This constant marks the upper bound of the "low volume" tier.
 */
const val MUSCLE_RECOVERY_LIGHT_DOSE = 3.0

/**
 * Minimum per-session fractional credits (primary 1.0/set, secondary 0.5/set) that count as a
 * *meaningful* stimulus requiring the adjacent-day (48h) recovery forbid — for ALL muscles.
 *
 * Set to 2.0 (≈2 hard sets): Sousa et al. (2024) place 1–3 sets in the 24–48h recovery window; 2.0
 * is the low end of that band — the dose at which a muscle's exposure is clearly more than
 * incidental tagging. Below this (e.g. a single set, or light secondary accumulation from one
 * compound) the exposure may repeat the next day. Both primary and secondary load count toward
 * the threshold: a triceps set is a triceps set whether it's a direct extension or a bench-press
 * secondary, so the gate uses total credit. In credit terms, 2.0 = two primary hard sets, or one
 * primary + two secondary-tagging compound sets, etc.
 */
const val MUSCLE_RECOVERY_MIN_SCHEDULING_DOSE = 2.0

/**
 * Moderate per-session dose — recovery shifts to the longer window: 48h minimum for small/isolation
 * muscles, 72h for large compounds (Sousa et al. 2024; ≈4–7 hard sets). At or above this dose the
 * 72h tier for large compounds becomes a HARD calendar constraint (see addLargeCompoundSpacingForbids).
 */
const val MUSCLE_RECOVERY_MODERATE_DOSE = 8.0

/** High per-session dose (≈8+ hard sets) — prefer 72h+ before repeating; recovery spills into 96h. */
const val MUSCLE_RECOVERY_HEAVY_DOSE = 12.0

fun muscleRecoveryClass(muscle: String): MuscleRecoveryClass =
    when (muscle) {
        "Chest",
        "Back",
        "Quad",
        "Hamstring",
        "Glute",
        -> MuscleRecoveryClass.LargeCompound
        // All other muscles — biceps, triceps, calves, every delt head (including the
        // anterior delt, which Korak 2015 shows recovers at the same 48h rate as side
        // raises / triceps extension), traps, forearms, abs, etc. — share one tier.
        else -> MuscleRecoveryClass.SmallIsolation
    }

/**
 * Minimum difference in weekday indices before the same muscle should be trained
 * again. Returns 0 when neither session contributes meaningful dose.
 *
 * Uses the earlier and later session doses separately so light cross-domain touch
 * (e.g. 2 triceps sets on a lower day) still requires 48 h before a push session
 * even when that push session is high volume.
 */
fun minimumRecoveryCalendarGap(
    muscle: String,
    earlierFractionalSets: Double,
    laterFractionalSets: Double,
): Int {
    if (earlierFractionalSets <= 0.0 && laterFractionalSets <= 0.0) return 0
    val peakDose = maxOf(earlierFractionalSets, laterFractionalSets)
    if (peakDose < MUSCLE_RECOVERY_MIN_SCHEDULING_DOSE) return 0
    val touchInvolved =
        earlierFractionalSets < MUSCLE_RECOVERY_LIGHT_DOSE ||
            laterFractionalSets < MUSCLE_RECOVERY_LIGHT_DOSE
    return when (muscleRecoveryClass(muscle)) {
        MuscleRecoveryClass.SmallIsolation ->
            when {
                touchInvolved -> 2
                peakDose >= MUSCLE_RECOVERY_HEAVY_DOSE -> 3
                else -> 2
            }
        MuscleRecoveryClass.LargeCompound ->
            when {
                peakDose < MUSCLE_RECOVERY_LIGHT_DOSE -> 2
                peakDose < MUSCLE_RECOVERY_MODERATE_DOSE -> 3
                else -> 3
            }
    }
}
