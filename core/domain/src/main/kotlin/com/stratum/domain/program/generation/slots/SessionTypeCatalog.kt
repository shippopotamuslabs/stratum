@file:Suppress("MagicNumber", "ReturnCount", "LoopWithTooManyJumpStatements", "MatchingDeclarationName", "ComplexCondition", "MaxLineLength")

package com.stratum.domain.program.generation.slots

import com.stratum.domain.lift.FatigueProfile
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.onboarding.SpecializationLevel
import com.stratum.domain.program.generation.context.ProgramGenerationContext
import com.stratum.domain.program.generation.reference.RpRepProfile
import com.stratum.domain.program.generation.reference.PATTERN_SECONDARY_MUSCLE_CREDITS

enum class SlotRole {
    Anchor,
    SecondaryCompound,
    Accessory,
    Optional,
}

enum class MuscleLengthBiasPreference {
    Lengthened,
    Shortened,
    MidRange,
    Any,
}

enum class SiblingVariant {
    A,
    B,
    Any,
}

data class WorkoutSlotTemplate(
    val slotId: String,
    val role: SlotRole,
    val pattern: MovementPattern,
    val primaryMuscle: MuscleGroup,
    val repProfile: RpRepProfile,
    val lengthBiasPreference: MuscleLengthBiasPreference? = null,
    /** Sibling B sessions may prefer alternate row flavor. */
    val siblingVariant: SiblingVariant? = null,
    /** Supplementary direct work on a session outside the muscle's primary domain. */
    val isCrossDomainTouch: Boolean = false,
    /**
     * When set, lifts whose [FatigueProfile] is in this set may match via fatigue allow-list
     * even if their movement pattern differs (pattern OR allow-list; not exclusive filter).
     * `null` = pattern match only. Used by accessory slots (e.g. `quad-iso`/`ham-iso`) to admit
     * Medium (leg press, hack squat) alongside Low (leg extension, leg curl).
     */
    val allowedFatigueProfiles: Set<FatigueProfile>? = null,
)

enum class SessionTypeId {
    Lower,
    Upper,
    UpperPush,
    UpperPull,
    FullBody,
    FullPush,
    FullPull,
    Chest,
    Back,
    Shoulders,
    Arms,
    QuadsCalves,
    GlutesHams,
    ChestTris,
    BackBis,
    /** Two-lift session: one anchor + one secondary from a different pattern family. */
    CompoundPair,
    /** Two secondary-tier lifts, no anchor (light rotation day). */
    AuxPair,
    /** Single anchor lift (micro maintenance day). */
    SingleAnchor,
}

/** Lower-day catalog templates — also sourced by [baseSlotLibrary]. */
internal val LOWER_SLOTS: List<WorkoutSlotTemplate> = listOf(
    WorkoutSlotTemplate("squat", SlotRole.Anchor, MovementPattern.SquatPattern, MuscleGroup.Quad, RpRepProfile.LowerFreeWeightCompound),
    WorkoutSlotTemplate("hinge", SlotRole.Anchor, MovementPattern.HipHinge, MuscleGroup.Hamstring, RpRepProfile.LowerFreeWeightCompound),
    WorkoutSlotTemplate("quad-iso", SlotRole.Accessory, MovementPattern.KneeExtension, MuscleGroup.Quad, RpRepProfile.Isolation,
        allowedFatigueProfiles = setOf(FatigueProfile.Low, FatigueProfile.Medium)),
    WorkoutSlotTemplate("ham-iso", SlotRole.Accessory, MovementPattern.KneeFlexion, MuscleGroup.Hamstring, RpRepProfile.Isolation,
        allowedFatigueProfiles = setOf(FatigueProfile.Low, FatigueProfile.Medium)),
    WorkoutSlotTemplate("calf", SlotRole.Optional, MovementPattern.PlantarFlexion, MuscleGroup.Calf, RpRepProfile.Isolation),
    // Glute-primary slot. Excluded from the universe for Maintenance users (Glute volume rides on
    // squat/hinge secondaries — see PATTERN_SECONDARY_MUSCLE_CREDITS); included only when the user
    // Specializes Glute, so direct hip-thrust work becomes schedulable (gated in `isSlotRelevant`).
    WorkoutSlotTemplate("glute", SlotRole.Anchor, MovementPattern.HipExtension, MuscleGroup.Glute, RpRepProfile.LowerFreeWeightCompound),
)

