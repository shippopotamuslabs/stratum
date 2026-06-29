@file:Suppress("MagicNumber", "ReturnCount", "LoopWithTooManyJumpStatements", "MatchingDeclarationName", "ComplexCondition", "MaxLineLength")

package com.stratum.domain.program.generation.split

import com.stratum.domain.program.generation.context.ProgramGenerationContext
import com.stratum.domain.program.generation.reference.RpRepProfile

enum class SlotRole {
    Anchor,
    SecondaryCompound,
    Isolation,
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
    val pattern: String,
    val primaryMuscle: String,
    val repProfile: RpRepProfile,
    val lengthBiasPreference: MuscleLengthBiasPreference? = null,
    /** Sibling B sessions may prefer alternate row flavor. */
    val siblingVariant: SiblingVariant? = null,
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
}

private val LOWER_SLOTS: List<WorkoutSlotTemplate> = listOf(
    WorkoutSlotTemplate("squat", SlotRole.Anchor, "SquatPattern", "Quad", RpRepProfile.LowerFreeWeightCompound),
    WorkoutSlotTemplate("hinge", SlotRole.Anchor, "HipHinge", "Hamstring", RpRepProfile.LowerFreeWeightCompound),
    WorkoutSlotTemplate("quad-iso", SlotRole.Isolation, "KneeExtension", "Quad", RpRepProfile.Isolation),
    WorkoutSlotTemplate("ham-iso", SlotRole.Isolation, "KneeFlexion", "Hamstring", RpRepProfile.Isolation),
    WorkoutSlotTemplate("calf", SlotRole.Optional, "PlantarFlexion", "Calf", RpRepProfile.Isolation),
)

private val UPPER_SLOTS: List<WorkoutSlotTemplate> = listOf(
    WorkoutSlotTemplate("h-press", SlotRole.Anchor, "HorizontalPress", "Chest", RpRepProfile.LowerMachineOrUpperPress),
    WorkoutSlotTemplate(
        "v-pull",
        SlotRole.SecondaryCompound,
        "VerticalPull",
        "Back",
        RpRepProfile.UpperMachineOrPull,
        siblingVariant = SiblingVariant.A,
    ),
    WorkoutSlotTemplate(
        "h-pull",
        SlotRole.SecondaryCompound,
        "HorizontalPull",
        "Back",
        RpRepProfile.UpperMachineOrPull,
        lengthBiasPreference = MuscleLengthBiasPreference.Lengthened,
        siblingVariant = SiblingVariant.A,
    ),
    WorkoutSlotTemplate(
        "incline",
        SlotRole.SecondaryCompound,
        "InclinePress",
        "Chest",
        RpRepProfile.LowerMachineOrUpperPress,
        siblingVariant = SiblingVariant.B,
    ),
    WorkoutSlotTemplate("lat-raise", SlotRole.Isolation, "ShoulderAbduction", "LateralDeltoid", RpRepProfile.Isolation),
    WorkoutSlotTemplate("bicep", SlotRole.Isolation, "ElbowFlexion", "Bicep", RpRepProfile.Isolation),
    WorkoutSlotTemplate("tricep", SlotRole.Isolation, "ElbowExtension", "Tricep", RpRepProfile.Isolation),
    WorkoutSlotTemplate(
        "rear-delt",
        SlotRole.Optional,
        "ShoulderHorizontalAbduction",
        "PosteriorDeltoid",
        RpRepProfile.Isolation,
    ),
)

private val UPPER_PUSH_SLOTS: List<WorkoutSlotTemplate> = listOf(
    WorkoutSlotTemplate("h-press", SlotRole.Anchor, "HorizontalPress", "Chest", RpRepProfile.LowerMachineOrUpperPress),
    WorkoutSlotTemplate(
        "v-press",
        SlotRole.SecondaryCompound,
        "VerticalPress",
        "AnteriorDeltoid",
        RpRepProfile.LowerMachineOrUpperPress,
    ),
    WorkoutSlotTemplate(
        "incline",
        SlotRole.SecondaryCompound,
        "InclinePress",
        "Chest",
        RpRepProfile.LowerMachineOrUpperPress,
    ),
    WorkoutSlotTemplate("lat-raise", SlotRole.Isolation, "ShoulderAbduction", "LateralDeltoid", RpRepProfile.Isolation),
    WorkoutSlotTemplate("tricep", SlotRole.Isolation, "ElbowExtension", "Tricep", RpRepProfile.Isolation),
)

