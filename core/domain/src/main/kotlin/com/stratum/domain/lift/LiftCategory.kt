package com.stratum.domain.lift

import com.stratum.domain.onboarding.MuscleGroup

/**
 * The lower-body [MuscleGroup]s — the canonical set used to decide whether a lift is a lower-
 * or upper-body movement. Lifted out of `:core:ui`'s `BodyCrop` so domain classifiers
 * ([LiftCategory], similarity) and the body diagram agree on a single source of truth.
 *
 * `Abductor` / `Adductor` are volume-only contributors and are intentionally excluded — they
 * are not visually represented on the body diagram and a lift should not be reclassified as
 * "lower body" purely because it tracks abductor volume (e.g. a sumo-stance compound).
 */
val LOWER_BODY_MUSCLE_GROUPS: Set<MuscleGroup> =
    setOf(
        MuscleGroup.Quad,
        MuscleGroup.Hamstring,
        MuscleGroup.Glute,
        MuscleGroup.Calf,
        MuscleGroup.Abductor,
        MuscleGroup.Adductor,
    )

/**
 * A coarse, user-facing classification of a [Lift] used by the swap UI's "Lift Type" filter.
 *
 * Five buckets that match how lifters actually think about exercise taxonomy:
 * - [CompoundUpper] / [CompoundLower]: multi-joint upper- or lower-body movements
 *   (bench press, row, squat, deadlift).
 * - [IsolationUpper] / [IsolationLower]: single-joint movements (bicep curl, leg curl).
 * - [Core]: spinal-flexion / ab / lower-back focused work.
 *
 * Derived deterministically from a [Lift] via [classifyLiftCategory] — no new catalog column,
 * no schema change (AGENTS.md §4.5). The upper/lower split reuses [LOWER_BODY_MUSCLE_GROUPS].
 */
enum class LiftCategory {
    CompoundUpper,
    CompoundLower,
    IsolationUpper,
    IsolationLower,
    Core,
    ;

    /** Display label for filter chips — the single source of truth for UI copy. */
    val displayName: String
        get() = when (this) {
            CompoundUpper -> "Compound Upper"
            CompoundLower -> "Compound Lower"
            IsolationUpper -> "Upper Isolation"
            IsolationLower -> "Lower Isolation"
            Core -> "Core"
        }
}

/**
 * Classify a [Lift] into the swap filter's [LiftCategory]. Rules, in priority order:
 *
 * 1. **Core** — [MovementPattern.SpinalFlexion] is present, or the lift's primary muscles are
 *    entirely within `{Ab, LowerBack}` and it is not compound. (A compound like a heavy squat
 *    that lists `LowerBack` as a secondary still classifies by its primary movers.)
 * 2. **Compound vs Isolation** — [Lift.isCompound] (`movementPatterns.size > 1`).
 * 3. **Upper vs Lower** — any primary muscle in [LOWER_BODY_MUSCLE_GROUPS] → Lower; else Upper.
 *
 * Pure function — safe to call from any thread, no framework deps.
 */
fun Lift.classifyLiftCategory(): LiftCategory {
    val isCorePattern = MovementPattern.SpinalFlexion in movementPatterns
    val primaryMuscles = primaryMuscles
    val isCoreOnly =
        primaryMuscles.isNotEmpty() &&
            primaryMuscles.all { it == MuscleGroup.Ab || it == MuscleGroup.LowerBack }
    if ((isCorePattern || isCoreOnly) && !isCompound) return LiftCategory.Core

    val isLower = primaryMuscles.any { it in LOWER_BODY_MUSCLE_GROUPS }
    return when {
        isCompound && isLower -> LiftCategory.CompoundLower
        isCompound -> LiftCategory.CompoundUpper
        isLower -> LiftCategory.IsolationLower
        else -> LiftCategory.IsolationUpper
    }
}
