@file:Suppress("MagicNumber", "ReturnCount", "LoopWithTooManyJumpStatements", "MatchingDeclarationName", "ComplexCondition", "MaxLineLength")

package com.stratum.domain.program.generation.split

import com.stratum.domain.program.generation.context.ProgramGenerationContext
import com.stratum.domain.program.generation.reference.FrequencyTier
import com.stratum.domain.program.generation.volume.WeeklyVolumeTargets
import com.stratum.domain.program.generation.volume.aggregateWeeklyAim
import com.stratum.domain.program.generation.volume.requiredFrequencyTier
import com.stratum.domain.program.generation.volume.validateCycleFrequency

data class ResolvedSessionPlan(
    val sessionType: SessionTypeId,
    val name: String,
    val slots: List<WorkoutSlotTemplate>,
    val siblingIndex: Int,
    val position: Int,
)

data class ResolvedSplit(
    val template: SplitTemplate,
    val sessions: List<ResolvedSessionPlan>,
    val cycleLength: Int,
)

private val WEEKDAY_ORDER = listOf(
    "Monday",
    "Tuesday",
    "Wednesday",
    "Thursday",
    "Friday",
    "Saturday",
    "Sunday",
)

internal fun minWeekdayGap(weekdays: List<String>): Int {
    if (weekdays.size < 2) return 1
    val indices = weekdays
        .map { WEEKDAY_ORDER.indexOf(it) }
        .filter { it >= 0 }
        .sorted()
    if (indices.size < 2) return 1
    var minGap = 7
    for (i in 1 until indices.size) {
        minGap = minOf(minGap, indices[i] - indices[i - 1])
    }
    val wrap = 7 - indices.last() + indices.first()
    return minOf(minGap, wrap)
}

internal fun recoveryScore(
    sessionTypes: List<SessionTypeId>,
    weekdays: List<String>,
    context: ProgramGenerationContext,
): Int =
    calendarRecoveryScore(sessionTypes, sortedWeekdayIndices(weekdays), context)

internal fun volumeFitScore(
    template: SplitTemplate,
    weeklyTargets: WeeklyVolumeTargets,
    context: ProgramGenerationContext,
): Int {
    val sessionMuscles = template.sessionTypes.map { st -> primaryMusclesForSessionType(st) }
    if (!validateCycleFrequency(sessionMuscles, weeklyTargets, context)) {
        return 0
    }
    return if (template.isDefault) 10 else 5
}

internal fun scoreTemplate(
    template: SplitTemplate,
    context: ProgramGenerationContext,
    weeklyTargets: WeeklyVolumeTargets,
): Int =
    recoveryScore(template.sessionTypes, context.weekdays, context) +
        volumeFitScore(template, weeklyTargets, context) +
        rotationTemplateScore(template, context, weeklyTargets) +
        if (template.isDefault) 5 else 0

internal fun rotationTemplateScore(
    template: SplitTemplate,
    context: ProgramGenerationContext,
    weeklyTargets: WeeklyVolumeTargets,
): Int {
    if (context.specialization.values.any { it == "Specialize" }) return 0

    var score = 0
    val mvCount = context.specialization.count { (_, level) -> level == "MinimumVolume" }
    val aggregateAim = aggregateWeeklyAim(weeklyTargets)
    val isTier1Profile = requiredFrequencyTier(weeklyTargets) == FrequencyTier.One &&
        (mvCount >= 3 || aggregateAim <= context.trainingDaysPerWeek * 12.0)
    val isRotationTemplate = template.id.contains("t1-") &&
        (template.id.contains("micro") || template.id.contains("carousel") || template.id.contains("fb-rot"))
    if (isTier1Profile && isRotationTemplate) score += 12

    val avgSlots = template.sessionTypes.map { slotsForSessionType(it).size }.average()
    if (context.trainingDaysPerWeek >= 5 &&
        (context.sessionLengthMinutes ?: 60) <= 45 &&
        avgSlots <= 2.5
    ) {
        score += 8
    }
  if (violatesLiftFamilySeparation(template)) score -= 10
    return score
}

/** Penalize same-day anchor + aux from the same pattern family on rotation templates. */
internal fun violatesLiftFamilySeparation(template: SplitTemplate): Boolean {
    val familyPatterns = mapOf(
        "squat" to setOf("SquatPattern", "KneeExtension"),
        "press" to setOf("HorizontalPress", "InclinePress", "VerticalPress"),
        "hinge" to setOf("HipHinge", "KneeFlexion"),
    )
    for (sessionType in template.sessionTypes) {
        val slots = slotsForSessionType(sessionType)
        val anchors = slots.filter { it.role == SlotRole.Anchor }
        val others = slots.filter { it.role != SlotRole.Anchor }
        for (anchor in anchors) {
            val anchorFamily = familyPatterns.entries.firstOrNull { (_, patterns) ->
                anchor.pattern in patterns
            }?.key ?: continue
            if (others.any { slot ->
                    familyPatterns[anchorFamily]?.contains(slot.pattern) == true
                }
            ) {
                return true
            }
        }
    }
    return false
}

internal fun siblingIndexForSessionType(sessionTypes: List<SessionTypeId>, index: Int): Int {
    val type = sessionTypes[index]
    var count = 0
    for (i in 0 until index) {
        if (sessionTypes[i] == type) count++
    }
    return count
}

internal fun buildSessions(
    template: SplitTemplate,
    context: ProgramGenerationContext,
): List<ResolvedSessionPlan> {
    val typeCounts = mutableMapOf<SessionTypeId, Int>()
    return template.sessionTypes.mapIndexed { position, sessionType ->
        val siblingIndex = siblingIndexForSessionType(template.sessionTypes, position)
        val count = typeCounts[sessionType] ?: 0
        typeCounts[sessionType] = count + 1
        ResolvedSessionPlan(
            sessionType = sessionType,
            name = displayNameForSessionType(sessionType, siblingIndex),
            slots = slotsForSessionType(sessionType, context, siblingIndex, sessionPosition = position).map { it.copy() },
            siblingIndex = siblingIndex,
            position = position,
        )
    }
}

fun resolveSplit(
    context: ProgramGenerationContext,
    weeklyTargets: WeeklyVolumeTargets,
): ResolvedSplit {
    val requiredTier = requiredFrequencyTier(weeklyTargets)
    var candidates = splitsForDaysAndTier(context.trainingDaysPerWeek, requiredTier)
    if (candidates.isEmpty()) {
        candidates = splitsForDaysAndTier(context.trainingDaysPerWeek, FrequencyTier.One)
    }
    if (candidates.isEmpty()) {
        error("No split template for ${context.trainingDaysPerWeek} training days")
    }

    val viable =
        candidates.filter { template ->
            volumeFitScore(template, weeklyTargets, context) > 0
        }
    val pool = viable.ifEmpty { candidates }
    val ranked = pool.sortedByDescending { scoreTemplate(it, context, weeklyTargets) }
    val template = ranked.first()
    val sessions = buildSessions(template, context)
    return ResolvedSplit(
        template = template,
        sessions = sessions,
        cycleLength = sessions.size,
    )
}
