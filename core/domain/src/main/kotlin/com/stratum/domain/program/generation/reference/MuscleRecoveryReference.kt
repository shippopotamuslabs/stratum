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
 * Single-joint / small muscles tolerate the same calendar spacing but may
 * tolerate lower per-session dose; multi-joint and large muscles warrant longer
 * gaps when weekly set dose is moderate or high (Korak et al. 2015; NSCA
 * Essentials 4th ed., ch. 15 recovery guidelines).
 */
enum class MuscleRecoveryClass {
    /** Biceps, triceps, calves, rear/lateral delts — isolation-dominant. */
    SmallIsolation,

    /** Anterior delt — often trained with compounds but smaller than chest/back/legs. */
    Medium,

    /** Chest, back, quads, hams, glutes — large multi-joint movers. */
    LargeCompound,
}

/** Fractional hard-set credits (primary 1.0, secondary 0.5) — touch / incidental dose. */
const val MUSCLE_RECOVERY_LIGHT_DOSE = 3.0

/**
 * Minimum per-session fractional credits that affect calendar spacing.
 * Incidental secondary volume below this (e.g. 1.0 triceps from pulldowns) is ignored
 * so optimizers can insert pull days between lower touch and push sessions.
 */
const val MUSCLE_RECOVERY_MIN_SCHEDULING_DOSE = 2.0

/** Meaningful per-session dose — 48 h minimum for small muscles, 72 h for large. */
const val MUSCLE_RECOVERY_MODERATE_DOSE = 8.0

/** High per-session dose — prefer 72 h+ before repeating. */
const val MUSCLE_RECOVERY_HEAVY_DOSE = 12.0

fun muscleRecoveryClass(muscle: String): MuscleRecoveryClass =
    when (muscle) {
        "Bicep",
        "Tricep",
        "Calf",
        "LateralDeltoid",
        "PosteriorDeltoid",
        -> MuscleRecoveryClass.SmallIsolation
        "AnteriorDeltoid" -> MuscleRecoveryClass.Medium
        "Chest",
        "Back",
        "Quad",
        "Hamstring",
        "Glute",
        -> MuscleRecoveryClass.LargeCompound
        else -> MuscleRecoveryClass.Medium
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
        MuscleRecoveryClass.Medium ->
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
