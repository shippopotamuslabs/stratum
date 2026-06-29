@file:Suppress("MagicNumber", "ReturnCount", "LoopWithTooManyJumpStatements", "MatchingDeclarationName", "ComplexCondition", "MaxLineLength")

package com.stratum.domain.program.generation.split

import com.stratum.domain.program.generation.context.ProgramGenerationContext
import com.stratum.domain.program.generation.reference.FrequencyTier
import com.stratum.domain.program.generation.volume.WeeklyVolumeTargets
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

internal fun recoveryScore(sessionTypes: List<SessionTypeId>, weekdays: List<String>): Int {
    val minGap = minWeekdayGap(weekdays)
    val musclePositions = mutableMapOf<String, MutableList<Int>>()
    sessionTypes.forEachIndexed { idx, st ->
        for (muscle in primaryMusclesForSessionType(st)) {
            musclePositions.getOrPut(muscle) { mutableListOf() }.add(idx)
        }
    }
    var penalty = 0
    for (positions in musclePositions.values) {
        if (positions.size < 2) continue
        for (i in 1 until positions.size) {
            val gap = positions[i] - positions[i - 1]
            if (gap < minGap) penalty += (minGap - gap) * 2
        }
    }
    return 100 - penalty
}

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
    recoveryScore(template.sessionTypes, context.weekdays) +
        volumeFitScore(template, weeklyTargets, context) +
        if (template.isDefault) 5 else 0

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
            slots = slotsForSessionType(sessionType, context).map { it.copy() },
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

    val ranked = candidates.sortedByDescending { scoreTemplate(it, context, weeklyTargets) }
    val template = ranked.first()
    val sessions = buildSessions(template, context)
    return ResolvedSplit(
        template = template,
        sessions = sessions,
        cycleLength = sessions.size,
    )
}
