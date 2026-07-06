@file:Suppress("MagicNumber", "ReturnCount", "LoopWithTooManyJumpStatements", "MatchingDeclarationName", "ComplexCondition", "MaxLineLength")

package com.stratum.domain.program.generation.reference

import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.onboarding.LastTrainedBucket
import com.stratum.domain.onboarding.TrainingAge

/** Movement patterns classified as major compound for back-off RIR clamping (§2.3). */
val MAJOR_COMPOUND_PATTERNS: Set<MovementPattern> = setOf(
    MovementPattern.SquatPattern,
    MovementPattern.HipHinge,
    MovementPattern.HorizontalPress,
    MovementPattern.VerticalPress,
    MovementPattern.InclinePress,
    MovementPattern.HorizontalPull,
    MovementPattern.VerticalPull,
)

/** Top-set RIR goal on a Double-progression lift (fixed; §2.3). */
const val TOP_SET_RIR = 2

enum class ProgressionScheme {
    Linear,
    Double,
}

/**
 * Assigns the progression scheme from training age. `Beginner -> Linear`, everything else `Double`.
 * This is the SOLE authority for `progressionScheme` — the model never authors it.
 */
fun schemeForTrainingAge(trainingAge: String): ProgressionScheme =
    if (trainingAge == "Beginner") ProgressionScheme.Linear else ProgressionScheme.Double

/**
 * NEW (D8): consults `lastTrained` + `isCompound` alongside `trainingAge`. Returns
 * `Linear` iff `isCompound && (trainingAge == Beginner || lastTrained == MoreThanThreeMonthsAgo)`;
 * else `Double`. Returns the domain [com.stratum.domain.program.ProgressionScheme].
 */
fun schemeForTrainingAge(
    trainingAge: TrainingAge?,
    lastTrained: LastTrainedBucket?,
    isCompound: Boolean,
): com.stratum.domain.program.ProgressionScheme {
    // Isolation lifts never run Linear (D8 Rejected e/f).
    if (!isCompound) return com.stratum.domain.program.ProgressionScheme.Double
    // Beginner-gains OR returning from a >3 month layoff → Linear on compounds.
    if (trainingAge == TrainingAge.Beginner) return com.stratum.domain.program.ProgressionScheme.Linear
    if (lastTrained == LastTrainedBucket.MoreThanThreeMonthsAgo) return com.stratum.domain.program.ProgressionScheme.Linear
    return com.stratum.domain.program.ProgressionScheme.Double
}

/**
 * Clamps a proposed back-off RIR to the §2.3 lift-class table: major compounds 1–2 (default 2 when
 * out of range); isolation / non-major compounds floor at 1 RIR (no failure back-offs).
 */
fun clampBackOffRir(isMajorCompound: Boolean, proposed: Int): Int {
    if (!isMajorCompound) {
        return proposed.coerceAtLeast(1)
    }
    // Major compound: 1..2; default 2 when the proposal is outside the range.
    if (proposed in 1..2) {
        return proposed
    }
    return 2
}

/**
 * Maximum distinct exercises per session for a day theme. Matches commercial 4-day upper/lower
 * templates (≈5 lower, ≈7–8 upper) rather than filling every advisory movement pattern.
 */
fun sessionExerciseCapForTheme(theme: String): Int {
    val t = theme.lowercase()
    return when {
        "lower" in t || "legs" in t -> 5
        "upper" in t || "push" in t || "pull" in t -> 8
        "arms" in t -> 6
        else -> 6
    }
}
