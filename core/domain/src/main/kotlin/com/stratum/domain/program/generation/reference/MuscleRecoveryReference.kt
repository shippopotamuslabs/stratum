@file:Suppress("MagicNumber", "MaxLineLength", "MatchingDeclarationName")

package com.stratum.domain.program.generation.reference

import kotlin.math.ceil
import com.stratum.domain.onboarding.Weekday

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

/**
 * Muscles whose sheer cross-sectional size makes the isolation recovery window insufficient at
 * low doses — a single-joint lift on a large muscle group (e.g. Leg Extension on Quad) recovers
 * more like a compound than a bicep curl. Matches the legacy [MuscleRecoveryClass.LargeCompound]
 * set; isolated here so the lift-based [LiftStressClass] model can re-introduce the muscle-size
 * signal it lost when it replaced [MuscleRecoveryClass].
 */
val LARGE_COMPOUND_MUSCLES: Set<String> = setOf("Chest", "Back", "Quad", "Hamstring", "Glute")

/** True when [muscle] is a [LARGE_COMPOUND_MUSCLES] — isolation lifts on these get +12h at Low tier. */
fun isLargeCompoundMuscle(muscle: String): Boolean = muscle in LARGE_COMPOUND_MUSCLES

/**
 * Added to the Low-tier isolation window when an isolation lift trains a [LARGE_COMPOUND_MUSCLES].
 * Lifts Leg Extension (1–3 sets, 1–3 RIR) from 24h to 36h → calendar gap 2 → adjacent-day forbid,
 * matching the literature-cited principle (MuscleRecoveryReference.kt header) that 24h is
 * insufficient for the same muscle group. Applied only at Low tier; Moderate/High isolation tiers
 * already meet or exceed 48h, so the surcharge would be redundant there.
 */
private const val MUSCLE_MASS_PENALTY_HOURS = 12

/**
 * Added to the hamstring curl (`KneeFlexion`) window when the lift trains the hamstrings in a
 * lengthened position (seated curl: hip flexed → hamstrings elongated → more muscle damage), and
 * subtracted when the position is shortened (lying curl: hip neutral → less damage). Per the
 * recovery spec table: "If you use the lying leg curl, you can shave about 12 hours off those
 * hamstring numbers." Applied at every tier for `Hamstring` + `Lengthened`/`Shortened` bias.
 */
private const val LENGTHENED_STATE_PENALTY_HOURS = 12

data class MuscleSessionStress(
    val fractionalSets: Double,
    val stressClass: LiftStressClass,
    /** Muscle receiving the dose — drives the muscle-mass penalty for isolation lifts on large
     *  compound muscles (Quad/Hamstring/Glute/Chest/Back). `null` only at legacy call sites that
     *  have not been migrated; those keep the pre-penalty baseline. */
    val muscle: String? = null,
    /** Length bias of the contributing lift (`Shortened`/`Lengthened`/`MidRange`). Drives the
     *  lengthened-state surcharge on `KneeFlexion` (seated curl vs lying curl). `null` when the
     *  contributor's length bias is unknown; treated as no surcharge. */
    val muscleLengthBias: String? = null,
    /**
     * Credits from lifts where this muscle is a **primary** mover. Defaults to [fractionalSets]
     * (legacy call sites treat the whole dose as primary).
     *
     * When primary credits are below [MUSCLE_RECOVERY_MIN_SCHEDULING_DOSE], the session is
     * **secondary-only** for this muscle (e.g. Glute from squat/RDL). Secondary hitchhiking does
     * not receive the large-muscle Isolation mass surcharge and does not hard-forbid adjacent
     * repeats — primary isolation on large muscles (Leg Extension → Quad) still does.
     */
    val primaryFractionalSets: Double = fractionalSets,
) {
    /** True when total dose may be meaningful but primary stimulus is not. */
    val isSecondaryOnly: Boolean
        get() = primaryFractionalSets < MUSCLE_RECOVERY_MIN_SCHEDULING_DOSE
}

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
 *
 * @param muscle the muscle receiving the dose; when `null` the muscle-mass penalty is skipped
 *  (legacy call sites that have not been migrated keep the pre-penalty baseline).
 * @param muscleLengthBias length bias of the contributing lift; drives the lengthened-state
 *  surcharge on `KneeFlexion`. `null` is treated as no surcharge.
 */
