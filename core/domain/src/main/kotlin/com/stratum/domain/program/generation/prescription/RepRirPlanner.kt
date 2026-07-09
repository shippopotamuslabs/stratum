@file:Suppress("MagicNumber", "ReturnCount", "LoopWithTooManyJumpStatements", "MatchingDeclarationName", "ComplexCondition", "MaxLineLength")

package com.stratum.domain.program.generation.prescription

import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.incrementForAmount
import com.stratum.domain.program.generation.context.LiftContext
import com.stratum.domain.program.generation.reference.RP_REP_RIR_BANDS
import com.stratum.domain.program.generation.reference.RpRepProfile
import com.stratum.domain.program.generation.reference.RpRepRirBand
import com.stratum.domain.program.generation.split.WorkoutSlotTemplate
import com.stratum.domain.training.EstimatedOneRm
import kotlin.math.abs
import kotlin.math.round
import kotlin.math.roundToInt

private val LOWER_FREE_WEIGHT_PATTERNS = setOf(MovementPattern.SquatPattern, MovementPattern.HipHinge)

fun classifyLiftRepProfile(
    slot: WorkoutSlotTemplate,
    lift: LiftContext,
): RpRepProfile {
    if (slot.repProfile != RpRepProfile.Isolation && slot.repProfile != RpRepProfile.UpperMachineOrPull) {
        if (
            lift.movementPattern in LOWER_FREE_WEIGHT_PATTERNS &&
            isLowerFreeWeightName(lift.name)
        ) {
            return RpRepProfile.LowerFreeWeightCompound
        }
        return slot.repProfile
    }
    if (slot.repProfile == RpRepProfile.Isolation) return RpRepProfile.Isolation
    return RpRepProfile.UpperMachineOrPull
}

private fun isLowerFreeWeightName(name: String): Boolean {
    val n = name.lowercase()
    if (Regex("""\b(machine|smith)\b""").containsMatchIn(n)) return false
    return Regex("""\b(barbell|squat|deadlift|rdl|romanian)\b""").containsMatchIn(n) ||
        Regex("""\bsquat\b""").containsMatchIn(n)
}

data class RepRirPlan(
    val repMin: Int,
    val repMax: Int,
    val topSetRir: Int,
    val backOffRir: Int,
)

fun repRirForSlot(
    slot: WorkoutSlotTemplate,
    lift: LiftContext,
    setCount: Int = 0,
): RepRirPlan {
    val profile = classifyLiftRepProfile(slot, lift)
    val band = RP_REP_RIR_BANDS.getValue(profile)
    val incrementWidth = repWidthFromIncrement(lift.estimatedOneRm, lift.incrementTiers, band)
    val (repMin, repMax) = repRangeFromFatigue(lift.fatigueDecaySlope, setCount, incrementWidth, band)
    return RepRirPlan(
        repMin = repMin,
        repMax = repMax,
        topSetRir = band.topSetRir,
        backOffRir = kotlin.math.round((band.backOffRirMin + band.backOffRirMax) / 2.0).toInt(),
    )
}

/**
 * Derives a hypertrophic top-set rep range by splitting the two questions a rep band answers:
 *
 * 1. **Floor (from fatigue):** "If you can only do the bottom of the rep range on the top set,
 *    will you still hit at least [LAST_SET_FLOOR] reps on the last set?" The within-session
 *    fatigue drop is `|slope| × (setCount − 1)`; the floor rises by that drop so the last set
 *    stays above [LAST_SET_FLOOR]. A steep slope (fast fatigue) raises the floor; a shallow
 *    slope leaves it at the [HYPERTROPHIC_FLOOR].
 * 2. **Width (from [incrementWidth]):** "How many reps of headroom before a single load
 *    increment pushes you off the bottom of the range?" Computed upstream by
 *    [repWidthFromIncrement] from the increment:typical-load ratio. The ceiling is the floor
 *    plus this width — the ceiling rises with the floor, never squeezed toward it.
 *
 * Guards:
 * - Floor is always ≥ [HYPERTROPHIC_FLOOR] (enforced).
 * - `slope == null` or `setCount <= 1` → floor is the [HYPERTROPHIC_FLOOR] (no fatigue signal).
 * - Ceiling is capped at [REP_RANGE_CEILING_CAP].
 * - Pure fallback (no slope AND no setCount) → the static [fallback] band.
 */
fun repRangeFromFatigue(
    fatigueSlope: Double?,
    setCount: Int,
    incrementWidth: Int,
    fallback: RpRepRirBand,
): Pair<Int, Int> {
    if (fatigueSlope == null && setCount <= 1) {
        return fallback.repMin to fallback.repMax
    }
    val fatigueDrop = if (fatigueSlope == null || setCount <= 1) {
        0
    } else {
        round(abs(fatigueSlope) * (setCount - 1)).toInt()
    }
    val repMin = maxOf(HYPERTROPHIC_FLOOR, LAST_SET_FLOOR + fatigueDrop)
    val repMax = (repMin + incrementWidth).coerceAtMost(REP_RANGE_CEILING_CAP)
    return repMin to repMax
}