/** Balanced upper catalog templates — also sourced by [baseSlotLibrary]. */
internal val UPPER_SLOTS: List<WorkoutSlotTemplate> = listOf(
    WorkoutSlotTemplate("h-press", SlotRole.Anchor, MovementPattern.HorizontalPress, MuscleGroup.Chest, RpRepProfile.LowerMachineOrUpperPress),
    WorkoutSlotTemplate(
        "v-pull",
        SlotRole.SecondaryCompound,
        MovementPattern.VerticalPull,
        MuscleGroup.Back,
        RpRepProfile.UpperMachineOrPull,
        siblingVariant = SiblingVariant.A,
    ),
    WorkoutSlotTemplate(
        "h-pull",
        SlotRole.SecondaryCompound,
        MovementPattern.HorizontalPull,
        MuscleGroup.Back,
        RpRepProfile.UpperMachineOrPull,
        lengthBiasPreference = MuscleLengthBiasPreference.Lengthened,
        siblingVariant = SiblingVariant.A,
    ),
    WorkoutSlotTemplate(
        "incline",
        SlotRole.SecondaryCompound,
        MovementPattern.InclinePress,
        MuscleGroup.Chest,
        RpRepProfile.LowerMachineOrUpperPress,
        siblingVariant = SiblingVariant.B,
    ),
    WorkoutSlotTemplate("lat-raise", SlotRole.Accessory, MovementPattern.ShoulderAbduction, MuscleGroup.LateralDeltoid, RpRepProfile.Isolation),
    WorkoutSlotTemplate("bicep", SlotRole.Accessory, MovementPattern.ElbowFlexion, MuscleGroup.Bicep, RpRepProfile.Isolation),
    WorkoutSlotTemplate("tricep", SlotRole.Accessory, MovementPattern.ElbowExtension, MuscleGroup.Tricep, RpRepProfile.Isolation),
    WorkoutSlotTemplate(
        "rear-delt",
        SlotRole.Optional,
        MovementPattern.ShoulderHorizontalAbduction,
        MuscleGroup.PosteriorDeltoid,
        RpRepProfile.Isolation,
    ),
)

/** Upper-push catalog templates — also sourced by [baseSlotLibrary]. */
internal val UPPER_PUSH_SLOTS: List<WorkoutSlotTemplate> = listOf(
    WorkoutSlotTemplate("h-press", SlotRole.Anchor, MovementPattern.HorizontalPress, MuscleGroup.Chest, RpRepProfile.LowerMachineOrUpperPress),
    WorkoutSlotTemplate(
        "v-press",
        SlotRole.SecondaryCompound,
        MovementPattern.VerticalPress,
        MuscleGroup.AnteriorDeltoid,
        RpRepProfile.LowerMachineOrUpperPress,
    ),
    WorkoutSlotTemplate(
        "incline",
        SlotRole.SecondaryCompound,
        MovementPattern.InclinePress,
        MuscleGroup.Chest,
        RpRepProfile.LowerMachineOrUpperPress,
    ),
    WorkoutSlotTemplate("lat-raise", SlotRole.Accessory, MovementPattern.ShoulderAbduction, MuscleGroup.LateralDeltoid, RpRepProfile.Isolation),
    WorkoutSlotTemplate("tricep", SlotRole.Accessory, MovementPattern.ElbowExtension, MuscleGroup.Tricep, RpRepProfile.Isolation),
)