fun recoveryHours(
    earlierDose: Double,
    stressClass: LiftStressClass,
    minRealizedRir: Int? = null,
    muscle: String? = null,
    muscleLengthBias: String? = null,
    secondaryOnly: Boolean = false,
): Int {
    val tier = volumeTier(earlierDose) ?: return 0
    val base = calibratedRecoveryHours(stressClass, tier, muscle, muscleLengthBias, secondaryOnly)
    val failure = if (minRealizedRir == 0) RECOVERY_FAILURE_HOURS else 0
    return base + failure
}

/** Minimum day-index gap after [earlier] before the muscle is meaningfully loaded again. */
fun minimumRecoveryCalendarGap(earlier: MuscleSessionStress, minRealizedRir: Int? = null): Int {
    val hours = recoveryHours(
        earlierDose = earlier.fractionalSets,
        stressClass = earlier.stressClass,
        minRealizedRir = minRealizedRir,
        muscle = earlier.muscle,
        muscleLengthBias = earlier.muscleLengthBias,
        secondaryOnly = earlier.isSecondaryOnly,
    )
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

/** Literature matrix contract — table wins over additive constant sums when they diverge.
 *
 *  Two muscle-aware surcharges are layered on the isolation tier:
 *  - **Muscle-mass penalty** ([MUSCLE_MASS_PENALTY_HOURS]): added at the Low tier when a
 *    **primary** isolation lift trains a [LARGE_COMPOUND_MUSCLES] (e.g. Leg Extension on Quad).
 *    Lifts the Low window from 24h to 36h so the calendar gap becomes 2 (adjacent-day forbid).
 *    **Secondary-only** hitchhiking (Glute from squat/RDL) does **not** get this surcharge —
 *    incidental compound secondaries stay on the 24h Isolation Low lane. Moderate/High isolation
 *    tiers already meet ≥48h, so the surcharge is skipped there either way.
 *  - **Lengthened-state penalty** ([LENGTHENED_STATE_PENALTY_HOURS]): added at every tier for
 *    `KneeFlexion` lifts whose [muscleLengthBias] is `Lengthened` (seated curl position),
 *    reflecting the greater muscle damage from training the hamstrings at longer lengths. Lying
 *    curls (`Shortened`) omit this surcharge. */
private fun calibratedRecoveryHours(
    stressClass: LiftStressClass,
    tier: RecoveryVolumeTier,
    muscle: String? = null,
    muscleLengthBias: String? = null,
    secondaryOnly: Boolean = false,
): Int {
    val base = when (stressClass) {
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
    if (stressClass != LiftStressClass.Isolation) return base
    var adjusted = base
    val lowRecoveryPrimary = (tier == RecoveryVolumeTier.Low && !secondaryOnly)
    val largeCompoundMuscle = (muscle != null && muscle in LARGE_COMPOUND_MUSCLES)
    // Muscle-mass penalty: primary isolation Low on large compounds only (not secondary hitchhiking).
    if (lowRecoveryPrimary && largeCompoundMuscle) {
        adjusted += MUSCLE_MASS_PENALTY_HOURS
    }
    // Hamstring curl length-bias adjustments (KneeFlexion):
    // - Lengthened (seated curl): +12h at every tier — greater muscle damage at longer lengths.
    // - Shortened (lying curl): −12h at every tier — shaves the lengthened surcharge off, per the
    //   recovery spec table ("lying leg curl shaves about 12 hours off the hamstring numbers").
    //   Net for lying Low = 24 + 12 (mass) − 12 (shortened) = 24h (bottom of the 24–36h band).
    if (muscle == "Hamstring") {
        when (muscleLengthBias) {
            "Lengthened" -> adjusted += LENGTHENED_STATE_PENALTY_HOURS
            "Shortened" -> adjusted -= LENGTHENED_STATE_PENALTY_HOURS
        }
    }
    return adjusted.coerceAtLeast(0)
}
