@file:Suppress("MagicNumber", "ReturnCount", "LoopWithTooManyJumpStatements", "MatchingDeclarationName", "ComplexCondition", "MaxLineLength")

package com.stratum.domain.program.generation.prescription

import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.program.generation.context.LiftContext
import com.stratum.domain.program.generation.reference.RP_REP_RIR_BANDS
import com.stratum.domain.program.generation.reference.RpRepProfile
import com.stratum.domain.program.generation.reference.RpRepRirBand
import com.stratum.domain.program.generation.split.WorkoutSlotTemplate
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
    fatigueSlope: Double? = null,
    setCount: Int = 0,
): RepRirPlan {
    val profile = classifyLiftRepProfile(slot, lift)
    val band = RP_REP_RIR_BANDS.getValue(profile)
    val (repMin, repMax) = repRangeFromFatigue(fatigueSlope, setCount, band)
    return RepRirPlan(
        repMin = repMin,
        repMax = repMax,
        topSetRir = band.topSetRir,
        backOffRir = kotlin.math.round((band.backOffRirMin + band.backOffRirMax) / 2.0).toInt(),
    )
}

/**
 * Derives a hypertrophic top-set rep range from the lifter's measured per-lift fatigue decay
 * slope, falling back to [fallback] (the static RP band) when no slope is available.
 *
 * Personalized-wins policy (AGENTS design): when a slope exists, the spread is driven by the
 * slope × set count, with the floor pinned at the hypertrophic floor (8). The static band's
 * spread is only used when no slope exists.
 *
 * Model: across `setCount` sets, expected reps decay by `slope * (setCount - 1)` from the
 * first set to the last. We pin the last set at the hypertrophic floor (8) and back-solve the
 * first-set target: `repMax = 8 + |slope| * (setCount - 1)`. A steep slope (fast fatigue, e.g.
 * -3.0) yields a wider range so the lifter can start high and still hit 8 on the last set; a
 * shallow slope (e.g. -1.0) yields a tight range. This is the increment/load principle: a
 * single increment shifts intensity more on a light isolation lift (fast decay) than on a
 * heavy compound (slow decay), so the former carries a wider practical range.
 *
 * Guards:
 * - Floor is always ≥ 8 (enforced).
 * - Ceiling is capped at [REP_RANGE_CEILING_CAP] to avoid runaway spreads from extreme slopes.
 * - `setCount <= 1` or `slope == null` → pure fallback to the static band.
 */
fun repRangeFromFatigue(
    fatigueSlope: Double?,
    setCount: Int,
    fallback: RpRepRirBand,
): Pair<Int, Int> {
    if (fatigueSlope == null || setCount <= 1) {
        return fallback.repMin to fallback.repMax
    }
    val repMin = HYPERTROPHIC_FLOOR
    val spread = (kotlin.math.abs(fatigueSlope) * (setCount - 1)).roundToInt()
    val repMax = (repMin + spread).coerceAtMost(REP_RANGE_CEILING_CAP)
    return repMin to repMax
}

/** Hypertrophic floor — below this many reps the set drifts into strength territory. */
const val HYPERTROPHIC_FLOOR: Int = 8

/** Caps the slope-derived ceiling so extreme slopes can't produce absurd rep ranges. */
const val REP_RANGE_CEILING_CAP: Int = 30
