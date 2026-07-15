package com.stratum.domain.lift

import com.stratum.domain.onboarding.MuscleGroup

/**
 * Coarse body-region for [classifyLiftCategory] resolution. Every [MovementPattern] maps to
 * exactly one region via [MovementPattern.categoryRegion] (total `when` — new patterns fail compile).
 */
enum class LiftCategoryRegion {
    Lower,
    Upper,
    Core,
}

/**
 * Region a [MovementPattern] implies for Gym / swap "Lift Type" classification.
 *
 * Orthogonal to [KinematicProfile]: a vertical pull can be compound or isolation-tagged wrongly;
 * region still says upper.
 */
val MovementPattern.categoryRegion: LiftCategoryRegion
    get() =
        when (this) {
            MovementPattern.SquatPattern,
            MovementPattern.HipHinge,
            MovementPattern.KneeExtension,
            MovementPattern.KneeFlexion,
            MovementPattern.PlantarFlexion,
            MovementPattern.HipExtension,
            MovementPattern.HipAbduction,
            MovementPattern.HipAdduction,
            -> LiftCategoryRegion.Lower

            MovementPattern.SpinalFlexion -> LiftCategoryRegion.Core

            MovementPattern.HorizontalPress,
            MovementPattern.InclinePress,
            MovementPattern.VerticalPress,
            MovementPattern.HorizontalPull,
            MovementPattern.VerticalPull,
            MovementPattern.ShoulderAdduction,
            MovementPattern.ShoulderAbduction,
            MovementPattern.ShoulderHorizontalAbduction,
            MovementPattern.ShoulderFlexion,
            MovementPattern.ElbowFlexion,
            MovementPattern.ElbowExtension,
            MovementPattern.WristFlexionExtension,
            MovementPattern.ScapularElevation,
            -> LiftCategoryRegion.Upper
        }

/**
 * Lower-body [MuscleGroup]s used for region classification and dashboard lower/upper splits.
 *
 * Includes [MuscleGroup.Abductor] / [MuscleGroup.Adductor] for volume and category region —
 * they are volume-tracked lower contributors. They are excluded only from the body-diagram
 * specialization picker (UI), not from this set.
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
 * Primary muscles that mark pure core work when the lift is isolation (not compound).
 * [MuscleGroup.LowerBack] alone does **not** force Core on compounds (deadlift, rack pull).
 */
val CORE_MUSCLE_GROUPS: Set<MuscleGroup> =
    setOf(
        MuscleGroup.Ab,
        MuscleGroup.LowerBack,
    )

/**
 * Multi-joint movement families for program generation (stress, RIR clamping, volume coeffs)
 * **and** for category-only compound hardening ([isCompoundForCategory]).
 *
 * Single source of truth — do not duplicate this set elsewhere.
 *
 * Does not include [MovementPattern.HipExtension] alone — glute bridge / reverse hyper can
 * legitimately be isolation. Does not change [Lift.isCompound] / default rest.
 */
val MAJOR_COMPOUND_PATTERNS: Set<MovementPattern> =
    setOf(
        MovementPattern.SquatPattern,
        MovementPattern.HipHinge,
        MovementPattern.HorizontalPress,
        MovementPattern.InclinePress,
        MovementPattern.VerticalPress,
        MovementPattern.HorizontalPull,
        MovementPattern.VerticalPull,
    )

/**
 * A coarse, user-facing classification of a [Lift] used by the swap UI's "Lift Type" filter.
 *
 * Five buckets that match how lifters actually think about exercise taxonomy:
 * - [CompoundUpper] / [CompoundLower]: multi-joint upper- or lower-body movements
 *   (bench press, row, squat, deadlift).
 * - [IsolationUpper] / [IsolationLower]: single-joint movements (bicep curl, leg curl).
 * - [Core]: spinal-flexion / ab / lower-back focused isolation work.
 *
 * Derived deterministically from a [Lift] via [classifyLiftCategory] — no new catalog column,
 * no schema change (AGENTS.md §4.5).
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
        get() =
            when (this) {
                CompoundUpper -> "Compound Upper"
                CompoundLower -> "Compound Lower"
                IsolationUpper -> "Upper Isolation"
                IsolationLower -> "Lower Isolation"
                Core -> "Core"
            }
}

/**
 * Classify a [Lift] into the swap filter's [LiftCategory].
 *
 * Rules, in order:
 *
 * 1. **Compound?** — [isCompoundForCategory]: multi-pattern, axial/non-axial kinematic, **or**
 *    any [MAJOR_COMPOUND_PATTERNS] (category-only hardening; does not change rest).
 * 2. **Core** — isolation only: any core-region pattern ([MovementPattern.SpinalFlexion]) **or**
 *    non-empty primaries entirely within [CORE_MUSCLE_GROUPS], and not compound. Compounds with
 *    LowerBack primary (deadlift) never land in Core.
 * 3. **Body region** — [resolveBodyRegion]: primary lower muscles → lower patterns → non-core
 *    primary → upper patterns → secondary lower → default Upper.
 * 4. **Bucket** — compound×region / isolation×region.
 *
 * Pure function — safe to call from any thread, no framework deps.
 */
