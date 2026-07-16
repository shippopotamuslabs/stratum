package com.stratum.domain.program.generation.slots

/**
 * Explicit base slot library for universe construction.
 *
 * Historically the universe was the deduped union of templates contributed by five
 * [SessionTypeId] catalogs (Upper, Lower, FullBody, UpperPush, UpperPull). That loop is gone;
 * this library is the same template set, grouped by movement family for readability.
 *
 * Groups are composed from [UPPER_SLOTS], [LOWER_SLOTS], [FULL_BODY_SLOTS], [UPPER_PUSH_SLOTS],
 * and [UPPER_PULL_SLOTS] — no invented slots. First-wins order matches the former
 * SOURCE_SESSION_TYPES iteration so non-key fields (length bias, rep profile, fatigue allow-lists)
 * stay faithful when templates share a [librarySlotKey].
 *
 * Logical groups (documentation / tests):
 * - [LOWER_ANCHORS]: squat, hinge (anchor), glute
 * - [LOWER_ACCESSORIES]: quad-iso (NonAxial), ham-iso (NonAxial), calf
 * - [UPPER_PRESS]: h-press, incline variants, v-press
 * - [UPPER_PULL]: v-pull / h-pull role+sibling variants
 * - [UPPER_ACCESSORIES]: lat-raise, bicep, tricep, rear-delt role variants
 * - Full-body role variants (hinge secondary, etc.) appear via the FullBody catalog union
 */
fun baseSlotLibrary(): List<WorkoutSlotTemplate> {
    val byKey = linkedMapOf<String, WorkoutSlotTemplate>()
    // Former SOURCE_SESSION_TYPES order: Upper → Lower → FullBody → UpperPush → UpperPull
    for (slot in UPPER_SLOTS + LOWER_SLOTS + FULL_BODY_SLOTS + UPPER_PUSH_SLOTS + UPPER_PULL_SLOTS) {
        val key = librarySlotKey(slot)
        if (key !in byKey) {
            byKey[key] = slot
        }
    }
    return byKey.values.map { it.copy() }
}

/** Squat / hinge anchors and glute-primary hip extension. */
val LOWER_ANCHORS: List<WorkoutSlotTemplate>
    get() = LOWER_SLOTS.filter { it.role == SlotRole.Anchor }

/** Quad/ham accessories (Low+Medium fatigue admission) and calf. */
val LOWER_ACCESSORIES: List<WorkoutSlotTemplate>
    get() = LOWER_SLOTS.filter { it.role == SlotRole.Accessory || it.role == SlotRole.Optional }

/** Horizontal / incline / vertical press family from upper + upper-push catalogs. */
val UPPER_PRESS: List<WorkoutSlotTemplate>
    get() =
        (UPPER_SLOTS + UPPER_PUSH_SLOTS).filter {
            it.pattern.name in setOf("HorizontalPress", "InclinePress", "VerticalPress")
        }.distinctBy { librarySlotKey(it) }

/** Vertical / horizontal pull family (role and sibling variants preserved). */
val UPPER_PULL: List<WorkoutSlotTemplate>
    get() =
        (UPPER_SLOTS + UPPER_PULL_SLOTS + FULL_BODY_SLOTS).filter {
            it.pattern.name in setOf("VerticalPull", "HorizontalPull")
        }.distinctBy { librarySlotKey(it) }

/** Lateral raise, arms, rear-delt (optional + accessory role variants). */
val UPPER_ACCESSORIES: List<WorkoutSlotTemplate>
    get() =
        (UPPER_SLOTS + UPPER_PULL_SLOTS).filter {
            it.role == SlotRole.Accessory ||
                it.role == SlotRole.Optional ||
                it.primaryMuscle.name in setOf("Bicep", "Tricep", "LateralDeltoid", "PosteriorDeltoid")
        }.filter {
            it.pattern.name in
                setOf(
                    "ShoulderAbduction",
                    "ElbowFlexion",
                    "ElbowExtension",
                    "ShoulderHorizontalAbduction",
                )
        }.distinctBy { librarySlotKey(it) }

/**
 * Identity used when unioning catalog templates into the base library.
 * Matches universe [com.stratum.domain.program.generation.universe] slot keys
 * (pattern|muscle|role|touch|variant) for non-touch base templates.
 */
fun librarySlotKey(slot: WorkoutSlotTemplate): String =
    listOf(
        slot.pattern.name,
        slot.primaryMuscle.name,
        slot.role.name,
        slot.isCrossDomainTouch.toString(),
        slot.siblingVariant?.name ?: "Any",
    ).joinToString("|")
