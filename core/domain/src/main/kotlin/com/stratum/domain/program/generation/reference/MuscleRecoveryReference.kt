@file:Suppress("MagicNumber", "MaxLineLength", "MatchingDeclarationName")

package com.stratum.domain.program.generation.reference

import kotlin.math.ceil

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
 * Phase 1 introduces [LiftStressClass] and an additive hours model whose outputs
 * are calibrated to the literature matrix (see [recoveryHours]). The legacy
 * muscle-name taxonomy ([MuscleRecoveryClass]) remains for existing solver forbids
 * until phase 2+ migration.
 */
enum class LiftStressClass {
    Isolation,
    UpperCompound,
    HeavyAxial,
}

/** @deprecated Replaced by [LiftStressClass] in phase 2+. */
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
 */
const val MUSCLE_RECOVERY_LIGHT_DOSE = 3.0

/** Upper bound of the low-volume tier in fractional credits (≈1–3 hard sets). */
const val MUSCLE_RECOVERY_LOW_DOSE_MAX = 3.0

/**
 * Minimum per-session fractional credits (primary 1.0/set, secondary 0.5/set) that count as a
 * *meaningful* stimulus requiring the adjacent-day (48h) recovery forbid — for ALL muscles.
 */
const val MUSCLE_RECOVERY_MIN_SCHEDULING_DOSE = 2.0

/**
 * Upper bound of the moderate-volume tier in fractional credits (≈4–7 hard sets).
 * Introduced for the class-aware model; the solver hard forbid still uses [MUSCLE_RECOVERY_MODERATE_DOSE].
 */
const val MUSCLE_RECOVERY_MODERATE_DOSE_MAX = 7.0

/**
 * Moderate per-session dose — recovery shifts to the longer window: 48h minimum for small/isolation
 * muscles, 72h for large compounds (Sousa et al. 2024; ≈4–7 hard sets). At or above this dose the
 * 72h tier for large compounds becomes a HARD calendar constraint (see addLargeCompoundSpacingForbids).
 */
const val MUSCLE_RECOVERY_MODERATE_DOSE = 8.0

/** High per-session dose (≈8+ hard sets) — prefer 72h+ before repeating; recovery spills into 96h. */
const val MUSCLE_RECOVERY_HEAVY_DOSE = 12.0

const val RECOVERY_BASELINE_HOURS = 24
const val RECOVERY_VOLUME_MODERATE_HOURS = 24
const val RECOVERY_VOLUME_HIGH_HOURS = 48
const val RECOVERY_CLASS_UPPER_COMPOUND_HOURS = 24
const val RECOVERY_CLASS_HEAVY_AXIAL_HOURS = 48
const val RECOVERY_FAILURE_HOURS = 24

data class MuscleSessionStress(
    val fractionalSets: Double,
    val stressClass: LiftStressClass,
)

private enum class RecoveryVolumeTier {
    Low,
    Moderate,
    High,
}

fun muscleRecoveryClass(muscle: String): MuscleRecoveryClass =
    when (muscle) {
        "Chest",
        "Back",
        "Quad",
        "Hamstring",
        "Glute",
        -> MuscleRecoveryClass.LargeCompound
        else -> MuscleRecoveryClass.SmallIsolation
    }

/** Maps legacy muscle-name classes to [LiftStressClass] for transitional call sites. */
fun legacyMuscleNameToStressClass(muscle: String): LiftStressClass =
    when (muscleRecoveryClass(muscle)) {
        MuscleRecoveryClass.LargeCompound -> LiftStressClass.HeavyAxial
        MuscleRecoveryClass.SmallIsolation -> LiftStressClass.Isolation
    }

/**
 * Additive recovery hours from the end of the earlier session. The calibrated matrix in
 * [calibratedRecoveryHours] is authoritative when it disagrees with raw constant sums.
 */
fun recoveryHours(
    earlierDose: Double,
    stressClass: LiftStressClass,
    minRealizedRir: Int? = null,
): Int {
    val tier = volumeTier(earlierDose) ?: return 0
    val base = calibratedRecoveryHours(stressClass, tier)
    val failure = if (minRealizedRir == 0) RECOVERY_FAILURE_HOURS else 0
    return base + failure
}

/** Minimum day-index gap after [earlier] before the muscle is meaningfully loaded again. */
fun minimumRecoveryCalendarGap(earlier: MuscleSessionStress, minRealizedRir: Int? = null): Int {
    val hours = recoveryHours(earlier.fractionalSets, earlier.stressClass, minRealizedRir)
    if (hours <= 0) return 0
    return ceil(hours / 24.0).toInt()
}

private fun volumeTier(dose: Double): RecoveryVolumeTier? {
    if (dose < MUSCLE_RECOVERY_MIN_SCHEDULING_DOSE) return null
    return when {
        dose <= MUSCLE_RECOVERY_LOW_DOSE_MAX -> RecoveryVolumeTier.Low
        dose <= MUSCLE_RECOVERY_MODERATE_DOSE_MAX -> RecoveryVolumeTier.Moderate
        else -> RecoveryVolumeTier.High
    }
}

/** Literature matrix contract — table wins over additive constant sums when they diverge. */
private fun calibratedRecoveryHours(
    stressClass: LiftStressClass,
    tier: RecoveryVolumeTier,
): Int =
    when (stressClass) {
        LiftStressClass.Isolation ->
            when (tier) {
                RecoveryVolumeTier.Low -> 24
                RecoveryVolumeTier.Moderate -> 48
                RecoveryVolumeTier.High -> 72
            }
        LiftStressClass.UpperCompound ->
            when (tier) {
                RecoveryVolumeTier.Low -> 48
                RecoveryVolumeTier.Moderate -> 72
                RecoveryVolumeTier.High -> 72
            }
        LiftStressClass.HeavyAxial ->
            when (tier) {
                RecoveryVolumeTier.Low -> 48
                RecoveryVolumeTier.Moderate -> 72
                RecoveryVolumeTier.High -> 96
            }
    }