fun Lift.classifyLiftCategory(): LiftCategory {
    val compound = isCompoundForCategory()
    val coreByPattern =
        movementPatterns.any { it.categoryRegion == LiftCategoryRegion.Core }
    val coreByMuscle =
        primaryMuscles.isNotEmpty() &&
            primaryMuscles.all { it in CORE_MUSCLE_GROUPS }
    if ((coreByPattern || coreByMuscle) && !compound) {
        return LiftCategory.Core
    }

    val lower = resolveBodyRegion() == LiftBodyRegion.Lower
    return when {
        compound && lower -> LiftCategory.CompoundLower
        compound -> LiftCategory.CompoundUpper
        lower -> LiftCategory.IsolationLower
        else -> LiftCategory.IsolationUpper
    }
}

/**
 * Whether this lift should appear under Compound Upper/Lower in the Gym / swap filters.
 *
 * **Dual semantics (intentional):**
 * - [Lift.isCompound] drives default rest and programming UI that must match logged rest.
 * - [isCompoundForCategory] is broader: also treats [MAJOR_COMPOUND_PATTERNS] as compound so a
 *   mis-tagged Isolation kinematic on a press/pull/squat/hinge still filters as compound.
 *
 * A custom lift can therefore be **filter-compound** (appears under Compound Upper/Lower) while
 * still resolving **isolation rest** (120s) when kinematic was left at Isolation. Prefer fixing
 * kinematic on create when patterns are major compounds; the filter remains forgiving.
 *
 * Does **not** change [Lift.resolvedDefaultRestSeconds].
 */
fun Lift.isCompoundForCategory(): Boolean =
    isCompound ||
        movementPatterns.any { it in MAJOR_COMPOUND_PATTERNS }

/**
 * Lower vs Upper for category after Core has been short-circuited in [classifyLiftCategory].
 * Never returns Core — core-only pattern region is handled before this is consulted; a Core
 * pattern on a compound falls through to Upper/Lower via other signals.
 */
enum class LiftBodyRegion {
    Lower,
    Upper,
}

/**
 * Body region for upper/lower category buckets (not Core).
 *
 * Priority (first match):
 * 1. Primary ∈ [LOWER_BODY_MUSCLE_GROUPS]
 * 2. Any lower [MovementPattern.categoryRegion]
 * 3. Any primary outside [CORE_MUSCLE_GROUPS] → Upper (non-core primary forces Upper)
 * 4. Any upper pattern → Upper
 * 5. Secondary ∈ lower set → Lower
 * 6. Default Upper
 */
internal fun Lift.resolveBodyRegion(): LiftBodyRegion =
    when {
        primaryMuscles.any { it in LOWER_BODY_MUSCLE_GROUPS } -> LiftBodyRegion.Lower
        movementPatterns.any { it.categoryRegion == LiftCategoryRegion.Lower } ->
            LiftBodyRegion.Lower
        // Non-core primary (chest, back, …) forces Upper before secondary lower signals.
        primaryMuscles.any { it !in CORE_MUSCLE_GROUPS } -> LiftBodyRegion.Upper
        movementPatterns.any { it.categoryRegion == LiftCategoryRegion.Upper } ->
            LiftBodyRegion.Upper
        secondaryMuscles.any { it in LOWER_BODY_MUSCLE_GROUPS } -> LiftBodyRegion.Lower
        else -> LiftBodyRegion.Upper
    }
