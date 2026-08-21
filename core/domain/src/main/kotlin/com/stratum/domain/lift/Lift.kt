package com.stratum.domain.lift

import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup

/**
 * A lift in the catalog — the primary catalog entity (program, log, filter). A [Lift] carries
 * everything onboarding (OS-4) and generation (OS-6) need to know about a movement:
 *
 * - [primaryMuscles] / [secondaryMuscles]: which [MuscleGroup]s a set contributes volume to
 *   (v1 is presence/absence, not fractional contribution — fractional is deferred per §2.3).
 *   Up to 4 secondary muscles are supported so compound lifts can credit multiple co-movers
 *   (e.g. triceps + front delts on a press); [CustomLiftMusclePolicy] derives sensible
 *   pattern-based secondary defaults so credit stays accurate even if tags were omitted.
 * - [incrementTiers]: **effective** weight jumps per load band (§2.2) after resolving
 *   lift-scoped overrides against equipment defaults (see [resolveEffectiveIncrementTiers]).
 * - [usesEquipmentDefaultIncrements]: true when no lift-scoped override rows exist (tiers may
 *   still come from equipment or a global fallback).
 * - [inheritsFromEquipmentLattice]: true when non-empty equipment tiers supplied the effective
 *   list (false for global fallback, including empty bodyweight lattices).
 * - [presetTags]: which [GymPreset]s bulk-enable this lift (§2.1).
 * - [equipmentId] / [equipmentName]: optional link to shared [Equipment] for default
 *   increments and display. Narrow equipment catalog — not an availability graph.
 * - [enabled]: whether this user has the lift available. Persisted on the lift itself (the
 *   onboarding toggle + voice edits mutate this, not a separate availability graph).
 * - [isCustom]: user-created vs. bundled. Distinguishes the two so generation (OS-6) and voice
 *   catalog edits (OS-8) can treat user lifts with appropriate defaults.
 * - [defaultRestSeconds]: optional per-lift rest override for sets created when this lift is
 *   added or swapped into a workout. Null means use [resolvedDefaultRestSeconds] (High → 180,
 *   else 120). Existing workout templates are never rewritten when this changes.
 * - [fatigueProfile]: loading / recovery cost band (catalog-authored).
 *
 * Pure domain value — no framework deps (ARCHITECTURE.md §0). Value class `LiftId` keeps id
 * assignment type-safe; muscle sets are decoded from the stored bitmask in the data mapper.
 */
data class Lift(
    val id: LiftId,
    val name: String,
    val movementPatterns: Set<MovementPattern>,
    val primaryMuscles: Set<MuscleGroup>,
    val secondaryMuscles: Set<MuscleGroup>,
    val incrementTiers: List<IncrementTier>,
    val presetTags: Set<GymPreset>,
    val equipmentId: EquipmentId? = null,
    val equipmentName: String? = null,
    val usesEquipmentDefaultIncrements: Boolean = true,
    val inheritsFromEquipmentLattice: Boolean = false,
    val enabled: Boolean,
    val isCustom: Boolean = false,
    val description: String? = null,
    val imageUris: List<String> = emptyList(),
    val muscleLengthBias: MuscleLengthBias = MuscleLengthBias.MidRange,
    val mainstream: Boolean = false,
    val fatigueProfile: FatigueProfile = FatigueProfile.Low,
    val loadType: LoadType = LoadType.Weighted,
    val defaultRestSeconds: Int? = null,
    /**
     * User-authored note pinned to this catalog lift across all workouts and programs.
     * Distinct from [description] (seed/catalog copy).
     */
    val pinnedNote: String? = null,
) {
    /**
     * Multi-joint / Linear programming gate — **not** used for rest.
     *
     * True when the lift has **more than one** movement pattern **or** [hasCompoundFatigue]
     * (High/Medium). Prefer this over ad-hoc High|Medium checks so Linear UI, generation
     * scheme, and selection stay aligned.
     *
     * **Not** the Gym/swap filter bit — that is [isCompoundForCategory] (broader, also major
     * compound patterns). **Not** rest — rest is [resolvedDefaultRestSeconds] from
     * [fatigueProfile] only.
     */
    val isCompound: Boolean
        get() = movementPatterns.size > 1 || hasCompoundFatigue
}

/**
 * True when [Lift.fatigueProfile] is High or Medium (systemic multi-joint fatigue band).
 * Used when pattern count must not promote Low accessories into “compound fatigue” logic.
 */
val Lift.hasCompoundFatigue: Boolean
    get() =
        fatigueProfile == FatigueProfile.High ||
            fatigueProfile == FatigueProfile.Medium


/** Default inter-set rest for High-fatigue lifts when no override is set. */
const val DEFAULT_HIGH_FATIGUE_REST_SECONDS: Int = 180

/** Default inter-set rest for Medium/Low fatigue lifts when no override is set. */
const val DEFAULT_STANDARD_REST_SECONDS: Int = 120

/**
 * Rest seconds to use when this lift is added or swapped into a workout. Prefer the stored
 * [Lift.defaultRestSeconds] override; otherwise High → 180s, Medium and Low → 120s.
 * Rest is driven by [Lift.fatigueProfile] only — never by [Lift.isCompound].
 */
fun Lift.resolvedDefaultRestSeconds(): Int =
    defaultRestSeconds
        ?: if (fatigueProfile == FatigueProfile.High) {
            DEFAULT_HIGH_FATIGUE_REST_SECONDS
        } else {
            DEFAULT_STANDARD_REST_SECONDS
        }
