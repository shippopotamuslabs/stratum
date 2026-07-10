package com.stratum.domain.lift

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
 * - [incrementTiers]: realistic weight jumps per load band (§2.2).
 * - [presetTags]: which [GymPreset]s bulk-enable this lift (§2.1).
 * - [equipment]: optional display string for the specific equipment/brand (e.g. "Hammer Strength",
 *   "Nautilus", "Pendulum"). Display copy only — not an entity graph (§2.1 lift-first catalog).
 *   Null on seed lifts (generic equipment is carried implicitly by the lift name); users can set
 *   it on custom lifts or override seed lifts when the brand matters to them.
 * - [enabled]: whether this user has the lift available. Persisted on the lift itself (the
 *   onboarding toggle + voice edits mutate this, not a separate availability graph).
 * - [isCustom]: user-created vs. bundled. Distinguishes the two so generation (OS-6) and voice
 *   catalog edits (OS-8) can treat user lifts with appropriate defaults.
 * - [defaultRestSeconds]: optional per-lift rest override for sets created when this lift is
 *   added or swapped into a workout. Null means use [resolvedDefaultRestSeconds] (compound vs
 *   isolation). Existing workout templates are never rewritten when this changes.
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
    val equipment: String? = null,
    val enabled: Boolean,
    val isCustom: Boolean = false,
    val description: String? = null,
    val imageUris: List<String> = emptyList(),
    val muscleLengthBias: MuscleLengthBias = MuscleLengthBias.MidRange,
    val mainstream: Boolean = false,
    val kinematicProfile: KinematicProfile = KinematicProfile.Isolation,
    val loadType: LoadType = LoadType.Weighted,
    val defaultRestSeconds: Int? = null,
) {
    val isCompound: Boolean
        get() = movementPatterns.size > 1
}

/** Default inter-set rest for compound lifts (multiple movement patterns) when no override is set. */
const val DEFAULT_COMPOUND_REST_SECONDS = 180

/** Default inter-set rest for isolation lifts when no override is set. */
const val DEFAULT_ISOLATION_REST_SECONDS = 120

/**
 * Rest seconds to use when this lift is added or swapped into a workout. Prefer the stored
 * [Lift.defaultRestSeconds] override; otherwise compound → 3 min, isolation → 2 min.
 */
fun Lift.resolvedDefaultRestSeconds(): Int =
    defaultRestSeconds
        ?: if (isCompound) DEFAULT_COMPOUND_REST_SECONDS else DEFAULT_ISOLATION_REST_SECONDS
