package com.stratum.domain.lift

/**
 * Pure pairwise biomechanical similarity between two lifts, on a `0.0`–`1.0` scale.
 *
 * Designed for the swap sheet's "smart display" (AGENTS.md §4.6 — deterministic core): when a
 * user swaps a lift, the catalog is ranked by similarity to the lift being replaced so good
 * options surface without a search. This is **not** the program-generation quality score
 * ([com.stratum.domain.program.generation.selection.scoreLift]) — it deliberately ignores
 * provenance, recency, and effectiveness so it works without a [ProgramGenerationContext].
 *
 * The blend weights movement-pattern overlap highest (a squat and a front squat are close even
 * if their muscle tags differ slightly), then primary-muscle overlap, then secondary-muscle
 * overlap, with small flat bonuses for matching [KinematicProfile], [MuscleLengthBias], and
 * [LiftCategory] (load class, length-tension profile, and coarse taxonomy).
 *
 * Symmetric: `liftSimilarity(a, b) == liftSimilarity(b, a)`. Pure function — no framework deps.
 */
fun liftSimilarity(target: Lift, candidate: Lift): Double {
    if (target == candidate) return IDENTICAL_LIFT_SCORE

    val patternScore = jaccard(target.movementPatterns, candidate.movementPatterns)
    val primaryScore = jaccard(target.primaryMuscles, candidate.primaryMuscles)
    val secondaryScore = jaccard(target.secondaryMuscles, candidate.secondaryMuscles)

    val weighted =
        patternScore * WEIGHT_PATTERN +
            primaryScore * WEIGHT_PRIMARY +
            secondaryScore * WEIGHT_SECONDARY

    val bonus =
        if (target.kinematicProfile == candidate.kinematicProfile) BONUS_KINEMATIC else 0.0
    val lengthBonus =
        if (target.muscleLengthBias == candidate.muscleLengthBias) BONUS_LENGTH_BIAS else 0.0
    val categoryBonus =
        if (target.classifyLiftCategory() == candidate.classifyLiftCategory()) BONUS_CATEGORY else 0.0

    return (weighted + bonus + lengthBonus + categoryBonus).coerceIn(0.0, 1.0)
}

/**
 * Jaccard overlap `|a ∩ b| / |a ∪ b|` of two sets. Returns `0.0` when both sets are empty —
 * two lifts with no tagged patterns are *not* considered identical for similarity purposes
 * (the absence of metadata is not signal).
 */
private fun <T> jaccard(a: Set<T>, b: Set<T>): Double {
    val union = a + b
    if (union.isEmpty()) return 0.0
    val intersection = a.intersect(b).size.toDouble()
    return intersection / union.size.toDouble()
}

private const val IDENTICAL_LIFT_SCORE = 1.0

// Blend weights — sum to 1.0 over the Jaccard terms.
private const val WEIGHT_PATTERN = 0.5
private const val WEIGHT_PRIMARY = 0.35
private const val WEIGHT_SECONDARY = 0.15

// Flat bonuses stacked on top of the Jaccard blend (total can exceed 1.0 before the clamp).
private const val BONUS_KINEMATIC = 0.1
private const val BONUS_LENGTH_BIAS = 0.05
private const val BONUS_CATEGORY = 0.1