/** Upper-pull catalog templates — also sourced by [baseSlotLibrary]. */
internal val UPPER_PULL_SLOTS: List<WorkoutSlotTemplate> = listOf(
    WorkoutSlotTemplate("v-pull", SlotRole.Anchor, MovementPattern.VerticalPull, MuscleGroup.Back, RpRepProfile.UpperMachineOrPull),
    WorkoutSlotTemplate(
        "h-pull",
        SlotRole.SecondaryCompound,
        MovementPattern.HorizontalPull,
        MuscleGroup.Back,
        RpRepProfile.UpperMachineOrPull,
        lengthBiasPreference = MuscleLengthBiasPreference.Lengthened,
    ),
    WorkoutSlotTemplate(
        "rear-delt",
        SlotRole.Accessory,
        MovementPattern.ShoulderHorizontalAbduction,
        MuscleGroup.PosteriorDeltoid,
        RpRepProfile.Isolation,
    ),
    WorkoutSlotTemplate("bicep", SlotRole.Accessory, MovementPattern.ElbowFlexion, MuscleGroup.Bicep, RpRepProfile.Isolation),
)

/** Full-body catalog templates — also sourced by [baseSlotLibrary] (role variants). */
internal val FULL_BODY_SLOTS: List<WorkoutSlotTemplate> = listOf(
    WorkoutSlotTemplate("squat", SlotRole.Anchor, MovementPattern.SquatPattern, MuscleGroup.Quad, RpRepProfile.LowerFreeWeightCompound),
    WorkoutSlotTemplate("h-press", SlotRole.Anchor, MovementPattern.HorizontalPress, MuscleGroup.Chest, RpRepProfile.LowerMachineOrUpperPress),
    WorkoutSlotTemplate("h-pull", SlotRole.SecondaryCompound, MovementPattern.HorizontalPull, MuscleGroup.Back, RpRepProfile.UpperMachineOrPull),
    WorkoutSlotTemplate("hinge", SlotRole.SecondaryCompound, MovementPattern.HipHinge, MuscleGroup.Hamstring, RpRepProfile.LowerFreeWeightCompound),
)

private val FULL_PUSH_SLOTS: List<WorkoutSlotTemplate> =
    UPPER_PUSH_SLOTS +
        listOf(
            WorkoutSlotTemplate("quad-iso", SlotRole.Accessory, MovementPattern.KneeExtension, MuscleGroup.Quad, RpRepProfile.Isolation,
                allowedFatigueProfiles = setOf(FatigueProfile.Low, FatigueProfile.Medium)),
            WorkoutSlotTemplate("calf", SlotRole.Optional, MovementPattern.PlantarFlexion, MuscleGroup.Calf, RpRepProfile.Isolation),
        )

private val FULL_PULL_SLOTS: List<WorkoutSlotTemplate> =
    UPPER_PULL_SLOTS +
        listOf(
            WorkoutSlotTemplate("hinge", SlotRole.Anchor, MovementPattern.HipHinge, MuscleGroup.Hamstring, RpRepProfile.LowerFreeWeightCompound),
            WorkoutSlotTemplate("ham-iso", SlotRole.Accessory, MovementPattern.KneeFlexion, MuscleGroup.Hamstring, RpRepProfile.Isolation,
                allowedFatigueProfiles = setOf(FatigueProfile.Low, FatigueProfile.Medium)),
        )

private val CHEST_SLOTS: List<WorkoutSlotTemplate> = listOf(
    WorkoutSlotTemplate("h-press", SlotRole.Anchor, MovementPattern.HorizontalPress, MuscleGroup.Chest, RpRepProfile.LowerMachineOrUpperPress),
    WorkoutSlotTemplate("incline", SlotRole.SecondaryCompound, MovementPattern.InclinePress, MuscleGroup.Chest, RpRepProfile.LowerMachineOrUpperPress),
    WorkoutSlotTemplate("fly", SlotRole.Accessory, MovementPattern.HorizontalPress, MuscleGroup.Chest, RpRepProfile.Isolation),
)

