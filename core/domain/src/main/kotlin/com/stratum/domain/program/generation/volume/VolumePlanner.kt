@file:Suppress("MagicNumber", "ReturnCount", "LoopWithTooManyJumpStatements", "MatchingDeclarationName", "ComplexCondition", "MaxLineLength")

package com.stratum.domain.program.generation.volume

import com.stratum.domain.program.generation.context.ProgramGenerationContext
import com.stratum.domain.program.generation.reference.sessionHardSetCapForLength
import com.stratum.domain.program.generation.split.SessionTypeId
import com.stratum.domain.program.generation.split.SplitTemplate
import com.stratum.domain.program.generation.split.SPLIT_CATALOG
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

private const val SPECIALIZATION_AIM_CAP = 20
private const val VOLUME_TOLERANCE_SETS = 2

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

internal fun defaultSplitTemplate(trainingDaysPerWeek: Int): SplitTemplate? {
    val matches = SPLIT_CATALOG.filter { it.trainingDaysPerWeek == trainingDaysPerWeek }
    if (matches.isEmpty()) return null
    return matches.find { it.isDefault && it.frequencyTier.value == 2 }
        ?: matches.find { it.isDefault }
        ?: matches.firstOrNull()
}

data class WeeklySessionCountsByDomain(
    val lower: Double,
    val upper: Double,
    val mixed: Double,
)

/** Weekly session counts by domain, inferred from the default split for this training frequency. */
fun weeklySessionCountsByDomain(context: ProgramGenerationContext): WeeklySessionCountsByDomain {
    val template = defaultSplitTemplate(context.trainingDaysPerWeek)
    if (template == null) {
        return WeeklySessionCountsByDomain(
            lower = 0.0,
            upper = context.trainingDaysPerWeek.toDouble(),
            mixed = 0.0,
        )
    }
    val cycleLen = template.sessionTypes.size
    val ratio = context.trainingDaysPerWeek.toDouble() / cycleLen
    var lower = 0.0
    var upper = 0.0
    var mixed = 0.0
    for (sessionType in template.sessionTypes) {
        when (sessionTrainingDomain(sessionType)) {
            TrainingDomain.Lower -> lower += ratio
            TrainingDomain.Upper -> upper += ratio
            null -> mixed += ratio
        }
    }
    return WeeklySessionCountsByDomain(lower = lower, upper = upper, mixed = mixed)
}

internal fun perSessionHardSetCap(context: ProgramGenerationContext): Int? =
    sessionHardSetCapForLength(context.sessionLengthMinutes)

/** Per-domain weekly hard-set budget from session length × domain session count (informational). */
fun domainWeeklyBudget(
    context: ProgramGenerationContext,
    domain: TrainingDomain,
): Int? {
    val perSession = perSessionHardSetCap(context) ?: return null
    val counts = weeklySessionCountsByDomain(context)
    val dedicated = if (domain == TrainingDomain.Lower) counts.lower else counts.upper
    val mixedShare = counts.mixed * 0.5
    return round((dedicated + mixedShare) * perSession).toInt()
}

internal fun resolveUnscaled(
    muscle: String,
    level: String,
    context: ProgramGenerationContext,
): WeeklyVolumeTarget {
    val landmark = context.volumeLandmarksByMuscle[muscle]
    val mev = landmark?.estimatedMev ?: 8.0
    val mav = landmark?.estimatedMav ?: 14.0
    val mrv = landmark?.estimatedMrv ?: 20.0
    val pv = landmark?.estimatedPv ?: 11.0

    val conservative =
        context.lastTrained == "SixToTwelveMonthsAgo" ||
            context.lastTrained == "MoreThanAYearAgo" ||
            context.lastTrained == "Never"

    val volumeResponse = context.volumeResponsesByMuscle[muscle]
    val respondsToVolume =
        volumeResponse != null &&
            volumeResponse.correlationSlope > 0 &&
            volumeResponse.evidence == "Proven"

    if (level == "Specialize") {
        val aim = min(
            SPECIALIZATION_AIM_CAP.toDouble(),
            if (respondsToVolume) mrv else mav + (mrv - mav) * 0.6,
        )
        return WeeklyVolumeTarget(
            min = max(mev, mav - VOLUME_TOLERANCE_SETS),
            aim = round(aim),
            max = mrv,
        )
    }
    if (conservative) {
        return WeeklyVolumeTarget(min = mev, aim = pv, max = mav)
    }
    return WeeklyVolumeTarget(min = mev, aim = mav, max = mrv)
}

internal fun volumePlannerTrainableMuscles(context: ProgramGenerationContext): List<String> {
    val patternsByMuscle = availablePatternsByMuscle(context)
    val muscles = mutableSetOf<String>()
    muscles.addAll(context.volumeLandmarksByMuscle.keys)
    muscles.addAll(context.specialization.keys)
    return muscles.filter { m ->
        if ((context.specialization[m] ?: "Maintenance") == "Omit") return@filter false
        (patternsByMuscle[m]?.size ?: 0) > 0
    }
}

/**
 * Plans weekly volume per muscle from landmarks. Per-session caps and session-length limits
 * are applied in slot allocation — weekly maintenance targets are not scaled down for arm spec.
 */
fun planWeeklyVolume(context: ProgramGenerationContext): WeeklyVolumeTargets {
    val muscles = volumePlannerTrainableMuscles(context)
    val out = mutableMapOf<String, WeeklyVolumeTarget>()
    for (muscle in muscles) {
        val level = context.specialization[muscle] ?: "Maintenance"
        out[muscle] = resolveUnscaled(muscle, level, context)
    }
    return out
}
