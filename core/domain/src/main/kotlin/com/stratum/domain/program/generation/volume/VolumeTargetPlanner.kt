@file:Suppress("MagicNumber", "ReturnCount", "LoopWithTooManyJumpStatements", "MatchingDeclarationName", "ComplexCondition", "MaxLineLength")

package com.stratum.domain.program.generation.volume

import com.stratum.domain.program.generation.context.MAJOR_MUSCLES
import com.stratum.domain.program.generation.context.ProgramGenerationContext
import kotlin.math.max
import kotlin.math.round

/** Per-muscle weekly hard-set credit target. `min` is MEV (or MAV−tolerance for Specialize),
 * `aim` is the productive target, `max` is the MRV ceiling. */
data class WeeklyVolumeTarget(
    val min: Double,
    val aim: Double,
    val max: Double,
)

typealias WeeklyVolumeTargets = Map<String, WeeklyVolumeTarget>

/** Hard-set credit tolerance applied to MEV/MAV/MRV bounds (matches the validators). */
private const val VOLUME_TOLERANCE_SETS = 2

/**
 * Per-muscle weekly volume target for [muscle] given specialization, return-to-training
 * conservatism, and volume-response evidence. Extracted verbatim from the former private
 * `resolveVolumeTarget` in `programValidation.ts` — do not change behavior without
 * updating the matching validator tests.
 */
internal fun resolveUnscaledTarget(
    muscle: String,
    specializationLevel: String,
    context: ProgramGenerationContext,
): WeeklyVolumeTarget {
    val landmark = context.volumeLandmarksByMuscle[muscle]
    val mev = landmark?.estimatedMev ?: 8.0
    val mav = landmark?.estimatedMav ?: 14.0
    val mrv = landmark?.estimatedMrv ?: 20.0
    val pv = landmark?.estimatedPv ?: 11.0

    val lastTrained = context.lastTrained
    val conservative =
        lastTrained == "SixToTwelveMonthsAgo" ||
            lastTrained == "MoreThanAYearAgo" ||
            lastTrained == "Never"

    val volumeResponse = context.volumeResponsesByMuscle[muscle]
    val respondsToVolume =
        volumeResponse != null &&
            volumeResponse.correlationSlope > 0 &&
            volumeResponse.evidence == "Proven"

    if (specializationLevel == "Specialize") {
        val aim = if (respondsToVolume) mrv else minOf(mrv, mav + (mrv - mav) * 0.6)
        return WeeklyVolumeTarget(
            min = max(mev, mav - VOLUME_TOLERANCE_SETS),
            aim = aim,
            max = mrv,
        )
    }

    if (conservative) {
        return WeeklyVolumeTarget(min = mev, aim = pv, max = mav)
    }

    return WeeklyVolumeTarget(min = mev, aim = mav, max = mrv)
}

internal fun trainableMuscles(context: ProgramGenerationContext): List<String> {
    val patternsByMuscle = availablePatternsByMuscle(context)
    return targetableMuscles(context).filter { muscle ->
        (patternsByMuscle[muscle]?.size ?: 0) > 0
    }
}

fun resolveMuscleWeeklyTarget(
    muscle: String,
    specializationLevel: String,
    context: ProgramGenerationContext,
): WeeklyVolumeTarget {
    val unscaled = resolveUnscaledTarget(muscle, specializationLevel, context)

    val sessionLength = context.sessionLengthMinutes
    if (sessionLength == null || sessionLength <= 0) {
        return unscaled
    }

    val timeBudget = kotlin.math.floor((sessionLength * 1.08) / 3.5).toInt()
    val weeklyTimeBudget = context.trainingDaysPerWeek * timeBudget

    var totalUnscaledAim = 0.0
    for (m in trainableMuscles(context)) {
        val lvl = context.specialization[m] ?: "Maintenance"
        val target = resolveUnscaledTarget(m, lvl, context)
        totalUnscaledAim += target.aim
    }

    if (totalUnscaledAim > weeklyTimeBudget) {
        val scale = weeklyTimeBudget / totalUnscaledAim
        return WeeklyVolumeTarget(
            min = max(2.0, round(unscaled.min * scale * 2) / 2.0),
            aim = max(2.0, round(unscaled.aim * scale * 2) / 2.0),
            max = max(2.0, round(unscaled.max * scale * 2) / 2.0),
        )
    }

    return unscaled
}

/**
 * The muscles Phase 0 must plan volume for: every major muscle, any muscle with a
 * personal/literature landmark, and any muscle the user explicitly specialized or omitted.
 * Omitted muscles are excluded from targeting (they receive zero volume).
 */
internal fun targetableMuscles(context: ProgramGenerationContext): List<String> {
    val muscles = MAJOR_MUSCLES.toMutableSet()
    muscles.addAll(context.volumeLandmarksByMuscle.keys)
    muscles.addAll(context.specialization.keys)
    return muscles.filter { muscle ->
        (context.specialization[muscle] ?: "Maintenance") != "Omit"
    }
}

/**
 * Computes the per-muscle weekly volume target for every non-Omitted muscle, keyed by muscle.
 * Consumed by the split allocator (Phase 0) and the LLM split planner (Phase 1, 5–6 days).
 */
fun computeAllWeeklyTargets(context: ProgramGenerationContext): WeeklyVolumeTargets {
    val targets = mutableMapOf<String, WeeklyVolumeTarget>()
    for (muscle in targetableMuscles(context)) {
        val level = context.specialization[muscle] ?: "Maintenance"
        targets[muscle] = resolveMuscleWeeklyTarget(muscle, level, context)
    }
    return targets
}

/**
 * For each muscle, the movement patterns the user has at least one enabled lift for. Used by
 * the split allocator's feasibility guard (never plan a slot you can't fill) and the LLM split
 * planner (knows a "Chest touch" day is possible). Patterns are drawn from the payload's
 * proven/available lift pool — both tiers are already filtered to enabled lifts on the client.
 */
fun availablePatternsByMuscle(context: ProgramGenerationContext): Map<String, List<String>> {
    val byMuscle = mutableMapOf<String, MutableSet<String>>()
    for (lift in context.liftsById.values) {
        for (muscle in lift.primaryMuscles + lift.secondaryMuscles) {
            byMuscle.getOrPut(muscle) { mutableSetOf() }.add(lift.movementPattern)
        }
    }
    return byMuscle.mapValues { (_, patterns) -> patterns.toList() }
}