private val BACK_SLOTS: List<WorkoutSlotTemplate> = listOf(
    WorkoutSlotTemplate("v-pull", SlotRole.Anchor, MovementPattern.VerticalPull, MuscleGroup.Back, RpRepProfile.UpperMachineOrPull),
    WorkoutSlotTemplate("h-pull", SlotRole.SecondaryCompound, MovementPattern.HorizontalPull, MuscleGroup.Back, RpRepProfile.UpperMachineOrPull),
    WorkoutSlotTemplate(
        "rear-delt",
        SlotRole.Optional,
        MovementPattern.ShoulderHorizontalAbduction,
        MuscleGroup.PosteriorDeltoid,
        RpRepProfile.Isolation,
    ),
)

private val SHOULDERS_SLOTS: List<WorkoutSlotTemplate> = listOf(
    WorkoutSlotTemplate("v-press", SlotRole.Anchor, MovementPattern.VerticalPress, MuscleGroup.AnteriorDeltoid, RpRepProfile.LowerMachineOrUpperPress),
    WorkoutSlotTemplate("lat-raise", SlotRole.Accessory, MovementPattern.ShoulderAbduction, MuscleGroup.LateralDeltoid, RpRepProfile.Isolation),
    WorkoutSlotTemplate(
        "rear-delt",
        SlotRole.Accessory,
        MovementPattern.ShoulderHorizontalAbduction,
        MuscleGroup.PosteriorDeltoid,
        RpRepProfile.Isolation,
    ),
)

private val ARMS_SLOTS: List<WorkoutSlotTemplate> = listOf(
    WorkoutSlotTemplate("bicep", SlotRole.Anchor, MovementPattern.ElbowFlexion, MuscleGroup.Bicep, RpRepProfile.Isolation),
    WorkoutSlotTemplate("tricep", SlotRole.Anchor, MovementPattern.ElbowExtension, MuscleGroup.Tricep, RpRepProfile.Isolation),
)

private val QUADS_CALVES_SLOTS: List<WorkoutSlotTemplate> = listOf(
    WorkoutSlotTemplate("squat", SlotRole.Anchor, MovementPattern.SquatPattern, MuscleGroup.Quad, RpRepProfile.LowerFreeWeightCompound),
    WorkoutSlotTemplate("quad-iso", SlotRole.Accessory, MovementPattern.KneeExtension, MuscleGroup.Quad, RpRepProfile.Isolation,
        allowedFatigueProfiles = setOf(FatigueProfile.Low, FatigueProfile.Medium)),
    WorkoutSlotTemplate("calf", SlotRole.Accessory, MovementPattern.PlantarFlexion, MuscleGroup.Calf, RpRepProfile.Isolation),
)

private val GLUTES_HAMS_SLOTS: List<WorkoutSlotTemplate> = listOf(
    WorkoutSlotTemplate("hinge", SlotRole.Anchor, MovementPattern.HipHinge, MuscleGroup.Hamstring, RpRepProfile.LowerFreeWeightCompound),
    WorkoutSlotTemplate("ham-iso", SlotRole.Accessory, MovementPattern.KneeFlexion, MuscleGroup.Hamstring, RpRepProfile.Isolation,
        allowedFatigueProfiles = setOf(FatigueProfile.Low, FatigueProfile.Medium)),
)

private val CHEST_TRIS_SLOTS: List<WorkoutSlotTemplate> =
    CHEST_SLOTS +
        listOf(
            WorkoutSlotTemplate("tricep", SlotRole.Accessory, MovementPattern.ElbowExtension, MuscleGroup.Tricep, RpRepProfile.Isolation),
        )