private val UPPER_PULL_SLOTS: List<WorkoutSlotTemplate> = listOf(
    WorkoutSlotTemplate("v-pull", SlotRole.Anchor, "VerticalPull", "Back", RpRepProfile.UpperMachineOrPull),
    WorkoutSlotTemplate(
        "h-pull",
        SlotRole.SecondaryCompound,
        "HorizontalPull",
        "Back",
        RpRepProfile.UpperMachineOrPull,
        lengthBiasPreference = MuscleLengthBiasPreference.Lengthened,
    ),
    WorkoutSlotTemplate(
        "rear-delt",
        SlotRole.Isolation,
        "ShoulderHorizontalAbduction",
        "PosteriorDeltoid",
        RpRepProfile.Isolation,
    ),
    WorkoutSlotTemplate("bicep", SlotRole.Isolation, "ElbowFlexion", "Bicep", RpRepProfile.Isolation),
)

private val FULL_BODY_SLOTS: List<WorkoutSlotTemplate> = listOf(
    WorkoutSlotTemplate("squat", SlotRole.Anchor, "SquatPattern", "Quad", RpRepProfile.LowerFreeWeightCompound),
    WorkoutSlotTemplate("h-press", SlotRole.Anchor, "HorizontalPress", "Chest", RpRepProfile.LowerMachineOrUpperPress),
    WorkoutSlotTemplate("h-pull", SlotRole.SecondaryCompound, "HorizontalPull", "Back", RpRepProfile.UpperMachineOrPull),
    WorkoutSlotTemplate("hinge", SlotRole.SecondaryCompound, "HipHinge", "Hamstring", RpRepProfile.LowerFreeWeightCompound),
)

private val FULL_PUSH_SLOTS: List<WorkoutSlotTemplate> =
    UPPER_PUSH_SLOTS +
        listOf(
            WorkoutSlotTemplate("quad-iso", SlotRole.Isolation, "KneeExtension", "Quad", RpRepProfile.Isolation),
            WorkoutSlotTemplate("calf", SlotRole.Optional, "PlantarFlexion", "Calf", RpRepProfile.Isolation),
        )

private val FULL_PULL_SLOTS: List<WorkoutSlotTemplate> =
    UPPER_PULL_SLOTS +
        listOf(
            WorkoutSlotTemplate("hinge", SlotRole.Anchor, "HipHinge", "Hamstring", RpRepProfile.LowerFreeWeightCompound),
            WorkoutSlotTemplate("ham-iso", SlotRole.Isolation, "KneeFlexion", "Hamstring", RpRepProfile.Isolation),
        )

private val CHEST_SLOTS: List<WorkoutSlotTemplate> = listOf(
    WorkoutSlotTemplate("h-press", SlotRole.Anchor, "HorizontalPress", "Chest", RpRepProfile.LowerMachineOrUpperPress),
    WorkoutSlotTemplate("incline", SlotRole.SecondaryCompound, "InclinePress", "Chest", RpRepProfile.LowerMachineOrUpperPress),
    WorkoutSlotTemplate("fly", SlotRole.Isolation, "HorizontalPress", "Chest", RpRepProfile.Isolation),
)

private val BACK_SLOTS: List<WorkoutSlotTemplate> = listOf(
    WorkoutSlotTemplate("v-pull", SlotRole.Anchor, "VerticalPull", "Back", RpRepProfile.UpperMachineOrPull),
    WorkoutSlotTemplate("h-pull", SlotRole.SecondaryCompound, "HorizontalPull", "Back", RpRepProfile.UpperMachineOrPull),
    WorkoutSlotTemplate(
        "rear-delt",
        SlotRole.Optional,
        "ShoulderHorizontalAbduction",
        "PosteriorDeltoid",
        RpRepProfile.Isolation,
    ),
)

private val SHOULDERS_SLOTS: List<WorkoutSlotTemplate> = listOf(
    WorkoutSlotTemplate("v-press", SlotRole.Anchor, "VerticalPress", "AnteriorDeltoid", RpRepProfile.LowerMachineOrUpperPress),
    WorkoutSlotTemplate("lat-raise", SlotRole.Isolation, "ShoulderAbduction", "LateralDeltoid", RpRepProfile.Isolation),
    WorkoutSlotTemplate(
        "rear-delt",
        SlotRole.Isolation,
        "ShoulderHorizontalAbduction",
        "PosteriorDeltoid",
        RpRepProfile.Isolation,
    ),
)

