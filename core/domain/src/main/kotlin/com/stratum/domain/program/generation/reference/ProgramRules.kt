@file:Suppress("MagicNumber", "ReturnCount", "LoopWithTooManyJumpStatements", "MatchingDeclarationName", "ComplexCondition", "MaxLineLength")

package com.stratum.domain.program.generation.reference

/** Movement patterns classified as major compound for back-off RIR clamping (§2.3). */
val MAJOR_COMPOUND_PATTERNS: Set<String> = setOf(
    "SquatPattern",
    "HipHinge",
    "HorizontalPress",
    "VerticalPress",
    "InclinePress",
    "HorizontalPull",
    "VerticalPull",
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