private val BACK_BIS_SLOTS: List<WorkoutSlotTemplate> =
    BACK_SLOTS +
        listOf(
            WorkoutSlotTemplate("bicep", SlotRole.Accessory, MovementPattern.ElbowFlexion, MuscleGroup.Bicep, RpRepProfile.Isolation),
        )

private val FULL_BODY_COMPLEMENT_B_SLOTS: List<WorkoutSlotTemplate> = listOf(
    WorkoutSlotTemplate("hinge", SlotRole.Anchor, MovementPattern.HipHinge, MuscleGroup.Hamstring, RpRepProfile.LowerFreeWeightCompound),
    WorkoutSlotTemplate("v-press", SlotRole.SecondaryCompound, MovementPattern.VerticalPress, MuscleGroup.AnteriorDeltoid, RpRepProfile.LowerMachineOrUpperPress),
    WorkoutSlotTemplate("squat-aux", SlotRole.SecondaryCompound, MovementPattern.SquatPattern, MuscleGroup.Quad, RpRepProfile.LowerFreeWeightCompound),
    WorkoutSlotTemplate("incline", SlotRole.SecondaryCompound, MovementPattern.InclinePress, MuscleGroup.Chest, RpRepProfile.LowerMachineOrUpperPress),
)

private val COMPOUND_PAIR_SLOTS: List<WorkoutSlotTemplate> = listOf(
    WorkoutSlotTemplate("anchor", SlotRole.Anchor, MovementPattern.SquatPattern, MuscleGroup.Quad, RpRepProfile.LowerFreeWeightCompound),
    WorkoutSlotTemplate("secondary", SlotRole.SecondaryCompound, MovementPattern.HorizontalPress, MuscleGroup.Chest, RpRepProfile.LowerMachineOrUpperPress),
)

private val AUX_PAIR_SLOTS: List<WorkoutSlotTemplate> = listOf(
    WorkoutSlotTemplate("aux-a", SlotRole.SecondaryCompound, MovementPattern.HorizontalPress, MuscleGroup.Chest, RpRepProfile.LowerMachineOrUpperPress),
    WorkoutSlotTemplate("aux-b", SlotRole.SecondaryCompound, MovementPattern.HipHinge, MuscleGroup.Hamstring, RpRepProfile.LowerFreeWeightCompound),
)

private val SINGLE_ANCHOR_SLOTS: List<WorkoutSlotTemplate> = listOf(
    WorkoutSlotTemplate("anchor", SlotRole.Anchor, MovementPattern.VerticalPress, MuscleGroup.AnteriorDeltoid, RpRepProfile.LowerMachineOrUpperPress),
)

private val SESSION_SLOTS: Map<SessionTypeId, List<WorkoutSlotTemplate>> = mapOf(
    SessionTypeId.Lower to LOWER_SLOTS,
    SessionTypeId.Upper to UPPER_SLOTS,
    SessionTypeId.UpperPush to UPPER_PUSH_SLOTS,
    SessionTypeId.UpperPull to UPPER_PULL_SLOTS,
    SessionTypeId.FullBody to FULL_BODY_SLOTS,
    SessionTypeId.FullPush to FULL_PUSH_SLOTS,
    SessionTypeId.FullPull to FULL_PULL_SLOTS,
    SessionTypeId.Chest to CHEST_SLOTS,
    SessionTypeId.Back to BACK_SLOTS,
    SessionTypeId.Shoulders to SHOULDERS_SLOTS,
    SessionTypeId.Arms to ARMS_SLOTS,
    SessionTypeId.QuadsCalves to QUADS_CALVES_SLOTS,
    SessionTypeId.GlutesHams to GLUTES_HAMS_SLOTS,
    SessionTypeId.ChestTris to CHEST_TRIS_SLOTS,
    SessionTypeId.BackBis to BACK_BIS_SLOTS,
    SessionTypeId.CompoundPair to COMPOUND_PAIR_SLOTS,
    SessionTypeId.AuxPair to AUX_PAIR_SLOTS,
    SessionTypeId.SingleAnchor to SINGLE_ANCHOR_SLOTS,
)