private val ARMS_SLOTS: List<WorkoutSlotTemplate> = listOf(
    WorkoutSlotTemplate("bicep", SlotRole.Anchor, "ElbowFlexion", "Bicep", RpRepProfile.Isolation),
    WorkoutSlotTemplate("tricep", SlotRole.Anchor, "ElbowExtension", "Tricep", RpRepProfile.Isolation),
)

private val QUADS_CALVES_SLOTS: List<WorkoutSlotTemplate> = listOf(
    WorkoutSlotTemplate("squat", SlotRole.Anchor, "SquatPattern", "Quad", RpRepProfile.LowerFreeWeightCompound),
    WorkoutSlotTemplate("quad-iso", SlotRole.Isolation, "KneeExtension", "Quad", RpRepProfile.Isolation),
    WorkoutSlotTemplate("calf", SlotRole.Isolation, "PlantarFlexion", "Calf", RpRepProfile.Isolation),
)

private val GLUTES_HAMS_SLOTS: List<WorkoutSlotTemplate> = listOf(
    WorkoutSlotTemplate("hinge", SlotRole.Anchor, "HipHinge", "Hamstring", RpRepProfile.LowerFreeWeightCompound),
    WorkoutSlotTemplate("ham-iso", SlotRole.Isolation, "KneeFlexion", "Hamstring", RpRepProfile.Isolation),
)

private val CHEST_TRIS_SLOTS: List<WorkoutSlotTemplate> =
    CHEST_SLOTS +
        listOf(
            WorkoutSlotTemplate("tricep", SlotRole.Isolation, "ElbowExtension", "Tricep", RpRepProfile.Isolation),
        )

private val BACK_BIS_SLOTS: List<WorkoutSlotTemplate> =
    BACK_SLOTS +
        listOf(
            WorkoutSlotTemplate("bicep", SlotRole.Isolation, "ElbowFlexion", "Bicep", RpRepProfile.Isolation),
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
)

fun slotsForSessionType(
    sessionType: SessionTypeId,
    context: ProgramGenerationContext? = null,
): List<WorkoutSlotTemplate> {
    val base = SESSION_SLOTS.getValue(sessionType).map { it.copy() }.toMutableList()
    if (context == null) return base

    promoteSpecializedSlots(base, context)

    if (sessionType == SessionTypeId.FullBody && armsSpecialized(context)) {
        base.add(
            WorkoutSlotTemplate("bicep-fb", SlotRole.Isolation, "ElbowFlexion", "Bicep", RpRepProfile.Isolation),
        )
        base.add(
            WorkoutSlotTemplate("tricep-fb", SlotRole.Isolation, "ElbowExtension", "Tricep", RpRepProfile.Isolation),
        )
    }
    if (sessionType == SessionTypeId.FullBody && calfSpecialized(context)) {
        base.add(
            WorkoutSlotTemplate("calf-fb", SlotRole.Isolation, "PlantarFlexion", "Calf", RpRepProfile.Isolation),
        )
    }
    return base
}

internal fun calfSpecialized(context: ProgramGenerationContext): Boolean =
    (context.specialization["Calf"] ?: "Maintenance") == "Specialize"

internal fun promoteSpecializedSlots(
    slots: MutableList<WorkoutSlotTemplate>,
    context: ProgramGenerationContext,
) {
    for (i in slots.indices) {
        val slot = slots[i]
        if (slot.role != SlotRole.Optional) continue
        if ((context.specialization[slot.primaryMuscle] ?: "Maintenance") == "Specialize") {
            slots[i] = slot.copy(role = SlotRole.Isolation)
        }
    }
}

internal fun armsSpecialized(context: ProgramGenerationContext): Boolean =
    (context.specialization["Bicep"] ?: "Maintenance") == "Specialize" ||
        (context.specialization["Tricep"] ?: "Maintenance") == "Specialize"

private val LETTERED_SESSION_TYPES = setOf(
    SessionTypeId.Lower,
    SessionTypeId.Upper,
    SessionTypeId.UpperPush,
    SessionTypeId.UpperPull,
    SessionTypeId.FullBody,
    SessionTypeId.FullPush,
    SessionTypeId.FullPull,
)

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
    )
    val base = labels.getValue(sessionType)
    val letter = if (index % 2 == 0) "A" else "B"
    if (sessionType in LETTERED_SESSION_TYPES) {
        return "$base $letter"
    }
    return base
}

/** Primary muscles trained on a session type (for recovery scoring). */
fun primaryMusclesForSessionType(sessionType: SessionTypeId): List<String> =
    SESSION_SLOTS.getValue(sessionType).map { it.primaryMuscle }.distinct()
