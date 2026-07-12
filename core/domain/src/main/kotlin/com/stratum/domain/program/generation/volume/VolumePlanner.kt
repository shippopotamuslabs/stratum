@file:Suppress("MagicNumber", "ReturnCount", "LoopWithTooManyJumpStatements", "MatchingDeclarationName", "ComplexCondition", "MaxLineLength")

package com.stratum.domain.program.generation.volume

import com.stratum.domain.program.generation.context.ProgramGenerationContext
import com.stratum.domain.program.generation.reference.sessionAimHardSetsForLength
import com.stratum.domain.program.generation.slots.SessionTypeId
import kotlin.math.round

private val LOWER_BODY_MUSCLES = setOf("Quad", "Hamstring", "Glute", "Calf", "LowerBack")

private val LOWER_SESSION_TYPES = setOf(
    SessionTypeId.Lower,
    SessionTypeId.QuadsCalves,
    SessionTypeId.GlutesHams,
)
private val UPPER_SESSION_TYPES = setOf(
    SessionTypeId.Upper,
    SessionTypeId.UpperPush,
    SessionTypeId.UpperPull,
    SessionTypeId.Chest,
    SessionTypeId.Back,
    SessionTypeId.Shoulders,
    SessionTypeId.Arms,
    SessionTypeId.ChestTris,
    SessionTypeId.BackBis,
)
private val MIXED_SESSION_TYPES = setOf(
    SessionTypeId.FullBody,
    SessionTypeId.FullPush,
    SessionTypeId.FullPull,
    SessionTypeId.CompoundPair,
    SessionTypeId.AuxPair,
    SessionTypeId.SingleAnchor,
)

enum class TrainingDomain {
    Lower,
    Upper,
}

fun muscleTrainingDomain(muscle: String): TrainingDomain =
    if (muscle in LOWER_BODY_MUSCLES) TrainingDomain.Lower else TrainingDomain.Upper

internal fun sessionTrainingDomain(sessionType: SessionTypeId): TrainingDomain? =
    when {
        sessionType in LOWER_SESSION_TYPES -> TrainingDomain.Lower
        sessionType in UPPER_SESSION_TYPES -> TrainingDomain.Upper
        sessionType in MIXED_SESSION_TYPES -> null
        else -> TrainingDomain.Upper
    }

data class WeeklySessionCountsByDomain(
    val lower: Double,
    val upper: Double,
    val mixed: Double,
)

/** Weekly session counts by domain for volume budgeting (typical U/L frequency heuristic). */
fun weeklySessionCountsByDomain(context: ProgramGenerationContext): WeeklySessionCountsByDomain {
    val days = context.trainingDaysPerWeek
    if (days <= 0) {
        return WeeklySessionCountsByDomain(lower = 0.0, upper = 0.0, mixed = 0.0)
    }
    val lower =
        when (days) {
            2 -> 1.0
            3 -> 1.0
            4 -> 2.0
            5 -> 2.0
            6 -> 3.0
            else -> (days / 2).toDouble()
        }
    return WeeklySessionCountsByDomain(
        lower = lower,
        upper = days - lower,
        mixed = 0.0,
    )
}

/** Soft per-session hard-set aim (band midpoint) for informational domain budgets. */
internal fun perSessionHardSetAim(context: ProgramGenerationContext): Int? =
    sessionAimHardSetsForLength(context.sessionLengthMinutes)

/** Per-domain weekly hard-set budget from session length × domain session count (informational). */
fun domainWeeklyBudget(
    context: ProgramGenerationContext,
    domain: TrainingDomain,
): Int? {
    val perSession = perSessionHardSetAim(context) ?: return null
    val counts = weeklySessionCountsByDomain(context)
    val dedicated = if (domain == TrainingDomain.Lower) counts.lower else counts.upper
    val mixedShare = counts.mixed * 0.5
    return round((dedicated + mixedShare) * perSession).toInt()
}

/**
 * Core aim muscles with at least one enabled-lift pattern contribution.
 * Delegates the membership set to [targetableMuscles] (MAJOR / CORE volume list only).
 */
internal fun volumePlannerTrainableMuscles(context: ProgramGenerationContext): List<String> =
    trainableMuscles(context)

/**
 * Plans weekly volume per muscle from MV/PV/RC snapshots. Specialized muscles aim at RC;
 * maintenance muscles floor at MV and aim at PV. Session time trims maintenance toward PV
 * before MV before reducing specialized targets.
 */
fun planWeeklyVolume(context: ProgramGenerationContext): WeeklyVolumeTargets {
    val muscles = volumePlannerTrainableMuscles(context)
    val unscaled = buildUnscaledWeeklyTargets(muscles, context)
    return applyWeeklyTimeBudgetPrecedence(unscaled, context)
}