/**
 * Derives the rep-range *width* (ceiling minus floor) from the increment:typical-load ratio.
 * A single load increment shifts intensity by `Δreps = continuousRepsForWeight(e1rm, w) −
 * continuousRepsForWeight(e1rm, w + inc)`; the band is wide enough to absorb
 * [INCREMENT_HEADROOM_STEPS] increments of progression before the lifter falls off the floor.
 *
 * The rep delta is computed in *continuous* (non-integer) reps because [EstimatedOneRm.repsForWeight]
 * rounds to the nearest integer, which collapses sub-rep resolution — exactly where the
 * increment-ratio signal lives (a 5 lb increment on a 140 lb lift shifts intensity ~1.3 reps,
 * which integer-rounds to 1 and saturates every lift to the minimum width). The continuous
 * formula is the inverse of the Brzycki load equation:
 * `reps = BASE × (1 − w/e1rm) / FACTOR` for `w/e1rm ≥ EXP_BASE` (the in-band region the
 * typical top-set weight and one increment above it both fall into).
 *
 * A heavy barbell where 5 lb is a small fraction of the load has a small Δreps → narrow band.
 * A light isolation lift where one increment jumps intensity a lot has a large Δreps → wider
 * band. This realizes the increment/load principle documented on [RP_REP_RIR_BANDS].
 *
 * Falls back to the static band's spread when either signal is missing (no e1RM from history,
 * or no increment tiers on the lift), so generated programs degrade gracefully for lifts with
 * no proven training data.
 */
fun repWidthFromIncrement(
    estimatedOneRm: Weight?,
    incrementTiers: List<com.stratum.domain.lift.IncrementTier>,
    fallback: RpRepRirBand,
): Int {
    val fallbackWidth = fallback.repMax - fallback.repMin
    if (estimatedOneRm == null || incrementTiers.isEmpty()) return fallbackWidth
    val e1rm = estimatedOneRm.amount
    if (e1rm <= 0.0) return fallbackWidth
    // Typical top-set weight ≈ the load for 10 reps at 0 RIR (mid-band anchor).
    val typicalWeight = EstimatedOneRm.weightForReps(e1rm, TYPICAL_LOAD_ANCHOR_REPS)
    val increment = incrementTiers.incrementForAmount(typicalWeight)
    if (increment <= 0.0) return fallbackWidth
    val repsAtTypical = continuousRepsForWeight(e1rm, typicalWeight)
    val repsAfterIncrement = continuousRepsForWeight(e1rm, typicalWeight + increment)
    val repsPerIncrement = (repsAtTypical - repsAfterIncrement).coerceAtLeast(MIN_REPS_PER_INCREMENT)
    val width = (INCREMENT_HEADROOM_STEPS * repsPerIncrement).roundToInt()
    return width.coerceIn(MIN_INCREMENT_WIDTH, MAX_INCREMENT_WIDTH)
}

/**
 * Continuous (non-integer) inverse of the Brzycki load equation: how many reps the lifter is
 * good for at [weight] against [e1rm] at 0 RIR. Mirrors [EstimatedOneRm.repsForWeight] but
 * skips the integer rounding so sub-rep resolution is preserved — needed by
 * [repWidthFromIncrement] because the per-increment rep shift is often fractional (e.g. 1.3
 * reps) and integer rounding would collapse the increment-ratio signal to 1 for every lift.
 *
 * For `w/e1rm ≥ EXP_BASE` (the in-band region the typical top-set weight and one increment
 * above it fall into), this is `BASE × (1 − w/e1rm) / FACTOR`. Above the splice point it falls
 * back to the exponential branch (same as [EstimatedOneRm.repsForWeight]) so the helper is
 * correct across the whole curve, not just the Brzycki region.
 */
private fun continuousRepsForWeight(e1rm: Double, weight: Double): Double {
    val targetRatio = weight / e1rm
    return if (targetRatio >= EXP_BASE) {
        BRZYCKI_DENOMINATOR_BASE * (1.0 - targetRatio) / BRZYCKI_DENOMINATOR_FACTOR
    } else {
        BRZYCKI_REP_THRESHOLD - kotlin.math.ln(targetRatio / EXP_BASE) / DECAY_RATE
    }
}

/** Hypertrophic floor — below this many reps the set drifts into strength territory. */
const val HYPERTROPHIC_FLOOR: Int = 8

/** Absolute last-set floor — fatigue raises the band floor so the last set stays ≥ this. */
const val LAST_SET_FLOOR: Int = 5

/** Rep count used as the typical-load anchor (weight for this many reps at 0 RIR). */
const val TYPICAL_LOAD_ANCHOR_REPS: Int = 10

/** Number of load increments of progression headroom the band width must absorb. */
const val INCREMENT_HEADROOM_STEPS: Int = 2

/** Minimum/maximum rep-range width (ceiling − floor) from the increment ratio. */
const val MIN_INCREMENT_WIDTH: Int = 4
const val MAX_INCREMENT_WIDTH: Int = 10

/**
 * Floors the per-increment rep shift so a tiny increment on a heavy lift doesn't produce a
 * sub-1 width. Below this the static band spread is the better signal.
 */
private const val MIN_REPS_PER_INCREMENT: Double = 0.5

/** Brzycki constants — mirrored from [EstimatedOneRm] for [continuousRepsForWeight]. */
private const val BRZYCKI_REP_THRESHOLD = 10
private const val BRZYCKI_DENOMINATOR_BASE = 1.0278
private const val BRZYCKI_DENOMINATOR_FACTOR = 0.0278
private const val DECAY_RATE = 0.0278 / 0.75
private const val EXP_BASE = 0.75 / 1.0278

/** Caps the slope-derived ceiling so extreme slopes can't produce absurd rep ranges. */
const val REP_RANGE_CEILING_CAP: Int = 30
