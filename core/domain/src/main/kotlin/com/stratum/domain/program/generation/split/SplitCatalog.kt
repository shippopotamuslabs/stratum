@file:Suppress("MagicNumber", "ReturnCount", "LoopWithTooManyJumpStatements", "MatchingDeclarationName", "ComplexCondition", "MaxLineLength")

package com.stratum.domain.program.generation.split

import com.stratum.domain.program.generation.reference.FrequencyTier

data class SplitTemplate(
    val id: String,
    val trainingDaysPerWeek: Int,
    /** Minimum frequency tier this split satisfies (TABLE 7.6). */
    val frequencyTier: FrequencyTier,
    val sessionTypes: List<SessionTypeId>,
    /** Default option when multiple splits match. */
    val isDefault: Boolean = false,
)

val SPLIT_CATALOG: List<SplitTemplate> = listOf(
    // 2 days
    SplitTemplate(
        id = "2d-t1-ul",
        trainingDaysPerWeek = 2,
        frequencyTier = FrequencyTier.One,
        sessionTypes = listOf(SessionTypeId.Upper, SessionTypeId.Lower),
        isDefault = true,
    ),
    SplitTemplate(
        id = "2d-t1-pushpull",
        trainingDaysPerWeek = 2,
        frequencyTier = FrequencyTier.One,
        sessionTypes = listOf(SessionTypeId.FullPush, SessionTypeId.FullPull),
    ),
    SplitTemplate(
        id = "2d-t2-fb",
        trainingDaysPerWeek = 2,
        frequencyTier = FrequencyTier.Two,
        sessionTypes = listOf(SessionTypeId.FullBody, SessionTypeId.FullBody),
        isDefault = true,
    ),

    // 3 days
    SplitTemplate(
        id = "3d-t1-l-up-upull",
        trainingDaysPerWeek = 3,
        frequencyTier = FrequencyTier.One,
        sessionTypes = listOf(SessionTypeId.Lower, SessionTypeId.UpperPush, SessionTypeId.UpperPull),
        isDefault = true,
    ),
    SplitTemplate(
        id = "3d-t2-l-u-fb",
        trainingDaysPerWeek = 3,
        frequencyTier = FrequencyTier.Two,
        sessionTypes = listOf(SessionTypeId.Lower, SessionTypeId.Upper, SessionTypeId.FullBody),
        isDefault = true,
    ),
    SplitTemplate(
        id = "3d-t2-fp-fpull-fb",
        trainingDaysPerWeek = 3,
        frequencyTier = FrequencyTier.Two,
        sessionTypes = listOf(SessionTypeId.FullPush, SessionTypeId.FullPull, SessionTypeId.FullBody),
    ),
    SplitTemplate(
        id = "3d-t3-fb",
        trainingDaysPerWeek = 3,
        frequencyTier = FrequencyTier.Three,
        sessionTypes = listOf(SessionTypeId.FullBody, SessionTypeId.FullBody, SessionTypeId.FullBody),
        isDefault = true,
    ),

    // 4 days
    SplitTemplate(
        id = "4d-t1-bodypart",
        trainingDaysPerWeek = 4,
        frequencyTier = FrequencyTier.One,
        sessionTypes = listOf(
            SessionTypeId.ChestTris,
            SessionTypeId.BackBis,
            SessionTypeId.Shoulders,
            SessionTypeId.Lower,
        ),
    ),
    SplitTemplate(
        id = "4d-t2-ul",
        trainingDaysPerWeek = 4,
        frequencyTier = FrequencyTier.Two,
        sessionTypes = listOf(
            SessionTypeId.Lower,
            SessionTypeId.Upper,
            SessionTypeId.Lower,
            SessionTypeId.Upper,
        ),
        isDefault = true,
    ),
    SplitTemplate(
        id = "4d-t2-pushpull",
        trainingDaysPerWeek = 4,
        frequencyTier = FrequencyTier.Two,
        sessionTypes = listOf(
            SessionTypeId.FullPush,
            SessionTypeId.FullPull,
            SessionTypeId.FullPush,
            SessionTypeId.FullPull,
        ),
    ),
    SplitTemplate(
        id = "4d-t3-ul-fb",
        trainingDaysPerWeek = 4,
        frequencyTier = FrequencyTier.Three,
        sessionTypes = listOf(
            SessionTypeId.Upper,
            SessionTypeId.Lower,
            SessionTypeId.FullBody,
            SessionTypeId.FullBody,
        ),
        isDefault = true,
    ),
    SplitTemplate(
        id = "4d-t3-fp-fpull-fb",
        trainingDaysPerWeek = 4,
        frequencyTier = FrequencyTier.Three,
        sessionTypes = listOf(
            SessionTypeId.FullPush,
            SessionTypeId.FullPull,
            SessionTypeId.FullBody,
            SessionTypeId.FullBody,
        ),
    ),

    // 5 days
    SplitTemplate(
        id = "5d-t1-bodypart",
        trainingDaysPerWeek = 5,
        frequencyTier = FrequencyTier.One,
        sessionTypes = listOf(
            SessionTypeId.Chest,
            SessionTypeId.Back,
            SessionTypeId.Lower,
            SessionTypeId.Shoulders,
            SessionTypeId.Arms,
        ),
    ),
    SplitTemplate(
        id = "5d-t2-l-up-upull-l-u",
        trainingDaysPerWeek = 5,
        frequencyTier = FrequencyTier.Two,
        sessionTypes = listOf(
            SessionTypeId.Lower,
            SessionTypeId.UpperPush,
            SessionTypeId.UpperPull,
            SessionTypeId.Lower,
            SessionTypeId.Upper,
        ),
        isDefault = true,
    ),
    SplitTemplate(
        id = "5d-t2-l-up-upull-fp-fpull",
        trainingDaysPerWeek = 5,
        frequencyTier = FrequencyTier.Two,
        sessionTypes = listOf(
            SessionTypeId.Lower,
            SessionTypeId.UpperPush,
            SessionTypeId.UpperPull,
            SessionTypeId.FullPush,
            SessionTypeId.FullPull,
        ),
    ),
    SplitTemplate(
        id = "5d-t3-l-up-upull-full",
        trainingDaysPerWeek = 5,
        frequencyTier = FrequencyTier.Three,
        sessionTypes = listOf(
            SessionTypeId.Lower,
            SessionTypeId.UpperPush,
            SessionTypeId.UpperPull,
            SessionTypeId.FullBody,
            SessionTypeId.FullBody,
        ),
        isDefault = true,
    ),

    // 6 days — rolling cycles (cycle length > training days/week)
    SplitTemplate(
        id = "6d-t1-rolling-bp",
        trainingDaysPerWeek = 6,
        frequencyTier = FrequencyTier.One,
        sessionTypes = listOf(
            SessionTypeId.Chest,
            SessionTypeId.Back,
            SessionTypeId.QuadsCalves,
            SessionTypeId.Shoulders,
            SessionTypeId.Arms,
            SessionTypeId.GlutesHams,
        ),
    ),
    SplitTemplate(
        id = "6d-t2-l-up-upull",
        trainingDaysPerWeek = 6,
        frequencyTier = FrequencyTier.Two,
        sessionTypes = listOf(
            SessionTypeId.Lower,
            SessionTypeId.UpperPush,
            SessionTypeId.UpperPull,
            SessionTypeId.Lower,
            SessionTypeId.UpperPush,
            SessionTypeId.UpperPull,
        ),
        isDefault = true,
    ),
    SplitTemplate(
        id = "6d-t3-ul",
        trainingDaysPerWeek = 6,
        frequencyTier = FrequencyTier.Three,
        sessionTypes = listOf(
            SessionTypeId.Lower,
            SessionTypeId.Upper,
            SessionTypeId.Lower,
            SessionTypeId.Upper,
            SessionTypeId.Lower,
            SessionTypeId.Upper,
        ),
        isDefault = true,
    ),
    SplitTemplate(
        id = "6d-t3-pushpull",
        trainingDaysPerWeek = 6,
        frequencyTier = FrequencyTier.Three,
        sessionTypes = listOf(
            SessionTypeId.FullPush,
            SessionTypeId.FullPull,
            SessionTypeId.FullPush,
            SessionTypeId.FullPull,
            SessionTypeId.FullPush,
            SessionTypeId.FullPull,
        ),
    ),

    // Tier-1 rotation templates (low volume / MV-heavy profiles)
    SplitTemplate(
        id = "2d-t1-fb-rot",
        trainingDaysPerWeek = 2,
        frequencyTier = FrequencyTier.One,
        sessionTypes = listOf(SessionTypeId.FullBody, SessionTypeId.FullBody),
    ),
    SplitTemplate(
        id = "4d-t1-main-carousel",
        trainingDaysPerWeek = 4,
        frequencyTier = FrequencyTier.One,
        sessionTypes = listOf(
            SessionTypeId.CompoundPair,
            SessionTypeId.CompoundPair,
            SessionTypeId.CompoundPair,
            SessionTypeId.CompoundPair,
        ),
    ),
    SplitTemplate(
        id = "5d-t1-micro",
        trainingDaysPerWeek = 5,
        frequencyTier = FrequencyTier.One,
        sessionTypes = listOf(
            SessionTypeId.CompoundPair,
            SessionTypeId.CompoundPair,
            SessionTypeId.CompoundPair,
            SessionTypeId.CompoundPair,
            SessionTypeId.AuxPair,
        ),
    ),
    SplitTemplate(
        id = "6d-t1-micro",
        trainingDaysPerWeek = 6,
        frequencyTier = FrequencyTier.One,
        sessionTypes = listOf(
            SessionTypeId.CompoundPair,
            SessionTypeId.CompoundPair,
            SessionTypeId.CompoundPair,
            SessionTypeId.CompoundPair,
            SessionTypeId.SingleAnchor,
            SessionTypeId.SingleAnchor,
        ),
    ),
)

fun splitsForDaysAndTier(
    trainingDaysPerWeek: Int,
    requiredTier: FrequencyTier,
): List<SplitTemplate> =
    SPLIT_CATALOG.filter { split ->
        split.trainingDaysPerWeek == trainingDaysPerWeek && split.frequencyTier.value >= requiredTier.value
    }
