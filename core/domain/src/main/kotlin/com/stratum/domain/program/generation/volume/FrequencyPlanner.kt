@file:Suppress("MagicNumber", "ReturnCount", "LoopWithTooManyJumpStatements", "MatchingDeclarationName", "ComplexCondition", "MaxLineLength")

package com.stratum.domain.program.generation.volume

import com.stratum.domain.program.generation.context.ProgramGenerationContext
import com.stratum.domain.program.generation.reference.SESSION_MUSCLE_SET_CAP
import com.stratum.domain.program.generation.reference.FrequencyTier
import com.stratum.domain.program.generation.reference.frequencyTierForWeeklySets
import com.stratum.domain.program.generation.volume.targetableMuscles

fun requiredFrequencyTier(weeklyTargets: WeeklyVolumeTargets): FrequencyTier {
    var maxTier = FrequencyTier.One
    for ((_, target) in weeklyTargets) {
        val tier = maxOf(
            frequencyTierForWeeklySets(target.aim),
            frequencyTierForSessionCap(target.aim),
        )
        if (tier.value > maxTier.value) maxTier = tier
    }
    return maxTier
}

/** Minimum sessions so no workout exceeds [SESSION_MUSCLE_SET_CAP] primary credits. */
internal fun frequencyTierForSessionCap(weeklyAim: Double): FrequencyTier =
    when {
        weeklyAim <= SESSION_MUSCLE_SET_CAP -> FrequencyTier.One
        weeklyAim <= SESSION_MUSCLE_SET_CAP * 2 -> FrequencyTier.Two
        else -> FrequencyTier.Three
    }

/** Sessions in cycle that train [muscle] as primary (from filled session plans). */
fun muscleSessionsInCycle(
    sessionMuscles: List<List<String>>,
    muscle: String,
): Int = sessionMuscles.count { muscles -> muscle in muscles }

fun validateCycleFrequency(
    sessionMuscles: List<List<String>>,
    weeklyTargets: WeeklyVolumeTargets,
    context: ProgramGenerationContext,
): Boolean {
    val cycleLength = sessionMuscles.size
    for (muscle in frequencyPlannerTrainableMuscles(context)) {
        val target = weeklyTargets[muscle] ?: continue
        if (target.aim <= 0) continue
        val tier = maxOf(
            frequencyTierForWeeklySets(target.aim),
            frequencyTierForSessionCap(target.aim),
        )
        val sessions = muscleSessionsInCycle(sessionMuscles, muscle)
        val minRequired = when (tier) {
            FrequencyTier.One -> 1
            FrequencyTier.Two -> 2
            FrequencyTier.Three -> 3
        }
        if (sessions < minRequired && cycleLength >= minRequired) {
            return false
        }
    }
    return true
}

internal fun frequencyPlannerTrainableMuscles(context: ProgramGenerationContext): List<String> {
    val patternsByMuscle = availablePatternsByMuscle(context)
    return targetableMuscles(context).filter { muscle ->
        (patternsByMuscle[muscle]?.size ?: 0) > 0
    }
}

fun aggregateWeeklyAim(weeklyTargets: WeeklyVolumeTargets): Double {
    var total = 0.0
    for ((_, t) in weeklyTargets) total += t.aim
    return total
}