fun slotsForSessionType(
    sessionType: SessionTypeId,
    context: ProgramGenerationContext? = null,
    siblingIndex: Int = 0,
    sessionPosition: Int? = null,
    composition: SessionComposition? = null,
    diagnostics: MutableList<SlotGenerationDiagnostic>? = null,
): List<WorkoutSlotTemplate> {
    val base = when {
        sessionType == SessionTypeId.FullBody && siblingIndex % 2 == 1 ->
            FULL_BODY_COMPLEMENT_B_SLOTS.map { it.copy() }.toMutableList()
        else ->
            SESSION_SLOTS.getValue(sessionType).map { it.copy() }.toMutableList()
    }
    if (context == null) return base

    promoteSpecializedSlots(base, context)
    when (composition) {
        SessionComposition.UpperSpecFocused -> applySpecFocusedUpperSlots(base, context, diagnostics)
        else -> injectSpecializedVariantSlots(base, context)
    }
    injectCrossDomainTouchSlots(sessionType, base, context, sessionPosition, composition, diagnostics)
    return base
}

internal fun promoteSpecializedSlots(
    slots: MutableList<WorkoutSlotTemplate>,
    context: ProgramGenerationContext,
) {
    for (i in slots.indices) {
        val slot = slots[i]
        if (slot.role != SlotRole.Optional) continue
        if (context.specialization[slot.primaryMuscle] == SpecializationLevel.Specialize) {
            slots[i] = slot.copy(role = SlotRole.Accessory)
        }
    }
}

private val LETTERED_SESSION_TYPES = setOf(
    SessionTypeId.Lower,
    SessionTypeId.Upper,
    SessionTypeId.UpperPush,
    SessionTypeId.UpperPull,
    SessionTypeId.FullBody,
    SessionTypeId.FullPush,
    SessionTypeId.FullPull,
    SessionTypeId.CompoundPair,
    SessionTypeId.AuxPair,
)

internal fun siblingIndexForSessionType(sessionTypes: List<SessionTypeId>, index: Int): Int {
    val type = sessionTypes[index]
    var count = 0
    for (i in 0 until index) {
        if (sessionTypes[i] == type) count++
    }
    return count
}

fun displayNameForSessionType(sessionType: SessionTypeId, index: Int): String {
    val labels = mapOf(
        SessionTypeId.Lower to "Lower",
        SessionTypeId.Upper to "Upper",
        SessionTypeId.UpperPush to "Upper Push",
        SessionTypeId.UpperPull to "Upper Pull",
        SessionTypeId.FullBody to "Full Body",
        SessionTypeId.FullPush to "Full Push",
        SessionTypeId.FullPull to "Full Pull",
        SessionTypeId.Chest to "Chest",
        SessionTypeId.Back to "Back",
        SessionTypeId.Shoulders to "Shoulders",
        SessionTypeId.Arms to "Arms",
        SessionTypeId.QuadsCalves to "Quads & Calves",
        SessionTypeId.GlutesHams to "Glutes & Hams",
        SessionTypeId.ChestTris to "Chest & Tris",
        SessionTypeId.BackBis to "Back & Bis",
        SessionTypeId.CompoundPair to "Compound Pair",
        SessionTypeId.AuxPair to "Aux Pair",
        SessionTypeId.SingleAnchor to "Single Anchor",
    )
    val base = labels.getValue(sessionType)
    if (sessionType in LETTERED_SESSION_TYPES) {
        val letter = ('A'.code + index.coerceAtMost(25)).toChar()
        return "$base $letter"
    }
    return base
}

/** Primary muscles trained on a session type (for recovery scoring). */
fun primaryMusclesForSessionType(sessionType: SessionTypeId): List<MuscleGroup> =
    SESSION_SLOTS.getValue(sessionType).map { it.primaryMuscle }.distinct()
